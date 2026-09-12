package me.rerere.ai.context

import kotlinx.serialization.encodeToString
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.baseCapacityTokens
import me.rerere.ai.provider.contextCapacityTokens
import me.rerere.ai.util.json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.ceil

enum class ContextCountConfidence {
    EXACT,
    PROVIDER_COUNTED,
    ESTIMATED,
}

private fun saturatedTokenSum(vararg values: Int): Int = values
    .fold(0L) { total, value -> total + value.coerceAtLeast(0).toLong() }
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()

private fun saturatedTokenSum(values: Iterable<Int>): Int = values
    .fold(0L) { total, value -> total + value.coerceAtLeast(0).toLong() }
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()

data class ContextUsageBreakdown(
    val conversationTokens: Int = 0,
    val systemPromptTokens: Int = 0,
    val summaryTokens: Int = 0,
    val memoryTokens: Int = 0,
    val skillTokens: Int = 0,
    val lorebookTokens: Int = 0,
    val toolDefinitionTokens: Int = 0,
    val toolCallTokens: Int = 0,
    val mediaTokens: Int = 0,
    val usedTokens: Int = saturatedTokenSum(
        conversationTokens,
        systemPromptTokens,
        summaryTokens,
        memoryTokens,
        skillTokens,
        lorebookTokens,
        toolDefinitionTokens,
        toolCallTokens,
        mediaTokens,
    ),
    val totalTokens: Int,
    /** Input capacity after smart response reserve and safety margin. */
    val usableInputTokens: Int = totalTokens,
    val imageCount: Int = 0,
    val maxImages: Int? = null,
    val confidence: ContextCountConfidence = ContextCountConfidence.ESTIMATED,
    val sourceKey: Int? = null,
) {
    /** Capacity intentionally unavailable to prompt content (response reserve, safety, input cap). */
    val reservedTokens: Int get() = (totalTokens - usableInputTokens).coerceAtLeast(0)
    val availableTokens: Int get() = (usableInputTokens - usedTokens).coerceAtLeast(0)
    val remainingTokens: Int get() = availableTokens
    val fractionUsed: Float get() = if (usableInputTokens <= 0) 0f else
        (usedTokens.toFloat() / usableInputTokens).coerceIn(0f, 1f)
    val fractionOfWindowUsed: Float get() = if (totalTokens <= 0) 0f else
        (usedTokens.toFloat() / totalTokens).coerceIn(0f, 1f)
    val fractionOfWindowReserved: Float get() = if (totalTokens <= 0) 0f else
        (reservedTokens.toFloat() / totalTokens).coerceIn(0f, 1f)
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
        confidence: ContextCountConfidence = ContextCountConfidence.PROVIDER_COUNTED,
    ): ContextUsageBreakdown {
        if (promptTokens <= 0 || breakdown.usedTokens <= 0) return breakdown
        val scale = promptTokens.toDouble() / breakdown.usedTokens
        if (
            breakdown.mediaTokens == 0 &&
            breakdown.toolDefinitionTokens == 0 &&
            breakdown.toolCallTokens == 0
        ) {
            model?.let { calibrate(it, scale) }
        }
        fun scaled(value: Int) = (value * scale).toInt().coerceAtLeast(0)
        val conversation = scaled(breakdown.conversationTokens)
        val system = scaled(breakdown.systemPromptTokens)
        val summary = scaled(breakdown.summaryTokens)
        val memory = scaled(breakdown.memoryTokens)
        val skills = scaled(breakdown.skillTokens)
        val lorebook = scaled(breakdown.lorebookTokens)
        val toolDefinitions = scaled(breakdown.toolDefinitionTokens)
        val toolCalls = scaled(breakdown.toolCallTokens)
        val media = scaled(breakdown.mediaTokens)
        val roundingResidual = (promptTokens - saturatedTokenSum(
            conversation,
            system,
            summary,
            memory,
            skills,
            lorebook,
            toolDefinitions,
            toolCalls,
            media,
        )).coerceAtLeast(0)
        return breakdown.copy(
            conversationTokens = conversation + roundingResidual,
            systemPromptTokens = system,
            summaryTokens = summary,
            memoryTokens = memory,
            skillTokens = skills,
            lorebookTokens = lorebook,
            toolDefinitionTokens = toolDefinitions,
            toolCallTokens = toolCalls,
            mediaTokens = media,
            usedTokens = promptTokens,
            confidence = confidence,
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
        var asciiWordCharacters = 0
        var asciiPunctuation = 0
        var whitespace = 0
        var cjkCharacters = 0
        var emojiCharacters = 0
        var otherCharacters = 0
        var index = 0
        while (index < text.length) {
            val first = text[index]
            val codePoint = if (first.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                val high = first.code - 0xD800
                val low = text[index + 1].code - 0xDC00
                index++
                0x10000 + (high shl 10) + low
            } else {
                first.code
            }
            when {
                codePoint <= 0x7F && codePoint.toChar().isLetterOrDigit() -> asciiWordCharacters++
                codePoint <= 0x7F && codePoint.toChar().isWhitespace() -> whitespace++
                codePoint <= 0x7F -> asciiPunctuation++
                codePoint in 0x3400..0x9FFF || codePoint in 0xF900..0xFAFF ||
                    codePoint in 0x3040..0x30FF || codePoint in 0xAC00..0xD7AF -> cjkCharacters++
                codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF -> emojiCharacters++
                else -> otherCharacters++
            }
            index++
        }
        val lexicalEstimate = ceil(asciiWordCharacters / charsPerToken) +
            ceil(asciiPunctuation * 0.55) +
            ceil(whitespace * 0.18) +
            cjkCharacters +
            emojiCharacters * 2.0 +
            ceil(otherCharacters * 0.8)
        val calibration = modelCalibration.load()[modelKey(model)] ?: 1.0
        return (lexicalEstimate * calibration)
            .toInt()
            .coerceAtLeast(1)
    }

    fun partTokens(part: UIMessagePart, model: Model? = null): Int = when (part) {
        is UIMessagePart.Text -> textTokens(part.text, model)
        is UIMessagePart.Thinking -> textTokens(part.thinking, model)
        is UIMessagePart.Reasoning -> textTokens(part.reasoning, model)
        is UIMessagePart.ToolCall -> saturatedTokenSum(
            textTokens(part.toolName, model),
            textTokens(part.arguments, model),
            12,
        )
        is UIMessagePart.ToolResult -> saturatedTokenSum(
            textTokens(part.toolName, model),
            textTokens(part.content.toString(), model),
            12,
        )
        is UIMessagePart.Image -> 1_024
        is UIMessagePart.Video -> 4_096
        is UIMessagePart.Audio -> 2_000
        is UIMessagePart.Document -> 512
        UIMessagePart.Search -> 8
    }

    fun messageTokens(message: UIMessage, model: Model? = null): Int = saturatedTokenSum(
        listOf(4) + message.parts.map { partTokens(it, model) }
    )

    fun messagesTokens(messages: List<UIMessage>, model: Model? = null): Int = saturatedTokenSum(
        messages.map { messageTokens(it, model) } + if (messages.isEmpty()) emptyList() else listOf(3)
    )

    fun toolDefinitionText(
        name: String,
        description: String,
        schema: InputSchema?,
    ): String = buildString {
        appendLine(name)
        append(description)
        schema?.let {
            append('\n')
            append(json.encodeToString(InputSchema.serializer(), it))
        }
    }

    fun toolDefinitionText(tool: Tool): String = toolDefinitionText(
        name = tool.name,
        description = tool.description,
        schema = tool.parameters(),
    )

    fun toolDefinitionTokens(
        name: String,
        description: String,
        schema: InputSchema?,
        model: Model,
    ): Int = saturatedTokenSum(
        ceil(textTokens(toolDefinitionText(name, description, schema), model) * 1.2).toInt(),
        16,
    )

    fun toolDefinitionTokens(tool: Tool, model: Model): Int = toolDefinitionTokens(
        name = tool.name,
        description = tool.description,
        schema = tool.parameters(),
        model = model,
    )

    fun breakdown(
        messages: List<UIMessage>,
        model: Model,
        systemPromptText: String = "",
        summaryText: String = "",
        memoryText: String = "",
        skillText: String = "",
        lorebookText: String = "",
        toolDefinitionText: String = "",
        embeddedToolText: String = "",
        namedContextEmbeddedInMessages: Boolean = false,
        pendingParts: List<UIMessagePart> = emptyList(),
        memoryTokensOverride: Int? = null,
        skillTokensOverride: Int? = null,
        lorebookTokensOverride: Int? = null,
        toolDefinitionTokensOverride: Int? = null,
        providerPromptTokens: Int? = null,
        usableInputTokens: Int? = null,
        sourceKey: Int? = null,
    ): ContextUsageBreakdown {
        var messageText = 0
        var toolCalls = 0
        var media = 0
        var images = 0
        (messages.flatMap { it.parts } + pendingParts).forEach { part ->
            when (part) {
                is UIMessagePart.Image -> {
                    media = saturatedTokenSum(media, partTokens(part, model))
                    images = (images.toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                is UIMessagePart.Video, is UIMessagePart.Audio, is UIMessagePart.Document ->
                    media = saturatedTokenSum(media, partTokens(part, model))
                is UIMessagePart.ToolCall, is UIMessagePart.ToolResult ->
                    toolCalls = saturatedTokenSum(toolCalls, partTokens(part, model))
                else -> messageText = saturatedTokenSum(messageText, partTokens(part, model))
            }
        }
        val framedMessageCount = messages.size.toLong() + if (pendingParts.isEmpty()) 0L else 1L
        val framingTokens = (framedMessageCount * 4L +
            if (messages.isEmpty() && pendingParts.isEmpty()) 0L else 3L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        messageText = saturatedTokenSum(messageText, framingTokens)
        val systemPrompt = textTokens(systemPromptText, model)
        val summary = textTokens(summaryText, model)
        val memories = memoryTokensOverride?.coerceAtLeast(0) ?: textTokens(memoryText, model)
        val skills = skillTokensOverride?.coerceAtLeast(0) ?: textTokens(skillText, model)
        val lorebook = lorebookTokensOverride?.coerceAtLeast(0) ?: textTokens(lorebookText, model)
        val embeddedTools = textTokens(embeddedToolText, model)
        val toolDefinitions = saturatedTokenSum(
            toolDefinitionTokensOverride?.coerceAtLeast(0)
                ?: textTokens(toolDefinitionText, model),
            embeddedTools,
        )
        // Request accounting names text already embedded in built messages; live UI accounting
        // supplies raw conversation messages, so its named context must be added independently.
        val embeddedNamedTokens = if (namedContextEmbeddedInMessages) {
            systemPrompt + summary + memories + skills + lorebook + embeddedTools
        } else {
            0
        }
        val conversation = (messageText - embeddedNamedTokens).coerceAtLeast(0)
        val estimatedTotal = saturatedTokenSum(
            conversation,
            systemPrompt,
            summary,
            memories,
            skills,
            lorebook,
            toolDefinitions,
            toolCalls,
            media,
        )
        val confirmedTotal = providerPromptTokens?.takeIf { it > 0 }
        val scale = if (confirmedTotal != null && estimatedTotal > 0) confirmedTotal.toDouble() / estimatedTotal else 1.0
        fun scaled(value: Int) = (value * scale).toInt().coerceAtLeast(0)
        return ContextUsageBreakdown(
            conversationTokens = scaled(conversation),
            systemPromptTokens = scaled(systemPrompt),
            summaryTokens = scaled(summary),
            memoryTokens = scaled(memories),
            skillTokens = scaled(skills),
            lorebookTokens = scaled(lorebook),
            toolDefinitionTokens = scaled(toolDefinitions),
            toolCallTokens = scaled(toolCalls),
            mediaTokens = scaled(media),
            usedTokens = confirmedTotal ?: estimatedTotal,
            totalTokens = model.contextCapacityTokens ?: 0,
            usableInputTokens = usableInputTokens
                ?.takeIf { it > 0 }
                ?.coerceAtMost(model.contextCapacityTokens ?: Int.MAX_VALUE)
                ?: (model.contextCapacityTokens ?: 0),
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
        listOfNotNull(model?.providerSlug, model?.canonicalModelId ?: model?.modelId)
            .joinToString("|")
            .trim()
            .lowercase()
}

/**
 * Predicts request-only context that cannot be known until generation starts. Recent observed
 * injections take precedence; cold-start estimates are deliberately bounded by the same share of
 * the input window that smart context management can practically give dynamic context.
 */
fun probableTemporaryTokenReserve(
    model: Model,
    requestedOutputTokens: Int?,
    memoryEnabled: Boolean,
    memoryRecallIsConditional: Boolean,
    memoryCandidateLimit: Int,
    memoryCandidateTokens: Int,
    memoryBudgetFraction: Double,
    observedMemoryTokenTotals: List<Int>,
    conditionalContextCandidateTokens: Int,
    observedConditionalTokenTotals: List<Int>,
): Int {
    val inputBudget = smartInputBudget(model, requestedOutputTokens) ?: return 0
    fun probableObserved(values: List<Int>): Int? {
        val sorted = values.filter { it > 0 }.sorted()
        if (sorted.isEmpty()) return null
        // A small upper-quartile sample is steadier than the last request but still preserves peaks.
        return sorted[((sorted.lastIndex * 3) / 4).coerceIn(0, sorted.lastIndex)]
    }

    val memoryCap = (inputBudget * memoryBudgetFraction.coerceIn(0.0, 0.70)).toInt()
    val memoryReserve = if (memoryEnabled && memoryRecallIsConditional && memoryCandidateTokens > 0) {
        probableObserved(observedMemoryTokenTotals)
            ?: minOf(memoryCandidateTokens, memoryCandidateLimit.coerceAtLeast(0) * 96)
    } else {
        0
    }.coerceAtMost(memoryCap)

    val conditionalCap = (inputBudget * 0.10f).toInt()
    val conditionalReserve = (
        probableObserved(observedConditionalTokenTotals)
            ?: (conditionalContextCandidateTokens * 0.35f).toInt()
        ).coerceIn(0, minOf(conditionalContextCandidateTokens, conditionalCap))

    return (memoryReserve + conditionalReserve)
        .coerceAtMost((inputBudget * 0.72f).toInt())
        .coerceAtLeast(0)
}

/**
 * Projects the raw chat history to the portion that can actually be sent before dynamic context is
 * added. This is shared by request assembly and the live meter so summaries and truncation cannot
 * disagree between them.
 */
fun effectiveHistoryForContext(
    messages: List<UIMessage>,
    smartManagement: Boolean,
    summaryUpToIndex: Int,
    truncateIndex: Int,
    manualHistoryLimit: Int? = null,
): List<UIMessage> {
    if (messages.isEmpty()) return messages
    val summaryStart = if (smartManagement && summaryUpToIndex in messages.indices) {
        summaryUpToIndex + 1
    } else {
        0
    }
    val explicitStart = truncateIndex.takeIf { it in messages.indices } ?: 0
    val start = maxOf(summaryStart, explicitStart)
    val retained = if (start >= messages.size) {
        emptyList()
    } else if (start <= 0) {
        messages
    } else {
        // limitContext moves the boundary backwards when necessary to avoid separating a tool
        // result from the call it depends on.
        messages.limitContext(messages.size - start)
    }
    return if (!smartManagement && (manualHistoryLimit ?: 0) > 0) {
        retained.limitContext(manualHistoryLimit ?: retained.size)
    } else {
        retained
    }
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
            if (message.role == me.rerere.ai.core.MessageRole.SYSTEM) return@map message
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

fun smartInputBudget(
    model: Model,
    requestedOutputTokens: Int?,
    customLimitTokens: Int? = null,
): Int? {
    val effectiveWindow = customLimitTokens?.takeIf { it > 0 }
        ?: model.contextCapacityTokens?.takeIf { it > 0 }
        ?: model.contextWindowTokens?.takeIf { it > 0 }
    val independentInputLimit = model.maxInputTokens?.takeIf { it > 0 }
    if (effectiveWindow == null && independentInputLimit == null) return null
    val outputReserve = smartOutputTokenBudget(model, requestedOutputTokens, customLimitTokens = effectiveWindow) ?: return null
    // Covers provider-specific message framing, tokenizer mismatch, and small transformations that
    // occur after context assembly. A visible unused sliver is preferable to a context overflow.
    val availableAfterOutput = effectiveWindow
        ?.let { (it - outputReserve).coerceAtLeast(0) }
        ?: independentInputLimit.orEmptyTokenLimit()
    val rawInputCeiling = listOfNotNull(
        availableAfterOutput,
        independentInputLimit,
    ).minOrNull() ?: return null
    val safetyMargin = (rawInputCeiling / 16)
        .coerceAtLeast(512)
        .coerceAtMost((rawInputCeiling / 4).coerceAtLeast(1))
        .coerceAtMost((rawInputCeiling - 1).coerceAtLeast(0))
    return (rawInputCeiling - safetyMargin).coerceAtLeast(0)
}

/**
 * Calculates the absolute minimum safe floor for a custom context limit slider.
 * Prevents setting impossible limits that starve system prompts, tools, or responses.
 */
fun calculateMinSafeFloorTokens(
    model: Model,
    systemPromptTokens: Int = 0,
    toolDefinitionTokens: Int = 0,
    requestedOutputTokens: Int? = null,
): Int {
    val reserve = smartOutputTokenBudget(model, requestedOutputTokens) ?: 1_024
    val fixed = (systemPromptTokens + toolDefinitionTokens + reserve).coerceAtLeast(0)
    val maxCap = model.baseCapacityTokens ?: model.contextCapacityTokens ?: Int.MAX_VALUE
    return maxOf(1_500, fixed + 512).coerceAtMost(maxCap)
}

/** The response ceiling paired with [smartInputBudget], so input + output use the same contract. */
fun smartOutputTokenBudget(
    model: Model,
    requestedOutputTokens: Int?,
    customLimitTokens: Int? = null,
): Int? {
    val window = customLimitTokens?.takeIf { it > 0 }
        ?: model.contextCapacityTokens?.takeIf { it > 0 }
        ?: model.contextWindowTokens?.takeIf { it > 0 }
    val independentOutputLimit = model.maxOutputTokens?.takeIf { it > 0 }
    val reference = window
        ?: independentOutputLimit
        ?: model.maxInputTokens?.takeIf { it > 0 }
        ?: return null
    val adaptiveReserve = (reference / 10).coerceIn(1_024, 8_192).let { reserve ->
        if (window != null) reserve.coerceAtMost((window / 2).coerceAtLeast(1)) else reserve
    }
    val requested = requestedOutputTokens
        ?.takeIf { it > 0 }
        ?.let { tokens -> if (window != null) tokens.coerceAtMost(window / 2) else tokens }
        ?: adaptiveReserve
    return independentOutputLimit
        ?.let { requested.coerceAtMost(it) }
        ?: requested
}

private fun Int?.orEmptyTokenLimit(): Int = this?.coerceAtLeast(0) ?: 0
