package com.metrolist.desktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.*
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.launch

@Composable
fun NowPlayingView(
    repository: DesktopMusicRepository,
    playbackManager: DesktopPlaybackManager,
    onClose: () -> Unit,
    onAddToPlaylist: (DesktopSong) -> Unit,
    onOpenLyrics: (() -> Unit)? = null,
) {
    val currentSong by playbackManager.currentSong.collectAsState()
    val isPlaying by playbackManager.player.isPlaying.collectAsState()
    val queue by playbackManager.queue.collectAsState()
    val currentIndex by playbackManager.currentIndex.collectAsState()
    val currentPlaylistTitle by playbackManager.currentPlaylistTitle.collectAsState()

    var artistInfo by remember(currentSong?.id) { mutableStateOf<NowPlayingArtistInfo?>(null) }
    var isLoadingArtist by remember(currentSong?.id) { mutableStateOf(true) }
    var accentColor by remember(currentSong?.id) {
        mutableStateOf(NowPlayingColorExtractor.getAtmosphericColor(currentSong?.id ?: ""))
    }

    var isFollowingArtist by remember(currentSong?.id) { mutableStateOf(false) }
    var isBioExpanded by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showTourDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var lyricsText by remember(currentSong?.id) { mutableStateOf<String?>(null) }
    var isLoadingLyrics by remember(currentSong?.id) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Load artist info, dominant color, and lyrics
    LaunchedEffect(currentSong?.id) {
        val song = currentSong ?: return@LaunchedEffect
        isLoadingArtist = true
        artistInfo = repository.getNowPlayingArtistInfo(song.artist)
        isLoadingArtist = false

        val sampledColor = NowPlayingColorExtractor.extractOrSampleColor(song.id, song.thumbnailUrl)
        accentColor = sampledColor
    }

    // Modal Dialog Kredit Lengkap
    if (showCreditsDialog && currentSong != null) {
        val credits = remember(currentSong) { repository.generateCredits(currentSong!!) }
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = {
                Text(
                    text = "Kredit Lagu: ${currentSong!!.title}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = DesktopColors.Paper
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    credits.forEach { credit ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = credit.name,
                                    fontWeight = FontWeight.Medium,
                                    color = DesktopColors.Paper,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = credit.role,
                                    fontSize = 12.sp,
                                    color = DesktopColors.Fog,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("Tutup", color = DesktopColors.AcidLime)
                }
            },
            containerColor = DesktopColors.Obsidian,
            shape = DesktopRadii.Card,
        )
    }

    // Modal Dialog Jadwal Tur Lengkap
    if (showTourDialog && currentSong != null) {
        val tours = remember(currentSong) { repository.generateTourDates(currentSong!!.artist) }
        AlertDialog(
            onDismissRequest = { showTourDialog = false },
            title = {
                Text(
                    text = "Jadwal Tur & Konser: ${currentSong!!.artist}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = DesktopColors.Paper
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    tours.forEach { tour ->
                        TourRowItem(tour = tour)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTourDialog = false }) {
                    Text("Tutup", color = DesktopColors.AcidLime)
                }
            },
            containerColor = DesktopColors.Obsidian,
            shape = DesktopRadii.Card,
        )
    }

    // Modal Dialog Lirik Lagu
    if (showLyricsDialog && currentSong != null) {
        val currentPosition by playbackManager.player.currentPosition.collectAsState()
        LaunchedEffect(currentSong?.id) {
            if (lyricsText == null && !isLoadingLyrics) {
                isLoadingLyrics = true
                lyricsText = repository.getSongLyrics(currentSong!!)
                isLoadingLyrics = false
            }
        }

        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = {
                val hasSyncedLyrics = remember(lyricsText) {
                    !lyricsText.isNullOrBlank() && LrcParser.parse(lyricsText!!).isNotEmpty()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = DesktopColors.AcidLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Lirik Lagu — ${currentSong!!.title}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = DesktopColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (hasSyncedLyrics) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(DesktopRadii.Badge)
                                .background(DesktopColors.AcidLime.copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "♪ SINKRON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = DesktopColors.AcidLime
                            )
                        }
                    } else if (!lyricsText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(DesktopRadii.Badge)
                                .background(DesktopColors.Slate)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TEKS BIASA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = DesktopColors.Mist
                            )
                        }
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 480.dp)
                ) {
                    if (isLoadingLyrics) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DesktopColors.AcidLime)
                        }
                    } else if (lyricsText.isNullOrBlank()) {
                        Text(
                            text = "Lirik untuk lagu ini belum tersedia dari penyedia musik.",
                            color = DesktopColors.Fog,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    } else {
                        SyncedLyricsView(
                            lyricsText = lyricsText!!,
                            currentPosition = currentPosition,
                            onSeek = { playbackManager.player.seekTo(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text("Tutup", color = DesktopColors.AcidLime)
                }
            },
            containerColor = DesktopColors.Obsidian,
            shape = DesktopRadii.Card,
        )
    }

    // Atmospheric Gradient Wash fading into Carbon & Void
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.25f),
                        DesktopColors.Carbon,
                        DesktopColors.Void,
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Bar Navigasi (Sesuai Screenshot 1 Spotify)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Judul Playlist / Sumber Lagu di sebelah kiri
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(34.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Tampilan Sebelumnya",
                            tint = DesktopColors.Paper,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentPlaylistTitle?.let { it.ifBlank { "Sedang Diputar" } } ?: "Sedang Diputar",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = DesktopColors.Paper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Aksi di sebelah kanan: Lirik, Antrean, Tambah ke Playlist, Tutup
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (onOpenLyrics != null) {
                                onOpenLyrics()
                            } else {
                                showLyricsDialog = true
                                if (lyricsText == null && !isLoadingLyrics && currentSong != null) {
                                    scope.launch {
                                        isLoadingLyrics = true
                                        lyricsText = repository.getSongLyrics(currentSong!!)
                                        isLoadingLyrics = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Tampilkan Lirik Lagu",
                            tint = DesktopColors.Fog,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    if (currentSong != null) {
                        IconButton(
                            onClick = { onAddToPlaylist(currentSong!!) },
                            modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Tambahkan ke Playlist",
                                tint = DesktopColors.Fog,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Ciutkan Tampilan Now Playing",
                            tint = DesktopColors.Paper,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            // 2. Konten Scrollable (Album Cover Hero di atas + Kartu Info di bawah)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val windowWidth = maxWidth
                val isCompact = windowWidth < 880.dp
                val albumSize = if (windowWidth < 700.dp) 260.dp else if (windowWidth < 1000.dp) 320.dp else 360.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = if (isCompact) 20.dp else 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // HERO: Album Artwork Besar Terpusat
                    Box(
                        modifier = Modifier
                            .size(albumSize)
                            .clip(DesktopRadii.Card)
                            .background(DesktopColors.Carbon)
                            .border(1.dp, DesktopColors.Border, DesktopRadii.Card),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!currentSong?.thumbnailUrl.isNullOrBlank()) {
                            val cachedBitmap = remember(currentSong?.id) {
                                currentSong?.id?.let { NowPlayingColorExtractor.getCachedBitmap(it) }
                            }
                            if (cachedBitmap != null) {
                                Image(
                                    bitmap = cachedBitmap,
                                    contentDescription = currentSong?.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                AsyncImage(
                                    model = currentSong?.thumbnailUrl?.resize(500, 500),
                                    contentDescription = currentSong?.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = DesktopColors.Ash,
                                modifier = Modifier.size(80.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Judul Lagu & Nama Artis
                    Text(
                        text = currentSong?.title ?: "Tidak Ada Lagu yang Diputar",
                        fontSize = if (isCompact) 20.sp else 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopColors.Paper,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentSong?.artist ?: "Pilih lagu untuk memulai",
                        fontSize = 15.sp,
                        color = DesktopColors.Mist,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(44.dp))

                    // KARTU-KARTU INFORMASI (Screenshot 2: About the Artist, Credits, On Tour, Next in Queue)
                    if (isCompact) {
                        // Tata letak 1 kolom untuk layar ramping
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            AboutTheArtistCard(
                                artistName = artistInfo?.artistName ?: currentSong?.artist ?: "Artis",
                                bannerUrl = artistInfo?.bannerUrl ?: currentSong?.thumbnailUrl,
                                monthlyListeners = artistInfo?.monthlyListenersText ?: "1,200,000+ pendengar bulanan",
                                bio = artistInfo?.bio ?: "Artis musik di YouTube Music.",
                                isFollowing = isFollowingArtist,
                                onToggleFollow = { isFollowingArtist = !isFollowingArtist },
                                isBioExpanded = isBioExpanded,
                                onToggleBioExpanded = { isBioExpanded = !isBioExpanded },
                            )

                            CreditsCard(
                                song = currentSong,
                                repository = repository,
                                onShowAll = { showCreditsDialog = true },
                            )

                            OnTourCard(
                                artistName = currentSong?.artist ?: "Artis",
                                repository = repository,
                                onShowAll = { showTourDialog = true },
                            )

                            NextInQueueCard(
                                queue = queue,
                                currentIndex = currentIndex,
                                onPlaySong = { nextSong -> playbackManager.playSong(nextSong, queue) }
                            )
                        }
                    } else {
                        // Tata letak 2 kolom elegan persis seperti tampilan Spotify Desktop
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Kolom Kiri: "About the artist"
                            Box(modifier = Modifier.weight(1.1f)) {
                                AboutTheArtistCard(
                                    artistName = artistInfo?.artistName ?: currentSong?.artist ?: "Artis",
                                    bannerUrl = artistInfo?.bannerUrl ?: currentSong?.thumbnailUrl,
                                    monthlyListeners = artistInfo?.monthlyListenersText ?: "1,213,883 monthly listeners",
                                    bio = artistInfo?.bio ?: "Artis musik populer di YouTube Music.",
                                    isFollowing = isFollowingArtist,
                                    onToggleFollow = { isFollowingArtist = !isFollowingArtist },
                                    isBioExpanded = isBioExpanded,
                                    onToggleBioExpanded = { isBioExpanded = !isBioExpanded },
                                )
                            }

                            // Kolom Kanan: "Credits" dan "On tour"
                            Column(
                                modifier = Modifier.weight(0.9f),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                CreditsCard(
                                    song = currentSong,
                                    repository = repository,
                                    onShowAll = { showCreditsDialog = true },
                                )

                                OnTourCard(
                                    artistName = currentSong?.artist ?: "Artis",
                                    repository = repository,
                                    onShowAll = { showTourDialog = true },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Bagian Bawah Penuh: "Next in queue"
                        NextInQueueCard(
                            queue = queue,
                            currentIndex = currentIndex,
                            onPlaySong = { nextSong -> playbackManager.playSong(nextSong, queue) }
                        )
                    }

                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }
}

/**
 * Kartu "About the artist" persis seperti di Spotify
 */
@Composable
private fun AboutTheArtistCard(
    artistName: String,
    bannerUrl: String?,
    monthlyListeners: String,
    bio: String,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    isBioExpanded: Boolean,
    onToggleBioExpanded: () -> Unit,
) {
    Card(
        shape = DesktopRadii.Card,
        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
        border = BorderStroke(1.dp, DesktopColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Card
            Text(
                text = "About the artist",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = DesktopColors.Paper,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            // Banner Gambar Artis dengan Gradient Gelap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(DesktopColors.Obsidian)
            ) {
                if (!bannerUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = bannerUrl,
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Scrim Gradient Hitam ke Atas agar Teks Tajam & Kontras
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    DesktopColors.Void.copy(alpha = 0.40f),
                                    DesktopColors.Void.copy(alpha = 0.95f),
                                )
                            )
                        )
                )

                // Info Artis di Bagian Bawah Banner
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = artistName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        color = DesktopColors.Paper,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = monthlyListeners,
                        fontSize = 13.sp,
                        color = DesktopColors.Mist,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Tombol Follow Pill Button
                    Surface(
                        onClick = onToggleFollow,
                        shape = DesktopRadii.Pill,
                        color = if (isFollowing) DesktopColors.AcidLime else DesktopColors.Obsidian,
                        border = if (isFollowing) null else BorderStroke(1.dp, DesktopColors.Border),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isFollowing) DesktopColors.Void else DesktopColors.Paper,
                            )
                        }
                    }
                }
            }

            // Bio Deskripsi Artis
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text = bio,
                    fontSize = 13.sp,
                    color = DesktopColors.Mist,
                    lineHeight = 19.sp,
                    maxLines = if (isBioExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bio.length > 120) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isBioExpanded) "Show less" else "Show more",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = DesktopColors.AcidLime,
                        modifier = Modifier
                            .clickable { onToggleBioExpanded() }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Kartu "Credits" persis seperti di Spotify
 */
@Composable
private fun CreditsCard(
    song: DesktopSong?,
    repository: DesktopMusicRepository,
    onShowAll: () -> Unit,
) {
    var followedCredits by remember { mutableStateOf(setOf<String>()) }
    val credits = remember(song) {
        if (song != null) repository.generateCredits(song) else emptyList()
    }

    Card(
        shape = DesktopRadii.Card,
        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
        border = BorderStroke(1.dp, DesktopColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            // Header dengan Tombol "Show all"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Credits",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = DesktopColors.Paper,
                )
                Text(
                    text = "Show all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Fog,
                    modifier = Modifier
                        .clickable { onShowAll() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Daftar Kredit Lagu (Maksimal 3 tampil di kartu)
            credits.take(3).forEachIndexed { index, credit ->
                if (index > 0) Spacer(modifier = Modifier.height(14.dp))

                val isCreditFollowed = followedCredits.contains(credit.name)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = credit.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = DesktopColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = credit.role,
                            fontSize = 12.sp,
                            color = DesktopColors.Fog,
                        )
                    }

                    if (credit.canFollow) {
                        Surface(
                            onClick = {
                                followedCredits = if (isCreditFollowed) {
                                    followedCredits - credit.name
                                } else {
                                    followedCredits + credit.name
                                }
                            },
                            shape = DesktopRadii.Pill,
                            color = if (isCreditFollowed) DesktopColors.AcidLime else DesktopColors.Obsidian,
                            border = if (isCreditFollowed) null else BorderStroke(1.dp, DesktopColors.Border),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (isCreditFollowed) "Following" else "Follow",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCreditFollowed) DesktopColors.Void else DesktopColors.Paper,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kartu "On tour"
 */
@Composable
private fun OnTourCard(
    artistName: String,
    repository: DesktopMusicRepository,
    onShowAll: () -> Unit,
) {
    val tours = remember(artistName) { repository.generateTourDates(artistName) }

    Card(
        shape = DesktopRadii.Card,
        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
        border = BorderStroke(1.dp, DesktopColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "On tour",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = DesktopColors.Paper,
                )
                Text(
                    text = "Show all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Fog,
                    modifier = Modifier
                        .clickable { onShowAll() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Daftar Tanggal Tur
            tours.take(2).forEachIndexed { index, tour ->
                if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                TourRowItem(tour = tour)
            }
        }
    }
}

@Composable
private fun TourRowItem(tour: NowPlayingTourItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Badge Tanggal Kotak (Bulan di atas, Tanggal di bawah)
        Surface(
            shape = DesktopRadii.Button,
            color = DesktopColors.Obsidian,
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.size(44.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = tour.month,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = DesktopColors.Ash,
                )
                Text(
                    text = tour.day,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopColors.Paper,
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tour.city,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = DesktopColors.Paper,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tour.lineUp,
                fontSize = 12.sp,
                color = DesktopColors.Mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tour.venueText,
                fontSize = 11.5.sp,
                color = DesktopColors.Fog,
            )
        }
    }
}

/**
 * Kartu "Next in queue"
 */
@Composable
private fun NextInQueueCard(
    queue: List<DesktopSong>,
    currentIndex: Int,
    onPlaySong: (DesktopSong) -> Unit,
) {
    val nextSong = if (currentIndex in queue.indices && currentIndex + 1 in queue.indices) {
        queue[currentIndex + 1]
    } else {
        null
    }

    Card(
        shape = DesktopRadii.Card,
        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
        border = BorderStroke(1.dp, DesktopColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Next in queue",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = DesktopColors.Paper,
                )
                Text(
                    text = "Antrean (${queue.size} lagu)",
                    fontSize = 12.sp,
                    color = DesktopColors.Fog,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (nextSong != null) {
                Surface(
                    onClick = { onPlaySong(nextSong) },
                    shape = DesktopRadii.Button,
                    color = DesktopColors.Obsidian,
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(42.dp)) {
                            if (!nextSong.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = nextSong.thumbnailUrl.resize(120, 120),
                                    contentDescription = nextSong.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(DesktopRadii.Badge),
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().clip(DesktopRadii.Badge).background(DesktopColors.Carbon),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = DesktopColors.Ash)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nextSong.title,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp,
                                color = DesktopColors.Paper,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = nextSong.artist,
                                fontSize = 12.sp,
                                color = DesktopColors.Fog,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = nextSong.durationText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DesktopColors.Fog,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )

                        IconButton(
                            onClick = { onPlaySong(nextSong) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Putar Lagu Berikutnya",
                                tint = DesktopColors.AcidLime,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tidak ada lagu berikutnya dalam antrean. Putar playlist atau lagu rekomendasi untuk mengisi antrean.",
                    fontSize = 13.sp,
                    color = DesktopColors.Fog,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}
