package me.rerere.rikkahub.data.ai

import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.ContextPriority

internal data class MemoryContextSelection(
    val memories: List<AssistantMemory>,
    val promptText: String,
    val promptTokens: Int,
    val allocationTokens: Int,
)

/**
 * Allocates deterministic/retrieved memories after protecting the active turn. It lets memories
 * consume otherwise unused history capacity, while context priority controls contention when both
 * history and memory want the same space.
 */
internal fun selectSmartMemoryContext(
    candidates: List<AssistantMemory>,
    model: Model,
    inputBudgetTokens: Int,
    requiredContextTokens: Int,
    historyMessages: List<UIMessage>,
    contextPriority: ContextPriority,
    episodeGroup: (Long) -> String,
): MemoryContextSelection {
    if (candidates.isEmpty()) return MemoryContextSelection(emptyList(), "", 0, 0)

    val remaining = (inputBudgetTokens - requiredContextTokens).coerceAtLeast(0)
    val protectedHistory = historyMessages.limitContext(minOf(4, historyMessages.size))
    val protectedHistoryTokens = ContextTokenEstimator.messagesTokens(protectedHistory, model)
    val completeHistoryTokens = ContextTokenEstimator.messagesTokens(historyMessages, model)
    val discretionaryPool = (remaining - protectedHistoryTokens).coerceAtLeast(0)
    val optionalHistoryDemand = (completeHistoryTokens - protectedHistoryTokens).coerceAtLeast(0)
    val memoryShare = when (contextPriority) {
        ContextPriority.CHAT_HISTORY -> 0.20
        ContextPriority.BALANCED -> 0.40
        ContextPriority.MEMORIES -> 0.65
    }
    val historySurplus = (discretionaryPool - optionalHistoryDemand).coerceAtLeast(0)
    val allocation = ((discretionaryPool * memoryShare).toInt() + historySurplus)
        .coerceIn(0, remaining)

    val selected = mutableListOf<AssistantMemory>()
    var rendered = ""
    var renderedTokens = 0
    candidates.forEach { candidate ->
        val proposed = selected + candidate
        val proposedText = renderMemoryContextPrompt(model, proposed, episodeGroup)
        val proposedTokens = ContextTokenEstimator.textTokens(proposedText, model)
        if (proposedTokens <= allocation) {
            selected += candidate
            rendered = proposedText
            renderedTokens = proposedTokens
        }
    }
    return MemoryContextSelection(selected, rendered, renderedTokens, allocation)
}

/** The exact text embedded in the request and attributed to the Memory meter category. */
internal fun renderMemoryContextPrompt(
    model: Model,
    memories: List<AssistantMemory>,
    episodeGroup: (Long) -> String,
): String {
    if (memories.isEmpty()) return ""
    val coreMemories = memories.filter { it.type == 0 }
    val episodicMemories = memories.filter { it.type == 1 }
    return buildString {
        append("## Memories\n")
        append("These are memories that you can reference in future conversations.\n")
        if (coreMemories.isNotEmpty()) {
            append("### Core Memories\n")
            coreMemories.forEach { memory ->
                append("- [ID: ${memory.id}] ${memory.content}\n")
            }
        }
        if (episodicMemories.isNotEmpty()) {
            append("### Episodic Memories\n")
            val grouped = episodicMemories.groupBy { memory -> episodeGroup(memory.timestamp) }
            listOf("Today", "Yesterday", "This Week", "Older").forEach { group ->
                grouped[group].orEmpty()
                    .sortedByDescending { it.timestamp }
                    .takeIf { it.isNotEmpty() }
                    ?.let { groupMemories ->
                        append("#### $group\n")
                        groupMemories.forEach { memory -> append("- ${memory.content}\n") }
                    }
            }
        }
        if (ModelAbility.TOOL in model.abilities) {
            append(
                """

                ## Memory Tool
                You are a stateless large language model; you **cannot store memories** internally. To remember information, you must use **memory tools**.
                Memory tools allow you (the assistant) to store multiple pieces of information (records) to recall details across conversations.
                You can use the `create_memory`, `edit_memory`, and `delete_memory` tools to create, update, or delete memories.
                - If there is no relevant information in memory, call `create_memory` to create a new record.
                - If a relevant record already exists, call `edit_memory` to update it.
                - If a memory is outdated or no longer useful, call `delete_memory` to remove it.
                **Note:** You can only edit or delete **Core Memories** (which have an ID). Episodic Memories are read-only context.

                **Do not store sensitive information.** Sensitive information includes: ethnicity, religious beliefs, sexual orientation, political views, sexual life, criminal records, etc.
                During chats, act like a personal secretary and **proactively** record user-related information, including but not limited to:
                - Name/Nickname
                - Age/Gender/Hobbies
                - Plans/To-do items
                """.trimIndent()
            )
        }
    }
}
