package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.data.ai.models.ttsProviderIconUri
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderConfigure
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderIcon
import me.rerere.tts.provider.TTSVoice
import me.rerere.tts.provider.android.discoverLocalTtsEngines
import me.rerere.tts.provider.android.discoverLocalTtsVoices
import me.rerere.tts.provider.withVoiceApplied
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
fun SettingTTSProviderDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalogSnapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    val provider = settings.ttsProviders.find { it.id == id } ?: return
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    fun updateProvider(updated: TTSProviderSetting) {
        vm.updateSettings(
            settings.copy(
                ttsProviders = settings.ttsProviders.map { if (it.id == updated.id) updated else it }
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TTSProviderIcon(provider = provider, catalogSnapshot = catalogSnapshot, modifier = Modifier.size(24.dp))
                        Text(provider.name.ifBlank { "TTS Provider" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        },
        bottomBar = {
            val haptics = rememberPremiumHaptics()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DetailTabButton(
                            selected = pager.currentPage == 0,
                            icon = { tint ->
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = "Configuration",
                                    tint = tint,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                scope.launch { pager.animateScrollToPage(0) }
                            },
                        )
                        DetailTabButton(
                            selected = pager.currentPage == 1,
                            icon = { tint ->
                                Icon(
                                    Icons.Rounded.ViewModule,
                                    contentDescription = "Voices",
                                    tint = tint,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                scope.launch { pager.animateScrollToPage(1) }
                            },
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
        ) { page ->
            when (page) {
                0 -> TtsProviderConfigTab(
                    provider = provider,
                    onUpdateProvider = ::updateProvider,
                    contentPadding = contentPadding,
                )
                1 -> TtsVoiceTab(
                    provider = provider,
                    onUpdateProvider = ::updateProvider,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun DetailTabButton(
    selected: Boolean,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        icon(
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun TtsProviderConfigTab(
    provider: TTSProviderSetting,
    onUpdateProvider: (TTSProviderSetting) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = contentPadding + PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
    ) {
        item {
            Card(
                shape = AppShapes.CardLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (LocalDarkMode.current) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
            ) {
                TTSProviderConfigure(
                    setting = provider,
                    modifier = Modifier.padding(16.dp),
                    showVoiceFields = false,
                    scrollable = false,
                    onValueChange = onUpdateProvider,
                )
            }
        }
    }
}

@Composable
private fun TtsVoiceTab(
    provider: TTSProviderSetting,
    onUpdateProvider: (TTSProviderSetting) -> Unit,
    contentPadding: PaddingValues,
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(provider.moveVoice(from.index, to.index))
    }
    var editingVoice by remember(provider.id) { mutableStateOf<TTSVoice?>(null) }
    var showManualVoiceSheet by remember(provider.id) { mutableStateOf(false) }
    var showProviderVoicesSheet by remember(provider.id) { mutableStateOf(false) }
    val tts = LocalTTSState.current
    val density = LocalDensity.current
    val haptics = rememberPremiumHaptics()
    var expanded by remember { mutableStateOf(true) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }

    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                ),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp) + PaddingValues(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (provider.voices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight(0.8f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "No voices yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add or fetch voices to use this provider.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }
            itemsIndexed(provider.voices, key = { _, voice -> voice.id }) { index, voice ->
                val position = when {
                    provider.voices.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == provider.voices.lastIndex -> ItemPosition.LAST
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

                ReorderableItem(reorderableState, key = voice.id) { isDragging ->
                    TtsVoiceRow(
                        provider = provider,
                        voice = voice,
                        position = position,
                        onEdit = { editingVoice = voice },
                        onDelete = { onUpdateProvider(provider.delVoice(voice)) },
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
                        onTest = {
                            tts.speak(
                                text = "Hello, this is what this voice sounds like.",
                                overrideSetting = provider.withVoiceApplied(voice),
                            )
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
                                ),
                            ) {
                                Icon(Icons.Rounded.DragIndicator, null)
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            if (isDragging) {
                                scaleX = 0.95f
                                scaleY = 0.95f
                            } else {
                                scaleX = 1f
                                scaleY = 1f
                            }
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
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .offset(y = -ScreenOffset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (provider !is TTSProviderSetting.SystemTTS) {
                ProviderVoicesFab(
                    provider = provider,
                    onAddVoice = { voice ->
                        onUpdateProvider(provider.addVoice(voice.copy(id = Uuid.random())))
                    },
                    onAddVoices = { voices ->
                        onUpdateProvider(
                            voices.fold(provider as TTSProviderSetting) { updated, voice ->
                                updated.addVoice(voice.copy(id = Uuid.random()))
                            }
                        )
                    },
                    onRemoveVoice = { voice ->
                        provider.voices.firstOrNull { selected -> voicesReferToSameProviderVoice(selected, voice) }?.let {
                            onUpdateProvider(provider.delVoice(it))
                        }
                    },
                    onRemoveVoices = { voices ->
                        val selectedVoices = voices.mapNotNull { voice ->
                            provider.voices.firstOrNull { selected -> voicesReferToSameProviderVoice(selected, voice) }
                        }
                        onUpdateProvider(selectedVoices.fold(provider as TTSProviderSetting) { updated, voice -> updated.delVoice(voice) })
                    },
                    onOpen = { showProviderVoicesSheet = true },
                    showSheet = showProviderVoicesSheet,
                    onDismiss = { showProviderVoicesSheet = false },
                )
                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showManualVoiceSheet = true
                    },
                    shape = AppShapes.CardLarge,
                ) {
                    Icon(Icons.Rounded.Add, null)
                }
            } else {
                ProviderVoicesFab(
                    provider = provider,
                    contentDescription = "Add system voice",
                    onAddVoice = { voice ->
                        onUpdateProvider(provider.addVoice(voice.copy(id = Uuid.random())))
                    },
                    onAddVoices = { voices ->
                        onUpdateProvider(
                            voices.fold(provider as TTSProviderSetting) { updated, voice ->
                                updated.addVoice(voice.copy(id = Uuid.random()))
                            }
                        )
                    },
                    onRemoveVoice = { voice ->
                        provider.voices.firstOrNull { selected -> voicesReferToSameProviderVoice(selected, voice) }?.let {
                            onUpdateProvider(provider.delVoice(it))
                        }
                    },
                    onRemoveVoices = { voices ->
                        val selectedVoices = voices.mapNotNull { voice ->
                            provider.voices.firstOrNull { selected -> voicesReferToSameProviderVoice(selected, voice) }
                        }
                        onUpdateProvider(selectedVoices.fold(provider as TTSProviderSetting) { updated, voice -> updated.delVoice(voice) })
                    },
                    onOpen = { showProviderVoicesSheet = true },
                    showSheet = showProviderVoicesSheet,
                    onDismiss = { showProviderVoicesSheet = false },
                )
            }
        }
    }

    VoiceEditorSheet(
        provider = provider,
        voice = editingVoice,
        onDismiss = { editingVoice = null },
        onSave = { updated ->
            onUpdateProvider(provider.editVoice(updated))
            editingVoice = null
        }
    )

    if (showManualVoiceSheet) {
        ManualVoiceSheet(
            provider = provider,
            onDismiss = { showManualVoiceSheet = false },
            onAddVoice = { voice ->
                onUpdateProvider(provider.addVoice(voice))
                showManualVoiceSheet = false
            }
        )
    }
}

@Composable
private fun TtsVoiceRow(
    provider: TTSProviderSetting,
    voice: TTSVoice,
    position: ItemPosition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    neighborOffset: Float,
    onDragProgress: (Float, Boolean) -> Unit,
    onDragEnd: () -> Unit,
    onTest: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    PhysicsSwipeToDelete(
        position = position,
        deleteEnabled = true,
        neighborOffset = neighborOffset,
        onDragProgress = onDragProgress,
        onDragEnd = onDragEnd,
        onDelete = onDelete,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .background(if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onEdit)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(voice.name.ifBlank { voice.providerVoiceId.ifBlank { "Voice" } }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val locale = voice.locale
                    if (!locale.isNullOrBlank()) Tag(type = TagType.INFO) { Text(locale) }
                    Tag { Text("x${"%.2f".format(voice.speed)}") }
                    Tag { Text("p${"%.2f".format(voice.pitch)}") }
                }
            }
            IconButton(onClick = onTest) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) }
            dragHandle()
        }
    }
}

@Composable
private fun VoiceEditorSheet(
    provider: TTSProviderSetting,
    voice: TTSVoice?,
    onDismiss: () -> Unit,
    onSave: (TTSVoice) -> Unit,
) {
    voice ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var current by remember(voice.id) { mutableStateOf(voice) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.8f)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Edit Voice", style = MaterialTheme.typography.headlineSmall) }
            item {
                OutlinedTextField(
                    value = current.name,
                    onValueChange = { current = current.copy(name = it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                )
            }
            item {
                OutlinedTextField(
                    value = current.providerVoiceId,
                    onValueChange = { current = current.copy(providerVoiceId = it) },
                    label = { Text("Provider voice id") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                )
            }
            item {
                OutlinedTextField(
                    value = current.locale.orEmpty(),
                    onValueChange = { current = current.copy(locale = it.takeIf(String::isNotBlank)) },
                    label = { Text("Locale") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                )
            }
            if (provider is TTSProviderSetting.SystemTTS) {
                item {
                    OutlinedTextField(
                        value = current.enginePackageName.orEmpty(),
                        onValueChange = { current = current.copy(enginePackageName = it.takeIf(String::isNotBlank)) },
                        label = { Text("Engine package") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.InputField,
                    )
                }
            }
            item {
                OutlinedNumberInput(
                    value = current.speed,
                    onValueChange = { if (it in 0.1f..4.0f) current = current.copy(speed = it) },
                    label = "Speed",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedNumberInput(
                    value = current.pitch,
                    onValueChange = { if (it in 0.1f..2.0f) current = current.copy(pitch = it) },
                    label = "Pitch",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = current.model.orEmpty(),
                    onValueChange = { current = current.copy(model = it.takeIf(String::isNotBlank)) },
                    label = { Text("Model override") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                )
            }
            if (provider is TTSProviderSetting.MiniMax) {
                item {
                    OutlinedTextField(
                        value = current.emotion.orEmpty(),
                        onValueChange = { current = current.copy(emotion = it.takeIf(String::isNotBlank)) },
                        label = { Text("Emotion") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.InputField,
                    )
                }
            }
            if (provider is TTSProviderSetting.Qwen) {
                item {
                    OutlinedTextField(
                        value = current.languageType.orEmpty(),
                        onValueChange = { current = current.copy(languageType = it.takeIf(String::isNotBlank)) },
                        label = { Text("Language type") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.InputField,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    TextButton(onClick = { onSave(current) }, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun ProviderVoicesFab(
    provider: TTSProviderSetting,
    icon: ImageVector = Icons.Rounded.Widgets,
    contentDescription: String = "Provider voices",
    onAddVoice: (TTSVoice) -> Unit,
    onAddVoices: (List<TTSVoice>) -> Unit,
    onRemoveVoice: (TTSVoice) -> Unit,
    onRemoveVoices: (List<TTSVoice>) -> Unit,
    onOpen: () -> Unit,
    showSheet: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val httpClient = koinInject<PlatformHttpClient>()
    val haptics = rememberPremiumHaptics()
    val tts = LocalTTSState.current
    var discovered by remember(provider.id) { mutableStateOf(providerPresetVoices(provider)) }
    var isFetching by remember { mutableStateOf(false) }
    var filterText by remember(provider.id) { mutableStateOf("") }
    val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
    val filteredVoices = discovered.fastFilter { voice ->
        if (filterKeywords.isEmpty()) {
            true
        } else {
            filterKeywords.all { keyword ->
                voice.name.contains(keyword, ignoreCase = true) ||
                    voice.providerVoiceId.contains(keyword, ignoreCase = true) ||
                    voice.locale.orEmpty().contains(keyword, ignoreCase = true) ||
                    voice.enginePackageName.orEmpty().contains(keyword, ignoreCase = true)
            }
        }
    }
    val allFilteredSelected = filteredVoices.isNotEmpty() && filteredVoices.all { voice ->
        provider.voices.any { selectedVoice -> voicesReferToSameProviderVoice(selectedVoice, voice) }
    }

    fun fetchVoices() {
        if (isFetching) return
        scope.launch {
            isFetching = true
            discovered = fetchProviderVoices(context, httpClient, provider).ifEmpty { discovered }
            isFetching = false
        }
    }

    FloatingActionButton(
        onClick = {
            haptics.perform(HapticPattern.Tick)
            onOpen()
            if (discovered.isEmpty()) fetchVoices()
        },
        shape = AppShapes.CardLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }

    if (!showSheet) return

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        if (allFilteredSelected) {
                            onRemoveVoices(filteredVoices)
                        } else {
                            val voicesToAdd = filteredVoices.filter { voice ->
                                provider.voices.none { selectedVoice -> voicesReferToSameProviderVoice(selectedVoice, voice) }
                            }
                            if (voicesToAdd.isNotEmpty()) {
                                onAddVoices(voicesToAdd)
                            }
                        }
                    },
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(if (allFilteredSelected) "Deselect all" else "Select all")
                }
                if (isFetching) {
                    LinearWavyProgressIndicator(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        fetchVoices()
                    },
                    enabled = !isFetching,
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("Reload")
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                if (filteredVoices.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (isFetching) "Loading voices..." else "No voices found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (!isFetching && discovered.isEmpty()) {
                                TextButton(onClick = { fetchVoices() }) {
                                    Text("Fetch voices")
                                }
                            }
                        }
                    }
                }
                itemsIndexed(filteredVoices, key = { _, voice -> "${voice.providerVoiceId}:${voice.name}" }) { _, voice ->
                    val selectedVoice = provider.voices.firstOrNull { selectedVoice -> voicesReferToSameProviderVoice(selectedVoice, voice) }
                    val selected = selectedVoice != null
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.98f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        label = "api_voice_card_scale",
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                                haptics.perform(HapticPattern.Pop)
                                if (selected) {
                                    onRemoveVoice(selectedVoice ?: voice)
                                } else {
                                    onAddVoice(voice)
                                }
                            },
                        shape = AppShapes.CardLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = voice.name.ifBlank { voice.providerVoiceId },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        voice.providerVoiceId,
                                        voice.locale,
                                        voice.enginePackageName,
                                    ).filter { it.isNotBlank() }.joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    tts.speak(
                                        text = "Hello, this is what this voice sounds like.",
                                        overrideSetting = provider.withVoiceApplied(voice),
                                    )
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null)
                            }
                            VoiceSelectionCircle(selected = selected)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text("Search voices") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Try name, id, locale, or engine") },
                singleLine = true,
                shape = AppShapes.SearchField,
            )
        }
    }
}

@Composable
private fun VoiceSelectionCircle(selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "voice_selection_circle_scale",
    )
    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ManualVoiceSheet(
    provider: TTSProviderSetting,
    onDismiss: () -> Unit,
    onAddVoice: (TTSVoice) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberPremiumHaptics()
    var voiceName by remember(provider.id) { mutableStateOf("") }
    var voiceId by remember(provider.id) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Manual Voice", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = voiceName,
                onValueChange = { voiceName = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.InputField,
                singleLine = true,
            )
            OutlinedTextField(
                value = voiceId,
                onValueChange = { voiceId = it },
                label = { Text("Provider voice id") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.InputField,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                TextButton(
                    enabled = voiceId.isNotBlank(),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onAddVoice(manualVoiceForProvider(provider, voiceName, voiceId))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Add")
                }
            }
        }
    }
}

private fun providerPresetVoices(provider: TTSProviderSetting): List<TTSVoice> {
    return when (provider) {
        is TTSProviderSetting.OpenAI -> listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer").map {
            TTSVoice(name = it.replaceFirstChar(Char::uppercaseChar), providerVoiceId = it, model = provider.model)
        }
        is TTSProviderSetting.Gemini -> listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede").map {
            TTSVoice(name = it, providerVoiceId = it, model = provider.model)
        }
        is TTSProviderSetting.MiniMax -> listOf("female-shaonv", "female-yujie", "male-qn-qingse", "audiobook_male_1").map {
            TTSVoice(name = it, providerVoiceId = it, model = provider.model, emotion = provider.emotion, speed = provider.speed)
        }
        is TTSProviderSetting.ElevenLabs -> listOf(TTSVoice(name = "Rachel", providerVoiceId = provider.voiceId, model = provider.modelId))
        is TTSProviderSetting.Qwen -> listOf("Cherry", "Serene", "Ethan", "Chelsie", "Momo", "Vivian").map {
            TTSVoice(name = it, providerVoiceId = it, model = provider.model, languageType = provider.languageType)
        }
        is TTSProviderSetting.SystemTTS -> emptyList()
        is TTSProviderSetting.Cartesia,
        is TTSProviderSetting.FishAudio,
        is TTSProviderSetting.PlayHT -> emptyList()
    }
}

private fun manualVoiceForProvider(
    provider: TTSProviderSetting,
    voiceName: String,
    voiceId: String,
): TTSVoice {
    val trimmedVoiceId = voiceId.trim()
    val trimmedName = voiceName.trim()
    val displayName = trimmedName.ifBlank { trimmedVoiceId }
    return when (provider) {
        is TTSProviderSetting.OpenAI -> TTSVoice(
            name = displayName,
            providerVoiceId = trimmedVoiceId,
            model = provider.model,
        )

        is TTSProviderSetting.Gemini -> TTSVoice(
            name = displayName,
            providerVoiceId = trimmedVoiceId,
            model = provider.model,
        )

        is TTSProviderSetting.SystemTTS -> TTSVoice(
            name = displayName,
            providerVoiceId = trimmedVoiceId,
            enginePackageName = provider.enginePackageName,
            pitch = provider.pitch,
            speed = provider.speechRate,
        )

        is TTSProviderSetting.MiniMax -> TTSVoice(
            name = displayName,
            providerVoiceId = trimmedVoiceId,
            model = provider.model,
            emotion = provider.emotion,
            speed = provider.speed,
        )

        is TTSProviderSetting.ElevenLabs -> TTSVoice(
            name = displayName,
            providerVoiceId = trimmedVoiceId,
            model = provider.modelId,
        )

        is TTSProviderSetting.Qwen -> TTSVoice(
            name = displayName,
            providerVoiceId = trimmedVoiceId,
            model = provider.model,
            languageType = provider.languageType,
        )

        is TTSProviderSetting.Cartesia,
        is TTSProviderSetting.FishAudio,
        is TTSProviderSetting.PlayHT -> TTSVoice(
            name = displayName,
            providerVoiceId = trimmedVoiceId,
        )
    }
}

private fun voicesReferToSameProviderVoice(a: TTSVoice, b: TTSVoice): Boolean {
    return a.providerVoiceId.equals(b.providerVoiceId, ignoreCase = true) &&
        (a.enginePackageName ?: "").equals(b.enginePackageName ?: "", ignoreCase = true) &&
        (a.model ?: "").equals(b.model ?: "", ignoreCase = true)
}

private suspend fun fetchProviderVoices(
    context: android.content.Context,
    httpClient: PlatformHttpClient,
    provider: TTSProviderSetting,
): List<TTSVoice> = withContext(Dispatchers.IO) {
    when (provider) {
        is TTSProviderSetting.SystemTTS -> discoverLocalTtsEngines(context).flatMap { engine ->
            discoverLocalTtsVoices(context, engine.packageName).map { voice ->
                TTSVoice(
                    name = voice.name,
                    providerVoiceId = voice.name,
                    locale = voice.localeTag.takeIf(String::isNotBlank),
                    enginePackageName = engine.packageName,
                    requiresNetwork = voice.requiresNetwork,
                )
            }
        }.distinctBy { voice ->
            "${voice.enginePackageName.orEmpty()}:${voice.providerVoiceId}"
        }

        is TTSProviderSetting.ElevenLabs -> runCatching {
            val response = httpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = "https://api.elevenlabs.io/v1/voices",
                    headers = mapOf("xi-api-key" to provider.apiKey),
                )
            )
            if (response.statusCode !in 200..299) return@runCatching emptyList()
            val root = Json.parseToJsonElement(response.body.decodeToString()) as? JsonObject
                ?: return@runCatching emptyList()
            val voices = root["voices"] as? JsonArray ?: return@runCatching emptyList()
            voices.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val voiceId = (obj["voice_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: voiceId
                TTSVoice(name = name, providerVoiceId = voiceId, model = provider.modelId)
            }
        }.getOrElse { emptyList() }

        else -> providerPresetVoices(provider)
    }
}


