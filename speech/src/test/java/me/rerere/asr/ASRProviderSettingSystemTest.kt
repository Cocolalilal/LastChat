package me.rerere.asr

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ASRProviderSettingSystemTest {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @Test
    fun system_defaults_are_expected() {
        val setting = ASRProviderSetting.SystemSTT()

        assertEquals("System STT", setting.name)
        assertNull(setting.servicePackageName)
        assertNull(setting.serviceClassName)
        assertEquals("", setting.language)
        assertEquals(true, setting.preferOffline)
        assertEquals(true, setting.partialResults)
    }

    @Test
    fun system_is_registered_in_provider_types() {
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.SystemSTT::class))
    }

    @Test
    fun system_round_trips_serialization() {
        val original: ASRProviderSetting = ASRProviderSetting.SystemSTT(
            id = Uuid.parse("11111111-2222-3333-4444-555555555555"),
            name = "Phone STT",
            servicePackageName = "com.example.speech",
            serviceClassName = "com.example.speech.Service",
            language = "en-US",
            preferOffline = false,
            partialResults = false,
        )

        val decoded = json.decodeFromString<ASRProviderSetting>(json.encodeToString(original))

        assertTrue(decoded is ASRProviderSetting.SystemSTT)
        val system = decoded as ASRProviderSetting.SystemSTT
        assertEquals(original.id, system.id)
        assertEquals("Phone STT", system.name)
        assertEquals("com.example.speech", system.servicePackageName)
        assertEquals("com.example.speech.Service", system.serviceClassName)
        assertEquals("en-US", system.language)
        assertEquals(false, system.preferOffline)
        assertEquals(false, system.partialResults)
    }
}
