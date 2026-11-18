package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.desktop.repository.LastPlaybackStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BadSignalPlaybackTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newTestPlayer(): VlcAudioPlayer {
        return VlcAudioPlayer().apply {
            suppressNativeCallsForTest = true
            testIsAvailableOverride = true
        }
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, label: () -> String = { "Condition" }, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(15)
        }
        fail("${label()} was not met within ${timeoutMs}ms")
    }

    @Test
    fun testSwitchingSongWhileOfflineNeverPlaysPreviousSongOnPlay() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("offline_switch"))
        val isOnline = AtomicBoolean(true)
        val resolveCalls = AtomicInteger(0)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id ->
                resolveCalls.incrementAndGet()
                if (isOnline.get()) "https://example.test/stream/$id" else null
            },
            retryDelayProvider = { 30L },
            debounceDelayMs = 0L,
        )

        val songA = DesktopSong("songA_offline_test", "Song A", "Artist A", "3:20")
        val songB = DesktopSong("songB_offline_test", "Song B", "Artist B", "4:10")

        // 1. Putar lagu A saat sinyal normal
        manager.playSong(songA, listOf(songA, songB))
        waitUntil { player.hasPlayableMedia.value && player.currentTitle.value == "Song A" }
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)

        // 2. Sinyal terputus, lalu ganti ke lagu B
        isOnline.set(false)
        manager.playSong(songB, listOf(songA, songB))

        // Segera harus berhenti memutar lagu A dan membersihkan media lama
        assertEquals("songB_offline_test", manager.currentSong.value?.id)
        assertEquals("Song B", player.currentTitle.value)
        assertFalse("Old song must stop playing immediately", player.isPlaying.value)
        assertFalse("Previous media must not remain resumable", player.hasPlayableMedia.value)
        assertFalse("Direct player resume must reject stale media", player.resumeIfLoaded())

        // 3. Tekan Play berulang kali saat masih offline — tidak boleh memutar lagu A
        manager.togglePlayPause()
        manager.resume()
        delay(120)
        assertFalse(player.isPlaying.value)
        assertEquals("Song B", player.currentTitle.value)
        assertTrue("Manager must be buffering/waiting for connection", manager.isBuffering.value)

        // 4. Sinyal kembali pulih — harus otomatis memutar lagu B dari awal
        isOnline.set(true)
        waitUntil { player.hasPlayableMedia.value && player.currentTitle.value == "Song B" }
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)
        assertEquals(0L, player.currentPosition.value)
        waitUntil { !manager.isBuffering.value }
        assertFalse(manager.isBuffering.value)

        player.release()
    }

    @Test
    fun testMidSongNetworkErrorPreservesPositionAndResumesFromLastPosition() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("mid_song_drop"))
        val isOnline = AtomicBoolean(true)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id ->
                if (isOnline.get()) "https://example.test/stream/$id" else null
            },
            retryDelayProvider = { 30L },
            debounceDelayMs = 0L,
        )

        val songA = DesktopSong("songA_mid_drop", "Midnight Train", "Artist A", "3:30")
        manager.playSong(songA, listOf(songA))
        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        player.handleVlcLengthChanged(210_000L)
        player.handleVlcTimeChanged(84_500L)
        assertEquals(84_500L, player.currentPosition.value)

        // Sinyal terputus di tengah lagu -> VLC memicu error dan stopped
        isOnline.set(false)
        player.handleVlcError()
        player.handleVlcStopped()
        delay(80)

        assertFalse(player.isPlaying.value)
        assertEquals("Position must be preserved on network drop", 84_500L, player.currentPosition.value)
        assertTrue(manager.isBuffering.value)

        // Tekan Play saat masih mencoba menyambung — posisi tetap 84_500L dan menunggu sinyal
        manager.togglePlayPause()
        delay(80)
        assertFalse(player.isPlaying.value)
        assertEquals(84_500L, player.currentPosition.value)

        // Sinyal kembali -> stream di-resolve ulang dan berlanjut dari 84_500L
        isOnline.set(true)
        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)
        assertEquals(84_500L, player.currentPosition.value)

        player.release()
    }

    @Test
    fun testPrematureFinishedMidSongRecoversInsteadOfSkippingTrack() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("premature_finish"))
        val attempts = AtomicInteger(0)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id ->
                val n = attempts.incrementAndGet()
                "https://example.test/stream/$id?attempt=$n"
            },
            retryDelayProvider = { 20L },
            debounceDelayMs = 0L,
        )

        val song1 = DesktopSong("song1_premature", "Track One", "Artist 1", "3:30")
        val song2 = DesktopSong("song2_premature", "Track Two", "Artist 2", "3:30")
        manager.playSong(song1, listOf(song1, song2))

        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        player.handleVlcLengthChanged(210_000L)
        player.handleVlcTimeChanged(50_000L)

        // Simulasi stream terputus sebelum selesai sehingga LibVLC memicu finished di 50s dari 210s
        player.handleVlcFinished()
        delay(80)

        // Tidak boleh lompat ke song2; harus tetap di song1 dan mencoba ulang dari 50_000L
        assertEquals("song1_premature", manager.currentSong.value?.id)
        assertEquals(50_000L, player.currentPosition.value)
        assertTrue("Stream resolver should be retried for song1", attempts.get() >= 2)

        player.release()
    }

    @Test
    fun testGenuineFinishedNearEndAdvancesToNextSong() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("genuine_finish"))

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id -> "https://example.test/stream/$id" },
            retryDelayProvider = { 20L },
            debounceDelayMs = 0L,
        )

        val song1 = DesktopSong("song1_done", "Track One", "Artist 1", "3:30")
        val song2 = DesktopSong("song2_done", "Track Two", "Artist 2", "3:30")
        manager.playSong(song1, listOf(song1, song2))

        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        player.handleVlcLengthChanged(210_000L)
        player.handleVlcTimeChanged(207_000L)
        player.handleVlcFinished()

        waitUntil { manager.currentSong.value?.id == "song2_done" }
        assertEquals("song2_done", manager.currentSong.value?.id)

        player.release()
    }

    @Test
    fun testBufferingStopsIsPlayingAndRestoresOnCache100() = runBlocking {
        val player = newTestPlayer()
        val song = DesktopSong("buf_song", "Buffered Song", "Artist", "3:00")
        player.play("https://example.test/buf", song.title, song.artist, expectedDurationMs = 180_000L)
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)
        assertFalse(player.isBuffering.value)

        // Buffering di tengah lagu -> isPlaying harus berhenti sementara
        player.handleVlcBuffering(25f, nativeIsPlaying = false)
        assertFalse(player.isPlaying.value)
        assertTrue(player.isBuffering.value)

        // Buffering selesai (100%) -> isPlaying pulih kembali
        player.handleVlcBuffering(100f, nativeIsPlaying = true)
        assertTrue(player.isPlaying.value)
        assertFalse(player.isBuffering.value)

        player.release()
    }

    @Test
    fun testRestoredPositionDoesNotLeakToNewlyClickedSong() = runBlocking {
        val testDir = tempFolder.newFolder("restored_leak_test")
        val store = LastPlaybackStore(testDir)
        val songA = DesktopSong("restored_song_a", "Saved A", "Artist A", "3:30")
        val songB = DesktopSong("fresh_song_b", "Fresh B", "Artist B", "3:30")
        store.saveLastPlayback(songA, listOf(songA, songB), currentIndex = 0, positionMs = 45_000L)

        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id -> "https://example.test/stream/$id" },
            retryDelayProvider = { 20L },
            debounceDelayMs = 0L,
        )

        waitUntil { manager.currentSong.value?.id == "restored_song_a" }
        assertEquals(45_000L, player.currentPosition.value)

        // Pilih lagu baru -> harus mulai dari 0L, tidak mewarisi posisi 45_000L milik lagu A
        manager.playSong(songB, listOf(songA, songB))
        waitUntil { player.hasPlayableMedia.value && player.currentTitle.value == "Fresh B" }
        player.handleVlcPlaying()
        assertEquals(0L, player.currentPosition.value)

        player.release()
    }

    @Test
    fun testPauseWhileWaitingCancelsRetryAndResumeContinuesFromLastPosition() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("pause_while_waiting"))
        val isOnline = AtomicBoolean(true)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id -> if (isOnline.get()) "https://example.test/stream/$id" else null },
            retryDelayProvider = { 30L },
            debounceDelayMs = 0L,
        )

        val song = DesktopSong("song_pause_wait", "Waiting Song", "Artist", "3:30")
        manager.playSong(song, listOf(song))
        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        player.handleVlcLengthChanged(210_000L)
        player.handleVlcTimeChanged(42_000L)

        // Sinyal terputus -> masuk mode tunggu koneksi
        isOnline.set(false)
        player.handleVlcError()
        delay(60)
        assertTrue(manager.isBuffering.value)
        assertEquals(42_000L, player.currentPosition.value)

        // User menekan Pause saat sedang menunggu sinyal
        manager.pause()
        waitUntil { !manager.isBuffering.value }
        assertFalse(player.isPlaying.value)

        // Sinyal kembali pulih saat status userPaused -> tidak boleh auto-play sebelum user menekan Play
        isOnline.set(true)
        delay(100)
        assertFalse("Must stay paused when user explicitly paused", player.hasPlayableMedia.value)
        assertFalse(player.isPlaying.value)

        // User menekan Play -> harus resolve & lanjut dari 42_000L
        manager.togglePlayPause()
        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)
        assertEquals(42_000L, player.currentPosition.value)

        player.release()
    }

    @Test
    fun testSeekWhileWaitingForNetworkPreservesSeekedPosition() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("seek_while_waiting"))
        val isOnline = AtomicBoolean(false)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id -> if (isOnline.get()) "https://example.test/stream/$id" else null },
            retryDelayProvider = { 30L },
            debounceDelayMs = 0L,
        )

        val song = DesktopSong("song_seek_wait", "Seek Waiting", "Artist", "4:00")
        manager.playSong(song, listOf(song))
        delay(80)
        assertFalse(player.hasPlayableMedia.value)

        // User menggeser seekbar ke 95 detik saat stream masih menunggu koneksi
        manager.seekTo(95_000L)
        assertEquals(95_000L, player.currentPosition.value)

        // Sinyal kembali -> stream berputar mulai dari posisi 95_000L
        isOnline.set(true)
        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)
        assertEquals(95_000L, player.currentPosition.value)

        player.release()
    }

    @Test
    fun testPausedTrackDoesNotResurrectIsPlayingOnBuffering100OrTimeChanged() = runBlocking {
        val player = newTestPlayer()
        player.play("https://example.test/p", "Paused Track", "Artist", expectedDurationMs = 180_000L)
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)

        player.pause()
        assertFalse(player.isPlaying.value)

        // Simulasikan callback LibVLC yang terlambat datang saat posisi sedang di-pause
        player.handleVlcBuffering(100f, nativeIsPlaying = false)
        assertFalse("Buffering 100% while paused must not flip isPlaying back to true", player.isPlaying.value)

        player.handleVlcTimeChanged(25_000L)
        assertFalse("TimeChanged while paused must not flip isPlaying back to true", player.isPlaying.value)

        player.release()
    }

    @Test
    fun testPrematureFinishedWithUnknownDurationRecoversInsteadOfSkipping() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("unknown_dur_finish"))
        val attempts = AtomicInteger(0)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id ->
                attempts.incrementAndGet()
                "https://example.test/stream/$id"
            },
            retryDelayProvider = { 20L },
            debounceDelayMs = 0L,
        )

        val song1 = DesktopSong("s1_unk", "Unknown Dur", "Artist", "--:--")
        val song2 = DesktopSong("s2_unk", "Next Track", "Artist", "3:00")
        manager.playSong(song1, listOf(song1, song2))

        waitUntil { player.hasPlayableMedia.value }
        // Stream terputus dan memicu finished() sebelum playing() pernah terjadi
        player.handleVlcFinished()
        delay(80)

        assertEquals("Must stay on song1 and recover instead of advancing", "s1_unk", manager.currentSong.value?.id)
        assertTrue("Must retry resolving song1", attempts.get() >= 2)

        player.release()
    }

    @Test
    fun testTapPlayWhileActivelyResolvingDoesNotAbortInFlightExtraction() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("inflight_resolve"))
        val startedResolves = AtomicInteger(0)
        val allowFinish = AtomicBoolean(false)

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id ->
                startedResolves.incrementAndGet()
                while (!allowFinish.get()) {
                    delay(15)
                }
                "https://example.test/stream/$id"
            },
            retryDelayProvider = { 20L },
            debounceDelayMs = 0L,
        )

        val song = DesktopSong("slow_song", "Slow Signal Song", "Artist", "3:30")
        manager.playSong(song, listOf(song))
        waitUntil { startedResolves.get() == 1 && manager.isResolving.value }

        // User menekan Play saat request pertama masih berlangsung di jaringan lambat
        manager.togglePlayPause()
        manager.resume()
        delay(50)

        assertEquals("Tapping Play must not cancel and restart active extraction", 1, startedResolves.get())

        allowFinish.set(true)
        waitUntil { player.hasPlayableMedia.value }
        player.handleVlcPlaying()
        assertTrue(player.isPlaying.value)

        player.release()
    }

    @Test
    fun testStaleSongErrorDoesNotHijackNewlySelectedSong() = runBlocking {
        val player = newTestPlayer()
        val repo = DesktopMusicRepository()
        val store = LastPlaybackStore(tempFolder.newFolder("stale_error_hijack"))

        val manager = DesktopPlaybackManager(
            player = player,
            repository = repo,
            lastPlaybackStore = store,
            streamUrlResolver = { id -> "https://example.test/$id" },
            retryDelayProvider = { 20L },
            debounceDelayMs = 0L,
        )

        val songA = DesktopSong("hijack_a", "Song A", "Artist A", "3:00")
        val songB = DesktopSong("hijack_b", "Song B", "Artist B", "3:00")
        manager.playSong(songA, listOf(songA, songB))
        waitUntil { player.hasPlayableMedia.value && player.currentTitle.value == "Song A" }
        player.handleVlcPlaying()
        player.handleVlcTimeChanged(75_000L)

        // Simulasikan error dari lagu A tepat sebelum/bersamaan user pindah ke lagu B
        player.handleVlcError()
        manager.playSong(songB, listOf(songA, songB))

        waitUntil { player.hasPlayableMedia.value && player.currentTitle.value == "Song B" }
        player.handleVlcPlaying()
        assertEquals("Song B must start at 0L, not inherit Song A's 75_000L", 0L, player.currentPosition.value)

        player.release()
    }

    private fun buildSilentWav(durationSeconds: Int, sampleRate: Int = 8000): ByteArray {
        val numSamples = durationSeconds * sampleRate
        val dataSize = numSamples
        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM chunk size
        header.putShort(1) // PCM format
        header.putShort(1) // Mono
        header.putInt(sampleRate)
        header.putInt(sampleRate) // Byte rate
        header.putShort(1) // Block align
        header.putShort(8) // Bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())
        // 0x80 is silence in 8-bit unsigned PCM
        val silence = ByteArray(dataSize) { 0x80.toByte() }
        out.write(silence)
        return out.toByteArray()
    }

    @Test
    fun testRealLibVlcOfflineSwitchAndMidSongHttpDropRecovery() = runBlocking {
        val player = VlcAudioPlayer()
        Assume.assumeTrue("LibVLC native library must be present for live engine test", player.isAvailable)

        val wavBytes = buildSilentWav(durationSeconds = 60)
        val dropActiveStreams = AtomicBoolean(false)
        val server = ServerSocket(0)
        val port = server.localPort
        val serverRunning = AtomicBoolean(true)

        val acceptThread = thread(isDaemon = true, name = "TestAudioHttpServer") {
            while (serverRunning.get()) {
                val client: Socket = try {
                    server.accept()
                } catch (_: Throwable) {
                    break
                }
                thread(isDaemon = true) {
                    client.use { sock ->
                        try {
                            val reader = sock.getInputStream().bufferedReader()
                            var rangeStart = 0
                            while (true) {
                                val headerLine = reader.readLine() ?: break
                                if (headerLine.isEmpty()) break
                                if (headerLine.startsWith("Range:", ignoreCase = true)) {
                                    val m = Regex("bytes=(\\d+)-").find(headerLine)
                                    if (m != null) {
                                        rangeStart = m.groupValues[1].toIntOrNull()?.coerceIn(0, wavBytes.size) ?: 0
                                    }
                                }
                            }
                            val out = sock.getOutputStream()
                            val remaining = wavBytes.size - rangeStart
                            val status = if (rangeStart > 0) "206 Partial Content" else "200 OK"
                            val headers = buildString {
                                append("HTTP/1.1 $status\r\n")
                                append("Content-Type: audio/wav\r\n")
                                append("Accept-Ranges: bytes\r\n")
                                append("Content-Length: $remaining\r\n")
                                if (rangeStart > 0) {
                                    append("Content-Range: bytes $rangeStart-${wavBytes.size - 1}/${wavBytes.size}\r\n")
                                }
                                append("Connection: close\r\n\r\n")
                            }
                            out.write(headers.toByteArray(Charsets.US_ASCII))

                            val chunkSize = 1024
                            var pos = rangeStart
                            var sentTotal = 0
                            while (pos < wavBytes.size && serverRunning.get()) {
                                if (dropActiveStreams.get()) {
                                    // Simulasikan koneksi HTTP terputus mendadak di tengah lagu
                                    break
                                }
                                val toWrite = minOf(chunkSize, wavBytes.size - pos)
                                out.write(wavBytes, pos, toWrite)
                                out.flush()
                                pos += toWrite
                                sentTotal += toWrite
                                // Burst first 32KB (~4s audio) so LibVLC passes header probe & caching immediately
                                if (sentTotal >= 32 * 1024) {
                                    Thread.sleep(90)
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        }

        try {
            val repo = DesktopMusicRepository()
            val store = LastPlaybackStore(tempFolder.newFolder("real_vlc_test"))
            val isOnline = AtomicBoolean(true)

            val manager = DesktopPlaybackManager(
                player = player,
                repository = repo,
                lastPlaybackStore = store,
                streamUrlResolver = { id ->
                    if (isOnline.get()) "http://127.0.0.1:$port/$id.wav" else null
                },
                retryDelayProvider = { 50L },
                debounceDelayMs = 0L,
            )

            val songA = DesktopSong("real_a", "Real Song A", "Artist A", "1:00")
            val songB = DesktopSong("real_b", "Real Song B", "Artist B", "1:00")

            // 1. Putar lagu A dengan LibVLC nyata
            manager.playSong(songA, listOf(songA, songB))
            waitUntil(timeoutMs = 10_000L, label = { "Song A playing >= 1200ms (pos=${player.currentPosition.value}, playing=${player.isPlaying.value}, buf=${player.isBuffering.value}, status=${manager.statusMessage.value})" }) {
                player.isPlaying.value && player.currentPosition.value >= 1200L
            }
            assertEquals("Real Song A", player.currentTitle.value)

            // 2. Matikan sinyal lalu pindah ke lagu B -> LibVLC nyata harus berhenti & Play tidak memutar lagu A
            isOnline.set(false)
            manager.playSong(songB, listOf(songA, songB))
            delay(250)
            assertFalse("Real LibVLC must stop Song A when switching offline", player.isPlaying.value)
            assertFalse(player.hasPlayableMedia.value)
            manager.togglePlayPause()
            manager.resume()
            delay(250)
            assertFalse("Pressing Play offline must never replay Song A", player.isPlaying.value)
            assertEquals("Real Song B", player.currentTitle.value)

            // 3. Aktifkan koneksi lagi -> lagu B harus berputar normal di LibVLC nyata
            isOnline.set(true)
            waitUntil(timeoutMs = 10_000L, label = { "Song B playing (pos=${player.currentPosition.value}, playing=${player.isPlaying.value}, buf=${player.isBuffering.value}, title=${player.currentTitle.value})" }) {
                player.isPlaying.value && player.currentTitle.value == "Real Song B"
            }

            // 4. Uji drop HTTP di tengah lagu B (~2s)
            waitUntil(timeoutMs = 10_000L, label = { "Song B reached >= 1800ms (pos=${player.currentPosition.value})" }) {
                player.currentPosition.value >= 1800L
            }
            val posBeforeDrop = player.currentPosition.value
            isOnline.set(false)
            dropActiveStreams.set(true)

            // Tunggu sampai koneksi drop terdeteksi dan manager masuk mode tunggu sinyal
            waitUntil(timeoutMs = 18_000L, label = { "Mid-song drop entered waiting state (playing=${player.isPlaying.value}, waiting=${manager.isWaitingForNetwork.value}, pos=${player.currentPosition.value}, song=${manager.currentSong.value?.id}, status=${manager.statusMessage.value})" }) {
                !player.isPlaying.value && manager.isWaitingForNetwork.value
            }
            assertEquals("Must stay on Song B after mid-song drop", "real_b", manager.currentSong.value?.id)
            assertTrue("Preserved position must be >= ${posBeforeDrop - 500L}, got ${player.currentPosition.value}", player.currentPosition.value >= posBeforeDrop - 500L)

            // 5. Pulihkan koneksi penuh -> Song B harus lanjut terputar dari posisi terakhir
            dropActiveStreams.set(false)
            isOnline.set(true)
            waitUntil(timeoutMs = 12_000L, label = { "Song B resumed after recovery (playing=${player.isPlaying.value}, buf=${manager.isBuffering.value}, pos=${player.currentPosition.value})" }) {
                player.isPlaying.value && !manager.isBuffering.value
            }
            assertEquals("real_b", manager.currentSong.value?.id)
            assertTrue("Resumed position must remain near preserved position (got ${player.currentPosition.value}, beforeDrop=$posBeforeDrop)", player.currentPosition.value >= posBeforeDrop - 1000L)

            player.releaseBlocking(500L)
        } finally {
            serverRunning.set(false)
            try { server.close() } catch (_: Throwable) {}
            acceptThread.join(500)
        }
    }
}
