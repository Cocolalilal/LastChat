package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.asr.ASRProviderSetting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import me.rerere.asr.fetchSttModels
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.sttProviderIconUri
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.CustomIconSelector
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus
import okhttp3.OkHttpClient
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.reflect.KClass

@Composable
fun SettingSTTPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val catalogSnapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "STT Providers",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
            )
        },
        bottomBar = {
            var showSettingsDialog by remember { mutableStateOf(false) }
            val haptics = rememberPremiumHaptics(enabled = vm.settings.value.displaySetting.enableUIHaptics)
            ProvidersBottomBar(selectedTab = ProvidersTab.Stt) {
                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showSettingsDialog = true
                    },
                    shape = AppShapes.CardLarge,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "STT Settings")
                }
                AddSTTProviderButton(
                    catalogSnapshot = catalogSnapshot,
                    asFab = true,
                ) { provider ->
                    val settings = vm.settings.value
                    vm.updateSettings(
                        settings.copy(sttProviders = listOf(provider) + settings.sttProviders)
                    )
                }
            }

            if (showSettingsDialog) {
                val settings by vm.settings.collectAsStateWithLifecycle()
                ModalBottomSheet(
                    onDismissRequest = { showSettingsDialog = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "STT Settings",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "By default, you can start the STT by pressing the plus button on the left of the message input field for more than 3 seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        FormItem(
                            modifier = Modifier.clickable {
                                vm.updateSettings(
                                    settings.copy(
                                        displaySetting = settings.displaySetting.copy(
                                            sttReplaceModelIcon = !settings.displaySetting.sttReplaceModelIcon
                                        )
                                    )
                                )
                            }.padding(vertical = 8.dp),
                            label = { Text("Replace model icon with microphone") },
                            description = { Text("Show the microphone instead of the model picker in the chat input.") },
                            tail = {
                                Switch(
                                    checked = settings.displaySetting.sttReplaceModelIcon,
                                    onCheckedChange = {
                                        vm.updateSettings(
                                            settings.copy(
                                                displaySetting = settings.displaySetting.copy(
                                                    sttReplaceModelIcon = it
                                                )
                                            )
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier.then(Modifier)
    ) { innerPadding ->
        SttProvidersContent(vm = vm, contentPadding = innerPadding)
    }
}

@Composable
internal fun SttProvidersContent(
    vm: SettingVM = koinViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.sttProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vm.updateSettings(settings.copy(sttProviders = newProviders))
    }

    var editingProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<ASRProviderSetting?>(null) }

    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = contentPadding + PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            state = lazyListState,
        ) {
            itemsIndexed(settings.sttProviders, key = { _, provider -> provider.id }) { index, provider ->
                val position = when {
                    settings.sttProviders.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == settings.sttProviders.lastIndex -> ItemPosition.LAST
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
                    animateItemModifier = Modifier.animateItem(),
                ) { isDragging ->
                    val dragScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isDragging) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "stt_provider_drag_scale",
                    )
                    key(provider.id) {
                        val selected = settings.selectedSttProviderId == provider.id
                        PhysicsSwipeToDelete(
                            position = position,
                            groupCornerRadius = if (selected) 50.dp else 24.dp,
                            itemCornerRadius = if (selected) 50.dp else 10.dp,
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
                                .fillMaxWidth(),
                        ) { shape ->
                            STTProviderItemContent(
                                provider = provider,
                                position = position,
                                selected = selected,
                                shape = shape,
                                haptics = haptics,
                                onClick = {
                                    vm.updateSettings(settings.copy(selectedSttProviderId = provider.id))
                                },
                                onEdit = { editingProvider = provider },
                                dragHandle = {
                                    IconButton(
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
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
        )
    }

    if (showDeleteDialog && providerToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                providerToDelete = null
            },
            title = { Text("Delete STT provider?") },
            text = { Text("This provider will be removed from your speech-to-text list.") },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    providerToDelete = null
                }) {
                    Text(stringResourceSafeCancel())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    providerToDelete?.let { provider ->
                        vm.updateSettings(
                            settings.copy(
                                sttProviders = settings.sttProviders.filterNot { it.id == provider.id },
                                selectedSttProviderId = settings.selectedSttProviderId.takeIf { it != provider.id },
                            ),
                        )
                    }
                    showDeleteDialog = false
                    providerToDelete = null
                }) {
                    Text("Delete")
                }
            },
        )
    }

    STTProviderEditorSheet(
        provider = editingProvider,
        onDismiss = { editingProvider = null },
        onSave = { original, updated ->
            vm.updateSettings(
                settings.copy(
                    sttProviders = settings.sttProviders.map { if (it.id == original.id) updated else it },
                ),
            )
            editingProvider = null
        },
    )
}

