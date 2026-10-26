package com.metrolist.desktop.repository

import com.metrolist.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class RecentSearchStore(
    private val storageDir: File = getDefaultStorageDir(),
    private val maxItems: Int = 15,
) {
    companion object {
        fun getDefaultStorageDir(): File {
            val userHome = System.getProperty("user.home") ?: "."
            val echoDir = File(userHome, ".echo")
            val metrolistDir = File(userHome, ".metrolist")
            val targetDir = if (echoDir.exists()) echoDir else if (metrolistDir.exists()) metrolistDir else echoDir
            return targetDir.apply { mkdirs() }
        }
    }

    private val searchHistoryFile = File(storageDir, "recent_searches.json")
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val recentSongs = mutableListOf<DesktopSong>()
    private var isLoaded = false

    suspend fun getRecentSearches(): List<DesktopSong> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            recentSongs.toList()
        }
    }

    suspend fun addRecentSearch(song: DesktopSong) = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            recentSongs.removeAll { it.id == song.id }
            recentSongs.add(0, song)
            if (recentSongs.size > maxItems) {
                while (recentSongs.size > maxItems) {
                    recentSongs.removeAt(recentSongs.size - 1)
                }
            }
            saveToFile()
        }
    }

    suspend fun removeRecentSearch(songId: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            val removed = recentSongs.removeAll { it.id == songId }
            if (removed) {
                saveToFile()
            }
            removed
        }
    }

    suspend fun clearAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            recentSongs.clear()
            saveToFile()
        }
    }

    private fun ensureLoaded() {
        if (isLoaded) return
        recentSongs.clear()
        if (searchHistoryFile.exists()) {
            runCatching {
                val content = searchHistoryFile.readText()
                if (content.isNotBlank()) {
                    val parsed = json.decodeFromString<List<DesktopSong>>(content)
                    recentSongs.addAll(parsed)
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
        isLoaded = true
    }

    private fun saveToFile() {
        runCatching {
            val content = json.encodeToString(recentSongs)
            searchHistoryFile.writeText(content)
        }.onFailure {
            it.printStackTrace()
        }
    }
}
