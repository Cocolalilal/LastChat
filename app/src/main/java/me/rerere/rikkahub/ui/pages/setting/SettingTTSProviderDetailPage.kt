package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.data.ai.models.ttsProviderIconUri
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderConfigure
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TTSVoice
import me.rerere.tts.provider.discoverLocalTtsVoices
import me.rerere.tts.provider.withVoiceApplied
import okhttp3.OkHttpClient
import okhttp3.Request
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
                        TtsProviderIcon(provider = provider, catalogSnapshot = catalogSnapshot)
                        Text(provider.name.ifBlank { "TTS Provider" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = AppShapes.ButtonPill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DetailTabButton(
                        selected = pager.currentPage == 0,
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    )
                    DetailTabButton(
                        selected = pager.currentPage == 1,
                        icon = { Icon(Icons.Rounded.ViewModule, null) },
                        onClick = { scope.launch { pager.animateScrollToPage(1) } },
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
        ) { page ->
            when (page) {
                0 -> TtsProviderConfigTab(provider = provider, onUpdateProvider = ::updateProvider)
                1 -> TtsVoiceTab(provider = provider, onUpdateProvider = ::updateProvider)
            }
        }
    }
}

@Composable
private fun DetailTabButton(
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun TtsProviderConfigTab(
    provider: TTSProviderSetting,
    onUpdateProvider: (TTSProviderSetting) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
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
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(provider.moveVoice(from.index, to.index))
    }
    var editingVoice by remember(provider.id) { mutableStateOf<TTSVoice?>(null) }
    var showAddSheet by remember(provider.id) { mutableStateOf(false) }
    val tts = LocalTTSState.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (provider.voices.isEmpty()) {
                item {
                    Text(
                        text = "No voices yet. Add or fetch voices to use this provider.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            itemsIndexed(provider.voices, key = { _, voice -> voice.id }) { index, voice ->
                val position = when {
                    provider.voices.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == provider.voices.lastIndex -> ItemPosition.LAST
                    else -> ItemPosition.MIDDLE
                }
                ReorderableItem(reorderableState, key = voice.id) {
                    TtsVoiceRow(
                        voice = voice,
                        position = position,
                        onEdit = { editingVoice = voice },
                        onDelete = { onUpdateProvider(provider.delVoice(voice)) },
                        onTest = {
                            tts.speak(
                                text = "Hello, this is what this voice sounds like.",
                                overrideSetting = provider.withVoiceApplied(voice),
                            )
                        },
                        dragHandle = {
                            Icon(
                                Icons.Rounded.DragIndicator,
                                null,
                                modifier = Modifier.longPressDraggableHandle(),
                            )
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = AppShapes.CardLarge,
        ) {
            Icon(Icons.Rounded.Add, null)
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

    if (showAddSheet) {
        AddVoiceSheet(
            provider = provider,
            onDismiss = { showAddSheet = false },
            onAddVoices = { voices ->
                var updated = provider
                voices.forEach { voice -> updated = updated.addVoice(voice) }
                onUpdateProvider(updated)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun TtsVoiceRow(
    voice: TTSVoice,
    position: ItemPosition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val shape = when (position) {
        ItemPosition.FIRST -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
        ItemPosition.LAST -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        ItemPosition.MIDDLE -> RoundedCornerShape(10.dp)
        ItemPosition.ONLY -> RoundedCornerShape(24.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onEdit)
            .padding(16.dp),
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
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null) }
        dragHandle()
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
private fun AddVoiceSheet(
    provider: TTSProviderSetting,
    onDismiss: () -> Unit,
    onAddVoices: (List<TTSVoice>) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val okHttpClient = koinInject<OkHttpClient>()
    var discovered by remember(provider.id) { mutableStateOf(providerPresetVoices(provider)) }
    var isFetching by remember { mutableStateOf(false) }
    var customVoice by remember { mutableStateOf(TTSVoice(name = "", providerVoiceId = "")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.8f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add Voices", style = MaterialTheme.typography.headlineSmall)
                IconButton(
                    enabled = !isFetching,
                    onClick = {
                        scope.launch {
                            isFetching = true
                            discovered = fetchProviderVoices(context, okHttpClient, provider).ifEmpty { discovered }
                            isFetching = false
                        }
                    }
                ) {
                    Icon(Icons.Rounded.CloudDownload, null)
                }
            }

            OutlinedTextField(
                value = customVoice.providerVoiceId,
                onValueChange = {
                    customVoice = customVoice.copy(
                        providerVoiceId = it,
                        name = customVoice.name.ifBlank { it },
                    )
                },
                label = { Text("Manual voice id") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.InputField,
            )
            TextButton(
                enabled = customVoice.providerVoiceId.isNotBlank(),
                onClick = {
                    onAddVoices(listOf(customVoice.copy(name = customVoice.name.ifBlank { customVoice.providerVoiceId })))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add manual voice")
            }
            HorizontalDivider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                itemsIndexed(discovered, key = { _, voice -> "${voice.providerVoiceId}:${voice.name}" }) { _, voice ->
                    Surface(
                        onClick = { onAddVoices(listOf(voice.copy(id = Uuid.random()))) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(voice.name.ifBlank { voice.providerVoiceId }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(voice.providerVoiceId, voice.locale).filter { it.isNotBlank() }.joinToString(" - "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
    }
}

private suspend fun fetchProviderVoices(
    context: android.content.Context,
    okHttpClient: OkHttpClient,
    provider: TTSProviderSetting,
): List<TTSVoice> = withContext(Dispatchers.IO) {
    when (provider) {
        is TTSProviderSetting.SystemTTS -> discoverLocalTtsVoices(context, provider.enginePackageName).map {
            TTSVoice(
                name = it.name,
                providerVoiceId = it.name,
                locale = it.localeTag.takeIf(String::isNotBlank),
                requiresNetwork = it.requiresNetwork,
            )
        }

        is TTSProviderSetting.ElevenLabs -> runCatching {
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/voices")
                .addHeader("xi-api-key", provider.apiKey)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val body = response.body.string()
                Json.parseToJsonElement(body).jsonObject["voices"]?.jsonArray.orEmpty().mapNotNull { item ->
                    val obj = item.jsonObject
                    val voiceId = obj["voice_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: voiceId
                    TTSVoice(name = name, providerVoiceId = voiceId, model = provider.modelId)
                }
            }
        }.getOrElse { emptyList() }

        else -> providerPresetVoices(provider)
    }
}

@Composable
private fun TtsProviderIcon(
    provider: TTSProviderSetting,
    catalogSnapshot: me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot?,
) {
    val catalogId = when (provider) {
        is TTSProviderSetting.OpenAI -> "openai"
        is TTSProviderSetting.Gemini -> "gemini"
        is TTSProviderSetting.MiniMax -> "minimax"
        is TTSProviderSetting.ElevenLabs -> "elevenlabs"
        is TTSProviderSetting.Qwen -> "qwen"
        is TTSProviderSetting.SystemTTS -> "System"
    }
    AutoAIIconWithUrl(
        name = provider.name.ifBlank { catalogId },
        customIconUri = catalogSnapshot?.ttsProviderIconUri(catalogId),
        modifier = Modifier.size(24.dp),
    )
}
