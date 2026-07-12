package me.rerere.rikkahub.ui.pages.assistant.detail

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

    selectedNode?.let { node -> GraphNodeCorrectionDialog(node, graph.first, vm) { selectedNode = null } }
    selectedEdge?.let { edge -> GraphEdgeCorrectionDialog(edge, vm) { selectedEdge = null } }
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
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth().height(260.dp)) {
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
                drawCircle(nodeColor, radius = if (index == 0) 9.dp.toPx() else 6.dp.toPx(), center = point)
            }
        }
    }
}

@Composable
private fun GraphNodeCorrectionDialog(
    node: MemoryGraphNodeEntity,
    allNodes: List<MemoryGraphNodeEntity>,
    vm: AssistantDetailVM,
    onDismiss: () -> Unit,
) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var mergeTarget by remember(node.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(node.summary.orEmpty())
                OutlinedTextField(label, { label = it }, label = { Text("Corrected name") })
                OutlinedTextField(mergeTarget, { mergeTarget = it }, label = { Text("Merge into entity name") })
                TextButton(onClick = { vm.loadGraphProvenance(node.id) }) { Text("View source") }
            }
        },
        confirmButton = {
            Row {
                IconButton(onClick = {
                    vm.addGraphOverride("node", node.id, "rename", buildJsonObject { put("label", label.trim()) }.toString()); onDismiss()
                }) { Icon(Icons.Rounded.Edit, "Rename") }
                IconButton(onClick = {
                    val target = allNodes.firstOrNull { it.label.equals(mergeTarget.trim(), true) }
                    if (target != null && target.id != node.id) {
                        vm.addGraphOverride("node", node.id, "merge", buildJsonObject { put("target_id", target.id) }.toString()); onDismiss()
                    }
                }) { Icon(Icons.Rounded.Merge, "Merge") }
                IconButton(onClick = {
                    vm.addGraphOverride("node", node.id, "hide", "{}"); onDismiss()
                }) { Icon(Icons.Rounded.VisibilityOff, "Hide") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GraphEdgeCorrectionDialog(edge: MemoryGraphEdgeEntity, vm: AssistantDetailVM, onDismiss: () -> Unit) {
    var statement by remember(edge.id) { mutableStateOf(edge.statement) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct relationship") },
        text = {
            Column {
                OutlinedTextField(statement, { statement = it }, label = { Text("Corrected relationship") }, minLines = 3)
                TextButton(onClick = { vm.loadGraphProvenance(edge.id) }) { Text("View source") }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.addGraphOverride("edge", edge.id, "correct", buildJsonObject { put("statement", statement.trim()) }.toString()); onDismiss()
            }) { Text("Save correction") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.addGraphOverride("edge", edge.id, "hide", "{}"); onDismiss() }) { Text("Hide") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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
