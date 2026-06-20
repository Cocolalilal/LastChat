package me.rerere.rikkahub.ui.components.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.data.ai.tools.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.AskUserAnswerPayload
import me.rerere.rikkahub.data.ai.tools.AskUserQuestionnaire
import me.rerere.rikkahub.data.ai.tools.normalizeAskUserAnswerPayload
import me.rerere.rikkahub.data.ai.tools.parseAskUserQuestionnaire
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

sealed interface TimelineEntry {
    val id: String

    data class Reasoning(
        override val id: String,
        val content: String,
        val durationMs: Long,
        val title: String? = null,
        val isInProgress: Boolean = false
    ) : TimelineEntry

    data class ToolCall(
        override val id: String,
        val toolName: String,
        val displayName: String,
        val argumentsText: String,
        val resultText: String?,
        val argumentsJson: JsonElement? = null,
        val resultJson: JsonElement? = null,
        val isLoading: Boolean = false
    ) : TimelineEntry

    data class MemoryAction(
        override val id: String,
        val toolName: String,
        val operation: MemoryOperation,
        val memoryId: Int?,
        val content: String?,
        val previousContent: String?,
        val memoryType: Int?,
        val timestamp: Long?,
        val isLoading: Boolean = false
    ) : TimelineEntry

    data class Ocr(
        override val id: String,
        val source: UIMessageAnnotation.OcrActivity.Source,
        val fileName: String?,
        val pageNumbers: List<Int>,
        val isInProgress: Boolean = false,
    ) : TimelineEntry

    data class Reply(
        override val id: String,
        val content: String,
        val isInProgress: Boolean = false
    ) : TimelineEntry
}

enum class MemoryOperation {
    CREATE,
    EDIT,
    DELETE
}

internal data class MemoryEditTarget(
    val id: Int,
    val content: String
)

internal data class MemoryDeleteTarget(
    val id: Int,
    val content: String?
)

internal data class SkillChangeSummary(
    val activated: List<String>,
    val disabled: List<String>
)

internal data class AskUserTimelineState(
    val questionnaire: AskUserQuestionnaire,
    val payload: AskUserAnswerPayload?,
)

internal data class TimelineMemoryActions(
    val findDeletedIds: suspend (List<Int>) -> Set<Int>,
    val updateContent: suspend (Int, String) -> Unit,
    val deleteMemory: suspend (Int) -> Unit,
    val restoreMemory: suspend (String) -> Unit,
    val revertMemory: suspend (Int, String) -> Unit,
)

internal enum class TimelineOpenMode {
    Collapsed,
    FocusCurrent,
}

internal data class TimelineOpenRequest(
    val focusType: ActivityType? = null,
    val openMode: TimelineOpenMode = TimelineOpenMode.Collapsed,
)

internal data class TimelineInitialFocus(
    val expandedEntryIds: Set<String> = emptySet(),
    val scrollIndex: Int? = null,
)

internal fun buildEntryFollowSignature(entry: TimelineEntry): String {
    return when (entry) {
        is TimelineEntry.Reasoning -> entry.content
        is TimelineEntry.ToolCall -> buildString {
            append(entry.resultText.orEmpty())
            append('|')
            append(entry.argumentsText)
            append('|')
            append(entry.resultJson?.toString().orEmpty())
            append('|')
            append(entry.argumentsJson?.toString().orEmpty())
            append('|')
            append(entry.isLoading)
        }

        is TimelineEntry.MemoryAction -> buildString {
            append(entry.content.orEmpty())
            append('|')
            append(entry.previousContent.orEmpty())
            append('|')
            append(entry.memoryId ?: -1)
            append('|')
            append(entry.isLoading)
        }

        is TimelineEntry.Ocr -> buildString {
            append(entry.source)
            append('|')
            append(entry.fileName.orEmpty())
            append('|')
            append(entry.pageNumbers.joinToString(","))
            append('|')
            append(entry.isInProgress)
        }

        is TimelineEntry.Reply -> entry.content
    }
}

private fun parseSkillNames(value: JsonElement?): List<String> {
    val array = value as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        when (item) {
            is JsonObject -> {
                item["name"]?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: item["id"]?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }
            }

            else -> item.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    }.distinct()
}

