package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Soft-3D mark — desktop Grok Bot 0.68.1 body (flat fill of the projected solid) with
 * eyes seated on the body surface in BOTH eye modes:
 *  - Grok: desktop capsule eyes (sphere SS / mesh `sx` seats), morphing between expressions.
 *  - Generical: the reference SVG eyes, resampled and morphed, seated with the same
 *    surface math so they wrap and foreshorten with the solid.
 * Motion comes from [AvatarMotionState] (crossfaded clips, gaze, blinks, alive layer).
 * Gaze rests forward (no Grok up-right brand stare).
 */
@Composable
fun AnimatedMarkAvatar(
    shapeId: String,
    eyeType: String,
    colorHex: String,
    isLoading: Boolean,
    isTyping: Boolean = false,
    colorPreset: String? = null,
    /** Null = mode default eye color. */
    eyeColorHex: String? = null,
    /** Generical eye bottom shade on/off (glides between the two). */
    eyeShade: Boolean = true,
    /** Character traits; null = sensible default for the eye style. */
    personality: Personality? = null,
    calmPreview: Boolean = false,
    /**
     * False = static (older chat messages): eases to the rest pose and stops; flipping
     * back to true eases into motion again. Ignored for calm previews / harness frames.
     */
    animate: Boolean = true,
    modifier: Modifier = Modifier,
    /** Render-harness hook: freeze the live path at this clip pose. Null in the app. */
    debugPose: ClipSample? = null,
    /** Render-harness hook: full frame override (pose, gaze, lids, expression morph). */
    debugFrame: MotionFrame? = null,
    /** Editor hook: the latest control change, so the avatar can notice it (engine16.2). */
    editorCue: EditorCue? = null,
    /** Harness hook: drive the shape/eye-style morph progress by hand (0…1); null = animate. */
    debugMorph: Float? = null,
) {
    // Engine17.2: retired shapes (house, wedge) migrate to the blob.
    val shapeIdM = MarkShapes.migrate(shapeId)
    val context = LocalContext.current
    LaunchedEffect(Unit) { AvatarPackData.ensureLoaded(context) }
    runCatching { AvatarPackData.ensureLoaded(context) }

    val frozen = calmPreview || debugPose != null || debugFrame != null
    val live = animate && !frozen
    val motionState = rememberAvatarMotionState(
        isLoading = if (frozen) false else isLoading,
        isTyping = if (frozen) false else isTyping,
        enabled = !frozen,
        animate = live,
    )
    val isGrok = eyeType.equals("grok", ignoreCase = true)
    // Swatch colour == rendered colour (see MarkColors).
    // Body colour glides on a change; the eye shade below follows it live.
    // Engine16.2c: the tweens are read in the draw phase only (no recomposition per frame).
    val bodyColorS = androidx.compose.animation.animateColorAsState(
        targetValue = MarkColors.bodyColor(colorHex, colorPreset, eyeType),
        animationSpec = androidx.compose.animation.core.tween(380),
        label = "markBody",
    )
    val eyeColorS = androidx.compose.animation.animateColorAsState(
        targetValue = MarkColors.eyeColor(eyeType, eyeColorHex, colorHex, colorPreset),
        animationSpec = androidx.compose.animation.core.tween(380),
        label = "markEye",
    )
    val shadeS = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (eyeShade) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(380),
        label = "eyeShade",
    )

    // Engine16.2: a shape or eye-style swap morphs the geometry from whatever was on screen
    // (even mid-morph) to the new mark - no pop, no size snap.
    val morphAnim = remember { Animatable(1f) }
    val morphHold = remember { MorphHolder() }
    val lastSig = remember { mutableStateOf("$shapeIdM|${eyeType.lowercase()}") }
    val sigNow = "$shapeIdM|${eyeType.lowercase()}"
    if (lastSig.value != sigNow && !frozen) {
        lastSig.value = sigNow
        // Engine16.2c flicker fix: the animation can only snap to 0 in the effect, which
        // runs after this frame is drawn; until then the draw pins progress at 0 so the
        // first transition frame is exactly what was on screen (not the target).
        morphHold.signatureChanged()
    }
    LaunchedEffect(sigNow) {
        if (!morphHold.pending) return@LaunchedEffect
        morphHold.pending = false
        morphAnim.snapTo(0f)
        morphHold.forceZero = false
        morphAnim.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        morphHold.from = null
        morphHold.lastBlend = null
    }
    if (editorCue != null && !frozen) LaunchedEffect(editorCue.seq) { motionState.editorCue(editorCue) }

    // --- shared stage: where am I, so neighbours can glance at me ---
    val stage = LocalAvatarStage.current
    val view = LocalView.current
    val stageId = remember { stage.newId() }
    val liveNow = rememberUpdatedState(live)
    if (!frozen) {
        motionState.stage = stage
        motionState.stageId = stageId
        motionState.personality = personality ?: Personality.defaultFor(eyeType)
        DisposableEffect(stageId) {
            onDispose { stage.remove(stageId) }
        }
    }
    val signature = "$shapeIdM|${eyeType.lowercase()}"
    val lastBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    fun register() {
        val b = lastBounds[0]
        if (!liveNow.value || b == null || b.width <= 0f || b.height <= 0f) {
            stage.remove(stageId)
            return
        }
        val root = view.rootView
        stage.update(
            id = stageId,
            windowKey = System.identityHashCode(root),
            center = b.center,
            radius = minOf(b.width, b.height) / 2f,
            signature = signature,
            windowW = root.width.toFloat(),
            windowH = root.height.toFloat(),
        )
    }
    // Static avatars are not on the stage (nobody glances at a statue); when one comes
    // alive again it re-registers right away, not on the next layout pass.
    if (!frozen) LaunchedEffect(live) { register() }

    var boxModifier = modifier
    if (!frozen) {
        boxModifier = boxModifier
            .onGloballyPositioned { coords ->
                lastBounds[0] = coords.boundsInWindow()
                register()
            }
            .pointerInput(Unit) {
                // Observe pokes without consuming, so parent click handlers still work.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Engine19: where the finger landed (−1…1, y down) — attention goes there.
                    val w = size.width.coerceAtLeast(1); val h = size.height.coerceAtLeast(1)
                    motionState.poke(down.position.x / w * 2f - 1f, down.position.y / h * 2f - 1f)
                }
            }
    }

    val backdrop = ContrastGuard.LocalAvatarBackdrop.current ?: androidx.compose.material3.MaterialTheme.colorScheme.background
    Box(modifier = boxModifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!AvatarPackData.isLoaded()) return@Canvas
            val f = when {
                calmPreview -> CALM_FRAME
                debugFrame != null -> debugFrame
                debugPose != null -> frameFromClip(debugPose)
                else -> motionState.frame
            }
            val shape = MarkShapes.resolve(shapeIdM) ?: return@Canvas
            val bodyColor = bodyColorS.value
            val eyeColor = eyeColorS.value
            val eyeFade = MarkColors.genericalEyeFade(bodyColor, eyeColor, shadeS.value)
            ContrastGuard.set(bodyColor, eyeColor, backdrop)
            if (!frozen) {
                drawLive(morphHold, shape, shapeIdM, isGrok, f, calmPreview, bodyColor, eyeColor, eyeFade, morphAnim.value)
                ContrastGuard.reset()
                return@Canvas
            }
            drawMark(
                shape = shape,
                shapeId = shapeIdM,
                isGrok = isGrok,
                f = f,
                calm = calmPreview,
                morph = 1f,
                body = bodyColor,
                eye = eyeColor,
                eyeFade = eyeFade,
            )
            ContrastGuard.reset()
        }
    }
}

