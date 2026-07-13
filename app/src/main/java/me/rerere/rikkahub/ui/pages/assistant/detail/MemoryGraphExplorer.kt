package me.rerere.rikkahub.ui.pages.assistant.detail

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphOverrideEntity
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MemoryGraphExplorer(vm: AssistantDetailVM, onDismiss: () -> Unit) {
    val rawNodes by vm.memoryGraphNodes.collectAsStateWithLifecycle()
    val rawEdges by vm.memoryGraphEdges.collectAsStateWithLifecycle()
    val overrides by vm.memoryGraphOverrides.collectAsStateWithLifecycle()
    val provenance by vm.graphProvenance.collectAsStateWithLifecycle()
    val graph = remember(rawNodes, rawEdges, overrides) { applyGraphOverrides(rawNodes, rawEdges, overrides) }
    var query by remember { mutableStateOf("") }
    var selectedNode by remember { mutableStateOf<MemoryGraphNodeEntity?>(null) }
    var selectedEdge by remember { mutableStateOf<MemoryGraphEdgeEntity?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Memory graph", style = MaterialTheme.typography.headlineSmall)
                        Text("${graph.first.size} entities • ${graph.second.size} relationships", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search entities, relationships, and metadata") },
                    modifier = Modifier.fillMaxWidth(),
                )
                InteractiveGraphCanvas(graph.first, graph.second, onNodeClick = { selectedNode = it })
                val matchingNodes = graph.first.filter { query.isBlank() || listOf(it.label, it.kind, it.summary.orEmpty()).any { value -> value.contains(query, true) } }
                val matchingEdges = graph.second.filter { query.isBlank() || listOf(it.predicate, it.statement).any { value -> value.contains(query, true) } }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matchingNodes, key = { "node:${it.id}" }) { node ->
                        Card(onClick = { selectedNode = node }, shape = AppShapes.ListItem) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(node.label, style = MaterialTheme.typography.titleSmall)
                                Text("${node.kind} • ${node.frame} • confidence ${(node.confidence * 100).toInt()}% • importance ${node.importance}" + node.summary?.let { " • $it" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    items(matchingEdges, key = { "edge:${it.id}" }) { edge ->
                        Card(onClick = { selectedEdge = edge }, shape = AppShapes.ListItem) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(edge.statement, style = MaterialTheme.typography.titleSmall)
                                Text("${edge.predicate} • ${edge.frame} • confidence ${(edge.confidence * 100).toInt()}% • importance ${edge.importance}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedNode?.let { node -> GraphNodeDetailSheet(node, graph.first, graph.second, vm) { selectedNode = null } }
    selectedEdge?.let { edge -> GraphEdgeDetailSheet(edge, vm) { selectedEdge = null } }
    provenance?.let { source ->
        AlertDialog(
            onDismissRequest = vm::clearGraphProvenance,
            title = { Text(if (source.sourceAvailable) "Source" else "Source unavailable") },
            text = { Text(source.excerpt.ifBlank { "The source chat is unavailable or was not included." }) },
            confirmButton = { TextButton(onClick = vm::clearGraphProvenance) { Text("Close") } },
        )
    }
}

@Composable
private fun InteractiveGraphCanvas(
    nodes: List<MemoryGraphNodeEntity>,
    edges: List<MemoryGraphEdgeEntity>,
    onNodeClick: (MemoryGraphNodeEntity) -> Unit,
) {
    var positions by remember(nodes) { mutableStateOf(emptyList<Offset>()) }
    val nodeColor = MaterialTheme.colorScheme.tertiary
    val importantNodeColor = MaterialTheme.colorScheme.primary
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    Surface(shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
    Box(Modifier.fillMaxWidth().height(280.dp).padding(8.dp)) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(nodes, positions) {
                detectTapGestures { tap ->
                    positions.mapIndexed { index, point -> index to (point - tap).getDistance() }
                        .minByOrNull { it.second }?.takeIf { it.second < 28.dp.toPx() }
                        ?.let { nodes.getOrNull(it.first)?.let(onNodeClick) }
                }
            }
        ) {
            val visible = nodes.take(80)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.4f
            positions = visible.mapIndexed { index, _ ->
                val ring = 1f + index / 24
                val angle = index.toFloat() / visible.size.coerceAtLeast(1) * Math.PI.toFloat() * 2f
                Offset(center.x + cos(angle) * radius / ring, center.y + sin(angle) * radius / ring)
            }
            val indexById = visible.mapIndexed { index, node -> node.id to index }.toMap()
            edges.take(200).forEach { edge ->
                val a = indexById[edge.subjectId]
                val b = edge.objectId?.let(indexById::get)
                if (a != null && b != null) drawLine(edgeColor, positions[a], positions[b], 1.dp.toPx())
            }
            positions.forEachIndexed { index, point ->
                val node = visible[index]
                drawCircle(
                    if (node.importance >= 4) importantNodeColor else nodeColor,
                    radius = (6 + node.importance.coerceIn(1, 5)).dp.toPx(),
                    center = point,
                )
            }
            drawIntoCanvas { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor.toArgb()
                    textSize = 11.dp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                visible.take(24).forEachIndexed { index, node ->
                    canvas.nativeCanvas.drawText(node.label.take(20), positions[index].x, positions[index].y + 23.dp.toPx(), paint)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphNodeDetailSheet(
    node: MemoryGraphNodeEntity,
    allNodes: List<MemoryGraphNodeEntity>,
    allEdges: List<MemoryGraphEdgeEntity>,
    vm: AssistantDetailVM,
    onDismiss: () -> Unit,
) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var mergeTarget by remember(node.id) { mutableStateOf("") }
    var editing by remember(node.id) { mutableStateOf(false) }
    val related = allEdges.filter { it.subjectId == node.id || it.objectId == node.id }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphTag(node.kind)
                GraphTag(node.frame)
                GraphTag("${(node.confidence * 100).toInt()}% confident")
            }
            Text(node.label, style = MaterialTheme.typography.headlineSmall)
            node.summary?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (related.isNotEmpty()) {
                Text("Related memories", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                related.take(8).forEach { edge ->
                    Surface(shape = AppShapes.ListItem, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text(edge.statement, Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
            }
            TextButton(onClick = { vm.loadGraphProvenance(node.id) }) { Text("View source conversation") }
            if (editing) {
                OutlinedTextField(label, { label = it }, label = { Text("Memory name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mergeTarget, { mergeTarget = it }, label = { Text("Merge into another memory") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (label.trim() != node.label) vm.addGraphOverride("node", node.id, "rename", buildJsonObject { put("label", label.trim()) }.toString())
                    val target = allNodes.firstOrNull { it.label.equals(mergeTarget.trim(), true) }
                    if (target != null && target.id != node.id) {
                        vm.addGraphOverride("node", node.id, "merge", buildJsonObject { put("target_id", target.id) }.toString())
                    }
                    onDismiss()
                }, modifier = Modifier.fillMaxWidth()) { Text("Save correction") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Text("Edit")
                    }
                    TextButton(onClick = { vm.addGraphOverride("node", node.id, "hide", "{}"); onDismiss() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp)); Text("Forget")
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun GraphTag(text: String) {
    Surface(shape = AppShapes.Tag, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphEdgeDetailSheet(edge: MemoryGraphEdgeEntity, vm: AssistantDetailVM, onDismiss: () -> Unit) {
    var statement by remember(edge.id) { mutableStateOf(edge.statement) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { GraphTag(edge.predicate); GraphTag(edge.frame) }
            Text(edge.statement, style = MaterialTheme.typography.headlineSmall)
            Text("${(edge.confidence * 100).toInt()}% confidence · importance ${edge.importance}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(statement, { statement = it }, label = { Text("Correct relationship") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { vm.loadGraphProvenance(edge.id) }) { Text("View source conversation") }
            Button(onClick = {
                vm.addGraphOverride("edge", edge.id, "correct", buildJsonObject { put("statement", statement.trim()) }.toString()); onDismiss()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save correction") }
            TextButton(onClick = { vm.addGraphOverride("edge", edge.id, "hide", "{}"); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Forget relationship") }
            androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
        }
    }
}

private fun applyGraphOverrides(
    nodes: List<MemoryGraphNodeEntity>,
    edges: List<MemoryGraphEdgeEntity>,
    overrides: List<MemoryGraphOverrideEntity>,
): Pair<List<MemoryGraphNodeEntity>, List<MemoryGraphEdgeEntity>> {
    val hidden = overrides.filter { it.operation == "hide" }.map { it.targetId }.toSet()
    val merges = overrides.filter { it.operation == "merge" }.mapNotNull { override ->
        val target = override.payload.objectValue("target_id") ?: return@mapNotNull null
        override.targetId to target
    }.toMap()
    val renamed = overrides.filter { it.operation == "rename" }.mapNotNull { override ->
        override.payload.objectValue("label")?.let { override.targetId to it }
    }.toMap()
    val corrected = overrides.filter { it.operation == "correct" }.mapNotNull { override ->
        override.payload.objectValue("statement")?.let { override.targetId to it }
    }.toMap()
    val effectiveNodes = nodes.filter { it.id !in hidden && it.id !in merges }.map { node ->
        renamed[node.id]?.let { node.copy(label = it, normalizedLabel = it.lowercase()) } ?: node
    }
    val effectiveEdges = edges.filter { it.id !in hidden }.map { edge ->
        edge.copy(
            subjectId = merges[edge.subjectId] ?: edge.subjectId,
            objectId = edge.objectId?.let { merges[it] ?: it },
            statement = corrected[edge.id] ?: edge.statement,
        )
    }.distinctBy { "${it.subjectId}|${it.predicate}|${it.objectId}|${it.objectValue}" }
    return effectiveNodes to effectiveEdges
}

private fun String.objectValue(key: String): String? = runCatching {
    (JsonInstant.parseToJsonElement(this) as? kotlinx.serialization.json.JsonObject)
        ?.get(key)?.jsonPrimitive?.contentOrNull
}.getOrNull()
