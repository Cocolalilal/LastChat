package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Eye drawing — Grok slits are exact path subpaths from babygrok-geometry.
 * Generical eyes are exact SVG path/rect from generical-eyes.json.
 * NEVER drawOval / ellipse for Grok.
 */
object EyeStyle {

    /** Alias map → generical-eyes.json expression keys. */
    fun resolveGenericalExpressionId(id: String): String {
        val exprs = if (AvatarPackData.isLoaded()) AvatarPackData.generical.expressions else emptyMap()
        val raw = id.trim()
        val low = raw.lowercase()
        // Never use Grok brand "Looking to the right" — design: rest faces forward.
        if (low in setOf(
                "looking", "looking_right", "looking to the right", "lookright",
                "looking_right", "inspecting",
            ) || raw.equals("Looking to the right", ignoreCase = true)
        ) {
            return "Neutral"
        }
        if (exprs.containsKey(raw) && raw != "Looking to the right") return raw
        val mapped = when (low) {
            "neutral", "rest" -> "Neutral"
            "happy", "happysquint" -> "Happy"
            "surprised", "wideopen", "shocked" -> "Surprised"
            "closed", "eyes_closed", "eyes closed", "happysquint_closed" -> "Eyes closed"
            "sleeping", "sleepy", "softclosed", "soft closed" -> "Sleeping"
            "bored" -> "Bored"
            "angry", "angered" -> "Angry"
            "concentrated", "angered or concentrated", "focused" -> "angered or concentrated"
            "sad", "sadsquint" -> "Sad"
            "worried" -> "Worried"
            "confused" -> "confused"
            "pouty" -> "pouty"
            "hurt", "cute", "hurt or cute", "wink" -> "hurt or cute"
            "playful", "group3", "group 3" -> "Group 3"
            "curious" -> "Surprised"
            "annoyed" -> "Angry"
            else -> "Neutral"
        }
        return if (exprs.isEmpty() || exprs.containsKey(mapped)) mapped else "Neutral"
    }

