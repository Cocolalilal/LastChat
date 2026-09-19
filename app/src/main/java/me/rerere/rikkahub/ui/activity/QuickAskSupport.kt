package me.rerere.rikkahub.ui.activity

import android.content.Intent
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
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
    val userPrompt: String? = null,
    val assistantId: String? = null,
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
    val reply = data.aiResponse?.trim().orEmpty()
    if (reply.isNotBlank()) {
        nodes += MessageNode.of(UIMessage.assistant(reply))
    }
    return withoutBlankAssistantTurns(nodes)
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

