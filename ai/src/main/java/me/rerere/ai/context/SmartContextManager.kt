package me.rerere.ai.context

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import me.rerere.ai.ui.limitTurnGroups
import me.rerere.ai.ui.toTurnGroups
import me.rerere.ai.util.json
import me.rerere.common.http.jsonPrimitiveOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Creates a structured semantic outcome receipt for an older tool result.
 * Preserves critical execution facts (command, exit code, compiler errors/stderr, file outline, search results)
 * while evaporating multi-kilobyte verbose outputs.
 *
 * Compatible with Zhipu AI / OpenAI / Anthropic schemas by matching the root JsonElement type.
 */
fun createSemanticToolReceipt(part: UIMessagePart.ToolResult): JsonElement {
    val toolName = part.toolName.ifBlank { "tool" }
    val lowerName = toolName.lowercase()
    val rawContent = when (val c = part.content) {
        is JsonPrimitive -> c.content
        else -> c.toString()
    }.trim()

    // Idempotent: don't re-wrap already formatted semantic receipts
    if (rawContent.startsWith("[") && rawContent.endsWith("]")) {
        return part.content
    }

    val argsObj = when (val args = part.arguments) {
        is JsonObject -> args
        is JsonPrimitive -> runCatching { json.parseToJsonElement(args.content) as? JsonObject }.getOrNull()
        else -> null
    }

    fun arg(vararg keys: String): String? {
        if (argsObj == null) return null
        for (key in keys) {
            val v = argsObj[key]?.jsonPrimitiveOrNull?.contentOrNull
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    val receiptText = when {
        // 1. Shell commands / code execution
        lowerName.contains("command") || lowerName.contains("exec") || lowerName.contains("shell") ||
            lowerName.contains("bash") || lowerName.contains("sh") || lowerName.contains("terminal") || lowerName.contains("run") -> {
            val cmd = arg("command", "cmd", "CommandLine", "command_line") ?: "command"
            val exitCode = arg("exit_code", "exitCode", "code") ?: run {
                if (rawContent.contains("exit code: 0", ignoreCase = true) || rawContent.contains("\"exit_code\": 0")) "0" else null
            }
            // Retain full compiler errors / stderr
            val hasError = rawContent.contains("error", ignoreCase = true) ||
                rawContent.contains("exception", ignoreCase = true) ||
                rawContent.contains("failed", ignoreCase = true) ||
                (exitCode != null && exitCode != "0")

            if (hasError) {
                val errorLines = rawContent.lines()
                    .filter { line ->
                        val l = line.lowercase()
                        l.contains("error") || l.contains("exception") || l.contains("fail") ||
                            l.contains("fatal") || l.contains("traceback") || l.startsWith("at ")
                    }
                    .take(8)
                    .joinToString("\n")
                val errorDetails = errorLines.ifBlank { rawContent.take(300) }
                "[Command: $cmd | Exit code: ${exitCode ?: "non-zero"} | Errors:\n$errorDetails]"
            } else {
                val preview = rawContent.take(120).replace("\n", " ").trim()
                "[Command: $cmd | Exit: ${exitCode ?: 0} | Output: $preview]"
            }
        }

        // 2. File reads
        lowerName.contains("read") || lowerName.contains("view") || lowerName.contains("cat") || lowerName.contains("file_content") -> {
            val path = arg("path", "file_path", "AbsolutePath", "TargetFile", "target_file", "file") ?: "file"
            val lineCount = rawContent.lines().size
            val outline = rawContent.lines()
                .filter { line ->
                    val t = line.trim()
                    t.startsWith("fun ") || t.startsWith("class ") || t.startsWith("def ") ||
                        t.startsWith("interface ") || t.startsWith("export ") || t.startsWith("struct ")
                }
                .take(5)
                .joinToString("; ")
            val snippet = if (outline.isNotBlank()) "Outline: $outline" else "Preview: ${rawContent.take(100).replace("\n", " ")}"
            "[Read file: $path ($lineCount lines) | $snippet]"
        }

        // 3. File writes / edits
        lowerName.contains("write") || lowerName.contains("edit") || lowerName.contains("replace") || lowerName.contains("create") -> {
            val path = arg("path", "file_path", "AbsolutePath", "TargetFile", "target_file", "file") ?: "file"
            "[Modified file: $path | Status: completed successfully]"
        }

        // 4. Web search
        lowerName.contains("search") || lowerName.contains("brave") || lowerName.contains("google") || lowerName.contains("tavily") || lowerName.contains("bing") -> {
            val query = arg("query", "q") ?: "search"
            val preview = rawContent.take(160).replace("\n", " ").trim()
            "[Web search: \"$query\" | Key findings: $preview]"
        }

        // 5. Default
        else -> {
            val preview = if (rawContent.length > 96) rawContent.take(93).trimEnd() + "…" else rawContent
            if (preview.isNotBlank() && !preview.startsWith("[")) {
                "[Completed $toolName: $preview]"
            } else {
                "[Older $toolName result compacted; call/result relationship retained]"
            }
        }
    }

    // Zhipu AI Compatibility: serialize as JsonObject if incoming content was JsonObject
    return when (part.content) {
        is JsonObject -> buildJsonObject {
            put("status", "compacted")
            put("receipt", receiptText)
        }
        else -> JsonPrimitive(receiptText)
    }
}

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

    // Keep the active turn atomic using TurnGroup.
    val lastResortTurn = messages.limitTurnGroups(1)
        .compactToTokenBudget(model, budget)
    if (lastResortTurn.isNotEmpty() && ContextTokenEstimator.messagesTokens(lastResortTurn, model) <= budget) {
        return lastResortTurn
    }

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
    val fallback = messages.limitContext(suffixSize)
        .compactToTokenBudget(model, budget)
    return fallback.takeIf { ContextTokenEstimator.messagesTokens(it, model) <= budget }
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
    val turnGroups = messages.toTurnGroups()
    val protectedTurnCount = if (aggressive) 2 else 4
    val protectedStart = if (turnGroups.size > protectedTurnCount) {
        turnGroups[turnGroups.size - protectedTurnCount].startIndex
    } else {
        (messages.size - (if (aggressive) 4 else 6)).coerceAtLeast(0)
    }

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
                            content = if (receiptify) {
                                createSemanticToolReceipt(part)
                            } else {
                                JsonPrimitive("[Older ${part.toolName.ifBlank { "tool" }} result compacted; call/result retained]")
                            },
                            // Arguments are mandatory for agent memory; NEVER wipe them.
                            arguments = part.arguments,
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
