@file:OptIn(ExperimentalLayoutApi::class)

package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DISABLED_MODEL_ID
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheModelStats
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
import me.rerere.rikkahub.ui.components.ui.TagType
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
private const val CATEGORY_WORKSPACES = "workspaces"
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
    @StringRes val labelRes: Int,
    val kind: ChatAttachmentKind?,
) {
    ALL(R.string.setting_chat_storage_filter_all, null),
    IMAGES(R.string.setting_chat_storage_filter_images, ChatAttachmentKind.IMAGE),
    DOCS(R.string.setting_chat_storage_filter_docs, ChatAttachmentKind.DOCUMENT),
    VIDEOS(R.string.setting_chat_storage_filter_videos, ChatAttachmentKind.VIDEO),
    AUDIO(R.string.setting_chat_storage_filter_audio, ChatAttachmentKind.AUDIO),
}

private enum class StorageSort(@StringRes val labelRes: Int) {
    RECENT(R.string.setting_chat_storage_sort_recent),
    SIZE(R.string.setting_chat_storage_sort_size),
    NAME(R.string.setting_chat_storage_sort_name),
}

private val RESOLUTION_OPTIONS = listOf<Int?>(null, 512, 640, 768, 960, 1024, 1280, 1600, 1920, 2048, 2560, 3072, 4096)
private val AUTO_DELETE_OPTIONS = listOf<Int?>(null, 7, 30, 90, 180)

