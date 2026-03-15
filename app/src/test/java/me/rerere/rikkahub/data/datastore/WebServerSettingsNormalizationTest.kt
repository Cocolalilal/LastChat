package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebServerSettingsNormalizationTest {

    @Test
    fun normalizeWebServerSettings_disablesJwtWithoutPassword_andPreservesBackgroundSetupFlag() {
        val normalized = Settings(
            webServerPort = 99,
            webServerJwtEnabled = true,
            webServerAccessPassword = "",
            webServerBackgroundSetupShown = true,
        ).normalizeWebServerSettings()

        assertEquals(1024, normalized.webServerPort)
        assertFalse(normalized.webServerJwtEnabled)
        assertTrue(normalized.webServerBackgroundSetupShown)
    }
}
