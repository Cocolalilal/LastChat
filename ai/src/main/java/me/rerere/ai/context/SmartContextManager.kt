package me.rerere.ai.context

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext

/**
 * The final, provider-agnostic context gate. Everything before this function is preference and
 * relevance selection; everything after it is guaranteed to fit the conservative message budget.
 */
fun smartFitContext(
    messages: List<UIMessage>,
    model: Model,
    messageBudgetTokens: Int,
): List<UIMessage> {
    val budget = messageBudgetTokens.coerceAtLeast(1)
    val plan = ContextPlanner.plan(messages, model, customBudgetTokens = budget)
    val imageLimit = plan.imageLimit

    var candidate = limitImages(messages, imageLimit)
    val initialTokens = ContextTokenEstimator.messagesTokens(candidate, model)
    if (initialTokens <= budget) return candidate

    candidate = compactLowValuePayloads(
        candidate,
        aggressive = plan.pressureTier >= ContextPressureTier.HIGH,
        receiptify = plan.receiptifyHistoricalTools,
    )
    if (ContextTokenEstimator.messagesTokens(candidate, model) <= budget) return candidate

    val preparedSystem = candidate.filter { it.role == MessageRole.SYSTEM }
    val preparedConversation = candidate.filterNot { it.role == MessageRole.SYSTEM }
    val minimumRecentCount = minOf(plan.protectedRecentTurns * 2, preparedConversation.size).coerceAtLeast(1)
    val fitted = (preparedConversation.size downTo minimumRecentCount)
        .asSequence()
        .map { size -> preparedSystem + preparedConversation.limitContext(size) }
        .firstOrNull { ContextTokenEstimator.messagesTokens(it, model) <= budget }
    if (fitted != null) return fitted

    val minimum = preparedSystem + preparedConversation.limitContext(minimumRecentCount)
    val compacted = minimum.compactToTokenBudget(model, budget)
    if (ContextTokenEstimator.messagesTokens(compacted, model) <= budget) return compacted

    // Keep the active turn atomic. In a tool loop the newest item may be TOOL, or a synthetic
    // image-only USER message immediately following a tool result; selecting only the last user
    // would silently detach the call/result chain.
    val latest = messages.lastOrNull()
    val suffixSize = if (
        latest?.role == MessageRole.USER &&
        latest.parts.none { it is UIMessagePart.Text && it.text.isNotBlank() } &&
        messages.getOrNull(messages.lastIndex - 1)?.role == MessageRole.TOOL
    ) {
        2
    } else {
        1
    }
    val lastResort = messages.limitContext(suffixSize)
        .compactToTokenBudget(model, budget)
    return lastResort.takeIf { ContextTokenEstimator.messagesTokens(it, model) <= budget }
        ?: emptyList()
}

/** Automatically compacts low-value old payloads before history selection starts dropping turns. */
fun smartPrepareHistory(
    messages: List<UIMessage>,
    model: Model,
    availableBudgetTokens: Int,
): List<UIMessage> {
    if (messages.isEmpty()) return messages
    val budget = availableBudgetTokens.coerceAtLeast(1)
    val plan = ContextPlanner.plan(messages, model, customBudgetTokens = budget)
    val limitedImages = limitImages(messages, plan.imageLimit)
    return when {
        plan.pressureTier < ContextPressureTier.NORMAL -> limitedImages
        plan.pressureTier < ContextPressureTier.HIGH -> compactLowValuePayloads(
            limitedImages,
            aggressive = false,
            receiptify = plan.receiptifyHistoricalTools,
        )
        else -> compactLowValuePayloads(
            limitedImages,
            aggressive = true,
            receiptify = plan.receiptifyHistoricalTools,
        )
    }
}

fun adaptiveImageLimit(model: Model, inputBudgetTokens: Int): Int {
    if (Modality.IMAGE !in model.inputModalities) return 0
    val budgetLimit = when {
        inputBudgetTokens < 12_000 -> 1
        inputBudgetTokens < 32_000 -> 2
        inputBudgetTokens < 64_000 -> 4
        inputBudgetTokens < 128_000 -> 6
        else -> 8
    }
    return model.maxImagesInContext?.takeIf { it > 0 }?.let { minOf(it, budgetLimit) }
        ?: budgetLimit
}

private fun compactLowValuePayloads(
    messages: List<UIMessage>,
    aggressive: Boolean,
    receiptify: Boolean = true,
): List<UIMessage> {
    val protectedStart = (messages.size - if (aggressive) 4 else 6).coerceAtLeast(0)
    val lastToolResultIndex = messages.indexOfLast { message ->
        message.parts.any { it is UIMessagePart.ToolResult }
    }
    return messages.mapIndexed { index, message ->
        if (index >= protectedStart) return@mapIndexed message
        message.copy(parts = message.parts.mapNotNull { part ->
            when (part) {
                is UIMessagePart.ToolResult -> {
                    val isSearch = part.toolName.contains("search", ignoreCase = true)
                    if (aggressive || isSearch || index != lastToolResultIndex) {
                        val toolName = part.toolName.ifBlank { "tool" }
                        val receiptText = if (receiptify) {
                            val rawContent = part.content.toString().trim()
                            val preview = if (rawContent.length > 96) {
                                rawContent.take(93).trimEnd() + "…"
                            } else {
                                rawContent
                            }
                            if (preview.isNotBlank() && !preview.startsWith("[")) {
                                "[Completed $toolName: $preview]"
                            } else {
                                "[Older $toolName result compacted; call/result relationship retained]"
                            }
                        } else {
                            "[Older $toolName result compacted; the call/result relationship is retained]"
                        }
                        part.copy(
                            content = JsonPrimitive(receiptText),
                            arguments = if (aggressive) JsonPrimitive("{}") else part.arguments,
                        )
                    } else part
                }
                is UIMessagePart.Thinking,
                is UIMessagePart.Reasoning -> null
                else -> part
            }
        })
    }
}

private fun limitImages(messages: List<UIMessage>, limit: Int): List<UIMessage> {
    var retained = 0
    return messages.asReversed().map { message ->
        message.copy(parts = message.parts.asReversed().map { part ->
            if (part is UIMessagePart.Image) {
                if (retained < limit) {
                    retained++
                    part
                } else {
                    UIMessagePart.Text("[Earlier image omitted; surrounding text and OCR remain available]")
                }
            } else part
        }.asReversed())
    }.asReversed()
}
