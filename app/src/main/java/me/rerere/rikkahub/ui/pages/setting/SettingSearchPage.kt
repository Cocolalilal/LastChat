package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.utils.plus
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.reflect.full.primaryConstructor
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics

@Composable
fun SettingSearchPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
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

                    IconButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    searchServices = listOf(SearchServiceOptions.BingLocalOptions()) + settings.searchServices
                                )
                            )
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.setting_page_search_add_provider)
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
        
        // State for swipe neighbor tracking
        var draggingIndex by remember { mutableStateOf(-1) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var isUnlocked by remember { mutableStateOf(false) }
        var neighborsUnlocked by remember { mutableStateOf(false) }
        
        // State for reorder ripple effect
        var reorderDropServiceId by remember { mutableStateOf<kotlin.uuid.Uuid?>(null) }
        var reorderDropTrigger by remember { mutableStateOf(0) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        // Check if delete is allowed (more than 1 service)
        val canDelete = settings.searchServices.size > 1
        
        // Reset neighborsUnlocked when offset returns to 0
        if (dragOffset == 0f && neighborsUnlocked) {
            neighborsUnlocked = false
        }
        
        // Ripple animation config
        val ripplePushDp = 10.dp
        val ripplePushPx = with(density) { ripplePushDp.toPx() }
        
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
                
                // Ripple animation
                val rippleOffset = remember { androidx.compose.animation.core.Animatable(0f) }
                androidx.compose.runtime.LaunchedEffect(reorderDropTrigger) {
                    if (reorderDropTrigger > 0 && reorderDropServiceId != null && reorderDropServiceId != service.id) {
                        val dropIndex = settings.searchServices.indexOfFirst { it.id == reorderDropServiceId }
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
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.5f,
                                        stiffness = 400f
                                    )
                                )
                            }
                        }
                    }
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
                                .offset { androidx.compose.ui.unit.IntOffset(0, rippleOffset.value.toInt()) }
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                        ) {
                        SearchProviderCardContent(
                            service = service,
                            haptics = haptics,
                            onUpdateService = { updatedService ->
                                val newServices = settings.searchServices.toMutableList()
                                newServices[index] = updatedService
                                vm.updateSettings(
                                    settings.copy(
                                        searchServices = newServices
                                    )
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
                                            reorderDropServiceId = service.id
                                            reorderDropTrigger++
                                        }
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = null
                                    )
                                }
                            }
                        )  // end SearchProviderCardContent
                        }  // end PhysicsSwipeToDelete content
                    }  // key(canDelete)
                }  // ReorderableItem
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
}


@Composable
private fun SearchProviderCardContent(
    service: SearchServiceOptions,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics,
    onUpdateService: (SearchServiceOptions) -> Unit,
    dragHandle: @Composable () -> Unit
) {
    var options by remember(service) {
        mutableStateOf(service)
    }
    var expand by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .animateContentSize(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                )
            )
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                haptics.perform(HapticPattern.Pop)
                expand = !expand
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Select(
                options = SearchServiceOptions.TYPES.keys.toList(),
                selectedOption = options::class,
                optionToString = { SearchServiceOptions.TYPES[it] ?: "[Unknown]" },
                onOptionSelected = {
                    options = it.primaryConstructor!!.callBy(mapOf())
                    onUpdateService(options)
                },
                optionLeading = {
                    AutoAIIcon(
                        name = SearchServiceOptions.TYPES[it] ?: it.simpleName ?: "unknown",
                        modifier = Modifier.size(24.dp)
                    )
                },
                leading = {
                    AutoAIIcon(
                        name = SearchServiceOptions.TYPES[options::class] ?: "unknown",
                        modifier = Modifier.size(24.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    expand = !expand
                }
            ) {
                Icon(
                    imageVector = if (expand) Icons.Rounded.Close else Icons.Rounded.Edit,
                    contentDescription = if (expand) "Hide details" else "Show details"
                )
            }
            
            dragHandle()
        }

        SearchAbilityTagLine(options = options)

        AnimatedVisibility(expand) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (options) {
                    is SearchServiceOptions.TavilyOptions -> {
                        TavilyOptions(options as SearchServiceOptions.TavilyOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.ExaOptions -> {
                        ExaOptions(options as SearchServiceOptions.ExaOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.ZhipuOptions -> {
                        ZhipuOptions(options as SearchServiceOptions.ZhipuOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.SearXNGOptions -> {
                        SearXNGOptions(options as SearchServiceOptions.SearXNGOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.LinkUpOptions -> {
                        SearchLinkUpOptions(options as SearchServiceOptions.LinkUpOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.BraveOptions -> {
                        BraveOptions(options as SearchServiceOptions.BraveOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.MetasoOptions -> {
                        MetasoOptions(options as SearchServiceOptions.MetasoOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.OllamaOptions -> {
                        OllamaOptions(options as SearchServiceOptions.OllamaOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.PerplexityOptions -> {
                        PerplexityOptions(options as SearchServiceOptions.PerplexityOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.BingLocalOptions -> {}

                    is SearchServiceOptions.FirecrawlOptions -> {
                        FirecrawlOptions(options as SearchServiceOptions.FirecrawlOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.JinaOptions -> {
                        JinaOptions(options as SearchServiceOptions.JinaOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.BochaOptions -> {
                        BochaOptions(options as SearchServiceOptions.BochaOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }

                    is SearchServiceOptions.NanoGPTOptions -> {
                        NanoGPTOptions(options as SearchServiceOptions.NanoGPTOptions) {
                            options = it
                            onUpdateService(options)
                        }
                    }
                }

                ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                    SearchService.getService(options).Description()
                }
            }
        }
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
    
    androidx.compose.material3.AlertDialog(
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
            androidx.compose.material3.TextButton(
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
            Switch(
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
            Text("Depth")
        }
    ) {
        OutlinedTextField(
            value = options.depth,
            onValueChange = {
                onUpdateOptions(
                    options.copy(
                        depth = it
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("standard") }
        )
    }
}
