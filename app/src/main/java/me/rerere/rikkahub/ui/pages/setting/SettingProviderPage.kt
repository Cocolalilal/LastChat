package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.ProviderViewMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoProviderIcon
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.decodeProviderSetting
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.ImageUtils
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import me.rerere.rikkahub.data.model.Tag as DataTag

@Composable
fun SettingProviderPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    
    // Search query state
    var searchQuery by remember { mutableStateOf("") }
    
    // View mode comes from settings for persistence
    val viewMode = settings.displaySetting.providerViewMode
    
    // Tag filter state
    var selectedTagIds by remember { mutableStateOf(emptySet<kotlin.uuid.Uuid>()) }
    
    // Filter providers based on search and tags
    val filteredProviders = remember(settings.providers, searchQuery, selectedTagIds) {
        var result = settings.providers
        
        // Filter by search query
        if (searchQuery.isNotBlank()) {
            result = result.filter { provider ->
                provider.name.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Filter by tags
        if (selectedTagIds.isNotEmpty()) {
            result = result.filter { provider ->
                provider.tags.containsAll(selectedTagIds)
            }
        }
        
        result
    }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_provider_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    ImportProviderButton {
                        vm.updateSettings(
                            settings.copy(
                                providers = listOf(it) + settings.providers
                            )
                        )
                    }
                    AddButton {
                        vm.updateSettings(
                            settings.copy(
                                providers = listOf(it) + settings.providers
                            )
                        )
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Delete confirmation dialog state
            var showDeleteDialog by remember { mutableStateOf(false) }
            var providerToDelete by remember { mutableStateOf<ProviderSetting?>(null) }
            
            // Search bar at top with view mode toggle
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.setting_provider_page_search_placeholder)) },
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
            if (settings.providerTags.isNotEmpty()) {
                ProviderTagsFilterRow(
                    providerTags = settings.providerTags,
                    selectedTagIds = selectedTagIds,
                    onUpdateSelectedTagIds = { selectedTagIds = it }
                )
            }
            
            // Provider list/grid view with AnimatedContent
            // Provider list view
            ProviderListView(
                providers = filteredProviders,
                allProviders = settings.providers,
                settings = settings,
                haptics = haptics,
                onNavigateToDetail = { provider ->
                    navController.navigate(Screen.SettingProviderDetail(providerId = provider.id.toString()))
                },
                onDeleteRequest = { provider ->
                    providerToDelete = provider
                    showDeleteDialog = true
                },
                onReorder = { from, to ->
                    val newProviders = settings.providers.toMutableList().apply {
                        add(to, removeAt(from))
                    }
                    vm.updateSettings(settings.copy(providers = newProviders))
                }
            )
            
            // Delete confirmation dialog
            if (showDeleteDialog && providerToDelete != null) {
                AlertDialog(
                    onDismissRequest = { 
                        showDeleteDialog = false
                        providerToDelete = null
                    },
                    title = {
                        Text(stringResource(R.string.confirm_delete))
                    },
                    text = {
                        Text(stringResource(R.string.setting_provider_page_delete_dialog_text))
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showDeleteDialog = false
                            providerToDelete = null
                        }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                providerToDelete?.let { p ->
                                    vm.updateSettings(
                                        settings.copy(
                                            providers = settings.providers.filter { it.id != p.id }
                                        )
                                    )
                                }
                                showDeleteDialog = false
                                providerToDelete = null
                            }
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchBarWithToggle(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    viewMode: ProviderViewMode,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.setting_provider_page_search_placeholder)) },
            modifier = Modifier.weight(1f),
            shape = AppShapes.SearchField,
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            } else null
        )
        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = if (viewMode == ProviderViewMode.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                contentDescription = "Toggle view mode"
            )
        }
    }
}

@Composable
private fun ProviderListView(
    providers: List<ProviderSetting>,
    allProviders: List<ProviderSetting>,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onNavigateToDetail: (ProviderSetting) -> Unit,
    onDeleteRequest: (ProviderSetting) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }
    
    // State for swipe neighbor tracking
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    
    // State for reorder ripple effect
    var reorderDropProviderId by remember { mutableStateOf<kotlin.uuid.Uuid?>(null) }
    var reorderDropTrigger by remember { mutableStateOf(0) }
    
    val canDelete = allProviders.size > 1
    val ripplePushDp = 10.dp
    val ripplePushPx = with(density) { ripplePushDp.toPx() }
    
    // Reset neighborsUnlocked when offset returns to 0
    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        state = lazyListState,
    ) {
        itemsIndexed(providers, key = { _, it -> it.id }) { index, provider ->
                val position = when {
                    providers.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == providers.lastIndex -> ItemPosition.LAST
                    else -> ItemPosition.MIDDLE
                }
                
                // Calculate neighbor offset
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
                
                // Ripple animation for this item
                val rippleOffset = remember { androidx.compose.animation.core.Animatable(0f) }
                androidx.compose.runtime.LaunchedEffect(reorderDropTrigger) {
                    if (reorderDropTrigger > 0 && reorderDropProviderId != null && reorderDropProviderId != provider.id) {
                        val dropIndex = providers.indexOfFirst { it.id == reorderDropProviderId }
                        if (dropIndex >= 0) {
                            val distance = kotlin.math.abs(index - dropIndex)
                            if (distance <= 3) {
                                val pushAmount = when (distance) {
                                    1 -> ripplePushPx
                                    2 -> ripplePushPx * 0.6f
                                    3 -> ripplePushPx * 0.3f
                                    else -> 0f
                                }
                                val direction = if (index < dropIndex) -1f else 1f
                                rippleOffset.animateTo(
                                    targetValue = pushAmount * direction,
                                    animationSpec = androidx.compose.animation.core.tween(80)
                                )
                                rippleOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
                                )
                            }
                        }
                    }
                }
                
                ReorderableItem(
                    state = reorderableState,
                    key = provider.id
                ) { isDragging ->
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
                                onDeleteRequest(provider)
                            },
                            modifier = Modifier
                                .offset { androidx.compose.ui.unit.IntOffset(0, rippleOffset.value.toInt()) }
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                        ) {
                            ProviderItemContent(
                                provider = provider,
                                providerTags = settings.providerTags,
                                haptics = haptics,
                                dragHandle = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .longPressDraggableHandle(
                                                onDragStarted = {
                                                    haptics.perform(HapticPattern.Pop)
                                                },
                                                onDragStopped = {
                                                    haptics.perform(HapticPattern.Thud)
                                                    reorderDropProviderId = provider.id
                                                    reorderDropTrigger++
                                                }
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = null
                                        )
                                    }
                                },
                                onClick = {
                                    onNavigateToDetail(provider)
                                }
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun ImportProviderButton(
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }

    val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        handleQRResult(result, onAdd, toaster, context)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            handleImageQRCode(it, onAdd, toaster, context)
        }
    }

    IconButton(
        onClick = {
            showImportDialog = true
        }
    ) {
        Icon(Icons.AutoMirrored.Rounded.Input, null)
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.setting_provider_page_import_dialog_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_import_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                showImportDialog = false
                                scanQrCodeLauncher.launch(null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.setting_provider_page_scan_qr_code),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                showImportDialog = false
                                pickImageLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.setting_provider_page_select_from_gallery),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

