@file:OptIn(ExperimentalLayoutApi::class)

package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.AppStorageSnapshot
import me.rerere.rikkahub.data.model.ChatAttachmentKind
import me.rerere.rikkahub.data.model.OtherUploadFile
import me.rerere.rikkahub.data.model.StorageCategoryUsage
import me.rerere.rikkahub.data.model.compactChatAttachmentDisplayName
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import me.rerere.rikkahub.data.repository.ChatAttachmentUsage
import me.rerere.rikkahub.data.repository.ChatStorageSummary
import me.rerere.rikkahub.service.ChatStorageMaintenanceWorker
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.ui.AppToasterState
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.openAttachmentUri
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val CATEGORY_CHAT = "chat_attachments"
private const val CATEGORY_ASSISTANT_MEDIA = "assistant_media"
private const val CATEGORY_LOREBOOK_MEDIA = "lorebook_media"
private const val CATEGORY_GENERATED_MEDIA = "generated_media"
private const val CATEGORY_PYTHON_SANDBOX = "python_sandbox"
private const val CATEGORY_ICONS_AND_FONTS = "icons_and_fonts"
private const val CATEGORY_DATABASES = "databases"
private const val CATEGORY_ICON_CACHE = "icon_cache"
private const val CATEGORY_OCR_CACHE = "ocr_cache"
private const val CATEGORY_TEMP_FILES = "temp_files"
private const val CATEGORY_APP_CACHE = "app_cache"
private const val CATEGORY_CODE_CACHE = "code_cache"
private const val CATEGORY_OTHER_UPLOADS = "other_uploads"
private const val CATEGORY_OTHER_APP_DATA = "other_app_data"

private enum class StorageFilter(
    val label: String,
    val kind: ChatAttachmentKind?,
) {
    ALL("All", null),
    IMAGES("Images", ChatAttachmentKind.IMAGE),
    DOCS("Docs", ChatAttachmentKind.DOCUMENT),
    VIDEOS("Videos", ChatAttachmentKind.VIDEO),
    AUDIO("Audio", ChatAttachmentKind.AUDIO),
}

private enum class StorageSort(val label: String) {
    RECENT("Recent"),
    SIZE("Size"),
    NAME("Name"),
}

private val RESOLUTION_OPTIONS = listOf<Int?>(null, 512, 640, 768, 960, 1024, 1280, 1600, 1920, 2048, 2560, 3072, 4096)
private val AUTO_DELETE_OPTIONS = listOf<Int?>(null, 7, 30, 90, 180)

