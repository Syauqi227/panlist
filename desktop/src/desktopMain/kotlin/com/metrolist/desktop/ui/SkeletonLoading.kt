package com.metrolist.desktop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmerEffect(
    shape: Shape = DesktopRadii.Badge,
    durationMillis: Int = 1200,
): Modifier {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            DesktopColors.Carbon,
            DesktopColors.Slate.copy(alpha = 0.6f),
            DesktopColors.Carbon,
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 400f, 400f),
    )
    return this.clip(shape).background(shimmerBrush)
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = DesktopRadii.Badge,
) {
    Box(modifier = modifier.shimmerEffect(shape = shape))
}

@Composable
fun SongListItemSkeleton(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail skeleton
        SkeletonBox(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist lines
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(13.dp),
                shape = RoundedCornerShape(4.dp),
            )
            Spacer(modifier = Modifier.height(7.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp),
                shape = RoundedCornerShape(4.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Duration / action skeleton
        SkeletonBox(
            modifier = Modifier
                .width(38.dp)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

@Composable
fun SongListSkeleton(
    count: Int = 7,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) {
            SongListItemSkeleton()
        }
    }
}

@Composable
fun PlaylistCardSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DesktopRadii.Card)
            .background(DesktopColors.Carbon.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        // Square cover art skeleton
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = DesktopRadii.Card,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Playlist title skeleton
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Author / song count skeleton
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

@Composable
fun PlaylistGridSkeleton(
    count: Int = 8,
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = 170.dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minColumnWidth),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(count) {
            PlaylistCardSkeleton()
        }
    }
}

@Composable
fun PlaylistDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        // Header: Big Cover Art + Playlist Metadata Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            SkeletonBox(
                modifier = Modifier.size(170.dp),
                shape = DesktopRadii.Card,
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom,
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .width(70.dp)
                        .height(14.dp),
                    shape = DesktopRadii.Badge,
                )
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(13.dp),
                    shape = RoundedCornerShape(4.dp),
                )
                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons skeleton
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(110.dp)
                            .height(36.dp),
                        shape = DesktopRadii.Card,
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .width(110.dp)
                            .height(36.dp),
                        shape = DesktopRadii.Card,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Song table / list skeleton
        SongListSkeleton(count = 6)
    }
}
