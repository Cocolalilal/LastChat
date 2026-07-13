package me.rerere.rikkahub.data.ai

import me.rerere.ai.memory.BuiltInMemoryEngines
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationHandlerTest {
    @Test
    fun formatToolExecutionError_usesConciseMessageWithoutStackTrace() {
        val error = formatToolExecutionError(
            IllegalStateException("Tool search_websearch_web not found")
        )

        assertEquals("Tool search_websearch_web not found", error)
        assertFalse(error.contains("IllegalStateException"))
        assertFalse(error.contains("\tat "))
    }

    @Test
    fun basicMemory_isPlacedAfterTheStableSystemPrompt() {
        val assistant = Assistant(
            enableMemory = true,
            memoryEngineId = BuiltInMemoryEngines.SIMPLE,
            useRagMemoryRetrieval = false,
        )

        assertTrue(shouldInjectMemoryInSystemPrompt(assistant))
        assertEquals(
            PromptContextBlocks(
                systemPrompt = "User-defined instructions\n## Memories\n- Stable fact",
                dynamicContext = "Current time: now",
            ),
            placeMemoryPrompt(
                baseSystemPrompt = "User-defined instructions",
                memoryPrompt = "## Memories\n- Stable fact",
                timeAwarenessPrompt = "Current time: now",
                injectMemoryInSystemPrompt = shouldInjectMemoryInSystemPrompt(assistant),
            )
        )
    }

    @Test
    fun ragAndGraphMemory_remainInLatestTurnDynamicContext() {
        val ragAssistant = Assistant(
            enableMemory = true,
            memoryEngineId = BuiltInMemoryEngines.SIMPLE,
            useRagMemoryRetrieval = true,
        )
        val graphAssistant = ragAssistant.copy(
            memoryEngineId = BuiltInMemoryEngines.GRAPH,
            useRagMemoryRetrieval = false,
        )

        assertFalse(shouldInjectMemoryInSystemPrompt(ragAssistant))
        assertFalse(shouldInjectMemoryInSystemPrompt(graphAssistant))
        assertEquals(
            PromptContextBlocks(
                systemPrompt = "User-defined instructions",
                dynamicContext = "## Memories\n- Retrieved fact\nCurrent time: now",
            ),
            placeMemoryPrompt(
                baseSystemPrompt = "User-defined instructions",
                memoryPrompt = "## Memories\n- Retrieved fact",
                timeAwarenessPrompt = "Current time: now",
                injectMemoryInSystemPrompt = false,
            )
        )
    }
}
