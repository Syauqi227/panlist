package com.metrolist.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.repository.DesktopMusicRepository

data class LyricEntry(
    val timeMs: Long,
    val text: String,
)

object LrcParser {
    private val LRC_REGEX = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]\s*(.*)$""")
    private val AGENT_TAG_REGEX = Regex("""\{agent:[^}]+\}""")
    private val BG_TAG_REGEX = Regex("""\{bg\}""")
    private val WORD_TAG_REGEX = Regex("""<[^>]+>""")
    private val METADATA_PREFIX_REGEX = Regex("""^(written by|composer|producer|lyrics by|arranger|作词|作曲|编曲|制作|演唱|by:)\b.*""", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<LyricEntry> {
        val list = mutableListOf<LyricEntry>()
        content.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            val match = LRC_REGEX.find(trimmed)
            if (match != null) {
                val (minStr, secStr, fracStr, text) = match.destructured
                val mins = minStr.toLongOrNull() ?: 0L
                val secs = secStr.toLongOrNull() ?: 0L
                val ms = when (fracStr.length) {
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fracStr.toLongOrNull() ?: 0L
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    else -> 0L
                }
                val totalMs = mins * 60000L + secs * 1000L + ms
                val cleanText = text
                    .replace(AGENT_TAG_REGEX, "")
                    .replace(BG_TAG_REGEX, "")
                    .replace(WORD_TAG_REGEX, "")
                    .trim()

                val isMetadata = totalMs <= 3000L && (METADATA_PREFIX_REGEX.containsMatchIn(cleanText) || cleanText.contains(" - "))
                if (cleanText.isNotBlank() && !isMetadata) {
                    list.add(LyricEntry(totalMs, cleanText))
                }
            }
        }
        return list.sortedBy { it.timeMs }
    }
}

@Composable
fun SyncedLyricsView(
    lyricsText: String,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    lyricsOffsetMs: Long = 0L,
    isLargeMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val entries = remember(lyricsText) { LrcParser.parse(lyricsText) }

    if (entries.isEmpty()) {
        // Fallback untuk lirik teks biasa tanpa penanda waktu
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isLargeMode) 24.dp else 8.dp, vertical = if (isLargeMode) 24.dp else 8.dp)
        ) {
            Surface(
                color = DesktopColors.Carbon,
                shape = DesktopRadii.Button,
                border = BorderStroke(1.dp, DesktopColors.Border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ℹ️ Lagu ini belum memiliki penanda waktu irama dari penyedia lirik (ditampilkan sebagai teks statis).",
                        fontSize = if (isLargeMode) 13.sp else 12.sp,
                        color = DesktopColors.Mist,
                        lineHeight = 18.sp,
                    )
                }
            }
            Text(
                text = lyricsText,
                color = DesktopColors.Paper,
                fontSize = if (isLargeMode) 20.sp else 15.sp,
                lineHeight = if (isLargeMode) 34.sp else 26.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    val activeIndex = remember(currentPosition, entries, lyricsOffsetMs) {
        val effectivePos = currentPosition + lyricsOffsetMs
        entries.indexOfLast { it.timeMs <= effectivePos }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetScroll = (activeIndex - (if (isLargeMode) 3 else 2)).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = if (isLargeMode) 90.dp else 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLargeMode) 16.dp else 10.dp),
    ) {
        itemsIndexed(entries) { index, entry ->
            val isActive = index == activeIndex
            val isPast = index < activeIndex

            val textColor = when {
                isActive -> DesktopColors.Paper
                isPast -> DesktopColors.Fog
                else -> DesktopColors.Ash
            }
            val fontSize = when {
                isLargeMode && isActive -> 24.sp
                isLargeMode -> 18.sp
                isActive -> 18.sp
                else -> 15.sp
            }
            val fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DesktopRadii.Button)
                    .background(if (isActive) DesktopColors.AcidLime.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSeek(entry.timeMs) }
                    .padding(horizontal = if (isLargeMode) 16.dp else 12.dp, vertical = if (isLargeMode) 10.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .width(if (isLargeMode) 3.5.dp else 2.5.dp)
                            .height(if (isLargeMode) 22.dp else 16.dp)
                            .clip(DesktopRadii.Badge)
                            .background(DesktopColors.AcidLime)
                    )
                    Spacer(modifier = Modifier.width(if (isLargeMode) 14.dp else 10.dp))
                }

                Text(
                    text = entry.text,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    lineHeight = when {
                        isLargeMode && isActive -> 34.sp
                        isLargeMode -> 26.sp
                        isActive -> 26.sp
                        else -> 22.sp
                    },
                )
            }
        }
    }
}

@Composable
fun DesktopLyricsDialog(
    song: DesktopSong,
    player: VlcAudioPlayer,
    repository: DesktopMusicRepository,
    onDismiss: () -> Unit,
) {
    var lyricsText by remember(song.id) { mutableStateOf<String?>(null) }
    var isLoadingLyrics by remember(song.id) { mutableStateOf(true) }

    val currentPosition by player.currentPosition.collectAsState()

    LaunchedEffect(song.id) {
        isLoadingLyrics = true
        lyricsText = repository.getSongLyrics(song)
        isLoadingLyrics = false
    }

    val hasSyncedLyrics = remember(lyricsText) {
        !lyricsText.isNullOrBlank() && LrcParser.parse(lyricsText!!).isNotEmpty()
    }
    var lyricsOffsetMs by remember(song.id) { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!song.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(DesktopRadii.Badge)
                                .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Badge)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(DesktopRadii.Badge)
                                .background(DesktopColors.Carbon),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = DesktopColors.AcidLime,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = DesktopColors.Paper,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (hasSyncedLyrics) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(DesktopRadii.Badge)
                                        .background(DesktopColors.AcidLime.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "♪ Sinkron",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DesktopColors.AcidLime
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(DesktopRadii.Badge)
                                        .background(DesktopColors.Slate)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "-0.5s",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DesktopColors.Mist,
                                        modifier = Modifier
                                            .clip(DesktopRadii.Badge)
                                            .clickable { lyricsOffsetMs -= 500L }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                    if (lyricsOffsetMs != 0L) {
                                        Text(
                                            text = "${if (lyricsOffsetMs > 0) "+" else ""}${lyricsOffsetMs / 1000.0}s",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DesktopColors.AcidLime,
                                            modifier = Modifier
                                                .clip(DesktopRadii.Badge)
                                                .clickable { lyricsOffsetMs = 0L }
                                                .padding(horizontal = 3.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "+0.5s",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DesktopColors.Mist,
                                        modifier = Modifier
                                            .clip(DesktopRadii.Badge)
                                            .clickable { lyricsOffsetMs += 500L }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (!lyricsText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(DesktopRadii.Badge)
                                        .background(DesktopColors.Slate)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Teks Biasa",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DesktopColors.Fog
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            fontSize = 12.sp,
                            color = DesktopColors.Mist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = DesktopColors.Fog,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 500.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = if (isLoadingLyrics || lyricsText.isNullOrBlank()) Alignment.Center else Alignment.TopStart,
            ) {
                if (isLoadingLyrics) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    ) {
                        CircularProgressIndicator(
                            color = DesktopColors.AcidLime,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Menyinkronkan lirik lagu...",
                            color = DesktopColors.Mist,
                            fontSize = 13.sp,
                        )
                    }
                } else if (lyricsText.isNullOrBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    ) {
                        Icon(
                            Icons.Default.MusicOff,
                            contentDescription = null,
                            tint = DesktopColors.Fog,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Lirik untuk lagu ini belum tersedia dari penyedia musik.",
                            color = DesktopColors.Mist,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                } else {
                    SyncedLyricsView(
                        lyricsText = lyricsText!!,
                        currentPosition = currentPosition,
                        onSeek = { player.seekTo(it) },
                        lyricsOffsetMs = lyricsOffsetMs,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 480.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = DesktopRadii.Button,
            ) {
                Text("Tutup", color = DesktopColors.AcidLime, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = DesktopColors.Obsidian,
        shape = DesktopRadii.Card,
    )
}

/**
 * Halaman penuh lirik lagu (Full Page Lyrics View)
 * Menampilkan lirik lagu dalam tata letak imersif halaman penuh ala Spotify/Apple Music
 */
@Composable
fun DesktopLyricsPageView(
    song: DesktopSong,
    player: VlcAudioPlayer,
    repository: DesktopMusicRepository,
    onClose: () -> Unit,
    onAddToPlaylist: ((DesktopSong) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var lyricsText by remember(song.id) { mutableStateOf<String?>(null) }
    var isLoadingLyrics by remember(song.id) { mutableStateOf(true) }
    val currentPosition by player.currentPosition.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    var atmosphericColor by remember(song.id) {
        mutableStateOf(NowPlayingColorExtractor.getAtmosphericColor(song.id))
    }

    LaunchedEffect(song.id) {
        isLoadingLyrics = true
        lyricsText = repository.getSongLyrics(song)
        isLoadingLyrics = false
        val sampled = NowPlayingColorExtractor.extractOrSampleColor(song.id, song.thumbnailUrl)
        atmosphericColor = sampled
    }

    val hasSyncedLyrics = remember(lyricsText) {
        !lyricsText.isNullOrBlank() && LrcParser.parse(lyricsText!!).isNotEmpty()
    }
    var lyricsOffsetMs by remember(song.id) { mutableLongStateOf(0L) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        atmosphericColor.copy(alpha = 0.20f),
                        DesktopColors.Carbon,
                        DesktopColors.Void
                    )
                )
            )
    ) {
        val pageW = maxWidth
        val isWideLayout = pageW >= 780.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // === 1. TOP HEADER BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = DesktopColors.Mist,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lirik Lagu",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopColors.Paper,
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    if (hasSyncedLyrics) {
                        Surface(
                            color = DesktopColors.AcidLime.copy(alpha = 0.15f),
                            shape = DesktopRadii.Badge,
                        ) {
                            Text(
                                text = "♪ Lirik Sinkron",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopColors.AcidLime,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Offset Calibration Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(DesktopRadii.Badge)
                                .background(DesktopColors.Slate)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "-0.5s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = DesktopColors.Mist,
                                modifier = Modifier
                                    .clip(DesktopRadii.Badge)
                                    .clickable { lyricsOffsetMs -= 500L }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            if (lyricsOffsetMs != 0L) {
                                Text(
                                    text = "${if (lyricsOffsetMs > 0) "+" else ""}${lyricsOffsetMs / 1000.0}s (Reset)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesktopColors.AcidLime,
                                    modifier = Modifier
                                        .clip(DesktopRadii.Badge)
                                        .clickable { lyricsOffsetMs = 0L }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "+0.5s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = DesktopColors.Mist,
                                modifier = Modifier
                                    .clip(DesktopRadii.Badge)
                                    .clickable { lyricsOffsetMs += 500L }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (!lyricsText.isNullOrBlank()) {
                        Surface(
                            color = DesktopColors.Slate,
                            shape = DesktopRadii.Badge,
                        ) {
                            Text(
                                text = "Teks Biasa",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = DesktopColors.Fog,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onAddToPlaylist != null) {
                        IconButton(
                            onClick = { onAddToPlaylist(song) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Simpan ke Playlist",
                                tint = DesktopColors.Mist,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup Lirik",
                            tint = DesktopColors.Mist,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // === 2. BODY CONTENT ===
            if (isLoadingLyrics) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = DesktopColors.AcidLime,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Menyinkronkan lirik lagu...",
                            color = DesktopColors.Mist,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else if (lyricsText.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.MusicOff,
                            contentDescription = null,
                            tint = DesktopColors.Fog,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Lirik untuk lagu ini belum tersedia dari penyedia musik.",
                            color = DesktopColors.Mist,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            } else {
                if (isWideLayout) {
                    // Two-column widescreen layout (Album info kiri, Lirik kanan)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(36.dp)
                    ) {
                        // Kolom Kiri: Artwork & Info Lagu
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.Start,
                        ) {
                            if (!song.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(DesktopRadii.Card)
                                        .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Card)
                                        .background(DesktopColors.Carbon)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(DesktopRadii.Card)
                                        .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Card)
                                        .background(DesktopColors.Carbon),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = DesktopColors.AcidLime,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = song.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopColors.Paper,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 26.sp,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = song.artist,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = DesktopColors.Mist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (!song.album.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.album,
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = DesktopColors.Fog,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (isPlaying) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(DesktopRadii.Badge)
                                        .background(DesktopColors.Slate)
                                        .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Badge)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    PlayingEqualizerBars(
                                        isPlaying = true,
                                        barWidth = 2.dp,
                                        maxHeight = 12.dp,
                                        barColor = DesktopColors.AcidLime,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sedang Memutar",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DesktopColors.AcidLime,
                                    )
                                }
                            }
                        }

                        // Kolom Kanan: Tampilan Lirik Mengalir
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            SyncedLyricsView(
                                lyricsText = lyricsText!!,
                                currentPosition = currentPosition,
                                onSeek = { player.seekTo(it) },
                                lyricsOffsetMs = lyricsOffsetMs,
                                isLargeMode = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    // Tata letak responsif jendela ramping
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            if (!song.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(DesktopRadii.Button)
                                        .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Button)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column {
                                Text(
                                    text = song.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            SyncedLyricsView(
                                lyricsText = lyricsText!!,
                                currentPosition = currentPosition,
                                onSeek = { player.seekTo(it) },
                                lyricsOffsetMs = lyricsOffsetMs,
                                isLargeMode = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
