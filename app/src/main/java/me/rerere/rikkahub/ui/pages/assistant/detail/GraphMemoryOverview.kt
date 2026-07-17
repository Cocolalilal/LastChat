package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import me.rerere.ai.memory.BuiltInMemoryEngines
import me.rerere.ai.memory.MemoryScope
import me.rerere.rikkahub.data.db.dao.MemoryGraphDao
import me.rerere.rikkahub.data.db.entity.GraphEntityEntity
import me.rerere.rikkahub.data.db.entity.GraphMemoryEntity
import me.rerere.rikkahub.data.db.entity.GraphMemorySourceEntity
import me.rerere.rikkahub.data.db.entity.MemoryTransferJobEntity
import me.rerere.rikkahub.data.memory.GraphMemoryRepository
import me.rerere.rikkahub.data.memory.MemoryTransferManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryBatchPreset
import me.rerere.rikkahub.data.model.resolvedMemoryEngineId
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.compose.koinInject

@Composable
internal fun MemoryEngineSelector(
    assistant: Assistant,
    modifier: Modifier = Modifier,
) {
    val manager = koinInject<MemoryTransferManager>()
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    val active = assistant.resolvedMemoryEngineId()
    val shown = assistant.pendingMemoryEngineId ?: active
    var expanded by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf<String?>(null) }
    val labels = mapOf(
        BuiltInMemoryEngines.OFF to "Off",
        BuiltInMemoryEngines.SIMPLE to "Simple Memory",
        BuiltInMemoryEngines.GRAPH to "Graph Memory",
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Memory system", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                haptics.perform(HapticPattern.Pop)
                expanded = true
            },
            shape = AppShapes.ButtonRounded,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (shown == BuiltInMemoryEngines.GRAPH) Icons.Rounded.AccountTree else Icons.Rounded.Memory, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(labels[shown].orEmpty(), fontWeight = FontWeight.SemiBold)
                    if (assistant.pendingMemoryEngineId != null) {
                        Text("Preparing in the background · ${labels[active]} stays active", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Icon(Icons.Rounded.MoreHoriz, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            if (id != shown) requested = id
                        },
                    )
                }
            }
        }
    }

    requested?.let { target ->
        AlertDialog(
            onDismissRequest = { requested = null },
            title = { Text("Switch to ${labels[target]}?") },
            text = {
                Text(
                    if (target == BuiltInMemoryEngines.OFF) {
                        "Memory data will be kept, but it will not be used until you turn memory back on."
                    } else {
                        "Existing data will be copied and kept in both systems. ${labels[active]} will keep working until the background transfer is complete."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    haptics.perform(HapticPattern.Success)
                    scope.launch { manager.switchEngine(assistant.id, target) }
                    requested = null
                }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { requested = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun MemoryOffOverview(assistant: Assistant) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        MemoryEngineSelector(assistant)
        Surface(shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(32.dp))
                Text("Memory is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Your existing memory data is still stored. Choose a memory system above whenever you want to use it again.")
            }
        }
    }
}

@Composable
internal fun GraphMemoryOverview(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    initialMemoryId: String? = null,
) {
    val repository = koinInject<GraphMemoryRepository>()
    val dao = koinInject<MemoryGraphDao>()
    val transferManager = koinInject<MemoryTransferManager>()
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    val memories by repository.observeMemories(assistant.id.toString()).collectAsState(emptyList())
    val entities by repository.observeEntities(assistant.id.toString()).collectAsState(emptyList())
    val allLinks by repository.observeLinks().collectAsState(emptyList())
    val activity by repository.observeActivity(assistant.id.toString()).collectAsState(emptyList())
    val transfer by dao.observeLatestTransfer(assistant.id.toString()).collectAsState(null)
    val modelOps by dao.observeModelOperations(
        assistant.id.toString(),
        System.currentTimeMillis() - 24 * 60 * 60 * 1000L,
    ).collectAsState(0)
    val memoryIds = remember(memories) { memories.mapTo(hashSetOf()) { it.id } }
    val entityIds = remember(entities) { entities.mapTo(hashSetOf()) { it.id } }
    val links = remember(allLinks, memoryIds, entityIds) {
        allLinks.filter { it.memoryId in memoryIds && it.entityId in entityIds }
    }
    var browseOpen by remember(initialMemoryId) { mutableStateOf(initialMemoryId != null) }
    var graphOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var selectedMemory by remember { mutableStateOf<GraphMemoryEntity?>(null) }
    var selectedEntity by remember { mutableStateOf<GraphEntityEntity?>(null) }
    val now = System.currentTimeMillis()
    val thisWeek = memories.count { it.createdAt >= now - 7 * 24 * 60 * 60 * 1000L }

    LaunchedEffect(transfer?.id, transfer?.state, transfer?.updatedAt) {
        val queued = transfer?.takeIf { it.state == "QUEUED" && it.stage == "PREPARING" }
            ?: return@LaunchedEffect
        delay(30_000)
        transferManager.recoverStaleQueued(queued.id, queued.updatedAt)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MemoryEngineSelector(assistant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GraphStat("${memories.size}", "memories", MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 10.dp, bottomEnd = 10.dp), Modifier.weight(1f))
                GraphStat("$thisWeek", "this week", MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp), Modifier.weight(1f))
                GraphStat("${entities.size}", "entities", MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp, topStart = 10.dp, bottomStart = 10.dp), Modifier.weight(1f))
            }
            GraphStatusCard(
                assistant = assistant,
                transfer = transfer,
                onPause = { id -> scope.launch { transferManager.pause(id) } },
                onResume = { id -> scope.launch { transferManager.resume(id) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Memory graph", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "See how memories and entities connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(224.dp).clickable {
                    haptics.perform(HapticPattern.Pop)
                    graphOpen = true
                },
                shape = AppShapes.CardLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box {
                    GraphCanvas(
                        memories = memories,
                        entities = entities,
                        links = links,
                        showLabels = false,
                        interactive = false,
                        maxNodes = 34,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                        shape = AppShapes.ButtonPill,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .96f),
                        shadowElevation = 3.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountTree, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Explore graph", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Button(onClick = { browseOpen = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = AppShapes.ButtonRounded) {
                Text("Browse all memories")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$modelOps model operations today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (activity.isEmpty()) {
                Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("New memories and changes will appear here.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    activity.take(12).forEachIndexed { index, item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedMemory = memories.firstOrNull { it.id == item.objectId }
                            },
                            shape = when {
                                activity.take(12).size == 1 -> AppShapes.CardMedium
                                index == 0 -> AppShapes.ListItemFirst
                                index == activity.take(12).lastIndex -> AppShapes.ListItemLast
                                else -> AppShapes.ListItemMiddle
                            },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(item.event.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp).clickable { settingsOpen = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp,
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Settings, "Graph memory settings") } }
    }

    if (browseOpen) {
        GraphMemoryBrowser(
            memories = memories,
            entities = entities,
            onClose = { browseOpen = false },
            onMemory = { selectedMemory = it },
            onEntity = { selectedEntity = it },
            onForgetMemory = { repository.forget(it.id) },
            onForgetEntity = { repository.forgetEntity(it.id) },
            repository = repository,
            assistantId = assistant.id.toString(),
            initialMemoryId = initialMemoryId,
        )
    }
    if (graphOpen) {
        FullGraphBrowser(
            memories = memories,
            entities = entities,
            links = links,
            onClose = { graphOpen = false },
            onMemory = { id -> selectedMemory = memories.firstOrNull { it.id == id } },
            onEntity = { id -> selectedEntity = entities.firstOrNull { it.id == id } },
        )
    }
    selectedMemory?.let { memory ->
        GraphMemoryDetail(
            memory = memory,
            repository = repository,
            onDismiss = { selectedMemory = null },
            onUpdated = { selectedMemory = it },
            onForgotten = { selectedMemory = null },
        )
    }
    selectedEntity?.let { entity ->
        GraphEntityDetail(
            entity = entity,
            connectedMemories = memories.filter { memory ->
                links.any { link -> link.entityId == entity.id && link.memoryId == memory.id }
            },
            onMemory = { memory ->
                selectedEntity = null
                selectedMemory = memory
            },
            onDismiss = { selectedEntity = null },
            onForget = {
                scope.launch {
                    repository.forgetEntity(entity.id)
                    selectedEntity = null
                }
            },
        )
    }
    if (settingsOpen) {
        GraphMemorySettingsSheet(
            assistant = assistant,
            onUpdateAssistant = onUpdateAssistant,
            onDismiss = { settingsOpen = false },
        )
    }
}

@Composable
private fun GraphStatusCard(
    assistant: Assistant,
    transfer: MemoryTransferJobEntity?,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
) {
    val activeTransfer = transfer?.takeIf { it.state in setOf("QUEUED", "RUNNING", "PAUSED", "FAILED") }
    val color = if (activeTransfer?.state == "FAILED") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(shape = AppShapes.ButtonPill, color = color, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (activeTransfer?.state == "FAILED") Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            else Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (activeTransfer?.state) {
                        "QUEUED" -> "Preparing graph memory"
                        "RUNNING" -> if (activeTransfer.total <= 0) {
                            "Starting graph memory"
                        } else {
                            "Building graph memory · ${progressPercent(activeTransfer)}%"
                        }
                        "PAUSED" -> "Memory transfer paused"
                        "FAILED" -> "Memory transfer failed"
                        else -> if (assistant.pendingMemoryEngineId != null) "Preparing graph memory" else "Memory is ready"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                activeTransfer?.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            }
            if (activeTransfer?.state == "RUNNING") {
                TextButton(onClick = { onPause(activeTransfer.id) }) { Text("Pause") }
            } else if (activeTransfer?.state in setOf("QUEUED", "PAUSED", "FAILED")) {
                val resumableTransfer = requireNotNull(activeTransfer)
                TextButton(onClick = { onResume(resumableTransfer.id) }) {
                    Text(if (resumableTransfer.state == "QUEUED") "Retry" else "Resume")
                }
            }
        }
        if (activeTransfer?.state == "RUNNING") {
            if (activeTransfer.total > 0) {
                LinearProgressIndicator(
                    progress = { progressPercent(activeTransfer) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun progressPercent(job: MemoryTransferJobEntity): Int = if (job.total <= 0) 0 else (job.processed * 100 / job.total).coerceIn(0, 100)

@Composable
private fun GraphStat(value: String, label: String, color: Color, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.height(92.dp), shape = shape, color = color) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private enum class GraphNodeKind { MEMORY, ENTITY }

private data class GraphVisualNode(
    val id: String,
    val label: String,
    val kind: GraphNodeKind,
)

private data class GraphVisualEdge(val start: Int, val end: Int)

private data class GraphVisualModel(
    val nodes: List<GraphVisualNode>,
    val edges: List<GraphVisualEdge>,
    val degree: IntArray,
)

private class GraphParticle(
    var x: Float,
    var y: Float,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
)

private fun buildGraphVisualModel(
    memories: List<GraphMemoryEntity>,
    entities: List<GraphEntityEntity>,
    links: List<me.rerere.rikkahub.data.db.entity.GraphMemoryEntityLinkEntity>,
    maxNodes: Int,
): GraphVisualModel {
    val memoriesById = memories.associateBy { it.id }
    val entitiesById = entities.associateBy { it.id }
    val selected = linkedSetOf<String>()
    val validLinks = links.filter { it.memoryId in memoriesById && it.entityId in entitiesById }
    val memoryDegree = validLinks.groupingBy { it.memoryId }.eachCount()
    val entityDegree = validLinks.groupingBy { it.entityId }.eachCount()

    // Build outward from the most meaningful hubs. Database order should not decide which
    // part of a large graph happens to be visible in the bounded preview/full graph budget.
    validLinks.sortedByDescending { link ->
        memoryDegree.getOrDefault(link.memoryId, 0) + entityDegree.getOrDefault(link.entityId, 0)
    }.forEach { link ->
        if (selected.size < maxNodes) selected += "m:${link.memoryId}"
        if (selected.size < maxNodes) selected += "e:${link.entityId}"
    }
    memories.forEach { if (selected.size < maxNodes) selected += "m:${it.id}" }
    entities.forEach { if (selected.size < maxNodes) selected += "e:${it.id}" }

    val nodes = selected.mapNotNull { key ->
        when {
            key.startsWith("m:") -> memoriesById[key.removePrefix("m:")]?.let {
                GraphVisualNode(key, it.content, GraphNodeKind.MEMORY)
            }
            else -> entitiesById[key.removePrefix("e:")]?.let {
                GraphVisualNode(key, it.canonicalName, GraphNodeKind.ENTITY)
            }
        }
    }
    val indexById = nodes.mapIndexed { index, node -> node.id to index }.toMap()
    val edges = validLinks.mapNotNull { link ->
        val start = indexById["m:${link.memoryId}"] ?: return@mapNotNull null
        val end = indexById["e:${link.entityId}"] ?: return@mapNotNull null
        GraphVisualEdge(start, end)
    }.distinct().take(maxNodes * 3)
    val degree = IntArray(nodes.size)
    edges.forEach { edge ->
        degree[edge.start]++
        degree[edge.end]++
    }
    return GraphVisualModel(nodes, edges, degree)
}

private fun createGraphParticles(model: GraphVisualModel): MutableList<GraphParticle> {
    if (model.nodes.isEmpty()) return mutableListOf()
    val particles = model.nodes.mapIndexedTo(mutableListOf()) { index, _ ->
        val angle = (2.0 * PI * index / model.nodes.size) - PI / 2
        val radius = .3f + ((index * 37) % 53) / 100f
        GraphParticle(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
    }
    repeat(180) { relaxGraph(model, particles, damping = .78f) }
    val centerX = particles.map { it.x }.average().toFloat()
    val centerY = particles.map { it.y }.average().toFloat()
    val extent = particles.maxOfOrNull { hypot(it.x - centerX, it.y - centerY) }
        ?.coerceAtLeast(.001f) ?: 1f
    val fitScale = .96f / extent
    particles.forEach { particle ->
        particle.x = (particle.x - centerX) * fitScale
        particle.y = (particle.y - centerY) * fitScale
        particle.velocityX = 0f
        particle.velocityY = 0f
    }
    return particles
}

private fun relaxGraph(
    model: GraphVisualModel,
    particles: MutableList<GraphParticle>,
    damping: Float = .86f,
    pinnedNode: Int? = null,
) {
    if (particles.size < 2) return
    val forceX = FloatArray(particles.size)
    val forceY = FloatArray(particles.size)

    for (first in 0 until particles.lastIndex) {
        for (second in first + 1 until particles.size) {
            val dx = particles[second].x - particles[first].x
            val dy = particles[second].y - particles[first].y
            val distanceSquared = max(dx * dx + dy * dy, .0025f)
            val distance = sqrt(distanceSquared)
            val repulsion = .0065f / distanceSquared
            val fx = dx / distance * repulsion
            val fy = dy / distance * repulsion
            forceX[first] -= fx
            forceY[first] -= fy
            forceX[second] += fx
            forceY[second] += fy
        }
    }
    model.edges.forEach { edge ->
        val first = particles[edge.start]
        val second = particles[edge.end]
        val dx = second.x - first.x
        val dy = second.y - first.y
        val distance = max(hypot(dx, dy), .001f)
        val desiredLength = .38f + .045f / max(model.degree[edge.start] + model.degree[edge.end], 1)
        val spring = (distance - desiredLength) * .034f
        val fx = dx / distance * spring
        val fy = dy / distance * spring
        forceX[edge.start] += fx
        forceY[edge.start] += fy
        forceX[edge.end] -= fx
        forceY[edge.end] -= fy
    }
    particles.forEachIndexed { index, particle ->
        if (index == pinnedNode) return@forEachIndexed
        forceX[index] -= particle.x * .0035f
        forceY[index] -= particle.y * .0035f
        particle.velocityX = ((particle.velocityX + forceX[index]) * damping).coerceIn(-.075f, .075f)
        particle.velocityY = ((particle.velocityY + forceY[index]) * damping).coerceIn(-.075f, .075f)
        particle.x += particle.velocityX
        particle.y += particle.velocityY
    }
}

private fun stabilizeGraphParticles(
    particles: MutableList<GraphParticle>,
    maxExtent: Float = 1.22f,
) {
    if (particles.isEmpty()) return
    val centerX = particles.sumOf { it.x.toDouble() }.toFloat() / particles.size
    val centerY = particles.sumOf { it.y.toDouble() }.toFloat() / particles.size
    particles.forEach { particle ->
        particle.x -= centerX
        particle.y -= centerY
    }
    val extent = particles.maxOfOrNull { hypot(it.x, it.y) } ?: return
    if (extent > maxExtent) {
        val shrink = maxExtent / extent
        particles.forEach { particle ->
            particle.x *= shrink
            particle.y *= shrink
            particle.velocityX *= shrink
            particle.velocityY *= shrink
        }
    }
}

@Composable
private fun GraphCanvas(
    memories: List<GraphMemoryEntity>,
    entities: List<GraphEntityEntity>,
    links: List<me.rerere.rikkahub.data.db.entity.GraphMemoryEntityLinkEntity>,
    showLabels: Boolean,
    interactive: Boolean,
    maxNodes: Int = if (interactive) 72 else 42,
    onNodeClick: ((GraphVisualNode) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    val motionPolicy = LocalMotionPolicy.current
    val currentOnNodeClick by rememberUpdatedState(onNodeClick)
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val edge = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val model = remember(memories, entities, links, maxNodes) {
        buildGraphVisualModel(memories, entities, links, maxNodes = maxNodes)
    }
    // Keep the prepared layout tied to the remembered visual model. A temporary asynchronous
    // loading state is fragile here because Room can emit several graph snapshots in quick
    // succession, repeatedly blanking the preview and starving the full-screen graph.
    val particles = remember(model) { createGraphParticles(model) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var translation by remember(model) { mutableStateOf(Offset.Zero) }
    var selectedNode by remember(model) { mutableStateOf<Int?>(null) }
    var renderTick by remember { mutableIntStateOf(0) }
    var reheatToken by remember { mutableIntStateOf(0) }
    var cameraJob by remember { mutableStateOf<Job?>(null) }
    var showHint by remember(model, interactive) { mutableStateOf(interactive) }

    // A single unit on both axes is important: deriving X and Y independently made the graph
    // visibly stretch on tall phones.
    fun graphUnit(): Float = min(viewportSize.width, viewportSize.height) * if (interactive) .4f else .36f

    fun worldToScreen(index: Int): Offset {
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f) + translation
        val particle = particles[index]
        return center + Offset(particle.x, particle.y) * graphUnit() * scale
    }

    fun boundedTranslation(value: Offset, cameraScale: Float): Offset {
        val horizontalLimit = viewportSize.width * max(cameraScale, 1f) * .9f
        val verticalLimit = viewportSize.height * max(cameraScale, 1f) * .9f
        return Offset(
            value.x.coerceIn(-horizontalLimit, horizontalLimit),
            value.y.coerceIn(-verticalLimit, verticalLimit),
        )
    }

    fun animateCamera(targetScale: Float, targetTranslation: Offset, durationMillis: Int = 360) {
        cameraJob?.cancel()
        cameraJob = coroutineScope.launch {
            val finalScale = targetScale.coerceIn(.55f, 4f)
            val finalTranslation = boundedTranslation(targetTranslation, finalScale)
            if (motionPolicy.reduceMotion) {
                scale = finalScale
                translation = finalTranslation
                return@launch
            }
            val startScale = scale
            val startTranslation = translation
            Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            ) {
                scale = startScale + (finalScale - startScale) * value
                translation = Offset(
                    startTranslation.x + (finalTranslation.x - startTranslation.x) * value,
                    startTranslation.y + (finalTranslation.y - startTranslation.y) * value,
                )
            }
        }
    }

    fun focusNode(index: Int) {
        val particle = particles.getOrNull(index) ?: return
        val targetScale = max(scale, 1.75f).coerceAtMost(2.6f)
        val targetTranslation = -Offset(particle.x, particle.y) * graphUnit() * targetScale
        animateCamera(targetScale, targetTranslation)
    }

    LaunchedEffect(interactive, model) {
        if (!interactive) return@LaunchedEffect
        delay(4_500)
        showHint = false
    }

    LaunchedEffect(model, reheatToken, interactive) {
        if (!interactive || reheatToken == 0) return@LaunchedEffect
        if (motionPolicy.reduceMotion) {
            repeat(100) { relaxGraph(model, particles, damping = .78f) }
            stabilizeGraphParticles(particles)
            renderTick++
        } else {
            repeat(150) {
                withFrameNanos { }
                repeat(2) { relaxGraph(model, particles) }
                stabilizeGraphParticles(particles)
                renderTick++
            }
        }
    }

    val interactionModifier = if (!interactive) Modifier else Modifier.pointerInput(model, viewportSize) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val hitRadius = with(density) { 32.dp.toPx() }
            var nodeIndex = model.nodes.indices
                .minByOrNull { (worldToScreen(it) - down.position).getDistance() }
                ?.takeIf { (worldToScreen(it) - down.position).getDistance() <= hitRadius }
            var didDrag = false
            var dragDistance = 0f

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                if (pressed.size >= 2) {
                    cameraJob?.cancel()
                    nodeIndex = null
                    didDrag = true
                    val currentCentroid = pressed
                        .map { it.position }
                        .reduce(Offset::plus) / pressed.size.toFloat()
                    val previousCentroid = pressed
                        .map { it.previousPosition }
                        .reduce(Offset::plus) / pressed.size.toFloat()
                    val currentSpan = pressed.sumOf {
                        (it.position - currentCentroid).getDistance().toDouble()
                    }.toFloat() / pressed.size
                    val previousSpan = pressed.sumOf {
                        (it.previousPosition - previousCentroid).getDistance().toDouble()
                    }.toFloat() / pressed.size
                    val oldScale = scale
                    val zoom = if (previousSpan > 1f) currentSpan / previousSpan else 1f
                    val newScale = (oldScale * zoom).coerceIn(.55f, 4f)
                    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                    val graphVector = previousCentroid - viewportCenter - translation
                    translation = currentCentroid - viewportCenter - graphVector * (newScale / oldScale)
                    translation = boundedTranslation(translation, newScale)
                    scale = newScale
                    event.changes.forEach { it.consume() }
                } else {
                    val change = pressed.first()
                    val delta = change.position - change.previousPosition
                    dragDistance += delta.getDistance()
                    if (!didDrag && dragDistance >= viewConfiguration.touchSlop) {
                        cameraJob?.cancel()
                        didDrag = true
                        if (nodeIndex != null) {
                            selectedNode = nodeIndex
                            showHint = false
                            haptics.perform(HapticPattern.DragStart)
                        }
                    }
                    if (didDrag) {
                        if (nodeIndex != null) {
                            val unit = graphUnit()
                            if (unit > 0f) {
                                particles[nodeIndex].x += delta.x / (unit * scale)
                                particles[nodeIndex].y += delta.y / (unit * scale)
                                particles[nodeIndex].velocityX = 0f
                                particles[nodeIndex].velocityY = 0f
                                repeat(3) { relaxGraph(model, particles, pinnedNode = nodeIndex) }
                                renderTick++
                            }
                        } else {
                            translation = boundedTranslation(translation + delta, scale)
                        }
                        change.consume()
                    }
                }
            }

            if (nodeIndex != null && !didDrag) {
                haptics.perform(HapticPattern.Pop)
                showHint = false
                if (selectedNode == nodeIndex) {
                    currentOnNodeClick?.invoke(model.nodes[nodeIndex])
                } else {
                    selectedNode = nodeIndex
                    focusNode(nodeIndex)
                }
            } else if (nodeIndex != null) {
                haptics.perform(HapticPattern.DragEnd)
                reheatToken++
            } else if (!didDrag) {
                selectedNode = null
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { viewportSize = it }
            .semantics {
                contentDescription = if (interactive) {
                    "Interactive memory graph. Drag the background to move, pinch to zoom, or drag a node to rearrange it."
                } else {
                    "Memory graph preview"
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().then(interactionModifier)) {
        @Suppress("UNUSED_EXPRESSION")
        renderTick
        if (model.nodes.isEmpty() || viewportSize == IntSize.Zero) return@Canvas
        val positions = model.nodes.indices.map(::worldToScreen)
        model.edges.forEach { graphEdge ->
            val connectedToSelection = selectedNode == graphEdge.start || selectedNode == graphEdge.end
            drawLine(
                color = if (connectedToSelection) primary.copy(alpha = .78f) else edge.copy(alpha = if (interactive) .3f else .2f),
                start = positions[graphEdge.start],
                end = positions[graphEdge.end],
                strokeWidth = if (connectedToSelection) 2.8f else if (interactive) 1.5f else 1.1f,
            )
        }
        model.nodes.forEachIndexed { index, node ->
            val point = positions[index]
            val isEntity = node.kind == GraphNodeKind.ENTITY
            val isSelected = selectedNode == index
            val degreeBoost = sqrt(model.degree[index].toFloat()).coerceAtMost(3f)
            val zoomBoost = if (interactive) sqrt(scale).coerceIn(.9f, 1.35f) else 1f
            val nodeRadius = ((if (isEntity) 6.4f else 5.2f) +
                degreeBoost * if (isEntity) 1.25f else .7f) * zoomBoost +
                if (isSelected) 2.5f else 0f
            drawCircle(
                color = if (isEntity) secondary else primary,
                radius = nodeRadius.dp.toPx(),
                center = point,
            )
            if (isEntity || isSelected) {
                drawCircle(
                    color = labelColor.copy(alpha = if (isSelected) .58f else .18f),
                    radius = nodeRadius.dp.toPx() + 3.dp.toPx(),
                    center = point,
                    style = Stroke(if (isSelected) 2.2.dp.toPx() else 1.dp.toPx()),
                )
            }
        }

        if (showLabels) {
            val labelLimit = when {
                scale >= 2.2f -> 12
                scale >= 1.4f -> 8
                else -> 5
            }
            val candidates = model.nodes.indices
                .filter { index ->
                    index == selectedNode ||
                        (model.nodes[index].kind == GraphNodeKind.ENTITY && model.degree[index] >= if (scale >= 1.4f) 2 else 3)
                }
                .sortedByDescending { if (it == selectedNode) Int.MAX_VALUE else model.degree[it] }
            val labelIndexes = mutableListOf<Int>()
            val minimumSpacing = 74.dp.toPx()
            candidates.forEach { index ->
                if (labelIndexes.size < labelLimit && (index == selectedNode || labelIndexes.all {
                        (positions[it] - positions[index]).getDistance() >= minimumSpacing
                    })) {
                    labelIndexes += index
                }
            }
            val textPaint = android.graphics.Paint().apply {
                color = labelColor.toArgb()
                textSize = 12.dp.toPx()
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val backgroundPaint = android.graphics.Paint().apply {
                color = labelBackground.copy(alpha = .94f).toArgb()
                isAntiAlias = true
            }
            labelIndexes.reversed().forEach { index ->
                val label = model.nodes[index].label.replace('\n', ' ').take(if (index == selectedNode) 42 else 24)
                val point = positions[index]
                val width = textPaint.measureText(label)
                val left = (point.x + 12.dp.toPx()).coerceIn(6.dp.toPx(), size.width - width - 10.dp.toPx())
                val baseline = (point.y + 4.dp.toPx()).coerceIn(18.dp.toPx(), size.height - 8.dp.toPx())
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    left - 5.dp.toPx(),
                    baseline - 15.dp.toPx(),
                    left + width + 5.dp.toPx(),
                    baseline + 5.dp.toPx(),
                    7.dp.toPx(),
                    7.dp.toPx(),
                    backgroundPaint,
                )
                drawContext.canvas.nativeCanvas.drawText(label, left, baseline, textPaint)
            }
        }
        }

        if (model.nodes.isEmpty()) {
            Text(
                "The memory graph will grow here as memories are learned.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (interactive && model.nodes.isNotEmpty()) {
            AnimatedVisibility(
                visible = showHint,
                modifier = Modifier.align(Alignment.TopCenter).padding(14.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    shape = AppShapes.ButtonPill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .94f),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        "Drag nodes  ·  Pinch to zoom",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                shape = AppShapes.CardMedium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .96f),
                shadowElevation = 4.dp,
            ) {
                Column {
                    IconButton(
                        onClick = {
                            val targetScale = (scale * 1.3f).coerceAtMost(4f)
                            animateCamera(targetScale, translation * (targetScale / scale), 220)
                        },
                    ) { Icon(Icons.Rounded.Add, "Zoom in") }
                    HorizontalDivider(Modifier.width(40.dp).align(Alignment.CenterHorizontally))
                    IconButton(
                        onClick = {
                            val targetScale = (scale / 1.3f).coerceAtLeast(.55f)
                            animateCamera(targetScale, translation * (targetScale / scale), 220)
                        },
                    ) { Icon(Icons.Rounded.Remove, "Zoom out") }
                    HorizontalDivider(Modifier.width(40.dp).align(Alignment.CenterHorizontally))
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            selectedNode = null
                            animateCamera(1f, Offset.Zero)
                        },
                    ) { Icon(Icons.Rounded.CenterFocusStrong, "Center graph") }
                }
            }

            AnimatedVisibility(
                visible = selectedNode != null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 80.dp, bottom = 16.dp)
                    .widthIn(max = 300.dp),
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
            ) {
                val node = selectedNode?.let(model.nodes::getOrNull)
                if (node != null) {
                    Surface(
                        modifier = Modifier.clickable {
                            haptics.perform(HapticPattern.Pop)
                            currentOnNodeClick?.invoke(node)
                        },
                        shape = AppShapes.CardMedium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .97f),
                        shadowElevation = 5.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (node.kind == GraphNodeKind.ENTITY) Icons.Rounded.Person else Icons.Rounded.Memory,
                                null,
                                tint = if (node.kind == GraphNodeKind.ENTITY) secondary else primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (node.kind == GraphNodeKind.ENTITY) "Entity" else "Memory",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(node.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Rounded.ChevronRight, "Open details")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullGraphBrowser(
    memories: List<GraphMemoryEntity>,
    entities: List<GraphEntityEntity>,
    links: List<me.rerere.rikkahub.data.db.entity.GraphMemoryEntityLinkEntity>,
    onClose: () -> Unit,
    onMemory: (String) -> Unit,
    onEntity: (String) -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "Back") }
                        Column {
                            Text("Memory graph", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "${memories.size} memories  ·  ${entities.size} entities",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Surface(
                Modifier.fillMaxSize().padding(padding),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                GraphCanvas(
                    memories = memories,
                    entities = entities,
                    links = links,
                    showLabels = true,
                    interactive = true,
                    maxNodes = 84,
                    onNodeClick = { node ->
                        when (node.kind) {
                            GraphNodeKind.MEMORY -> onMemory(node.id.removePrefix("m:"))
                            GraphNodeKind.ENTITY -> onEntity(node.id.removePrefix("e:"))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun GraphMemoryBrowser(
    memories: List<GraphMemoryEntity>,
    entities: List<GraphEntityEntity>,
    onClose: () -> Unit,
    onMemory: (GraphMemoryEntity) -> Unit,
    onEntity: (GraphEntityEntity) -> Unit,
    onForgetMemory: suspend (GraphMemoryEntity) -> Unit,
    onForgetEntity: suspend (GraphEntityEntity) -> Unit,
    repository: GraphMemoryRepository,
    assistantId: String,
    initialMemoryId: String? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var newMemory by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val toaster = LocalToaster.current
    val filteredMemories = memories.filter { it.content.contains(query, true) }
    val filteredEntities = entities.filter { it.canonicalName.contains(query, true) }
    LaunchedEffect(initialMemoryId, memories) {
        val targetIndex = memories.indexOfFirst { it.id == initialMemoryId }
        if (targetIndex >= 0) {
            tab = 0
            query = ""
            listState.scrollToItem(targetIndex)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "Back") }
                            Text("Browse memory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Add memory") }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            placeholder = { Text("Search memories and entities") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = AppShapes.SearchField,
                            singleLine = true,
                        )
                        TabRow(tab) {
                            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Memories (${filteredMemories.size})") })
                            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Entities (${filteredEntities.size})") })
                        }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (tab == 0) {
                    itemsIndexed(filteredMemories, key = { _, it -> it.id }) { index, memory ->
                        PhysicsSwipeToDelete(
                            onDelete = {
                                scope.launch {
                                    onForgetMemory(memory)
                                    toaster.show("Memory forgotten", action = ToastAction("Undo") {
                                        scope.launch { repository.addManual(MemoryScope(assistantId), memory.content) }
                                    })
                                }
                            },
                            position = listPosition(index, filteredMemories.size),
                        ) { shape ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onMemory(memory) },
                                shape = shape,
                                color = if (memory.id == initialMemoryId) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(memory.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(memory.origin.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(filteredEntities, key = { _, it -> it.id }) { index, entity ->
                        PhysicsSwipeToDelete(
                            onDelete = { scope.launch { onForgetEntity(entity) } },
                            position = listPosition(index, filteredEntities.size),
                        ) { shape ->
                            Surface(
                                shape = shape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth().clickable { onEntity(entity) },
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(entity.canonicalName)
                                        Text(entity.entityType.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add memory") },
            text = { OutlinedTextField(newMemory, { newMemory = it }, minLines = 3, placeholder = { Text("What should this character remember?") }) },
            confirmButton = {
                Button(
                    enabled = newMemory.isNotBlank(),
                    onClick = {
                        scope.launch { repository.addManual(MemoryScope(assistantId), newMemory) }
                        newMemory = ""
                        adding = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GraphEntityDetail(
    entity: GraphEntityEntity,
    connectedMemories: List<GraphMemoryEntity>,
    onMemory: (GraphMemoryEntity) -> Unit,
    onDismiss: () -> Unit,
    onForget: () -> Unit,
) {
    val aliases = remember(entity.aliasesJson) {
        runCatching { JsonInstant.decodeFromString<List<String>>(entity.aliasesJson) }
            .getOrDefault(emptyList())
            .filter { it.isNotBlank() && !it.equals(entity.canonicalName, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = AppShapes.BottomSheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entity.canonicalName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemoryChip(entity.entityType.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase))
                        MemoryChip("${connectedMemories.size} ${if (connectedMemories.size == 1) "memory" else "memories"}")
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
            }

            if (aliases.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Also known as", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        aliases.take(6).forEach { alias -> MemoryChip(alias) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connected memories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (connectedMemories.isEmpty()) {
                    Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text(
                            "This entity is not connected to a visible memory.",
                            Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        connectedMemories.forEachIndexed { index, memory ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onMemory(memory) },
                                shape = when {
                                    connectedMemories.size == 1 -> AppShapes.ListItem
                                    index == 0 -> AppShapes.ListItemFirst
                                    index == connectedMemories.lastIndex -> AppShapes.ListItemLast
                                    else -> AppShapes.ListItemMiddle
                                },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(memory.content, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onForget,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.ButtonRounded,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Forget entity")
            }
        }
    }
}

private fun listPosition(index: Int, size: Int): ItemPosition = when {
    size <= 1 -> ItemPosition.ONLY
    index == 0 -> ItemPosition.FIRST
    index == size - 1 -> ItemPosition.LAST
    else -> ItemPosition.MIDDLE
}

@Composable
private fun GraphMemoryDetail(
    memory: GraphMemoryEntity,
    repository: GraphMemoryRepository,
    onDismiss: () -> Unit,
    onUpdated: (GraphMemoryEntity) -> Unit,
    onForgotten: () -> Unit,
) {
    var sources by remember(memory.id) { mutableStateOf<List<GraphMemorySourceEntity>>(emptyList()) }
    var relatedEntities by remember(memory.id) { mutableStateOf<List<GraphEntityEntity>>(emptyList()) }
    var relatedLinks by remember(memory.id) { mutableStateOf<List<me.rerere.rikkahub.data.db.entity.GraphMemoryEntityLinkEntity>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember(memory.id) { mutableStateOf(memory.content) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(memory.id) {
        sources = repository.sources(memory.id)
        val snapshot = repository.snapshot(memory.assistantId)
        relatedLinks = snapshot.links.filter { it.memoryId == memory.id }
        val ids = relatedLinks.mapTo(hashSetOf()) { it.entityId }
        relatedEntities = snapshot.entities.filter { it.id in ids }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = AppShapes.BottomSheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(memory.content, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemoryChip(memory.origin.lowercase().replaceFirstChar(Char::uppercase))
                        memory.attributedTo?.let { MemoryChip(it.lowercase().replaceFirstChar(Char::uppercase)) }
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
            }
            Surface(shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().height(180.dp)) {
                GraphCanvas(
                    memories = listOf(memory),
                    entities = relatedEntities,
                    links = relatedLinks,
                    showLabels = false,
                    interactive = false,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (sources.isEmpty()) Text("Added manually", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sources.isNotEmpty()) {
                    Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sources.forEach { source ->
                                Row {
                                    Surface(Modifier.width(3.dp).height(52.dp), color = MaterialTheme.colorScheme.primary, shape = AppShapes.ButtonPill) {}
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("“${source.excerpt}”", maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        Text(source.sourceType.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { editing = true }, modifier = Modifier.weight(1f), shape = AppShapes.ButtonRounded) {
                    Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit")
                }
                Button(
                    onClick = { scope.launch { repository.forget(memory.id); onForgotten() } },
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.ButtonRounded,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) { Text("Forget") }
            }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit memory") },
            text = { OutlinedTextField(editText, { editText = it }, minLines = 3, maxLines = 8) },
            confirmButton = {
                Button(onClick = {
                    scope.launch { onUpdated(repository.update(memory.id, editText)); editing = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MemoryChip(text: String) {
    Surface(shape = AppShapes.Chip, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GraphMemorySettingsSheet(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = AppShapes.BottomSheet) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Graph memory settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SettingSwitch("Learn from chats", "Extract durable memories after messages are sent.", assistant.graphLearnFromChats) {
                onUpdateAssistant(assistant.copy(graphLearnFromChats = it))
            }
            SettingSwitch("Deep memory search tool", "Lets the assistant deliberately search deeper when automatic recall is not enough.", assistant.graphSearchToolEnabled) {
                onUpdateAssistant(assistant.copy(graphSearchToolEnabled = it))
            }
            Text("Background batching", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MemoryBatchPreset.entries.forEach { preset ->
                    val selected = assistant.memoryBatchPreset == preset
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onUpdateAssistant(assistant.copy(memoryBatchPreset = preset)) },
                        shape = AppShapes.ButtonRounded,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(preset.name.lowercase().replaceFirstChar(Char::uppercase), Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = AppShapes.CardSmall, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HapticSwitch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
