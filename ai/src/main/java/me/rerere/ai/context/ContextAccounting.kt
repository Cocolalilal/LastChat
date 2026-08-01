package me.rerere.ai.context

import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.ceil

enum class ContextCountConfidence {
    EXACT,
    PROVIDER_COUNTED,
    ESTIMATED,
}

data class ContextUsageBreakdown(
    val conversationTokens: Int = 0,
    val systemPromptTokens: Int = 0,
    val summaryTokens: Int = 0,
    val memoryTokens: Int = 0,
    val addonTokens: Int = 0,
    val toolDefinitionTokens: Int = 0,
    val toolCallTokens: Int = 0,
    val mediaTokens: Int = 0,
    val usedTokens: Int = conversationTokens + systemPromptTokens + summaryTokens + memoryTokens +
        addonTokens + toolDefinitionTokens + toolCallTokens + mediaTokens,
    val totalTokens: Int,
    val imageCount: Int = 0,
    val maxImages: Int? = null,
    val confidence: ContextCountConfidence = ContextCountConfidence.ESTIMATED,
    val sourceKey: Int? = null,
) {
    val remainingTokens: Int get() = (totalTokens - usedTokens).coerceAtLeast(0)
    val fractionUsed: Float get() = if (totalTokens <= 0) 0f else (usedTokens.toFloat() / totalTokens).coerceIn(0f, 1f)
}

/**
 * Fast, conservative counter for live UI and providers without a public tokenizer endpoint.
 * Provider-reported prompt usage can be supplied to reconcile the total after generation.
 */
@OptIn(ExperimentalAtomicApi::class)
object ContextTokenEstimator {
    private val modelCalibration = AtomicReference<Map<String, Double>>(emptyMap())

    fun reconcileProviderCount(
        breakdown: ContextUsageBreakdown,
        promptTokens: Int,
        model: Model? = null,
    ): ContextUsageBreakdown {
        if (promptTokens <= 0 || breakdown.usedTokens <= 0) return breakdown
        val scale = promptTokens.toDouble() / breakdown.usedTokens
        model?.let { calibrate(it, scale) }
        fun scaled(value: Int) = (value * scale).toInt().coerceAtLeast(0)
        return breakdown.copy(
            conversationTokens = scaled(breakdown.conversationTokens),
            systemPromptTokens = scaled(breakdown.systemPromptTokens),
            summaryTokens = scaled(breakdown.summaryTokens),
            memoryTokens = scaled(breakdown.memoryTokens),
            addonTokens = scaled(breakdown.addonTokens),
            toolDefinitionTokens = scaled(breakdown.toolDefinitionTokens),
            toolCallTokens = scaled(breakdown.toolCallTokens),
            mediaTokens = scaled(breakdown.mediaTokens),
            usedTokens = promptTokens,
            confidence = ContextCountConfidence.PROVIDER_COUNTED,
        )
    }

    fun textTokens(text: String, model: Model? = null): Int {
        if (text.isBlank()) return 0
        val id = model?.canonicalModelId ?: model?.modelId.orEmpty()
        val charsPerToken = when {
            id.contains("gpt", true) || id.contains("o1", true) || id.contains("o3", true) -> 3.7
            id.contains("claude", true) -> 3.5
            id.contains("gemini", true) -> 3.8
            else -> 3.2 // Conservative for unknown and multilingual tokenizers.
        }
        val calibration = modelCalibration.load()[modelKey(model)] ?: 1.0
        return ((ceil(text.length / charsPerToken).toInt() + text.count { it == '\n' } / 2) * calibration)
            .toInt()
            .coerceAtLeast(1)
    }

    fun partTokens(part: UIMessagePart, model: Model? = null): Int = when (part) {
        is UIMessagePart.Text -> textTokens(part.text, model)
        is UIMessagePart.Thinking -> textTokens(part.thinking, model)
        is UIMessagePart.Reasoning -> textTokens(part.reasoning, model)
        is UIMessagePart.ToolCall -> textTokens(part.toolName, model) + textTokens(part.arguments, model) + 12
        is UIMessagePart.ToolResult -> textTokens(part.toolName, model) + textTokens(part.content.toString(), model) +
            textTokens(part.arguments.toString(), model) + 12
        is UIMessagePart.Image -> 1_024
        is UIMessagePart.Video -> 4_096
        is UIMessagePart.Audio -> 2_000
        is UIMessagePart.Document -> 512
        UIMessagePart.Search -> 8
    }

