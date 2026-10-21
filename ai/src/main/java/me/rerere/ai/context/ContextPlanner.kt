package me.rerere.ai.context

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.contextCapacityTokens
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.TurnGroup
import me.rerere.ai.ui.toTurnGroups


/**
 * Slicing execution plan for atomic milestone summarization.
 */
data class MilestoneSlicePlan(
    val startIndex: Int,
    val lastIndexToSummarize: Int,
    val groupsSummarized: Int,
)

/**
 * The execution blueprint produced by the context manager before prompt packing.
 *
 * Guarantees:
 * - Cache prefix stability ([freezeBoundaryIndex]) snapped to TurnGroup boundaries.
 * - Sacred character prompts (system prompts are never truncated).
 * - Tool schema distillation is locked to [allowToolDistillation] (CRITICAL only).
 * - Discretionary dual-watermark hysteresis for scale-aware auto-summarization.
 */
data class ContextPlan(
    val archetype: ContextScaleArchetype,
    val pressureTier: ContextPressureTier,
    val totalCapacityTokens: Int,
    val usableInputTokens: Int,
    val reservedOutputTokens: Int,
    val fixedOverheadTokens: Int,
    val discretionaryTokens: Int,
    val freezeBoundaryIndex: Int,
    val allowToolDistillation: Boolean,
    val receiptifyHistoricalTools: Boolean,
    val protectedRecentTurns: Int,
    val imageLimit: Int,
    val discretionaryFraction: Float = 0f,
    val highWatermarkFraction: Float = 0.75f,
    val lowWatermarkTargetTokens: Int = 0,
    val retentionTurnGroupCount: Int = 4,
    val shouldTriggerAutoSummary: Boolean = false,
)

object ContextPlanner {

    fun plan(
        messages: List<UIMessage>,
        model: Model,
        customBudgetTokens: Int? = null,
        systemPromptTokens: Int = 0,
        toolDefinitionTokens: Int = 0,
        policy: ContextCompressionPolicy = ContextCompressionPolicy(),
    ): ContextPlan {
        val capacity = model.contextCapacityTokens ?: Int.MAX_VALUE
        val archetype = ContextScaleArchetype.fromCapacity(capacity)
        val usableInput = customBudgetTokens?.coerceAtLeast(1)
            ?: smartInputBudget(model, null)
            ?: capacity
        val reservedOutput = smartOutputTokenBudget(model, null) ?: 0
        // usableInput already excludes the response reserve and safety margin.
        val fixedOverhead = systemPromptTokens + toolDefinitionTokens
        val discretionary = (usableInput - fixedOverhead).coerceAtLeast(0)

        val rawMessageTokens = ContextTokenEstimator.messagesTokens(messages, model)
        val totalEstimatedUsed = rawMessageTokens + systemPromptTokens + toolDefinitionTokens
        val pressure = ContextPressureTier.fromUsage(totalEstimatedUsed, usableInput, archetype)

        val protectedTurns = policy.protectedTurns(archetype)

        val turnGroups = messages.toTurnGroups()
        val freezeBoundary = if (pressure == ContextPressureTier.CRITICAL) {
            0 // In emergency, allow compaction across the full range
        } else if (turnGroups.size > protectedTurns) {
            turnGroups[turnGroups.size - protectedTurns].startIndex
        } else {
            (messages.size - (protectedTurns * 2)).coerceAtLeast(0)
        }

        val allowDistillation = pressure == ContextPressureTier.CRITICAL ||
            totalEstimatedUsed >= usableInput ||
            (archetype == ContextScaleArchetype.MICRO && discretionary < 400)

        val receiptifyTools = pressure >= ContextPressureTier.NORMAL ||
            archetype == ContextScaleArchetype.MICRO

        val baseImageLimit = adaptiveImageLimit(model, usableInput)
        val imageLimit = when (pressure) {
            ContextPressureTier.CRITICAL -> minOf(1, baseImageLimit)
            ContextPressureTier.HIGH -> minOf(2, baseImageLimit)
            else -> baseImageLimit
        }

        // Dual-watermark discretionary hysteresis
        val highWatermarkFraction = policy.triggerFraction(when (archetype) {
            ContextScaleArchetype.MICRO -> 0.70f
            ContextScaleArchetype.COMPACT -> 0.75f
            ContextScaleArchetype.VAST -> 0.85f
        })

        val gammaClearance = when (archetype) {
            ContextScaleArchetype.MICRO -> 0.60f
            ContextScaleArchetype.COMPACT -> 0.65f
            ContextScaleArchetype.VAST -> 0.70f
        }
        val lowWatermarkTarget = (discretionary * policy.targetFraction(gammaClearance)).toInt()

        val discretionaryUsed = (totalEstimatedUsed - (systemPromptTokens + toolDefinitionTokens)).coerceAtLeast(0)
        val discretionaryFraction = if (discretionary > 0) {
            (discretionaryUsed.toFloat() / discretionary).coerceIn(0f, 2f)
        } else {
            1f
        }

        val retentionTurnGroups = when (archetype) {
            ContextScaleArchetype.MICRO -> when {
                pressure >= ContextPressureTier.HIGH -> 2
                pressure == ContextPressureTier.MODERATE -> 3
                else -> 4
            }
            ContextScaleArchetype.COMPACT -> when {
                pressure >= ContextPressureTier.HIGH -> 8
                pressure == ContextPressureTier.MODERATE -> 12
                else -> 18
            }
            ContextScaleArchetype.VAST -> when {
                pressure >= ContextPressureTier.HIGH -> 30
                pressure == ContextPressureTier.MODERATE -> 50
                else -> 80
            }
        }

        val overallPressureRatio = totalEstimatedUsed.toFloat() / usableInput
        val shouldAutoSummary = discretionaryFraction >= highWatermarkFraction ||
            pressure == ContextPressureTier.CRITICAL ||
            overallPressureRatio >= 0.92f

        return ContextPlan(
            archetype = archetype,
            pressureTier = pressure,
            totalCapacityTokens = capacity,
            usableInputTokens = usableInput,
            reservedOutputTokens = reservedOutput,
            fixedOverheadTokens = fixedOverhead,
            discretionaryTokens = discretionary,
            freezeBoundaryIndex = freezeBoundary,
            allowToolDistillation = allowDistillation,
            receiptifyHistoricalTools = receiptifyTools,
            protectedRecentTurns = protectedTurns,
            imageLimit = imageLimit,
            discretionaryFraction = discretionaryFraction,
            highWatermarkFraction = highWatermarkFraction,
            lowWatermarkTargetTokens = lowWatermarkTarget,
            retentionTurnGroupCount = retentionTurnGroups,
            shouldTriggerAutoSummary = shouldAutoSummary,
        )
    }

