package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.utils.plus
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import me.rerere.rikkahub.ui.hooks.rememberSwipeNeighborState
import me.rerere.rikkahub.ui.hooks.rememberReorderableItems
import me.rerere.rikkahub.ui.hooks.reorderedByKeys

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.settings.CollapseSpacingColumn
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.isAssignedTo
import me.rerere.rikkahub.data.datastore.withLorebookDefault
import me.rerere.rikkahub.data.datastore.withLorebookAssignment
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.collectAttachmentFileRefs
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
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
import me.rerere.rikkahub.utils.OwnedFileDirectory
import me.rerere.rikkahub.utils.getFileNameFromUri
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.importOwnedFile
import me.rerere.rikkahub.utils.importOwnedFiles
import me.rerere.rikkahub.utils.LorebookExportImport
import androidx.activity.result.contract.ActivityResultContracts
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import me.rerere.rikkahub.ui.hooks.AppReorderableItem
import me.rerere.rikkahub.ui.hooks.rememberAppReorderableLazyListState
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
    var savingEntry by remember { mutableStateOf(false) }
    var savingMetadata by remember { mutableStateOf(false) }
    var showEditLorebookSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("") }
    var pendingExportContent by remember { mutableStateOf("") }
    var showAssistantToggleSheet by remember { mutableStateOf(false) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }

    // Filter entries based on search query (name and keywords only)
    val entryOrder = rememberReorderableItems(lorebook?.entries.orEmpty(), key = { it.id }) { order ->
        vm.updateSettings { latest -> latest.copy(lorebooks = latest.lorebooks.map { book ->
            if (book.id == lorebook?.id) book.copy(entries = book.entries.reorderedByKeys(order) { it.id }) else book
        }) }
    }
    val filteredEntries = remember(entryOrder.items, searchQuery) {
        val entries = entryOrder.items
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
    val swipeNeighbors = rememberSwipeNeighborState()


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
    val canDelete = true

    fun updateLorebook(updated: Lorebook) {
        val previousCoverUrl = (lorebook.cover as? Avatar.Image)?.url
        val updatedCoverUrl = (updated.cover as? Avatar.Image)?.url
        vm.updateSettings(afterPersist = {
            if (previousCoverUrl != null && previousCoverUrl != updatedCoverUrl) {
                vm.cleanupFilesIfUnreferenced(listOf(previousCoverUrl))
            }
        }) { latest -> latest.copy(lorebooks = latest.lorebooks.map { current ->
            if (current.id == updated.id) current.copy(name = updated.name, description = updated.description, cover = updated.cover)
            else current
        }) }
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

    val reorderableState = rememberAppReorderableLazyListState(lazyListState, entryOrder, enabled = !isFiltering)

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
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.clear_search)
                                        )
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
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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

                    val neighborOffset = swipeNeighbors.offsetFor(index, filteredEntries) { it.id }

                    AppReorderableItem(
                        state = reorderableState,
                        key = entry.id
                    ) { isDragging ->
                        PhysicsSwipeToDelete(
                            position = position,
                            deleteEnabled = canDelete,
                            neighborOffset = neighborOffset,
                            onDragProgress = { offset, unlocked -> swipeNeighbors.update(entry.id, offset, unlocked) },
                            onDragEnd = { swipeNeighbors.end(entry.id) },
                            onDelete = {
                                val deletedEntry = entry
                                val deletedIndex = lorebook.entries.indexOfFirst { it.id == entry.id }.coerceAtLeast(0)
                                val cleanupKey = "lorebook-entry:${deletedEntry.id}"
                                vm.updateSettings { latest -> latest.copy(lorebooks = latest.lorebooks.map { book ->
                                    if (book.id == lorebook.id) book.copy(entries = book.entries.filterNot { it.id == entry.id }) else book
                                }) }
                                vm.cleanupFilesIfUnreferenced(
                                    fileRefs = deletedEntry.collectAttachmentFileRefs(),
                                    delayMs = me.rerere.rikkahub.data.deletion.DESTRUCTIVE_UNDO_WINDOW_MS,
                                    cleanupKey = cleanupKey,
                                )
                                toaster.show(
                                    message = context.getString(R.string.lorebook_entry_deleted, entry.name.ifEmpty { context.getString(R.string.lorebook_entry_unnamed) }),
                                    duration = me.rerere.rikkahub.data.deletion.DESTRUCTIVE_UNDO_WINDOW_MS,
                                    action = ToastAction(
                                        label = context.getString(R.string.undo),
                                        onClick = {
                                            vm.cancelUnreferencedFileCleanup(cleanupKey)
                                            vm.updateSettings { latest -> latest.copy(lorebooks = latest.lorebooks.map { book ->
                                                if (book.id != lorebook.id || book.entries.any { it.id == deletedEntry.id }) book else book.copy(
                                                    entries = book.entries.toMutableList().apply { add(deletedIndex.coerceIn(0, size), deletedEntry) }
                                                )
                                            }) }
                                        }
                                    )
                                )
                            },
                            modifier = Modifier
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

    if (showAddEntrySheet || editingEntry != null) {
        LorebookEntryEditorSheet(
            entry = editingEntry,
            saving = savingEntry,
            onDiscardFiles = { vm.cleanupFilesIfUnreferenced(it) },
            onDismiss = { discardedFileRefs ->
                if (!savingEntry) {
                    vm.cleanupFilesIfUnreferenced(discardedFileRefs)
                    showAddEntrySheet = false
                    editingEntry = null
                }
            },
            onSave = { savedEntry ->
                if (savingEntry) return@LorebookEntryEditorSheet
                savingEntry = true
                val originalEntry = editingEntry
                scope.launch {
                    val finalEntry = if (savedEntry.activationType == LorebookActivationType.RAG) {
                        try {
                            val result = embeddingService.embedWithModelId(savedEntry.prompt)
                            savedEntry.copy(embedding = result.embeddings.firstOrNull(), hasEmbedding = true,
                                embeddingModelId = result.modelId)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            toaster.show(context.getString(R.string.lorebook_embedding_failed, error.message.orEmpty()),
                                me.rerere.rikkahub.ui.components.ui.ToastType.Error)
                            savedEntry.copy(embedding = null, hasEmbedding = false, embeddingModelId = null)
                        }
                    } else savedEntry.copy(embedding = null, hasEmbedding = false, embeddingModelId = null)
                    val removedFiles = originalEntry?.attachments.orEmpty().map { it.url }
                        .filter { url -> finalEntry.attachments.none { it.url == url } }
                    vm.updateSettings(afterPersist = {
                        vm.cleanupFilesIfUnreferenced(removedFiles)
                        showAddEntrySheet = false
                        editingEntry = null
                        savingEntry = false
                    }, onError = { error ->
                        savingEntry = false
                        toaster.show(error.message ?: "Could not save entry",
                            me.rerere.rikkahub.ui.components.ui.ToastType.Error)
                    }) { latest ->
                        latest.copy(lorebooks = latest.lorebooks.map { current ->
                            if (current.id != lorebook.id) current else current.copy(entries =
                                if (originalEntry != null) current.entries.map {
                                    if (it.id == finalEntry.id) finalEntry else it
                                } else if (current.entries.any { it.id == finalEntry.id }) {
                                    current.entries.map { if (it.id == finalEntry.id) finalEntry else it }
                                } else current.entries + finalEntry)
                        })
                    }
                }
            },
        )
    }

    // Edit Lorebook Sheet
    if (showEditLorebookSheet) {
        LorebookEditorSheet(
            lorebook = lorebook,
            saving = savingMetadata,
            onDiscardFiles = { vm.cleanupFilesIfUnreferenced(it) },
            onDismiss = { discardedFileRefs ->
                vm.cleanupFilesIfUnreferenced(discardedFileRefs)
                showEditLorebookSheet = false
            },
            onSave = { updated ->
                if (!savingMetadata) {
                    savingMetadata = true
                    val previousCoverUrl = (lorebook.cover as? Avatar.Image)?.url
                    val updatedCoverUrl = (updated.cover as? Avatar.Image)?.url
                    vm.updateSettings(afterPersist = {
                        if (previousCoverUrl != null && previousCoverUrl != updatedCoverUrl) {
                            vm.cleanupFilesIfUnreferenced(listOf(previousCoverUrl))
                        }
                        savingMetadata = false
                        showEditLorebookSheet = false
                    }, onError = { error ->
                        savingMetadata = false
                        toaster.show(error.message ?: "Could not save lorebook")
                    }) { latest -> latest.copy(lorebooks = latest.lorebooks.map { book ->
                        if (book.id == updated.id) book.copy(name = updated.name, description = updated.description, cover = updated.cover) else book
                    }) }
                }
            },
            onAutoSave = { updated ->
                updateLorebook(updated)
            }
        )
    }

    // Assistant Toggle Sheet
    if (showAssistantToggleSheet) {
        AssistantLorebookToggleSheet(
            lorebook = lorebook,
            assistants = settings.assistants,
            onDefaultChange = { all ->
                vm.updateSettings { it.withLorebookDefault(lorebook.id, all) }
            },
            onAssignmentChange = { assistantId, checked ->
                vm.updateSettings { it.withLorebookAssignment(lorebook.id, assistantId, checked) }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
internal fun LorebookEntryEditorSheet(
    entry: LorebookEntry?,
    onDismiss: (List<String>) -> Unit,
    onSave: (LorebookEntry) -> Unit,
    onAutoSave: ((LorebookEntry) -> Unit)? = null,
    saving: Boolean = false,
    onDiscardFiles: (List<String>) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    // Capture entry ID and initial values ONCE at composition time
    // Using Unit as key means these values are captured only on first composition
    // and won't change even if parent recomposes
    val initialEntry = remember(entry?.id) { entry ?: LorebookEntry() }
    var activeEditor by remember { mutableStateOf(true) }
    var importingAttachments by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { activeEditor = false } }

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
    var namePending by remember { mutableStateOf(false) }
    var promptPending by remember { mutableStateOf(false) }
    var keywordsPending by remember { mutableStateOf(false) }
    var scanDepthPending by remember { mutableStateOf(false) }
    val isAutoSaving = entry != null && (namePending || promptPending || keywordsPending || scanDepthPending)
    val initialAttachmentUrls = remember(entry) {
        entry?.attachments?.map { attachment -> attachment.url }?.toSet().orEmpty()
    }

    fun buildCurrentEntry(
        currentName: String = name,
        currentPrompt: String = prompt,
        currentKeywords: String = keywords,
        currentScanDepth: Int = scanDepth,
    ): LorebookEntry {
        return initialEntry.copy(
            name = currentName,
            prompt = currentPrompt,
            activationType = activationType,
            keywords = currentKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() },
            caseSensitive = caseSensitive,
            useRegex = useRegex,
            scanDepth = currentScanDepth,
            injectionPosition = injectionPosition,
            attachments = attachments
        )
    }

    fun cleanupUnsavedAttachments(urls: Collection<String>) {
        if (urls.isEmpty()) {
            return
        }
        onDiscardFiles(urls.toList())
    }

    fun currentDiscardedAttachmentUrls(): List<String> {
        return attachments.map { attachment -> attachment.url }
            .filter { url -> url !in initialAttachmentUrls }
    }

    fun dismissEditor() {
        onDismiss(currentDiscardedAttachmentUrls())
    }

    fun importAttachments(uris: List<android.net.Uri>, defaultMime: String) {
        if (uris.isEmpty()) return
        importingAttachments = true
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                var failed = false
                val imported = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    uris.mapNotNull { source ->
                        try {
                            val mime = context.getFileMimeType(source) ?: defaultMime
                            val fileName = context.getFileNameFromUri(source) ?: "file"
                            val copy = context.importOwnedFile(source, OwnedFileDirectory.LOREBOOK_ATTACHMENT)
                            if (copy == null) {
                                failed = true
                                null
                            } else ModeAttachment(
                                url = copy.toString(), fileName = fileName, mime = mime,
                                type = when {
                                    mime.startsWith("image/") -> ModeAttachmentType.IMAGE
                                    mime.startsWith("video/") -> ModeAttachmentType.VIDEO
                                    mime.startsWith("audio/") -> ModeAttachmentType.AUDIO
                                    else -> ModeAttachmentType.DOCUMENT
                                },
                            )
                        } catch (_: Exception) {
                            failed = true
                            null
                        }
                    }
                }
                if (activeEditor) {
                    attachments = attachments + imported
                    importingAttachments = false
                    if (failed) toaster.show("Some files could not be added",
                        me.rerere.rikkahub.ui.components.ui.ToastType.Error)
                } else onDiscardFiles(imported.map { it.url })
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        importAttachments(uris, "image/*")
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importAttachments(uris, "application/octet-stream")
    }

    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = { if (!saving) dismissEditor() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                enabled = !saving,
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        dismissEditor()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        }
    ) {
        CollapseSpacingColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            spacing = 16.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (entry != null) R.string.lorebook_entry_edit
                        else R.string.lorebook_entry_add
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                AutoSaveIndicator(visible = isAutoSaving || saving)
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_name)) }
            ) {
                DebouncedTextField(
                    value = name,
                    onValueChange = { newVal ->
                        name = newVal
                        if (entry != null && newVal.isNotBlank()) {
                            onAutoSave?.invoke(buildCurrentEntry(currentName = newVal))
                        }
                    },
                    enabled = !saving,
                    stateKey = "entry_name_${entry?.id ?: "new"}",
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = context.getString(R.string.lorebook_entry_name) },
                    singleLine = true,
                    placeholder = stringResource(R.string.lorebook_entry_name_placeholder),
                    onPendingChange = { namePending = it },
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_prompt)) }
            ) {
                DebouncedTextField(
                    value = prompt,
                    onValueChange = { newVal ->
                        prompt = newVal
                        if (entry != null && newVal.isNotBlank()) {
                            onAutoSave?.invoke(buildCurrentEntry(currentPrompt = newVal))
                        }
                    },
                    enabled = !saving,
                    stateKey = "entry_prompt_${entry?.id ?: "new"}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp).semantics { contentDescription = context.getString(R.string.lorebook_entry_prompt) },
                    placeholder = stringResource(R.string.lorebook_entry_prompt_placeholder),
                    onPendingChange = { promptPending = it },
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_activation_type)) }
            ) {
                Select(
                    options = LorebookActivationType.entries,
                    selectedOption = activationType,
                    onOptionSelected = { if (!saving) activationType = it },
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

            ExpandableContent(visible = activationType == LorebookActivationType.KEYWORDS) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormItem(
                        label = { Text(stringResource(R.string.lorebook_entry_keywords)) }
                    ) {
                        DebouncedTextField(
                            value = keywords,
                            onValueChange = { newVal ->
                                keywords = newVal
                                if (entry != null) {
                                    onAutoSave?.invoke(buildCurrentEntry(currentKeywords = newVal))
                                }
                            },
                            enabled = !saving,
                    stateKey = "entry_keywords_${entry?.id ?: "new"}",
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.lorebook_entry_keywords_placeholder),
                            onPendingChange = { keywordsPending = it },
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
                            onCheckedChange = { caseSensitive = it }, enabled = !saving
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
                            onCheckedChange = { useRegex = it }, enabled = !saving
                        )
                    }
                }
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_scan_depth)) }
            ) {
                DebouncedTextField(
                    value = scanDepth.toString(),
                    onValueChange = { newVal ->
                        val parsed = newVal.filter { it.isDigit() }.toIntOrNull() ?: 5
                        scanDepth = parsed
                        if (entry != null) {
                            onAutoSave?.invoke(buildCurrentEntry(currentScanDepth = parsed))
                        }
                    },
                    enabled = !saving,
                    stateKey = "entry_scan_depth_${entry?.id ?: "new"}",
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    onPendingChange = { scanDepthPending = it },
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebook_entry_injection_position)) }
            ) {
                Select(
                    options = InjectionPosition.entries,
                    selectedOption = injectionPosition,
                    onOptionSelected = { if (!saving) injectionPosition = it },
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
                                        if (saving) return@LorebookEntryAttachmentItem
                                        if (attachment.url !in initialAttachmentUrls) {
                                            cleanupUnsavedAttachments(listOf(attachment.url))
                                        }
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
                            enabled = !saving && !importingAttachments,
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
                            enabled = !saving && !importingAttachments,
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
                TextButton(enabled = !saving, onClick = { dismissEditor() }) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val savedEntry = buildCurrentEntry()
                        onSave(savedEntry)
                    },
                    enabled = prompt.isNotBlank() && !saving && !importingAttachments && !namePending && !promptPending && !keywordsPending && !scanDepthPending
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
internal fun LorebookEditorSheet(
    lorebook: Lorebook,
    onDismiss: (List<String>) -> Unit,
    onSave: (Lorebook) -> Unit,
    onAutoSave: ((Lorebook) -> Unit)? = null,
    saving: Boolean = false,
    onDiscardFiles: (List<String>) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var activeEditor by remember { mutableStateOf(true) }
    var importingCover by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { activeEditor = false } }

    var name by remember { mutableStateOf(lorebook.name) }
    var description by remember { mutableStateOf(lorebook.description) }
    var cover by remember { mutableStateOf(lorebook.cover) }
    var namePending by remember { mutableStateOf(false) }
    var descriptionPending by remember { mutableStateOf(false) }
    val isAutoSaving = namePending || descriptionPending
    val initialCoverUrl = remember(lorebook.id) {
        (lorebook.cover as? Avatar.Image)?.url
    }

    fun cleanupIfUnsaved(url: String?) {
        if (url.isNullOrBlank() || url == initialCoverUrl) {
            return
        }
        onDiscardFiles(listOf(url))
    }

    fun discardedCoverRefs(): List<String> {
        val currentCoverUrl = (cover as? Avatar.Image)?.url
        return listOfNotNull(currentCoverUrl?.takeIf { it != initialCoverUrl })
    }

    fun dismissEditor() {
        onDismiss(discardedCoverRefs())
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null && !saving && !importingCover) {
            importingCover = true
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    try {
                        val previousUnsavedCoverUrl = (cover as? Avatar.Image)?.url?.takeIf { it != initialCoverUrl }
                        context.importOwnedFile(uri, OwnedFileDirectory.LOREBOOK_COVER)?.let { localUri ->
                            if (!activeEditor) {
                                onDiscardFiles(listOf(localUri.toString()))
                            } else {
                                val newCover = Avatar.Image(localUri.toString())
                                cover = newCover
                                onAutoSave?.invoke(lorebook.copy(name = name, description = description, cover = newCover))
                                if (onAutoSave == null) cleanupIfUnsaved(previousUnsavedCoverUrl)
                            }
                        }
                    } catch (error: Exception) {
                        if (activeEditor) toaster.show(error.message ?: "Could not import cover",
                            me.rerere.rikkahub.ui.components.ui.ToastType.Error)
                    } finally {
                        if (activeEditor) importingCover = false
                    }
                }
            }
        }
    }

    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = { if (!saving) dismissEditor() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.lorebook_edit),
                    style = MaterialTheme.typography.titleLarge
                )
                AutoSaveIndicator(visible = isAutoSaving || saving)
            }

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
                    onClick = { if (!saving && !importingCover) imagePickerLauncher.launch("image/*") }
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
                    DebouncedTextField(
                        value = name,
                        onValueChange = { newVal ->
                            name = newVal
                            if (newVal.isNotBlank()) {
                                onAutoSave?.invoke(lorebook.copy(
                                    name = newVal,
                                    description = description,
                                    cover = cover
                                ))
                            }
                        },
                        enabled = !saving,
                        stateKey = "lorebook_name_${lorebook.id}",
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = context.getString(R.string.lorebooks_page_name)
                        },
                        singleLine = true,
                        placeholder = stringResource(R.string.lorebooks_page_name_placeholder),
                        onPendingChange = { namePending = it },
                    )
                }
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebooks_page_description), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ) {
                DebouncedTextField(
                    value = description,
                    onValueChange = { newVal ->
                        description = newVal
                        onAutoSave?.invoke(lorebook.copy(
                            name = name,
                            description = newVal,
                            cover = cover
                        ))
                    },
                    enabled = !saving,
                    stateKey = "lorebook_desc_${lorebook.id}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp).semantics {
                            contentDescription = context.getString(R.string.lorebooks_page_description)
                        },
                    placeholder = stringResource(R.string.lorebooks_page_description_placeholder),
                    onPendingChange = { descriptionPending = it },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(enabled = !saving, onClick = { dismissEditor() }) {
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
                    enabled = name.isNotBlank() && !saving && !importingCover && !namePending && !descriptionPending
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
internal fun AssistantLorebookToggleSheet(
    lorebook: Lorebook,
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    onDefaultChange: (Boolean) -> Unit,
    onAssignmentChange: (kotlin.uuid.Uuid, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val cornerRadius = 28.dp
    val smallCorner = 8.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = AppShapes.CardLarge,
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled by default", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    HapticSwitch(lorebook.allAssistants, onDefaultChange,
                        modifier = Modifier.semantics { contentDescription = "Enabled by default" })
                }
            }

            assistants.forEachIndexed { index, assistant ->
                val isEnabled = lorebook.isAssignedTo(assistant)

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
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
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
                            onCheckedChange = { onAssignmentChange(assistant.id, it) },
                            modifier = Modifier.semantics { contentDescription = assistant.name },
                        )
                    }
                }
            }
        }
    }
}
