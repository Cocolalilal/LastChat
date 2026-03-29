package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.PresetTheme
import me.rerere.rikkahub.ui.theme.SeafoamMintThemeId

val SeafoamMintThemePreset by lazy {
    PresetTheme(
        id = SeafoamMintThemeId,
        name = {
            Text(stringResource(id = R.string.theme_name_seafoam_mint))
        },
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

// Generated from the Material You tonal-spot palette using #98D9C5 as the seed.
private val primaryLight = Color(0xFF0E6B58)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFA2F2DA)
private val onPrimaryContainerLight = Color(0xFF005142)
private val secondaryLight = Color(0xFF4B635B)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFCDE9DE)
private val onSecondaryContainerLight = Color(0xFF344C44)
private val tertiaryLight = Color(0xFF416277)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFC5E7FF)
private val onTertiaryContainerLight = Color(0xFF294A5E)
private val errorLight = Color(0xFFBA1A1A)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF93000A)
private val backgroundLight = Color(0xFFF5FBF7)
private val onBackgroundLight = Color(0xFF171D1B)
private val surfaceLight = Color(0xFFF5FBF7)
private val onSurfaceLight = Color(0xFF171D1B)
private val surfaceVariantLight = Color(0xFFDBE5E0)
private val onSurfaceVariantLight = Color(0xFF3F4945)
private val outlineLight = Color(0xFF6F7975)
private val outlineVariantLight = Color(0xFFBFC9C4)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF2B322F)
private val inverseOnSurfaceLight = Color(0xFFECF2EE)
private val inversePrimaryLight = Color(0xFF86D6BE)
private val surfaceDimLight = Color(0xFFD5DBD8)
private val surfaceBrightLight = Color(0xFFF5FBF7)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFEFF5F1)
private val surfaceContainerLight = Color(0xFFE9EFEB)
private val surfaceContainerHighLight = Color(0xFFE3EAE6)
private val surfaceContainerHighestLight = Color(0xFFDEE4E0)

private val primaryDark = Color(0xFF86D6BE)
private val onPrimaryDark = Color(0xFF00382D)
private val primaryContainerDark = Color(0xFF005142)
private val onPrimaryContainerDark = Color(0xFFA2F2DA)
private val secondaryDark = Color(0xFFB2CCC2)
private val onSecondaryDark = Color(0xFF1D352E)
private val secondaryContainerDark = Color(0xFF344C44)
private val onSecondaryContainerDark = Color(0xFFCDE9DE)
private val tertiaryDark = Color(0xFFA9CBE3)
private val onTertiaryDark = Color(0xFF0F3447)
private val tertiaryContainerDark = Color(0xFF294A5E)
private val onTertiaryContainerDark = Color(0xFFC5E7FF)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val backgroundDark = Color(0xFF0F1513)
private val onBackgroundDark = Color(0xFFDEE4E0)
private val surfaceDark = Color(0xFF0F1513)
private val onSurfaceDark = Color(0xFFDEE4E0)
private val surfaceVariantDark = Color(0xFF3F4945)
private val onSurfaceVariantDark = Color(0xFFBFC9C4)
private val outlineDark = Color(0xFF89938E)
private val outlineVariantDark = Color(0xFF3F4945)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFDEE4E0)
private val inverseOnSurfaceDark = Color(0xFF2B322F)
private val inversePrimaryDark = Color(0xFF0E6B58)
private val surfaceDimDark = Color(0xFF0F1513)
private val surfaceBrightDark = Color(0xFF343B38)
private val surfaceContainerLowestDark = Color(0xFF090F0D)
private val surfaceContainerLowDark = Color(0xFF171D1B)
private val surfaceContainerDark = Color(0xFF1B211F)
private val surfaceContainerHighDark = Color(0xFF252B29)
private val surfaceContainerHighestDark = Color(0xFF303634)

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)
