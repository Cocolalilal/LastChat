package me.rerere.rikkahub.ui.pages.memory

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * Full-screen live memory graph (§10.2 + user's graph brief). Live, smooth, interactive: pan/zoom is
 * a pure `graphicsLayer` transform (60fps, never recomputes positions), LOD labels fade in on
 * zoom-in with a hard no-overlap guarantee ([MemoryGraphLabels]), node radius is driven by relation
 * count, and tapping a node enters focus mode (camera flies to it, everything else fades away); a
 * second tap opens the node sheet. Back gesture / recenter / pinch-out exit focus. Reduced motion
 * collapses to a pre-settled static layout with no camera animation.
 */
@Composable
fun MemoryGraphPage(assistantId: String?, focusNodeId: String? = null) {
    val vm: MemoryVM = koinViewModel(
        key = "graph_${assistantId ?: "global"}",
        parameters = { parametersOf(assistantId ?: "") },
    )
    val navController = LocalNavController.current
    val input by vm.graphInput.collectAsStateWithLifecycle()
    val nodeDetail by vm.nodeDetail.collectAsStateWithLifecycle()

    MemoryGraphCanvas(
        input = input,
        initialFocusId = focusNodeId,
        onOpenNode = { vm.openNode(it) },
        onTogglePin = { id, pinned -> vm.setPinned(id, pinned) },
    )

    nodeDetail?.let { detail ->
        MemoryNodeSheet(
            detail = detail,
            onDismiss = { vm.closeNode() },
            onPin = { vm.setPinned(detail.node.id, !detail.node.pinned) },
            onForget = { vm.forget(detail.node.id); vm.closeNode() },
            onRestore = { vm.restore(detail.node.id) },
            onEdit = { vm.edit(detail.node.id, it) },
            onOpenConversation = { convId ->
                runCatching { Uuid.parse(convId) }.getOrNull()?.let { navigateToChatPage(navController, chatId = it) }
            },
            onOpenGraph = null, // already on the graph
        )
    }
}

@Composable
private fun MemoryGraphCanvas(
    input: MemoryGraphInput,
    initialFocusId: String?,
    onOpenNode: (String) -> Unit,
    onTogglePin: (nodeId: String, pinned: Boolean) -> Unit,
) {
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    val haptics = rememberPremiumHaptics()
    val palette = rememberGraphPalette()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontSize = 11.sp) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

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
    var userInteracted by remember(input, expandedIds) { mutableStateOf(false) }

    var render by remember { mutableStateOf<GraphRender?>(null) }
    var fitRequest by remember { mutableStateOf(FitRequest(0, animate = false)) }

    // Focus mode: the tapped node's neighborhood stays lit; everything else fades to near-transparent.
    var focusedId by remember { mutableStateOf(initialFocusId) }
    val focusAnim = remember { Animatable(if (initialFocusId != null) 1f else 0f) }

    val labels = remember(render?.prepared, labelStyle) {
        render?.prepared?.nodes?.map { textMeasurer.measure(it.label, labelStyle) } ?: emptyList()
    }
    val labelSizes = remember(labels) {
        labels.map { MemoryGraphLabels.LabelSize(it.size.width.toFloat(), it.size.height.toFloat()) }
    }
    val highlightIndices = remember(render?.prepared, matchIds) {
        val p = render?.prepared ?: return@remember IntArray(0)
        if (matchIds.isEmpty()) IntArray(0)
        else p.nodes.indices.filter { p.nodes[it].id in matchIds }.toIntArray()
    }

    // Focus neighborhood (1-hop) computed from the prepared edges when focus changes.
    val focusNeighbors = remember(render?.prepared, focusedId) {
        val p = render?.prepared ?: return@remember null
        val fid = focusedId ?: return@remember null
        val focusIdx = p.nodes.indexOfFirst { it.id == fid }
        if (focusIdx < 0) return@remember null
        val set = HashSet<Int>()
        set.add(focusIdx)
        for (e in p.edges) {
            if (e.from == focusIdx) set.add(e.to)
            if (e.to == focusIdx) set.add(e.from)
        }
        set
    }

    // ---- build + simulate off-thread ----
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
        fitRequest = FitRequest(fitRequest.token + 1, animate = false)

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
        if (!userInteracted && query.isBlank() && focusedId == null) {
            fitRequest = FitRequest(fitRequest.token + 1, animate = true)
        }
    }

    // Auto-fit once the canvas size is known.
    LaunchedEffect(fitRequest, canvasSize) {
        val r = render ?: return@LaunchedEffect
        if (r.prepared.isEmpty || canvasSize == IntSize.Zero) return@LaunchedEffect
        val fit = computeFit(r.positions, r.prepared, canvasSize) ?: return@LaunchedEffect
        flyCamera(cameraScale, cameraOffset, fit, animate = fitRequest.animate && !reduceMotion) { s, o ->
            cameraScale = s; cameraOffset = o
        }
    }

    // Search flies the camera to frame matches.
    LaunchedEffect(matchIds, render?.frozen, canvasSize) {
        if (matchIds.isEmpty() || canvasSize == IntSize.Zero) return@LaunchedEffect
        val r = render ?: return@LaunchedEffect
        val indices = r.prepared.nodes.indices.filter { r.prepared.nodes[it].id in matchIds }
        if (indices.isEmpty()) return@LaunchedEffect
        val fit = computeFit(r.positions, r.prepared, canvasSize, onlyIndices = indices, zoomCap = MATCH_ZOOM_CAP) ?: return@LaunchedEffect
        userInteracted = true
        flyCamera(cameraScale, cameraOffset, fit, animate = !reduceMotion) { s, o -> cameraScale = s; cameraOffset = o }
    }

    // Focus entry: fly the camera to center the focused node and light its neighborhood.
    fun enterFocus(id: String) {
        focusedId = id
        val r = render ?: return
        val idx = r.prepared.nodes.indexOfFirst { it.id == id }
        if (idx < 0) return
        val neighborIdx = buildList {
            add(idx)
            for (e in r.prepared.edges) {
                if (e.from == idx) add(e.to)
                if (e.to == idx) add(e.from)
            }
        }
        scope.launch {
            val fit = computeFit(r.positions, r.prepared, canvasSize, onlyIndices = neighborIdx, zoomCap = FOCUS_ZOOM_CAP)
            if (fit != null) flyCamera(cameraScale, cameraOffset, fit, animate = !reduceMotion) { s, o -> cameraScale = s; cameraOffset = o }
            focusAnim.animateTo(1f, if (reduceMotion) tween(0) else tween(280, easing = FastOutSlowInEasing))
        }
    }

    fun exitFocus() {
        focusedId = null
        scope.launch {
            focusAnim.animateTo(0f, if (reduceMotion) tween(0) else tween(240, easing = FastOutSlowInEasing))
        }
        userInteracted = false
        fitRequest = FitRequest(fitRequest.token + 1, animate = !reduceMotion)
    }

    // Initial focus (deep link) once the render is available.
    LaunchedEffect(render?.prepared, initialFocusId) {
        val fid = initialFocusId ?: return@LaunchedEffect
        val r = render ?: return@LaunchedEffect
        if (r.prepared.nodes.any { it.id == fid }) enterFocus(fid)
    }

    BackHandler(enabled = focusedId != null) { exitFocus() }

    // ---- LOD labels: recompute only when scale changes >2% or the frame swaps ----
    var visibleLabels by remember { mutableStateOf(IntArray(0)) }
    LaunchedEffect(render?.prepared) {
        val r = render ?: return@LaunchedEffect
        if (r.prepared.isEmpty) { visibleLabels = IntArray(0); return@LaunchedEffect }
        var lastScale = -1f
        snapshotFlow { Triple(cameraScale, cameraOffset, r.positions) }
            .collect { (scale, offset, positions) ->
                if (abs(scale - lastScale) < lastScale * 0.02f && lastScale > 0f) return@collect
                lastScale = scale
                val n = r.prepared.nodes.size
                val xs = FloatArray(n) { positions[it * 2] }
                val ys = FloatArray(n) { positions[it * 2 + 1] }
                val radii = FloatArray(n) { r.prepared.nodes[it].radius }
                visibleLabels = withContext(Dispatchers.Default) {
                    MemoryGraphLabels.computeVisibleLabels(
                        worldX = xs, worldY = ys, radii = radii, labelSizes = labelSizes,
                        scale = scale, offsetX = offset.x, offsetY = offset.y,
                        minScreenRadiusPx = LABEL_MIN_SCREEN_RADIUS,
                    )
                }
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
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = cameraScale
                            val newScale = (oldScale * zoom).coerceIn(GRAPH_MIN_SCALE, GRAPH_MAX_SCALE)
                            cameraOffset = centroid - (centroid - cameraOffset) * (newScale / oldScale) + pan
                            cameraScale = newScale
                            userInteracted = true
                            // Pinch-out while focused exits focus mode.
                            if (focusedId != null && zoom < 0.985f) exitFocus()
                        }
                    }
                    .pointerInput(render) {
                        detectTapGestures(
                            onTap = { p ->
                                val hit = hitTest(render, p, cameraOffset, cameraScale) ?: return@detectTapGestures
                                haptics.perform(HapticPattern.Pop)
                                userInteracted = true
                                if (focusedId == hit.id) {
                                    onOpenNode(hit.id) // second tap on the focused node → detail sheet
                                } else {
                                    expandedIds = expandedIds + hit.id
                                    enterFocus(hit.id)
                                }
                            },
                            onLongPress = { p ->
                                val hit = hitTest(render, p, cameraOffset, cameraScale) ?: return@detectTapGestures
                                haptics.perform(HapticPattern.Thud)
                                onTogglePin(hit.id, !hit.pinned)
                            },
                        )
                    },
            ) {
                // Node/edge layer under the graphicsLayer transform (frozen frame, no recompute on pan/zoom).
                Canvas(
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
                    drawGraphNodes(r, highlightIndices, palette, focusNeighbors, focusAnim.value)
                }

                // Label overlay: NOT under graphicsLayer; reads camera state in the draw phase so only
                // the draw is invalidated (never composition), and labels stay constant screen size.
                Canvas(Modifier.fillMaxSize()) {
                    val r = render ?: return@Canvas
                    drawGraphLabels(r, labels, visibleLabels, palette, cameraScale, cameraOffset, focusNeighbors, focusAnim.value)
                }
            }

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

            IconButton(
                onClick = {
                    if (focusedId != null) exitFocus() else {
                        userInteracted = false
                        fitRequest = FitRequest(fitRequest.token + 1, animate = !reduceMotion)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50)),
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Re-center")
            }

            GraphLegend(
                truncated = prepared?.truncated ?: 0,
                shown = prepared?.nodes?.size ?: 0,
                palette = palette,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}

