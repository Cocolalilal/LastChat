package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.min

/**
 * The Memory Center **Graph** tab (§10.2). A custom Compose `Canvas` force-directed view — no heavy
 * graph dependency. See [MemoryGraphLayout] for the design rationale and the §14 open-question-2
 * decision (custom simulation, bounded neighborhood + freeze, so pan/zoom stays 60fps on mid-range
 * ARM; reduced-motion collapses to a pre-settled static layout).
 *
 * Pipeline: [MemoryGraphBuilder] selects a bounded neighborhood → [MemoryForceLayout] settles it off
 * the UI thread → whole immutable position frames are swapped into one Compose state (never a
 * `SnapshotStateList` drawn per element) → the frozen frame is rendered once, and pan/zoom is a pure
 * `graphicsLayer` transform that neither recomputes positions nor recomposes the tree.
 */
@Composable
fun MemoryGraphTab(
    input: MemoryGraphInput,
    onOpenNode: (String) -> Unit,
    onTogglePin: (nodeId: String, pinned: Boolean) -> Unit,
) {
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    val haptics = rememberPremiumHaptics()
    val palette = rememberGraphPalette()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontSize = 11.sp) }

    var query by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    // Pinned ids and current search matches come from the live input (the source of truth).
    val pinnedIds = remember(input) { input.nodes.filter { it.pinned }.mapTo(HashSet()) { it.id } }
    val matchIds = remember(input, query) {
        if (query.isBlank()) emptySet()
        else input.nodes.filter { matchesGraphQuery(it, query) }.mapTo(HashSet()) { it.id }
    }
    val forcedIds = remember(pinnedIds, matchIds) { pinnedIds + matchIds }

    // Camera: screen = world * scale + offset, transform origin top-left.
    var cameraOffset by remember { mutableStateOf(Offset.Zero) }
    var cameraScale by remember { mutableFloatStateOf(1f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // Set once the user pans/zooms/taps, so an auto-fit on settle doesn't yank the view back.
    var userInteracted by remember(input, expandedIds) { mutableStateOf(false) }

    // The rendered frame. Whole-object swaps only — obeys the no-SnapshotStateList-to-draw rule.
    var render by remember { mutableStateOf<GraphRender?>(null) }
    // Bumped to request an auto-fit; consumed by the fit effect once the canvas has a size.
    var fitRequest by remember { mutableStateOf(FitRequest(0, animate = false)) }

    // Cache measured labels per prepared graph (positions animate; text does not).
    val labels = remember(render?.prepared, labelStyle) {
        render?.prepared?.nodes?.map { textMeasurer.measure(it.label, labelStyle) } ?: emptyList()
    }
    // Indices of the current search matches within the prepared graph (for the highlight ring).
    val highlightIndices = remember(render?.prepared, matchIds) {
        val p = render?.prepared ?: return@remember IntArray(0)
        if (matchIds.isEmpty()) IntArray(0)
        else p.nodes.indices.filter { p.nodes[it].id in matchIds }.toIntArray()
    }

    // Build + simulate off-thread whenever the graph shape or the neighborhood selection changes.
    LaunchedEffect(input, expandedIds, forcedIds, reduceMotion) {
        val prepared = withContext(Dispatchers.Default) {
            MemoryGraphBuilder.build(input, expandedIds = expandedIds, forcedIds = forcedIds)
        }
        if (prepared.isEmpty) {
            render = GraphRender(prepared, FloatArray(0), frozen = true)
            return@LaunchedEffect
        }
        val sim = withContext(Dispatchers.Default) { MemoryForceLayout(prepared) }
        render = GraphRender(prepared, sim.snapshot(), frozen = false)
        fitRequest = FitRequest(fitRequest.token + 1, animate = false) // fit the seeded spread

        if (reduceMotion) {
            val frame = withContext(Dispatchers.Default) { sim.runToSettle(); sim.snapshot() }
            render = GraphRender(prepared, frame, frozen = true)
            fitRequest = FitRequest(fitRequest.token + 1, animate = false)
            return@LaunchedEffect
        }

        var settled = false
        var iter = 0
        while (isActive && !settled && iter < MemoryForceLayout.MAX_ITERATIONS) {
            val frame = withContext(Dispatchers.Default) {
                repeat(STEP_BATCH) { if (!settled) settled = sim.step(); iter++ }
                sim.snapshot()
            }
            render = GraphRender(prepared, frame, frozen = settled)
            if (!settled) delay(FRAME_DELAY_MS)
        }
        render = GraphRender(prepared, sim.snapshot(), frozen = true)
        // Re-fit on freeze unless the user has taken over the camera or is searching.
        if (!userInteracted && query.isBlank()) fitRequest = FitRequest(fitRequest.token + 1, animate = true)
    }

    // Auto-fit (initial spread / settle) once the canvas size is known.
    LaunchedEffect(fitRequest, canvasSize) {
        val r = render ?: return@LaunchedEffect
        if (r.prepared.isEmpty || canvasSize == IntSize.Zero) return@LaunchedEffect
        val fit = computeFit(r.positions, r.prepared, canvasSize) ?: return@LaunchedEffect
        flyCamera(cameraScale, cameraOffset, fit, animate = fitRequest.animate && !reduceMotion) { s, o ->
            cameraScale = s; cameraOffset = o
        }
    }

    // Search "flies the camera" to frame the matches.
    LaunchedEffect(matchIds, render?.frozen, canvasSize) {
        if (matchIds.isEmpty() || canvasSize == IntSize.Zero) return@LaunchedEffect
        val r = render ?: return@LaunchedEffect
        val indices = r.prepared.nodes.indices.filter { r.prepared.nodes[it].id in matchIds }
        if (indices.isEmpty()) return@LaunchedEffect
        val fit = computeFit(r.positions, r.prepared, canvasSize, onlyIndices = indices, zoomCap = MATCH_ZOOM_CAP)
            ?: return@LaunchedEffect
        userInteracted = true // the fly-to is an intentional camera move; don't let settle override it
        flyCamera(cameraScale, cameraOffset, fit, animate = !reduceMotion) { s, o ->
            cameraScale = s; cameraOffset = o
        }
    }

    val prepared = render?.prepared
    Box(Modifier.fillMaxSize()) {
        if (prepared != null && prepared.isEmpty) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "The memory graph is empty. Memories appear here as they're learned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val a11y = remember(prepared) {
                prepared?.let {
                    "Memory graph, ${it.nodes.size} memories and ${it.edges.size} connections shown. " +
                        "Use the Browse tab for a screen-reader-friendly list."
                } ?: "Memory graph loading"
            }
            // Gesture surface: the Box's layout bounds are NOT transformed by the child Canvas's
            // graphicsLayer, so pointer coordinates stay in screen space and the inverse transform below is exact.
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .semantics { contentDescription = a11y }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = cameraScale
                            val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            // Keep the world point under the centroid fixed while applying pan.
                            cameraOffset = centroid - (centroid - cameraOffset) * (newScale / oldScale) + pan
                            cameraScale = newScale
                            userInteracted = true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { p ->
                                val hit = hitTest(render, p, cameraOffset, cameraScale) ?: return@detectTapGestures
                                haptics.perform(HapticPattern.Pop)
                                userInteracted = true
                                expandedIds = expandedIds + hit.id // grow the neighborhood
                                onOpenNode(hit.id)                  // + open the P4a detail sheet
                            },
                            onLongPress = { p ->
                                val hit = hitTest(render, p, cameraOffset, cameraScale) ?: return@detectTapGestures
                                haptics.perform(HapticPattern.Thud)
                                onTogglePin(hit.id, !hit.pinned)
                            },
                        )
                    },
            ) {
                androidx.compose.foundation.Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = cameraOffset.x
                            translationY = cameraOffset.y
                            scaleX = cameraScale
                            scaleY = cameraScale
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) {
                    val r = render ?: return@Canvas
                    drawGraph(r, labels, highlightIndices, palette)
                }
            }

            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search the graph") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = AppShapes.SearchField,
            )

            // Re-center button.
            IconButton(
                onClick = {
                    userInteracted = false
                    fitRequest = FitRequest(fitRequest.token + 1, animate = !reduceMotion)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50)),
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Re-center")
            }

            // Legend + truncation hint.
            GraphLegend(
                truncated = prepared?.truncated ?: 0,
                shown = prepared?.nodes?.size ?: 0,
                palette = palette,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}