@Composable
private fun STTProviderItemContent(
    provider: ASRProviderSetting,
    position: ItemPosition,
    selected: Boolean,
    shape: Shape,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (LocalDarkMode.current) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "sttProviderBackground"
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "sttProviderTextColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickable {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        STTProviderIcon(provider = provider, contentColor = contentColor)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = provider.name.ifBlank { provider.typeName() },
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = {
            haptics.perform(HapticPattern.Pop)
            onEdit()
        }) {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = contentColor)
        }
        dragHandle()
    }
}

@Composable
private fun STTProviderIcon(provider: ASRProviderSetting, contentColor: Color) {
    AutoAIIconWithUrl(
        name = provider.name.ifBlank { provider.typeName() },
        customIconUri = provider.customIconUri,
        modifier = Modifier.size(40.dp),
    )
}

@Composable
internal fun AddSTTProviderButton(
    catalogSnapshot: ModelCatalogSnapshot? = null,
    enableHaptics: Boolean = true,
    asFab: Boolean = false,
    onAdd: (ASRProviderSetting) -> Unit,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customProvider by remember { mutableStateOf<ASRProviderSetting.OpenAICompatible?>(null) }
    val haptics = rememberPremiumHaptics(enabled = enableHaptics)

    val onClick = {
        haptics.perform(HapticPattern.Pop)
        searchQuery = ""
        showBottomSheet = true
    }

    if (asFab) {
        FloatingActionButton(
            onClick = onClick,
            shape = AppShapes.CardLarge
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.setting_tts_page_add_provider_content_description))
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.setting_tts_page_add_provider_content_description))
        }
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        data class STTPreset(
            val type: KClass<out ASRProviderSetting>,
            val name: String,
            val description: String,
            val catalogId: String? = null,
            val baseUrl: String? = null,
            val defaultModel: String? = null,
        )

        val allPresets = listOf(
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "OpenAI", "OpenAI Whisper / gpt-4o-transcribe", catalogId = "openai", baseUrl = "https://api.openai.com/v1", defaultModel = "whisper-1"),
            STTPreset(ASRProviderSetting.OpenAIRealtime::class, "OpenAI Realtime", "Live transcription via WebSocket", catalogId = "openai_realtime"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "OpenRouter", "Multiple STT providers through one API", catalogId = "openrouter", baseUrl = "https://openrouter.ai/api/v1", defaultModel = "openai/whisper-large-v3"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "Groq", "Ultra-fast Whisper on Groq", catalogId = "groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "whisper-large-v3-turbo"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "Regolo AI", "European Whisper STT", catalogId = "regolo", baseUrl = "https://api.regolo.ai/v1", defaultModel = "faster-whisper-large-v3"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "xAI", "Grok speech-to-text API", catalogId = "xai", baseUrl = "https://api.x.ai/v1", defaultModel = "grok-stt-v1"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "DeepInfra", "Whisper on DeepInfra", catalogId = "deepinfra", baseUrl = "https://api.deepinfra.com/v1/openai", defaultModel = "openai/whisper-large"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "Together AI", "Whisper on Together AI", catalogId = "together", baseUrl = "https://api.together.ai/v1", defaultModel = "openai/whisper-large-v3"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "Fireworks AI", "Whisper on Fireworks AI", catalogId = "fireworks", baseUrl = "https://api.fireworks.ai/inference/v1", defaultModel = "whisper-v3"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "SiliconFlow", "Whisper on SiliconFlow", catalogId = "siliconflow", baseUrl = "https://api.siliconflow.cn/v1", defaultModel = "FunAudioLLM/SenseVoiceSmall"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "AiHubMix", "OpenAI-compatible STT aggregator", catalogId = "aihubmix", baseUrl = "https://aihubmix.com/v1", defaultModel = "whisper-1"),
            STTPreset(ASRProviderSetting.OpenAICompatible::class, "Novita AI", "Whisper on Novita AI", catalogId = "novita", baseUrl = "https://api.novita.ai/v1", defaultModel = "openai/whisper-large-v3"),
            STTPreset(ASRProviderSetting.DashScope::class, "DashScope", "Aliyun DashScope realtime ASR", catalogId = "dashscope"),
            STTPreset(ASRProviderSetting.Volcengine::class, "Volcengine", "ByteDance Volcengine SeedASR", catalogId = "volcengine"),
            STTPreset(ASRProviderSetting.MiMo::class, "MiMo", "Xiaomi MiMo ASR", catalogId = "mimo"),
            STTPreset(ASRProviderSetting.Step::class, "Step", "StepFun StepAudio ASR", catalogId = "step"),
        )

        val filteredPresets = if (searchQuery.isBlank()) {
            allPresets
        } else {
            allPresets.filter { preset ->
                preset.name.contains(searchQuery, ignoreCase = true) ||
                    preset.description.contains(searchQuery, ignoreCase = true)
            }
        }

        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                Text(
                    text = "Add STT Provider",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

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
                        // Add Custom Provider card at the top
                        item {
                            Card(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    showBottomSheet = false
                                    customProvider = ASRProviderSetting.OpenAICompatible()
                                    showCustomDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.setting_provider_page_add_custom_provider),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.setting_provider_page_add_custom_provider_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

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

                            val iconUri = preset.catalogId?.let { catalogSnapshot?.sttProviderIconUri(it) }

                            Surface(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    val newProvider = when (preset.type) {
                                        ASRProviderSetting.OpenAICompatible::class -> ASRProviderSetting.OpenAICompatible(
                                            name = preset.name,
                                            baseUrl = preset.baseUrl ?: "https://api.openai.com/v1",
                                            model = preset.defaultModel ?: "whisper-1",
                                            customIconUri = iconUri,
                                        )
                                        ASRProviderSetting.OpenAIRealtime::class -> ASRProviderSetting.OpenAIRealtime(
                                            name = preset.name,
                                            customIconUri = iconUri,
                                        )
                                        ASRProviderSetting.DashScope::class -> ASRProviderSetting.DashScope(
                                            name = preset.name,
                                            customIconUri = iconUri,
                                        )
                                        ASRProviderSetting.Volcengine::class -> ASRProviderSetting.Volcengine(
                                            name = preset.name,
                                            customIconUri = iconUri,
                                        )
                                        ASRProviderSetting.MiMo::class -> ASRProviderSetting.MiMo(
                                            name = preset.name,
                                            customIconUri = iconUri,
                                        )
                                        ASRProviderSetting.Step::class -> ASRProviderSetting.Step(
                                            name = preset.name,
                                            customIconUri = iconUri,
                                        )
                                        else -> ASRProviderSetting.OpenAICompatible(name = preset.name)
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
                                    AutoAIIconWithUrl(
                                        name = preset.name,
                                        customIconUri = iconUri,
                                        modifier = Modifier.size(40.dp)
                                    )

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
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom provider dialog
    if (showCustomDialog && customProvider != null) {
        STTProviderEditorSheet(
            provider = customProvider,
            title = "Add Custom STT Provider",
            onDismiss = {
                showCustomDialog = false
                customProvider = null
            },
            onSave = { _, updated ->
                onAdd(updated)
                showCustomDialog = false
                customProvider = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun STTProviderEditorSheet(
    provider: ASRProviderSetting?,
    title: String = "Edit STT Provider",
    onDismiss: () -> Unit,
    onSave: (ASRProviderSetting, ASRProviderSetting) -> Unit,
    onProviderChange: ((ASRProviderSetting) -> Unit)? = null,
) {
    provider ?: return
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentProvider by remember(provider) { mutableStateOf(provider) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentProvider) {
        onProviderChange?.invoke(currentProvider)
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                },
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxHeight(0.82f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            STTProviderConfigure(
                setting = currentProvider,
                onValueChange = { currentProvider = it },
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResourceSafeCancel())
                }
                TextButton(
                    onClick = { onSave(provider, currentProvider) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun STTProviderConfigure(
    setting: ASRProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (ASRProviderSetting) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        ProviderTypeField(setting = setting, onValueChange = onValueChange)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CustomIconSelector(
                customIconUri = setting.customIconUri,
                onPickFile = { /* file picker not wired for STT — LobeHub only */ },
                onPickLobeHubIcon = { slug ->
                    onValueChange(setting.copyProvider(customIconUri = "lobehub://$slug"))
                },
                onReset = {
                    onValueChange(setting.copyProvider(customIconUri = null))
                },
            ) { iconModifier ->
                AutoAIIconWithUrl(
                    name = setting.name.ifBlank { setting.typeName() },
                    customIconUri = setting.customIconUri,
                    modifier = iconModifier,
                )
            }
            OutlinedTextField(
                value = setting.name,
                onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("STT Provider") },
                shape = AppShapes.InputField,
            )
        }

        when (setting) {
            is ASRProviderSetting.SystemSTT -> {}
            is ASRProviderSetting.OpenAICompatible -> OpenAICompatibleSTTConfiguration(setting, onValueChange)
            is ASRProviderSetting.OpenAIRealtime -> OpenAIRealtimeSTTConfiguration(setting, onValueChange)
            is ASRProviderSetting.DashScope -> DashScopeSTTConfiguration(setting, onValueChange)
            is ASRProviderSetting.Volcengine -> VolcengineSTTConfiguration(setting, onValueChange)
            is ASRProviderSetting.MiMo -> MiMoSTTConfiguration(setting, onValueChange)
            is ASRProviderSetting.Step -> StepSTTConfiguration(setting, onValueChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTypeField(
    setting: ASRProviderSetting,
    onValueChange: (ASRProviderSetting) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FormItem(
        label = { Text("Provider Type") },
        description = { Text("Changing type keeps the provider name and id.") },
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = setting.typeName(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = AppShapes.InputField,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                ASRProviderSetting.Types.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.sttTypeName()) },
                        onClick = {
                            expanded = false
                            onValueChange(setting.convertTo(type))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenAICompatibleSTTConfiguration(
    setting: ASRProviderSetting.OpenAICompatible,
    onValueChange: (ASRProviderSetting) -> Unit,
) {
    ApiKeyField(setting.apiKey, { onValueChange(setting.copy(apiKey = it)) }, "API key")
    TextFieldItem("Base URL", setting.baseUrl, { onValueChange(setting.copy(baseUrl = it)) }, "https://api.openai.com/v1")
    SttModelPicker(
        label = "Model",
        currentModel = setting.model,
        baseUrl = setting.baseUrl,
        apiKey = setting.apiKey,
        onValueChange = { onValueChange(setting.copy(model = it)) },
        placeholder = "whisper-1",
    )
    TextFieldItem("Language", setting.language, { onValueChange(setting.copy(language = it)) }, "auto (e.g. en, zh)")
    TextFieldItem("Prompt", setting.prompt, { onValueChange(setting.copy(prompt = it)) }, "Optional context", minLines = 2)
    FormItem(label = { Text("Temperature") }, description = { Text("0 = most deterministic, 1 = more random.") }) {
        OutlinedNumberInput(
            value = setting.temperature,
            onValueChange = { if (it in 0f..1f) onValueChange(setting.copy(temperature = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Temperature",
        )
    }
    FormItem(label = { Text("Response Format") }) {
        OutlinedTextField(
            value = setting.responseFormat,
            onValueChange = { onValueChange(setting.copy(responseFormat = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("text") },
            shape = AppShapes.InputField,
        )
    }
    NumberItem("Sample Rate", setting.sampleRate, { if (it in 8000..48000) onValueChange(setting.copy(sampleRate = it)) })
    NumberItem("Segment Duration", setting.segmentDurationSec, { if (it in 0..300) onValueChange(setting.copy(segmentDurationSec = it)) })
}

/**
 * Model picker for STT providers. Fetches available models from the provider's
 * /models endpoint and shows them in a bottom sheet. Falls back to free-text
 * if fetching fails or the user prefers to type manually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SttModelPicker(
    label: String,
    currentModel: String,
    baseUrl: String,
    apiKey: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    val httpClient = koinInject<OkHttpClient>()
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var manualText by remember { mutableStateOf("") }
    val haptics = rememberPremiumHaptics()

    fun loadModels() {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        scope.launch {
            isLoading = true
            loadError = null
            val result = runCatching { fetchSttModels(httpClient, baseUrl, apiKey) }
            isLoading = false
            hasLoaded = true
            result
                .onSuccess { models = it }
                .onFailure { loadError = it.message ?: "Failed to fetch models" }
        }
    }

    FormItem(label = { Text(label) }) {
        OutlinedTextField(
            value = currentModel,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            shape = AppShapes.InputField,
            trailingIcon = {
                IconButton(onClick = {
                    haptics.perform(HapticPattern.Pop)
                    showPicker = true
                    if (!hasLoaded && !isLoading) loadModels()
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Pick model")
                }
            },
        )
    }

    if (showPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Select Model", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        haptics.perform(HapticPattern.Pop)
                        loadModels()
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }

                if (baseUrl.isBlank() || apiKey.isBlank()) {
                    Text(
                        text = "Enter Base URL and API key first to fetch models.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (loadError != null) {
                    Text(
                        text = loadError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (models.isEmpty() && hasLoaded) {
                    Text(
                        text = "No models found. Enter a model ID manually below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                models.forEach { modelId ->
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            onValueChange(modelId)
                            showPicker = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.InputField,
                        color = if (modelId == currentModel) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Text(
                            text = modelId,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (modelId == currentModel) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Manual entry:", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(placeholder) },
                        shape = AppShapes.InputField,
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            if (manualText.isNotBlank()) {
                                haptics.perform(HapticPattern.Pop)
                                onValueChange(manualText.trim())
                                manualText = ""
                                showPicker = false
                            }
                        },
                    ) {
                        Text("Set")
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenAIRealtimeSTTConfiguration(
    setting: ASRProviderSetting.OpenAIRealtime,
    onValueChange: (ASRProviderSetting) -> Unit,
) {
    ApiKeyField(setting.apiKey, { onValueChange(setting.copy(apiKey = it)) }, "OpenAI API key")
    TextFieldItem("WebSocket URL", setting.websocketUrl, { onValueChange(setting.copy(websocketUrl = it)) }, "wss://api.openai.com/v1/realtime?intent=transcription")
    SttModelPicker(
        label = "Model",
        currentModel = setting.model,
        baseUrl = "https://api.openai.com/v1",
        apiKey = setting.apiKey,
        onValueChange = { onValueChange(setting.copy(model = it)) },
        placeholder = "gpt-4o-transcribe",
    )
    TextFieldItem("Language", setting.language, { onValueChange(setting.copy(language = it)) }, "auto")
    TextFieldItem("Prompt", setting.prompt, { onValueChange(setting.copy(prompt = it)) }, "Optional", minLines = 2)
    NumberItem("Sample Rate", setting.sampleRate, { if (it in 8000..48000) onValueChange(setting.copy(sampleRate = it)) })
    NumberItem("VAD Threshold", setting.vadThreshold, { if (it in 0f..1f) onValueChange(setting.copy(vadThreshold = it)) })
    NumberItem("Prefix Padding", setting.prefixPaddingMs, { if (it in 0..2000) onValueChange(setting.copy(prefixPaddingMs = it)) })
    NumberItem("Silence Duration", setting.silenceDurationMs, { if (it in 100..5000) onValueChange(setting.copy(silenceDurationMs = it)) })
}

@Composable
private fun DashScopeSTTConfiguration(setting: ASRProviderSetting.DashScope, onValueChange: (ASRProviderSetting) -> Unit) {
    ApiKeyField(setting.apiKey, { onValueChange(setting.copy(apiKey = it)) }, "DashScope API key")
    TextFieldItem("WebSocket URL", setting.websocketUrl, { onValueChange(setting.copy(websocketUrl = it)) }, "wss://dashscope.aliyuncs.com/api-ws/v1/inference")
    TextFieldItem("Model", setting.model, { onValueChange(setting.copy(model = it)) }, "qwen3-asr-flash-realtime")
    TextFieldItem("Language", setting.language, { onValueChange(setting.copy(language = it)) }, "auto")
    NumberItem("Sample Rate", setting.sampleRate, { if (it in 8000..48000) onValueChange(setting.copy(sampleRate = it)) })
    NumberItem("VAD Threshold", setting.vadThreshold, { if (it in 0f..1f) onValueChange(setting.copy(vadThreshold = it)) })
    NumberItem("Silence Duration", setting.silenceDurationMs, { if (it in 100..5000) onValueChange(setting.copy(silenceDurationMs = it)) })
}

@Composable
private fun VolcengineSTTConfiguration(setting: ASRProviderSetting.Volcengine, onValueChange: (ASRProviderSetting) -> Unit) {
    ApiKeyField(setting.apiKey, { onValueChange(setting.copy(apiKey = it)) }, "Volcengine API key")
    TextFieldItem("WebSocket URL", setting.websocketUrl, { onValueChange(setting.copy(websocketUrl = it)) }, "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel")
    TextFieldItem("Resource ID", setting.resourceId, { onValueChange(setting.copy(resourceId = it)) }, "volc.seedasr.sauc.duration")
    TextFieldItem("Language", setting.language, { onValueChange(setting.copy(language = it)) }, "auto")
}

@Composable
private fun MiMoSTTConfiguration(setting: ASRProviderSetting.MiMo, onValueChange: (ASRProviderSetting) -> Unit) {
    ApiKeyField(setting.apiKey, { onValueChange(setting.copy(apiKey = it)) }, "MiMo API key")
    TextFieldItem("Base URL", setting.baseUrl, { onValueChange(setting.copy(baseUrl = it)) }, "https://api.xiaomimimo.com/v1")
    SttModelPicker(
        label = "Model",
        currentModel = setting.model,
        baseUrl = setting.baseUrl,
        apiKey = setting.apiKey,
        onValueChange = { onValueChange(setting.copy(model = it)) },
        placeholder = "mimo-v2.5-asr",
    )
    TextFieldItem("Language", setting.language, { onValueChange(setting.copy(language = it)) }, "auto")
    NumberItem("Sample Rate", setting.sampleRate, { if (it in 8000..48000) onValueChange(setting.copy(sampleRate = it)) })
    NumberItem("Segment Duration", setting.segmentDurationSec, { if (it in 0..300) onValueChange(setting.copy(segmentDurationSec = it)) })
}

@Composable
private fun StepSTTConfiguration(setting: ASRProviderSetting.Step, onValueChange: (ASRProviderSetting) -> Unit) {
    ApiKeyField(setting.apiKey, { onValueChange(setting.copy(apiKey = it)) }, "Step API key")
    TextFieldItem("Base URL", setting.baseUrl, { onValueChange(setting.copy(baseUrl = it)) }, "https://api.stepfun.com")
    SttModelPicker(
        label = "Model",
        currentModel = setting.model,
        baseUrl = "${setting.baseUrl.trimEnd('/')}/v1",
        apiKey = setting.apiKey,
        onValueChange = { onValueChange(setting.copy(model = it)) },
        placeholder = "stepaudio-2.5-asr",
    )
    TextFieldItem("Language", setting.language, { onValueChange(setting.copy(language = it)) }, "auto")
    NumberItem("Sample Rate", setting.sampleRate, { if (it in 8000..48000) onValueChange(setting.copy(sampleRate = it)) })
    NumberItem("Segment Duration", setting.segmentDurationSec, { if (it in 0..300) onValueChange(setting.copy(segmentDurationSec = it)) })
    FormItem(label = { Text("Inverse text normalization") }, description = { Text("Convert spoken numbers into normalized text.") }) {
        Switch(checked = setting.enableItn, onCheckedChange = { onValueChange(setting.copy(enableItn = it)) })
    }
    FormItem(label = { Text("Timestamps") }, description = { Text("Request word-level timestamps when supported.") }) {
        Switch(checked = setting.enableTimestamp, onCheckedChange = { onValueChange(setting.copy(enableTimestamp = it)) })
    }
    TextFieldItem("Hotwords", setting.hotwords.joinToString(","), {
        onValueChange(setting.copy(hotwords = it.split(",").map { word -> word.trim() }.filter { word -> word.isNotEmpty() }))
    }, "word one, word two")
}

@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit, description: String) {
    var visible by remember { mutableStateOf(false) }
    FormItem(label = { Text("API Key") }, description = { Text(description) }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Hide" else "Show")
                }
            },
            shape = AppShapes.InputField,
        )
    }
}

@Composable
private fun TextFieldItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
) {
    FormItem(label = { Text(label) }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            placeholder = { Text(placeholder) },
            shape = AppShapes.InputField,
        )
    }
}

@Composable
private fun NumberItem(label: String, value: Int, onValueChange: (Int) -> Unit) {
    FormItem(label = { Text(label) }) {
        OutlinedNumberInput(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
        )
    }
}

@Composable
private fun NumberItem(label: String, value: Float, onValueChange: (Float) -> Unit) {
    FormItem(label = { Text(label) }) {
        OutlinedNumberInput(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
        )
    }
}

private fun ASRProviderSetting.convertTo(type: KClass<out ASRProviderSetting>): ASRProviderSetting {
    val id = id
    val name = name
    val iconUri = customIconUri
    return when (type) {
        ASRProviderSetting.OpenAICompatible::class -> ASRProviderSetting.OpenAICompatible(id = id, name = name.ifBlank { "OpenAI-compatible STT" }, customIconUri = iconUri)
        ASRProviderSetting.OpenAIRealtime::class -> ASRProviderSetting.OpenAIRealtime(id = id, name = name.ifBlank { "OpenAI Realtime STT" }, customIconUri = iconUri)
        ASRProviderSetting.DashScope::class -> ASRProviderSetting.DashScope(id = id, name = name.ifBlank { "DashScope STT" }, customIconUri = iconUri)
        ASRProviderSetting.Volcengine::class -> ASRProviderSetting.Volcengine(id = id, name = name.ifBlank { "Volcengine STT" }, customIconUri = iconUri)
        ASRProviderSetting.MiMo::class -> ASRProviderSetting.MiMo(id = id, name = name.ifBlank { "MiMo STT" }, customIconUri = iconUri)
        ASRProviderSetting.Step::class -> ASRProviderSetting.Step(id = id, name = name.ifBlank { "Step STT" }, customIconUri = iconUri)
        else -> this
    }
}

private fun ASRProviderSetting.typeName(): String {
    return this::class.sttTypeName()
}

private fun KClass<out ASRProviderSetting>.sttTypeName(): String {
    return when (this) {
        ASRProviderSetting.OpenAICompatible::class -> "OpenAI-compatible"
        ASRProviderSetting.OpenAIRealtime::class -> "OpenAI Realtime"
        ASRProviderSetting.DashScope::class -> "DashScope"
        ASRProviderSetting.Volcengine::class -> "Volcengine"
        ASRProviderSetting.MiMo::class -> "MiMo"
        ASRProviderSetting.Step::class -> "Step"
        ASRProviderSetting.SystemSTT::class -> "System STT"
        else -> "STT Provider"
    }
}

@Composable
private fun stringResourceSafeCancel(): String {
    return androidx.compose.ui.res.stringResource(R.string.cancel)
}
