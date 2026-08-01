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
    val imageLimit = adaptiveImageLimit(model, budget)

    var candidate = limitImages(messages, imageLimit)
    val initialTokens = ContextTokenEstimator.messagesTokens(candidate, model)
    if (initialTokens <= budget) return candidate

    val pressure = initialTokens.toDouble() / budget
    candidate = compactLowValuePayloads(candidate, aggressive = pressure >= 1.25)
    if (ContextTokenEstimator.messagesTokens(candidate, model) <= budget) return candidate

    val preparedSystem = candidate.filter { it.role == MessageRole.SYSTEM }
    val preparedConversation = candidate.filterNot { it.role == MessageRole.SYSTEM }
    val minimumRecentCount = minOf(4, preparedConversation.size).coerceAtLeast(1)
    val fitted = (preparedConversation.size downTo minimumRecentCount)
        .asSequence()
        .map { size -> preparedSystem + preparedConversation.limitContext(size) }
        .firstOrNull { ContextTokenEstimator.messagesTokens(it, model) <= budget }
    if (fitted != null) return fitted

    val minimum = preparedSystem + preparedConversation.limitContext(minimumRecentCount)
    val compacted = minimum.compactToTokenBudget(model, budget)
    if (ContextTokenEstimator.messagesTokens(compacted, model) <= budget) return compacted

    // Preserve the actual latest user request if optional context cannot fit. If even a compacted
    // latest request is mathematically impossible, return no messages and let the provider gate
    // refuse or normalize the request rather than fabricating user intent.
    val lastResort = listOfNotNull(messages.lastOrNull { it.role == MessageRole.USER })
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
    val limitedImages = limitImages(messages, adaptiveImageLimit(model, budget))
    val pressure = ContextTokenEstimator.messagesTokens(limitedImages, model).toDouble() / budget
    return when {
        pressure < 0.72 -> limitedImages
        pressure < 1.0 -> compactLowValuePayloads(limitedImages, aggressive = false)
        else -> compactLowValuePayloads(limitedImages, aggressive = true)
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
                        part.copy(
                            content = JsonPrimitive(
                                "[Older ${part.toolName.ifBlank { "tool" }} result compacted; " +
                                    "the call/result relationship is retained]"
                            ),
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
