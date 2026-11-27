package me.rerere.rikkahub.ui.activity

import android.content.Intent
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.TextSelectionAction
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

internal const val EXTRA_QUICK_ASK_CONTINUATION =
    "me.rerere.rikkahub.extra.QUICK_ASK_CONTINUATION"

@Serializable
internal data class QuickAskAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String? = null,
)

@Serializable
internal data class QuickAskInputData(
    val text: String = "",
    val attachments: List<QuickAskAttachment> = emptyList(),
) {
    fun hasContent(): Boolean {
        return text.isNotBlank() || attachments.isNotEmpty()
    }
}

@Serializable
internal data class QuickAskContinuationData(
    val text: String = "",
    val attachments: List<QuickAskAttachment> = emptyList(),
    val aiResponse: String? = null,
    val reasoningText: String? = null,
    val reasoningDurationMs: Long? = null,
    val userPrompt: String? = null,
    val assistantId: String? = null,
    val actionName: String? = null,
)

internal fun buildQuickAskTextContent(
    text: String,
    customPrompt: String? = null,
): String {
    val trimmedText = text.trim()
    val trimmedPrompt = customPrompt?.trim().takeIf { !it.isNullOrBlank() }
    return when {
        trimmedText.isNotBlank() && trimmedPrompt != null -> {
            "$trimmedText\n\nQuestion: $trimmedPrompt"
        }

        trimmedText.isNotBlank() -> trimmedText
        trimmedPrompt != null -> "Question: $trimmedPrompt"
        else -> ""
    }
}

internal fun buildQuickAskMessageParts(
    text: String,
    attachments: List<QuickAskAttachment>,
    customPrompt: String? = null,
): List<UIMessagePart> {
    val parts = mutableListOf<UIMessagePart>()
    val mergedText = buildQuickAskTextContent(text, customPrompt)
    if (mergedText.isNotBlank()) {
        parts += UIMessagePart.Text(mergedText)
    }
    attachments.forEach { attachment ->
        val mimeType = attachment.mimeType.orEmpty()
        parts += when {
            mimeType.startsWith("image/") -> UIMessagePart.Image(url = attachment.uri)
            mimeType.startsWith("video/") -> UIMessagePart.Video(url = attachment.uri)
            mimeType.startsWith("audio/") -> UIMessagePart.Audio(url = attachment.uri)
            else -> UIMessagePart.Document(
                url = attachment.uri,
                fileName = attachment.fileName,
                mime = attachment.mimeType ?: "application/octet-stream"
            )
        }
    }
    return parts
}

internal fun buildQuickAskPresetSystemPrompt(
    action: TextSelectionAction,
    customPrompt: String,
    assistantPrompt: String,
    translateLanguage: String,
    inheritAssistantSystemPrompt: Boolean,
): String {
    var processedPrompt = action.prompt
        .replace("{{language}}", translateLanguage)
        .replace("{{custom_prompt}}", customPrompt)
        .trim()

    if (action.isCustomPrompt && customPrompt.isNotBlank() && !action.prompt.contains("{{custom_prompt}}")) {
        processedPrompt = if (processedPrompt.isNotBlank()) {
            "$processedPrompt\n\nUser's question: $customPrompt"
        } else {
            "Answer the user's question about the provided text.\nUser's question: $customPrompt"
        }
    }

    val effectiveActionPrompt = processedPrompt.ifBlank {
        when (action.id) {
            "translate" -> """
                You are a translator. Translate the user's text to $translateLanguage.
                Only output the translation, nothing else. Do not include any explanations or notes.
            """.trimIndent()

            "explain" -> """
                Explain the following text in simple, easy-to-understand terms.
                Be concise but thorough. Use examples if helpful.
            """.trimIndent()

            "summarize" -> """
                Provide a clear, concise summary of the following text.
                Capture the key points and main ideas. Be brief but complete.
            """.trimIndent()

            else -> if (action.isCustomPrompt && customPrompt.isNotBlank()) {
                "Answer the user's question about the provided text.\nUser's question: $customPrompt"
            } else {
                "You are a helpful assistant."
            }
        }
    }

    val shouldPrependAssistantPrompt =
        inheritAssistantSystemPrompt &&
            assistantPrompt.isNotBlank() &&
            action.id != "translate"

    return if (shouldPrependAssistantPrompt) {
        """
            ${assistantPrompt.trim()}

            $effectiveActionPrompt
        """.trimIndent()
    } else {
        effectiveActionPrompt
    }
}

internal fun buildQuickAskConversationTitle(
    actionName: String?,
    inputText: String,
    customPrompt: String? = null,
): String {
    val cleanAction = actionName?.trim().orEmpty().removeSuffix("…").trim()
    val rawSnippet = customPrompt?.trim().takeIf { !it.isNullOrBlank() }
        ?: inputText.trim()
    val normalizedSnippet = rawSnippet.replace(Regex("\\s+"), " ").take(36).trim()
    return when {
        cleanAction.isNotBlank() && normalizedSnippet.isNotBlank() -> "$cleanAction: $normalizedSnippet"
        normalizedSnippet.isNotBlank() -> normalizedSnippet
        cleanAction.isNotBlank() -> cleanAction
        else -> "Quick Ask"
    }
}

internal fun Intent.putQuickAskContinuationData(data: QuickAskContinuationData) {
    putExtra(EXTRA_QUICK_ASK_CONTINUATION, JsonInstant.encodeToString(data))
}

