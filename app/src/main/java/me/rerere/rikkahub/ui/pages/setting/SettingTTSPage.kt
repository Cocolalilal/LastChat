package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.ttsProviderIconUri
import me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID
import me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_VOICE_ID
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderConfigure
import me.rerere.rikkahub.utils.plus
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.withDefaultVoices
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderIcon
import me.rerere.rikkahub.ui.pages.setting.components.toTTSProviderPresets
import me.rerere.rikkahub.ui.pages.setting.components.FALLBACK_TTS_PROVIDER_PRESETS
import me.rerere.rikkahub.ui.pages.setting.components.toTTSProviderSetting

@Composable
fun SettingTTSPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalogSnapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val context = LocalContext.current
    var editingProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    
    // Move lazyListState outside for canScroll detection
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.ttsProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vm.updateSettings(settings.copy(ttsProviders = newProviders))
    }

    // State for TTS text filter settings dialog
    var showFilterSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_tts_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        bottomBar = {
            ProvidersBottomBar(selectedTab = ProvidersTab.Tts) {
                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showFilterSettingsDialog = true
                    },
                    shape = AppShapes.CardLarge,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.setting_tts_settings_title))
                }

                AddTTSProviderButton(
                    catalogSnapshot = catalogSnapshot,
                    enableHaptics = settings.displaySetting.enableUIHaptics,
                    asFab = true
                ) {
                    vm.updateSettings(
                        settings.copy(
                            ttsProviders = listOf(it.withDefaultVoices()) + settings.ttsProviders
                        )
                    )
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        // State for swipe neighbor tracking
        var draggingIndex by remember { mutableStateOf(-1) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var isUnlocked by remember { mutableStateOf(false) }
        var neighborsUnlocked by remember { mutableStateOf(false) }
        
        
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        // Reset neighborsUnlocked when offset returns to 0
        if (dragOffset == 0f && neighborsUnlocked) {
            neighborsUnlocked = false
        }
        

        
        // Delete confirmation state
        var showDeleteDialog by remember { mutableStateOf(false) }
        var providerToDelete by remember { mutableStateOf<TTSProviderSetting?>(null) }
        
        // TTS error state - show as toast notification
        val tts = LocalTTSState.current
        val ttsError by tts.error.collectAsState()
        val toaster = LocalToaster.current
        
        // Show error toast when TTS error changes
        LaunchedEffect(ttsError) {
            ttsError?.let { errorMessage ->
                toaster.show(
                    message = context.getString(R.string.setting_tts_error, errorMessage),
                    type = ToastType.Error
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = innerPadding + PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                state = lazyListState
            ) {
                itemsIndexed(settings.ttsProviders, key = { _, provider -> provider.id }) { index, provider ->
                val position = when {
                    settings.ttsProviders.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == settings.ttsProviders.lastIndex -> ItemPosition.LAST
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
                

                ReorderableItem(
                    state = reorderableState,
                    key = provider.id,
                    animateItemModifier = Modifier.animateItem()
                ) { isDragging ->
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "tts_provider_drag_scale"
                    )
                    key(provider.id) {
                        PhysicsSwipeToDelete(
                            position = position,
                            groupCornerRadius = 24.dp,
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
                                providerToDelete = provider
                                showDeleteDialog = true
                            },
                            modifier = Modifier
                                .scale(dragScale)
                                .fillMaxWidth()
                        ) { _ ->
                        TTSProviderItemContent(
                            provider = provider,
                            catalogSnapshot = catalogSnapshot,
                            haptics = haptics,
                            onClick = {
                                navController.navigate(Screen.SettingTTSProviderDetail(provider.id.toString()))
                            },
                            dragHandle = {
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
                        )
                    }
                    }
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
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog && providerToDelete != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    providerToDelete = null
                },
                title = { Text(stringResource(R.string.confirm_delete)) },
                text = { Text(stringResource(R.string.setting_tts_delete_service)) },
                dismissButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        providerToDelete = null
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        providerToDelete?.let { p ->
                            val removedVoiceIds = p.voices.map { it.id }.toSet()
                            val newProviders = settings.ttsProviders - p
                            val newSelectedId =
                                if (settings.selectedTTSProviderId == p.id) {
                                    newProviders.firstOrNull()?.id ?: DEFAULT_SYSTEM_TTS_ID
                                } else {
                                    settings.selectedTTSProviderId
                                }
                            val newSelectedVoiceId = if (settings.selectedTTSVoiceId in removedVoiceIds) {
                                newProviders.find { it.id == newSelectedId }?.voices?.firstOrNull()?.id
                                    ?: newProviders.firstOrNull()?.voices?.firstOrNull()?.id
                                    ?: DEFAULT_SYSTEM_TTS_VOICE_ID
                            } else {
                                settings.selectedTTSVoiceId
                            }
                            vm.updateSettings(settings.copy(
                                ttsProviders = newProviders,
                                selectedTTSProviderId = newSelectedId,
                                selectedTTSVoiceId = newSelectedVoiceId
                            ))
                        }
                        showDeleteDialog = false
                        providerToDelete = null
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        }
    }

    // Edit TTS Provider Bottom Sheet
    editingProvider?.let { provider ->
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var currentProvider by remember(provider) { mutableStateOf(provider) }
        val tts = LocalTTSState.current
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = {
                editingProvider = null
            },
            sheetState = bottomSheetState,
            sheetGesturesEnabled = false,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            bottomSheetState.hide()
                            editingProvider = null
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
                    .padding(16.dp)
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.setting_tts_page_edit_provider),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                tts.speak(
                                    text = context.getString(R.string.setting_tts_test_voice_preview),
                                    overrideSetting = currentProvider
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = stringResource(R.string.setting_tts_test_voice)
                        )
                    }
                }


                TTSProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            editingProvider = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    TextButton(
                        onClick = {
                            val newProviders = settings.ttsProviders.map {
                                if (it.id == provider.id) currentProvider else it
                            }
                            vm.updateSettings(settings.copy(ttsProviders = newProviders))
                            editingProvider = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.chat_page_save))
                    }
                }
            }
        }
    }
    
    // TTS Text Filter Settings Dialog
    if (showFilterSettingsDialog) {
        TtsTextFilterSettingsDialog(
            rules = settings.displaySetting.ttsTextFilterRules,
            onDismiss = { showFilterSettingsDialog = false },
            onUpdateRules = { newRules ->
                vm.updateSettings(
                    settings.copy(
                        displaySetting = settings.displaySetting.copy(
                            ttsTextFilterRules = newRules
                        )
                    )
                )
            }
        )
    }
}

