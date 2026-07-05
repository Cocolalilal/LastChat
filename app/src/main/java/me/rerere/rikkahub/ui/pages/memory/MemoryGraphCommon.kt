package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.MaterialTheme
import me.rerere.rikkahub.data.db.entity.MemNodeType
import kotlin.math.min

/**
 * Shared graph rendering primitives used by both the static preview card and the full-screen
 * live graph page: the immutable render frame, the color palette, the node fill mapping, and the
 * fit/hit-test math. Kept out of the page files so the preview and the full view stay consistent.
 */

/** Immutable position frame handed from the simulation to the UI (whole-object swaps only). */
class GraphRender(
    val prepared: PreparedGraph,
    val positions: FloatArray,
    val frozen: Boolean = false,
)

data class GraphPalette(
    val entity: Color,
    val frame: Color,
    val episode: Color,
    val fact: Color,
    val edge: Color,
    val highlight: Color,
    val pin: Color,
    val onSurface: Color,
)

@Composable
fun rememberGraphPalette(): GraphPalette {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        GraphPalette(
            entity = cs.secondaryContainer,
            frame = cs.primaryContainer,
            episode = cs.tertiaryContainer,
            fact = cs.surfaceContainerHighest,
            edge = cs.onSurfaceVariant,
            highlight = cs.primary,
            pin = cs.primary,
            onSurface = cs.onSurface,
        )
    }
}

fun graphNodeFill(type: Int, p: GraphPalette): Color = when (type) {
    MemNodeType.ENTITY -> p.entity
    MemNodeType.FRAME -> p.frame
    MemNodeType.EPISODE, MemNodeType.GIST -> p.episode
    MemNodeType.GOAL -> p.frame
    MemNodeType.HABIT -> p.entity
    else -> p.fact // FACT and anything else
}

data class CameraFit(val scale: Float, val offset: Offset)

const val GRAPH_MIN_SCALE = 0.2f
const val GRAPH_MAX_SCALE = 3.5f
private const val FIT_MARGIN_PX = 80f

/** Fit [positions] (or a subset) into [size], returning the camera transform (screen = world*scale + offset). */
fun computeFit(
    positions: FloatArray,
    prepared: PreparedGraph,
    size: IntSize,
    onlyIndices: List<Int>? = null,
    zoomCap: Float = 1.6f,
): CameraFit? {
    val nodes = prepared.nodes
    if (nodes.isEmpty() || size == IntSize.Zero || positions.size < nodes.size * 2) return null
    val indices = onlyIndices ?: nodes.indices.toList()
    if (indices.isEmpty()) return null
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (i in indices) {
        val x = positions[i * 2]; val y = positions[i * 2 + 1]; val rad = nodes[i].radius
        if (x - rad < minX) minX = x - rad
        if (y - rad < minY) minY = y - rad
        if (x + rad > maxX) maxX = x + rad
        if (y + rad > maxY) maxY = y + rad
    }
    val worldW = (maxX - minX).coerceAtLeast(1f)
    val worldH = (maxY - minY).coerceAtLeast(1f)
    val availW = (size.width - 2 * FIT_MARGIN_PX).coerceAtLeast(1f)
    val availH = (size.height - 2 * FIT_MARGIN_PX).coerceAtLeast(1f)
    val scale = min(availW / worldW, availH / worldH).coerceIn(GRAPH_MIN_SCALE, zoomCap)
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    return CameraFit(scale, Offset(size.width / 2f - cx * scale, size.height / 2f - cy * scale))
}
