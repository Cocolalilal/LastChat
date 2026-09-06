package me.rerere.ai.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

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

    @Transient
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

/**
 * Merges [messages] into the node list by message id, replacing snapshots inside their existing
 * node and appending genuinely new messages as new nodes. versionTag from the active assistant
 * turn propagates to new snapshots so tool results stay linked to their generation.
 */
fun List<MessageNode>.mergeCurrentVersionMessages(messages: List<UIMessage>): List<MessageNode> {
    val newNodes = this.toMutableList()

    // Get the versionTag from the active turn's last assistant node (if it exists)
    // We only look past the most recent user message to avoid leaking tags from past turns
    val activeVersionTag = this
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
    }

    return newNodes
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
