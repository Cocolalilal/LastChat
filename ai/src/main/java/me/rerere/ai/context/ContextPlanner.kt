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
    ): ContextPlan {
        val capacity = model.contextCapacityTokens ?: Int.MAX_VALUE
        val archetype = ContextScaleArchetype.fromCapacity(capacity)
        val usableInput = customBudgetTokens?.coerceAtLeast(1)
            ?: smartInputBudget(model, null)
            ?: capacity
        val reservedOutput = smartOutputTokenBudget(model, null) ?: 0
        val fixedOverhead = systemPromptTokens + toolDefinitionTokens + reservedOutput
        val discretionary = (usableInput - fixedOverhead).coerceAtLeast(0)

        val rawMessageTokens = ContextTokenEstimator.messagesTokens(messages, model)
        val totalEstimatedUsed = rawMessageTokens + systemPromptTokens + toolDefinitionTokens
        val pressure = ContextPressureTier.fromUsage(totalEstimatedUsed, usableInput, archetype)

        val protectedTurns = when (archetype) {
            ContextScaleArchetype.MICRO -> if (pressure == ContextPressureTier.CRITICAL) 1 else 2
            ContextScaleArchetype.COMPACT -> if (pressure >= ContextPressureTier.HIGH) 2 else 4
            ContextScaleArchetype.VAST -> 6
        }

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
        val highWatermarkFraction = when (archetype) {
            ContextScaleArchetype.MICRO -> 0.70f
            ContextScaleArchetype.COMPACT -> 0.75f
            ContextScaleArchetype.VAST -> 0.85f
        }

        val gammaClearance = when (archetype) {
            ContextScaleArchetype.MICRO -> 0.45f
            ContextScaleArchetype.COMPACT -> 0.50f
            ContextScaleArchetype.VAST -> 0.60f
        }
        val lowWatermarkTarget = fixedOverhead + (discretionary * gammaClearance).toInt()

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
            pressure >= ContextPressureTier.HIGH ||
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

        // For VAST models, trigger periodic milestone preservation after sufficient dialogue
        if (plan.archetype == ContextScaleArchetype.VAST && unsummarizedTurnCount >= 20) {
            return true
        }

        return plan.shouldTriggerAutoSummary
    }

    /**
     * Computes the atomic TurnGroup milestone slicing boundary for conversation summarization.
     * Prevents auto-summarization deadlock on VAST and COMPACT models by slicing into standard
     * milestone chunks (e.g. 16 TurnGroups for VAST, 8 for COMPACT) while strictly preserving
     * atomic TurnGroup integrity and keeping the recent dialogue active.
     */
    fun calculateMilestoneSlice(
        turnGroups: List<TurnGroup>,
        startIndex: Int,
        archetype: ContextScaleArchetype,
        pressureTier: ContextPressureTier,
    ): MilestoneSlicePlan? {
        val unsummarizedGroups = turnGroups.filter { it.startIndex >= startIndex }
        if (unsummarizedGroups.size <= 1) return null

        val milestoneChunkSize = when (archetype) {
            ContextScaleArchetype.MICRO -> 4
            ContextScaleArchetype.COMPACT -> 8
            ContextScaleArchetype.VAST -> 16
        }

        val groupsToSummarize = when {
            pressureTier >= ContextPressureTier.HIGH -> {
                val tailToKeep = when {
                    pressureTier == ContextPressureTier.CRITICAL -> 1
                    archetype == ContextScaleArchetype.MICRO -> 2
                    archetype == ContextScaleArchetype.COMPACT -> 4
                    else -> 6
                }.coerceAtMost(unsummarizedGroups.size - 1)
                (unsummarizedGroups.size - tailToKeep).coerceIn(1, unsummarizedGroups.size - 1)
            }
            unsummarizedGroups.size >= milestoneChunkSize + 2 -> {
                milestoneChunkSize
            }
            else -> {
                val tailToKeep = 2.coerceAtMost(unsummarizedGroups.size - 1)
                (unsummarizedGroups.size - tailToKeep).coerceIn(1, unsummarizedGroups.size - 1)
            }
        }

        val targetGroup = unsummarizedGroups[groupsToSummarize - 1]
        return MilestoneSlicePlan(
            startIndex = startIndex,
            lastIndexToSummarize = targetGroup.endIndex,
            groupsSummarized = groupsToSummarize,
        )
    }
}
