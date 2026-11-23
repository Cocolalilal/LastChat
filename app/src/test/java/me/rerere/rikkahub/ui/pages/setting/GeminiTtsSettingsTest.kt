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
        compose.onNodeWithText("gemini-2.5-pro-preview-tts").performScrollTo().performClick()
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
    @Test fun addVoiceUsesLiveApiNamesAndIdsInsteadOfTheFallbackList() = kotlinx.coroutines.runBlocking {
        val http = object : me.rerere.common.platform.PlatformHttpClient {
            override suspend fun execute(request: me.rerere.common.platform.PlatformHttpRequest) =
                me.rerere.common.platform.PlatformHttpResponse(200, body =
                    """{"voices":[{"id":"NewApiVoice","display_name":"New voice from Google","type":"prebuilt"}]}""".encodeToByteArray())
            override fun streamEvents(request: me.rerere.common.platform.PlatformHttpRequest):
                kotlinx.coroutines.flow.Flow<me.rerere.common.platform.PlatformServerEvent> = kotlinx.coroutines.flow.emptyFlow()
        }
        val voices = fetchProviderVoices(org.robolectric.RuntimeEnvironment.getApplication(), http,
            TTSProviderSetting.Gemini(apiKey = "test-key"))
        assertEquals(listOf("NewApiVoice"), voices.map { it.providerVoiceId })
        assertEquals("New voice from Google", voices.single().name)
        assertFalse(voices.any { it.providerVoiceId == "Kore" })
    }

}
