package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.InstantSerializer
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
        get(): List<UIMessage> {
            return messageNodes.map { node -> node.messages[node.selectIndex] }
        }

    fun getMessageNodeByMessage(message: UIMessage): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.contains(message) }
    }

    fun getMessageNodeByMessageId(messageId: Uuid): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.any { it.id == messageId } }
    }

    fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
        val newNodes = this.messageNodes.toMutableList()
        
        // Get the versionTag from the last assistant node's current message (if it exists)
        // This is the version tag we'll propagate to new messages during streaming
        // We use lastOrNull instead of firstOrNull to get the most recent assistant message
        // (the one currently being generated)
        val activeVersionTag = this.messageNodes
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.currentMessage?.versionTag

        messages.forEachIndexed { index, message ->
            // Propagate versionTag to new messages that don't have one
            // This ensures tool results and subsequent messages inherit the versionTag
            // from the assistant message they're associated with
            val messageWithTag = if (activeVersionTag != null && message.versionTag == null) {
                message.copy(versionTag = activeVersionTag)
            } else {
                message
            }
            
            val node = newNodes
                .getOrElse(index) { messageWithTag.toMessageNode() }

            val newMessages = node.messages.toMutableList()
            var newMessageIndex = node.selectIndex
            if (newMessages.any { it.id == messageWithTag.id }) {
                newMessages[newMessages.indexOfFirst { it.id == messageWithTag.id }] = messageWithTag
            } else {
                newMessages.add(messageWithTag)
                newMessageIndex = newMessages.lastIndex
            }

            val newNode = node.copy(
                messages = newMessages,
                selectIndex = newMessageIndex
            )

            // 更新newNodes
            if (index > newNodes.lastIndex) {
                newNodes.add(newNode)
            } else {
                newNodes[index] = newNode
            }
        }

        return this.copy(
            messageNodes = newNodes
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

@Serializable
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
) {
    val currentMessage get() = if (messages.isEmpty() || selectIndex !in messages.indices) {
        throw IllegalStateException("MessageNode has no valid current message: messages.size=${messages.size}, selectIndex=$selectIndex")
    } else {
        messages[selectIndex]
    }

    val role get() = messages.firstOrNull()?.role ?: MessageRole.USER

    companion object {
        fun of(message: UIMessage) = MessageNode(
            messages = listOf(message),
            selectIndex = 0
        )
    }
}

fun UIMessage.toMessageNode(): MessageNode {
    return MessageNode(
        messages = listOf(this),
        selectIndex = 0
    )
}
