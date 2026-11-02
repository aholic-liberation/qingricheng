package com.qingricheng.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cream = Color(0xFFF7F4EC)
val CreamDeep = Color(0xFFEFE9DD)
val Paper = Color(0xFFFFFDF7)
val Ink = Color(0xFF20242A)
val Muted = Color(0xFF667085)
// Signal: the only high-salience hue. Reserved for the primary action (send)
// and "now" markers. Everything else stays in the calm paper/ink range.
val Accent = Color(0xFF1D4ED8)
val AccentSoft = Color(0xFFE6EDFB)
// Low-salience informational colors.
val Sage = Color(0xFF3F6B5A)
val Remote = Color(0xFF5B6B8C)
val RemoteSoft = Color(0xFFE7EAF0)
val Line = Color(0x33A8A297)

val StickyButter = Color(0xFFF3E6A6)
val StickyPeach = Color(0xFFF2D6C6)
val StickySage = Color(0xFFDCE8D8)
val StickySky = Color(0xFFDCE7F7)
val StickyRose = Color(0xFFF3D7DF)

private val colors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = CreamDeep,
    onPrimaryContainer = Ink,
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = Accent,
    tertiary = Sage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6EFE8),
    onTertiaryContainer = Sage,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Cream,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = CreamDeep,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFB3261E)
)

private val typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 25.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp)
)

private val shapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
)

@Composable
fun QingRiChengTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content
    )
}

fun stickyColor(name: String): Color = when (name) {
    "peach" -> StickyPeach
    "sage" -> StickySage
    "sky" -> StickySky
    "rose" -> StickyRose
    else -> StickyButter
}
