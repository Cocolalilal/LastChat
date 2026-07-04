package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import me.rerere.ai.provider.ModelType
import me.rerere.ai.util.fuzzyMemoryAgeLabel
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.MemoryNodeTypeCodec
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

private enum class MemoryTab(val label: String) { OVERVIEW("Overview"), GRAPH("Graph"), BROWSE("Browse"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCenterPage(assistantId: String?, focusNodeId: String? = null) {
    val vm: MemoryCenterVM = koinViewModel(
        key = assistantId ?: "global",
        parameters = { parametersOf(assistantId ?: "") },
    )
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val importProgress by vm.importProgress.collectAsStateWithLifecycle()
    val budgetToday by vm.budgetToday.collectAsStateWithLifecycle()
    val nodeDetail by vm.nodeDetail.collectAsStateWithLifecycle()
    val graphInput by vm.graphInput.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflow by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }

    // Deep-link: open a node sheet on entry (activity-pill link).
    LaunchedEffect(focusNodeId) { if (focusNodeId != null) vm.openNode(focusNodeId) }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = vm.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess { toaster.show("Exported") }.onFailure { toaster.show("Export failed: ${it.message}") }
        }
    }

    val title = if (vm.isGlobal) "Shared memory" else (assistant?.name?.takeIf { it.isNotBlank() } ?: "Memory")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton() },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text(if (vm.isGlobal) "Export shared memory…" else "Export memory…") },
                                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                                onClick = { showOverflow = false; exportLauncher.launch("lastchat-memory-${System.currentTimeMillis()}.json") },
                            )
                            DropdownMenuItem(
                                text = { Text("Erase memory…") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showOverflow = false; showWipe = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedTab == MemoryTab.BROWSE.ordinal && !vm.isGlobal) {
                var showAdd by remember { mutableStateOf(false) }
                FloatingActionButton(onClick = { haptics.perform(HapticPattern.Pop); showAdd = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add memory")
                }
                if (showAdd) AddMemoryDialog(onDismiss = { showAdd = false }, onConfirm = { content, importance, pinned ->
                    vm.addManual(content, importance, pinned); showAdd = false; toaster.show("Memory added")
                })
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                MemoryTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { haptics.perform(HapticPattern.Tick); selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "memory_tab",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (MemoryTab.entries[tab]) {
                    MemoryTab.OVERVIEW -> OverviewTab(vm, nodes, activity, importProgress, budgetToday, settings, onOpenNode = { vm.openNode(it) })
                    MemoryTab.GRAPH -> MemoryGraphTab(
                        input = graphInput,
                        onOpenNode = { vm.openNode(it) },
                        onTogglePin = { nodeId, pinned -> vm.setPinned(nodeId, pinned) },
                    )
                    MemoryTab.BROWSE -> BrowseTab(vm, nodes, onOpenNode = { vm.openNode(it) })
                    MemoryTab.SETTINGS -> SettingsTab(vm, settings, navController)
                }
            }
        }
    }

    // Node sheet
    nodeDetail?.let { detail ->
        NodeSheet(
            detail = detail,
            onDismiss = { vm.closeNode() },
            onPin = { vm.setPinned(detail.node.id, !detail.node.pinned) },
            onForget = {
                vm.forget(detail.node.id); vm.closeNode()
                toaster.show("Forgotten — restore from Browse › Recently forgotten")
            },
            onRestore = { vm.restore(detail.node.id) },
            onEdit = { vm.edit(detail.node.id, it) },
            onOpenConversation = { convId ->
                runCatching { navigateTo(navController, convId) }
            },
        )
    }

    if (showWipe) {
        WipeConfirmSheet(
            isGlobal = vm.isGlobal,
            characterName = assistant?.name?.takeIf { it.isNotBlank() },
            onDismiss = { showWipe = false },
            onConfirm = {
                haptics.perform(HapticPattern.Error)
                if (vm.isGlobal) vm.wipeEverything() else vm.wipeCharacter()
                showWipe = false
                toaster.show("Memory erased")
            },
        )
    }
}