    /**
     * Determines whether auto-summarization should trigger for a conversation.
     * Enforces conjunctive cooldown ($N_{min}$ turns and $K_{min}$ tokens) to prevent
     * churn while bypassing cooldown under emergency pressure ($>= 92\%$).
     */
    fun shouldTriggerSummarization(
        messages: List<UIMessage>,
        model: Model,
        unsummarizedTokens: Int,
        unsummarizedTurnCount: Int,
        plan: ContextPlan,
    ): Boolean {
        val minTurns = ContextTurnEstimator.minimumCooldownTurns(plan.archetype)
        val minTokens = ContextTurnEstimator.minimumCooldownTokens(plan.archetype)

        // Emergency bypass at >= 92% pressure if at least 2 unsummarized turns exist
        if (plan.pressureTier == ContextPressureTier.CRITICAL && unsummarizedTurnCount >= 2) {
            return true
        }

        // Conjunctive cooldown: requires both turn count and token delta
        if (unsummarizedTurnCount < minTurns || unsummarizedTokens < minTokens) {
            return false
        }

        return plan.shouldTriggerAutoSummary
    }

    /**
     * Summarizes the smallest old prefix needed to reach the tail budget. A manual refresh
     * without a token target uses a bounded chunk. All boundaries remain atomic; only a tail
     * that cannot otherwise fit may reduce the recent-turn protection, down to one live turn.
     */
    fun calculateMilestoneSlice(
        turnGroups: List<TurnGroup>,
        startIndex: Int,
        archetype: ContextScaleArchetype,
        pressureTier: ContextPressureTier,
        messages: List<UIMessage> = emptyList(),
        model: Model? = null,
        targetRemainingTokens: Int = 0,
        policy: ContextCompressionPolicy = ContextCompressionPolicy(),
    ): MilestoneSlicePlan? {
        val groups = turnGroups.filter { it.startIndex >= startIndex }
        if (groups.size <= 1 || groups.first().startIndex != startIndex) return null
        if (policy.recentTurns != null && groups.size <= policy.protectedTurns(archetype) &&
            pressureTier != ContextPressureTier.CRITICAL
        ) return null
        val protectedTurns = policy.protectedTurns(archetype).coerceAtMost(groups.size - 1)
        val maxGroups = if (pressureTier == ContextPressureTier.CRITICAL) groups.size - 1
            else groups.size - protectedTurns
        val groupsToSummarize = if (targetRemainingTokens > 0 && model != null && messages.isNotEmpty()) {
            // Count each group once rather than recounting the full tail for every boundary.
            var remaining = ContextTokenEstimator.messagesTokens(groups.flatMap { it.messages }, model)
            var count = 0
            while (remaining > targetRemainingTokens && count < maxGroups) {
                remaining -= groups[count].messages.sumOf { ContextTokenEstimator.messageTokens(it, model) }
                count++
            }
            count
        } else {
            val chunkSize = when (archetype) {
                ContextScaleArchetype.MICRO -> 4
                ContextScaleArchetype.COMPACT -> 8
                ContextScaleArchetype.VAST -> 16
            }
            minOf(chunkSize, groups.size - protectedTurns)
        }
        if (groupsToSummarize == 0) return null
        // Never claim coverage of an unfinished tool exchange.
        val safeCount = groups.take(groupsToSummarize).indexOfFirst { it.hasUnresolvedToolCalls }
            .takeIf { it >= 0 } ?: groupsToSummarize
        if (safeCount == 0) return null
        return MilestoneSlicePlan(startIndex, groups[safeCount - 1].endIndex, safeCount)
    }
}
