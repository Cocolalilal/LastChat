package me.rerere.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.encodeToString
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.ReasoningLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderOptionsSharedTest {
    @Test
    fun reasoningBehaviorReturnsBodiesForLevel() {
        val lowBody = CustomBody("reasoning_effort", JsonPrimitive("low"))
        val behavior = ReasoningRequestBehavior(low = listOf(lowBody))

        assertEquals(listOf(lowBody), behavior.bodiesFor(ReasoningLevel.LOW))
        assertEquals(emptyList(), behavior.bodiesFor(ReasoningLevel.HIGH))
    }

    @Test
    fun inputSchemaConcreteTypeSerializesFromCommonCode() {
        val schema = InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                })
            },
            required = listOf("query")
        )

        val encoded = Json.encodeToString(schema)

        assertTrue(encoded.contains("query"))
        assertEquals(listOf("query"), schema.required)
    }
}
