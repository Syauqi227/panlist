package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.metrolist.desktop.updater.DesktopReleaseInfo
import com.metrolist.desktop.updater.DesktopUpdater

@Composable
fun UpdateDialog(
    releaseInfo: DesktopReleaseInfo,
    onDismissRequest: () -> Unit,
    onDownloadClick: (url: String) -> Unit = { url ->
        DesktopUpdater.openBrowser(url)
        onDismissRequest()
    },
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.95f).padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DesktopColors.AcidLime.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.ArrowCircleUp,
                                contentDescription = null,
                                tint = DesktopColors.AcidLime,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                "Pembaruan Tersedia",
                                color = DesktopColors.Paper,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                "Versi ${releaseInfo.versionName} siap diunduh",
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

                Spacer(modifier = Modifier.height(16.dp))

                // Version Badge Card
                Card(
                    shape = DesktopRadii.Button,
                    colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Versi Terpasang", color = DesktopColors.Fog, fontSize = 11.sp)
                            Text(
                                "v${DesktopUpdater.CURRENT_VERSION}",
                                color = DesktopColors.Mist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                        Text("➔", color = DesktopColors.Fog, fontSize = 14.sp)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Versi Terbaru", color = DesktopColors.AcidLime, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "v${releaseInfo.versionName}",
                                color = DesktopColors.Paper,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                if (releaseInfo.title.isNotBlank() && releaseInfo.title != releaseInfo.tagName) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        releaseInfo.title,
                        color = DesktopColors.Paper,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Changelog title
                Text(
                    "Catatan Rilis (Changelog):",
                    color = DesktopColors.Mist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Changelog box
                Card(
                    shape = DesktopRadii.Button,
                    colors = CardDefaults.cardColors(containerColor = DesktopColors.Void),
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 220.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = releaseInfo.description.ifBlank { "Tidak ada catatan rilis rinci." },
                            color = DesktopColors.Paper.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.SansSerif,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons: Later & Download Update
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.Mist),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = DesktopRadii.Button,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Nanti", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val targetUrl = releaseInfo.downloadUrl ?: releaseInfo.releaseUrl
                            onDownloadClick(targetUrl)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopColors.AcidLime,
                            contentColor = DesktopColors.Void,
                        ),
                        shape = DesktopRadii.Button,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Unduh Pembaruan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
