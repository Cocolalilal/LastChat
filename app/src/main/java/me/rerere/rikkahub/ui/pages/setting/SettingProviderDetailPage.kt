package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ImageGenerationMethod
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.models.ModelResolutionOptions
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ai.ProviderBalanceText
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.ProviderIcon
import me.rerere.rikkahub.ui.components.ui.ModelIcon
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.SiliconFlowPowerByIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.components.ui.lobeHubIconUri
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.setting.components.CustomIconSelector
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.SettingProviderBalanceOption
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Tag as DataTag
import me.rerere.rikkahub.ui.components.ui.FormItem

private val providerPickerResolutionOptions = ModelResolutionOptions(
    preserveDisplayName = true,
    preserveExistingCapabilities = true,
    preserveExistingType = true,
)

private fun resolveProviderModel(
    resolver: ModelMetadataResolver,
    provider: ProviderSetting,
    model: Model,
): Model {
    return resolver.applyToModel(
        model = model,
        providerHint = provider,
        options = providerPickerResolutionOptions,
    )
}

internal fun modelsReferToSameApiModel(savedModel: Model, apiModel: Model): Boolean {
    val savedKeys = savedModel.matchKeys()
    val apiKeys = apiModel.matchKeys()
    if (savedKeys.isEmpty() || apiKeys.isEmpty() || savedKeys.intersect(apiKeys).isEmpty()) {
        return false
    }

    if (savedModel.qualifiers() != apiModel.qualifiers()) {
        return false
    }

    val savedSlug = savedModel.providerSlug?.normalizeModelMatchToken()
    val apiSlug = apiModel.providerSlug?.normalizeModelMatchToken()
    return savedSlug == null || apiSlug == null || savedSlug == apiSlug
}

private fun Model.qualifiers(): Set<String> {
    return buildSet {
        modelId.lowercase().split(Regex("[\\-_:\\/\\s()]+")).forEach { token ->
            if (token in ModelIdNormalizer.removableSuffixes) {
                add(token)
            }
        }
        canonicalModelId?.lowercase()?.split(Regex("[\\-_:\\/\\s()]+"))?.forEach { token ->
            if (token in ModelIdNormalizer.removableSuffixes) {
                add(token)
            }
        }
    }
}

private fun Model.matchKeys(): Set<String> {
    return buildSet {
        modelId.takeIf { it.isNotBlank() }?.let {
            add(it.lowercase())
            add(ModelIdNormalizer.preprocess(it))
            add(ModelIdNormalizer.canonicalize(it))
        }
        canonicalModelId?.takeIf { it.isNotBlank() }?.let {
            add(it.lowercase())
            add(ModelIdNormalizer.canonicalize(modelId, it))
            add(ModelIdNormalizer.preprocess(it))
        }
    }.filterTo(linkedSetOf()) { it.isNotBlank() }
}

private fun String.normalizeModelMatchToken(): String {
    return lowercase()
        .replace('_', '-')
        .replace('.', '-')
}

private fun ProviderSetting.apiModelCacheKey(): String {
    return when (this) {
        is ProviderSetting.OpenAI -> listOf(
            "openai",
            id.toString(),
            baseUrl,
            chatCompletionsPath,
            useResponseApi.toString(),
            apiKey.hashCode().toString(),
        )

        is ProviderSetting.Google -> listOf(
            "google",
            id.toString(),
            baseUrl,
            vertexAI.toString(),
            location,
            projectId,
            apiKey.hashCode().toString(),
        )

        is ProviderSetting.Claude -> listOf(
            "claude",
            id.toString(),
            baseUrl,
            apiKey.hashCode().toString(),
        )

        is ProviderSetting.ComfyUI -> listOf(
            "comfyui",
            id.toString(),
            baseUrl,
            workflowJson.hashCode().toString(),
        )
    }.joinToString("|")
}

private fun ProviderSetting.canFetchApiModels(): Boolean {
    return when (this) {
        is ProviderSetting.OpenAI -> apiKey.isNotBlank()
        is ProviderSetting.Google -> if (vertexAI) {
            serviceAccountEmail.isNotBlank() && privateKey.isNotBlank() && projectId.isNotBlank()
        } else {
            apiKey.isNotBlank()
        }
        is ProviderSetting.Claude -> apiKey.isNotBlank()
        is ProviderSetting.ComfyUI -> workflowJson.isNotBlank()
    }
}

private fun iconFileExtension(context: android.content.Context, uri: android.net.Uri): String {
    val mimeType = context.contentResolver.getType(uri)?.lowercase()
    return when {
        mimeType == "image/svg+xml" -> "svg"
        mimeType == "image/png" -> "png"
        mimeType == "image/jpeg" -> "jpg"
        mimeType == "image/webp" -> "webp"
        !mimeType.isNullOrBlank() -> android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: "png"
        else -> uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.length in 2..5 }
            ?: "png"
    }
}

private object ApiModelListCache {
    private val lock = Any()
    private val modelsByProvider = mutableMapOf<String, List<Model>>()

    fun get(key: String): List<Model> = synchronized(lock) {
        modelsByProvider[key].orEmpty()
    }

    fun put(key: String, models: List<Model>) = synchronized(lock) {
        if (models.isNotEmpty()) {
            modelsByProvider[key] = models
        }
    }
}

