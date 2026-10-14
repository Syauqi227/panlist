package com.metrolist.desktop.download

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class OfflineDownloadManager {

    val downloadsDir: File by lazy {
        val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")?.takeIf { it.isNotBlank() }
            ?: "."
        File(baseDir, "Metrolist/downloads").apply { mkdirs() }
    }

    private val indexFile by lazy { File(downloadsDir, "downloads_index.json") }
    private val mutex = Mutex()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

    private val _activeDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeDownloadProgress: StateFlow<Map<String, Float>> = _activeDownloadProgress.asStateFlow()

    private val cachedSongs = mutableMapOf<String, DesktopSong>()
    private var isLoaded = false

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) {
            isLoaded = true
            return
        }
        try {
            val content = indexFile.readText()
            if (content.isNotBlank()) {
                val list = json.decodeFromString<List<DesktopSong>>(content)
                val validSongs = list.filter { song ->
                    val file = File(downloadsDir, "${song.id}.audio")
                    file.exists() && file.length() > 10 * 1024
                }
                cachedSongs.clear()
                validSongs.forEach { cachedSongs[it.id] = it }
                _downloadedSongIds.value = cachedSongs.keys.toSet()
            }
        } catch (e: Exception) {
            System.err.println("Error loading download index: ${e.message}")
        }
        isLoaded = true
    }

    private fun saveIndex() {
        try {
            val list = cachedSongs.values.toList()
            indexFile.writeText(json.encodeToString(list))
            _downloadedSongIds.value = cachedSongs.keys.toSet()
        } catch (e: Exception) {
            System.err.println("Error saving download index: ${e.message}")
        }
    }

    fun isSongDownloaded(songId: String): Boolean {
        if (!isLoaded) loadIndex()
        if (!_downloadedSongIds.value.contains(songId)) return false
        val file = File(downloadsDir, "$songId.audio")
        return file.exists() && file.length() > 10 * 1024
    }

    fun getDownloadedAudioFile(songId: String): File? {
        val file = File(downloadsDir, "$songId.audio")
        return if (file.exists() && file.length() > 10 * 1024) file else null
    }

    fun getDownloadedSongs(): List<DesktopSong> {
        if (!isLoaded) loadIndex()
        return cachedSongs.values.toList()
    }

    suspend fun downloadSong(
        song: DesktopSong,
        repository: DesktopMusicRepository,
    ): Result<File> = withContext(Dispatchers.IO) {
        val finalFile = File(downloadsDir, "${song.id}.audio")
        if (finalFile.exists() && finalFile.length() > 10 * 1024) {
            mutex.withLock {
                cachedSongs[song.id] = song
                saveIndex()
            }
            return@withContext Result.success(finalFile)
        }

        val tempFile = File(downloadsDir, "temp_${song.id}_${System.currentTimeMillis()}.tmp")

        try {
            _activeDownloadProgress.value = _activeDownloadProgress.value + (song.id to 0.05f)

            val streamUrl = repository.resolveStreamUrl(song.id)
                ?: return@withContext Result.failure(IllegalStateException("Tidak dapat memperoleh stream audio untuk '${song.title}'."))

            _activeDownloadProgress.value = _activeDownloadProgress.value + (song.id to 0.15f)

            var lastProgressUpdate = 0L
            val downloadOk = downloadFastWithChunks(
                streamUrl = streamUrl,
                tempFile = tempFile,
            ) { fraction ->
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > 100 || fraction >= 0.99f) {
                    lastProgressUpdate = now
                    _activeDownloadProgress.value = _activeDownloadProgress.value + (song.id to fraction)
                }
            }

            if (!downloadOk || tempFile.length() < 10 * 1024) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("File audio yang diunduh rusak atau gagal diterima."))
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            mutex.withLock {
                cachedSongs[song.id] = song
                saveIndex()
            }

            _activeDownloadProgress.value = _activeDownloadProgress.value - song.id
            Result.success(finalFile)
        } catch (e: Exception) {
            tempFile.delete()
            _activeDownloadProgress.value = _activeDownloadProgress.value - song.id
            System.err.println("Error downloading song '${song.title}': ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Downloads audio using chunked HTTP Range requests to bypass YouTube/Google Video
     * server-side bandwidth throttling (playback pacing).
     */
    private fun downloadFastWithChunks(
        streamUrl: String,
        tempFile: File,
        updateProgress: (Float) -> Unit,
    ): Boolean {
        val chunkSize = 1024 * 1024L // 1 MB chunk per range request to get full burst bandwidth

        // Initial request for first chunk
        val firstConn = URI(streamUrl).toURL().openConnection() as HttpURLConnection
        firstConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        firstConn.setRequestProperty("Range", "bytes=0-${chunkSize - 1}")
        firstConn.connectTimeout = 15000
        firstConn.readTimeout = 25000

        val firstCode = firstConn.responseCode
        if (firstCode == 206) {
            // Partial content supported! Extract total bytes from Content-Range header (e.g. "bytes 0-1048575/3748308")
            val contentRange = firstConn.getHeaderField("Content-Range")
            val totalBytes = contentRange?.substringAfterLast('/')?.toLongOrNull() ?: -1L

            tempFile.outputStream().use { output ->
                firstConn.inputStream.use { it.copyTo(output) }
                var downloadedBytes = tempFile.length()

                if (totalBytes > 0) {
                    updateProgress(0.15f + (downloadedBytes.toFloat() / totalBytes) * 0.84f)
                }

                while (totalBytes > 0 && downloadedBytes < totalBytes) {
                    val nextStart = downloadedBytes
                    val nextEnd = (nextStart + chunkSize - 1).coerceAtMost(totalBytes - 1)

                    val chunkConn = URI(streamUrl).toURL().openConnection() as HttpURLConnection
                    chunkConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    chunkConn.setRequestProperty("Range", "bytes=$nextStart-$nextEnd")
                    chunkConn.connectTimeout = 15000
                    chunkConn.readTimeout = 25000

                    val code = chunkConn.responseCode
                    if (code != 206) {
                        return false
                    }

                    chunkConn.inputStream.use { it.copyTo(output) }
                    downloadedBytes = tempFile.length()
                    if (totalBytes > 0) {
                        updateProgress(0.15f + (downloadedBytes.toFloat() / totalBytes) * 0.84f)
                    }
                }
            }
            return true
        } else if (firstCode in 200..299) {
            // Server returned full stream instead of Range
            val totalBytes = firstConn.contentLengthLong.takeIf { it > 0 } ?: -1L
            tempFile.outputStream().use { output ->
                firstConn.inputStream.use { input ->
                    val buffer = ByteArray(65536)
                    var downloadedBytes = 0L
                    var lastUpdate = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val now = System.currentTimeMillis()
                        if (totalBytes > 0 && now - lastUpdate > 120) {
                            lastUpdate = now
                            updateProgress(0.15f + (downloadedBytes.toFloat() / totalBytes) * 0.84f)
                        }
                    }
                }
            }
            return true
        } else {
            return false
        }
    }

    suspend fun deleteDownload(songId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(downloadsDir, "$songId.audio")
            if (file.exists()) file.delete()
            cachedSongs.remove(songId)
            saveIndex()
            true
        }
    }

    /**
     * Downloads an entire playlist concurrently using a worker pool (Semaphore),
     * drastically reducing total download time compared to sequential downloading.
     */
    suspend fun downloadPlaylist(
        songs: List<DesktopSong>,
        repository: DesktopMusicRepository,
        onProgress: (current: Int, total: Int, song: DesktopSong) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val total = songs.size
        if (total == 0) return@withContext 0

        val completedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val semaphore = Semaphore(3) // 3 concurrent song downloads for high throughput

        coroutineScope {
            songs.map { song ->
                async {
                    semaphore.withPermit {
                        if (!isSongDownloaded(song.id)) {
                            val res = downloadSong(song, repository)
                            if (res.isSuccess) {
                                successCount.incrementAndGet()
                            }
                        } else {
                            successCount.incrementAndGet()
                        }
                        val current = completedCount.incrementAndGet()
                        onProgress(current, total, song)
                    }
                }
            }.awaitAll()
        }

        successCount.get()
    }
}
