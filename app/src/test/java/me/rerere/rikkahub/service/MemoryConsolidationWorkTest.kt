package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConsolidationWorkTest {
    @Test
    fun buildMemoryConsolidationInputData_includesExplicitAssistantScope() {
        val data = buildMemoryConsolidationInputData(
            assistantId = "assistant-1",
            isFullScan = true,
            forceConversationId = "conversation-1",
        )

        assertEquals("assistant-1", data.getString(MEMORY_CONSOLIDATION_KEY_ASSISTANT_ID))
        assertTrue(data.getBoolean(MEMORY_CONSOLIDATION_KEY_FULL_SCAN, false))
        assertEquals("conversation-1", data.getString(MEMORY_CONSOLIDATION_KEY_FORCE_CONVERSATION_ID))
    }

    @Test
    fun resolveConsolidationAssistantId_prefersExplicitAssistantId() {
        assertEquals(
            "assistant-explicit",
            resolveConsolidationAssistantId(
                explicitAssistantId = "assistant-explicit",
                fallbackAssistantId = "assistant-fallback",
            )
        )
        assertEquals(
            "assistant-fallback",
            resolveConsolidationAssistantId(
                explicitAssistantId = null,
                fallbackAssistantId = "assistant-fallback",
            )
        )
        assertEquals(
            "assistant-fallback",
            resolveConsolidationAssistantId(
                explicitAssistantId = "",
                fallbackAssistantId = "assistant-fallback",
            )
        )
    }
}
