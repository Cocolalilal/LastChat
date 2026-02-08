package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CommentsDisabled
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import kotlinx.coroutines.launch
import me.rerere.ai.core.InputSchema
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingMcpPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mcpConfigs = settings.mcpServers
    val creationState = useEditState<McpServerConfig> {
        vm.updateSettings(
            settings.copy(
                mcpServers = mcpConfigs + it
            )
        )
    }
    val editState = useEditState<McpServerConfig> { newConfig ->
        vm.updateSettings(
            settings.copy(
                mcpServers = mcpConfigs.map {
                    if (it.id == newConfig.id) {
                        newConfig
                    } else {
                        it
                    }
                }
            ))
    }
    
    // Delete confirmation state - at function level so accessible by dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var mcpToDelete by remember { mutableStateOf<McpServerConfig?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    
    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_mcp_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            creationState.open(McpServerConfig.SseTransportServer())
                        }
                    ) {
                        Icon(Icons.Rounded.Add, null)
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        val mcpManager = koinInject<McpManager>()
        val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val state = rememberPullToRefreshState()
        val loading = status.values.any { it == McpStatus.Connecting }
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = {
                scope.launch {
                    mcpManager.syncAll()
                }
            },
            state = state,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Track which item is being dragged and its offset
            var draggingIndex by remember { mutableStateOf(-1) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            var isUnlocked by remember { mutableStateOf(false) }
            var neighborsUnlocked by remember { mutableStateOf(false) }
            
            // Reset neighborsUnlocked when offset returns to 0 (entry back in place)
            if (dragOffset == 0f && neighborsUnlocked) {
                neighborsUnlocked = false
            }
            
            // Screen-level fade on left edge
            val density = androidx.compose.ui.platform.LocalDensity.current
            val unlockThresholdPx = with(density) { 35.dp.toPx() }
            val fadeProgress = (kotlin.math.abs(dragOffset) / unlockThresholdPx).coerceIn(0f, 1f)
            val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
            
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    itemsIndexed(mcpConfigs, key = { _, it -> it.id }) { index, mcpConfig ->
                        val position = when {
                            mcpConfigs.size == 1 -> ItemPosition.ONLY
                            index == 0 -> ItemPosition.FIRST
                            index == mcpConfigs.lastIndex -> ItemPosition.LAST
                            else -> ItemPosition.MIDDLE
                        }
                        
                        // Calculate neighbor offset based on distance from dragging item
                        val thresholdPx = with(density) { 35.dp.toPx() }
                        
                        // Check if we just crossed the threshold
                        if (draggingIndex >= 0 && !neighborsUnlocked && kotlin.math.abs(dragOffset) >= thresholdPx) {
                            neighborsUnlocked = true
                        }
                        
                        // Neighbors only follow if we haven't unlocked yet
                        val shouldNeighborFollow = draggingIndex >= 0 && 
                            draggingIndex != index && 
                            !isUnlocked && 
                            !neighborsUnlocked
                        
                        val neighborOffset = if (shouldNeighborFollow) {
                            val distance = kotlin.math.abs(index - draggingIndex)
                            when (distance) {
                                1 -> dragOffset * 0.35f  // Direct neighbors get 35%
                                2 -> dragOffset * 0.12f  // Neighbors of neighbors get 12%
                                else -> 0f
                            }
                        } else {
                            0f
                        }
                        
                        McpServerItem(
                            item = mcpConfig,
                            position = position,
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
                            onEdit = {
                                editState.open(mcpConfig)
                            },
                            onDelete = {
                                mcpToDelete = mcpConfig
                                showDeleteDialog = true
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            if (mcpConfigs.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.setting_mcp_page_no_mcp_servers_found))
                    Text(
                        text = stringResource(R.string.setting_mcp_page_add_one_to_get_started),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && mcpToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                mcpToDelete = null
            },
            title = {
                Text(stringResource(R.string.confirm_delete))
            },
            text = {
                Text("Are you sure you want to delete this MCP server?")
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    mcpToDelete = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mcpToDelete?.let { mcp ->
                            vm.updateSettings(
                                settings.copy(
                                    mcpServers = mcpConfigs.filter { it.id != mcp.id }
                                )
                            )
                        }
                        showDeleteDialog = false
                        mcpToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        )
    }
    McpServerConfigModal(creationState)
    McpServerConfigModal(editState)
}

@Composable
private fun McpServerItem(
    item: McpServerConfig,
    position: ItemPosition,
    neighborOffset: Float = 0f,
    onDragProgress: ((Float, Boolean) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (McpServerConfig) -> Unit,
) {
    val mcpManager = koinInject<McpManager>()
    val status by mcpManager.getStatus(item).collectAsStateWithLifecycle(McpStatus.Idle)
    val haptics = rememberPremiumHaptics()
    
    PhysicsSwipeToDelete(
        onDelete = onDelete,
        position = position,
        neighborOffset = neighborOffset,
        onDragProgress = onDragProgress,
        onDragEnd = onDragEnd,
        modifier = modifier
    ) { animatedShape ->
        // Define the normal card color (used for both enabled background and disabled border)
        val normalCardColor = if (LocalDarkMode.current) 
            MaterialTheme.colorScheme.surfaceContainerLow 
        else 
            MaterialTheme.colorScheme.surfaceContainerHigh
        
        // Disabled cards: transparent background (black in dark mode) with outline
        val disabledBackground = if (LocalDarkMode.current) 
            Color.Black 
        else 
            MaterialTheme.colorScheme.surface
        
        // Grayscale modifier for disabled items
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
        
        val grayscaleModifier = if (!item.commonOptions.enable) {
            Modifier
                .graphicsLayer { alpha = 0.99f }
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
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(animatedShape)
                .then(
                    if (!item.commonOptions.enable) {
                        Modifier
                            .background(disabledBackground, animatedShape)
                            .border(3.dp, normalCardColor, animatedShape)
                    } else {
                        Modifier.background(normalCardColor)
                    }
                )
                .clickable {
                    haptics.perform(HapticPattern.Pop)
                    onEdit(item)
                }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = grayscaleModifier) {
                when (status) {
                    McpStatus.Idle -> Icon(Icons.Rounded.CommentsDisabled, null)
                    McpStatus.Connecting -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                    McpStatus.Connected -> Icon(Icons.Rounded.Terminal, null)
                    is McpStatus.Error -> Icon(Icons.Rounded.ErrorOutline, null)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.commonOptions.name,
                    style = MaterialTheme.typography.titleMedium,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Show disabled tag only for disabled items (with gray styling)
                    if (!item.commonOptions.enable) {
                        Tag(type = TagType.DEFAULT) {
                            Text(stringResource(R.string.setting_provider_page_disabled))
                        }
                    }
                    Tag(type = TagType.SUCCESS) {
                        when (item) {
                            is McpServerConfig.SseTransportServer -> Text("SSE")
                            is McpServerConfig.StreamableHTTPServer -> Text("Streamable HTTP")
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    onEdit(item)
                }
            ) {
                Icon(Icons.Rounded.Settings, null)
            }
        }
    }
}


@Composable
private fun McpServerConfigModal(state: EditState<McpServerConfig>) {
    state.EditStateContent { config, updateValue ->
        val pagerState = rememberPagerState { 2 }
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = sheetState,
            sheetGesturesEnabled = false,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            state.dismiss()
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
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        text = {
                            Text(stringResource(R.string.setting_mcp_page_basic_settings))
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        text = {
                            Text(stringResource(R.string.setting_mcp_page_tools))
                        }
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> {
                            McpCommonOptionsConfigure(
                                config = config,
                                update = updateValue
                            )
                        }

                        1 -> {
                            McpToolsConfigure(
                                config = config,
                                update = updateValue,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            if (config.commonOptions.name.isNotBlank()) {
                                state.confirm()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.setting_mcp_page_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun McpCommonOptionsConfigure(
    config: McpServerConfig,
    update: (McpServerConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 启用/禁用开关
        FormItem(
            label = {
                Text(stringResource(R.string.setting_mcp_page_enable))
            },
            description = {
                Text(stringResource(R.string.setting_mcp_page_enable_desc))
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.setting_mcp_page_enable))
                Spacer(Modifier.weight(1f))
                HapticSwitch(
                    checked = config.commonOptions.enable,
                    onCheckedChange = { enabled ->
                        update(
                            when (config) {
                                is McpServerConfig.SseTransportServer -> config.copy(
                                    commonOptions = config.commonOptions.copy(enable = enabled)
                                )

                                is McpServerConfig.StreamableHTTPServer -> config.copy(
                                    commonOptions = config.commonOptions.copy(enable = enabled)
                                )
                            }
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 名称输入框
        FormItem(
            label = {
                Text(stringResource(R.string.setting_mcp_page_name))
            },
            description = {
                Text(stringResource(R.string.setting_mcp_page_name_desc))
            }
        ) {
            OutlinedTextField(
                value = config.commonOptions.name,
                onValueChange = { name ->
                    update(
                        when (config) {
                            is McpServerConfig.SseTransportServer -> config.copy(
                                commonOptions = config.commonOptions.copy(name = name)
                            )

                            is McpServerConfig.StreamableHTTPServer -> config.copy(
                                commonOptions = config.commonOptions.copy(name = name)
                            )
                        }
                    )
                },
                label = { Text(stringResource(R.string.setting_mcp_page_name)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_mcp_page_name_placeholder)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 传输类型选择
        FormItem(
            label = {
                Text(stringResource(R.string.setting_mcp_page_transport_type))
            },
            description = {
                Text(stringResource(R.string.setting_mcp_page_transport_type_desc))
            }
        ) {
            val transportTypes = listOf(
                "SSE",
                "Streamable HTTP"
            )
            val currentTypeIndex = when (config) {
                is McpServerConfig.SseTransportServer -> 0
                is McpServerConfig.StreamableHTTPServer -> 1
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                transportTypes.forEachIndexed { index, type ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, transportTypes.size),
                        onClick = {
                            if (index != currentTypeIndex) {
                                val newConfig = when (index) {
                                    0 -> McpServerConfig.SseTransportServer(
                                        id = config.id,
                                        commonOptions = config.commonOptions,
                                        url = when (config) {
                                            is McpServerConfig.SseTransportServer -> config.url
                                            is McpServerConfig.StreamableHTTPServer -> config.url
                                        }
                                    )

                                    1 -> McpServerConfig.StreamableHTTPServer(
                                        id = config.id,
                                        commonOptions = config.commonOptions,
                                        url = when (config) {
                                            is McpServerConfig.SseTransportServer -> config.url
                                            is McpServerConfig.StreamableHTTPServer -> config.url
                                        }
                                    )

                                    else -> config
                                }
                                update(newConfig)
                            }
                        },
                        selected = index == currentTypeIndex
                    ) {
                        Text(type)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 服务器地址配置
        FormItem(
            label = {
                Text(stringResource(R.string.setting_mcp_page_server_url))
            },
            description = {
                Text(
                    when (config) {
                        is McpServerConfig.SseTransportServer -> stringResource(R.string.setting_mcp_page_sse_url_desc)
                        is McpServerConfig.StreamableHTTPServer -> stringResource(R.string.setting_mcp_page_streamable_http_url_desc)
                    }
                )
            }
        ) {
            OutlinedTextField(
                value = when (config) {
                    is McpServerConfig.SseTransportServer -> config.url
                    is McpServerConfig.StreamableHTTPServer -> config.url
                },
                onValueChange = { url ->
                    update(
                        when (config) {
                            is McpServerConfig.SseTransportServer -> config.copy(url = url)
                            is McpServerConfig.StreamableHTTPServer -> config.copy(url = url)
                        }
                    )
                },
                label = { Text(stringResource(R.string.setting_mcp_page_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        when (config) {
                            is McpServerConfig.SseTransportServer -> stringResource(R.string.setting_mcp_page_sse_url_placeholder)
                            is McpServerConfig.StreamableHTTPServer -> stringResource(R.string.setting_mcp_page_streamable_http_url_placeholder)
                        }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 请求头配置
        FormItem(
            label = {
                Text(stringResource(R.string.setting_mcp_page_custom_headers))
            },
            description = {
                Text(stringResource(R.string.setting_mcp_page_custom_headers_desc))
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                config.commonOptions.headers.forEachIndexed { index, header ->
                    var headerName by remember(header.first) { mutableStateOf(header.first) }
                    var headerValue by remember(header.second) { mutableStateOf(header.second) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = headerName,
                                onValueChange = {
                                    headerName = it
                                    val updatedHeaders =
                                        config.commonOptions.headers.toMutableList()
                                    updatedHeaders[index] =
                                        it.trim() to updatedHeaders[index].second
                                    update(
                                        when (config) {
                                            is McpServerConfig.SseTransportServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )

                                            is McpServerConfig.StreamableHTTPServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )
                                        }
                                    )
                                },
                                label = { Text(stringResource(R.string.setting_mcp_page_header_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.setting_mcp_page_header_name_placeholder)) }
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = headerValue,
                                onValueChange = {
                                    headerValue = it
                                    val updatedHeaders =
                                        config.commonOptions.headers.toMutableList()
                                    updatedHeaders[index] = updatedHeaders[index].first to it.trim()
                                    update(
                                        when (config) {
                                            is McpServerConfig.SseTransportServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )

                                            is McpServerConfig.StreamableHTTPServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )
                                        }
                                    )
                                },
                                label = { Text(stringResource(R.string.setting_mcp_page_header_value)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.setting_mcp_page_header_value_placeholder)) }
                            )
                        }
                        IconButton(onClick = {
                            val updatedHeaders = config.commonOptions.headers.toMutableList()
                            updatedHeaders.removeAt(index)
                            update(
                                when (config) {
                                    is McpServerConfig.SseTransportServer -> config.copy(
                                        commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                    )

                                    is McpServerConfig.StreamableHTTPServer -> config.copy(
                                        commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                    )
                                }
                            )
                        }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.setting_mcp_page_delete_header)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val updatedHeaders = config.commonOptions.headers.toMutableList()
                        updatedHeaders.add("" to "")
                        update(
                            when (config) {
                                is McpServerConfig.SseTransportServer -> config.copy(
                                    commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                )

                                is McpServerConfig.StreamableHTTPServer -> config.copy(
                                    commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                )
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.setting_mcp_page_add_header)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.setting_mcp_page_add_header))
                }
            }
        }
    }
}

@Composable
private fun McpToolsConfigure(
    config: McpServerConfig,
    update: (McpServerConfig) -> Unit,
) {
    val mcpManager = koinInject<McpManager>()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mcpManager.getClient(config) == null) {
            item {
                Text(stringResource(R.string.setting_mcp_page_tools_unavailable_message))
            }
        }
        items(config.commonOptions.tools) { tool ->
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (LocalDarkMode.current) androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = tool.description ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tool.inputSchema?.let { it as InputSchema.Obj }?.let { schema ->
                                schema.properties.forEach { (key, _) ->
                                    Tag(
                                        type = if (schema.required?.contains(key) == true) TagType.INFO else TagType.DEFAULT
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HapticSwitch(
                        checked = tool.enable,
                        onCheckedChange = { newVal ->
                            update(
                                config.clone(
                                    commonOptions = config.commonOptions.copy(
                                        tools = config.commonOptions.tools.map {
                                            if (tool.name == it.name) {
                                                it.copy(enable = newVal)
                                            } else {
                                                it
                                            }
                                        }
                                    )
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
