package me.rerere.rikkahub.ui.components.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListItemInfo
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.ai.tools.AskUserAnswer
import me.rerere.rikkahub.data.ai.tools.AskUserQuestion
import me.rerere.rikkahub.ui.components.message.SANDBOX_FILE_TOOLS
import me.rerere.rikkahub.ui.components.message.WORKSPACE_TOOLS
import me.rerere.rikkahub.ui.components.message.buildPythonToolSummary
import me.rerere.rikkahub.ui.components.message.buildSandboxFileToolSummary
import me.rerere.rikkahub.ui.components.message.buildWorkspaceToolSummary
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.fadeEdges
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject

private const val TIMELINE_PANEL_ANIMATION_MS = 220
private const val TIMELINE_MAX_HEIGHT_DP = 360
private const val TIMELINE_GESTURE_IDLE_TIMEOUT_MS = 120L
private const val TIMELINE_FOLLOW_BOTTOM_KEY = "timeline_follow_bottom"

internal enum class TimelineScrollHandoffMode {
    LockedToPanel,
    EdgeGatedToParent
}

internal enum class TimelineScrollDirection {
    TowardTop,
    TowardBottom
}

internal enum class TimelineScrollEdge {
    Top,
    Bottom;

    val direction: TimelineScrollDirection
        get() = when (this) {
            Top -> TimelineScrollDirection.TowardTop
            Bottom -> TimelineScrollDirection.TowardBottom
        }
}

internal enum class TimelineScrollHandoffDecision {
    ConsumeInsidePanel,
    ReleaseToParent
}

internal data class TimelineScrollHandoffState(
    val activeGestureSessionId: Long = 0L,
    val isGestureActive: Boolean = false,
    val armedEdge: TimelineScrollEdge? = null,
    val armedSessionId: Long? = null,
    val lastDirection: TimelineScrollDirection? = null,
)

internal fun TimelineScrollHandoffState.beginGesture(
    direction: TimelineScrollDirection
): TimelineScrollHandoffState {
    val nextSessionId = if (isGestureActive) {
        activeGestureSessionId
    } else {
        activeGestureSessionId + 1
    }
    val keepArmedEdge = armedEdge?.direction == direction
    return copy(
        activeGestureSessionId = nextSessionId,
        isGestureActive = true,
        armedEdge = armedEdge.takeIf { keepArmedEdge },
        armedSessionId = armedSessionId.takeIf { keepArmedEdge },
        lastDirection = direction,
    )
}

internal fun TimelineScrollHandoffState.endGesture(): TimelineScrollHandoffState {
    return copy(
        isGestureActive = false,
        lastDirection = null,
    )
}

internal fun TimelineScrollHandoffState.onTimelineMoved(): TimelineScrollHandoffState {
    return copy(
        armedEdge = null,
        armedSessionId = null,
    )
}

internal fun TimelineScrollHandoffState.onEdgeReached(
    edge: TimelineScrollEdge
): Pair<TimelineScrollHandoffState, TimelineScrollHandoffDecision> {
    val shouldReleaseToParent = armedEdge == edge &&
        armedSessionId != null &&
        armedSessionId != activeGestureSessionId
    return if (shouldReleaseToParent) {
        copy(lastDirection = edge.direction) to TimelineScrollHandoffDecision.ReleaseToParent
    } else {
        copy(
            armedEdge = edge,
            armedSessionId = activeGestureSessionId,
            lastDirection = edge.direction,
        ) to TimelineScrollHandoffDecision.ConsumeInsidePanel
    }
}

internal fun timelineScrollDirectionFor(deltaY: Float): TimelineScrollDirection? {
    return when {
        deltaY > 0f -> TimelineScrollDirection.TowardTop
        deltaY < 0f -> TimelineScrollDirection.TowardBottom
        else -> null
    }
}

internal fun timelineScrollEdgeFor(
    listState: LazyListState,
    deltaY: Float
): TimelineScrollEdge? {
    return when {
        deltaY > 0f && !listState.canScrollBackward -> TimelineScrollEdge.Top
        deltaY < 0f && !listState.canScrollForward -> TimelineScrollEdge.Bottom
        else -> null
    }
}

internal fun isTimelineAtFollowBottom(
    visibleItems: List<LazyListItemInfo>,
    canScrollForward: Boolean,
    viewportEndOffset: Int,
): Boolean {
    if (visibleItems.any { it.key == TIMELINE_FOLLOW_BOTTOM_KEY }) {
        return true
    }
    val lastItem = visibleItems.lastOrNull() ?: return false
    return !canScrollForward ||
        lastItem.offset + lastItem.size <= viewportEndOffset + lastItem.size * 0.15f + 24f
}

