package com.metrolist.desktop.cast

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.*
import javax.sound.sampled.AudioSystem

enum class DeviceType {
    COMPUTER,
    SMART_TV,
    SPEAKER,
    BLUETOOTH,
}

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val protocol: String,
    val ipAddress: String? = null,
    val isCurrent: Boolean = false,
)

object DeviceDiscoveryService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val defaultComputerDevice = DiscoveredDevice(
        id = "local_pc",
        name = "This computer",
        type = DeviceType.COMPUTER,
        protocol = "Komputer ini",
        isCurrent = true,
    )

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(listOf(defaultComputerDevice))
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _selectedDeviceId = MutableStateFlow<String>("local_pc")
    val selectedDeviceId: StateFlow<String> = _selectedDeviceId.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun selectDevice(id: String) {
        _selectedDeviceId.value = id
        _devices.value = _devices.value.map { dev ->
            dev.copy(isCurrent = (dev.id == id))
        }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            _isScanning.value = true
            val found = mutableMapOf<String, DiscoveredDevice>()

            // Selalu sediakan perangkat lokal (This computer)
            found["local_pc"] = defaultComputerDevice.copy(isCurrent = (_selectedDeviceId.value == "local_pc"))
            _devices.value = found.values.toList()

            // 1. Deteksi audio endpoint Windows (Bluetooth, HDMI, dsb.)
            try {
                detectWindowsAudioEndpoints(found)
                _devices.value = found.values.toList()
            } catch (_: Throwable) {}

            // Cari antarmuka Wi-Fi / LAN yang aktif dengan alamat IPv4 privat
            val activeInterfaces = getActiveLanInterfaces()

            // Jalankan mDNS dan SSDP secara paralel untuk setiap antarmuka jaringan yang aktif
            val scanJobs = mutableListOf<Job>()

            for (lanAddress in activeInterfaces) {
                // 2. Deteksi Google Cast via mDNS (dengan flag UNICAST-RESPONSE 0x8001)
                scanJobs += launch {
                    try {
                        discoverGoogleCastOnInterface(lanAddress, found)
                    } catch (_: Throwable) {}
                }

                // 3. Deteksi Smart TV via SSDP (UPnP / DLNA / DIAL)
                scanJobs += launch {
                    try {
                        discoverSsdpOnInterface(lanAddress, found)
                    } catch (_: Throwable) {}
                }
            }

            // Tunggu hingga pemindaian selesai atau batas waktu 3 detik
            withTimeoutOrNull(3000) {
                scanJobs.joinAll()
            }

            _devices.value = found.values.toList()
            _isScanning.value = false
        }
    }

    private fun getActiveLanInterfaces(): List<Inet4Address> {
        val result = mutableListOf<Inet4Address>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback || !ni.supportsMulticast()) continue

                val addrs = ni.inetAddresses.toList().filterIsInstance<Inet4Address>()
                for (addr in addrs) {
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                    val host = addr.hostAddress ?: continue
                    // Utamakan alamat jaringan lokal privat (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
                    // Abaikan hotspot virtual Windows (192.168.137.x)
                    if (host.startsWith("192.168.137.")) continue

                    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                        result.add(addr)
                    }
                }
            }
        } catch (_: Throwable) {}
        return result
    }

    private fun detectWindowsAudioEndpoints(found: MutableMap<String, DiscoveredDevice>) {
        val mixers = AudioSystem.getMixerInfo()
        for (info in mixers) {
            val name = info.name ?: continue
            val desc = info.description?.lowercase() ?: ""
            val lower = name.lowercase()

            val isBluetooth = lower.contains("bluetooth") || desc.contains("bluetooth")
            val isTvOrHdmi = lower.contains("tv") || lower.contains("hdmi") || desc.contains("hdmi") || lower.contains("display")
            val isHeadphone = lower.contains("headphone") || lower.contains("headset")

            if (isBluetooth || isTvOrHdmi || isHeadphone) {
                if (lower.contains("mic") || lower.contains("capture") || lower.contains("line in")) continue

                val type = when {
                    isTvOrHdmi -> DeviceType.SMART_TV
                    isBluetooth -> DeviceType.BLUETOOTH
                    else -> DeviceType.SPEAKER
                }
                val protocol = when {
                    isTvOrHdmi -> "HDMI / Display Audio"
                    isBluetooth -> "Bluetooth Audio"
                    else -> "Audio Eksternal"
                }

                val cleanName = cleanAudioName(name)
                val id = "win_audio_${cleanName.hashCode()}"
                if (!found.containsKey(id)) {
                    found[id] = DiscoveredDevice(
                        id = id,
                        name = cleanName,
                        type = type,
                        protocol = protocol,
                        isCurrent = (_selectedDeviceId.value == id),
                    )
                }
            }
        }
    }

    private fun cleanAudioName(raw: String): String {
        return raw.replace(Regex("^Speakers \\((.*)\\)$"), "$1")
            .replace(Regex("^Headphones \\((.*)\\)$"), "$1")
            .trim()
    }

    private fun discoverGoogleCastOnInterface(
        localIp: Inet4Address,
        found: MutableMap<String, DiscoveredDevice>,
    ) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(0) // ID
        dos.writeShort(0) // Query flags
        dos.writeShort(1) // 1 question
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        // Query "_googlecast._tcp.local"
        for (part in listOf("_googlecast", "_tcp", "local")) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
        dos.writeShort(12) // Type: PTR
        dos.writeShort(0x8001) // Class: IN + UNICAST-RESPONSE (QU bit aktif)

        val packetData = baos.toByteArray()
        val mDnsGroup = InetAddress.getByName("224.0.0.251")

        DatagramSocket(InetSocketAddress(localIp, 0)).use { socket ->
            socket.soTimeout = 2500

            val sendPacket = DatagramPacket(packetData, packetData.size, mDnsGroup, 5353)
            socket.send(sendPacket)

            val buf = ByteArray(4096)
            val recvPacket = DatagramPacket(buf, buf.size)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 2500) {
                try {
                    socket.receive(recvPacket)
                    val rawStr = String(buf, 0, recvPacket.length, Charsets.ISO_8859_1)
                    val fnIdx = rawStr.indexOf("fn=")
                    if (fnIdx != -1) {
                        var endIdx = rawStr.indexOf('\u0000', fnIdx)
                        if (endIdx == -1) endIdx = rawStr.length
                        for (i in (fnIdx + 3) until endIdx) {
                            if (rawStr[i].code < 32) {
                                endIdx = i
                                break
                            }
                        }
                        val friendlyName = rawStr.substring(fnIdx + 3, endIdx).trim()
                        if (friendlyName.isNotBlank()) {
                            val ip = recvPacket.address?.hostAddress
                            val id = "cast_${friendlyName.hashCode()}"
                            val dev = DiscoveredDevice(
                                id = id,
                                name = friendlyName,
                                type = DeviceType.SMART_TV,
                                protocol = "Google Cast",
                                ipAddress = ip,
                                isCurrent = (_selectedDeviceId.value == id),
                            )
                            found[id] = dev
                            _devices.value = found.values.toList()
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Throwable) {
                    break
                }
            }
        }
    }

    private fun discoverSsdpOnInterface(
        localIp: Inet4Address,
        found: MutableMap<String, DiscoveredDevice>,
    ) {
        val ssdpMessage = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n\r\n"

        val ssdpBytes = ssdpMessage.toByteArray(Charsets.UTF_8)
        val ssdpGroup = InetAddress.getByName("239.255.255.250")

        DatagramSocket(InetSocketAddress(localIp, 0)).use { socket ->
            socket.soTimeout = 2500
            val sendPacket = DatagramPacket(ssdpBytes, ssdpBytes.size, ssdpGroup, 1900)
            socket.send(sendPacket)

            val buf = ByteArray(4096)
            val recvPacket = DatagramPacket(buf, buf.size)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 2500) {
                try {
                    socket.receive(recvPacket)
                    val response = String(buf, 0, recvPacket.length, Charsets.UTF_8)
                    val serverLine = response.lines().firstOrNull { it.startsWith("SERVER:", ignoreCase = true) }
                    val stLine = response.lines().firstOrNull { it.startsWith("ST:", ignoreCase = true) }

                    // Jika merespons sebagai MediaRenderer atau DIAL TV (Smart TV)
                    val isMedia = stLine?.contains("MediaRenderer", ignoreCase = true) == true ||
                            stLine?.contains("dial-multiscreen", ignoreCase = true) == true

                    if (isMedia && serverLine != null) {
                        val serverVal = serverLine.substringAfter(":").trim()
                        val name = serverVal.split("/").firstOrNull()?.trim() ?: "Smart TV"
                        val ip = recvPacket.address?.hostAddress

                        // Hanya tambahkan jika belum terdeteksi oleh Google Cast mDNS
                        val isAlreadyCast = found.values.any { it.ipAddress == ip && it.protocol == "Google Cast" }
                        if (!isAlreadyCast) {
                            val id = "ssdp_${name.hashCode()}_${ip}"
                            val dev = DiscoveredDevice(
                                id = id,
                                name = name,
                                type = DeviceType.SMART_TV,
                                protocol = "Smart TV (DLNA / Wi-Fi)",
                                ipAddress = ip,
                                isCurrent = (_selectedDeviceId.value == id),
                            )
                            found[id] = dev
                            _devices.value = found.values.toList()
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Throwable) {
                    break
                }
            }
        }
    }
}
