package com.metrolist.desktop.player

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
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
                    }

                    override fun paused(mediaPlayer: MediaPlayer?) {
                        _isPlaying.value = false
                    }

                    override fun stopped(mediaPlayer: MediaPlayer?) {
                        _isPlaying.value = false
                        _currentPosition.value = 0L
                    }

                    override fun finished(mediaPlayer: MediaPlayer?) {
                        _isPlaying.value = false
                        _currentPosition.value = 0L
                        onPlaybackFinished?.invoke()
                    }

                    override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
                        if (newLength > 0) {
                            _duration.value = newLength
                        }
                    }

                    override fun timeChanged(mediaPlayer: MediaPlayer?, newTime: Long) {
                        if (newTime >= 0) {
                            _currentPosition.value = newTime
                        }
                    }

                    override fun error(mediaPlayer: MediaPlayer?) {
                        System.err.println("VLC encountered an error during playback")
                        _isPlaying.value = false
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

        scope.launch(playerDispatcher) {
            playerComponent?.mediaPlayer()?.media()?.play(url)
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
        _isPlaying.value = false
        scope.launch(playerDispatcher) {
            playerComponent?.mediaPlayer()?.controls()?.pause()
        }
    }

    fun resume() {
        scope.launch(playerDispatcher) {
            playerComponent?.mediaPlayer()?.controls()?.play()
        }
    }

    fun stop() {
        _isPlaying.value = false
        _currentPosition.value = 0L
        scope.launch(playerDispatcher) {
            playerComponent?.mediaPlayer()?.controls()?.stop()
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs
        scope.launch(playerDispatcher) {
            playerComponent?.mediaPlayer()?.controls()?.setTime(positionMs)
        }
    }

    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 100)
        _volume.value = clamped
        scope.launch(playerDispatcher) {
            playerComponent?.mediaPlayer()?.audio()?.setVolume(clamped)
        }
    }

    fun setMute(muted: Boolean) {
        scope.launch(playerDispatcher) {
            playerComponent?.mediaPlayer()?.audio()?.setMute(muted)
        }
    }

    fun isMuted(): Boolean {
        return playerComponent?.mediaPlayer()?.audio()?.isMute ?: false
    }

    fun release() {
        scope.launch(playerDispatcher) {
            playerComponent?.release()
            playerExecutor.shutdown()
        }
    }
}
