package com.metrolist.desktop.discord

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.VlcAudioPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

class DesktopDiscordRpc(
    private val player: VlcAudioPlayer,
    private val playbackManager: DesktopPlaybackManager,
    initialClientId: String = DiscordIpcClient.DEFAULT_CLIENT_ID,
    initialEnabled: Boolean = true,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    companion object {
        const val DEFAULT_ICON_URL = "https://raw.githubusercontent.com/Syauqi227/panlist/main/desktop/src/desktopMain/resources/icons/icon.png"

        fun parseDurationText(durationText: String?): Long {
            if (durationText.isNullOrBlank()) return 0L
            val parts = durationText.trim().split(":").mapNotNull { it.toIntOrNull() }
            return when (parts.size) {
                2 -> (parts[0] * 60L + parts[1]) * 1000L
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                else -> 0L
            }
        }
    }

    val ipcClient = DiscordIpcClient(initialClientId, scope)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isEnabled = MutableStateFlow(initialEnabled)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var syncJob: Job? = null

    init {
        ipcClient.onStatusChanged = { connected ->
            _isConnected.value = connected
        }

        if (initialEnabled) {
            start()
        }
    }

    fun start() {
        _isEnabled.value = true
        ipcClient.start()
        startSync()
    }

    fun stop() {
        _isEnabled.value = false
        syncJob?.cancel()
        ipcClient.stop()
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == _isEnabled.value) return
        if (enabled) {
            start()
        } else {
            stop()
        }
    }

    fun setClientId(newId: String) {
        ipcClient.updateClientId(newId)
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            // 1. Sinkronisasi pergantian lagu secara real-time
            launch {
                playbackManager.currentSong.collectLatest { song ->
                    updatePresence(song, player.isPlaying.value, player.currentPosition.value, player.duration.value)
                }
            }
            // 2. Sinkronisasi status Play/Pause
            launch {
                player.isPlaying.collectLatest { isPlaying ->
                    updatePresence(playbackManager.currentSong.value, isPlaying, player.currentPosition.value, player.duration.value)
                }
            }
            // 3. Sinkronisasi durasi lagu setelah LibVLC selesai memuat file
            launch {
                player.duration.collectLatest { duration ->
                    if (duration > 0L) {
                        updatePresence(playbackManager.currentSong.value, player.isPlaying.value, player.currentPosition.value, duration)
                    }
                }
            }
            // 4. Sinkronisasi seek position manual
            launch {
                var lastExpectedPos = -1L
                var lastTimestamp = 0L
                player.currentPosition.collectLatest { pos ->
                    val now = System.currentTimeMillis()
                    val timeDelta = if (lastTimestamp > 0L) now - lastTimestamp else 0L
                    val expectedPos = if (lastExpectedPos >= 0L) lastExpectedPos + timeDelta else pos

                    if (player.isPlaying.value && lastExpectedPos >= 0L && kotlin.math.abs(pos - expectedPos) > 3000L) {
                        updatePresence(playbackManager.currentSong.value, true, pos, player.duration.value)
                    }
                    lastExpectedPos = pos
                    lastTimestamp = now
                }
            }
        }
    }

    fun updatePresence(song: DesktopSong?, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        if (!_isEnabled.value) {
            ipcClient.clearActivity()
            return
        }

        if (song == null) {
            ipcClient.clearActivity()
            return
        }

        val artists = song.artist.ifBlank { "Unknown Artist" }
        val title = song.title.ifBlank { "Unknown Title" }
        val album = song.album?.takeIf { it.isNotBlank() && it != title }
        val artistAndAlbum = if (album != null) "$artists • $album" else artists
        val now = System.currentTimeMillis()

        // Ambil durasi aktual VLC atau fallback parsing dari text durasi (misal "3:06")
        val totalDuration = if (durationMs > 0L) durationMs else parseDurationText(song.durationText)
        val coverImage = song.thumbnailUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: DEFAULT_ICON_URL

        val buttons = listOf("Echo di GitHub" to "https://github.com/Syauqi227/panlist")

        val activity = if (isPlaying) {
            val start = if (positionMs in 1..totalDuration) now - positionMs else now
            val end = if (totalDuration > 0L && totalDuration > positionMs) start + totalDuration else null

            DiscordActivityPayload(
                type = 2, // 2 = Listening to Echo
                details = title,
                state = artistAndAlbum,
                startTimestamp = start,
                endTimestamp = end,
                largeImage = coverImage,
                largeText = song.album?.takeIf { it.isNotBlank() } ?: title,
                smallImage = DEFAULT_ICON_URL,
                smallText = "Echo Desktop",
                buttons = buttons,
            )
        } else {
            // Mode dijeda (Pause): hilangkan endTimestamp agar progress bar di Discord tidak berjalan
            DiscordActivityPayload(
                type = 2, // 2 = Listening to Echo
                details = title,
                state = "$artistAndAlbum (Dijeda)",
                startTimestamp = null,
                endTimestamp = null,
                largeImage = coverImage,
                largeText = song.album?.takeIf { it.isNotBlank() } ?: title,
                smallImage = DEFAULT_ICON_URL,
                smallText = "Dijeda",
                buttons = buttons,
            )
        }

        ipcClient.setActivity(activity)
    }

    fun close() {
        stop()
    }
}
