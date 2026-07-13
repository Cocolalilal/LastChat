package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.db.entity.MemoryConversationDigestEntity
import me.rerere.rikkahub.data.db.entity.MemoryDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryDocumentRevisionEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MAX_MEMORY_DOCUMENT_CHAR_LIMIT
import me.rerere.rikkahub.data.model.MIN_MEMORY_DOCUMENT_CHAR_LIMIT
import me.rerere.rikkahub.data.model.MemoryDocumentKind
import me.rerere.rikkahub.data.model.MemorySystemType
import me.rerere.rikkahub.data.model.memoryCodePointCount
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
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
    var editingKind by remember { mutableStateOf<MemoryDocumentKind?>(null) }
    var showGraphExplorer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var systemMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(highlightedSourceKind, highlightedSourceId) {
        when (highlightedSourceKind) {
            "GRAPH_NODE", "GRAPH_RELATION" -> showGraphExplorer = true
            "USER_PROFILE" -> editingKind = MemoryDocumentKind.USER_PROFILE
            "CHARACTER_MEMORY" -> editingKind = MemoryDocumentKind.CHARACTER_MEMORY
        }
    }

    val userDocument = documents.firstOrNull { it.kind == MemoryDocumentKind.USER_PROFILE.name }
    val characterDocument = documents.firstOrNull { it.kind == MemoryDocumentKind.CHARACTER_MEMORY.name }
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val recentCount = digests.count { it.recordedAt >= weekAgo }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MemoryStatsRow(memoryCount = nodes.size + digests.size, recentCount = recentCount, entityCount = nodes.size)
        MemoryProcessingBanner(processingStates, vm::processMemoryBacklog)
        MemoryConversionPanel(vm)

        SectionTitle("Profiles")
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            MemoryDocumentSummaryCard(
                title = "User Profile",
                description = "What this character understands about you",
                document = userDocument,
                position = DocumentCardPosition.FIRST,
                highlighted = highlightedSourceKind == "USER_PROFILE",
                onClick = { editingKind = MemoryDocumentKind.USER_PROFILE },
            )
            MemoryDocumentSummaryCard(
                title = "Character Memory",
                description = "Shared history, commitments, and relationship continuity",
                document = characterDocument,
                position = DocumentCardPosition.LAST,
                highlighted = highlightedSourceKind == "CHARACTER_MEMORY",
                onClick = { editingKind = MemoryDocumentKind.CHARACTER_MEMORY },
            )
        }

        SectionTitle("Memories")
        MemoryGraphOverview(nodes, edges, onClick = { showGraphExplorer = true })

        MemoryActivityHeader(digests.size)
        if (digests.isEmpty()) {
            Surface(shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    "Recent conversations will appear here after they become part of this character's continuity.",
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                digests.take(6).forEachIndexed { index, digest ->
                    ContinuityActivityCard(
                        digest = digest,
                        first = index == 0,
                        last = index == digests.take(6).lastIndex,
                        onPin = { vm.setDigestPinned(digest.id, !digest.pinned) },
                        onDismiss = { vm.dismissDigest(digest.id) },
                    )
                }
            }
        }

        Surface(
            onClick = { showSettings = !showSettings },
            shape = AppShapes.CardMedium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Memory settings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${if (assistant.enableMemory) "On" else "Off"} · Document-based",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (showSettings) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                AnimatedVisibility(showSettings, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        HorizontalDivider()
                        MemorySettingsRow("Memory", "Use memories in future chats") {
                            HapticSwitch(assistant.enableMemory, { onUpdateAssistant(assistant.copy(enableMemory = it)) })
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Surface(onClick = { systemMenuExpanded = true }, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Memory system", style = MaterialTheme.typography.titleSmall)
                                    Text("Document-based", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.ChevronRight, null)
                                DropdownMenu(systemMenuExpanded, { systemMenuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Entry-based") },
                                        onClick = {
                                            systemMenuExpanded = false
                                            onUpdateAssistant(assistant.copy(memorySystem = MemorySystemType.ENTRY_BASED))
                                        },
                                    )
                                    DropdownMenuItem(text = { Text("Document-based") }, onClick = { systemMenuExpanded = false })
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        MemorySettingsRow("Memory search", "Always available in Document-based memory") {
                            HapticSwitch(true, {}, enabled = false)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        TextButton(
                            onClick = vm::rebuildMemoryIndex,
                            enabled = assistant.enableMemory,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        ) { Text("Rebuild memory index") }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    editingKind?.let { kind ->
        val document = if (kind == MemoryDocumentKind.USER_PROFILE) userDocument else characterDocument
        val revisionsFlow = remember(document?.id) { vm.documentRevisions(document?.id) }
        val revisions by revisionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        MemoryDocumentEditorSheet(
            kind = kind,
            document = document,
            revisions = revisions,
            limit = if (kind == MemoryDocumentKind.USER_PROFILE) assistant.userProfileCharLimit else assistant.characterMemoryCharLimit,
            onLimitChange = {
                onUpdateAssistant(
                    if (kind == MemoryDocumentKind.USER_PROFILE) assistant.copy(userProfileCharLimit = it)
                    else assistant.copy(characterMemoryCharLimit = it)
                )
            },
            onSave = { vm.updateMemoryDocument(kind, it) },
            onRestore = { vm.restoreMemoryDocument(kind, it) },
            onDismiss = { editingKind = null },
        )
    }
    if (showGraphExplorer) MemoryGraphExplorer(vm = vm, onDismiss = { showGraphExplorer = false })
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

private enum class DocumentCardPosition { FIRST, LAST }

@Composable
private fun MemoryDocumentSummaryCard(
    title: String,
    description: String,
    document: MemoryDocumentEntity?,
    position: DocumentCardPosition,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = if (position == DocumentCardPosition.FIRST) AppShapes.ListItemFirst else AppShapes.ListItemLast,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    document?.content?.ifBlank { description } ?: description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.Edit, "Edit $title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContinuityActivityCard(
    digest: MemoryConversationDigestEntity,
    first: Boolean,
    last: Boolean,
    onPin: () -> Unit,
    onDismiss: () -> Unit,
) {
    var menuExpanded by remember(digest.id) { mutableStateOf(false) }
    val shape = when {
        first && last -> AppShapes.ListItem
        first -> AppShapes.ListItemFirst
        last -> AppShapes.ListItemLast
        else -> RoundedCornerShape(4.dp)
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 13.dp, bottom = 13.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(digest.summary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (digest.openThreads != "[]") Text("Has an open thread", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
            DropdownMenu(menuExpanded, { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (digest.pinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                    onClick = { menuExpanded = false; onPin() },
                )
                DropdownMenuItem(text = { Text("Dismiss") }, onClick = { menuExpanded = false; onDismiss() })
            }
        }
    }
}

@Composable
private fun MemorySettingsRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryDocumentEditorSheet(
    kind: MemoryDocumentKind,
    document: MemoryDocumentEntity?,
    revisions: List<MemoryDocumentRevisionEntity>,
    limit: Int,
    onLimitChange: (Int) -> Unit,
    onSave: (String) -> Unit,
    onRestore: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(document?.id) { mutableStateOf(document?.content.orEmpty()) }
    var showAdvanced by remember(document?.id) { mutableStateOf(false) }
    LaunchedEffect(document?.revision) { text = document?.content.orEmpty() }
    val title = if (kind == MemoryDocumentKind.USER_PROFILE) "User Profile" else "Character Memory"
    val count = text.memoryCodePointCount()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (kind == MemoryDocumentKind.USER_PROFILE) "A living understanding of the user—not a form they need to maintain."
                else "The character's shared history, promises, and ongoing relationship context.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { candidate -> if (candidate.memoryCodePointCount() <= limit) text = candidate },
                modifier = Modifier.fillMaxWidth(),
                minLines = 10,
                supportingText = { Text("$count of $limit characters") },
            )
            Button(
                onClick = { onSave(text); onDismiss() },
                enabled = document != null && text != document.content,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save changes") }
            Surface(onClick = { showAdvanced = !showAdvanced }, shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Advanced", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
            }
            AnimatedVisibility(showAdvanced, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Document limit · $limit characters", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = limit.toFloat(),
                        onValueChange = { onLimitChange((it / 250).toInt() * 250) },
                        valueRange = MIN_MEMORY_DOCUMENT_CHAR_LIMIT.toFloat()..MAX_MEMORY_DOCUMENT_CHAR_LIMIT.toFloat(),
                        steps = 17,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Revision history", style = MaterialTheme.typography.titleSmall)
                    }
                    if (revisions.isEmpty()) Text("No earlier versions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    revisions.take(10).forEach { revision ->
                        Surface(shape = AppShapes.ListItem, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Revision ${revision.revision} · ${revision.reason}", style = MaterialTheme.typography.labelLarge)
                                    if (revision.diff.isNotBlank()) Text(revision.diff, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                if (revision.revision != document?.revision) TextButton(onClick = { onRestore(revision.revision) }) { Text("Restore") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
