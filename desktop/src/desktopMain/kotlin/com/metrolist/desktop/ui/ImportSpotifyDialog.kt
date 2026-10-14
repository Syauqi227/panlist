package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.desktop.spotify.SpotifyImporter
import kotlinx.coroutines.launch

@Composable
fun ImportSpotifyDialog(
    repository: DesktopMusicRepository,
    onDismissRequest: () -> Unit,
    onPlaylistImported: (DesktopPlaylist) -> Unit,
) {
    var inputUrl by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isImporting) onDismissRequest() }) {
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header dengan Branding Spotify
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(DesktopRadii.Button)
                            .background(DesktopColors.Carbon)
                            .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Button),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Spotify",
                            tint = DesktopColors.Spotify,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Impor Playlist Spotify",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopColors.Paper,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Konversi otomatis lagu Spotify ke stream YouTube Music",
                            fontSize = 12.sp,
                            color = DesktopColors.Mist,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Tempel tautan playlist Spotify publik di bawah ini:",
                    fontSize = 12.sp,
                    color = DesktopColors.Mist,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        errorMessage = null
                    },
                    placeholder = {
                        Text(
                            "https://open.spotify.com/playlist/...",
                            fontSize = 13.sp,
                            color = DesktopColors.Fog,
                        )
                    },
                    singleLine = true,
                    enabled = !isImporting,
                    trailingIcon = {
                        if (inputUrl.isNotBlank() && !isImporting) {
                            IconButton(onClick = { inputUrl = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Hapus tautan", tint = DesktopColors.Fog)
                            }
                        }
                    },
                    shape = DesktopRadii.Input,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesktopColors.Spotify,
                        unfocusedBorderColor = DesktopColors.Border,
                        focusedTextColor = DesktopColors.Paper,
                        unfocusedTextColor = DesktopColors.Paper,
                        focusedContainerColor = DesktopColors.Carbon,
                        unfocusedContainerColor = DesktopColors.Carbon,
                        cursorColor = DesktopColors.Spotify,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Error message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = DesktopColors.CoralRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(errorMessage!!, color = DesktopColors.CoralRed, fontSize = 12.sp)
                    }
                }

                // Progress Bar saat Konversi
                if (isImporting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progressFraction > 0f) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = DesktopColors.Spotify,
                            trackColor = DesktopColors.Slate,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(DesktopRadii.Badge),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = DesktopColors.Spotify,
                            trackColor = DesktopColors.Slate,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(DesktopRadii.Badge),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progressText.ifBlank { "Menghubungkan ke Spotify..." },
                        fontSize = 12.sp,
                        color = DesktopColors.Mist,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        enabled = !isImporting,
                        colors = ButtonDefaults.textButtonColors(contentColor = DesktopColors.Fog),
                        shape = DesktopRadii.Button,
                    ) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (inputUrl.isBlank()) {
                                errorMessage = "Masukkan tautan playlist Spotify terlebih dahulu."
                                return@Button
                            }
                            isImporting = true
                            errorMessage = null
                            progressText = "Membaca data playlist dari Spotify..."
                            progressFraction = 0f

                            scope.launch {
                                val result = SpotifyImporter.importPlaylist(
                                    inputUrl = inputUrl,
                                    repository = repository,
                                    onProgress = { current, total, songTitle ->
                                        progressFraction = current.toFloat() / total.toFloat()
                                        progressText = "Mengonversi $current / $total: $songTitle"
                                    }
                                )

                                isImporting = false
                                if (result.isSuccess) {
                                    val playlist = result.getOrNull()
                                    if (playlist != null) {
                                        onPlaylistImported(playlist)
                                    }
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Gagal mengimpor playlist Spotify."
                                }
                            }
                        },
                        enabled = !isImporting && inputUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopColors.Spotify,
                            contentColor = Color.White,
                            disabledContainerColor = DesktopColors.Slate,
                            disabledContentColor = DesktopColors.Ash,
                        ),
                        shape = DesktopRadii.Button,
                    ) {
                        Text(
                            text = if (isImporting) "Mengonversi..." else "Mulai Impor",
                            color = if (!isImporting && inputUrl.isNotBlank()) Color.White else DesktopColors.Ash,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
