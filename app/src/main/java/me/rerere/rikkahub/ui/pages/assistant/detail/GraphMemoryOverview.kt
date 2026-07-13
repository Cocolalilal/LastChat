package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
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
import me.rerere.rikkahub.ui.theme.AppShapes
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
) {
    val repository = koinInject<GraphMemoryRepository>()
    val dao = koinInject<MemoryGraphDao>()
    val transferManager = koinInject<MemoryTransferManager>()
    val scope = rememberCoroutineScope()
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
    var browseOpen by remember { mutableStateOf(false) }
    var graphOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var selectedMemory by remember { mutableStateOf<GraphMemoryEntity?>(null) }
    val now = System.currentTimeMillis()
    val thisWeek = memories.count { it.createdAt >= now - 7 * 24 * 60 * 60 * 1000L }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MemoryEngineSelector(assistant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GraphStat("${memories.size}", "memories", MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp), Modifier.weight(1f))
                GraphStat("$thisWeek", "this week", MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp), Modifier.weight(1f))
                GraphStat("${entities.size}", "entities", MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp, topStart = 8.dp, bottomStart = 8.dp), Modifier.weight(1f))
            }
            GraphStatusCard(
                assistant = assistant,
                transfer = transfer,
                onPause = { id -> scope.launch { transferManager.pause(id) } },
                onResume = { id -> scope.launch { transferManager.resume(id) } },
            )
            Text("Memories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth().height(270.dp).clickable { graphOpen = true },
                shape = AppShapes.CardLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box {
                    GraphCanvas(memories, entities, links, showLabels = false, modifier = Modifier.fillMaxSize().padding(10.dp))
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        shape = AppShapes.ButtonPill,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                else -> AppShapes.ButtonSquared
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
            onForgetMemory = { repository.forget(it.id) },
            onForgetEntity = { repository.forgetEntity(it.id) },
            repository = repository,
            assistantId = assistant.id.toString(),
        )
    }
    if (graphOpen) {
        FullGraphBrowser(memories, entities, links, onClose = { graphOpen = false })
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
                        "RUNNING" -> "Building graph memory · ${progressPercent(activeTransfer)}%"
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
            } else if (activeTransfer?.state in setOf("PAUSED", "FAILED")) {
                TextButton(onClick = { onResume(requireNotNull(activeTransfer).id) }) { Text("Resume") }
            }
        }
        if (activeTransfer?.state == "RUNNING") {
            LinearProgressIndicator(
                progress = { progressPercent(activeTransfer) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
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

@Composable
private fun GraphCanvas(
    memories: List<GraphMemoryEntity>,
    entities: List<GraphEntityEntity>,
    links: List<me.rerere.rikkahub.data.db.entity.GraphMemoryEntityLinkEntity>,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    translation: Offset = Offset.Zero,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val edge = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val nodes = (memories.take(48).map { "m:${it.id}" to it.content } + entities.take(32).map { "e:${it.id}" to it.canonicalName })
        if (nodes.isEmpty()) return@Canvas
        val radius = min(size.width, size.height) * .36f * scale
        val center = Offset(size.width / 2f, size.height / 2f) + translation
        val positions = nodes.mapIndexed { index, node ->
            val ring = if (nodes.size == 1) 0f else if (index < 12) .55f else 1f
            val angle = (2.0 * PI * index / nodes.size) - PI / 2
            node.first to Offset(
                center.x + cos(angle).toFloat() * radius * ring,
                center.y + sin(angle).toFloat() * radius * ring,
            )
        }.toMap()
        links.take(120).forEach { link ->
            val a = positions["m:${link.memoryId}"]
            val b = positions["e:${link.entityId}"]
            if (a != null && b != null) drawLine(edge, a, b, strokeWidth = 2f)
        }
        nodes.forEach { (key, label) ->
            val point = positions.getValue(key)
            val isEntity = key.startsWith("e:")
            drawCircle(if (isEntity) secondary else primary, radius = if (isEntity) 9f else 12f, center = point)
            drawCircle(labelColor.copy(alpha = .16f), radius = if (isEntity) 13f else 17f, center = point, style = Stroke(2f))
            if (showLabels) {
                drawContext.canvas.nativeCanvas.drawText(
                    label.take(22),
                    point.x + 15f,
                    point.y + 5f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((labelColor.alpha * 255).toInt(), (labelColor.red * 255).toInt(), (labelColor.green * 255).toInt(), (labelColor.blue * 255).toInt())
                        textSize = 30f
                        isAntiAlias = true
                    },
                )
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
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "Back") }
                        Text("Memory graph", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            },
        ) { padding ->
            Surface(
                Modifier.fillMaxSize().padding(padding).pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(.55f, 2.5f)
                        translation += pan
                    }
                },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                GraphCanvas(memories, entities, links, showLabels = true, modifier = Modifier.fillMaxSize(), scale = scale, translation = translation)
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
    onForgetMemory: suspend (GraphMemoryEntity) -> Unit,
    onForgetEntity: suspend (GraphEntityEntity) -> Unit,
    repository: GraphMemoryRepository,
    assistantId: String,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var newMemory by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val filteredMemories = memories.filter { it.content.contains(query, true) }
    val filteredEntities = entities.filter { it.canonicalName.contains(query, true) }
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
                                color = MaterialTheme.colorScheme.surfaceContainer,
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
                            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (sources.isEmpty()) Text("Added manually", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sources.isNotEmpty()) {
                    Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sources.take(4).forEach { source ->
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
            SettingSwitch("Session memory", "Keeps a conversation-scoped working memory in addition to normal context.", assistant.enableSessionMemory) {
                onUpdateAssistant(assistant.copy(enableSessionMemory = it))
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
