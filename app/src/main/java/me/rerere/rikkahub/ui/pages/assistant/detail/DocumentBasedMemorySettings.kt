package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.db.entity.MemoryDocumentEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MAX_MEMORY_DOCUMENT_CHAR_LIMIT
import me.rerere.rikkahub.data.model.MIN_MEMORY_DOCUMENT_CHAR_LIMIT
import me.rerere.rikkahub.data.model.MemoryDocumentKind
import me.rerere.rikkahub.data.model.MemorySystemType
import me.rerere.rikkahub.data.model.memoryCodePointCount
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DocumentBasedMemorySettings(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    vm: AssistantDetailVM,
    highlightedSourceKind: String? = null,
    highlightedSourceId: String? = null,
) {
    val documents by vm.memoryDocuments.collectAsStateWithLifecycle()
    val digests by vm.continuityDigests.collectAsStateWithLifecycle()
    val nodes by vm.memoryGraphNodes.collectAsStateWithLifecycle()
    val edges by vm.memoryGraphEdges.collectAsStateWithLifecycle()
    val processingStates by vm.memoryProcessingStates.collectAsStateWithLifecycle()
    var systemExpanded by remember { mutableStateOf(false) }
    var showGraphExplorer by remember { mutableStateOf(false) }
    LaunchedEffect(highlightedSourceKind, highlightedSourceId) {
        if (highlightedSourceKind == "GRAPH_NODE" || highlightedSourceKind == "GRAPH_RELATION") showGraphExplorer = true
    }
    val userDocument = documents.firstOrNull { it.kind == MemoryDocumentKind.USER_PROFILE.name }
    val characterDocument = documents.firstOrNull { it.kind == MemoryDocumentKind.CHARACTER_MEMORY.name }
    val userRevisionFlow = remember(userDocument?.id) { vm.documentRevisions(userDocument?.id) }
    val characterRevisionFlow = remember(characterDocument?.id) { vm.documentRevisions(characterDocument?.id) }
    val userRevisions by userRevisionFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val characterRevisions by characterRevisionFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Memory", style = MaterialTheme.typography.titleMedium)
                        Text("Build continuity across this character's chats", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HapticSwitch(checked = assistant.enableMemory, onCheckedChange = { onUpdateAssistant(assistant.copy(enableMemory = it)) })
                }
                ExposedDropdownMenuBox(expanded = systemExpanded, onExpandedChange = { systemExpanded = it }) {
                    OutlinedTextField(
                        value = "Document-based",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Memory system") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(systemExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = systemExpanded, onDismissRequest = { systemExpanded = false }) {
                        MemorySystemType.entries.forEach { system ->
                            DropdownMenuItem(
                                text = { Text(if (system == MemorySystemType.ENTRY_BASED) "Entry-based" else "Document-based") },
                                onClick = {
                                    systemExpanded = false
                                    onUpdateAssistant(assistant.copy(memorySystem = system))
                                },
                            )
                        }
                    }
                }
            }
        }

        MemoryConversionPanel(vm)

        MemoryDocumentEditor(
            title = "User Profile",
            description = "Preferences, circumstances, people, projects, and stable facts about the user.",
            document = userDocument,
            limit = assistant.userProfileCharLimit,
            onLimitChange = { onUpdateAssistant(assistant.copy(userProfileCharLimit = it)) },
            onSave = { vm.updateMemoryDocument(MemoryDocumentKind.USER_PROFILE, it) },
            revisions = userRevisions,
            onRestore = { vm.restoreMemoryDocument(MemoryDocumentKind.USER_PROFILE, it) },
            highlighted = highlightedSourceKind == "USER_PROFILE",
        )
        MemoryDocumentEditor(
            title = "Character Memory",
            description = "Shared history, relationship continuity, commitments, and fictional context.",
            document = characterDocument,
            limit = assistant.characterMemoryCharLimit,
            onLimitChange = { onUpdateAssistant(assistant.copy(characterMemoryCharLimit = it)) },
            onSave = { vm.updateMemoryDocument(MemoryDocumentKind.CHARACTER_MEMORY, it) },
            revisions = characterRevisions,
            onRestore = { vm.restoreMemoryDocument(MemoryDocumentKind.CHARACTER_MEMORY, it) },
            highlighted = highlightedSourceKind == "CHARACTER_MEMORY",
        )

        Text("Memory graph", style = MaterialTheme.typography.titleMedium)
        Surface(shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${nodes.size} entities · ${edges.size} relationships", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val nodeColor = MaterialTheme.colorScheme.tertiary
                val edgeColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                    if (nodes.isEmpty()) return@Canvas
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension * 0.36f
                    val positions = nodes.take(32).mapIndexed { index, _ ->
                        val angle = (index.toFloat() / nodes.take(32).size) * (Math.PI * 2).toFloat()
                        Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    }
                    edges.take(64).forEach { edge ->
                        val a = nodes.take(32).indexOfFirst { it.id == edge.subjectId }
                        val b = nodes.take(32).indexOfFirst { it.id == edge.objectId }
                        if (a >= 0 && b >= 0) drawLine(edgeColor, positions[a], positions[b], 1.dp.toPx())
                    }
                    positions.forEach { drawCircle(nodeColor, radius = 5.dp.toPx(), center = it) }
                }
                if (nodes.isEmpty()) Text("The graph will appear as chats are processed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { showGraphExplorer = true }) { Text("Open graph explorer") }
            }
        }

        Text("Recent continuity", style = MaterialTheme.typography.titleMedium)
        if (digests.isEmpty()) {
            Text("No conversation digests yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            digests.take(10).forEach { digest ->
                Surface(shape = AppShapes.ListItem, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(digest.summary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { vm.setDigestPinned(digest.id, !digest.pinned) }) {
                            Icon(Icons.Rounded.PushPin, null, tint = if (digest.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.dismissDigest(digest.id) }) { Icon(Icons.Rounded.Close, null) }
                    }
                }
            }
        }

        val pendingCount = processingStates.count { it.indexedAt > it.processedAt }
        val failedCount = processingStates.count { !it.lastError.isNullOrBlank() }
        Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Processing status", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        failedCount > 0 -> "$pendingCount conversations waiting • $failedCount need retry"
                        pendingCount > 0 -> "$pendingCount conversations waiting"
                        else -> "Memory is up to date"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = vm::processMemoryBacklog, enabled = assistant.enableMemory) { Text("Process backlog") }
                TextButton(onClick = vm::rebuildMemoryIndex, enabled = assistant.enableMemory) { Text("Full rebuild index") }
            }
        }

        Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(16.dp)) {
                Text("Memory recall tool", style = MaterialTheme.typography.titleSmall)
                Text("Always enabled for Document-based memory", color = MaterialTheme.colorScheme.onSecondaryContainer)
                HapticSwitch(checked = true, onCheckedChange = {}, enabled = false)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showGraphExplorer) MemoryGraphExplorer(vm = vm, onDismiss = { showGraphExplorer = false })
}

