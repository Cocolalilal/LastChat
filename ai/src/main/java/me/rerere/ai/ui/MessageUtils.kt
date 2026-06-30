package me.rerere.ai.ui

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.common.http.jsonPrimitiveOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

// 公共消息抽象, 具体的Provider实现会转换为API接口需要的DTO
@Serializable
data class UIMessage(
    val id: Uuid = Uuid.random(),
    val role: MessageRole,
    val parts: List<UIMessagePart>,
    val annotations: List<UIMessageAnnotation> = emptyList(),
    val createdAt: LocalDateTime = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()),
    val modelId: Uuid? = null,
    val usage: TokenUsage? = null,
    val translation: String? = null,
    val generationDurationMs: Long? = null, // Duration of AI generation in milliseconds
    val usedLorebookEntries: List<UsedLorebookEntry>? = null, // Lorebook entries used in this message
    val usedModes: List<UsedMode>? = null, // Modes used in this message
    val usedMemories: List<UsedMemory>? = null, // Memories used in this message
    val versionTag: String? = null // Links messages from same generation for multi-node turn versioning
) {
    private fun appendChunk(chunk: MessageChunk): UIMessage {
        val choice = chunk.choices.getOrNull(0)
        return choice?.delta?.let { delta ->
            // Handle Parts
            var newParts = delta.parts.fold(parts) { acc, deltaPart ->
                when (deltaPart) {
                    is UIMessagePart.Text -> {
                        val existingTextPart =
                            acc.find { it is UIMessagePart.Text } as? UIMessagePart.Text
                        if (existingTextPart != null) {
                            acc.map { part ->
                                if (part is UIMessagePart.Text) {
                                    val combined = existingTextPart.text + deltaPart.text
                                    UIMessagePart.Text(if (existingTextPart.text.isEmpty()) combined.trimStart() else combined)
                                } else part
                            }
                        } else {
                            acc + deltaPart.copy(text = deltaPart.text.trimStart())
                        }
                    }

                    is UIMessagePart.Image -> {
                        val existingImagePart =
                            acc.find { it is UIMessagePart.Image } as? UIMessagePart.Image
                        if (existingImagePart != null) {
                            acc.map { part ->
                                if (part is UIMessagePart.Image) {
                                    UIMessagePart.Image(
                                        url = existingImagePart.url + deltaPart.url,
                                    )
                                } else part
                            }
                        } else {
                            acc + UIMessagePart.Image(
                                url = "data:image/png;base64,${deltaPart.url}",
                            )
                        }
                    }

                    is UIMessagePart.Reasoning -> {
                        val existingReasoningPart =
                            acc.find { it is UIMessagePart.Reasoning } as? UIMessagePart.Reasoning
                        if (existingReasoningPart != null) {
                            val reasoning = existingReasoningPart.reasoning + deltaPart.reasoning
                            // Prefer: (1) explicit title on delta, (2) title from the new delta text,
                            // (3) latest title found anywhere in the accumulated text, (4) keep old title.
                            val title = deltaPart.title
                                ?: deltaPart.reasoning.extractReasoningSummaryTitle()
                                ?: reasoning.extractLatestReasoningSummaryTitle()
                                ?: existingReasoningPart.title
                            acc.map { part ->
                                if (part is UIMessagePart.Reasoning) {
                                    UIMessagePart.Reasoning(
                                        reasoning = reasoning,
                                        createdAt = existingReasoningPart.createdAt,
                                        finishedAt = null,
                                        title = title,
                                        metadata = deltaPart.metadata ?: existingReasoningPart.metadata,
                                    ).also {
                                        if (deltaPart.metadata != null) {
                                            it.metadata = deltaPart.metadata // 更新metadata
                                            println("更新metadata: ${kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }.encodeToString(deltaPart)}")
                                        }
                                    }
                                } else part
                            }
                        } else {
                            val title = deltaPart.title ?: deltaPart.reasoning.extractReasoningSummaryTitle()
                            acc + deltaPart.copy(title = title)
                        }
                    }

                    is UIMessagePart.ToolCall -> {
                        if (deltaPart.toolCallId.isBlank()) {
                            val lastToolCall =
                                acc.lastOrNull { it is UIMessagePart.ToolCall } as? UIMessagePart.ToolCall
                            if (lastToolCall != null && (lastToolCall == acc.lastOrNull() || !lastToolCall.toolCallId.isBlank())) {
                                if (shouldStartNewBlankIdToolCall(lastToolCall, deltaPart)) {
                                    acc + deltaPart.copy()
                                } else {
                                    acc.map { part ->
                                        if (part == lastToolCall && part is UIMessagePart.ToolCall) {
                                            part.merge(deltaPart)
                                        } else part
                                    }
                                }
                            } else {
                                acc + deltaPart.copy()
                            }
                        } else {
                            // insert or update
                            val existsPart = acc.find {
                                it is UIMessagePart.ToolCall && it.toolCallId == deltaPart.toolCallId
                            } as? UIMessagePart.ToolCall
                            if (existsPart == null) {
                                // insert
                                acc + deltaPart.copy()
                            } else {
                                // update
                                acc.map { part ->
                                    if (part is UIMessagePart.ToolCall && part.toolCallId == deltaPart.toolCallId) {
                                        part.merge(deltaPart)
                                    } else part
                                }
                            }
                        }
                    }

                    else -> {
                        println("delta part append not supported: $deltaPart")
                        acc
                    }
                }
            }
            // Handle Reasoning End
            if (parts.filterIsInstance<UIMessagePart.Reasoning>()
                    .isNotEmpty() && delta.parts.filterIsInstance<UIMessagePart.Reasoning>()
                    .isEmpty()
            ) {
                newParts = newParts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                        part.copy(finishedAt = Clock.System.now())
                    } else part
                }
            }
            // Handle annotations
            val newAnnotations = if (delta.annotations.isEmpty()) {
                annotations
            } else {
                (annotations + delta.annotations).distinct()
            }
            copy(
                parts = newParts,
                annotations = newAnnotations,
            )
        } ?: this
    }

    fun summaryAsText(): String {
        return "[${role.name}]: " + parts.joinToString(separator = "\n") { part ->
            when (part) {
                is UIMessagePart.Text -> part.text
                is UIMessagePart.Thinking -> part.thinking
                is UIMessagePart.Reasoning -> part.reasoning
                else -> ""
            }
        }
    }

    fun toText() = parts.joinToString(separator = "\n") { part ->
        when (part) {
            is UIMessagePart.Text -> part.text


            else -> ""
        }
    }

    /**
     * Extract only text content, excluding reasoning/thinking parts.
     * Use this for background tasks where reasoning output should not be included.
     */
    fun toContentText(): String {
        val text = parts.filterIsInstance<UIMessagePart.Text>()
            .joinToString(separator = "\n") { it.text }
        
        return text.replace(Regex("<think(?:ing)?>([\\s\\S]*?)(?:</think(?:ing)?>|$)", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    fun getToolCalls() = parts.filterIsInstance<UIMessagePart.ToolCall>()

    fun getToolResults() = parts.filterIsInstance<UIMessagePart.ToolResult>()

    fun isValidToUpload() = parts.any { part ->
        when (part) {
            is UIMessagePart.Text -> part.text.isNotBlank()
            is UIMessagePart.Image -> part.url.isNotBlank()
            is UIMessagePart.Video -> part.url.isNotBlank()
            is UIMessagePart.Audio -> part.url.isNotBlank()
            is UIMessagePart.Document -> part.url.isNotBlank()
            is UIMessagePart.Reasoning -> part.reasoning.isNotBlank()
            else -> true
        }
    }

    inline fun <reified P : UIMessagePart> hasPart(): Boolean {
        return parts.any {
            it is P
        }
    }

    operator fun plus(chunk: MessageChunk): UIMessage {
        return this.appendChunk(chunk)
    }



    companion object {
        fun system(prompt: String) = UIMessage(
            role = MessageRole.SYSTEM,
            parts = listOf(UIMessagePart.Text(prompt))
        )

        fun user(prompt: String) = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(prompt))
        )

        fun assistant(prompt: String) = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text(prompt))
        )
    }
}

