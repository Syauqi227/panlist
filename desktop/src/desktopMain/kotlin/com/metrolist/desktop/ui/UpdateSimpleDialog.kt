package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.metrolist.desktop.updater.DesktopReleaseInfo
import com.metrolist.desktop.updater.DesktopUpdater

@Composable
fun UpdateSimpleDialog(
    releaseInfo: DesktopReleaseInfo,
    isStaged: Boolean,
    onDismissRequest: () -> Unit,
    onSkipVersion: () -> Unit,
    onUpdateClick: () -> Unit,
    onRestartClick: () -> Unit,
) {
    val cleanCurrentVersion = DesktopUpdater.cleanVersion(DesktopUpdater.CURRENT_VERSION)
    val cleanNewVersion = DesktopUpdater.cleanVersion(releaseInfo.versionName)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                // Header with title and close icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (isStaged) Icons.Default.Refresh else Icons.Default.ArrowCircleUp,
                            contentDescription = null,
                            tint = if (isStaged) DesktopColors.PulseGreen else DesktopColors.AcidLime,
                            modifier = Modifier.size(22.dp),
                        )
                        Column {
                            Text(
                                text = if (isStaged) "Pasang Pembaruan" else "Pembaruan Tersedia",
                                color = DesktopColors.Paper,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = if (isStaged) "Versi $cleanNewVersion siap dipasang" else "Versi $cleanNewVersion siap diunduh",
                                color = DesktopColors.Mist,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = DesktopColors.Fog,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Version detail card (Linear style)
                Card(
                    shape = DesktopRadii.Button,
                    colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Versi Saat Ini", color = DesktopColors.Fog, fontSize = 11.sp)
                                Text(
                                    "v$cleanCurrentVersion",
                                    color = DesktopColors.Mist,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                            Text("➔", color = DesktopColors.Fog, fontSize = 14.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Versi Terbaru", color = DesktopColors.AcidLime, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "v$cleanNewVersion",
                                    color = DesktopColors.Paper,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        if (releaseInfo.title.isNotBlank() && releaseInfo.title != releaseInfo.tagName) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = releaseInfo.title,
                                color = DesktopColors.Paper.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons: Skip/Cancel on left, Update/Restart on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isStaged) {
                        TextButton(
                            onClick = onSkipVersion,
                            colors = ButtonDefaults.textButtonColors(contentColor = DesktopColors.Fog),
                        ) {
                            Text("Lewati versi ini", fontSize = 12.sp)
                        }
                    } else {
                        TextButton(
                            onClick = onDismissRequest,
                            colors = ButtonDefaults.textButtonColors(contentColor = DesktopColors.Fog),
                        ) {
                            Text("Nanti", fontSize = 12.sp)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isStaged) {
                            Button(
                                onClick = onUpdateClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopColors.AcidLime,
                                    contentColor = DesktopColors.Void,
                                ),
                                shape = DesktopRadii.Button,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                            ) {
                                Icon(
                                    Icons.Default.ArrowCircleUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = DesktopColors.Void,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Update",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DesktopColors.Void,
                                )
                            }
                        } else {
                            Button(
                                onClick = onRestartClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopColors.PulseGreen,
                                    contentColor = DesktopColors.Paper,
                                ),
                                shape = DesktopRadii.Button,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = DesktopColors.Paper,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Restart Sekarang",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DesktopColors.Paper,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
