package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.PresetTheme
import me.rerere.rikkahub.ui.theme.SeafoamMintThemeId
import me.rerere.rikkahub.ui.theme.seafoamMintColorScheme

val SeafoamMintThemePreset by lazy {
    PresetTheme(
        id = SeafoamMintThemeId,
        name = { Text(stringResource(id = R.string.theme_name_seafoam_mint)) },
        standardLight = seafoamMintColorScheme(dark = false),
        standardDark = seafoamMintColorScheme(dark = true),
    )
}
