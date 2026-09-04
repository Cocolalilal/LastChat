package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import me.rerere.rikkahub.ui.theme.presets.AutumnThemePreset
import me.rerere.rikkahub.ui.theme.presets.BlackThemePreset
import me.rerere.rikkahub.ui.theme.presets.IosThemePreset
import me.rerere.rikkahub.ui.theme.presets.OceanThemePreset
import me.rerere.rikkahub.ui.theme.presets.SakuraThemePreset
import me.rerere.rikkahub.ui.theme.presets.SeafoamMintThemePreset
import me.rerere.rikkahub.ui.theme.presets.SpringThemePreset

const val IosThemeId = "ios"
const val SeafoamMintThemeId = "seafoam_mint"

private const val LegacyNightskyBlueThemeId = "nightsky_blue"

data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean): ColorScheme {
        return if (dark) standardDark else standardLight
    }
}

val PresetThemes by lazy {
    listOf(
        IosThemePreset,
        SeafoamMintThemePreset,
        OceanThemePreset,
        SakuraThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
    )
}

fun normalizePresetThemeId(id: String): String {
    return when (id) {
        LegacyNightskyBlueThemeId -> SeafoamMintThemeId
        else -> id
    }
}

fun findPresetTheme(id: String): PresetTheme {
    val normalizedId = normalizePresetThemeId(id)
    return PresetThemes.find { it.id == normalizedId } ?: IosThemePreset
}
