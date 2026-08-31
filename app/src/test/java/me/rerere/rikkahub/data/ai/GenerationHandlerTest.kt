package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CustomBody
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
    fun smartContextSafeCustomBodiesProtectsRequestAndOutputBudgetFields() {
        val safe = smartContextSafeCustomBodies(
            listOf(
                CustomBody("messages", JsonPrimitive("replacement")),
                CustomBody("tools", JsonPrimitive("replacement")),
                CustomBody("max_tokens", JsonPrimitive(999_999)),
                CustomBody(
                    "generationConfig",
                    JsonObject(
                        mapOf(
                            "maxOutputTokens" to JsonPrimitive(999_999),
                            "temperature" to JsonPrimitive(0.4),
                        )
                    ),
                ),
                CustomBody("seed", JsonPrimitive(42)),
            )
        )

        assertEquals(listOf("generationConfig", "seed"), safe.map { it.key })
        val generationConfig = safe.first().value as JsonObject
        assertFalse(generationConfig.containsKey("maxOutputTokens"))
        assertTrue(generationConfig.containsKey("temperature"))
    }
}
