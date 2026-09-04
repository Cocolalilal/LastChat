package me.rerere.rikkahub.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PresetThemeTest {
    @Test
    fun legacyNightskyBlueThemeIdMapsToSeafoamMint() {
        assertEquals(SeafoamMintThemeId, normalizePresetThemeId("nightsky_blue"))
        assertEquals(SeafoamMintThemeId, findPresetTheme("nightsky_blue").id)
    }

    @Test
    fun iosThemeIsFirstPreset() {
        assertEquals(IosThemeId, PresetThemes.first().id)
        assertEquals(IosThemeId, findPresetTheme("ios").id)
    }
}
