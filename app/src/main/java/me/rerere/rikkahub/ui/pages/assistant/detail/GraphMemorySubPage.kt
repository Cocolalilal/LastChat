package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.rerere.rikkahub.data.db.entity.GraphEpisodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.JsonInstant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class GraphView(val label: String, val icon: ImageVector) {
    ENTITIES("Entities", Icons.Rounded.Hub),
    RELATIONSHIPS("Relationships", Icons.Rounded.Link),
    TIMELINE("Timeline", Icons.Rounded.Timeline),
    EPISODES("Episodes", Icons.Rounded.MenuBook),
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GraphMemoryContent(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    nodes: List<MemoryNodeEntity> = emptyList(),
    edges: List<MemoryEdgeEntity> = emptyList(),
    timelineEvents: List<TimelineEventEntity> = emptyList(),
    episodes: List<GraphEpisodeEntity> = emptyList(),
    personProfiles: List<PersonProfileEntity> = emptyList(),
    userAvatar: Avatar = Avatar.Dummy,
    nodeCountFlow: Flow<Int> = flowOf(0),
    edgeCountFlow: Flow<Int> = flowOf(0),
    activeEventCountFlow: Flow<Int> = flowOf(0),
    episodeCountFlow: Flow<Int> = flowOf(0),
    onClearAllGraphData: () -> Unit = {},
    onDeleteNode: (Int) -> Unit = {},
    onDeleteEdge: (Int) -> Unit = {},
    onProcessText: (String) -> Unit = {},
    onUpsertPersonProfile: (PersonProfileEntity) -> Unit = {},
    isProcessing: Boolean = false,
) {
    val nodeCount by nodeCountFlow.collectAsState(initial = 0)
    val edgeCount by edgeCountFlow.collectAsState(initial = 0)
    val activeEventCount by activeEventCountFlow.collectAsState(initial = 0)
    val episodeCount by episodeCountFlow.collectAsState(initial = 0)

    var selectedView by remember { mutableStateOf(GraphView.ENTITIES) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showIngestion by remember { mutableStateOf(false) }
    var ingestionText by remember { mutableStateOf("") }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Graph Memory") },
            text = {
                Text("This will permanently delete all graph memory data (nodes, edges, events, episodes) for this assistant. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllGraphData()
                        showClearDialog = false
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ─── Stats Card (long-press for text ingestion) ─────────────────
        GraphStatusCard(
            nodeCount = nodeCount,
            edgeCount = edgeCount,
            activeEventCount = activeEventCount,
            episodeCount = episodeCount,
            isProcessing = isProcessing,
            onLongPress = { showIngestion = !showIngestion }
        )

        // ─── Text Ingestion (hidden) ────────────────────────────────────
        AnimatedVisibility(
            visible = showIngestion,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Manual Ingestion",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showIngestion = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    OutlinedTextField(
                        value = ingestionText,
                        onValueChange = { ingestionText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste or type text to extract knowledge from...") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Button(
                        onClick = {
                            if (ingestionText.isNotBlank()) {
                                onProcessText(ingestionText)
                                ingestionText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ingestionText.isNotBlank() && !isProcessing,
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Processing...")
                        } else {
                            Icon(Icons.Rounded.Send, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Extract to Graph")
                        }
                    }
                }
            }
        }

        // ─── View Selector (chip row) ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GraphView.entries.forEach { view ->
                val count = when (view) {
                    GraphView.ENTITIES -> nodeCount
                    GraphView.RELATIONSHIPS -> edgeCount
                    GraphView.TIMELINE -> activeEventCount
                    GraphView.EPISODES -> episodeCount
                }
                FilterChip(
                    selected = selectedView == view,
                    onClick = { selectedView = view },
                    label = { Text("${view.label} ($count)") },
                    leadingIcon = {
                        Icon(view.icon, null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                )
            }
        }

        // ─── View Content ───────────────────────────────────────────────
        AnimatedContent(
            targetState = selectedView,
            label = "graphViewContent"
        ) { view ->
            when (view) {
                GraphView.ENTITIES -> EntitiesView(
                    nodes = nodes,
                    edges = edges,
                    assistant = assistant,
                    userAvatar = userAvatar,
                    personProfiles = personProfiles,
                    onUpsertPersonProfile = onUpsertPersonProfile,
                    onDeleteNode = onDeleteNode,
                )
                GraphView.RELATIONSHIPS -> RelationshipsView(
                    edges = edges,
                    nodes = nodes,
                    onDeleteEdge = onDeleteEdge,
                )
                GraphView.TIMELINE -> TimelineView(
                    events = timelineEvents,
                    nodes = nodes,
                )
                GraphView.EPISODES -> EpisodesView(
                    episodes = episodes,
                    nodes = nodes,
                )
            }
        }

        // ─── Settings Toggle ────────────────────────────────────────────
        Surface(
            onClick = { showSettings = !showSettings },
            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Graph Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Icon(
                    if (showSettings) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            GraphSettingsSection(
                assistant = assistant,
                onUpdateAssistant = onUpdateAssistant,
                nodeCount = nodeCount,
                edgeCount = edgeCount,
                onClearAll = { showClearDialog = true }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STATUS CARD
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphStatusCard(
    nodeCount: Int,
    edgeCount: Int,
    activeEventCount: Int,
    episodeCount: Int,
    isProcessing: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongPress
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Knowledge Graph",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Long-press for manual text ingestion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GraphStatItem(nodeCount.toString(), "Nodes", MaterialTheme.colorScheme.primary)
                GraphStatItem(edgeCount.toString(), "Edges", MaterialTheme.colorScheme.secondary)
                GraphStatItem(activeEventCount.toString(), "Events", MaterialTheme.colorScheme.tertiary)
                GraphStatItem(episodeCount.toString(), "Episodes", MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun GraphStatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ENTITIES VIEW
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntitiesView(
    nodes: List<MemoryNodeEntity>,
    edges: List<MemoryEdgeEntity>,
    assistant: Assistant,
    userAvatar: Avatar,
    personProfiles: List<PersonProfileEntity>,
    onUpsertPersonProfile: (PersonProfileEntity) -> Unit,
    onDeleteNode: (Int) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var expandedNodeId by remember { mutableStateOf<Int?>(null) }
    var selectedPersonNode by remember { mutableStateOf<MemoryNodeEntity?>(null) }

    val profileMap = remember(personProfiles) { personProfiles.associateBy { it.nodeId } }

    val filteredNodes = nodes
        .filter { node ->
            (selectedType == null || node.nodeType == selectedType) &&
                (searchQuery.isBlank() || node.name.contains(searchQuery, ignoreCase = true) || node.description.contains(searchQuery, ignoreCase = true))
        }
        .sortedWith(compareByDescending<MemoryNodeEntity> { it.nodeType == NodeType.PERSON }.thenByDescending { it.importance * 1000 + it.mentionCount })

    val typeCounts = nodes.groupBy { it.nodeType }.mapValues { it.value.size }

    if (selectedPersonNode != null) {
        PersonProfileSheet(
            node = selectedPersonNode!!,
            assistant = assistant,
            userAvatar = userAvatar,
            profile = profileMap[selectedPersonNode!!.id],
            edges = edges,
            nodes = nodes,
            onDismiss = { selectedPersonNode = null },
            onSave = onUpsertPersonProfile,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search entities...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("All (${nodes.size})") })
            NodeType.ALL.forEach { type ->
                val count = typeCounts[type] ?: 0
                if (count > 0) {
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text("${type.replaceFirstChar { it.uppercase() }} ($count)") },
                        leadingIcon = { Icon(nodeTypeIcon(type), null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = nodeTypeColor(type).copy(alpha = 0.2f))
                    )
                }
            }
        }

        Column(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (filteredNodes.isEmpty()) {
                EmptyPlaceholder(if (searchQuery.isBlank() && selectedType == null) "No entities yet — they'll appear as you chat" else "No matching entities")
            } else {
                filteredNodes.forEachIndexed { index, node ->
                    key(node.id) {
                        val isExpanded = expandedNodeId == node.id
                        val nodeEdges = if (isExpanded) edges.filter { it.sourceNodeId == node.id || it.targetNodeId == node.id } else emptyList()
                        val connectedNodes = if (isExpanded) {
                            val connectedIds = nodeEdges.map { if (it.sourceNodeId == node.id) it.targetNodeId else it.sourceNodeId }.toSet()
                            nodes.filter { it.id in connectedIds }.associateBy { it.id }
                        } else emptyMap()

                        NodeCard(
                            node = node,
                            isExpanded = isExpanded,
                            edges = nodeEdges,
                            connectedNodes = connectedNodes,
                            position = cardPosition(index, filteredNodes.size),
                            isPersonProfile = node.nodeType == NodeType.PERSON,
                            onToggleExpand = { expandedNodeId = if (isExpanded) null else node.id },
                            onOpenProfile = { selectedPersonNode = node },
                            onDelete = { onDeleteNode(node.id) }
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NODE CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NodeCard(
    node: MemoryNodeEntity,
    isExpanded: Boolean,
    edges: List<MemoryEdgeEntity>,
    connectedNodes: Map<Int, MemoryNodeEntity>,
    position: String,
    isPersonProfile: Boolean = false,
    onToggleExpand: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onDelete: () -> Unit,
) {
    val shape = cardShape(position)
    val typeColor = nodeTypeColor(node.nodeType)
    val valenceColor = when {
        node.emotionalValence > 0.3f -> Color(0xFF4CAF50).copy(alpha = 0.08f)
        node.emotionalValence < -0.3f -> Color(0xFFF44336).copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Surface(
        onClick = {
            if (isPersonProfile) onOpenProfile() else onToggleExpand()
        },
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(valenceColor)
                .padding(14.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type icon with colored circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        nodeTypeIcon(node.nodeType),
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = typeColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Importance badge
                        if (node.importance >= 7) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "★${node.importance}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (node.description.isNotBlank()) {
                        Text(
                            text = node.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Mentions + action
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${node.mentionCount}×",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        if (isPersonProfile) Icons.Rounded.Edit else if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded details
            AnimatedVisibility(
                visible = isExpanded && !isPersonProfile,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Metadata row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                node.nodeType.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = typeColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Importance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ImportanceBar(node.importance)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last seen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                relativeTime(node.lastMentioned),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // Connected edges
                    if (edges.isNotEmpty()) {
                        Text(
                            "Connections (${edges.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        edges.take(8).forEach { edge ->
                            val isSource = edge.sourceNodeId == node.id
                            val otherNodeId = if (isSource) edge.targetNodeId else edge.sourceNodeId
                            val otherNode = connectedNodes[otherNodeId]
                            val arrow = if (isSource) "→" else "←"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(arrow, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    edge.relationType.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    otherNode?.name ?: "Node #$otherNodeId",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Strength indicator
                                StrengthBar(edge.strength, modifier = Modifier.width(40.dp))
                            }
                        }
                        if (edges.size > 8) {
                            Text(
                                "...and ${edges.size - 8} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    // Delete action
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonProfileSheet(
    node: MemoryNodeEntity,
    assistant: Assistant,
    userAvatar: Avatar,
    profile: PersonProfileEntity?,
    edges: List<MemoryEdgeEntity>,
    nodes: List<MemoryNodeEntity>,
    onDismiss: () -> Unit,
    onSave: (PersonProfileEntity) -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEditing by remember { mutableStateOf(false) }

    val existingAvatar = remember(profile?.avatar, node.name, assistant.avatar, userAvatar) {
        when {
            !profile?.avatar.isNullOrBlank() -> runCatching { JsonInstant.decodeFromString(Avatar.serializer(), profile!!.avatar!!) }.getOrNull() ?: Avatar.Dummy
            node.name.equals(assistant.name, ignoreCase = true) -> assistant.avatar
            else -> userAvatar
        }
    }

    var draftName by remember(profile, node) { mutableStateOf(profile?.displayName?.ifBlank { node.name } ?: node.name) }
    var draftAvatar by remember(profile, existingAvatar) { mutableStateOf(existingAvatar) }
    var draftDateOfBirth by remember(profile) { mutableStateOf(profile?.dateOfBirth.orEmpty()) }
    var draftBirthYear by remember(profile) { mutableStateOf(profile?.birthYear?.toString().orEmpty()) }
    var draftPhysical by remember(profile) { mutableStateOf(profile?.physicalSummary.orEmpty()) }
    var draftPersonality by remember(profile) { mutableStateOf(profile?.personalitySummary.orEmpty()) }
    var draftOther by remember(profile) { mutableStateOf(profile?.otherSummary.orEmpty()) }

    val related = remember(edges, nodes, node.id) {
        val ids = edges.filter { it.sourceNodeId == node.id || it.targetNodeId == node.id }
            .map { if (it.sourceNodeId == node.id) it.targetNodeId else it.sourceNodeId }
            .toSet()
        nodes.filter { it.id in ids }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Person Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    isEditing = !isEditing
                    haptics.perform(HapticPattern.Pop)
                }) { Text(if (isEditing) "Done" else "Edit") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UIAvatar(name = draftName, value = draftAvatar, modifier = Modifier.size(56.dp), onUpdate = if (isEditing) ({ draftAvatar = it }) else null)
                Column {
                    Text(draftName.ifBlank { node.name }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Age: ${calculateAgeText(draftBirthYear, draftDateOfBirth)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isEditing) {
                TextField(value = draftName, onValueChange = { draftName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, shape = RoundedCornerShape(14.dp))
                TextField(value = draftDateOfBirth, onValueChange = { draftDateOfBirth = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Date of birth") }, placeholder = { Text("YYYY or YYYY-MM-DD") }, shape = RoundedCornerShape(14.dp))
                TextField(value = draftBirthYear, onValueChange = { draftBirthYear = it.filter(Char::isDigit).take(4) }, modifier = Modifier.fillMaxWidth(), label = { Text("Birth year") }, shape = RoundedCornerShape(14.dp))
                TextField(value = draftPhysical, onValueChange = { draftPhysical = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Physical attributes summary") }, shape = RoundedCornerShape(14.dp))
                TextField(value = draftPersonality, onValueChange = { draftPersonality = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Personality summary") }, shape = RoundedCornerShape(14.dp))
                TextField(value = draftOther, onValueChange = { draftOther = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Other info summary") }, shape = RoundedCornerShape(14.dp))
                Button(
                    onClick = {
                        onSave(
                            PersonProfileEntity(
                                id = profile?.id ?: 0,
                                assistantId = node.assistantId,
                                nodeId = node.id,
                                displayName = draftName,
                                avatar = JsonInstant.encodeToString(Avatar.serializer(), draftAvatar),
                                dateOfBirth = draftDateOfBirth.ifBlank { null },
                                birthYear = draftBirthYear.toIntOrNull(),
                                physicalSummary = draftPhysical,
                                personalitySummary = draftPersonality,
                                otherSummary = draftOther,
                                physicalSourceNodeIds = profile?.physicalSourceNodeIds ?: "[]",
                                personalitySourceNodeIds = profile?.personalitySourceNodeIds ?: "[]",
                            )
                        )
                        isEditing = false
                        haptics.perform(HapticPattern.Success)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save profile") }
            } else {
                ProfileSummaryBlock("Physical attributes", profile?.physicalSummary)
                ProfileSummaryBlock("Personality", profile?.personalitySummary)
                ProfileSummaryBlock("Other", profile?.otherSummary)
                ProfileSummaryBlock("Relationships", related.joinToString { it.name }.ifBlank { "No linked people yet" })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileSummaryBlock(title: String, value: String?) {
    Surface(
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value?.ifBlank { "No information yet" } ?: "No information yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun calculateAgeText(birthYearText: String, dateOfBirthText: String): String {
    val year = birthYearText.toIntOrNull()
        ?: dateOfBirthText.take(4).toIntOrNull()
        ?: return "Unknown"
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    return (currentYear - year).coerceAtLeast(0).toString()
}

// ═══════════════════════════════════════════════════════════════════════════════
// RELATIONSHIPS VIEW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RelationshipsView(
    edges: List<MemoryEdgeEntity>,
    nodes: List<MemoryNodeEntity>,
    onDeleteEdge: (Int) -> Unit,
) {
    var sortByStrength by remember { mutableStateOf(true) }
    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }

    val sortedEdges = if (sortByStrength) {
        edges.sortedByDescending { it.strength }
    } else {
        edges.sortedByDescending { it.lastReinforced }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Sort toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = sortByStrength,
                onClick = { sortByStrength = true },
                label = { Text("By Strength") },
            )
            FilterChip(
                selected = !sortByStrength,
                onClick = { sortByStrength = false },
                label = { Text("By Recency") },
            )
        }

        Column(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (sortedEdges.isEmpty()) {
                EmptyPlaceholder("No relationships yet")
            } else {
                sortedEdges.forEachIndexed { index, edge ->
                    key(edge.id) {
                        val sourceName = nodeMap[edge.sourceNodeId]?.name ?: "?"
                        val targetName = nodeMap[edge.targetNodeId]?.name ?: "?"

                        Surface(
                            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = cardShape(cardPosition(index, sortedEdges.size)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Source → Target
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            sourceName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text("→", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            targetName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            edge.relationType.replace("_", " "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        if (edge.description.isNotBlank()) {
                                            Text(
                                                "· ${edge.description}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                // Strength
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    StrengthBar(edge.strength, modifier = Modifier.width(40.dp))
                                    Text(
                                        "${(edge.strength * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TIMELINE VIEW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimelineView(
    events: List<TimelineEventEntity>,
    nodes: List<MemoryNodeEntity>,
) {
    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }
    val sortedEvents = events.sortedByDescending { it.scheduledAt ?: it.lastChecked }

    Column(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (sortedEvents.isEmpty()) {
            EmptyPlaceholder("No timeline events — temporal items will appear as you discuss plans and schedules")
        } else {
            sortedEvents.forEachIndexed { index, event ->
                key(event.id) {
                    val nodeName = nodeMap[event.nodeId]?.name ?: "Unknown"
                    val icon = when (event.eventType) {
                        "upcoming" -> Icons.Rounded.Schedule
                        "ongoing" -> Icons.Rounded.PlayArrow
                        "completed" -> Icons.Rounded.TaskAlt
                        "recurring" -> Icons.Rounded.CalendarMonth
                        else -> Icons.Rounded.Pending
                    }
                    val statusColor = when (event.eventType) {
                        "upcoming" -> MaterialTheme.colorScheme.primary
                        "ongoing" -> MaterialTheme.colorScheme.tertiary
                        "completed" -> Color(0xFF4CAF50)
                        "recurring" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = cardShape(cardPosition(index, sortedEvents.size)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, modifier = Modifier.size(16.dp), tint = statusColor)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    nodeName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (event.description.isNotBlank()) {
                                    Text(
                                        event.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    event.eventType.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Medium,
                                )
                                event.scheduledAt?.let {
                                    Text(
                                        relativeTime(it),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EPISODES VIEW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EpisodesView(
    episodes: List<GraphEpisodeEntity>,
    nodes: List<MemoryNodeEntity>,
) {
    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }
    val sortedEpisodes = episodes.sortedByDescending { it.endTime }
    var expandedEpisodeId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (sortedEpisodes.isEmpty()) {
            EmptyPlaceholder("No episodes yet — they're created after conversations are processed")
        } else {
            sortedEpisodes.forEachIndexed { index, episode ->
                key(episode.id) {
                    val isExpanded = expandedEpisodeId == episode.id
                    val linkedNodeIds = try {
                        kotlinx.serialization.json.Json.decodeFromString<List<Int>>(episode.nodeIds)
                    } catch (_: Exception) { emptyList() }
                    val linkedNodes = linkedNodeIds.mapNotNull { nodeMap[it] }

                    Surface(
                        onClick = { expandedEpisodeId = if (isExpanded) null else episode.id },
                        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = cardShape(cardPosition(index, sortedEpisodes.size)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                                .animateContentSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        episode.content.take(80) + if (episode.content.length > 80) "..." else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            formatDate(episode.endTime),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (episode.significance >= 7) {
                                            Text(
                                                "★ Important",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    if (isExpanded && episode.content.length > 80) {
                                        Text(
                                            episode.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    if (linkedNodes.isNotEmpty()) {
                                        Text(
                                            "Related entities:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            linkedNodes.forEach { node ->
                                                Surface(
                                                    color = nodeTypeColor(node.nodeType).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        node.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        color = nodeTypeColor(node.nodeType)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GraphSettingsSection(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    nodeCount: Int,
    edgeCount: Int,
    onClearAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Decay Rate
        Surface(
            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var decayRate by remember(assistant.graphDecayRateDays) {
                    mutableIntStateOf(assistant.graphDecayRateDays)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Memory Decay Rate", style = MaterialTheme.typography.titleSmall)
                    Text("$decayRate days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("Half-life for edge strength decay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = decayRate.toFloat(),
                    onValueChange = {
                        decayRate = it.toInt()
                        onUpdateAssistant(assistant.copy(graphDecayRateDays = it.toInt()))
                    },
                    valueRange = 3f..90f,
                    steps = 28,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Max Nodes
        Surface(
            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var maxNodes by remember(assistant.graphMaxNodes) {
                    mutableIntStateOf(assistant.graphMaxNodes)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Max Active Nodes", style = MaterialTheme.typography.titleSmall)
                    Text("$maxNodes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("Maximum nodes before archiving low-importance ones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = maxNodes.toFloat(),
                    onValueChange = {
                        maxNodes = it.toInt()
                        onUpdateAssistant(assistant.copy(graphMaxNodes = it.toInt()))
                    },
                    valueRange = 50f..1000f,
                    steps = 18,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Timeline toggle
        Surface(
            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("Timeline Tracking") },
                description = { Text("Track plans, deadlines, and recurring events from conversations") },
                tail = {
                    HapticSwitch(
                        checked = assistant.graphTimelineEnabled,
                        onCheckedChange = { onUpdateAssistant(assistant.copy(graphTimelineEnabled = it)) }
                    )
                }
            )
        }

        // Clear all
        Button(
            onClick = onClearAll,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            enabled = nodeCount > 0 || edgeCount > 0
        ) {
            Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Clear All Graph Data")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ImportanceBar(importance: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(10) { i ->
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i < importance) {
                            when {
                                importance >= 8 -> MaterialTheme.colorScheme.tertiary
                                importance >= 5 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun StrengthBar(strength: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { strength.coerceIn(0f, 1f) },
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = when {
            strength >= 0.7f -> MaterialTheme.colorScheme.primary
            strength >= 0.4f -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        strokeCap = StrokeCap.Round,
    )
}

@Composable
private fun EmptyPlaceholder(text: String) {
    Surface(
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITIES
// ═══════════════════════════════════════════════════════════════════════════════

private fun nodeTypeIcon(type: String): ImageVector = when (type) {
    NodeType.PERSON -> Icons.Rounded.Group
    NodeType.PLACE -> Icons.Rounded.Place
    NodeType.OBJECT -> Icons.Rounded.Widgets
    NodeType.EVENT -> Icons.Rounded.CalendarMonth
    NodeType.CONCEPT -> Icons.Rounded.Lightbulb
    NodeType.PREFERENCE -> Icons.Rounded.Favorite
    NodeType.EMOTION -> Icons.Rounded.EmojiEmotions
    NodeType.PLAN -> Icons.Rounded.TaskAlt
    else -> Icons.Rounded.Star
}

@Composable
private fun nodeTypeColor(type: String): Color = when (type) {
    NodeType.PERSON -> MaterialTheme.colorScheme.primary
    NodeType.PLACE -> Color(0xFF26A69A)
    NodeType.OBJECT -> MaterialTheme.colorScheme.secondary
    NodeType.EVENT -> Color(0xFFFF7043)
    NodeType.CONCEPT -> Color(0xFF7E57C2)
    NodeType.PREFERENCE -> Color(0xFFEC407A)
    NodeType.EMOTION -> Color(0xFFFFA726)
    NodeType.PLAN -> Color(0xFF42A5F5)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun cardPosition(index: Int, total: Int): String = when {
    total == 1 -> "ONLY"
    index == 0 -> "FIRST"
    index == total - 1 -> "LAST"
    else -> "MIDDLE"
}

private fun cardShape(position: String): RoundedCornerShape = when (position) {
    "ONLY" -> RoundedCornerShape(20.dp)
    "FIRST" -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    "LAST" -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    else -> RoundedCornerShape(8.dp)
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
