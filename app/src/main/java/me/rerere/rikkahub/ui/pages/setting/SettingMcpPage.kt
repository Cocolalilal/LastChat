package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import me.rerere.rikkahub.ui.hooks.rememberSwipeNeighborState
import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch
import me.rerere.ai.core.InputSchema
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpAuthMode
import me.rerere.rikkahub.data.ai.mcp.McpConnectionPreset
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpOAuthManager
import me.rerere.rikkahub.data.ai.mcp.McpOAuthStatus
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.ai.mcp.POPULAR_MCP_CONNECTIONS
import me.rerere.rikkahub.data.ai.mcp.endpointUrl
import me.rerere.rikkahub.data.ai.mcp.findMcpConnectionPreset
import me.rerere.rikkahub.ui.components.ai.McpServerFavicon
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.openUrl
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontFamily
import me.rerere.rikkahub.data.repository.PythonPluginManager
import me.rerere.rikkahub.data.repository.PythonPlugin
import me.rerere.rikkahub.data.repository.PythonEnvStatus
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.Screen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingMcpPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mcpConfigs = settings.mcpServers
    val creationState = useEditState<McpServerConfig> {
        vm.updateSettings { settings -> settings.copy(
                mcpServers = mcpConfigs + it
            ) }
    }
    val editState = useEditState<McpServerConfig> { newConfig ->
        vm.updateSettings { settings -> settings.copy(
                mcpServers = mcpConfigs.map {
                    if (it.id == newConfig.id) {
                        newConfig
                    } else {
                        it
                    }
                }
            ) }
    }
    
    // Delete confirmation state - at function level so accessible by dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var mcpToDelete by remember { mutableStateOf<McpServerConfig?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showConnectionsSheet by remember { mutableStateOf(false) }
    var setupRequiredPreset by remember { mutableStateOf<McpConnectionPreset?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val oauthManager = koinInject<McpOAuthManager>()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    var selectedMainTab by rememberSaveable { mutableIntStateOf(0) }
    val pythonPluginManager = koinInject<PythonPluginManager>()
    var showCreatePythonPluginDialog by remember { mutableStateOf(false) }

    fun addConnection(preset: McpConnectionPreset) {
        val existing = mcpConfigs.firstOrNull { it.commonOptions.presetId == preset.id }
        if (existing != null) {
            if (preset.authMode == McpAuthMode.OAUTH) oauthManager.startAuthorization(existing)
            showConnectionsSheet = false
            return
        }
        if (preset.authMode == McpAuthMode.EXTERNAL_OAUTH_SETUP) {
            showConnectionsSheet = false
            setupRequiredPreset = preset
            return
        }
        val config = preset.createConfig()
        vm.updateSettings(
            settings.copy(mcpServers = listOf(config) + mcpConfigs),
            base = settings,
            afterPersist = if (preset.authMode == McpAuthMode.OAUTH) {
                { oauthManager.startAuthorization(config) }
            } else {
                null
            },
        )
        showConnectionsSheet = false
    }
    
    Scaffold(
        topBar = {
            Column {
                OneUITopAppBar(
                    title = stringResource(R.string.setting_mcp_page_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        BackButton()
                    }
                )
                SecondaryTabRow(
                    selectedTabIndex = selectedMainTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedMainTab == 0,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            selectedMainTab = 0
                        },
                        text = {
                            Text(stringResource(R.string.setting_mcp_tab_mcp))
                        },
                        icon = {
                            Icon(Icons.Rounded.Cloud, null, modifier = Modifier.size(18.dp))
                        }
                    )
                    Tab(
                        selected = selectedMainTab == 1,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            selectedMainTab = 1
                        },
                        text = {
                            Text(stringResource(R.string.setting_mcp_tab_python))
                        },
                        icon = {
                            Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                if (selectedMainTab == 0) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FloatingActionButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                showImportDialog = true
                            },
                            shape = AppShapes.CardLarge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Input, "Import MCP configuration")
                        }
                        FloatingActionButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                showConnectionsSheet = true
                            },
                            shape = AppShapes.CardLarge,
                        ) {
                            Icon(Icons.Rounded.Add, "Add connection")
                        }
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showCreatePythonPluginDialog = true
                        },
                        shape = AppShapes.CardLarge,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.python_new_plugin))
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        if (selectedMainTab == 0) {
        val mcpManager = koinInject<McpManager>()
        val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val state = rememberPullToRefreshState()
        val loading = status.values.any { it == McpStatus.Connecting }
        val canRefresh = lazyListState.firstVisibleItemIndex == 0 &&
            lazyListState.firstVisibleItemScrollOffset == 0 &&
            scrollBehavior.state.collapsedFraction == 0f
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding)
                .pullToRefresh(
                    state = state,
                    isRefreshing = loading,
                    enabled = canRefresh,
                    onRefresh = {
                        scope.launch {
                            mcpManager.syncAll()
                        }
                    }
                )
        ) {
            val swipeNeighbors = rememberSwipeNeighborState()
            
            
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
                ) {
                    if (mcpConfigs.isEmpty()) {
                        item(key = "empty") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (LocalDarkMode.current) {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ),
                                shape = AppShapes.CardLarge
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Extension,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = stringResource(R.string.setting_mcp_page_no_mcp_servers_found),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.setting_mcp_page_add_one_to_get_started),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                    itemsIndexed(mcpConfigs, key = { _, it -> it.id }) { index, mcpConfig ->
                        val position = when {
                            mcpConfigs.size == 1 -> ItemPosition.ONLY
                            index == 0 -> ItemPosition.FIRST
                            index == mcpConfigs.lastIndex -> ItemPosition.LAST
                            else -> ItemPosition.MIDDLE
                        }
                        
                        // Calculate neighbor offset based on distance from dragging item
                        val neighborOffset = swipeNeighbors.offsetFor(index, mcpConfigs) { it.id }
                        
                        McpServerItem(
                            item = mcpConfig,
                            position = position,
                            neighborOffset = neighborOffset,
                            onDragProgress = { offset, unlocked -> swipeNeighbors.update(mcpConfig.id, offset, unlocked) },
                            onDragEnd = { swipeNeighbors.end(mcpConfig.id) },
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

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
            }

            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = loading,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    } else {
        PythonExtensionsContent(
            pluginManager = pythonPluginManager,
            showCreateDialog = showCreatePythonPluginDialog,
            onDismissCreateDialog = { showCreatePythonPluginDialog = false },
            innerPadding = innerPadding,
        )
    }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && mcpToDelete != null) {
        LastChatDestructiveConfirmDialog(
            title = stringResource(R.string.setting_mcp_delete_title),
            consequence = stringResource(R.string.setting_mcp_delete_server),
            onDismiss = {
                showDeleteDialog = false
                mcpToDelete = null
            },
            onConfirm = {
                mcpToDelete?.let { mcp ->
                    vm.updateSettings { settings -> settings.copy(
                            mcpServers = mcpConfigs.filter { it.id != mcp.id }
                        ) }
                }
                showDeleteDialog = false
                mcpToDelete = null
            },
        )
    }
    McpServerConfigModal(creationState)
    McpServerConfigModal(editState)
    if (showConnectionsSheet) {
        McpConnectionsSheet(
            installedPresetIds = mcpConfigs.mapNotNull { it.commonOptions.presetId }.toSet(),
            onDismiss = { showConnectionsSheet = false },
            onAddCustom = {
                showConnectionsSheet = false
                creationState.open(
                    McpServerConfig.StreamableHTTPServer(
                        commonOptions = McpCommonOptions(name = "", authMode = McpAuthMode.CUSTOM_HEADERS)
                    )
                )
            },
            onSelect = ::addConnection,
        )
    }
    if (showImportDialog) {
        McpImportModal(
            onDismiss = { showImportDialog = false },
            onImport = { importedConfigs ->
                val existingNames = mcpConfigs.map { it.commonOptions.name.trim().lowercase() }.toSet()
                val newConfigs = importedConfigs.filter { it.commonOptions.name.trim().lowercase() !in existingNames }
                if (newConfigs.isNotEmpty()) {
                    vm.updateSettings { settings -> settings.copy(
                            mcpServers = mcpConfigs + newConfigs
                        ) }
                }
                showImportDialog = false
            }
        )
    }

    setupRequiredPreset?.let { preset ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { setupRequiredPreset = null },
            icon = { Icon(Icons.Rounded.Info, null) },
            title = { Text("${preset.name} needs app setup") },
            text = {
                Text(
                    preset.setupNote ?: "This service requires an OAuth application to be registered for LastChat."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        preset.documentationUrl?.let(context::openUrl)
                        setupRequiredPreset = null
                    },
                ) { Text("Open setup guide") }
            },
            dismissButton = {
                TextButton(onClick = { setupRequiredPreset = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun McpConnectionsSheet(
    installedPresetIds: Set<String>,
    onDismiss: () -> Unit,
    onAddCustom: () -> Unit,
    onSelect: (McpConnectionPreset) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptics = rememberPremiumHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val isDarkMode = LocalDarkMode.current
    val titleColor = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val itemTextColor = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface
    val customServerContentColor = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) POPULAR_MCP_CONNECTIONS else POPULAR_MCP_CONNECTIONS.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                it.badge.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = iconColor) }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Add a connection",
                style = MaterialTheme.typography.headlineSmall,
                color = titleColor,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.SearchField,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = iconColor) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, "Clear", tint = iconColor) } }
                } else null,
                placeholder = { Text("Search connections", color = if (isDarkMode) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = if (isDarkMode) {
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLeadingIconColor = Color.White,
                        unfocusedLeadingIconColor = Color.White,
                        focusedTrailingIconColor = Color.White,
                        unfocusedTrailingIconColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            onAddCustom()
                        },
                        shape = AppShapes.CardMedium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = customServerContentColor,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(40.dp), tint = customServerContentColor)
                            Text(
                                "Custom MCP server",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = customServerContentColor,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                itemsIndexed(filtered, key = { _, preset -> preset.id }) { index, preset ->
                    val installed = preset.id in installedPresetIds
                    val shape = when {
                        filtered.size == 1 -> AppShapes.CardMedium
                        index == 0 -> AppShapes.ListItemFirst
                        index == filtered.lastIndex -> AppShapes.ListItemLast
                        else -> AppShapes.ListItemMiddle
                    }
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            onSelect(preset)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = itemTextColor,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AutoAIIconWithUrl(
                                name = preset.name,
                                customIconUri = preset.iconUri,
                                modifier = Modifier.size(40.dp),
                                contentColor = itemTextColor,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = itemTextColor,
                                )
                                Text(
                                    preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (preset.authMode != McpAuthMode.NONE) {
                                        Tag(type = if (preset.authMode == McpAuthMode.OAUTH) TagType.SUCCESS else TagType.DEFAULT) {
                                            Text(preset.badge)
                                        }
                                    }
                                    if (installed) Tag(type = TagType.INFO) { Text("Added") }
                                }
                            }
                            Icon(
                                if (installed) Icons.AutoMirrored.Rounded.Login else Icons.Rounded.Add,
                                contentDescription = null,
                                tint = iconColor,
                            )
                        }
                    }
                }
            }
        }
    }
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
    val oauthManager = koinInject<McpOAuthManager>()
    val status by mcpManager.getStatus(item).collectAsStateWithLifecycle(McpStatus.Idle)
    val oauthStatuses by oauthManager.statuses.collectAsStateWithLifecycle()
    val oauthStatus = oauthStatuses[item.id] ?: McpOAuthStatus.Idle
    val preset = remember(item) {
        findMcpConnectionPreset(item)
    }
    val hasOAuthCredentials = remember(oauthStatuses, item.id) {
        oauthManager.hasCredentials(item.id)
    }
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
        val normalCardColor = MaterialTheme.colorScheme.surfaceContainerLow
        
        val disabledBackground = MaterialTheme.colorScheme.surfaceContainerHighest
        
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
                if (preset != null) {
                    AutoAIIconWithUrl(
                        name = preset.name,
                        customIconUri = preset.iconUri,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    McpServerFavicon(
                        url = item.endpointUrl,
                        modifier = Modifier.size(40.dp),
                        fallback = {
                            when (status) {
                                McpStatus.Idle -> Icon(Icons.Rounded.Extension, null)
                                McpStatus.Connecting -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                McpStatus.Connected -> Icon(Icons.Rounded.Extension, null)
                                is McpStatus.Error -> Icon(Icons.Rounded.ErrorOutline, null)
                            }
                        }
                    )
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
                            Text(
                                if (item.commonOptions.authMode == McpAuthMode.OAUTH && !hasOAuthCredentials) {
                                    "Sign-in required"
                                } else {
                                    stringResource(R.string.setting_provider_page_disabled)
                                }
                            )
                        }
                    }
                    if (oauthStatus == McpOAuthStatus.WaitingForUser) {
                        Tag(type = TagType.INFO) { Text("Waiting for sign-in") }
                    }
                    if (oauthStatus is McpOAuthStatus.Error || status is McpStatus.Error) {
                        Tag(type = TagType.ERROR) { Text("Connection error") }
                    }
                    Tag(type = TagType.SUCCESS) {
                        when (item) {
                            is McpServerConfig.SseTransportServer -> Text(stringResource(R.string.setting_mcp_transport_sse))
                            is McpServerConfig.StreamableHTTPServer -> Text("HTTP")
                        }
                    }
                }
                val currentOAuthStatus = oauthStatus
                val currentStatus = status
                val errorMessage = when {
                    currentOAuthStatus is McpOAuthStatus.Error -> currentOAuthStatus.message
                    currentStatus is McpStatus.Error -> currentStatus.message
                    else -> null
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (item.commonOptions.authMode == McpAuthMode.OAUTH && !hasOAuthCredentials) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        oauthManager.startAuthorization(item)
                    }
                ) {
                    if (oauthStatus is McpOAuthStatus.Discovering || oauthStatus is McpOAuthStatus.ExchangingCode) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Login, "Sign in")
                    }
                }
            } else {
                IconButton(onClick = { onEdit(item) }) {
                    Icon(Icons.Rounded.Settings, null)
                }
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                placeholder = { Text(stringResource(R.string.setting_mcp_page_name_placeholder)) },
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
                stringResource(R.string.setting_mcp_transport_sse),
                stringResource(R.string.setting_mcp_transport_streamable_http)
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
                },
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
                                placeholder = { Text(stringResource(R.string.setting_mcp_page_header_name_placeholder)) },
                                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
                                placeholder = { Text(stringResource(R.string.setting_mcp_page_header_value_placeholder)) },
                                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
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

