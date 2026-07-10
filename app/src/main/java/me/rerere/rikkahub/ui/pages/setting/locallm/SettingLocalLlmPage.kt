package me.rerere.rikkahub.ui.pages.setting.locallm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.utils.plus
import me.rerere.locallm.InstalledLocalModel
import me.rerere.locallm.LocalAccelerator
import me.rerere.locallm.LocalDownload
import me.rerere.locallm.LocalModelConfig
import me.rerere.locallm.LocalModelKind
import me.rerere.locallm.LocalModelMetadata
import me.rerere.locallm.LocalRuntimeState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.inferFamilyEntry
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SettingLocalLlmPage(vm: SettingLocalLlmViewModel = koinViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val catalogSnapshot by vm.catalogSnapshot.collectAsStateWithLifecycle()
    val haptics = rememberPremiumHaptics()
    var editingModel by remember { mutableStateOf<InstalledLocalModel?>(null) }
    var modelPendingDelete by remember { mutableStateOf<InstalledLocalModel?>(null) }
    val huggingFaceToken by vm.huggingFaceToken.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val runtimeStatus = runtimeStatusText(state.runtime)
    val installedModelsStartIndex = (if (runtimeStatus != null) 1 else 0) + 1
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index - installedModelsStartIndex
        val toIndex = to.index - installedModelsStartIndex
        if (fromIndex in state.installed.indices && toIndex in state.installed.indices) {
            vm.moveInstalledModel(fromIndex, toIndex)
        }
    }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(stringResource(R.string.local_llm_litert_name))
                    }
                },
            )
        },
        floatingActionButton = {
            ImportModelFab(onInstallUrl = { url ->
                vm.installFromUrl(url)
            })
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(16.dp) + padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Runtime status (loading / generating / switched to CPU / error)
            runtimeStatus?.let { status ->
                item {
                    Surface(
                        shape = AppShapes.CardMedium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(status, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (state.installed.isNotEmpty()) {
                item { 
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(stringResource(R.string.local_llm_manage_files_title)) 
                }
                itemsIndexed(state.installed, key = { _, it -> it.id }) { index, model ->
                    val position = when {
                        state.installed.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == state.installed.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }
                    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 35.dp.toPx() }
                    if (draggingIndex >= 0 && !neighborsUnlocked && kotlin.math.abs(dragOffset) >= thresholdPx) {
                        neighborsUnlocked = true
                    }
                    val neighborOffset = if (
                        draggingIndex >= 0 && draggingIndex != index && !isUnlocked && !neighborsUnlocked
                    ) {
                        when (kotlin.math.abs(index - draggingIndex)) {
                            1 -> dragOffset * 0.35f
                            2 -> dragOffset * 0.12f
                            else -> 0f
                        }
                    } else {
                        0f
                    }
                    val iconUrl = remember(model.id, catalogSnapshot) {
                        catalogSnapshot?.inferFamilyEntry(model.displayName)?.iconUrl
                    }
                    ReorderableItem(state = reorderableState, key = model.id) { isDragging ->
                        PhysicsSwipeToDelete(
                            position = position,
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
                            onDelete = { modelPendingDelete = model },
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isDragging) 0.95f else 1f),
                        ) { shape ->
                            InstalledModelCard(
                                model = model,
                                iconUrl = iconUrl,
                                download = state.downloads[model.id],
                                hasUpdate = model.id in state.updates,
                                shape = shape,
                                onClick = { editingModel = model },
                                onUpdate = { vm.update(model.id) },
                                onDismissError = { vm.dismissDownloadError(model.id) },
                                dragHandle = {
                                    androidx.compose.material3.IconButton(
                                        onClick = {},
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = { haptics.perform(HapticPattern.Pop) },
                                            onDragStopped = { haptics.perform(HapticPattern.Thud) },
                                        ),
                                    ) {
                                        Icon(Icons.Rounded.DragIndicator, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
            } else if (state.runtime is LocalRuntimeState.Idle) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.local_llm_no_models_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.local_llm_no_models_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.importedDownloads.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Downloads")
                }
                items(state.importedDownloads, key = { it.modelId }) { download ->
                    ImportedDownloadCard(
                        download = download,
                        onCancel = { vm.cancelDownload(download.modelId) },
                        onDismissError = { vm.dismissDownloadError(download.modelId) },
                    )
                }
            }

            item { 
                Spacer(Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.local_llm_catalog_title)) 
            }
            itemsIndexed(state.downloadable, key = { _, it -> it.id }) { index, meta ->
                val shape = when {
                    state.downloadable.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
                    index == state.downloadable.lastIndex -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(10.dp)
                }
                val iconUrl = remember(meta.id, catalogSnapshot) {
                    catalogSnapshot?.inferFamilyEntry(meta.name)?.iconUrl
                }
                DownloadableModelCard(
                    meta = meta,
                    iconUrl = iconUrl,
                    shape = shape,
                    download = state.downloads[meta.id],
                    huggingFaceToken = huggingFaceToken,
                    onDownload = { vm.download(meta) },
                    onCancel = { vm.cancelDownload(meta.id) },
                    onDismissError = { vm.dismissDownloadError(meta.id) },
                    onFocusTokenField = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("HuggingFace Configuration")
                Card(
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Set a HuggingFace token to download gated models like Gemma 3.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = huggingFaceToken,
                            onValueChange = { vm.updateHuggingFaceToken(it) },
                            label = { Text("HuggingFace Token") },
                            placeholder = { Text("hf_...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = AppShapes.InputField,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    editingModel?.let { model ->
        // Re-derive from latest state so the sheet reflects live edits.
        val live = state.installed.firstOrNull { it.id == model.id } ?: model
        ModelSettingsSheet(
            model = live,
            onDismiss = { editingModel = null },
            onRename = { vm.rename(live.id, it) },
            onConfigChange = { vm.updateConfig(live.id, it) },
            backend = live.id in state.backendModelIds,
            onBackendChange = { vm.setChatModelBackend(live.id, it) },
            onDelete = {
                vm.delete(live)
                editingModel = null
            },
        )
    }

    modelPendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelPendingDelete = null },
            title = { Text(stringResource(R.string.local_llm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.local_llm_delete_confirm_message, model.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(model)
                    modelPendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { modelPendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun InstalledModelCard(
    model: InstalledLocalModel,
    iconUrl: String?,
    download: LocalDownload?,
    hasUpdate: Boolean,
    shape: androidx.compose.ui.graphics.Shape = AppShapes.CardMedium,
    onClick: () -> Unit,
    onUpdate: () -> Unit,
    onDismissError: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (iconUrl != null) {
                    AutoAIIconWithUrl(name = model.displayName, customIconUri = iconUrl, modifier = Modifier.size(36.dp))
                } else {
                    AutoAIIcon(name = model.displayName, modifier = Modifier.size(36.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "%.1f GB".format(model.sizeInBytes / 1_000_000_000f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasUpdate && download == null) {
                    OutlinedButton(onClick = onUpdate) {
                        Icon(Icons.Rounded.Upgrade, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.local_llm_update))
                    }
                }
                dragHandle()
            }
            CapabilityTags(model.supportsImage, model.supportsAudio, model.supportsThinking, model.supportsSpeculativeDecoding, model.isEmbedding)
            DownloadStatus(download, meta = null, onDismissError = onDismissError)
        }
    }
}

@Composable
private fun ImportedDownloadCard(
    download: LocalDownload,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
) {
    Card(
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DownloadForOffline,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.local_llm_install_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DownloadStatus(
                download = download,
                onDismissError = onDismissError,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun DownloadableModelCard(
    meta: LocalModelMetadata,
    iconUrl: String?,
    shape: androidx.compose.ui.graphics.Shape = AppShapes.CardMedium,
    download: LocalDownload?,
    huggingFaceToken: String,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onFocusTokenField: () -> Unit,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (iconUrl != null) {
                    AutoAIIconWithUrl(name = meta.name, customIconUri = iconUrl, modifier = Modifier.size(36.dp))
                } else {
                    AutoAIIcon(name = meta.name, modifier = Modifier.size(36.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(meta.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        stringResource(R.string.local_llm_catalog_size_format, meta.sizeInGb, meta.minDeviceMemoryInGb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CapabilityTags(meta.supportsImage, meta.supportsAudio, meta.supportsThinking, meta.supportsSpeculativeDecoding, meta.kind == LocalModelKind.EMBEDDING)
            DownloadStatus(download, meta, onDismissError, onCancel)
            
            if (download == null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (meta.requiresLicense && huggingFaceToken.isBlank()) {
                        Button(onClick = onFocusTokenField) {
                            Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Needs HF Token")
                        }
                    } else {
                        Button(onClick = onDownload) {
                            Icon(Icons.Rounded.DownloadForOffline, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.local_llm_catalog_install))
                        }
                    }
                }
            } else if (download is LocalDownload.Failed && meta.requiresLicense && download.message.contains("403")) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { uriHandler.openUri("https://huggingface.co/${meta.hfRepo}") }) {
                        Text("Accept License")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatus(
    download: LocalDownload?,
    meta: LocalModelMetadata? = null,
    onDismissError: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    when (download) {
        is LocalDownload.Running -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (download.progress.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { download.progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.local_llm_download_progress, download.progress.percent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.local_llm_downloading_button),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                onCancel?.let {
                    TextButton(onClick = it) { Text(stringResource(R.string.cancel)) }
                }
            }
        }

        is LocalDownload.Failed -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.local_llm_status_error_format, download.message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.cancel)) }
            }
        }

        null -> Unit
    }
}

@Composable
private fun CapabilityTags(image: Boolean, audio: Boolean, thinking: Boolean, speculative: Boolean, embedding: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (embedding) Tag(type = TagType.SUCCESS) { Text(stringResource(R.string.local_llm_catalog_tag_embedding)) }
        if (image || audio) Tag(type = TagType.INFO) { Text(stringResource(R.string.local_llm_catalog_tag_multimodal)) }
        if (thinking) Tag(type = TagType.INFO) { Text(stringResource(R.string.local_llm_catalog_tag_thinking)) }
        if (speculative) Tag(type = TagType.INFO) { Text(stringResource(R.string.local_llm_catalog_tag_speculative)) }
    }
}

@Composable
private fun ImportModelFab(onInstallUrl: (String) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val invalidUrlMessage = stringResource(R.string.local_llm_invalid_url)

    FloatingActionButton(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            showSheet = true
        },
        shape = AppShapes.CardLarge,
    ) {
        Icon(Icons.Rounded.DownloadForOffline, contentDescription = stringResource(R.string.local_llm_install_url_action))
    }

    if (showSheet) {
        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.local_llm_install_url_action), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.local_llm_install_url_label)) },
                    supportingText = { Text(stringResource(R.string.local_llm_install_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = AppShapes.InputField,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = { showSheet = false }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val trimmed = url.trim()
                            if (me.rerere.locallm.ModelInstall.parseImportUrl(trimmed) == null) {
                                toaster.show(invalidUrlMessage, type = ToastType.Error)
                            } else {
                                onInstallUrl(trimmed)
                                url = ""
                                showSheet = false
                            }
                        },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.local_llm_install_url_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsSheet(
    model: InstalledLocalModel,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onConfigChange: (LocalModelConfig) -> Unit,
    backend: Boolean,
    onBackendChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(model.id) { mutableStateOf(model.displayName) }
    var config by remember(model.id) { mutableStateOf(model.config) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val default = model.defaultConfig

    fun push(newConfig: LocalModelConfig) {
        config = newConfig
        onConfigChange(newConfig)
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = {
            if (name.trim() != model.displayName) onRename(name)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AutoAIIcon(name = model.displayName, modifier = Modifier.size(44.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.local_llm_rename_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.InputField,
                )
            }

            // Generation sampling controls are meaningless for an embedding model.
            if (!model.isEmbedding) {
                SliderRow(
                    label = stringResource(R.string.local_llm_max_tokens_label),
                    value = (config.contextLength ?: default.effectiveContextLength).toFloat(),
                    valueRange = 512f..default.effectiveContextLength.toFloat().coerceAtLeast(512f),
                    steps = 0,
                    valueText = (config.contextLength ?: default.effectiveContextLength).toString(),
                    onChange = { push(config.copy(contextLength = it.toInt())) },
                )
                SliderRow(
                    label = "Top-K",
                    value = (config.topK ?: default.topK).toFloat(),
                    valueRange = 1f..128f,
                    steps = 0,
                    valueText = (config.topK ?: default.topK).toString(),
                    onChange = { push(config.copy(topK = it.toInt())) },
                )
                SliderRow(
                    label = "Top-P",
                    value = config.topP ?: default.topP,
                    valueRange = 0f..1f,
                    steps = 0,
                    valueText = "%.2f".format(config.topP ?: default.topP),
                    onChange = { push(config.copy(topP = it)) },
                )
                SliderRow(
                    label = "Temperature",
                    value = config.temperature ?: default.temperature,
                    valueRange = 0f..2f,
                    steps = 0,
                    valueText = "%.2f".format(config.temperature ?: default.temperature),
                    onChange = { push(config.copy(temperature = it)) },
                )

                LocalChatBackendToggle(
                    backend = backend,
                    onBackendChange = onBackendChange,
                )
            }

            Text("Accelerator", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(LocalAccelerator.AUTO, LocalAccelerator.CPU, LocalAccelerator.GPU)
                options.forEachIndexed { index, acc ->
                    SegmentedButton(
                        selected = config.accelerator == acc,
                        onClick = { push(config.copy(accelerator = acc)) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(acc.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
            if (config.accelerator != LocalAccelerator.CPU) {
                Text(
                    stringResource(R.string.local_llm_try_gpu_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.local_llm_delete_model), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.local_llm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.local_llm_delete_confirm_message, model.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LocalChatBackendToggle(
    backend: Boolean,
    onBackendChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.setting_provider_page_backend_model),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.setting_provider_page_backend_model_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HapticSwitch(
            checked = backend,
            onCheckedChange = onBackendChange,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = valueRange, steps = steps)
    }
}

private fun runtimeStatusText(state: LocalRuntimeState): String? = when (state) {
    is LocalRuntimeState.LoadingModel -> "Loading ${state.displayName}…"
    is LocalRuntimeState.Generating -> "Generating with ${state.displayName}…"
    is LocalRuntimeState.SwitchedToCpu -> "Switched ${state.displayName} to CPU"
    is LocalRuntimeState.Error -> "Error: ${state.message}"
    LocalRuntimeState.Idle -> null
    is LocalRuntimeState.Ready -> null
}