/**
 * Represents a lorebook entry that was used when generating a message.
 * Stores enough info to display the entry and allow editing.
 */
@Serializable
data class UsedLorebookEntry(
    val lorebookId: String,  // UUID as string for serialization compatibility
    val lorebookName: String,
    val lorebookCover: String? = null,  // Avatar serialized as string or null
    val entryId: String,  // UUID as string
    val entryName: String,
    val entryIndex: Int,  // Position in the lorebook's entry list
    val priority: Int = 0,  // Higher = more priority (for sorting display)
    val activationReason: String? = null // e.g. "Always Active", "Keywords: foo, bar", "RAG (0.85)"
)

/**
 * Represents a mode that was used when generating a message.
 */
@Serializable
data class UsedMode(
    val modeId: String,  // UUID as string for serialization compatibility
    val modeName: String,
    val modeIcon: String? = null,  // Material icon name
    val priority: Int = 0,  // Position in mode list (higher = more priority)
    val activationReason: String? = null  // "Activated by user" or "Default enabled"
)

/**
 * Represents a memory that was used when generating a message.
 */
@Serializable
data class UsedMemory(
    val memoryId: Int,  // Negative IDs for episodic memories
    val memoryContent: String,  // First line/truncated content for display
    val memoryType: Int,  // 0 = CORE, 1 = EPISODIC
    val priority: Int = 0,
    val activationReason: String? = null  // "Contextually relevant", "Always included", "Recent episode boost"
)


