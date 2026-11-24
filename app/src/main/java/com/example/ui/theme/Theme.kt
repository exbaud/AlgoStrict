package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CobaltLight,
    onPrimary = DeepNavy,
    primaryContainer = Color(0xFF1D3566),
    onPrimaryContainer = ChalkPrimary,
    secondary = CyanAccent,
    onSecondary = DeepNavy,
    secondaryContainer = Color(0xFF13384A),
    onSecondaryContainer = ChalkPrimary,
    tertiary = VioletKeyword,
    background = DeepNavy,
    onBackground = ChalkPrimary,
    surface = SlateSurfaceDark,
    onSurface = ChalkPrimary,
    surfaceVariant = SlateCardDark,
    onSurfaceVariant = ChalkSecondary,
    outline = SlateBorderDark,
    error = CrimsonError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFF7C3AED),
    background = IvoryBackground,
    onBackground = InkPrimary,
    surface = WhiteSurface,
    onSurface = InkPrimary,
    surfaceVariant = SoftSlateLight,
    onSurfaceVariant = InkSecondary,
    outline = SlateBorderLight,
    error = CrimsonError,
    onError = Color.White
)

@Composable
fun AlgoStrictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
