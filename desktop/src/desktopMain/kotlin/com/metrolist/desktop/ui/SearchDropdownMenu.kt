package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface DropdownItem {
    data class Song(val song: DesktopSong, val isRecent: Boolean) : DropdownItem
    data class Query(val query: String) : DropdownItem
}

@Composable
fun SearchDropdownMenu(
    searchQuery: String,
    repository: DesktopMusicRepository,
    selectedIndex: Int,
    onItemsChanged: (List<DropdownItem>) -> Unit,
    onQuerySelected: (String) -> Unit,
    onSongSelected: (DesktopSong) -> Unit,
    onAddToPlaylist: (DesktopSong) -> Unit,
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var recentSongs by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var quickPicks by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var querySuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchSongResults by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load recent searches & fallback quick picks on initial render
    LaunchedEffect(Unit) {
        recentSongs = repository.recentSearchStore.getRecentSearches()
        if (recentSongs.isEmpty()) {
            quickPicks = repository.getHomeQuickPicks().take(6)
        }
    }

    // Refresh recent searches whenever query becomes blank
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            recentSongs = repository.recentSearchStore.getRecentSearches()
            querySuggestions = emptyList()
            searchSongResults = emptyList()
            isLoading = false
        } else {
            isLoading = true
            // Debounce user input by 250ms
            delay(250)
            val trimmed = searchQuery.trim()
            querySuggestions = repository.getQuerySuggestions(trimmed).take(4)
            searchSongResults = repository.searchSongs(trimmed).take(6)
            isLoading = false
        }
    }

    // Calculate current flattened items for keyboard navigation and notify parent
    val currentItems = remember(searchQuery, recentSongs, quickPicks, querySuggestions, searchSongResults) {
        if (searchQuery.isBlank()) {
            if (recentSongs.isNotEmpty()) {
                recentSongs.map { DropdownItem.Song(it, isRecent = true) }
            } else {
                quickPicks.map { DropdownItem.Song(it, isRecent = false) }
            }
        } else {
            querySuggestions.map { DropdownItem.Query(it) } +
                searchSongResults.map { DropdownItem.Song(it, isRecent = false) }
        }
    }

    LaunchedEffect(currentItems) {
        onItemsChanged(currentItems)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in currentItems.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Surface(
        modifier = modifier
            .widthIn(min = 380.dp, max = 520.dp)
            .shadow(20.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        color = DesktopColors.Obsidian,
        border = BorderStroke(1.dp, Color(0xFF333333)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // 1. Keyboard Hints Header (High-Contrast, Clearly Legible Keycaps)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KeyBadge("↑")
                    KeyBadge("↓")
                    Text(
                        "Navigate",
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KeyBadge("Enter")
                    Text(
                        "Search",
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable Content
            if (searchQuery.isBlank()) {
                // ---- STATE 1: RECENT SEARCHES / POPULAR PICKS ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (recentSongs.isNotEmpty()) "Recent searches" else "Rekomendasi untukmu",
                        color = DesktopColors.Paper,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (recentSongs.isNotEmpty()) {
                        Text(
                            "Hapus Semua",
                            color = DesktopColors.Fog,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        repository.recentSearchStore.clearAll()
                                        recentSongs = emptyList()
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .padding(horizontal = 6.dp),
                ) {
                    itemsIndexed(
                        items = currentItems,
                        key = { _, item -> (item as DropdownItem.Song).song.id }
                    ) { index, item ->
                        val songItem = item as DropdownItem.Song
                        val isCurrent = currentSongId != null && currentSongId == songItem.song.id
                        val isThisPlaying = isCurrent && isPlaying
                        SearchSongCard(
                            song = songItem.song,
                            isRecent = songItem.isRecent,
                            isSelected = (index == selectedIndex),
                            isCurrentSong = isCurrent,
                            isPlaying = isThisPlaying,
                            onPlay = {
                                coroutineScope.launch {
                                    repository.recentSearchStore.addRecentSearch(songItem.song)
                                }
                                onSongSelected(songItem.song)
                            },
                            onRemove = {
                                coroutineScope.launch {
                                    repository.recentSearchStore.removeRecentSearch(songItem.song.id)
                                    recentSongs = repository.recentSearchStore.getRecentSearches()
                                }
                            },
                            onAddToPlaylist = { onAddToPlaylist(songItem.song) },
                        )
                    }
                }
            } else {
                // ---- STATE 2: ACTIVE QUERY SUGGESTIONS & MATCHING SONGS ----
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = DesktopColors.AcidLime,
                            strokeWidth = 2.dp,
                        )
                    }
                } else if (currentItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Tidak ada rekomendasi lagu",
                            color = DesktopColors.Fog,
                            fontSize = 12.5.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(horizontal = 6.dp),
                    ) {
                        itemsIndexed(
                            items = currentItems,
                            key = { index, item ->
                                when (item) {
                                    is DropdownItem.Query -> "q_${item.query}_$index"
                                    is DropdownItem.Song -> "s_${item.song.id}_$index"
                                }
                            }
                        ) { index, item ->
                            when (item) {
                                is DropdownItem.Query -> {
                                    QuerySuggestionRow(
                                        query = item.query,
                                        isSelected = (index == selectedIndex),
                                        onClick = { onQuerySelected(item.query) },
                                    )
                                }
                                is DropdownItem.Song -> {
                                    val isCurrent = currentSongId != null && currentSongId == item.song.id
                                    val isThisPlaying = isCurrent && isPlaying
                                    SearchSongCard(
                                        song = item.song,
                                        isRecent = false,
                                        isSelected = (index == selectedIndex),
                                        isCurrentSong = isCurrent,
                                        isPlaying = isThisPlaying,
                                        onPlay = {
                                            coroutineScope.launch {
                                                repository.recentSearchStore.addRecentSearch(item.song)
                                            }
                                            onSongSelected(item.song)
                                        },
                                        onRemove = {},
                                        onAddToPlaylist = { onAddToPlaylist(item.song) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyBadge(label: String) {
    Surface(
        color = Color(0xFF262626),
        border = BorderStroke(1.dp, Color(0xFF4D4D4D)),
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color(0xFFEDEDED),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun QuerySuggestionRow(
    query: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isHovered || isSelected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHighlighted) Color(0xFF2A2A2A) else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = if (isHighlighted) DesktopColors.Paper else DesktopColors.Fog,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = query,
            color = if (isHighlighted) Color.White else DesktopColors.Paper,
            fontSize = 13.sp,
            fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchSongCard(
    song: DesktopSong,
    isRecent: Boolean,
    isSelected: Boolean,
    isCurrentSong: Boolean = false,
    isPlaying: Boolean = false,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isHovered || isSelected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHighlighted) Color(0xFF282828) else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail with Hover / Selection Play Button Overlay
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF202020)),
            contentAlignment = Alignment.Center,
        ) {
            if (!song.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = DesktopColors.Fog,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Dark Scrim + Play/Pause Icon Overlay on Hover or Keyboard Selection or when playing
            if (isHighlighted || (isCurrentSong && isPlaying)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isCurrentSong && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isCurrentSong && isPlaying) "Jeda" else if (isCurrentSong) "Lanjutkan" else "Putar",
                        tint = if (isCurrentSong) DesktopColors.AcidLime else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and Subtitle ("Song • Artist")
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = song.title,
                color = if (isCurrentSong) DesktopColors.AcidLime else if (isHighlighted) Color.White else DesktopColors.Paper,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Song • ${song.artist}",
                color = if (isHighlighted) Color(0xFFCCCCCC) else DesktopColors.Fog,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Buttons on Far Right
        if (isRecent) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Hapus dari riwayat",
                    tint = if (isHighlighted) DesktopColors.Paper else DesktopColors.Fog,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Tambahkan ke playlist",
                    tint = if (isHighlighted) DesktopColors.Paper else DesktopColors.Fog,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