/**
 * 处理MessageChunk合并
 *
 * @receiver 已有消息列表
 * @param chunk 消息chunk
 * @param model 模型, 可以不传，如果传了，会把模型id写入到消息，标记是哪个模型输出的消息
 * @return 新消息列表
 */
fun List<UIMessage>.handleMessageChunk(chunk: MessageChunk, model: Model? = null): List<UIMessage> {
    require(this.isNotEmpty()) {
        "messages must not be empty"
    }
    val choice = chunk.choices.getOrNull(0) ?: return this
    val message = choice.delta ?: choice.message ?: return this
    if (this.last().role != message.role) {
        return this + message.copy(modelId = model?.id)
    } else {
        val last = this.last() + chunk
        return this.dropLast(1) + last
    }
}

/**
 * 判断这个消息是否有有任何用户**可输入内容**
 *
 * 例如: 文本，图片, 文档
 */
fun List<UIMessagePart>.isEmptyInputMessage(): Boolean {
    if (this.isEmpty()) return true
    return this.all { message ->
        when (message) {
            is UIMessagePart.Text -> message.text.isBlank()
            is UIMessagePart.Image -> message.url.isBlank()
            is UIMessagePart.Document -> message.url.isBlank()
            is UIMessagePart.Video -> message.url.isBlank()
            is UIMessagePart.Audio -> message.url.isBlank()
            else -> true
        }
    }
}

/**
 * 判断这个消息在UI上是否显示任何内容
 */
fun List<UIMessagePart>.isEmptyUIMessage(): Boolean {
    if (this.isEmpty()) return true
    return this.all { message ->
        when (message) {
            is UIMessagePart.Text -> message.text.isBlank()
            is UIMessagePart.Image -> message.url.isBlank()
            is UIMessagePart.Document -> message.url.isBlank()
            is UIMessagePart.Reasoning -> message.reasoning.isBlank()
            is UIMessagePart.Video -> message.url.isBlank()
            is UIMessagePart.Audio -> message.url.isBlank()
            else -> true
        }
    }
}

fun List<UIMessage>.truncate(index: Int): List<UIMessage> {
    if (index < 0 || index > this.lastIndex) return this
    return this.subList(index, this.size)
}

