package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

const val SharedSeafoamMintThemeId = "seafoam_mint"

fun seafoamMintColorScheme(dark: Boolean): ColorScheme = if (dark) SeafoamDark else SeafoamLight

private val SeafoamLight = lightColorScheme(
    primary = Color(0xFF0E6B58), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA2F2DA), onPrimaryContainer = Color(0xFF005142),
    secondary = Color(0xFF4B635B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE9DE), onSecondaryContainer = Color(0xFF344C44),
    tertiary = Color(0xFF416277), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5E7FF), onTertiaryContainer = Color(0xFF294A5E),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF5FBF7), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF7), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E0), onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C4),
    scrim = Color(0xFF000000), inverseSurface = Color(0xFF2B322F),
    inverseOnSurface = Color(0xFFECF2EE), inversePrimary = Color(0xFF86D6BE),
    surfaceDim = Color(0xFFD5DBD8), surfaceBright = Color(0xFFF5FBF7),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB), surfaceContainerHigh = Color(0xFFE3EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E0),
)

private val SeafoamDark = darkColorScheme(
    primary = Color(0xFF86D6BE), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142), onPrimaryContainer = Color(0xFFA2F2DA),
    secondary = Color(0xFFB2CCC2), onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF344C44), onSecondaryContainer = Color(0xFFCDE9DE),
    tertiary = Color(0xFFA9CBE3), onTertiary = Color(0xFF0F3447),
    tertiaryContainer = Color(0xFF294A5E), onTertiaryContainer = Color(0xFFC5E7FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1513), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1513), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938E), outlineVariant = Color(0xFF3F4945),
    scrim = Color(0xFF000000), inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2B322F), inversePrimary = Color(0xFF0E6B58),
    surfaceDim = Color(0xFF0F1513), surfaceBright = Color(0xFF343B38),
    surfaceContainerLowest = Color(0xFF090F0D), surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F), surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
)
