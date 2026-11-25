package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.*
import me.rerere.rikkahub.ui.pages.setting.components.AddonEmptyState
import me.rerere.rikkahub.ui.pages.setting.components.addonItemPosition
import me.rerere.rikkahub.ui.pages.setting.components.addonItemShape
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus

@Composable
internal fun AdditionalContextContent(
    settings: Settings,
    onEdit: (AdditionalContext) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    onUpdateSettings: ((Settings) -> Settings) -> Unit = {},
) {
    if (settings.additionalContexts.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            AddonEmptyState(Icons.Rounded.Notes, "No additional context")
        }
        return
    }
    val toaster = LocalToaster.current
    val undoLabel = stringResource(R.string.undo)
    val haptics = rememberPremiumHaptics()
    val neighbors = rememberSwipeNeighborState()
    val order = rememberReorderableItems(settings.additionalContexts, key = { it.id }) { keys ->
        onUpdateSettings { latest -> latest.copy(additionalContexts = latest.additionalContexts.reorderedByKeys(keys) { it.id }) }
    }
    val entries = order.items
    val reorderable = rememberAppReorderableLazyListState(listState, order,
        scrollThresholdPadding = contentPadding + PaddingValues(bottom = 45.dp))
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding), state = listState,
        contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 45.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(entries, key = { _, it -> it.id }) { index, snippet ->
            val position = addonItemPosition(index, entries.size)
            AppReorderableItem(state = reorderable, key = snippet.id) { _ ->
                PhysicsSwipeToDelete(
                    position = position, modifier = Modifier.fillMaxWidth(),
                    neighborOffset = neighbors.offsetFor(index, entries) { it.id },
                    onDragProgress = { offset, unlocked -> neighbors.update(snippet.id, offset, unlocked) },
                    onDragEnd = { neighbors.end(snippet.id) },
                    onDelete = {
                        onUpdateSettings { latest -> latest.copy(additionalContexts = latest.additionalContexts.filterNot { it.id == snippet.id }) }
                        toaster.show("${snippet.name.ifBlank { "Context" }} deleted", action = ToastAction(undoLabel) {
                            onUpdateSettings { latest ->
                                if (latest.additionalContexts.any { it.id == snippet.id }) latest else latest.copy(
                                    additionalContexts = latest.additionalContexts.toMutableList().apply { add(index.coerceIn(0, size), snippet) })
                            }
                        })
                    },
                ) {
                    Card(onClick = { haptics.perform(HapticPattern.Pop); onEdit(snippet) },
                        shape = addonItemShape(position),
                        colors = CardDefaults.cardColors(containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(snippet.name.ifBlank { "Untitled context" }, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (snippet.prompt.isNotBlank()) Text(snippet.prompt, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = {}, modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = { haptics.perform(HapticPattern.Pop) }, onDragStopped = { haptics.perform(HapticPattern.Thud) })) {
                                Icon(Icons.Rounded.DragIndicator, stringResource(R.string.drag_to_reorder))
                            }
                        }
                    }
                }
            }
        }
    }
}
