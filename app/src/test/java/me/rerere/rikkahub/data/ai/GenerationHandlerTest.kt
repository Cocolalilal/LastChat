package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
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

    @Test
    fun selectSmartTools_prioritizesLookAtScreenEvenWithAmbiguousQuery() {
        val lookAtScreenTool = Tool(
            name = "look_at_screen",
            description = "Inspect the screenshot of the user's active screen captured when the assistant was summoned.",
            execute = { JsonPrimitive("") },
        )
        val weatherTool = Tool(
            name = "get_weather",
            description = "Fetch current weather conditions for a specified city or location.",
            execute = { JsonPrimitive("") },
        )
        val calculatorTool = Tool(
            name = "calculate",
            description = "Perform mathematical expressions and arithmetic operations.",
            execute = { JsonPrimitive("") },
        )
        val gitTool = Tool(
            name = "git_status",
            description = "Check git working tree status and branch diffs.",
            execute = { JsonPrimitive("") },
        )

        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("What is this?")),
            )
        )

        // Budget that fits ~2 tools
        val selected = selectSmartTools(
            tools = listOf(weatherTool, calculatorTool, gitTool, lookAtScreenTool),
            messages = messages,
            model = Model(),
            inputBudgetTokens = 600,
        )

        assertTrue(
            "look_at_screen must be preserved by smart tool selection due to high priority score",
            selected.any { it.name == "look_at_screen" },
        )
    }

    @Test
    fun extractInjectedImageParts_extractsAndSanitizesToolResults() {
        val testUrl = "data:image/jpeg;base64,dummybase64"
        val toolResult = UIMessagePart.ToolResult(
            toolCallId = "call_test",
            toolName = "look_at_screen",
            content = buildJsonObject {
                put("note", JsonPrimitive("Screenshot captured"))
                put(
                    TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY,
                    JsonArray(listOf(JsonPrimitive(testUrl))),
                )
            },
            arguments = buildJsonObject { },
        )

        val (sanitized, injectedImages) = extractInjectedImageParts(listOf(toolResult))

        assertEquals(1, sanitized.size)
        val sanitizedContent = sanitized.first().content as JsonObject
        assertEquals("Screenshot captured", sanitizedContent["note"]?.let { (it as JsonPrimitive).content })
        assertFalse(
            "TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY should be removed from sanitized tool result",
            sanitizedContent.containsKey(TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY),
        )

        assertEquals(1, injectedImages.size)
        assertEquals(testUrl, injectedImages.first().url)
    }
}
