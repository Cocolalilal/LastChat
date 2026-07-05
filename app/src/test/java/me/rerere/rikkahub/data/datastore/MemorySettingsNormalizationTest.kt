package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.memory.MemoryPreset
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §11 settings normalization, now per-assistant: [normalizeAssistantMemory] coerces each assistant's
 * memory preset to a known name and enforces the dependent-toggle rule (curiosity web lookups require
 * proactive curiosity).
 */
class MemorySettingsNormalizationTest {

    private fun settingsWith(assistant: Assistant) = Settings(assistants = listOf(assistant))

    @Test
    fun unknownPreset_coercesToBalancedDefault() {
        val normalized = settingsWith(Assistant(memoryPreset = "TURBO")).normalizeAssistantMemory()
        assertEquals(MemoryPreset.BALANCED.name, normalized.assistants.first().memoryPreset)
    }

    @Test
    fun lowercasePreset_canonicalisesToUpperCaseName() {
        val normalized = settingsWith(Assistant(memoryPreset = "rich")).normalizeAssistantMemory()
        assertEquals(MemoryPreset.RICH.name, normalized.assistants.first().memoryPreset)
    }

    @Test
    fun curiosityWebLookups_forcedOffWhenProactiveCuriosityOff() {
        val normalized = settingsWith(
            Assistant(memoryProactiveCuriosity = false, memoryCuriosityWebLookups = true)
        ).normalizeAssistantMemory()
        assertFalse(normalized.assistants.first().memoryCuriosityWebLookups)
    }

    @Test
    fun curiosityWebLookups_keptWhenProactiveCuriosityOn() {
        val normalized = settingsWith(
            Assistant(
                memoryPreset = "BALANCED",
                memoryProactiveCuriosity = true,
                memoryCuriosityWebLookups = true,
            )
        ).normalizeAssistantMemory()
        assertTrue(normalized.assistants.first().memoryCuriosityWebLookups)
    }

    @Test
    fun alreadyNormalized_returnsSameInstance() {
        val settings = settingsWith(Assistant(memoryPreset = "BALANCED"))
        assertSame(settings, settings.normalizeAssistantMemory())
    }
}
