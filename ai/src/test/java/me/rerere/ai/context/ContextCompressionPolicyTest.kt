package me.rerere.ai.context

import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.toTurnGroups
import org.junit.Assert.*
import org.junit.Test

class ContextCompressionPolicyTest {
    @Test
    fun preservationStylesChangeTriggerSpaceAndSummaryDetailTogether() {
        val model = Model(modelId = "large", contextWindowTokens = 128_000)
        val detail = ContextPlanner.plan(emptyList(), model, policy = ContextCompressionPolicy(ContextCompressionStyle.MORE_DETAIL))
        val balanced = ContextPlanner.plan(emptyList(), model)
        val room = ContextPlanner.plan(emptyList(), model, policy = ContextCompressionPolicy(ContextCompressionStyle.MORE_ROOM))
        assertTrue(detail.highWatermarkFraction > balanced.highWatermarkFraction)
        assertTrue(balanced.highWatermarkFraction > room.highWatermarkFraction)
        assertTrue(detail.lowWatermarkTargetTokens > balanced.lowWatermarkTargetTokens)
        assertTrue(balanced.lowWatermarkTargetTokens > room.lowWatermarkTargetTokens)
        assertTrue(ContextCompressionStyle.MORE_DETAIL.summaryFraction > ContextCompressionStyle.MORE_ROOM.summaryFraction)
    }

    @Test
    fun recentExchangePreferenceIsRelaxedOnlyForCriticalPressure() {
        val model = Model(modelId = "chat", contextWindowTokens = 32_000)
        val messages = (0 until 10).flatMap { listOf(UIMessage.user("User $it"), UIMessage.assistant("Reply $it")) }
        val policy = ContextCompressionPolicy(recentTurns = 6)
        val normal = ContextPlanner.calculateMilestoneSlice(
            messages.toTurnGroups(), 0, ContextScaleArchetype.COMPACT, ContextPressureTier.HIGH,
            messages, model, 1, policy,
        )!!
        assertEquals(4, normal.groupsSummarized)
        val emergency = ContextPlanner.calculateMilestoneSlice(
            messages.toTurnGroups(), 0, ContextScaleArchetype.COMPACT, ContextPressureTier.CRITICAL,
            messages, model, 1, policy,
        )!!
        assertEquals(9, emergency.groupsSummarized)
    }
}