internal val CALM_FRAME = MotionFrame()

/** Mark a shape / eye-style change: the next frame starts the morph from what is on screen. */
internal fun MorphHolder.signatureChanged() { needFrom = true; forceZero = true; pending = true }

/**
 * Live draw with the seamless morph (engine16.2c). [animValue] is the morph animation's
 * value, which is stale (1) on the change frame; [MorphHolder.forceZero] pins it to 0.
 */
internal fun DrawScope.drawLive(
    h: MorphHolder, shape: GrokShape, shapeId: String, isGrok: Boolean, f: MotionFrame, calm: Boolean,
    bodyColor: Color, eyeColor: Color, eyeFade: Color, animValue: Float,
) {
    if (h.needFrom) {
        h.needFrom = false
        // From = what is on screen: the current blend mid-morph, else the mark as last drawn
        // (captured once, here, at this pose).
        val g = h.lastBlend ?: h.drawnShape?.let { ps ->
            MarkShapes.resolve(ps)?.let { psh -> MarkGeom().also { drawMark(psh, ps, h.drawnGrok, f, false, 1f, bodyColor, eyeColor, eyeFade, sink = it) } }
        }
        h.from = g; h.fromPts = g?.let { MorphPts.of(it) }
    }
    val u = if (h.forceZero) 0f else animValue
    val from = h.from
    if (from != null && u < 1f) {
        val now = h.target
        drawMark(shape, shapeId, isGrok, f, calm, 1f, bodyColor, eyeColor, eyeFade, sink = now)
        val out = MarkGeom()
        drawMorph(from, h.fromPts!!, now, u, out)
        h.lastBlend = out
    } else {
        drawMark(shape, shapeId, isGrok, f, calm, 1f, bodyColor, eyeColor, eyeFade)
    }
    h.drawnShape = shapeId; h.drawnGrok = isGrok
}

internal class MorphHolder {
    var drawnShape: String? = null
    var drawnGrok = false
    var lastBlend: MarkGeom? = null
    var from: MarkGeom? = null
    var fromPts: MorphPts? = null
    val target = MarkGeom()
    var needFrom = false
    var forceZero = false
    var pending = false
}

/** A change made in the avatar editor. [at] = control centre in window px (null = unknown). */
data class EditorCue(val seq: Int, val kind: Kind, val at: Offset? = null) {
    enum class Kind { Shape, EyeStyle, BodyColor, EyeColor, Shade }
}

private val NEUTRAL_GAP_CACHE = HashMap<String, Float>()

/** Thinnest a Grok eye gets when the lids are shut (fraction of the open height). */
internal const val GROK_SLIT = 0.12f

/** Clear margin kept between the eyes and the silhouette edge (fraction of mark radius). */
internal const val GROK_EDGE_MARGIN = 0.20f
internal const val GEN_EDGE_MARGIN = 0.21f
/** Smaller silhouette-clip guard margin for Generical nudge / per-eye horizontal squash. */
internal const val GEN_CLIP_MARGIN = 0.05f

/** Harness: a raw clip pose as a frame (expression from the clip, eyes open). */
private fun frameFromClip(c: ClipSample): MotionFrame = MotionFrame(
    body = BodyPose(
        pitch = c.rotationDeg[0],
        yaw = c.rotationDeg[1],
        roll = c.rotationDeg[2],
        posY = c.position[1],
        squashX = c.squash[0],
        squashY = c.squash[1],
        scale = c.scale / MarkClipEngine.DEFAULT_ZOOM,
        lidOpen = c.lids,
    ),
    exprFrom = ExpressionDirector.normalize(c.expression),
    exprTo = ExpressionDirector.normalize(c.expression),
)

