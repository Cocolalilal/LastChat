package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.util.fuzzyMemoryAgeLabel
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.MemActivityKind
import me.rerere.rikkahub.data.db.entity.MemActivityState
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.memory.MemoryGraphRepository
import me.rerere.rikkahub.data.memory.MemoryModels
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
 * The per-character memory page (user sketches 1–2): stat cards, a status pill when something is
 * happening (import/backfill/models missing), a Suggestions section only when there are pending
 * suggestions, the static graph preview + "Browse all memories", and the day-grouped expandable
 * activity feed with an "N API calls today" label. Gear FAB → per-character memory settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCharacterPage(assistantId: String, focusNodeId: String? = null) {
    val vm: MemoryVM = koinViewModel(
        key = "character_$assistantId",
        parameters = { parametersOf(assistantId) },
    )
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val importProgress by vm.importProgress.collectAsStateWithLifecycle()
    val budgetToday by vm.budgetToday.collectAsStateWithLifecycle()
    val nodeDetail by vm.nodeDetail.collectAsStateWithLifecycle()
    val graphInput by vm.graphInput.collectAsStateWithLifecycle()

    var showOverflow by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }

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

    // ---- derived stats ----
    val live = remember(nodes) { liveMemoryNodes(nodes) }
    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000
    val thisWeek = remember(live) { live.count { it.recordedAt >= weekAgo } }
    val entityCount = remember(live) { live.count { it.type == MemNodeType.ENTITY } }

    // Embedding backfill %: fraction of injectable non-entity nodes carrying an embedding.
    val injectable = remember(live) {
        live.filter { it.type != MemNodeType.ENTITY && (it.status == MemStatus.ACTIVE || it.status == MemStatus.PROVISIONAL) }
    }
    val embedPercent = if (injectable.isEmpty()) 100 else (injectable.count { it.embeddingBlob != null } * 100 / injectable.size)

    val health = remember(settings) { MemoryModels.health(settings) }

    val promotionSuggestions = remember(activity, now) {
        activity.filter {
            it.kind == MemActivityKind.PROMOTION_SUGGESTED &&
                it.state == MemActivityState.PENDING &&
                now - it.at <= 30L * 24 * 60 * 60 * 1000
        }.take(3)
    }
    val adjudicationPending = remember(nodes) { nodes.filter { it.adjudicationPending && it.status != MemStatus.SUPERSEDED } }
    val groupedActivity = remember(activity) { activity.groupBy { dayKey(it.at) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(assistant?.name?.takeIf { it.isNotBlank() } ?: "Memory", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton() },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Export memory…") },
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
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            // Round teal gear per the mockups (bottom-right).
            FloatingActionButton(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    navController.navigate(Screen.MemoryCharacterSettings(assistantId))
                },
                shape = androidx.compose.foundation.shape.CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Memory settings")
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stat cards
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Modifier.weight(1f), live.size.toString(), "memories", MaterialTheme.colorScheme.primary)
                    StatCard(Modifier.weight(1f), thisWeek.toString(), "this week", MaterialTheme.colorScheme.tertiary)
                    StatCard(Modifier.weight(1f), entityCount.toString(), "entities", MaterialTheme.colorScheme.tertiary)
                }
            }

            // Status pill — only when something needs surfacing.
            val importing = importProgress?.let { !it.completed && it.total > 0 } == true
            if (health.anyModelMissing || importing || embedPercent < 100) {
                item {
                    MemoryStatusPill(
                        health = health,
                        importing = importing,
                        importProgress = importProgress,
                        embedPercent = embedPercent,
                        onOpenModels = { navController.navigate(Screen.SettingModels) },
                    )
                }
            }

            // Suggestions — only when pending.
            if (promotionSuggestions.isNotEmpty() || adjudicationPending.isNotEmpty()) {
                item { SectionHeader("Suggestions") }
                items(promotionSuggestions, key = { "promo_${it.id}" }) { row ->
                    PromotionSuggestionCard(
                        row = row,
                        onAccept = { haptics.perform(HapticPattern.Success); vm.acceptPromotion(row.id) },
                        onDismiss = { haptics.perform(HapticPattern.Pop); vm.dismissPromotion(row.id) },
                    )
                }
                items(adjudicationPending.take(3), key = { "adj_${it.id}" }) { node ->
                    Surface(
                        onClick = { vm.openNode(node.id) },
                        shape = AppShapes.CardLarge,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Possibly duplicated or corrected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(
                                    node.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            // Memories: graph preview + browse button.
            item { SectionHeader("Memories") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MemoryGraphPreviewCard(
                        input = graphInput,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            navController.navigate(Screen.MemoryGraph(assistantId = assistantId))
                        },
                    )
                    // Quiet full-width pill (mockup), not a tonal button.
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            navController.navigate(Screen.MemoryBrowse(assistantId = assistantId))
                        },
                        shape = AppShapes.ButtonPill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Browse all memories",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Activity
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Activity")
                    Spacer(Modifier.weight(1f))
                    val calls = budgetToday.values.sum()
                    Text(
                        "$calls API ${if (calls == 1) "call" else "calls"} today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (activity.isEmpty()) {
                item { EmptyHint("No memory activity yet.") }
            } else {
                groupedActivity.entries.forEachIndexed { groupIndex, (day, rows) ->
                    if (groupIndex > 0) {
                        item(key = "day_$day") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HorizontalDivider(Modifier.weight(1f))
                                Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(Modifier.weight(1f))
                            }
                        }
                    }
                    items(rows, key = { it.id }) { row ->
                        ExpandableActivityCard(row = row, onOpenNode = { vm.openNode(it) })
                    }
                }
            }
        }
    }

    nodeDetail?.let { detail ->
        MemoryNodeSheet(
            detail = detail,
            onDismiss = { vm.closeNode() },
            onPin = { vm.setPinned(detail.node.id, !detail.node.pinned) },
            onForget = {
                vm.forget(detail.node.id); vm.closeNode()
                toaster.show("Forgotten — restore via Browse › Recently forgotten")
            },
            onRestore = { vm.restore(detail.node.id) },
            onEdit = { vm.edit(detail.node.id, it) },
            onOpenConversation = { convId ->
                runCatching { Uuid.parse(convId) }.getOrNull()?.let { navigateToChatPage(navController, chatId = it) }
            },
            onOpenGraph = { focusId ->
                vm.closeNode()
                navController.navigate(Screen.MemoryGraph(assistantId = assistantId, focusNodeId = focusId))
            },
        )
    }

    if (showWipe) {
        WipeConfirmSheet(
            isGlobal = false,
            characterName = assistant?.name?.takeIf { it.isNotBlank() },
            onDismiss = { showWipe = false },
            onConfirm = {
                haptics.perform(HapticPattern.Error)
                vm.wipeCharacter()
                showWipe = false
                toaster.show("Memory erased")
            },
        )
    }
}

