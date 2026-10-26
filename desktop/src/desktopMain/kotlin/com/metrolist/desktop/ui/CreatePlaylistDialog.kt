package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction

@Composable
fun CreatePlaylistDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val titleFocusRequester = remember { FocusRequester() }

    val handleConfirm = {
        if (title.isNotBlank()) {
            onConfirm(title.trim(), description.trim())
        }
    }

    LaunchedEffect(Unit) {
        titleFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                        onDismissRequest()
                        true
                    } else false
                },
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(DesktopColors.AcidLime, DesktopRadii.Button),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DesktopColors.Void)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Buat Playlist Baru",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopColors.Paper,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nama Playlist") },
                    placeholder = { Text("Contoh: Lagu Favorit, Kerja Fokus, Santai...", color = DesktopColors.Fog) },
                    singleLine = true,
                    shape = DesktopRadii.Input,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleConfirm() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesktopColors.AcidLime,
                        unfocusedBorderColor = DesktopColors.Border,
                        focusedTextColor = DesktopColors.Paper,
                        unfocusedTextColor = DesktopColors.Paper,
                        focusedLabelColor = DesktopColors.AcidLime,
                        unfocusedLabelColor = DesktopColors.Mist,
                        focusedContainerColor = DesktopColors.Carbon,
                        unfocusedContainerColor = DesktopColors.Carbon,
                        cursorColor = DesktopColors.AcidLime,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                handleConfirm()
                                true
                            } else false
                        },
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi (Opsional)") },
                    placeholder = { Text("Keterangan singkat tentang playlist ini", color = DesktopColors.Fog) },
                    maxLines = 3,
                    shape = DesktopRadii.Input,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesktopColors.AcidLime,
                        unfocusedBorderColor = DesktopColors.Border,
                        focusedTextColor = DesktopColors.Paper,
                        unfocusedTextColor = DesktopColors.Paper,
                        focusedLabelColor = DesktopColors.AcidLime,
                        unfocusedLabelColor = DesktopColors.Mist,
                        focusedContainerColor = DesktopColors.Carbon,
                        unfocusedContainerColor = DesktopColors.Carbon,
                        cursorColor = DesktopColors.AcidLime,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.textButtonColors(contentColor = DesktopColors.Fog),
                        shape = DesktopRadii.Button,
                    ) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { handleConfirm() },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopColors.AcidLime,
                            contentColor = DesktopColors.Void,
                            disabledContainerColor = DesktopColors.Slate,
                            disabledContentColor = DesktopColors.Ash,
                        ),
                        shape = DesktopRadii.Button,
                    ) {
                        Text(
                            text = "Simpan Playlist",
                            color = if (title.isNotBlank()) DesktopColors.Void else DesktopColors.Ash,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