    fun messageTokens(message: UIMessage, model: Model? = null): Int =
        4 + message.parts.sumOf { partTokens(it, model) }

    fun messagesTokens(messages: List<UIMessage>, model: Model? = null): Int =
        messages.sumOf { messageTokens(it, model) } + if (messages.isEmpty()) 0 else 3

    fun breakdown(
        messages: List<UIMessage>,
        model: Model,
        systemPromptText: String = "",
        summaryText: String = "",
        memoryText: String = "",
        addonText: String = "",
        toolDefinitionText: String = "",
        embeddedToolText: String = "",
        namedContextEmbeddedInMessages: Boolean = false,
        pendingParts: List<UIMessagePart> = emptyList(),
        providerPromptTokens: Int? = null,
        sourceKey: Int? = null,
    ): ContextUsageBreakdown {
        var messageText = 0
        var toolCalls = 0
        var media = 0
        var images = 0
        (messages.flatMap { it.parts } + pendingParts).forEach { part ->
            when (part) {
                is UIMessagePart.Image -> { media += partTokens(part, model); images++ }
                is UIMessagePart.Video, is UIMessagePart.Audio, is UIMessagePart.Document -> media += partTokens(part, model)
                is UIMessagePart.ToolCall, is UIMessagePart.ToolResult -> toolCalls += partTokens(part, model)
                else -> messageText += partTokens(part, model)
            }
        }
        messageText += (messages.size + if (pendingParts.isEmpty()) 0 else 1) * 4 +
            if (messages.isEmpty() && pendingParts.isEmpty()) 0 else 3
        val systemPrompt = textTokens(systemPromptText, model)
        val summary = textTokens(summaryText, model)
        val memories = textTokens(memoryText, model)
        val addons = textTokens(addonText, model)
        val embeddedTools = textTokens(embeddedToolText, model)
        val toolDefinitions = textTokens(toolDefinitionText, model) + embeddedTools
        // Request accounting names text already embedded in built messages; live UI accounting
        // supplies raw conversation messages, so its named context must be added independently.
        val embeddedNamedTokens = if (namedContextEmbeddedInMessages) {
            systemPrompt + summary + memories + addons + embeddedTools
        } else {
            0
        }
        val conversation = (messageText - embeddedNamedTokens).coerceAtLeast(0)
        val estimatedTotal = conversation + systemPrompt + summary + memories + addons +
            toolDefinitions + toolCalls + media
        val confirmedTotal = providerPromptTokens?.takeIf { it > 0 }
        val scale = if (confirmedTotal != null && estimatedTotal > 0) confirmedTotal.toDouble() / estimatedTotal else 1.0
        fun scaled(value: Int) = (value * scale).toInt().coerceAtLeast(0)
        return ContextUsageBreakdown(
            conversationTokens = scaled(conversation),
            systemPromptTokens = scaled(systemPrompt),
            summaryTokens = scaled(summary),
            memoryTokens = scaled(memories),
            addonTokens = scaled(addons),
            toolDefinitionTokens = scaled(toolDefinitions),
            toolCallTokens = scaled(toolCalls),
            mediaTokens = scaled(media),
            usedTokens = confirmedTotal ?: estimatedTotal,
            totalTokens = model.contextWindowTokens?.takeIf { it > 0 } ?: 0,
            imageCount = images,
            maxImages = model.maxImagesInContext?.takeIf { it > 0 },
            confidence = if (confirmedTotal != null) ContextCountConfidence.PROVIDER_COUNTED else ContextCountConfidence.ESTIMATED,
            sourceKey = sourceKey,
        )
    }

    private fun calibrate(model: Model, observedScale: Double) {
        val key = modelKey(model)
        if (key.isBlank() || !observedScale.isFinite()) return
        while (true) {
            val current = modelCalibration.load()
            val old = current[key] ?: 1.0
            val nextFactor = (old * (0.75 + observedScale.coerceIn(0.5, 2.5) * 0.25))
                .coerceIn(0.65, 2.5)
            if (modelCalibration.compareAndSet(current, current + (key to nextFactor))) return
        }
    }

    private fun modelKey(model: Model?): String =
        (model?.canonicalModelId ?: model?.modelId).orEmpty().trim().lowercase()
}

