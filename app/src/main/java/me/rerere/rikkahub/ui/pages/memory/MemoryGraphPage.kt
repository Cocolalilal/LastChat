package me.rerere.rikkahub.ui.pages.memory

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
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
 * Full-screen live memory graph, per the brief:
 *  - pan/zoom is a pure `graphicsLayer` transform over a frozen frame — always smooth;
 *  - labels appear **smoothly** as you zoom in, with a hard zero-overlap guarantee
 *    ([MemoryGraphLabels] picks the visible set; each label cross-fades in/out individually);
 *  - node size ∝ relation count (degree; see [MemoryGraphBuilder.radiusFor]);
 *  - tap a node → focus mode: the camera flies to it, its neighborhood stays, the rest fades out;
 *    back gesture / pinch-out / recenter fades everything back; tapping the focused node again
 *    opens the same node sheet used everywhere else;
 *  - reduced motion → pre-settled static layout, no camera or fade animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryGraphPage(assistantId: String?, focusNodeId: String? = null) {
    val vm: MemoryVM = koinViewModel(
        key = "graph_${assistantId ?: "global"}",
        parameters = { parametersOf(assistantId ?: "") },
    )
    val navController = LocalNavController.current
    val input by vm.graphInput.collectAsStateWithLifecycle()
    val nodeDetail by vm.nodeDetail.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (vm.isGlobal) "Shared memory graph"
                        else (assistant?.name?.takeIf { it.isNotBlank() } ?: "Memory graph"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            MemoryGraphCanvas(
                input = input,
                initialFocusId = focusNodeId,
                onOpenNode = { vm.openNode(it) },
                onTogglePin = { id, pinned -> vm.setPinned(id, pinned) },
            )
        }
    }

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

    // Focus mode.
    var focusedId by remember { mutableStateOf(initialFocusId) }
    val focusAnim = remember { Animatable(if (initialFocusId != null) 1f else 0f) }
    // Cumulative pinch since focus entry — zooming out noticeably exits focus.
    var focusPinchAccum by remember { mutableFloatStateOf(1f) }

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

    // ---- build + simulate off-thread; swap whole immutable frames ----
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

    fun enterFocus(id: String) {
        focusedId = id
        focusPinchAccum = 1f
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
        if (focusedId == null) return
        focusedId = null
        scope.launch {
            focusAnim.animateTo(0f, if (reduceMotion) tween(0) else tween(240, easing = FastOutSlowInEasing))
        }
        userInteracted = false
        fitRequest = FitRequest(fitRequest.token + 1, animate = !reduceMotion)
    }

    LaunchedEffect(render?.prepared, initialFocusId) {
        val fid = initialFocusId ?: return@LaunchedEffect
        val r = render ?: return@LaunchedEffect
        if (r.prepared.nodes.any { it.id == fid }) enterFocus(fid)
    }

    BackHandler(enabled = focusedId != null) { exitFocus() }

    // ---- LOD labels: zero-overlap target set + smooth per-label cross-fade ----
    var labelTargets by remember { mutableStateOf(IntArray(0)) }
    LaunchedEffect(render?.prepared, labelSizes) {
        val r = render ?: return@LaunchedEffect
        if (r.prepared.isEmpty || labelSizes.isEmpty()) { labelTargets = IntArray(0); return@LaunchedEffect }
        var lastScale = -1f
        snapshotFlow { Triple(cameraScale, cameraOffset, r.positions) }
            .collect { (scale, offset, positions) ->
                // Overlap is pan-invariant (labels are constant screen size), so recompute only on
                // meaningful zoom changes or a new frame.
                if (lastScale > 0f && abs(scale - lastScale) < lastScale * 0.02f) return@collect
                lastScale = scale
                val n = r.prepared.nodes.size
                val xs = FloatArray(n) { positions[it * 2] }
                val ys = FloatArray(n) { positions[it * 2 + 1] }
                val radii = FloatArray(n) { r.prepared.nodes[it].radius }
                labelTargets = withContext(Dispatchers.Default) {
                    MemoryGraphLabels.computeVisibleLabels(
                        worldX = xs, worldY = ys, radii = radii, labelSizes = labelSizes,
                        scale = scale, offsetX = offset.x, offsetY = offset.y,
                        minScreenRadiusPx = LABEL_MIN_SCREEN_RADIUS,
                    )
                }
            }
    }

    // Per-label alphas eased toward the target set (~150ms); whole-array swaps drive draw only.
    var labelAlphas by remember { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(render?.prepared, reduceMotion) {
        val r = render ?: return@LaunchedEffect
        val n = r.prepared.nodes.size
        if (labelAlphas.size != n) labelAlphas = FloatArray(n)
        snapshotFlow { labelTargets }.collectLatest { targets ->
            val targetSet = targets.toHashSet()
            if (reduceMotion) {
                labelAlphas = FloatArray(n) { if (it in targetSet) 1f else 0f }
                return@collectLatest
            }
            var animating = true
            var lastNanos = 0L
            while (isActive && animating) {
                withFrameNanos { nanos ->
                    val dt = if (lastNanos == 0L) 16_000_000L else (nanos - lastNanos)
                    lastNanos = nanos
                    val step = (dt / 1_000_000f) / LABEL_FADE_MS // fraction of the fade per frame
                    val current = labelAlphas
                    val next = FloatArray(n)
                    animating = false
                    for (i in 0 until n) {
                        val target = if (i in targetSet) 1f else 0f
                        val cur = current.getOrElse(i) { 0f }
                        val v = when {
                            cur < target -> (cur + step).coerceAtMost(target)
                            cur > target -> (cur - step).coerceAtLeast(target)
                            else -> cur
                        }
                        next[i] = v
                        if (abs(v - target) > 0.001f) animating = true
                    }
                    labelAlphas = next
                }
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
                            if (focusedId != null) {
                                focusPinchAccum *= zoom
                                if (focusPinchAccum < FOCUS_EXIT_ZOOM) exitFocus()
                            }
                        }
                    }
                    .pointerInput(render) {
                        detectTapGestures(
                            onTap = { p ->
                                val hit = hitTest(render, p, cameraOffset, cameraScale)
                                if (hit == null) return@detectTapGestures
                                haptics.perform(HapticPattern.Pop)
                                userInteracted = true
                                if (focusedId == hit.id) {
                                    onOpenNode(hit.id) // second tap on the focused node → node sheet
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
                // Node/edge layer: frozen frame under the camera transform (60fps pan/zoom).
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

                // Label overlay: screen space (constant text size). Reads camera + alphas in the
                // draw phase only, so pan/zoom never recomposes.
                Canvas(Modifier.fillMaxSize()) {
                    val r = render ?: return@Canvas
                    drawGraphLabels(r, labels, labelAlphas, palette, cameraScale, cameraOffset, focusNeighbors, focusAnim.value)
                }
            }

            // Search pill, app style.
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

            // Round teal recenter (exits focus first), matching the app's round FABs.
            FloatingActionButton(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    if (focusedId != null) exitFocus() else {
                        userInteracted = false
                        fitRequest = FitRequest(fitRequest.token + 1, animate = !reduceMotion)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Re-center")
            }
        }
    }
}

private data class FitRequest(val token: Int, val animate: Boolean)

private const val STEP_BATCH = 6
private const val FRAME_DELAY_MS = 16L
private const val MATCH_ZOOM_CAP = 1.8f
private const val FOCUS_ZOOM_CAP = 2.2f
private const val FOCUS_EXIT_ZOOM = 0.8f
private const val TOUCH_SLOP_PX = 14f
private const val LABEL_MIN_SCREEN_RADIUS = 13f
private const val LABEL_FADE_MS = 150f
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

/** Labels drawn in screen space (constant size) with individual cross-fade alphas — never overlap. */
private fun DrawScope.drawGraphLabels(
    render: GraphRender,
    labels: List<TextLayoutResult>,
    alphas: FloatArray,
    palette: GraphPalette,
    scale: Float,
    offset: Offset,
    focusNeighbors: Set<Int>?,
    focusT: Float,
) {
    val pos = render.positions
    val nodes = render.prepared.nodes
    if (pos.size < nodes.size * 2) return
    for (i in nodes.indices) {
        val fade = alphas.getOrElse(i) { 0f }
        if (fade <= 0.01f) continue
        val n = nodes[i]
        val layout = labels.getOrNull(i) ?: continue
        val nodeScreenX = pos[i * 2] * scale + offset.x
        val nodeScreenY = pos[i * 2 + 1] * scale + offset.y
        var alpha = (if (n.faded) 0.55f else 0.95f) * fade
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

private fun hitTest(render: GraphRender?, screen: Offset, offset: Offset, scale: Float): GraphVizNode? {
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
