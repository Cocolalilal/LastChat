package me.rerere.ai.generation

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.currentVersionMessages
import me.rerere.ai.ui.toMessageNode
import kotlin.uuid.Uuid

fun List<MessageNode>.selectTurnVersion(nodeId: Uuid, selectIndex: Int): List<MessageNode> {
    val nodeIndex = indexOfFirst { it.id == nodeId }
    if (nodeIndex == -1) return this
    val node = this[nodeIndex]
    if (node.messages.isEmpty()) return this
    val clampedSelectIndex = selectIndex.coerceIn(0, node.messages.lastIndex)
    if (node.role == MessageRole.USER) {
        return mapIndexed { index, current ->
            if (index == nodeIndex) current.copy(selectIndex = clampedSelectIndex) else current
        }
    }

    val targetTag = node.messages[clampedSelectIndex].versionTag
    val turnStartIndex = subList(0, nodeIndex + 1).indexOfLast { it.role == MessageRole.USER } + 1
    val turnEndIndex = subList(nodeIndex, size)
        .indexOfFirst { it.role == MessageRole.USER }
        .let { if (it == -1) size else nodeIndex + it }
    val versionDelta = clampedSelectIndex - node.selectIndex

    return mapIndexed { index, current ->
        when {
            index == nodeIndex -> current.copy(selectIndex = clampedSelectIndex)
            index in turnStartIndex until turnEndIndex && current.role != MessageRole.USER -> {
                val matchingIndex = current.messages.indexOfLast { it.versionTag == targetTag }
                val fallbackIndex = if (matchingIndex >= 0) {
                    matchingIndex
                } else if (current.messages.isEmpty()) {
                    current.selectIndex
                } else {
                    (current.selectIndex + versionDelta).coerceIn(0, current.messages.lastIndex)
                }
                current.copy(selectIndex = fallbackIndex)
            }
            else -> current
        }
    }
}

fun List<MessageNode>.mergeRegeneratedTurn(
    turnStartIndex: Int,
    versionTag: String,
    generatedMessages: List<UIMessage>,
): List<MessageNode> {
    if (turnStartIndex !in 0..size) return this
    val nodes = toMutableList()
    generatedMessages.forEachIndexed { offset, generatedMessage ->
        val taggedMessage = if (generatedMessage.versionTag == versionTag) {
            generatedMessage
        } else {
            generatedMessage.copy(versionTag = versionTag)
        }
        val nodeIndex = turnStartIndex + offset
        val currentTurnEnd = nodes
            .subList(turnStartIndex, nodes.size)
            .indexOfFirst { it.role == MessageRole.USER }
            .let { if (it == -1) nodes.size else turnStartIndex + it }

        if (nodeIndex < currentTurnEnd) {
            val node = nodes[nodeIndex]
            val existingIndex = node.messages.indexOfFirst { it.id == taggedMessage.id }
            val updatedMessages = node.messages.toMutableList()
            val selectedIndex = if (existingIndex >= 0) {
                updatedMessages[existingIndex] = taggedMessage
                existingIndex
            } else {
                updatedMessages += taggedMessage
                updatedMessages.lastIndex
            }
            nodes[nodeIndex] = node.copy(messages = updatedMessages, selectIndex = selectedIndex)
        } else {
            nodes.add(nodeIndex, MessageNode.of(taggedMessage))
        }
    }
    return nodes
}

fun List<MessageNode>.seedAssistantRegeneration(
    turnStartIndex: Int,
    placeholder: UIMessage,
): List<MessageNode> {
    val node = getOrNull(turnStartIndex) ?: return this
    val newMessages = node.messages + placeholder
    return toMutableList().apply {
        this[turnStartIndex] = node.copy(
            messages = newMessages,
            selectIndex = newMessages.lastIndex,
        )
    }
}

fun List<MessageNode>.withEditedMessage(
    messageId: Uuid,
    parts: List<UIMessagePart>,
): List<MessageNode> {
    return map { node ->
        val original = node.messages.find { it.id == messageId } ?: return@map node
        node.copy(
            messages = node.messages + UIMessage(
                role = original.role,
                parts = parts,
                versionTag = original.versionTag,
            ),
            selectIndex = node.messages.size,
        )
    }
}

fun List<MessageNode>.forkThroughMessage(
    messageId: Uuid,
    remapNodeId: (Uuid) -> Uuid = { it },
    remapPartUrl: (String) -> String = { it },
): List<MessageNode>? {
    val targetIndex = indexOfFirst { node -> node.messages.any { it.id == messageId } }
    if (targetIndex < 0) return null
    return subList(0, targetIndex + 1).map { node ->
        node.copy(
            id = remapNodeId(node.id),
            messages = node.messages.map { message ->
                message.copy(
                    parts = message.parts.map { part ->
                        when (part) {
                            is UIMessagePart.Image -> part.copy(url = remapPartUrl(part.url))
                            is UIMessagePart.Document -> part.copy(url = remapPartUrl(part.url))
                            is UIMessagePart.Video -> part.copy(url = remapPartUrl(part.url))
                            is UIMessagePart.Audio -> part.copy(url = remapPartUrl(part.url))
                            else -> part
                        }
                    },
                )
            },
        )
    }
}

