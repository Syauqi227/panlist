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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong

@Composable
fun AddToPlaylistPanel(
    song: DesktopSong,
    playlists: List<DesktopPlaylist>,
    feedbackMessage: String? = null,
    onClose: () -> Unit,
    onSelectPlaylist: (playlist: DesktopPlaylist) -> Unit,
    onCreateNewPlaylistAndAdd: (title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newPlaylistTitle by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }

    val newPlaylistFocusRequester = remember { FocusRequester() }

    val handleCreateNew = {
        if (newPlaylistTitle.isNotBlank()) {
            onCreateNewPlaylistAndAdd(newPlaylistTitle.trim())
            newPlaylistTitle = ""
            isCreatingNew = false
        }
    }

    LaunchedEffect(isCreatingNew) {
        if (isCreatingNew) {
            newPlaylistFocusRequester.requestFocus()
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
            // Header: Title & Close (X)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = DesktopColors.AcidLime,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tambah ke Playlist",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopColors.Paper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

            Spacer(modifier = Modifier.height(12.dp))

            // Active Song Preview Card
            Card(
                shape = DesktopRadii.Button,
                colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
                border = BorderStroke(1.dp, DesktopColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
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
                                .background(DesktopColors.Carbon),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = DesktopColors.Fog, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = DesktopColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            fontSize = 11.5.sp,
                            color = DesktopColors.Mist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Inline form to create a new playlist
            if (isCreatingNew) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newPlaylistTitle,
                        onValueChange = { newPlaylistTitle = it },
                        placeholder = { Text("Nama playlist baru...", fontSize = 12.5.sp, color = DesktopColors.Fog) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { handleCreateNew() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesktopColors.AcidLime,
                            unfocusedBorderColor = DesktopColors.Border,
                            focusedTextColor = DesktopColors.Paper,
                            unfocusedTextColor = DesktopColors.Paper,
                            focusedContainerColor = DesktopColors.Obsidian,
                            unfocusedContainerColor = DesktopColors.Obsidian,
                            cursorColor = DesktopColors.AcidLime,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(newPlaylistFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                    handleCreateNew()
                                    true
                                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                    isCreatingNew = false
                                    true
                                } else {
                                    false
                                }
                            },
                        shape = DesktopRadii.Input,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { isCreatingNew = false },
                            shape = DesktopRadii.Button,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text("Batal", color = DesktopColors.Fog, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { handleCreateNew() },
                            enabled = newPlaylistTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopColors.AcidLime,
                                contentColor = DesktopColors.Void,
                                disabledContainerColor = DesktopColors.Slate,
                                disabledContentColor = DesktopColors.Ash,
                            ),
                            shape = DesktopRadii.Button,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(
                                text = "Simpan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (newPlaylistTitle.isNotBlank()) DesktopColors.Void else DesktopColors.Ash,
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { isCreatingNew = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DesktopColors.Obsidian,
                        contentColor = DesktopColors.Mist,
                    ),
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    shape = DesktopRadii.Button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DesktopColors.AcidLime, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Playlist Baru", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = DesktopRadii.Button,
                    color = DesktopColors.AcidLime.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, DesktopColors.AcidLime.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = DesktopColors.AcidLime,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feedbackMessage,
                            color = DesktopColors.Paper,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Pilih Playlist Tujuan",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DesktopColors.Fog,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable list of local playlists
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Belum ada playlist lokal. Buat satu di atas untuk menyimpan lagu.",
                        color = DesktopColors.Fog,
                        fontSize = 12.5.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(playlists, key = { _, pl -> pl.id }) { _, pl ->
                        val alreadyInPlaylist = pl.songs.any { it.id == song.id }
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()

                        Card(
                            shape = DesktopRadii.Button,
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isHovered -> DesktopColors.Slate.copy(alpha = 0.55f)
                                    alreadyInPlaylist -> DesktopColors.Obsidian
                                    else -> DesktopColors.Void
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isHovered -> DesktopColors.Smoke
                                    alreadyInPlaylist -> DesktopColors.AcidLime.copy(alpha = 0.4f)
                                    else -> DesktopColors.Border
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .hoverable(interactionSource)
                                .clip(DesktopRadii.Button)
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    onSelectPlaylist(pl)
                                }
                                .pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = if (alreadyInPlaylist) DesktopColors.AcidLime else DesktopColors.Fog,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.title,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.5.sp,
                                            color = DesktopColors.Paper,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${pl.songs.size} lagu",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = DesktopColors.Fog,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (alreadyInPlaylist) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Sudah ditambahkan",
                                            tint = DesktopColors.AcidLime,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Ada",
                                            color = DesktopColors.AcidLime,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = DesktopColors.AcidLime,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            "Tambah",
                                            color = DesktopColors.AcidLime,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Anda dapat terus menggulir dan memilih lagu lain di sebelah kanan. Tekan X untuk menutup.",
                fontSize = 11.sp,
                color = DesktopColors.Ash,
                lineHeight = 15.sp,
            )
        }
    }
}
