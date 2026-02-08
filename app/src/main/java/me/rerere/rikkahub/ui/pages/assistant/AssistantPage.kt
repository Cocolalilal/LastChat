package me.rerere.rikkahub.ui.pages.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.OutlinedButton
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANTS_IDS
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantImporter
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid
import androidx.compose.foundation.lazy.items as lazyItems

import androidx.compose.material3.Slider
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.lazy.itemsIndexed
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.AssistantExportImport
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Upload


@Composable
fun AssistantPage(vm: AssistantVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val createState = useEditState<Assistant> {
        vm.addAssistant(it)
    }
    val navController = LocalNavController.current
    val toaster = me.rerere.rikkahub.ui.context.LocalToaster.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Import state
    var pendingImportResult by remember { mutableStateOf<AssistantExportImport.ImportResult.Configurable?>(null) }
    
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
             scope.launch {
                 val res = AssistantExportImport.parseImport(uri, context)
                 when (res) {
                     is AssistantExportImport.ImportResult.Error -> toaster.show(res.message)
                     is AssistantExportImport.ImportResult.Success -> {
                         vm.addAssistant(res.assistant)
                         toaster.show("Character Imported")
                     }
                     is AssistantExportImport.ImportResult.Configurable -> {
                         pendingImportResult = res
                     }
                 }
             }
        }
    }

    // Search query state
    var searchQuery by remember { mutableStateOf("") }
    
    // Tag filter state
    var selectedTagIds by remember { mutableStateOf(emptySet<kotlin.uuid.Uuid>()) }
    
    // Filter assistants by both search query and tags
    val filteredAssistants = remember(settings.assistants, searchQuery, selectedTagIds) {
        var result = settings.assistants
        
        // Filter by search query
        if (searchQuery.isNotBlank()) {
            result = result.filter { assistant ->
                assistant.name.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Filter by tags
        if (selectedTagIds.isNotEmpty()) {
            result = result.filter { assistant ->
                assistant.tags.containsAll(selectedTagIds)
            }
        }
        
        result
    }
    
    val isFiltering = selectedTagIds.isNotEmpty() || searchQuery.isNotBlank()
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    // Move lazyListState outside for canScroll detection
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (!isFiltering) {
            val newAssistants = settings.assistants.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            vm.updateSettings(settings.copy(assistants = newAssistants))
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.assistant_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            createState.open(Assistant())
                        }
                    ) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.assistant_page_add))
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .consumeWindowInsets(it),
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.assistant_page_search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                } else null
            )
            
            // Tag filter row - only show when there are tags
            if (settings.assistantTags.isNotEmpty()) {
                AssistantTagsFilterRow(
                    settings = settings,
                    vm = vm,
                    selectedTagIds = selectedTagIds,
                    onUpdateSelectedTagIds = { ids ->
                        selectedTagIds = ids
                    }
                )
            }
            
            // State for swipe neighbor tracking
            var draggingIndex by remember { mutableStateOf(-1) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            var isUnlocked by remember { mutableStateOf(false) }
            var neighborsUnlocked by remember { mutableStateOf(false) }
            
            
            val density = androidx.compose.ui.platform.LocalDensity.current
            val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
            
            // Check if delete is allowed (more than 1 assistant)
            val canDelete = settings.assistants.size > 1
            
            // Reset neighborsUnlocked when offset returns to 0
            if (dragOffset == 0f && neighborsUnlocked) {
                neighborsUnlocked = false
            }
            

            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                state = lazyListState,
            ) {
                itemsIndexed(filteredAssistants, key = { _, assistant -> assistant.id }) { index, assistant ->
                    val position = when {
                        filteredAssistants.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == filteredAssistants.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }
                    
                    // Calculate neighbor offset for swipe effect
                    val thresholdPx = with(density) { 35.dp.toPx() }
                    if (draggingIndex >= 0 && !neighborsUnlocked && kotlin.math.abs(dragOffset) >= thresholdPx) {
                        neighborsUnlocked = true
                    }
                    
                    val shouldNeighborFollow = draggingIndex >= 0 && 
                        draggingIndex != index && 
                        !isUnlocked && 
                        !neighborsUnlocked
                    
                    val neighborOffset = if (shouldNeighborFollow) {
                        val distance = kotlin.math.abs(index - draggingIndex)
                        when (distance) {
                            1 -> dragOffset * 0.35f
                            2 -> dragOffset * 0.12f
                            else -> 0f
                        }
                    } else {
                        0f
                    }
                    

                    // Collect memories OUTSIDE of ReorderableItem to prevent recomposition issues during drag
                    val memories by vm.getMemories(assistant).collectAsStateWithLifecycle(
                        initialValue = emptyList(),
                    )

                    ReorderableItem(
                        state = reorderableState, 
                        key = assistant.id
                    ) { isDragging ->
                        // Key on canDelete to force complete PhysicsSwipeToDelete recreation when list size changes
                        androidx.compose.runtime.key(canDelete) {
                            PhysicsSwipeToDelete(
                                position = position,
                                deleteEnabled = canDelete,
                                neighborOffset = neighborOffset,
                                onDragProgress = { offset, unlocked ->
                                    draggingIndex = index
                                    dragOffset = offset
                                    isUnlocked = unlocked
                                },
                                onDragEnd = {
                                    if (draggingIndex == index) {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    }
                                },
                                onDelete = {
                                    vm.removeAssistant(assistant)
                                    toaster.show(
                                        message = context.getString(R.string.assistant_deleted, assistant.name),
                                        action = me.rerere.rikkahub.ui.components.ui.ToastAction(
                                            label = context.getString(R.string.undo),
                                            onClick = {
                                                vm.undoRemoveAssistant(assistant)
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .scale(if (isDragging) 0.95f else 1f)
                                    .fillMaxWidth()
                        ) { _ ->
                            AssistantItemContent(
                                assistant = assistant,
                                settings = settings,
                                memories = memories,
                                haptics = haptics,
                                onClick = {
                                    navController.navigate(Screen.AssistantDetail(id = assistant.id.toString()))
                                },
                                onCopy = {
                                    vm.copyAssistant(assistant)
                                },
                                dragHandle = {
                                    if (!isFiltering) {
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
                                                contentDescription = null
                                            )
                                        }
                                    }
                                }
                            )
                            }  // end PhysicsSwipeToDelete content
                        }  // key(canDelete)
                    }  // ReorderableItem
                }
            }
            
        }
    }

    if (pendingImportResult != null) {
        val res = pendingImportResult!!
        ImportConfigDialog(
            onDismissRequest = { pendingImportResult = null },
            hasMemories = res.hasMemories,
            hasLorebooks = res.hasLorebooks,
            missingModels = res.missingModels,
            onConfirm = { m, l ->
                 scope.launch {
                     val assistant = if (res.exportV1 != null) {
                         AssistantExportImport.finalizeLastChatImport(res.exportV1, context, m, l)
                     } else {
                         res.assistant
                     }
                     // Clear missing models
                     val finalAssistant = AssistantExportImport.clearMissingModels(assistant)
                     
                     vm.addAssistant(finalAssistant)
                     toaster.show("Character Imported")
                     pendingImportResult = null
                 }
            }
        )
    }

    AssistantCreationSheet(
        state = createState,
        onImportClick = {
            createState.dismiss()
            importLauncher.launch(arrayOf("*/*"))
        }
    )
}

