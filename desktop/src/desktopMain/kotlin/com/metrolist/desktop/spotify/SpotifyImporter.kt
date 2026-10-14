package com.metrolist.desktop.spotify

import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

data class SpotifyTrackInfo(
    val title: String,
    val artist: String,
    val durationMs: Long,
)

data class SpotifyPlaylistInfo(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val tracks: List<SpotifyTrackInfo>,
)

object SpotifyImporter {

    private val json = Json { ignoreUnknownKeys = true }

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val urlRegex = Regex("""(?:spotify\.com/playlist/|spotify:playlist:)([a-zA-Z0-9]+)""")
        val match = urlRegex.find(trimmed)
        if (match != null) return match.groupValues[1]
        if (trimmed.length == 22 && trimmed.all { it.isLetterOrDigit() }) return trimmed
        return null
    }

    suspend fun fetchPlaylistMetadata(playlistId: String): SpotifyPlaylistInfo? = withContext(Dispatchers.IO) {
        try {
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val connection = URI(embedUrl).toURL().openConnection() as HttpURLConnection
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val scriptRegex = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = scriptRegex.find(html) ?: return@withContext null

            val root = json.parseToJsonElement(jsonMatch.groupValues[1]).jsonObject
            val entity = root["props"]?.jsonObject
                ?.get("pageProps")?.jsonObject
                ?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject ?: return@withContext null

            val title = entity["name"]?.jsonPrimitive?.content
                ?: entity["title"]?.jsonPrimitive?.content
                ?: "Spotify Playlist"

            val coverUrl = entity["coverArt"]?.jsonObject
                ?.get("sources")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.content

            val trackListJson = entity["trackList"]?.jsonArray.orEmpty()
            val tracks = trackListJson.mapNotNull { item ->
                val obj = item.jsonObject
                val trackTitle = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val artist = obj["subtitle"]?.jsonPrimitive?.content ?: ""
                val duration = obj["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                SpotifyTrackInfo(trackTitle, artist, duration)
            }

            SpotifyPlaylistInfo(playlistId, title, coverUrl, tracks)
        } catch (e: Exception) {
            System.err.println("Failed to fetch Spotify playlist metadata: ${e.message}")
            null
        }
    }

    suspend fun importPlaylist(
        inputUrl: String,
        repository: DesktopMusicRepository,
        onProgress: (current: Int, total: Int, songTitle: String) -> Unit,
    ): Result<DesktopPlaylist> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(inputUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Tautan Spotify tidak valid. Masukkan link playlist Spotify (contoh: https://open.spotify.com/playlist/...)"))

        val metadata = fetchPlaylistMetadata(playlistId)
            ?: return@withContext Result.failure(IllegalStateException("Gagal mengambil data playlist dari Spotify. Pastikan playlist bersifat publik."))

        if (metadata.tracks.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Playlist Spotify kosong atau tidak memiliki lagu yang dapat dibaca."))
        }

        val total = metadata.tracks.size
        val convertedSongs = mutableListOf<DesktopSong>()

        for ((index, track) in metadata.tracks.withIndex()) {
            onProgress(index + 1, total, "${track.title} - ${track.artist}")
            try {
                val query = "${track.title} ${track.artist}".trim()
                var match = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    ?.items?.filterIsInstance<SongItem>()?.firstOrNull()

                if (match == null) {
                    match = YouTube.search(track.title, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                }

                if (match != null) {
                    val durationSeconds = match.duration ?: (track.durationMs / 1000).toInt()
                    val durationText = "%d:%02d".format(durationSeconds / 60, durationSeconds % 60)
                    convertedSongs.add(
                        DesktopSong(
                            id = match.id,
                            title = match.title,
                            artist = match.artists.joinToString(", ") { it.name }.ifBlank { track.artist },
                            durationText = durationText,
                            thumbnailUrl = match.thumbnail,
                        )
                    )
                }
            } catch (e: Exception) {
                System.err.println("Failed to match track '${track.title}': ${e.message}")
            }
        }

        if (convertedSongs.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Tidak ada lagu yang berhasil dicocokkan ke YouTube Music."))
        }

        val createdPlaylist = repository.createLocalPlaylist(
            title = metadata.title,
            description = "Diimpor dari Spotify (${convertedSongs.size} dari ${metadata.tracks.size} lagu)",
            initialSongs = convertedSongs,
            thumbnailUrl = metadata.coverUrl,
            author = "Spotify",
        )

        Result.success(createdPlaylist)
    }
}
