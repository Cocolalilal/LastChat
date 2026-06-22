package me.rerere.tts.provider

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class TTSProviderSettingTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testCartesiaSerialization() {
        val cartesia = TTSProviderSetting.Cartesia(
            id = Uuid.random(),
            apiKey = "cartesia-key"
        )
        val serialized = json.encodeToString<TTSProviderSetting>(cartesia)
        
        val deserialized = json.decodeFromString<TTSProviderSetting>(serialized)
        assertTrue(deserialized is TTSProviderSetting.Cartesia)
        assertEquals((deserialized as TTSProviderSetting.Cartesia).apiKey, "cartesia-key")
    }

    @Test
    fun testFishAudioSerialization() {
        val fishAudio = TTSProviderSetting.FishAudio(
            id = Uuid.random(),
            apiKey = "fish-audio-key"
        )
        val serialized = json.encodeToString<TTSProviderSetting>(fishAudio)
        
        val deserialized = json.decodeFromString<TTSProviderSetting>(serialized)
        assertTrue(deserialized is TTSProviderSetting.FishAudio)
        assertEquals((deserialized as TTSProviderSetting.FishAudio).apiKey, "fish-audio-key")
    }

    @Test
    fun testPlayHTSerialization() {
        val playHT = TTSProviderSetting.PlayHT(
            id = Uuid.random(),
            apiKey = "playht-key",
            userId = "playht-user"
        )
        val serialized = json.encodeToString<TTSProviderSetting>(playHT)
        
        val deserialized = json.decodeFromString<TTSProviderSetting>(serialized)
        assertTrue(deserialized is TTSProviderSetting.PlayHT)
        assertEquals((deserialized as TTSProviderSetting.PlayHT).apiKey, "playht-key")
        assertEquals((deserialized as TTSProviderSetting.PlayHT).userId, "playht-user")
    }

    @Test
    fun testCopyProviderCustomIcon() {
        val cartesia = TTSProviderSetting.Cartesia(
            id = Uuid.random(),
            apiKey = "cartesia-key"
        )
        val updated = cartesia.copyProvider(customIconUri = "custom-icon")
        assertEquals("custom-icon", updated.customIconUri)
        assertTrue(updated is TTSProviderSetting.Cartesia)
        assertEquals("cartesia-key", (updated as TTSProviderSetting.Cartesia).apiKey)
    }
}
