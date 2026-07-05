package me.rerere.rikkahub.data.memory

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.MemorySettings
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [MemoryModels] resolution has NO fallback chain (an unset model pauses that work) and drives the
 * "set up memory models" health notice; the global preset is the max among memory-enabled assistants.
 */
class MemoryModelsTest {

    private val modelId = Uuid.random()

    private fun settingsWithModel(memory: MemorySettings, assistants: List<Assistant> = emptyList()): Settings {
        val provider = ProviderSetting.OpenAI(
            id = Uuid.random(),
            name = "P",
            models = listOf(Model(id = modelId, displayName = "m")),
        )
        return Settings(
            providers = listOf(provider),
            summarizerModelId = modelId, // present, but must NOT be used as a fallback
            chatModelId = modelId,
            memory = memory,
            assistants = assistants,
        )
    }

    @Test
    fun unsetParserResolvesNullDespiteSummarizerAndChatConfigured() {
        val s = settingsWithModel(MemorySettings(parserModelId = null))
        assertNull(MemoryModels.resolveParser(s))
    }

    @Test
    fun unsetConsolidationResolvesNull() {
        val s = settingsWithModel(MemorySettings(consolidationModelId = null))
        assertNull(MemoryModels.resolveConsolidation(s))
    }

    @Test
    fun setParserResolvesToConfiguredModel() {
        val s = settingsWithModel(MemorySettings(parserModelId = modelId))
        val resolved = MemoryModels.resolveParser(s)
        assertNotNull(resolved)
        assertEquals(modelId, resolved!!.second.id)
    }

    @Test
    fun healthFlagsMissingModels() {
        val s = settingsWithModel(MemorySettings(parserModelId = modelId, consolidationModelId = null))
        val health = MemoryModels.health(s)
        assertFalse(health.parserMissing)
        assertTrue(health.consolidationMissing)
        assertTrue(health.anyModelMissing)
    }

    @Test
    fun globalPresetIsMaxOfEnabledAssistants() {
        val s = settingsWithModel(
            MemorySettings(),
            assistants = listOf(
                Assistant(enableMemory = true, memoryPreset = "ECO"),
                Assistant(enableMemory = true, memoryPreset = "RICH"),
                Assistant(enableMemory = false, memoryPreset = "ECO"), // disabled, ignored
            ),
        )
        assertEquals(MemoryPreset.RICH, MemoryModels.globalPreset(s))
    }

    @Test
    fun globalPresetFloorsToBalancedWhenNoneEnabled() {
        val s = settingsWithModel(MemorySettings(), assistants = listOf(Assistant(enableMemory = false, memoryPreset = "ECO")))
        assertEquals(MemoryPreset.BALANCED, MemoryModels.globalPreset(s))
    }

    @Test
    fun presetForCharacterUsesItsOwnPreset() {
        val a = Assistant(enableMemory = true, memoryPreset = "ECO")
        val s = settingsWithModel(MemorySettings(), assistants = listOf(a))
        assertEquals(MemoryPreset.ECO, MemoryModels.presetFor(s, a.id.toString()))
    }
}