fun List<UIMessage>.limitContext(size: Int): List<UIMessage> {
    if (size <= 0 || this.size <= size) return this

    val startIndex = this.size - size
    var adjustedStartIndex = startIndex

    // 循环往前查找，直到满足所有依赖条件
    var needsAdjustment = true
    val visitedIndices = mutableSetOf<Int>()

    while (needsAdjustment && adjustedStartIndex > 0) {
        needsAdjustment = false

        // 防止无限循环
        if (adjustedStartIndex in visitedIndices) break
        visitedIndices.add(adjustedStartIndex)

        val currentMessage = this[adjustedStartIndex]

        // 如果当前消息包含tool result，往前查找对应的tool call
        if (currentMessage.getToolResults().isNotEmpty()) {
            for (i in adjustedStartIndex - 1 downTo 0) {
                if (this[i].getToolCalls().isNotEmpty()) {
                    adjustedStartIndex = i
                    needsAdjustment = true
                    break
                }
            }
        }

        // 如果当前消息包含tool call，往前查找对应的用户消息
        if (currentMessage.getToolCalls().isNotEmpty()) {
            for (i in adjustedStartIndex - 1 downTo 0) {
                if (this[i].role == MessageRole.USER) {
                    adjustedStartIndex = i
                    needsAdjustment = true
                    break
                }
            }
        }
    }

    return this.subList(adjustedStartIndex, this.size)
}

@Serializable
sealed class UIMessagePart {
    abstract val priority: Int
    abstract val metadata: JsonObject?

    @Serializable
    data class Text(
        val text: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 0
    }

    @Serializable
    data class Image(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Video(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Audio(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Document(
        val url: String,
        val fileName: String,
        val mime: String = "text/*",
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 1
    }

    @Serializable
    data class Reasoning(
        val reasoning: String,
        val createdAt: Instant = Clock.System.now(),
        val finishedAt: Instant? = Clock.System.now(),
        val title: String? = null,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = -1
    }

    @Serializable
    data class Thinking(
        val thinking: String,
        val createdAt: Instant = Clock.System.now(),
        val finishedAt: Instant? = Clock.System.now(),
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = -1
    }

    @Deprecated("Deprecated")
    @Serializable
    data object Search : UIMessagePart() {
        override val priority: Int = 0
        override var metadata: JsonObject? = null
    }

    @Serializable
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
        val approvalState: ToolApprovalState = ToolApprovalState.Auto,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        fun merge(other: ToolCall): ToolCall {
            return ToolCall(
                toolCallId = toolCallId,
                toolName = mergeToolName(toolName, other.toolName),
                arguments = arguments + other.arguments,
                approvalState = other.approvalState.takeUnless { it == ToolApprovalState.Auto } ?: approvalState,
                metadata = if (other.metadata != null) other.metadata else metadata,
            )
        }

        override val priority: Int = 0
    }

    @Serializable
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val content: JsonElement,
        val arguments: JsonElement,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        override val priority: Int = 0
    }
}

private fun mergeToolName(existing: String, incoming: String): String {
    return when {
        incoming.isBlank() -> existing
        existing.isBlank() -> incoming
        existing == incoming -> existing
        else -> existing + incoming
    }
}

private fun shouldStartNewBlankIdToolCall(
    existing: UIMessagePart.ToolCall,
    incoming: UIMessagePart.ToolCall,
): Boolean {
    return existing.toolCallId.isBlank() &&
        incoming.toolCallId.isBlank() &&
        existing.toolName.isNotBlank() &&
        incoming.toolName.isNotBlank() &&
        existing.arguments.isCompleteJsonElementString() &&
        incoming.arguments.isCompleteJsonElementString()
}

private fun String.isCompleteJsonElementString(): Boolean {
    val input = trim()
    if (input.isBlank()) return false

    val opener = input.first()
    val expectedCloser = when (opener) {
        '{' -> '}'
        '[' -> ']'
        else -> return false
    }

    val stack = ArrayDeque<Char>()
    var inString = false
    var escaping = false

    input.forEachIndexed { index, char ->
        if (escaping) {
            escaping = false
            return@forEachIndexed
        }

        when (char) {
            '\\' -> if (inString) {
                escaping = true
            }

            '"' -> inString = !inString
            else -> {
                if (inString) return@forEachIndexed

                when (char) {
                    '{' -> stack.addLast('}')
                    '[' -> stack.addLast(']')
                    '}', ']' -> {
                        if (stack.isEmpty() || stack.removeLast() != char) return false
                        if (stack.isEmpty()) {
                            return char == expectedCloser && input.substring(index + 1).isBlank()
                        }
                    }
                }
            }
        }
    }

    return false
}

@Serializable
sealed class ToolApprovalState {
    @Serializable
    @SerialName("auto")
    data object Auto : ToolApprovalState()