internal fun Intent.readQuickAskContinuationData(): QuickAskContinuationData? {
    val encoded = getStringExtra(EXTRA_QUICK_ASK_CONTINUATION) ?: return null
    return runCatching {
        JsonInstant.decodeFromString<QuickAskContinuationData>(encoded)
    }.getOrNull()
}

internal data class QuickAskChatSeed(
    val messageNodes: List<MessageNode>,
    val reuseConversationId: Uuid? = null,
)

internal fun UIMessage.hasDisplayableChatContent(): Boolean {
    if (toContentText().isNotBlank()) return true
    if (parts.any {
            (it is UIMessagePart.Reasoning && it.reasoning.isNotBlank()) ||
                (it is UIMessagePart.Thinking && it.thinking.isNotBlank())
        }
    ) return true
    return parts.any { part ->
        when (part) {
            is UIMessagePart.Image,
            is UIMessagePart.Video,
            is UIMessagePart.Audio,
            is UIMessagePart.Document -> true
            else -> false
        }
    }
}

internal fun MessageNode.hasDisplayableChatContent(): Boolean {
    return messages.any { it.hasDisplayableChatContent() }
}

internal fun withoutBlankAssistantTurns(nodes: List<MessageNode>): List<MessageNode> {
    return nodes.mapNotNull { node ->
        if (node.role != MessageRole.ASSISTANT) return@mapNotNull node
        val messages = node.messages.filter { it.hasDisplayableChatContent() }
        if (messages.isEmpty()) return@mapNotNull null
        val selectedId = node.messages.getOrNull(node.selectIndex)?.id
        val selectedIndex = selectedId
            ?.let { id -> messages.indexOfFirst { message -> message.id == id } }
            ?.takeIf { it >= 0 }
            ?: node.selectIndex.coerceIn(0, messages.lastIndex)
        node.copy(messages = messages, selectIndex = selectedIndex)
    }
}

internal fun isBlankAssistantDraft(conversation: Conversation): Boolean {
    val messages = conversation.currentMessages
    if (messages.isEmpty()) return true
    return messages.all { message ->
        message.role == MessageRole.ASSISTANT && !message.hasDisplayableChatContent()
    }
}

internal fun buildQuickAskContinuationNodes(data: QuickAskContinuationData): List<MessageNode> {
    val nodes = mutableListOf<MessageNode>()
    val userParts = buildQuickAskMessageParts(
        text = data.text,
        attachments = data.attachments,
        customPrompt = data.userPrompt,
    )
    if (userParts.isNotEmpty()) {
        nodes += MessageNode.of(
            UIMessage(
                role = MessageRole.USER,
                parts = userParts,
            )
        )
    }
    val reasoning = data.reasoningText?.trim().orEmpty()
    val reply = data.aiResponse?.trim().orEmpty()
    if (reasoning.isNotBlank() || reply.isNotBlank()) {
        val finishedAt = Clock.System.now()
        val startedAt = data.reasoningDurationMs
            ?.takeIf { it > 0L }
            ?.let { finishedAt - it.milliseconds }
            ?: finishedAt
        val assistantParts = buildList {
            if (reasoning.isNotBlank()) {
                add(
                    UIMessagePart.Reasoning(
                        reasoning = reasoning,
                        createdAt = startedAt,
                        finishedAt = finishedAt,
                    )
                )
            }
            if (reply.isNotBlank()) {
                add(UIMessagePart.Text(reply))
            }
        }
        nodes += MessageNode.of(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = assistantParts,
            )
        )
    }
    return withoutBlankAssistantTurns(nodes)
}

internal fun buildQuickAskPersistedConversation(
    conversationId: Uuid,
    assistantId: Uuid,
    data: QuickAskContinuationData,
): Conversation? {
    val nodes = buildQuickAskContinuationNodes(data)
    if (nodes.isEmpty()) return null
    val title = buildQuickAskConversationTitle(
        actionName = data.actionName,
        inputText = data.text,
        customPrompt = data.userPrompt,
    )
    return Conversation.ofId(
        id = conversationId,
        assistantId = assistantId,
        messages = nodes,
    ).copy(title = title)
}

internal fun seedQuickAskChat(
    data: QuickAskContinuationData,
    existingConversation: Conversation?,
    introNodes: List<MessageNode>,
): QuickAskChatSeed {
    val continuation = buildQuickAskContinuationNodes(data)
    val cleanIntros = withoutBlankAssistantTurns(introNodes)
    val existing = existingConversation

    if (continuation.isNotEmpty()) {
        if (existing != null && !isBlankAssistantDraft(existing)) {
            return QuickAskChatSeed(
                messageNodes = withoutBlankAssistantTurns(existing.messageNodes) + continuation,
                reuseConversationId = existing.id,
            )
        }
        return QuickAskChatSeed(
            messageNodes = continuation,
            reuseConversationId = existing?.id?.takeIf { isBlankAssistantDraft(existing) },
        )
    }

    if (existing != null && !isBlankAssistantDraft(existing)) {
        return QuickAskChatSeed(
            messageNodes = withoutBlankAssistantTurns(existing.messageNodes),
            reuseConversationId = existing.id,
        )
    }
    return QuickAskChatSeed(
        messageNodes = cleanIntros,
        reuseConversationId = existing?.id?.takeIf { isBlankAssistantDraft(existing) },
    )
}
