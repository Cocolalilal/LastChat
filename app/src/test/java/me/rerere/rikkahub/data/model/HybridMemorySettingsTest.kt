package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UsedMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMemorySettingsTest {
    @Test
    fun legacyAssistantDefaultsToEntryBasedAndAliasesRemainEffective() {
        val assistant = Json { ignoreUnknownKeys = true }.decodeFromString<Assistant>(
            """{"name":"Legacy","enableMemory":true,"enableRecentChatsReference":true,"enableMemoryConsolidation":true,"enableMemorySearchTool":true}"""
        )

        assertEquals(MemorySystemType.ENTRY_BASED, assistant.memorySystem)
        assertTrue(assistant.effectiveAdvancedEntryMemoryEnabled())
        assertTrue(assistant.effectiveRecentContinuityEnabled())
        assertTrue(assistant.effectiveMemorySearchToolEnabled())
        assertTrue(assistant.effectiveRagMemoryEnabled())
    }

    @Test
    fun advancedForcesEffectiveControlsWithoutOverwritingStoredPreferences() {
        val assistant = Assistant(
            enableMemory = true,
            entryAdvancedMemoryEnabled = true,
            entryRecentContinuityEnabled = false,
            entryMemorySearchToolEnabled = false,
            useRagMemoryRetrieval = false,
        )

        assertTrue(assistant.effectiveRecentContinuityEnabled())
        assertTrue(assistant.effectiveMemorySearchToolEnabled())
        assertTrue(assistant.effectiveRagMemoryEnabled())

        val disabled = assistant.copy(entryAdvancedMemoryEnabled = false)
        assertFalse(disabled.effectiveRecentContinuityEnabled())
        assertFalse(disabled.effectiveMemorySearchToolEnabled())
        assertFalse(disabled.effectiveRagMemoryEnabled())
    }

    @Test
    fun documentLimitsCountUnicodeCodePointsRatherThanUtf16Units() {
        assertEquals(3, "A🧠B".memoryCodePointCount())
    }

    @Test
    fun historicalIntegerOnlyUsedMemoryRemainsReadable() {
        val decoded = Json.decodeFromString<UsedMemory>(
            """{"memoryId":12,"memoryContent":"old entry","memoryType":0}"""
        )

        assertEquals(12, decoded.memoryId)
        assertEquals(null, decoded.sourceId)
        assertEquals(null, decoded.sourceKind)
    }
}
