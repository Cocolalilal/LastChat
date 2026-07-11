package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
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
        val newNodes = this.messageNodes.toMutableList()
        
        // Get the versionTag from the active turn's last assistant node (if it exists)
        // We only look past the most recent user message to avoid leaking tags from past turns
        val activeVersionTag = this.messageNodes
            .takeLastWhile { it.role != MessageRole.USER }
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.currentMessage?.versionTag

        var previousNodeIndex = -1
        messages.forEach { message ->
            val existingNodeIndex = newNodes.indexOfFirst { node ->
                node.messages.any { it.id == message.id }
            }
            val isNewGeneratedMessage = existingNodeIndex == -1

            // Propagate versionTag ONLY to new messages that don't have one
            // This ensures tool results and newly spawned assistant nodes inherit the tag
            val messageWithTag = if (isNewGeneratedMessage && activeVersionTag != null && message.versionTag == null) {
                message.copy(versionTag = activeVersionTag)
            } else {
                message
            }
            
            if (existingNodeIndex >= 0) {
                val node = newNodes[existingNodeIndex]
                val messageIndex = node.messages.indexOfFirst { it.id == messageWithTag.id }
                val newMessages = node.messages.toMutableList()
                newMessages[messageIndex] = messageWithTag
                newNodes[existingNodeIndex] = node.copy(
                    messages = newMessages,
                    selectIndex = messageIndex,
                )
                previousNodeIndex = existingNodeIndex
            } else {
                val insertionIndex = (previousNodeIndex + 1).coerceIn(0, newNodes.size)
                newNodes.add(insertionIndex, messageWithTag.toMessageNode())
                previousNodeIndex = insertionIndex
            }

            // 更新newNodes
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

/**
 * Resolves the visible message path without mixing nodes from different assistant-turn versions.
 * Regenerated tool/assistant turns can have different numbers of nodes, so a node that has no
 * snapshot for the active tag must be omitted rather than falling back to another reply.
 */
fun List<MessageNode>.currentVersionMessages(): List<UIMessage> {
    val result = mutableListOf<UIMessage>()
    var index = 0
    while (index < size) {
        val node = this[index]
        if (node.role == MessageRole.USER) {
            node.messages.getOrNull(node.selectIndex)?.let(result::add)
            index++
            continue
        }

        val turnStart = index
        while (index < size && this[index].role != MessageRole.USER) {
            index++
        }
        val turnNodes = subList(turnStart, index)
        val activeTag = turnNodes
            .firstOrNull { it.messages.isNotEmpty() }
            ?.let { turnNode -> turnNode.messages.getOrNull(turnNode.selectIndex)?.versionTag }
        val selected = turnNodes.mapNotNull { turnNode ->
            val selectedIndex = turnNode.selectIndex.takeIf { candidate ->
                turnNode.messages.getOrNull(candidate)?.versionTag == activeTag
            } ?: turnNode.messages.indexOfLast { it.versionTag == activeTag }
            turnNode.messages.getOrNull(selectedIndex)
        }.toMutableList()

        // Older tool-result snapshots were not always tagged. Retain only results that belong to
        // a tool call in the active version; unrelated results from another version stay hidden.
        val activeToolCallIds = selected
            .flatMap { it.getToolCalls() }
            .map { it.toolCallId }
            .toSet()
        if (activeToolCallIds.isNotEmpty()) {
            turnNodes.forEach { turnNode ->
                if (selected.any { selectedMessage -> selectedMessage.id in turnNode.messages.map(UIMessage::id) }) {
                    return@forEach
                }
                turnNode.messages.lastOrNull { candidate ->
                    candidate.getToolResults().any { it.toolCallId in activeToolCallIds }
                }?.let(selected::add)
            }
            selected.sortBy { message ->
                turnNodes.indexOfFirst { turnNode -> turnNode.messages.any { it.id == message.id } }
            }
        }
        result += selected
    }
    return result
}

@Serializable
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    val forceTurnBreakBefore: Boolean = false,
) {
    val currentMessage get() = if (messages.isEmpty() || selectIndex !in messages.indices) {
        if (messages.isNotEmpty()) {
            messages[selectIndex.coerceIn(messages.indices)]
        } else {
            UIMessage(
                role = MessageRole.USER,
                parts = emptyList()
            )
        }
    } else {
        messages[selectIndex]
    }

    val role get() = messages.firstOrNull()?.role ?: MessageRole.USER
    
    @kotlinx.serialization.Transient
    val cachedVersionSelectionIndices: List<Int> by lazy {
        if (messages.isEmpty()) return@lazy emptyList()
        val latestIndexByTag = linkedMapOf<String?, Int>()
        messages.forEachIndexed { index, message ->
            latestIndexByTag[message.versionTag] = index
        }
        latestIndexByTag.values.toList()
    }

    companion object {
        fun of(
            message: UIMessage,
            forceTurnBreakBefore: Boolean = false,
        ) = MessageNode(
            messages = listOf(message),
            selectIndex = 0,
            forceTurnBreakBefore = forceTurnBreakBefore,
        )
    }
}

fun UIMessage.toMessageNode(): MessageNode {
    return MessageNode(
        messages = listOf(this),
        selectIndex = 0
    )
}

/**
 * Returns the canonical snapshot index for each user-visible message version.
 *
 * Multiple snapshots can share the same versionTag while a response streams or gets edited.
 * The selector should treat those as one version and point at the latest snapshot for that tag.
 */
fun MessageNode.versionSelectionIndices(): List<Int> {
    return this.cachedVersionSelectionIndices
}

fun MessageNode.versionSelectionPosition(selectedIndex: Int = selectIndex): Int {
    if (messages.isEmpty()) return -1

    val versionIndices = versionSelectionIndices()
    val selectedTag = messages.getOrNull(selectedIndex)?.versionTag
    val tagPosition = versionIndices.indexOfFirst { index ->
        messages.getOrNull(index)?.versionTag == selectedTag
    }
    if (tagPosition >= 0) {
        return tagPosition
    }

    return versionIndices.indexOf(selectedIndex)
}
