package com.metrolist.desktop.repository

import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class LocalPlaylistStore(
    private val storageDir: File = getDefaultStorageDir(),
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

    private val playlistFile = File(storageDir, "playlists.json")
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val playlists = mutableListOf<DesktopPlaylist>()
    private var isLoaded = false

    suspend fun getPlaylists(): List<DesktopPlaylist> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            playlists.toList()
        }
    }

    suspend fun getPlaylistById(id: String): DesktopPlaylist? = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            playlists.find { it.id == id }
        }
    }

    suspend fun createPlaylist(
        title: String,
        description: String = "",
        initialSongs: List<DesktopSong> = emptyList(),
        thumbnailUrl: String? = null,
        author: String = "Lokal",
    ): DesktopPlaylist = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            val newPlaylist = DesktopPlaylist(
                id = "local_" + UUID.randomUUID().toString().replace("-", "").take(12),
                title = title.trim(),
                author = author,
                description = description.ifBlank { null },
                thumbnailUrl = thumbnailUrl ?: initialSongs.firstOrNull()?.thumbnailUrl,
                songs = initialSongs,
                isLocal = true,
                createdAt = System.currentTimeMillis(),
            )
            playlists.add(0, newPlaylist)
            saveToFile()
            newPlaylist
        }
    }

    suspend fun deletePlaylist(id: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            val removed = playlists.removeAll { it.id == id }
            if (removed) {
                saveToFile()
            }
            removed
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: DesktopSong): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            val index = playlists.indexOfFirst { it.id == playlistId }
            if (index == -1) return@withContext false

            val current = playlists[index]
            if (current.songs.any { it.id == song.id }) return@withContext true

            val updatedSongs = current.songs + song
            val updated = current.copy(
                songs = updatedSongs,
                thumbnailUrl = current.thumbnailUrl ?: song.thumbnailUrl,
            )
            playlists[index] = updated
            saveToFile()
            true
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            val index = playlists.indexOfFirst { it.id == playlistId }
            if (index == -1) return@withContext false

            val current = playlists[index]
            val updatedSongs = current.songs.filterNot { it.id == songId }
            val updated = current.copy(
                songs = updatedSongs,
                thumbnailUrl = updatedSongs.firstOrNull()?.thumbnailUrl,
            )
            playlists[index] = updated
            saveToFile()
            true
        }
    }

    suspend fun updatePlaylist(playlist: DesktopPlaylist): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            val index = playlists.indexOfFirst { it.id == playlist.id }
            if (index == -1) return@withContext false
            playlists[index] = playlist
            saveToFile()
            true
        }
    }

    private fun ensureLoaded() {
        if (isLoaded) return
        playlists.clear()
        if (playlistFile.exists()) {
            try {
                val content = playlistFile.readText()
                if (content.isNotBlank()) {
                    val loaded = json.decodeFromString<List<DesktopPlaylist>>(content)
                    playlists.addAll(loaded)
                }
            } catch (e: Exception) {
                System.err.println("Gagal memuat local playlists: ${e.message}")
            }
        }

        if (playlists.isEmpty()) {
            val userHome = System.getProperty("user.home") ?: "."
            val legacyMetrolistFile = File(File(userHome, ".metrolist"), "playlists.json")
            var migrated = false
            if (storageDir == getDefaultStorageDir() && legacyMetrolistFile.exists()) {
                try {
                    val legacyContent = legacyMetrolistFile.readText()
                    if (legacyContent.isNotBlank()) {
                        val legacyPlaylists = json.decodeFromString<List<DesktopPlaylist>>(legacyContent)
                        if (legacyPlaylists.isNotEmpty()) {
                            playlists.addAll(legacyPlaylists)
                            saveToFile()
                            migrated = true
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("Gagal migrasi playlist lama dari .metrolist: ${e.message}")
                }
            }

            if (!migrated) {
                val defaultFavorite = DesktopPlaylist(
                    id = "local_favorites",
                    title = "Favorit Saya",
                    author = "Lokal",
                    description = "Kumpulan lagu-lagu favorit pilihan Anda",
                    thumbnailUrl = null,
                    songs = emptyList(),
                    isLocal = true,
                    createdAt = System.currentTimeMillis(),
                )
                playlists.add(defaultFavorite)
                saveToFile()
            }
        }

        isLoaded = true
    }

    private fun saveToFile() {
        try {
            storageDir.mkdirs()
            val text = json.encodeToString(playlists)
            val tempFile = File(storageDir, "playlists.json.tmp")
            tempFile.writeText(text)
            val renamed = tempFile.renameTo(playlistFile)
            if (!renamed || tempFile.exists()) {
                playlistFile.writeText(text)
                tempFile.delete()
            }
        } catch (e: Exception) {
            System.err.println("Gagal menyimpan local playlists: ${e.message}")
        }
    }
}
