package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.util.fuzzyMemoryAgeLabel
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.memory.MemoryGraphRepository
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The redesigned memory detail sheet (user sketch 3): chip row (type / Literal-Roleplay /
 * dashed Provisional), bold content, "Learned …" info line, a Sources card with quoted provenance
 * excerpts, a focused static mini-graph (the node + its direct relations), and a Pin / Edit / Forget
 * action row. History + connections stay reachable behind a collapsed section.
 *
 * Used by the browse page, the per-character page (deep links) and the full-screen graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryNodeSheet(
    detail: MemoryGraphRepository.NodeDetail,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onForget: () -> Unit,
    onRestore: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenGraph: ((focusNodeId: String) -> Unit)? = null,
) {
    val node = detail.node
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberPremiumHaptics()
    var editing by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = AppShapes.BottomSheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Chip row
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeChip(node.type)
                RealityChip(node.reality)
                if (node.status == MemStatus.PROVISIONAL) ProvisionalChip() else StatusLabelChip(node.status)
                if (node.scope == MemScope.GLOBAL_USER) SmallTag("shared")
                if (node.sensitivity == MemSensitivity.SENSITIVE) SmallTag("sensitive")
            }

            // Title / content — long memories read as body text, not a bold wall.
            if (node.content.length <= MEMORY_TITLE_MAX_CHARS) {
                Text(node.content, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            } else {
                Text(node.content, style = MaterialTheme.typography.bodyLarge)
            }

            // Focused static mini-graph: this node + its direct relations (black + outline, mockup).
            if (detail.edges.isNotEmpty()) {
                GraphCard(
                    onClick = onOpenGraph?.let { open -> { open(node.id) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NodeMiniGraph(
                        detail = detail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .padding(16.dp),
                    )
                }
            }

            // Learned line
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                val confirmed = if (node.lastConfirmedAt > node.recordedAt) {
                    ", confirmed ${fuzzyMemoryAgeLabel(node.lastConfirmedAt)}"
                } else ""
                Text(
                    "Learned ${fuzzyMemoryAgeLabel(node.recordedAt)}$confirmed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Sources card — quoted provenance excerpts on the teal container (mockup).
            if (detail.provenance.any { it.excerpt.isNotBlank() || it.rationale.isNotBlank() }) {
                Surface(
                    shape = AppShapes.CardLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sources", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        detail.provenance.forEach { p ->
                            if (p.excerpt.isBlank() && p.rationale.isBlank()) return@forEach
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Quote accent bar
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(if (p.excerpt.isNotBlank()) 36.dp else 20.dp)
                                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f), AppShapes.Tag)
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (p.excerpt.isNotBlank()) {
                                        Text(
                                            "“${p.excerpt}”",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (p.rationale.isNotBlank()) {
                                        Text(
                                            p.rationale,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                        )
                                    }
                                    p.conversationId?.let { cid ->
                                        Text(
                                            "Open source conversation →",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable { onOpenConversation(cid) }
                                                .padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Collapsed history + connections (visually secondary)
            if (detail.history.isNotEmpty() || detail.edges.isNotEmpty()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDetails = !showDetails }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            "History & connections",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (showDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            detail.history.forEach { h ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusLabel(h.status)
                                    Text(h.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(fuzzyMemoryAgeLabel(h.recordedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (detail.history.isNotEmpty() && detail.edges.isNotEmpty()) HorizontalDivider()
                            detail.edges.forEach { e ->
                                Text(
                                    "${if (e.outgoing) "→" else "←"} ${edgeTypeLabel(e.edge.type)}: ${e.otherLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Action row: Pin / Edit / Forget (per the sketch — three equal blocks, Forget in error red)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionBlock(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PushPin,
                    label = if (node.pinned) "Unpin" else "Pin",
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = if (node.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    onClick = { haptics.perform(HapticPattern.Pop); onPin() },
                )
                ActionBlock(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Edit,
                    label = "Edit",
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurface,
                    onClick = { haptics.perform(HapticPattern.Pop); editing = true },
                )
                if (node.status == MemStatus.FORGOTTEN) {
                    ActionBlock(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Restore,
                        label = "Restore",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { haptics.perform(HapticPattern.Success); onRestore() },
                    )
                } else {
                    ActionBlock(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.DeleteForever,
                        label = "Forget",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = { haptics.perform(HapticPattern.Error); onForget() },
                    )
                }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(node.content) }
        AlertDialog(
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

/** Non-provisional status shown as a plain small label pill in the header row. */
@Composable
private fun StatusLabelChip(status: Int) {
    Surface(shape = AppShapes.Tag, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) { StatusLabel(status) }
    }
}

@Composable
private fun ActionBlock(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    // Big square-ish blocks per the sketch's bottom action row.
    Surface(onClick = onClick, shape = AppShapes.CardMedium, color = container, modifier = modifier) {
        Column(
            Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/**
 * Deterministic static mini-graph: focus node centered, direct relations on a ring around it.
 * No labels, non-interactive (the whole card is the click target) — per the sketch.
 */
@Composable
private fun NodeMiniGraph(detail: MemoryGraphRepository.NodeDetail, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val focusColor = cs.primary
    val neighborColor = cs.secondaryContainer
    val fadedColor = cs.surfaceContainerHigh
    val edgeColor = cs.onSurfaceVariant.copy(alpha = 0.25f)
    val neighbors = remember(detail) { detail.edges.take(12) }

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val ring = min(size.width, size.height) / 2f - 18f
        val positions = neighbors.mapIndexed { i, _ ->
            val angle = (2.0 * Math.PI * i / neighbors.size).toFloat() - (Math.PI / 2).toFloat()
            // Alternate two ring radii slightly so dense neighborhoods don't form a perfect circle.
            val r = ring * if (i % 2 == 0) 1f else 0.78f
            center + Offset(cos(angle) * r, sin(angle) * r)
        }
        positions.forEach { p -> drawLine(edgeColor, center, p, strokeWidth = 2f) }
        neighbors.forEachIndexed { i, e ->
            val dormant = e.otherStatus == MemStatus.DORMANT || e.otherStatus == MemStatus.CLOSED
            drawCircle(
                color = if (dormant) fadedColor else neighborColor,
                radius = 9f,
                center = positions[i],
            )
        }
        drawCircle(color = focusColor, radius = 14f, center = center)
    }
}
