package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.launch

@Composable
fun AddSongDirectlyDialog(
    playlist: DesktopPlaylist,
    repository: DesktopMusicRepository,
    onDismissRequest: () -> Unit,
    onSongAdded: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingRecommendations by remember { mutableStateOf(true) }

    var addedSongIds by remember { mutableStateOf(playlist.songs.map { it.id }.toSet()) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var currentSongCount by remember { mutableStateOf(playlist.songs.size) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            isLoadingRecommendations = true
            recommendations = repository.getHomeQuickPicks()
            isLoadingRecommendations = false
        }
    }

    fun performSearch() {
        if (query.isBlank()) return
        scope.launch {
            isSearching = true
            searchResults = repository.searchSongs(query)
            isSearching = false
        }
    }

    fun addSong(song: DesktopSong) {
        scope.launch {
            val success = repository.addSongToLocalPlaylist(playlist.id, song)
            if (success) {
                addedSongIds = addedSongIds + song.id
                currentSongCount++
                feedbackMessage = "Lagu '${song.title}' berhasil ditambahkan ke playlist!"
                onSongAdded()
            }
        }
    }

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .padding(12.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                        onDismissRequest()
                        true
                    } else false
                },
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(DesktopColors.AcidLime, DesktopRadii.Button),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = DesktopColors.Void,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Tambah Lagu ke Playlist",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopColors.Paper,
                            )
                            Text(
                                text = "${playlist.title} • $currentSongCount lagu",
                                fontSize = 12.sp,
                                color = DesktopColors.Mist,
                            )
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = DesktopColors.Fog)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Search Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Cari lagu, artis, atau album di YouTube Music...", fontSize = 13.sp, color = DesktopColors.Fog) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesktopColors.AcidLime,
                            unfocusedBorderColor = DesktopColors.Border,
                            focusedTextColor = DesktopColors.Paper,
                            unfocusedTextColor = DesktopColors.Paper,
                            focusedContainerColor = DesktopColors.Carbon,
                            unfocusedContainerColor = DesktopColors.Carbon,
                            cursorColor = DesktopColors.AcidLime,
                        ),
                        shape = DesktopRadii.Input,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                    performSearch()
                                    true
                                } else false
                            },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; searchResults = emptyList() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus teks pencarian", tint = DesktopColors.Fog)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = ::performSearch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopColors.AcidLime,
                            contentColor = DesktopColors.Void,
                        ),
                        shape = DesktopRadii.Button,
                        modifier = Modifier.height(54.dp),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Mulai Pencarian", tint = DesktopColors.Void, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cari", color = DesktopColors.Void, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section Title
                Text(
                    text = if (searchResults.isNotEmpty()) {
                        "Hasil Pencarian (${searchResults.size} lagu):"
                    } else if (isSearching) {
                        "Mencari lagu di YouTube Music..."
                    } else {
                        "Lagu Rekomendasi Populer (Klik + Tambah):"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Mist,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Song list content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isSearching || (query.isBlank() && isLoadingRecommendations)) {
                        SongListSkeleton(count = 5)
                    } else {
                        val displayList = if (searchResults.isNotEmpty()) searchResults else recommendations

                        if (displayList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Tidak ada lagu yang ditemukan.", color = DesktopColors.Fog)
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
                                            containerColor = if (isAlreadyAdded) DesktopColors.Obsidian else DesktopColors.Carbon
                                        ),
                                        border = BorderStroke(1.dp, if (isAlreadyAdded) DesktopColors.BorderSubtle else DesktopColors.Border),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (!song.thumbnailUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = song.thumbnailUrl,
                                                    contentDescription = song.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(DesktopRadii.Badge),
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(DesktopRadii.Badge)
                                                        .background(DesktopColors.Obsidian),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = DesktopColors.Fog)
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = song.title,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp,
                                                    color = DesktopColors.Paper,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = song.artist,
                                                    fontSize = 12.sp,
                                                    color = DesktopColors.Mist,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }

                                            Text(
                                                text = song.durationText,
                                                fontSize = 11.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = DesktopColors.Fog,
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                            )

                                            if (isAlreadyAdded) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Sudah di playlist",
                                                        tint = DesktopColors.AcidLime,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Sudah Ada",
                                                        color = DesktopColors.AcidLime,
                                                        fontSize = 12.sp,
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
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(32.dp),
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = DesktopColors.Void, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Tambah", fontSize = 12.sp, color = DesktopColors.Void, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Footer: status feedback + Done button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = feedbackMessage ?: "Pilih lagu yang ingin ditambahkan ke playlist ini.",
                        fontSize = 12.sp,
                        color = if (feedbackMessage != null) DesktopColors.AcidLime else DesktopColors.Fog,
                        modifier = Modifier.weight(1f),
                    )

                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopColors.Carbon,
                            contentColor = DesktopColors.Paper,
                        ),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = DesktopRadii.Button,
                    ) {
                        Text("Selesai", color = DesktopColors.Paper, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