fun List<UIMessage>.limitImagesForModel(model: Model): List<UIMessage> {
    val limit = model.maxImagesInContext?.takeIf { it > 0 } ?: return this
    var retained = 0
    return asReversed().map { message ->
        message.copy(parts = message.parts.asReversed().map { part ->
            if (part is UIMessagePart.Image) {
                if (retained < limit) {
                    retained++
                    part
                } else {
                    UIMessagePart.Text("[Earlier image omitted to respect this model's image context limit]")
                }
            } else part
        }.asReversed())
    }.asReversed()
}

/**
 * Last-resort compaction for a retained context slice. It keeps every message (and therefore
 * tool-call/result IDs and ordering) while shrinking verbose payloads until the hard input budget
 * is respected. Normal history selection happens before this and is preferred whenever possible.
 */
fun List<UIMessage>.compactToTokenBudget(model: Model, budget: Int): List<UIMessage> {
    if (budget <= 0) return emptyList()
    var result = this
    if (ContextTokenEstimator.messagesTokens(result, model) <= budget) return result

    // Tool/search payloads and hidden reasoning are the least useful verbatim history.
    result = result.map { message ->
        message.copy(parts = message.parts.map { part ->
            when (part) {
                is UIMessagePart.ToolResult -> part.copy(
                    content = JsonPrimitive("[Older tool result compacted for context]"),
                    arguments = JsonPrimitive("{}"),
                )
                is UIMessagePart.Thinking -> part.copy(thinking = compactText(part.thinking, 160))
                is UIMessagePart.Reasoning -> part.copy(reasoning = compactText(part.reasoning, 160))
                else -> part
            }
        })
    }
    if (ContextTokenEstimator.messagesTokens(result, model) <= budget) return result

    // Progressively reduce textual payloads without removing the latest turn or dependency nodes.
    var characterLimit = 1_024
    while (characterLimit >= 64 && ContextTokenEstimator.messagesTokens(result, model) > budget) {
        val limit = characterLimit
        result = result.map { message ->
            message.copy(parts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Text -> part.copy(text = compactText(part.text, limit))
                    is UIMessagePart.Thinking -> part.copy(thinking = compactText(part.thinking, limit))
                    is UIMessagePart.Reasoning -> part.copy(reasoning = compactText(part.reasoning, limit))
                    is UIMessagePart.ToolCall -> part.copy(
                        toolName = compactText(part.toolName, 96),
                        arguments = if (part.arguments.length > limit) "{}" else part.arguments,
                    )
                    else -> part
                }
            })
        }
        characterLimit /= 2
    }
    if (ContextTokenEstimator.messagesTokens(result, model) <= budget) return result

    // Media has a fixed token cost. Omit oldest media only if retained text still cannot fit.
    val mutable = result.map { it.copy(parts = it.parts.toMutableList()) }.toMutableList()
    outer@ for (messageIndex in mutable.indices) {
        val parts = mutable[messageIndex].parts.toMutableList()
        for (partIndex in parts.indices) {
            if (parts[partIndex] is UIMessagePart.Image || parts[partIndex] is UIMessagePart.Video ||
                parts[partIndex] is UIMessagePart.Audio || parts[partIndex] is UIMessagePart.Document
            ) {
                parts[partIndex] = UIMessagePart.Text("[Earlier media omitted for context]")
                mutable[messageIndex] = mutable[messageIndex].copy(parts = parts)
                if (ContextTokenEstimator.messagesTokens(mutable, model) <= budget) break@outer
            }
        }
    }
    return mutable
}

private fun compactText(value: String, maxCharacters: Int): String {
    if (value.length <= maxCharacters) return value
    val marker = "\n… context compacted …\n"
    val available = (maxCharacters - marker.length).coerceAtLeast(2)
    val prefix = available / 2
    return value.take(prefix) + marker + value.takeLast(available - prefix)
}

fun smartInputBudget(model: Model, requestedOutputTokens: Int?): Int? {
    val window = model.contextWindowTokens?.takeIf { it > 0 } ?: return null
    val adaptiveReserve = (window / 10).coerceIn(1_024, 8_192)
    val outputReserve = requestedOutputTokens?.takeIf { it > 0 }?.coerceAtMost(window / 2) ?: adaptiveReserve
    val safetyMargin = (window / 50).coerceAtLeast(128)
    return (window - outputReserve - safetyMargin).coerceAtLeast(256)
}