@Composable
private fun MemoryDocumentEditor(
    title: String,
    description: String,
    document: MemoryDocumentEntity?,
    limit: Int,
    onLimitChange: (Int) -> Unit,
    onSave: (String) -> Unit,
    revisions: List<me.rerere.rikkahub.data.db.entity.MemoryDocumentRevisionEntity>,
    onRestore: (Long) -> Unit,
    highlighted: Boolean,
) {
    var text by remember(document?.id) { mutableStateOf(document?.content.orEmpty()) }
    LaunchedEffect(document?.revision) { text = document?.content.orEmpty() }
    val count = text.memoryCodePointCount()
    var showHistory by remember(document?.id) { mutableStateOf(false) }
    Surface(
        shape = AppShapes.CardLarge,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = text,
                onValueChange = { candidate -> if (candidate.memoryCodePointCount() <= limit) text = candidate },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                supportingText = { Text("$count / $limit characters · revision ${document?.revision ?: 0}") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(text) }, enabled = document != null && text != document?.content) { Text("Save") }
                TextButton(onClick = { showHistory = !showHistory }, enabled = revisions.isNotEmpty()) {
                    Text(if (showHistory) "Hide history" else "Revision history")
                }
            }
            if (showHistory) {
                revisions.take(12).forEach { revision ->
                    Surface(shape = AppShapes.ListItem, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Revision ${revision.revision} • ${revision.reason}", style = MaterialTheme.typography.labelLarge)
                                if (revision.diff.isNotBlank()) Text(revision.diff, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                            }
                            if (revision.revision != document?.revision) {
                                TextButton(onClick = { onRestore(revision.revision) }) { Text("Restore") }
                            }
                        }
                    }
                }
            }
            Text("Document limit", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = limit.toFloat(),
                onValueChange = { onLimitChange((it / 250).toInt() * 250) },
                valueRange = MIN_MEMORY_DOCUMENT_CHAR_LIMIT.toFloat()..MAX_MEMORY_DOCUMENT_CHAR_LIMIT.toFloat(),
                steps = 17,
            )
        }
    }
}
