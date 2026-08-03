package com.strik3forc3.ytdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = YtdlColors.Accent,
    onPrimary = YtdlColors.TextPrimary,
    primaryContainer = YtdlColors.AccentContainer,
    onPrimaryContainer = YtdlColors.AccentText,
    secondary = YtdlColors.AccentText,
    onSecondary = YtdlColors.Background,
    background = YtdlColors.Background,
    onBackground = YtdlColors.TextPrimary,
    surface = YtdlColors.Surface,
    onSurface = YtdlColors.TextPrimary,
    surfaceVariant = YtdlColors.SurfaceRaised,
    onSurfaceVariant = YtdlColors.TextMuted,
    outline = YtdlColors.Outline,
    outlineVariant = YtdlColors.Outline,
    error = YtdlColors.Error,
    onError = YtdlColors.TextPrimary,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The Windows app uses 14–20px Segoe UI throughout, which leaves almost no typographic
 * hierarchy — every label competes. These steps separate section headings, row titles and
 * metadata clearly.
 */
private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp,
    ),
)

/**
 * Always dark. The palette is the product's identity, and a light variant of a
 * near-black purple theme would be a different design rather than the same one inverted.
 */
@Composable
fun YtDownloaderTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
