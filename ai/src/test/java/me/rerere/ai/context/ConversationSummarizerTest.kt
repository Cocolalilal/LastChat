package me.rerere.ai.context

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class ConversationSummarizerTest {
    @Test
    fun fullSourceIncludesLateConstraintsAndToolDetails() {
        val requirement = "Do not publish until Friday; keep customer_id=00127 exactly."
        val code = "x".repeat(3_000) + "\nreturn retainOriginalMessages()"
        val outcome = "log\n".repeat(2_000) + "exit_code=1; rollback required"
        val messages = listOf(
            UIMessage.user("Background ".repeat(700) + requirement),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
                UIMessagePart.ToolCall("call-1", "write_file", code),
            )),
            UIMessage(role = MessageRole.TOOL, parts = listOf(
                UIMessagePart.ToolResult("call-1", "write_file", JsonPrimitive(outcome), JsonPrimitive("{}")),
            )),
        )
        val source = ConversationSummarizer.transcript(messages)
        assertTrue(source.contains(requirement))
        assertTrue(source.contains(code))
        assertTrue(source.contains(outcome))
        assertEquals(2, Regex("id=call-1").findAll(source).count())
    }

    @Test
    fun smallHelperBatchesAnOversizedTurnWithoutDroppingOrRepeatingCharacters() {
        val model = Model(modelId = "small-helper", contextWindowTokens = 4_096)
        val messages = listOf(UIMessage.user("事实: keep 🐈 identifiers 00127\n".repeat(2_000)))
        val requests = ConversationSummarizer.requests(messages, model)
        assertTrue(requests.size > 1)
        assertEquals(ConversationSummarizer.transcript(messages), requests.joinToString("") { it.source })
        requests.forEach {
            assertTrue(ContextTokenEstimator.messagesTokens(it.messages, model) <=
                smartInputBudget(model, it.maxOutputTokens)!!)
            assertEquals(MessageRole.SYSTEM, it.messages.first().role)
            assertEquals(it.source, it.messages.last().toText())
            assertFalse(it.source.last().isHighSurrogate())
            assertFalse(it.source.first().isLowSurrogate())
        }
    }

    @Test
    fun explicitReasoningBudgetIsReservedBeforeFittingSummaryInput() {
        val model = Model(modelId = "reasoning-helper", contextWindowTokens = 32_000)
        val requests = ConversationSummarizer.requests(
            listOf(UIMessage.user("source ".repeat(10_000))), model, thinkingBudget = 8_192,
        )
        assertTrue(requests.isNotEmpty())
        requests.forEach {
            assertTrue(it.maxOutputTokens > 8_192)
            assertTrue(ContextTokenEstimator.messagesTokens(it.messages, model) <=
                smartInputBudget(model, it.maxOutputTokens)!!)
        }
    }

    @Test
    fun incompleteOrBlankBatchDoesNotProduceASuccessfulCheckpoint() = runBlocking {
        val model = Model(modelId = "helper", contextWindowTokens = 4_096)
        val requests = ConversationSummarizer.requests(listOf(UIMessage.user("source ".repeat(4_000))), model)
        assertTrue(requests.size > 1)
        for (reason in listOf("length", "MAX_TOKENS", "content_filter", "blank")) {
            var calls = 0
            val result = runCatching {
                ConversationSummarizer.summarize(requests) {
                    calls++
                    UIMessageChoice(
                        index = 0, delta = null,
                        message = UIMessage.assistant(if (calls == 2 && reason == "blank") " " else "checkpoint"),
                        finishReason = if (calls == 2) reason else "stop",
                    )
                }
            }
            assertTrue("Must reject $reason", result.isFailure)
            assertEquals(2, calls)
        }
    }

    @Test
    fun allSuccessfulBatchesAreKeptInOrder() = runBlocking {
        val model = Model(modelId = "helper", contextWindowTokens = 4_096)
        val requests = ConversationSummarizer.requests(listOf(UIMessage.user("source ".repeat(4_000))), model)
        var calls = 0
        val summary = ConversationSummarizer.summarize(requests) {
            calls++
            UIMessageChoice(0, null, UIMessage.assistant("checkpoint $calls"), "stop")
        }
        assertEquals(requests.size, calls)
        requests.indices.forEach { assertTrue(summary.contains("checkpoint ${it + 1}")) }
        assertTrue(summary.indexOf("checkpoint 1") < summary.indexOf("checkpoint 2"))
    }

    @Test
    fun historyAndToolEvidenceRemainVerbatimWhileTheyFit() {
        val model = Model(modelId = "compact", contextWindowTokens = 32_000)
        val messages = (0 until 8).flatMap {
            listOf(
                UIMessage.user("Question $it " + "context ".repeat(100)),
                UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
                    UIMessagePart.ToolCall("c$it", "web_search", "{\"query\":\"evidence\"}"),
                )),
                UIMessage(role = MessageRole.TOOL, parts = listOf(
                    UIMessagePart.ToolResult(
                        "c$it", "web_search", JsonPrimitive("Source $it " + "finding ".repeat(200)), JsonPrimitive("{}"),
                    ),
                )),
                UIMessage.assistant("Answer $it"),
            )
        }
        val budget = ContextTokenEstimator.messagesTokens(messages, model) + 1
        assertEquals(messages, smartPrepareHistory(messages, model, budget))
        assertEquals(messages, smartFitContext(messages, model, budget))
    }

    @Test
    fun receiptsDoNotReportSuccessWithoutEvidence() {
        val failedEdit = createSemanticToolReceipt(UIMessagePart.ToolResult(
            "edit-1", "write_file", JsonPrimitive("Permission denied: file unchanged"), JsonPrimitive("{}"),
        )).toString()
        assertTrue(failedEdit.contains("Permission denied"))
        assertFalse(failedEdit.contains("successfully"))
        val unknownCommand = createSemanticToolReceipt(UIMessagePart.ToolResult(
            "exec-1", "run_command", JsonPrimitive("Process started; still running"), JsonPrimitive("{}"),
        )).toString()
        assertTrue(unknownCommand.contains("unknown"))
        val search = createSemanticToolReceipt(UIMessagePart.ToolResult(
            "search-1", "web_search", JsonPrimitive("Found evidence"), JsonPrimitive("{}"),
        )).toString()
        assertTrue(search.contains("Web search"))
        assertFalse(search.contains("Command:"))
    }

    @Test
    fun emergencyReceiptReductionPreservesObjectShapeAndArguments() {
        val model = Model(modelId = "small", contextWindowTokens = 2_048)
        val arguments = buildJsonObject { put("command", "test") }
        val messages = listOf(
            UIMessage.user("Run tests"),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
                UIMessagePart.ToolCall("c1", "run_command", "{\"command\":\"test\"}"),
            )),
            UIMessage(role = MessageRole.TOOL, parts = listOf(UIMessagePart.ToolResult(
                "c1", "run_command", buildJsonObject {
                    put("stdout", "log ".repeat(4_000))
                    put("exit_code", 7)
                }, arguments,
            ))),
        )
        val fitted = messages.compactToTokenBudget(model, 400)
        assertTrue(ContextTokenEstimator.messagesTokens(fitted, model) <= 400)
        val result = fitted.flatMap { it.getToolResults() }.single()
        assertTrue(result.content is JsonObject)
        assertEquals(arguments, result.arguments)
        assertEquals("c1", result.toolCallId)
    }
}
