package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberAssistantState
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.uuid.Uuid

@Composable
fun AssistantPicker(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onNavigate: (Assistant) -> Unit = {},  // Called after panels close
    modifier: Modifier = Modifier,
    onClickSetting: () -> Unit,
) {
    val state = rememberAssistantState(settings, onUpdateSettings)
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
    var showPicker by remember { mutableStateOf(false) }

    NavigationDrawerItem(
        icon = null,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.currentAssistant.name.ifEmpty { defaultAssistantName },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.weight(1f))

                UIAvatar(
                    name = state.currentAssistant.name.ifEmpty { defaultAssistantName },
                    value = state.currentAssistant.avatar,
                    onClick = onClickSetting
                )
            }
        },
        onClick = {
            showPicker = true
        },
        modifier = modifier,
        selected = false,
    )

    if (showPicker) {
        AssistantPickerSheet(
            settings = settings,
            currentAssistant = state.currentAssistant,
            onAssistantSelected = { assistant ->
                // Settings update happens immediately inside the sheet
                state.setSelectAssistant(assistant)
            },
            onNavigate = { assistant ->
                // Navigation callback - called after animation
                showPicker = false
                onNavigate(assistant)
            },
            onDismiss = {
                showPicker = false
            }
        )
    }
}

@Composable
fun AssistantPickerSheet(
    settings: Settings,
    currentAssistant: Assistant,
    onAssistantSelected: (Assistant) -> Unit,
    onNavigate: (Assistant) -> Unit = {},  // Called after animation completes
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)

    // 标签过滤状态
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }
    
    // Transition state - which assistant is being switched to (null = not transitioning)
    var transitioningAssistantId by remember { mutableStateOf<Uuid?>(null) }
    val isTransitioning = transitioningAssistantId != null

    // 根据选中的标签过滤助手
    val filteredAssistants = remember(settings.assistants, selectedTagIds) {
        if (selectedTagIds.isEmpty()) {
            settings.assistants
        } else {
            settings.assistants.filter { assistant ->
                assistant.tags.containsAll(selectedTagIds)
            }
        }
    }

    val isDarkMode = LocalDarkMode.current
    val haptics = rememberPremiumHaptics()
    
    // State to lock the sheet height to its initial size to prevent jumping animations
    var sheetHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var handleDragAmount by remember { mutableStateOf(0f) }
    val handleDismissThreshold = with(density) { 48.dp.toPx() }

    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                modifier = Modifier.pointerInput(handleDismissThreshold) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            handleDragAmount = 0f
                        },
                        onDragEnd = {
                            if (handleDragAmount > handleDismissThreshold) {
                                haptics.perform(HapticPattern.Thud)
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }
                            handleDragAmount = 0f
                        },
                        onDragCancel = {
                            handleDragAmount = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0f) {
                                handleDragAmount += dragAmount
                            }
                        }
                    )
                },
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .then(
                    if (sheetHeight > 0.dp) Modifier.heightIn(min = sheetHeight) else Modifier
                )
                .onSizeChanged {
                    if (sheetHeight == 0.dp) {
                        sheetHeight = with(density) { it.height.toDp() }
                    }
                },
        ) {
            Text(
                text = stringResource(R.string.assistant_page_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 标签过滤器
            if (settings.assistantTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(settings.assistantTags, key = { tag -> tag.id }) { tag ->
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 助手列表
            val navController = LocalNavController.current
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(filteredAssistants, key = { _, item -> item.id }) { index, assistant ->
                    val checked = assistant.id == currentAssistant.id
                    
                    // Determine position in the list for corner rounding
                    val position = when {
                        filteredAssistants.size == 1 -> "ONLY"
                        index == 0 -> "FIRST"
                        index == filteredAssistants.lastIndex -> "LAST"
                        else -> "MIDDLE"
                    }
                    
                    // Animated corner radius - selected items animate to fully round
                    val topCorner by animateDpAsState(
                        targetValue = if (checked) 50.dp else when (position) {
                            "ONLY", "FIRST" -> 24.dp
                            else -> 10.dp
                        },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
                        label = "topCorner"
                    )
                    val bottomCorner by animateDpAsState(
                        targetValue = if (checked) 50.dp else when (position) {
                            "ONLY", "LAST" -> 24.dp
                            else -> 10.dp
                        },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
                        label = "bottomCorner"
                    )
                    
                    val shape = RoundedCornerShape(
                        topStart = topCorner, topEnd = topCorner,
                        bottomStart = bottomCorner, bottomEnd = bottomCorner
                    )
                    
                    // Use Row+clip+background pattern like ReasoningPicker
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer 
                                       else if (isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable(enabled = !isTransitioning) {
                                if (!checked) {
                                    haptics.perform(HapticPattern.Pop)
                                    transitioningAssistantId = assistant.id
                                    // Update settings immediately
                                    onAssistantSelected(assistant)
                                    // Close panels then navigate
                                    scope.launch {
                                        transitioningAssistantId = null
                                        sheetState.hide() // Animate sheet close
                                        onNavigate(assistant) // drawer close + navigate
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UIAvatar(
                            name = assistant.name.ifEmpty { defaultAssistantName },
                            value = assistant.avatar,
                            modifier = Modifier.size(40.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = assistant.name.ifEmpty { defaultAssistantName },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = assistant.systemPrompt.ifBlank { stringResource(R.string.assistant_page_no_system_prompt) },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Crossfade between edit icon and loading spinner
                        val showSpinner = transitioningAssistantId == assistant.id
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Crossfade(
                                targetState = showSpinner,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                label = "edit_spinner"
                            ) { transitioning ->
                                if (transitioning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (!isTransitioning) {
                                                scope.launch {
                                                    sheetState.hide()
                                                    onDismiss()
                                                    navController.navigate(Screen.AssistantDetail(assistant.id.toString()))
                                                }
                                            }
                                        },
                                        enabled = !isTransitioning
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = null,
                                            tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun AssistantItem(
    assistant: Assistant,
    defaultAssistantName: String,
    onEdit: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = assistant.name.ifEmpty { defaultAssistantName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = assistant.systemPrompt.ifBlank { stringResource(R.string.assistant_page_no_system_prompt) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        leadingContent = {
            UIAvatar(
                name = assistant.name.ifEmpty { defaultAssistantName },
                value = assistant.avatar,
                modifier = Modifier.size(32.dp)
            )
        },
        trailingContent = {
            IconButton(
                onClick = {
                    onEdit()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