/**
 * Character-moment spins (engine12b) are real soft-3D rotations: the spin is added to the
 * solid's yaw after the per-shape turn cap, the ring mesh is projected at that angle, and the
 * eyes are seated rigidly on the rotated surface (same mesh projection, no safe-region squeeze,
 * no drift); an eye disappears when its surface point turns past the limb, and the silhouette
 * clip occludes any part beyond the edge. The body stays one flat fill.
 */
internal fun DrawScope.drawMark(
    shape: GrokShape,
    shapeId: String,
    isGrok: Boolean,
    f: MotionFrame,
    calm: Boolean,
    morph: Float,
    body: Color,
    eye: Color,
    eyeFade: Color,
    sink: MarkGeom? = null,
) {
    drawMarkInner(shape, shapeId, isGrok, f, calm, morph, body, eye, eyeFade, sink)
}

private fun DrawScope.drawMarkInner(
    shape: GrokShape,
    shapeId: String,
    isGrok: Boolean,
    f: MotionFrame,
    calm: Boolean,
    morph: Float,
    body: Color,
    eye: Color,
    eyeFade: Color,
    sink: MarkGeom? = null,
) {
    val he = AvatarPackData.grok.headCenter
    val bodyRaw = AvatarPackData.parseSvgPath(shape.pathData)
    val bodyCentered = Path().apply { addPath(bodyRaw, Offset(-he, -he)) }
    val sizePx = size.minDimension * Soft3DMark.FILL * shape.scale.coerceAtLeast(0.95f)
    val bodyKind = shape.solid.kind

    // Head look (gaze) is an euler add-on on body + eyes (desktop setGaze → Ou).
    // Screen y (+down) → pitch (+down). LOOK_TURN_GAIN lets glances read as visible body turns.
    val look = WhSphereEyes.ou(bodyKind, f.headLookX * LOOK_TURN_GAIN, -f.headLookY * LOOK_TURN_GAIN)
    // Visual turn cap (engine10): total body + head turn is soft-limited per shape so the
    // silhouette stays recognisable (the heart's 3D projection lumps past ~14°) and the
    // eyes keep their inset. tanh keeps it springy instead of a hard stop.
    val (yawCap, pitchCap) = turnCapFor(shape.id, bodyKind)
    val pitch = softCap(f.body.pitch + look.pitchDeg, pitchCap) + f.spinPitch
    val spinning = kotlin.math.abs(f.spinYaw) >= 0.5f || kotlin.math.abs(f.spinPitch) >= 0.5f || kotlin.math.abs(f.spinRoll) >= 0.5f
    val yaw = softCap(f.body.yaw + look.yawDeg, yawCap) + f.spinYaw
    val roll = f.body.roll + f.spinRoll
    val clip = ClipSample(
        clipName = "live",
        time = 0f,
        duration = 1f,
        squashAnchor = 0f,
        rotationDeg = floatArrayOf(pitch, yaw, roll),
        position = floatArrayOf(0f, f.body.posY),
        squash = floatArrayOf(f.body.squashX, f.body.squashY),
        scale = MarkClipEngine.DEFAULT_ZOOM * f.body.scale,
        lids = 1f,
        expression = f.exprTo,
    )
    val yawR = Math.toRadians(yaw.toDouble()).toFloat()
    val pitchR = Math.toRadians(pitch.toDouble()).toFloat()
    val rollR = Math.toRadians(roll.toDouble()).toFloat()
    val projected = Soft3DMark.projectBody(bodyCentered, shape.solid, yawR, pitchR, rollR, he, shapeId = shape.id)

    if (sink != null) {
        sink.body = projected; sink.clip = clip; sink.sizePx = sizePx; sink.he = he
        sink.bodyColor = body; sink.eyeColor = eye; sink.eyeFade = eyeFade; sink.isGrok = isGrok
        sink.fit = WhEyeSeat.genericalFitScale(shapeId)
    } else Soft3DMark.drawBody(
        drawScope = this,
        bodyPath = bodyCentered,
        fill = body,
        solid = shape.solid,
        clip = clip,
        gazeX = 0f,
        gazeY = 0f,
        sizePx = sizePx,
        morphProgress = morph,
        markRadius = he,
        mainFillPath = projected,
        calmFaceOn = calm,
        allowXBob = false,
        shapeId = shape.id,
    )
    if (sink == null) with(ContrastGuard) {
        if (bodyK >= 0.01f) Soft3DMark.withBodyTransform(this@drawMarkInner, clip, sizePx, he, morph, false) {
            // Outer silhouette only: the projected body can carry inner facet edges mid-spin.
            // Engine18: the true outer boundary (path union), so concave tips mid-spin keep their
            // shape (the radial silhouette kinked at the star's side tips); radial is the fallback.
            val sil = Path().apply { op(projected, Path(), androidx.compose.ui.graphics.PathOperation.Union) }
            bodyOutline(if (sil.isEmpty) radialSilhouette(projected).let { radialPath(it, it, 1f, Path()) } else sil, he)
        }
    }

    val lid = f.lid.coerceIn(0f, 1f)
    if (isGrok) {
        // A closed Grok eye is a thin slit, never nothing: the eyes must not vanish
        // (and pop back) during blinks or blink-masked expression changes.
        val lidG = lid.coerceAtLeast(GROK_SLIT)
        // Engine16.1: eyes that are already closed (sleepy arcs, ^ ^ happy arcs) never blink —
        // the lid is ignored in proportion to how far the face is into a closed shape.
        val ws = f.exprWeights
        val closedW = if (ws != null) ws.sumOf { (n, w) -> if (WhSphereEyes.grokNameFor(n) in GROK_CLOSED) w.toDouble() else 0.0 }.toFloat() else {
            val closedPair = (if (WhSphereEyes.grokNameFor(f.exprFrom) in GROK_CLOSED) 1f - f.exprT else 0f) +
                (if (WhSphereEyes.grokNameFor(f.exprTo) in GROK_CLOSED) f.exprT else 0f)
            val topW = if (f.exprTop != null) f.exprTopT.coerceIn(0f, 1f) else 0f
            closedPair * (1f - topW) +
                (if (f.exprTop != null && WhSphereEyes.grokNameFor(f.exprTop) in GROK_CLOSED) topW else 0f)
        }
        val lidOpenG = lidG + (1f - lidG) * closedW.coerceIn(0f, 1f)
        // Engine21.1: the reopen settle stretches open pills a hair (closed shapes ignore it).
        val popG = 1f + f.eyePop * (1f - closedW.coerceIn(0f, 1f))
        val lidForPair = (if (bodyKind == "sphere") lidOpenG * f.eyeScale.coerceIn(0.5f, 1.2f) else lidOpenG) * popG
        val symFace = f.face.grokSymmetric()
        val pair = (if (ws != null) WhSphereEyes.morphWeighted(ws, lidForPair)
            else WhSphereEyes.morphPair(f.exprFrom, f.exprTo, f.exprT, lidForPair, f.exprTop, f.exprTopT))
            .mapIndexed { i, e -> FaceRig.shapeCapsule(e, i, symFace.eye(i)) }
        val safeBody = EyeSafeRegion.Body(projected)
        // The turn applied to the eyes is scaled down only as far as needed to keep
        // them inside the silhouette (body turn itself is untouched).
        // Engine14: one spacing per shape. The neutral face at rest gets the circle-relative 0.40
        // gap; that centre spacing is baked in for every expression and pose, and expressions
        // only get the tiny never-touch guard below (no widening by tilts or wide arcs).
        val neutralExtra = NEUTRAL_GAP_CACHE.getOrPut("$shapeId|$bodyKind|$he|${f.eyeGap}|${f.eyeScale}") {
            val np = WhSphereEyes.morphPair("Neutral", "Neutral", 1f, 1f).mapIndexed { i, e -> FaceRig.shapeCapsule(e, i, f.face.eye(i)) }
            val (l0, r0) = WhEyeSeat.buildSeatedGrokEyes(
                pickerId = shapeId, solidKind = bodyKind, he = he, gazeX = 0f, gazeY = 0f, lid = 1f,
                eyeGap = f.eyeGap * 0.92f * WhEyeSeat.grokGapFix(shapeId), eyeScale = f.eyeScale,
                timeSec = 0f, awake = 0f, yawDeg = 0f, pitchDeg = 0f, rollDeg = 0f, shape = shape,
                bodyPath = bodyCentered, expressionId = "neutral", eyesOverride = np, leadX = 0f, leadY = 0f,
            )
            if (NEUTRAL_GAP_CACHE.size > 64) NEUTRAL_GAP_CACHE.clear()
            val (dl, dr) = GrokEyeGap.separation(l0, r0, he, WhEyeSeat.grokGapFix(shapeId))
            ((dr.x - dl.x) / 2f / he).coerceAtLeast(0f)
        }
        fun seatGrok(k: Float, yawOverride: Float? = null, extra: Float = neutralExtra) = WhEyeSeat.buildSeatedGrokEyes(
            pickerId = shapeId,
            solidKind = bodyKind,
            he = he,
            gazeX = 0f,
            gazeY = 0f,
            lid = lidOpenG,
            eyeGap = f.eyeGap * 0.92f * WhEyeSeat.grokGapFix(shapeId),
            eyeScale = f.eyeScale,
            timeSec = f.timeSec,
            awake = if (calm || spinning) 0f else 1f,
            yawDeg = yawOverride ?: (yaw * k),
            pitchDeg = pitch * k,
            rollDeg = roll,
            shape = shape,
            bodyPath = bodyCentered,
            expressionId = WhSphereEyes.grokNameFor(f.exprTo),
            // The sphere path seats from the eye states (no half-gap), so the spacing rides offX.
            eyesOverride = if (bodyKind == "sphere" && extra != 0f) pair.map { it.copy(offX = it.offX + extra) } else pair,
            leadX = f.leadX * k,
            leadY = f.leadY * k,
            extraHalfGap = extra,
        )
        // Rigid spin: take the spacing the gap pass gives the un-spun face and bake it into the
        // surface seating, so the eyes stay on fixed surface points for the whole turn.
        fun seatSpin(): Pair<Path, Path> {
            if (bodyKind == "sphere") return seatGrok(1f)
            // The face is frozen during a spin, so the baked spacing is computed once per spin.
            return seatGrok(1f)
        }
        var (left, right) = if (spinning) seatSpin() else if (bodyKind == "sphere") seatGrok(1f)
            else EyeSafeRegion.fit(safeBody, he * GROK_EDGE_MARGIN, ::seatGrok) { listOf(it.first, it.second) }
        // Reject only empty / degenerate seating, never valid capsules.
        fun ok(p: Path): Boolean {
            if (p.isEmpty) return false
            val b = p.getBounds()
            return b.width >= he * 0.02f && b.height >= he * 0.005f && b.width <= he * 1.6f && b.height <= he * 1.6f
        }
        if (!(ok(left) && ok(right)) && bodyKind == "sphere") {
            val ss = WhSphereEyes.projectPair(WhSphereEyes.expressionPair("neutral", lidForPair), pitch, yaw, roll, he)
            left = ss.first; right = ss.second
        }
        // Never let the eyes touch or overlap (Sad ^ ^, closed arcs, mid-morph, turned).
        // A spin is a rigid rotation of the face: the projected gap narrows with the surface, so
        // the gap pass (which would push the eyes off their surface points) is skipped then.
        // Engine21.2: never two eyes leaning the same way (// or \\) — blends, accents on a face
        // and a diagonal gaze's perspective included (head roll is kept). Rigid while spinning.
        if (ok(left) && ok(right) && !spinning) TiltGuard.grok(left, right, roll).let { left = it.first; right = it.second }
        if (ok(left) && ok(right) && !spinning) {
            GrokEyeGap.shrinkInPlace(left, right, he, 1f, GrokEyeGap.TOUCH_GAP).let { left = it.first; right = it.second }
            val (dl, dr) = GrokEyeGap.separation(left, right, he, 1f, GrokEyeGap.TOUCH_GAP)
            if (dl != androidx.compose.ui.geometry.Offset.Zero) {
                left = Path().apply { addPath(left, dl) }
                right = Path().apply { addPath(right, dr) }
            }
        }
        if (ok(left) && ok(right) && !spinning) {
            val (k, c) = EyeSafeRegion.shrink(safeBody, listOf(left, right), he * GROK_EDGE_MARGIN)
            if (k < 1f) {
                // Shrinking about the pair centre narrows the gap a little, so the gap
                // constraint runs again afterwards.
                left = EyeSafeRegion.scalePath(left, k, c)
                right = EyeSafeRegion.scalePath(right, k, c)
                val (dl, dr) = GrokEyeGap.separation(left, right, he, 1f, GrokEyeGap.TOUCH_GAP)
                if (dl != androidx.compose.ui.geometry.Offset.Zero) {
                    left = Path().apply { addPath(left, dl) }
                    right = Path().apply { addPath(right, dr) }
                }
            }
        }
        // Shrink first (keeps the pair centre, so gaze motion survives), then slide in.
        if (ok(left) && ok(right) && !spinning) {
            val n = EyeSafeRegion.nudge(safeBody, listOf(left, right), he * GROK_EDGE_MARGIN)
            if (n != androidx.compose.ui.geometry.Offset.Zero) {
                left = Path().apply { addPath(left, n) }
                right = Path().apply { addPath(right, n) }
            }
        }
        if (AvatarDebugMetrics.enabled && ok(left) && ok(right)) {
            val a = GrokEyeGap.outline(left); val b = GrokEyeGap.outline(right)
            val c = AvatarDebugMetrics.centroid(listOf(left, right)) - safeBody.center
            AvatarDebugMetrics.rows += "${AvatarDebugMetrics.label},grok,${shape.id},${f.exprFrom},${f.exprTo},${f.exprT}," +
                "${GrokEyeGap.clearance(a, b) / he},${EyeSafeRegion.inset(safeBody, listOf(left, right), he * 0.5f) / he},${c.x / he},${c.y / he}"
        }
        if (sink != null) {
            sink.eyes = listOfNotNull(left.takeIf { ok(it) }, right.takeIf { ok(it) }).map { p ->
                val b = p.getBounds(); MarkGeom.Eye(p, Offset(b.center.x, b.top), Offset(b.center.x, b.bottom))
            }
            return
        }
        // While spinning, clip to the rotated silhouette so an eye rolling over the edge wraps away.
        if (spinning) Soft3DMark.withBodyClip(this, clip, projected, sizePx, he, morph, false) {
            if (ok(left)) { drawPath(left, eye); with(ContrastGuard) { eyeOutline(left, he) } }
            if (ok(right)) { drawPath(right, eye); with(ContrastGuard) { eyeOutline(right, he) } }
        } else Soft3DMark.withBodyTransform(this, clip, sizePx, he, morph, false) {
            if (ok(left)) { drawPath(left, eye); with(ContrastGuard) { eyeOutline(left, he) } }
            if (ok(right)) { drawPath(right, eye); with(ContrastGuard) { eyeOutline(right, he) } }
        }
        return
    }

    // Generical: morph → seat on the surface → eye-colour fill fading into the surface tint.
    val closure = (1f - lid).coerceIn(0f, 1f)
    val wsG = f.exprWeights
    val lo = (if (wsG != null) GenericalFace.outlineWeighted(0, wsG, closure) else GenericalFace.outline(0, f.exprFrom, f.exprTo, f.exprT, closure, f.exprTop, f.exprTopT)) ?: return
    val ro = (if (wsG != null) GenericalFace.outlineWeighted(1, wsG, closure) else GenericalFace.outline(1, f.exprFrom, f.exprTo, f.exprT, closure, f.exprTop, f.exprTopT)) ?: return
    // Engine21.1: the reopen settle — a hair taller (from the bottom, like the lid) and wider.
    fun popped(e: EyeShape) = if (f.eyePop == 0f) e else e.copy(sx = e.sx * (1f + 0.4f * f.eyePop), sy = e.sy * (1f + f.eyePop))
    FaceRig.shapeOutline(lo.x, lo.y, 0, popped(f.face.left))
    FaceRig.shapeOutline(ro.x, ro.y, 1, popped(f.face.right))
    // Engine21.2: no same-way lean of the two top lids (blends, a face + an accent).
    TiltGuard.generical(lo.x, lo.y, ro.x, ro.y)
    val gap = WhEyeSeat.genericalGapScale(shapeId) * (f.eyeGap / 0.94f).coerceIn(0.92f, 1.06f)
    val fit = WhEyeSeat.genericalFitScale(shapeId)
    fun seatGen(k: Float) = GenericalSeat.seat(
        shape = shape,
        he = he,
        bodyPath = bodyCentered,
        left = lo,
        right = ro,
        gap = gap,
        fit = fit,
        leadX = f.leadX * k,
        leadY = f.leadY * k,
        pitchDeg = pitch * k,
        yawDeg = yaw * k,
        rollDeg = roll,
    )
    val genBody = EyeSafeRegion.Body(projected)
    var (le, re) = if (bodyKind == "sphere" || calm || spinning) seatGen(1f)
        else EyeSafeRegion.fit(genBody, he * GEN_EDGE_MARGIN, ::seatGen) { listOfNotNull(it.first?.path, it.second?.path) }
    if (!calm && !spinning) {
        val clipMargin = he * GEN_CLIP_MARGIN
        val n = EyeSafeRegion.nudge(genBody, listOfNotNull(le?.path, re?.path), clipMargin)
        if (n != androidx.compose.ui.geometry.Offset.Zero) {
            fun mv(e: GenericalSeat.SeatedEye?) = e?.let { GenericalSeat.SeatedEye(Path().apply { addPath(it.path, n) }, it.gradStart + n, it.gradEnd + n) }
            le = mv(le); re = mv(re)
        }
        fun shX(e: GenericalSeat.SeatedEye?): GenericalSeat.SeatedEye? {
            if (e == null) return null
            val (kx, c) = EyeSafeRegion.shrinkX(genBody, e.path, clipMargin)
            if (kx >= 1f) return e
            fun scX(o: Offset) = Offset(c.x + (o.x - c.x) * kx, o.y)
            return GenericalSeat.SeatedEye(EyeSafeRegion.scalePathX(e.path, kx, c), scX(e.gradStart), scX(e.gradEnd))
        }
        le = shX(le); re = shX(re)
    }
    if (AvatarDebugMetrics.enabled) {
        val ps = listOfNotNull(le?.path, re?.path)
        val c = AvatarDebugMetrics.centroid(ps) - genBody.center
        AvatarDebugMetrics.rows += "${AvatarDebugMetrics.label},generical,${shape.id},${f.exprFrom},${f.exprTo},${f.exprT}," +
            "NaN,${EyeSafeRegion.inset(genBody, ps, he * 0.5f) / he},${c.x / he},${c.y / he}"
    }
    if (sink != null) {
        sink.eyes = listOfNotNull(le, re).map { MarkGeom.Eye(it.path, it.gradStart, it.gradEnd) }
        return
    }
    val drawEyes: DrawScope.() -> Unit = {
        le?.let { GenericalSeat.draw(this, it, eye, eyeFade, he, fit); with(ContrastGuard) { eyeOutline(it.path, he) } }
        re?.let { GenericalSeat.draw(this, it, eye, eyeFade, he, fit); with(ContrastGuard) { eyeOutline(it.path, he) } }
    }
    if (calm) {
        Soft3DMark.withBodyTransform(this, clip, sizePx, he, morph, false, drawEyes)
    } else {
        // Clip to the projected silhouette so an eye rolling over the edge wraps away.
        Soft3DMark.withBodyClip(this, clip, projected, sizePx, he, morph, false, drawEyes)
    }
}


