package me.rerere.asr

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ASRProviderSettingOpenAICompatibleTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun openai_compatible_defaults_are_expected() {
        val setting = ASRProviderSetting.OpenAICompatible()

        assertEquals("OpenAI-compatible STT", setting.name)
        assertEquals("https://api.openai.com/v1", setting.baseUrl)
        assertEquals("whisper-1", setting.model)
        assertEquals("", setting.language)
        assertEquals(0f, setting.temperature)
        assertEquals("text", setting.responseFormat)
        assertEquals(16000, setting.sampleRate)
        assertEquals(30, setting.segmentDurationSec)
        assertTrue(setting.enabled)
    }

    @Test
    fun openai_compatible_is_registered_in_provider_types() {
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.OpenAICompatible::class))
    }

    @Test
    fun openai_compatible_serialization_round_trip() {
        val original = ASRProviderSetting.OpenAICompatible(
            apiKey = "sk-test",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "whisper-large-v3-turbo",
            language = "en",
            temperature = 0.2f,
            responseFormat = "json",
            sampleRate = 48000,
            segmentDurationSec = 15,
            customIconUri = "icons/groq.svg",
        )
        val encoded = json.encodeToString(ASRProviderSetting.serializer(), original)
        val decoded = json.decodeFromString(ASRProviderSetting.serializer(), encoded)

        assertTrue(decoded is ASRProviderSetting.OpenAICompatible)
        val compat = decoded as ASRProviderSetting.OpenAICompatible
        assertEquals(original.id, compat.id)
        assertEquals("sk-test", compat.apiKey)
        assertEquals("https://api.groq.com/openai/v1", compat.baseUrl)
        assertEquals("whisper-large-v3-turbo", compat.model)
        assertEquals("en", compat.language)
        assertEquals(0.2f, compat.temperature)
        assertEquals("json", compat.responseFormat)
        assertEquals(48000, compat.sampleRate)
        assertEquals(15, compat.segmentDurationSec)
        assertEquals("icons/groq.svg", compat.customIconUri)
    }

    @Test
    fun openai_compatible_copy_provider_preserves_extra_fields() {
        val original = ASRProviderSetting.OpenAICompatible(
            apiKey = "sk-test",
            baseUrl = "https://example.com/v1",
            model = "whisper-1",
            customIconUri = "icons/custom.svg",
        )
        val copied = original.copyProvider(id = original.id, name = "renamed")

        assertTrue(copied is ASRProviderSetting.OpenAICompatible)
        val compat = copied as ASRProviderSetting.OpenAICompatible
        assertEquals("renamed", compat.name)
        assertEquals("sk-test", compat.apiKey)
        assertEquals("https://example.com/v1", compat.baseUrl)
        assertEquals("whisper-1", compat.model)
        assertEquals("icons/custom.svg", compat.customIconUri)
    }

    @Test
    fun openrouter_is_detected_via_base_url() {
        val openrouter = ASRProviderSetting.OpenAICompatible(
            baseUrl = "https://openrouter.ai/api/v1",
        )
        assertTrue(openrouter.isOpenRouter)

        val groq = ASRProviderSetting.OpenAICompatible(
            baseUrl = "https://api.groq.com/openai/v1",
        )
        assertFalse(groq.isOpenRouter)
    }

    @Test
    fun openai_compatible_serial_name_is_openai_compatible() {
        val original = ASRProviderSetting.OpenAICompatible()
        val encoded = json.encodeToString(ASRProviderSetting.serializer(), original)
        assertTrue(encoded.contains("\"openai_compatible\""))
    }
}
