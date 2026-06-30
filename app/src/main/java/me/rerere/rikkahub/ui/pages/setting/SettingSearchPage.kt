package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.searchProviderIconUri
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.plus
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import me.rerere.search.R as SearchR
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch

/**
 * Data class representing a search service preset for quick setup
 */
data class SearchServicePreset(
    val name: String,
    @StringRes val descriptionRes: Int,
    val createOptions: () -> SearchServiceOptions,
    val hasScraping: Boolean = false
)

/**
 * List of search service presets
 */
val SEARCH_SERVICE_PRESETS = listOf(
    SearchServicePreset(
        name = "Bing",
        descriptionRes = R.string.setting_search_preset_bing_desc,
        createOptions = { SearchServiceOptions.BingLocalOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Perplexity",
        descriptionRes = R.string.setting_search_preset_perplexity_desc,
        createOptions = { SearchServiceOptions.PerplexityOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Ollama",
        descriptionRes = R.string.setting_search_preset_ollama_desc,
        createOptions = { SearchServiceOptions.OllamaOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Brave",
        descriptionRes = R.string.setting_search_preset_brave_desc,
        createOptions = { SearchServiceOptions.BraveOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Grok",
        descriptionRes = R.string.setting_search_preset_grok_desc,
        createOptions = { SearchServiceOptions.GrokOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "NanoGPT",
        descriptionRes = R.string.setting_search_preset_nanogpt_desc,
        createOptions = { SearchServiceOptions.NanoGPTOptions() },
        hasScraping = true
    ),
    SearchServicePreset(
        name = "Tavily",
        descriptionRes = R.string.setting_search_preset_tavily_desc,
        createOptions = { SearchServiceOptions.TavilyOptions() },
        hasScraping = true
    ),
    SearchServicePreset(
        name = "Exa",
        descriptionRes = R.string.setting_search_preset_exa_desc,
        createOptions = { SearchServiceOptions.ExaOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Jina",
        descriptionRes = R.string.setting_search_preset_jina_desc,
        createOptions = { SearchServiceOptions.JinaOptions() },
        hasScraping = true
    ),
    SearchServicePreset(
        name = "Firecrawl",
        descriptionRes = R.string.setting_search_preset_firecrawl_desc,
        createOptions = { SearchServiceOptions.FirecrawlOptions() },
        hasScraping = true
    ),
    SearchServicePreset(
        name = "SearXNG",
        descriptionRes = R.string.setting_search_preset_searxng_desc,
        createOptions = { SearchServiceOptions.SearXNGOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "LinkUp",
        descriptionRes = R.string.setting_search_preset_linkup_desc,
        createOptions = { SearchServiceOptions.LinkUpOptions() },
        hasScraping = true
    ),
    SearchServicePreset(
        name = "智谱",
        descriptionRes = R.string.setting_search_preset_zhipu_desc,
        createOptions = { SearchServiceOptions.ZhipuOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "秘塔",
        descriptionRes = R.string.setting_search_preset_metaso_desc,
        createOptions = { SearchServiceOptions.MetasoOptions() },
        hasScraping = false
    ),
    SearchServicePreset(
        name = "博查",
        descriptionRes = R.string.setting_search_preset_bocha_desc,
        createOptions = { SearchServiceOptions.BochaOptions() },
        hasScraping = false
    ),
)

@Composable
private fun SearchServiceDescription(service: SearchServiceOptions) {
    val uriHandler = LocalUriHandler.current

    @Composable
    fun apiKeyButton(url: String) {
        TextButton(onClick = { uriHandler.openUri(url) }) {
            Text(stringResource(SearchR.string.click_to_get_api_key))
        }
    }

    when (service) {
        is SearchServiceOptions.BingLocalOptions -> Text(stringResource(SearchR.string.bing_desc))
        is SearchServiceOptions.SearXNGOptions -> {
            Text(stringResource(SearchR.string.searxng_desc_1))
            Text(stringResource(SearchR.string.searxng_desc_2))
        }
        is SearchServiceOptions.MetasoOptions -> {
            Text(buildAnnotatedString {
                append("秘塔搜索: ")
                withLink(LinkAnnotation.Url("https://metaso.cn/")) {
                    append("https://metaso.cn/")
                }
            })
        }
        is SearchServiceOptions.BochaOptions -> apiKeyButton("https://open.bochaai.com/")
        is SearchServiceOptions.BraveOptions -> apiKeyButton("https://api.search.brave.com/")
        is SearchServiceOptions.ExaOptions -> apiKeyButton("https://dashboard.exa.ai/api-keys")
        is SearchServiceOptions.FirecrawlOptions -> apiKeyButton("https://docs.firecrawl.dev/features/search")
        is SearchServiceOptions.GrokOptions -> apiKeyButton("https://console.x.ai/")
        is SearchServiceOptions.JinaOptions -> apiKeyButton("https://jina.ai/")
        is SearchServiceOptions.LinkUpOptions -> apiKeyButton("https://www.linkup.so/")
        is SearchServiceOptions.NanoGPTOptions -> apiKeyButton("https://nano-gpt.com/api")
        is SearchServiceOptions.OllamaOptions -> apiKeyButton("https://ollama.com/settings/keys")
        is SearchServiceOptions.PerplexityOptions -> apiKeyButton("https://www.perplexity.ai/settings/api")
        is SearchServiceOptions.TavilyOptions -> apiKeyButton("https://app.tavily.com/home")
        is SearchServiceOptions.ZhipuOptions -> apiKeyButton("https://bigmodel.cn/usercenter/proj-mgmt/apikeys")
    }
}


@Composable
fun SettingSearchPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalogSnapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    
    // State for editing a service
    var editingService by remember { mutableStateOf<SearchServiceOptions?>(null) }
    var showCommonOptions by remember { mutableStateOf(false) }
    
    // Move lazyListState outside for canScroll detection
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset = 0
        val fromIndex = from.index - offset
        val toIndex = to.index - offset

        if (fromIndex >= 0 && toIndex >= 0 && fromIndex < settings.searchServices.size && toIndex < settings.searchServices.size) {
            val newServices = settings.searchServices.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            vm.updateSettings(
                settings.copy(
                    searchServices = newServices
                )
            )
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_page_search_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        bottomBar = {
            ProvidersBottomBar(selectedTab = ProvidersTab.Search) {
                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showCommonOptions = true
                    },
                    shape = AppShapes.CardLarge,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.setting_page_search_common_options)
                    )
                }

                AddSearchServiceButton(
                    enableHaptics = settings.displaySetting.enableUIHaptics,
                    catalogSnapshot = catalogSnapshot,
                    asFab = true
                ) { newService ->
                    vm.updateSettings(
                        settings.copy(
                            searchServices = listOf(newService) + settings.searchServices
                        )
                    )
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        val density = LocalDensity.current
        
        // State for swipe neighbor tracking
        var draggingIndex by remember { mutableStateOf(-1) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var isUnlocked by remember { mutableStateOf(false) }
        var neighborsUnlocked by remember { mutableStateOf(false) }
        
        
        // Reset neighborsUnlocked when offset returns to 0
        if (dragOffset == 0f && neighborsUnlocked) {
            neighborsUnlocked = false
        }
        

        
        // Delete confirmation state
        var showDeleteDialog by remember { mutableStateOf(false) }
        var serviceToDelete by remember { mutableStateOf<SearchServiceOptions?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = it + PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                state = lazyListState
            ) {
                itemsIndexed(settings.searchServices, key = { _, service -> service.id }) { index, service ->
                val position = when {
                    settings.searchServices.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == settings.searchServices.lastIndex -> ItemPosition.LAST
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
                    key = service.id
                ) { isDragging ->
                    PhysicsSwipeToDelete(
                        position = position,
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
                            serviceToDelete = service
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .fillMaxWidth()
                    ) { _ ->
                        SearchServiceItemContent(
                            service = service,
                            catalogSnapshot = catalogSnapshot,
                            haptics = haptics,
                            onClick = {
                                editingService = service
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
        if (showDeleteDialog && serviceToDelete != null) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    serviceToDelete = null
                },
                title = { Text(stringResource(R.string.confirm_delete)) },
                text = { Text(stringResource(R.string.setting_search_delete_service)) },
                dismissButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        serviceToDelete = null
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        serviceToDelete?.let { svc ->
                            val idx = settings.searchServices.indexOfFirst { it.id == svc.id }
                            if (idx >= 0) {
                                val newServices = settings.searchServices.toMutableList()
                                newServices.removeAt(idx)
                                vm.updateSettings(settings.copy(searchServices = newServices))
                            }
                        }
                        showDeleteDialog = false
                        serviceToDelete = null
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        }
    }

    if (showCommonOptions) {
        CommonOptionsDialog(
            settings = settings,
            onDismissRequest = { showCommonOptions = false },
            onUpdate = { options ->
                vm.updateSettings(
                    settings.copy(
                        searchCommonOptions = options
                    )
                )
            }
        )
    }
    
    // Edit Search Service Bottom Sheet
    editingService?.let { service ->
        val context = LocalContext.current
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var currentService by remember(service) { mutableStateOf(service) }

        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = {
                editingService = null
            },
            sheetState = bottomSheetState,
            sheetGesturesEnabled = false,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            bottomSheetState.hide()
                            editingService = null
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
                // Header
                Text(
                    text = stringResource(
                        R.string.setting_search_edit_service,
                        SearchServiceOptions.TYPES[service::class]
                            ?: context.getString(R.string.setting_search_service_generic)
                    ),
                    style = MaterialTheme.typography.headlineSmall
                )
                
                // Configuration options based on service type
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        when (currentService) {
                            is SearchServiceOptions.TavilyOptions -> {
                                TavilyOptions(currentService as SearchServiceOptions.TavilyOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.ExaOptions -> {
                                ExaOptions(currentService as SearchServiceOptions.ExaOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.ZhipuOptions -> {
                                ZhipuOptions(currentService as SearchServiceOptions.ZhipuOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.SearXNGOptions -> {
                                SearXNGOptions(currentService as SearchServiceOptions.SearXNGOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.LinkUpOptions -> {
                                SearchLinkUpOptions(currentService as SearchServiceOptions.LinkUpOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.BraveOptions -> {
                                BraveOptions(currentService as SearchServiceOptions.BraveOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.MetasoOptions -> {
                                MetasoOptions(currentService as SearchServiceOptions.MetasoOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.OllamaOptions -> {
                                OllamaOptions(currentService as SearchServiceOptions.OllamaOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.PerplexityOptions -> {
                                PerplexityOptions(currentService as SearchServiceOptions.PerplexityOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.GrokOptions -> {
                                GrokOptions(currentService as SearchServiceOptions.GrokOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.BingLocalOptions -> {
                                // No configuration needed for Bing
                                Text(
                                    text = stringResource(R.string.setting_search_bing_no_config),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is SearchServiceOptions.FirecrawlOptions -> {
                                FirecrawlOptions(currentService as SearchServiceOptions.FirecrawlOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.JinaOptions -> {
                                JinaOptions(currentService as SearchServiceOptions.JinaOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.BochaOptions -> {
                                BochaOptions(currentService as SearchServiceOptions.BochaOptions) {
                                    currentService = it
                                }
                            }
                            is SearchServiceOptions.NanoGPTOptions -> {
                                NanoGPTOptions(currentService as SearchServiceOptions.NanoGPTOptions) {
                                    currentService = it
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Service description
                        ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                            SearchServiceDescription(currentService)
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            editingService = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    TextButton(
                        onClick = {
                            val newServices = settings.searchServices.map {
                                if (it.id == service.id) currentService else it
                            }
                            vm.updateSettings(settings.copy(searchServices = newServices))
                            editingService = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.chat_page_save))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchProvidersContent(
    vm: SettingVM = koinViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalogSnapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (from.index >= 0 && to.index >= 0 && from.index < settings.searchServices.size && to.index < settings.searchServices.size) {
            val newServices = settings.searchServices.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            vm.updateSettings(settings.copy(searchServices = newServices))
        }
    }

    var editingService by remember { mutableStateOf<SearchServiceOptions?>(null) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var serviceToDelete by remember { mutableStateOf<SearchServiceOptions?>(null) }

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
            state = lazyListState
        ) {
            itemsIndexed(settings.searchServices, key = { _, service -> service.id }) { index, service ->
                val position = when {
                    settings.searchServices.size == 1 -> ItemPosition.ONLY
                    index == 0 -> ItemPosition.FIRST
                    index == settings.searchServices.lastIndex -> ItemPosition.LAST
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
                    key = service.id
                ) { isDragging ->
                    PhysicsSwipeToDelete(
                        position = position,
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
                            serviceToDelete = service
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .fillMaxWidth()
                    ) { _ ->
                        SearchServiceItemContent(
                            service = service,
                            catalogSnapshot = catalogSnapshot,
                            haptics = haptics,
                            onClick = { editingService = service },
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

    if (showDeleteDialog && serviceToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                serviceToDelete = null
            },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.setting_search_delete_service)) },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    serviceToDelete = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    serviceToDelete?.let { svc ->
                        val idx = settings.searchServices.indexOfFirst { it.id == svc.id }
                        if (idx >= 0) {
                            val newServices = settings.searchServices.toMutableList()
                            newServices.removeAt(idx)
                            vm.updateSettings(settings.copy(searchServices = newServices))
                        }
                    }
                    showDeleteDialog = false
                    serviceToDelete = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    SearchServiceEditorSheet(
        service = editingService,
        settings = settings,
        onDismiss = { editingService = null },
        onSave = { original, updated ->
            val newServices = settings.searchServices.map {
                if (it.id == original.id) updated else it
            }
            vm.updateSettings(settings.copy(searchServices = newServices))
            editingService = null
        }
    )
}

@Composable
private fun SearchServiceEditorSheet(
    service: SearchServiceOptions?,
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (SearchServiceOptions, SearchServiceOptions) -> Unit
) {
    service ?: return
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var currentService by remember(service) { mutableStateOf(service) }

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
            Text(
                text = stringResource(
                    R.string.setting_search_edit_service,
                    SearchServiceOptions.TYPES[service::class]
                        ?: context.getString(R.string.setting_search_service_generic)
                ),
                style = MaterialTheme.typography.headlineSmall
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    when (currentService) {
                        is SearchServiceOptions.TavilyOptions -> TavilyOptions(currentService as SearchServiceOptions.TavilyOptions) { currentService = it }
                        is SearchServiceOptions.ExaOptions -> ExaOptions(currentService as SearchServiceOptions.ExaOptions) { currentService = it }
                        is SearchServiceOptions.ZhipuOptions -> ZhipuOptions(currentService as SearchServiceOptions.ZhipuOptions) { currentService = it }
                        is SearchServiceOptions.SearXNGOptions -> SearXNGOptions(currentService as SearchServiceOptions.SearXNGOptions) { currentService = it }
                        is SearchServiceOptions.LinkUpOptions -> SearchLinkUpOptions(currentService as SearchServiceOptions.LinkUpOptions) { currentService = it }
                        is SearchServiceOptions.BraveOptions -> BraveOptions(currentService as SearchServiceOptions.BraveOptions) { currentService = it }
                        is SearchServiceOptions.MetasoOptions -> MetasoOptions(currentService as SearchServiceOptions.MetasoOptions) { currentService = it }
                        is SearchServiceOptions.OllamaOptions -> OllamaOptions(currentService as SearchServiceOptions.OllamaOptions) { currentService = it }
                        is SearchServiceOptions.PerplexityOptions -> PerplexityOptions(currentService as SearchServiceOptions.PerplexityOptions) { currentService = it }
                        is SearchServiceOptions.GrokOptions -> GrokOptions(currentService as SearchServiceOptions.GrokOptions) { currentService = it }
                        is SearchServiceOptions.BingLocalOptions -> Text(
                            text = stringResource(R.string.setting_search_bing_no_config),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        is SearchServiceOptions.FirecrawlOptions -> FirecrawlOptions(currentService as SearchServiceOptions.FirecrawlOptions) { currentService = it }
                        is SearchServiceOptions.JinaOptions -> JinaOptions(currentService as SearchServiceOptions.JinaOptions) { currentService = it }
                        is SearchServiceOptions.BochaOptions -> BochaOptions(currentService as SearchServiceOptions.BochaOptions) { currentService = it }
                        is SearchServiceOptions.NanoGPTOptions -> NanoGPTOptions(currentService as SearchServiceOptions.NanoGPTOptions) { currentService = it }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                        SearchServiceDescription(currentService)
                    }
                }
            }

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
                    onClick = {
                        onSave(service, currentService)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            }
        }
    }
}

@Composable
internal fun AddSearchServiceButton(
    enableHaptics: Boolean,
    catalogSnapshot: ModelCatalogSnapshot?,
    asFab: Boolean = false,
    onAdd: (SearchServiceOptions) -> Unit
) {
    val context = LocalContext.current
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val haptics = rememberPremiumHaptics(enabled = enableHaptics)
    val openSearchProviderSheet = {
        haptics.perform(HapticPattern.Pop)
        searchQuery = ""
        showBottomSheet = true
    }

    if (asFab) {
        FloatingActionButton(
            onClick = openSearchProviderSheet,
            shape = AppShapes.CardLarge
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.setting_page_search_add_provider))
        }
    } else {
        IconButton(onClick = openSearchProviderSheet) {
            Icon(Icons.Rounded.Add, stringResource(R.string.setting_page_search_add_provider))
        }
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        
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
                    text = stringResource(R.string.setting_page_search_add_provider),
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
                
                // Filter presets based on search
                val filteredPresets = remember(searchQuery, context) {
                    if (searchQuery.isBlank()) {
                        SEARCH_SERVICE_PRESETS
                    } else {
                        SEARCH_SERVICE_PRESETS.filter { preset ->
                            val description = context.getString(preset.descriptionRes)
                            preset.name.contains(searchQuery, ignoreCase = true) ||
                            description.contains(searchQuery, ignoreCase = true)
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
                                    val newService = preset.createOptions()
                                    onAdd(newService)
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
                                        customIconUri = catalogSnapshot?.searchProviderIconUri(preset.name),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = context.getString(preset.descriptionRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    // Show capability tags
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (preset.hasScraping) {
                                            Tag(type = TagType.INFO) {
                                                Text(stringResource(R.string.search_ability_scrape))
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
private fun SearchServiceItemContent(
    service: SearchServiceOptions,
    catalogSnapshot: ModelCatalogSnapshot?,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val serviceName = SearchServiceOptions.TYPES[service::class] ?: stringResource(R.string.setting_search_unknown_provider)
    val hasScraping = SearchService.getService(service).scrapingParameters != null
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutoAIIconWithUrl(
            name = serviceName,
            customIconUri = catalogSnapshot?.searchProviderIconUri(serviceName),
            modifier = Modifier.size(40.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = serviceName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Tags row
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
                    Tag(type = TagType.DEFAULT) {
                        Text(stringResource(R.string.search_ability_search))
                    }
                    if (hasScraping) {
                        Tag(type = TagType.DEFAULT) {
                            Text(stringResource(R.string.search_ability_scrape))
                        }
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
                                    if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
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
fun SearchAbilityTagLine(
    modifier: Modifier = Modifier,
    options: SearchServiceOptions
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Tag(
            type = TagType.DEFAULT,
        ) {
            Text(stringResource(R.string.search_ability_search))
        }
        if (SearchService.getService(options).scrapingParameters != null) {
            Tag(
                type = TagType.DEFAULT,
            ) {
                Text(stringResource(R.string.search_ability_scrape))
            }
        }
    }
}

@Composable
private fun TavilyOptions(
    options: SearchServiceOptions.TavilyOptions,
    onUpdateOptions: (SearchServiceOptions.TavilyOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_depth))
        }
    ) {
        val depthOptions = listOf("basic", "advanced")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            depthOptions.forEachIndexed { index, depth ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = depthOptions.size),
                    onClick = {
                        onUpdateOptions(
                            options.copy(
                                depth = depth
                            )
                        )
                    },
                    selected = options.depth == depth
                ) {
                    Text(depth.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}

@Composable
private fun ExaOptions(
    options: SearchServiceOptions.ExaOptions,
    onUpdateOptions: (SearchServiceOptions.ExaOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}


@Composable
fun ZhipuOptions(
    options: SearchServiceOptions.ZhipuOptions,
    onUpdateOptions: (SearchServiceOptions.ZhipuOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
internal fun CommonOptionsDialog(
    settings: Settings,
    onDismissRequest: () -> Unit,
    onUpdate: (SearchCommonOptions) -> Unit
) {
    var commonOptions by remember(settings.searchCommonOptions) {
        mutableStateOf(settings.searchCommonOptions)
    }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.setting_page_search_common_options))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_page_search_result_size))
                    }
                ) {
                    OutlinedNumberInput(
                        value = commonOptions.resultSize,
                        onValueChange = {
                            commonOptions = commonOptions.copy(
                                resultSize = it
                            )
                            onUpdate(commonOptions)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun SearXNGOptions(
    options: SearchServiceOptions.SearXNGOptions,
    onUpdateOptions: (SearchServiceOptions.SearXNGOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_url))
        }
    ) {
        OutlinedTextField(
            value = options.url,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        url = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_engines))
        }
    ) {
        OutlinedTextField(
            value = options.engines,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        engines = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_language))
        }
    ) {
        OutlinedTextField(
            value = options.language,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        language = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_username))
        }
    ) {
        OutlinedTextField(
            value = options.username,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        username = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_password))
        }
    ) {
        OutlinedTextField(
            value = options.password,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        password = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
private fun SearchLinkUpOptions(
    options: SearchServiceOptions.LinkUpOptions,
    onUpdateOptions: (SearchServiceOptions.LinkUpOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_depth))
        }
    ) {
        val depthOptions = listOf("standard", "deep")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            depthOptions.forEachIndexed { index, depth ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = depthOptions.size),
                    onClick = {
                        onUpdateOptions(
                            options.copy(
                                depth = depth
                            )
                        )
                    },
                    selected = options.depth == depth
                ) {
                    Text(depth.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}

@Composable
private fun BraveOptions(
    options: SearchServiceOptions.BraveOptions,
    onUpdateOptions: (SearchServiceOptions.BraveOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}


@Composable
private fun MetasoOptions(
    options: SearchServiceOptions.MetasoOptions,
    onUpdateOptions: (SearchServiceOptions.MetasoOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
private fun OllamaOptions(
    options: SearchServiceOptions.OllamaOptions,
    onUpdateOptions: (SearchServiceOptions.OllamaOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
private fun PerplexityOptions(
    options: SearchServiceOptions.PerplexityOptions,
    onUpdateOptions: (SearchServiceOptions.PerplexityOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_max_tokens_per_page))
        }
    ) {
        OutlinedTextField(
            value = options.maxTokensPerPage?.takeIf { it > 0 }?.toString() ?: "",
            onValueChange = { value ->
                onUpdateOptions(
                    options.copy(
                        maxTokensPerPage = value.toIntOrNull()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
private fun GrokOptions(
    options: SearchServiceOptions.GrokOptions,
    onUpdateOptions: (SearchServiceOptions.GrokOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_model))
        },
        description = {
            Text(stringResource(R.string.search_grok_model_desc))
        }
    ) {
        OutlinedTextField(
            value = options.model,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        model = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
private fun FirecrawlOptions(
    options: SearchServiceOptions.FirecrawlOptions,
    onUpdateOptions: (SearchServiceOptions.FirecrawlOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
private fun JinaOptions(
    options: SearchServiceOptions.JinaOptions,
    onUpdateOptions: (SearchServiceOptions.JinaOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}

@Composable
private fun BochaOptions(
    options: SearchServiceOptions.BochaOptions,
    onUpdateOptions: (SearchServiceOptions.BochaOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_summary))
        },
        description = {
            Text(stringResource(R.string.search_field_summary_desc))
        },
        tail = {
            HapticSwitch(
                checked = options.summary,
                onCheckedChange = { checked ->
                    onUpdateOptions(
                        options.copy(
                            summary = checked
                        )
                    )
                }
            )
        }
    )
}

@Composable
private fun NanoGPTOptions(
    options: SearchServiceOptions.NanoGPTOptions,
    onUpdateOptions: (SearchServiceOptions.NanoGPTOptions) -> Unit
) {
    FormItem(
        label = {
            Text(stringResource(R.string.search_field_api_key))
        }
    ) {
        OutlinedTextField(
            value = options.apiKey,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        apiKey = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_search_depth))
        },
        description = {
            Text(stringResource(R.string.search_field_search_depth_desc))
        }
    ) {
        val depthOptions = listOf("standard", "deep")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            depthOptions.forEachIndexed { index, depth ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = depthOptions.size),
                    onClick = {
                        onUpdateOptions(
                            options.copy(
                                depth = depth
                            )
                        )
                    },
                    selected = options.depth == depth
                ) {
                    Text(depth.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_output_type))
        },
        description = {
            Text(stringResource(R.string.search_field_output_type_desc))
        }
    ) {
        val outputOptions = listOf("searchResults", "sourcedAnswer")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            outputOptions.forEachIndexed { index, output ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = outputOptions.size),
                    onClick = {
                        onUpdateOptions(
                            options.copy(
                                outputType = output
                            )
                        )
                    },
                    selected = options.outputType == output
                ) {
                    Text(if (output == "searchResults") stringResource(R.string.search_output_results) else stringResource(R.string.search_output_answer))
                }
            }
        }
    }

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_include_images))
        },
        description = {
            Text(stringResource(R.string.search_field_include_images_desc))
        },
        tail = {
            HapticSwitch(
                checked = options.includeImages,
                onCheckedChange = { checked ->
                    onUpdateOptions(
                        options.copy(
                            includeImages = checked
                        )
                    )
                }
            )
        }
    )

    FormItem(
        label = {
            Text(stringResource(R.string.search_field_stealth_mode))
        },
        description = {
            Text(stringResource(R.string.search_field_stealth_mode_desc))
        },
        tail = {
            HapticSwitch(
                checked = options.stealthMode,
                onCheckedChange = { checked ->
                    onUpdateOptions(
                        options.copy(
                            stealthMode = checked
                        )
                    )
                }
            )
        }
    )
}