@Composable
fun SettingProviderDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val provider = settings.providers.find { it.id == id } ?: return
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var savingResetJob by remember { mutableStateOf<Job?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val onEdit = { newProvider: ProviderSetting ->
        val newSettings = settings.copy(
            providers = settings.providers.map {
                if (newProvider.id == it.id) {
                    newProvider
                } else {
                    it
                }
            }
        )
        isSaving = true
        savingResetJob?.cancel()
        vm.updateSettings(newSettings)
        savingResetJob = scope.launch {
            delay(600)
            isSaving = false
        }
    }
    val onDelete = {
        val newSettings = settings.copy(
            providers = settings.providers - provider
        )
        vm.updateSettings(newSettings)
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton()
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderIcon(provider = provider, modifier = Modifier.size(22.dp))
                        Text(text = provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    val shareSheetState = rememberShareSheetState()
                    ShareSheet(shareSheetState)

                    ConnectionTesterButton(
                        provider = provider,
                        scope = scope
                    )

                    IconButton(
                        onClick = {
                            shareSheetState.show(provider)
                        }
                    ) {
                        Icon(Icons.Rounded.Share, null)
                    }
                }
            )
        },
        bottomBar = {
            val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
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
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (pager.currentPage == 0)
                                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                        else Modifier.clickable {
                                            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Tick)
                                            scope.launch { pager.animateScrollToPage(0) }
                                        }
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.setting_provider_page_configuration),
                                    tint = if (pager.currentPage == 0)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (pager.currentPage == 1)
                                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                        else Modifier.clickable {
                                            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Tick)
                                            scope.launch { pager.animateScrollToPage(1) }
                                        }
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ViewModule,
                                    contentDescription = stringResource(R.string.setting_provider_page_models),
                                    tint = if (pager.currentPage == 1)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(contentPadding)
            ) { page ->
                when {
                    page == 0 -> {
                        SettingProviderConfigPage(
                            provider = provider,
                            providerTags = settings.providerTags,
                            onEdit = {
                                onEdit(it)
                            },
                            onUpdateTags = { providerWithNewTags, updatedTags ->
                                // Update the provider first
                                val updatedProviders = settings.providers.map {
                                    if (it.id == providerWithNewTags.id) providerWithNewTags else it
                                }
                                
                                // Auto-cleanup: Filter out tags that are no longer used by any provider
                                val usedTagIds = updatedProviders.flatMap { it.tags }.toSet()
                                val cleanedTags = updatedTags.filter { tag -> tag.id in usedTagIds }
                                
                                val newSettings = settings.copy(
                                    providers = updatedProviders,
                                    providerTags = cleanedTags
                                )
                                vm.updateSettings(newSettings)
                            },
                            contentPadding = contentPadding,
                            isSaving = isSaving
                        )
                    }

                    page == 1 -> {
                        SettingProviderModelPage(
                            provider = provider,
                            onEdit = onEdit,
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingProviderConfigPage(
    provider: ProviderSetting,
    providerTags: List<DataTag>,
    onEdit: (ProviderSetting) -> Unit,
    onUpdateTags: (ProviderSetting, List<DataTag>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    isSaving: Boolean
) {
    var internalProvider by remember(provider) { mutableStateOf(provider) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                ProviderConfigure(
                    provider = internalProvider,
                    modifier = Modifier.padding(16.dp),
                    showSavingIndicator = isSaving,
                    onEdit = {
                        internalProvider = it
                        // Auto-save immediately
                        onEdit(it)
                    }
                )
            }
            
            // Tags section
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormItem(
                        label = {
                            Text(stringResource(R.string.assistant_page_tags))
                        },
                    ) {
                        TagsInput(
                            value = internalProvider.tags,
                            tags = providerTags,
                            onValueChange = { tagIds, updatedTags ->
                                // Update internal provider with new tag IDs
                                val updatedProvider = internalProvider.copyProvider(tags = tagIds)
                                internalProvider = updatedProvider
                                // Update both provider and global tags
                                onUpdateTags(updatedProvider, updatedTags)
                            },
                        )
                    }
                }
            }

            if (internalProvider is ProviderSetting.OpenAI) {
                SettingProviderBalanceOption(
                    provider = internalProvider,
                    balanceOption = internalProvider.balanceOption,
                    onEdit = { 
                        val updated = internalProvider.copyProvider(balanceOption = it)
                        internalProvider = updated
                        onEdit(updated)  // Auto-save like other config fields
                    }
                )
                ProviderBalanceText(providerSetting = provider, style = MaterialTheme.typography.labelSmall)
            }

            // SiliconFlow icon
            if (provider is ProviderSetting.OpenAI && provider.baseUrl.contains("siliconflow.cn")) {
                SiliconFlowPowerByIcon(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 16.dp)
                )
            }
        }
        
        // Bottom fade gradient
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
}

@Composable
private fun SettingProviderModelPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    ModelList(
        providerSetting = provider,
        onUpdateProvider = onEdit,
        contentPadding = contentPadding
    )
}

