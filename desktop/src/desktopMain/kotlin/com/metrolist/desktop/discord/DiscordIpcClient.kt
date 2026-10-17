package com.metrolist.desktop.discord

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DiscordActivityPayload(
    val type: Int = 2, // 2 = Listening, 0 = Playing
    val details: String? = null,
    val state: String? = null,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
    val largeImage: String? = null,
    val largeText: String? = null,
    val smallImage: String? = null,
    val smallText: String? = null,
    val buttons: List<Pair<String, String>> = emptyList(),
)

class DiscordIpcClient(
    var clientId: String = DEFAULT_CLIENT_ID,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    companion object {
        const val DEFAULT_CLIENT_ID = "1560875581988671528"
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val OP_CLOSE = 2
        private const val OP_PING = 3
        private const val OP_PONG = 4
    }

    private var pipe: RandomAccessFile? = null
    private val mutex = Mutex()
    private var isConnected = false
    private var isRunning = false
    private var currentActivity: DiscordActivityPayload? = null
    private var connectionLoopJob: Job? = null

    var onStatusChanged: ((Boolean) -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        startConnectionLoop()
    }

    fun stop() {
        isRunning = false
        connectionLoopJob?.cancel()
        scope.launch {
            mutex.withLock {
                if (isConnected && pipe != null) {
                    try {
                        val pid = getPid()
                        val clearJson = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":$pid,\"activity\":null},\"nonce\":\"${UUID.randomUUID()}\"}"
                        writePacketInternal(pipe!!, OP_FRAME, clearJson)
                        // Tunggu konfirmasi singkat agar Discord selesai menghapus status
                        waitForData(pipe!!, 200L)
                        if (pipe!!.length() > 0) {
                            readPacketInternal(pipe!!)
                        }
                    } catch (_: Throwable) {}
                    closePipeInternal()
                    notifyStatus(false)
                }
            }
        }
    }

    fun updateClientId(newId: String) {
        if (newId.isBlank() || newId == clientId) return
        clientId = newId.trim()
        if (isRunning) {
            scope.launch {
                mutex.withLock { closePipeInternal() }
                notifyStatus(false)
            }
        }
    }

    fun setActivity(activity: DiscordActivityPayload?) {
        currentActivity = activity
        if (!isConnected) return
        scope.launch {
            sendActivityPayload(activity)
        }
    }

    fun clearActivity() {
        currentActivity = null
        if (!isConnected) return
        scope.launch {
            sendActivityPayload(null)
        }
    }

    private fun startConnectionLoop() {
        connectionLoopJob?.cancel()
        connectionLoopJob = scope.launch {
            while (isActive && isRunning) {
                if (!isConnected) {
                    val connected = connectPipe()
                    if (connected) {
                        currentActivity?.let { sendActivityPayload(it) }
                    }
                } else {
                    // Cek keaktifan pipe jika Discord ditutup
                    checkConnectionHealth()
                }

                // Polling interval: 2 detik saat belum terhubung, 3 detik saat terhubung
                delay(if (isConnected) 3000L else 2000L)
            }
        }
    }

    private suspend fun connectPipe(): Boolean = mutex.withLock {
        closePipeInternal()
        for (i in 0..9) {
            val pipePath = "\\\\.\\pipe\\discord-ipc-$i"
            try {
                val file = RandomAccessFile(pipePath, "rw")

                // Kirim Handshake
                val handshakeJson = buildJsonObject {
                    put("v", 1)
                    put("client_id", clientId)
                }.toString()

                writePacketInternal(file, OP_HANDSHAKE, handshakeJson)

                // Tunggu respon READY dari Discord (maks 1 detik)
                val hasData = waitForData(file, 1000L)
                if (hasData) {
                    val (op, _) = readPacketInternal(file)
                    if (op == OP_FRAME) {
                        pipe = file
                        isConnected = true
                        notifyStatus(true)
                        return true
                    }
                }
                file.close()
            } catch (_: Exception) {
                closePipeInternal()
            }
        }
        notifyStatus(false)
        return false
    }

    private suspend fun checkConnectionHealth() = mutex.withLock {
        val currentPipe = pipe ?: return@withLock
        if (!isConnected) return@withLock

        try {
            writePacketInternal(currentPipe, OP_PING, "{}")
            val hasData = waitForData(currentPipe, 500L)
            if (hasData) {
                readPacketInternal(currentPipe)
            }
        } catch (_: Throwable) {
            closePipeInternal()
            notifyStatus(false)
        }
    }

    private suspend fun sendActivityPayload(activity: DiscordActivityPayload?) {
        mutex.withLock {
            val currentPipe = pipe ?: return@withLock
            if (!isConnected) return@withLock

            try {
                val payload = buildActivityJson(activity)
                writePacketInternal(currentPipe, OP_FRAME, payload)

                // Bersihkan buffer respon dari Discord
                val hasData = waitForData(currentPipe, 500L)
                if (hasData) {
                    readPacketInternal(currentPipe)
                }
            } catch (_: Throwable) {
                closePipeInternal()
                notifyStatus(false)
            }
        }
    }

    private fun waitForData(file: RandomAccessFile, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                if (file.length() > 0) return true
            } catch (_: Throwable) {
                return false
            }
            Thread.sleep(25)
        }
        return false
    }

    private fun readPacketInternal(raf: RandomAccessFile): Pair<Int, String> {
        val header = ByteArray(8)
        raf.readFully(header)
        val headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val op = headerBuf.int
        val len = headerBuf.int

        val body = ByteArray(len)
        raf.readFully(body)
        val jsonStr = String(body, StandardCharsets.UTF_8)
        return Pair(op, jsonStr)
    }

    private fun buildActivityJson(activity: DiscordActivityPayload?): String {
        val pid = getPid()
        val root = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", pid)
                if (activity == null) {
                    put("activity", JsonNull)
                } else {
                    putJsonObject("activity") {
                        put("type", activity.type)
                        activity.details?.takeIf { it.isNotBlank() }?.let {
                            put("details", it.padToMinLen(2).take(128))
                        }
                        activity.state?.takeIf { it.isNotBlank() }?.let {
                            put("state", it.padToMinLen(2).take(128))
                        }

                        if (activity.startTimestamp != null || activity.endTimestamp != null) {
                            putJsonObject("timestamps") {
                                activity.startTimestamp?.let { put("start", it) }
                                activity.endTimestamp?.let { put("end", it) }
                            }
                        }

                        if (activity.largeImage != null || activity.smallImage != null) {
                            putJsonObject("assets") {
                                activity.largeImage?.let { put("large_image", it) }
                                activity.largeText?.let { put("large_text", it.take(128)) }
                                activity.smallImage?.let { put("small_image", it) }
                                activity.smallText?.let { put("small_text", it.take(128)) }
                            }
                        }

                        if (activity.buttons.isNotEmpty()) {
                            putJsonArray("buttons") {
                                for ((label, url) in activity.buttons.take(2)) {
                                    addJsonObject {
                                        put("label", label.take(32))
                                        put("url", url.take(512))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            put("nonce", UUID.randomUUID().toString())
        }

        return root.toString()
    }

    private fun writePacketInternal(raf: RandomAccessFile, op: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(op)
        buf.putInt(bytes.size)
        buf.put(bytes)
        raf.write(buf.array())
    }

    private fun closePipeInternal() {
        isConnected = false
        try {
            pipe?.close()
        } catch (_: Throwable) {}
        pipe = null
    }

    private fun notifyStatus(connected: Boolean) {
        try {
            onStatusChanged?.invoke(connected)
        } catch (_: Throwable) {}
    }

    private fun getPid(): Int {
        return try {
            ProcessHandle.current().pid().toInt()
        } catch (_: Throwable) {
            1000
        }
    }

    private fun String.padToMinLen(minLen: Int): String {
        return if (this.length < minLen) this.padEnd(minLen, ' ') else this
    }
}