@Composable
internal fun TtsProvidersContent(
    vm: SettingVM = koinViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalogSnapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val lazyListState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.ttsProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vm.updateSettings(settings.copy(ttsProviders = newProviders))
    }

    var editingProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<TTSProviderSetting?>(null) }

    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    val tts = LocalTTSState.current
    val ttsError by tts.error.collectAsState()
    val toaster = LocalToaster.current

    LaunchedEffect(ttsError) {
        ttsError?.let { errorMessage ->
            toaster.show(
                message = context.getString(R.string.setting_tts_error, errorMessage),
                type = ToastType.Error
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = contentPadding + PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            state = lazyListState
        ) {
            itemsIndexed(settings.ttsProviders, key = { _, provider -> provider.id }) { index, provider ->
                val position = when {
                    settings.ttsProviders.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == settings.ttsProviders.lastIndex -> ItemPosition.LAST
                    else -> ItemPosition.MIDDLE
                }

                val thresholdPx = with(density) { 35.dp.toPx() }
                if (draggingIndex >= 0 && !neighborsUnlocked && kotlin.math.abs(dragOffset) >= thresholdPx) {
                    neighborsUnlocked = true
                }

                val shouldNeighborFollow = draggingIndex >= 0 &&
                    draggingIndex != index &&
                    !isUnlocked &&
                    !neighborsUnlocked

                val neighborOffset = if (shouldNeighborFollow) {
                    when (kotlin.math.abs(index - draggingIndex)) {
                        1 -> dragOffset * 0.35f
                        2 -> dragOffset * 0.12f
                        else -> 0f
                    }
                } else {
                    0f
                }

                ReorderableItem(
                    state = reorderableState,
                    key = provider.id,
                    animateItemModifier = Modifier.animateItem()
                ) { isDragging ->
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "tts_provider_drag_scale"
                    )
                    key(provider.id) {
                        PhysicsSwipeToDelete(
                            position = position,
                            groupCornerRadius = 24.dp,
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
                                providerToDelete = provider
                                showDeleteDialog = true
                            },
                            modifier = Modifier
                                .scale(dragScale)
                                .fillMaxWidth()
                        ) { _ ->
                            TTSProviderItemContent(
                                provider = provider,
                                catalogSnapshot = catalogSnapshot,
                                haptics = haptics,
                                onClick = {
                                    navController.navigate(Screen.SettingTTSProviderDetail(provider.id.toString()))
                                },
                                dragHandle = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = { haptics.perform(HapticPattern.Pop) },
                                            onDragStopped = { haptics.perform(HapticPattern.Thud) }
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                        }
                    }
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
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
    }

    if (showDeleteDialog && providerToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                providerToDelete = null
            },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.setting_tts_delete_service)) },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    providerToDelete = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    providerToDelete?.let { provider ->
                        val newProviders = settings.ttsProviders - provider
                        val newSelectedId =
                            if (settings.selectedTTSProviderId == provider.id) {
                                newProviders.firstOrNull()?.id ?: DEFAULT_SYSTEM_TTS_ID
                            } else {
                                settings.selectedTTSProviderId
                            }
                        val removedVoiceIds = provider.voices.map { it.id }.toSet()
                        val newSelectedVoiceId = if (settings.selectedTTSVoiceId in removedVoiceIds) {
                            newProviders.find { it.id == newSelectedId }?.voices?.firstOrNull()?.id
                                ?: newProviders.firstOrNull()?.voices?.firstOrNull()?.id
                                ?: DEFAULT_SYSTEM_TTS_VOICE_ID
                        } else {
                            settings.selectedTTSVoiceId
                        }
                        vm.updateSettings(
                            settings.copy(
                                ttsProviders = newProviders,
                                selectedTTSProviderId = newSelectedId,
                                selectedTTSVoiceId = newSelectedVoiceId
                            )
                        )
                    }
                    showDeleteDialog = false
                    providerToDelete = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    TtsProviderEditorSheet(
        provider = editingProvider,
        onDismiss = { editingProvider = null },
        onSave = { original, updated ->
            val newProviders = settings.ttsProviders.map {
                if (it.id == original.id) updated else it
            }
            vm.updateSettings(settings.copy(ttsProviders = newProviders))
            editingProvider = null
        }
    )
}

