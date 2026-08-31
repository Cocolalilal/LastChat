package me.rerere.lastchat.ios

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosTtsPreferencesTest {
    @Test
    fun everyCloudProviderMapsToItsPortableSetting() {
        IosTtsProviderType.entries.forEach { type ->
            val setting = type.defaultPreferences().toProviderSetting("secret")
            when (type) {
                IosTtsProviderType.OPENAI -> assertIs<TTSProviderSetting.OpenAI>(setting)
                IosTtsProviderType.GEMINI -> assertIs<TTSProviderSetting.Gemini>(setting)
                IosTtsProviderType.MINIMAX -> assertIs<TTSProviderSetting.MiniMax>(setting)
                IosTtsProviderType.ELEVENLABS -> assertIs<TTSProviderSetting.ElevenLabs>(setting)
                IosTtsProviderType.QWEN -> assertIs<TTSProviderSetting.Qwen>(setting)
                IosTtsProviderType.FISH_AUDIO -> assertIs<TTSProviderSetting.FishAudio>(setting)
                IosTtsProviderType.CARTESIA -> assertIs<TTSProviderSetting.Cartesia>(setting)
                IosTtsProviderType.PLAY_HT -> assertIs<TTSProviderSetting.PlayHT>(setting)
            }
            assertTrue(setting.name.isNotBlank())
        }
    }

    @Test
    fun persistedPreferencesContainNoCredentialField() {
        val preferences = IosTtsProviderType.CARTESIA.defaultPreferences().copy(
            enabled = true,
            speed = 1.25f,
        )
        val encoded = Json.encodeToString(preferences)

        assertFalse(encoded.contains("secret", ignoreCase = true))
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertEquals(1.25f, Json.decodeFromString<IosTtsPreferences>(encoded).speed)
    }

    @Test
    fun providerDefaultsAreImmediatelyEditable() {
        IosTtsProviderType.entries.forEach { type ->
            val defaults = type.defaultPreferences()
            assertEquals(type, defaults.type)
            assertTrue(defaults.baseUrl.isNotBlank())
            assertTrue(defaults.model.isNotBlank())
            assertTrue(defaults.voice.isNotBlank())
        }
    }
}