@Composable
private fun ConnectionTesterButton(
    provider: ProviderSetting,
    scope: CoroutineScope
) {
    var showTestDialog by remember { mutableStateOf(false) }
    val providerManager = koinInject<ProviderManager>()
    IconButton(
        onClick = {
            showTestDialog = true
        }
    ) {
        Icon(Icons.Rounded.NetworkCheck, null)
    }
    if (showTestDialog) {
        var model by remember(provider) {
            mutableStateOf(provider.models.firstOrNull { it.type == ModelType.CHAT })
        }
        var testState: UiState<String> by remember { mutableStateOf(UiState.Idle) }
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = {
                Text(stringResource(R.string.setting_provider_page_test_connection))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ModelSelector(
                        modelId = model?.id,
                        providers = listOf(provider),
                        type = ModelType.CHAT,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        model = it
                    }
                    when (testState) {
                        is UiState.Loading -> {
                            LinearWavyProgressIndicator()
                        }

                        is UiState.Success -> {
                            Text(
                                text = stringResource(R.string.setting_provider_page_test_success),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.extendColors.green6
                            )
                        }

                        is UiState.Error -> {
                            Text(
                                text = (testState as UiState.Error).error.message ?: "Error",
                                color = MaterialTheme.extendColors.red6,
                                maxLines = 10
                            )
                        }

                        else -> {}
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {

                TextButton(
                    onClick = {
                        if (model == null) return@TextButton
                        val providerInstance = providerManager.getProviderByType(provider)
                        scope.launch {
                            runCatching {
                                testState = UiState.Loading
                                providerInstance.generateText(
                                    providerSetting = provider,
                                    messages = listOf(
                                        UIMessage.user("hello")
                                    ),
                                    params = TextGenerationParams(
                                        model = model!!,
                                    )
                                )
                                testState = UiState.Success("Success")
                            }.onFailure {
                                testState = UiState.Error(it)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_test))
                }
            }
        )
    }
}

@Composable
private fun ModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val providerManager = koinInject<ProviderManager>()
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val modelItemIndexOffset = 0
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromModelIndex = from.index - modelItemIndexOffset
        val toModelIndex = to.index - modelItemIndexOffset
        if (
            fromModelIndex in providerSetting.models.indices &&
            toModelIndex in providerSetting.models.indices
        ) {
            onUpdateProvider(providerSetting.moveModel(fromModelIndex, toModelIndex))
        }
    }
    val density = LocalDensity.current
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val modelMetadataResolver = koinInject<ModelMetadataResolver>()
    val apiModelCacheKey = remember(providerSetting) { providerSetting.apiModelCacheKey() }
    var modelList by remember(apiModelCacheKey) { mutableStateOf(ApiModelListCache.get(apiModelCacheKey)) }
    var isReloadingModels by remember(apiModelCacheKey) { mutableStateOf(false) }
    var reloadModelsError by remember(apiModelCacheKey) { mutableStateOf<String?>(null) }

    fun syncFreshModelMetadata(freshModels: List<Model>, currentProvider: ProviderSetting): ProviderSetting {
        val updatedModels = currentProvider.models.map { savedModel ->
            val freshModel = freshModels.firstOrNull { apiModel ->
                modelsReferToSameApiModel(savedModel, apiModel)
            }
            if (freshModel != null) {
                val preserveDisplayName = savedModel.displayName.isNotBlank() &&
                    savedModel.displayName != savedModel.modelId
                savedModel.copy(
                    displayName = if (preserveDisplayName) savedModel.displayName else freshModel.displayName,
                    canonicalModelId = freshModel.canonicalModelId ?: savedModel.canonicalModelId,
                    type = freshModel.type,
                    inputModalities = freshModel.inputModalities,
                    outputModalities = freshModel.outputModalities,
                    abilities = freshModel.abilities,
                    iconUrl = freshModel.iconUrl,
                    providerSlug = freshModel.providerSlug,
                    imageGenerationMethod = freshModel.imageGenerationMethod,
                    reasoningBehavior = savedModel.reasoningBehavior ?: freshModel.reasoningBehavior,
                )
            } else {
                resolveProviderModel(modelMetadataResolver, currentProvider, savedModel)
            }
        }
        return currentProvider.copyProvider(models = updatedModels)
    }

    fun reloadApiModels() {
        if (isReloadingModels) return
        if (!providerSetting.canFetchApiModels()) return
        scope.launch {
            isReloadingModels = true
            reloadModelsError = null
            var retryAttempt = 0
            do {
                runCatching {
                    withContext(Dispatchers.IO) {
                        providerManager.getProviderByType(providerSetting)
                            .listModels(providerSetting)
                            .sortedBy { it.modelId }
                            .toList()
                    }.map { model ->
                        resolveProviderModel(modelMetadataResolver, providerSetting, model)
                    }
                }.onSuccess { freshModels ->
                    if (freshModels.isNotEmpty()) {
                        ApiModelListCache.put(apiModelCacheKey, freshModels)
                        modelList = freshModels
                        val updatedProvider = syncFreshModelMetadata(freshModels, providerSetting)
                        if (updatedProvider != providerSetting) {
                            onUpdateProvider(updatedProvider)
                        }
                    }
                }.onFailure { error ->
                    reloadModelsError = error.message ?: error::class.simpleName
                    isReloadingModels = false
                    return@launch
                }

                if (modelList.isEmpty()) {
                    retryAttempt++
                    delay((retryAttempt * 1_500L).coerceAtMost(10_000L))
                }
            } while (modelList.isEmpty())
            isReloadingModels = false
        }
    }
    
    // State for swipe neighbor tracking
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    
    
    val canDelete = true
    
    // Reset neighborsUnlocked when offset returns to 0
    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
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
            state = lazyListState
        ) {


            // 模型列表
            itemsIndexed(providerSetting.models, key = { _, item -> item.id }) { index, item ->
                val position = when {
                    providerSetting.models.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == providerSetting.models.lastIndex -> ItemPosition.LAST
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
                    state = reorderableLazyListState,
                    key = item.id
                ) { isDragging ->

                    androidx.compose.runtime.key(canDelete) {
                        ModelCard(
                            model = item,
                            position = position,
                            canDelete = canDelete,
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
                                onUpdateProvider(providerSetting.delModel(item))
                            },
                            onEdit = { editedModel ->
                                onUpdateProvider(providerSetting.editModel(editedModel))
                            },
                            parentProvider = providerSetting,
                            dragHandle = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                                        },
                                        onDragStopped = {
                                            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Thud)
                                        }
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier
                                .graphicsLayer {
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
            
            // Empty state for saved models
            if (providerSetting.models.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight(0.8f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.setting_provider_page_no_models),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.setting_provider_page_add_models_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
        // Bottom fade gradient
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
        
        // Stacked FABs for adding models
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .offset(y = -ScreenOffset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Model picker FAB (gray, like lorebook toggle button)
            ModelPickerFab(
                models = modelList,
                selectedModels = providerSetting.models,
                isLoading = isReloadingModels,
                reloadError = reloadModelsError,
                onReload = ::reloadApiModels,
                onAddModel = {
                    onUpdateProvider(providerSetting.addModel(it))
                },
                onRemoveModel = {
                    onUpdateProvider(providerSetting.delModel(it))
                },
                onAddModels = { models ->
                    var updated = providerSetting
                    models.forEach { model ->
                        updated = updated.addModel(model)
                    }
                    onUpdateProvider(updated)
                },
                onRemoveModels = { models ->
                    var updated = providerSetting
                    models.forEach { model ->
                        updated = updated.delModel(model)
                    }
                    onUpdateProvider(updated)
                },
                parentProvider = providerSetting,
                onInstallModel = null,
            )
            
            AddNewModelFab(
                onAddModel = {
                    onUpdateProvider(providerSetting.addModel(it))
                },
                parentProvider = providerSetting
            )
        }
    }
}



@Composable
private fun LocalHuggingFaceInstallFab(
    isDownloading: Boolean,
    onInstallUrl: (String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()

    FloatingActionButton(
        onClick = {
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
            showSheet = true
        },
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.local_llm_install_url_action))
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
                Text(
                    text = stringResource(R.string.local_llm_install_url_action),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.local_llm_install_url_label)) },
                    supportingText = { Text(stringResource(R.string.local_llm_install_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = { showSheet = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onInstallUrl(url)
                            url = ""
                            showSheet = false
                        },
                        enabled = url.isNotBlank() && !isDownloading,
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
private fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null
) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val providerManager = koinInject<ProviderManager>()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var isProbingCapabilities by remember(model.id, parentProvider?.id) { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val extension = iconFileExtension(context, uri)
            val copiedUri = withContext(Dispatchers.IO) {
                ImageUtils.copyImageToInternalStorage(
                    context = context,
                    sourceUri = uri,
                    fileName = "model_icon_${model.id}.$extension",
                )
            }
            copiedUri?.let { iconUri ->
                onModelChange(model.copy(customIconUri = iconUri.toString()))
            }
        }
    }

    fun setModelId(id: String) {
        onModelChange(model.copy(modelId = id, canonicalModelId = null))
    }

    Column {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                },
                text = { Text(stringResource(R.string.setting_provider_page_basic_settings)) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                },
                text = { Text(stringResource(R.string.setting_provider_page_advanced_settings)) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    // 基本设置页面
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = model.modelId,
                            onValueChange = {
                                if (!isEdit) {
                                    setModelId(it.trim())
                                }
                            },
                            label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                if (!isEdit) {
                                    Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
                                }
                            },
                            enabled = !isEdit,
                            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                        )

                        // Display name with catalog icon preview
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CustomIconSelector(
                                customIconUri = model.customIconUri,
                                onPickFile = {
                                    imagePickerLauncher.launch(arrayOf("image/*", "image/svg+xml"))
                                },
                                onPickLobeHubIcon = { slug ->
                                    onModelChange(model.copy(customIconUri = lobeHubIconUri(slug)))
                                },
                                onReset = {
                                    onModelChange(model.copy(customIconUri = null))
                                },
                            ) { iconModifier ->
                                ModelIcon(
                                    model = model,
                                    provider = parentProvider,
                                    modifier = iconModifier,
                                )
                            }
                            OutlinedTextField(
                                value = model.displayName,
                                onValueChange = {
                                    onModelChange(model.copy(displayName = it))
                                },
                                label = { Text(stringResource(if (isEdit) R.string.setting_provider_page_model_name else R.string.setting_provider_page_model_display_name)) },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    if (!isEdit) {
                                        Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
                                    }
                                },
                                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                            )
                        }

                        ModelTypeSelector(
                            selectedType = model.type,
                            onTypeSelected = {
                                onModelChange(model.copy(type = it))
                            }
                        )

                        if (model.type == ModelType.STT) {
                            Spacer(modifier = Modifier.height(16.dp))
                            ModelSttOptionsForm(
                                sttOptions = model.sttOptions,
                                onUpdate = {
                                    onModelChange(model.copy(sttOptions = it))
                                }
                            )
                        }


                        if (model.type == ModelType.CHAT && parentProvider != null) {
                            ModelCapabilityProbeButton(
                                enabled = model.modelId.isNotBlank(),
                                isLoading = isProbingCapabilities,
                                onClick = {
                                    scope.launch {
                                        isProbingCapabilities = true
                                        runCatching {
                                            probeModelCapabilities(
                                                providerManager = providerManager,
                                                provider = parentProvider,
                                                model = model,
                                            )
                                        }.onSuccess { probedCapabilities ->
                                            if (probedCapabilities == null) {
                                                toaster.show(
                                                    context.getString(R.string.setting_provider_page_probe_capabilities_no_data),
                                                    type = ToastType.Info,
                                                )
                                            } else {
                                                val updatedModel = model.copy(
                                                    inputModalities = probedCapabilities.inputModalities,
                                                    outputModalities = probedCapabilities.outputModalities,
                                                    abilities = probedCapabilities.abilities,
                                                )
                                                if (
                                                    updatedModel.inputModalities == model.inputModalities &&
                                                    updatedModel.outputModalities == model.outputModalities &&
                                                    updatedModel.abilities == model.abilities
                                                ) {
                                                    toaster.show(
                                                        context.getString(R.string.setting_provider_page_probe_capabilities_unchanged),
                                                        type = ToastType.Info,
                                                    )
                                                } else {
                                                    onModelChange(updatedModel)
                                                    toaster.show(
                                                        context.getString(R.string.setting_provider_page_probe_capabilities_success),
                                                        type = ToastType.Success,
                                                    )
                                                }
                                            }
                                        }.onFailure { error ->
                                            toaster.show(
                                                context.getString(
                                                    R.string.setting_provider_page_probe_capabilities_error,
                                                    error.message ?: context.getString(R.string.backup_page_unknown_error),
                                                ),
                                                type = ToastType.Error,
                                            )
                                        }
                                        isProbingCapabilities = false
                                    }
                                },
                            )
                        }

                        // Image Generation Method selector (only for IMAGE type)
                        if (model.type == ModelType.IMAGE) {
                            ImageGenerationMethodSelector(
                                selectedMethod = model.imageGenerationMethod,
                                onMethodSelected = {
                                    onModelChange(model.copy(imageGenerationMethod = it))
                                },
                                supportsImageInput = model.inputModalities.contains(Modality.IMAGE),
                                onImageInputChanged = { supportsImage ->
                                    val newInputModalities = if (supportsImage) {
                                        model.inputModalities + Modality.IMAGE
                                    } else {
                                        model.inputModalities - Modality.IMAGE
                                    }
                                    onModelChange(model.copy(inputModalities = newInputModalities))
                                }
                            )
                        }

                        ModelModalitySelector(
                            model = model,
                            inputModalities = model.inputModalities,
                            onUpdateInputModalities = {
                                onModelChange(model.copy(inputModalities = it))
                            },
                            outputModalities = model.outputModalities,
                            onUpdateOutputModalities = {
                                onModelChange(model.copy(outputModalities = it))
                            }
                        )

                        if (model.type == ModelType.CHAT) {
                            ModalAbilitySelector(
                                abilities = model.abilities,
                                onUpdateAbilities = {
                                    onModelChange(model.copy(abilities = it))
                                }
                            )
                        }
                    }
                }

                1 -> {
                    // 高级设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderOverrideSettings(
                            providerOverride = model.providerOverwrite,
                            onUpdateProviderOverride = { providerOverride ->
                                onModelChange(model.copy(providerOverwrite = providerOverride))
                            },
                            parentProvider = parentProvider
                        )

                        CustomHeaders(
                            headers = model.customHeaders,
                            onUpdate = { headers ->
                                onModelChange(model.copy(customHeaders = headers))
                            }
                        )

                        CustomBodies(
                            customBodies = model.customBodies,
                            onUpdate = { bodies ->
                                onModelChange(model.copy(customBodies = bodies))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPickerFab(
    models: List<Model>,
    selectedModels: List<Model>,
    isLoading: Boolean,
    reloadError: String?,
    onReload: () -> Unit,
    onAddModel: (Model) -> Unit,
    onRemoveModel: (Model) -> Unit,
    onAddModels: (List<Model>) -> Unit,
    onRemoveModels: (List<Model>) -> Unit,
    parentProvider: ProviderSetting,
    onInstallModel: ((Model) -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val modelMetadataResolver = koinInject<ModelMetadataResolver>()
    
    FloatingActionButton(
        onClick = { 
            showPicker = true
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Tick)
            if (models.isEmpty() && !isLoading) {
                onReload()
            }
        },
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Icon(
            Icons.Rounded.Widgets,
            contentDescription = stringResource(R.string.setting_provider_page_add_from_list)
        )
    }
    
    if (showPicker) {
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            var filterText by remember { mutableStateOf("") }
            val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
            val resolvedModels = remember(models, parentProvider) {
                models.map { model ->
                    resolveProviderModel(modelMetadataResolver, parentProvider, model)
                }
            }
            val filteredModels = resolvedModels.fastFilter {
                if (filterKeywords.isEmpty()) {
                    true
                } else {
                    filterKeywords.all { keyword ->
                        it.modelId.contains(keyword, ignoreCase = true) ||
                        it.displayName.contains(keyword, ignoreCase = true)
                    }
                }
            }
            val allFilteredSelected = filteredModels.isNotEmpty() && filteredModels.all { model ->
                selectedModels.any { selected -> modelsReferToSameApiModel(selected, model) }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                            onClick = {
                                haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                                if (allFilteredSelected) {
                                    val modelsToRemove = filteredModels.mapNotNull { model ->
                                        selectedModels.firstOrNull { selected -> modelsReferToSameApiModel(selected, model) }
                                    }
                                    if (modelsToRemove.isNotEmpty()) {
                                        onRemoveModels(modelsToRemove)
                                    }
                                } else {
                                    val modelsToAdd = filteredModels.filter { model ->
                                        !selectedModels.any { selected -> modelsReferToSameApiModel(selected, model) }
                                    }
                                    if (modelsToAdd.isNotEmpty()) {
                                        onAddModels(modelsToAdd)
                                    }
                                }
                            },
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text(stringResource(if (allFilteredSelected) R.string.deselect_all else R.string.select_all))
                        }
                        if (isLoading) {
                            LinearWavyProgressIndicator(modifier = Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = {
                                haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                                onReload()
                            },
                            enabled = !isLoading,
                            modifier = Modifier.height(40.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.setting_provider_page_reload_models))
                        }
                }
                reloadError?.let { error ->
                        Text(
                            text = stringResource(R.string.setting_provider_page_reload_models_error, error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredModels.isEmpty()) {
                        item {
                            val hasApiKey = when (parentProvider) {
                                is ProviderSetting.OpenAI -> parentProvider.apiKey.isNotBlank()
                                is ProviderSetting.Google -> parentProvider.apiKey.isNotBlank()
                                is ProviderSetting.Claude -> parentProvider.apiKey.isNotBlank()
                                is ProviderSetting.ComfyUI -> parentProvider.workflowJson.isNotBlank()
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        if (hasApiKey) R.string.setting_provider_page_no_models_with_api_key
                                        else R.string.setting_provider_page_no_models_no_api_key
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    items(filteredModels) { model ->
                        val selectedModel = selectedModels.firstOrNull { selected -> modelsReferToSameApiModel(selected, model) }
                        val isSelected = selectedModel != null
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.98f else 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                            label = "api_model_card_scale",
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
                                    haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                                    if (isSelected) {
                                        onRemoveModel(selectedModel ?: model)
                                    } else {
                                        onAddModel(model)
                                    }
                                },
                            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            ) {
                                ModelIcon(
                                    model = model,
                                    provider = parentProvider,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = model.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        ModelTypeTag(model = model)
                                        ModelModalityTag(model = model)
                                        ModelAbilityTag(model = model)
                                    }
                                }
                                ModelSelectionCircle(selected = isSelected)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.setting_provider_page_filter_example)) },
                    singleLine = true,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField,
                )
            }
        }
    }
}

