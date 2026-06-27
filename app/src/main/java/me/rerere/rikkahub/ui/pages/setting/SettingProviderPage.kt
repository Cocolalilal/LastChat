package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
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
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.components.ui.AppToasterState
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.ProviderViewMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.ProviderIcon
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.ProviderShareQrContent
import me.rerere.rikkahub.ui.components.ui.ProviderShareQrPart
import me.rerere.rikkahub.ui.components.ui.computeAIIconByName
import me.rerere.rikkahub.ui.components.ui.decodeProviderSettingParts
import me.rerere.rikkahub.ui.components.ui.getProviderSlugFromName
import me.rerere.rikkahub.ui.components.ui.parseProviderShareQrContent
import me.rerere.rikkahub.ui.components.ui.searchLobeHubIcon
import me.rerere.rikkahub.ui.components.ui.lobeHubIconUri
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.setting.components.FALLBACK_PROVIDER_PRESETS
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.toProviderSetting
import me.rerere.rikkahub.ui.pages.setting.components.toProviderPresets
import me.rerere.rikkahub.ui.pages.setting.components.withSpecialProviderPresets
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.tts.provider.withDefaultVoices
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import me.rerere.rikkahub.data.model.Tag as DataTag
import kotlin.uuid.Uuid


@Composable
fun SettingProviderPage(
    initialTab: ProvidersTab = ProvidersTab.Models,
    vm: SettingVM = koinViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalogSnapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val useWideLayout = LocalSettingsWideLayout.current
    val pager = rememberPagerState(initialPage = initialTab.ordinal) { ProvidersTab.entries.size }
    val currentTab = ProvidersTab.entries[pager.currentPage]
    
    var showSearchCommonOptions by remember { mutableStateOf(false) }
    var showTtsFilterSettings by remember { mutableStateOf(false) }
    val providerPresets = remember(catalogSnapshot) {
        (catalogSnapshot?.toProviderPresets()?.takeIf { it.isNotEmpty() }
            ?: FALLBACK_PROVIDER_PRESETS
        ).withSpecialProviderPresets()
    }
    
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
    val httpClient = koinInject<PlatformHttpClient>()
    fun addProvider(provider: ProviderSetting) {
        val providerToAdd = provider.withUniqueId(settings.providers)
        vm.updateSettings(
            settings.copy(
                providers = listOf(providerToAdd) + settings.providers
            )
        )
        
        // Asynchronously check if we can query LobeHub for a monochrome icon
        if (providerToAdd.customIconUri.isNullOrBlank()) {
            val providerName = providerToAdd.name
            val hasLocalIcon = computeAIIconByName(providerName) != null || 
                    getProviderSlugFromName(providerName) != null
            if (!hasLocalIcon) {
                scope.launch {
                    val slug = searchLobeHubIcon(httpClient, providerName)
                    if (slug != null) {
                        val latestSettings = vm.settings.value
                        val updatedProviders = latestSettings.providers.map { p ->
                            if (p.id == providerToAdd.id) {
                                p.copyProvider(customIconUri = lobeHubIconUri(slug))
                            } else p
                        }
                        vm.updateSettings(latestSettings.copy(providers = updatedProviders))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(currentTab.titleRes),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        bottomBar = {
            ProvidersBottomBar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    scope.launch {
                        pager.animateScrollToPage(tab.ordinal)
                    }
                }
            ) {
                if (useWideLayout) {
                    when (currentTab) {
                        ProvidersTab.Models -> ImportProviderButton(
                            asFab = true,
                            enableHaptics = settings.displaySetting.enableUIHaptics,
                            onAdd = { addProvider(it) }
                        )

                        ProvidersTab.Search,
                        ProvidersTab.Tts -> FloatingActionButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                if (currentTab == ProvidersTab.Search) {
                                    showSearchCommonOptions = true
                                } else {
                                    showTtsFilterSettings = true
                                }
                            },
                            shape = AppShapes.CardLarge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = when (currentTab) {
                                    ProvidersTab.Search -> stringResource(R.string.setting_page_search_common_options)
                                    else -> stringResource(R.string.setting_tts_settings_title)
                                }
                            )
                        }
                    }
                }
                when (currentTab) {
                    ProvidersTab.Models -> AddButton(
                        enableHaptics = settings.displaySetting.enableUIHaptics,
                        providerPresets = providerPresets,
                        asFab = true,
                        onAdd = { addProvider(it) }
                    )

                    ProvidersTab.Search -> AddSearchServiceButton(
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

                    ProvidersTab.Tts -> AddTTSProviderButton(
                        catalogSnapshot = catalogSnapshot,
                        enableHaptics = settings.displaySetting.enableUIHaptics,
                        asFab = true
                    ) { newProvider ->
                        val providerToAdd = newProvider.withDefaultVoices()
                        vm.updateSettings(
                            settings.copy(
                                ttsProviders = listOf(providerToAdd) + settings.ttsProviders
                            )
                        )
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !useWideLayout,
            ) { page ->
            when (ProvidersTab.entries[page]) {
                ProvidersTab.Models -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
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
                settings = settings,
                haptics = haptics,
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                searchQuery = searchQuery,
                onNavigateToDetail = { provider ->
                    navController.navigate(Screen.SettingProviderDetail(providerId = provider.id.toString()))
                },
                onDeleteRequest = { provider ->
                    providerToDelete = provider
                    showDeleteDialog = true
                },
                onReorder = { fromProvider, toProvider ->
                    val reorderedProviders = settings.providers.toMutableList()
                    val from = reorderedProviders.indexOfFirst { it.id == fromProvider.id }
                    val to = reorderedProviders.indexOfFirst { it.id == toProvider.id }
                    if (from >= 0 && to >= 0 && from != to) {
                        reorderedProviders.add(to, reorderedProviders.removeAt(from))
                        vm.updateSettings(settings.copy(providers = reorderedProviders))
                    }
                },
                onAddProvider = { provider ->
                    addProvider(provider)
                },
                providerPresets = providerPresets
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
                    if (!useWideLayout) {
                        ProvidersSecondaryActionSlot(modifier = Modifier.fillMaxSize()) {
                            ImportProviderButton(
                                asFab = true,
                                enableHaptics = settings.displaySetting.enableUIHaptics,
                                onAdd = { addProvider(it) }
                            )
                        }
                    }
                }

                ProvidersTab.Search -> SearchProvidersContent(vm = vm, contentPadding = innerPadding)
                ProvidersTab.Tts -> TtsProvidersContent(vm = vm, contentPadding = innerPadding)
            }
        }
            AnimatedVisibility(
                visible = !useWideLayout && (currentTab == ProvidersTab.Search || currentTab == ProvidersTab.Tts),
                enter = slideInHorizontally(
                    animationSpec = tween(120),
                    initialOffsetX = { it }
                ) + fadeIn(animationSpec = tween(90)),
                exit = slideOutHorizontally(
                    animationSpec = tween(120),
                    targetOffsetX = { it }
                ) + fadeOut(animationSpec = tween(70))
            ) {
                ProvidersSecondaryActionSlot(modifier = Modifier.fillMaxSize()) {
                    FloatingActionButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            if (currentTab == ProvidersTab.Search) {
                                showSearchCommonOptions = true
                            } else {
                                showTtsFilterSettings = true
                            }
                        },
                        shape = AppShapes.CardLarge,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = when (currentTab) {
                                ProvidersTab.Search -> stringResource(R.string.setting_page_search_common_options)
                                else -> stringResource(R.string.setting_tts_settings_title)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSearchCommonOptions) {
        CommonOptionsDialog(
            settings = settings,
            onDismissRequest = { showSearchCommonOptions = false },
            onUpdate = { options ->
                vm.updateSettings(
                    settings.copy(
                        searchCommonOptions = options
                    )
                )
            }
        )
    }

    if (showTtsFilterSettings) {
        TtsTextFilterSettingsDialog(
            rules = settings.displaySetting.ttsTextFilterRules,
            onDismiss = { showTtsFilterSettings = false },
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
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            } else null
        )
        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = if (viewMode == ProviderViewMode.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                contentDescription = stringResource(R.string.a11y_toggle_view_mode)
            )
        }
    }
}

@Composable
private fun ProviderListView(
    providers: List<ProviderSetting>,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    searchQuery: String,
    providerPresets: List<me.rerere.rikkahub.ui.pages.setting.components.ProviderPreset>,
    onNavigateToDetail: (ProviderSetting) -> Unit,
    onDeleteRequest: (ProviderSetting) -> Unit,
    onReorder: (ProviderSetting, ProviderSetting) -> Unit,
    onAddProvider: (ProviderSetting) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromProvider = providers.getOrNull(from.index)
        val toProvider = providers.getOrNull(to.index)
        if (fromProvider != null && toProvider != null) {
            onReorder(fromProvider, toProvider)
        }
    }
    
    // State for swipe neighbor tracking
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    // Reset neighborsUnlocked when offset returns to 0
    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }
    
    // Check for matching preset when no providers found
    val matchingPreset = remember(searchQuery, providers) {
        if (providers.isEmpty() && searchQuery.isNotBlank()) {
            providerPresets.find { preset ->
                preset.name.contains(searchQuery, ignoreCase = true) ||
                preset.description.contains(searchQuery, ignoreCase = true)
            }
        } else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp) + PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            state = lazyListState,
        ) {
            // Show preset suggestion if no providers match but preset exists
            if (matchingPreset != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_no_providers_but_preset),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    
                    Surface(
                        onClick = {
                            val provider = matchingPreset.toProviderSetting()
                            onAddProvider(provider)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AutoAIIconWithUrl(
                                name = matchingPreset.name,
                                customIconUri = matchingPreset.customIconUri,
                                modifier = Modifier.size(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = matchingPreset.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = matchingPreset.description,
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
                

                ReorderableItem(
                    state = reorderableState,
                    key = provider.id
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
                            onDeleteRequest(provider)
                        },
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .fillMaxWidth()
                    ) { animatedShape ->
                        ProviderItemContent(
                            provider = provider,
                            animatedShape = animatedShape,
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
private fun ImportProviderButton(
    asFab: Boolean = false,
    enableHaptics: Boolean,
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics(enabled = enableHaptics)
    var showImportDialog by remember { mutableStateOf(false) }
    var importSession by remember { mutableStateOf<ProviderQrImportSession?>(null) }

    val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        handleQRResult(
            result = result,
            onAdd = onAdd,
            toaster = toaster,
            context = context,
            importSession = importSession,
            onImportSessionChange = { importSession = it },
            onSuccess = { haptics.perform(HapticPattern.Success) },
            onProgress = { haptics.perform(HapticPattern.Pop) },
            onError = { haptics.perform(HapticPattern.Error) }
        )
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            handleImageQRCode(
                uri = it,
                onAdd = onAdd,
                toaster = toaster,
                context = context,
                importSession = importSession,
                onImportSessionChange = { session -> importSession = session },
                onSuccess = { haptics.perform(HapticPattern.Success) },
                onProgress = { haptics.perform(HapticPattern.Pop) },
                onError = { haptics.perform(HapticPattern.Error) }
            )
        }
    }

    val openImportDialog = {
        haptics.perform(HapticPattern.Pop)
        showImportDialog = true
    }

    if (asFab) {
        FloatingActionButton(
            onClick = openImportDialog,
            shape = AppShapes.CardLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Icon(Icons.AutoMirrored.Rounded.Input, stringResource(R.string.import_label))
        }
    } else {
        IconButton(onClick = openImportDialog) {
            Icon(Icons.AutoMirrored.Rounded.Input, null)
        }
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
                                haptics.perform(HapticPattern.Pop)
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
                                haptics.perform(HapticPattern.Pop)
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
                    onClick = {
                        haptics.perform(HapticPattern.Cancel)
                        showImportDialog = false
                    },
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

    importSession?.let { session ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = stringResource(R.string.setting_provider_page_multi_qr_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.setting_provider_page_multi_qr_message,
                            session.scannedCount,
                            session.total
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.setting_provider_page_multi_qr_parts,
                            session.scannedIndexes
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        scanQrCodeLauncher.launch(null)
                    },
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.setting_provider_page_scan_next_qr))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            importSession = session.removeLastScannedPart()
                        },
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.setting_provider_page_back_step))
                    }
                    TextButton(
                        onClick = {
                            haptics.perform(HapticPattern.Cancel)
                            importSession = null
                        },
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

private fun ProviderSetting.withUniqueId(existingProviders: List<ProviderSetting>): ProviderSetting {
    if (existingProviders.none { it.id == id }) {
        return this
    }
    return copyProvider(id = Uuid.random())
}

private data class ProviderQrImportSession(
    val transferId: String,
    val total: Int,
    val parts: Map<Int, ProviderShareQrPart>,
    val scanOrder: List<Int>
) {
    val scannedCount: Int get() = parts.size
    val scannedIndexes: String get() = parts.keys.sorted().joinToString(", ")

    fun removeLastScannedPart(): ProviderQrImportSession? {
        val lastIndex = scanOrder.lastOrNull() ?: return null
        val nextParts = parts - lastIndex
        val nextScanOrder = scanOrder.dropLast(1)
        return if (nextParts.isEmpty()) {
            null
        } else {
            copy(parts = nextParts, scanOrder = nextScanOrder)
        }
    }
}

private fun handleQRResult(
    result: QRResult,
    onAdd: (ProviderSetting) -> Unit,
    toaster: AppToasterState,
    context: android.content.Context,
    importSession: ProviderQrImportSession?,
    onImportSessionChange: (ProviderQrImportSession?) -> Unit,
    onSuccess: () -> Unit,
    onProgress: () -> Unit,
    onError: () -> Unit
) {
    runCatching {
        when (result) {
            is QRResult.QRError -> {
                onError()
                toaster.show(
                    context.getString(
                        R.string.setting_provider_page_scan_error,
                        result
                    ), type = ToastType.Error
                )
            }

            QRResult.QRMissingPermission -> {
                onError()
                toaster.show(
                    context.getString(R.string.setting_provider_page_no_permission),
                    type = ToastType.Error
                )
            }

            is QRResult.QRSuccess -> {
                handleProviderShareQrValue(
                    value = result.content.rawValue ?: "",
                    onAdd = onAdd,
                    toaster = toaster,
                    context = context,
                    importSession = importSession,
                    onImportSessionChange = onImportSessionChange,
                    onSuccess = onSuccess,
                    onProgress = onProgress,
                    onError = onError
                )
            }

            QRResult.QRUserCanceled -> {}
        }
    }.onFailure { error ->
        onError()
        toaster.show(
            context.getString(R.string.setting_provider_page_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}

private fun handleImageQRCode(
    uri: Uri,
    onAdd: (ProviderSetting) -> Unit,
    toaster: AppToasterState,
    context: android.content.Context,
    importSession: ProviderQrImportSession?,
    onImportSessionChange: (ProviderQrImportSession?) -> Unit,
    onSuccess: () -> Unit,
    onProgress: () -> Unit,
    onError: () -> Unit
) {
    runCatching {
        val qrContent = ImageUtils.decodeQRCodeFromUri(context, uri)

        if (qrContent.isNullOrEmpty()) {
            onError()
            toaster.show(
                context.getString(R.string.setting_provider_page_no_qr_found),
                type = ToastType.Error
            )
            return
        }

        handleProviderShareQrValue(
            value = qrContent,
            onAdd = onAdd,
            toaster = toaster,
            context = context,
            importSession = importSession,
            onImportSessionChange = onImportSessionChange,
            onSuccess = onSuccess,
            onProgress = onProgress,
            onError = onError
        )
    }.onFailure { error ->
        onError()
        toaster.show(
            context.getString(R.string.setting_provider_page_image_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}

private fun handleProviderShareQrValue(
    value: String,
    onAdd: (ProviderSetting) -> Unit,
    toaster: AppToasterState,
    context: android.content.Context,
    importSession: ProviderQrImportSession?,
    onImportSessionChange: (ProviderQrImportSession?) -> Unit,
    onSuccess: () -> Unit,
    onProgress: () -> Unit,
    onError: () -> Unit
) {
    when (val content = parseProviderShareQrContent(value)) {
        is ProviderShareQrContent.Single -> {
            onImportSessionChange(null)
            onAdd(content.provider)
            onSuccess()
            toaster.show(
                context.getString(R.string.setting_provider_page_import_success),
                type = ToastType.Success
            )
        }

        is ProviderShareQrContent.Part -> {
            val part = content.part
            val currentSession = importSession ?: ProviderQrImportSession(
                transferId = part.transferId,
                total = part.total,
                parts = emptyMap(),
                scanOrder = emptyList()
            )

            if (currentSession.transferId != part.transferId || currentSession.total != part.total) {
                onError()
                toaster.show(
                    context.getString(R.string.setting_provider_page_multi_qr_wrong_transfer),
                    type = ToastType.Error
                )
                return
            }

            if (currentSession.parts[part.index]?.data == part.data) {
                onProgress()
                toaster.show(
                    context.getString(R.string.setting_provider_page_multi_qr_duplicate),
                    type = ToastType.Info
                )
                onImportSessionChange(currentSession)
                return
            }

            val updatedSession = currentSession.copy(
                parts = currentSession.parts + (part.index to part),
                scanOrder = currentSession.scanOrder + part.index
            )

            if (updatedSession.scannedCount == updatedSession.total) {
                val setting = decodeProviderSettingParts(updatedSession.parts.values)
                onImportSessionChange(null)
                onAdd(setting)
                onSuccess()
                toaster.show(
                    context.getString(R.string.setting_provider_page_import_success),
                    type = ToastType.Success
                )
            } else {
                onImportSessionChange(updatedSession)
                onProgress()
                toaster.show(
                    context.getString(
                        R.string.setting_provider_page_multi_qr_progress,
                        updatedSession.scannedCount,
                        updatedSession.total
                    ),
                    type = ToastType.Info
                )
            }
        }
    }
}


@Composable
private fun AddButton(
    enableHaptics: Boolean,
    providerPresets: List<me.rerere.rikkahub.ui.pages.setting.components.ProviderPreset>,
    asFab: Boolean = false,
    onAdd: (ProviderSetting) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCustomProviderDialog by remember { mutableStateOf(false) }
    
    // Custom provider dialog state
    val customDialogState = useEditState<ProviderSetting> {
        onAdd(it)
    }

    val haptics = rememberPremiumHaptics(enabled = enableHaptics)
    val openProviderSheet = {
        haptics.perform(HapticPattern.Pop)
        searchQuery = ""
        showBottomSheet = true
    }

    if (asFab) {
        FloatingActionButton(
            onClick = openProviderSheet,
            shape = AppShapes.CardLarge
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.add))
        }
    } else {
        IconButton(onClick = openProviderSheet) {
            Icon(Icons.Rounded.Add, stringResource(R.string.add))
        }
    }

    // Provider selection bottom sheet
    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    text = stringResource(R.string.setting_provider_page_choose_provider),
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
                val filteredPresets = remember(searchQuery, providerPresets) {
                    if (searchQuery.isBlank()) {
                        providerPresets
                    } else {
                        providerPresets.filter { preset ->
                            preset.name.contains(searchQuery, ignoreCase = true) ||
                            preset.description.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }
                
                CompositionLocalProvider(
                    LocalOverscrollFactory provides null
                ) {
                    val lazyListState = rememberLazyListState()
                    // Consume scroll events to prevent sheet from closing when scrolling
                    val nestedScrollConnection = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                // Only consume if we're not at the top and scrolling up
                                if (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0) {
                                    return Offset.Zero // Let the list handle it
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
                                customDialogState.open(ProviderSetting.OpenAI())
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
                    
                    // Provider presets
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
                                val provider = preset.toProviderSetting()
                                onAdd(provider)
                                showBottomSheet = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape,
                            color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
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
                                    customIconUri = preset.customIconUri,
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
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    }
                }
            }
        }
    }
    
    // Custom provider dialog (old behavior)
    if (customDialogState.isEditing) {
        AlertDialog(
            onDismissRequest = {
                customDialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.setting_provider_page_add_provider))
            },
            text = {
                customDialogState.currentState?.let {
                    ProviderConfigure(
                        provider = it,
                        showEnabledToggle = false,
                        onEdit = { newState ->
                            customDialogState.currentState = newState
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customDialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        customDialogState.dismiss()
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
    animatedShape: Shape,
    providerTags: List<DataTag>,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    dragHandle: @Composable () -> Unit,
    onClick: () -> Unit
) {
    // Define the normal card color (used for both enabled background and disabled border)
    val normalCardColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) 
        MaterialTheme.colorScheme.surfaceContainerLow 
    else 
        MaterialTheme.colorScheme.surfaceContainerHigh
    
    // Disabled cards: transparent background (black in dark mode) with outline
    val disabledBackground = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) 
        Color.Black 
    else 
        MaterialTheme.colorScheme.surface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(animatedShape)
            .then(
                if (!provider.enabled) {
                    Modifier
                        .background(disabledBackground, animatedShape)
                        .border(3.dp, normalCardColor, animatedShape)
                } else {
                    Modifier.background(normalCardColor)
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
        // Desaturation for disabled providers - use Paint with ColorFilter for grayscale
        // IMPORTANT: remember must be called unconditionally (outside of if/else)
        val saturationMatrix = remember { 
            android.graphics.ColorMatrix().apply { setSaturation(0f) } 
        }
        val colorFilter = remember(saturationMatrix) {
            android.graphics.ColorMatrixColorFilter(saturationMatrix)
        }
        val grayscalePaint = remember { 
            android.graphics.Paint().apply {
                this.colorFilter = colorFilter
            }
        }
        
        val grayscaleModifier = if (!provider.enabled) {
            Modifier
                .graphicsLayer { alpha = 0.99f } // Force offscreen buffer
                .drawWithContent {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.saveLayer(null, grayscalePaint)
                        drawContent()
                        canvas.nativeCanvas.restore()
                    }
                }
        } else {
            Modifier
        }
        
        ProviderIcon(
            provider = provider,
            modifier = Modifier
                .size(40.dp)
                .then(grayscaleModifier)
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
                    // Show disabled tag only for disabled providers (gray styling)
                    if (!provider.enabled) {
                        Tag(type = TagType.DEFAULT) {
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
                                        normalCardColor
                                    } else {
                                        disabledBackground
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