/** Gain applied to head look so glancing around turns the 3D body ~15–20% more without breaking the silhouette. */
private const val LOOK_TURN_GAIN = 1.2f

/** Max visual (yaw, pitch) in degrees per shape. */
internal fun turnCapFor(shapeId: String, bodyKind: String): Pair<Float, Float> = when {
    // Heart's 3D projection lumps past ~14° and upper lobes narrow quickly; kept conservative.
    shapeId.contains("heart", ignoreCase = true) -> 13f to 10f
    // Sphere allows more turn (+3° yaw / +2° pitch) without silhouette distortion.
    bodyKind == "sphere" -> 25f to 17f
    // Non-sphere shapes get modest headroom (+2.5° yaw / +2° pitch) so glances read as turns.
    else -> 19.5f to 14f
}

internal fun softCap(v: Float, cap: Float): Float = cap * kotlin.math.tanh(v / cap)


/** Grok eye shapes that are already closed: blinks don't apply to them. */
private const val GROK_ACCENT_GAIN = 1.7f
private const val GROK_SLANT_GAIN = 2.6f
private val GROK_CLOSED = setOf("sleepy", "happy", "closed", "wink")

/**
 * Engine16.1: Grok faces stay mirror-symmetric. Size / lid / height are averaged across the
 * two eyes and only the mirrored part of the rotation is kept, so no accent makes one eye
 * crooked. [EyeShape.dx] is left alone (it carries gaze translation, not shape).
 */
