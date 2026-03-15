package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontSettingsNormalizationTest {

    @Test
    fun normalize_collapsesContentToHeaderAndPreservesOtherFields() {
        val headerFont = FontConfig(
            fontSource = FontSource.Custom,
            customFontPath = "header.ttf",
            customFontName = "Header Font",
            weight = 650f,
            width = 110f
        )
        val contentFont = FontConfig(
            fontSource = FontSource.System,
            weight = 300f,
            width = 80f,
            roundness = 0f
        )
        val codeFont = FontConfig(
            fontSource = FontSource.Custom,
            customFontPath = "code.ttf",
            customFontName = "Code Font",
            weight = 500f
        )

        val normalized = FontSettings(
            useSameFontForHeadersAndContent = false,
            usePhoneSystemFont = true,
            headerFont = headerFont,
            contentFont = contentFont,
            codeFont = codeFont
        ).normalize()

        assertTrue(normalized.useSameFontForHeadersAndContent)
        assertTrue(normalized.usePhoneSystemFont)
        assertEquals(headerFont, normalized.headerFont)
        assertEquals(headerFont, normalized.contentFont)
        assertEquals(codeFont, normalized.codeFont)
    }

    @Test
    fun normalizeFontSettings_isStableAcrossDisplaySettingPersistenceRoundTrip() {
        val divergentFontSettings = FontSettings(
            useSameFontForHeadersAndContent = false,
            usePhoneSystemFont = false,
            headerFont = FontConfig(
                fontSource = FontSource.Custom,
                customFontPath = "app.ttf",
                customFontName = "App Font",
                weight = 700f
            ),
            contentFont = FontConfig.DEFAULT_NORMAL,
            codeFont = FontConfig.DEFAULT_CODE.copy(weight = 450f)
        )

        val normalizedSettings = Settings(
            displaySetting = DisplaySetting(fontSettings = divergentFontSettings)
        ).normalizeFontSettings()

        val persistedDisplaySetting = JsonInstant.decodeFromString<DisplaySetting>(
            JsonInstant.encodeToString(normalizedSettings.displaySetting)
        ).normalizeFontSettings()

        assertTrue(normalizedSettings.displaySetting.fontSettings.useSameFontForHeadersAndContent)
        assertFalse(normalizedSettings.displaySetting.fontSettings.usePhoneSystemFont)
        assertEquals(
            normalizedSettings.displaySetting.fontSettings,
            persistedDisplaySetting.fontSettings
        )
        assertEquals(
            normalizedSettings.displaySetting.fontSettings.headerFont,
            persistedDisplaySetting.fontSettings.contentFont
        )
    }
}
