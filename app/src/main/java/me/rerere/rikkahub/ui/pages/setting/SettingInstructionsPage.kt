package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.utils.plus
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.InstructionItem
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.AppReorderableItem
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberAppReorderableLazyListState
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberReorderableItems
import me.rerere.rikkahub.ui.hooks.rememberSwipeNeighborState
import me.rerere.rikkahub.ui.hooks.reorderedByKeys
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingInstructionsPage(
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val listState = rememberLazyListState()
    val useWideLayout = LocalSettingsWideLayout.current

    var showDialog by remember { mutableStateOf(false) }
    var editingInstruction by remember { mutableStateOf<InstructionItem?>(null) }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "Instructions",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                if (!useWideLayout) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = -ScreenOffset),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Skills tab
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        navController.navigate(Screen.SettingSkills()) {
                                            popUpTo(Screen.SettingInstructions) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Lorebooks tab
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        navController.navigate(Screen.SettingLorebooks) {
                                            popUpTo(Screen.SettingInstructions) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Instructions tab (selected)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Tick)
                        editingInstruction = null
                        showDialog = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(if (useWideLayout) Modifier else Modifier.offset(y = -ScreenOffset)),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add Instruction")
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        val swipeNeighbors = rememberSwipeNeighborState()
        val itemOrder = rememberReorderableItems(settings.instructions, key = { it.id }) { order ->
            vm.updateSettings { latest -> latest.copy(instructions = latest.instructions.reorderedByKeys(order) { it.id }) }
        }
        val orderedInstructions = itemOrder.items

        val reorderableState = rememberAppReorderableLazyListState(
            listState, itemOrder,
            scrollThresholdPadding = contentPadding + PaddingValues(bottom = 45.dp),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(contentPadding),
                state = listState,
                contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 75.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (orderedInstructions.isEmpty()) {
                    item(key = "empty") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (LocalDarkMode.current) {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ),
                            shape = AppShapes.CardLarge
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "No instructions yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Add custom instructions to steer model behavior",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = orderedInstructions,
                        key = { _, inst -> inst.id }
                    ) { index, inst ->
                        val position = when {
                            orderedInstructions.size == 1 -> ItemPosition.ONLY
                            index == 0 -> ItemPosition.FIRST
                            index == orderedInstructions.lastIndex -> ItemPosition.LAST
                            else -> ItemPosition.MIDDLE
                        }
                        val neighborOffset = swipeNeighbors.offsetFor(index, orderedInstructions) { it.id }

                        AppReorderableItem(
                            state = reorderableState,
                            key = inst.id
                        ) { _ ->
                            PhysicsSwipeToDelete(
                                position = position,
                                deleteEnabled = true,
                                neighborOffset = neighborOffset,
                                onDragProgress = { offset, unlocked -> swipeNeighbors.update(inst.id, offset, unlocked) },
                                onDragEnd = { swipeNeighbors.end(inst.id) },
                                onDelete = {
                                    val deleted = inst
                                    vm.updateSettings { s -> s.copy(instructions = s.instructions.filter { it.id != inst.id }) }
                                    toaster.show(
                                        message = "Deleted ${deleted.name.ifEmpty { "instruction" }}",
                                        action = ToastAction(
                                            label = "Undo",
                                            onClick = {
                                                vm.updateSettings { s ->
                                                    s.copy(
                                                        instructions = s.instructions.toMutableList().apply {
                                                            add(index.coerceAtMost(size), deleted)
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                InstructionCard(
                                    instruction = inst,
                                    position = position,
                                    onEdit = {
                                        editingInstruction = inst
                                        showDialog = true
                                    },
                                    onToggle = { checked ->
                                        vm.updateSettings { s ->
                                            s.copy(
                                                instructions = s.instructions.map {
                                                    if (it.id == inst.id) it.copy(enabled = checked) else it
                                                }
                                            )
                                        }
                                    },
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.longPressDraggableHandle(
                                                onDragStarted = { haptics.perform(HapticPattern.Pop) },
                                                onDragStopped = { haptics.perform(HapticPattern.Tick) }
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = "Drag to reorder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (showDialog) {
        var name by remember { mutableStateOf(editingInstruction?.name.orEmpty()) }
        var content by remember { mutableStateOf(editingInstruction?.content.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(if (editingInstruction == null) "New Instruction" else "Edit Instruction")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Instructions") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedContent = content.trim()
                        if (trimmedContent.isNotBlank()) {
                            if (editingInstruction == null) {
                                val newItem = InstructionItem(
                                    name = trimmedName,
                                    content = trimmedContent,
                                    enabled = true
                                )
                                vm.updateSettings { s -> s.copy(instructions = s.instructions + newItem) }
                            } else {
                                vm.updateSettings { s ->
                                    s.copy(
                                        instructions = s.instructions.map {
                                            if (it.id == editingInstruction!!.id) {
                                                it.copy(name = trimmedName, content = trimmedContent)
                                            } else it
                                        }
                                    )
                                }
                            }
                            haptics.perform(HapticPattern.Success)
                            showDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InstructionCard(
    instruction: InstructionItem,
    position: ItemPosition,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val cornerRadius = 28.dp
    val smallCorner = 8.dp
    val shape = when (position) {
        ItemPosition.ONLY -> RoundedCornerShape(cornerRadius)
        ItemPosition.FIRST -> RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = smallCorner,
            bottomEnd = smallCorner
        )
        ItemPosition.MIDDLE -> RoundedCornerShape(smallCorner)
        ItemPosition.LAST -> RoundedCornerShape(
            topStart = smallCorner,
            topEnd = smallCorner,
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        )
    }

    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (instruction.enabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (instruction.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = instruction.name.ifBlank { "Untitled Instruction" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = instruction.content.ifBlank { "No content" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HapticSwitch(
                checked = instruction.enabled,
                onCheckedChange = onToggle
            )

            dragHandle()
        }
    }
}
