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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.data.model.collectAttachmentFileRefs
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.pages.setting.components.AddonTextEditor
import me.rerere.rikkahub.ui.pages.setting.components.AddonEditorField
import me.rerere.rikkahub.ui.pages.setting.components.AddonEmptyState
import me.rerere.rikkahub.ui.pages.setting.components.AddonListItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.utils.OwnedFileDirectory
import me.rerere.rikkahub.utils.getFileNameFromUri
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.importOwnedFile
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
    val navController = LocalNavController.current
    val embeddingService: EmbeddingService = koinInject()
    val scope = rememberCoroutineScope()
    
    var showAddEntrySheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LorebookEntry?>(null) }
    var savingEntry by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("") }
    var pendingExportContent by remember { mutableStateOf("") }
    
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
        vm.updateSettings { settings -> settings.copy(
            lorebooks = settings.lorebooks.map {
                if (it.id == lorebook.id) updated else it
            }
        ) }
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
                            navController.navigate(Screen.SettingLorebookSettings(lorebook.id.toString()))
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
            contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 88.dp),
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Search bar
                    if (lorebook.entries.isNotEmpty()) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.lorebook_search_entries_placeholder)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = AppShapes.SearchField,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
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
                    AddonEmptyState(icon = Icons.Rounded.Edit,
                        title = stringResource(R.string.lorebook_detail_no_entries),
                        modifier = Modifier.fillMaxWidth().padding(24.dp))
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
                                val cleanupKey = "lorebook-entry:${deletedEntry.id}"
                                updateLorebook(lorebook.copy(
                                    entries = lorebook.entries.filter { it.id != entry.id }
                                ))
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
                                .fillMaxWidth()
                        ) { _ ->
                            EntryCard(
                                entry = entry,
                                priority = (lorebook.entries.indexOf(entry) + 1),
                                position = position,
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

}

@Composable
private fun EntryCard(
    entry: LorebookEntry,
    priority: Int,
    position: ItemPosition,
    onEdit: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val activation = when (entry.activationType) {
        LorebookActivationType.ALWAYS -> stringResource(R.string.activation_always)
        LorebookActivationType.KEYWORDS -> stringResource(R.string.activation_keywords_with_count, entry.keywords.size)
        LorebookActivationType.RAG -> stringResource(R.string.activation_rag)
    }
    AddonListItem(
        title = entry.name.ifBlank { stringResource(R.string.lorebook_entry_unnamed) },
        subtitle = listOf(activation, entry.prompt.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty())
            .filter { it.isNotBlank() }.joinToString(" · "),
        position = position,
        leading = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(priority.toString(), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
        trailing = dragHandle,
        onClick = onEdit,
    )
}

@Composable
internal fun LorebookEntryEditorSheet(
    entry: LorebookEntry?,
    onDismiss: (List<String>) -> Unit,
    onSave: (LorebookEntry) -> Unit,
    saving: Boolean = false,
    onDiscardFiles: (List<String>) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val toaster = LocalToaster.current
    var activeEditor by remember { mutableStateOf(true) }
    var importingAttachments by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { activeEditor = false } }
    
    val initialEntry = remember(entry?.id) { entry ?: LorebookEntry() }
    var name by remember { mutableStateOf(entry?.name ?: "") }
    var prompt by remember { mutableStateOf(entry?.prompt ?: "") }
    var editingPrompt by remember { mutableStateOf(false) }
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
    val pendingFields = namePending || promptPending || keywordsPending || scanDepthPending
    val initialAttachmentUrls = remember(entry) {
        entry?.attachments?.map { attachment -> attachment.url }?.toSet().orEmpty()
    }

    fun buildCurrentEntry(): LorebookEntry = initialEntry.copy(
        name = name, prompt = prompt, activationType = activationType,
        keywords = keywords.split(",").map { it.trim() }.filter { it.isNotBlank() },
        caseSensitive = caseSensitive, useRegex = useRegex, scanDepth = scanDepth,
        injectionPosition = injectionPosition, attachments = attachments,
    )

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

    if (editingPrompt) AddonTextEditor(
        title = stringResource(R.string.lorebook_entry_prompt), initialContent = prompt,
        onDismiss = { editingPrompt = false },
        onSave = { prompt = it; editingPrompt = false },
    )
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = { if (!saving) dismissEditor() },
        sheetState = sheetState,
        sheetGesturesEnabled = !saving,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(if (entry == null) R.string.lorebook_entry_add else R.string.lorebook_entry_edit),
                    style = MaterialTheme.typography.titleLarge)
                AutoSaveIndicator(visible = saving)
            }
            SettingsGroup(title = "", horizontalPadding = 0.dp) {
                AddonEditorField(
                    title = stringResource(R.string.lorebook_entry_name), value = name,
                    onValueChange = { name = it }, stateKey = "entry-name-${entry?.id}", singleLine = true, enabled = !saving,
                    onPendingChange = { namePending = it },
                )
                AddonEditorField(
                    title = stringResource(R.string.lorebook_entry_prompt), value = prompt,
                    onValueChange = { prompt = it }, stateKey = "entry-prompt-${entry?.id}", minLines = 4, maxLines = 10, enabled = !saving,
                    onPendingChange = { promptPending = it },
                    onExpand = if (saving) null else ({ editingPrompt = true }),
                )
            }
            SettingsGroup(title = "", horizontalPadding = 0.dp) {
                SettingGroupInputItem(title = stringResource(R.string.lorebook_entry_activation_type)) {
                    if (saving) Text(stringResource(when (activationType) {
                        LorebookActivationType.ALWAYS -> R.string.activation_always
                        LorebookActivationType.KEYWORDS -> R.string.activation_keywords
                        LorebookActivationType.RAG -> R.string.activation_rag
                    })) else Select(options = LorebookActivationType.entries, selectedOption = activationType,
                        onOptionSelected = { if (!saving) activationType = it }, optionToString = {
                            when (it) {
                                LorebookActivationType.ALWAYS -> stringResource(R.string.activation_always)
                                LorebookActivationType.KEYWORDS -> stringResource(R.string.activation_keywords)
                                LorebookActivationType.RAG -> stringResource(R.string.activation_rag)
                            }
                        }, modifier = Modifier.fillMaxWidth())
                }
                if (activationType == LorebookActivationType.KEYWORDS) {
                    AddonEditorField(
                        title = stringResource(R.string.lorebook_entry_keywords), value = keywords,
                        onValueChange = { keywords = it }, stateKey = "entry-keywords-${entry?.id}", minLines = 1, maxLines = 3, enabled = !saving,
                        onPendingChange = { keywordsPending = it },
                    )
                    SettingGroupItem(title = stringResource(R.string.lorebook_entry_case_sensitive),
                        trailing = { HapticSwitch(caseSensitive, { caseSensitive = it }, enabled = !saving) },
                        onClick = if (saving) null else ({ caseSensitive = !caseSensitive }))
                    SettingGroupItem(title = stringResource(R.string.lorebook_entry_use_regex),
                        trailing = { HapticSwitch(useRegex, { useRegex = it }, enabled = !saving) },
                        onClick = if (saving) null else ({ useRegex = !useRegex }))
                }
                AddonEditorField(
                    title = stringResource(R.string.lorebook_entry_scan_depth), value = scanDepth.toString(),
                    onValueChange = { scanDepth = it.filter(Char::isDigit).toIntOrNull() ?: 5 },
                    stateKey = "entry-scan-depth-${entry?.id}", singleLine = true, enabled = !saving,
                    onPendingChange = { scanDepthPending = it },
                )
                SettingGroupInputItem(title = stringResource(R.string.lorebook_entry_injection_position)) {
                    if (saving) Text(stringResource(when (injectionPosition) {
                        InjectionPosition.BEFORE_SYSTEM -> R.string.injection_position_before_system
                        InjectionPosition.AFTER_SYSTEM -> R.string.injection_position_after_system
                        InjectionPosition.TOP_OF_CHAT -> R.string.injection_position_top_of_chat
                        InjectionPosition.BEFORE_LATEST -> R.string.injection_position_before_latest
                        InjectionPosition.AT_DEPTH -> R.string.injection_position_at_depth
                    })) else Select(options = InjectionPosition.entries, selectedOption = injectionPosition,
                        onOptionSelected = { if (!saving) injectionPosition = it }, optionToString = {
                            when (it) {
                                InjectionPosition.BEFORE_SYSTEM -> stringResource(R.string.injection_position_before_system)
                                InjectionPosition.AFTER_SYSTEM -> stringResource(R.string.injection_position_after_system)
                                InjectionPosition.TOP_OF_CHAT -> stringResource(R.string.injection_position_top_of_chat)
                                InjectionPosition.BEFORE_LATEST -> stringResource(R.string.injection_position_before_latest)
                                InjectionPosition.AT_DEPTH -> stringResource(R.string.injection_position_at_depth)
                            }
                        }, modifier = Modifier.fillMaxWidth())
                }
            }
            SettingsGroup(title = "", horizontalPadding = 0.dp) {
                SettingGroupInputItem(title = stringResource(R.string.modes_page_attachments)) {
                    if (attachments.isNotEmpty()) Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEach { attachment ->
                            LorebookEntryAttachmentItem(attachment = attachment, enabled = !saving, onRemove = {
                                if (!saving) {
                                    if (attachment.url !in initialAttachmentUrls) cleanupUnsavedAttachments(listOf(attachment.url))
                                    attachments = attachments.filter { it != attachment }
                                }
                            })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !saving && !importingAttachments, onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.modes_page_add_image))
                        }
                        TextButton(enabled = !saving && !importingAttachments, onClick = { documentPickerLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.modes_page_add_file))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !saving, onClick = { dismissEditor() }) { Text(stringResource(R.string.cancel)) }
                TextButton(enabled = prompt.isNotBlank() && !pendingFields && !saving && !importingAttachments,
                    onClick = { focusManager.clearFocus(); keyboard?.hide(); onSave(buildCurrentEntry()) }) {
                    Text(stringResource(if (entry == null) R.string.create else R.string.save))
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryAttachmentItem(
    attachment: ModeAttachment,
    onRemove: () -> Unit,
    enabled: Boolean = true,
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
            contentDescription = "Remove ${attachment.fileName}",
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = enabled) { onRemove() }
                .align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
            tint = MaterialTheme.colorScheme.onSecondary
        )
    }
}
