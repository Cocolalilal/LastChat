package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.currentVersionMessages
import me.rerere.ai.ui.mergeCurrentVersionMessages
import me.rerere.rikkahub.utils.InstantSerializer
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * 精简版的会话信息，用于列表显示，不包含消息内容以避免 OOM
 */
data class ConversationSummary(
    val id: Uuid,
    val assistantId: Uuid,
    val title: String,
    val isPinned: Boolean = false,
    val createAt: Instant,
    val updateAt: Instant,
    val isConsolidated: Boolean = false,
    val isFork: Boolean = false,
)

@Serializable
data class Conversation(
    val id: Uuid = Uuid.Companion.random(),
    val assistantId: Uuid,
    val title: String = "",
    val messageNodes: List<MessageNode>,
    val truncateIndex: Int = -1,
    val chatSuggestions: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val enabledModeIds: Set<Uuid> = emptySet(), // Per-chat enabled modes
    val enabledLorebookIds: Set<Uuid>? = null, // Null inherits assistant defaults; non-null is a per-chat override
    @Serializable(with = InstantSerializer::class)
    val createAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updateAt: Instant = Instant.now(),
    val isConsolidated: Boolean = false,
    val contextSummary: String? = null, // Summary of pruned messages
    val contextSummaryUpToIndex: Int = -1, // Messages 0..N were summarized into contextSummary
    val lastPruneTime: Long = 0L, // Timestamp of last auto-prune
    val lastPruneMessageCount: Int = 0, // Messages pruned in last auto-prune
    val lastRefreshTime: Long = 0L, // Timestamp of last manual refresh
    val isFork: Boolean = false,
) {
    val files: List<Uri>
        get() {
            val images = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Image>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            val documents = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Document>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            val videos = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Video>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            val audios = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Audio>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            return images + documents + videos + audios
        }

    /**
     *  当前选中的 message
     */
    val currentMessages
        get(): List<UIMessage> = messageNodes.currentVersionMessages()

    fun getMessageNodeByMessage(message: UIMessage): MessageNode? {
        return getMessageNodeByMessageId(message.id)
            ?: messageNodes.firstOrNull { node -> node.messages.contains(message) }
    }

    fun getMessageNodeByMessageId(messageId: Uuid): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.any { it.id == messageId } }
    }

    fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
        return this.copy(
            messageNodes = messageNodes.mergeCurrentVersionMessages(messages)
        )
    }

    companion object {
        fun ofId(
            id: Uuid,
            assistantId: Uuid = DEFAULT_ASSISTANT_ID,
            messages: List<MessageNode> = emptyList(),
        ) = Conversation(
            id = id,
            assistantId = assistantId,
            messageNodes = messages
        )
    }
}