private fun parseMcpServersFromJson(json: String): List<McpServerConfig> {
    val root = JsonInstant.parseToJsonElement(json) as? JsonObject ?: return emptyList()
    val mcpServers = root["mcpServers"] as? JsonObject ?: return emptyList()
    return mcpServers.entries.mapNotNull { (name, element) ->
        val configObject = element as? JsonObject ?: return@mapNotNull null
        val url = configObject["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (url.isBlank()) return@mapNotNull null

        val type = configObject["type"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
        val headers = (configObject["headers"] as? JsonObject)
            ?.entries
            ?.mapNotNull { (key, value) ->
                value.jsonPrimitive.contentOrNull?.let { key to it }
            }
            .orEmpty()
        val commonOptions = McpCommonOptions(name = name, headers = headers)
        when (type) {
            "sse" -> McpServerConfig.SseTransportServer(commonOptions = commonOptions, url = url)
            else -> McpServerConfig.StreamableHTTPServer(commonOptions = commonOptions, url = url)
        }
    }
}

@Composable
private fun McpImportModal(
    onDismiss: () -> Unit,
    onImport: (List<McpServerConfig>) -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val noValidConfigMessage = stringResource(R.string.setting_mcp_page_import_no_valid_config)
    val parseErrorMessage = stringResource(R.string.setting_mcp_page_import_parse_error)

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.setting_mcp_page_import_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.setting_mcp_page_import_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = jsonText,
                onValueChange = {
                    jsonText = it
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = {
                    Text(stringResource(R.string.setting_mcp_import_placeholder))
                },
                isError = errorMessage != null,
                supportingText = errorMessage?.let { message ->
                    {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        runCatching {
                            parseMcpServersFromJson(jsonText.trim())
                        }.onSuccess { configs ->
                            if (configs.isEmpty()) {
                                errorMessage = noValidConfigMessage
                            } else {
                                onImport(configs)
                            }
                        }.onFailure { error ->
                            errorMessage = parseErrorMessage.format(error.message ?: "")
                        }
                    }
                ) {
                    Text(stringResource(R.string.setting_mcp_page_import_confirm))
                }
            }
        }
    }
}

