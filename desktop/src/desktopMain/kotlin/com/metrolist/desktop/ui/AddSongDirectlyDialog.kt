package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AddSongDirectlyPanel(
    playlist: DesktopPlaylist,
    repository: DesktopMusicRepository,
    refreshTick: Int = 0,
    onClose: () -> Unit,
    onSongAdded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var isLoadingRecommendations by remember { mutableStateOf(true) }

    var addedSongIds by remember(playlist.id) { mutableStateOf(playlist.songs.map { it.id }.toSet()) }
    var feedbackMessage by remember(playlist.id) { mutableStateOf<String?>(null) }
    var currentSongCount by remember(playlist.id) { mutableStateOf(playlist.songs.size) }

    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(playlist.id, playlist.songs.size, refreshTick) {
        val latest = repository.getLocalPlaylistById(playlist.id)
        if (latest != null) {
            addedSongIds = latest.songs.map { it.id }.toSet()
            currentSongCount = latest.songs.size
        } else {
            addedSongIds = playlist.songs.map { it.id }.toSet()
            currentSongCount = playlist.songs.size
        }
    }

    LaunchedEffect(Unit) {
        isLoadingRecommendations = true
        recommendations = repository.getHomeQuickPicks().distinctBy { it.id }
        isLoadingRecommendations = false
    }

    fun performSearch() {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            isSearching = true
            hasSearched = true
            searchResults = repository.searchSongs(trimmed).distinctBy { it.id }
            isSearching = false
        }
    }

    fun addSong(song: DesktopSong) {
        scope.launch {
            val success = repository.addSongToLocalPlaylist(playlist.id, song)
            if (success) {
                addedSongIds = addedSongIds + song.id
                currentSongCount++
                feedbackMessage = "Lagu '${song.title}' berhasil ditambahkan."
                onSongAdded()
            }
        }
    }

    Card(
        shape = DesktopRadii.Card,
        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
        border = BorderStroke(1.dp, DesktopColors.Border),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(DesktopColors.AcidLime, DesktopRadii.Button),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = DesktopColors.Void,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tambah Lagu",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${playlist.title} • $currentSongCount lagu",
                            fontSize = 11.5.sp,
                            color = DesktopColors.Mist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = DesktopColors.Fog,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar Row (Linear Compact Style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = DesktopRadii.Input,
                    color = DesktopColors.Obsidian,
                    border = BorderStroke(1.dp, if (query.isNotEmpty()) DesktopColors.Smoke else DesktopColors.Border),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
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
                            tint = if (query.isNotEmpty()) DesktopColors.Paper else DesktopColors.Fog,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val searchTextStyle = TextStyle(
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
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = searchTextStyle,
                                cursorBrush = SolidColor(DesktopColors.AcidLime),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                        ) {
                                            performSearch()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (query.isEmpty()) {
                                            Text(
                                                "Cari lagu...",
                                                style = searchTextStyle.copy(color = DesktopColors.Fog),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchJob?.cancel()
                                    isSearching = false
                                    hasSearched = false
                                    query = ""
                                    searchResults = emptyList()
                                },
                                modifier = Modifier
                                    .size(20.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Hapus teks pencarian",
                                    tint = DesktopColors.Fog,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = ::performSearch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopColors.AcidLime,
                        contentColor = DesktopColors.Void,
                    ),
                    shape = DesktopRadii.Button,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text("Cari", color = DesktopColors.Void, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section Title
            Text(
                text = when {
                    isSearching -> "Mencari lagu di YouTube Music..."
                    hasSearched -> "Hasil Pencarian (${searchResults.size} lagu)"
                    else -> "Rekomendasi Populer"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DesktopColors.Fog,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Song list content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isSearching || (!hasSearched && isLoadingRecommendations)) {
                    SongListSkeleton(count = 5)
                } else {
                    val displayList = if (hasSearched) searchResults else recommendations

                    if (displayList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tidak ada lagu yang ditemukan.", color = DesktopColors.Fog, fontSize = 12.5.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(displayList, key = { it.id }) { song ->
                                val isAlreadyAdded = song.id in addedSongIds

                                Card(
                                    shape = DesktopRadii.Button,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAlreadyAdded) DesktopColors.Obsidian else DesktopColors.Void
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isAlreadyAdded) DesktopColors.AcidLime.copy(alpha = 0.35f) else DesktopColors.Border
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (!song.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = song.thumbnailUrl,
                                                contentDescription = song.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(DesktopRadii.Badge),
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(DesktopRadii.Badge)
                                                    .background(DesktopColors.Obsidian),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = DesktopColors.Fog, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = DesktopColors.Paper,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${song.artist} • ${song.durationText}",
                                                fontSize = 11.5.sp,
                                                color = DesktopColors.Fog,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (isAlreadyAdded) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Sudah di playlist",
                                                    tint = DesktopColors.AcidLime,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Ada",
                                                    color = DesktopColors.AcidLime,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { addSong(song) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = DesktopColors.AcidLime,
                                                    contentColor = DesktopColors.Void,
                                                ),
                                                shape = DesktopRadii.Button,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .pointerHoverIcon(PointerIcon.Hand),
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Tambah lagu", tint = DesktopColors.Void, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = feedbackMessage ?: "Pilih lagu untuk ditambahkan ke playlist ini.",
                fontSize = 11.5.sp,
                color = if (feedbackMessage != null) DesktopColors.AcidLime else DesktopColors.Fog,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
