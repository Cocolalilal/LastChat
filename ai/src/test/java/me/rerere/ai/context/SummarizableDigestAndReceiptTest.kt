package me.rerere.ai.context

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarizableDigestAndReceiptTest {

    @Test
    fun toSummarizableDigest_preservesToolActionsAndResultsUnlikeToText() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("Let me check the directory."),
                UIMessagePart.ToolCall("call-1", "run_command", """{"command":"git status"}"""),
            )
        )
        val resultMessage = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult("call-1", "run_command", JsonPrimitive("On branch main\nnothing to commit"), JsonPrimitive("""{"command":"git status"}"""))
            )
        )

        // msg.toText() historically returns "" for tool calls and results:
        assertEquals("Let me check the directory.", message.toText().trim())
        assertEquals("", resultMessage.toText().trim())

        // toSummarizableDigest() preserves them with high fidelity:
        val assistantDigest = message.toSummarizableDigest()
        assertTrue("Digest must include action: $assistantDigest", assistantDigest.contains("[Action: run_command"))
        assertTrue("Digest must include arguments: $assistantDigest", assistantDigest.contains("git status"))

        val resultDigest = resultMessage.toSummarizableDigest()
        assertTrue("Digest must include action result: $resultDigest", resultDigest.contains("[Action Result: run_command"))
        assertTrue("Digest must include command output: $resultDigest", resultDigest.contains("On branch main"))
    }

    @Test
    fun createSemanticToolReceipt_preservesCompilerErrorsAndExitCode() {
        val part = UIMessagePart.ToolResult(
            toolCallId = "call-1",
            toolName = "run_command",
            content = JsonPrimitive("Compilation failed:\nerror: Unresolved reference 'foo'\n    at Main.kt:10\nBUILD FAILED"),
            arguments = JsonPrimitive("""{"command":"./gradlew compileKotlin","exit_code":"1"}"""),
        )

        val receipt = createSemanticToolReceipt(part)
        val text = (receipt as JsonPrimitive).content

        assertTrue("Receipt should contain command: $text", text.contains("./gradlew compileKotlin"))
        assertTrue("Receipt should contain exit code: $text", text.contains("1"))
        assertTrue("Receipt must preserve compiler error: $text", text.contains("Unresolved reference 'foo'"))
    }

    @Test
    fun createSemanticToolReceipt_preservesFileOutlineAndLineCount() {
        val fileContent = """
            package com.example
            
            class Engine {
                fun start() { println("started") }
                fun stop() { println("stopped") }
            }
        """.trimIndent()

        val part = UIMessagePart.ToolResult(
            toolCallId = "call-read",
            toolName = "read_file",
            content = JsonPrimitive(fileContent),
            arguments = JsonPrimitive("""{"path":"src/Engine.kt"}"""),
        )

        val receipt = createSemanticToolReceipt(part)
        val text = (receipt as JsonPrimitive).content

        assertTrue("Receipt should contain path: $text", text.contains("src/Engine.kt"))
        assertTrue("Receipt should contain line count: $text", text.contains("6 lines"))
        assertTrue("Receipt should extract class/function outline: $text", text.contains("class Engine") || text.contains("fun start()"))
    }

    @Test
    fun createSemanticToolReceipt_zhipuJsonObjectCompatibility() {
        val part = UIMessagePart.ToolResult(
            toolCallId = "call-zhipu",
            toolName = "web_search",
            content = buildJsonObject {
                put("status", "ok")
                put("results", "Found 5 articles about Kotlin 2.0")
            },
            arguments = JsonPrimitive("""{"query":"Kotlin 2.0"}"""),
        )

        val receipt = createSemanticToolReceipt(part)
        // If content was JsonObject, receipt must be JsonObject for provider schema compatibility
        assertTrue("Receipt must be JsonObject: $receipt", receipt is JsonObject)
        val obj = receipt as JsonObject
        assertEquals("compacted", obj["status"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun compactToTokenBudget_neverMutatesThinkingBlockText() {
        val originalThinkingText = "This is a cryptographically signed thinking trace that must not change."
        val messages = listOf(
            UIMessage.user("Solve this puzzle"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Thinking(thinking = originalThinkingText),
                    UIMessagePart.Text("Here is the answer: 42"),
                )
            )
        )

        val model = Model(modelId = "claude-3-7-sonnet", contextWindowTokens = 32_000)
        val compacted = messages.compactToTokenBudget(model, budget = 200)

        val assistantMessage = compacted.first { it.role == MessageRole.ASSISTANT }
        val thinkingPart = assistantMessage.parts.filterIsInstance<UIMessagePart.Thinking>().first()

        // The text in the thinking part MUST be 100% identical (no truncation / signature mismatch)
        assertEquals(originalThinkingText, thinkingPart.thinking)
    }

    @Test
    fun compactToTokenBudget_neverWipesToolArguments() {
        val originalArgs = JsonPrimitive("""{"query":"Kotlin Coroutines","limit":10}""")
        val messages = listOf(
            UIMessage.user("Search for coroutines"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.ToolCall("c1", "search", """{"query":"Kotlin Coroutines","limit":10}"""))
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult("c1", "search", JsonPrimitive("x".repeat(5000)), originalArgs)
                )
            ),
        )

        val model = Model(modelId = "gpt-4o", contextWindowTokens = 32_000)
        val compacted = messages.compactToTokenBudget(model, budget = 300)

        val toolMessage = compacted.first { it.role == MessageRole.TOOL }
        val toolResult = toolMessage.parts.filterIsInstance<UIMessagePart.ToolResult>().first()

        // Arguments must NOT be wiped to "{}"!
        assertEquals(originalArgs, toolResult.arguments)
        assertFalse(toolResult.arguments.toString() == "{}")
    }
}