@Composable
private fun PythonExtensionsContent(
    pluginManager: PythonPluginManager,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    innerPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    val navController = LocalNavController.current

    var envStatus by remember { mutableStateOf<PythonEnvStatus?>(null) }
    var plugins by remember { mutableStateOf<List<PythonPlugin>>(emptyList()) }
    val runningServers by pluginManager.runningServers.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }
    var newPluginName by remember { mutableStateOf("") }
    var editingPlugin by remember { mutableStateOf<PythonPlugin?>(null) }
    var editCode by remember { mutableStateOf("") }
    var pluginToDelete by remember { mutableStateOf<PythonPlugin?>(null) }

    fun reload() {
        scope.launch {
            isRefreshing = true
            val status = pluginManager.checkEnvironmentStatus()
            envStatus = status
            if (status.workspace != null && status.isRootfsReady) {
                plugins = pluginManager.listPlugins(status.workspace)
            } else {
                plugins = emptyList()
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())
            .consumeWindowInsets(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Environment Status Banner
        item {
            val status = envStatus
            if (status == null || !status.hasWorkspace) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Workspace Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "No workspace found. Python extensions require a workspace to run PRoot Linux.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { navController.navigate(Screen.Workspaces) }
                        ) {
                            Text("Open Workspaces")
                        }
                    }
                }
            } else if (!status.isRootfsReady) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.python_env_needs_setup),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = stringResource(R.string.python_env_setup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (status.isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = stringResource(R.string.python_installing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Button(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    status.workspace?.let { ws ->
                                        pluginManager.installFullEnvironment(ws)
                                        reload()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.python_install_action))
                            }
                        }
                    }
                }
            } else if (!status.isPythonInstalled) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Python 3 Runtime Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Linux PRoot environment is ready in '${status.workspace?.name}', but Python 3 is not yet installed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (status.isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Installing Python 3 & pip…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Button(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    status.workspace?.let { ws ->
                                        pluginManager.installPythonOnly(ws)
                                        reload()
                                    }
                                }
                            ) {
                                Text("Install Python 3 & Pip")
                            }
                        }
                    }
                }
            } else {
                // Environment is Ready!
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.python_env_ready),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Workspace: ${status.workspace?.name} • /workspace/plugins",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        SuggestionChip(
                            onClick = { navController.navigate(Screen.Workspaces) },
                            label = { Text("Terminal") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }
            }
        }

        // Running Python Servers Section
        if (runningServers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "Running Python Servers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${runningServers.size} active (Foreground Service)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(runningServers.values.toList(), key = { it.id }) { server ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = AppShapes.CardMedium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = server.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = server.command,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (server.port != null) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(":${server.port}") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val uptimeSec = (System.currentTimeMillis() - server.startedAt) / 1000
                            Text(
                                text = "Running for ${uptimeSec}s • Foreground Notification Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (server.port != null) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                pluginManager.addLocalMcpServer(
                                                    name = server.name,
                                                    url = "http://127.0.0.1:${server.port}/sse"
                                                )
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Added ${server.name} to MCP servers",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    ) {
                                        Text("Add to MCP")
                                    }
                                }

                                Button(
                                    onClick = {
                                        haptics.perform(HapticPattern.Thud)
                                        pluginManager.stopServer(server.id)
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Text("Stop")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Extensions
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Extensions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${plugins.count { it.enabled }} active / ${plugins.size} total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (plugins.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = stringResource(R.string.python_no_plugins),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Drop Python scripts into /workspace/plugins/ or start with our template.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    envStatus?.workspace?.let { ws ->
                                        scope.launch {
                                            pluginManager.getPluginsDirectory(ws)
                                            reload()
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.python_create_sample))
                            }
                        }
                    }
                }
            }
        } else {
            items(plugins, key = { it.id }) { plugin ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = AppShapes.CardMedium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = if (plugin.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v${plugin.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HapticSwitch(
                                checked = plugin.enabled,
                                onCheckedChange = { isEnabled ->
                                    scope.launch {
                                        pluginManager.setPluginEnabled(plugin.id, isEnabled)
                                        reload()
                                    }
                                }
                            )
                        }

                        Text(
                            text = plugin.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (plugin.hooks.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                plugin.hooks.forEach { hook ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = hook,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plugin.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        envStatus?.workspace?.let { ws ->
                                            scope.launch {
                                                val code = pluginManager.readPluginCode(ws, plugin.fileName)
                                                editCode = code
                                                editingPlugin = plugin
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Code, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit Code")
                                }

                                IconButton(
                                    onClick = { pluginToDelete = plugin }
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs: Create, Edit, Delete
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateDialog,
            title = { Text(stringResource(R.string.python_new_plugin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a file name for your new extension (e.g. typing_fx or my_theme).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPluginName,
                        onValueChange = { newPluginName = it },
                        label = { Text("Extension Name") },
                        singleLine = true,
                        shape = AppShapes.InputField,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val base = newPluginName.trim().lowercase().replace(' ', '_').removeSuffix(".py")
                        if (base.isNotBlank()) {
                            val fName = "$base.py"
                            envStatus?.workspace?.let { ws ->
                                scope.launch {
                                    val starterCode = """
# Name: ${newPluginName.trim()}
# Description: Custom extension in Python
# Version: 1.0.0
# Author: Ammar
# Hooks: before_prompt, after_reply

class Hook:
    @staticmethod
    def before_prompt(prompt: str) -> str:
        return prompt

    @staticmethod
    def after_reply(reply: str) -> str:
        return reply
""".trimIndent()
                                    pluginManager.savePluginCode(ws, fName, starterCode)
                                    newPluginName = ""
                                    onDismissCreateDialog()
                                    reload()
                                }
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreateDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (editingPlugin != null) {
        AlertDialog(
            onDismissRequest = { editingPlugin = null },
            title = { Text("Edit: ${editingPlugin!!.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "File: ${editingPlugin!!.fileName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editCode,
                        onValueChange = { editCode = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        shape = AppShapes.InputField
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        envStatus?.workspace?.let { ws ->
                            scope.launch {
                                pluginManager.savePluginCode(ws, editingPlugin!!.fileName, editCode)
                                editingPlugin = null
                                reload()
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPlugin = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pluginToDelete != null) {
        AlertDialog(
            onDismissRequest = { pluginToDelete = null },
            title = { Text("Delete Extension?") },
            text = { Text("Are you sure you want to delete '${pluginToDelete!!.name}' (${pluginToDelete!!.fileName})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDel = pluginToDelete!!
                        pluginToDelete = null
                        envStatus?.workspace?.let { ws ->
                            scope.launch {
                                pluginManager.deletePlugin(ws, toDel.fileName)
                                reload()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pluginToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