private data class FitRequest(val token: Int, val animate: Boolean)

private const val STEP_BATCH = 6
private const val FRAME_DELAY_MS = 16L
private const val MATCH_ZOOM_CAP = 1.8f
private const val FOCUS_ZOOM_CAP = 2.2f
private const val TOUCH_SLOP_PX = 14f
private const val LABEL_MIN_SCREEN_RADIUS = 13f
private const val FOCUS_DIM_ALPHA = 0.07f

// ─────────────────────────────── drawing ───────────────────────────────

private fun DrawScope.drawGraphNodes(
    render: GraphRender,
    highlightIndices: IntArray,
    palette: GraphPalette,
    focusNeighbors: Set<Int>?,
    focusT: Float,
) {
    val pos = render.positions
    val nodes = render.prepared.nodes
    if (pos.size < nodes.size * 2) return

    fun dim(index: Int, base: Float): Float {
        if (focusNeighbors == null || focusT <= 0f) return base
        val lit = index in focusNeighbors
        val target = if (lit) base else FOCUS_DIM_ALPHA * base
        return base + (target - base) * focusT
    }

    for (e in render.prepared.edges) {
        val a = e.from; val b = e.to
        val faded = nodes[a].faded || nodes[b].faded
        val base = if (faded) 0.12f else 0.22f
        val edgeLit = focusNeighbors == null || (a in focusNeighbors && b in focusNeighbors)
        val alpha = if (focusNeighbors == null || focusT <= 0f) base
        else base + ((if (edgeLit) base else FOCUS_DIM_ALPHA * base) - base) * focusT
        drawLine(
            color = palette.edge.copy(alpha = alpha),
            start = Offset(pos[a * 2], pos[a * 2 + 1]),
            end = Offset(pos[b * 2], pos[b * 2 + 1]),
            strokeWidth = 1.5f,
        )
    }

    val highlight = HashSet<Int>(highlightIndices.size * 2).apply { highlightIndices.forEach { add(it) } }
    for (i in nodes.indices) {
        val n = nodes[i]
        val center = Offset(pos[i * 2], pos[i * 2 + 1])
        val alpha = dim(i, if (n.faded) 0.42f else 1f)
        drawCircle(color = graphNodeFill(n.type, palette).copy(alpha = alpha), radius = n.radius, center = center)
        if (n.provisional) {
            drawCircle(
                color = palette.onSurface.copy(alpha = 0.55f * alpha),
                radius = n.radius + 2.5f,
                center = center,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
            )
        }
        if (n.pinned) drawCircle(color = palette.pin.copy(alpha = dim(i, 1f)), radius = n.radius + 3.5f, center = center, style = Stroke(width = 2f))
        if (i in highlight) drawCircle(color = palette.highlight, radius = n.radius + 6f, center = center, style = Stroke(width = 2.5f))
    }
}