@Composable
fun SettingChatStoragePage(
    vm: SettingVM = koinViewModel(),
    repository: ChatAttachmentRepository = koinInject(),
    appStorageRepository: AppStorageRepository = koinInject(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val chatSummary by repository.observeStorageSummary()
        .collectAsStateWithLifecycle(initialValue = ChatStorageSummary())
    val appStorageSnapshot by appStorageRepository.observeSnapshot()
        .collectAsStateWithLifecycle(initialValue = AppStorageSnapshot())
    val usage by repository.observeAttachmentUsage().collectAsStateWithLifecycle(initialValue = emptyList())
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var filter by remember { mutableStateOf(StorageFilter.ALL) }
    var sort by remember { mutableStateOf(StorageSort.RECENT) }
    var pendingDeletion by remember { mutableStateOf<ChatAttachmentUsage?>(null) }
    var pendingDeleteAllOtherUploads by remember { mutableStateOf(false) }
    var pendingOtherUploadDeletion by remember { mutableStateOf<OtherUploadFile?>(null) }
    var otherUploadFiles by remember { mutableStateOf<List<OtherUploadFile>>(emptyList()) }
    var isLoadingOtherUploads by remember { mutableStateOf(false) }
    var showOtherUploadsInspector by rememberSaveable { mutableStateOf(false) }
    var showAllStorageCategories by rememberSaveable { mutableStateOf(false) }

    fun loadOtherUploads() {
        scope.launch {
            isLoadingOtherUploads = true
            otherUploadFiles = appStorageRepository.listOtherUploadFiles()
            isLoadingOtherUploads = false
        }
    }

    val visibleFiles = remember(usage, filter, sort) {
        val filtered = usage.filter { attachment ->
            filter.kind == null || attachment.kind == filter.kind
        }
        when (sort) {
            StorageSort.RECENT -> filtered.sortedWith(
                compareByDescending<ChatAttachmentUsage> { it.lastUsedAt ?: 0L }
                    .thenByDescending { it.sizeBytes }
            )

            StorageSort.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            StorageSort.NAME -> filtered.sortedBy { it.displayName.lowercase() }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "Chat Storage",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 12.dp,
                bottom = 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    AppStorageHeroCard(
                        snapshot = appStorageSnapshot,
                        chatSummary = chatSummary,
                    )
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsGroup(
                    title = "App storage",
                    horizontalPadding = 8.dp,
                    titleStartPadding = 8.dp,
                ) {
                    val primaryCategories = appStorageSnapshot.categories.filter { it.isPrimaryStorageCategory() }
                    val secondaryCategories = appStorageSnapshot.categories.filterNot { it.isPrimaryStorageCategory() }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (appStorageSnapshot.isScanning && appStorageSnapshot.categories.isEmpty()) {
                            SettingGroupItem(
                                title = "Scanning storage",
                                subtitle = "Reading app files and categories.",
                                icon = { Icon(Icons.Rounded.Storage, null) },
                            )
                        } else {
                            primaryCategories.forEach { category ->
                                StorageCategoryRow(
                                    category = category,
                                    haptics = haptics,
                                    appStorageRepository = appStorageRepository,
                                    toaster = toaster,
                                    onInspect = if (category.id == CATEGORY_OTHER_UPLOADS) {
                                        {
                                            showOtherUploadsInspector = true
                                            loadOtherUploads()
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                            if (secondaryCategories.isNotEmpty()) {
                                SettingGroupItem(
                                    title = if (showAllStorageCategories) {
                                        "Hide more categories"
                                    } else {
                                        "Show more categories"
                                    },
                                    subtitle = if (showAllStorageCategories) {
                                        "Hide read-only app data details."
                                    } else {
                                        "${secondaryCategories.size} more categories"
                                    },
                                    icon = { Icon(Icons.Rounded.FolderOpen, null) },
                                    trailing = {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.graphicsLayer {
                                                rotationZ = if (showAllStorageCategories) 180f else 0f
                                            },
                                        )
                                    },
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        showAllStorageCategories = !showAllStorageCategories
                                    }
                                )
                                AnimatedVisibility(visible = showAllStorageCategories) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        secondaryCategories.forEach { category ->
                                            StorageCategoryRow(
                                                category = category,
                                                haptics = haptics,
                                                appStorageRepository = appStorageRepository,
                                                toaster = toaster,
                                                onInspect = if (category.id == CATEGORY_OTHER_UPLOADS) {
                                                    {
                                                        showOtherUploadsInspector = true
                                                        loadOtherUploads()
                                                    }
                                                } else {
                                                    null
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsGroup(
                    title = "Chat settings",
                    horizontalPadding = 8.dp,
                    titleStartPadding = 8.dp,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ResolutionSliderCard(
                            selectedValue = settings.chatStorage.imageMaxLongEdgePx,
                            haptics = haptics,
                            onValueChange = { newValue ->
                                vm.updateSettings(
                                    settings.copy(
                                        chatStorage = settings.chatStorage.copy(imageMaxLongEdgePx = newValue)
                                    )
                                )
                            }
                        )

                        AutoDeleteSliderCard(
                            selectedValue = settings.chatStorage.autoDeleteChatImagesAfterDays,
                            haptics = haptics,
                            onValueChange = { newValue ->
                                vm.updateSettings(
                                    settings.copy(
                                        chatStorage = settings.chatStorage.copy(autoDeleteChatImagesAfterDays = newValue)
                                    )
                                )
                            }
                        )

                        if (chatSummary.overview.duplicateSizeBytes > 0L) {
                            SettingGroupItem(
                                title = "Duplicate space",
                                subtitle = chatSummary.overview.duplicateSizeBytes.fileSizeToString(),
                                icon = { Icon(Icons.Rounded.Inventory2, null) },
                            )
                        }

                        SettingGroupInputItem(
                            title = "Maintenance",
                            subtitle = "Reindex and compact chat files now.",
                            icon = { Icon(Icons.Rounded.CleaningServices, null) },
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Thud)
                                    WorkManager.getInstance(context).enqueue(
                                        OneTimeWorkRequestBuilder<ChatStorageMaintenanceWorker>().build()
                                    )
                                    toaster.show("Chat storage maintenance queued", type = ToastType.Info)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.ButtonPill,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Run maintenance now",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsGroup(
                    title = "Files",
                    horizontalPadding = 8.dp,
                    titleStartPadding = 8.dp,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactChipSection(
                            title = "Filter",
                            icon = Icons.Rounded.FilterAlt,
                        ) {
                            StorageFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        filter = option
                                    },
                                    label = { Text(option.label) },
                                )
                            }
                        }

                        CompactChipSection(
                            title = "Sort",
                            icon = Icons.AutoMirrored.Rounded.Sort,
                        ) {
                            StorageSort.entries.forEach { option ->
                                FilterChip(
                                    selected = sort == option,
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        sort = option
                                    },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                    }
                }
            }

            if (visibleFiles.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        EmptyFilesCard(
                            isSyncing = appStorageSnapshot.isScanning,
                            hasFilters = filter != StorageFilter.ALL,
                        )
                    }
                }
            } else {
                items(visibleFiles, key = { it.id }) { attachment ->
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        StorageAttachmentTile(
                            attachment = attachment,
                            haptics = haptics,
                            onDelete = {
                                haptics.perform(HapticPattern.Thud)
                                pendingDeletion = attachment
                            }
                        )
                    }
                }
            }
        }
    }

    pendingDeletion?.let { attachment ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete stored file?") },
            text = {
                Text("Remove ${attachment.displayName.ifBlank { "this file" }} from chat storage?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = attachment
                        pendingDeletion = null
                        scope.launch {
                            repository.deleteAttachment(target.id)
                            appStorageRepository.refreshNow()
                            toaster.show("Removed from chat storage", type = ToastType.Info)
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingOtherUploadDeletion?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingOtherUploadDeletion = null },
            title = { Text("Delete untracked file?") },
            text = {
                Text("Remove ${file.displayName.ifBlank { "this file" }} from the shared upload folder?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = file
                        pendingOtherUploadDeletion = null
                        scope.launch {
                            val deleted = appStorageRepository.deleteOtherUploadFile(target.path)
                            if (deleted) {
                                otherUploadFiles = appStorageRepository.listOtherUploadFiles()
                                toaster.show("Removed from other uploads", type = ToastType.Info)
                            } else {
                                toaster.show("Unable to remove file", type = ToastType.Error)
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOtherUploadDeletion = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingDeleteAllOtherUploads) {
        AlertDialog(
            onDismissRequest = { pendingDeleteAllOtherUploads = false },
            title = { Text("Delete all orphaned uploads?") },
            text = {
                Text("Remove all files currently listed here? These files are no longer referenced by chats, saved settings content, or active web uploads.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteAllOtherUploads = false
                        scope.launch {
                            val deletedCount = appStorageRepository.deleteAllOtherUploadFiles()
                            otherUploadFiles = appStorageRepository.listOtherUploadFiles()
                            toaster.show(
                                if (deletedCount > 0) {
                                    "Removed $deletedCount orphaned uploads"
                                } else {
                                    "No orphaned uploads to remove"
                                },
                                type = ToastType.Info,
                            )
                        }
                    }
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAllOtherUploads = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOtherUploadsInspector) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showOtherUploadsInspector = false },
            sheetState = sheetState,
        ) {
            OtherUploadsSheet(
                files = otherUploadFiles,
                isLoading = isLoadingOtherUploads,
                onOpenFile = { file ->
                    haptics.perform(HapticPattern.Pop)
                    context.openAttachmentUri(
                        uri = file.uri.toUri(),
                        mimeType = file.mime,
                    )
                },
                onDeleteFile = { file ->
                    haptics.perform(HapticPattern.Thud)
                    pendingOtherUploadDeletion = file
                },
                onDeleteAll = if (otherUploadFiles.isNotEmpty()) {
                    {
                        haptics.perform(HapticPattern.Thud)
                        pendingDeleteAllOtherUploads = true
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun AppStorageHeroCard(
    snapshot: AppStorageSnapshot,
    chatSummary: ChatStorageSummary,
) {
    val settingsSurface = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardLarge,
        color = settingsSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to MaterialTheme.colorScheme.primaryContainer,
                            0.35f to MaterialTheme.colorScheme.primaryContainer,
                            1.0f to settingsSurface,
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = "App storage",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = if (snapshot.isScanning) {
                                "Scanning app storage..."
                            } else {
                                snapshot.totalBytes.fileSizeToString()
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (snapshot.isScanning) {
                        "Aligning app totals and chat files."
                    } else {
                        "Chat uses ${snapshot.chatBytes.fileSizeToString()} across ${snapshot.chatCount} files."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!snapshot.isScanning) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StorageStatPill(text = "App ${snapshot.appBytes.fileSizeToString()}")
                        StorageStatPill(text = "Data ${snapshot.dataBytes.fileSizeToString()}")
                        StorageStatPill(text = "Cache ${snapshot.cacheBytes.fileSizeToString()}")
                        if (chatSummary.overview.duplicateSizeBytes > 0L) {
                            StorageStatPill(text = "Chat dupes ${chatSummary.overview.duplicateSizeBytes.fileSizeToString()}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageStatPill(
    text: String,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StorageCategoryRow(
    category: StorageCategoryUsage,
    haptics: PremiumHaptics,
    appStorageRepository: AppStorageRepository,
    toaster: AppToasterState,
    onInspect: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    SettingGroupItem(
        title = category.label,
        subtitle = category.bytes.fileSizeToString(),
        icon = { Icon(category.icon(), null) },
        trailing = when {
            category.clearable -> {
                {
                    TextButton(
                        onClick = {
                            haptics.perform(HapticPattern.Thud)
                            scope.launch {
                                appStorageRepository.clearCategory(category.id)
                                toaster.show(
                                    "${category.label} cleared",
                                    type = ToastType.Info,
                                )
                            }
                        }
                    ) {
                        Text("Clear")
                    }
                }
            }

            onInspect != null -> {
                {
                    TextButton(onClick = onInspect) {
                        Text("Inspect")
                    }
                }
            }

            else -> null
        },
        onClick = onInspect,
    )
}

@Composable
private fun ResolutionSliderCard(
    selectedValue: Int?,
    haptics: PremiumHaptics,
    onValueChange: (Int?) -> Unit,
) {
    val selectedIndex = RESOLUTION_OPTIONS.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
    var sliderValue by remember(selectedValue) { mutableFloatStateOf(selectedIndex.toFloat()) }

    SettingGroupInputItem(
        title = "Image resolution",
        subtitle = "Scale down large chat images before saving.",
        icon = { Icon(Icons.Rounded.PhotoSizeSelectLarge, null) },
    ) {
        Text(
            text = selectedValue?.let { "$it px long edge" } ?: "Original size",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                haptics.perform(HapticPattern.Pop)
                onValueChange(
                    RESOLUTION_OPTIONS[sliderValue.toInt().coerceIn(0, RESOLUTION_OPTIONS.lastIndex)]
                )
            },
            valueRange = 0f..(RESOLUTION_OPTIONS.lastIndex.toFloat()),
            steps = RESOLUTION_OPTIONS.lastIndex - 1,
        )
    }
}

@Composable
private fun AutoDeleteSliderCard(
    selectedValue: Int?,
    haptics: PremiumHaptics,
    onValueChange: (Int?) -> Unit,
) {
    val selectedIndex = AUTO_DELETE_OPTIONS.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
    var sliderValue by remember(selectedValue) { mutableFloatStateOf(selectedIndex.toFloat()) }

    SettingGroupInputItem(
        title = "Delete old chat images",
        subtitle = "Delete by age for chat attachments only.",
        icon = { Icon(Icons.Rounded.Schedule, null) },
    ) {
        Text(
            text = selectedValue?.let { "$it days" } ?: "Never",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                haptics.perform(HapticPattern.Pop)
                onValueChange(
                    AUTO_DELETE_OPTIONS[sliderValue.toInt().coerceIn(0, AUTO_DELETE_OPTIONS.lastIndex)]
                )
            },
            valueRange = 0f..(AUTO_DELETE_OPTIONS.lastIndex.toFloat()),
            steps = AUTO_DELETE_OPTIONS.lastIndex - 1,
        )
    }
}

@Composable
private fun CompactChipSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    SettingGroupInputItem(
        title = title,
        icon = { Icon(icon, null) },
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EmptyFilesCard(
    isSyncing: Boolean,
    hasFilters: Boolean,
) {
    Surface(
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (isSyncing) "Scanning chat attachments" else "No files here",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (isSyncing) {
                    "Counts will update when the scan finishes."
                } else if (hasFilters) {
                    "Try another filter."
                } else {
                    "Saved chat attachments will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageAttachmentTile(
    attachment: ChatAttachmentUsage,
    haptics: PremiumHaptics,
    onDelete: () -> Unit,
) {
    when (attachment.kind) {
        ChatAttachmentKind.IMAGE -> StorageImageTile(
            attachment = attachment,
            onDelete = onDelete,
        )

        ChatAttachmentKind.DOCUMENT,
        ChatAttachmentKind.VIDEO,
        ChatAttachmentKind.AUDIO,
        -> StorageFileTile(
            attachment = attachment,
            haptics = haptics,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun StorageImageTile(
    attachment: ChatAttachmentUsage,
    onDelete: () -> Unit,
) {
    Surface(
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                ZoomableAsyncImage(
                    model = attachment.uri,
                    contentDescription = attachment.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.08f)
                        .storageArchivedPreview(attachment.deleted)
                        .graphicsLayer(alpha = if (attachment.deleted) 0.72f else 1f),
                    contentScale = ContentScale.Crop,
                )

                DeleteButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    onDelete = onDelete,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = compactChatAttachmentDisplayName(
                        attachment.displayName.ifBlank { "Image" },
                        maxLength = 24,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = imageMetadataLabel(attachment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StorageFileTile(
    attachment: ChatAttachmentUsage,
    haptics: PremiumHaptics,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    Surface(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            context.openAttachmentUri(
                uri = attachment.uri.toUri(),
                mimeType = attachment.mime,
            )
        },
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = attachment.kind.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = attachment.mime
                            .substringAfterLast('/', attachment.kind.defaultName())
                            .ifBlank { attachment.kind.defaultName() }
                            .uppercase()
                            .take(8),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                        fontWeight = FontWeight.Bold,
                    )
                }

                DeleteButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onDelete = onDelete,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = compactChatAttachmentDisplayName(
                        attachment.displayName.ifBlank { attachment.kind.defaultName() },
                        maxLength = 24,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = fileMetadataLabel(attachment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeleteButton(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
    ) {
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun imageMetadataLabel(
    attachment: ChatAttachmentUsage,
): String {
    return buildString {
        append(attachment.sizeBytes.fileSizeToString())
        attachment.width?.let { width ->
            attachment.height?.let { height ->
                append(" | ")
                append("${width}x$height")
            }
        }
        attachment.lastUsedAt?.let {
            append(" | ")
            append(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)))
        }
    }
}

private fun fileMetadataLabel(
    attachment: ChatAttachmentUsage,
): String {
    return buildString {
        append(attachment.sizeBytes.fileSizeToString())
        append(" | ")
        append("${attachment.referenceCount} chats")
        attachment.lastUsedAt?.let {
            append(" | ")
            append(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)))
        }
    }
}

private fun ChatAttachmentKind.icon(): ImageVector {
    return when (this) {
        ChatAttachmentKind.IMAGE -> Icons.Rounded.Image
        ChatAttachmentKind.DOCUMENT -> Icons.Rounded.Description
        ChatAttachmentKind.VIDEO -> Icons.Rounded.VideoFile
        ChatAttachmentKind.AUDIO -> Icons.Rounded.AudioFile
    }
}

private fun ChatAttachmentKind.defaultName(): String {
    return when (this) {
        ChatAttachmentKind.IMAGE -> "Image"
        ChatAttachmentKind.DOCUMENT -> "Document"
        ChatAttachmentKind.VIDEO -> "Video"
        ChatAttachmentKind.AUDIO -> "Audio"
    }
}

private fun StorageCategoryUsage.icon(): ImageVector {
    return when (id) {
        CATEGORY_CHAT -> Icons.Rounded.Storage
        CATEGORY_ASSISTANT_MEDIA -> Icons.Rounded.Visibility
        CATEGORY_LOREBOOK_MEDIA -> Icons.Rounded.FolderOpen
        CATEGORY_GENERATED_MEDIA -> Icons.Rounded.Image
        CATEGORY_PYTHON_SANDBOX -> Icons.Rounded.Code
        CATEGORY_ICONS_AND_FONTS -> Icons.Rounded.AutoAwesome
        CATEGORY_DATABASES -> Icons.Rounded.Memory
        CATEGORY_ICON_CACHE -> Icons.Rounded.Inventory2
        CATEGORY_OCR_CACHE -> Icons.Rounded.Description
        CATEGORY_TEMP_FILES -> Icons.Rounded.CleaningServices
        CATEGORY_APP_CACHE -> Icons.Rounded.Storage
        CATEGORY_CODE_CACHE -> Icons.Rounded.Code
        CATEGORY_OTHER_UPLOADS -> Icons.Rounded.FolderOpen
        CATEGORY_OTHER_APP_DATA -> Icons.Rounded.Storage
        else -> Icons.Rounded.Storage
    }
}

private fun StorageCategoryUsage.isPrimaryStorageCategory(): Boolean {
    return id == CATEGORY_CHAT || id == CATEGORY_TEMP_FILES
}

@Composable
private fun OtherUploadsSheet(
    files: List<OtherUploadFile>,
    isLoading: Boolean,
    onOpenFile: (OtherUploadFile) -> Unit,
    onDeleteFile: (OtherUploadFile) -> Unit,
    onDeleteAll: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Other uploads",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (isLoading) {
                "Scanning the shared upload folder."
            } else {
                "Files left in upload that are no longer referenced by the app."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (files.isEmpty()) {
            EmptyFilesCard(
                isSyncing = false,
                hasFilters = false,
            )
        } else {
            FilledTonalButton(
                onClick = { onDeleteAll?.invoke() },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.ButtonPill,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Delete all orphaned uploads",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = "${files.size} files • ${files.sumOf { it.sizeBytes }.fileSizeToString()}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 24.dp),
            ) {
                items(files, key = { it.path }) { file ->
                    OtherUploadTile(
                        file = file,
                        onOpen = { onOpenFile(file) },
                        onDelete = { onDeleteFile(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OtherUploadTile(
    file: OtherUploadFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    if (file.isImage) {
        Surface(
            onClick = onOpen,
            shape = AppShapes.CardMedium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ZoomableAsyncImage(
                        model = file.uri,
                        contentDescription = file.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.08f),
                        contentScale = ContentScale.Crop,
                    )

                    DeleteButton(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        onDelete = onDelete,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = compactChatAttachmentDisplayName(
                            file.displayName.ifBlank { "Image" },
                            maxLength = 24,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = otherUploadMetadataLabel(file),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        Surface(
            onClick = onOpen,
            shape = AppShapes.CardMedium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = mimeToStorageIcon(file.mime),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = file.mime
                                ?.substringAfterLast('/')
                                ?.ifBlank { "FILE" }
                                ?.uppercase()
                                ?.take(8)
                                ?: "FILE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    DeleteButton(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onDelete = onDelete,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = compactChatAttachmentDisplayName(
                            file.displayName.ifBlank { "File" },
                            maxLength = 24,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = otherUploadMetadataLabel(file),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun Modifier.storageArchivedPreview(
    archived: Boolean,
): Modifier {
    if (!archived) {
        return this
    }

    val saturationMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
    val colorFilter = android.graphics.ColorMatrixColorFilter(saturationMatrix)
    val grayscalePaint = android.graphics.Paint().apply {
        this.colorFilter = colorFilter
    }

    return this
        .graphicsLayer { alpha = 0.99f }
        .drawWithContent {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.saveLayer(null, grayscalePaint)
                drawContent()
                canvas.nativeCanvas.restore()
            }
        }
}

private fun otherUploadMetadataLabel(
    file: OtherUploadFile,
): String {
    return buildString {
        append(file.sizeBytes.fileSizeToString())
        if (file.modifiedAt > 0L) {
            append(" | ")
            append(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(file.modifiedAt)))
        }
    }
}

private fun mimeToStorageIcon(
    mime: String?,
): ImageVector {
    return when {
        mime?.startsWith("image/") == true -> Icons.Rounded.Image
        mime?.startsWith("video/") == true -> Icons.Rounded.VideoFile
        mime?.startsWith("audio/") == true -> Icons.Rounded.AudioFile
        mime == "application/pdf" -> Icons.Rounded.Description
        else -> Icons.Rounded.FolderOpen
    }
}