internal fun getSkillChangeSummary(entry: TimelineEntry.ToolCall): SkillChangeSummary {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject

    val activated = parseSkillNames(resultObj?.get("activated"))
    val disabled = parseSkillNames(resultObj?.get("disabled"))
    if (activated.isNotEmpty() || disabled.isNotEmpty()) {
        return SkillChangeSummary(activated = activated, disabled = disabled)
    }

    val matched = parseSkillNames(resultObj?.get("matched"))
    val operation = argsObj?.get("operation")?.jsonPrimitiveOrNull?.contentOrNull?.lowercase()
    return when (operation) {
        "disable" -> SkillChangeSummary(activated = emptyList(), disabled = matched)
        "enable", "set" -> SkillChangeSummary(activated = matched, disabled = emptyList())
        else -> SkillChangeSummary(activated = emptyList(), disabled = emptyList())
    }
}

internal fun parseAskUserTimelineState(entry: TimelineEntry.ToolCall): AskUserTimelineState? {
    if (entry.toolName != ASK_USER_TOOL_NAME) {
        return null
    }

    val questionnaire = entry.argumentsJson?.let(::parseAskUserQuestionnaire)
        ?: parseAskUserQuestionnaire(entry.argumentsText)
        ?: return null

    val payload = when {
        entry.resultJson != null -> normalizeAskUserAnswerPayload(questionnaire, entry.resultJson)
        !entry.resultText.isNullOrBlank() && entry.resultText != "null" -> {
            normalizeAskUserAnswerPayload(questionnaire, entry.resultText)
        }

        else -> null
    }

    return AskUserTimelineState(
        questionnaire = questionnaire,
        payload = payload,
    )
}

internal fun getTimelineIcon(entry: TimelineEntry): ImageVector {
    return when (entry) {
        is TimelineEntry.Reasoning -> if (entry.title != null) Icons.Rounded.Memory else Icons.Rounded.Lightbulb
        is TimelineEntry.ToolCall -> when (entry.toolName) {
            "search_web", "scrape_web" -> Icons.Rounded.Public
            "search_memory" -> Icons.Rounded.Memory
            "eval_python", "pip_install", "write_sandbox_file",
            "read_sandbox_file", "list_sandbox_files", "delete_sandbox_file" -> Icons.Rounded.Terminal
            "workspace_shell" -> Icons.Rounded.Terminal
            "workspace_read_file", "workspace_write_file", "workspace_edit_file" -> Icons.Rounded.Description
            "manage_skills" -> Icons.Rounded.Category
            else -> if (entry.toolName.startsWith("workspace_")) Icons.Rounded.Computer else Icons.Rounded.Build
        }

        is TimelineEntry.MemoryAction -> when (entry.operation) {
            MemoryOperation.DELETE -> Icons.Rounded.BookmarkRemove
            else -> Icons.Rounded.Bookmark
        }

        is TimelineEntry.Ocr -> Icons.Rounded.Image

        is TimelineEntry.Reply -> Icons.Rounded.ChevronRight
    }
}

@Composable
private fun getLocalizedToolLabel(toolName: String, fallback: String): String {
    return when (toolName) {
        "search_web" -> stringResource(R.string.activity_timeline_tool_search_web)
        "search_memory" -> stringResource(R.string.activity_timeline_tool_search_memory)
        "scrape_web" -> stringResource(R.string.activity_timeline_tool_scrape_web)
        "eval_python" -> stringResource(R.string.chat_message_tool_python_eval)
        "pip_install" -> stringResource(R.string.chat_message_tool_python_pip)
        "write_sandbox_file" -> stringResource(R.string.activity_timeline_tool_write_file)
        "read_sandbox_file" -> stringResource(R.string.activity_timeline_tool_read_file)
        "list_sandbox_files" -> stringResource(R.string.chat_message_tool_python_list_files)
        "delete_sandbox_file" -> stringResource(R.string.activity_timeline_tool_delete_file)
        "workspace_read_file" -> stringResource(R.string.activity_timeline_tool_workspace_read_file)
        "workspace_write_file" -> stringResource(R.string.activity_timeline_tool_workspace_write_file)
        "workspace_edit_file" -> stringResource(R.string.activity_timeline_tool_workspace_edit_file)
        "workspace_shell" -> stringResource(R.string.activity_timeline_tool_workspace_shell)
        "ask_user" -> stringResource(R.string.activity_timeline_tool_ask_user)
        "manage_skills" -> stringResource(R.string.activity_timeline_tool_manage_skills)
        else -> fallback
    }
}

