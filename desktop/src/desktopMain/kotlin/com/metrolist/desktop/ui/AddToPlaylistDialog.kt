package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong

@Composable
fun AddToPlaylistDialog(
    song: DesktopSong,
    playlists: List<DesktopPlaylist>,
    onDismissRequest: () -> Unit,
    onSelectPlaylist: (playlist: DesktopPlaylist) -> Unit,
    onCreateNewPlaylistAndAdd: (title: String) -> Unit,
) {
    var newPlaylistTitle by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.92f).padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header: Dialog Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = DesktopColors.AcidLime,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tambah ke Playlist",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopColors.Paper,
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = DesktopColors.Fog)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Song preview card
                Card(
                    shape = DesktopRadii.Button,
                    colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
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
                                    .size(44.dp)
                                    .clip(DesktopRadii.Badge),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
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
                                fontWeight = FontWeight.SemiBold,
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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inline form to create a new playlist
                if (isCreatingNew) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newPlaylistTitle,
                            onValueChange = { newPlaylistTitle = it },
                            placeholder = { Text("Nama playlist baru...", fontSize = 13.sp, color = DesktopColors.Fog) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DesktopColors.AcidLime,
                                unfocusedBorderColor = DesktopColors.Border,
                                focusedTextColor = DesktopColors.Paper,
                                unfocusedTextColor = DesktopColors.Paper,
                                focusedContainerColor = DesktopColors.Carbon,
                                unfocusedContainerColor = DesktopColors.Carbon,
                                cursorColor = DesktopColors.AcidLime,
                            ),
                            modifier = Modifier.weight(1f),
                            shape = DesktopRadii.Input,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPlaylistTitle.isNotBlank()) {
                                    onCreateNewPlaylistAndAdd(newPlaylistTitle.trim())
                                    newPlaylistTitle = ""
                                    isCreatingNew = false
                                }
                            },
                            enabled = newPlaylistTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopColors.AcidLime,
                                contentColor = DesktopColors.Void,
                                disabledContainerColor = DesktopColors.Slate,
                                disabledContentColor = DesktopColors.Ash,
                            ),
                            shape = DesktopRadii.Button,
                        ) {
                            Text(
                                text = "Simpan",
                                fontWeight = FontWeight.SemiBold,
                                color = if (newPlaylistTitle.isNotBlank()) DesktopColors.Void else DesktopColors.Ash,
                            )
                        }
                        IconButton(onClick = { isCreatingNew = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Batal", tint = DesktopColors.Fog)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { isCreatingNew = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DesktopColors.Carbon,
                            contentColor = DesktopColors.Mist,
                        ),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = DesktopRadii.Button,
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DesktopColors.AcidLime, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buat Playlist Baru", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Pilih Playlist Tujuan:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DesktopColors.Mist,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // List of existing local playlists
                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Belum ada playlist lokal. Buat satu di atas untuk menyimpan lagu!", color = DesktopColors.Fog, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(playlists) { pl ->
                            val alreadyInPlaylist = pl.songs.any { it.id == song.id }
                            Card(
                                shape = DesktopRadii.Button,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (alreadyInPlaylist) DesktopColors.Obsidian else DesktopColors.Carbon
                                ),
                                border = BorderStroke(1.dp, if (alreadyInPlaylist) DesktopColors.BorderSubtle else DesktopColors.Border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectPlaylist(pl)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = pl.title,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = DesktopColors.Paper,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${pl.songs.size} lagu",
                                                fontSize = 11.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = DesktopColors.Fog,
                                            )
                                        }
                                    }

                                    if (alreadyInPlaylist) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Sudah ditambahkan",
                                                tint = DesktopColors.AcidLime,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sudah Ada", color = DesktopColors.AcidLime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                tint = DesktopColors.AcidLime,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tambahkan", color = DesktopColors.AcidLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