// ─────────────────────────────── render frame ───────────────────────────────

/** Immutable position frame handed from the simulation to the UI. */
class GraphRender(
    val prepared: PreparedGraph,
    val positions: FloatArray,
    val frozen: Boolean = false,
)

private data class FitRequest(val token: Int, val animate: Boolean)

private const val STEP_BATCH = 6
private const val FRAME_DELAY_MS = 16L
private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 3.5f
private const val MATCH_ZOOM_CAP = 1.8f
private const val FIT_MARGIN_PX = 80f
private const val TOUCH_SLOP_PX = 14f

// ─────────────────────────────── drawing ───────────────────────────────

private fun DrawScope.drawGraph(
    render: GraphRender,
    labels: List<TextLayoutResult>,
    highlightIndices: IntArray,
    palette: GraphPalette,
) {
    val pos = render.positions
    val nodes = render.prepared.nodes
    if (pos.size < nodes.size * 2) return

    // Edges under nodes.
    for (e in render.prepared.edges) {
        val a = e.from; val b = e.to
        val faded = nodes[a].faded || nodes[b].faded
        drawLine(
            color = palette.edge.copy(alpha = if (faded) 0.12f else 0.22f),
            start = Offset(pos[a * 2], pos[a * 2 + 1]),
            end = Offset(pos[b * 2], pos[b * 2 + 1]),
            strokeWidth = 1.5f,
        )
    }

    val highlight = HashSet<Int>(highlightIndices.size * 2).apply { highlightIndices.forEach { add(it) } }

    for (i in nodes.indices) {
        val n = nodes[i]
        val center = Offset(pos[i * 2], pos[i * 2 + 1])
        val alpha = if (n.faded) 0.42f else 1f
        drawCircle(color = graphFill(n.type, palette).copy(alpha = alpha), radius = n.radius, center = center)

        if (n.provisional) {
            // Dashed ring = provisional belief awaiting confirmation.
            drawCircle(
                color = palette.onSurface.copy(alpha = 0.55f * alpha),
                radius = n.radius + 2.5f,
                center = center,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
            )
        }
        if (n.pinned) {
            drawCircle(color = palette.pin, radius = n.radius + 3.5f, center = center, style = Stroke(width = 2f))
        }
        if (i in highlight) {
            drawCircle(color = palette.highlight, radius = n.radius + 6f, center = center, style = Stroke(width = 2.5f))
        }
    }

    // Labels last. Scale-independent (hubs + matches only) so zoom never triggers a redraw.
    for (i in nodes.indices) {
        val n = nodes[i]
        if (!n.isHub && i !in highlight) continue
        val layout = labels.getOrNull(i) ?: continue
        val center = Offset(pos[i * 2], pos[i * 2 + 1])
        drawText(
            textLayoutResult = layout,
            color = palette.onSurface.copy(alpha = if (n.faded) 0.5f else 0.95f),
            topLeft = Offset(center.x - layout.size.width / 2f, center.y + n.radius + 2f),
        )
    }
}

