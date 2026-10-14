package com.metrolist.desktop.player

import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    init {
        player.onPlaybackFinished = {
            scope.launch {
                handleTrackFinished()
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
            val shuffled = playlist.songs.shuffled()
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
            player.seekTo(0L)
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
        if (newShuffle) {
            val remaining = q.filter { it.id != current?.id }.shuffled()
            val newQueue = if (current != null) listOf(current) + remaining else remaining
            _queue.value = newQueue
            _currentIndex.value = 0
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
            // 1. Prioritaskan file audio offline jika sudah terunduh di disk
            val localAudio = repository.downloadManager.getDownloadedAudioFile(song.id)
            if (localAudio != null && localAudio.exists()) {
                _isResolving.value = false
                _statusMessage.value = if (!player.isAvailable) "LibVLC belum terinstal pada sistem. Silakan pasang VLC (64-bit)." else null
                player.play(
                    url = localAudio.absolutePath,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                )
                return@launch
            }

            // 2. Jika belum diunduh, streaming langsung lewat jaringan
            _isResolving.value = true
            _statusMessage.value = "Memuat audio stream untuk: ${song.title}..."

            val streamUrl = repository.resolveStreamUrl(song.id)
            _isResolving.value = false

            if (!streamUrl.isNullOrBlank()) {
                if (!player.isAvailable) {
                    _statusMessage.value = "LibVLC belum terinstal pada sistem. Silakan pasang VLC (64-bit)."
                } else {
                    _statusMessage.value = null
                }
                player.play(
                    url = streamUrl,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                )
            } else {
                _statusMessage.value = "Koneksi terputus dan lagu '${song.title}' belum diunduh untuk offline."
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
