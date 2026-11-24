package com.hextapp.android.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HextPrimary = Color(0xFF7B5CFF)
val HextSecondary = Color(0xFF00E5B0)
val HextCyan = Color(0xFF00D4FF)
val HextError = Color(0xFFE54868)

private val DarkPalette = darkColorScheme(
    primary = HextPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF231952),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = HextSecondary,
    onSecondary = Color(0xFF00201A),
    tertiary = HextCyan,
    background = Color(0xFF06070D),
    onBackground = Color(0xFFF4F6FF),
    surface = Color(0xFF0E101C),
    onSurface = Color(0xFFF4F6FF),
    surfaceVariant = Color(0xFF151829),
    onSurfaceVariant = Color(0xFF9EA4C4),
    outline = Color(0xFF282D4A),
    error = HextError,
    onError = Color.White
)

private val LightPalette = lightColorScheme(
    primary = HextPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE8FF),
    onPrimaryContainer = Color(0xFF241366),
    secondary = Color(0xFF009E76),
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF12121A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12121A),
    surfaceVariant = Color(0xFFECEEF6),
    onSurfaceVariant = Color(0xFF5D5F75),
    outline = Color(0xFFDCDFE9),
    error = HextError,
    onError = Color.White
)

private val HextappTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val animSpec = tween<Color>(durationMillis = 260)
    val bg by animateColorAsState(target.background, animSpec, label = "bg")
    val surface by animateColorAsState(target.surface, animSpec, label = "surface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animSpec, label = "surfaceVar")
    val onBg by animateColorAsState(target.onBackground, animSpec, label = "onBg")
    val onSurface by animateColorAsState(target.onSurface, animSpec, label = "onSurface")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, animSpec, label = "onSurfaceVar")
    val outline by animateColorAsState(target.outline, animSpec, label = "outline")

    return target.copy(
        background = bg,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onBackground = onBg,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline
    )
}

@Composable
fun HextappTheme(
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = animatedColorScheme(if (isDarkTheme) DarkPalette else LightPalette)
    MaterialTheme(
        colorScheme = scheme,
        typography = HextappTypography,
        content = content
    )
}