internal fun FaceParams.grokSymmetric(): FaceParams {
    if (left == right && left.rot == 0f && left == EyeShape()) return this
    // Engine21: pills carry an accent only through size, lid and slant, so the subtle
    // FaceRig deltas (tuned for Generical's drawn lids) vanished on Grok. Amplify the
    // averaged deviation from rest so each accent reads, clamped to a sane pill range.
    val k = GROK_ACCENT_GAIN
    fun m(a: Float, b: Float, rest: Float = 1f) = (rest + ((a + b) / 2f - rest) * k)
    val r = (right.rot - left.rot) / 2f
    fun mk(e: EyeShape, rot: Float) = e.copy(
        sx = m(left.sx, right.sx).coerceIn(0.7f, 1.35f), sy = m(left.sy, right.sy).coerceIn(0.6f, 1.4f),
        dy = m(left.dy, right.dy, 0f),
        upper = m(left.upper, right.upper).coerceIn(0.35f, 1f), lower = m(left.lower, right.lower).coerceIn(0.35f, 1f),
        // Slant is the pill's only "brow": it gets a stronger gain so annoyed / smug / drowsy tilt visibly.
        slant = (m(left.slant, right.slant, 0f) * GROK_SLANT_GAIN / k).coerceIn(-0.75f, 0.75f), rot = rot * k,
    )
    return FaceParams(mk(left, -r), mk(right, r))
}


