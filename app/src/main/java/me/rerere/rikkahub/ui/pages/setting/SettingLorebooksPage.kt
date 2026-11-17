package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.utils.plus
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import me.rerere.rikkahub.ui.hooks.rememberSwipeNeighborState
import me.rerere.rikkahub.ui.hooks.rememberReorderableItems
import me.rerere.rikkahub.ui.hooks.reorderedByKeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.collectMediaFileRefs
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.AddonEmptyState
import me.rerere.rikkahub.ui.pages.setting.components.AddonListItem
import org.koin.androidx.compose.koinViewModel
import me.rerere.rikkahub.ui.hooks.AppReorderableItem
import me.rerere.rikkahub.ui.hooks.rememberAppReorderableLazyListState

@Composable
fun SettingLorebooksPage(vm: SettingVM = koinViewModel()) {
    SettingPromptInjectionsPage(initialPage = 2, vm = vm)
}

@Composable
fun LorebooksPageContent(
    settings: Settings,
    vm: SettingVM,
    haptics: PremiumHaptics,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current

    val swipeNeighbors = rememberSwipeNeighborState()
    val itemOrder = rememberReorderableItems(settings.lorebooks, key = { it.id }) { order ->
        vm.updateSettings { latest -> latest.copy(lorebooks = latest.lorebooks.reorderedByKeys(order) { it.id }) }
    }
    val orderedLorebooks = itemOrder.items
    val canDelete = true


    val reorderableState = rememberAppReorderableLazyListState(
        listState, itemOrder,
        scrollThresholdPadding = contentPadding + PaddingValues(bottom = 40.dp),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = listState,
            contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (orderedLorebooks.isEmpty()) {
                item(key = "empty") {
                    AddonEmptyState(icon = Icons.Rounded.Book,
                        title = stringResource(R.string.lorebooks_page_empty_title),
                        modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            } else {
                itemsIndexed(
                    items = orderedLorebooks,
                    key = { _, lorebook -> lorebook.id }
                ) { index, lorebook ->
                    val position = when {
                        orderedLorebooks.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == orderedLorebooks.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }

                    val neighborOffset = swipeNeighbors.offsetFor(index, orderedLorebooks) { it.id }

                    AppReorderableItem(
                        state = reorderableState,
                        key = lorebook.id
                    ) { isDragging ->
                        PhysicsSwipeToDelete(
                            position = position,
                            deleteEnabled = canDelete,
                            neighborOffset = neighborOffset,
                            onDragProgress = { offset, unlocked -> swipeNeighbors.update(lorebook.id, offset, unlocked) },
                            onDragEnd = { swipeNeighbors.end(lorebook.id) },
                            onDelete = {
                                val deletedLorebook = lorebook
                                val cleanupKey = "lorebook:${deletedLorebook.id}"
                                vm.updateSettings { settings -> settings.copy(lorebooks = settings.lorebooks.filter { it.id != lorebook.id }) }
                                vm.cleanupFilesIfUnreferenced(
                                    fileRefs = deletedLorebook.collectMediaFileRefs(),
                                    delayMs = me.rerere.rikkahub.data.deletion.DESTRUCTIVE_UNDO_WINDOW_MS,
                                    cleanupKey = cleanupKey,
                                )
                                toaster.show(
                                    message = context.getString(
                                        R.string.lorebooks_page_deleted,
                                        lorebook.name.ifEmpty { context.getString(R.string.lorebooks_page_unnamed) }
                                    ),
                                    duration = me.rerere.rikkahub.data.deletion.DESTRUCTIVE_UNDO_WINDOW_MS,
                                    action = ToastAction(
                                        label = context.getString(R.string.undo),
                                        onClick = {
                                            vm.cancelUnreferencedFileCleanup(cleanupKey)
                                            vm.updateSettings { settings -> settings.copy(lorebooks = settings.lorebooks.toMutableList().apply {
                                                    add(index.coerceAtMost(size), deletedLorebook)
                                                }) }
                                        }
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        ) { _ ->
                            LorebookCard(
                                lorebook = lorebook,
                                position = position,
                                onClick = {
                                    navController.navigate(Screen.SettingLorebookDetail(lorebook.id.toString()))
                                },
                                dragHandle = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                haptics.perform(HapticPattern.Pop)
                                            },
                                            onDragStopped = {
                                                haptics.perform(HapticPattern.Thud)
                                            }
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = stringResource(R.string.drag_to_reorder)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }


    }
}

@Composable
private fun LorebookCard(
    lorebook: Lorebook,
    position: ItemPosition,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    AddonListItem(
        title = lorebook.name.ifBlank { stringResource(R.string.lorebooks_page_unnamed) },
        subtitle = listOf(stringResource(R.string.lorebooks_page_entries_count, lorebook.entries.size),
            lorebook.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty())
            .filter { it.isNotBlank() }.joinToString(" · "),
        position = position,
        leading = {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(width = 44.dp, height = 60.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    when (val cover = lorebook.cover) {
                        is Avatar.Image -> coil3.compose.AsyncImage(model = cover.url, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        is Avatar.Emoji -> Text(cover.content, style = MaterialTheme.typography.titleLarge)
                        else -> Icon(Icons.Rounded.Book, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        },
        trailing = dragHandle,
        onClick = onClick,
    )
}
