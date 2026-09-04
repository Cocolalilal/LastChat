package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.IosThemeId
import me.rerere.rikkahub.ui.theme.PresetTheme

val IosThemePreset by lazy {
    PresetTheme(
        id = IosThemeId,
        name = {
            Text(stringResource(id = R.string.theme_name_ios))
        },
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

// iOS Human Interface Guidelines System Colors (Light)
private val primaryLight = Color(0xFF007AFF)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFE5F0FF)
private val onPrimaryContainerLight = Color(0xFF0040A8)
private val secondaryLight = Color(0xFF5856D6)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFE5E5EA)
private val onSecondaryContainerLight = Color(0xFF1C1C1E)
private val tertiaryLight = Color(0xFF34C759)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFDDF7E4)
private val onTertiaryContainerLight = Color(0xFF0E4A1E)
private val errorLight = Color(0xFFFF3B30)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFECEB)
private val onErrorContainerLight = Color(0xFFD70015)
private val backgroundLight = Color(0xFFF2F2F7)
private val onBackgroundLight = Color(0xFF000000)
private val surfaceLight = Color(0xFFFFFFFF)
private val onSurfaceLight = Color(0xFF000000)
private val surfaceVariantLight = Color(0xFFE5E5EA)
private val onSurfaceVariantLight = Color(0xFF3C3C43)
private val outlineLight = Color(0xFFC6C6C8)
private val outlineVariantLight = Color(0xFFD1D1D6)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF1C1C1E)
private val inverseOnSurfaceLight = Color(0xFFF2F2F7)
private val inversePrimaryLight = Color(0xFF0A84FF)
private val surfaceDimLight = Color(0xFFE5E5EA)
private val surfaceBrightLight = Color(0xFFFFFFFF)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFF9F9FB)
private val surfaceContainerLight = Color(0xFFF2F2F7)
private val surfaceContainerHighLight = Color(0xFFE5E5EA)
private val surfaceContainerHighestLight = Color(0xFFD1D1D6)

// iOS Human Interface Guidelines System Colors (Dark)
private val primaryDark = Color(0xFF0A84FF)
private val onPrimaryDark = Color(0xFFFFFFFF)
private val primaryContainerDark = Color(0xFF004085)
private val onPrimaryContainerDark = Color(0xFFD0E4FF)
private val secondaryDark = Color(0xFF5E5CE6)
private val onSecondaryDark = Color(0xFFFFFFFF)
private val secondaryContainerDark = Color(0xFF2C2C2E)
private val onSecondaryContainerDark = Color(0xFFE5E5EA)
private val tertiaryDark = Color(0xFF30D158)
private val onTertiaryDark = Color(0xFF000000)
private val tertiaryContainerDark = Color(0xFF0E4A1E)
private val onTertiaryContainerDark = Color(0xFFB6F3C7)
private val errorDark = Color(0xFFFF453A)
private val onErrorDark = Color(0xFF000000)
private val errorContainerDark = Color(0xFF680009)
private val onErrorContainerDark = Color(0xFFFFD2CE)
private val backgroundDark = Color(0xFF000000)
private val onBackgroundDark = Color(0xFFFFFFFF)
private val surfaceDark = Color(0xFF1C1C1E)
private val onSurfaceDark = Color(0xFFFFFFFF)
private val surfaceVariantDark = Color(0xFF2C2C2E)
private val onSurfaceVariantDark = Color(0xFF8E8E93)
private val outlineDark = Color(0xFF38383A)
private val outlineVariantDark = Color(0xFF2C2C2E)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFF2F2F7)
private val inverseOnSurfaceDark = Color(0xFF1C1C1E)
private val inversePrimaryDark = Color(0xFF007AFF)
private val surfaceDimDark = Color(0xFF000000)
private val surfaceBrightDark = Color(0xFF2C2C2E)
private val surfaceContainerLowestDark = Color(0xFF000000)
private val surfaceContainerLowDark = Color(0xFF121214)
private val surfaceContainerDark = Color(0xFF1C1C1E)
private val surfaceContainerHighDark = Color(0xFF2C2C2E)
private val surfaceContainerHighestDark = Color(0xFF3A3A3C)

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