fun List<MessageNode>.withDeletedMessage(messageId: Uuid): List<MessageNode> {
    val message = flatMap { it.messages }.firstOrNull { it.id == messageId } ?: return this
    if (message.role == MessageRole.USER) {
        val nodeIndex = indexOfFirst { node -> node.messages.any { it.id == messageId } }
        if (nodeIndex == -1) return this
        val node = this[nodeIndex]
        return if (node.messages.size > 1) {
            val remaining = node.messages.filter { it.id != messageId }
            val updatedNode = node.copy(
                messages = remaining,
                selectIndex = if (node.selectIndex >= remaining.size) remaining.lastIndex else node.selectIndex,
            )
            subList(0, nodeIndex) + listOf(updatedNode)
        } else {
            subList(0, nodeIndex)
        }
    }

    val currentMessages = currentVersionMessages()
    val viewIndex = currentMessages.indexOfFirst { it.id == messageId }
    val related = if (viewIndex == -1) {
        emptyList()
    } else {
        buildList {
            for (i in viewIndex - 1 downTo 0) {
                val candidate = currentMessages[i]
                if (candidate.getToolCalls().isNotEmpty() || candidate.getToolResults().isNotEmpty()) {
                    add(candidate)
                } else break
            }
            for (i in viewIndex + 1 until currentMessages.size) {
                val candidate = currentMessages[i]
                if (candidate.getToolCalls().isNotEmpty() || candidate.getToolResults().isNotEmpty()) {
                    add(candidate)
                } else break
            }
        }
    }
    var result = withDeletedNodeMessage(message)
    related.forEach { relatedMessage ->
        val target = result.flatMap { it.messages }.firstOrNull { it.id == relatedMessage.id }
            ?: return@forEach
        result = result.withDeletedNodeMessage(target)
    }
    return result
}

fun List<MessageNode>.applyToolApprovalState(
    toolCallId: String,
    approvalState: ToolApprovalState,
): List<MessageNode> {
    return map { node ->
        val updatedMessages = node.messages.map { message ->
            val updatedParts = message.parts.map { part ->
                if (part is UIMessagePart.ToolCall && part.toolCallId == toolCallId) {
                    part.copy(approvalState = approvalState)
                } else {
                    part
                }
            }
            if (updatedParts == message.parts) message else message.copy(parts = updatedParts)
        }
        if (updatedMessages == node.messages) node else node.copy(messages = updatedMessages)
    }
}

fun List<UIMessage>.dropTrailingBlankAssistant(): List<UIMessage> {
    val last = lastOrNull() ?: return this
    if (last.role != MessageRole.ASSISTANT) return this
    if (last.toText().isNotBlank() || last.getToolCalls().isNotEmpty()) return this
    return dropLast(1)
}

private fun List<MessageNode>.withDeletedNodeMessage(message: UIMessage): List<MessageNode> {
    val nodeIndex = indexOfFirst { node -> node.messages.any { it.id == message.id } }
    if (nodeIndex == -1) return this
    val node = this[nodeIndex]
    val deleteVersionTag = message.versionTag
    val turnStartIndex = subList(0, nodeIndex + 1).indexOfLast { it.role == MessageRole.USER } + 1
    val turnEndIndex = subList(nodeIndex, size)
        .indexOfFirst { it.role == MessageRole.USER }
        .let { if (it == -1) size else nodeIndex + it }

    return if (node.messages.size == 1 && deleteVersionTag == null) {
        filterIndexed { index, _ -> index != nodeIndex }
    } else {
        mapIndexedNotNull { index, messageNode ->
            val canDeleteByVersionTag = deleteVersionTag != null &&
                index in turnStartIndex until turnEndIndex &&
                messageNode.role != MessageRole.USER
            val remaining = messageNode.messages.filter { currentMessage ->
                if (canDeleteByVersionTag && currentMessage.versionTag == deleteVersionTag) {
                    false
                } else {
                    currentMessage.id != message.id
                }
            }
            if (remaining.isEmpty()) {
                null
            } else {
                messageNode.copy(
                    messages = remaining,
                    selectIndex = if (messageNode.selectIndex >= remaining.size) {
                        remaining.lastIndex
                    } else {
                        messageNode.selectIndex
                    },
                )
            }
        }
    }
}

fun UIMessage.toNode(): MessageNode = toMessageNode()