private fun graphFill(type: Int, p: GraphPalette): Color = when (type) {
    MemNodeType.ENTITY -> p.entity
    MemNodeType.FRAME -> p.frame
    MemNodeType.EPISODE, MemNodeType.GIST -> p.episode
    MemNodeType.GOAL -> p.frame
    MemNodeType.HABIT -> p.entity
    else -> p.fact // FACT and anything else
}

// ─────────────────────────────── camera / fit / hit-test ───────────────────────────────

private fun hitTest(render: GraphRender?, screen: Offset, offset: Offset, scale: Float): GraphVizNode? {
    val r = render ?: return null
    val nodes = r.prepared.nodes
    val pos = r.positions
    if (pos.size < nodes.size * 2) return null
    val world = (screen - offset) / scale
    val slop = TOUCH_SLOP_PX / scale
    // Reverse order = topmost first.
    for (i in nodes.indices.reversed()) {
        val dx = world.x - pos[i * 2]
        val dy = world.y - pos[i * 2 + 1]
        val reach = nodes[i].radius + slop
        if (dx * dx + dy * dy <= reach * reach) return nodes[i]
    }
    return null
}

private data class CameraFit(val scale: Float, val offset: Offset)

private fun computeFit(
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
    val scale = min(availW / worldW, availH / worldH).coerceIn(MIN_SCALE, zoomCap)
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    return CameraFit(scale, Offset(size.width / 2f - cx * scale, size.height / 2f - cy * scale))
}

private suspend fun flyCamera(
    fromScale: Float,
    fromOffset: Offset,
    target: CameraFit,
    animate: Boolean,
    set: (Float, Offset) -> Unit,
) {
    if (!animate) {
        set(target.scale, target.offset)
        return
    }
    val anim = Animatable(0f)
    anim.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing)) {
        val t = value
        val s = fromScale + (target.scale - fromScale) * t
        val ox = fromOffset.x + (target.offset.x - fromOffset.x) * t
        val oy = fromOffset.y + (target.offset.y - fromOffset.y) * t
        set(s, Offset(ox, oy))
    }
}

private fun matchesGraphQuery(node: MemoryNodeEntity, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false
    return node.content.contains(q, ignoreCase = true) ||
        (node.displayLabel?.contains(q, ignoreCase = true) == true)
}

// ─────────────────────────────── palette & legend ───────────────────────────────

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
private fun rememberGraphPalette(): GraphPalette {
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

@Composable
private fun GraphLegend(truncated: Int, shown: Int, palette: GraphPalette, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = AppShapes.CardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot("Entity", palette.entity)
                LegendDot("Frame", palette.frame)
                LegendDot("Episode", palette.episode)
                LegendDot("Fact", palette.fact)
            }
            val hint = if (truncated > 0) "$shown shown · $truncated more — tap to explore" else "$shown memories"
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
