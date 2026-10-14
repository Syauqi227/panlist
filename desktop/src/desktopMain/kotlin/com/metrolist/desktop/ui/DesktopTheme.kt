package com.metrolist.desktop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Linear Design Tokens - Style Reference
 * "midnight precision instrument"
 *
 * Near-black surfaces (#08090a), paper-white type (#ffffff),
 * and one electric acid-lime accent (#e4f222).
 */
object DesktopColors {
    // Linear Core Surfaces
    val Void = Color(0xFF08090A)        // #08090a: Page canvas, full-bleed backgrounds
    val Carbon = Color(0xFF0F1011)      // #0f1011: Card surfaces, nav bars, containers
    val Obsidian = Color(0xFF161718)    // #161718: Elevated surfaces, deeper panels, dialogs
    val Slate = Color(0xFF23252A)       // #23252a: Interactive surface tint, hover

    // Canvas & Container Aliases
    val Background = Void
    val Surface = Carbon
    val Card = Carbon
    val CardElevated = Obsidian
    val CardHover = Slate

    // Linear Hairline Borders
    val Graphite = Color(0xFF23252A)    // #23252a: Subtle hairline borders, ghost button outlines
    val Smoke = Color(0xFF383B3F)       // #383b3f: Section separators, focused outlines
    val Border = Graphite
    val BorderSubtle = Color(0x1AFFFFFF) // Hairline subtle border
    val BorderActive = Smoke

    // Linear Typography Greys & Maximum Contrast Whites
    val Paper = Color(0xFFFFFFFF)       // #ffffff: Primary headings, hero type
    val Bone = Color(0xFFE5E5E6)        // #e5e5e6: Near-white surface fills, high-contrast text
    val Mist = Color(0xFFD0D6E0)        // #d0d6e0: Secondary headings, button text, body
    val Fog = Color(0xFF8A8F98)         // #8a8f98: Tertiary text, placeholder copy, icon fills
    val Ash = Color(0xFF62666D)         // #62666d: Muted body text, inactive icons, metadata

    val TextPrimary = Paper
    val TextSecondary = Mist
    val TextTertiary = Fog
    val TextMuted = Ash

    // Linear Electric Acid Lime Accent - The sole functional flashlight
    val AcidLime = Color(0xFFE4F222)    // #e4f222: Primary action buttons, active indicators
    val AcidLimeHover = Color(0xFFD2E016)
    val OnAcidLime = Void               // #08090a: Max contrast dark text on Acid Lime

    val Primary = AcidLime
    val PrimaryHover = AcidLimeHover
    val PrimaryContainer = Color(0x1AE4F222) // 10% wash
    val OnPrimary = OnAcidLime

    // Linear Supporting Chromatic Accents (tags, badges, status)
    val PulseGreen = Color(0xFF27A644)  // #27a644: Outline accent, offline/success
    val CoralRed = Color(0xFFEB5757)    // #eb5757: Danger / error / delete
    val SignalTeal = Color(0xFF02B8CC)  // #02b8cc: Info, decorative
    val IrisViolet = Color(0xFF6366F1)  // #6366f1: Tag/badge fills
    val Lavender = Color(0xFF8B5CF6)    // #8b5cf6: Secondary tag fills

    val Success = PulseGreen
    val SuccessDark = Color(0xFF0F2615)
    val Error = CoralRed
    val ErrorDark = Color(0xFF2E1214)
    val Warning = Color(0xFFE5A124)
    val WarningDark = Color(0xFF2E220D)

    // Third-party branding (Spotify)
    val Spotify = Color(0xFF1DB954)
    val SpotifyDark = Color(0xFF123820)
    val SpotifyHover = Color(0xFF1ED760)
}

/**
 * Standard Radii Vocabulary according to Linear Design System:
 * - Cards: 12px
 * - Buttons / Inputs: 6px
 * - Pills: 9999px (CircleShape)
 * - Badges: 4px
 * - Small: 2px
 */
object DesktopRadii {
    val Card = RoundedCornerShape(12.dp)
    val Button = RoundedCornerShape(6.dp)
    val Input = RoundedCornerShape(6.dp)
    val Badge = RoundedCornerShape(4.dp)
    val Pill = RoundedCornerShape(9999.dp)
    val Small = RoundedCornerShape(2.dp)
}

val DesktopTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 72.sp,
        lineHeight = 72.sp,
        letterSpacing = (-1.584).sp,
        color = DesktopColors.Paper,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.704).sp,
        color = DesktopColors.Paper,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.288).sp,
        color = DesktopColors.Paper,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.24).sp,
        color = DesktopColors.Paper,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = DesktopColors.Paper,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = DesktopColors.Mist,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.165).sp,
        color = DesktopColors.Mist,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        color = DesktopColors.Fog,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = DesktopColors.Ash,
    ),
)

val DesktopColorScheme = darkColorScheme(
    primary = DesktopColors.Primary,
    onPrimary = DesktopColors.OnPrimary,
    primaryContainer = DesktopColors.PrimaryContainer,
    onPrimaryContainer = DesktopColors.TextPrimary,
    secondary = DesktopColors.PrimaryHover,
    onSecondary = DesktopColors.OnPrimary,
    background = DesktopColors.Background,
    onBackground = DesktopColors.TextPrimary,
    surface = DesktopColors.Surface,
    onSurface = DesktopColors.TextPrimary,
    surfaceVariant = DesktopColors.CardElevated,
    onSurfaceVariant = DesktopColors.TextSecondary,
    outline = DesktopColors.Border,
    outlineVariant = DesktopColors.BorderSubtle,
)

/**
 * Animated equalizer bars for active playback delight.
 * Bounces 3 vertical bars with rhythmic offsets drawn in draw phase.
 */
@Composable
fun PlayingEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = DesktopColors.AcidLime,
    barWidth: Dp = 2.dp,
    maxHeight: Dp = 16.dp,
) {
    val transition = rememberInfiniteTransition()

    val height1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isPlaying) 1.0f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val height2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = if (isPlaying) 0.2f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val height3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isPlaying) 0.9f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, delayMillis = 180, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val density = LocalDensity.current
    val spacingPx = with(density) { 2.dp.toPx() }
    val barWidthPx = with(density) { barWidth.toPx() }
    val totalWidth = barWidth * 3 + 4.dp

    Canvas(
        modifier = modifier.size(width = totalWidth, height = maxHeight)
    ) {
        val totalHeightPx = size.height
        val cornerRadius = CornerRadius(2f, 2f)

        val h1 = (if (isPlaying) totalHeightPx * height1 else 4f).coerceAtLeast(3f)
        val h2 = (if (isPlaying) totalHeightPx * height2 else 6f).coerceAtLeast(3f)
        val h3 = (if (isPlaying) totalHeightPx * height3 else 4f).coerceAtLeast(3f)

        drawRoundRect(
            color = barColor,
            topLeft = Offset(0f, totalHeightPx - h1),
            size = Size(barWidthPx, h1),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = barColor,
            topLeft = Offset(barWidthPx + spacingPx, totalHeightPx - h2),
            size = Size(barWidthPx, h2),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = barColor,
            topLeft = Offset((barWidthPx + spacingPx) * 2, totalHeightPx - h3),
            size = Size(barWidthPx, h3),
            cornerRadius = cornerRadius
        )
    }
}
