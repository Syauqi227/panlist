package com.metrolist.desktop.discord

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.VlcAudioPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

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

    private data class PresenceTrigger(
        val song: DesktopSong?,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val durationMs: Long,
        val seekSeq: Long,
    )

    val ipcClient = DiscordIpcClient(initialClientId, scope)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isEnabled = MutableStateFlow(initialEnabled)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _lastSentActivity = MutableStateFlow<DiscordActivityPayload?>(null)
    val lastSentActivity: StateFlow<DiscordActivityPayload?> = _lastSentActivity.asStateFlow()

    private var syncJob: Job? = null

    @Volatile
    private var lastSentStartTimestampMs: Long? = null

    @Volatile
    private var lastSentSongId: String? = null

    @Volatile
    private var lastSentActivelyPlaying: Boolean = false

    init {
        ipcClient.onStatusChanged = { connected ->
            _isConnected.value = connected
            if (connected && _isEnabled.value) {
                scope.launch {
                    updatePresence(
                        song = playbackManager.currentSong.value,
                        isPlaying = player.isPlaying.value,
                        positionMs = player.currentPosition.value,
                        durationMs = player.duration.value,
                        isBuffering = playbackManager.isBuffering.value,
                    )
                }
            }
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
        resetTracking()
        _lastSentActivity.value = null
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

    private fun resetTracking() {
        lastSentStartTimestampMs = null
        lastSentSongId = null
        lastSentActivelyPlaying = false
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            // 1. Sinkronisasi atomik lagu, status Play/Pause, Buffering/menunggu sinyal, durasi, dan seek
            launch {
                combine(
                    playbackManager.currentSong,
                    player.isPlaying,
                    playbackManager.isBuffering,
                    player.duration,
                    player.seekSequence,
                ) { song, isPlaying, isBuffering, duration, seekSeq ->
                    PresenceTrigger(song, isPlaying, isBuffering, duration, seekSeq)
                }.collectLatest { trigger ->
                    updatePresence(
                        song = trigger.song,
                        isPlaying = trigger.isPlaying,
                        positionMs = player.currentPosition.value,
                        durationMs = trigger.durationMs,
                        isBuffering = trigger.isBuffering,
                    )
                }
            }

            // 2. Sinkronisasi pergeseran posisi / seek atau pemulihan posisi setelah buffering
            launch {
                player.currentPosition.collectLatest { pos ->
                    val activeSong = playbackManager.currentSong.value ?: return@collectLatest
                    val isBufferingNow = playbackManager.isBuffering.value
                    val isActivelyPlaying = player.isPlaying.value && !isBufferingNow
                    if (!isActivelyPlaying) return@collectLatest

                    val sentStart = lastSentStartTimestampMs
                    if (sentStart != null && lastSentActivelyPlaying && lastSentSongId == activeSong.id) {
                        val expectedDiscordPos = System.currentTimeMillis() - sentStart
                        if (kotlin.math.abs(pos - expectedDiscordPos) > 1500L) {
                            updatePresence(
                                song = activeSong,
                                isPlaying = true,
                                positionMs = pos,
                                durationMs = player.duration.value,
                                isBuffering = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun buildActivityPayload(
        song: DesktopSong?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        isBuffering: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): DiscordActivityPayload? {
        if (song == null) return null

        val artists = song.artist.ifBlank { "Unknown Artist" }
        val title = song.title.ifBlank { "Unknown Title" }
        val album = song.album?.takeIf { it.isNotBlank() && it != title }
        val artistAndAlbum = if (album != null) "$artists • $album" else artists

        val totalDuration = if (durationMs > 0L) durationMs else parseDurationText(song.durationText)
        val coverImage = song.thumbnailUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: DEFAULT_ICON_URL
        val buttons = listOf("Echo di GitHub" to "https://github.com/Syauqi227/panlist")

        return when {
            isBuffering -> {
                // Buffering / menunggu sinyal: hentikan timer durasi agar tidak bertambah terus
                DiscordActivityPayload(
                    type = 2,
                    details = title,
                    state = "$artistAndAlbum (Buffering)",
                    startTimestamp = null,
                    endTimestamp = null,
                    largeImage = coverImage,
                    largeText = song.album?.takeIf { it.isNotBlank() } ?: title,
                    smallImage = DEFAULT_ICON_URL,
                    smallText = "Buffering",
                    buttons = buttons,
                )
            }
            !isPlaying -> {
                // Dijeda (Paused): hentikan timer durasi agar waktu di Discord ikut berhenti
                DiscordActivityPayload(
                    type = 2,
                    details = title,
                    state = "$artistAndAlbum (Paused)",
                    startTimestamp = null,
                    endTimestamp = null,
                    largeImage = coverImage,
                    largeText = song.album?.takeIf { it.isNotBlank() } ?: title,
                    smallImage = DEFAULT_ICON_URL,
                    smallText = "Paused",
                    buttons = buttons,
                )
            }
            else -> {
                val safePos = if (totalDuration > 0L) {
                    positionMs.coerceIn(0L, totalDuration)
                } else {
                    positionMs.coerceAtLeast(0L)
                }
                val start = nowMs - safePos
                val end = if (totalDuration > 0L) start + totalDuration else null

                DiscordActivityPayload(
                    type = 2,
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
            }
        }
    }

    fun updatePresence(
        song: DesktopSong?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        isBuffering: Boolean = false,
    ) {
        if (!_isEnabled.value || song == null) {
            resetTracking()
            _lastSentActivity.value = null
            ipcClient.clearActivity()
            return
        }

        val now = System.currentTimeMillis()
        val activity = buildActivityPayload(
            song = song,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            isBuffering = isBuffering,
            nowMs = now,
        )

        if (activity == null) {
            resetTracking()
            _lastSentActivity.value = null
            ipcClient.clearActivity()
            return
        }

        lastSentSongId = song.id
        lastSentActivelyPlaying = isPlaying && !isBuffering
        lastSentStartTimestampMs = activity.startTimestamp
        _lastSentActivity.value = activity

        ipcClient.setActivity(activity)
    }

    fun close() {
        _isEnabled.value = false
        syncJob?.cancel()
        resetTracking()
        _lastSentActivity.value = null
        ipcClient.stopBlocking(timeoutMs = 800L)
    }
}