/**
 * Engine16.2: geometry of one drawn mark in the body-transform space (body silhouette as
 * projected, seated eye paths with their gradient axis, colours). Two of these blend into a
 * seamless shape / eye-style morph (see [drawMorph]).
 */
internal class MarkGeom {
    class Eye(val path: Path, val gradStart: Offset, val gradEnd: Offset)
    var body: Path = Path()
    var eyes: List<Eye> = emptyList()
    var clip: ClipSample? = null
    var sizePx = 0f
    var he = 1f
    var fit = 1f
    var isGrok = false
    var bodyColor = Color.Black
    var eyeColor = Color.White
    var eyeFade = Color.White
}

/** Sample the longest contour of [p] into [n] points, counter-clockwise, start at the topmost. */
internal fun resamplePath(p: Path, n: Int = 120): List<Offset> {
    // A turned body's projected hull can be several overlapping face contours; union them
    // into the true silhouette first, then take the contour enclosing the most area.
    val pm = android.graphics.PathMeasure(p.asAndroidPath(), true)
    var best = -1f
    var bestPts: List<Offset> = emptyList()
    val pos = FloatArray(2)
    do {
        val len = pm.length
        if (len > 0f) {
            val pts = List(n) { i -> pm.getPosTan(len * i / n, pos, null); Offset(pos[0], pos[1]) }
            var a2 = 0f
            for (i in pts.indices) { val a = pts[i]; val q = pts[(i + 1) % n]; a2 += a.x * q.y - q.x * a.y }
            if (kotlin.math.abs(a2) > best) { best = kotlin.math.abs(a2); bestPts = pts }
        }
    } while (pm.nextContour())
    if (bestPts.size < 3) return bestPts
    var area = 0f
    for (i in bestPts.indices) { val a = bestPts[i]; val b = bestPts[(i + 1) % n]; area += a.x * b.y - b.x * a.y }
    val pts = if (area < 0f) bestPts.reversed() else bestPts
    val top = pts.indices.minBy { pts[it].y }
    return List(n) { pts[(top + it) % n] }
}

