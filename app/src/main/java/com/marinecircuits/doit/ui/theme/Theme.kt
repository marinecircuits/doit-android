package com.marinecircuits.doit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val DoitDarkColors = darkColorScheme(
    primary = Color(0xFFA9C2FF), onPrimary = Color(0xFF10234A),
    primaryContainer = Color(0xFF1D2B49), onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFFBAC4D9), onSecondary = Color(0xFF253047),
    secondaryContainer = Color(0xFF252D3B), onSecondaryContainer = Color(0xFFE1E7F5),
    tertiary = Color(0xFFCBBDEF), onTertiary = Color(0xFF33264F),
    tertiaryContainer = Color(0xFF372E49), onTertiaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF0B0D12), onBackground = Color(0xFFEFF2F9),
    surface = Color(0xFF0B0D12), onSurface = Color(0xFFEFF2F9),
    surfaceVariant = Color(0xFF272D38), onSurfaceVariant = Color(0xFFADB6C7),
    surfaceContainerLowest = Color(0xFF07090D), surfaceContainerLow = Color(0xFF14171E),
    surfaceContainer = Color(0xFF1A1E27), surfaceContainerHigh = Color(0xFF232833),
    surfaceContainerHighest = Color(0xFF2D3440), surfaceDim = Color(0xFF0B0D12),
    surfaceBright = Color(0xFF363E4B), surfaceTint = Color.Transparent,
    outline = Color(0xFF78859B), outlineVariant = Color(0xFF2B3342),
    error = Color(0xFFFFB4BD), onError = Color(0xFF640F20),
    errorContainer = Color(0xFF43232D), onErrorContainer = Color(0xFFFFD9E0),
    inverseSurface = Color(0xFFE6EAF3), inverseOnSurface = Color(0xFF1A2030),
    inversePrimary = Color(0xFF365CB2),
)

internal val DoitLightColors = lightColorScheme(
    primary = Color(0xFF365CB2), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF), onPrimaryContainer = Color(0xFF10234A),
    secondary = Color(0xFF566079), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EAF5), onSecondaryContainer = Color(0xFF202A3B),
    tertiary = Color(0xFF68548B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECDDFF), onTertiaryContainer = Color(0xFF28163F),
    background = Color(0xFFF5F7FC), onBackground = Color(0xFF171D2B),
    surface = Color(0xFFF5F7FC), onSurface = Color(0xFF171D2B),
    surfaceVariant = Color(0xFFE5EAF3), onSurfaceVariant = Color(0xFF515D72),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F3FA),
    surfaceContainer = Color(0xFFEAEEF6), surfaceContainerHigh = Color(0xFFE3E8F2),
    surfaceContainerHighest = Color(0xFFDDE3EE),
    outline = Color(0xFF717D92), outlineVariant = Color(0xFFCCD4E3),
    surfaceTint = Color.Transparent,
)

private val DoitTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp),
)

/** An ink, silver, and blue palette, with a light counterpart following the device setting. */
@Composable
fun DoitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DoitDarkColors else DoitLightColors,
        typography = DoitTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
