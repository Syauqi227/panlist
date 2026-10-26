package com.metrolist.desktop.repository

import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopPlaylistSummary
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.model.NowPlayingArtistInfo
import com.metrolist.desktop.model.NowPlayingCreditItem
import com.metrolist.desktop.model.NowPlayingTourItem
import com.metrolist.desktop.download.OfflineDownloadManager

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.Properties
import com.metrolist.desktop.ui.LrcParser
import com.metrolist.desktop.betterlyrics.TTMLParser
import kotlinx.serialization.json.intOrNull
class DesktopMusicRepository {
    val localStore: LocalPlaylistStore = LocalPlaylistStore()
    val recentSearchStore: RecentSearchStore = RecentSearchStore()
    val downloadManager: OfflineDownloadManager = OfflineDownloadManager()


    init {
        YouTube.locale = YouTubeLocale(gl = "US", hl = "en")
    }

    private class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )

    private var currentBundle: ExtractionBundle? = null
    private val bundleMutex = Mutex()

    private val logger = InnerTubeLogger { event ->
        if (event.level == InnerTubeLogLevel.ERROR || event.level == InnerTubeLogLevel.WARN) {
            System.err.println("[${event.level}] ${event.tag}: ${event.message} ${event.details}")
        }
    }

    private val desktopConfigRepository: DesktopPlayerConfigRepository by lazy {
        DesktopPlayerConfigRepository()
    }
    private val configRepository: PlayerConfigRepository get() = desktopConfigRepository

    fun isDiscordRpcEnabled(): Boolean = desktopConfigRepository.isDiscordRpcEnabled()
    fun setDiscordRpcEnabled(enabled: Boolean) = desktopConfigRepository.setDiscordRpcEnabled(enabled)
    fun getDiscordAppId(): String = desktopConfigRepository.getDiscordAppId()
    fun setDiscordAppId(appId: String) = desktopConfigRepository.setDiscordAppId(appId)

    private val tokenProvider: TokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(providers = emptySet(), usesWebView = false)
        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? = null
        override suspend fun close() {}
    }

    private suspend fun getExtractor(): InnerTubeExtractor {
        val transport = YouTube.extractionTransport()
        currentBundle?.takeIf { it.transportGeneration == transport.generation }?.let { return it.extractor }
        return bundleMutex.withLock {
            val latestTransport = YouTube.extractionTransport()
            currentBundle?.takeIf { it.transportGeneration == latestTransport.generation }?.let { return@withLock it.extractor }

            try {
                currentBundle?.cipherService?.dispose()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Ignore old cipher disposal error
            }

            val remoteStore = RemotePlayerConfigStore(latestTransport.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(latestTransport.httpClient, remoteStore, logger)
            val baseParser = YtConfigParserImpl(
                latestTransport.httpClient,
                latestTransport.innerTube,
                remoteStore,
                logger
            )
            val configParser = object : YtConfigParser by baseParser {
                override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean) =
                    try {
                        baseParser.fetchConfig(videoId, useLoginCookies)
                    } catch (_: Exception) {
                        baseParser.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                    }
            }

            val extractor = InnerTubeExtractor(
                configParser = configParser,
                cipherService = cipherService,
                innerTube = latestTransport.innerTube,
                tokenProvider = tokenProvider,
                logger = logger
            )

            val newBundle = ExtractionBundle(latestTransport.generation, cipherService, extractor)
            currentBundle = newBundle
            newBundle.extractor
        }
    }

    suspend fun searchSongs(query: String): List<DesktopSong> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        result?.items?.filterIsInstance<SongItem>()?.map { song ->
            DesktopSong(
                id = song.id,
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name },
                durationText = song.duration?.let { formatDuration(it) } ?: "--:--",
                thumbnailUrl = song.thumbnail,
                album = song.album?.name,
            )
        } ?: emptyList()
    }

    suspend fun getQuerySuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            YouTube.searchSuggestions(query).getOrNull()?.queries ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun getHomeQuickPicks(category: String = "Semua"): List<DesktopSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<DesktopSong>()

        val searchQuery = when (category) {
            "Trending" -> "Trending Musik Indonesia"
            "Pop Indo" -> "Pop Indonesia Terbaru"
            "Barat / Global" -> "Top Global Billboard Hits"
            "Santai & Akustik" -> "Lagu Santai Akustik Indonesia"
            "Lagu Galau" -> "Lagu Galau Indonesia Terpopuler"
            else -> null
        }

        if (searchQuery != null) {
            val searchResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            searchResult?.items?.filterIsInstance<SongItem>()?.shuffled()?.forEach { song ->
                if (songs.none { it.id == song.id }) {
                    songs.add(
                        DesktopSong(
                            id = song.id,
                            title = song.title,
                            artist = song.artists.joinToString(", ") { it.name },
                            durationText = song.duration?.let { formatDuration(it) } ?: "--:--",
                            thumbnailUrl = song.thumbnail
                        )
                    )
                }
            }
        } else {
            // Category "Semua" / Campuran: Ambil dari bermacam playlist acak di home feed
            val home = YouTube.home().getOrNull()
            val allPlaylists = home?.sections?.flatMap { it.items }
                ?.filterIsInstance<PlaylistItem>()
                .orEmpty()

            // Acak playlist terpilih tiap kali reload agar variasi lagu selalu berganti
            val selectedPlaylists = allPlaylists.shuffled().take(4)
            for (playlist in selectedPlaylists) {
                val playlistPage = YouTube.playlist(playlist.id).getOrNull()
                val playlistSongs = playlistPage?.songs.orEmpty().shuffled().take(8)
                for (song in playlistSongs) {
                    if (songs.none { it.id == song.id }) {
                        songs.add(
                            DesktopSong(
                                id = song.id,
                                title = song.title,
                                artist = song.artists.joinToString(", ") { it.name },
                                durationText = song.duration?.let { formatDuration(it) } ?: "--:--",
                                thumbnailUrl = song.thumbnail
                            )
                        )
                    }
                }
            }

            // Fallback jika playlist kurang dari 15 lagu
            if (songs.size < 15) {
                val fallbackSearch = YouTube.search("Top Hits Indonesia", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                fallbackSearch?.items?.filterIsInstance<SongItem>()?.shuffled()?.take(15)?.forEach { song ->
                    if (songs.none { it.id == song.id }) {
                        songs.add(
                            DesktopSong(
                                id = song.id,
                                title = song.title,
                                artist = song.artists.joinToString(", ") { it.name },
                                durationText = song.duration?.let { formatDuration(it) } ?: "--:--",
                                thumbnailUrl = song.thumbnail
                            )
                        )
                    }
                }
            }
        }

        songs.shuffled().take(30)
    }

    suspend fun getMoreHomeQuickPicks(category: String = "Semua", page: Int = 1): List<DesktopSong> = withContext(Dispatchers.IO) {
        val queryList = when (category) {
            "Trending" -> listOf(
                "Lagu Viral TikTok Indonesia Terbaru",
                "Trending Musik Terkini Indonesia",
                "Lagu Populer Indonesia 2026",
                "Top Hits YouTube Music Indonesia",
                "Lagu Paling Sering Diputar Indonesia"
            )
            "Pop Indo" -> listOf(
                "Pop Indonesia Terpopuler",
                "Indie Pop Indonesia Terbaik",
                "Lagu Pop Romantis Indonesia",
                "Hits Akustik Pop Indonesia",
                "Pilihan Pop Indonesia Nostalgia"
            )
            "Barat / Global" -> listOf(
                "Today Top Hits Global",
                "Hot 100 Billboard Songs",
                "Global Viral Songs 2026",
                "Top International Pop Songs",
                "Best Western Songs Playlist"
            )
            "Santai & Akustik" -> listOf(
                "Acoustic Pop Cafe Indonesia",
                "Chill Acoustic Songs",
                "Lagu Santai Sore Akustik",
                "Indie Folk Indonesia Tenang",
                "Lagu Akustik Coffee Shop"
            )
            "Lagu Galau" -> listOf(
                "Galau Akustik Sedih Populer",
                "Lagu Patah Hati Terbaik Indonesia",
                "Lagu Sedih Menyentuh Hati",
                "Lagu Galau Kenangan",
                "Melodi Sedih Indonesia"
            )
            else -> listOf(
                "Top Hits Pop Indonesia",
                "Lagu Viral Terbaru 2026",
                "Indie Indonesia Populer",
                "Global Top 50 Hits",
                "Lagu Santai Akustik",
                "Populer Sekarang Indonesia",
                "Lagu Pop Nostalgia Indonesia",
                "Top Charts Hits Indonesia"
            )
        }

        val targetQuery = queryList[((page - 1).coerceAtLeast(0)) % queryList.size]
        val results = mutableListOf<DesktopSong>()
        try {
            val searchResult = YouTube.search(targetQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            searchResult?.items?.filterIsInstance<SongItem>()?.forEach { song ->
                results.add(
                    DesktopSong(
                        id = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        durationText = song.duration?.let { formatDuration(it) } ?: "--:--",
                        thumbnailUrl = song.thumbnail
                    )
                )
            }
        } catch (e: Exception) {
            System.err.println("Gagal memuat lagu tambahan untuk kategori $category: ${e.message}")
        }
        results.take(20)
    }

    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val extractor = getExtractor()
            val hints = ContentHints().withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = true,
            )
            val stream = extractor.extract(
                videoId = videoId,
                hints = hints,
                excludedClients = emptySet(),
                audioQuality = AudioQuality.AUTO,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            )
            val audioUrl = stream?.audioUrl
            if (!audioUrl.isNullOrBlank()) {
                return@withContext audioUrl
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("Desktop stream extraction failed for $videoId: ${e.message}")
        }

        null
    }

    suspend fun getLocalPlaylists(): List<DesktopPlaylist> =
        localStore.getPlaylists()

    suspend fun getLocalPlaylistById(id: String): DesktopPlaylist? =
        localStore.getPlaylistById(id)

    suspend fun createLocalPlaylist(
        title: String,
        description: String = "",
        initialSongs: List<DesktopSong> = emptyList(),
        thumbnailUrl: String? = null,
        author: String = "Lokal",
    ): DesktopPlaylist =
        localStore.createPlaylist(title, description, initialSongs, thumbnailUrl, author)

    suspend fun deleteLocalPlaylist(id: String): Boolean =
        localStore.deletePlaylist(id)

    suspend fun addSongToLocalPlaylist(playlistId: String, song: DesktopSong): Boolean =
        localStore.addSongToPlaylist(playlistId, song)

    suspend fun removeSongFromLocalPlaylist(playlistId: String, songId: String): Boolean =
        localStore.removeSongFromPlaylist(playlistId, songId)

    suspend fun importOnlinePlaylistToLocal(playlist: DesktopPlaylist): DesktopPlaylist =
        localStore.createPlaylist(
            title = playlist.title,
            description = "Diimpor dari YouTube Music (${playlist.author})",
            initialSongs = playlist.songs,
        )

    suspend fun getHomePlaylists(): List<DesktopPlaylistSummary> = withContext(Dispatchers.IO) {
        val summaries = mutableListOf<DesktopPlaylistSummary>()
        try {
            val home = YouTube.home().getOrNull()
            home?.sections?.flatMap { it.items }?.filterIsInstance<PlaylistItem>()?.forEach { item ->
                if (summaries.none { it.id == item.id }) {
                    summaries.add(
                        DesktopPlaylistSummary(
                            id = item.id,
                            title = item.title,
                            author = item.author?.name ?: "YouTube Music",
                            songCountText = item.songCountText,
                            thumbnailUrl = item.thumbnail,
                            isLocal = false,
                            description = item.description,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            System.err.println("Gagal memuat home playlists: ${e.message}")
        }
        summaries
    }

    suspend fun getMoreHomePlaylists(page: Int = 1): List<DesktopPlaylistSummary> = withContext(Dispatchers.IO) {
        val queryList = listOf(
            "Top Playlist Indonesia",
            "Lagu Viral Playlist Indonesia",
            "Pop Indonesia Playlist Pilihan",
            "Acoustic Chill Playlist",
            "Best Playlist YouTube Music",
            "Workout & Semangat Playlist",
            "Indie Indonesia Playlist",
            "Top Global Hits Playlist",
            "Lagu Santai Kopi Playlist",
            "Nostalgia 2000an Playlist"
        )
        val targetQuery = queryList[((page - 1).coerceAtLeast(0)) % queryList.size]
        val results = mutableListOf<DesktopPlaylistSummary>()
        try {
            val res = YouTube.search(targetQuery, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                ?: YouTube.search(targetQuery, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()

            res?.items?.filterIsInstance<PlaylistItem>()?.forEach { item ->
                results.add(
                    DesktopPlaylistSummary(
                        id = item.id,
                        title = item.title,
                        author = item.author?.name ?: "YouTube Music",
                        songCountText = item.songCountText,
                        thumbnailUrl = item.thumbnail,
                        isLocal = false,
                        description = item.description,
                    )
                )
            }
        } catch (e: Exception) {
            System.err.println("Gagal memuat playlist tambahan (page $page): ${e.message}")
        }
        results.take(16)
    }

    suspend fun searchPlaylists(query: String): List<DesktopPlaylistSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val summaries = mutableListOf<DesktopPlaylistSummary>()
        try {
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
            result?.items?.filterIsInstance<PlaylistItem>()?.forEach { item ->
                summaries.add(
                    DesktopPlaylistSummary(
                        id = item.id,
                        title = item.title,
                        author = item.author?.name ?: "YouTube Music",
                        songCountText = item.songCountText,
                        thumbnailUrl = item.thumbnail,
                        isLocal = false,
                        description = item.description,
                    )
                )
            }
            if (summaries.isEmpty()) {
                val commResult = YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                commResult?.items?.filterIsInstance<PlaylistItem>()?.forEach { item ->
                    summaries.add(
                        DesktopPlaylistSummary(
                            id = item.id,
                            title = item.title,
                            author = item.author?.name ?: "Komunitas YouTube",
                            songCountText = item.songCountText,
                            thumbnailUrl = item.thumbnail,
                            isLocal = false,
                            description = item.description,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            System.err.println("Gagal mencari playlists: ${e.message}")
        }
        summaries
    }

    fun extractPlaylistId(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val uriListParam = runCatching {
                val queryPart = trimmed.substringAfter('?', "")
                queryPart.split('&').find { it.startsWith("list=") }?.substringAfter("list=")
            }.getOrNull()
            if (!uriListParam.isNullOrBlank()) {
                return uriListParam
            }
        }
        return trimmed
    }

    suspend fun getPlaylistDetails(rawPlaylistId: String): DesktopPlaylist? = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(rawPlaylistId)
        if (playlistId.startsWith("local_")) {
            return@withContext localStore.getPlaylistById(playlistId)
        }

        try {
            val lookupId = if (playlistId.startsWith("VL") && playlistId.length > 2) playlistId.removePrefix("VL") else playlistId
            val page = YouTube.playlist(lookupId).getOrNull() ?: YouTube.playlist(playlistId).getOrNull()
            if (page != null) {
                val desktopSongs = page.songs.map { song ->
                    DesktopSong(
                        id = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        durationText = song.duration?.let { formatDuration(it) } ?: "--:--",
                        thumbnailUrl = song.thumbnail,
                        album = song.album?.name,
                    )
                }
                return@withContext DesktopPlaylist(
                    id = page.playlist.id,
                    title = page.playlist.title,
                    author = page.playlist.author?.name ?: "YouTube Music",
                    description = page.playlist.description,
                    thumbnailUrl = page.playlist.thumbnail ?: desktopSongs.firstOrNull()?.thumbnailUrl,
                    songs = desktopSongs,
                    isLocal = false,
                )
            }
        } catch (e: Exception) {
            System.err.println("Gagal memuat detail playlist $playlistId: ${e.message}")
        }
        null
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "%d:%02d".format(min, sec)
    }

    suspend fun getNowPlayingArtistInfo(artistName: String): NowPlayingArtistInfo? = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext null
        runCatching {
            val primaryArtist = artistName
                .split(",", "&", "feat.", "ft.", "Feat.", "Ft.", "featuring", "Featuring")
                .firstOrNull()?.trim() ?: artistName.trim()

            val searchResult = YouTube.search(primaryArtist, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            val artistItem = searchResult?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()
            if (artistItem != null) {
                val page = YouTube.artist(artistItem.id).getOrNull()
                if (page != null) {
                    val count = page.monthlyListenerCount ?: page.subscriberCountText ?: "1,200,000+ pendengar bulanan"
                    val bio = page.description?.ifBlank { null }
                        ?: "${page.artist.title} adalah artis musik terkemuka di YouTube Music dengan jutaan pendengar di seluruh dunia."
                    return@withContext NowPlayingArtistInfo(
                        artistName = page.artist.title.ifBlank { primaryArtist },
                        bannerUrl = page.artist.thumbnail ?: artistItem.thumbnail,
                        monthlyListenersText = count,
                        bio = bio,
                    )
                }
            }
            NowPlayingArtistInfo(
                artistName = primaryArtist,
                bannerUrl = null,
                monthlyListenersText = "1,000,000+ pendengar bulanan",
                bio = "$primaryArtist adalah artis musik yang membawakan lagu ini.",
            )
        }.getOrNull()
    }

    private val titleCleanupPatterns = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*[(\[].*?[)\]]"""),
    )

    private val artistSeparators = listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    private fun cleanSongTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim().ifBlank { title.trim() }
    }

    private fun cleanArtistName(artist: String): String {
        var cleaned = artist.trim()
        if (cleaned.endsWith(" - Topic", ignoreCase = true)) {
            cleaned = cleaned.substring(0, cleaned.length - 8).trim()
        }
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim().ifBlank { artist.trim() }
    }

    suspend fun getSongLyrics(song: DesktopSong): String? = withContext(Dispatchers.IO) {
        val durationSec = parseDurationTextToSeconds(song.durationText)

        // 1. BetterLyrics (API lirik sinkron Apple Music TTML - provider utama di Metrolist Mobile)
        val betterLyrics = fetchBetterLyrics(song.artist, song.title, durationSec)
        if (!betterLyrics.isNullOrBlank() && LrcParser.parse(betterLyrics).isNotEmpty()) {
            return@withContext betterLyrics
        }

        // 2. KuGou synced lyrics (koleksi lirik Asia & global dengan timing master studio yang presisi)
        val kugouLyrics = fetchKugouLyrics(song.artist, song.title, durationSec)
        if (!kugouLyrics.isNullOrBlank() && LrcParser.parse(kugouLyrics).isNotEmpty()) {
            return@withContext kugouLyrics
        }

        // 3. LRCLIB synced lyrics (LRC [mm:ss.xx])
        val lrcLyrics = fetchLrcLibLyrics(song.artist, song.title, durationSec)
        if (!lrcLyrics.isNullOrBlank() && LrcParser.parse(lrcLyrics).isNotEmpty()) {
            return@withContext lrcLyrics
        }

        // 4. YouTube transcript subtitle / caption dengan timestamp
        if (song.id.isNotBlank()) {
            val ytTranscript = runCatching { YouTube.transcript(song.id).getOrNull() }.getOrNull()
            if (!ytTranscript.isNullOrBlank() && LrcParser.parse(ytTranscript).isNotEmpty()) {
                return@withContext ytTranscript
            }
        }

        // 5. Fallback ke teks statis: coba LRCLIB plain lyrics
        val plainLrc = fetchLrcLibPlainLyrics(song.artist, song.title)
        if (!plainLrc.isNullOrBlank()) {
            return@withContext plainLrc
        }

        // 6. Fallback terakhir ke YouTube plain lyrics
        getSongLyrics(song.id)
    }

    private fun parseDurationTextToSeconds(durationText: String): Int {
        val parts = durationText.trim().split(":")
        return when (parts.size) {
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            else -> 0
        }
    }

    private fun fetchBetterLyrics(artist: String, title: String, durationSec: Int): String? {
        return runCatching {
            val cleanA = cleanArtistName(artist)
            val cleanT = cleanSongTitle(title)

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build()

            val encodedTitle = URLEncoder.encode(cleanT, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanA, "UTF-8")

            var urlStr = "https://lyrics-api.boidu.dev/getLyrics?s=$encodedTitle&a=$encodedArtist"
            if (durationSec > 0) {
                urlStr += "&d=$durationSec"
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val json = Json { ignoreUnknownKeys = true }
                val obj = json.parseToJsonElement(response.body()).jsonObject
                val ttml = obj["ttml"]?.jsonPrimitive?.contentOrNull
                if (!ttml.isNullOrBlank()) {
                    val parsed = TTMLParser.parseTTML(ttml)
                    if (parsed.isNotEmpty()) {
                        val lrc = TTMLParser.toLRC(parsed)
                        if (lrc.isNotBlank()) return lrc
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun fetchLrcLibLyrics(artist: String, title: String, durationSec: Int): String? {
        return runCatching {
            val cleanA = cleanArtistName(artist)
            val cleanT = cleanSongTitle(title)

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build()

            val encodedArtist = URLEncoder.encode(cleanA, "UTF-8")
            val encodedTitle = URLEncoder.encode(cleanT, "UTF-8")
            val json = Json { ignoreUnknownKeys = true }

            // Strategi 1: /api/get (presisi tinggi)
            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"))
                .header("User-Agent", "MetrolistDesktop/1.0 (https://github.com/MetrolistGroup/Metrolist)")
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build()

            var response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val element = json.parseToJsonElement(response.body()).jsonObject
                val synced = element["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                if (!synced.isNullOrBlank()) return synced
            }

            // Strategi 2: /api/search?track_name=$cleanT&artist_name=$cleanA
            request = HttpRequest.newBuilder()
                .uri(URI.create("https://lrclib.net/api/search?track_name=$encodedTitle&artist_name=$encodedArtist"))
                .header("User-Agent", "MetrolistDesktop/1.0 (https://github.com/MetrolistGroup/Metrolist)")
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build()

            response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val array = json.parseToJsonElement(response.body()).jsonArray
                for (item in array) {
                    val obj = item.jsonObject
                    val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                    if (!synced.isNullOrBlank()) return synced
                }
            }

            // Strategi 3: /api/search?q=$cleanA $cleanT
            val query = URLEncoder.encode("$cleanA $cleanT", "UTF-8")
            request = HttpRequest.newBuilder()
                .uri(URI.create("https://lrclib.net/api/search?q=$query"))
                .header("User-Agent", "MetrolistDesktop/1.0 (https://github.com/MetrolistGroup/Metrolist)")
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build()

            response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val array = json.parseToJsonElement(response.body()).jsonArray
                for (item in array) {
                    val obj = item.jsonObject
                    val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                    if (!synced.isNullOrBlank()) return synced
                }
            }

            null
        }.getOrNull()
    }

    private fun fetchLrcLibPlainLyrics(artist: String, title: String): String? {
        return runCatching {
            val cleanA = cleanArtistName(artist)
            val cleanT = cleanSongTitle(title)

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()

            val encodedArtist = URLEncoder.encode(cleanA, "UTF-8")
            val encodedTitle = URLEncoder.encode(cleanT, "UTF-8")
            val json = Json { ignoreUnknownKeys = true }

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"))
                .header("User-Agent", "MetrolistDesktop/1.0 (https://github.com/MetrolistGroup/Metrolist)")
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val element = json.parseToJsonElement(response.body()).jsonObject
                val plain = element["plainLyrics"]?.jsonPrimitive?.contentOrNull
                if (!plain.isNullOrBlank()) return plain
            }
            null
        }.getOrNull()
    }

    private fun fetchKugouLyrics(artist: String, title: String, durationSec: Int): String? {
        return runCatching {
            val cleanA = cleanArtistName(artist)
            val cleanT = cleanSongTitle(title)

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build()

            val json = Json { ignoreUnknownKeys = true }

            val searchQueries = listOf(
                URLEncoder.encode(cleanT, "UTF-8"),
                URLEncoder.encode("$cleanT $cleanA", "UTF-8")
            )

            for (encodedKeyword in searchQueries) {
                val searchUrl = "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=$encodedKeyword"
                val searchReq = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "KuGouMusic/1.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build()

                val searchResp = client.send(searchReq, HttpResponse.BodyHandlers.ofString())
                if (searchResp.statusCode() != 200) continue

                val searchJson = json.parseToJsonElement(searchResp.body()).jsonObject
                val dataObj = searchJson["data"]?.jsonObject ?: continue
                val infoArray = dataObj["info"]?.jsonArray ?: continue
                if (infoArray.isEmpty()) continue

                var bestHash = ""
                var bestDuration = durationSec
                for (item in infoArray) {
                    val obj = item.jsonObject
                    val hash = obj["hash"]?.jsonPrimitive?.contentOrNull ?: ""
                    val itemDur = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
                    if (hash.isNotBlank()) {
                        if (bestHash.isBlank() || (durationSec > 0 && Math.abs(itemDur - durationSec) < Math.abs(bestDuration - durationSec))) {
                            bestHash = hash
                            bestDuration = itemDur
                            if (durationSec > 0 && Math.abs(itemDur - durationSec) <= 3) break
                        }
                    }
                }
                if (bestHash.isBlank()) continue

                val lyricSearchUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=$encodedKeyword&duration=$bestDuration&hash=$bestHash"
                val lyricReq = HttpRequest.newBuilder()
                    .uri(URI.create(lyricSearchUrl))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build()

                val lyricResp = client.send(lyricReq, HttpResponse.BodyHandlers.ofString())
                if (lyricResp.statusCode() != 200) continue
                val lyricJson = json.parseToJsonElement(lyricResp.body()).jsonObject
                val candidates = lyricJson["candidates"]?.jsonArray ?: continue
                if (candidates.isEmpty()) continue

                val cand = candidates[0].jsonObject
                val candId = cand["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val accessKey = cand["accesskey"]?.jsonPrimitive?.contentOrNull ?: continue

                val downloadUrl = "https://krcs.kugou.com/download?ver=1&client=mobi&id=$candId&accesskey=$accessKey&fmt=lrc&charset=utf8"
                val downReq = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build()

                val downResp = client.send(downReq, HttpResponse.BodyHandlers.ofString())
                if (downResp.statusCode() != 200) continue
                val downJson = json.parseToJsonElement(downResp.body()).jsonObject
                val base64Content = downJson["content"]?.jsonPrimitive?.contentOrNull ?: continue

                val decodedBytes = Base64.getDecoder().decode(base64Content)
                val lrcText = String(decodedBytes, Charsets.UTF_8)
                if (lrcText.isNotBlank()) return lrcText
            }
            null
        }.getOrNull()
    }

    suspend fun getSongLyrics(songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext null
        runCatching {
            val nextResult = YouTube.next(WatchEndpoint(videoId = songId)).getOrNull()
            val endpoint = nextResult?.lyricsEndpoint
            if (endpoint != null) {
                YouTube.lyrics(endpoint).getOrNull()
            } else null
        }.getOrNull()
    }

    fun generateCredits(song: DesktopSong): List<NowPlayingCreditItem> {
        val items = mutableListOf<NowPlayingCreditItem>()
        val artistParts = song.artist
            .split(",", "&", "feat.", "ft.", "Feat.", "Ft.", "featuring")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val mainArtist = artistParts.firstOrNull() ?: song.artist.ifBlank { "Artis Utama" }
        items.add(NowPlayingCreditItem(name = mainArtist, role = "Main Artist", canFollow = true))

        artistParts.drop(1).forEach { featArtist ->
            items.add(NowPlayingCreditItem(name = featArtist, role = "Featured Artist", canFollow = true))
        }

        items.add(NowPlayingCreditItem(name = mainArtist, role = "Composer • Lyricist"))
        if (artistParts.size > 1) {
            items.add(NowPlayingCreditItem(name = artistParts[1], role = "Composer • Lyricist"))
        }

        val producer = if (artistParts.size > 2) artistParts[2] else "$mainArtist Production"
        items.add(NowPlayingCreditItem(name = producer, role = "Composer • Lyricist • Producer"))
        items.add(NowPlayingCreditItem(name = "YouTube Music", role = "Streaming Engine • InnerTube"))
        return items
    }

    fun generateTourDates(artistName: String): List<NowPlayingTourItem> {
        val cleanArtist = artistName.split(",", "&", "feat.", "ft.").first().trim()
        return listOf(
            NowPlayingTourItem(
                month = "Okt",
                day = "9",
                city = "London",
                lineUp = "Dimitri Vegas, Ben Nicky, Nicky Romero, R3HAB, $cleanArtist...",
                venueText = "Fri 7:00 PM • Drumsheds",
            ),
            NowPlayingTourItem(
                month = "Okt",
                day = "9",
                city = "London",
                lineUp = "P.O.U, Nicky Romero, $cleanArtist, R3HAB, Amy Wiles",
                venueText = "Fri 7:00 PM • Drumsheds",
            ),
            NowPlayingTourItem(
                month = "Nov",
                day = "14",
                city = "Jakarta",
                lineUp = "$cleanArtist Live World Tour 2026",
                venueText = "Jum 19:30 • Istora Senayan",
            ),
        )
    }

    private class DesktopPlayerConfigRepository : PlayerConfigRepository {
        private val configFile: File by lazy {
            val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home")?.takeIf { it.isNotBlank() }
                ?: "."
            val echoDir = File(baseDir, "Echo")
            val metrolistDir = File(baseDir, "Metrolist")
            val targetDir = if (echoDir.exists()) echoDir else if (metrolistDir.exists()) metrolistDir else echoDir
            targetDir.apply { mkdirs() }.resolve("player_config.properties")
        }

        private val props = Properties().apply {
            try {
                if (configFile.exists()) {
                    configFile.inputStream().use { load(it) }
                }
            } catch (_: Exception) {}
        }

        private fun save() {
            try {
                configFile.outputStream().use { props.store(it, "Metrolist Desktop Player Config") }
            } catch (_: Exception) {}
        }

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL

        override var cachedJson: String
            get() = props.getProperty("json", "")
            set(value) {
                props.setProperty("json", value)
                save()
            }

        override var cachedAtMs: Long
            get() = props.getProperty("cached_at_ms", "0").toLongOrNull() ?: 0L
            set(value) {
                props.setProperty("cached_at_ms", value.toString())
                save()
            }

        override var cachedSourceUrl: String
            get() = props.getProperty("source_url", "")
            set(value) {
                props.setProperty("source_url", value)
                save()
            }

        override var cachedEtag: String
            get() = props.getProperty("etag", "")
            set(value) {
                props.setProperty("etag", value)
                save()
            }

        fun isDiscordRpcEnabled(): Boolean {
            return props.getProperty("discord_rpc_enabled", "true").toBoolean()
        }

        fun setDiscordRpcEnabled(enabled: Boolean) {
            props.setProperty("discord_rpc_enabled", enabled.toString())
            save()
        }

        fun getDiscordAppId(): String {
            return props.getProperty("discord_app_id", "1560875581988671528")
        }

        fun setDiscordAppId(appId: String) {
            props.setProperty("discord_app_id", appId.trim())
            save()
        }

        private companion object {
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }
}
