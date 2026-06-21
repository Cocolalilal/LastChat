package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
