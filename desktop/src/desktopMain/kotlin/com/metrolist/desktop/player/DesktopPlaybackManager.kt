package com.metrolist.desktop.player

import com.metrolist.desktop.cast.GoogleCastManager
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.desktop.repository.LastPlaybackStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

enum class RepeatMode {
    NONE,
    ALL,
    ONE,
}

class DesktopPlaybackManager(
    val player: VlcAudioPlayer,
    val repository: DesktopMusicRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    val lastPlaybackStore: LastPlaybackStore = LastPlaybackStore(),
    private val streamUrlResolver: suspend (String) -> String? = { videoId -> repository.resolveStreamUrl(videoId) },
    private val retryDelayProvider: (attempt: Int) -> Long = { attempt -> (attempt * 1500L).coerceAtMost(4000L) },
    private val debounceDelayMs: Long = 200L,
) {
    private var hasLoadedMediaInSession: Boolean = false
    private var loadedSongId: String? = null
    private var pendingInitialSeekMs: Long? = null
    private var restoredSongId: String? = null
    private var resumePositionMs: Long = 0L

    @Volatile
    private var userPaused: Boolean = false

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

    private val _isWaitingForNetwork = MutableStateFlow(false)
    val isWaitingForNetwork: StateFlow<Boolean> = _isWaitingForNetwork.asStateFlow()

    val isBuffering: StateFlow<Boolean> = combine(
        _isResolving,
        _isWaitingForNetwork,
        player.isBuffering,
    ) { resolving, waiting, playerBuffering ->
        resolving || waiting || playerBuffering
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _currentPlaylistTitle = MutableStateFlow<String?>(null)
    val currentPlaylistTitle: StateFlow<String?> = _currentPlaylistTitle.asStateFlow()

    private var playbackJob: Job? = null
    private var stallWatchdogJob: Job? = null
    private var lastLocalVolume: Int = 100
    private var lastFinishedTime: Long = 0L
    private val stateLock = Any()
    private var playbackGeneration: Long = 0L

    init {
        player.onPlaybackFinished = {
            val token = player.currentSessionToken
            val songId = _currentSong.value?.id
            if (!GoogleCastManager.isConnected.value) {
                scope.launch {
                    synchronized(stateLock) {
                        if (player.currentSessionToken == token && _currentSong.value?.id == songId) {
                            handleTrackFinished()
                        }
                    }
                }
            }
        }

        player.onPlaybackStarted = {
            val token = player.currentSessionToken
            val songId = _currentSong.value?.id
            synchronized(stateLock) {
                if (player.currentSessionToken == token && _currentSong.value?.id == songId) {
                    _isResolving.value = false
                    _isWaitingForNetwork.value = false
                    _statusMessage.value = null
                }
            }
        }

        player.onPlaybackError = { lastPosMs ->
            val token = player.currentSessionToken
            val songId = _currentSong.value?.id
            if (!GoogleCastManager.isConnected.value) {
                scope.launch {
                    synchronized(stateLock) {
                        if (player.currentSessionToken == token && _currentSong.value?.id == songId) {
                            handleStreamError(lastPosMs)
                        }
                    }
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
                        val pos = maxOf(resumePositionMs, player.currentPosition.value)
                        loadAndPlay(song, startPositionMs = pos)
                    }
                } else {
                    player.setMute(false)
                }
            }
        }

        scope.launch {
            val restored = lastPlaybackStore.loadLastPlayback()
            if (restored != null && _currentSong.value == null) {
                _currentSong.value = restored.currentSong
                val q = if (restored.queue.isNotEmpty()) restored.queue else listOf(restored.currentSong)
                unShuffledQueue = q
                _queue.value = q
                _currentIndex.value = restored.currentIndex.coerceIn(0, q.size - 1)
                _currentPlaylistTitle.value = restored.playlistTitle
                hasLoadedMediaInSession = false
                loadedSongId = null
                restoredSongId = restored.currentSong.id
                if (restored.lastPositionMs > 1000L) {
                    pendingInitialSeekMs = restored.lastPositionMs
                    resumePositionMs = restored.lastPositionMs
                }
                player.setMetadata(
                    title = restored.currentSong.title,
                    artist = restored.currentSong.artist,
                    thumbnailUrl = restored.currentSong.thumbnailUrl,
                    initialPositionMs = restored.lastPositionMs,
                )
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
        synchronized(stateLock) {
            unShuffledQueue = queueToUse
            _currentSong.value = song
            _currentPlaylistTitle.value = null
            pendingInitialSeekMs = null
            restoredSongId = null

            if (_isShuffleEnabled.value) {
                val otherSongs = queueToUse.filter { it.id != song.id }.shuffled()
                val shuffledQueue = listOf(song) + otherSongs
                _queue.value = shuffledQueue
                _currentIndex.value = 0
            } else {
                _queue.value = queueToUse
                val index = queueToUse.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                _currentIndex.value = index
            }

            loadAndPlay(song, startPositionMs = 0L)
        }

        // Jika antrean hanya 1 lagu (misal diputar langsung dari search dropdown),
        // otomatis ambil rekomendasi lagu terkait di latar belakang
        if (queueToUse.size <= 1) {
            scope.launch {
                val related = repository.getRelatedSongs(song.id)
                synchronized(stateLock) {
                    val currentQ = _queue.value
                    val currentQIds = currentQ.map { it.id }.toSet()
                    val newSongs = related.filter { !currentQIds.contains(it.id) }
                    if (newSongs.isNotEmpty()) {
                        unShuffledQueue = unShuffledQueue + newSongs
                        if (_isShuffleEnabled.value) {
                            _queue.value = currentQ + newSongs.shuffled()
                        } else {
                            _queue.value = currentQ + newSongs
                        }
                    }
                }
            }
        }
    }

    fun playPlaylist(
        playlist: DesktopPlaylist,
        startIndex: Int = 0,
        shuffle: Boolean = false,
    ) {
        synchronized(stateLock) {
            if (playlist.songs.isEmpty()) {
                _statusMessage.value = "Playlist '${playlist.title}' kosong."
                return
            }

            unShuffledQueue = playlist.songs
            _currentPlaylistTitle.value = playlist.title
            pendingInitialSeekMs = null
            restoredSongId = null

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
                loadAndPlay(targetSong, startPositionMs = 0L)
            } else {
                _isShuffleEnabled.value = false
                _queue.value = playlist.songs
                val safeIndex = startIndex.coerceIn(0, playlist.songs.size - 1)
                _currentIndex.value = safeIndex
                val targetSong = playlist.songs[safeIndex]
                _currentSong.value = targetSong
                loadAndPlay(targetSong, startPositionMs = 0L)
            }
        }
    }

    fun playNext() {
        synchronized(stateLock) {
            val q = _queue.value
            if (q.isEmpty()) return

            val current = _currentIndex.value
            val nextIndex = if (current >= q.size - 1) {
                if (_repeatMode.value == RepeatMode.ALL) 0 else return
            } else {
                current + 1
            }

            pendingInitialSeekMs = null
            restoredSongId = null
            _currentIndex.value = nextIndex
            val nextSong = q[nextIndex]
            _currentSong.value = nextSong
            loadAndPlay(nextSong, startPositionMs = 0L)
        }
    }

    fun playPrevious() {
        synchronized(stateLock) {
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

            pendingInitialSeekMs = null
            restoredSongId = null
            _currentIndex.value = prevIndex
            val prevSong = q[prevIndex]
            _currentSong.value = prevSong
            loadAndPlay(prevSong, startPositionMs = 0L)
        }
    }

    fun toggleShuffle() {
        val q = _queue.value
        if (q.isEmpty()) return

        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle

        val current = _currentSong.value
        val currIdx = _currentIndex.value

        if (newShuffle) {
            val playedHistory = if (currIdx in q.indices) q.take(currIdx) else emptyList()
            val currentItem = if (currIdx in q.indices) q[currIdx] else current
            val upcoming = if (currIdx in q.indices) q.drop(currIdx + 1) else q.filter { it.id != current?.id }

            // Jika upcoming kosong namun ada lagu lain di unShuffledQueue (misal memilih lagu terakhir di pencarian),
            // acak seluruh lagu lainnya ke dalam upcoming agar antrean tidak kosong
            if (upcoming.isEmpty() && unShuffledQueue.size > 1) {
                val otherSongs = unShuffledQueue.filter { it.id != currentItem?.id }.shuffled()
                val newQueue = listOfNotNull(currentItem) + otherSongs
                _queue.value = newQueue
                _currentIndex.value = 0
            } else {
                val shuffledUpcoming = upcoming.shuffled()
                val newQueue = playedHistory + listOfNotNull(currentItem) + shuffledUpcoming
                _queue.value = newQueue
                _currentIndex.value = playedHistory.size
            }

            // Jika antrean hanya 1 lagu, otomatis tarik lagu terkait dan acak
            if (_queue.value.size <= 1 && currentItem != null) {
                scope.launch {
                    val related = repository.getRelatedSongs(currentItem.id)
                    val existingIds = _queue.value.map { it.id }.toSet()
                    val newSongs = related.filter { !existingIds.contains(it.id) }
                    if (newSongs.isNotEmpty()) {
                        unShuffledQueue = unShuffledQueue + newSongs
                        _queue.value = _queue.value + newSongs.shuffled()
                    }
                }
            }
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

    private fun handleStreamError(lastPosMs: Long) {
        val activeSong = _currentSong.value ?: return
        val savedPos = maxOf(resumePositionMs, lastPosMs)
        resumePositionMs = savedPos
        hasLoadedMediaInSession = false
        loadedSongId = null
        stallWatchdogJob?.cancel()

        if (userPaused || player.isReleased) {
            return
        }

        _isWaitingForNetwork.value = true
        _statusMessage.value = "Sinyal terputus, mencoba menyambung ulang '${activeSong.title}'..."
        loadAndPlay(activeSong, startPositionMs = savedPos)
    }

    private fun handleTrackFinished() {
        val now = System.currentTimeMillis()
        if (now - lastFinishedTime < 1500L) return
        lastFinishedTime = now

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentSong.value?.let { loadAndPlay(it, startPositionMs = 0L) }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.NONE -> {
                if (_currentIndex.value < _queue.value.size - 1) {
                    playNext()
                } else {
                    // Antrean habis: otomatis ambil radio/rekomendasi lagu lanjutan (Continuous Autoplay)
                    val lastSong = _currentSong.value
                    if (lastSong != null) {
                        scope.launch {
                            _statusMessage.value = "Memuat rekomendasi lagu..."
                            val related = repository.getRelatedSongs(lastSong.id)
                            val currentQueueIds = _queue.value.map { it.id }.toSet()
                            val newSongs = related.filter { !currentQueueIds.contains(it.id) }
                            if (newSongs.isNotEmpty()) {
                                val toAdd = if (_isShuffleEnabled.value) newSongs.shuffled() else newSongs
                                unShuffledQueue = unShuffledQueue + newSongs
                                _queue.value = _queue.value + toAdd
                                _statusMessage.value = null
                                playNext()
                            } else {
                                _statusMessage.value = "Pemutaran antrean playlist selesai."
                            }
                        }
                    } else {
                        _statusMessage.value = "Pemutaran antrean playlist selesai."
                    }
                }
            }
        }
    }

    private fun loadAndPlay(song: DesktopSong, startPositionMs: Long = 0L) = synchronized(stateLock) {
        val generation = ++playbackGeneration
        userPaused = false
        hasLoadedMediaInSession = false
        loadedSongId = null

        val restoredPos = if (restoredSongId == song.id) (pendingInitialSeekMs ?: 0L) else 0L
        if (restoredSongId == song.id) {
            pendingInitialSeekMs = null
            restoredSongId = null
        }

        val targetStartPos = maxOf(startPositionMs, restoredPos).coerceAtLeast(0L)
        resumePositionMs = targetStartPos
        val expectedDurationMs = parseDurationTextToMs(song.durationText)

        // Hentikan media sebelumnya dan perbarui metadata segera agar tidak pernah memutar lagu lama saat offline
        player.prepareForNewTrack(
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl,
            initialPositionMs = targetStartPos,
            expectedDurationMs = expectedDurationMs,
        )

        scope.launch {
            lastPlaybackStore.saveLastPlayback(
                song = song,
                queue = _queue.value,
                currentIndex = _currentIndex.value,
                playlistTitle = _currentPlaylistTitle.value,
                positionMs = targetStartPos,
            )
        }

        playbackJob?.cancel()
        stallWatchdogJob?.cancel()
        playbackJob = scope.launch {
            if (debounceDelayMs > 0L) {
                delay(debounceDelayMs)
            }
            if (!isActive || userPaused || _currentSong.value?.id != song.id || player.isReleased || playbackGeneration != generation) return@launch

            val isCast = GoogleCastManager.isConnected.value
            val localAudio = repository.downloadManager.getDownloadedAudioFile(song.id)
            val hasLocalAudio = localAudio != null && localAudio.exists()

            // 1. Dapatkan remote stream URL dengan retry sampai koneksi pulih jika belum diunduh lokal
            var remoteStreamUrl: String? = null
            if (isCast || !hasLocalAudio) {
                var attempt = 0
                while (isActive && !userPaused && _currentSong.value?.id == song.id && !player.isReleased && playbackGeneration == generation) {
                    attempt++
                    _isResolving.value = true
                    if (attempt > 1) {
                        _isWaitingForNetwork.value = true
                    }
                    val devName = GoogleCastManager.connectedDevice.value?.name ?: "TV"
                    _statusMessage.value = when {
                        attempt > 1 -> "Sinyal terputus, menunggu koneksi untuk '${song.title}'..."
                        isCast -> "Streaming '${song.title}' ke $devName..."
                        else -> "Memuat audio: ${song.title}..."
                    }

                    val url = try {
                        streamUrlResolver(song.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }

                    if (!url.isNullOrBlank()) {
                        remoteStreamUrl = url
                        break
                    }

                    if (!isCast && !player.isAvailable) {
                        _isResolving.value = false
                        _isWaitingForNetwork.value = false
                        player.setBuffering(false)
                        _statusMessage.value = "LibVLC belum terinstal pada sistem. Silakan pasang VLC (64-bit)."
                        return@launch
                    }

                    _isResolving.value = false
                    _isWaitingForNetwork.value = true
                    player.setBuffering(true)
                    _statusMessage.value = "Sinyal terputus, menunggu koneksi untuk '${song.title}'..."
                    val backoffMs = retryDelayProvider(attempt)
                    if (backoffMs > 0L) {
                        delay(backoffMs)
                    }
                }
            }

            if (!isActive || userPaused || _currentSong.value?.id != song.id || player.isReleased || playbackGeneration != generation) {
                _isResolving.value = false
                return@launch
            }

            _isResolving.value = false
            _isWaitingForNetwork.value = false

            // 2. Putar ke Cast jika terhubung
            if (isCast) {
                if (!remoteStreamUrl.isNullOrBlank()) {
                    _statusMessage.value = null
                    GoogleCastManager.loadMedia(
                        streamUrl = remoteStreamUrl,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                    )
                    if (resumePositionMs > 1000L) {
                        val seekPos = resumePositionMs
                        scope.launch {
                            delay(600)
                            if (GoogleCastManager.isConnected.value && _currentSong.value?.id == song.id) {
                                GoogleCastManager.seek(seekPos)
                            }
                        }
                    }
                } else {
                    _statusMessage.value = "Gagal memuat stream TV untuk '${song.title}'."
                }
            }

            // 3. Putar ke VLC lokal (offline jika ada, atau remote stream jika belum)
            val playUrl = if (hasLocalAudio) localAudio.absolutePath else remoteStreamUrl
            if (!playUrl.isNullOrBlank()) {
                if (!isCast && !player.isAvailable) {
                    player.setBuffering(false)
                    _statusMessage.value = "LibVLC belum terinstal pada sistem. Silakan pasang VLC (64-bit)."
                    return@launch
                }
                val startPosSnapshot = synchronized(stateLock) {
                    if (!isActive || userPaused || _currentSong.value?.id != song.id || player.isReleased || playbackGeneration != generation) {
                        return@launch
                    }
                    loadedSongId = song.id
                    hasLoadedMediaInSession = true
                    resumePositionMs
                }
                player.play(
                    url = playUrl,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    startPositionMs = startPosSnapshot,
                    expectedDurationMs = expectedDurationMs,
                )
                player.setMute(isCast)
                if (hasLocalAudio) {
                    _statusMessage.value = null
                } else if (!isCast) {
                    startStallWatchdog(song)
                }
            } else if (!isCast) {
                player.setBuffering(false)
                _statusMessage.value = "Koneksi terputus dan lagu '${song.title}' belum diunduh untuk offline."
            }
        }
    }

    private fun startStallWatchdog(song: DesktopSong) {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = scope.launch {
            var lastObservedPos = player.currentPosition.value
            var stalledMs = 0L
            var unstartedIdleMs = 0L
            while (isActive && !userPaused && _currentSong.value?.id == song.id && !player.isReleased) {
                delay(2000L)
                if (!isActive || userPaused || _currentSong.value?.id != song.id || player.isReleased) break
                if (GoogleCastManager.isConnected.value) break

                val curPos = player.currentPosition.value
                if (curPos > resumePositionMs) {
                    resumePositionMs = curPos
                }

                val isBufferingNow = player.isBuffering.value
                val isPlayingNow = player.isPlaying.value

                if (isBufferingNow) {
                    stalledMs += 2000L
                    unstartedIdleMs = 0L
                    _statusMessage.value = "Buffering '${song.title}'..."
                } else if (isPlayingNow) {
                    unstartedIdleMs = 0L
                    if (curPos == lastObservedPos) {
                        stalledMs += 2000L
                    } else {
                        lastObservedPos = curPos
                        stalledMs = 0L
                    }
                } else {
                    // Jika tidak playing dan tidak buffering padahal tidak di-pause user,
                    // bisa terjadi bila resume()/play() gagal diam-diam akibat socket terputus.
                    unstartedIdleMs += 2000L
                    stalledMs += 2000L
                    if (unstartedIdleMs < 6000L) {
                        continue
                    }
                }

                if (stalledMs >= 12_000L || unstartedIdleMs >= 6000L) {
                    synchronized(stateLock) {
                        if (!isActive || userPaused || _currentSong.value?.id != song.id || player.isReleased) return@synchronized
                        val recoverPos = maxOf(resumePositionMs, curPos)
                        resumePositionMs = recoverPos
                        hasLoadedMediaInSession = false
                        loadedSongId = null
                        _isWaitingForNetwork.value = true
                        _statusMessage.value = "Sinyal melambat, menyambung ulang '${song.title}'..."
                        loadAndPlay(song, startPositionMs = recoverPos)
                    }
                    break
                }
            }
        }
    }

    fun togglePlayPause() {
        val song = _currentSong.value ?: return
        if (player.isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        synchronized(stateLock) {
            playbackGeneration++
            userPaused = true
            playbackJob?.cancel()
            stallWatchdogJob?.cancel()
            _isResolving.value = false
            _isWaitingForNetwork.value = false
            val curPos = player.currentPosition.value
            if (curPos > 0L) {
                resumePositionMs = curPos
            }
            if (GoogleCastManager.isConnected.value) {
                GoogleCastManager.pause()
            }
            player.pause()
            _statusMessage.value = null
            savePlaybackPositionNow()
        }
    }

    fun resume() {
        synchronized(stateLock) {
            val song = _currentSong.value ?: return
            userPaused = false

            // Jika sudah aktif me-resolve URL stream di jaringan (bukan sedang sleep backoff), biarkan berlanjut
            if (playbackJob?.isActive == true && _isResolving.value && !_isWaitingForNetwork.value) {
                return
            }

            val isSameSongLoaded = loadedSongId == song.id &&
                hasLoadedMediaInSession &&
                player.hasPlayableMedia.value &&
                player.hasStartedMedia

            if (!isSameSongLoaded || _isWaitingForNetwork.value) {
                val targetPos = maxOf(
                    resumePositionMs,
                    if (restoredSongId == song.id) (pendingInitialSeekMs ?: 0L) else 0L,
                    player.currentPosition.value,
                )
                // Jika masih dalam delay backoff menunggu jaringan atau media belum siap, mulai ulang segera
                loadAndPlay(song, startPositionMs = targetPos)
                return
            }

            if (player.isBuffering.value) {
                // Sedang buffering stream lagu aktif — pastikan watchdog aktif dan tunggu sampai benar-benar terputar
                startStallWatchdog(song)
                return
            }

            if (GoogleCastManager.isConnected.value) {
                GoogleCastManager.resume()
            }
            if (!player.resumeIfLoaded()) {
                val targetPos = maxOf(resumePositionMs, player.currentPosition.value)
                loadAndPlay(song, startPositionMs = targetPos)
            } else {
                startStallWatchdog(song)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        synchronized(stateLock) {
            val safePos = positionMs.coerceAtLeast(0L)
            resumePositionMs = safePos
            val activeSong = _currentSong.value
            if (activeSong != null && restoredSongId == activeSong.id) {
                pendingInitialSeekMs = safePos
            }
            val isSameSongLoaded = activeSong != null && loadedSongId == activeSong.id && hasLoadedMediaInSession && player.hasPlayableMedia.value
            if (isSameSongLoaded) {
                player.seekTo(safePos)
                if (GoogleCastManager.isConnected.value) {
                    GoogleCastManager.seek(safePos)
                }
            } else {
                player.updatePositionOnly(safePos)
            }
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

    fun savePlaybackPositionNow() {
        val song = _currentSong.value ?: return
        val pos = maxOf(resumePositionMs, player.currentPosition.value)
        scope.launch {
            lastPlaybackStore.saveLastPlayback(
                song = song,
                queue = _queue.value,
                currentIndex = _currentIndex.value,
                playlistTitle = _currentPlaylistTitle.value,
                positionMs = pos,
            )
        }
    }

    fun savePlaybackPositionBlocking(timeoutMs: Long = 250L) {
        val snapshot = synchronized(stateLock) {
            val song = _currentSong.value ?: return
            val pos = maxOf(resumePositionMs, player.currentPosition.value)
            Triple(song, _queue.value, Triple(_currentIndex.value, _currentPlaylistTitle.value, pos))
        }
        try {
            runBlocking {
                withTimeoutOrNull(timeoutMs) {
                    lastPlaybackStore.saveLastPlayback(
                        song = snapshot.first,
                        queue = snapshot.second,
                        currentIndex = snapshot.third.first,
                        playlistTitle = snapshot.third.second,
                        positionMs = snapshot.third.third,
                    )
                }
            }
        } catch (_: Throwable) {}
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun parseDurationTextToMs(durationText: String?): Long {
        if (durationText.isNullOrBlank()) return 0L
        val parts = durationText.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }
}
