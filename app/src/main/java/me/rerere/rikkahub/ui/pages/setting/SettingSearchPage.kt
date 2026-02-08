package me.rerere.rikkahub.ui.pages.setting

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
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
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import kotlin.reflect.full.primaryConstructor

/**
 * Data class representing a search service preset for quick setup
 */
data class SearchServicePreset(
    val name: String,
    val description: String,
    val optionsClass: kotlin.reflect.KClass<out SearchServiceOptions>,
    val hasScraping: Boolean = false
)

/**
 * List of search service presets
 */
val SEARCH_SERVICE_PRESETS = listOf(
    SearchServicePreset(
        name = "Bing",
        description = "Free local Bing search, no API key needed",
        optionsClass = SearchServiceOptions.BingLocalOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Perplexity",
        description = "AI-powered search with citations",
        optionsClass = SearchServiceOptions.PerplexityOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Ollama",
        description = "Search powered by Ollama",
        optionsClass = SearchServiceOptions.OllamaOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Brave",
        description = "Privacy-focused web search",
        optionsClass = SearchServiceOptions.BraveOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "NanoGPT",
        description = "AI web search with scraping and stealth mode",
        optionsClass = SearchServiceOptions.NanoGPTOptions::class,
        hasScraping = true
    ),
    SearchServicePreset(
        name = "Tavily",
        description = "AI-optimized search with scraping support",
        optionsClass = SearchServiceOptions.TavilyOptions::class,
        hasScraping = true
    ),
    SearchServicePreset(
        name = "Exa",
        description = "Neural search engine for quality results",
        optionsClass = SearchServiceOptions.ExaOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "Jina",
        description = "Search and web scraping API",
        optionsClass = SearchServiceOptions.JinaOptions::class,
        hasScraping = true
    ),
    SearchServicePreset(
        name = "Firecrawl",
        description = "Web scraping and crawling API",
        optionsClass = SearchServiceOptions.FirecrawlOptions::class,
        hasScraping = true
    ),
    SearchServicePreset(
        name = "SearXNG",
        description = "Self-hosted metasearch engine",
        optionsClass = SearchServiceOptions.SearXNGOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "LinkUp",
        description = "Link aggregation and search",
        optionsClass = SearchServiceOptions.LinkUpOptions::class,
        hasScraping = true
    ),
    SearchServicePreset(
        name = "智谱",
        description = "Zhipu AI web search",
        optionsClass = SearchServiceOptions.ZhipuOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "秘塔",
        description = "Metaso Chinese search engine",
        optionsClass = SearchServiceOptions.MetasoOptions::class,
        hasScraping = false
    ),
    SearchServicePreset(
        name = "博查",
        description = "Bocha search with summary",
        optionsClass = SearchServiceOptions.BochaOptions::class,
        hasScraping = false
    ),
)


@Composable
fun SettingSearchPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    // State for editing a service
    var editingService by remember { mutableStateOf<SearchServiceOptions?>(null) }
    
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
                },
                actions = {
                    var showCommonOptions by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            showCommonOptions = true
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.setting_page_search_common_options)
                        )
                    }

                    AddSearchServiceButton(
                        enableHaptics = settings.displaySetting.enableUIHaptics
                    ) { newService ->
                        vm.updateSettings(
                            settings.copy(
                                searchServices = listOf(newService) + settings.searchServices
                            )
                        )
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
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
        val density = LocalDensity.current
        
        // State for swipe neighbor tracking
        var draggingIndex by remember { mutableStateOf(-1) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var isUnlocked by remember { mutableStateOf(false) }
        var neighborsUnlocked by remember { mutableStateOf(false) }
        
        
        // Check if delete is allowed (more than 1 service)
        val canDelete = settings.searchServices.size > 1
        
        // Reset neighborsUnlocked when offset returns to 0
        if (dragOffset == 0f && neighborsUnlocked) {
            neighborsUnlocked = false
        }
        

        
        // Delete confirmation state
        var showDeleteDialog by remember { mutableStateOf(false) }
        var serviceToDelete by remember { mutableStateOf<SearchServiceOptions?>(null) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = it + PaddingValues(16.dp),
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
                    // Key on canDelete to force complete PhysicsSwipeToDelete recreation when list size changes
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
                                serviceToDelete = service
                                showDeleteDialog = true
                            },
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                        ) { _ ->
                            SearchServiceItemContent(
                                service = service,
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
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog && serviceToDelete != null) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    serviceToDelete = null
                },
                title = { Text(stringResource(R.string.confirm_delete)) },
                text = { Text("Are you sure you want to delete this search service?") },
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
    
    // Edit Search Service Bottom Sheet
    editingService?.let { service ->
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var currentService by remember(service) { mutableStateOf(service) }

        ModalBottomSheet(
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
                    text = "Edit ${SearchServiceOptions.TYPES[service::class] ?: "Search Service"}",
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
                            is SearchServiceOptions.BingLocalOptions -> {
                                // No configuration needed for Bing
                                Text(
                                    text = "Bing search doesn't require any configuration.",
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
                            SearchService.getService(currentService).Description()
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
private fun AddSearchServiceButton(
    enableHaptics: Boolean,
    onAdd: (SearchServiceOptions) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    IconButton(
        onClick = {
            searchQuery = ""
            showBottomSheet = true
        }
    ) {
        Icon(Icons.Rounded.Add, stringResource(R.string.setting_page_search_add_provider))
    }

    val haptics = rememberPremiumHaptics(enabled = enableHaptics)

    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        
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
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    } else null
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Filter presets based on search
                val filteredPresets = remember(searchQuery) {
                    if (searchQuery.isBlank()) {
                        SEARCH_SERVICE_PRESETS
                    } else {
                        SEARCH_SERVICE_PRESETS.filter { preset ->
                            preset.name.contains(searchQuery, ignoreCase = true) ||
                            preset.description.contains(searchQuery, ignoreCase = true)
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
                                    val newService = preset.optionsClass.primaryConstructor!!.callBy(mapOf())
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
                                    AutoAIIcon(
                                        name = preset.name,
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
                                    // Show capability tags
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (preset.hasScraping) {
                                            Tag(type = TagType.INFO) {
                                                Text("Scrape")
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
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val serviceName = SearchServiceOptions.TYPES[service::class] ?: "Unknown"
    val hasScraping = SearchService.getService(service).scrapingParameters != null
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutoAIIcon(
            name = serviceName,
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
                                    if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Depth")
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CommonOptionsDialog(
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
            Text("API URL")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Engines")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Language")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Username")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Password")
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
            modifier = Modifier.fillMaxWidth()
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Depth")
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Max Tokens / Page")
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Summary")
        },
        description = {
            Text("Enable summary generation")
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
            Text("API Key")
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
            modifier = Modifier.fillMaxWidth()
        )
    }

    FormItem(
        label = {
            Text("Search Depth")
        },
        description = {
            Text("Standard is faster/cheaper, Deep is more comprehensive")
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
            Text("Output Type")
        },
        description = {
            Text("searchResults: multiple sources, sourcedAnswer: synthesized answer")
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
                    Text(if (output == "searchResults") "Results" else "Answer")
                }
            }
        }
    }

    FormItem(
        label = {
            Text("Include Images")
        },
        description = {
            Text("Include images in search results")
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
            Text("Stealth Mode")
        },
        description = {
            Text("Use stealth mode for web scraping")
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
