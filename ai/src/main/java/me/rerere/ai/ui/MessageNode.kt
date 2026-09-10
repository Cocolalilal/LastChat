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
    if (messages.isEmpty()) return this

    // Fast path: during streaming generation, messages are appended to or updated in the trailing node,
    // while all preceding nodes are unchanged. This avoids allocating HashMaps and iterating all historical
    // turns on every single token chunk (which happens 50-100 times/sec in 200k-token chats).
    if (isNotEmpty()) {
        if (size == messages.size) {
            val lastNode = last()
            val lastMessage = messages.last()
            if (lastNode.currentMessage.id == lastMessage.id) {
                var prefixMatches = true
                for (i in 0 until size - 1) {
                    val nodeMsg = this[i].currentMessage
                    val incomingMsg = messages[i]
                    if (nodeMsg !== incomingMsg && nodeMsg != incomingMsg) {
                        prefixMatches = false
                        break
                    }
                }
                if (prefixMatches) {
                    val messageIndex = lastNode.messages.indexOfFirst { it.id == lastMessage.id }
                    val existingMessage = if (messageIndex >= 0) lastNode.messages[messageIndex] else null
                    if (existingMessage == lastMessage && lastNode.selectIndex == messageIndex) {
                        return this
                    }
                    val newMessages = lastNode.messages.toMutableList()
                    if (messageIndex >= 0) {
                        newMessages[messageIndex] = lastMessage
                    } else {
                        newMessages.add(lastMessage)
                    }
                    val updatedSelectIndex = if (messageIndex >= 0) messageIndex else newMessages.lastIndex
                    val updatedLastNode = lastNode.copy(
                        messages = newMessages,
                        selectIndex = updatedSelectIndex,
                    )
                    val newNodes = this.toMutableList()
                    newNodes[newNodes.lastIndex] = updatedLastNode
                    return newNodes
                }
            }
        } else if (size + 1 == messages.size) {
            var prefixMatches = true
            for (i in 0 until size) {
                val nodeMsg = this[i].currentMessage
                val incomingMsg = messages[i]
                if (nodeMsg !== incomingMsg && nodeMsg != incomingMsg) {
                    prefixMatches = false
                    break
                }
            }
            if (prefixMatches) {
                val activeVersionTag = this
                    .takeLastWhile { it.role != MessageRole.USER }
                    .lastOrNull { it.role == MessageRole.ASSISTANT }
                    ?.currentMessage?.versionTag
                val lastIncoming = messages.last()
                val messageWithTag = if (activeVersionTag != null && lastIncoming.versionTag == null) {
                    lastIncoming.copy(versionTag = activeVersionTag)
                } else {
                    lastIncoming
                }
                val newNodes = this.toMutableList()
                newNodes.add(messageWithTag.toMessageNode())
                return newNodes
            }
        }
    }

    val activeVersionTag = this
        .takeLastWhile { it.role != MessageRole.USER }
        .lastOrNull { it.role == MessageRole.ASSISTANT }
        ?.currentMessage?.versionTag

    // Map each message ID to its node index for O(1) lookup
    val messageIdToNodeIndex = HashMap<Uuid, Int>(this.size * 2)
    for (nodeIndex in indices) {
        val node = this[nodeIndex]
        for (msg in node.messages) {
            messageIdToNodeIndex[msg.id] = nodeIndex
        }
    }

    var newNodes: MutableList<MessageNode>? = null
    var hasChanges = false
    var previousNodeIndex = -1

    messages.forEach { message ->
        val existingNodeIndex = messageIdToNodeIndex[message.id] ?: -1
        val isNewGeneratedMessage = existingNodeIndex == -1

        // Propagate versionTag ONLY to new messages that don't have one
        // This ensures tool results and newly spawned assistant nodes inherit the tag
        val messageWithTag = if (isNewGeneratedMessage && activeVersionTag != null && message.versionTag == null) {
            message.copy(versionTag = activeVersionTag)
        } else {
            message
        }

        if (existingNodeIndex >= 0) {
            val currentNodes = newNodes ?: this
            val node = currentNodes[existingNodeIndex]
            val messageIndex = node.messages.indexOfFirst { it.id == messageWithTag.id }
            val existingMessage = if (messageIndex >= 0) node.messages[messageIndex] else null
            if (existingMessage != messageWithTag || node.selectIndex != messageIndex) {
                if (newNodes == null) {
                    newNodes = this.toMutableList()
                }
                val newMessages = node.messages.toMutableList()
                if (messageIndex >= 0) {
                    newMessages[messageIndex] = messageWithTag
                } else {
                    newMessages.add(messageWithTag)
                }
                val updatedSelectIndex = if (messageIndex >= 0) messageIndex else newMessages.lastIndex
                newNodes[existingNodeIndex] = node.copy(
                    messages = newMessages,
                    selectIndex = updatedSelectIndex,
                )
                messageIdToNodeIndex[messageWithTag.id] = existingNodeIndex
                hasChanges = true
            }
            previousNodeIndex = existingNodeIndex
        } else {
            if (newNodes == null) {
                newNodes = this.toMutableList()
            }
            val insertionIndex = (previousNodeIndex + 1).coerceIn(0, newNodes.size)
            val newNode = messageWithTag.toMessageNode()
            newNodes.add(insertionIndex, newNode)
            // Shift indices for nodes at or after insertionIndex
            for (entry in messageIdToNodeIndex.entries) {
                if (entry.value >= insertionIndex) {
                    entry.setValue(entry.value + 1)
                }
            }
            messageIdToNodeIndex[newNode.currentMessage.id] = insertionIndex
            for (msg in newNode.messages) {
                messageIdToNodeIndex[msg.id] = insertionIndex
            }
            previousNodeIndex = insertionIndex
            hasChanges = true
        }
    }

    return if (hasChanges && newNodes != null) newNodes else this
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
