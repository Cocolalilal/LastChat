package me.rerere.rikkahub.data.ai

import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.db.entity.MemorySummaryEntity
import me.rerere.rikkahub.data.memory.MemorySummaryData
import me.rerere.rikkahub.data.memory.MemorySummaryMarkdownCodec
import me.rerere.rikkahub.data.memory.TokenEstimator
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.ContextPriority

object MemoryContextPlanner {
    private const val MAX_SUMMARY_CONTEXT_TOKENS = 3_500

    fun buildHierarchicalSummarySystemBlock(
        summaryEntity: MemorySummaryEntity,
        parsedSummary: MemorySummaryData?,
    ): String {
        val rawText = when {
            parsedSummary != null && parsedSummary.sections.isNotEmpty() ->
                MemorySummaryMarkdownCodec.encodeFullDocument(parsedSummary)
            summaryEntity.rawMarkdown.isNotBlank() ->
                summaryEntity.rawMarkdown
            else -> return ""
        }

        val boundedSummary = TokenEstimator.truncateToTokenBudget(rawText, MAX_SUMMARY_CONTEXT_TOKENS)

        return buildString {
            appendLine("<memory_summary_index>")
            appendLine(boundedSummary)
            appendLine("</memory_summary_index>")
            appendLine()
            appendLine("<memory_pointer_protocol>")
            appendLine("1. The <memory_summary_index> above is a compact overview of the user's persistent memories. Inline tags such as [#79] or [#182, #240] are database pointers to verbatim raw memory records.")
            appendLine("2. Do NOT show raw [#ID] tags in your normal reply to the user unless they explicitly ask for memory IDs or sources.")
            appendLine("3. ON-DEMAND RETRIEVAL: Call `get_memories(ids=[...])` ONLY when you need exact quotes, timestamps, technical details, or full context behind a summarized item to answer accurately. Combine multiple IDs into a single batched call.")
            appendLine("4. ANTI-HALLUCINATION: Never guess details omitted from the summary or fabricate facts for IDs reported in `missing_ids`.")
            appendLine("5. SUMMARY MAINTENANCE: When the user asks to inspect, amend, or reorganize their memory summary during chat, use `manage_memory_summary` (prefer `update_section` over `replace_summary`). When storing a brand-new persistent fact, continue using `create_memory` first so it receives a database ID.")
            appendLine("</memory_pointer_protocol>")
        }.trim()
    }
}

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
    memorySummary: String? = null,
    includeWriteTools: Boolean = true,
): MemoryContextSelection {
    if (candidates.isEmpty()) {
        val toolPrompt = renderMemoryContextPrompt(model, emptyList(), episodeGroup, memorySummary, includeWriteTools)
        if (toolPrompt.isBlank()) return MemoryContextSelection(emptyList(), "", 0, 0)
        val toolTokens = ContextTokenEstimator.textTokens(toolPrompt, model)
        val remaining = (inputBudgetTokens - requiredContextTokens).coerceAtLeast(0)
        return if (toolTokens <= remaining) {
            MemoryContextSelection(emptyList(), toolPrompt, toolTokens, 0)
        } else {
            MemoryContextSelection(emptyList(), "", 0, 0)
        }
    }

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
        val proposedText = renderMemoryContextPrompt(model, proposed, episodeGroup, memorySummary, includeWriteTools)
        val proposedTokens = ContextTokenEstimator.textTokens(proposedText, model)
        if (proposedTokens <= allocation) {
            selected += candidate
            rendered = proposedText
            renderedTokens = proposedTokens
        }
    }

    if (rendered.isEmpty() && ((includeWriteTools && ModelAbility.TOOL in model.abilities) || !memorySummary.isNullOrBlank())) {
        val toolPrompt = renderMemoryContextPrompt(model, emptyList(), episodeGroup, memorySummary, includeWriteTools)
        val toolTokens = ContextTokenEstimator.textTokens(toolPrompt, model)
        if (toolTokens <= allocation) {
            rendered = toolPrompt
            renderedTokens = toolTokens
        }
    }

    return MemoryContextSelection(selected, rendered, renderedTokens, allocation)
}

internal fun renderMemorySummarySection(memorySummary: String?): String {
    if (memorySummary.isNullOrBlank()) return ""
    return buildString {
        append("## User Memory Baseline Profile\n")
        append("This is a synthesized, high-level understanding of the user across all past interactions:\n")
        append(memorySummary.trim())
    }
}

internal fun renderMemoryRecordsAndToolPrompt(
    model: Model,
    memories: List<AssistantMemory>,
    episodeGroup: (Long) -> String,
    includeWriteTools: Boolean = true,
): String {
    val hasToolAbility = includeWriteTools && ModelAbility.TOOL in model.abilities
    if (memories.isEmpty() && !hasToolAbility) return ""

    val coreMemories = memories.filter { it.type == 0 }
    val episodicMemories = memories.filter { it.type == 1 }
    return buildString {
        if (memories.isNotEmpty()) {
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
        }
        if (hasToolAbility) {
            if (memories.isNotEmpty()) {
                append("\n\n")
            }
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

/** The exact text embedded in the request and attributed to the Memory meter category. */
internal fun renderMemoryContextPrompt(
    model: Model,
    memories: List<AssistantMemory>,
    episodeGroup: (Long) -> String,
    memorySummary: String? = null,
    includeWriteTools: Boolean = true,
): String {
    val summarySection = renderMemorySummarySection(memorySummary)
    val recordsAndToolSection = renderMemoryRecordsAndToolPrompt(
        model = model,
        memories = memories,
        episodeGroup = episodeGroup,
        includeWriteTools = includeWriteTools,
    )
    if (summarySection.isBlank() && recordsAndToolSection.isBlank()) return ""
    return buildString {
        if (summarySection.isNotBlank()) {
            append(summarySection)
            append("\n\n")
        }
        append(recordsAndToolSection)
    }
}