/**
 * Engine16.2c: silhouette of a (possibly many-contour) body path as [n] radii at fixed
 * angles around [c], angle 0 = straight up, clockwise. A turned mesh hull is ~770 face
 * contours; the old union + PathMeasure took ~460 ms. This is one pass over sampled points
 * (max radius per angle bin), and two radial samplings blend without any alignment search.
 */
internal fun radialSilhouette(p: Path, n: Int = RADIAL_N, out: FloatArray = FloatArray(n)): FloatArray {
    // Engine17.2: the radius is interpolated exactly at each bin-centre angle along the
    // sampled segments (outermost wins), instead of the per-bin max drawn at the bin centre —
    // that quantised staircase is what made morph frames look low-res. Accurate enough to
    // sample at twice the step, so it costs no more than before.
    java.util.Arrays.fill(out, 0f)
    val pm = android.graphics.PathMeasure(p.asAndroidPath(), true)
    val pos = FloatArray(2)
    val twoPi = (2.0 * Math.PI).toFloat()
    val binW = twoPi / n
    do {
        val len = pm.length
        if (len <= 0f) continue
        val steps = (len / RADIAL_STEP).toInt().coerceIn(3, 400)
        pm.getPosTan(0f, pos, null)
        val fx = pos[0]; val fy = pos[1]
        var px = fx; var py = fy
        var pa = kotlin.math.atan2(px, -py).let { if (it < 0f) it + twoPi else it }
        var crossed = false
        for (i in 1..steps) {
            val qx: Float; val qy: Float
            if (i == steps) { qx = fx; qy = fy } else { pm.getPosTan(len * i / steps, pos, null); qx = pos[0]; qy = pos[1] }
            val qa = kotlin.math.atan2(qx, -qy).let { if (it < 0f) it + twoPi else it }
            var d = qa - pa
            if (d > Math.PI) d -= twoPi else if (d < -Math.PI) d += twoPi
            if (kotlin.math.abs(d) > 1e-7f) {
                // Bin centres (k + 0.5)·binW strictly inside (pa, pa + d].
                val lo = minOf(pa, pa + d); val hi = maxOf(pa, pa + d)
                var k = kotlin.math.ceil(lo / binW - 0.5f).toInt()
                while ((k + 0.5f) * binW <= hi) {
                    val c = (k + 0.5f) * binW
                    val f = ((c - pa) / d).coerceIn(0f, 1f)
                    val x = px + (qx - px) * f; val y = py + (qy - py) * f
                    val r = kotlin.math.sqrt(x * x + y * y)
                    val kk = ((k % n) + n) % n
                    if (r > out[kk]) out[kk] = r
                    crossed = true
                    k++
                }
            }
            px = qx; py = qy; pa = qa
        }
        if (!crossed) {
            // A contour too small to cross a bin centre: per-bin max of its samples.
            for (i in 0 until steps) {
                pm.getPosTan(len * i / steps, pos, null)
                var a = kotlin.math.atan2(pos[0], -pos[1]); if (a < 0f) a += twoPi
                val k = ((a / twoPi) * n).toInt() % n
                val r = kotlin.math.sqrt(pos[0] * pos[0] + pos[1] * pos[1])
                if (r > out[k]) out[k] = r
            }
        }
    } while (pm.nextContour())
    // Fill any empty bins from neighbours.
    for (pass in 0 until 3) for (k in 0 until n) if (out[k] == 0f) out[k] = maxOf(out[(k + n - 1) % n], out[(k + 1) % n])
    return out
}

