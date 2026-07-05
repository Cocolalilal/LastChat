package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.min

/**
 * The "Memories" card graph preview (user sketch): the whole (bounded) graph, pre-settled
 * off-thread, rendered STATIC and simplified — dots + edges, no labels, no gestures. Tapping the
 * card opens the full-screen live graph. Re-settles only when the graph shape changes
 * (node/edge counts), so scrolling the page never re-runs the simulation.
 */
@Composable
fun MemoryGraphPreviewCard(
    input: MemoryGraphInput,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberGraphPalette()
    var frame by remember { mutableStateOf<GraphRender?>(null) }

    val shapeKey = input.nodes.size to input.edges.size
    LaunchedEffect(shapeKey) {
        frame = withContext(Dispatchers.Default) {
            val prepared = MemoryGraphBuilder.build(input, maxNodes = 140)
            if (prepared.isEmpty) {
                GraphRender(prepared, FloatArray(0), frozen = true)
            } else {
                val sim = MemoryForceLayout(prepared)
                sim.runToSettle()
                GraphRender(prepared, sim.snapshot(), frozen = true)
            }
        }
    }

    // Mockup: near-black card with a subtle outline ring (GraphCard), not a tonal surface.
    GraphCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.height(200.dp).padding(16.dp)) {
            val r = frame
            if (r == null || r.prepared.isEmpty) {
                Text(
                    "The memory graph grows here as memories are learned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val nodes = r.prepared.nodes
                    val pos = r.positions
                    if (pos.size < nodes.size * 2) return@Canvas
                    // Fit the settled layout into the card.
                    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                    for (i in nodes.indices) {
                        val x = pos[i * 2]; val y = pos[i * 2 + 1]
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                    val worldW = (maxX - minX).coerceAtLeast(1f)
                    val worldH = (maxY - minY).coerceAtLeast(1f)
                    val scale = min(size.width / worldW, size.height / worldH) * 0.92f
                    val cx = (minX + maxX) / 2f
                    val cy = (minY + maxY) / 2f
                    fun screen(i: Int) = Offset(
                        (pos[i * 2] - cx) * scale + size.width / 2f,
                        (pos[i * 2 + 1] - cy) * scale + size.height / 2f,
                    )
                    for (e in r.prepared.edges) {
                        drawLine(
                            color = palette.edge.copy(alpha = 0.18f),
                            start = screen(e.from),
                            end = screen(e.to),
                            strokeWidth = 1f,
                        )
                    }
                    for (i in nodes.indices) {
                        val n = nodes[i]
                        drawCircle(
                            color = graphNodeFill(n.type, palette).copy(alpha = if (n.faded) 0.4f else 1f),
                            radius = (n.radius * scale).coerceIn(1.5f, 8f),
                            center = screen(i),
                        )
                    }
                }
            }
        }
    }
}