private fun handleQRResult(
    result: QRResult,
    onAdd: (ProviderSetting) -> Unit,
    toaster: com.dokar.sonner.ToasterState,
    context: android.content.Context
) {
    runCatching {
        when (result) {
            is QRResult.QRError -> {
                toaster.show(
                    context.getString(
                        R.string.setting_provider_page_scan_error,
                        result
                    ), type = ToastType.Error
                )
            }

            QRResult.QRMissingPermission -> {
                toaster.show(
                    context.getString(R.string.setting_provider_page_no_permission),
                    type = ToastType.Error
                )
            }

            is QRResult.QRSuccess -> {
                val setting = decodeProviderSetting(result.content.rawValue ?: "")
                onAdd(setting)
                toaster.show(
                    context.getString(R.string.setting_provider_page_import_success),
                    type = ToastType.Success
                )
            }

            QRResult.QRUserCanceled -> {}
        }
    }.onFailure { error ->
        toaster.show(
            context.getString(R.string.setting_provider_page_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}

private fun handleImageQRCode(
    uri: Uri,
    onAdd: (ProviderSetting) -> Unit,
    toaster: com.dokar.sonner.ToasterState,
    context: android.content.Context
) {
    runCatching {
        val qrContent = ImageUtils.decodeQRCodeFromUri(context, uri)

        if (qrContent.isNullOrEmpty()) {
            toaster.show(
                context.getString(R.string.setting_provider_page_no_qr_found),
                type = ToastType.Error
            )
            return
        }

        val setting = decodeProviderSetting(qrContent)
        onAdd(setting)
        toaster.show(
            context.getString(R.string.setting_provider_page_import_success),
            type = ToastType.Success
        )
    }.onFailure { error ->
        toaster.show(
            context.getString(R.string.setting_provider_page_image_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}


@Composable
private fun AddButton(onAdd: (ProviderSetting) -> Unit) {
    val dialogState = useEditState<ProviderSetting> {
        onAdd(it)
    }

    IconButton(
        onClick = {
            dialogState.open(ProviderSetting.OpenAI())
        }
    ) {
        Icon(Icons.Rounded.Add, "Add")
    }

    if (dialogState.isEditing) {
        AlertDialog(
            onDismissRequest = {
                dialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.setting_provider_page_add_provider))
            },
            text = {
                dialogState.currentState?.let {
                    ProviderConfigure(it) { newState ->
                        dialogState.currentState = newState
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderItemContent(
    provider: ProviderSetting,
    providerTags: List<DataTag>,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    dragHandle: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (provider.enabled) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
            .clickable {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutoProviderIcon(
            name = provider.name,
            baseUrl = when (provider) {
                is ProviderSetting.OpenAI -> provider.baseUrl
                is ProviderSetting.Google -> provider.baseUrl
                is ProviderSetting.Claude -> provider.baseUrl
                is ProviderSetting.NanoGPT -> provider.baseUrl
            },
            modifier = Modifier.size(40.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                    // Show disabled tag only for disabled providers
                    if (!provider.enabled) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.setting_provider_page_disabled))
                        }
                    }
                    Tag(type = TagType.INFO) {
                        Text(
                            stringResource(
                                R.string.setting_provider_page_model_count,
                                provider.models.size
                            )
                        )
                    }
                    if (provider.name == "AiHubMix") {
                        Tag(type = TagType.INFO) {
                            Text("10% off")
                        }
                    }
                    // Show provider's assigned tags
                    provider.tags.forEach { tagId ->
                        providerTags.find { it.id == tagId }?.let { tag ->
                            Tag(type = TagType.DEFAULT) {
                                Text(tag.name)
                            }
                        }
                    }
                }
                // Fade gradient overlay to card background color (matches enabled/disabled state)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 40.dp, height = 24.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    if (provider.enabled) {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    }
                                )
                            )
                        )
                )
            }
        }
        dragHandle()
    }
}

@Composable
private fun ProviderTagsFilterRow(
    providerTags: List<DataTag>,
    selectedTagIds: Set<kotlin.uuid.Uuid>,
    onUpdateSelectedTagIds: (Set<kotlin.uuid.Uuid>) -> Unit
) {
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
        items(providerTags) { tag ->
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