private fun navigateTo(navController: androidx.navigation.NavHostController, conversationId: String) {
    val uuid = runCatching { Uuid.parse(conversationId) }.getOrNull() ?: return
    me.rerere.rikkahub.utils.navigateToChatPage(navController, chatId = uuid)
}

// ─────────────────────────────── Overview ───────────────────────────────

@Composable
private fun OverviewTab(
    vm: MemoryCenterVM,
    allNodes: List<MemoryNodeEntity>,
    activity: List<MemoryActivityEntity>,
    importProgress: me.rerere.rikkahub.data.memory.MemoryGraphRepository.ImportProgress?,
    budgetToday: Map<String, Int>,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    onOpenNode: (String) -> Unit,
) {
    val live = remember(allNodes) { vm.liveNodes(allNodes) }
    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000
    val thisWeek = live.count { it.recordedAt >= weekAgo }
    val byType = remember(live) { live.groupingBy { it.type }.eachCount() }

    // Embedding index %: fraction of injectable non-entity nodes carrying an embedding.
    val injectable = live.filter { it.type != MemNodeType.ENTITY && (it.status == MemStatus.ACTIVE || it.status == MemStatus.PROVISIONAL) }
    val embedded = injectable.count { it.embeddingBlob != null }
    val embedPercent = if (injectable.isEmpty()) 100 else (embedded * 100 / injectable.size)

    val suggestions = remember(activity, now) {
        activity.filter {
            it.kind == me.rerere.rikkahub.data.db.entity.MemActivityKind.PROMOTION_SUGGESTED &&
                it.state == me.rerere.rikkahub.data.db.entity.MemActivityState.PENDING &&
                now - it.at <= 30L * 24 * 60 * 60 * 1000
        }.take(3)
    }
    val groupedActivity = remember(activity) { activity.groupBy { dayKey(it.at) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), live.size.toString(), "memories", MaterialTheme.colorScheme.primary)
                StatCard(Modifier.weight(1f), thisWeek.toString(), "this week", MaterialTheme.colorScheme.tertiary)
                StatCard(Modifier.weight(1f), (byType[MemNodeType.ENTITY] ?: 0).toString(), "entities", MaterialTheme.colorScheme.secondary)
            }
        }

        item { HealthStrip(embedPercent = embedPercent, importProgress = importProgress, memoryModelMissing = isMemoryModelMissing(settings)) }

        if (suggestions.isNotEmpty()) {
            item { SectionHeader("Suggestions") }
            items(suggestions, key = { it.id }) { row ->
                PromotionChipRow(
                    row = row,
                    onAccept = { vm.acceptPromotion(row.id) },
                    onDismiss = { vm.dismissPromotion(row.id) },
                )
            }
        }

        item { QuickToggles(vm, settings) }

        item { BudgetGlance(budgetToday, settings) }

        item { SectionHeader("Activity") }
        if (activity.isEmpty()) {
            item { EmptyHint("No memory activity yet.") }
        } else {
            groupedActivity.forEach { (day, rows) ->
                item(key = "day_$day") {
                    Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
                items(rows, key = { it.id }) { row ->
                    ActivityRow(row) {
                        parseFirstNodeId(row.nodeIds)?.let(onOpenNode)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Surface(modifier = modifier, shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun HealthStrip(
    embedPercent: Int,
    importProgress: me.rerere.rikkahub.data.memory.MemoryGraphRepository.ImportProgress?,
    memoryModelMissing: Boolean,
) {
    val lines = buildList {
        if (memoryModelMissing) add("No memory model configured — extraction paused. Set one in Settings.")
        if (importProgress != null && !importProgress.completed && importProgress.total > 0) {
            add("Importing previous memories… ${importProgress.processed}/${importProgress.total}")
        }
        if (embedPercent < 100) add("Embedding index: $embedPercent% — rebuilding in the background")
    }
    if (lines.isEmpty()) return
    Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text("Status", style = MaterialTheme.typography.labelLarge)
            }
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (importProgress != null && !importProgress.completed && importProgress.total > 0) {
                LinearProgressIndicator(
                    progress = { importProgress.processed.toFloat() / importProgress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuickToggles(vm: MemoryCenterVM, settings: me.rerere.rikkahub.data.datastore.Settings) {
    Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormItem(
                label = { Text("Memory system") },
                description = { Text("Master switch for the human-like memory pipeline") },
                tail = { HapticSwitch(checked = settings.memory.enabled, onCheckedChange = { on -> vm.updateGlobalMemory { it.copy(enabled = on) } }) },
            )
            FormItem(
                label = { Text("Time awareness") },
                description = { Text("Fuzzy ages and past-tense phrasing in recall") },
                tail = {
                    HapticSwitch(
                        checked = settings.memory.timeAwareness,
                        enabled = settings.memory.enabled,
                        onCheckedChange = { on -> vm.updateGlobalMemory { it.copy(timeAwareness = on) } },
                    )
                },
            )
        }
    }
}

@Composable
private fun BudgetGlance(budgetToday: Map<String, Int>, settings: me.rerere.rikkahub.data.datastore.Settings) {
    val total = budgetToday.values.sum()
    Surface(shape = AppShapes.CardSmall, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("~$total background calls today", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(settings.memory.preset.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PromotionChipRow(row: MemoryActivityEntity, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberPremiumHaptics()
    Surface(shape = AppShapes.CardSmall, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.summary.ifBlank { "Share this with all characters?" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { haptics.perform(HapticPattern.Success); onAccept() }) { Text("Share") }
                TextButton(onClick = { haptics.perform(HapticPattern.Pop); onDismiss() }) { Text("Keep private") }
            }
        }
    }
}

@Composable
private fun ActivityRow(row: MemoryActivityEntity, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = AppShapes.CardSmall, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(activityIcon(row.kind), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(prettyKind(row.kind), style = MaterialTheme.typography.labelMedium)
                Text(row.summary.ifBlank { "memory updated" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(fuzzyMemoryAgeLabel(row.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────── Browse ───────────────────────────────

@Composable
private fun BrowseTab(vm: MemoryCenterVM, allNodes: List<MemoryNodeEntity>, onOpenNode: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<Int?>(null) }
    var statusFilter by remember { mutableStateOf<Int?>(null) }
    var recentlyForgotten by remember { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()

    // Recently-forgotten is a separate load (statuses beyond the live set are also in allNodes,
    // but grace filtering needs the repository helper).
    var forgotten by remember { mutableStateOf<List<MemoryNodeEntity>>(emptyList()) }
    LaunchedEffect(recentlyForgotten, allNodes) {
        if (recentlyForgotten) forgotten = vm.getRecentlyForgotten()
    }

    val base = if (recentlyForgotten) forgotten else allNodes.filter { it.status != MemStatus.FORGOTTEN && it.status != MemStatus.SUPERSEDED }
    val filtered = base.filter { n ->
        (query.isBlank() || n.content.contains(query, ignoreCase = true) || (n.displayLabel?.contains(query, ignoreCase = true) == true)) &&
            (typeFilter == null || n.type == typeFilter) &&
            (statusFilter == null || n.status == statusFilter)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search memories") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = AppShapes.SearchField,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = recentlyForgotten, onClick = { haptics.perform(HapticPattern.Tick); recentlyForgotten = !recentlyForgotten }, label = { Text("Recently forgotten") }, leadingIcon = { Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(18.dp)) })
            if (!recentlyForgotten) {
                listOf(
                    MemNodeType.FACT to "Facts", MemNodeType.EPISODE to "Episodes", MemNodeType.ENTITY to "Entities",
                    MemNodeType.HABIT to "Habits", MemNodeType.GOAL to "Goals",
                ).forEach { (t, label) ->
                    FilterChip(selected = typeFilter == t, onClick = { typeFilter = if (typeFilter == t) null else t }, label = { Text(label) })
                }
                listOf(
                    MemStatus.PROVISIONAL to "Provisional", MemStatus.DORMANT to "Dormant", MemStatus.CLOSED to "Closed",
                ).forEach { (s, label) ->
                    FilterChip(selected = statusFilter == s, onClick = { statusFilter = if (statusFilter == s) null else s }, label = { Text(label) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            EmptyHint(if (recentlyForgotten) "Nothing forgotten recently." else "No memories match.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { node ->
                    NodeListItem(
                        node = node,
                        showRestore = recentlyForgotten,
                        onClick = { onOpenNode(node.id) },
                        onRestore = { vm.restore(node.id); haptics.perform(HapticPattern.Success) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeListItem(node: MemoryNodeEntity, showRestore: Boolean, onClick: () -> Unit, onRestore: () -> Unit) {
    Surface(onClick = onClick, shape = AppShapes.CardSmall, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeChip(node.type)
                    if (node.pinned) Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    StatusLabel(node.status)
                }
                Text(node.displayLabel ?: node.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (showRestore) {
                IconButton(onClick = onRestore) { Icon(Icons.Rounded.Restore, "Restore", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

// ─────────────────────────────── Node sheet ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeSheet(
    detail: me.rerere.rikkahub.data.memory.MemoryGraphRepository.NodeDetail,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onForget: () -> Unit,
    onRestore: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val node = detail.node
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = AppShapes.BottomSheet) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeChip(node.type)
                StatusLabel(node.status)
                if (node.scope == MemScope.GLOBAL_USER) SmallTag("shared")
                if (node.sensitivity == MemSensitivity.SENSITIVE) SmallTag("sensitive")
            }
            Text(node.content, style = MaterialTheme.typography.titleMedium)
            Text(
                "learned ${fuzzyMemoryAgeLabel(node.recordedAt)}, last confirmed ${fuzzyMemoryAgeLabel(node.lastConfirmedAt)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Why I remember this (provenance)
            if (detail.provenance.isNotEmpty()) {
                HorizontalDivider()
                SectionHeader("Why I remember this")
                detail.provenance.forEach { p ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (p.rationale.isNotBlank()) Text(p.rationale, style = MaterialTheme.typography.bodyMedium)
                        if (p.excerpt.isNotBlank()) Text("“${p.excerpt}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.conversationId?.let { cid ->
                            TextButton(onClick = { onOpenConversation(cid) }) {
                                Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open source conversation")
                            }
                        }
                    }
                }
            }

            // History (supersedes chain)
            if (detail.history.isNotEmpty()) {
                HorizontalDivider()
                SectionHeader("History")
                detail.history.forEach { h ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusLabel(h.status)
                        Text(h.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(fuzzyMemoryAgeLabel(h.recordedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Connections
            if (detail.edges.isNotEmpty()) {
                HorizontalDivider()
                SectionHeader("Connections")
                detail.edges.forEach { e ->
                    Text(
                        "${if (e.outgoing) "→" else "←"} ${edgeTypeLabel(e.edge.type)}: ${e.otherLabel}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()
            // Actions
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onPin, label = { Text(if (node.pinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Rounded.PushPin, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { editing = true }, label = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)) })
                if (node.status == MemStatus.FORGOTTEN) {
                    AssistChip(onClick = onRestore, label = { Text("Restore") }, leadingIcon = { Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)) })
                } else {
                    AssistChip(
                        onClick = onForget,
                        label = { Text("Forget") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error, leadingIconContentColor = MaterialTheme.colorScheme.error),
                    )
                }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(node.content) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit memory") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2, maxLines = 8, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { onEdit(text); editing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

// ─────────────────────────────── Settings ───────────────────────────────

@Composable
private fun SettingsTab(vm: MemoryCenterVM, settings: me.rerere.rikkahub.data.datastore.Settings, navController: androidx.navigation.NavHostController) {
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val enabled = settings.memory.enabled
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FormItem(
                    label = { Text("Memory system") },
                    description = { Text("Master switch. Off keeps the store but stops extraction and injection.") },
                    tail = { HapticSwitch(checked = enabled, onCheckedChange = { on -> vm.updateGlobalMemory { it.copy(enabled = on) } }) },
                )
                HorizontalDivider()
                FormItem(label = { Text("Cost preset") }, description = { Text("Balances extraction cadence and background work") }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        me.rerere.rikkahub.data.memory.MemoryPreset.entries.filter { it != me.rerere.rikkahub.data.memory.MemoryPreset.OFF }.forEach { preset ->
                            FilterChip(
                                selected = settings.memory.preset.equals(preset.name, true),
                                enabled = enabled,
                                onClick = { vm.updateGlobalMemory { it.copy(preset = preset.name) } },
                                label = { Text(preset.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                            )
                        }
                    }
                }
                HorizontalDivider()
                FormItem(
                    label = { Text("Time awareness") },
                    description = { Text("Fuzzy ages and past-tense phrasing in recall") },
                    tail = { HapticSwitch(checked = settings.memory.timeAwareness, enabled = enabled, onCheckedChange = { on -> vm.updateGlobalMemory { it.copy(timeAwareness = on) } }) },
                )
                FormItem(
                    label = { Text("Proactive curiosity") },
                    description = { Text("Occasional gentle in-character questions") },
                    tail = { HapticSwitch(checked = settings.memory.proactiveCuriosity, enabled = enabled, onCheckedChange = { on -> vm.updateGlobalMemory { it.copy(proactiveCuriosity = on) } }) },
                )
                FormItem(
                    label = { Text("Web lookups for curiosity") },
                    description = { Text("Let curiosity pre-research answers via web search") },
                    tail = {
                        HapticSwitch(
                            checked = settings.memory.curiosityWebLookups,
                            enabled = enabled && settings.memory.proactiveCuriosity,
                            onCheckedChange = { on -> vm.updateGlobalMemory { it.copy(curiosityWebLookups = on) } },
                        )
                    },
                )
                FormItem(
                    label = { Text("Habit induction") },
                    description = { Text("Compress repeated episodes into habit memories") },
                    tail = { HapticSwitch(checked = settings.memory.habitInduction, enabled = enabled, onCheckedChange = { on -> vm.updateGlobalMemory { it.copy(habitInduction = on) } }) },
                )
                HorizontalDivider()
                FormItem(label = { Text("Memory model (global)") }, description = { Text("Cheap fast model recommended for background calls") }) {
                    ModelSelector(
                        modelId = settings.memory.memoryModelId,
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        allowClear = true,
                        onClear = { vm.updateGlobalMemory { it.copy(memoryModelId = null) } },
                        onSelect = { model -> vm.updateGlobalMemory { it.copy(memoryModelId = model.id) } },
                    )
                }
            }
        }

        // Per-character overrides
        assistant?.let { a ->
            Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("This character", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FormItem(
                        label = { Text("Memory for this character") },
                        description = { Text("Per-character master switch") },
                        tail = { HapticSwitch(checked = a.enableMemory, enabled = enabled, onCheckedChange = { on -> vm.updateAssistant { it.copy(enableMemory = on) } }) },
                    )
                    FormItem(
                        label = { Text("Shared user memory") },
                        description = { Text("Read and write the cross-character layer. Off = this character's memory only.") },
                        tail = { HapticSwitch(checked = a.useSharedUserMemory, enabled = enabled && a.enableMemory, onCheckedChange = { on -> vm.updateAssistant { it.copy(useSharedUserMemory = on) } }) },
                    )
                    FormItem(label = { Text("Memory model override") }, description = { Text("Overrides the global memory model for this character") }) {
                        ModelSelector(
                            modelId = a.memoryModelId,
                            providers = settings.providers,
                            type = ModelType.CHAT,
                            allowClear = true,
                            onClear = { vm.updateAssistant { it.copy(memoryModelId = null) } },
                            onSelect = { model -> vm.updateAssistant { it.copy(memoryModelId = model.id) } },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── Dialogs ───────────────────────────────

@Composable
private fun AddMemoryDialog(onDismiss: () -> Unit, onConfirm: (String, Int, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var importance by remember { mutableIntStateOf(3) }
    var pinned by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth(), placeholder = { Text("A fact to remember") })
                FormItem(label = { Text("Importance: $importance") }) {
                    androidx.compose.material3.Slider(value = importance.toFloat(), onValueChange = { importance = it.toInt() }, valueRange = 1f..5f, steps = 3)
                }
                FormItem(label = { Text("Pin") }, description = { Text("Exempt from automatic forgetting") }, tail = { HapticSwitch(checked = pinned, onCheckedChange = { pinned = it }) })
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text, importance, pinned) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WipeConfirmSheet(isGlobal: Boolean, characterName: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val expected = if (isGlobal) "everything" else (characterName ?: "everything")
    var typed by remember { mutableStateOf("") }
    var enableCountdown by remember { mutableIntStateOf(3) }
    val matched = typed.trim().equals(expected, ignoreCase = true)
    LaunchedEffect(matched) {
        if (matched) {
            enableCountdown = 3
            while (enableCountdown > 0) { delay(1000); enableCountdown-- }
        } else enableCountdown = 3
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = AppShapes.BottomSheet) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                Text("Erase memory", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (isGlobal) "This permanently erases ALL memory across every character. This cannot be undone."
                else "This permanently erases everything ${characterName ?: "this character"} remembers. The shared layer is kept. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Type “$expected” to confirm") })
            Button(
                onClick = onConfirm,
                enabled = matched && enableCountdown == 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(if (matched && enableCountdown > 0) "Erase in $enableCountdown…" else "Erase permanently")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

// ─────────────────────────────── shared bits ───────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TypeChip(type: Int) {
    val label = MemoryNodeTypeCodec.toString(type).lowercase().replaceFirstChar { it.uppercase() }
    Surface(shape = AppShapes.Chip, color = nodeTypeColor(type)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun SmallTag(text: String) {
    Surface(shape = AppShapes.Chip, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun StatusLabel(status: Int) {
    val (label, color) = when (status) {
        MemStatus.ACTIVE -> "active" to MaterialTheme.colorScheme.primary
        MemStatus.PROVISIONAL -> "provisional" to MaterialTheme.colorScheme.tertiary
        MemStatus.DORMANT -> "dormant" to MaterialTheme.colorScheme.onSurfaceVariant
        MemStatus.SUPERSEDED -> "superseded" to MaterialTheme.colorScheme.onSurfaceVariant
        MemStatus.CLOSED -> "closed" to MaterialTheme.colorScheme.onSurfaceVariant
        MemStatus.FORGOTTEN -> "forgotten" to MaterialTheme.colorScheme.error
        else -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun nodeTypeColor(type: Int): Color = when (type) {
    MemNodeType.ENTITY -> MaterialTheme.colorScheme.secondaryContainer
    MemNodeType.EPISODE -> MaterialTheme.colorScheme.tertiaryContainer
    MemNodeType.FRAME -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh
}

private fun edgeTypeLabel(type: Int): String = when (type) {
    me.rerere.rikkahub.data.db.entity.MemEdgeType.ABOUT -> "about"
    me.rerere.rikkahub.data.db.entity.MemEdgeType.SUPERSEDES -> "supersedes"
    me.rerere.rikkahub.data.db.entity.MemEdgeType.CONTRADICTS -> "contradicts"
    me.rerere.rikkahub.data.db.entity.MemEdgeType.INSTANCE_OF -> "instance of"
    me.rerere.rikkahub.data.db.entity.MemEdgeType.DERIVED_FROM -> "derived from"
    me.rerere.rikkahub.data.db.entity.MemEdgeType.IN_FRAME -> "in frame"
    else -> "relates to"
}

private fun activityIcon(kind: String) = when {
    kind.startsWith("GOAL") -> Icons.Rounded.AutoAwesome
    kind == "MERGED" || kind == "COMPRESSED" -> Icons.Rounded.Nightlight
    kind == "FORGOTTEN" || kind == "DECAYED" || kind == "EVICTED" -> Icons.Rounded.History
    kind == "PROMOTED" || kind == "PROMOTION_SUGGESTED" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.AutoAwesome
}

private fun prettyKind(kind: String): String = kind.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun isMemoryModelMissing(settings: me.rerere.rikkahub.data.datastore.Settings): Boolean {
    if (!settings.memory.enabled) return false
    val id = settings.memory.memoryModelId ?: settings.summarizerModelId ?: settings.chatModelId
    return settings.findModelById(id) == null
}

private val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private fun dayKey(millis: Long): String = dayFormat.format(Date(millis))

private fun parseFirstNodeId(json: String): String? = runCatching {
    me.rerere.rikkahub.utils.JsonInstant.decodeFromString<List<String>>(json).firstOrNull()
}.getOrNull()
