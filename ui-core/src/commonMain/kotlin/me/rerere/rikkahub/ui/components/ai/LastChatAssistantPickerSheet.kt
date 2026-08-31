package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class LastChatAssistantPickerItem(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val tagIds: Set<String> = emptySet(),
)

data class LastChatAssistantPickerTag(
    val id: String,
    val name: String,
)

/** Android's production assistant switcher, parameterized only at platform boundaries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastChatAssistantPickerSheet(
    assistants: List<LastChatAssistantPickerItem>,
    currentAssistantId: String,
    title: String,
    noSystemPromptLabel: String,
    onAssistantSelected: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
    onPopHaptic: () -> Unit,
    onThudHaptic: () -> Unit,
    avatar: @Composable (LastChatAssistantPickerItem, Modifier) -> Unit,
    tags: List<LastChatAssistantPickerTag> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedTagIds by remember { mutableStateOf(emptySet<String>()) }
    var transitioningAssistantId by remember { mutableStateOf<String?>(null) }
    val isTransitioning = transitioningAssistantId != null
    val filteredAssistants = remember(assistants, selectedTagIds) {
        if (selectedTagIds.isEmpty()) assistants
        else assistants.filter { it.tagIds.containsAll(selectedTagIds) }
    }
    var sheetHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var handleDragAmount by remember { mutableStateOf(0f) }
    val handleDismissThreshold = with(density) { 48.dp.toPx() }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                modifier = Modifier.pointerInput(handleDismissThreshold) {
                    detectVerticalDragGestures(
                        onDragStart = { handleDragAmount = 0f },
                        onDragEnd = {
                            if (handleDragAmount > handleDismissThreshold) {
                                onThudHaptic()
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }
                            handleDragAmount = 0f
                        },
                        onDragCancel = { handleDragAmount = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0f) handleDragAmount += dragAmount
                        },
                    )
                },
                onClick = {
                    onPopHaptic()
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
            ) {
                Icon(RoundedKeyboardArrowDownIcon, contentDescription = null)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .then(if (sheetHeight > 0.dp) Modifier.heightIn(min = sheetHeight) else Modifier)
                .onSizeChanged {
                    if (sheetHeight == 0.dp) sheetHeight = with(density) { it.height.toDp() }
                },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (tags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(tags, key = { it.id }) { tag ->
                        FilterChip(
                            modifier = Modifier.animateItem(),
                            onClick = {
                                selectedTagIds = if (tag.id in selectedTagIds) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                            label = { Text(tag.name) },
                            selected = tag.id in selectedTagIds,
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(filteredAssistants, key = { _, item -> item.id }) { index, assistant ->
                    val checked = assistant.id == currentAssistantId
                    val topCorner by animateDpAsState(
                        targetValue = if (checked) 50.dp else if (index == 0) 24.dp else 10.dp,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
                        label = "topCorner",
                    )
                    val bottomCorner by animateDpAsState(
                        targetValue = if (checked) 50.dp else if (index == filteredAssistants.lastIndex) 24.dp else 10.dp,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
                        label = "bottomCorner",
                    )
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topCorner, topCorner, bottomCorner, bottomCorner))
                            .background(
                                if (checked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            .clickable(enabled = !isTransitioning) {
                                if (!checked) {
                                    onPopHaptic()
                                    transitioningAssistantId = assistant.id
                                    onAssistantSelected(assistant.id)
                                    scope.launch {
                                        transitioningAssistantId = null
                                        sheetState.hide()
                                        onNavigate(assistant.id)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        avatar(assistant, Modifier.size(40.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = assistant.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = assistant.systemPrompt.ifBlank { noSystemPromptLabel },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Crossfade(
                                targetState = transitioningAssistantId == assistant.id,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                label = "edit_spinner",
                            ) { transitioning ->
                                if (transitioning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    IconButton(
                                        enabled = !isTransitioning,
                                        onClick = {
                                            if (!isTransitioning) scope.launch {
                                                sheetState.hide()
                                                onDismiss()
                                                onEdit(assistant.id)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = RoundedEditIcon,
                                            contentDescription = null,
                                            tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val RoundedKeyboardArrowDownIcon by lazy {
    vector(
        "Rounded.KeyboardArrowDown",
        "M8.12,9.29L12,13.17l3.88,-3.88a0.996,0.996 0,1 1,1.41 1.41l-4.59,4.59a0.996,0.996 0,0 1,-1.41 0L6.7,10.7a0.996,0.996 0,0 1,1.42 -1.41z",
    )
}

private val RoundedEditIcon by lazy {
    vector(
        "Rounded.Edit",
        "M3,17.25L3,20.5c0,0.28 0.22,0.5 0.5,0.5h3.25c0.13,0 0.26,-0.05 0.35,-0.15L18.81,9.14l-3.95,-3.95L3.15,16.9c-0.1,0.1 -0.15,0.22 -0.15,0.35zM21.87,5.08c0.18,-0.18 0.18,-0.47 0,-0.65l-2.34,-2.34a0.46,0.46 0,0 0,-0.65 0l-1.83,1.83L21,7.87z",
    )
}

private fun vector(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()
