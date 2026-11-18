package com.metrolist.desktop.discord

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.desktop.repository.LastPlaybackStore
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class DiscordRpcTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDefaultClientIdMatchesRegisteredEchoApp() {
        assertEquals("1560875581988671528", DiscordIpcClient.DEFAULT_CLIENT_ID)
    }

    @Test
    fun testPayloadStructureForListeningState() {
        val now = System.currentTimeMillis()
        val durationMs = 210000L // 3m 30s
        val positionMs = 30000L // 30s
        val start = now - positionMs
        val end = start + durationMs

        val payload = DiscordActivityPayload(
            type = 2,
            details = "Starboy",
            state = "The Weeknd, Daft Punk",
            startTimestamp = start,
            endTimestamp = end,
            largeImage = "https://lh3.googleusercontent.com/test",
            largeText = "Starboy Album",
            smallImage = DesktopDiscordRpc.DEFAULT_ICON_URL,
            smallText = "Echo Desktop",
            buttons = listOf(
                "Dengarkan di YouTube Music" to "https://music.youtube.com/watch?v=d_HlPboLRL8"
            )
        )

        assertEquals(2, payload.type)
        assertEquals("Starboy", payload.details)
        assertEquals("The Weeknd, Daft Punk", payload.state)
        assertEquals(start, payload.startTimestamp)
        assertEquals(end, payload.endTimestamp)
        assertEquals(1, payload.buttons.size)
        assertEquals("Dengarkan di YouTube Music", payload.buttons[0].first)
    }

    @Test
    fun testPausedStateOmitsEndTimestamp() {
        val payload = DiscordActivityPayload(
            type = 2,
            details = "Starboy",
            state = "The Weeknd (Dijeda)",
            startTimestamp = null,
            endTimestamp = null,
            smallText = "Dijeda"
        )

        assertNull("Paused state must not have endTimestamp", payload.endTimestamp)
        assertNull("Paused state must not have startTimestamp", payload.startTimestamp)
        assertEquals("Dijeda", payload.smallText)
    }

    @Test
    fun testDurationParsingFallback() {
        assertEquals(186000L, DesktopDiscordRpc.parseDurationText("3:06"))
        assertEquals(258000L, DesktopDiscordRpc.parseDurationText("04:18"))
        assertEquals(3858000L, DesktopDiscordRpc.parseDurationText("1:04:18"))
        assertEquals(0L, DesktopDiscordRpc.parseDurationText(""))
        assertEquals(0L, DesktopDiscordRpc.parseDurationText(null))
        assertEquals(0L, DesktopDiscordRpc.parseDurationText("--:--"))
    }

    @Test
    fun testLiveIpcClientLifecycle() = runBlocking {
        val client = DiscordIpcClient()
        var isConnected = false
        client.onStatusChanged = { connected ->
            isConnected = connected
        }

        client.start()
        // Tunggu koneksi terbentuk jika Discord sedang berjalan
        delay(1200)

        // Set activity pertama
        client.setActivity(
            DiscordActivityPayload(
                type = 2,
                details = "Lagu Uji Satu",
                state = "by Artis Uji",
                largeImage = DesktopDiscordRpc.DEFAULT_ICON_URL,
                largeText = "Album Uji",
            )
        )
        delay(1000)

        // Set activity kedua (simulasi lagu berganti secara real-time)
        client.setActivity(
            DiscordActivityPayload(
                type = 2,
                details = "Lagu Uji Dua",
                state = "by Artis Dua",
                largeImage = DesktopDiscordRpc.DEFAULT_ICON_URL,
                largeText = "Album Dua",
            )
        )
        delay(1000)

        // Clear activity (simulasi toggle OFF)
        client.clearActivity()
        delay(500)

        client.stop()
        delay(300)
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(15)
        }
        fail("Condition was not met within ${timeoutMs}ms")
    }

    @Test
    fun testBuildActivityPayloadForPausedBufferingAndPlayingStates() {
        val player = VlcAudioPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("rpc_payload_store"))
        val manager = DesktopPlaybackManager(player, repo, lastPlaybackStore = store)
        val rpc = DesktopDiscordRpc(player, manager, initialEnabled = false)

        val song = DesktopSong("rpc_1", "Blinding Lights", "The Weeknd", "3:20", album = "After Hours")

        // 1. Paused state must omit both timestamps so Discord stops counting
        val pausedPayload = rpc.buildActivityPayload(
            song = song,
            isPlaying = false,
            positionMs = 45_000L,
            durationMs = 200_000L,
            isBuffering = false,
            nowMs = 1_000_000L,
        )
        assertNotNull(pausedPayload)
        assertEquals("Blinding Lights", pausedPayload!!.details)
        assertTrue(pausedPayload.state!!.contains("Paused"))
        assertEquals("Paused", pausedPayload.smallText)
        assertNull("Paused state must omit startTimestamp", pausedPayload.startTimestamp)
        assertNull("Paused state must omit endTimestamp", pausedPayload.endTimestamp)

        // 2. Buffering / waiting for signal must omit timestamps EVEN IF isPlaying flag was true
        val bufferingPayload = rpc.buildActivityPayload(
            song = song,
            isPlaying = true,
            positionMs = 45_000L,
            durationMs = 200_000L,
            isBuffering = true,
            nowMs = 1_000_000L,
        )
        assertNotNull(bufferingPayload)
        assertEquals("Blinding Lights", bufferingPayload!!.details)
        assertTrue(bufferingPayload.state!!.contains("Buffering"))
        assertEquals("Buffering", bufferingPayload.smallText)
        assertNull("Buffering state must omit startTimestamp", bufferingPayload.startTimestamp)
        assertNull("Buffering state must omit endTimestamp", bufferingPayload.endTimestamp)

        // 3. Playing state computes accurate start/end timestamps using fallback durationText when durationMs == 0
        val playingPayload = rpc.buildActivityPayload(
            song = song,
            isPlaying = true,
            positionMs = 60_000L,
            durationMs = 0L,
            isBuffering = false,
            nowMs = 1_000_000L,
        )
        assertNotNull(playingPayload)
        assertEquals("Echo Desktop", playingPayload!!.smallText)
        assertEquals(1_000_000L - 60_000L, playingPayload.startTimestamp)
        assertEquals((1_000_000L - 60_000L) + 200_000L, playingPayload.endTimestamp)

        rpc.close()
        player.release()
    }

    @Test
    fun testClearActivityJsonContainsNullActivity() {
        val client = DiscordIpcClient()
        val json = client.buildActivityJson(null)
        assertTrue("Clear payload must send SET_ACTIVITY", json.contains("\"cmd\":\"SET_ACTIVITY\""))
        assertTrue("Clear payload must set activity to null", json.contains("\"activity\":null"))
    }

    @Test
    fun testLiveDiscordRpcSyncAcrossBufferingPlayPauseSeekAndClose() = runBlocking {
        val player = VlcAudioPlayer().apply {
            suppressNativeCallsForTest = true
            testIsAvailableOverride = true
        }
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("rpc_live_sync"))
        val isOnline = AtomicBoolean(false)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id -> if (isOnline.get()) "https://example.test/$id" else null },
            retryDelayProvider = { 30L },
            debounceDelayMs = 0L,
        )
        val rpc = DesktopDiscordRpc(player, manager, initialEnabled = true)

        val song1 = DesktopSong("sync_1", "After Hours", "The Weeknd", "6:01")

        // 1. Putar saat offline -> Discord harus menampilkan judul lagu dengan status Buffering & timer berhenti
        manager.playSong(song1, listOf(song1))
        waitUntil { rpc.lastSentActivity.value?.details == "After Hours" }
        val waitingActivity = rpc.lastSentActivity.value!!
        assertEquals("Buffering", waitingActivity.smallText)
        assertNull(waitingActivity.startTimestamp)
        assertNull(waitingActivity.endTimestamp)

        // 2. Sinyal pulih dan benar-benar berputar -> timer Discord harus aktif akurat
        isOnline.set(true)
        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        player.handleVlcLengthChanged(361_000L)
        waitUntil { rpc.lastSentActivity.value?.startTimestamp != null }
        val playingActivity = rpc.lastSentActivity.value!!
        assertEquals("After Hours", playingActivity.details)
        assertEquals("Echo Desktop", playingActivity.smallText)
        assertNotNull(playingActivity.startTimestamp)
        assertNotNull(playingActivity.endTimestamp)

        // 3. Buffering di tengah lagu -> timer Discord harus berhenti
        player.handleVlcBuffering(15f, nativeIsPlaying = false)
        waitUntil { rpc.lastSentActivity.value?.smallText == "Buffering" }
        assertNull(rpc.lastSentActivity.value!!.startTimestamp)
        assertNull(rpc.lastSentActivity.value!!.endTimestamp)

        // 4. Buffering selesai -> timer kembali berjalan, lalu Seek ke 2:00 (120_000 ms)
        player.handleVlcBuffering(100f, nativeIsPlaying = true)
        waitUntil { rpc.lastSentActivity.value?.startTimestamp != null }
        val beforeSeekStart = rpc.lastSentActivity.value!!.startTimestamp!!

        manager.seekTo(120_000L)
        waitUntil {
            val s = rpc.lastSentActivity.value?.startTimestamp
            s != null && kotlin.math.abs((System.currentTimeMillis() - s) - 120_000L) < 2000L
        }
        val afterSeekStart = rpc.lastSentActivity.value!!.startTimestamp!!
        assertTrue("Seek forward must shift startTimestamp earlier", afterSeekStart < beforeSeekStart - 100_000L)

        // 5. Pause -> timer Discord harus berhenti
        manager.pause()
        waitUntil { rpc.lastSentActivity.value?.smallText == "Paused" }
        assertNull(rpc.lastSentActivity.value!!.startTimestamp)
        assertNull(rpc.lastSentActivity.value!!.endTimestamp)

        // 6. Close aplikasi -> aktivitas langsung dibersihkan
        rpc.close()
        assertNull(rpc.lastSentActivity.value)
        assertFalse(rpc.isEnabled.value)

        player.release()
    }

    @Test
    fun testStopBlockingCompletesPromptlyAndClearsConnection() {
        val client = DiscordIpcClient(clientId = "000000000000000000")
        var latestStatus: Boolean? = null
        client.onStatusChanged = { latestStatus = it }
        client.start()

        val startMs = System.currentTimeMillis()
        client.stopBlocking(timeoutMs = 500L)
        val elapsedMs = System.currentTimeMillis() - startMs

        assertTrue("stopBlocking must finish promptly (took ${elapsedMs}ms)", elapsedMs < 1500L)
        assertEquals(false, latestStatus)
    }

    @Test
    fun testRealWindowsNamedPipeHandshakeSyncAndClearOnClose() = runBlocking {
        Assume.assumeTrue(
            "Windows Named Pipe test only runs on Windows",
            System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true
        )

        val pipePath = "\\\\.\\pipe\\echo-test-ipc-${UUID.randomUUID()}"
        val kernel32 = Kernel32.INSTANCE
        val pipeHandle: HANDLE = kernel32.CreateNamedPipe(
            pipePath,
            WinBase.PIPE_ACCESS_DUPLEX,
            WinBase.PIPE_TYPE_BYTE or WinBase.PIPE_READMODE_BYTE or WinBase.PIPE_WAIT,
            1,
            65536,
            65536,
            0,
            null
        )
        assertNotEquals("CreateNamedPipe must succeed", WinBase.INVALID_HANDLE_VALUE, pipeHandle)

        val receivedFrames = CopyOnWriteArrayList<Pair<Int, String>>()
        val closePacketLatch = CountDownLatch(1)
        val serverReadyLatch = CountDownLatch(1)
        val serverRunning = AtomicBoolean(true)

        fun readExact(count: Int): ByteArray? {
            val out = ByteArray(count)
            var offset = 0
            while (offset < count && serverRunning.get()) {
                val chunk = ByteArray(count - offset)
                val readRef = IntByReference()
                val ok = kernel32.ReadFile(pipeHandle, chunk, chunk.size, readRef, null)
                val n = readRef.value
                if (!ok || n <= 0) return null
                System.arraycopy(chunk, 0, out, offset, n)
                offset += n
            }
            return if (offset == count) out else null
        }

        fun writePacket(op: Int, json: String) {
            val payload = json.toByteArray(StandardCharsets.UTF_8)
            val buf = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(op)
            buf.putInt(payload.size)
            buf.put(payload)
            val bytes = buf.array()
            val writtenRef = IntByReference()
            kernel32.WriteFile(pipeHandle, bytes, bytes.size, writtenRef, null)
            kernel32.FlushFileBuffers(pipeHandle)
        }

        val serverThread = thread(isDaemon = true, name = "MockDiscordNamedPipeServer") {
            try {
                serverReadyLatch.countDown()
                val connected = kernel32.ConnectNamedPipe(pipeHandle, null) ||
                    kernel32.GetLastError() == 535 // ERROR_PIPE_CONNECTED
                if (!connected) return@thread

                while (serverRunning.get()) {
                    val header = readExact(8) ?: break
                    val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    val op = hb.int
                    val len = hb.int
                    if (len < 0 || len > 1_048_576) break
                    val bodyBytes = if (len > 0) (readExact(len) ?: break) else ByteArray(0)
                    val body = String(bodyBytes, StandardCharsets.UTF_8)
                    receivedFrames.add(op to body)

                    when (op) {
                        0 -> writePacket(1, """{"cmd":"DISPATCH","evt":"READY","data":{}}""")
                        1 -> writePacket(1, """{"cmd":"SET_ACTIVITY","evt":null}""")
                        3 -> writePacket(4, "{}")
                        2 -> {
                            closePacketLatch.countDown()
                            break
                        }
                    }
                }
            } finally {
                try { kernel32.DisconnectNamedPipe(pipeHandle) } catch (_: Throwable) {}
                try { kernel32.CloseHandle(pipeHandle) } catch (_: Throwable) {}
            }
        }

        assertTrue(serverReadyLatch.await(2, TimeUnit.SECONDS))

        val player = VlcAudioPlayer().apply {
            suppressNativeCallsForTest = true
            testIsAvailableOverride = true
        }
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("named_pipe_rpc_store"))
        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id -> "https://example.test/$id" },
            retryDelayProvider = { 20L },
            debounceDelayMs = 0L,
        )
        val rpc = DesktopDiscordRpc(player, manager, initialEnabled = false)
        rpc.ipcClient.pipePathsOverride = listOf(pipePath)

        try {
            rpc.start()
            waitUntil(timeoutMs = 5000L) { rpc.isConnected.value }

            val song = DesktopSong("pipe_song", "Pipe Melody", "Pipe Artist", "3:20")
            manager.playSong(song, listOf(song))
            waitUntil(timeoutMs = 3000L) { player.hasPlayableMedia.value }
            player.handleVlcPlaying()
            player.handleVlcLengthChanged(200_000L)

            waitUntil(timeoutMs = 3000L) {
                receivedFrames.any { (op, body) ->
                    op == 1 && body.contains("\"details\":\"Pipe Melody\"") && body.contains("\"timestamps\"")
                }
            }

            manager.pause()
            waitUntil(timeoutMs = 3000L) {
                receivedFrames.any { (op, body) ->
                    op == 1 && body.contains("Paused") && !body.contains("\"timestamps\"")
                }
            }

            rpc.close()
            assertTrue("OP_CLOSE must be delivered over named pipe on close()", closePacketLatch.await(2, TimeUnit.SECONDS))
            val clearIndex = receivedFrames.indexOfLast { (op, body) ->
                op == 1 && body.contains("\"activity\":null")
            }
            val closeIndex = receivedFrames.indexOfLast { (op, _) -> op == 2 }
            assertTrue("SET_ACTIVITY(null) must be sent over pipe on close", clearIndex >= 0)
            assertTrue("OP_CLOSE must follow SET_ACTIVITY(null)", closeIndex > clearIndex)
        } finally {
            serverRunning.set(false)
            rpc.close()
            player.release()
            serverThread.join(1000)
        }
    }
}