    /**
     * Draw Grok eye slits from exact path subpaths (already in mark viewBox space).
     * Gaze = translate each eye; blink = Y-scale (lidOpen*blink) around eye center.
     * morphTurn = brief eye-orbit seating (done celebrate), not body spin.
     */
    fun drawGrokEyePaths(
        drawScope: DrawScope,
        leftEyePathData: String,
        rightEyePathData: String,
        originX: Float,
        originY: Float,
        gazeX: Float,
        gazeY: Float,
        lid: Float,
        eyeGap: Float,
        eyeScale: Float,
        morphTurn: Float,
        timeSec: Float,
        awake: Float,
        fill: Color,
    ) {
        if (lid < 0.07f) return
        val eyes = listOf(
            leftEyePathData to -1f,
            rightEyePathData to 1f,
        )
        for ((d, side) in eyes) {
            val bounds = AvatarPackData.pathBounds(d)
            var ecx = bounds.center.x
            var ecy = bounds.center.y
            // eyeGap: pull toward / push from midline
            ecx = originX + (ecx - originX) * eyeGap

            val driftX = (sin(timeSec * 0.42f + side) * 1.4f + sin(timeSec + side * 2f) * 0.5f) * awake
            val driftY = sin(timeSec * 0.58f + side) * 0.9f * awake

            var orbitSx = 1f
            if (abs(morphTurn) > 1e-4f) {
                val dx = ecx - originX
                val dy = ecy - originY
                val lam0 = atan2(dx, max(abs(dy), 1e-3f))
                val lam = lam0 + morphTurn
                val cosL = cos(lam)
                val cos0 = max(cos(lam0), 0.05f)
                orbitSx = (max(cosL, 0.05f) / cos0).coerceIn(0.35f, 1.15f)
                val r = hypot(dx, dy)
                ecx = originX + sin(lam) * r
                ecy = originY + cos(lam) * r * 0.35f + dy * 0.65f
            }

            val gazeForeshorten = 1f - 0.12f * min(1f, abs(gazeX) / 20f)
            val widen = if (lid < 0.45f) {
                lerp(1.2f, 1f, lid / 0.45f)
            } else {
                1f
            }
            val sx = orbitSx * gazeForeshorten * eyeScale * widen
            val sy = lid.coerceIn(0.02f, 1.25f)

            drawScope.withTransform({
                translate(left = ecx + gazeX + driftX, top = ecy + gazeY + driftY)
                scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero)
                translate(left = -bounds.center.x, top = -bounds.center.y)
            }) {
                // EXACT APK path subpath — never ellipse / drawOval
                drawPath(path = AvatarPackData.parseSvgPath(d), color = fill)
            }
        }
    }


    /**
     * Draw Grok eyes from 0.68 Qc contour rings (path holes), seated with
     * clip rotation foreshortening + gaze. NEVER ellipse/drawOval.
     */
    fun drawGrokQcEyes(
        drawScope: DrawScope,
        clip: ClipSample,
        gazeX: Float,
        gazeY: Float,
        lid: Float,
        eyeGap: Float,
        eyeScale: Float,
        timeSec: Float,
        awake: Float,
        fill: Color,
        canvasScale: Float,
        he: Float,
    ) {
        if (lid < 0.07f) return
        if (!AvatarPackData.isLoaded()) return
        val rings = AvatarPackData.qcRings
        if (rings.isEmpty()) return
        val pair = rings[0]
        if (pair.size < 2) return

        val pitch = Math.toRadians(clip.rotationDeg[0].toDouble()).toFloat()
        val yaw = Math.toRadians(clip.rotationDeg[1].toDouble()).toFloat()
        val roll = Math.toRadians(clip.rotationDeg[2].toDouble()).toFloat() * 0.35f

        val cx = drawScope.size.width / 2f
        val cy = drawScope.size.height / 2f
        val sizePx = drawScope.size.minDimension * Soft3DMark.FILL * clip.scale
        val pathScale = (sizePx * 0.5f) / he.coerceAtLeast(1f)
        val posScale = sizePx * 0.22f
        val ox = clip.position[0] * posScale + gazeX * 0.035f * sizePx
        val oy = -clip.position[1] * posScale + gazeY * 0.03f * sizePx
        val foreshortenX = (1f - 0.18f * abs(sin(yaw))).coerceIn(0.78f, 1f)
        val foreshortenY = (1f - 0.14f * abs(sin(pitch))).coerceIn(0.82f, 1f)
        val squashX = clip.squash[0].coerceIn(0.55f, 1.35f) * foreshortenX
        val squashY = clip.squash[1].coerceIn(0.55f, 1.35f) * foreshortenY

        drawScope.withTransform({
            translate(left = cx + ox, top = cy + oy)
            rotate(degrees = Math.toDegrees(roll.toDouble()).toFloat(), pivot = Offset.Zero)
            scale(scaleX = pathScale * squashX, scaleY = pathScale * squashY, pivot = Offset.Zero)
            translate(left = -he, top = -he)
        }) {
            for (side in 0..1) {
                val pts = pair[side]
                if (pts.isEmpty()) continue
                var ecx = 0f; var ecy = 0f
                for (p in pts) { ecx += p[0]; ecy += p[1] }
                ecx /= pts.size; ecy /= pts.size
                // eyeGap toward midline
                ecx = he + (ecx - he) * eyeGap

                val sideSign = if (side == 0) -1f else 1f
                val driftX = (sin(timeSec * 0.42f + sideSign) * 1.4f + sin(timeSec + sideSign * 2f) * 0.5f) * awake
                val driftY = sin(timeSec * 0.58f + sideSign) * 0.9f * awake

                // Sphere-seat foreshorten from yaw
                val seatX = (ecx - he) / he
                val seatY = (he - ecy) / he
                val z = max(0.05f, kotlin.math.sqrt(max(0f, 1f - seatX * seatX - seatY * seatY)))
                // simple yaw rotation on seating
                val cyaw = cos(yaw); val syaw = sin(yaw)
                val rx = seatX * cyaw + z * syaw
                val rz = -seatX * syaw + z * cyaw
                val vis = (rz > 0.02f)
                if (!vis) continue
                val sxF = (rz / max(z, 0.05f)).coerceIn(0.35f, 1.15f)
                val widen = if (lid < 0.45f) lerp(1.2f, 1f, lid / 0.45f) else 1f
                val sx = sxF * eyeScale * widen
                val sy = lid.coerceIn(0.02f, 1.25f)

                val path = Path().apply {
                    val p0 = pts[0]
                    moveTo(p0[0], p0[1])
                    for (i in 1 until pts.size) lineTo(pts[i][0], pts[i][1])
                    close()
                }
                val drawCx = he + rx * he
                val drawCy = he - seatY * he
                withTransform({
                    translate(left = drawCx + gazeX * 0.35f + driftX, top = drawCy + gazeY * 0.35f + driftY)
                    scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero)
                    translate(left = -ecx, top = -ecy)
                }) {
                    drawPath(path = path, color = fill)
                }
            }
        }
    }


    /**
     * Build Grok Qc eye paths in He-centered mark space for evenodd cutouts.
     * Eyes are contour rings (never ellipses). Lid = Y-scale around eye center;
     * gaze nudges position; eyeGap pulls toward midline.
     */
    fun buildGrokQcEyePathsHeCentered(
        he: Float,
        gazeX: Float,
        gazeY: Float,
        lid: Float,
        eyeGap: Float,
        eyeScale: Float,
        timeSec: Float,
        awake: Float,
        yawDeg: Float,
        pitchDeg: Float = 0f,
        rollDeg: Float = 0f,
    ): Pair<Path, Path> {
        val empty = Path() to Path()
        if (!AvatarPackData.isLoaded()) return empty
        val rings = AvatarPackData.qcRings
        if (rings.isEmpty() || rings[0].size < 2) return empty
        val pair = rings[0]
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat() * 0.35f
        val lidSy = lid.coerceIn(0.02f, 1.25f)
        val widen = if (lid < 0.45f) lerp(1.2f, 1f, lid / 0.45f) else 1f
        val cyaw = cos(yaw); val syaw = sin(yaw)
        val cpitch = cos(pitch); val spitch = sin(pitch)
        val croll = cos(roll); val sroll = sin(roll)

        fun eyePath(side: Int): Path {
            val pts = pair[side]
            if (pts.isEmpty()) return Path()
            var ecx = 0f; var ecy = 0f
            for (pt in pts) { ecx += pt[0]; ecy += pt[1] }
            ecx /= pts.size; ecy /= pts.size
            ecx = he + (ecx - he) * eyeGap

            val sideSign = if (side == 0) -1f else 1f
            val driftX = (sin(timeSec * 0.42f + sideSign) * 0.55f) * awake
            val driftY = sin(timeSec * 0.58f + sideSign) * 0.4f * awake

            // Jqe-style sphere seat: eyes on unit sphere, pose, project (body stays put)
            val seatX = (ecx - he) / he
            val seatY = (he - ecy) / he
            val z0 = max(0.05f, sqrt(max(0f, 1f - seatX * seatX - seatY * seatY)))
            val x1 = seatX * cyaw + z0 * syaw
            val z1 = -seatX * syaw + z0 * cyaw
            val y1 = seatY * cpitch - z1 * spitch
            val z2 = seatY * spitch + z1 * cpitch
            val x2 = x1 * croll - y1 * sroll
            val y2 = x1 * sroll + y1 * croll
            if (z2 <= 0.02f) return Path()
            // Pills foreshorten / curve into arcs at extremes
            val sxF = (z2 / max(z0, 0.05f)).coerceIn(0.28f, 1.15f)
            val syF = (0.55f + 0.45f * z2).coerceIn(0.35f, 1.1f)
            val sx = sxF * eyeScale * widen
            val sy = lidSy * syF
            val shear = (syaw * 0.35f + spitch * 0.15f).coerceIn(-0.45f, 0.45f)

            val drawCx = he + x2 * he
            val drawCy = he - y2 * he
            return Path().apply {
                fun mapXY(x: Float, y: Float): Pair<Float, Float> {
                    val lx = (x - ecx) * sx
                    val ly = (y - ecy) * sy
                    val wx = lx + ly * shear
                    return (drawCx + gazeX * 0.35f + driftX + wx) - he to
                        (drawCy + gazeY * 0.35f + driftY + ly) - he
                }
                val p0 = pts[0]
                val (mx0, my0) = mapXY(p0[0], p0[1])
                moveTo(mx0, my0)
                for (i in 1 until pts.size) {
                    val (mx, my) = mapXY(pts[i][0], pts[i][1])
                    lineTo(mx, my)
                }
                close()
            }
        }
        return eyePath(0) to eyePath(1)
    }

    fun drawGenericalEyes(
        drawScope: DrawScope,
        expressionId: String,
        gazeX: Float,
        gazeY: Float,
        lid: Float,
        bodyTintOverride: Color? = null,
        yawDeg: Float = 0f,
        pitchDeg: Float = 0f,
        /**
         * Stronger gradient only. Outline ~6 (design review: a little thicker than 5;
         * never ≥7 — he rejected thick outlines before).
         */
        stronger: Boolean = false,
        /** Per-shape Hw fit so eyes seat inside skinny C_ silhouettes. */
        fitScale: Float = 1f,
        /** <1 pulls eyes toward midline (design review: too far apart). */
        gapScale: Float = 0.82f,
        rollDeg: Float = 0f,
    ) {
        if (!AvatarPackData.isLoaded()) return
        val exprId = resolveGenericalExpressionId(expressionId)
        val expr = AvatarPackData.generical.expressions[exprId]
            ?: AvatarPackData.generical.expressions["Neutral"]
            ?: return

        val softLidExpr = exprId in setOf(
            "Sleeping", "Eyes closed", "Happy", "Sad", "hurt or cute", "Bored",
        )
        val useLid = if (softLidExpr) {
            max(lid, 0.85f)
        } else {
            lid.coerceIn(0.05f, 1.2f)
        }

        // Foreshorten + ride the body euler (eyes move WITH the solid).
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
        val faceZ = (cos(yaw) * cos(pitch)).coerceIn(0.28f, 1f)
        val shear = (sin(yaw) * 0.28f).coerceIn(-0.4f, 0.4f)
        // Body-follow shift in 500 viewBox units — matches soft-3D remesh direction.
        val bodyShiftX = sin(yaw) * 48f + sin(roll) * 10f
        val bodyShiftY = -sin(pitch) * 42f + (1f - cos(yaw)) * 6f
        val gap = gapScale.coerceIn(0.55f, 1.05f)

        drawGenericalEye(
            drawScope, expr.left,
            gazeX * 0.65f + bodyShiftX, gazeY * 0.65f + bodyShiftY, useLid,
            bodyTintOverride, faceZ, shear, stronger, -1f, fitScale, gap,
        )
        drawGenericalEye(
            drawScope, expr.right,
            gazeX * 0.65f + bodyShiftX, gazeY * 0.65f + bodyShiftY, useLid,
            bodyTintOverride, faceZ, shear, stronger, 1f, fitScale, gap,
        )
    }

    private fun drawGenericalEye(
        drawScope: DrawScope,
        eye: GenericalEye,
        gazeX: Float,
        gazeY: Float,
        lid: Float,
        tintOverride: Color?,
        faceZ: Float = 1f,
        shear: Float = 0f,
        stronger: Boolean = false,
        side: Float = 0f,
        fitScale: Float = 1f,
        gapScale: Float = 0.82f,
    ) {
        val (rawCx, rawCy) = eyeCenter(eye)
        // Pull toward viewBox midline so interocular spacing fits each shape.
        val mid = 250f
        val ecx = mid + (rawCx - mid) * gapScale.coerceIn(0.55f, 1.05f)
        val ecy = rawCy
        val gradFrom = if (stronger) Color.White else eye.gradientFrom
        val gradToBase = tintOverride ?: eye.gradientTo
        val gradTo = if (stronger) MarkColors.shade(gradToBase, 0.82f) else gradToBase
        val brush = Brush.linearGradient(
            colors = listOf(gradFrom, gradTo),
            start = Offset(eye.gradX1, eye.gradY1),
            end = Offset(eye.gradX2, eye.gradY2),
        )
        // Neutral.svg strokeWidth=5 → bump toward ~6 (design review, polish3). Cap <7.
        val strokeW = (if (eye.strokeWidth > 0f) eye.strokeWidth * 1.18f else 5.9f)
            .coerceIn(5.0f, 6.25f)
        val path = buildEyePath(eye) ?: return
        val fit = fitScale.coerceIn(0.5f, 1.05f)
        val sx = faceZ.coerceIn(0.35f, 1.1f) * fit
        val sy = (if (lid < 1f) max(lid, 0.18f) else 1f) * (0.6f + 0.4f * faceZ) * fit

        drawScope.withTransform({
            translate(left = ecx + gazeX + side * shear * 8f, top = ecy + gazeY)
            scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero)
            translate(left = -rawCx, top = -rawCy)
            val angle = eye.rotateAngle
            if (angle != null && eye.rotateCx != null && eye.rotateCy != null) {
                rotate(degrees = angle, pivot = Offset(eye.rotateCx, eye.rotateCy))
            }
        }) {
            drawPath(path = path, brush = brush)
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }

    private fun eyeCenter(eye: GenericalEye): Pair<Float, Float> {
        if (eye.x != null && eye.y != null && eye.width != null && eye.height != null) {
            return (eye.x + eye.width / 2f) to (eye.y + eye.height / 2f)
        }
        val d = eye.pathData ?: return 250f to 250f
        val b = AvatarPackData.pathBounds(d)
        return b.center.x to b.center.y
    }

    private fun buildEyePath(eye: GenericalEye): Path? {
        return when {
            eye.kind == "path" && !eye.pathData.isNullOrBlank() ->
                AvatarPackData.parseSvgPath(eye.pathData)
            eye.kind == "rect" &&
                eye.x != null && eye.y != null &&
                eye.width != null && eye.height != null -> {
                val rx = (eye.rx ?: 0f).coerceAtMost(min(eye.width, eye.height) / 2f)
                Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = eye.x,
                            top = eye.y,
                            right = eye.x + eye.width,
                            bottom = eye.y + eye.height,
                            cornerRadius = CornerRadius(rx, rx),
                        )
                    )
                }
            }
            !eye.pathData.isNullOrBlank() -> AvatarPackData.parseSvgPath(eye.pathData)
            else -> null
        }
    }


    /**
     * Generical eye outlines in eye-local units for WhSphereEyes.SS seating.
     * 500 viewBox → local via /250 so half-eye ~ matches desktop Iu scale.
     */
    fun genericalLocalOutlines(
        expressionId: String,
        fitScale: Float = 1f,
    ): Pair<List<FloatArray>, List<FloatArray>> {
        if (!AvatarPackData.isLoaded()) return emptyList<FloatArray>() to emptyList()
        val exprId = resolveGenericalExpressionId(expressionId)
        val expr = AvatarPackData.generical.expressions[exprId]
            ?: AvatarPackData.generical.expressions["Neutral"]
            ?: return emptyList<FloatArray>() to emptyList()
        val fit = fitScale.coerceIn(0.55f, 1.1f)
        fun localOf(eye: GenericalEye): List<FloatArray> {
            val (ecx, ecy) = eyeCenter(eye)
            val pts: List<FloatArray> = when {
                eye.kind == "rect" && eye.width != null && eye.height != null -> {
                    val hw = eye.width / 2f
                    val hh = eye.height / 2f
                    val rx = (eye.rx ?: 0f).coerceAtMost(min(hw, hh))
                    // Rounded-rect outline samples
                    val out = ArrayList<FloatArray>()
                    val n = 10
                    // top edge
                    for (i in 0..n) out += floatArrayOf(ecx - hw + rx + (2f * (hw - rx)) * i / n, ecy - hh)
                    // right
                    for (i in 1..n) out += floatArrayOf(ecx + hw, ecy - hh + rx + (2f * (hh - rx)) * i / n)
                    // bottom
                    for (i in 1..n) out += floatArrayOf(ecx + hw - rx - (2f * (hw - rx)) * i / n, ecy + hh)
                    // left
                    for (i in 1 until n) out += floatArrayOf(ecx - hw, ecy + hh - rx - (2f * (hh - rx)) * i / n)
                    out
                }
                else -> {
                    // Path: use bounds ellipse as seating proxy (SS cares about silhouette on sphere)
                    val d = eye.pathData ?: return emptyList()
                    val b = AvatarPackData.pathBounds(d)
                    val hw = b.width / 2f
                    val hh = b.height / 2f
                    val cx = b.center.x
                    val cy = b.center.y
                    (0 until 36).map { i ->
                        val a = (2f * PI.toFloat() * i / 36f)
                        floatArrayOf(cx + hw * cos(a), cy + hh * sin(a))
                    }
                }
            }
            return pts.map { pt ->
                floatArrayOf(
                    (pt[0] - ecx) / 250f * fit,
                    -((pt[1] - ecy) / 250f) * fit,
                )
            }
        }
        return localOf(expr.left) to localOf(expr.right)
    }

    fun strokeGenericalSeated(
        drawScope: DrawScope,
        left: Path,
        right: Path,
        tint: Color,
    ) {
        val strokeW = 5.9f // ~6 in 500 viewBox (design review, polish3; <7)
        val he = AvatarPackData.He
        val w = strokeW * (he / 250f)
        val fill = Color(
            red = (tint.red * 0.35f + 0.65f).coerceIn(0f, 1f),
            green = (tint.green * 0.45f + 0.55f).coerceIn(0f, 1f),
            blue = (tint.blue * 0.55f + 0.45f).coerceIn(0f, 1f),
            alpha = 0.95f,
        )
        for (path in listOf(left, right)) {
            if (path.isEmpty) continue
            drawScope.drawPath(path, fill)
            drawScope.drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = w.coerceIn(1.5f, 3.2f), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }


    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    // --- Legacy helpers kept for unit tests (rect param lerp) ---

    data class EyeParams(
        val cx: Float,
        val cy: Float,
        val width: Float,
        val height: Float,
        val rx: Float,
        val rotation: Float = 0f,
        val scaleY: Float = 1f,
    )

    fun lerp(a: EyeParams, b: EyeParams, t: Float): EyeParams {
        val u = t.coerceIn(0f, 1f)
        fun mix(x: Float, y: Float) = x + (y - x) * u
        return EyeParams(
            cx = mix(a.cx, b.cx),
            cy = mix(a.cy, b.cy),
            width = mix(a.width, b.width),
            height = mix(a.height, b.height),
            rx = mix(a.rx, b.rx),
            rotation = mix(a.rotation, b.rotation),
            scaleY = mix(a.scaleY, b.scaleY),
        )
    }

    /** Neutral left eye center in 500 viewBox (from the reference Neutral.svg rect). */
    fun neutralLeftEyeUnit(): EyeParams = EyeParams(
        cx = 167f, cy = 249.5f, width = 113f, height = 148f, rx = 40.5f,
    )
}

// Re-export for tests that imported EyeParams at package level
typealias EyeParams = EyeStyle.EyeParams
