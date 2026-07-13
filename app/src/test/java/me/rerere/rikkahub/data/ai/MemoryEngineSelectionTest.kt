package me.rerere.rikkahub.data.ai

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import me.rerere.ai.memory.BuiltInMemoryEngines
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.resolvedMemoryEngineId

class MemoryEngineSelectionTest {
    @Test
    fun legacyAssistantsResolveWithoutChangingTheirStoredJson() {
        assertEquals(BuiltInMemoryEngines.OFF, Assistant(enableMemory = false).resolvedMemoryEngineId())
        assertEquals(BuiltInMemoryEngines.SIMPLE, Assistant(enableMemory = true).resolvedMemoryEngineId())
    }

    @Test
    fun graphSearchToolUsesItsOwnToggle() {
        val graph = Assistant(
            enableMemory = true,
            memoryEngineId = BuiltInMemoryEngines.GRAPH,
            enableMemorySearchTool = false,
            graphSearchToolEnabled = true,
        )
        assertTrue(shouldRegisterMemorySearchTool(graph))
        assertFalse(shouldRegisterMemorySearchTool(graph.copy(graphSearchToolEnabled = false)))
    }
}
