package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole

/**
 * Represents an atomic conversational turn group.
 *
 * A TurnGroup encapsulates a complete conversational transaction, typically initiated by a
 * USER message, including all intermediate assistant tool calls, tool results, and the final
 * assistant response. Slicing boundaries (for summarization, truncation, and history limits)
 * may only sit between TurnGroups, never inside them.
 */
data class TurnGroup(
    val messages: List<UIMessage>,
    val startIndex: Int,
    val endIndex: Int,
) {
    init {
        require(messages.isNotEmpty()) { "TurnGroup messages cannot be empty" }
        require(endIndex >= startIndex) { "endIndex ($endIndex) must be >= startIndex ($startIndex)" }
        require(endIndex - startIndex + 1 == messages.size) {
            "Index range size (${endIndex - startIndex + 1}) must match messages count (${messages.size})"
        }
    }

    val initiatingMessage: UIMessage get() = messages.first()
    val role: MessageRole get() = initiatingMessage.role
    val isUserTurn: Boolean get() = role == MessageRole.USER
    val toolCalls: List<UIMessagePart.ToolCall> get() = messages.flatMap { it.getToolCalls() }
    val toolResults: List<UIMessagePart.ToolResult> get() = messages.flatMap { it.getToolResults() }
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
    val hasUnresolvedToolCalls: Boolean get() {
        val calls = toolCalls
        if (calls.isEmpty()) return false
        val callIds = calls.map { it.toolCallId }.filter { it.isNotBlank() }.toSet()
        val resultIds = toolResults.map { it.toolCallId }.filter { it.isNotBlank() }.toSet()
        return !resultIds.containsAll(callIds)
    }
    val hasPendingToolCall: Boolean get() = hasUnresolvedToolCalls
}

/**
 * Groups a flat list of messages into atomic TurnGroups.
 *
 * Slicing boundaries must only sit between TurnGroups. Intermediate tool calls and tool
 * results are guaranteed to remain together in the same TurnGroup.
 */
fun List<UIMessage>.toTurnGroups(): List<TurnGroup> {
    if (isEmpty()) return emptyList()

    val groups = mutableListOf<TurnGroup>()
    var currentGroupMessages = mutableListOf<UIMessage>()
    var currentStartIndex = 0

    fun flushGroup(endIndex: Int) {
        if (currentGroupMessages.isNotEmpty()) {
            groups.add(
                TurnGroup(
                    messages = currentGroupMessages.toList(),
                    startIndex = currentStartIndex,
                    endIndex = endIndex,
                )
            )
            currentGroupMessages = mutableListOf()
        }
    }

    for (index in indices) {
        val msg = this[index]
        val startsNewGroup = when {
            index == 0 -> false
            msg.role == MessageRole.USER -> true
            msg.role == MessageRole.SYSTEM -> true
            // If previous group started with SYSTEM and now dialogue begins (ASSISTANT):
            currentGroupMessages.isNotEmpty() && currentGroupMessages.first().role == MessageRole.SYSTEM -> true
            else -> false
        }

        if (startsNewGroup) {
            flushGroup(index - 1)
            currentStartIndex = index
        }
        currentGroupMessages.add(msg)
    }
    flushGroup(lastIndex)
    return groups
}

/**
 * Retains the latest [keepCount] TurnGroups from this message list.
 */
fun List<UIMessage>.limitTurnGroups(keepCount: Int): List<UIMessage> {
    if (keepCount <= 0) return emptyList()
    val groups = toTurnGroups()
    if (groups.size <= keepCount) return this
    val targetGroups = groups.takeLast(keepCount)
    val startIndex = targetGroups.first().startIndex
    return subList(startIndex, size)
}

/**
 * Snaps a candidate message index to the nearest TurnGroup boundary.
 *
 * @param candidateIndex The target index in the flat message list.
 * @param preferEarlier If true, snaps to the start of the containing TurnGroup (retaining more).
 *                      If false, snaps to the start of the next TurnGroup (retaining less).
 * @return A safe message index sitting strictly on a TurnGroup boundary.
 */
fun List<UIMessage>.snapToTurnGroupBoundary(candidateIndex: Int, preferEarlier: Boolean = true): Int {
    if (isEmpty() || candidateIndex <= 0) return 0
    if (candidateIndex >= size) return size

    val groups = toTurnGroups()
    val containingGroup = groups.firstOrNull { candidateIndex in it.startIndex..it.endIndex }
        ?: return candidateIndex.coerceIn(0, size)

    return if (candidateIndex == containingGroup.startIndex) {
        candidateIndex
    } else if (preferEarlier) {
        containingGroup.startIndex
    } else {
        (containingGroup.endIndex + 1).coerceAtMost(size)
    }
}
