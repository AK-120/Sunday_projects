package com.meetingscribe.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette: deep green ink and gold, the colours of a Kerala kasavu border.
// The recording screen is drenched in LiveGround so it reads from across a table.
val LiveGround = Color(0xFF0A3A2C)
val LiveInk = Color(0xFFEAF2EC)
val LiveMuted = Color(0xFF9FC1B1)
val Gold = Color(0xFFE3A91B)
val OnGold = Color(0xFF241A00)

private val Ink = Color(0xFF0C2A22)
private val Green = Color(0xFF0F5C45)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = Color(0xFF8A6200),
    background = Color(0xFFF2F5F1),
    onBackground = Ink,
    surface = Color(0xFFF2F5F1),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE2E9E3),
    onSurfaceVariant = Color(0xFF51655C),
    outline = Color(0xFF7C8F85),
    outlineVariant = Color(0xFFD0DAD2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CC3A0),
    onPrimary = Color(0xFF00382A),
    secondary = Gold,
    background = Color(0xFF0D1914),
    onBackground = Color(0xFFE3ECE7),
    surface = Color(0xFF0D1914),
    onSurface = Color(0xFFE3ECE7),
    surfaceVariant = Color(0xFF1B2B24),
    onSurfaceVariant = Color(0xFF9DB1A6),
    outline = Color(0xFF6F847A),
    outlineVariant = Color(0xFF2A3D34),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Line heights are generous: Malayalam and other Indic scripts need the room.
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 64.sp, lineHeight = 70.sp, fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