@Composable
private fun TtsProviderEditorSheet(
    provider: TTSProviderSetting?,
    onDismiss: () -> Unit,
    onSave: (TTSProviderSetting, TTSProviderSetting) -> Unit
) {
    provider ?: return
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentProvider by remember(provider) { mutableStateOf(provider) }
    val tts = LocalTTSState.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        bottomSheetState.hide()
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
                .padding(16.dp)
                .fillMaxHeight(0.8f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.setting_tts_page_edit_provider),
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            tts.speak(
                                text = context.getString(R.string.setting_tts_test_voice_preview),
                                overrideSetting = currentProvider
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.setting_tts_test_voice)
                    )
                }
            }

            TTSProviderConfigure(
                setting = currentProvider,
                onValueChange = { currentProvider = it },
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                TextButton(
                    onClick = { onSave(provider, currentProvider) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            }
        }
    }
}

@Composable
internal fun TtsTextFilterSettingsDialog(
    rules: List<me.rerere.rikkahub.data.datastore.TtsTextFilterRule>,
    onDismiss: () -> Unit,
    onUpdateRules: (List<me.rerere.rikkahub.data.datastore.TtsTextFilterRule>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<me.rerere.rikkahub.data.datastore.TtsTextFilterRule?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.setting_tts_filter_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.setting_tts_filter_add_rule))
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
                ) {
                    Text(
                        text = stringResource(R.string.setting_tts_filter_desc),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (rules.isEmpty()) {
                        item {
                            androidx.compose.material3.Card(
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.setting_tts_filter_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    IconButton(onClick = { showAddDialog = true }) {
                                        Icon(
                                            Icons.Rounded.Add,
                                            contentDescription = stringResource(R.string.setting_tts_filter_add_rule)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(rules, key = { it.id }) { rule ->
                            TtsFilterRuleItem(
                                rule = rule,
                                onToggle = { enabled ->
                                    onUpdateRules(rules.map {
                                        if (it.id == rule.id) it.copy(enabled = enabled) else it
                                    })
                                },
                                onEdit = { editingRule = rule },
                                onDelete = {
                                    onUpdateRules(rules.filter { it.id != rule.id })
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
    
    // Add/Edit Dialog
    if (showAddDialog || editingRule != null) {
        TtsFilterRuleEditDialog(
            rule = editingRule,
            onDismiss = {
                showAddDialog = false
                editingRule = null
            },
            onSave = { newRule ->
                val ruleToEdit = editingRule
                if (ruleToEdit != null) {
                    onUpdateRules(rules.map {
                        if (it.id == ruleToEdit.id) newRule else it
                    })
                } else {
                    onUpdateRules(rules + newRule)
                }
                showAddDialog = false
                editingRule = null
            }
        )
    }
}

@Composable
private fun TtsFilterRuleItem(
    rule: me.rerere.rikkahub.data.datastore.TtsTextFilterRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val modeText = when (rule.mode) {
        me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP -> stringResource(R.string.setting_tts_filter_skip)
        me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ -> stringResource(R.string.setting_tts_filter_only_read)
    }
    val modeColor = when (rule.mode) {
        me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP -> MaterialTheme.colorScheme.error
        me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ -> MaterialTheme.colorScheme.primary
    }
    
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        onClick = onEdit
    ) {
        androidx.compose.material3.ListItem(
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.setting_rp_custom_preview, rule.pattern))
                }
            },
            supportingContent = {
                Text(
                    text = modeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = modeColor
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            stringResource(R.string.setting_tts_filter_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    HapticSwitch(
                        checked = rule.enabled,
                        onCheckedChange = onToggle
                    )
                }
            }
        )
    }
}

@Composable
private fun TtsFilterRuleEditDialog(
    rule: me.rerere.rikkahub.data.datastore.TtsTextFilterRule?,
    onDismiss: () -> Unit,
    onSave: (me.rerere.rikkahub.data.datastore.TtsTextFilterRule) -> Unit
) {
    var pattern by remember { mutableStateOf(rule?.pattern ?: "*") }
    var mode by remember { mutableStateOf(rule?.mode ?: me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (rule != null) {
                    stringResource(R.string.setting_tts_filter_edit_rule)
                } else {
                    stringResource(R.string.setting_tts_filter_add_rule)
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.setting_tts_filter_pattern)) },
                    placeholder = { Text(stringResource(R.string.setting_tts_filter_pattern_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(stringResource(R.string.setting_tts_filter_pattern_desc, pattern))
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                
                // Mode selector
                Text(
                    text = stringResource(R.string.setting_tts_filter_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = mode == me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP,
                        onClick = { mode = me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP },
                        label = { Text(stringResource(R.string.setting_tts_filter_skip)) },
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = mode == me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ,
                        onClick = { mode = me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ },
                        label = { Text(stringResource(R.string.setting_tts_filter_only_read)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Mode description
                Text(
                    text = when (mode) {
                        me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP -> stringResource(
                            R.string.setting_tts_filter_skip_desc,
                            pattern
                        )
                        me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ -> stringResource(
                            R.string.setting_tts_filter_only_read_desc,
                            pattern
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onSave(
                            me.rerere.rikkahub.data.datastore.TtsTextFilterRule(
                                id = rule?.id ?: kotlin.uuid.Uuid.random().toString(),
                                pattern = pattern,
                                mode = mode,
                                enabled = rule?.enabled ?: true
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
@Composable
internal fun AddTTSProviderButton(
    catalogSnapshot: ModelCatalogSnapshot?,
    enableHaptics: Boolean = true,
    asFab: Boolean = false,
    onAdd: (TTSProviderSetting) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val haptics = rememberPremiumHaptics(enabled = enableHaptics)

    val openTtsProviderSheet = {
        haptics.perform(HapticPattern.Pop)
        searchQuery = ""
        showBottomSheet = true
    }

    if (asFab) {
        FloatingActionButton(
            onClick = openTtsProviderSheet,
            shape = AppShapes.CardLarge
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.setting_tts_page_add_provider_content_description))
        }
    } else {
        IconButton(onClick = openTtsProviderSheet) {
            Icon(Icons.Rounded.Add, stringResource(R.string.setting_tts_page_add_provider_content_description))
        }
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        
        val allTtsPresets = remember(catalogSnapshot) {
            catalogSnapshot?.toTTSProviderPresets() ?: FALLBACK_TTS_PROVIDER_PRESETS
        }        
        // Filter presets based on search
        val filteredPresets = if (searchQuery.isBlank()) {
            allTtsPresets
        } else {
            allTtsPresets.filter { preset ->
                preset.name.contains(searchQuery, ignoreCase = true) ||
                    preset.description.contains(searchQuery, ignoreCase = true)
            }
        }
        
        val scope = rememberCoroutineScope()
        
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = {
                showBottomSheet = false
            },
            sheetState = bottomSheetState,
            sheetGesturesEnabled = false,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            bottomSheetState.hide()
                            showBottomSheet = false
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
                    .padding(horizontal = 16.dp)
                    .fillMaxHeight(0.85f)
                    .clipToBounds()
            ) {
                // Title
                Text(
                    text = stringResource(R.string.setting_tts_page_add_provider),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.setting_provider_page_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.SearchField,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    } else null
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onAdd(TTSProviderSetting.OpenAI(name = "Custom TTS"))
                        showBottomSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Column {
                            Text(text = "Custom OpenAI-compatible TTS", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Add any TTS that supports the OpenAI /audio/speech protocol.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                CompositionLocalProvider(
                    LocalOverscrollFactory provides null
                ) {
                    val lazyListState = rememberLazyListState()
                    val nestedScrollConnection = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0) {
                                    return Offset.Zero
                                }
                                return Offset.Zero
                            }
                        }
                    }
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .clipToBounds()
                            .nestedScroll(nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(filteredPresets, key = { _, preset -> preset.name }) { index, preset ->
                            val position = when {
                                filteredPresets.size == 1 -> ItemPosition.ONLY
                                index == 0 -> ItemPosition.FIRST
                                index == filteredPresets.lastIndex -> ItemPosition.LAST
                                else -> ItemPosition.MIDDLE
                            }

                            val shape = when (position) {
                                ItemPosition.FIRST -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
                                ItemPosition.LAST -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                ItemPosition.MIDDLE -> RoundedCornerShape(10.dp)
                                ItemPosition.ONLY -> RoundedCornerShape(24.dp)
                            }

                            Surface(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    val newProvider = preset.toTTSProviderSetting()
                                    onAdd(newProvider)
                                    showBottomSheet = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = shape,
                                color = if (LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (preset.isLocal) {
                                        Box(
                                            modifier = Modifier.size(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhoneAndroid,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else {
                                        AutoAIIconWithUrl(
                                            name = preset.name,
                                            customIconUri = preset.customIconUri,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = preset.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (preset.isLocal) {
                                            Tag(type = TagType.SUCCESS) {
                                                Text(stringResource(R.string.local_label))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun TTSProviderItemContent(
    provider: TTSProviderSetting,
    catalogSnapshot: ModelCatalogSnapshot?,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "ttsProviderBackground"
    )
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "ttsProviderTextColor"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(backgroundColor)
            .clickable {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TTSProviderIcon(
            provider = provider,
            catalogSnapshot = catalogSnapshot,
            modifier = Modifier.size(40.dp),
            tint = textColor
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) },
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        dragHandle()
    }
}

