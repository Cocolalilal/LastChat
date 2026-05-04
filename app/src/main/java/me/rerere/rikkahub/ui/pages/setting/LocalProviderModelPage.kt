package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.local.CompatibilityResult
import me.rerere.rikkahub.data.ai.local.LocalModelCatalogEntry
import me.rerere.rikkahub.data.ai.local.LocalModelCatalogState
import me.rerere.rikkahub.data.ai.local.LocalModelDownloadAccess
import me.rerere.rikkahub.data.ai.local.LocalModelInstallCoordinator
import me.rerere.rikkahub.data.ai.local.LocalModelRepository
import me.rerere.rikkahub.data.ai.local.LocalModelStatus
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ui.ClickableIconPicker
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.ModelIcon
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@Composable
internal fun LocalProviderModelPage(
    provider: ProviderSetting.Local,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val repository = koinInject<LocalModelRepository>()
    val coordinator = koinInject<LocalModelInstallCoordinator>()
    val states by repository.observeCatalogStates().collectAsStateWithLifecycle(initialValue = emptyList())
    val installedStates = remember(states) { states.filter { it.status == LocalModelStatus.READY } }
    val activeStates = remember(states) { states.filter { it.status in activeStatuses } }
    val haptics = rememberPremiumHaptics()
    var showSheet by remember { mutableStateOf(false) }
    var editingState by remember { mutableStateOf<LocalModelCatalogState?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp) + PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.local_model_page_intro),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.local_model_page_intro_secondary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (activeStates.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.local_model_page_active_downloads),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(activeStates, key = { it.entry.id }) { state ->
                    LocalModelCard(
                        state = state,
                        onPrimaryAction = { coordinator.cancelModel(state.entry.id) },
                        onSecondaryAction = null,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.local_model_page_installed_models),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (installedStates.isEmpty()) {
                item {
                    Card(
                        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.local_model_page_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.local_model_page_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(installedStates, key = { _, state -> state.entry.id }) { index, state ->
                    SwipeableLocalModelCard(
                        state = state,
                        position = itemPosition(index, installedStates.size),
                        onRemove = { coordinator.removeModel(state.entry.id) },
                        onEdit = { editingState = state },
                        onSecondaryAction = if (state.entry.downloadAccess == LocalModelDownloadAccess.PUBLIC) {
                            { coordinator.retryModel(state.entry.id) }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )

        FloatingActionButton(
            onClick = {
                showSheet = true
                haptics.perform(HapticPattern.Pop)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).offset(y = (-12).dp),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(Icons.Rounded.Widgets, contentDescription = stringResource(R.string.local_model_page_manage_models))
        }
    }

    if (showSheet) {
        LocalModelPickerSheet(
            provider = provider,
            states = states,
            onDismiss = { showSheet = false },
            onDownload = { coordinator.downloadModel(it) },
            onRetry = { coordinator.retryModel(it) },
            onCancel = { coordinator.cancelModel(it) },
            onRemove = { coordinator.removeModel(it) },
            onEdit = { editingState = it },
        )
    }

    editingState?.let { state ->
        LocalModelEditSheet(
            state = state,
            provider = provider,
            onDismiss = { editingState = null },
            onSave = { model ->
                scope.launch {
                    repository.updateModelMetadata(model)
                }
                editingState = null
            },
        )
    }
}

@Composable
private fun LocalModelPickerSheet(
    provider: ProviderSetting.Local,
    states: List<LocalModelCatalogState>,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (LocalModelCatalogState) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterText by remember { mutableStateOf("") }
    val filteredStates = remember(states, filterText) {
        val keywords = filterText.split(" ").filter { it.isNotBlank() }
        states.fastFilter { state ->
            keywords.isEmpty() || keywords.all { keyword ->
                state.entry.displayName.contains(keyword, ignoreCase = true) ||
                    state.entry.modelId.contains(keyword, ignoreCase = true) ||
                    state.entry.description.contains(keyword, ignoreCase = true)
            }
        }
    }
    val compatibilityEstimator = koinInject<me.rerere.rikkahub.data.ai.local.LocalCompatibilityEstimator>()
    val repository = koinInject<LocalModelRepository>()
    var hfSearchStates by remember { mutableStateOf<List<LocalModelCatalogState>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(filterText) {
        if (filterText.length > 2) {
            kotlinx.coroutines.delay(500)
            isSearching = true
            val results = repository.searchHuggingFaceModels(filterText)
            hfSearchStates = results.map { entry ->
                LocalModelCatalogState(
                    entry = entry,
                    install = null,
                    compatibility = compatibilityEstimator.estimate(entry)
                )
            }
            isSearching = false
        } else {
            hfSearchStates = emptyList()
        }
    }

    val coordinator = koinInject<LocalModelInstallCoordinator>()
    val scope = rememberCoroutineScope()
    var hfToken by remember { mutableStateOf("") }
    var hasHfToken by remember { mutableStateOf(false) }
    var showHfSettings by remember { mutableStateOf(false) }
    var pendingDownloadState by remember { mutableStateOf<LocalModelCatalogState?>(null) }

    LaunchedEffect(Unit) {
        hfToken = repository.getHuggingFaceToken()
        hasHfToken = hfToken.isNotBlank()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            coordinator.importModel(uri)
        }
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        haptics.perform(HapticPattern.Tick)
                        importLauncher.launch(arrayOf("*/*"))
                    },
                ) {
                    Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.local_model_page_import_button))
                }
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showHfSettings = !showHfSettings
                    },
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Hugging Face")
                }
            }

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text(stringResource(R.string.local_model_page_filter_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField,
            )

            if (showHfSettings) {
                Card(
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Hugging Face", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "For gated downloads. Stored encrypted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = hfToken,
                                onValueChange = { hfToken = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("hf_ token") },
                                visualTransformation = PasswordVisualTransformation(),
                                shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField,
                            )
                            Button(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    scope.launch {
                                        repository.saveHuggingFaceToken(hfToken)
                                        hasHfToken = hfToken.isNotBlank()
                                    }
                                },
                            ) {
                                Text(if (hasHfToken) "Update" else "Save")
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (isSearching) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                
                val combinedStates = (filteredStates + hfSearchStates).distinctBy { it.entry.id }
                
                items(combinedStates, key = { it.entry.id }) { state ->
                    val primaryAction: (() -> Unit)? = when (state.status) {
                        LocalModelStatus.READY -> null
                        LocalModelStatus.QUEUED,
                        LocalModelStatus.DOWNLOADING,
                        LocalModelStatus.VERIFYING,
                        LocalModelStatus.INSTALLING -> ({ onCancel(state.entry.id) })
                        LocalModelStatus.FAILED,
                        LocalModelStatus.CANCELED -> ({
                            if (state.entry.canDownloadFromHub(hasHfToken)) onRetry(state.entry.id)
                            else importLauncher.launch(arrayOf("*/*"))
                        })
                        LocalModelStatus.NOT_DOWNLOADED -> ({
                            if (state.entry.canDownloadFromHub(hasHfToken)) {
                                if (state.entry.provenance == me.rerere.rikkahub.data.ai.local.LocalModelProvenance.CURATED) {
                                    val act = {
                                        scope.launch {
                                            repository.queueHuggingFaceModel(state.entry)
                                            coordinator.downloadModel(state.entry.id)
                                        }
                                    }
                                    if (state.compatibility.result == CompatibilityResult.Tight) {
                                        pendingDownloadState = state
                                    } else {
                                        act()
                                    }
                                } else {
                                    if (state.compatibility.result == CompatibilityResult.Tight) {
                                        pendingDownloadState = state
                                    } else {
                                        onDownload(state.entry.id)
                                    }
                                }
                            } else importLauncher.launch(arrayOf("*/*"))
                        })
                        LocalModelStatus.INCOMPATIBLE -> null
                    }
                    val secondaryAction: (() -> Unit)? = when {
                        state.status == LocalModelStatus.READY && state.entry.downloadAccess == LocalModelDownloadAccess.PUBLIC -> ({ onRetry(state.entry.id) })
                        else -> null
                    }
                    if (state.status == LocalModelStatus.READY) {
                        SwipeableLocalModelCard(
                            state = state,
                            hasHuggingFaceToken = hasHfToken,
                            position = ItemPosition.ONLY,
                            onRemove = { onRemove(state.entry.id) },
                            onEdit = { onEdit(state) },
                            onSecondaryAction = secondaryAction,
                        )
                    } else {
                        LocalModelCard(
                            state = state,
                            hasHuggingFaceToken = hasHfToken,
                            onPrimaryAction = primaryAction,
                            onSecondaryAction = secondaryAction,
                            onEdit = null,
                        )
                    }
                }
            }
        }
    }

    pendingDownloadState?.let { state ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDownloadState = null },
            title = { Text("Memory Warning") },
            text = { Text(state.compatibility.reasons.joinToString(" \n")) },
            confirmButton = {
                Button(
                    onClick = {
                        if (state.entry.provenance == me.rerere.rikkahub.data.ai.local.LocalModelProvenance.CURATED) {
                            scope.launch {
                                repository.queueHuggingFaceModel(state.entry)
                                coordinator.downloadModel(state.entry.id)
                            }
                        } else {
                            onDownload(state.entry.id)
                        }
                        pendingDownloadState = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDownloadState = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SwipeableLocalModelCard(
    state: LocalModelCatalogState,
    position: ItemPosition,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onSecondaryAction: (() -> Unit)?,
    hasHuggingFaceToken: Boolean = false,
) {
    PhysicsSwipeToDelete(
        position = position,
        deleteEnabled = true,
        onDelete = onRemove,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LocalModelCard(
            state = state,
            hasHuggingFaceToken = hasHuggingFaceToken,
            onPrimaryAction = null,
            onSecondaryAction = onSecondaryAction,
            onEdit = onEdit,
        )
    }
}

@Composable
private fun LocalModelCard(
    state: LocalModelCatalogState,
    hasHuggingFaceToken: Boolean = false,
    onPrimaryAction: (() -> Unit)?,
    onSecondaryAction: (() -> Unit)?,
    onEdit: (() -> Unit)? = null,
) {
    val install = state.install
    val model = remember(state.entry) { state.entry.toModel() }
    val progress = install?.progressPercent?.coerceIn(0, 100) ?: 0

    Card(
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.entry.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.status != LocalModelStatus.READY) {
                Text(
                    text = state.entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(type = statusTagType(state.status)) {
                    Text(statusLabel(state.status))
                }
                Tag(type = when (state.compatibility.result) {
                    CompatibilityResult.Supported -> TagType.SUCCESS
                    CompatibilityResult.Tight -> TagType.WARNING
                    CompatibilityResult.Unsupported -> TagType.ERROR
                }) {
                    Text(state.compatibility.result.name)
                }
                if (state.entry.downloadAccess != LocalModelDownloadAccess.PUBLIC) {
                    Tag(type = TagType.INFO) {
                        Text(accessLabel(state.entry.downloadAccess, hasHuggingFaceToken))
                    }
                }
            }

            if (state.status != LocalModelStatus.READY) {
                Text(
                    text = compactSizeLine(state.entry, install?.bytesDownloaded ?: 0L),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.status in activeStatuses) {
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = compactProgressDetailLine(install),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (false && state.entry.featureTags.isNotEmpty()) {
                Text(
                    text = state.entry.featureTags.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.compatibility.reasons.isNotEmpty()) {
                Text(
                    text = state.compatibility.reasons.joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!install?.lastError.isNullOrBlank()) {
                Text(
                    text = install?.lastError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (onPrimaryAction != null || onSecondaryAction != null || onEdit != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onEdit?.let {
                        OutlinedButton(
                            onClick = it, 
                            modifier = Modifier.weight(1f),
                            shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill
                        ) {
                            Text("Edit")
                        }
                    }
                    onPrimaryAction?.let {
                        Button(
                            onClick = it,
                            modifier = Modifier.weight(1f),
                            enabled = state.status != LocalModelStatus.INCOMPATIBLE,
                            shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(primaryButtonLabel(state, hasHuggingFaceToken))
                        }
                    }
                    onSecondaryAction?.let {
                        OutlinedButton(
                            onClick = it, 
                            modifier = Modifier.weight(1f),
                            shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill
                        ) {
                            Text(stringResource(R.string.local_model_page_redownload))
                        }
                    }
                }
            }
        }
    }
}

private fun primaryButtonLabel(state: LocalModelCatalogState, hasHuggingFaceToken: Boolean): String {
    return when (state.status) {
        LocalModelStatus.READY -> "Open" // Should not be visible, but just in case
        LocalModelStatus.QUEUED,
        LocalModelStatus.DOWNLOADING,
        LocalModelStatus.VERIFYING,
        LocalModelStatus.INSTALLING -> "Cancel"
        LocalModelStatus.NOT_DOWNLOADED -> when (state.entry.downloadAccess) {
            LocalModelDownloadAccess.PUBLIC -> "Download"
            LocalModelDownloadAccess.AUTH_REQUIRED -> if (hasHuggingFaceToken) "Download" else "Import"
            LocalModelDownloadAccess.IMPORT_ONLY -> "Import"
        }
        LocalModelStatus.FAILED,
        LocalModelStatus.CANCELED -> when (state.entry.downloadAccess) {
            LocalModelDownloadAccess.PUBLIC -> "Retry"
            LocalModelDownloadAccess.AUTH_REQUIRED -> if (hasHuggingFaceToken) "Retry" else "Import again"
            LocalModelDownloadAccess.IMPORT_ONLY -> "Import again"
        }
        LocalModelStatus.INCOMPATIBLE -> "Unavailable"
    }
}

private fun LocalModelCatalogEntry.canDownloadFromHub(hasHuggingFaceToken: Boolean): Boolean {
    return downloadAccess == LocalModelDownloadAccess.PUBLIC ||
        (downloadAccess == LocalModelDownloadAccess.AUTH_REQUIRED && hasHuggingFaceToken)
}

private fun accessLabel(access: LocalModelDownloadAccess, hasHuggingFaceToken: Boolean): String {
    return when (access) {
        LocalModelDownloadAccess.PUBLIC -> "Download"
        LocalModelDownloadAccess.AUTH_REQUIRED -> if (hasHuggingFaceToken) "HF token ready" else "HF token"
        LocalModelDownloadAccess.IMPORT_ONLY -> "Manual import"
    }
}

private fun compactSizeLine(entry: LocalModelCatalogEntry, bytesDownloaded: Long): String {
    val totalBytes = if (entry.estimatedDownloadBytes > 0L) entry.estimatedDownloadBytes else entry.estimatedInstalledBytes
    return buildList {
        add("Size ${if (totalBytes > 0L) formatBytes(totalBytes) else "unknown"}")
        add("RAM ${formatBytes(entry.recommendedRamBytes)}")
        if (bytesDownloaded > 0L) add(formatBytes(bytesDownloaded))
    }.joinToString(" · ")
}

private fun compactProgressDetailLine(install: me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity?): String {
    if (install == null) return ""
    return buildList {
        add("${install.progressPercent}%")
        if (install.bytesPerSecond > 0L) add("${formatBytes(install.bytesPerSecond)}/s")
        if (install.etaSeconds > 0L) add("${install.etaSeconds}s left")
        if (install.currentFile.isNotBlank()) add(install.currentFile)
    }.joinToString(" · ")
}

@Composable
private fun LocalModelEditSheet(
    state: LocalModelCatalogState,
    provider: ProviderSetting.Local,
    onDismiss: () -> Unit,
    onSave: suspend (Model) -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var model by remember(state.entry.id) { mutableStateOf(state.entry.toModel()) }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Edit model",
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClickableIconPicker(
                    currentIconUri = model.customIconUri,
                    defaultContent = {
                        ModelIcon(
                            model = model,
                            provider = provider,
                            modifier = Modifier.size(40.dp),
                        )
                    },
                    onIconSelected = { uri -> model = model.copy(customIconUri = uri.toString()) },
                    onIconCleared = { model = model.copy(customIconUri = null) },
                    iconSize = 48.dp,
                )
                OutlinedTextField(
                    value = model.displayName,
                    onValueChange = { model = model.copy(displayName = it) },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        scope.launch { onSave(model) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

private fun statusLabel(status: LocalModelStatus): String {
    return status.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}

private fun statusTagType(status: LocalModelStatus): TagType {
    return when (status) {
        LocalModelStatus.READY -> TagType.SUCCESS
        LocalModelStatus.FAILED, LocalModelStatus.INCOMPATIBLE -> TagType.ERROR
        LocalModelStatus.QUEUED, LocalModelStatus.DOWNLOADING, LocalModelStatus.VERIFYING, LocalModelStatus.INSTALLING -> TagType.WARNING
        LocalModelStatus.CANCELED -> TagType.INFO
        LocalModelStatus.NOT_DOWNLOADED -> TagType.DEFAULT
    }
}

private fun sizeLine(entry: LocalModelCatalogEntry, bytesDownloaded: Long): String {
    val totalBytes = if (entry.estimatedDownloadBytes > 0L) entry.estimatedDownloadBytes else entry.estimatedInstalledBytes
    return buildString {
        append("Size ")
        append(if (totalBytes > 0L) formatBytes(totalBytes) else "Unknown")
        append(" • RAM: ")
        append(formatBytes(entry.recommendedRamBytes))
        if (bytesDownloaded > 0L) {
            append(" • Progress: ")
            append(formatBytes(bytesDownloaded))
        }
    }
}

private fun progressDetailLine(install: me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity?): String {
    if (install == null) return ""
    return buildString {
        append("${install.progressPercent}%")
        if (install.bytesPerSecond > 0L) append(" • ${formatBytes(install.bytesPerSecond)}/s")
        if (install.etaSeconds > 0L) append(" • ${install.etaSeconds}s left")
        if (install.currentFile.isNotBlank()) append(" • ${install.currentFile}")
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", value, units[unitIndex])
}

private val activeStatuses = setOf(
    LocalModelStatus.QUEUED,
    LocalModelStatus.DOWNLOADING,
    LocalModelStatus.VERIFYING,
    LocalModelStatus.INSTALLING,
)

private fun itemPosition(index: Int, size: Int): ItemPosition {
    return when {
        size <= 1 -> ItemPosition.ONLY
        index == 0 -> ItemPosition.FIRST
        index == size - 1 -> ItemPosition.LAST
        else -> ItemPosition.MIDDLE
    }
}