@Composable
internal fun getTimelineLabel(entry: TimelineEntry): String {
    return when (entry) {
        is TimelineEntry.Reasoning -> entry.title ?: stringResource(R.string.activity_timeline_reasoning)
        is TimelineEntry.ToolCall -> getLocalizedToolLabel(entry.toolName, entry.displayName)
        is TimelineEntry.MemoryAction -> when (entry.operation) {
            MemoryOperation.CREATE -> stringResource(R.string.chat_message_tool_create_memory)
            MemoryOperation.EDIT -> stringResource(R.string.chat_message_tool_edit_memory)
            MemoryOperation.DELETE -> stringResource(R.string.chat_message_tool_delete_memory)
        }

        is TimelineEntry.Ocr -> stringResource(R.string.activity_timeline_ocr)

        is TimelineEntry.Reply -> stringResource(R.string.activity_timeline_reply)
    }
}

@Composable
internal fun getSkillSummaryPreview(summary: SkillChangeSummary): String {
    val sections = buildList {
        if (summary.activated.isNotEmpty()) {
            add("${stringResource(R.string.activity_timeline_activated)}: ${summary.activated.joinToString(", ")}")
        }
        if (summary.disabled.isNotEmpty()) {
            add("${stringResource(R.string.activity_timeline_disabled)}: ${summary.disabled.joinToString(", ")}")
        }
    }
    return if (sections.isNotEmpty()) {
        sections.joinToString(" | ")
    } else {
        stringResource(R.string.activity_timeline_no_skill_changes)
    }
}

@Composable
internal fun getTimelineAccentColor(entry: TimelineEntry): Color {
    return when (entry) {
        is TimelineEntry.Reasoning -> MaterialTheme.colorScheme.tertiary
        is TimelineEntry.ToolCall -> MaterialTheme.colorScheme.secondary
        is TimelineEntry.MemoryAction -> MaterialTheme.colorScheme.primary
        is TimelineEntry.Ocr -> MaterialTheme.colorScheme.secondary
        is TimelineEntry.Reply -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

internal fun formatTimelineDuration(ms: Long): String? {
    if (ms <= 0) return null
    val seconds = ms / 1000.0
    return if (seconds < 10) {
        String.format("%.1fs", seconds)
    } else {
        String.format("%.0fs", seconds)
    }
}

internal fun entryMatchesType(entry: TimelineEntry, type: ActivityType): Boolean {
    return when (entry) {
        is TimelineEntry.Reasoning -> type == ActivityType.REASONING
        is TimelineEntry.ToolCall -> categorizeToolName(entry.toolName) == type
        is TimelineEntry.MemoryAction -> categorizeToolName(entry.toolName) == type
        is TimelineEntry.Ocr -> type == ActivityType.OCR
        else -> false
    }
}

internal fun findFirstMatchingEntryIndex(
    entries: List<TimelineEntry>,
    focusType: ActivityType?
): Int? {
    if (focusType == null) return null
    val index = entries.indexOfFirst { entryMatchesType(it, focusType) }
    return index.takeIf { it >= 0 }
}

internal fun findCurrentEntryIndex(entries: List<TimelineEntry>): Int? {
    val reasoningIndex = entries.indexOfLast { entry ->
        entry is TimelineEntry.Reasoning && entry.isInProgress
    }
    if (reasoningIndex >= 0) return reasoningIndex

    val toolIndex = entries.indexOfLast { entry ->
        when (entry) {
            is TimelineEntry.ToolCall -> entry.isLoading
            is TimelineEntry.MemoryAction -> entry.isLoading
            else -> false
        }
    }
    if (toolIndex >= 0) return toolIndex
    val ocrIndex = entries.indexOfLast { entry ->
        entry is TimelineEntry.Ocr && entry.isInProgress
    }
    if (ocrIndex >= 0) return ocrIndex
    val replyIndex = entries.indexOfLast { entry ->
        entry is TimelineEntry.Reply && entry.isInProgress
    }
    if (replyIndex >= 0) return replyIndex
    return null
}

internal fun buildInitialTimelineFocus(
    entries: List<TimelineEntry>,
    openRequest: TimelineOpenRequest?
): TimelineInitialFocus {
    if (openRequest == null) {
        return TimelineInitialFocus()
    }

    return when (openRequest.openMode) {
        TimelineOpenMode.Collapsed -> TimelineInitialFocus(
            scrollIndex = findFirstMatchingEntryIndex(entries, openRequest.focusType)
        )

        TimelineOpenMode.FocusCurrent -> {
            val currentIndex = findCurrentEntryIndex(entries)
            if (currentIndex != null) {
                TimelineInitialFocus(
                    expandedEntryIds = setOf(entries[currentIndex].id),
                    scrollIndex = currentIndex
                )
            } else {
                TimelineInitialFocus(
                    scrollIndex = findFirstMatchingEntryIndex(entries, openRequest.focusType)
                )
            }
        }
    }
}
