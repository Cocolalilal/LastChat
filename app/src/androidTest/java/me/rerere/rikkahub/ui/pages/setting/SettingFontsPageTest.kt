package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import me.rerere.rikkahub.data.datastore.FontConfig
import me.rerere.rikkahub.data.datastore.FontSettings
import me.rerere.rikkahub.data.datastore.FontSource
import me.rerere.rikkahub.utils.FontFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SettingFontsPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneSystemFontToggle_hidesEditorsAndPreservesSelections() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fontManager = FontFileManager(context)
        val appFont = FontConfig(
            fontSource = FontSource.Custom,
            customFontPath = "app.ttf",
            customFontName = "App Font",
            weight = 620f
        )
        val codeFont = FontConfig(
            fontSource = FontSource.Custom,
            customFontPath = "code.ttf",
            customFontName = "Code Font",
            weight = 500f
        )

        var currentFontSettings by mutableStateOf(
            FontSettings(
                headerFont = appFont,
                contentFont = appFont,
                codeFont = codeFont
            )
        )

        composeRule.setContent {
            MaterialTheme {
                FontSettingsContent(
                    fontSettings = currentFontSettings,
                    fontManager = fontManager,
                    onFontSettingsChange = { updatedSettings, _ ->
                        currentFontSettings = updatedSettings
                    }
                )
            }
        }

        composeRule.onNodeWithTag(PhoneSystemFontToggleTag).assertIsOff()
        composeRule.onNodeWithText("App Font").assertExists()
        composeRule.onNodeWithText("Code Blocks").assertExists()
        composeRule.onNodeWithText("Preview").assertExists()

        composeRule.onNodeWithTag(PhoneSystemFontToggleTag).performClick()

        composeRule.onNodeWithTag(PhoneSystemFontToggleTag).assertIsOn()
        composeRule.onNodeWithText("App Font").assertDoesNotExist()
        composeRule.onNodeWithText("Code Blocks").assertDoesNotExist()
        composeRule.onNodeWithText("Preview").assertExists()

        composeRule.onNodeWithTag(PhoneSystemFontToggleTag).performClick()

        composeRule.onNodeWithTag(PhoneSystemFontToggleTag).assertIsOff()
        composeRule.onNodeWithText("App Font").assertExists()
        composeRule.onNodeWithText("Code Blocks").assertExists()

        composeRule.runOnIdle {
            assertFalse(currentFontSettings.usePhoneSystemFont)
            assertEquals(appFont, currentFontSettings.headerFont)
            assertEquals(appFont, currentFontSettings.contentFont)
            assertEquals(codeFont, currentFontSettings.codeFont)
        }
    }
}
