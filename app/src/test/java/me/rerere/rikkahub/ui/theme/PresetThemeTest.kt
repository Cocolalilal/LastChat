package me.rerere.rikkahub.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PresetThemeTest {
    @Test
    fun legacyNightskyBlueThemeIdMapsToSeafoamMint() {
        assertEquals(SeafoamMintThemeId, normalizePresetThemeId("nightsky_blue"))
        assertEquals(SeafoamMintThemeId, findPresetTheme("nightsky_blue").id)
    }
}