/**
 * Activity timeline bottom sheet.
 *
 * Shows a chronological list of all activities during the generation.
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
            assistantId = assistantId,
            scrollHandoffMode = TimelineScrollHandoffMode.LockedToPanel,
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
    scrollHandoffMode: TimelineScrollHandoffMode = TimelineScrollHandoffMode.LockedToPanel,
    listState: LazyListState = rememberLazyListState(),
    onTimelineClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    var autoFollowCurrentEntry by remember { mutableStateOf(false) }
    val bottomFollowRequester = remember { BringIntoViewRequester() }
    var handoffState by remember(scrollHandoffMode) { mutableStateOf(TimelineScrollHandoffState()) }
    var gestureEndJob by remember { mutableStateOf<Job?>(null) }
    val timelineScrollLock = remember(scrollHandoffMode, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    autoFollowCurrentEntry = false
                    timelineScrollDirectionFor(available.y)?.let { direction ->
                        handoffState = handoffState.beginGesture(direction)
                        gestureEndJob?.cancel()
                        gestureEndJob = scope.launch {
                            delay(TIMELINE_GESTURE_IDLE_TIMEOUT_MS)
                            handoffState = handoffState.endGesture()
                        }
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y != 0f) {
                    autoFollowCurrentEntry = false
                    timelineScrollDirectionFor(available.y)?.let { direction ->
                        handoffState = handoffState.beginGesture(direction)
                    }
                    gestureEndJob?.cancel()
                }
                return Velocity.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y != 0f) {
                    handoffState = handoffState.onTimelineMoved()
                }
                if (available.y == 0f) {
                    return Offset.Zero
                }
                if (scrollHandoffMode == TimelineScrollHandoffMode.LockedToPanel) {
                    return Offset(x = 0f, y = available.y)
                }
                if (source != NestedScrollSource.UserInput) {
                    return Offset(x = 0f, y = available.y)
                }
                val edge = timelineScrollEdgeFor(listState, available.y)
                    ?: return Offset(x = 0f, y = available.y)
                val (nextState, decision) = handoffState.onEdgeReached(edge)
                handoffState = nextState
                return if (decision == TimelineScrollHandoffDecision.ConsumeInsidePanel) {
                    Offset(x = 0f, y = available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (consumed.y != 0f) {
                    handoffState = handoffState.onTimelineMoved()
                }
                val result = when {
                    available.y == 0f -> Velocity.Zero
                    scrollHandoffMode == TimelineScrollHandoffMode.LockedToPanel -> Velocity(x = 0f, y = available.y)
                    else -> {
                        val edge = timelineScrollEdgeFor(listState, available.y)
                        if (edge == null) {
                            Velocity(x = 0f, y = available.y)
                        } else {
                            val (nextState, decision) = handoffState.onEdgeReached(edge)
                            handoffState = nextState
                            if (decision == TimelineScrollHandoffDecision.ConsumeInsidePanel) {
                                Velocity(x = 0f, y = available.y)
                            } else {
                                Velocity.Zero
                            }
                        }
                    }
                }
                gestureEndJob?.cancel()
                handoffState = handoffState.endGesture()
                return result
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

    var editTarget by remember { mutableStateOf<MemoryEditTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<MemoryDeleteTarget?>(null) }
    var deletedMemoryIds by remember { mutableStateOf(setOf<Int>()) }
    val context = LocalContext.current
    val toaster = runCatching { LocalToaster.current }.getOrNull()
    val entryIds = remember(entries) { entries.map { it.id } }
    val currentEntryIndex = remember(entries) {
        findCurrentEntryIndex(entries)
    }
    val currentEntryId = remember(entries, currentEntryIndex) {
        currentEntryIndex?.let { entries[it].id }
    }
    val currentEntryFollowSignature = remember(entries, currentEntryIndex) {
        currentEntryIndex?.let { buildEntryFollowSignature(entries[it]) }.orEmpty()
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
    }

    LaunchedEffect(initialOpenRequest) {
        val initialFocus = buildInitialTimelineFocus(entries, initialOpenRequest)
        autoFollowCurrentEntry = initialOpenRequest?.openMode == TimelineOpenMode.FocusCurrent

        val scrollIndex = initialFocus.scrollIndex
        if (scrollIndex != null) {
            listState.scrollToItem(scrollIndex)
        }
    }

    LaunchedEffect(listState, currentEntryId) {
        snapshotFlow {
            isTimelineAtFollowBottom(
                visibleItems = listState.layoutInfo.visibleItemsInfo,
                canScrollForward = listState.canScrollForward,
                viewportEndOffset = listState.layoutInfo.viewportEndOffset,
            )
        }
            .collect { isAtBottom ->
                if (isAtBottom && currentEntryId != null) {
                    autoFollowCurrentEntry = true
                }
            }
    }

    LaunchedEffect(autoFollowCurrentEntry, currentEntryId, currentEntryFollowSignature) {
        val followIndex = currentEntryIndex ?: return@LaunchedEffect
        if (!autoFollowCurrentEntry) return@LaunchedEffect

        if (followIndex == entries.lastIndex) {
            try {
                bottomFollowRequester.bringIntoView()
            } catch (_: IllegalStateException) {
                // Animated visibility can briefly detach the follow anchor during live updates.
            }
        } else {
            listState.scrollToItem(followIndex)
        }
    }

    Surface(
        shape = AppShapes.InputField,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_timeline_panel")
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTimelineClick
            )
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
                        .fadeEdges(fadeTop = true, fadeBottom = true)
                        .testTag("activity_timeline_list")
                        .nestedScroll(timelineScrollLock),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp)
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { index, entry -> "${entry.id}:$index" }
                    ) { index, entry ->
                        TimelineEntryItem(
                            entry = entry,
                            showDivider = index > 0,
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
                                currentEntryId == entry.id,
                            onClickEntry = onTimelineClick,
                            onCopyEntry = {
                                val text = buildTimelineCopyText(entry)
                                if (text.isBlank()) {
                                    haptics.perform(HapticPattern.Pop)
                                    val message = context.getString(R.string.no_text_content_to_copy)
                                    toaster?.show(message)
                                        ?: Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                } else {
                                    context.writeClipboardText(text)
                                    haptics.perform(HapticPattern.Success)
                                    val message = context.getString(R.string.chat_page_export_copied)
                                    toaster?.show(message = message, type = ToastType.Success)
                                        ?: Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    if (entries.size > 1) {
                        item(key = "footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pluralStringResource(R.plurals.activity_timeline_steps, entries.size, entries.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item(key = TIMELINE_FOLLOW_BOTTOM_KEY) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .bringIntoViewRequester(bottomFollowRequester)
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
                    text = target.content
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineEntryItem(
    entry: TimelineEntry,
    showDivider: Boolean,
    isLocallyDeleted: Boolean,
    onEditMemory: (Int, String) -> Unit,
    onDeleteMemory: (Int, String?) -> Unit,
    onRestoreMemory: (String) -> Unit,
    onRevertMemory: (Int, String) -> Unit,
    canRestore: Boolean,
    followLiveContent: Boolean,
    onClickEntry: () -> Unit,
    onCopyEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasContent = when (entry) {
        is TimelineEntry.Reasoning -> entry.content.isNotBlank()
        is TimelineEntry.ToolCall -> entry.argumentsText.isNotBlank() ||
            entry.resultText != null ||
            entry.argumentsJson != null ||
            entry.resultJson != null
        is TimelineEntry.MemoryAction -> true
        is TimelineEntry.Ocr -> true
        is TimelineEntry.Reply -> entry.content.isNotBlank()
    }
    val isMemoryDeleted = (entry is TimelineEntry.MemoryAction &&
        entry.operation == MemoryOperation.DELETE) || isLocallyDeleted
    val accentColor = if (isMemoryDeleted) {
        MaterialTheme.colorScheme.error
    } else {
        getTimelineAccentColor(entry)
    }

    val viewRequester = remember { BringIntoViewRequester() }
    val followSignature = remember(entry) { buildEntryFollowSignature(entry) }
    val isSingleEntry = !showDivider

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_entry_${entry.id}")
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClickEntry,
                onLongClick = onCopyEntry
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val durationLabel = if (entry is TimelineEntry.Reasoning) {
            formatTimelineDuration(entry.durationMs)?.let { " · $it" } ?: ""
        } else ""

        if (!isSingleEntry) {
            TimelineDivider(
                label = getTimelineLabel(entry) + durationLabel,
                icon = getTimelineIcon(entry),
                color = accentColor,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (hasContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimelineExpandedContent(
                    entry = entry,
                    isDeleted = isMemoryDeleted,
                    onEditMemory = onEditMemory,
                    onDeleteMemory = onDeleteMemory,
                    onRestoreMemory = onRestoreMemory,
                    onRevertMemory = onRevertMemory,
                    canRestore = canRestore,
                    followLiveContent = followLiveContent
                )
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .bringIntoViewRequester(viewRequester)
                )
            }
        }
    }
    LaunchedEffect(followLiveContent, followSignature) {
        if (followLiveContent) {
            try {
                viewRequester.bringIntoView()
            } catch (_: IllegalStateException) {
                // The row may be leaving composition while live content is still updating.
            }
        }
    }
}

internal fun buildTimelineCopyText(entry: TimelineEntry): String {
    return when (entry) {
        is TimelineEntry.Reasoning -> entry.content
        is TimelineEntry.Reply -> entry.content
        is TimelineEntry.Ocr -> buildList {
            entry.fileName?.takeIf { it.isNotBlank() }?.let { add("File: $it") }
            add("Source: ${entry.source}")
            if (entry.pageNumbers.isNotEmpty()) {
                add("Pages: ${entry.pageNumbers.joinToString(", ")}")
            }
        }.joinToString("\n")
        is TimelineEntry.MemoryAction -> buildList {
            add("Memory ${entry.operation.name.lowercase()}")
            entry.memoryId?.let { add("ID: $it") }
            entry.previousContent?.takeIf { it.isNotBlank() }?.let { add("Before:\n$it") }
            entry.content?.takeIf { it.isNotBlank() }?.let { add("Content:\n$it") }
        }.joinToString("\n\n")
        is TimelineEntry.ToolCall -> buildToolCopyText(entry)
    }.trim()
}

private fun buildToolCopyText(entry: TimelineEntry.ToolCall): String {
    val header = entry.displayName.ifBlank { entry.toolName }
    return when (entry.toolName) {
        "search_web" -> buildSearchCopyText(entry, header)
        "search_memory" -> buildMemoryRecallCopyText(entry, header)
        "eval_python" -> buildPythonCopyText(entry, header)
        in SANDBOX_FILE_TOOLS -> buildSandboxFileCopyText(entry, header)
        in WORKSPACE_TOOLS -> buildWorkspaceCopyText(entry, header)
        "manage_skills" -> buildSkillCopyText(entry, header)
        else -> buildGenericToolCopyText(entry, header)
    }.trim()
}

private fun buildSearchCopyText(entry: TimelineEntry.ToolCall, header: String): String {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject
    val items = (resultObj?.get("items") as? JsonArray) ?: JsonArray(emptyList())
    return buildList {
        add(header)
        argsObj?.get("query")?.jsonPrimitiveOrNull?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { add("Query: $it") }
        resultObj?.get("answer")?.jsonPrimitiveOrNull?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { add("Answer:\n$it") }
        items.forEachIndexed { index, item ->
            val obj = item as? JsonObject ?: return@forEachIndexed
            val title = obj["title"]?.jsonPrimitiveOrNull?.contentOrNull
            val url = obj["url"]?.jsonPrimitiveOrNull?.contentOrNull
            val text = obj["text"]?.jsonPrimitiveOrNull?.contentOrNull
            add(
                buildList {
                    add("Source ${index + 1}:")
                    title?.takeIf { it.isNotBlank() }?.let { add(it) }
                    url?.takeIf { it.isNotBlank() }?.let { add(it) }
                    text?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString("\n")
            )
        }
    }.joinToString("\n\n")
}

private fun buildMemoryRecallCopyText(entry: TimelineEntry.ToolCall, header: String): String {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject
    return buildList {
        add(header)
        argsObj?.get("query")?.jsonPrimitiveOrNull?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { add("Query: $it") }
        resultObj?.get("summary")?.jsonPrimitiveOrNull?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { add("Summary:\n$it") }
        resultObj?.let { add(JsonInstantPretty.encodeToString(it)) }
    }.distinct().joinToString("\n\n")
}

private fun buildPythonCopyText(entry: TimelineEntry.ToolCall, header: String): String {
    val summary = buildPythonToolSummary(entry.argumentsJson, entry.resultJson)
        ?: return buildGenericToolCopyText(entry, header)
    return buildList {
        add(header)
        summary.code.takeIf { it.isNotBlank() }?.let { add("Code:\n$it") }
        summary.stdout?.takeIf { it.isNotBlank() }?.let { add("Stdout:\n$it") }
        summary.stderr?.takeIf { it.isNotBlank() }?.let { add("Stderr:\n$it") }
        summary.result?.takeIf { it.isNotBlank() && it != "null" }?.let { add("Result:\n$it") }
        summary.error?.takeIf { it.isNotBlank() }?.let { add("Error:\n$it") }
    }.joinToString("\n\n")
}

private fun buildSandboxFileCopyText(entry: TimelineEntry.ToolCall, header: String): String {
    val summary = buildSandboxFileToolSummary(entry.toolName, entry.argumentsJson, entry.resultJson)
        ?: return buildGenericToolCopyText(entry, header)
    return buildList {
        add(header)
        summary.path?.takeIf { it.isNotBlank() }?.let { add("Path: $it") }
        summary.uri?.takeIf { it.isNotBlank() }?.let { add("URI: $it") }
        summary.fileCount?.let { add("Files: $it") }
        summary.success?.let { add("Success: $it") }
        summary.error?.takeIf { it.isNotBlank() }?.let { add("Error: $it") }
    }.joinToString("\n")
}

private fun buildWorkspaceCopyText(entry: TimelineEntry.ToolCall, header: String): String {
    val summary = buildWorkspaceToolSummary(entry.toolName, entry.argumentsJson, entry.resultJson)
        ?: return buildGenericToolCopyText(entry, header)
    return buildList {
        add(header)
        summary.command?.takeIf { it.isNotBlank() }?.let { add("Command: $it") }
        summary.path?.takeIf { it.isNotBlank() }?.let { add("Path: $it") }
        summary.cwd?.takeIf { it.isNotBlank() }?.let { add("CWD: $it") }
        summary.exitCode?.let { add("Exit code: $it") }
        summary.stdout?.takeIf { it.isNotBlank() }?.let { add("Stdout:\n$it") }
        summary.stderr?.takeIf { it.isNotBlank() }?.let { add("Stderr:\n$it") }
        summary.text?.takeIf { it.isNotBlank() }?.let { add("Text:\n$it") }
        summary.error?.takeIf { it.isNotBlank() }?.let { add("Error:\n$it") }
    }.joinToString("\n\n")
}

private fun buildSkillCopyText(entry: TimelineEntry.ToolCall, header: String): String {
    val summary = getSkillChangeSummary(entry)
    return buildList {
        add(header)
        if (summary.activated.isNotEmpty()) add("Activated: ${summary.activated.joinToString(", ")}")
        if (summary.disabled.isNotEmpty()) add("Disabled: ${summary.disabled.joinToString(", ")}")
    }.joinToString("\n")
}

private fun buildGenericToolCopyText(entry: TimelineEntry.ToolCall, header: String): String {
    val argumentsPretty = entry.argumentsJson?.let { JsonInstantPretty.encodeToString(it) }
        ?: entry.argumentsText
    val resultPretty = entry.resultJson?.let { JsonInstantPretty.encodeToString(it) }
        ?: entry.resultText
    return buildList {
        add(header)
        argumentsPretty.takeIf { it.isNotBlank() && it != "{}" }?.let { add("Arguments:\n$it") }
        resultPretty?.takeIf { it.isNotBlank() && it != "null" }?.let { add("Result:\n$it") }
    }.joinToString("\n\n")
}

@Composable
private fun TimelineDivider(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.2f))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.2f))
        )
    }
}

@Composable
private fun ToolCallDetails(entry: TimelineEntry.ToolCall) {
    when (entry.toolName) {
        "search_web" -> SearchTimelineDetails(entry)
        "search_memory" -> MemoryRecallTimelineDetails(entry)
        "scrape_web" -> ScrapeTimelineDetails(entry)
        "eval_python" -> PythonTimelineDetails(entry)
        in SANDBOX_FILE_TOOLS -> SandboxFileTimelineDetails(entry)
        in WORKSPACE_TOOLS -> WorkspaceTimelineDetails(entry)
        "ask_user" -> AskUserTimelineDetails(entry)
        "manage_skills" -> SkillManagementTimelineDetails(entry)
        else -> GenericToolDetails(entry)
    }
}

@Composable
private fun MemoryRecallTimelineDetails(entry: TimelineEntry.ToolCall) {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject
    val query = argsObj?.get("query")?.jsonPrimitiveOrNull?.contentOrNull
    val requestedTime = argsObj?.get("time_range")?.jsonPrimitiveOrNull?.contentOrNull
    val resolvedTime = resultObj?.get("time_filter")?.jsonPrimitiveOrNull?.contentOrNull
    val summary = resultObj?.get("summary")?.jsonPrimitiveOrNull?.contentOrNull
    val results = (resultObj?.get("results") as? JsonArray) ?: JsonArray(emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!query.isNullOrBlank()) {
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_query),
                value = query
            )
        }
        (resolvedTime ?: requestedTime)?.takeIf { it.isNotBlank() }?.let { range ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_memory_time_filter),
                value = range
            )
        }

        if (!summary.isNullOrBlank()) {
            Surface(
                shape = AppShapes.CardSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (results.isNotEmpty()) {
            Text(
                text = stringResource(R.string.activity_timeline_memory_findings, results.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEach { item ->
                    val obj = item as? JsonObject ?: return@forEach
                    val source = obj["source"]?.jsonPrimitiveOrNull?.contentOrNull
                    val title = obj["conversation_title"]?.jsonPrimitiveOrNull?.contentOrNull
                    val timeAgo = obj["time_ago"]?.jsonPrimitiveOrNull?.contentOrNull
                    val confidence = obj["confidence"]?.jsonPrimitiveOrNull?.contentOrNull
                    val itemSummary = obj["summary"]?.jsonPrimitiveOrNull?.contentOrNull
                    val matchedText = obj["matched_text"]?.jsonPrimitiveOrNull?.contentOrNull

                    Surface(
                        shape = AppShapes.CardSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (source) {
                                        "core_memory" -> stringResource(R.string.activity_timeline_memory_source_core)
                                        "past_chat" -> stringResource(R.string.activity_timeline_memory_source_chat)
                                        else -> source.orEmpty().ifBlank { stringResource(R.string.activity_timeline_tool_search_memory) }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                timeAgo?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            title?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!itemSummary.isNullOrBlank()) {
                                Text(
                                    text = itemSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!matchedText.isNullOrBlank() && matchedText != itemSummary) {
                                Text(
                                    text = matchedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            confidence?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = stringResource(R.string.activity_timeline_memory_confidence, it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        } else if (summary.isNullOrBlank()) {
            GenericToolDetails(entry)
        }
    }
}

@Composable
private fun buildOcrPreviewText(entry: TimelineEntry.Ocr): String {
    val pagesText = if (entry.pageNumbers.isEmpty()) {
        null
    } else {
        stringResource(
            R.string.activity_timeline_ocr_pages_value,
            entry.pageNumbers.joinToString(", ")
        )
    }

    return listOfNotNull(entry.fileName, pagesText).joinToString(" - ").ifBlank {
        stringResource(R.string.activity_timeline_ocr)
    }
}

@Composable
private fun OcrDetails(entry: TimelineEntry.Ocr) {
    val sourceLabel = stringResource(
        when (entry.source) {
            me.rerere.ai.ui.UIMessageAnnotation.OcrActivity.Source.IMAGE -> R.string.activity_timeline_ocr_source_image
            me.rerere.ai.ui.UIMessageAnnotation.OcrActivity.Source.PDF -> R.string.activity_timeline_ocr_source_pdf
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_file_name),
                value = fileName
            )
        }
        TimelineFieldRow(
            label = stringResource(R.string.activity_timeline_ocr_source),
            value = sourceLabel
        )
        if (entry.pageNumbers.isNotEmpty()) {
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_ocr_pages),
                value = stringResource(
                    R.string.activity_timeline_ocr_pages_value,
                    entry.pageNumbers.joinToString(", ")
                )
            )
        }
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
    canRestore: Boolean,
    followLiveContent: Boolean = false
) {
    when (entry) {
        is TimelineEntry.Reasoning -> {
            MarkdownBlock(
                content = entry.content,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                paragraphSpacing = 8.dp,
                streamingTextReveal = followLiveContent
            )
        }

        is TimelineEntry.ToolCall -> {
            ToolCallCompactDetails(entry = entry)
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

        is TimelineEntry.Ocr -> {
            OcrDetails(entry)
        }

        is TimelineEntry.Reply -> {
            MarkdownBlock(
                content = entry.content,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                paragraphSpacing = 8.dp,
                streamingTextReveal = followLiveContent
            )
        }
    }
}

@Composable
private fun ToolCallCompactDetails(entry: TimelineEntry.ToolCall) {
    when (entry.toolName) {
        "search_web" -> SearchTimelineCompactDetails(entry)
        "search_memory" -> MemoryRecallTimelineCompactDetails(entry)
        "scrape_web" -> ScrapeTimelineCompactDetails(entry)
        "eval_python" -> PythonTimelineCompactDetails(entry)
        in SANDBOX_FILE_TOOLS -> SandboxFileTimelineCompactDetails(entry)
        in WORKSPACE_TOOLS -> WorkspaceTimelineCompactDetails(entry)
        "ask_user" -> AskUserTimelineCompactDetails(entry)
        "manage_skills" -> SkillManagementTimelineCompactDetails(entry)
        else -> GenericToolCompactDetails(entry)
    }
}

@Composable
private fun SearchTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject
    val query = argsObj?.get("query")?.jsonPrimitiveOrNull?.contentOrNull
    val answer = resultObj?.get("answer")?.jsonPrimitiveOrNull?.contentOrNull
    val items = (resultObj?.get("items") as? JsonArray) ?: JsonArray(emptyList())
    val context = LocalContext.current
    val sourceLinks = items.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val url = obj["url"]?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        host to url
    }.distinctBy { it.first }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        query?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        answer?.takeIf { it.isNotBlank() && it != query }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (sourceLinks.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sourceLinks.forEach { (host, url) ->
                    TimelineTagChip(
                        text = host,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryRecallTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject
    val query = argsObj?.get("query")?.jsonPrimitiveOrNull?.contentOrNull
    val summary = resultObj?.get("summary")?.jsonPrimitiveOrNull?.contentOrNull
    val results = (resultObj?.get("results") as? JsonArray) ?: JsonArray(emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        query?.takeIf { it.isNotBlank() }?.let {
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_query),
                value = it
            )
        }
        summary?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (results.isNotEmpty()) {
            TimelineTagChip(
                text = stringResource(R.string.activity_timeline_memory_findings, results.size),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ScrapeTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    val argsObj = entry.argumentsJson as? JsonObject
    val resultObj = entry.resultJson as? JsonObject
    val url = argsObj?.get("url")?.jsonPrimitiveOrNull?.contentOrNull
    val content = resultObj?.get("content")?.jsonPrimitiveOrNull?.contentOrNull
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        url?.takeIf { it.isNotBlank() }?.let {
            TimelineTagChip(
                text = runCatching { Uri.parse(it).host }.getOrNull()?.takeIf { host -> host.isNotBlank() } ?: it,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        content?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PythonTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    val summary = buildPythonToolSummary(
        arguments = entry.argumentsJson,
        content = entry.resultJson
    ) ?: return GenericToolCompactDetails(entry)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        summary.code.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
        (summary.error ?: summary.result?.takeIf { it != "null" } ?: summary.stdout ?: summary.stderr)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!summary.error.isNullOrBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
    }
}

@Composable
private fun SandboxFileTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    val summary = buildSandboxFileToolSummary(
        toolName = entry.toolName,
        arguments = entry.argumentsJson,
        content = entry.resultJson
    ) ?: return GenericToolCompactDetails(entry)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            summary.path?.takeIf { it.isNotBlank() }?.let {
                TimelineTagChip(
                    text = it.substringAfterLast("/").substringAfterLast("\\"),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.fileCount?.let {
                TimelineTagChip(
                    text = stringResource(R.string.activity_timeline_file_count_value, it),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            summary.success?.let {
                TimelineTagChip(
                    text = stringResource(if (it) R.string.activity_timeline_status_success else R.string.activity_timeline_status_failed),
                    containerColor = if (it) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (it) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        summary.error?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun WorkspaceTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    val summary = buildWorkspaceToolSummary(
        toolName = entry.toolName,
        arguments = entry.argumentsJson,
        content = entry.resultJson
    ) ?: return GenericToolCompactDetails(entry)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (summary.command ?: summary.path ?: summary.name)?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            summary.exitCode?.let {
                TimelineTagChip(
                    text = "exit $it",
                    containerColor = if (it == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (it == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (summary.timedOut == true) {
                TimelineTagChip(
                    text = stringResource(R.string.activity_timeline_workspace_timed_out),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (summary.truncated == true) {
                TimelineTagChip(
                    text = stringResource(R.string.activity_timeline_workspace_truncated),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        (summary.error ?: summary.stderr ?: summary.stdout ?: summary.text)?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (!summary.error.isNullOrBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun AskUserTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    Text(
        text = buildAskUserPreviewText(entry),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SkillManagementTimelineCompactDetails(entry: TimelineEntry.ToolCall) {
    val summary = getSkillChangeSummary(entry)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        summary.activated.forEach {
            TimelineTagChip(
                text = it,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        summary.disabled.forEach {
            TimelineTagChip(
                text = it,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (summary.activated.isEmpty() && summary.disabled.isEmpty()) {
            Text(
                text = stringResource(R.string.activity_timeline_no_skill_changes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GenericToolCompactDetails(entry: TimelineEntry.ToolCall) {
    GenericToolDetails(entry)
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
                                color = MaterialTheme.colorScheme.onSurface
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun AskUserTimelineDetails(entry: TimelineEntry.ToolCall) {
    val state = remember(entry) { parseAskUserTimelineState(entry) } ?: return GenericToolDetails(entry)
    val answersById = remember(state.payload) {
        state.payload?.answers.orEmpty().associateBy { it.id }
    }
    val answeredCount = remember(state.payload) {
        state.payload?.answers.orEmpty().count { answer ->
            answer.status == "answered" && !answer.value.isNullOrBlank()
        }
    }
    val isDismissed = state.payload?.dismissed == true

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TimelineTagChip(
                text = stringResource(
                    R.string.activity_timeline_questionnaire_count,
                    state.questionnaire.questions.size
                ),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (answeredCount > 0) {
                TimelineTagChip(
                    text = stringResource(
                        R.string.activity_timeline_questionnaire_answered_count,
                        answeredCount
                    ),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            when {
                isDismissed -> TimelineTagChip(
                    text = stringResource(R.string.activity_timeline_questionnaire_dismissed),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                entry.isLoading -> TimelineTagChip(
                    text = stringResource(R.string.activity_timeline_questionnaire_pending),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.questionnaire.questions.forEachIndexed { index, question ->
            AskUserQuestionTimelineCard(
                index = index,
                totalQuestions = state.questionnaire.questions.size,
                question = question,
                answer = answersById[question.id],
                questionnaireDismissed = isDismissed
            )
        }
    }
}

@Composable
private fun AskUserQuestionTimelineCard(
    index: Int,
    totalQuestions: Int,
    question: AskUserQuestion,
    answer: AskUserAnswer?,
    questionnaireDismissed: Boolean,
) {
    val selectedOption = answer?.value?.takeIf { answer.source == "option" }
    val answerValue = answer?.value?.takeIf { it.isNotBlank() }
    val showCustomAnswer = answerValue != null && (answer.source != "option" ||
        question.options.none { option -> option.label == answerValue })

    Surface(
        shape = AppShapes.CardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.character_questions_progress, index + 1, totalQuestions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = question.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (question.options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    question.options.forEach { option ->
                        val isSelected = selectedOption == option.label
                        Surface(
                            shape = AppShapes.CardSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                } else {
                                    Color.Transparent
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                option.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showCustomAnswer) {
                TimelineDetailBlock(
                    label = stringResource(
                        if (answer?.source == "option") {
                            R.string.activity_timeline_questionnaire_source_option
                        } else {
                            R.string.activity_timeline_questionnaire_source_custom
                        }
                    ),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = answerValue.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            } else if (answer?.status == "skipped" && !questionnaireDismissed) {
                TimelineTagChip(
                    text = stringResource(R.string.activity_timeline_questionnaire_skipped),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
private fun WorkspaceTimelineDetails(entry: TimelineEntry.ToolCall) {
    val summary = buildWorkspaceToolSummary(
        toolName = entry.toolName,
        arguments = entry.argumentsJson,
        content = entry.resultJson
    ) ?: return GenericToolDetails(entry)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        summary.command?.takeIf { it.isNotBlank() }?.let { command ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_command),
                value = command,
                monospace = true
            )
        }
        summary.path?.takeIf { it.isNotBlank() }?.let { path ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_file_path),
                value = path,
                monospace = true
            )
        }
        summary.cwd?.takeIf { it.isNotBlank() }?.let { cwd ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_cwd),
                value = cwd,
                monospace = true
            )
        }
        summary.timeout?.takeIf { it.isNotBlank() }?.let { timeout ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_timeout),
                value = timeout
            )
        }
        summary.exitCode?.let { exitCode ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_exit_code),
                value = exitCode.toString(),
                valueColor = if (exitCode == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        summary.timedOut?.let { timedOut ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_timed_out),
                value = if (timedOut) {
                    stringResource(R.string.activity_timeline_value_yes)
                } else {
                    stringResource(R.string.activity_timeline_value_no)
                },
                valueColor = if (timedOut) MaterialTheme.colorScheme.error else null
            )
        }
        summary.truncated?.let { truncated ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_truncated),
                value = if (truncated) {
                    stringResource(R.string.activity_timeline_value_yes)
                } else {
                    stringResource(R.string.activity_timeline_value_no)
                }
            )
        }
        summary.name?.takeIf { it.isNotBlank() }?.let { name ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_file_name),
                value = name,
                monospace = true
            )
        }
        summary.isDirectory?.let { isDirectory ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_entry_type),
                value = stringResource(
                    if (isDirectory) {
                        R.string.activity_timeline_workspace_directory
                    } else {
                        R.string.activity_timeline_workspace_file
                    }
                )
            )
        }
        summary.sizeBytes?.let { sizeBytes ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_size),
                value = stringResource(R.string.activity_timeline_workspace_size_bytes, sizeBytes)
            )
        }
        summary.updatedAt?.let { updatedAt ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_workspace_updated),
                value = updatedAt.toString()
            )
        }
        summary.text?.takeIf { it.isNotBlank() }?.let { text ->
            TimelineDetailBlock(
                label = stringResource(R.string.activity_timeline_content),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        summary.stdout?.takeIf { it.isNotBlank() }?.let { stdout ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_python_stdout),
                value = stdout,
                monospace = true
            )
        }
        summary.stderr?.takeIf { it.isNotBlank() }?.let { stderr ->
            TimelineFieldRow(
                label = stringResource(R.string.activity_timeline_python_stderr),
                value = stderr,
                valueColor = MaterialTheme.colorScheme.error,
                monospace = true
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
        if (
            summary.command != null &&
            summary.exitCode == 0 &&
            summary.stdout.isNullOrBlank() &&
            summary.stderr.isNullOrBlank()
        ) {
            Text(
                text = stringResource(R.string.activity_timeline_no_output),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                text = resultPretty,
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
private fun TimelineTagChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = AppShapes.Chip,
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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
private fun buildAskUserPreviewText(entry: TimelineEntry.ToolCall): String {
    val state = remember(entry) { parseAskUserTimelineState(entry) } ?: return entry.argumentsText
    val answeredCount = remember(state.payload) {
        state.payload?.answers.orEmpty().count { answer ->
            answer.status == "answered" && !answer.value.isNullOrBlank()
        }
    }

    return when {
        answeredCount > 0 -> stringResource(
            R.string.activity_timeline_questionnaire_answered_summary,
            answeredCount,
            state.questionnaire.questions.size
        )

        state.payload?.dismissed == true -> stringResource(
            R.string.activity_timeline_questionnaire_dismissed
        )

        entry.isLoading -> state.questionnaire.questions.firstOrNull()?.question.orEmpty()

        else -> stringResource(
            R.string.activity_timeline_questionnaire_count,
            state.questionnaire.questions.size
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
