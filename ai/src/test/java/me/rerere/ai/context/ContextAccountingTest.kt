package me.rerere.ai.context

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAccountingTest {
    @Test
    fun smartBudget_reservesOutputAndSafetyMargin() {
        val model = Model(modelId = "gpt-test", contextWindowTokens = 32_000)

        val budget = smartInputBudget(model, requestedOutputTokens = 4_000)

        assertEquals(27_360, budget)
    }

    @Test
    fun unknownTokenizer_isConservative() {
        val text = "a".repeat(320)

        val tokens = ContextTokenEstimator.textTokens(text, Model(modelId = "private-model"))

        assertTrue(tokens >= 100)
    }

    @Test
    fun imageLimit_keepsNewestImagesAndPreservesText() {
        val model = Model(modelId = "vision", contextWindowTokens = 8_192, maxImagesInContext = 2)
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("first"), UIMessagePart.Image("old"))),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("middle"), UIMessagePart.Image("newer"))),
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("latest"), UIMessagePart.Image("newest"))),
        )

        val limited = messages.limitImagesForModel(model)
        val images = limited.flatMap { it.parts }.filterIsInstance<UIMessagePart.Image>().map { it.url }

        assertEquals(listOf("newer", "newest"), images)
        assertTrue(limited.first().parts.filterIsInstance<UIMessagePart.Text>().any { it.text == "first" })
        assertTrue(limited.first().parts.filterIsInstance<UIMessagePart.Text>().any { it.text.contains("omitted") })
    }

    @Test
    fun providerCount_reconcilesTotalWithoutLosingBreakdown() {
        val model = Model(modelId = "gpt-test", contextWindowTokens = 10_000)
        val usage = ContextTokenEstimator.breakdown(
            messages = listOf(UIMessage.user("hello world")),
            model = model,
            systemPromptText = "system",
            providerPromptTokens = 123,
        )

        assertEquals(123, usage.usedTokens)
        assertEquals(ContextCountConfidence.PROVIDER_COUNTED, usage.confidence)
        assertTrue(usage.conversationTokens + usage.systemPromptTokens > 0)
    }

    @Test
    fun hardCompaction_preservesToolPairAndFitsBudget() {
        val model = Model(modelId = "private-model", contextWindowTokens = 2_048)
        val messages = listOf(
            UIMessage.user("question"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.ToolCall("call-1", "search", "{\"q\":\"query\"}")),
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        "call-1",
                        "search",
                        JsonPrimitive("x".repeat(8_000)),
                        JsonPrimitive("{}"),
                    )
                ),
            ),
        )

        val compacted = messages.compactToTokenBudget(model, 500)

        assertTrue(ContextTokenEstimator.messagesTokens(compacted, model) <= 500)
        assertEquals(1, compacted.flatMap { it.parts }.filterIsInstance<UIMessagePart.ToolCall>().size)
        assertEquals(1, compacted.flatMap { it.parts }.filterIsInstance<UIMessagePart.ToolResult>().size)
    }

    @Test
    fun liveBreakdown_countsSummaryAndNamedContextOutsideRawMessages() {
        val model = Model(modelId = "private-model", contextWindowTokens = 8_192)
        val withoutSummary = ContextTokenEstimator.breakdown(
            messages = listOf(UIMessage.user("hello")),
            model = model,
            systemPromptText = "system prompt",
        )
        val withSummary = ContextTokenEstimator.breakdown(
            messages = listOf(UIMessage.user("hello")),
            model = model,
            systemPromptText = "system prompt",
            summaryText = "summary ".repeat(100),
        )

        assertTrue(withSummary.summaryTokens > 0)
        assertTrue(withSummary.usedTokens > withoutSummary.usedTokens)
    }
}