@Composable
private fun AddNewModelFab(
    onAddModel: (Model) -> Unit,
    parentProvider: ProviderSetting
) {
    val dialogState = useEditState<Model> { onAddModel(it) }
    val scope = rememberCoroutineScope()
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val modelMetadataResolver = koinInject<ModelMetadataResolver>()
    
    FloatingActionButton(
        onClick = { 
            dialogState.open(Model())
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
        },
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
    ) {
        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.setting_provider_page_add_model))
    }
    
    if (dialogState.isEditing) {
        dialogState.currentState?.let { modelState ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                dialogState.dismiss()
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
                        .fillMaxHeight(0.95f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_add_model),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ModelSettingsForm(
                            model = modelState,
                            onModelChange = { dialogState.currentState = it },
                            isEdit = false,
                            parentProvider = parentProvider
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        OutlinedButton(
                            onClick = {
                                dialogState.dismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                if (modelState.modelId.isNotBlank()) {
                                    dialogState.currentState = modelMetadataResolver.applyToModel(
                                        model = modelState,
                                        providerHint = parentProvider,
                                        options = ModelResolutionOptions(
                                            preserveDisplayName = true,
                                            preserveExistingCapabilities = true,
                                            preserveExistingType = modelState.type != ModelType.CHAT,
                                        ),
                                    )
                                    dialogState.confirm()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.setting_provider_page_add))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelectionCircle(selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "model_selection_circle_scale",
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
private fun ModelPicker(
    models: List<Model>,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onModelsSelected: (List<Model>) -> Unit = {},
    onModelsDeselected: (List<Model>) -> Unit = {},
    parentProvider: ProviderSetting
) {
    var showModal by remember { mutableStateOf(false) }
    val modelMetadataResolver = koinInject<ModelMetadataResolver>()
    if (showModal) {
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showModal = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            )
        ) {
            var filterText by remember { mutableStateOf("") }
            val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
            val filteredModels = models.fastFilter {
                if (filterKeywords.isEmpty()) {
                    true
                } else {
                    filterKeywords.all { keyword ->
                        it.modelId.contains(keyword, ignoreCase = true) ||
                            it.displayName.contains(keyword, ignoreCase = true)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Select All / Deselect All - show only one based on selection state
                val allFilteredSelected = filteredModels.isNotEmpty() && filteredModels.all { model ->
                    selectedModels.any { selected -> modelsReferToSameApiModel(selected, model) }
                }
                
                if (allFilteredSelected) {
                    // All filtered models are selected, show Deselect All
                    TextButton(onClick = {
                        val modelsToRemove = filteredModels.mapNotNull { model ->
                            selectedModels.firstOrNull { selected -> modelsReferToSameApiModel(selected, model) }
                        }
                        if (modelsToRemove.isNotEmpty()) {
                            onModelsDeselected(modelsToRemove)
                        }
                    }) {
                        Text(stringResource(R.string.deselect_all))
                    }
                } else {
                    // Not all selected, show Select All
                    TextButton(onClick = {
                        val modelsToAdd = filteredModels.filter { model ->
                            !selectedModels.any { selected -> modelsReferToSameApiModel(selected, model) }
                        }.map { model ->
                            resolveProviderModel(modelMetadataResolver, parentProvider, model)
                        }
                        if (modelsToAdd.isNotEmpty()) {
                            onModelsSelected(modelsToAdd)
                        }
                    }) {
                        Text(stringResource(R.string.select_all))
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    // Empty state for API model list
                    if (models.isEmpty()) {
                        item {
                            // Check if provider has an API key
                            val hasApiKey = when (parentProvider) {
                                is ProviderSetting.OpenAI -> parentProvider.apiKey.isNotBlank()
                                is ProviderSetting.Google -> parentProvider.apiKey.isNotBlank()
                                is ProviderSetting.Claude -> parentProvider.apiKey.isNotBlank()
                                is ProviderSetting.ComfyUI -> parentProvider.workflowJson.isNotBlank()
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        if (hasApiKey) R.string.setting_provider_page_no_models_with_api_key
                                        else R.string.setting_provider_page_no_models_no_api_key
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    items(filteredModels) {
                        Card(
                            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            ) {
                                ModelIcon(
                                    model = it,
                                    provider = parentProvider,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(
                                        4.dp
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = it.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = it.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        val modelMeta = remember(it) {
                                            resolveProviderModel(modelMetadataResolver, parentProvider, it)
                                        }
                                        ModelTypeTag(
                                            model = modelMeta,
                                        )
                                        ModelModalityTag(
                                            model = modelMeta,
                                        )
                                        ModelAbilityTag(
                                            model = modelMeta,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val selectedModel = selectedModels.firstOrNull { model -> modelsReferToSameApiModel(model, it) }
                                        if (selectedModel != null) {
                                            // 从selectedModels中计算出要删除的model，因为删除需要id匹配，而不是ModelId
                                            onModelDeselected(selectedModel)
                                        } else {
                                            onModelSelected(resolveProviderModel(modelMetadataResolver, parentProvider, it))
                                        }
                                    }
                                ) {
                                    ModelSelectionCircle(
                                        selected = selectedModels.any { model -> modelsReferToSameApiModel(model, it) }
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = filterText,
                    onValueChange = {
                        filterText = it
                    },
                    label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.setting_provider_page_filter_example))
                    },
                    singleLine = true,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField,
                )
            }
        }
    }
    BadgedBox(
        badge = {
            if (models.isNotEmpty()) {
                Badge {
                    Text(models.size.toString())
                }
            }
        }
    ) {
        IconButton(
            onClick = {
                showModal = true
            }
        ) {
            Icon(Icons.Rounded.Widgets, null)
        }
    }
}

private data class ProbedModelCapabilities(
    val inputModalities: List<Modality>,
    val outputModalities: List<Modality>,
    val abilities: List<ModelAbility>,
)

private const val CAPABILITY_PROBE_TOOL_NAME = "lastchat_capability_probe_tool"
private const val CAPABILITY_PROBE_IMAGE_DATA_URI =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF9sAAAAASUVORK5CYII="

private suspend fun probeModelCapabilities(
    providerManager: ProviderManager,
    provider: ProviderSetting,
    model: Model,
): ProbedModelCapabilities? {
    return when (provider) {
        is ProviderSetting.OpenAI -> probeModelCapabilities(
            providerInstance = providerManager.getProviderByType(provider),
            provider = provider,
            model = model,
        )

        is ProviderSetting.Google -> probeModelCapabilities(
            providerInstance = providerManager.getProviderByType(provider),
            provider = provider,
            model = model,
        )

        is ProviderSetting.Claude -> probeModelCapabilities(
            providerInstance = providerManager.getProviderByType(provider),
            provider = provider,
            model = model,
        )

        is ProviderSetting.ComfyUI -> null
    }
}

private suspend fun <T : ProviderSetting> probeModelCapabilities(
    providerInstance: Provider<T>,
    provider: T,
    model: Model,
): ProbedModelCapabilities? {
    val apiModel = runCatching { providerInstance.listModels(provider).findExactModel(model.modelId) }
        .getOrNull()

    ensureModelResponds(
        providerInstance = providerInstance,
        provider = provider,
        model = model,
    )

    val supportsVisionInput = apiModel?.inputModalities?.contains(Modality.IMAGE) == true ||
        runCatching {
            probeVisionInputSupport(
                providerInstance = providerInstance,
                provider = provider,
                model = model,
            )
        }.getOrDefault(false)

    val supportsToolCalling = apiModel?.abilities?.contains(ModelAbility.TOOL) == true ||
        runCatching {
            probeToolSupport(
                providerInstance = providerInstance,
                provider = provider,
                model = model,
            )
        }.getOrDefault(false)

    val supportsReasoning = apiModel?.abilities?.contains(ModelAbility.REASONING) == true ||
        runCatching {
            probeReasoningSupport(
                providerInstance = providerInstance,
                provider = provider,
                model = model,
            )
        }.getOrDefault(false)

    val hasExternalSignal = apiModel != null || supportsVisionInput || supportsToolCalling || supportsReasoning
    if (!hasExternalSignal) {
        return null
    }

    val inputModalities = linkedSetOf<Modality>().apply {
        addAll(model.inputModalities.ifEmpty { listOf(Modality.TEXT) })
        add(Modality.TEXT)
        addAll(apiModel?.inputModalities.orEmpty())
        if (supportsVisionInput) {
            add(Modality.IMAGE)
        }
    }.toList()

    val outputModalities = linkedSetOf<Modality>().apply {
        addAll(model.outputModalities.ifEmpty { listOf(Modality.TEXT) })
        add(Modality.TEXT)
        addAll(apiModel?.outputModalities.orEmpty())
    }.toList()

    val abilities = linkedSetOf<ModelAbility>().apply {
        addAll(model.abilities)
        addAll(apiModel?.abilities.orEmpty())
        if (supportsToolCalling) {
            add(ModelAbility.TOOL)
        }
        if (supportsReasoning) {
            add(ModelAbility.REASONING)
        }
    }.toList()

    return ProbedModelCapabilities(
        inputModalities = inputModalities,
        outputModalities = outputModalities,
        abilities = abilities,
    )
}

private suspend fun <T : ProviderSetting> ensureModelResponds(
    providerInstance: Provider<T>,
    provider: T,
    model: Model,
) {
    providerInstance.generateText(
        providerSetting = provider,
        messages = listOf(UIMessage.user("Reply with OK only.")),
        params = TextGenerationParams(
            model = model.copy(abilities = emptyList()),
            maxTokens = 8,
            thinkingBudget = 0,
            customHeaders = model.customHeaders,
            customBody = model.customBodies,
        ),
    )
}

private suspend fun <T : ProviderSetting> probeToolSupport(
    providerInstance: Provider<T>,
    provider: T,
    model: Model,
): Boolean {
    val response = providerInstance.generateText(
        providerSetting = provider,
        messages = listOf(
            UIMessage.system("You are testing tool support. Call the provided tool immediately and do not answer with plain text."),
            UIMessage.user("Call the capability probe tool now."),
        ),
        params = TextGenerationParams(
            model = model.copy(
                abilities = (model.abilities + ModelAbility.TOOL).distinct(),
            ),
            maxTokens = 32,
            tools = listOf(
                Tool(
                    name = CAPABILITY_PROBE_TOOL_NAME,
                    description = "Simple capability probe tool.",
                    parameters = {
                        InputSchema.Obj(
                            properties = JsonObject(emptyMap()),
                            required = emptyList(),
                        )
                    },
                    execute = { JsonNull },
                )
            ),
            thinkingBudget = 0,
            customHeaders = model.customHeaders,
            customBody = model.customBodies + buildToolProbeCustomBodies(provider),
        ),
    )

    return response.primaryMessage()
        ?.parts
        ?.filterIsInstance<UIMessagePart.ToolCall>()
        ?.any { it.toolName == CAPABILITY_PROBE_TOOL_NAME }
        ?: false
}

private suspend fun <T : ProviderSetting> probeReasoningSupport(
    providerInstance: Provider<T>,
    provider: T,
    model: Model,
): Boolean {
    val response = providerInstance.generateText(
        providerSetting = provider,
        messages = listOf(UIMessage.user("Reply with the single word OK.")),
        params = TextGenerationParams(
            model = model.copy(
                abilities = (model.abilities + ModelAbility.REASONING).distinct(),
            ),
            maxTokens = 32,
            thinkingBudget = ReasoningLevel.LOW.budgetTokens,
            customHeaders = model.customHeaders,
            customBody = model.customBodies,
        ),
    )

    return response.primaryMessage()
        ?.parts
        ?.any { part ->
            part is UIMessagePart.Reasoning && part.reasoning.isNotBlank()
        }
        ?: false
}

private suspend fun <T : ProviderSetting> probeVisionInputSupport(
    providerInstance: Provider<T>,
    provider: T,
    model: Model,
): Boolean {
    providerInstance.generateText(
        providerSetting = provider,
        messages = listOf(
            UIMessage(
                role = me.rerere.ai.core.MessageRole.USER,
                parts = listOf(
                    UIMessagePart.Text("Reply with OK only."),
                    UIMessagePart.Image(CAPABILITY_PROBE_IMAGE_DATA_URI),
                ),
            )
        ),
        params = TextGenerationParams(
            model = model,
            maxTokens = 8,
            thinkingBudget = 0,
            customHeaders = model.customHeaders,
            customBody = model.customBodies,
        ),
    )

    return true
}

private fun List<Model>.findExactModel(modelId: String): Model? {
    return firstOrNull { it.modelId == modelId }
        ?: firstOrNull { it.modelId.equals(modelId, ignoreCase = true) }
}

private fun MessageChunk.primaryMessage() = choices.firstOrNull()?.message ?: choices.firstOrNull()?.delta

private fun buildToolProbeCustomBodies(provider: ProviderSetting): List<CustomBody> {
    return when (provider) {
        is ProviderSetting.OpenAI -> listOf(
            CustomBody(
                key = "tool_choice",
                value = JsonPrimitive("required"),
            )
        )

        is ProviderSetting.Claude -> listOf(
            CustomBody(
                key = "tool_choice",
                value = buildJsonObject {
                    put("type", "any")
                },
            )
        )

        is ProviderSetting.Google -> listOf(
            CustomBody(
                key = "toolConfig",
                value = buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "ANY")
                    })
                },
            )
        )

        is ProviderSetting.ComfyUI -> emptyList()
    }
}

@Composable
private fun ModelCapabilityProbeButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && !isLoading && isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "probeCapabilityScale",
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = {
                haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                onClick()
            },
            enabled = enabled && !isLoading,
            shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.NetworkCheck,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        if (isLoading) {
                            R.string.setting_provider_page_probe_capabilities_loading
                        } else {
                            R.string.setting_provider_page_probe_capabilities
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        if (isLoading) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.setting_provider_page_probe_capabilities_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_model_type),
        style = MaterialTheme.typography.titleSmall
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        ModelType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, ModelType.entries.size),
                label = {
                    Text(
                        text = stringResource(
                            when (type) {
                                ModelType.CHAT -> R.string.setting_provider_page_chat_model
                                ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                                ModelType.IMAGE -> R.string.setting_provider_page_image_model
                                ModelType.STT -> R.string.setting_provider_page_stt_model
                            }
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = 14.sp,
                            stepSize = 1.sp
                        )
                    )
                },
                selected = selectedType == type,
                onClick = { onTypeSelected(type) }
            )
        }
    }
}

@Composable
private fun ImageGenerationMethodSelector(
    selectedMethod: ImageGenerationMethod?,
    onMethodSelected: (ImageGenerationMethod) -> Unit,
    supportsImageInput: Boolean = false,
    onImageInputChanged: (Boolean) -> Unit = {}
) {
    Text(
        stringResource(R.string.setting_provider_page_image_method),
        style = MaterialTheme.typography.titleSmall
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        ImageGenerationMethod.entries.forEachIndexed { index, method ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, ImageGenerationMethod.entries.size),
                label = {
                    Text(
                        text = stringResource(
                            when (method) {
                                ImageGenerationMethod.DIFFUSION -> R.string.setting_provider_page_image_method_diffusion
                                ImageGenerationMethod.MULTIMODAL -> R.string.setting_provider_page_image_method_multimodal
                            }
                        )
                    )
                },
                selected = selectedMethod == method,
                onClick = { onMethodSelected(method) }
            )
        }
    }

    // Image input toggle (for image-to-image generation)
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.setting_provider_page_image_input),
            style = MaterialTheme.typography.bodyMedium
        )
        HapticSwitch(
            checked = supportsImageInput,
            onCheckedChange = onImageInputChanged
        )
    }
}

