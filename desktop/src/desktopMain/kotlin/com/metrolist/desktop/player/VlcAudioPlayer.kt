package com.metrolist.desktop.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

class VlcAudioPlayer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var playerComponent: AudioPlayerComponent? = null
    val isAvailable: Boolean

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

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

    private var progressJob: Job? = null

    var onPlaybackFinished: (() -> Unit)? = null

    init {
        val discovered = try {
            NativeDiscovery().discover()
        } catch (e: Throwable) {
            System.err.println("VLC discovery exception: ${e.message}")
            false
        }

        isAvailable = discovered
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
                    override fun playing(mediaPlayer: MediaPlayer?) {
                        _isPlaying.value = true
                        startProgressTracker()
                    }

                    override fun paused(mediaPlayer: MediaPlayer?) {
                        _isPlaying.value = false
                        stopProgressTracker()
                    }

                    override fun stopped(mediaPlayer: MediaPlayer?) {
                        _isPlaying.value = false
                        _currentPosition.value = 0L
                        stopProgressTracker()
                    }

                    override fun finished(mediaPlayer: MediaPlayer?) {
                        _isPlaying.value = false
                        _currentPosition.value = 0L
                        stopProgressTracker()
                        onPlaybackFinished?.invoke()
                    }

                    override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
                        if (newLength > 0) {
                            _duration.value = newLength
                        }
                    }

                    override fun error(mediaPlayer: MediaPlayer?) {
                        System.err.println("VLC encountered an error during playback")
                        _isPlaying.value = false
                        stopProgressTracker()
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

    fun play(url: String, title: String, artist: String, thumbnailUrl: String? = null) {
        _currentTitle.value = title
        _currentArtist.value = artist
        _currentThumbnailUrl.value = thumbnailUrl
        _currentPosition.value = 0L

        playerComponent?.mediaPlayer()?.media()?.play(url)
    }

    fun togglePlayPause() {
        val player = playerComponent?.mediaPlayer() ?: return
        if (player.status().isPlaying) {
            player.controls().pause()
        } else {
            player.controls().play()
        }
    }

    fun pause() {
        playerComponent?.mediaPlayer()?.controls()?.pause()
    }

    fun resume() {
        playerComponent?.mediaPlayer()?.controls()?.play()
    }
    fun stop() {
        playerComponent?.mediaPlayer()?.controls()?.stop()
        _isPlaying.value = false
        _currentPosition.value = 0L
        stopProgressTracker()
    }


    fun seekTo(positionMs: Long) {
        playerComponent?.mediaPlayer()?.controls()?.setTime(positionMs)
        _currentPosition.value = positionMs
    }

    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 100)
        _volume.value = clamped
        playerComponent?.mediaPlayer()?.audio()?.setVolume(clamped)
    }

    fun release() {
        stopProgressTracker()
        playerComponent?.release()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                playerComponent?.mediaPlayer()?.let { mp ->
                    if (mp.status().isPlaying) {
                        val time = mp.status().time()
                        if (time >= 0) {
                            _currentPosition.value = time
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
