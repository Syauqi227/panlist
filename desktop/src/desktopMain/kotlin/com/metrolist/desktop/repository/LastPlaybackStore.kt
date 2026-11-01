package com.metrolist.desktop.repository

import com.metrolist.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LastPlaybackState(
    val currentSong: DesktopSong,
    val queue: List<DesktopSong> = emptyList(),
    val currentIndex: Int = 0,
    val playlistTitle: String? = null,
    val lastPositionMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
)

class LastPlaybackStore(
    private val storageDir: File = LocalPlaylistStore.getDefaultStorageDir(),
) {
    private val stateFile = File(storageDir, "last_playback.json")
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun saveLastPlayback(
        song: DesktopSong,
        queue: List<DesktopSong>,
        currentIndex: Int,
        playlistTitle: String? = null,
        positionMs: Long = 0L,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                storageDir.mkdirs()
                val state = LastPlaybackState(
                    currentSong = song,
                    queue = queue,
                    currentIndex = currentIndex,
                    playlistTitle = playlistTitle,
                    lastPositionMs = positionMs,
                )
                val jsonStr = json.encodeToString(state)
                stateFile.writeText(jsonStr)
            } catch (_: Exception) {
                // Silently ignore write failures to avoid interrupting playback
            }
        }
    }

    suspend fun loadLastPlayback(): LastPlaybackState? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!stateFile.exists()) return@withContext null
                val content = stateFile.readText()
                if (content.isBlank()) return@withContext null
                json.decodeFromString<LastPlaybackState>(content)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun clearLastPlayback() = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (stateFile.exists()) {
                    stateFile.delete()
                }
            } catch (_: Exception) {}
        }
    }
}
