package me.rerere.rikkahub.ui.components.chat

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.message.SANDBOX_FILE_TOOLS
import me.rerere.rikkahub.ui.components.message.buildPythonToolSummary
import me.rerere.rikkahub.ui.components.message.buildSandboxFileToolSummary
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.compose.koinInject

/**
 * An entry in the activity timeline.
 */
sealed interface TimelineEntry {
    val id: String

    /** Reasoning/thinking phase */
    data class Reasoning(
        override val id: String,
        val content: String,
        val durationMs: Long,
        val title: String? = null,
        val isInProgress: Boolean = false
    ) : TimelineEntry

    /** Tool call */
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

    /** Memory-specific operation */
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

    /** Reply segment (when model alternates between thinking and replying) */
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

private data class MemoryEditTarget(
    val id: Int,
    val content: String
)

private data class MemoryDeleteTarget(
    val id: Int,
    val content: String?
)

private data class SkillChangeSummary(
    val activated: List<String>,
    val disabled: List<String>
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

private const val TIMELINE_PANEL_ANIMATION_MS = 220
private const val TIMELINE_ENTRY_ANIMATION_MS = 180
private const val TIMELINE_MAX_HEIGHT_DP = 360

private fun buildEntryFollowSignature(entry: TimelineEntry): String {
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

private fun getSkillChangeSummary(entry: TimelineEntry.ToolCall): SkillChangeSummary {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject

    val activated = parseSkillNames(resultObj?.get("activated"))
    val disabled = parseSkillNames(resultObj?.get("disabled"))
    if (activated.isNotEmpty() || disabled.isNotEmpty()) {
        return SkillChangeSummary(activated = activated, disabled = disabled)
    }

    // Fallback for older saved tool results before activated/disabled were added.
    val matched = parseSkillNames(resultObj?.get("matched"))
    val operation = argsObj?.get("operation")?.jsonPrimitiveOrNull?.contentOrNull?.lowercase()
    return when (operation) {
        "disable" -> SkillChangeSummary(activated = emptyList(), disabled = matched)
        "enable", "set" -> SkillChangeSummary(activated = matched, disabled = emptyList())
        else -> SkillChangeSummary(activated = emptyList(), disabled = emptyList())
    }
}

/**
 * Get icon for a timeline entry type.
 */
private fun getTimelineIcon(entry: TimelineEntry): ImageVector {
    return when (entry) {
        is TimelineEntry.Reasoning -> Icons.Rounded.Lightbulb
        is TimelineEntry.ToolCall -> when (entry.toolName) {
            "search_web", "scrape_web" -> Icons.Rounded.Public
            "eval_python", "pip_install", "write_sandbox_file",
            "read_sandbox_file", "list_sandbox_files", "delete_sandbox_file" -> Icons.Rounded.Terminal
            "manage_skills" -> Icons.Rounded.Category
            else -> Icons.Rounded.Build
        }
        is TimelineEntry.MemoryAction -> when (entry.operation) {
            MemoryOperation.DELETE -> Icons.Rounded.BookmarkRemove
            else -> Icons.Rounded.Bookmark
        }
        is TimelineEntry.Reply -> Icons.Rounded.ChevronRight
    }
}

@Composable
private fun getLocalizedToolLabel(toolName: String, fallback: String): String {
    return when (toolName) {
        "search_web" -> stringResource(R.string.activity_timeline_tool_search_web)
        "scrape_web" -> stringResource(R.string.activity_timeline_tool_scrape_web)
        "eval_python" -> stringResource(R.string.chat_message_tool_python_eval)
        "pip_install" -> stringResource(R.string.chat_message_tool_python_pip)
        "write_sandbox_file" -> stringResource(R.string.activity_timeline_tool_write_file)
        "read_sandbox_file" -> stringResource(R.string.activity_timeline_tool_read_file)
        "list_sandbox_files" -> stringResource(R.string.chat_message_tool_python_list_files)
        "delete_sandbox_file" -> stringResource(R.string.activity_timeline_tool_delete_file)
        "manage_skills" -> stringResource(R.string.activity_timeline_tool_manage_skills)
        else -> fallback
    }
}

@Composable
private fun getTimelineLabel(entry: TimelineEntry): String {
    return when (entry) {
        is TimelineEntry.Reasoning -> entry.title ?: stringResource(R.string.activity_timeline_reasoning)
        is TimelineEntry.ToolCall -> getLocalizedToolLabel(entry.toolName, entry.displayName)
        is TimelineEntry.MemoryAction -> when (entry.operation) {
            MemoryOperation.CREATE -> stringResource(R.string.chat_message_tool_create_memory)
            MemoryOperation.EDIT -> stringResource(R.string.chat_message_tool_edit_memory)
            MemoryOperation.DELETE -> stringResource(R.string.chat_message_tool_delete_memory)
        }
        is TimelineEntry.Reply -> stringResource(R.string.activity_timeline_reply)
    }
}

@Composable
private fun getSkillSummaryPreview(summary: SkillChangeSummary): String {
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
private fun getTimelineAccentColor(entry: TimelineEntry): Color {
    return when (entry) {
        is TimelineEntry.Reasoning -> MaterialTheme.colorScheme.tertiary
        is TimelineEntry.ToolCall -> MaterialTheme.colorScheme.secondary
        is TimelineEntry.MemoryAction -> MaterialTheme.colorScheme.primary
        is TimelineEntry.Reply -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Format duration in a human-readable way.
 */
private fun formatDuration(ms: Long): String? {
    if (ms <= 0) return null
    val seconds = ms / 1000.0
    return if (seconds < 10) {
        String.format("%.1fs", seconds)
    } else {
        String.format("%.0fs", seconds)
    }
}

private fun entryMatchesType(entry: TimelineEntry, type: ActivityType): Boolean {
    return when (entry) {
        is TimelineEntry.Reasoning -> type == ActivityType.REASONING
        is TimelineEntry.ToolCall -> categorizeToolName(entry.toolName) == type
        is TimelineEntry.MemoryAction -> categorizeToolName(entry.toolName) == type
        else -> false
    }
}

private fun findFirstMatchingEntryIndex(
    entries: List<TimelineEntry>,
    focusType: ActivityType?
): Int? {
    if (focusType == null) return null
    val index = entries.indexOfFirst { entryMatchesType(it, focusType) }
    return index.takeIf { it >= 0 }
}

private fun findCurrentEntryIndex(entries: List<TimelineEntry>): Int? {
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

/**
 * Activity timeline bottom sheet.
 *
 * Shows a chronological list of all activities during the generation.
 * Each item can be expanded to show full content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityTimelineSheet(
    entries: List<TimelineEntry>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialOpenRequest: TimelineOpenRequest? = null,
    assistantId: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        ActivityTimelinePanel(
            entries = entries,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            initialOpenRequest = initialOpenRequest,
            assistantId = assistantId
        )
    }
}

@Composable
internal fun ActivityTimelinePanel(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier,
    initialOpenRequest: TimelineOpenRequest? = null,
    assistantId: String? = null,
    memoryActions: TimelineMemoryActions? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    var autoFollowCurrentEntry by remember { mutableStateOf(false) }
    val timelineScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    autoFollowCurrentEntry = false
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y != 0f) {
                    autoFollowCurrentEntry = false
                }
                return Velocity.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity(x = 0f, y = available.y)
            }
        }
    }
    val memoryRepo: MemoryRepository? = if (
        memoryActions == null && entries.any { it is TimelineEntry.MemoryAction }
    ) {
        koinInject()
    } else {
        null
    }
    val resolvedMemoryActions = remember(memoryActions, memoryRepo, assistantId) {
        memoryActions ?: TimelineMemoryActions(
            findDeletedIds = { memoryIds ->
                if (memoryRepo == null) {
                    emptySet()
                } else {
                    withContext(Dispatchers.IO) {
                        memoryIds.filter { id -> memoryRepo.getMemoryById(id) == null }.toSet()
                    }
                }
            },
            updateContent = { id, content ->
                if (memoryRepo != null) {
                    withContext(Dispatchers.IO) {
                        memoryRepo.updateContent(id, content)
                    }
                }
            },
            deleteMemory = { id ->
                if (memoryRepo != null) {
                    withContext(Dispatchers.IO) {
                        memoryRepo.deleteMemory(id)
                    }
                }
            },
            restoreMemory = { content ->
                if (memoryRepo != null && assistantId != null) {
                    withContext(Dispatchers.IO) {
                        memoryRepo.addMemory(assistantId, content)
                    }
                }
            },
            revertMemory = { id, content ->
                if (memoryRepo != null) {
                    withContext(Dispatchers.IO) {
                        memoryRepo.updateContent(id, content)
                    }
                }
            }
        )
    }

    var expandedEntryIds by remember { mutableStateOf(setOf<String>()) }
    var editTarget by remember { mutableStateOf<MemoryEditTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<MemoryDeleteTarget?>(null) }
    var deletedMemoryIds by remember { mutableStateOf(setOf<Int>()) }
    val entryIds = remember(entries) { entries.map { it.id } }
    val currentEntryId = remember(entries) {
        findCurrentEntryIndex(entries)?.let { entries[it].id }
    }

    LaunchedEffect(entryIds) {
        val memoryIds = entries.filterIsInstance<TimelineEntry.MemoryAction>()
            .mapNotNull { it.memoryId }
            .distinct()
        deletedMemoryIds = if (memoryIds.isNotEmpty()) {
            resolvedMemoryActions.findDeletedIds(memoryIds)
        } else {
            emptySet()
        }
        expandedEntryIds = expandedEntryIds.intersect(entryIds.toSet())
    }

    LaunchedEffect(initialOpenRequest) {
        val initialFocus = buildInitialTimelineFocus(entries, initialOpenRequest)
        expandedEntryIds = initialFocus.expandedEntryIds
        autoFollowCurrentEntry = initialOpenRequest?.openMode == TimelineOpenMode.FocusCurrent

        val scrollIndex = initialFocus.scrollIndex
        if (scrollIndex != null) {
            listState.scrollToItem(scrollIndex)
        }
    }

    Surface(
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_timeline_panel")
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = TIMELINE_PANEL_ANIMATION_MS,
                    easing = LinearOutSlowInEasing
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.activity_timeline_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.activity_timeline_description, entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.activity_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = TIMELINE_MAX_HEIGHT_DP.dp)
                        .nestedScroll(timelineScrollLock),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.id }
                    ) { index, entry ->
                        TimelineEntryItem(
                            entry = entry,
                            expanded = expandedEntryIds.contains(entry.id),
                            onToggleExpanded = {
                                haptics.perform(HapticPattern.Pop)
                                expandedEntryIds = if (expandedEntryIds.contains(entry.id)) {
                                    expandedEntryIds - entry.id
                                } else {
                                    expandedEntryIds + entry.id
                                }
                            },
                            isLast = index == entries.lastIndex,
                            isLocallyDeleted = entry is TimelineEntry.MemoryAction &&
                                entry.memoryId != null &&
                                deletedMemoryIds.contains(entry.memoryId),
                            onEditMemory = { id, content ->
                                haptics.perform(HapticPattern.Pop)
                                editTarget = MemoryEditTarget(id, content)
                            },
                            onDeleteMemory = { id, content ->
                                haptics.perform(HapticPattern.Thud)
                                deleteTarget = MemoryDeleteTarget(id, content)
                            },
                            onRestoreMemory = { content ->
                                haptics.perform(HapticPattern.Success)
                                scope.launch {
                                    resolvedMemoryActions.restoreMemory(content)
                                }
                            },
                            onRevertMemory = { id, content ->
                                haptics.perform(HapticPattern.Pop)
                                scope.launch {
                                    resolvedMemoryActions.revertMemory(id, content)
                                }
                            },
                            canRestore = assistantId != null,
                            followLiveContent = autoFollowCurrentEntry &&
                                currentEntryId != null &&
                                currentEntryId == entry.id
                        )
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.content) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(stringResource(R.string.activity_timeline_edit_memory_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                    shape = AppShapes.InputField
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                resolvedMemoryActions.updateContent(target.id, trimmed)
                                editTarget = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.activity_timeline_delete_memory_title)) },
            text = {
                Text(
                    text = target.content?.take(140)
                        ?: stringResource(R.string.activity_timeline_delete_memory_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            resolvedMemoryActions.deleteMemory(target.id)
                            deletedMemoryIds = deletedMemoryIds + target.id
                            deleteTarget = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.chat_page_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * A single entry in the timeline.
 */
@Composable
private fun TimelineEntryItem(
    entry: TimelineEntry,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    isLast: Boolean,
    isLocallyDeleted: Boolean,
    onEditMemory: (Int, String) -> Unit,
    onDeleteMemory: (Int, String?) -> Unit,
    onRestoreMemory: (String) -> Unit,
    onRevertMemory: (Int, String) -> Unit,
    canRestore: Boolean,
    followLiveContent: Boolean,
    modifier: Modifier = Modifier
) {
    val hasExpandableContent = when (entry) {
        is TimelineEntry.Reasoning -> entry.content.isNotBlank()
        is TimelineEntry.ToolCall -> entry.argumentsText.isNotBlank() ||
            entry.resultText != null ||
            entry.argumentsJson != null ||
            entry.resultJson != null
        is TimelineEntry.MemoryAction -> true
        is TimelineEntry.Reply -> entry.content.isNotBlank()
    }
    val isMemoryDeleted = (entry is TimelineEntry.MemoryAction &&
        entry.operation == MemoryOperation.DELETE) || isLocallyDeleted
    val accentColor = if (isMemoryDeleted) {
        MaterialTheme.colorScheme.error
    } else {
        getTimelineAccentColor(entry)
    }
    val containerColor = when {
        isMemoryDeleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        entry is TimelineEntry.MemoryAction -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val followSignature = remember(entry) { buildEntryFollowSignature(entry) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(accentColor.copy(alpha = 0.25f))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = AppShapes.CardMedium,
            color = containerColor,
            modifier = Modifier
                .testTag("timeline_entry_${entry.id}")
                .fillMaxWidth()
                .clip(AppShapes.CardMedium)
                .clickable(enabled = hasExpandableContent) { onToggleExpanded() }
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = TIMELINE_ENTRY_ANIMATION_MS,
                        easing = LinearOutSlowInEasing
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getTimelineIcon(entry),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = getTimelineLabel(entry),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (entry is TimelineEntry.Reasoning) {
                        formatDuration(entry.durationMs)?.let { duration ->
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    if (hasExpandableContent) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) {
                                stringResource(R.string.activity_timeline_collapse)
                            } else {
                                stringResource(R.string.activity_timeline_expand)
                            },
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasExpandableContent) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = expanded,
                        transitionSpec = {
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = TIMELINE_ENTRY_ANIMATION_MS,
                                    easing = LinearOutSlowInEasing
                                )
                            )
                                .togetherWith(
                                    fadeOut(
                                        animationSpec = tween(
                                            durationMillis = TIMELINE_ENTRY_ANIMATION_MS / 2,
                                            easing = FastOutLinearInEasing
                                        )
                                    )
                                )
                        },
                        label = "timeline_expand"
                    ) { isExpanded ->
                        if (isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TimelineExpandedContent(
                                    entry = entry,
                                    isDeleted = isMemoryDeleted,
                                    onEditMemory = onEditMemory,
                                    onDeleteMemory = onDeleteMemory,
                                    onRestoreMemory = onRestoreMemory,
                                    onRevertMemory = onRevertMemory,
                                    canRestore = canRestore
                                )
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .bringIntoViewRequester(bringIntoViewRequester)
                                )
                            }
                        } else {
                            TimelinePreview(entry = entry)
                        }
                    }
                } else {
                    TimelinePreview(entry = entry)
                }
            }
        }
    }

