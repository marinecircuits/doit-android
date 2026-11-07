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
    primary = Color(0xFFC4F281), onPrimary = Color(0xFF17230C),
    primaryContainer = Color(0xFF28371B), onPrimaryContainer = Color(0xFFD9F6B5),
    secondary = Color(0xFFBCCBB0), onSecondary = Color(0xFF25321D),
    secondaryContainer = Color(0xFF292F25), onSecondaryContainer = Color(0xFFDFE7D8),
    tertiary = Color(0xFF8FD5CE), onTertiary = Color(0xFF003732),
    tertiaryContainer = Color(0xFF164E48), onTertiaryContainer = Color(0xFFB9F1EA),
    background = Color(0xFF101215), onBackground = Color(0xFFF1F2ED),
    surface = Color(0xFF101215), onSurface = Color(0xFFF1F2ED),
    surfaceVariant = Color(0xFF272B30), onSurfaceVariant = Color(0xFFA9ADB5),
    surfaceContainerLowest = Color(0xFF0B0D10), surfaceContainerLow = Color(0xFF171A1F),
    surfaceContainer = Color(0xFF1D2026), surfaceContainerHigh = Color(0xFF252930),
    surfaceContainerHighest = Color(0xFF30343B), surfaceDim = Color(0xFF101215),
    surfaceBright = Color(0xFF383C43), surfaceTint = Color.Transparent,
    outline = Color(0xFF777F89), outlineVariant = Color(0xFF343941),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF4C2424), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE3E5DF), inverseOnSurface = Color(0xFF252822),
    inversePrimary = Color(0xFF416817),
)

internal val DoitLightColors = lightColorScheme(
    primary = Color(0xFF416817), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F6B5), onPrimaryContainer = Color(0xFF17230C),
    secondary = Color(0xFF526348), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EBD9), onSecondaryContainer = Color(0xFF25321D),
    background = Color(0xFFF6F7F3), onBackground = Color(0xFF191D17),
    surface = Color(0xFFF6F7F3), onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFE8ECE2), onSurfaceVariant = Color(0xFF565E51),
    surfaceContainerLow = Color(0xFFF0F2EB), surfaceContainer = Color(0xFFEAEEE4),
    surfaceContainerHigh = Color(0xFFE5E9DF), surfaceContainerHighest = Color(0xFFDFE4D8),
    outline = Color(0xFF747E6B), outlineVariant = Color(0xFFCED5C5),
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

/** A consistent graphite and lime palette, with a light counterpart following the device setting. */
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
