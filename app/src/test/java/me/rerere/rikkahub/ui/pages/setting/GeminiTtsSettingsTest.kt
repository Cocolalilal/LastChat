package me.rerere.rikkahub.ui.pages.setting

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.rerere.rikkahub.ui.pages.setting.components.GeminiTtsChoiceField
import me.rerere.tts.provider.GeminiTtsCatalog
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.withVoiceApplied
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GeminiTtsSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun addVoiceIncludesAllThirtyAndSelectedModelReachesEffectiveProvider() {
        val provider = TTSProviderSetting.Gemini()
        val voices = providerPresetVoices(provider)
        assertEquals(30, voices.size)
        assertEquals(30, voices.map { it.providerVoiceId }.distinct().size)
        val voice = voices.single { it.providerVoiceId == "Sulafat" }.copy(model = "gemini-2.5-pro-preview-tts")
        val effective = provider.withVoiceApplied(voice) as TTSProviderSetting.Gemini
        assertEquals("Sulafat", effective.voiceName)
        assertEquals("gemini-2.5-pro-preview-tts", effective.model)
        assertEquals("gemini-2.5-pro-preview-tts", GeminiTtsCatalog.modelOptions(voice.model!!).first())
    }

    @Test fun modelMenuSelectsProAndStillAllowsCustomModelIds() {
        var value by mutableStateOf("gemini-2.5-flash-preview-tts")
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            GeminiTtsChoiceField(value, GeminiTtsCatalog.modelOptions(value), "Model", "Model", onValueChange = { value = it })
        } } }
        compose.onNodeWithContentDescription("Model").performClick()
        compose.onNodeWithText("gemini-2.5-pro-preview-tts").performClick()
        compose.runOnIdle { assertEquals("gemini-2.5-pro-preview-tts", value) }
        compose.onNodeWithContentDescription("Model").performTextReplacement("my-proxy-speech-model")
        compose.runOnIdle { assertEquals("my-proxy-speech-model", value) }
    }

    @Test fun voiceMenuCanSelectAFormerlyMissingVoice() {
        var value by mutableStateOf("Kore")
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            GeminiTtsChoiceField(value, GeminiTtsCatalog.voices, "Voice", "Voice", onValueChange = { value = it })
        } } }
        compose.onNodeWithContentDescription("Voice").performClick()
        compose.onNodeWithText("Sulafat").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Sulafat", value) }
    }
}