@Composable
fun AssistantCreationSheet(
    state: EditState<Assistant>,
    onImportClick: () -> Unit,
) {
    state.EditStateContent { assistant, update ->
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = {},
            sheetGesturesEnabled = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FormItem(
                        label = {
                            Text(stringResource(R.string.assistant_page_name))
                        },
                    ) {
                        OutlinedTextField(
                            value = assistant.name, onValueChange = {
                                update(
                                    assistant.copy(
                                        name = it
                                    )
                                )
                            }, modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedButton(
                        onClick = onImportClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.assistant_importer_import_character))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            state.dismiss()
                        }) {
                        Text(stringResource(R.string.assistant_page_cancel))
                    }
                    TextButton(
                        onClick = {
                            state.confirm()
                        }) {
                        Text(stringResource(R.string.assistant_page_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantItemContent(
    assistant: Assistant,
    settings: Settings,
    memories: List<AssistantMemory>,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UIAvatar(
            name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            value = assistant.avatar,
            modifier = Modifier
                .size(40.dp)
                .heroAnimation(key = "assistant_avatar_${assistant.id}")
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Only show tag row when there are tags or memory
            val hasContent = assistant.enableMemory || assistant.tags.isNotEmpty()
            if (hasContent) {
                Spacer(modifier = Modifier.height(8.dp))
                // Non-interactive tag row with fixed height - fades to card background at right edge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clipToBounds()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                    ) {
                        if (assistant.enableMemory) {
                            Tag(type = TagType.SUCCESS) {
                                Text(stringResource(R.string.assistant_page_memory_count, memories.size))
                            }
                        }
                        if (assistant.tags.isNotEmpty()) {
                            assistant.tags.fastForEach { tagId ->
                                val tag = settings.assistantTags.find { it.id == tagId } ?: return@fastForEach
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        text = tag.name,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    // Fade gradient overlay to card background color
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(width = 40.dp, height = 24.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            )
                    )
                }
            }
        }
        // Copy button only
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.assistant_page_clone),
            modifier = Modifier
                .onClick {
                    onCopy()
                }
                .size(20.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        )
        dragHandle()
    }
}

@Composable
fun AssistantTagsFilterRow(
    settings: Settings,
    vm: AssistantVM,
    selectedTagIds: Set<Uuid>,
    onUpdateSelectedTagIds: (Set<Uuid>) -> Unit
) {
    val tags = settings.assistantTags
    val scrollState = rememberLazyListState()
    val canScrollBackward by remember { derivedStateOf { scrollState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }
    
    LazyRow(
        state = scrollState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                // Left edge fade (only if can scroll backward)
                if (canScrollBackward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = 0f,
                            endX = 24.dp.toPx()
                        ),
                        blendMode = BlendMode.DstOut
                    )
                }
                // Right edge fade (only if can scroll forward)
                if (canScrollForward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startX = size.width - 24.dp.toPx(),
                            endX = size.width
                        ),
                        blendMode = BlendMode.DstOut
                    )
                }
            }
    ) {
        lazyItems(tags) { tag ->
            FilterChip(
                selected = tag.id in selectedTagIds,
                onClick = {
                    val newSelection = if (tag.id in selectedTagIds) {
                        selectedTagIds - tag.id
                    } else {
                        selectedTagIds + tag.id
                    }
                    onUpdateSelectedTagIds(newSelection)
                },
                label = { Text(tag.name) }
            )
        }
    }
}
