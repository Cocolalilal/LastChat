package me.rerere.rikkahub.ui.components.chat

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Build
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.data.repository.MemoryRepository
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
        val title: String? = null
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
        val preview: String
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
            else -> Icons.Rounded.Build
        }
        is TimelineEntry.MemoryAction -> when (entry.operation) {
            MemoryOperation.DELETE -> Icons.Rounded.BookmarkRemove
            else -> Icons.Rounded.Bookmark
        }
        is TimelineEntry.Reply -> Icons.Rounded.ChevronRight
    }
}

/**
 * Get display label for a timeline entry.
 */
private fun getTimelineLabel(entry: TimelineEntry): String {
    return when (entry) {
        is TimelineEntry.Reasoning -> entry.title ?: "Reasoning"
        is TimelineEntry.ToolCall -> entry.displayName
        is TimelineEntry.MemoryAction -> when (entry.operation) {
            MemoryOperation.CREATE -> "Memory created"
            MemoryOperation.EDIT -> "Memory edited"
            MemoryOperation.DELETE -> "Memory deleted"
        }
        is TimelineEntry.Reply -> "Reply"
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

/**
 * Activity timeline bottom sheet.
 *
 * Shows a chronological list of all activities during the generation.
 * Each item can be expanded to show full content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTimelineSheet(
    entries: List<TimelineEntry>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialExpandedType: ActivityType? = null,
    assistantId: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    val memoryRepo: MemoryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()

    var expandedEntryIds by remember { mutableStateOf(setOf<String>()) }
    var editTarget by remember { mutableStateOf<MemoryEditTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<MemoryDeleteTarget?>(null) }
    var deletedMemoryIds by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(entries, initialExpandedType) {
        val memoryIds = entries.filterIsInstance<TimelineEntry.MemoryAction>()
            .mapNotNull { it.memoryId }
            .distinct()
        deletedMemoryIds = if (memoryIds.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                memoryIds.filter { id -> memoryRepo.getMemoryById(id) == null }.toSet()
            }
        } else {
            emptySet()
        }

        val initialIds = if (initialExpandedType != null) {
            entries.filter { entryMatchesType(it, initialExpandedType) }
                .map { it.id }
                .toSet()
        } else {
            emptySet()
        }
        expandedEntryIds = initialIds

        val index = if (initialExpandedType != null) {
            entries.indexOfFirst { entryMatchesType(it, initialExpandedType) }
        } else {
            -1
        }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (entries.isNotEmpty()) {
                    Text(
                        text = "${entries.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = "No activity recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
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
                                if (assistantId == null) return@TimelineEntryItem
                                haptics.perform(HapticPattern.Success)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        memoryRepo.addMemory(assistantId, content)
                                    }
                                }
                            },
                            onRevertMemory = { id, content ->
                                haptics.perform(HapticPattern.Pop)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        memoryRepo.updateContent(id, content)
                                    }
                                }
                            },
                            canRestore = assistantId != null
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
            title = { Text("Edit memory") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                    shape = AppShapes.CardMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    memoryRepo.updateContent(target.id, trimmed)
                                }
                                editTarget = null
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete memory?") },
            text = {
                Text(
                    text = target.content?.take(140)
                        ?: "This memory will be removed from the assistant."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                memoryRepo.deleteMemory(target.id)
                            }
                            deletedMemoryIds = deletedMemoryIds + target.id
                            deleteTarget = null
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
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
    modifier: Modifier = Modifier
) {
    val hasExpandableContent = when (entry) {
        is TimelineEntry.Reasoning -> entry.content.isNotBlank()
        is TimelineEntry.ToolCall -> entry.argumentsText.isNotBlank() ||
            entry.resultText != null ||
            entry.argumentsJson != null ||
            entry.resultJson != null
        is TimelineEntry.MemoryAction -> true
        is TimelineEntry.Reply -> false
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
                .fillMaxWidth()
                .clip(AppShapes.CardMedium)
                .clickable(enabled = hasExpandableContent) { onToggleExpanded() }
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
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
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasExpandableContent) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = expanded,
                        transitionSpec = {
                            fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f))
                                .togetherWith(
                                    fadeOut(animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f))
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
            } else {
                val args = entry.argumentsText
                val result = entry.resultText
                if (args.isNotBlank() && args != "{}") args.take(160)
                else result?.take(160).orEmpty()
            }
        }
        is TimelineEntry.MemoryAction -> entry.content?.take(160) ?: entry.previousContent?.take(160).orEmpty()
        is TimelineEntry.Reply -> entry.preview
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

        else -> Unit
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
            text = "Query",
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
            text = "Sources (${items.size})",
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
            text = "URL",
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
private fun GenericToolDetails(entry: TimelineEntry.ToolCall) {
    val argumentsPretty = entry.argumentsJson?.let { JsonInstantPretty.encodeToString(it) }
        ?: entry.argumentsText
    val resultPretty = entry.resultJson?.let { JsonInstantPretty.encodeToString(it) }
        ?: entry.resultText

    if (argumentsPretty.isNotBlank() && argumentsPretty != "{}") {
        Text(
            text = "Arguments",
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
            text = "Result",
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
        0 -> "Core memory"
        1 -> "Episodic memory"
        else -> null
    }

    if (isDeleted) {
        Surface(
            shape = AppShapes.Chip,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                text = "Deleted",
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
                MemoryContentBlock(label = "Content", content = content)
            }
        }
        MemoryOperation.EDIT -> {
            entry.previousContent?.let { previous ->
                MemoryContentBlock(label = "Before", content = previous)
            }
            entry.content?.let { content ->
                MemoryContentBlock(label = "After", content = content)
            }
        }
        MemoryOperation.DELETE -> {
            entry.content?.let { content ->
                MemoryContentBlock(label = "Deleted", content = content)
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
                        label = "Edit",
                        icon = Icons.Rounded.Edit,
                        onClick = { onEditMemory(entry.memoryId, entry.content) }
                    )
                    TimelineActionButton(
                        label = "Delete",
                        icon = Icons.Rounded.Delete,
                        onClick = { onDeleteMemory(entry.memoryId, entry.content) }
                    )
                }
            }
            MemoryOperation.EDIT -> {
                if (entry.memoryId != null && entry.content != null) {
                    TimelineActionButton(
                        label = "Edit",
                        icon = Icons.Rounded.Edit,
                        onClick = { onEditMemory(entry.memoryId, entry.content) }
                    )
                }
                if (entry.memoryId != null && entry.previousContent != null) {
                    TimelineActionButton(
                        label = "Revert",
                        icon = Icons.Rounded.Refresh,
                        onClick = { onRevertMemory(entry.memoryId, entry.previousContent) }
                    )
                }
            }
            MemoryOperation.DELETE -> {
                if (entry.content != null) {
                    TimelineActionButton(
                        label = "Restore",
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
