package me.rerere.ai.context

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPlannerTest {

    @Test
    fun scaleArchetype_detectedCorrectly() {
        assertEquals(ContextScaleArchetype.MICRO, ContextScaleArchetype.fromCapacity(4_096))
        assertEquals(ContextScaleArchetype.MICRO, ContextScaleArchetype.fromCapacity(8_192))
        assertEquals(ContextScaleArchetype.COMPACT, ContextScaleArchetype.fromCapacity(16_384))
        assertEquals(ContextScaleArchetype.COMPACT, ContextScaleArchetype.fromCapacity(32_768))
        assertEquals(ContextScaleArchetype.COMPACT, ContextScaleArchetype.fromCapacity(65_536))
        assertEquals(ContextScaleArchetype.VAST, ContextScaleArchetype.fromCapacity(128_000))
        assertEquals(ContextScaleArchetype.VAST, ContextScaleArchetype.fromCapacity(1_000_000))
    }

    @Test
    fun pressureTier_compactThresholds() {
        assertEquals(ContextPressureTier.LIGHT, ContextPressureTier.fromUsage(4_000, 10_000, ContextScaleArchetype.COMPACT))
        assertEquals(ContextPressureTier.NORMAL, ContextPressureTier.fromUsage(6_000, 10_000, ContextScaleArchetype.COMPACT))
        assertEquals(ContextPressureTier.MODERATE, ContextPressureTier.fromUsage(7_500, 10_000, ContextScaleArchetype.COMPACT))
        assertEquals(ContextPressureTier.HIGH, ContextPressureTier.fromUsage(9_000, 10_000, ContextScaleArchetype.COMPACT))
        assertEquals(ContextPressureTier.CRITICAL, ContextPressureTier.fromUsage(9_600, 10_000, ContextScaleArchetype.COMPACT))
    }

    @Test
    fun toolDistillation_lockedToCriticalOrMicroEmergency() {
        val compactModel = Model(modelId = "test-compact", contextWindowTokens = 32_000)
        val shortChat = listOf(
            UIMessage.user("Hello"),
            UIMessage.assistant("Hi there!"),
        )

        // Light pressure -> no tool distillation
        val planNormal = ContextPlanner.plan(
            messages = shortChat,
            model = compactModel,
            customBudgetTokens = 25_000,
            systemPromptTokens = 500,
            toolDefinitionTokens = 1_000,
        )
        assertFalse(planNormal.allowToolDistillation)

        // Critical pressure -> tool distillation allowed
        val planCritical = ContextPlanner.plan(
            messages = shortChat,
            model = compactModel,
            customBudgetTokens = 1_400,
            systemPromptTokens = 500,
            toolDefinitionTokens = 1_000,
        )
        assertTrue(planCritical.allowToolDistillation)
    }

    @Test
    fun characterSystemPrompts_neverCompactedByCompactToTokenBudget() {
        val longSystemText = "You are a specialized character. ".repeat(40) // ~1300 chars
        val longUserText = "Here is my long code question. ".repeat(40)

        val messages = listOf(
            UIMessage.system(longSystemText),
            UIMessage.user(longUserText),
        )

        val model = Model(modelId = "test-model", contextWindowTokens = 32_000)
        // Force aggressive compaction by giving a very small budget
        val compacted = messages.compactToTokenBudget(model, budget = 250)

        val compactedSystem = compacted.first { it.role == MessageRole.SYSTEM }
        val compactedUser = compacted.first { it.role == MessageRole.USER }

        // System prompt text MUST NOT be modified or truncated
        val systemTextPart = compactedSystem.parts.filterIsInstance<UIMessagePart.Text>().first()
        assertEquals(longSystemText, systemTextPart.text)

        // User text should be compacted
        val userTextPart = compactedUser.parts.filterIsInstance<UIMessagePart.Text>().first()
        assertTrue(userTextPart.text.contains("context compacted"))
    }

    @Test
    fun calculateMinSafeFloorTokens_enforcesSafetyMargin() {
        val model = Model(modelId = "test-floor", contextWindowTokens = 32_000)

        val floor = calculateMinSafeFloorTokens(
            model = model,
            systemPromptTokens = 1_000,
            toolDefinitionTokens = 1_200,
            requestedOutputTokens = 2_000,
        )

        // Fixed = 1000 + 1200 + 2000 = 4200. Floor = 4200 + 512 = 4712.
        assertEquals(4_712, floor)
        assertTrue(floor >= 1_500)

        // Model capacity smaller than theoretical floor is clamped to capacity
        val smallModel = Model(modelId = "tiny-floor", contextWindowTokens = 4_000)
        val clampedFloor = calculateMinSafeFloorTokens(
            model = smallModel,
            systemPromptTokens = 2_000,
            toolDefinitionTokens = 2_000,
            requestedOutputTokens = 1_000,
        )
        assertEquals(4_000, clampedFloor)
    }
}