internal const val RADIAL_N = 288
private const val RADIAL_STEP = 3f // engine17.2: exact interpolation allows twice the step

internal fun radialPath(a: FloatArray, b: FloatArray, u: Float, into: Path): Path {
    into.reset()
    val n = a.size
    val step = (2.0 * Math.PI).toFloat() / n
    fun r(k: Int) = a[k] + (b[k] - a[k]) * u
    return SmoothPath.closed(n, into, x = { k -> kotlin.math.sin((k + 0.5f) * step) * r(k) }, y = { k -> -kotlin.math.cos((k + 0.5f) * step) * r(k) })
}

/** Point-wise blend of two resampled contours, with the start index aligned for least travel. */
internal fun blendContours(a: List<Offset>, b: List<Offset>, u: Float): Path {
    val n = minOf(a.size, b.size)
    val out = Path()
    if (n < 3) return out
    var bestK = 0; var bestD = Float.MAX_VALUE
    for (k in -n / 8..n / 8) {
        var d = 0f
        var i = 0
        while (i < n) { val q = b[((i + k) % n + n) % n]; val dx = a[i].x - q.x; val dy = a[i].y - q.y; d += dx * dx + dy * dy; i += 4 }
        if (d < bestD) { bestD = d; bestK = k }
    }
    return SmoothPath.closed(n, out,
        x = { i -> val q = b[((i + bestK) % n + n) % n]; a[i].x + (q.x - a[i].x) * u },
        y = { i -> val q = b[((i + bestK) % n + n) % n]; a[i].y + (q.y - a[i].y) * u })
}

private fun lerpC(a: Color, b: Color, u: Float) = androidx.compose.ui.graphics.lerp(a, b, u)

/** Draw the blend of [from] → [to] at [u] (0…1). Generical/Grok eye looks cross-blend too. */
internal fun DrawScope.drawMorph(from: MarkGeom, fromPts: MorphPts, to: MarkGeom, u: Float, out: MarkGeom) {
    val clip = to.clip ?: return
    val toPts = MorphPts.of(to)
    val body = radialPath(fromPts.body, toPts.body, u, Path())
    val sizePx = from.sizePx + (to.sizePx - from.sizePx) * u
    val he = to.he
    val gW = (if (from.isGrok) 0f else 1f) * (1f - u) + (if (to.isGrok) 0f else 1f) * u
    val bodyC = lerpC(from.bodyColor, to.bodyColor, u)
    val top = lerpC(from.eyeColor, to.eyeColor, u)
    val fromBottom = if (from.isGrok) from.eyeColor else from.eyeFade
    val toBottom = if (to.isGrok) to.eyeColor else to.eyeFade
    val bottom = lerpC(fromBottom, toBottom, u)
    val eyes = if (fromPts.eyes.size == toPts.eyes.size) fromPts.eyes.indices.map { i ->
        val ea = from.eyes[i]; val eb = to.eyes[i]
        fun lo(a: Offset, b: Offset) = Offset(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u)
        MarkGeom.Eye(blendContours(fromPts.eyes[i], toPts.eyes[i], u), lo(ea.gradStart, eb.gradStart), lo(ea.gradEnd, eb.gradEnd))
    } else to.eyes
    val fit = from.fit + (to.fit - from.fit) * u
    Soft3DMark.withBodyTransform(this, clip, sizePx, he, 1f, false) {
        drawPath(body, bodyC)
        with(ContrastGuard) { bodyOutline(body, he) }
        clipPath(body) {
            for (e in eyes) {
                drawPath(e.path, androidx.compose.ui.graphics.Brush.linearGradient(listOf(top, bottom), e.gradStart, e.gradEnd))
                if (gW > 0.01f) drawPath(e.path, top.copy(alpha = top.alpha * gW),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f * fit * he / 250f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                with(ContrastGuard) { eyeOutline(e.path, he) }
            }
        }
    }
    out.body = body; out.eyes = eyes; out.clip = clip; out.sizePx = sizePx; out.he = he; out.fit = fit
    out.isGrok = gW < 0.5f; out.bodyColor = bodyC; out.eyeColor = top; out.eyeFade = bottom
}

/** Resampled contours of a [MarkGeom] (computed once for a frozen "from" snapshot). */
internal class MorphPts(val body: FloatArray, val eyes: List<List<Offset>>) {
    companion object {
        fun of(g: MarkGeom) = MorphPts(radialSilhouette(g.body), g.eyes.map { resamplePath(it.path, 96) }) // engine17.2: same density as static eyes
    }
}
