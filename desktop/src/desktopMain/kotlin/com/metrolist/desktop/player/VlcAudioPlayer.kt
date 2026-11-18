package com.metrolist.desktop.player

import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

class VlcAudioPlayer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val playerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "VlcAudioPlayer-Worker").apply { isDaemon = true }
    }
    private val playerDispatcher = playerExecutor.asCoroutineDispatcher()

    private var playerComponent: AudioPlayerComponent? = null
    private val nativeDiscovered: Boolean
    internal var testIsAvailableOverride: Boolean? = null
    internal var suppressNativeCallsForTest: Boolean = false
    val isAvailable: Boolean
        get() = testIsAvailableOverride ?: nativeDiscovered

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _hasPlayableMedia = MutableStateFlow(false)
    val hasPlayableMedia: StateFlow<Boolean> = _hasPlayableMedia.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow("")
    val currentArtist: StateFlow<String> = _currentArtist.asStateFlow()

    private val _currentThumbnailUrl = MutableStateFlow<String?>(null)
    val currentThumbnailUrl: StateFlow<String?> = _currentThumbnailUrl.asStateFlow()

    private val _seekSequence = MutableStateFlow(0L)
    val seekSequence: StateFlow<Long> = _seekSequence.asStateFlow()

    @Volatile
    var isReleased: Boolean = false
        private set

    @Volatile
    private var lastValidPositionMs: Long = 0L

    @Volatile
    private var expectedDurationMs: Long = 0L

    @Volatile
    private var pendingSeekOnPlayingMs: Long = 0L

    @Volatile
    private var hasStartedCurrentMedia: Boolean = false

    val hasStartedMedia: Boolean
        get() = hasStartedCurrentMedia

    @Volatile
    private var isUserPaused: Boolean = false

    @Volatile
    private var isExplicitStop: Boolean = true

    @Volatile
    private var mediaSessionToken: Long = 0L

    @Volatile
    private var activeNativeToken: Long = 0L

    @Volatile
    private var seekGuardTargetMs: Long = -1L

    @Volatile
    private var seekGuardDeadlineMs: Long = 0L

    internal val currentSessionToken: Long
        get() = mediaSessionToken

    var onPlaybackFinished: (() -> Unit)? = null
    var onPlaybackError: ((lastPositionMs: Long) -> Unit)? = null
    var onPlaybackStarted: (() -> Unit)? = null

    init {
        val discovered = try {
            NativeDiscovery().discover()
        } catch (e: Throwable) {
            System.err.println("VLC discovery exception: ${e.message}")
            false
        }

        nativeDiscovered = discovered
        if (discovered) {
            try {
                val mediaPlayerFactory = uk.co.caprica.vlcj.factory.MediaPlayerFactory(
                    "--no-video",
                    "--no-spu",
                    "--no-osd",
                    "--no-stats",
                    "--no-sub-autodetect-file",
                    "--network-caching=2000"
                )
                val component = AudioPlayerComponent(mediaPlayerFactory)
                playerComponent = component

                component.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                    override fun opening(mediaPlayer: MediaPlayer?) {
                        handleVlcOpening()
                    }

                    override fun buffering(mediaPlayer: MediaPlayer?, newCache: Float) {
                        val nativePlaying = try {
                            mediaPlayer?.status()?.isPlaying == true
                        } catch (_: Throwable) {
                            false
                        }
                        handleVlcBuffering(newCache, nativePlaying)
                    }

                    override fun playing(mediaPlayer: MediaPlayer?) {
                        handleVlcPlaying()
                    }

                    override fun paused(mediaPlayer: MediaPlayer?) {
                        handleVlcPaused()
                    }

                    override fun stopped(mediaPlayer: MediaPlayer?) {
                        handleVlcStopped()
                    }

                    override fun finished(mediaPlayer: MediaPlayer?) {
                        handleVlcFinished()
                    }

                    override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
                        handleVlcLengthChanged(newLength)
                    }

                    override fun timeChanged(mediaPlayer: MediaPlayer?, newTime: Long) {
                        handleVlcTimeChanged(newTime)
                    }

                    override fun error(mediaPlayer: MediaPlayer?) {
                        handleVlcError()
                    }
                })
                println("VLCJ Audio Engine initialized successfully with LibVLC!")
            } catch (e: Throwable) {
                System.err.println("Failed to initialize AudioPlayerComponent: ${e.message}")
            }
        } else {
            System.err.println("LibVLC was not found on this system.")
        }
    }

    internal fun handleVlcOpening() {
        if (activeNativeToken != mediaSessionToken || !_hasPlayableMedia.value || isExplicitStop) return
        if (isUserPaused) return
        _isBuffering.value = true
        _isPlaying.value = false
    }

    internal fun handleVlcBuffering(newCache: Float, nativeIsPlaying: Boolean = false) {
        if (activeNativeToken != mediaSessionToken || !_hasPlayableMedia.value || isExplicitStop) return
        if (isUserPaused) {
            _isPlaying.value = false
            _isBuffering.value = false
            return
        }
        if (newCache < 99.5f) {
            _isBuffering.value = true
            // Saat buffering di tengah lagu atau awal lagu, hentikan status playing agar durasi Discord ikut berhenti
            _isPlaying.value = false
        } else {
            _isBuffering.value = false
            if (nativeIsPlaying) {
                _isPlaying.value = true
            }
        }
    }

    internal fun handleVlcPlaying() {
        if (activeNativeToken != mediaSessionToken || !_hasPlayableMedia.value || isExplicitStop) return
        val token = mediaSessionToken
        val seekTarget = pendingSeekOnPlayingMs
        pendingSeekOnPlayingMs = 0L
        hasStartedCurrentMedia = true

        if (isUserPaused) {
            _isBuffering.value = false
            _isPlaying.value = false
            if (!isReleased && !suppressNativeCallsForTest) {
                scope.launch(playerDispatcher) {
                    if (mediaSessionToken == token && !isReleased && isUserPaused) {
                        playerComponent?.mediaPlayer()?.controls()?.pause()
                    }
                }
            }
            return
        }

        if (seekTarget > 1000L && !isReleased) {
            _currentPosition.value = seekTarget
            lastValidPositionMs = seekTarget
            seekGuardTargetMs = seekTarget
            seekGuardDeadlineMs = System.currentTimeMillis() + 3000L
            if (!suppressNativeCallsForTest) {
                scope.launch(playerDispatcher) {
                    if (mediaSessionToken != token || !_hasPlayableMedia.value || isReleased) return@launch
                    val actualBefore = try {
                        playerComponent?.mediaPlayer()?.status()?.time() ?: -1L
                    } catch (_: Throwable) {
                        -1L
                    }
                    if (actualBefore !in (seekTarget - 2500L)..(seekTarget + 2500L)) {
                        playerComponent?.mediaPlayer()?.controls()?.setTime(seekTarget)
                    }
                    // Verifikasi ulang seek jika demuxer butuh beberapa ratus ms setelah event playing
                    delay(350)
                    if (mediaSessionToken == token && _hasPlayableMedia.value && !isReleased && !isUserPaused) {
                        val actualTime = try {
                            playerComponent?.mediaPlayer()?.status()?.time() ?: -1L
                        } catch (_: Throwable) {
                            -1L
                        }
                        if (actualTime in 0L until (seekTarget - 3000L)) {
                            playerComponent?.mediaPlayer()?.controls()?.setTime(seekTarget)
                        }
                    }
                }
            }
        }

        _isBuffering.value = false
        _isPlaying.value = true
        onPlaybackStarted?.invoke()
    }

    internal fun handleVlcPaused() {
        if (activeNativeToken != mediaSessionToken) return
        _isPlaying.value = false
        _isBuffering.value = false
    }

    internal fun handleVlcStopped() {
        if (activeNativeToken != mediaSessionToken) return
        _isPlaying.value = false
        _isBuffering.value = false
        if (isExplicitStop) {
            return
        }
        // Jika berhenti mendadak (misal koneksi terputus tanpa event error atau setelah error),
        // pertahankan posisi terakhir dan picu pemulihan error alih-alih me-reset ke 0!
        if (_hasPlayableMedia.value) {
            _hasPlayableMedia.value = false
            _currentPosition.value = lastValidPositionMs
            onPlaybackError?.invoke(lastValidPositionMs)
        }
    }

    internal fun handleVlcFinished() {
        if (activeNativeToken != mediaSessionToken) return
        _isPlaying.value = false
        _isBuffering.value = false
        if (!_hasPlayableMedia.value || isExplicitStop) return

        val actualDur = _duration.value
        val refDur = if (actualDur > 0L) actualDur else expectedDurationMs
        val isPrematureDrop = !hasStartedCurrentMedia ||
            (refDur > 15_000L && lastValidPositionMs < refDur - 8_000L)

        if (isPrematureDrop) {
            _hasPlayableMedia.value = false
            _currentPosition.value = lastValidPositionMs
            onPlaybackError?.invoke(lastValidPositionMs)
            return
        }

        _hasPlayableMedia.value = false
        _currentPosition.value = 0L
        lastValidPositionMs = 0L
        onPlaybackFinished?.invoke()
    }

    internal fun handleVlcLengthChanged(newLength: Long) {
        if (activeNativeToken != mediaSessionToken || !_hasPlayableMedia.value || isExplicitStop) return
        if (newLength > 0) {
            _duration.value = newLength
        }
    }

    internal fun handleVlcTimeChanged(newTime: Long) {
        if (activeNativeToken != mediaSessionToken || !_hasPlayableMedia.value || isExplicitStop) return
        if (newTime < 0) return

        val guardTarget = seekGuardTargetMs
        if (guardTarget >= 0L) {
            val now = System.currentTimeMillis()
            if (now <= seekGuardDeadlineMs && kotlin.math.abs(newTime - guardTarget) > 2500L) {
                return
            }
            seekGuardTargetMs = -1L
        }

        _currentPosition.value = newTime
        lastValidPositionMs = newTime
        if (_isBuffering.value) {
            _isBuffering.value = false
        }
        if (!isUserPaused && !_isPlaying.value && hasStartedCurrentMedia) {
            _isPlaying.value = true
        }
    }

    internal fun handleVlcError() {
        if (activeNativeToken != mediaSessionToken) return
        System.err.println("VLC encountered an error during playback")
        _isPlaying.value = false
        _isBuffering.value = false
        if (_hasPlayableMedia.value && !isExplicitStop) {
            _hasPlayableMedia.value = false
            _currentPosition.value = lastValidPositionMs
            onPlaybackError?.invoke(lastValidPositionMs)
        }
    }

    fun setMetadata(title: String, artist: String, thumbnailUrl: String? = null, initialPositionMs: Long = 0L) {
        _currentTitle.value = title
        _currentArtist.value = artist
        _currentThumbnailUrl.value = thumbnailUrl
        _currentPosition.value = initialPositionMs
        lastValidPositionMs = initialPositionMs
    }

    fun setBuffering(buffering: Boolean) {
        _isBuffering.value = buffering
        if (buffering) {
            _isPlaying.value = false
        }
    }

    fun updatePositionOnly(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        _currentPosition.value = safePos
        lastValidPositionMs = safePos
        _seekSequence.value += 1L
    }

    /**
     * Dipanggil segera saat berpindah ke lagu baru sebelum URL stream selesai di-resolve.
     * Menghentikan pemutaran lagu sebelumnya dan menghapus hak resume media lama agar
     * menekan tombol Play saat offline tidak pernah memutar ulang lagu sebelumnya.
     */
    fun prepareForNewTrack(
        title: String,
        artist: String,
        thumbnailUrl: String? = null,
        initialPositionMs: Long = 0L,
        expectedDurationMs: Long = 0L,
    ) {
        mediaSessionToken += 1L
        activeNativeToken = 0L
        isUserPaused = false
        isExplicitStop = true
        _hasPlayableMedia.value = false
        hasStartedCurrentMedia = false
        pendingSeekOnPlayingMs = 0L
        seekGuardTargetMs = -1L
        this.expectedDurationMs = expectedDurationMs

        _isPlaying.value = false
        _isBuffering.value = true
        _currentTitle.value = title
        _currentArtist.value = artist
        _currentThumbnailUrl.value = thumbnailUrl
        _duration.value = 0L
        val safePos = initialPositionMs.coerceAtLeast(0L)
        _currentPosition.value = safePos
        lastValidPositionMs = safePos

        if (!isReleased && !suppressNativeCallsForTest) {
            scope.launch(playerDispatcher) {
                try {
                    playerComponent?.mediaPlayer()?.controls()?.stop()
                } catch (_: Throwable) {}
            }
        }
    }

    fun play(
        url: String,
        title: String,
        artist: String,
        thumbnailUrl: String? = null,
        startPositionMs: Long = 0L,
        expectedDurationMs: Long = 0L,
    ) {
        val token = mediaSessionToken + 1L
        mediaSessionToken = token
        isUserPaused = false
        isExplicitStop = false
        _hasPlayableMedia.value = true
        hasStartedCurrentMedia = false
        this.expectedDurationMs = expectedDurationMs

        val safePos = startPositionMs.coerceAtLeast(0L)
        pendingSeekOnPlayingMs = if (safePos > 1000L) safePos else 0L
        if (safePos > 1000L) {
            seekGuardTargetMs = safePos
            seekGuardDeadlineMs = System.currentTimeMillis() + 3000L
        } else {
            seekGuardTargetMs = -1L
        }

        _isPlaying.value = false
        _isBuffering.value = true
        _currentTitle.value = title
        _currentArtist.value = artist
        _currentThumbnailUrl.value = thumbnailUrl
        _duration.value = 0L
        _currentPosition.value = safePos
        lastValidPositionMs = safePos

        if (suppressNativeCallsForTest) {
            activeNativeToken = token
        } else if (!isReleased) {
            activeNativeToken = 0L
            scope.launch(playerDispatcher) {
                if (mediaSessionToken != token || isReleased || isExplicitStop) return@launch
                try {
                    val mp = playerComponent?.mediaPlayer()
                    if (mp != null) {
                        if (mp.status().isPlaying) {
                            mp.controls().stop()
                        }
                        if (mediaSessionToken != token || isReleased || isExplicitStop) return@launch
                        activeNativeToken = token
                        if (safePos > 1000L) {
                            val startSec = String.format(Locale.US, "%.3f", safePos / 1000.0)
                            mp.media().play(url, ":start-time=$startSec")
                        } else {
                            mp.media().play(url)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        isUserPaused = true
        _isPlaying.value = false
        _isBuffering.value = false
        if (!isReleased && _hasPlayableMedia.value && !suppressNativeCallsForTest) {
            scope.launch(playerDispatcher) {
                playerComponent?.mediaPlayer()?.controls()?.pause()
            }
        }
    }

    fun resumeIfLoaded(): Boolean {
        if (!_hasPlayableMedia.value || isExplicitStop || isReleased) {
            return false
        }
        isUserPaused = false
        if (!suppressNativeCallsForTest) {
            scope.launch(playerDispatcher) {
                playerComponent?.mediaPlayer()?.controls()?.play()
            }
        }
        return true
    }

    fun resume() {
        resumeIfLoaded()
    }

    fun stop() {
        mediaSessionToken += 1L
        activeNativeToken = 0L
        isUserPaused = false
        isExplicitStop = true
        _hasPlayableMedia.value = false
        hasStartedCurrentMedia = false
        pendingSeekOnPlayingMs = 0L
        seekGuardTargetMs = -1L
        _isPlaying.value = false
        _isBuffering.value = false
        _currentPosition.value = 0L
        lastValidPositionMs = 0L
        if (!isReleased && !suppressNativeCallsForTest) {
            scope.launch(playerDispatcher) {
                playerComponent?.mediaPlayer()?.controls()?.stop()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        _currentPosition.value = safePos
        lastValidPositionMs = safePos
        _seekSequence.value += 1L
        seekGuardTargetMs = safePos
        seekGuardDeadlineMs = System.currentTimeMillis() + 2000L
        if (!_hasPlayableMedia.value || isReleased) {
            pendingSeekOnPlayingMs = safePos
            return
        }
        if (!hasStartedCurrentMedia) {
            pendingSeekOnPlayingMs = safePos
        }
        if (!suppressNativeCallsForTest) {
            scope.launch(playerDispatcher) {
                playerComponent?.mediaPlayer()?.controls()?.setTime(safePos)
            }
        }
    }

    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 100)
        _volume.value = clamped
        if (!isReleased) {
            scope.launch(playerDispatcher) {
                playerComponent?.mediaPlayer()?.audio()?.setVolume(clamped)
            }
        }
    }

    fun setMute(muted: Boolean) {
        if (!isReleased) {
            scope.launch(playerDispatcher) {
                playerComponent?.mediaPlayer()?.audio()?.setMute(muted)
            }
        }
    }

    fun isMuted(): Boolean {
        return playerComponent?.mediaPlayer()?.audio()?.isMute ?: false
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        mediaSessionToken += 1L
        activeNativeToken = 0L
        isUserPaused = false
        isExplicitStop = true
        _hasPlayableMedia.value = false
        _isPlaying.value = false
        _isBuffering.value = false
        scope.launch(playerDispatcher) {
            try {
                playerComponent?.mediaPlayer()?.controls()?.stop()
            } catch (_: Throwable) {}
            try {
                playerComponent?.release()
            } catch (_: Throwable) {}
            playerExecutor.shutdown()
        }
    }

    fun releaseBlocking(timeoutMs: Long = 500L) {
        if (isReleased) return
        isReleased = true
        mediaSessionToken += 1L
        activeNativeToken = 0L
        isUserPaused = false
        isExplicitStop = true
        _hasPlayableMedia.value = false
        _isPlaying.value = false
        _isBuffering.value = false
        val latch = CountDownLatch(1)
        try {
            playerExecutor.execute {
                try {
                    playerComponent?.mediaPlayer()?.controls()?.stop()
                } catch (_: Throwable) {}
                try {
                    playerComponent?.release()
                } catch (_: Throwable) {}
                latch.countDown()
            }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
        } finally {
            try {
                playerExecutor.shutdownNow()
            } catch (_: Throwable) {}
        }
    }
}