@Composable
fun SettingChatStoragePage(
    vm: SettingVM = koinViewModel(),
    repository: ChatAttachmentRepository = koinInject(),
    appStorageRepository: AppStorageRepository = koinInject(),
    embeddingCacheDAO: EmbeddingCacheDAO = koinInject(),
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
    var embeddingCacheStats by remember { mutableStateOf<List<EmbeddingCacheModelStats>>(emptyList()) }
    var isCleaningEmbeddingCache by remember { mutableStateOf(false) }
    var showEmbeddingCacheInspector by rememberSaveable { mutableStateOf(false) }
    var pendingModelCacheDeletion by remember { mutableStateOf<String?>(null) }

    val activeEmbeddingModelIds = remember(settings.embeddingModelId, settings.assistants) {
        buildSet {
            settings.embeddingModelId.toString().takeIf { it != DISABLED_MODEL_ID.toString() }?.let(::add)
            settings.assistants.mapNotNull { assistant -> assistant.embeddingModelId }
                .map { it.toString() }
                .filter { it != DISABLED_MODEL_ID.toString() }
                .forEach(::add)
        }
    }
    val unusedEmbeddingCacheStats = remember(embeddingCacheStats, activeEmbeddingModelIds) {
        embeddingCacheStats.filter { it.modelId !in activeEmbeddingModelIds }
    }

    fun loadEmbeddingCacheStats() {
        scope.launch {
            embeddingCacheStats = withContext(Dispatchers.IO) {
                embeddingCacheDAO.getModelStats()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadEmbeddingCacheStats()
    }

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
                title = stringResource(R.string.setting_page_chat_storage),
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
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                    AppStorageHeroCard(
                        snapshot = appStorageSnapshot,
                        chatSummary = chatSummary,
                    )
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsGroup(
                    title = stringResource(R.string.setting_chat_storage_app_storage_title),
                    horizontalPadding = 4.dp,
                    titleStartPadding = 4.dp,
                ) {
                    val primaryCategories = appStorageSnapshot.categories.filter { it.isPrimaryStorageCategory() }
                    val secondaryCategories = appStorageSnapshot.categories.filterNot { it.isPrimaryStorageCategory() }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (appStorageSnapshot.isScanning && appStorageSnapshot.categories.isEmpty()) {
                            SettingGroupItem(
                                title = stringResource(R.string.setting_chat_storage_scanning_title),
                                subtitle = stringResource(R.string.setting_chat_storage_scanning_desc),
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
                                        stringResource(R.string.setting_chat_storage_hide_more_categories)
                                    } else {
                                        stringResource(R.string.setting_chat_storage_show_more_categories)
                                    },
                                    subtitle = if (showAllStorageCategories) {
                                        stringResource(R.string.setting_chat_storage_hide_more_categories_desc)
                                    } else {
                                        stringResource(
                                            R.string.setting_chat_storage_more_categories_count,
                                            secondaryCategories.size
                                        )
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
                    title = stringResource(R.string.setting_chat_storage_chat_settings),
                    horizontalPadding = 4.dp,
                    titleStartPadding = 4.dp,
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
                                title = stringResource(R.string.setting_chat_storage_duplicate_space),
                                subtitle = chatSummary.overview.duplicateSizeBytes.fileSizeToString(),
                                icon = { Icon(Icons.Rounded.Inventory2, null) },
                            )
                        }

                        SettingGroupInputItem(
                            title = stringResource(R.string.setting_chat_storage_maintenance_title),
                            subtitle = stringResource(R.string.setting_chat_storage_maintenance_desc),
                            icon = { Icon(Icons.Rounded.CleaningServices, null) },
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Thud)
                                    WorkManager.getInstance(context).enqueue(
                                        OneTimeWorkRequestBuilder<ChatStorageMaintenanceWorker>().build()
                                    )
                                    toaster.show(
                                        context.getString(R.string.setting_chat_storage_maintenance_queued),
                                        type = ToastType.Info
                                    )
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
                                    text = stringResource(R.string.setting_chat_storage_run_maintenance),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsGroup(
                    title = "Memory embeddings",
                    horizontalPadding = 4.dp,
                    titleStartPadding = 4.dp,
                ) {
                    val unusedCount = unusedEmbeddingCacheStats.sumOf { it.count }
                    val unusedBytes = unusedEmbeddingCacheStats.sumOf { it.estimatedBytes }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingGroupItem(
                            title = "Cached vectors",
                            subtitle = if (embeddingCacheStats.isEmpty()) {
                                "No cached embeddings yet."
                            } else {
                                "${embeddingCacheStats.sumOf { it.count }} vectors · ${embeddingCacheStats.size} models"
                            },
                            icon = { Icon(Icons.Rounded.Memory, null) },
                            onClick = {
                                showEmbeddingCacheInspector = true
                            }
                        )
                        SettingGroupInputItem(
                            title = "Unused embeddings",
                            subtitle = if (unusedCount == 0) {
                                "Nothing to clean."
                            } else {
                                "$unusedCount vectors · ${unusedBytes.fileSizeToString()}"
                            },
                            icon = { Icon(Icons.Rounded.Delete, null) },
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Thud)
                                    scope.launch {
                                        isCleaningEmbeddingCache = true
                                        val deleted = withContext(Dispatchers.IO) {
                                            if (activeEmbeddingModelIds.isEmpty()) {
                                                embeddingCacheDAO.deleteAllEmbeddings()
                                            } else {
                                                embeddingCacheDAO.deleteExceptModelIds(activeEmbeddingModelIds.toList())
                                            }
                                        }
                                        isCleaningEmbeddingCache = false
                                        loadEmbeddingCacheStats()
                                        toaster.show(
                                            "Deleted $deleted unused memory embeddings",
                                            type = ToastType.Success,
                                        )
                                    }
                                },
                                enabled = unusedCount > 0 && !isCleaningEmbeddingCache,
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.ButtonPill,
                            ) {
                                if (isCleaningEmbeddingCache) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = "Delete unused",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsGroup(
                    title = stringResource(R.string.setting_chat_storage_files_title),
                    horizontalPadding = 4.dp,
                    titleStartPadding = 4.dp,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactChipSection(
                            title = stringResource(R.string.setting_chat_storage_filter_title),
                            icon = Icons.Rounded.FilterAlt,
                        ) {
                            StorageFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        filter = option
                                    },
                                    label = { Text(stringResource(option.labelRes)) },
                                )
                            }
                        }

                        CompactChipSection(
                            title = stringResource(R.string.setting_chat_storage_sort_title),
                            icon = Icons.AutoMirrored.Rounded.Sort,
                        ) {
                            StorageSort.entries.forEach { option ->
                                FilterChip(
                                    selected = sort == option,
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        sort = option
                                    },
                                    label = { Text(stringResource(option.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }

            if (visibleFiles.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyFilesCard(
                        isSyncing = appStorageSnapshot.isScanning,
                        hasFilters = filter != StorageFilter.ALL,
                    )
                }
            } else {
                items(visibleFiles, key = { it.id }) { attachment ->
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

    pendingDeletion?.let { attachment ->
        val fallbackFileName = stringResource(R.string.setting_chat_storage_file_fallback)
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.setting_chat_storage_delete_stored_file_title)) },
            text = {
                Text(
                    context.getString(
                        R.string.setting_chat_storage_delete_stored_file_message,
                        attachment.displayName.ifBlank { fallbackFileName }
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = attachment
                        pendingDeletion = null
                        scope.launch {
                            repository.deleteAttachment(target.id)
                            appStorageRepository.refreshNow()
                            toaster.show(
                                context.getString(R.string.setting_chat_storage_removed_from_chat),
                                type = ToastType.Info
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingOtherUploadDeletion?.let { file ->
        val fallbackFileName = stringResource(R.string.setting_chat_storage_file_fallback)
        AlertDialog(
            onDismissRequest = { pendingOtherUploadDeletion = null },
            title = { Text(stringResource(R.string.setting_chat_storage_delete_untracked_file_title)) },
            text = {
                Text(
                    context.getString(
                        R.string.setting_chat_storage_delete_untracked_file_message,
                        file.displayName.ifBlank { fallbackFileName }
                    )
                )
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
                                toaster.show(
                                    context.getString(R.string.setting_chat_storage_removed_from_uploads),
                                    type = ToastType.Info
                                )
                            } else {
                                toaster.show(
                                    context.getString(R.string.setting_chat_storage_remove_failed),
                                    type = ToastType.Error
                                )
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOtherUploadDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingDeleteAllOtherUploads) {
        AlertDialog(
            onDismissRequest = { pendingDeleteAllOtherUploads = false },
            title = { Text(stringResource(R.string.setting_chat_storage_delete_orphaned_title)) },
            text = {
                Text(stringResource(R.string.setting_chat_storage_delete_orphaned_message))
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
                                    context.resources.getQuantityString(
                                        R.plurals.setting_chat_storage_orphaned_removed,
                                        deletedCount,
                                        deletedCount
                                    )
                                } else {
                                    context.getString(R.string.setting_chat_storage_orphaned_none)
                                },
                                type = ToastType.Info,
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.setting_chat_storage_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAllOtherUploads = false }) {
                    Text(stringResource(R.string.cancel))
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
    if (showEmbeddingCacheInspector) {
        ModalBottomSheet(
            onDismissRequest = { showEmbeddingCacheInspector = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = {
                IconButton(onClick = { showEmbeddingCacheInspector = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Embedding model cache",
                    style = MaterialTheme.typography.titleLarge
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(embeddingCacheStats) { stat ->
                        val isSelected = stat.modelId in activeEmbeddingModelIds
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stat.modelId.substringAfterLast("/"),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${stat.count} vectors · ${stat.estimatedBytes.fileSizeToString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (isSelected) {
                                    me.rerere.rikkahub.ui.components.ui.Tag(type = me.rerere.rikkahub.ui.components.ui.TagType.INFO) {
                                        Text("Selected")
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            pendingModelCacheDeletion = stat.modelId
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingModelCacheDeletion?.let { modelId ->
        AlertDialog(
            onDismissRequest = { pendingModelCacheDeletion = null },
            title = { Text("Delete cached embeddings?") },
            text = { Text("This will remove all cached vector data for $modelId. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = modelId
                        pendingModelCacheDeletion = null
                        scope.launch {
                            embeddingCacheDAO.deleteByModelId(target)
                            loadEmbeddingCacheStats()
                            toaster.show("Cache deleted for $target")
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModelCacheDeletion = null }) {
                    Text("Cancel")
                }
            }
        )
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
                            text = stringResource(R.string.setting_chat_storage_app_storage_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = if (snapshot.isScanning) {
                                stringResource(R.string.setting_storage_scanning)
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
                        stringResource(R.string.setting_chat_storage_chat_usage_scanning)
                    } else {
                        stringResource(
                            R.string.setting_chat_storage_chat_usage_value,
                            snapshot.chatBytes.fileSizeToString(),
                            snapshot.chatCount
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!snapshot.isScanning) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StorageStatPill(
                            text = stringResource(
                                R.string.setting_chat_storage_stat_app,
                                snapshot.appBytes.fileSizeToString()
                            )
                        )
                        StorageStatPill(
                            text = stringResource(
                                R.string.setting_chat_storage_stat_data,
                                snapshot.dataBytes.fileSizeToString()
                            )
                        )
                        StorageStatPill(
                            text = stringResource(
                                R.string.setting_chat_storage_stat_cache,
                                snapshot.cacheBytes.fileSizeToString()
                            )
                        )
                        if (chatSummary.overview.duplicateSizeBytes > 0L) {
                            StorageStatPill(
                                text = stringResource(
                                    R.string.setting_chat_storage_stat_chat_dupes,
                                    chatSummary.overview.duplicateSizeBytes.fileSizeToString()
                                )
                            )
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categoryLabel = remember(category, context) { category.localizedLabel(context) }
    SettingGroupItem(
        title = categoryLabel,
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
                                    context.getString(
                                        R.string.setting_chat_storage_category_cleared,
                                        categoryLabel
                                    ),
                                    type = ToastType.Info,
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.setting_chat_storage_clear_action))
                    }
                }
            }

            onInspect != null -> {
                {
                    TextButton(onClick = onInspect) {
                        Text(stringResource(R.string.setting_chat_storage_inspect_action))
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
        title = stringResource(R.string.setting_chat_storage_image_resolution_title),
        subtitle = stringResource(R.string.setting_chat_storage_image_resolution_desc),
        icon = { Icon(Icons.Rounded.PhotoSizeSelectLarge, null) },
    ) {
        Text(
            text = selectedValue?.let {
                stringResource(R.string.setting_chat_storage_px_long_edge, it)
            } ?: stringResource(R.string.setting_chat_storage_original_size),
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
        title = stringResource(R.string.setting_chat_storage_delete_old_images_title),
        subtitle = stringResource(R.string.setting_chat_storage_delete_old_images_desc),
        icon = { Icon(Icons.Rounded.Schedule, null) },
    ) {
        Text(
            text = selectedValue?.let {
                stringResource(R.string.setting_chat_storage_days_value, it)
            } ?: stringResource(R.string.setting_chat_storage_never),
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
                text = if (isSyncing) {
                    stringResource(R.string.setting_chat_storage_empty_scanning)
                } else {
                    stringResource(R.string.setting_chat_storage_empty_none)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (isSyncing) {
                    stringResource(R.string.setting_chat_storage_empty_scanning_desc)
                } else if (hasFilters) {
                    stringResource(R.string.setting_chat_storage_empty_filtered_desc)
                } else {
                    stringResource(R.string.setting_chat_storage_empty_default_desc)
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
    val context = LocalContext.current
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
                        attachment.displayName.ifBlank {
                            context.getString(R.string.setting_chat_storage_image_fallback)
                        },
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
    val defaultName = remember(attachment.kind, context) { attachment.kind.defaultName(context) }

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
                            .substringAfterLast('/', defaultName)
                            .ifBlank { defaultName }
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
                        attachment.displayName.ifBlank { defaultName },
                        maxLength = 24,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = fileMetadataLabel(context, attachment),
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
                contentDescription = stringResource(R.string.delete),
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
    context: Context,
    attachment: ChatAttachmentUsage,
): String {
    return buildString {
        append(attachment.sizeBytes.fileSizeToString())
        append(" | ")
        append(
            context.resources.getQuantityString(
                R.plurals.setting_chat_storage_chats_count,
                attachment.referenceCount,
                attachment.referenceCount
            )
        )
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

private fun ChatAttachmentKind.defaultName(context: Context): String {
    return when (this) {
        ChatAttachmentKind.IMAGE -> context.getString(R.string.setting_chat_storage_image_fallback)
        ChatAttachmentKind.DOCUMENT -> context.getString(R.string.setting_chat_storage_document_fallback)
        ChatAttachmentKind.VIDEO -> context.getString(R.string.video)
        ChatAttachmentKind.AUDIO -> context.getString(R.string.audio)
    }
}

private fun StorageCategoryUsage.localizedLabel(context: Context): String {
    return when (id) {
        CATEGORY_CHAT -> context.getString(R.string.setting_chat_storage_category_chat_attachments)
        CATEGORY_ASSISTANT_MEDIA -> context.getString(R.string.setting_chat_storage_category_assistant_media)
        CATEGORY_LOREBOOK_MEDIA -> context.getString(R.string.setting_chat_storage_category_lorebook_media)
        CATEGORY_GENERATED_MEDIA -> context.getString(R.string.setting_chat_storage_category_generated_media)
        CATEGORY_WORKSPACES -> context.getString(R.string.setting_chat_storage_category_workspaces)
        CATEGORY_ICONS_AND_FONTS -> context.getString(R.string.setting_chat_storage_category_icons_and_fonts)
        CATEGORY_DATABASES -> context.getString(R.string.setting_chat_storage_category_settings_database)
        CATEGORY_ICON_CACHE -> context.getString(R.string.setting_chat_storage_category_icon_cache)
        CATEGORY_OCR_CACHE -> context.getString(R.string.setting_chat_storage_category_ocr_cache)
        CATEGORY_TEMP_FILES -> context.getString(R.string.setting_chat_storage_category_temp_files)
        CATEGORY_APP_CACHE -> context.getString(R.string.setting_chat_storage_category_app_cache)
        CATEGORY_CODE_CACHE -> context.getString(R.string.setting_chat_storage_category_code_cache)
        CATEGORY_OTHER_UPLOADS -> context.getString(R.string.setting_chat_storage_category_other_uploads)
        CATEGORY_OTHER_APP_DATA -> context.getString(R.string.setting_chat_storage_category_other_app_data)
        else -> label
    }
}

private fun StorageCategoryUsage.icon(): ImageVector {
    return when (id) {
        CATEGORY_CHAT -> Icons.Rounded.Storage
        CATEGORY_ASSISTANT_MEDIA -> Icons.Rounded.Visibility
        CATEGORY_LOREBOOK_MEDIA -> Icons.Rounded.FolderOpen
        CATEGORY_GENERATED_MEDIA -> Icons.Rounded.Image
        CATEGORY_WORKSPACES -> Icons.Rounded.Code
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
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_chat_storage_other_uploads_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (isLoading) {
                stringResource(R.string.setting_chat_storage_other_uploads_scanning)
            } else {
                stringResource(R.string.setting_chat_storage_other_uploads_desc)
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
                    text = stringResource(R.string.setting_chat_storage_delete_all_orphaned),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = context.resources.getQuantityString(
                    R.plurals.setting_chat_storage_file_count_size,
                    files.size,
                    files.size,
                    files.sumOf { it.sizeBytes }.fileSizeToString()
                ),
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
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
    val context = LocalContext.current
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
                            file.displayName.ifBlank {
                                context.getString(R.string.setting_chat_storage_image_fallback)
                            },
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
                                ?.ifBlank {
                                    context.getString(R.string.setting_chat_storage_file_fallback).uppercase()
                                }
                                ?.uppercase()
                                ?.take(8)
                                ?: context.getString(R.string.setting_chat_storage_file_fallback).uppercase(),
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
                            file.displayName.ifBlank {
                                context.getString(R.string.setting_chat_storage_file_fallback)
                            },
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
