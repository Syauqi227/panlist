package com.metrolist.desktop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.desktop.cast.DeviceDiscoveryService
import com.metrolist.desktop.cast.DeviceType
import com.metrolist.desktop.cast.DiscoveredDevice
import com.metrolist.desktop.cast.GoogleCastManager
import com.metrolist.desktop.cast.WindowsCastHelper

@Composable
fun ConnectDeviceFlyout(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices by DeviceDiscoveryService.devices.collectAsState()
    val selectedId by DeviceDiscoveryService.selectedDeviceId.collectAsState()
    val isScanning by DeviceDiscoveryService.isScanning.collectAsState()
    val isCastConnected by GoogleCastManager.isConnected.collectAsState()
    val isCastConnecting by GoogleCastManager.isConnecting.collectAsState()
    val connectedDevice by GoogleCastManager.connectedDevice.collectAsState()
    val statusText by GoogleCastManager.statusText.collectAsState()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSupportedDevicesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DeviceDiscoveryService.startScan()
    }

    Card(
        shape = DesktopRadii.Card,
        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
        border = BorderStroke(1.dp, DesktopColors.Border),
        modifier = modifier
            .width(360.dp)
            .heightIn(min = 420.dp, max = 560.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                // Mencegah klik di dalam flyout menutup flyout
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            // --- 1. Header: "Connect" & Close Button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Connect",
                        color = DesktopColors.Paper,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    if (isScanning || isCastConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = DesktopColors.AcidLime,
                            strokeWidth = 2.dp,
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

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2. Konten List Scrollable ---
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Perangkat Pertama: "This computer" (Komputer ini)
                val computerDevice = devices.firstOrNull { it.id == "local_pc" }
                if (computerDevice != null) {
                    item {
                        val isSelected = selectedId == "local_pc" && !isCastConnected
                        Card(
                            shape = DesktopRadii.Button,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) DesktopColors.Obsidian else DesktopColors.Void,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DesktopColors.AcidLime.copy(alpha = 0.5f) else DesktopColors.Border,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DesktopRadii.Button)
                                .clickable {
                                    if (isCastConnected || isCastConnecting) {
                                        GoogleCastManager.disconnect()
                                    }
                                    DeviceDiscoveryService.selectDevice("local_pc")
                                }
                                .pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    Icons.Default.Laptop,
                                    contentDescription = null,
                                    tint = if (isSelected) DesktopColors.AcidLime else DesktopColors.Fog,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "This computer",
                                        color = if (isSelected) DesktopColors.AcidLime else DesktopColors.Paper,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isSelected) "Pemutar audio aktif" else "Output speaker lokal",
                                        color = if (isSelected) DesktopColors.AcidLime.copy(alpha = 0.8f) else DesktopColors.Fog,
                                        fontSize = 11.5.sp,
                                    )
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(DesktopColors.AcidLime),
                                    )
                                }
                            }
                        }
                    }
                }

                // Header Perangkat Terdeteksi
                val remoteDevices = devices.filter { it.id != "local_pc" }
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Perangkat Lain di Sekitar",
                            color = DesktopColors.Fog,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        IconButton(
                            onClick = { DeviceDiscoveryService.startScan() },
                            modifier = Modifier.size(22.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Pindai ulang",
                                tint = DesktopColors.Fog,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                if (remoteDevices.isEmpty()) {
                    item {
                        Card(
                            shape = DesktopRadii.Button,
                            colors = CardDefaults.cardColors(containerColor = DesktopColors.Void),
                            border = BorderStroke(1.dp, DesktopColors.Border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = DesktopColors.AcidLime,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Memindai Smart TV & speaker...",
                                        color = DesktopColors.Mist,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = DesktopColors.Fog,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Belum ada perangkat nirkabel terdeteksi",
                                        color = DesktopColors.Mist,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Pastikan TV dan laptop di Wi-Fi yang sama",
                                        color = DesktopColors.Fog,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (!statusText.isNullOrBlank() && !isCastConnected && !isCastConnecting) {
                        item {
                            Card(
                                shape = DesktopRadii.Badge,
                                colors = CardDefaults.cardColors(containerColor = DesktopColors.CoralRed.copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, DesktopColors.CoralRed.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            ) {
                                Text(
                                    text = statusText ?: "",
                                    color = DesktopColors.CoralRed,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    items(remoteDevices, key = { it.id }) { dev ->
                        val isConnectedToThis = isCastConnected && (connectedDevice?.id == dev.id || selectedId == dev.id)
                        val isConnectingToThis = isCastConnecting && (connectedDevice?.id == dev.id || selectedId == dev.id)
                        val isSelected = isConnectedToThis || isConnectingToThis || (selectedId == dev.id && !isCastConnected)

                        Card(
                            shape = DesktopRadii.Button,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) DesktopColors.Obsidian else DesktopColors.Void,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DesktopColors.AcidLime.copy(alpha = 0.5f) else DesktopColors.Border,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DesktopRadii.Button)
                                .clickable {
                                    if (isConnectedToThis) {
                                        // Putuskan koneksi Cast dan kembali ke speaker lokal komputer
                                        GoogleCastManager.disconnect()
                                    } else if (dev.protocol == "Google Cast" || dev.ipAddress != null) {
                                        // Hubungkan langsung via Google Cast TLS (port 8009) tanpa membuka Windows Cast!
                                        GoogleCastManager.connect(dev)
                                    } else {
                                        // Miracast / display nirkabel Windows
                                        DeviceDiscoveryService.selectDevice(dev.id)
                                        WindowsCastHelper.openCastFlyout()
                                    }
                                }
                                .pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                val deviceIcon = when (dev.type) {
                                    DeviceType.SMART_TV -> Icons.Default.Tv
                                    DeviceType.SPEAKER -> Icons.Default.Speaker
                                    DeviceType.BLUETOOTH -> Icons.Default.Bluetooth
                                    DeviceType.COMPUTER -> Icons.Default.Laptop
                                }

                                Icon(
                                    deviceIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) DesktopColors.AcidLime else DesktopColors.Paper,
                                    modifier = Modifier.size(24.dp),
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dev.name,
                                        color = if (isSelected) DesktopColors.AcidLime else DesktopColors.Paper,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (isConnectingToThis) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(11.dp),
                                                color = DesktopColors.AcidLime,
                                                strokeWidth = 1.5.dp,
                                            )
                                            Text(
                                                text = "Menghubungkan ke ${dev.name}...",
                                                color = DesktopColors.AcidLime,
                                                fontSize = 11.5.sp,
                                            )
                                        } else if (isConnectedToThis) {
                                            Icon(
                                                Icons.Default.CastConnected,
                                                contentDescription = null,
                                                tint = DesktopColors.AcidLime,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Text(
                                                text = "Terhubung • ${dev.protocol}",
                                                color = DesktopColors.AcidLime,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Cast,
                                                contentDescription = null,
                                                tint = DesktopColors.Fog,
                                                modifier = Modifier.size(11.dp),
                                            )
                                            Text(
                                                text = dev.protocol,
                                                color = DesktopColors.Fog,
                                                fontSize = 11.5.sp,
                                            )
                                        }
                                    }
                                }

                                if (isConnectedToThis) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(DesktopColors.AcidLime),
                                    )
                                } else if (isConnectingToThis) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = DesktopColors.AcidLime,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }

                // Tombol pintasan langsung Windows Cast
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = DesktopRadii.Button,
                        colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DesktopRadii.Button)
                            .clickable {
                                WindowsCastHelper.openCastFlyout()
                            }
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = null,
                                tint = DesktopColors.AcidLime,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pindai via Windows Cast (Win + K)",
                                    color = DesktopColors.Paper,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Buka panel nirkabel native Windows",
                                    color = DesktopColors.Fog,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 3. Footer Links: "Don't see your device?" & "What can I connect to?" ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DesktopRadii.Small)
                        .clickable { showHelpDialog = true }
                        .padding(vertical = 4.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Don't see your device?",
                        color = DesktopColors.Mist,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = DesktopColors.Fog,
                        modifier = Modifier.size(14.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DesktopRadii.Small)
                        .clickable { showSupportedDevicesDialog = true }
                        .padding(vertical = 4.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "What can I connect to?",
                        color = DesktopColors.Mist,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = DesktopColors.Fog,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }

    // Modal Panduan Bantuan jika perangkat tidak terlihat
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text("Tidak Melihat Perangkat Anda?", color = DesktopColors.Paper, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. Pastikan Smart TV dan PC terhubung ke jaringan Wi-Fi lokal yang sama.",
                        color = DesktopColors.Mist,
                        fontSize = 12.5.sp,
                    )
                    Text(
                        "2. Pastikan fitur Cast / Miracast / AirPlay / DLNA diaktifkan di menu Pengaturan TV Anda.",
                        color = DesktopColors.Mist,
                        fontSize = 12.5.sp,
                    )
                    Text(
                        "3. Untuk speaker Bluetooth, pastikan Bluetooth aktif dan perangkat dalam mode pairing.",
                        color = DesktopColors.Mist,
                        fontSize = 12.5.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHelpDialog = false
                        WindowsCastHelper.openBluetoothSettings()
                    },
                ) {
                    Text("Pengaturan Bluetooth", color = DesktopColors.AcidLime)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Tutup", color = DesktopColors.Fog)
                }
            },
            containerColor = DesktopColors.Obsidian,
            shape = DesktopRadii.Card,
        )
    }

    // Modal Perangkat yang Didukung
    if (showSupportedDevicesDialog) {
        AlertDialog(
            onDismissRequest = { showSupportedDevicesDialog = false },
            title = {
                Text("Perangkat yang Didukung", color = DesktopColors.Paper, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Smart TV: Samsung (Tizen), LG (webOS), Toshiba, Android TV, Google TV, Roku, Sony Bravia.", color = DesktopColors.Mist, fontSize = 12.5.sp)
                    Text("• Streaming Adapter: Chromecast, Google Cast, Wireless Display adapter.", color = DesktopColors.Mist, fontSize = 12.5.sp)
                    Text("• Audio: Speaker Bluetooth, Soundbar, Headphone nirkabel, dan HDMI Display Audio.", color = DesktopColors.Mist, fontSize = 12.5.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportedDevicesDialog = false }) {
                    Text("Mengerti", color = DesktopColors.AcidLime)
                }
            },
            containerColor = DesktopColors.Obsidian,
            shape = DesktopRadii.Card,
        )
    }
}