    @Serializable
    @SerialName("pending")
    data object Pending : ToolApprovalState()

    @Serializable
    @SerialName("approved")
    data object Approved : ToolApprovalState()

    @Serializable
    @SerialName("denied")
    data class Denied(
        val reason: String,
    ) : ToolApprovalState()

    @Serializable
    @SerialName("answered")
    data class Answered(
        val answer: String,
    ) : ToolApprovalState()
}

fun List<UIMessagePart>.toSortedMessageParts(): List<UIMessagePart> {
    return sortedBy { it.priority }
}

fun UIMessage.finishReasoning(): UIMessage {
    return copy(
        parts = parts.map { part ->
            when (part) {
                is UIMessagePart.Reasoning -> {
                    if (part.finishedAt == null) {
                        part.copy(
                            finishedAt = Clock.System.now()
                        )
                    } else {
                        part
                    }
                }

                else -> part
            }
        }
    )
}

@Serializable
sealed class UIMessageAnnotation {
    @Serializable
    @SerialName("url_citation")
    data class UrlCitation(
        val title: String,
        val url: String
    ) : UIMessageAnnotation()

    @Serializable
    @SerialName("ocr_activity")
    data class OcrActivity(
        val source: Source,
        val fileName: String? = null,
        val pageNumbers: List<Int> = emptyList(),
    ) : UIMessageAnnotation() {
        @Serializable
        enum class Source {
            @SerialName("image")
            IMAGE,

            @SerialName("pdf")
            PDF,
        }
    }
}

@Serializable
data class MessageChunk(
    val id: String,
    val model: String,
    val choices: List<UIMessageChoice>,
    val usage: TokenUsage? = null,
)

@Serializable
data class UIMessageChoice(
    val index: Int,
    val delta: UIMessage?,
    val message: UIMessage?,
    val finishReason: String?
)

fun String.extractReasoningSummaryTitle(): String? {
    val firstLine = lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?: return null

    return extractTitleFromLine(firstLine, hasMultipleLines = this.contains('\n') || this.contains('\r'))
}

/**
 * Scans the full accumulated reasoning text and returns the title from the LAST
 * heading/bold line found. This allows the pill to track the current reasoning
 * section as new blocks stream in.
 */
fun String.extractLatestReasoningSummaryTitle(): String? {
    val hasMultipleLines = this.contains('\n') || this.contains('\r')
    // Walk lines in reverse, return the first (i.e. latest) title we find.
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
        .asReversed()
        .firstNotNullOfOrNull { line ->
            extractTitleFromLine(line, hasMultipleLines)
        }
}

private fun extractTitleFromLine(line: String, hasMultipleLines: Boolean): String? {
    val trimmedLine = line.trim()
        .replace(Regex("^(?:[-*+]|\\d+\\.)\\s+"), "")
        .trim()

    val stripped = when {
        trimmedLine.startsWith("**") -> {
            val idx = trimmedLine.indexOf("**", startIndex = 2)
            if (idx >= 0) trimmedLine.substring(2, idx).trim() else null
        }
        trimmedLine.startsWith("__") -> {
            val idx = trimmedLine.indexOf("__", startIndex = 2)
            if (idx >= 0) trimmedLine.substring(2, idx).trim() else null
        }
        trimmedLine.startsWith("#") -> {
            if (hasMultipleLines) trimmedLine.replace(Regex("^#+\\s*"), "") else null
        }
        else -> null
    }?.trim()?.trimEnd(':')?.trim()
    return stripped?.takeIf { it.isNotBlank() }?.take(80)
}

private fun JsonObject?.isReasoningSummary(): Boolean {
    return this?.get("reasoning_kind")?.jsonPrimitiveOrNull?.contentOrNull == "summary"
}
