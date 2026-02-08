package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileNameFromUri
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.LorebookExportImport
import androidx.activity.result.contract.ActivityResultContracts
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

@Composable
fun SettingLorebookDetailPage(
    id: String,
    scrollToEntryId: String? = null,
    vm: SettingVM = koinViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lorebook = settings.lorebooks.find { it.id.toString() == id }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val embeddingService: EmbeddingService = koinInject()
    val scope = rememberCoroutineScope()
    
    var showAddEntrySheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LorebookEntry?>(null) }
    var showEditLorebookSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("") }
    var pendingExportContent by remember { mutableStateOf("") }
    var showAssistantToggleSheet by remember { mutableStateOf(false) }
    
    // Search state
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter entries based on search query (name and keywords only)
    val filteredEntries = remember(lorebook?.entries, searchQuery) {
        val entries = lorebook?.entries ?: emptyList()
        if (searchQuery.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.name.contains(searchQuery, ignoreCase = true) ||
                entry.keywords.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }
    val isFiltering = searchQuery.isNotBlank()
    
    // Track drag state for neighbor offset
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    
    // Scroll to specific entry if requested
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    LaunchedEffect(scrollToEntryId, lorebook) {
        if (scrollToEntryId != null && lorebook != null) {
            val entryIndex = lorebook.entries.indexOfFirst { it.id.toString() == scrollToEntryId }
            if (entryIndex >= 0) {
                // Collapse the top bar to give more space
                if (scrollBehavior.state.heightOffsetLimit != -Float.MAX_VALUE) {
                    scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
                }
                
                // Calculate offset to center the item (approximate)
                // We want the item effectively in the middle of the screen
                // scrollToItem's offset is "pixels from start of viewport"
                // So we use a negative offset to push the item down
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                val targetOffset = -(screenHeightPx / 2.5f).toInt() // Slightly above center usually looks better
                
                // +1 for header item
                lazyListState.animateScrollToItem(entryIndex + 1, scrollOffset = targetOffset)
            }
        }
    }
    
    // Export file launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportContent.isNotEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { 
                    it.write(pendingExportContent.toByteArray())
                }
                haptics.perform(HapticPattern.Success)
                toaster.show(context.getString(R.string.lorebook_export_success))
            } catch (e: Exception) {
                haptics.perform(HapticPattern.Error)
                toaster.show(context.getString(R.string.lorebook_export_error))
            }
            pendingExportContent = ""
            pendingExportFormat = ""
        }
    }
    
    if (lorebook == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.lorebook_detail_not_found))
        }
        return
    }
    
    fun updateLorebook(updated: Lorebook) {
        vm.updateSettings(settings.copy(
            lorebooks = settings.lorebooks.map {
                if (it.id == lorebook.id) updated else it
            }
        ))
    }
    
    fun exportLorebook(format: String) {
        val content = when (format) {
            "lastchat" -> LorebookExportImport.exportToLastChatFormat(lorebook, context)
            "tavern" -> LorebookExportImport.exportToTavernFormat(lorebook)
            "sillytavern" -> LorebookExportImport.exportToSillyTavernFormat(lorebook)
            else -> LorebookExportImport.exportToLastChatFormat(lorebook, context)
        }
        pendingExportContent = content
        pendingExportFormat = format
        exportLauncher.launch(LorebookExportImport.getSuggestedFileName(lorebook, format))
    }
    
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newEntries = lorebook.entries.toMutableList().apply {
            add(to.index - 1, removeAt(from.index - 1)) // -1 for header item
        }
        updateLorebook(lorebook.copy(entries = newEntries))
        haptics.perform(HapticPattern.Pop)
    }
    
    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = lorebook.name.ifEmpty { stringResource(R.string.lorebooks_page_unnamed) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            showEditLorebookSheet = true
                            haptics.perform(HapticPattern.Tick)
                        }
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    Box {
                        IconButton(
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                showExportMenu = true
                            }
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = stringResource(R.string.export_label))
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_format_lastchat)) },
                                onClick = {
                                    showExportMenu = false
                                    exportLorebook("lastchat")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_format_sillytavern)) },
                                onClick = {
                                    showExportMenu = false
                                    exportLorebook("sillytavern")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_format_tavern)) },
                                onClick = {
                                    showExportMenu = false
                                    exportLorebook("tavern")
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Toggle assistants FAB - same size as Add, gray like floating toolbar
                FloatingActionButton(
                    onClick = { 
                        showAssistantToggleSheet = true
                        haptics.perform(HapticPattern.Tick)
                    },
                    shape = AppShapes.CardLarge,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(
                        Icons.Rounded.ToggleOn,
                        contentDescription = stringResource(R.string.lorebook_toggle_assistants)
                    )
                }
                
                // Main FAB for add entry
                FloatingActionButton(
                    onClick = { 
                        showAddEntrySheet = true
                        haptics.perform(HapticPattern.Pop)
                    },
                    shape = AppShapes.CardLarge
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add))
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = lazyListState,
            contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 135.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Lorebook description (centered, no card)
            item(key = "header") {
                Column {
                    if (lorebook.description.isNotEmpty()) {
                        Text(
                            text = lorebook.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Search bar
                    if (lorebook.entries.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.lorebook_search_entries_placeholder)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = AppShapes.SearchField,
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
                    }
                }
            }
            
            if (lorebook.entries.isEmpty()) {
                item(key = "empty") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = AppShapes.CardLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.lorebook_detail_no_entries),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.lorebook_detail_add_entry_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredEntries,
                    key = { _, entry -> entry.id }
                ) { index, entry ->
                    val position = when {
                        filteredEntries.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == filteredEntries.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }
                    
                    val neighborOffset = when {
                        draggingIndex == -1 -> 0f
                        index == draggingIndex - 1 && isUnlocked -> dragOffset * 0.15f
                        index == draggingIndex + 1 && isUnlocked -> dragOffset * 0.15f
                        else -> 0f
                    }

                    ReorderableItem(
                        state = reorderableState, 
                        key = entry.id
                    ) { isDragging ->
                        PhysicsSwipeToDelete(
                            position = position,
                            deleteEnabled = true,
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
                                val deletedEntry = entry
                                updateLorebook(lorebook.copy(
                                    entries = lorebook.entries.filter { it.id != entry.id }
                                ))
                                toaster.show(
                                    message = context.getString(R.string.lorebook_entry_deleted, entry.name.ifEmpty { context.getString(R.string.lorebook_entry_unnamed) }),
                                    action = ToastAction(
                                        label = context.getString(R.string.undo),
                                        onClick = {
                                            updateLorebook(lorebook.copy(
                                                entries = lorebook.entries.toMutableList().apply {
                                                    add(index.coerceAtMost(size), deletedEntry)
                                                }
                                            ))
                                        }
                                    )
                                )
                            },
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                        ) { _ ->
                            EntryCard(
                                entry = entry,
                                priority = (lorebook.entries.indexOf(entry) + 1),
                                onEdit = { editingEntry = entry },
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
                                            Icon(Icons.Rounded.DragIndicator, contentDescription = null)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Entry Sheet
    if (showAddEntrySheet || editingEntry != null) {
        EntryEditorSheet(
            entry = editingEntry,
            onDismiss = {
                showAddEntrySheet = false
                editingEntry = null
            },
            onSave = { savedEntry ->
                // Capture editing state before async work
                val wasEditing = editingEntry != null
                val currentLorebook = lorebook
                
                // Generate embedding for RAG entries
                scope.launch {
                    val finalEntry = if (savedEntry.activationType == LorebookActivationType.RAG && savedEntry.prompt.isNotBlank()) {
                        try {
                            val embeddingResult = embeddingService.embedWithModelId(savedEntry.prompt)
                            savedEntry.copy(
                                embedding = embeddingResult.embeddings.firstOrNull(),
                                hasEmbedding = true,
                                embeddingModelId = embeddingResult.modelId
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("LorebookDetail", "Failed to generate embedding", e)
                            toaster.show("Failed to generate embedding: ${e.message}", me.rerere.rikkahub.ui.components.ui.ToastType.Error)
                            savedEntry.copy(hasEmbedding = false)
                        }
                    } else {
                        savedEntry.copy(embedding = null, hasEmbedding = false, embeddingModelId = null)
                    }
                    
                    if (wasEditing) {
                        val updatedEntries = currentLorebook.entries.map {
                            if (it.id == finalEntry.id) finalEntry else it
                        }
                        updateLorebook(currentLorebook.copy(entries = updatedEntries))
                    } else {
                        updateLorebook(currentLorebook.copy(entries = currentLorebook.entries + finalEntry))
                    }
                }
                showAddEntrySheet = false
                editingEntry = null
            }
        )
    }
    
    // Edit Lorebook Sheet
    if (showEditLorebookSheet) {
        LorebookEditorSheet(
            lorebook = lorebook,
            onDismiss = { showEditLorebookSheet = false },
            onSave = { updated ->
                updateLorebook(updated)
                showEditLorebookSheet = false
            }
        )
    }
    
    // Assistant Toggle Sheet
    if (showAssistantToggleSheet) {
        AssistantLorebookToggleSheet(
            lorebook = lorebook,
            assistants = settings.assistants,
            onUpdateAssistant = { assistant ->
                vm.updateSettings(settings.copy(
                    assistants = settings.assistants.map { 
                        if (it.id == assistant.id) assistant else it 
                    }
                ))
            },
            onDismiss = { showAssistantToggleSheet = false }
        )
    }
}

@Composable
private fun EntryCard(
    entry: LorebookEntry,
    priority: Int,
    onEdit: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = AppShapes.CardLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Content (Number)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = priority.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Middle Content (Text)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.name.ifEmpty { stringResource(R.string.lorebook_entry_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.prompt.take(50) + if (entry.prompt.length > 50) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = when (entry.activationType) {
                        LorebookActivationType.ALWAYS -> stringResource(R.string.activation_always)
                        LorebookActivationType.KEYWORDS -> stringResource(R.string.activation_keywords_with_count, entry.keywords.size)
                        LorebookActivationType.RAG -> stringResource(R.string.activation_rag)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Trailing Content (Drag Handle)
            dragHandle()
        }
    }
}

@Composable
private fun EntryEditorSheet(
    entry: LorebookEntry?,
    onDismiss: () -> Unit,
    onSave: (LorebookEntry) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Capture entry ID and initial values ONCE at composition time
    // Using Unit as key means these values are captured only on first composition
    // and won't change even if parent recomposes
    val entryId by remember { mutableStateOf(entry?.id) }
    val isEditing by remember { mutableStateOf(entry != null) }
    
    var name by remember { mutableStateOf(entry?.name ?: "") }
    var prompt by remember { mutableStateOf(entry?.prompt ?: "") }
    var activationType by remember { mutableStateOf(entry?.activationType ?: LorebookActivationType.ALWAYS) }
    var keywords by remember { mutableStateOf(entry?.keywords?.joinToString(", ") ?: "") }
    var caseSensitive by remember { mutableStateOf(entry?.caseSensitive ?: false) }
    var useRegex by remember { mutableStateOf(entry?.useRegex ?: false) }
    var scanDepth by remember { mutableStateOf(entry?.scanDepth ?: 5) }
    var injectionPosition by remember { 
        mutableStateOf(entry?.injectionPosition ?: InjectionPosition.AFTER_SYSTEM) 
    }
    var attachments by remember { mutableStateOf(entry?.attachments ?: emptyList()) }
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val savedUris = context.createChatFilesByContents(uris)
            val newAttachments = savedUris.mapIndexed { index, uri ->
                val originalUri = uris.getOrNull(index)
                val fileName = originalUri?.let { context.getFileNameFromUri(it) } ?: "image"
                val mime = originalUri?.let { context.getFileMimeType(it) } ?: "image/*"
                val type = when {
                    mime.startsWith("video/") -> ModeAttachmentType.VIDEO
                    else -> ModeAttachmentType.IMAGE
                }
                ModeAttachment(
                    url = uri.toString(),
                    type = type,
                    fileName = fileName,
                    mime = mime
                )
            }
            attachments = attachments + newAttachments
        }
    }
    
    // Document picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val savedUris = context.createChatFilesByContents(uris)
            val newAttachments = savedUris.mapIndexed { index, uri ->
                val originalUri = uris.getOrNull(index)
                val fileName = originalUri?.let { context.getFileNameFromUri(it) } ?: "file"
                val mime = originalUri?.let { context.getFileMimeType(it) } ?: "application/octet-stream"
                val type = when {
                    mime.startsWith("audio/") -> ModeAttachmentType.AUDIO
                    else -> ModeAttachmentType.DOCUMENT
                }
                ModeAttachment(
                    url = uri.toString(),
                    type = type,
                    fileName = fileName,
                    mime = mime
                )
            }
            attachments = attachments + newAttachments
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (entry != null) R.string.lorebook_entry_edit 
                    else R.string.lorebook_entry_add
                ),
                style = MaterialTheme.typography.titleLarge
            )

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_name)) }
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.lorebook_entry_name_placeholder)) }
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_prompt)) }
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text(stringResource(R.string.lorebook_entry_prompt_placeholder)) }
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_activation_type)) }
            ) {
                Select(
                    options = LorebookActivationType.entries,
                    selectedOption = activationType,
                    onOptionSelected = { activationType = it },
                    optionToString = { type ->
                        when (type) {
                            LorebookActivationType.ALWAYS -> stringResource(R.string.activation_always)
                            LorebookActivationType.KEYWORDS -> stringResource(R.string.activation_keywords)
                            LorebookActivationType.RAG -> stringResource(R.string.activation_rag)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = activationType == LorebookActivationType.KEYWORDS) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormItem(
                        label = { Text(stringResource(R.string.lorebook_entry_keywords)) }
                    ) {
                        OutlinedTextField(
                            value = keywords,
                            onValueChange = { keywords = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.lorebook_entry_keywords_placeholder)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.lorebook_entry_case_sensitive),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HapticSwitch(
                            checked = caseSensitive,
                            onCheckedChange = { caseSensitive = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.lorebook_entry_use_regex),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HapticSwitch(
                            checked = useRegex,
                            onCheckedChange = { useRegex = it }
                        )
                    }
                }
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_scan_depth)) }
            ) {
                OutlinedTextField(
                    value = scanDepth.toString(),
                    onValueChange = { scanDepth = it.toIntOrNull() ?: 5 },
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_injection_position)) }
            ) {
                Select(
                    options = InjectionPosition.entries,
                    selectedOption = injectionPosition,
                    onOptionSelected = { injectionPosition = it },
                    optionToString = { position ->
                        when (position) {
                            InjectionPosition.BEFORE_SYSTEM -> stringResource(R.string.injection_position_before_system)
                            InjectionPosition.AFTER_SYSTEM -> stringResource(R.string.injection_position_after_system)
                            InjectionPosition.TOP_OF_CHAT -> stringResource(R.string.injection_position_top_of_chat)
                            InjectionPosition.BEFORE_LATEST -> stringResource(R.string.injection_position_before_latest)
                            InjectionPosition.AT_DEPTH -> stringResource(R.string.injection_position_at_depth)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Attachments section
            FormItem(
                label = { Text(stringResource(R.string.modes_page_attachments)) }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Display existing attachments
                    if (attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attachments.forEach { attachment ->
                                LorebookEntryAttachmentItem(
                                    attachment = attachment,
                                    onRemove = {
                                        attachments = attachments.filter { it != attachment }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Add buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") }
                        ) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.modes_page_add_image))
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { documentPickerLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Icon(
                                Icons.Rounded.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.modes_page_add_file))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        // Use captured entryId - if editing, preserve ID; if new, create new ID
                        val savedEntry = LorebookEntry(
                            id = entryId ?: kotlin.uuid.Uuid.random(),
                            name = name,
                            prompt = prompt,
                            activationType = activationType,
                            keywords = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            caseSensitive = caseSensitive,
                            useRegex = useRegex,
                            scanDepth = scanDepth,
                            injectionPosition = injectionPosition,
                            attachments = attachments
                        )
                        onSave(savedEntry)
                    },
                    enabled = prompt.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun LorebookEditorSheet(
    lorebook: Lorebook,
    onDismiss: () -> Unit,
    onSave: (Lorebook) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf(lorebook.name) }
    var description by remember { mutableStateOf(lorebook.description) }
    var cover by remember { mutableStateOf(lorebook.cover) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val localUri = context.createChatFilesByContents(listOf(it)).firstOrNull()
            if (localUri != null) {
                cover = me.rerere.rikkahub.data.model.Avatar.Image(localUri.toString())
            }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.lorebook_edit),
                style = MaterialTheme.typography.titleLarge
            )

            // Cover picker + Name input inline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover picker - book-like aspect ratio
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(width = 56.dp, height = 78.dp),
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (val c = cover) {
                            is me.rerere.rikkahub.data.model.Avatar.Image -> {
                                coil3.compose.AsyncImage(
                                    model = c.url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            is me.rerere.rikkahub.data.model.Avatar.Emoji -> {
                                Text(text = c.content, fontSize = 28.sp)
                            }
                            else -> {
                                Icon(
                                    androidx.compose.material.icons.Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                // Name input
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lorebooks_page_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.lorebooks_page_name_placeholder)) }
                    )
                }
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebooks_page_description)) }
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = { Text(stringResource(R.string.lorebooks_page_description_placeholder)) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onSave(lorebook.copy(
                            name = name,
                            description = description,
                            cover = cover
                        ))
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryAttachmentItem(
    attachment: ModeAttachment,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.size(80.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (attachment.type) {
                    ModeAttachmentType.IMAGE -> {
                        coil3.compose.AsyncImage(
                            model = attachment.url,
                            contentDescription = attachment.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    ModeAttachmentType.VIDEO -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    ModeAttachmentType.AUDIO -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    ModeAttachmentType.DOCUMENT -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Remove button
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .clickable { onRemove() }
                .align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
            tint = MaterialTheme.colorScheme.onSecondary
        )
    }
}

@Composable
private fun AssistantLorebookToggleSheet(
    lorebook: Lorebook,
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    onUpdateAssistant: (me.rerere.rikkahub.data.model.Assistant) -> Unit,
    onDismiss: () -> Unit
) {
    val cornerRadius = 28.dp
    val smallCorner = 8.dp
    val isDarkMode = LocalDarkMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = lorebook.name.ifEmpty { stringResource(R.string.lorebooks_page_unnamed) },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            assistants.forEachIndexed { index, assistant ->
                val isEnabled = assistant.enabledLorebookIds.contains(lorebook.id)
                
                // Calculate position for connected card styling
                val position = when {
                    assistants.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == assistants.lastIndex -> ItemPosition.LAST
                    else -> ItemPosition.MIDDLE
                }
                
                // Calculate shape based on position (grouped cards)
                val shape = when (position) {
                    ItemPosition.ONLY -> androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
                    ItemPosition.FIRST -> androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = cornerRadius, topEnd = cornerRadius,
                        bottomStart = smallCorner, bottomEnd = smallCorner
                    )
                    ItemPosition.MIDDLE -> androidx.compose.foundation.shape.RoundedCornerShape(smallCorner)
                    ItemPosition.LAST -> androidx.compose.foundation.shape.RoundedCornerShape(
                        topStart = smallCorner, topEnd = smallCorner,
                        bottomStart = cornerRadius, bottomEnd = cornerRadius
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = shape
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Assistant avatar
                        me.rerere.rikkahub.ui.components.ui.UIAvatar(
                            name = assistant.name,
                            value = assistant.avatar,
                            modifier = Modifier.size(40.dp)
                        )

                        Text(
                            text = assistant.name.ifEmpty { "Character" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        HapticSwitch(
                            checked = isEnabled,
                            onCheckedChange = { newEnabled ->
                                val newIds = if (newEnabled) {
                                    assistant.enabledLorebookIds + lorebook.id
                                } else {
                                    assistant.enabledLorebookIds - lorebook.id
                                }
                                onUpdateAssistant(assistant.copy(enabledLorebookIds = newIds))
                            }
                        )
                    }
                }
            }
        }
    }
}
