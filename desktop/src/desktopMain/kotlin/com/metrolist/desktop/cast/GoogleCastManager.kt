package com.metrolist.desktop.cast

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.SocketException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object GoogleCastManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectedDevice = MutableStateFlow<DiscoveredDevice?>(null)
    val connectedDevice: StateFlow<DiscoveredDevice?> = _connectedDevice.asStateFlow()

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    private val _castVolume = MutableStateFlow(30)
    val castVolume: StateFlow<Int> = _castVolume.asStateFlow()

    private val _isCastMuted = MutableStateFlow(false)
    val isCastMuted: StateFlow<Boolean> = _isCastMuted.asStateFlow()

    private var socket: SSLSocket? = null
    private var outStream: DataOutputStream? = null
    private var inStream: DataInputStream? = null
    private var heartBeatJob: Job? = null
    private var readerJob: Job? = null
    private var transportId: String? = null
    private var sessionId: String? = null
    private var mediaSessionId: Int? = null
    private val requestIdCounter = AtomicInteger(1)
    private var loadMediaJob: Job? = null

    var onTrackFinished: (() -> Unit)? = null
    var onCurrentTimeUpdated: ((Long) -> Unit)? = null

    fun connect(device: DiscoveredDevice) {
        val ip = device.ipAddress ?: return
        disconnect()

        scope.launch {
            _isConnecting.value = true
            _connectedDevice.value = device
            _statusText.value = "Menghubungkan ke ${device.name}..."
            try {
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                    override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
                })
                val sc = SSLContext.getInstance("TLS")
                sc.init(null, trustAll, SecureRandom())

                val sock = sc.socketFactory.createSocket(ip, 8009) as SSLSocket
                sock.soTimeout = 0 // Persistent streaming connection
                sock.tcpNoDelay = true
                socket = sock
                outStream = DataOutputStream(sock.outputStream)
                inStream = DataInputStream(sock.inputStream)

                startHeartbeat()
                startReader()

                // 1. Send CONNECT to receiver-0
                sendMessage(
                    sourceId = "sender-0",
                    destId = "receiver-0",
                    namespace = "urn:x-cast:com.google.cast.tp.connection",
                    payloadJson = """{"type":"CONNECT","userAgent":"Echo/13.7.3"}"""
                )

                // 2. Launch Default Media Receiver (CC1AD845)
                val reqId = requestIdCounter.incrementAndGet()
                sendMessage(
                    sourceId = "sender-0",
                    destId = "receiver-0",
                    namespace = "urn:x-cast:com.google.cast.receiver",
                    payloadJson = """{"type":"LAUNCH","appId":"CC1AD845","requestId":$reqId}"""
                )

                // 3. Tunggu transportId siap dari Default Media Receiver (hingga 15 detik jika TV baru menyala)
                var waitCount = 0
                while (transportId == null && waitCount < 150 && _isConnecting.value) {
                    delay(100)
                    waitCount++
                }

                if (transportId != null) {
                    _isConnected.value = true
                    _isConnecting.value = false
                    _statusText.value = "Terhubung ke ${device.name}"
                    DeviceDiscoveryService.selectDevice(device.id)
                } else {
                    throw IllegalStateException("Timeout menunggu aplikasi pemutar di ${device.name}")
                }
            } catch (e: Throwable) {
                _isConnected.value = false
                _isConnecting.value = false
                _connectedDevice.value = null
                _statusText.value = "Gagal terhubung ke ${device.name}: ${e.message}"
                disconnect()
            }
        }
    }

    private fun handleConnectionLoss(reason: String) {
        if (!_isConnected.value && !_isConnecting.value) return
        scope.launch {
            _statusText.value = reason
            disconnect(sendStop = false)
        }
    }

    fun disconnect(sendStop: Boolean = true) {
        heartBeatJob?.cancel()
        heartBeatJob = null
        readerJob?.cancel()
        readerJob = null
        loadMediaJob?.cancel()
        loadMediaJob = null

        if (sendStop) {
            try {
                if (_isConnected.value && socket != null && !socket!!.isClosed) {
                    // 1. Hentikan media jika ada yang sedang berjalan di TV
                    mediaSessionId?.let { sId ->
                        val reqId = requestIdCounter.incrementAndGet()
                        sendMessage(
                            sourceId = "sender-0",
                            destId = transportId ?: "receiver-0",
                            namespace = "urn:x-cast:com.google.cast.media",
                            payloadJson = """{"type":"STOP","mediaSessionId":$sId,"requestId":$reqId}"""
                        )
                    }

                    // 2. Beritahu TV untuk menutup aplikasi Default Media Receiver (CC1AD845)
                    sessionId?.let { sId ->
                        val reqId = requestIdCounter.incrementAndGet()
                        sendMessage(
                            sourceId = "sender-0",
                            destId = "receiver-0",
                            namespace = "urn:x-cast:com.google.cast.receiver",
                            payloadJson = """{"type":"STOP","sessionId":"$sId","requestId":$reqId}"""
                        )
                    }

                    // 3. Tutup transport connection
                    sendMessage(
                        sourceId = "sender-0",
                        destId = transportId ?: "receiver-0",
                        namespace = "urn:x-cast:com.google.cast.tp.connection",
                        payloadJson = """{"type":"CLOSE"}"""
                    )
                    sendMessage(
                        sourceId = "sender-0",
                        destId = "receiver-0",
                        namespace = "urn:x-cast:com.google.cast.tp.connection",
                        payloadJson = """{"type":"CLOSE"}"""
                    )
                }
            } catch (_: Throwable) {}
        }

        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        outStream = null
        inStream = null
        transportId = null
        sessionId = null
        mediaSessionId = null

        _isConnected.value = false
        _isConnecting.value = false
        _connectedDevice.value = null
        _statusText.value = null
        _isCastMuted.value = false
        DeviceDiscoveryService.selectDevice("local_pc")
    }

    fun loadMedia(streamUrl: String, title: String, artist: String, thumbnailUrl: String?) {
        val dest = transportId ?: run {
            System.err.println("GoogleCastManager: Tidak dapat loadMedia karena transportId belum siap.")
            return
        }

        val reqId = requestIdCounter.incrementAndGet()
        val safeTitle = title.replace("\"", "\\\"")
        val safeArtist = artist.replace("\"", "\\\"")
        val thumbObj = if (!thumbnailUrl.isNullOrBlank()) """[{"url":"$thumbnailUrl"}]""" else "[]"

        val json = """
            {
                "type": "LOAD",
                "requestId": $reqId,
                "media": {
                    "contentId": "$streamUrl",
                    "streamType": "BUFFERED",
                    "contentType": "audio/mp4",
                    "metadata": {
                        "metadataType": 3,
                        "title": "$safeTitle",
                        "artist": "$safeArtist",
                        "images": $thumbObj
                    }
                },
                "autoplay": true
            }
        """.trimIndent()

        loadMediaJob?.cancel()
        loadMediaJob = scope.launch {
            try {
                sendMessage(
                    sourceId = "sender-0",
                    destId = dest,
                    namespace = "urn:x-cast:com.google.cast.media",
                    payloadJson = json
                )
            } catch (e: Throwable) {
                System.err.println("GoogleCastManager loadMedia error: ${e.message}")
            }
        }
    }

    fun pause() {
        val dest = transportId ?: return
        val reqId = requestIdCounter.incrementAndGet()
        val sId = mediaSessionId ?: 1
        scope.launch {
            try {
                sendMessage("sender-0", dest, "urn:x-cast:com.google.cast.media", """{"type":"PAUSE","mediaSessionId":$sId,"requestId":$reqId}""")
            } catch (_: Throwable) {}
        }
    }

    fun resume() {
        val dest = transportId ?: return
        val reqId = requestIdCounter.incrementAndGet()
        val sId = mediaSessionId ?: 1
        scope.launch {
            try {
                sendMessage("sender-0", dest, "urn:x-cast:com.google.cast.media", """{"type":"PLAY","mediaSessionId":$sId,"requestId":$reqId}""")
            } catch (_: Throwable) {}
        }
    }

    fun seek(positionMs: Long) {
        val dest = transportId ?: return
        val reqId = requestIdCounter.incrementAndGet()
        val sId = mediaSessionId ?: 1
        val currentTimeSec = positionMs / 1000.0
        scope.launch {
            try {
                sendMessage("sender-0", dest, "urn:x-cast:com.google.cast.media", """{"type":"SEEK","mediaSessionId":$sId,"currentTime":$currentTimeSec,"requestId":$reqId}""")
            } catch (_: Throwable) {}
        }
    }

    fun setVolume(volPercent: Int) {
        val clamped = volPercent.coerceIn(0, 100)
        _castVolume.value = clamped
        _isCastMuted.value = false
        val reqId = requestIdCounter.incrementAndGet()
        val level = clamped / 100f
        scope.launch {
            try {
                sendMessage(
                    "sender-0",
                    "receiver-0",
                    "urn:x-cast:com.google.cast.receiver",
                    """{"type":"SET_VOLUME","volume":{"level":$level,"muted":false},"requestId":$reqId}"""
                )
            } catch (_: Throwable) {}
        }
    }

    fun setMuted(muted: Boolean) {
        _isCastMuted.value = muted
        val reqId = requestIdCounter.incrementAndGet()
        scope.launch {
            try {
                sendMessage(
                    "sender-0",
                    "receiver-0",
                    "urn:x-cast:com.google.cast.receiver",
                    """{"type":"SET_VOLUME","volume":{"muted":$muted},"requestId":$reqId}"""
                )
            } catch (_: Throwable) {}
        }
    }

    private fun startHeartbeat() {
        heartBeatJob?.cancel()
        heartBeatJob = scope.launch {
            while (isActive && (socket != null && !socket!!.isClosed)) {
                try {
                    sendMessage("sender-0", transportId ?: "receiver-0", "urn:x-cast:com.google.cast.tp.heartbeat", """{"type":"PING"}""")
                    val currentTransportId = transportId
                    if (currentTransportId != null) {
                        val reqId = requestIdCounter.incrementAndGet()
                        val sId = mediaSessionId
                        val getStatusPayload = if (sId != null) {
                            """{"type":"GET_STATUS","mediaSessionId":$sId,"requestId":$reqId}"""
                        } else {
                            """{"type":"GET_STATUS","requestId":$reqId}"""
                        }
                        sendMessage("sender-0", currentTransportId, "urn:x-cast:com.google.cast.media", getStatusPayload)
                    }
                } catch (_: Throwable) {}
                delay(3000)
            }
        }
    }

    private fun startReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val stream = inStream ?: return@launch
            while (isActive && socket != null && !socket!!.isClosed) {
                try {
                    val len = stream.readInt()
                    if (len <= 0 || len > 1048576) {
                        handleConnectionLoss("Sesi pemutar di TV ditutup.")
                        break
                    }
                    val buf = ByteArray(len)
                    stream.readFully(buf)
                    val rawStr = String(buf, Charsets.UTF_8)

                    // 1. Cek transportId dan sessionId Default Media Receiver (CC1AD845)
                    if (rawStr.contains("sessionId")) {
                        val sessionMatch = Regex(""""sessionId"\s*:\s*"([^"]+)"""").find(rawStr)
                        if (sessionMatch != null) {
                            sessionId = sessionMatch.groupValues[1]
                        }
                    }

                    if (rawStr.contains("CC1AD845") || (rawStr.contains("transportId") && transportId == null)) {
                        val match = Regex(""""appId"\s*:\s*"CC1AD845"[^}]*?"transportId"\s*:\s*"([^"]+)"""").find(rawStr)
                            ?: Regex(""""transportId"\s*:\s*"([^"]+)"[^}]*?"appId"\s*:\s*"CC1AD845"""").find(rawStr)
                            ?: Regex(""""transportId"\s*:\s*"([^"]+)"""").find(rawStr)

                        if (match != null) {
                            val newTransportId = match.groupValues[1]
                            if (transportId != newTransportId) {
                                transportId = newTransportId
                                sendMessage(
                                    sourceId = "sender-0",
                                    destId = newTransportId,
                                    namespace = "urn:x-cast:com.google.cast.tp.connection",
                                    payloadJson = """{"type":"CONNECT","userAgent":"Echo/13.7.3"}"""
                                )
                            }
                        }
                    }

                    // 2. Cek status volume dari TV (RECEIVER_STATUS)
                    if (rawStr.contains("RECEIVER_STATUS") && rawStr.contains("volume")) {
                        val levelMatch = Regex(""""volume"\s*:\s*\{[^}]*?"level"\s*:\s*([0-9.]+)""").find(rawStr)
                        if (levelMatch != null) {
                            val rawLvl = levelMatch.groupValues[1].toFloat()
                            val lvl = (rawLvl * 100).toInt()
                            if (lvl > 0) {
                                _castVolume.value = lvl
                            }
                        }
                        val muteMatch = Regex(""""volume"\s*:\s*\{[^}]*?"muted"\s*:\s*(true|false)""").find(rawStr)
                        if (muteMatch != null) {
                            _isCastMuted.value = muteMatch.groupValues[1].toBoolean()
                        }
                    }

                    // 3. Cek mediaSessionId dari MEDIA_STATUS
                    if (rawStr.contains("mediaSessionId")) {
                        val sessMatch = Regex(""""mediaSessionId"\s*:\s*([0-9]+)""").find(rawStr)
                        if (sessMatch != null) {
                            mediaSessionId = sessMatch.groupValues[1].toInt()
                        }
                    }

                    // 4. Cek currentTime dari MEDIA_STATUS
                    if (rawStr.contains("currentTime")) {
                        val timeMatch = Regex(""""currentTime"\s*:\s*([0-9.]+)""").find(rawStr)
                        if (timeMatch != null) {
                            val sec = timeMatch.groupValues[1].toDoubleOrNull()
                            if (sec != null && sec >= 0.0) {
                                val ms = (sec * 1000).toLong()
                                onCurrentTimeUpdated?.invoke(ms)
                            }
                        }
                    }

                    // 5. Cek lagu selesai diputar di TV
                    if (rawStr.contains("IDLE") && rawStr.contains("FINISHED")) {
                        onTrackFinished?.invoke()
                    }
                } catch (e: EOFException) {
                    handleConnectionLoss("Koneksi TV ditutup oleh perangkat.")
                    break
                } catch (e: SocketException) {
                    handleConnectionLoss("Koneksi TV terputus.")
                    break
                } catch (e: Throwable) {
                    handleConnectionLoss("Gangguan koneksi TV: ${e.message}")
                    break
                }
            }
        }
    }

    private fun sendMessage(sourceId: String, destId: String, namespace: String, payloadJson: String) {
        val out = outStream ?: return
        val body = ByteArrayOutputStream()
        body.write(0x08)
        body.write(0x00)

        writeStringField(body, 2, sourceId)
        writeStringField(body, 3, destId)
        writeStringField(body, 4, namespace)

        body.write(0x28)
        body.write(0x00)

        writeStringField(body, 6, payloadJson)

        val bodyBytes = body.toByteArray()
        val envelope = ByteArrayOutputStream()
        val dos = DataOutputStream(envelope)
        dos.writeInt(bodyBytes.size)
        dos.write(bodyBytes)

        try {
            synchronized(this) {
                out.write(envelope.toByteArray())
                out.flush()
            }
        } catch (e: Throwable) {
            handleConnectionLoss("Gagal mengirim data ke TV: ${e.message}")
        }
    }

    private fun encodeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private fun writeStringField(out: ByteArrayOutputStream, fieldNum: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val tag = (fieldNum shl 3) or 2
        encodeVarint(out, tag)
        encodeVarint(out, bytes.size)
        out.write(bytes)
    }
}
