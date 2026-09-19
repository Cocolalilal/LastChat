package me.rerere.rikkahub.data.ai

import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.ContextPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryContextPlannerTest {
    private val model = Model(modelId = "private-model", contextWindowTokens = 16_384)

    @Test
    fun fixedMemoryUsesExactRenderedPromptAndAvailableSurplus() {
        val candidates = listOf(
            AssistantMemory(id = 1, content = "The user's favorite color is green.", type = 0),
            AssistantMemory(id = 2, content = "The user is planning a hiking trip.", type = 0),
        )

        val plan = selectSmartMemoryContext(
            candidates = candidates,
            model = model,
            inputBudgetTokens = 12_000,
            requiredContextTokens = 500,
            historyMessages = listOf(UIMessage.user("hello")),
            contextPriority = ContextPriority.CHAT_HISTORY,
            episodeGroup = { "Older" },
        )

        assertEquals(candidates, plan.memories)
        assertTrue(plan.promptText.contains("[ID: 1]"))
        assertEquals(ContextTokenEstimator.textTokens(plan.promptText, model), plan.promptTokens)
    }

    @Test
    fun memoryPriorityAllocatesAtLeastAsMuchUnderContention() {
        val candidates = (1..30).map { index ->
            AssistantMemory(id = index, content = "memory $index " + "detail ".repeat(35), type = 0)
        }
        val history = (1..20).map { index ->
            UIMessage.user("history $index " + "conversation ".repeat(50))
        }
        fun plan(priority: ContextPriority) = selectSmartMemoryContext(
            candidates = candidates,
            model = model,
            inputBudgetTokens = 4_000,
            requiredContextTokens = 500,
            historyMessages = history,
            contextPriority = priority,
            episodeGroup = { "Older" },
        )

        assertTrue(
            plan(ContextPriority.MEMORIES).promptTokens >=
                plan(ContextPriority.CHAT_HISTORY).promptTokens
        )
    }

    @Test
    fun renderedMemoryCostIncludesToolInstructions() {
        val toolModel = model.copy(abilities = listOf(ModelAbility.TOOL))
        val memory = listOf(AssistantMemory(id = 7, content = "Remember this.", type = 0))

        val withoutTools = renderMemoryContextPrompt(model, memory) { "Older" }
        val withTools = renderMemoryContextPrompt(toolModel, memory) { "Older" }

        assertTrue(withTools.contains("## Memory Tool"))
        assertTrue(
            ContextTokenEstimator.textTokens(withTools, toolModel) >
                ContextTokenEstimator.textTokens(withoutTools, model)
        )
    }

    @Test
    fun renderedMemoryPromptIncludesToolInstructionsEvenWhenMemoriesEmpty() {
        val toolModel = model.copy(abilities = listOf(ModelAbility.TOOL))

        val withoutTools = renderMemoryContextPrompt(model, emptyList()) { "Older" }
        val withTools = renderMemoryContextPrompt(toolModel, emptyList()) { "Older" }

        assertEquals("", withoutTools)
        assertTrue(withTools.contains("## Memory Tool"))
    }

    @Test
    fun smartMemoryContextIncludesToolInstructionsWhenCandidatesEmpty() {
        val toolModel = model.copy(abilities = listOf(ModelAbility.TOOL))

        val nonToolPlan = selectSmartMemoryContext(
            candidates = emptyList(),
            model = model,
            inputBudgetTokens = 4_000,
            requiredContextTokens = 500,
            historyMessages = listOf(UIMessage.user("hello")),
            contextPriority = ContextPriority.BALANCED,
            episodeGroup = { "Older" },
        )
        assertEquals("", nonToolPlan.promptText)
        assertEquals(0, nonToolPlan.promptTokens)

        val toolPlan = selectSmartMemoryContext(
            candidates = emptyList(),
            model = toolModel,
            inputBudgetTokens = 4_000,
            requiredContextTokens = 500,
            historyMessages = listOf(UIMessage.user("hello")),
            contextPriority = ContextPriority.BALANCED,
            episodeGroup = { "Older" },
        )
        assertTrue(toolPlan.promptText.contains("## Memory Tool"))
        assertTrue(toolPlan.promptTokens > 0)
        assertEquals(ContextTokenEstimator.textTokens(toolPlan.promptText, toolModel), toolPlan.promptTokens)
    }
}
