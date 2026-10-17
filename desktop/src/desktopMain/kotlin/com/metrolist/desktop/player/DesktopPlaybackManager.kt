package com.metrolist.desktop.player

import com.metrolist.desktop.cast.GoogleCastManager
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RepeatMode {
    NONE,
    ALL,
    ONE,
}

class DesktopPlaybackManager(
    val player: VlcAudioPlayer,
    val repository: DesktopMusicRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val _queue = MutableStateFlow<List<DesktopSong>>(emptyList())
    val queue: StateFlow<List<DesktopSong>> = _queue.asStateFlow()

    private var unShuffledQueue: List<DesktopSong> = emptyList()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<DesktopSong?>(null)
    val currentSong: StateFlow<DesktopSong?> = _currentSong.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _currentPlaylistTitle = MutableStateFlow<String?>(null)
    val currentPlaylistTitle: StateFlow<String?> = _currentPlaylistTitle.asStateFlow()

    private var playbackJob: Job? = null
    private var lastLocalVolume: Int = 100
    private var lastFinishedTime: Long = 0L

    init {
        player.onPlaybackFinished = {
            if (!GoogleCastManager.isConnected.value) {
                scope.launch {
                    handleTrackFinished()
                }
            }
        }

        GoogleCastManager.onTrackFinished = {
            if (GoogleCastManager.isConnected.value) {
                scope.launch {
                    handleTrackFinished()
                }
            }
        }

        GoogleCastManager.onCurrentTimeUpdated = { tvMs ->
            if (GoogleCastManager.isConnected.value) {
                val currentLocal = player.currentPosition.value
                if (kotlin.math.abs(currentLocal - tvMs) > 1500L) {
                    player.seekTo(tvMs)
                }
            }
        }

        scope.launch {
            GoogleCastManager.isConnected.collect { connected ->
                if (connected) {
                    player.setMute(true)
                    _currentSong.value?.let { song ->
                        loadAndPlay(song)
                    }
                } else {
                    player.setMute(false)
                }
            }
        }
    }

    val hasNext: Boolean
        get() {
            val q = _queue.value
            val idx = _currentIndex.value
            if (q.isEmpty()) return false
            if (_repeatMode.value == RepeatMode.ALL || _repeatMode.value == RepeatMode.ONE) return true
            return idx < q.size - 1
        }

    val hasPrevious: Boolean
        get() {
            val q = _queue.value
            val idx = _currentIndex.value
            if (q.isEmpty()) return false
            if (_repeatMode.value == RepeatMode.ALL || _repeatMode.value == RepeatMode.ONE) return true
            return idx > 0
        }

    fun playSong(song: DesktopSong, sourceQueue: List<DesktopSong> = listOf(song)) {
        val queueToUse = if (sourceQueue.any { it.id == song.id }) sourceQueue else listOf(song) + sourceQueue
        unShuffledQueue = queueToUse
        _queue.value = queueToUse
        _isShuffleEnabled.value = false
        val index = queueToUse.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _currentIndex.value = index
        _currentSong.value = song
        _currentPlaylistTitle.value = null
        loadAndPlay(song)
    }

    fun playPlaylist(
        playlist: DesktopPlaylist,
        startIndex: Int = 0,
        shuffle: Boolean = false,
    ) {
        if (playlist.songs.isEmpty()) {
            _statusMessage.value = "Playlist '${playlist.title}' kosong."
            return
        }

        unShuffledQueue = playlist.songs
        _currentPlaylistTitle.value = playlist.title

        if (shuffle) {
            _isShuffleEnabled.value = true
            val safeIndex = startIndex.coerceIn(0, playlist.songs.size - 1)
            val shuffled = if (startIndex > 0) {
                val startSong = playlist.songs[safeIndex]
                val remaining = playlist.songs.filterIndexed { i, _ -> i != safeIndex }.shuffled()
                listOf(startSong) + remaining
            } else {
                playlist.songs.shuffled()
            }
            _queue.value = shuffled
            _currentIndex.value = 0
            val targetSong = shuffled[0]
            _currentSong.value = targetSong
            loadAndPlay(targetSong)
        } else {
            _isShuffleEnabled.value = false
            _queue.value = playlist.songs
            val safeIndex = startIndex.coerceIn(0, playlist.songs.size - 1)
            _currentIndex.value = safeIndex
            val targetSong = playlist.songs[safeIndex]
            _currentSong.value = targetSong
            loadAndPlay(targetSong)
        }
    }

    fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        val current = _currentIndex.value
        val nextIndex = if (current >= q.size - 1) {
            if (_repeatMode.value == RepeatMode.ALL) 0 else return
        } else {
            current + 1
        }

        _currentIndex.value = nextIndex
        val nextSong = q[nextIndex]
        _currentSong.value = nextSong
        loadAndPlay(nextSong)
    }

    fun playPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return

        // Jika lagu sudah berjalan lebih dari 4 detik, ulangi dari awal
        if (player.currentPosition.value > 4000L) {
            seekTo(0L)
            return
        }

        val current = _currentIndex.value
        val prevIndex = if (current <= 0) {
            if (_repeatMode.value == RepeatMode.ALL) q.size - 1 else 0
        } else {
            current - 1
        }

        _currentIndex.value = prevIndex
        val prevSong = q[prevIndex]
        _currentSong.value = prevSong
        loadAndPlay(prevSong)
    }

    fun toggleShuffle() {
        val q = _queue.value
        if (q.isEmpty()) return

        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle

        val current = _currentSong.value
        val currIdx = _currentIndex.value

        if (newShuffle) {
            // Preserve playback history: songs played before currIdx remain in the past
            val playedHistory = if (currIdx in q.indices) q.take(currIdx) else emptyList()
            val currentItem = if (currIdx in q.indices) q[currIdx] else current
            val upcoming = if (currIdx in q.indices) q.drop(currIdx + 1) else q.filter { it.id != current?.id }

            // Only upcoming unplayed songs are shuffled
            val shuffledUpcoming = upcoming.shuffled()
            val newQueue = playedHistory + listOfNotNull(currentItem) + shuffledUpcoming

            _queue.value = newQueue
            _currentIndex.value = playedHistory.size
        } else {
            _queue.value = unShuffledQueue
            _currentIndex.value = if (current != null) {
                unShuffledQueue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            } else 0
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    private fun handleTrackFinished() {
        val now = System.currentTimeMillis()
        if (now - lastFinishedTime < 1500L) return
        lastFinishedTime = now

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentSong.value?.let { loadAndPlay(it) }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.NONE -> {
                if (_currentIndex.value < _queue.value.size - 1) {
                    playNext()
                } else {
                    _statusMessage.value = "Pemutaran antrean playlist selesai."
                }
            }
        }
    }

    private fun loadAndPlay(song: DesktopSong) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            // Debounce klik beruntun (spam next/previous/song) agar tidak membanjiri antrean & network
            delay(250)

            val isCast = GoogleCastManager.isConnected.value
            val localAudio = repository.downloadManager.getDownloadedAudioFile(song.id)
            val hasLocalAudio = localAudio != null && localAudio.exists()

            // 1. Dapatkan remote stream URL jika Cast aktif atau audio lokal belum diunduh
            val remoteStreamUrl = if (isCast || !hasLocalAudio) {
                _isResolving.value = true
                val devName = GoogleCastManager.connectedDevice.value?.name ?: "TV"
                _statusMessage.value = if (isCast) "Streaming '${song.title}' ke $devName..." else "Memuat audio: ${song.title}..."
                val url = repository.resolveStreamUrl(song.id)
                _isResolving.value = false
                url
            } else {
                null
            }

            _isResolving.value = false
            _statusMessage.value = null

            // 2. Putar ke Cast jika terhubung
            if (isCast) {
                if (!remoteStreamUrl.isNullOrBlank()) {
                    GoogleCastManager.loadMedia(
                        streamUrl = remoteStreamUrl,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                    )
                } else {
                    _statusMessage.value = "Gagal memuat stream TV untuk '${song.title}'."
                }
            }

            // 3. Putar ke VLC lokal (offline jika ada, atau remote stream jika belum)
            val playUrl = if (hasLocalAudio) localAudio.absolutePath else remoteStreamUrl
            if (!playUrl.isNullOrBlank()) {
                if (!isCast && !player.isAvailable) {
                    _statusMessage.value = "LibVLC belum terinstal pada sistem. Silakan pasang VLC (64-bit)."
                }
                player.play(
                    url = playUrl,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                )
                player.setMute(isCast)
            } else if (!isCast) {
                _statusMessage.value = "Koneksi terputus dan lagu '${song.title}' belum diunduh untuk offline."
            }
        }
    }

    fun togglePlayPause() {
        if (GoogleCastManager.isConnected.value) {
            if (player.isPlaying.value) {
                GoogleCastManager.pause()
                player.pause()
            } else {
                GoogleCastManager.resume()
                player.resume()
            }
        } else {
            player.togglePlayPause()
        }
    }

    fun pause() {
        if (GoogleCastManager.isConnected.value) {
            GoogleCastManager.pause()
        }
        player.pause()
    }

    fun resume() {
        if (GoogleCastManager.isConnected.value) {
            GoogleCastManager.resume()
        }
        player.resume()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        if (GoogleCastManager.isConnected.value) {
            GoogleCastManager.seek(positionMs)
        }
    }

    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 100)
        if (GoogleCastManager.isConnected.value) {
            GoogleCastManager.setVolume(clamped)
            lastLocalVolume = clamped
        } else {
            player.setVolume(clamped)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
