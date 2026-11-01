package com.metrolist.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.metrolist.desktop.updater.DesktopReleaseInfo
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UpdateNavRailButton(
    detectedUpdate: DesktopReleaseInfo?,
    isDownloading: Boolean,
    downloadProgress: Float,
    isStaged: Boolean,
    isChecking: Boolean,
    currentVersion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isTooltipVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(200)
            isTooltipVisible = true
        } else {
            isTooltipVisible = false
        }
    }

    val tooltipText = remember(detectedUpdate, isDownloading, downloadProgress, isStaged, isChecking, currentVersion) {
        val targetVersion = detectedUpdate?.versionName ?: currentVersion
        when {
            isDownloading -> "Mengunduh pembaruan: ${(downloadProgress * 100).toInt()}%"
            isStaged -> "Pembaruan v$targetVersion siap dipasang. Klik untuk me-restart."
            detectedUpdate != null -> "Pembaruan v$targetVersion tersedia"
            isChecking -> "Memeriksa pembaruan..."
            else -> "Echo v$currentVersion (Versi Terbaru)"
        }
    }

    val density = LocalDensity.current
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val gapPx = with(density) { 8.dp.roundToPx() }
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val y = anchorBounds.top - popupContentSize.height - gapPx
                return IntOffset(
                    x = x.coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)),
                    y = y.coerceAtLeast(8),
                )
            }
        }
    }

    val transition = updateTransition(targetState = isTooltipVisible, label = "UpdateTooltipTransition")
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 150, easing = LinearOutSlowInEasing) },
        label = "UpdateTooltipAlpha"
    ) { if (it) 1f else 0f }
    val offsetY by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160, easing = FastOutSlowInEasing) },
        label = "UpdateTooltipOffsetY"
    ) { if (it) 0f else 4f }
    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160, easing = FastOutSlowInEasing) },
        label = "UpdateTooltipScale"
    ) { if (it) 1f else 0.94f }

    val hasUpdate = detectedUpdate != null

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        contentAlignment = Alignment.Center,
    ) {
        // Main Button Surface
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isHovered -> DesktopColors.Slate.copy(alpha = 0.7f)
                        hasUpdate || isStaged -> DesktopColors.Obsidian
                        else -> Color.Transparent
                    }
                )
                .border(
                    BorderStroke(
                        1.dp,
                        when {
                            isHovered -> DesktopColors.Smoke
                            hasUpdate || isStaged -> DesktopColors.Border
                            else -> DesktopColors.BorderSubtle
                        }
                    ),
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center,
        ) {
            // Icon
            val iconTint = when {
                isStaged -> DesktopColors.PulseGreen
                isDownloading -> DesktopColors.AcidLime
                hasUpdate -> if (isHovered) DesktopColors.Paper else DesktopColors.Mist
                isHovered -> DesktopColors.Mist
                else -> DesktopColors.Ash
            }

            if (isStaged) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart untuk Memperbarui",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowCircleUp,
                    contentDescription = "Pembaruan",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Circular progress indicator when downloading
            if (isDownloading) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(42.dp),
                    color = DesktopColors.AcidLime,
                    trackColor = DesktopColors.Slate,
                    strokeWidth = 2.5.dp,
                )
            } else if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    color = DesktopColors.AcidLime,
                    strokeWidth = 2.dp,
                )
            }

            // Red dot notification badge when update is available (not downloading and not staged)
            if (hasUpdate && !isDownloading && !isStaged) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(DesktopColors.CoralRed)
                        .border(BorderStroke(1.5.dp, DesktopColors.Carbon), CircleShape)
                )
            } else if (isStaged) {
                // Green dot badge when staged
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(DesktopColors.PulseGreen)
                        .border(BorderStroke(1.5.dp, DesktopColors.Carbon), CircleShape)
                )
            }
        }

        // Tooltip Popup (Reference Image 2)
        if (transition.currentState || transition.targetState) {
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DesktopColors.Obsidian,
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    shadowElevation = 8.dp,
                    modifier = Modifier.graphicsLayer {
                        this.alpha = alpha
                        this.translationY = offsetY
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                ) {
                    Text(
                        text = tooltipText,
                        color = DesktopColors.Paper,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
