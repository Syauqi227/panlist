package com.metrolist.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class UpdateToastType {
    DOWNLOADING,
    READY,
    INFO,
}

data class UpdateToastData(
    val title: String,
    val message: String? = null,
    val type: UpdateToastType = UpdateToastType.INFO,
    val durationMs: Long = 4000L,
)

@Composable
fun BoxScope.UpdateToastOverlay(
    toast: UpdateToastData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(toast.durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 88.dp),
    ) {
        if (toast != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DesktopColors.Obsidian),
                border = BorderStroke(1.dp, DesktopColors.Border),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.widthIn(max = 380.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    when (toast.type) {
                        UpdateToastType.DOWNLOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DesktopColors.AcidLime,
                                strokeWidth = 2.dp,
                            )
                        }
                        UpdateToastType.READY -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = DesktopColors.PulseGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        UpdateToastType.INFO -> {
                            Icon(
                                imageVector = Icons.Default.ArrowCircleUp,
                                contentDescription = null,
                                tint = DesktopColors.AcidLime,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = toast.title,
                            color = DesktopColors.Paper,
                            fontSize = 13.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        if (!toast.message.isNullOrBlank()) {
                            Text(
                                text = toast.message,
                                color = DesktopColors.Mist,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
