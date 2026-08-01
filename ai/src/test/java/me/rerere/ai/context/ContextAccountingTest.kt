package me.rerere.ai.context

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Modality
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

        assertEquals(26_000, budget)
        assertEquals(4_000, smartOutputTokenBudget(model, requestedOutputTokens = 4_000))
    }

    @Test
    fun unknownTokenizer_isConservative() {
        val text = "a".repeat(320)

        val tokens = ContextTokenEstimator.textTokens(text, Model(modelId = "private-model"))

        assertTrue(tokens >= 100)
    }

    @Test
    fun unknownTokenizer_isConservativeForNonAsciiText() {
        val text = "ä½ å¥½ä¸–ç•Œ".repeat(100)

        val tokens = ContextTokenEstimator.textTokens(text, Model(modelId = "private-model"))

        assertTrue(tokens >= 350)
    }

    @Test
    fun smartOutputBudget_neverClaimsMoreThanHalfTheWindow() {
        val model = Model(modelId = "small", contextWindowTokens = 4_096)

        assertEquals(2_048, smartOutputTokenBudget(model, requestedOutputTokens = 8_192))
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
    fun smartFit_preservesLatestToolDependencyAndNeverExceedsBudget() {
        val model = Model(modelId = "private-model", contextWindowTokens = 2_048)
        val messages = buildList {
            repeat(20) { index ->
                add(UIMessage.user("old question $index " + "detail ".repeat(80)))
                add(UIMessage.assistant("old answer $index " + "answer ".repeat(80)))
            }
            add(UIMessage.user("latest question"))
            add(
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.ToolCall("latest-call", "search", "{\"q\":\"latest\"}")),
                )
            )
            add(
                UIMessage(
                    role = MessageRole.TOOL,
                    parts = listOf(
                        UIMessagePart.ToolResult(
                            "latest-call",
                            "search",
                            JsonPrimitive("result ".repeat(1_000)),
                            JsonPrimitive("{}"),
                        )
                    ),
                )
            )
        }

        val fitted = smartFitContext(messages, model, messageBudgetTokens = 700)
        val parts = fitted.flatMap { it.parts }

        assertTrue(ContextTokenEstimator.messagesTokens(fitted, model) <= 700)
        assertEquals(1, parts.filterIsInstance<UIMessagePart.ToolCall>().count { it.toolCallId == "latest-call" })
        assertEquals(1, parts.filterIsInstance<UIMessagePart.ToolResult>().count { it.toolCallId == "latest-call" })
    }

    @Test
    fun smartFit_usesAdaptiveHardImageLimit() {
        val model = Model(
            modelId = "vision",
            contextWindowTokens = 16_384,
            maxImagesInContext = 8,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
        )
        val messages = (1..6).map { index ->
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("image $index"), UIMessagePart.Image("image-$index")),
            )
        }

        val fitted = smartFitContext(messages, model, messageBudgetTokens = 8_000)

        assertEquals(1, fitted.flatMap { it.parts }.filterIsInstance<UIMessagePart.Image>().size)
        assertTrue(fitted.flatMap { it.parts }.filterIsInstance<UIMessagePart.Text>().any { it.text.contains("omitted") })
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

    @Test
    fun effectiveHistory_replacesSummarizedMessagesAndHonorsTruncation() {
        val messages = (0 until 100).map { index -> UIMessage.user("message $index") }

        val retained = effectiveHistoryForContext(
            messages = messages,
            smartManagement = true,
            summaryUpToIndex = 89,
            truncateIndex = 94,
        )

        assertEquals(messages.drop(94), retained)
    }

    @Test
    fun effectiveHistory_keepsFullHistoryWhenSummaryIndexIsInvalid() {
        val messages = (0 until 5).map { index -> UIMessage.user("message $index") }

        val retained = effectiveHistoryForContext(
            messages = messages,
            smartManagement = true,
            summaryUpToIndex = 99,
            truncateIndex = -1,
        )

        assertEquals(messages, retained)
    }

    @Test
    fun summaryReplacement_materiallyLowersNextRequestUsage() {
        val model = Model(modelId = "private-model", contextWindowTokens = 32_000)
        val messages = (0 until 100).map { index ->
            UIMessage.user("message $index " + "substantial conversation detail ".repeat(20))
        }
        val before = ContextTokenEstimator.breakdown(messages = messages, model = model)
        val retained = effectiveHistoryForContext(
            messages = messages,
            smartManagement = true,
            summaryUpToIndex = 89,
            truncateIndex = -1,
        )
        val after = ContextTokenEstimator.breakdown(
            messages = retained,
            model = model,
            summaryText = "A concise summary of the earlier conversation.",
        )

        assertEquals(10, retained.size)
        assertTrue(after.usedTokens < before.usedTokens / 3)
        assertTrue(after.summaryTokens > 0)
    }

    @Test
    fun probableTemporaryReserve_usesRecentObservedInjectionCosts() {
        val model = Model(modelId = "private-model", contextWindowTokens = 100_000)

        val reserve = probableTemporaryTokenReserve(
            model = model,
            requestedOutputTokens = 4_000,
            memoryEnabled = true,
            memoryCandidateLimit = 10,
            observedMemoryTokenTotals = listOf(100, 200, 500, 1_000),
            conditionalContextCandidateTokens = 2_000,
            observedConditionalTokenTotals = listOf(300),
        )

        assertEquals(800, reserve)
    }

    @Test
    fun probableTemporaryTokens_areProjectedWithoutInflatingCurrentUsage() {
        val model = Model(modelId = "private-model", contextWindowTokens = 16_384)
        val usage = ContextTokenEstimator.breakdown(
            messages = listOf(UIMessage.user("hello")),
            model = model,
            probableTemporaryTokens = 900,
        )

        assertEquals(900, usage.probableTemporaryTokens)
        assertEquals(usage.usedTokens + 900, usage.projectedUsedTokens)
        assertTrue(usage.probableFraction > 0f)
    }
}