@Composable
private fun ModelModalitySelector(
    model: Model,
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit
) {
    if (model.type == ModelType.CHAT) {
        val selectableInputModalities = Modality.entries.filter { it != Modality.AUDIO }
        val selectableOutputModalities = Modality.entries.filter { it != Modality.AUDIO }

        Text(
            stringResource(R.string.setting_provider_page_input_modality),
            style = MaterialTheme.typography.titleSmall
        )
        MultiChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            selectableInputModalities.forEachIndexed { index, modality ->
                SegmentedButton(
                    checked = modality in inputModalities,
                    shape = SegmentedButtonDefaults.itemShape(index, selectableInputModalities.size),
                    onCheckedChange = {
                        if (it) {
                            onUpdateInputModalities(inputModalities + modality)
                        } else {
                            onUpdateInputModalities(inputModalities - modality)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(
                            when (modality) {
                                Modality.TEXT -> R.string.setting_provider_page_text
                                Modality.IMAGE -> R.string.setting_provider_page_image
                                Modality.AUDIO -> R.string.setting_provider_page_audio
                            }
                        )
                    )
                }
            }
        }

        Text(
            stringResource(R.string.setting_provider_page_output_modality),
            style = MaterialTheme.typography.titleSmall
        )
        MultiChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            selectableOutputModalities.forEachIndexed { index, modality ->
                SegmentedButton(
                    checked = modality in outputModalities,
                    shape = SegmentedButtonDefaults.itemShape(index, selectableOutputModalities.size),
                    onCheckedChange = {
                        if (it) {
                            onUpdateOutputModalities(outputModalities + modality)
                        } else {
                            onUpdateOutputModalities(outputModalities - modality)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(
                            when (modality) {
                                Modality.TEXT -> R.string.setting_provider_page_text
                                Modality.IMAGE -> R.string.setting_provider_page_image
                                Modality.AUDIO -> R.string.setting_provider_page_audio
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_abilities),
        style = MaterialTheme.typography.titleSmall
    )
    MultiChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ModelAbility.entries.forEachIndexed { index, ability ->
            SegmentedButton(
                checked = ability in abilities,
                shape = SegmentedButtonDefaults.itemShape(index, ModelAbility.entries.size),
                onCheckedChange = {
                    if (it) {
                        onUpdateAbilities(abilities + ability)
                    } else {
                        onUpdateAbilities(abilities - ability)
                    }
                },
                label = {
                    Text(
                        text = stringResource(
                            when (ability) {
                                ModelAbility.TOOL -> R.string.setting_provider_page_tool
                                ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun LocalLiteRtModelIdentitySheet(
    model: Model,
    onModelChange: (Model) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    parentProvider: ProviderSetting,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val extension = iconFileExtension(context, uri)
            val copiedUri = withContext(Dispatchers.IO) {
                ImageUtils.copyImageToInternalStorage(
                    context = context,
                    sourceUri = uri,
                    fileName = "model_icon_${model.id}.$extension",
                )
            }
            copiedUri?.let { iconUri ->
                onModelChange(model.copy(customIconUri = iconUri.toString()))
            }
        }
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Rounded.Close, null)
                }
                Text(
                    text = stringResource(R.string.local_llm_edit_identity_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CustomIconSelector(
                    customIconUri = model.customIconUri,
                    onPickFile = {
                        imagePickerLauncher.launch(arrayOf("image/*", "image/svg+xml"))
                    },
                    onPickLobeHubIcon = { slug ->
                        onModelChange(model.copy(customIconUri = lobeHubIconUri(slug)))
                    },
                    onReset = {
                        onModelChange(model.copy(customIconUri = null))
                    },
                ) { iconModifier ->
                    ModelIcon(
                        model = model,
                        provider = parentProvider,
                        modifier = iconModifier,
                    )
                }
                OutlinedTextField(
                    value = model.displayName,
                    onValueChange = { onModelChange(model.copy(displayName = it)) },
                    label = { Text(stringResource(R.string.setting_provider_page_model_display_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = model.displayName.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: Model,
    position: ItemPosition,
    canDelete: Boolean,
    neighborOffset: Float,
    onDragProgress: (Float, Boolean) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit,
    parentProvider: ProviderSetting,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogState = useEditState<Model> {
        onEdit(it)
    }
    val scope = rememberCoroutineScope()


    if (dialogState.isEditing) {
        dialogState.currentState?.let { editingModel ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = null,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    dialogState.dismiss()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.Rounded.Close, null)
                        }
                        Text(
                            text = stringResource(R.string.setting_provider_page_edit_model),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ModelSettingsForm(
                            model = editingModel,
                            onModelChange = { dialogState.currentState = it },
                            isEdit = true,
                            parentProvider = parentProvider
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        OutlinedButton(
                            onClick = {
                                dialogState.dismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                if (editingModel.displayName.isNotBlank()) {
                                    dialogState.confirm()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }

    PhysicsSwipeToDelete(
        position = position,
        deleteEnabled = canDelete,
        neighborOffset = neighborOffset,
        onDragProgress = onDragProgress,
        onDragEnd = onDragEnd,
        onDelete = onDelete,
        modifier = modifier.fillMaxWidth()
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .background(
                    color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) 
                        MaterialTheme.colorScheme.surfaceContainerLow 
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .clickable {
                    dialogState.open(model.copy())
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelIcon(
                model = model,
                provider = parentProvider,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (model.providerOverwrite != null) {
                        Tag(type = TagType.INFO) {
                            Text(
                                model.providerOverwrite?.let { it::class.simpleName } ?: model.providerOverwrite?.name
                                ?: "ProviderOverwrite"
                            )
                        }
                    }
                    ModelTypeTag(model = model)
                    ModelModalityTag(model = model)
                    ModelAbilityTag(model = model)
                }
            }
            dragHandle()
        }
    }
}

@Composable
private fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_page_built_in_tools),
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = stringResource(R.string.setting_page_built_in_tools_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val availableTools = listOf(
            BuiltInTools.Search to Pair(
                stringResource(R.string.setting_page_built_in_tools_search),
                stringResource(R.string.setting_page_built_in_tools_search_desc)
            ),
            BuiltInTools.UrlContext to Pair(
                stringResource(R.string.setting_page_built_in_tools_url_context),
                stringResource(R.string.setting_page_built_in_tools_url_context_desc)
            )
        )

        availableTools.forEach { (tool, info) ->
            val (title, description) = info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HapticSwitch(
                        checked = tool in tools,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onUpdateTools(tools + tool)
                            } else {
                                onUpdateTools(tools - tool)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderOverrideSettings(
    providerOverride: ProviderSetting?,
    onUpdateProviderOverride: (ProviderSetting?) -> Unit,
    parentProvider: ProviderSetting?
) {
    var showProviderConfig by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_provider_page_provider_override),
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = stringResource(R.string.setting_provider_page_provider_override_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (providerOverride != null) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProviderIcon(
                            provider = providerOverride,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${providerOverride.name} (Override)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editingProvider = providerOverride
                                showProviderConfig = true
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.a11y_edit_provider_override)
                            )
                        }
                        IconButton(
                            onClick = {
                                onUpdateProviderOverride(null)
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.a11y_remove_provider_override)
                            )
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    editingProvider = parentProvider?.copyProvider(
                        id = Uuid.random(),
                        builtIn = false,
                        models = emptyList(), // 这里必须设置为空，不然会导致循环依赖JSON
                    )
                    showProviderConfig = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.setting_provider_page_add_provider_override))
            }
        }

        // Provider configuration modal
        if (showProviderConfig && editingProvider != null) {
            ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                onDismissRequest = {
                    showProviderConfig = false
                    editingProvider = null
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                var internalProvider by remember(editingProvider) { mutableStateOf(editingProvider!!) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_configure_provider_override),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderConfigure(
                            provider = internalProvider,
                            onEdit = { internalProvider = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                onUpdateProviderOverride(internalProvider)
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.setting_provider_page_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSttOptionsForm(
    sttOptions: me.rerere.ai.provider.SttOptions?,
    onUpdate: (me.rerere.ai.provider.SttOptions) -> Unit
) {
    val options = sttOptions ?: me.rerere.ai.provider.SttOptions()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = options.language,
            onValueChange = { onUpdate(options.copy(language = it)) },
            label = { Text(stringResource(R.string.setting_provider_page_stt_language)) },
            supportingText = { Text(stringResource(R.string.setting_provider_page_stt_language_desc)) },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
        OutlinedTextField(
            value = options.prompt,
            onValueChange = { onUpdate(options.copy(prompt = it)) },
            label = { Text(stringResource(R.string.setting_provider_page_stt_prompt)) },
            supportingText = { Text(stringResource(R.string.setting_provider_page_stt_prompt_desc)) },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
        OutlinedTextField(
            value = options.responseFormat,
            onValueChange = { onUpdate(options.copy(responseFormat = it)) },
            label = { Text(stringResource(R.string.setting_provider_page_stt_response_format)) },
            supportingText = { Text(stringResource(R.string.setting_provider_page_stt_response_format_desc)) },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}
