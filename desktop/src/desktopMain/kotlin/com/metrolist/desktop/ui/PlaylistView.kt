package com.metrolist.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopPlaylistSummary
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.model.resize
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.launch

@Composable
fun PlaylistView(
    repository: DesktopMusicRepository,
    playbackManager: DesktopPlaybackManager,
    activePlaylistId: String? = null,
    onOpenPlaylist: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onAddToPlaylistRequest: (DesktopSong) -> Unit,
) {
    var localActivePlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSpotifyDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val currentPlaylistId = activePlaylistId ?: localActivePlaylistId

    val handleOpenPlaylist: (String) -> Unit = { id ->
        if (onOpenPlaylist != null) {
            onOpenPlaylist(id)
        } else {
            localActivePlaylistId = id
        }
    }

    val handleBack: () -> Unit = {
        if (onBack != null) {
            onBack()
        } else {
            localActivePlaylistId = null
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismissRequest = { showCreateDialog = false },
            onConfirm = { title, description ->
                scope.launch {
                    val newPl = repository.createLocalPlaylist(title, description)
                    showCreateDialog = false
                    handleOpenPlaylist(newPl.id)
                }
            }
        )
    }

    if (showSpotifyDialog) {
        ImportSpotifyDialog(
            repository = repository,
            onDismissRequest = { showSpotifyDialog = false },
            onPlaylistImported = { imported ->
                showSpotifyDialog = false
                handleOpenPlaylist(imported.id)
            }
        )
    }
    if (currentPlaylistId != null) {
        PlaylistDetailView(
            playlistId = currentPlaylistId,
            repository = repository,
            playbackManager = playbackManager,
            onBack = handleBack,
            onAddToPlaylistRequest = onAddToPlaylistRequest,
            onPlaylistDeleted = handleBack,
        )
    } else {
        PlaylistOverviewView(
            repository = repository,
            playbackManager = playbackManager,
            onSelectPlaylist = handleOpenPlaylist,
            onCreatePlaylistClick = { showCreateDialog = true },
            onImportSpotifyClick = { showSpotifyDialog = true },
        )
    }
}