    LaunchedEffect(expanded, followLiveContent, followSignature) {
        if (expanded && followLiveContent) {
            bringIntoViewRequester.bringIntoView()
        }
    }
}

@Composable
private fun TimelinePreview(entry: TimelineEntry) {
    val previewText = when (entry) {
        is TimelineEntry.Reasoning -> entry.content.take(160)
        is TimelineEntry.ToolCall -> {
            if (entry.toolName == "search_web") {
                val query = (entry.argumentsJson as? JsonObject)
                    ?.get("query")
                    ?.jsonPrimitiveOrNull
                    ?.contentOrNull
                val answer = (entry.resultJson as? JsonObject)
                    ?.get("answer")
                    ?.jsonPrimitiveOrNull
                    ?.contentOrNull
                (answer ?: query).orEmpty().take(160)
            } else if (entry.toolName == "manage_skills") {
                getSkillSummaryPreview(getSkillChangeSummary(entry)).take(160)
            } else if (entry.toolName == "eval_python") {
                buildPythonToolSummary(
                    arguments = entry.argumentsJson,
                    content = entry.resultJson
                )?.previewText?.take(160).orEmpty()
            } else if (entry.toolName in SANDBOX_FILE_TOOLS) {
                val fileSummary = buildSandboxFileToolSummary(
                    toolName = entry.toolName,
                    arguments = entry.argumentsJson,
                    content = entry.resultJson
                )
                when {
                    fileSummary?.fileCount != null -> stringResource(
                        R.string.activity_timeline_file_count_value,
                        fileSummary.fileCount
                    )
                    !fileSummary?.previewText.isNullOrBlank() -> fileSummary?.previewText.orEmpty().take(160)
                    else -> entry.argumentsText.take(160)
                }
            } else {
                val args = entry.argumentsText
                val result = entry.resultText
                if (args.isNotBlank() && args != "{}") args.take(160)
                else result?.take(160).orEmpty()
            }
        }
        is TimelineEntry.MemoryAction -> entry.content?.take(160) ?: entry.previousContent?.take(160).orEmpty()
        is TimelineEntry.Reply -> entry.content.take(160)
    }

    if (previewText.isNotBlank()) {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolCallDetails(entry: TimelineEntry.ToolCall) {
    when (entry.toolName) {
        "search_web" -> SearchTimelineDetails(entry)
        "scrape_web" -> ScrapeTimelineDetails(entry)
        "eval_python" -> PythonTimelineDetails(entry)
        in SANDBOX_FILE_TOOLS -> SandboxFileTimelineDetails(entry)
        "manage_skills" -> SkillManagementTimelineDetails(entry)
        else -> GenericToolDetails(entry)
    }
}

@Composable
private fun TimelineExpandedContent(
    entry: TimelineEntry,
    isDeleted: Boolean,
    onEditMemory: (Int, String) -> Unit,
    onDeleteMemory: (Int, String?) -> Unit,
    onRestoreMemory: (String) -> Unit,
    onRevertMemory: (Int, String) -> Unit,
    canRestore: Boolean
) {
    when (entry) {
        is TimelineEntry.Reasoning -> {
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is TimelineEntry.ToolCall -> {
            ToolCallDetails(entry = entry)
        }

        is TimelineEntry.MemoryAction -> {
            MemoryDetails(
                entry = entry,
                isDeleted = isDeleted,
                onEditMemory = onEditMemory,
                onDeleteMemory = onDeleteMemory,
                onRestoreMemory = onRestoreMemory,
                onRevertMemory = onRevertMemory,
                canRestore = canRestore
            )
        }

        is TimelineEntry.Reply -> {
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchTimelineDetails(entry: TimelineEntry.ToolCall) {
    val argsObj = entry.argumentsJson as? JsonObject
    val query = argsObj?.get("query")?.jsonPrimitiveOrNull?.contentOrNull
    val resultObj = entry.resultJson as? JsonObject
    val answer = resultObj?.get("answer")?.jsonPrimitiveOrNull?.contentOrNull
    val items = (resultObj?.get("items") as? JsonArray) ?: JsonArray(emptyList())

    if (!query.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.activity_timeline_query),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (!answer.isNullOrBlank()) {
        Surface(
            shape = AppShapes.CardSmall,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    if (items.isNotEmpty()) {
        Text(
            text = stringResource(R.string.activity_timeline_sources, items.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val title = obj["title"]?.jsonPrimitiveOrNull?.contentOrNull
                val url = obj["url"]?.jsonPrimitiveOrNull?.contentOrNull
                val snippet = obj["text"]?.jsonPrimitiveOrNull?.contentOrNull
                val host = url?.let { Uri.parse(it).host }

                Surface(
                    shape = AppShapes.CardSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!title.isNullOrBlank()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!host.isNullOrBlank()) {
                            Text(
                                text = host,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!snippet.isNullOrBlank()) {
                            Text(
                                text = snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrapeTimelineDetails(entry: TimelineEntry.ToolCall) {
    val argsObj = entry.argumentsJson as? JsonObject
    val url = argsObj?.get("url")?.jsonPrimitiveOrNull?.contentOrNull
    val resultObj = entry.resultJson as? JsonObject
    val content = resultObj?.get("content")?.jsonPrimitiveOrNull?.contentOrNull

    if (!url.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.activity_timeline_url),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (!content.isNullOrBlank()) {
        Surface(
            shape = AppShapes.CardSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = content.take(800),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun PythonTimelineDetails(entry: TimelineEntry.ToolCall) {
    val summary = buildPythonToolSummary(
        arguments = entry.argumentsJson,
        content = entry.resultJson
    ) ?: return GenericToolDetails(entry)

    if (summary.code.isNotBlank()) {
        TimelineDetailBlock(
            label = stringResource(R.string.chat_message_tool_python_code),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = summary.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    Text(
        text = stringResource(R.string.chat_message_tool_python_output),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary
    )

    val hasOutput = !summary.error.isNullOrBlank() ||
        !summary.stdout.isNullOrBlank() ||
        (!summary.result.isNullOrBlank() && summary.result != "null") ||
        !summary.stderr.isNullOrBlank()

    Surface(
        shape = AppShapes.CardSmall,
        color = if (!summary.error.isNullOrBlank()) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!summary.error.isNullOrBlank()) {
                TimelineFieldRow(
                    label = stringResource(R.string.activity_timeline_python_error),
                    value = summary.error,
                    valueColor = MaterialTheme.colorScheme.onErrorContainer,
                    monospace = true
                )
            } else {
                if (!summary.stdout.isNullOrBlank()) {
                    TimelineFieldRow(
                        label = stringResource(R.string.activity_timeline_python_stdout),
                        value = summary.stdout,
                        monospace = true
                    )
                }
                if (!summary.result.isNullOrBlank() && summary.result != "null") {
                    TimelineFieldRow(
                        label = stringResource(R.string.activity_timeline_python_result),
                        value = summary.result,
                        monospace = true
                    )
                }
                if (!summary.stderr.isNullOrBlank()) {
                    TimelineFieldRow(
                        label = stringResource(R.string.activity_timeline_python_stderr),
                        value = summary.stderr,
                        valueColor = MaterialTheme.colorScheme.error,
                        monospace = true
                    )
                }
                if (!hasOutput) {
                    Text(
                        text = stringResource(R.string.activity_timeline_no_output),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

}

@Composable
private fun SandboxFileTimelineDetails(entry: TimelineEntry.ToolCall) {
    val summary = buildSandboxFileToolSummary(
        toolName = entry.toolName,
        arguments = entry.argumentsJson,
        content = entry.resultJson
    ) ?: return GenericToolDetails(entry)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        summary.path?.takeIf { it.isNotBlank() }?.let { path ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_file_path),
                value = path,
                monospace = true
            )
        }
        if (summary.fileCount != null) {
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_file_count),
                value = stringResource(R.string.activity_timeline_file_count_value, summary.fileCount)
            )
        }
        summary.uri?.takeIf { it.isNotBlank() }?.let { uri ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_file_uri),
                value = uri,
                monospace = true
            )
        }
        if (summary.success != null) {
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_status),
                value = if (summary.success) {
                    stringResource(R.string.activity_timeline_status_success)
                } else {
                    stringResource(R.string.activity_timeline_status_failed)
                },
                valueColor = if (summary.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        summary.error?.takeIf { it.isNotBlank() }?.let { error ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_error),
                value = error,
                valueColor = MaterialTheme.colorScheme.error,
                monospace = true
            )
        }
    }
}

@Composable
private fun SkillManagementTimelineDetails(entry: TimelineEntry.ToolCall) {
    val summary = getSkillChangeSummary(entry)

    if (summary.activated.isNotEmpty()) {
        Text(
            text = stringResource(R.string.activity_timeline_activated),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Surface(
            shape = AppShapes.CardSmall,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                text = summary.activated.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    if (summary.disabled.isNotEmpty()) {
        Text(
            text = stringResource(R.string.activity_timeline_disabled),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Surface(
            shape = AppShapes.CardSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = summary.disabled.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    if (summary.activated.isEmpty() && summary.disabled.isEmpty()) {
        Text(
            text = stringResource(R.string.activity_timeline_no_skill_changes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GenericToolDetails(entry: TimelineEntry.ToolCall) {
    val argumentsPretty = entry.argumentsJson?.let { JsonInstantPretty.encodeToString(it) }
        ?: entry.argumentsText
    val resultPretty = entry.resultJson?.let { JsonInstantPretty.encodeToString(it) }
        ?: entry.resultText

    if (argumentsPretty.isNotBlank() && argumentsPretty != "{}") {
        Text(
            text = stringResource(R.string.activity_timeline_arguments),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Surface(
            shape = AppShapes.CardSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = argumentsPretty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }

    if (!resultPretty.isNullOrBlank() && resultPretty != "null") {
        Text(
            text = stringResource(R.string.chat_message_tool_call_result),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Surface(
            shape = AppShapes.CardSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = resultPretty.take(1200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun TimelineDetailBlock(
    label: String,
    containerColor: Color,
    content: @Composable () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary
    )
    Surface(
        shape = AppShapes.CardSmall,
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(10.dp)) {
            content()
        }
    }
}

@Composable
private fun TimelineFieldRow(
    label: String,
    value: String?,
    valueColor: Color? = null,
    monospace: Boolean = false
) {
    if (value.isNullOrBlank()) return
    val resolvedValueColor = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    TimelineDetailBlock(
        label = label,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = resolvedValueColor,
            fontFamily = if (monospace) FontFamily.Monospace else null
        )
    }
}

@Composable
private fun MemoryDetails(
    entry: TimelineEntry.MemoryAction,
    isDeleted: Boolean,
    onEditMemory: (Int, String) -> Unit,
    onDeleteMemory: (Int, String?) -> Unit,
    onRestoreMemory: (String) -> Unit,
    onRevertMemory: (Int, String) -> Unit,
    canRestore: Boolean
) {
    val memoryTypeLabel = when (entry.memoryType) {
        0 -> stringResource(R.string.activity_timeline_memory_core)
        1 -> stringResource(R.string.activity_timeline_memory_episodic)
        else -> null
    }

    if (isDeleted) {
        Surface(
            shape = AppShapes.Chip,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                text = stringResource(R.string.activity_timeline_deleted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    if (memoryTypeLabel != null) {
        Surface(
            shape = AppShapes.Chip,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = memoryTypeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    when (entry.operation) {
        MemoryOperation.CREATE -> {
            entry.content?.let { content ->
                MemoryContentBlock(label = stringResource(R.string.activity_timeline_content), content = content)
            }
        }
        MemoryOperation.EDIT -> {
            entry.previousContent?.let { previous ->
                MemoryContentBlock(label = stringResource(R.string.activity_timeline_before), content = previous)
            }
            entry.content?.let { content ->
                MemoryContentBlock(label = stringResource(R.string.activity_timeline_after), content = content)
            }
        }
        MemoryOperation.DELETE -> {
            entry.content?.let { content ->
                MemoryContentBlock(label = stringResource(R.string.activity_timeline_deleted), content = content)
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (entry.operation) {
            MemoryOperation.CREATE -> {
                if (entry.memoryId != null && entry.content != null) {
                    TimelineActionButton(
                        label = stringResource(R.string.edit),
                        icon = Icons.Rounded.Edit,
                        onClick = { onEditMemory(entry.memoryId, entry.content) }
                    )
                    TimelineActionButton(
                        label = stringResource(R.string.chat_page_delete),
                        icon = Icons.Rounded.Delete,
                        onClick = { onDeleteMemory(entry.memoryId, entry.content) }
                    )
                }
            }
            MemoryOperation.EDIT -> {
                if (entry.memoryId != null && entry.content != null) {
                    TimelineActionButton(
                        label = stringResource(R.string.edit),
                        icon = Icons.Rounded.Edit,
                        onClick = { onEditMemory(entry.memoryId, entry.content) }
                    )
                }
                if (entry.memoryId != null && entry.previousContent != null) {
                    TimelineActionButton(
                        label = stringResource(R.string.activity_timeline_revert),
                        icon = Icons.Rounded.Refresh,
                        onClick = { onRevertMemory(entry.memoryId, entry.previousContent) }
                    )
                }
            }
            MemoryOperation.DELETE -> {
                if (entry.content != null) {
                    TimelineActionButton(
                        label = stringResource(R.string.activity_timeline_restore),
                        icon = Icons.Rounded.Refresh,
                        onClick = { onRestoreMemory(entry.content) },
                        enabled = canRestore
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryContentBlock(
    label: String,
    content: String
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary
    )
    Surface(
        shape = AppShapes.CardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun TimelineActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "timeline_action_scale"
    )

    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.ButtonPill,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
