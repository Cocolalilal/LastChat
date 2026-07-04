package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.memory.MemoryPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §12.3 settings-migration polish: [normalizeMemorySettings] coerces the preset to a known name and
 * enforces the §11 dependent-toggle rule (curiosity web lookups require proactive curiosity).
 */
class MemorySettingsNormalizationTest {

    @Test
    fun unknownPreset_coercesToBalancedDefault() {
        val normalized = Settings(memory = MemorySettings(preset = "TURBO")).normalizeMemorySettings()
        assertEquals(MemoryPreset.BALANCED.name, normalized.memory.preset)
    }

    @Test
    fun lowercasePreset_canonicalisesToUpperCaseName() {
        val normalized = Settings(memory = MemorySettings(preset = "rich")).normalizeMemorySettings()
        assertEquals(MemoryPreset.RICH.name, normalized.memory.preset)
    }

    @Test
    fun curiosityWebLookups_forcedOffWhenProactiveCuriosityOff() {
        val normalized = Settings(
            memory = MemorySettings(proactiveCuriosity = false, curiosityWebLookups = true)
        ).normalizeMemorySettings()
        assertFalse(normalized.memory.curiosityWebLookups)
    }

    @Test
    fun curiosityWebLookups_keptWhenProactiveCuriosityOn() {
        val normalized = Settings(
            memory = MemorySettings(
                preset = "BALANCED",
                proactiveCuriosity = true,
                curiosityWebLookups = true,
            )
        ).normalizeMemorySettings()
        assertTrue(normalized.memory.curiosityWebLookups)
    }

    @Test
    fun alreadyNormalized_returnsSameInstance() {
        val settings = Settings(memory = MemorySettings(preset = "BALANCED"))
        assertSame(settings, settings.normalizeMemorySettings())
    }
}
