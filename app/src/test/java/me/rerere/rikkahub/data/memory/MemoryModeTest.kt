package me.rerere.rikkahub.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemoryMode
import me.rerere.rikkahub.data.model.resolvedMemoryMode
import me.rerere.rikkahub.data.model.withMemoryMode

class MemoryModeTest {
    @Test
    fun legacyCombinationsResolveWithoutAVisibleTransfer() {
        assertEquals(AssistantMemoryMode.OFF, Assistant(enableMemory = false).resolvedMemoryMode())
        assertEquals(
            AssistantMemoryMode.BASIC,
            Assistant(enableMemory = true, useRagMemoryRetrieval = false).resolvedMemoryMode(),
        )
        assertEquals(
            AssistantMemoryMode.SEARCHABLE,
            Assistant(enableMemory = true, useRagMemoryRetrieval = true).resolvedMemoryMode(),
        )
        assertEquals(
            AssistantMemoryMode.ADAPTIVE,
            Assistant(enableMemory = true, enableMemoryConsolidation = true).resolvedMemoryMode(),
        )
    }

    @Test
    fun explicitModesKeepCompatibilityFlagsCoherent() {
        val adaptive = Assistant().withMemoryMode(AssistantMemoryMode.ADAPTIVE)
        assertTrue(adaptive.enableMemory)
        assertTrue(adaptive.useRagMemoryRetrieval)
        assertTrue(adaptive.enableMemoryConsolidation)
        assertTrue(adaptive.enableTimeAwareness)

        val basic = adaptive.withMemoryMode(AssistantMemoryMode.BASIC)
        assertTrue(basic.enableMemory)
        assertFalse(basic.useRagMemoryRetrieval)
        assertFalse(basic.enableMemoryConsolidation)
    }
}
