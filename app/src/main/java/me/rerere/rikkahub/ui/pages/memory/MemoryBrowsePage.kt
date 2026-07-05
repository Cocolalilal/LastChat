package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.util.fuzzyMemoryAgeLabel
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

/**
 * The redesigned memory browse page. Serves two routes:
 *  - `Screen.MemoryBrowse(assistantId)` — a character's memories ("Browse all memories");
 *  - `Screen.MemoryBrowse(null)` — the **Shared Memory** page opened from Settings (GLOBAL_USER
 *    scope only; view/add/edit/forget/pin — deliberately no settings, graph or activity here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryBrowsePage(assistantId: String?) {
    val vm: MemoryVM = koinViewModel(
        key = "browse_${assistantId ?: "global"}",
        parameters = { parametersOf(assistantId ?: "") },
    )
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val nodeDetail by vm.nodeDetail.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<Int?>(null) }
    var statusFilter by remember { mutableStateOf<Int?>(null) }
    var recentlyForgotten by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    var forgotten by remember { mutableStateOf<List<MemoryNodeEntity>>(emptyList()) }
    LaunchedEffect(recentlyForgotten, nodes) {
        if (recentlyForgotten) forgotten = vm.getRecentlyForgotten()
    }

    val base = if (recentlyForgotten) forgotten else nodes.filter { it.status != MemStatus.FORGOTTEN && it.status != MemStatus.SUPERSEDED }
    val filtered = base.filter { n ->
        (query.isBlank() || n.content.contains(query, ignoreCase = true) || (n.displayLabel?.contains(query, ignoreCase = true) == true)) &&
            (typeFilter == null || n.type == typeFilter) &&
            (statusFilter == null || n.status == statusFilter)
    }

    val title = if (vm.isGlobal) "Shared Memory" else (assistant?.name?.takeIf { it.isNotBlank() } ?: "Memories")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { haptics.perform(HapticPattern.Pop); showAdd = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add memory")
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search memories") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = AppShapes.SearchField,
            )
            // Filter pills styled like the tags they filter for (mockup chip row), not generic chips.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill(
                    label = "Recently forgotten",
                    selected = recentlyForgotten,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { haptics.perform(HapticPattern.Tick); recentlyForgotten = !recentlyForgotten },
                )
                if (!recentlyForgotten) {
                    listOf(
                        MemNodeType.FACT to "Facts", MemNodeType.EPISODE to "Episodes", MemNodeType.ENTITY to "Entities",
                        MemNodeType.HABIT to "Habits", MemNodeType.GOAL to "Goals",
                    ).forEach { (t, label) ->
                        val (container, content) = nodeTypeChipColors(t)
                        FilterPill(
                            label = label,
                            selected = typeFilter == t,
                            container = container,
                            content = content,
                            onClick = { haptics.perform(HapticPattern.Tick); typeFilter = if (typeFilter == t) null else t },
                        )
                    }
                    FilterPill(
                        label = "Provisional",
                        selected = statusFilter == MemStatus.PROVISIONAL,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        dashed = true,
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            statusFilter = if (statusFilter == MemStatus.PROVISIONAL) null else MemStatus.PROVISIONAL
                        },
                    )
                    listOf(MemStatus.DORMANT to "Dormant", MemStatus.CLOSED to "Closed").forEach { (s, label) ->
                        FilterPill(
                            label = label,
                            selected = statusFilter == s,
                            container = MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { haptics.perform(HapticPattern.Tick); statusFilter = if (statusFilter == s) null else s },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyHint(
                    when {
                        recentlyForgotten -> "Nothing forgotten recently."
                        vm.isGlobal && nodes.isEmpty() -> "No shared memories yet. Facts every character should know end up here — or add one yourself."
                        else -> "No memories match."
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { node ->
                        MemoryListItem(
                            node = node,
                            showRestore = recentlyForgotten,
                            onClick = { vm.openNode(node.id) },
                            onRestore = { vm.restore(node.id); haptics.perform(HapticPattern.Success) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddMemoryDialog(
            onDismiss = { showAdd = false },
            onConfirm = { content, importance, pinned ->
                vm.addManual(content, importance, pinned)
                showAdd = false
                toaster.show(if (vm.isGlobal) "Shared memory added" else "Memory added")
            },
        )
    }

    nodeDetail?.let { detail ->
        MemoryNodeSheet(
            detail = detail,
            onDismiss = { vm.closeNode() },
            onPin = { vm.setPinned(detail.node.id, !detail.node.pinned) },
            onForget = {
                vm.forget(detail.node.id); vm.closeNode()
                toaster.show("Forgotten — restore via the Recently forgotten filter")
            },
            onRestore = { vm.restore(detail.node.id) },
            onEdit = { vm.edit(detail.node.id, it) },
            onOpenConversation = { convId ->
                runCatching { Uuid.parse(convId) }.getOrNull()?.let { navigateToChatPage(navController, chatId = it) }
            },
            onOpenGraph = if (vm.isGlobal) null else { focusId ->
                vm.closeNode()
                navController.navigate(Screen.MemoryGraph(assistantId = assistantId, focusNodeId = focusId))
            },
        )
    }
}

/** A selectable pill styled like the tag it filters for; unselected = quiet outline of that style. */
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    dashed: Boolean = false,
) {
    val baseModifier = if (dashed && !selected) {
        Modifier.dashedBorder(MaterialTheme.colorScheme.outline, cornerRadius = 50.dp)
    } else Modifier
    Surface(
        onClick = onClick,
        shape = AppShapes.Tag,
        color = if (selected) container else androidx.compose.ui.graphics.Color.Transparent,
        border = if (selected || dashed) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = baseModifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Mockup list row: title first (bold only when short), tag pills below. */
@Composable
fun MemoryListItem(
    node: MemoryNodeEntity,
    showRestore: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit,
) {
    Surface(onClick = onClick, shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val text = node.displayLabel ?: node.content
                if (text.length <= MEMORY_TITLE_MAX_CHARS) {
                    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeChip(node.type)
                    if (node.type != MemNodeType.ENTITY) RealityChip(node.reality)
                    if (node.status == MemStatus.PROVISIONAL) ProvisionalChip() else if (node.status != MemStatus.ACTIVE) StatusLabel(node.status)
                    if (node.pinned) Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }
            if (showRestore) {
                IconButton(onClick = onRestore) { Icon(Icons.Rounded.Restore, "Restore", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
