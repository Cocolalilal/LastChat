package me.rerere.rikkahub.ui.pages.assistant.detail

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryProcessingStateEntity
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun MemoryStatsRow(memoryCount: Int, recentCount: Int, entityCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MemoryStatCard(memoryCount.toString(), "memories", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        MemoryStatCard(recentCount.toString(), "this week", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        MemoryStatCard(entityCount.toString(), "entities", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
    }
}

@Composable
private fun MemoryStatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = AppShapes.CardSmall, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun MemoryGraphOverview(
    nodes: List<MemoryGraphNodeEntity>,
    edges: List<MemoryGraphEdgeEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeColor = MaterialTheme.colorScheme.primary
    val strongNodeColor = MaterialTheme.colorScheme.tertiary
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (nodes.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Hub, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Connections will appear as conversations become memories", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Canvas(Modifier.matchParentSize()) {
                        val visible = nodes.sortedByDescending { it.importance }.take(14)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val orbit = size.minDimension * 0.34f
                        val positions = visible.mapIndexed { index, _ ->
                            if (index == 0) center else {
                                val angle = ((index - 1).toFloat() / (visible.size - 1).coerceAtLeast(1)) * Math.PI.toFloat() * 2f
                                val ring = if (index > 8) 0.64f else 1f
                                Offset(center.x + cos(angle) * orbit * ring, center.y + sin(angle) * orbit * ring)
                            }
                        }
                        val byId = visible.mapIndexed { index, node -> node.id to index }.toMap()
                        edges.take(60).forEach { edge ->
                            val from = byId[edge.subjectId]
                            val to = edge.objectId?.let(byId::get)
                            if (from != null && to != null) drawLine(edgeColor, positions[from], positions[to], 1.5.dp.toPx())
                        }
                        visible.forEachIndexed { index, node ->
                            val radius = (7 + node.importance.coerceIn(1, 5)).dp.toPx()
                            drawCircle(if (index == 0) strongNodeColor else nodeColor, radius, positions[index])
                        }
                        drawIntoCanvas { canvas ->
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = labelColor.toArgb()
                                textSize = 11.dp.toPx()
                                textAlign = Paint.Align.CENTER
                            }
                            visible.take(9).forEachIndexed { index, node ->
                                canvas.nativeCanvas.drawText(node.label.take(18), positions[index].x, positions[index].y + 24.dp.toPx(), paint)
                            }
                        }
                    }
                }
            }
            Surface(shape = AppShapes.ButtonPill, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (nodes.isEmpty()) "Browse memory" else "Browse all memories", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun MemoryProcessingBanner(
    states: List<MemoryProcessingStateEntity>,
    onRetry: () -> Unit,
) {
    val pending = states.count { it.indexedAt > it.processedAt }
    val failed = states.count { !it.lastError.isNullOrBlank() }
    if (pending == 0 && failed == 0) return
    val error = failed > 0
    Surface(
        onClick = onRetry,
        shape = AppShapes.ButtonPill,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Text(
                    if (error) "Memory needs attention · tap to retry" else "Updating memory from $pending conversation${if (pending == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (!error) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun MemoryActivityHeader(activityCount: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Activity", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$activityCount recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