/** Labels are drawn in screen space (constant size) so they never overlap and zoom reveals more. */
private fun DrawScope.drawGraphLabels(
    render: GraphRender,
    labels: List<TextLayoutResult>,
    visible: IntArray,
    palette: GraphPalette,
    scale: Float,
    offset: Offset,
    focusNeighbors: Set<Int>?,
    focusT: Float,
) {
    val pos = render.positions
    val nodes = render.prepared.nodes
    if (pos.size < nodes.size * 2) return
    for (i in visible) {
        if (i >= nodes.size) continue
        val n = nodes[i]
        val layout = labels.getOrNull(i) ?: continue
        val nodeScreenX = pos[i * 2] * scale + offset.x
        val nodeScreenY = pos[i * 2 + 1] * scale + offset.y
        var alpha = if (n.faded) 0.55f else 0.95f
        if (focusNeighbors != null && focusT > 0f) {
            val lit = i in focusNeighbors
            val target = if (lit) alpha else FOCUS_DIM_ALPHA * alpha
            alpha += (target - alpha) * focusT
        }
        drawText(
            textLayoutResult = layout,
            color = palette.onSurface.copy(alpha = alpha),
            topLeft = Offset(nodeScreenX - layout.size.width / 2f, nodeScreenY + n.radius * scale + 2f),
        )
    }
}

// ─────────────────────────────── camera / hit-test ───────────────────────────────

private fun hitTest(render: GraphRender?, screen: Offset, offset: Offset, scale: Float): me.rerere.rikkahub.ui.pages.memory.GraphVizNode? {
    val r = render ?: return null
    val nodes = r.prepared.nodes
    val pos = r.positions
    if (pos.size < nodes.size * 2) return null
    val world = (screen - offset) / scale
    val slop = TOUCH_SLOP_PX / scale
    for (i in nodes.indices.reversed()) {
        val dx = world.x - pos[i * 2]
        val dy = world.y - pos[i * 2 + 1]
        val reach = nodes[i].radius + slop
        if (dx * dx + dy * dy <= reach * reach) return nodes[i]
    }
    return null
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
    return node.content.contains(q, ignoreCase = true) || (node.displayLabel?.contains(q, ignoreCase = true) == true)
}

// ─────────────────────────────── legend ───────────────────────────────

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
