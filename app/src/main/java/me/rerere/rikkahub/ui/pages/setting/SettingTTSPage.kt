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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoProviderIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderConfigure
import me.rerere.rikkahub.utils.plus
import me.rerere.tts.provider.TTSProviderSetting
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.components.ui.ToastType

@Composable
fun SettingTTSPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    var editingProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
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
                },
                actions = {
                    IconButton(onClick = { showFilterSettingsDialog = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "TTS Settings")
                    }
                    AddTTSProviderButton {
                        vm.updateSettings(
                            settings.copy(
                                ttsProviders = listOf(it) + settings.ttsProviders
                            )
                        )
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        
        val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
        
        // State for swipe neighbor tracking
        var draggingIndex by remember { mutableStateOf(-1) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var isUnlocked by remember { mutableStateOf(false) }
        var neighborsUnlocked by remember { mutableStateOf(false) }
        
        
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        // Check if delete is allowed (more than 1 provider)
        val canDelete = settings.ttsProviders.size > 1
        
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
                    message = "TTS Error: $errorMessage",
                    type = ToastType.Error
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            state = lazyListState
        ) {
            itemsIndexed(settings.ttsProviders, key = { _, provider -> provider.id }) { index, provider ->
                val isSelected = settings.selectedTTSProviderId == provider.id
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
                    animateItemModifier = Modifier
                ) { isDragging ->
                    // Key on isSelected to force complete PhysicsSwipeToDelete recreation when selection changes
                    key(isSelected) {
                        PhysicsSwipeToDelete(
                            position = if (isSelected) ItemPosition.ONLY else position,
                            groupCornerRadius = if (isSelected) 100.dp else 24.dp, // Pill shape for selected
                            deleteEnabled = canDelete && !isSelected,
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
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                        ) { _ ->
                        TTSProviderItemContent(
                            provider = provider,
                            isSelected = isSelected,
                            haptics = haptics,
                            onSelect = {
                                if (!isSelected) {
                                    haptics.perform(HapticPattern.Pop)
                                    vm.updateSettings(settings.copy(selectedTTSProviderId = provider.id))
                                }
                            },
                            onEdit = {
                                editingProvider = provider
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
                    } // key(isSelected)
                }
            }
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog && providerToDelete != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    providerToDelete = null
                },
                title = { Text(stringResource(R.string.confirm_delete)) },
                text = { Text("Are you sure you want to delete this TTS service?") },
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
                            val newProviders = settings.ttsProviders - p
                            val newSelectedId =
                                if (settings.selectedTTSProviderId == p.id) DEFAULT_SYSTEM_TTS_ID else settings.selectedTTSProviderId
                            vm.updateSettings(settings.copy(
                                ttsProviders = newProviders,
                                selectedTTSProviderId = newSelectedId
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
                                    text = "Hello user, this is what your characters will sound like if you use this setup!",
                                    overrideSetting = currentProvider
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Test TTS"
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
private fun TtsTextFilterSettingsDialog(
    rules: List<me.rerere.rikkahub.data.datastore.TtsTextFilterRule>,
    onDismiss: () -> Unit,
    onUpdateRules: (List<me.rerere.rikkahub.data.datastore.TtsTextFilterRule>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<me.rerere.rikkahub.data.datastore.TtsTextFilterRule?>(null) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Text Filter Rules",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Rule")
                }
            }
            
            // Description
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Configure text patterns for TTS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "Skip: Text matching the pattern will be skipped.\nOnly Read: Only text matching the pattern will be read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Rules list
            if (rules.isEmpty()) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No rules yet. Tap + to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rules.forEach { rule ->
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
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Add/Edit Dialog
    if (showAddDialog || editingRule != null) {
        TtsFilterRuleEditDialog(
            rule = editingRule,
            onDismiss = {
                showAddDialog = false
                editingRule = null
            },
            onSave = { newRule ->
                if (editingRule != null) {
                    onUpdateRules(rules.map {
                        if (it.id == editingRule!!.id) newRule else it
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
        me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP -> "Skip"
        me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ -> "Only Read"
    }
    val modeColor = when (rule.mode) {
        me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP -> MaterialTheme.colorScheme.error
        me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ -> MaterialTheme.colorScheme.primary
    }
    
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
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
                    Text("${rule.pattern}text${rule.pattern}")
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
                            "Delete",
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
            Text(if (rule != null) "Edit Rule" else "Add Rule")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    placeholder = { Text("e.g., * or %") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Text wrapped like ${pattern}text${pattern} will be filtered")
                    }
                )
                
                // Mode selector
                Text(
                    text = "Mode",
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
                        label = { Text("Skip") },
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = mode == me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ,
                        onClick = { mode = me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ },
                        label = { Text("Only Read") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Mode description
                Text(
                    text = when (mode) {
                        me.rerere.rikkahub.data.datastore.TtsFilterMode.SKIP -> "Text inside ${pattern}...${pattern} will be skipped"
                        me.rerere.rikkahub.data.datastore.TtsFilterMode.ONLY_READ -> "Only text inside ${pattern}...${pattern} will be read"
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
@Composable
private fun AddTTSProviderButton(onAdd: (TTSProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val haptics = rememberPremiumHaptics()

    IconButton(
        onClick = {
            searchQuery = ""
            showBottomSheet = true
        }
    ) {
        Icon(Icons.Rounded.Add, stringResource(R.string.setting_tts_page_add_provider_content_description))
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        
        // TTS Provider presets
        data class TTSPreset(
            val type: kotlin.reflect.KClass<out TTSProviderSetting>,
            val name: String,
            val description: String,
            val isLocal: Boolean = false
        )
        
        val allTtsPresets = remember {
            listOf(
                TTSPreset(TTSProviderSetting.SystemTTS::class, "System TTS", "Uses device's built-in TTS engine", isLocal = true),
                TTSPreset(TTSProviderSetting.OpenAI::class, "OpenAI", "High-quality voices with emotion"),
                TTSPreset(TTSProviderSetting.Gemini::class, "Gemini", "Google's TTS with natural voices"),
                TTSPreset(TTSProviderSetting.ElevenLabs::class, "ElevenLabs", "Professional voice cloning"),
                TTSPreset(TTSProviderSetting.MiniMax::class, "MiniMax", "Chinese TTS with emotions"),
            )
        }
        
        // Filter presets based on search
        val filteredPresets = remember(searchQuery) {
            if (searchQuery.isBlank()) {
                allTtsPresets
            } else {
                allTtsPresets.filter { preset ->
                    preset.name.contains(searchQuery, ignoreCase = true) ||
                    preset.description.contains(searchQuery, ignoreCase = true)
                }
            }
        }
        
        val scope = rememberCoroutineScope()
        
        ModalBottomSheet(
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
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    } else null
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
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
                        
                        androidx.compose.material3.Surface(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                val newProvider = when (preset.type) {
                                    TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS()
                                    TTSProviderSetting.OpenAI::class -> TTSProviderSetting.OpenAI()
                                    TTSProviderSetting.Gemini::class -> TTSProviderSetting.Gemini()
                                    TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs()
                                    TTSProviderSetting.MiniMax::class -> TTSProviderSetting.MiniMax()
                                    else -> TTSProviderSetting.SystemTTS()
                                }
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
                                // Icon
                                when (preset.type) {
                                    TTSProviderSetting.SystemTTS::class -> {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = me.rerere.rikkahub.ui.hooks.rememberAvatarShape(false)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhoneAndroid,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        val providerName = when (preset.type) {
                                            TTSProviderSetting.OpenAI::class -> "OpenAI"
                                            TTSProviderSetting.Gemini::class -> "Google"
                                            TTSProviderSetting.ElevenLabs::class -> "ElevenLabs"
                                            TTSProviderSetting.MiniMax::class -> "MiniMax"
                                            else -> "Unknown"
                                        }
                                        AutoProviderIcon(
                                            name = providerName,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
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
                                
                                // Tags on right side
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (preset.isLocal) {
                                        Tag(type = TagType.SUCCESS) {
                                            Text("Local")
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


@Composable
private fun TTSProviderItemContent(
    provider: TTSProviderSetting,
    isSelected: Boolean,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()
    val isAvailable by tts.isAvailable.collectAsState()
    
    // Animated color transition for selection
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "selectionBackground"
    )
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "textColor"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(backgroundColor)
            .clickable {
                haptics.perform(HapticPattern.Pop)
                onSelect()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon: System TTS shows a phone icon, others use provider icon lookup
        when (provider) {
            is TTSProviderSetting.SystemTTS -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = me.rerere.rikkahub.ui.hooks.rememberAvatarShape(false)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            else -> {
                // Use provider type name for icon lookup (OpenAI, Gemini, MiniMax, ElevenLabs)
                val providerTypeName = when (provider) {
                    is TTSProviderSetting.OpenAI -> "OpenAI"
                    is TTSProviderSetting.Gemini -> "Google"
                    is TTSProviderSetting.MiniMax -> "MiniMax"
                    is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
                    is TTSProviderSetting.SystemTTS -> "System"
                }
                AutoProviderIcon(
                    name = providerTypeName,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = if (provider is TTSProviderSetting.SystemTTS) Arrangement.spacedBy(8.dp) else Arrangement.Center,
        ) {
            Text(
                text = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) },
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Tags row - only show for SystemTTS
            if (provider is TTSProviderSetting.SystemTTS) {
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
                        Tag(type = TagType.SUCCESS) {
                            Text("Local")
                        }
                    }
                    // Fade gradient overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(width = 40.dp, height = 24.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        backgroundColor
                                    )
                                )
                            )
                    )
                }
            }
        }
        
        // Settings button first
        IconButton(
            onClick = {
                haptics.perform(HapticPattern.Pop)
                onEdit()
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.setting_tts_page_more_options_content_description)
            )
        }
        
        // Drag handle at the end
        dragHandle()
    }
}