@Composable
private fun HoverActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: Color,
    hoverColor: Color = containerColor,
    contentColor: Color = DesktopColors.Paper,
    borderColor: Color? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        shape = DesktopRadii.Button,
        color = if (!enabled) containerColor.copy(alpha = 0.5f)
                else if (isHovered) hoverColor
                else containerColor,
        contentColor = if (!enabled) contentColor.copy(alpha = 0.5f) else contentColor,
        border = if (borderColor != null) BorderStroke(1.dp, if (!enabled) borderColor.copy(alpha = 0.5f) else borderColor) else null,
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = if (isHovered && enabled) 12.dp else 10.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = if (!enabled) contentColor.copy(alpha = 0.5f) else contentColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            AnimatedVisibility(
                visible = isHovered && enabled,
                enter = fadeIn(tween(140)) + expandHorizontally(tween(180, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(140, easing = FastOutSlowInEasing)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!enabled) contentColor.copy(alpha = 0.5f) else contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistOverviewView(
    repository: DesktopMusicRepository,
    playbackManager: DesktopPlaybackManager,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportSpotifyClick: () -> Unit,
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val categories = listOf("Semua", "Playlist Lokal", "Lagu Offline (Terunduh)", "Rekomendasi YouTube", "Cari Playlist Online")

    var localPlaylists by remember { mutableStateOf<List<DesktopPlaylist>>(emptyList()) }
    var onlinePlaylists by remember { mutableStateOf<List<DesktopPlaylistSummary>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<DesktopPlaylistSummary>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    var playlistToDelete by remember { mutableStateOf<DesktopPlaylist?>(null) }
    var playlistToAddSongDirectly by remember { mutableStateOf<DesktopPlaylist?>(null) }


    val scope = rememberCoroutineScope()
    var playlistPage by remember { mutableStateOf(1) }
    var isLoadingMorePlaylists by remember { mutableStateOf(false) }
    var canLoadMorePlaylists by remember { mutableStateOf(true) }

    fun loadData() {
        scope.launch {
            isLoading = true
            playlistPage = 1
            canLoadMorePlaylists = true
            localPlaylists = repository.getLocalPlaylists()
            onlinePlaylists = repository.getHomePlaylists()
            isLoading = false
        }
    }

    fun loadMorePlaylists() {
        if (isLoading || isLoadingMorePlaylists || !canLoadMorePlaylists) return
        scope.launch {
            isLoadingMorePlaylists = true
            val nextPage = playlistPage + 1
            val newPlaylists = repository.getMoreHomePlaylists(page = nextPage)
            if (newPlaylists.isNotEmpty()) {
                val existingIds = onlinePlaylists.map { it.id }.toHashSet()
                val uniqueNew = newPlaylists.filter { !existingIds.contains(it.id) }
                if (uniqueNew.isNotEmpty()) {
                    // RAM Safety: batasi maksimal 120 playlist agar penggunaan memori tetap minimal
                    onlinePlaylists = (onlinePlaylists + uniqueNew).take(120)
                    playlistPage = nextPage
                } else {
                    playlistPage = nextPage
                }
            } else {
                canLoadMorePlaylists = false
            }
            isLoadingMorePlaylists = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Hapus Playlist Lokal?", color = DesktopColors.Paper, fontWeight = FontWeight.SemiBold) },
            text = { Text("Apakah Anda yakin ingin menghapus playlist '${playlistToDelete?.title}'? Seluruh daftar lagu di dalamnya akan dihapus dari komputer Anda.", color = DesktopColors.Mist) },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete?.let { pl ->
                            scope.launch {
                                repository.deleteLocalPlaylist(pl.id)
                                localPlaylists = repository.getLocalPlaylists()
                                playlistToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.CoralRed),
                    shape = DesktopRadii.Button,
                ) {
                    Text("Hapus Playlist", color = DesktopColors.Void, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Batal", color = DesktopColors.Fog)
                }
            },
            containerColor = DesktopColors.Obsidian,
            shape = DesktopRadii.Card,
        )
    }

    if (playlistToAddSongDirectly != null) {
        AddSongDirectlyDialog(
            playlist = playlistToAddSongDirectly!!,
            repository = repository,
            onDismissRequest = {
                playlistToAddSongDirectly = null
                loadData()
            },
            onSongAdded = {
                loadData()
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
    ) {
        // Header Bar (Responsif FlowRow)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "Koleksi Playlist",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopColors.Paper,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Kelola playlist lokal dan dengarkan kurasi musik dari YouTube Music",
                    fontSize = 13.sp,
                    color = DesktopColors.Fog,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                @OptIn(ExperimentalFoundationApi::class)
                TooltipArea(
                    tooltip = {
                        Surface(
                            shape = DesktopRadii.Button,
                            color = DesktopColors.Obsidian,
                            border = BorderStroke(1.dp, DesktopColors.Border),
                        ) {
                            Text(
                                text = "Muat Ulang Koleksi",
                                fontSize = 11.5.sp,
                                color = DesktopColors.Paper,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    },
                    delayMillis = 300,
                ) {
                    IconButton(
                        onClick = { loadData() },
                        enabled = !isLoading,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Muat Ulang Koleksi",
                            tint = if (!isLoading) DesktopColors.Paper else DesktopColors.Ash,
                        )
                    }
                }

                HoverActionButton(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    text = "Import Playlist",
                    containerColor = DesktopColors.Obsidian,
                    hoverColor = DesktopColors.Slate,
                    contentColor = DesktopColors.Mist,
                    borderColor = DesktopColors.Border,
                    onClick = onImportSpotifyClick,
                )

                HoverActionButton(
                    icon = Icons.Default.Add,
                    text = "Buat Playlist Baru",
                    containerColor = DesktopColors.AcidLime,
                    hoverColor = DesktopColors.AcidLime.copy(alpha = 0.9f),
                    contentColor = DesktopColors.Void,
                    onClick = onCreatePlaylistClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEachIndexed { index, name ->
                val isSelected = selectedCategoryIndex == index
                val chipBg = if (isSelected) DesktopColors.AcidLime else DesktopColors.Obsidian
                val chipTextColor = if (isSelected) DesktopColors.Void else DesktopColors.Mist
                val chipBorder = if (isSelected) null else BorderStroke(1.dp, DesktopColors.Border)
                Surface(
                    shape = DesktopRadii.Pill,
                    color = chipBg,
                    border = chipBorder,
                    onClick = { selectedCategoryIndex = index },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = name,
                            color = chipTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // If Category is Offline Songs:
        if (selectedCategoryIndex == 2) {
            val downloadedSongs = repository.downloadManager.getDownloadedSongs()
            if (downloadedSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = DesktopRadii.Card,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = DesktopColors.AcidLime, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Belum Ada Lagu Tersimpan Offline", color = DesktopColors.Paper, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Buka playlist favorit Anda lalu klik tombol 'Download Offline' untuk menyimpan lagu ke komputer.", fontSize = 13.sp, color = DesktopColors.Mist, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${downloadedSongs.size} lagu tersimpan di komputer • Siap diputar tanpa internet",
                            fontSize = 13.sp,
                            color = DesktopColors.Mist,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = {
                                val offlinePl = DesktopPlaylist(
                                    id = "offline_playlist",
                                    title = "Lagu Offline",
                                    author = "Tersimpan di Komputer",
                                    songs = downloadedSongs,
                                    isLocal = true,
                                )
                                playbackManager.playPlaylist(offlinePl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.AcidLime),
                            shape = DesktopRadii.Button
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DesktopColors.Void, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Putar Semua Offline", color = DesktopColors.Void, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            downloadedSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "downloaded_song_item" }
                        ) { index, song ->
                            Card(
                                shape = DesktopRadii.Button,
                                colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                                border = BorderStroke(1.dp, DesktopColors.Border),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val offlinePl = DesktopPlaylist(
                                        id = "offline_playlist",
                                        title = "Lagu Offline",
                                        author = "Tersimpan di Komputer",
                                        songs = downloadedSongs,
                                        isLocal = true,
                                    )
                                    playbackManager.playPlaylist(offlinePl, startIndex = index)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${index + 1}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = DesktopColors.Ash,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    if (!song.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = song.thumbnailUrl.resize(120, 120),
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(42.dp).clip(DesktopRadii.Badge).background(DesktopColors.Obsidian)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(42.dp).clip(DesktopRadii.Badge).background(DesktopColors.Obsidian),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = DesktopColors.Ash)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(song.title, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = DesktopColors.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, fontSize = 12.sp, color = DesktopColors.Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(DesktopRadii.Badge)
                                            .background(DesktopColors.Slate.copy(alpha = 0.5f))
                                            .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Badge)
                                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    ) {
                                        Text("OFFLINE", fontSize = 9.5.sp, color = DesktopColors.Mist, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(
                                        onClick = {
                                            val offlinePl = DesktopPlaylist(
                                                id = "offline_playlist",
                                                title = "Lagu Offline",
                                                author = "Tersimpan di Komputer",
                                                songs = downloadedSongs,
                                                isLocal = true,
                                            )
                                            playbackManager.playPlaylist(offlinePl, startIndex = index)
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = DesktopColors.Obsidian),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = DesktopColors.AcidLime, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                repository.downloadManager.deleteDownload(song.id)
                                                loadData()
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = DesktopColors.Obsidian),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus Unduhan", tint = DesktopColors.CoralRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        // If Category is Search Online Playlists:
        if (selectedCategoryIndex == 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari playlist di katalog YouTube Music...", fontSize = 13.sp, color = DesktopColors.Fog) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesktopColors.AcidLime,
                        unfocusedBorderColor = DesktopColors.Border,
                        focusedTextColor = DesktopColors.Paper,
                        unfocusedTextColor = DesktopColors.Paper,
                        focusedContainerColor = DesktopColors.Obsidian,
                        unfocusedContainerColor = DesktopColors.Obsidian,
                        cursorColor = DesktopColors.AcidLime,
                    ),
                    shape = DesktopRadii.Input,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            scope.launch {
                                isSearching = true
                                searchResults = repository.searchPlaylists(searchQuery)
                                isSearching = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.AcidLime),
                    shape = DesktopRadii.Button,
                    modifier = Modifier.height(50.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Cari", tint = DesktopColors.Void)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cari Playlist", color = DesktopColors.Void, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isSearching) {
                PlaylistGridSkeleton(count = 8)
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = DesktopRadii.Card,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (searchQuery.isBlank()) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = DesktopColors.AcidLime, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Jelajahi Playlist YouTube Music", color = DesktopColors.Paper, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Ketik kata kunci untuk mencari playlist publik di katalog YouTube Music.", color = DesktopColors.Mist, fontSize = 13.sp, textAlign = TextAlign.Center)
                            } else {
                                Icon(Icons.Default.SearchOff, contentDescription = null, tint = DesktopColors.Ash, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Playlist Tidak Ditemukan", color = DesktopColors.Paper, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Tidak ditemukan playlist untuk kata kunci \"$searchQuery\". Coba gunakan kata kunci lain.", color = DesktopColors.Mist, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } else {
                PlaylistGrid(
                    playlists = searchResults,
                    onSelect = onSelectPlaylist,
                    onPlay = { pl ->
                        scope.launch {
                            val details = repository.getPlaylistDetails(pl.id)
                            details?.let { playbackManager.playPlaylist(it) }
                        }
                    },
                    onDelete = null,
                )
            }
            return
        }

        // Displaying lists according to category
        if (isLoading) {
            PlaylistGridSkeleton(count = 8)
        } else {
            val localSummaries = localPlaylists.map { it.toSummary() }
            val itemsToDisplay = when (selectedCategoryIndex) {
                0 -> localSummaries + onlinePlaylists
                1 -> localSummaries
                3 -> onlinePlaylists
                else -> emptyList()
            }

            if (itemsToDisplay.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = DesktopRadii.Card,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = DesktopColors.AcidLime, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Belum Ada Playlist di Kategori Ini", color = DesktopColors.Paper, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Mulai buat playlist lokal baru atau impor playlist favorit Anda dari Spotify.", color = DesktopColors.Mist, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onCreatePlaylistClick,
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.AcidLime),
                                shape = DesktopRadii.Button,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DesktopColors.Void, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buat Playlist Baru", color = DesktopColors.Void, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            } else {
                PlaylistGrid(
                    playlists = itemsToDisplay,
                    onSelect = onSelectPlaylist,
                    onPlay = { pl ->
                        scope.launch {
                            val details = repository.getPlaylistDetails(pl.id)
                            details?.let { playbackManager.playPlaylist(it) }
                        }
                    },
                    onAddSongDirectly = { plSummary ->
                        val local = localPlaylists.find { it.id == plSummary.id }
                        if (local != null) {
                            playlistToAddSongDirectly = local
                        }
                    },
                    onDelete = { plSummary ->
                        val local = localPlaylists.find { it.id == plSummary.id }
                        if (local != null) {
                            playlistToDelete = local
                        }
                    },
                    onLoadMore = if (selectedCategoryIndex in listOf(0, 3) && searchQuery.isBlank()) {
                        { loadMorePlaylists() }
                    } else null,
                    isLoadingMore = isLoadingMorePlaylists,
                )
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<DesktopPlaylistSummary>,
    onSelect: (String) -> Unit,
    onPlay: (DesktopPlaylistSummary) -> Unit,
    onAddSongDirectly: ((DesktopPlaylistSummary) -> Unit)? = null,
    onDelete: ((DesktopPlaylistSummary) -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    isLoadingMore: Boolean = false,
) {
    val gridState = rememberLazyGridState()

    // Otomatis muat playlist baru saat scroll mendekati ujung bawah (RAM-efficient)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore) {
            onLoadMore?.invoke()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 210.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
    ) {
        items(
            playlists,
            key = { it.id },
            contentType = { "playlist_summary_card" },
        ) { pl ->
            Card(
                shape = DesktopRadii.Card,
                colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                border = BorderStroke(1.dp, DesktopColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(pl.id) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(DesktopRadii.Button)
                            .background(DesktopColors.Obsidian),
                    ) {
                        if (!pl.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = pl.thumbnailUrl.resize(300, 300),
                                contentDescription = pl.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = DesktopColors.Ash,
                                    modifier = Modifier.size(54.dp),
                                )
                            }
                        }

                        // Quick Play Button
                        IconButton(
                            onClick = { onPlay(pl) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = DesktopColors.AcidLime),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(36.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = DesktopColors.Void, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp,
                                color = DesktopColors.Paper,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${pl.author ?: "YouTube"} • ${pl.songCountText ?: ""}",
                                fontSize = 12.sp,
                                color = DesktopColors.Fog,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pl.isLocal && onAddSongDirectly != null) {
                                IconButton(
                                    onClick = { onAddSongDirectly(pl) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Tambah lagu langsung",
                                        tint = DesktopColors.AcidLime,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            if (pl.isLocal && onDelete != null) {
                                IconButton(
                                    onClick = { onDelete(pl) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = DesktopColors.Fog, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoadingMore) {
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "loading_more_playlists_indicator",
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DesktopColors.AcidLime,
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            "Memuat playlist lainnya...",
                            color = DesktopColors.Fog,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun calculateTotalPlaylistDuration(songs: List<DesktopSong>): String {
    var totalSeconds = 0
    for (s in songs) {
        val parts = s.durationText.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size == 2) {
            totalSeconds += parts[0] * 60 + parts[1]
        } else if (parts.size == 3) {
            totalSeconds += parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    }
    if (totalSeconds == 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "$hours jam $minutes mnt"
    } else {
        "$minutes mnt"
    }
}

private fun formatPlaylistDate(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.ENGLISH)
        sdf.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        "-"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailView(
    playlistId: String,
    repository: DesktopMusicRepository,
    playbackManager: DesktopPlaybackManager,
    onBack: () -> Unit,
    onAddToPlaylistRequest: (DesktopSong) -> Unit,
    onPlaylistDeleted: () -> Unit,
) {
    var playlist by remember { mutableStateOf<DesktopPlaylist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchSongQuery by remember { mutableStateOf("") }
    var statusFeedback by remember { mutableStateOf<String?>(null) }
    var showAddSongDirectlyDialog by remember { mutableStateOf(false) }
    var isDownloadingPlaylist by remember { mutableStateOf(false) }

    val currentSong by playbackManager.currentSong.collectAsState()
    val isPlaying by playbackManager.player.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()

    fun loadDetails() {
        scope.launch {
            isLoading = true
            playlist = repository.getPlaylistDetails(playlistId)
            isLoading = false
        }
    }

    LaunchedEffect(playlistId) {
        loadDetails()
    }

    if (showAddSongDirectlyDialog && playlist != null) {
        AddSongDirectlyDialog(
            playlist = playlist!!,
            repository = repository,
            onDismissRequest = {
                showAddSongDirectlyDialog = false
                loadDetails()
            },
            onSongAdded = {
                loadDetails()
            },
        )
    }

    if (isLoading) {
        PlaylistDetailSkeleton()
        return
    }

    val pl = playlist
    if (pl == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gagal memuat detail playlist.", color = DesktopColors.TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.CardElevated),
                ) {
                    Text("Kembali", color = Color.White)
                }
            }
        }
        return
    }

    val isSongFromThisPlaylist = currentSong != null && pl.songs.any { it.id == currentSong?.id }
    val isCurrentPlaylistPlaying = isPlaying && isSongFromThisPlaylist

    val onPlayPlaylistToggle = {
        if (isCurrentPlaylistPlaying) {
            playbackManager.player.pause()
        } else if (isSongFromThisPlaylist) {
            playbackManager.player.resume()
        } else {
            playbackManager.playPlaylist(pl, startIndex = 0, shuffle = false)
        }
    }

    val filteredSongs = remember(pl.songs, searchSongQuery) {
        if (searchSongQuery.isBlank()) {
            pl.songs
        } else {
            pl.songs.filter {
                it.title.contains(searchSongQuery, ignoreCase = true) ||
                    it.artist.contains(searchSongQuery, ignoreCase = true) ||
                    (it.album?.contains(searchSongQuery, ignoreCase = true) == true)
            }
        }
    }

    val totalDurationText = remember(pl.songs) {
        calculateTotalPlaylistDuration(pl.songs)
    }

    val dateAddedText = remember(pl.createdAt) {
        formatPlaylistDate(pl.createdAt)
    }

    val downloadedIds by repository.downloadManager.downloadedSongIds.collectAsState()
    val downloadProgress by repository.downloadManager.activeDownloadProgress.collectAsState()

    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 130
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DesktopColors.Background)
    ) {
        val showAlbumColumn = maxWidth >= 760.dp
        val showDateColumn = maxWidth >= 960.dp
        val isCompactHero = maxWidth < 750.dp

        // Atmospheric top gradient (Linear subtle slate wash fading into void)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DesktopColors.Slate.copy(alpha = 0.30f),
                            DesktopColors.Carbon.copy(alpha = 0.10f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent Top Header Bar (Tinggi stabil 52.dp agar fade in / fade out 100% mulus tanpa nyendat)
            PlaylistTopStickyBar(
                title = pl.title,
                isScrolled = isScrolled,
                isPlaying = isCurrentPlaylistPlaying,
                onPlayToggle = onPlayPlaylistToggle,
            )

            // Scrollable Content
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // 1. Hero Section
                item(key = "hero") {
                    PlaylistHeroSection(
                        playlist = pl,
                        totalDurationText = totalDurationText,
                        isCompact = isCompactHero,
                    )
                }

                // 2. Action Bar
                item(key = "actions") {
                    PlaylistActionBar(
                        playlist = pl,
                        isPlaying = isCurrentPlaylistPlaying,
                        isDownloading = isDownloadingPlaylist,
                        searchQuery = searchSongQuery,
                        onSearchQueryChange = { searchSongQuery = it },
                        onPlayClick = onPlayPlaylistToggle,
                        onShuffleClick = {
                            playbackManager.playPlaylist(pl, startIndex = 0, shuffle = true)
                        },
                        onDownloadClick = {
                            scope.launch {
                                isDownloadingPlaylist = true
                                statusFeedback = "Memulai download playlist untuk offline..."
                                repository.downloadManager.downloadPlaylist(
                                    songs = pl.songs,
                                    repository = repository,
                                    onProgress = { current, total, song ->
                                        statusFeedback = "Selesai Mengunduh ${song.title} ($current/$total)"
                                    }
                                )
                                isDownloadingPlaylist = false
                                statusFeedback = "Selesai mengunduh playlist untuk offline!"
                            }
                        },
                        onAddSongClick = { showAddSongDirectlyDialog = true },
                        onDeleteClick = {
                            scope.launch {
                                repository.deleteLocalPlaylist(pl.id)
                                onPlaylistDeleted()
                            }
                        },
                        onSaveToLocalClick = {
                            scope.launch {
                                repository.importOnlinePlaylistToLocal(pl)
                                statusFeedback = "Playlist berhasil disimpan ke lokal!"
                            }
                        },
                        statusFeedback = statusFeedback,
                    )
                }

                // 3. Sticky Table Header
                stickyHeader(key = "table_header") {
                    PlaylistTableHeader(
                        showAlbum = showAlbumColumn,
                        showDate = showDateColumn,
                    )
                }

                // 4. Song rows or Empty state
                if (filteredSongs.isEmpty()) {
                    item(key = "empty_state") {
                        if (pl.songs.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                                border = BorderStroke(1.dp, DesktopColors.Border),
                                shape = DesktopRadii.Card,
                                modifier = Modifier
                                    .padding(28.dp)
                                    .fillMaxWidth(),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .fillMaxWidth(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(DesktopRadii.Button)
                                            .background(DesktopColors.Obsidian),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = DesktopColors.AcidLime,
                                            modifier = Modifier.size(30.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Playlist Ini Masih Kosong",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DesktopColors.Paper,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (pl.isLocal) "Mulai tambahkan lagu favorit Anda langsung ke playlist ini." else "Tidak ada lagu yang ditemukan pada playlist ini.",
                                        fontSize = 13.sp,
                                        color = DesktopColors.Mist,
                                        textAlign = TextAlign.Center,
                                    )
                                    if (pl.isLocal) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { showAddSongDirectlyDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.AcidLime),
                                            shape = DesktopRadii.Button,
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = DesktopColors.Void, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Tambah Lagu Sekarang", color = DesktopColors.Void, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Tidak ada lagu yang cocok dengan kata kunci filter.", color = DesktopColors.Fog)
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        filteredSongs,
                        key = { _, s -> s.id },
                        contentType = { _, _ -> "playlist_song_row" }
                    ) { index, song ->
                        val isThisSongCurrent = currentSong?.id == song.id
                        val isDownloaded = downloadedIds.contains(song.id)
                        val isDownloading = downloadProgress.containsKey(song.id)

                        PlaylistSongRow(
                            index = index,
                            song = song,
                            isCurrentPlaying = isThisSongCurrent,
                            isPlaying = isPlaying && isThisSongCurrent,
                            dateAddedText = dateAddedText,
                            showAlbum = showAlbumColumn,
                            showDate = showDateColumn,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isLocal = pl.isLocal,
                            onRowClick = {
                                if (isThisSongCurrent) {
                                    playbackManager.togglePlayPause()
                                } else {
                                    val actualIndex = pl.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    playbackManager.playPlaylist(pl, startIndex = actualIndex)
                                }
                            },
                            onPlayClick = {
                                if (isThisSongCurrent) {
                                    playbackManager.player.resume()
                                } else {
                                    val actualIndex = pl.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    playbackManager.playPlaylist(pl, startIndex = actualIndex)
                                }
                            },
                            onPauseClick = {
                                playbackManager.player.pause()
                            },
                            onToggleDownload = {
                                scope.launch {
                                    if (isDownloaded) {
                                        repository.downloadManager.deleteDownload(song.id)
                                        statusFeedback = "Hapus unduhan offline: '${song.title}'"
                                    } else {
                                        statusFeedback = "Mengunduh '${song.title}' untuk offline..."
                                        val res = repository.downloadManager.downloadSong(song, repository)
                                        if (res.isSuccess) {
                                            statusFeedback = "Lagu '${song.title}' berhasil diunduh!"
                                        } else {
                                            statusFeedback = "Gagal mengunduh: ${res.exceptionOrNull()?.message}"
                                        }
                                    }
                                }
                            },
                            onAddToPlaylist = {
                                onAddToPlaylistRequest(song)
                            },
                            onDeleteFromPlaylist = {
                                scope.launch {
                                    repository.removeSongFromLocalPlaylist(pl.id, song.id)
                                    loadDetails()
                                }
                            },
                        )
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTopStickyBar(
    title: String,
    isScrolled: Boolean,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isScrolled) DesktopColors.Carbon else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
    )
    val borderColor by animateColorAsState(
        targetValue = if (isScrolled) DesktopColors.Border else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
    )

    Surface(
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        AnimatedVisibility(
            visible = isScrolled,
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 200)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onPlayToggle,
                    shape = CircleShape,
                    color = DesktopColors.AcidLime,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            tint = DesktopColors.Void,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeroSection(
    playlist: DesktopPlaylist,
    totalDurationText: String,
    isCompact: Boolean,
) {
    val artworkSize = if (isCompact) 140.dp else 180.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Artwork
        if (!playlist.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.thumbnailUrl.resize(400, 400),
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(artworkSize)
                    .clip(DesktopRadii.Card)
                    .border(1.dp, DesktopColors.Border, DesktopRadii.Card)
                    .background(DesktopColors.Obsidian),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .clip(DesktopRadii.Card)
                    .border(1.dp, DesktopColors.Border, DesktopRadii.Card)
                    .background(DesktopColors.Obsidian),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = DesktopColors.Ash,
                    modifier = Modifier.size(if (isCompact) 56.dp else 72.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Metadata & info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PLAYLIST",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = DesktopColors.Ash,
                letterSpacing = 1.5.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = playlist.title,
                fontSize = if (isCompact) 24.sp else 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = DesktopColors.Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!playlist.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = playlist.description,
                    fontSize = 13.sp,
                    color = DesktopColors.Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = playlist.author,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Paper,
                    fontSize = 13.sp,
                )
                Text(
                    text = " • ",
                    color = DesktopColors.Ash,
                    fontSize = 13.sp,
                )
                Text(
                    text = "${playlist.songs.size} lagu",
                    color = DesktopColors.Fog,
                    fontSize = 13.sp,
                )
                if (totalDurationText.isNotBlank()) {
                    Text(
                        text = " • ",
                        color = DesktopColors.Ash,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = totalDurationText,
                        color = DesktopColors.Fog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistActionBar(
    playlist: DesktopPlaylist,
    isPlaying: Boolean,
    isDownloading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddSongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSaveToLocalClick: () -> Unit,
    statusFeedback: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Big circular Play Button (52dp)
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = DesktopColors.AcidLime,
                enabled = playlist.songs.isNotEmpty(),
                modifier = Modifier.size(50.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar Semua",
                        tint = DesktopColors.Void,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Shuffle Button
            IconButton(
                onClick = onShuffleClick,
                enabled = playlist.songs.isNotEmpty(),
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Acak Antrean",
                    tint = DesktopColors.Fog,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Download Button
            IconButton(
                onClick = onDownloadClick,
                enabled = playlist.songs.isNotEmpty() && !isDownloading,
                modifier = Modifier.size(38.dp),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = DesktopColors.AcidLime,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download Playlist Offline",
                        tint = DesktopColors.Fog,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            if (playlist.isLocal) {
                // + Tambah Lagu button
                Button(
                    onClick = onAddSongClick,
                    shape = DesktopRadii.Button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopColors.Obsidian,
                        contentColor = DesktopColors.Paper,
                    ),
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DesktopColors.AcidLime, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Lagu", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Hapus Playlist button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus Playlist",
                        tint = DesktopColors.Ash,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                // Simpan ke Lokal button
                Button(
                    onClick = onSaveToLocalClick,
                    shape = DesktopRadii.Button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopColors.Obsidian,
                        contentColor = DesktopColors.Paper,
                    ),
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = DesktopColors.Paper, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan ke Lokal", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Filter search input
            Surface(
                shape = DesktopRadii.Input,
                color = DesktopColors.Obsidian,
                border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) DesktopColors.Smoke else DesktopColors.Border),
                modifier = Modifier
                    .width(220.dp)
                    .height(34.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchQuery.isNotEmpty()) DesktopColors.Paper else DesktopColors.Ash,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val filterTextStyle = TextStyle(
                        color = DesktopColors.Paper,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onSearchQueryChange,
                            singleLine = true,
                            textStyle = filterTextStyle,
                            cursorBrush = SolidColor(DesktopColors.AcidLime),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Filter lagu...",
                                            style = filterTextStyle.copy(color = DesktopColors.Ash),
                                            maxLines = 1,
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Hapus filter",
                                tint = DesktopColors.Fog,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }

        if (statusFeedback != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusFeedback,
                color = DesktopColors.AcidLime,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PlaylistTableHeader(
    showAlbum: Boolean,
    showDate: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesktopColors.Void)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = DesktopColors.Ash,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Judul",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = DesktopColors.Ash,
                modifier = Modifier
                    .weight(3.5f)
                    .padding(start = 12.dp),
            )
            if (showAlbum) {
                Text(
                    text = "Album",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Ash,
                    modifier = Modifier
                        .weight(2.5f)
                        .padding(horizontal = 8.dp),
                )
            }
            if (showDate) {
                Text(
                    text = "Tanggal Ditambahkan",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Ash,
                    modifier = Modifier
                        .weight(1.8f)
                        .padding(horizontal = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = "Durasi",
                    tint = DesktopColors.Ash,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 28.dp),
            thickness = 1.dp,
            color = DesktopColors.Border,
        )
    }
}

@Composable
private fun PlaylistSongRow(
    index: Int,
    song: DesktopSong,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    dateAddedText: String,
    showAlbum: Boolean,
    showDate: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isLocal: Boolean,
    onRowClick: () -> Unit,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteFromPlaylist: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val rowBg = when {
        isCurrentPlaying -> DesktopColors.Obsidian
        isHovered -> DesktopColors.Slate.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Surface(
        onClick = onRowClick,
        interactionSource = interactionSource,
        shape = DesktopRadii.Button,
        color = rowBg,
        border = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Index / Play Arrow / Equalizer
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrentPlaying) {
                    if (isHovered) {
                        IconButton(
                            onClick = { if (isPlaying) onPauseClick() else onPlayClick() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                tint = DesktopColors.AcidLime,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        PlayingEqualizerBars(
                            isPlaying = isPlaying,
                            barColor = DesktopColors.AcidLime,
                            maxHeight = 16.dp,
                        )
                    }
                } else if (isHovered) {
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = DesktopColors.AcidLime,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Text(
                        text = "${index + 1}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = DesktopColors.Ash,
                    )
                }
            }

            // Thumbnail + Title + Artist
            Row(
                modifier = Modifier
                    .weight(3.5f)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.thumbnailUrl.resize(120, 120),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(DesktopRadii.Badge)
                            .background(DesktopColors.Obsidian),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(DesktopRadii.Badge)
                            .background(DesktopColors.Obsidian),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = DesktopColors.Ash, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = if (isCurrentPlaying) DesktopColors.AcidLime else DesktopColors.Paper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        fontSize = 12.sp,
                        color = DesktopColors.Fog,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Album
            if (showAlbum) {
                Text(
                    text = song.album?.takeIf { it.isNotBlank() } ?: song.title,
                    fontSize = 12.5.sp,
                    color = DesktopColors.Fog,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(2.5f)
                        .padding(horizontal = 8.dp),
                )
            }

            // Date added
            if (showDate) {
                Text(
                    text = dateAddedText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DesktopColors.Ash,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1.8f)
                        .padding(horizontal = 8.dp),
                )
            }

            // Actions & Duration
            Row(
                modifier = Modifier
                    .width(110.dp)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = DesktopColors.AcidLime,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                } else if (isDownloaded) {
                    IconButton(
                        onClick = onToggleDownload,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Tersimpan Offline",
                            tint = DesktopColors.AcidLime,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else if (isHovered) {
                    IconButton(
                        onClick = onToggleDownload,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download Offline",
                            tint = DesktopColors.Fog,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                if (isHovered) {
                    IconButton(
                        onClick = onAddToPlaylist,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Tambah ke Playlist",
                            tint = DesktopColors.Fog,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    if (isLocal) {
                        IconButton(
                            onClick = onDeleteFromPlaylist,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Hapus",
                                tint = DesktopColors.CoralRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = song.durationText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DesktopColors.Fog,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
