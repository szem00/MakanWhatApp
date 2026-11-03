package com.budgetapp.voiceexpense.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The Kopi coin look: espresso browns and a gold coin, on warm paper by day and dark roast by night.
// It replaces the phone's wallpaper colours, so the app looks the same on every phone.

internal val KopiLight = lightColorScheme(
    primary = Color(0xFF6B4226),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E3C8),
    onPrimaryContainer = Color(0xFF3B2412),
    inversePrimary = Color(0xFFE7B24A),
    secondary = Color(0xFF75594A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E3C8),
    onSecondaryContainer = Color(0xFF3B2412),
    tertiary = Color(0xFF8A5A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFADFA6),
    onTertiaryContainer = Color(0xFF2C1800),
    background = Color(0xFFFBF4E8),
    onBackground = Color(0xFF2A1A10),
    surface = Color(0xFFFBF4E8),
    onSurface = Color(0xFF2A1A10),
    surfaceVariant = Color(0xFFEFE2D0),
    onSurfaceVariant = Color(0xFF6E5846),
    surfaceTint = Color(0xFF6B4226),
    inverseSurface = Color(0xFF3A2A1F),
    inverseOnSurface = Color(0xFFFBF4E8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8D7663),
    outlineVariant = Color(0xFFDED5C9),
    surfaceBright = Color(0xFFFBF4E8),
    surfaceDim = Color(0xFFE3D8C8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7EEDF),
    surfaceContainer = Color(0xFFF3E8D7),
    surfaceContainerHigh = Color(0xFFEEE2CF),
    surfaceContainerHighest = Color(0xFFE9DCC8),
)

internal val KopiDark = darkColorScheme(
    primary = Color(0xFFE7B24A),
    onPrimary = Color(0xFF2A1A10),
    primaryContainer = Color(0xFF4A3220),
    onPrimaryContainer = Color(0xFFF7E3BD),
    inversePrimary = Color(0xFF6B4226),
    secondary = Color(0xFFDCC2A8),
    onSecondary = Color(0xFF3B2412),
    secondaryContainer = Color(0xFF4A3220),
    onSecondaryContainer = Color(0xFFF7E3BD),
    tertiary = Color(0xFFF0C46C),
    onTertiary = Color(0xFF2A1A10),
    tertiaryContainer = Color(0xFF6A4710),
    onTertiaryContainer = Color(0xFFFADFA6),
    background = Color(0xFF1A110B),
    onBackground = Color(0xFFF3E6D2),
    surface = Color(0xFF1A110B),
    onSurface = Color(0xFFF3E6D2),
    surfaceVariant = Color(0xFF3A2C21),
    onSurfaceVariant = Color(0xFFC4AE95),
    surfaceTint = Color(0xFFE7B24A),
    inverseSurface = Color(0xFFF3E6D2),
    inverseOnSurface = Color(0xFF2A1A10),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF9C8671),
    outlineVariant = Color(0xFF41372F),
    surfaceBright = Color(0xFF42342A),
    surfaceDim = Color(0xFF1A110B),
    surfaceContainerLowest = Color(0xFF140C07),
    surfaceContainerLow = Color(0xFF22170F),
    surfaceContainer = Color(0xFF281C13),
    surfaceContainerHigh = Color(0xFF332519),
    surfaceContainerHighest = Color(0xFF3E2F22),
)

/** Colours the Material scheme has no slot for. Going over budget uses the scheme's error colour. */
@Immutable
data class BrandColors(
    /** The question mark in the app's name. */
    val coin: Color,
    val onTrack: Color,
    val nearlyUsed: Color,
)

internal val LightBrandColors = BrandColors(
    coin = Color(0xFF8A5A12),
    onTrack = Color(0xFF2E7D32),
    nearlyUsed = Color(0xFFB45309),
)

internal val DarkBrandColors = BrandColors(
    coin = Color(0xFFE7B24A),
    onTrack = Color(0xFF81C784),
    nearlyUsed = Color(0xFFF0A860),
)

val LocalBrandColors = staticCompositionLocalOf { LightBrandColors }
