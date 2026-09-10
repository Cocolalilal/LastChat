package me.rerere.ai.context

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.contextCapacityTokens
import me.rerere.ai.ui.UIMessage

/**
 * The execution blueprint produced by the context manager before prompt packing.
 *
 * Guarantees:
 * - Cache prefix stability ([freezeBoundaryIndex]) to prevent KV-cache churn.
 * - Sacred character prompts (system prompts are never truncated).
 * - Tool schema distillation is locked to [allowToolDistillation] (CRITICAL only).
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

        val freezeBoundary = if (pressure == ContextPressureTier.CRITICAL) {
            0 // In emergency, allow compaction across the full range
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
        )
    }
}