/** The "Rebuilding memory · 83%" / "Set up memory models" pill under the stat cards. */
@Composable
private fun MemoryStatusPill(
    health: MemoryModels.Health,
    importing: Boolean,
    importProgress: MemoryGraphRepository.ImportProgress?,
    embedPercent: Int,
    onOpenModels: () -> Unit,
) {
    val modelsMissing = health.anyModelMissing
    val label = when {
        modelsMissing -> "Set up memory models"
        importing && importProgress != null -> {
            val pct = (importProgress.processed * 100 / importProgress.total.coerceAtLeast(1)).coerceIn(0, 99)
            "Importing memories · $pct%"
        }
        else -> "Rebuilding memory · $embedPercent%"
    }
    // Solid teal pill per the mockup ("Rebuilding memory · 83%").
    Surface(
        onClick = onOpenModels,
        enabled = modelsMissing,
        shape = AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (importing && importProgress != null) {
                LinearProgressIndicator(
                    progress = { importProgress.processed.toFloat() / importProgress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PromotionSuggestionCard(row: MemoryActivityEntity, onAccept: () -> Unit, onDismiss: () -> Unit) {
    // Teal suggestion cards per the mockup.
    Surface(shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.summary.ifBlank { "Share this with all characters?" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, shape = AppShapes.ButtonPill) { Text("Share") }
                TextButton(onClick = onDismiss, shape = AppShapes.ButtonPill) {
                    Text("Keep private", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

/** Collapsed = icon + summary + fuzzy age; expanded = kind + node links. */
@Composable
private fun ExpandableActivityCard(row: MemoryActivityEntity, onOpenNode: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val nodeIds = remember(row.id, expanded) {
        if (expanded) runCatching {
            me.rerere.rikkahub.utils.JsonInstant.decodeFromString<List<String>>(row.nodeIds)
        }.getOrDefault(emptyList()) else emptyList()
    }
    Surface(
        onClick = { expanded = !expanded },
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(activityIcon(row.kind), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    row.summary.ifBlank { "memory updated" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(fuzzyMemoryAgeLabel(row.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Text(prettyKind(row.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (nodeIds.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        nodeIds.take(4).forEach { id ->
                            Surface(
                                onClick = { onOpenNode(id) },
                                shape = AppShapes.Tag,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    "View memory",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
