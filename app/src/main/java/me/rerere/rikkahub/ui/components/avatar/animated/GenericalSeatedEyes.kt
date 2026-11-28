package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generical eye geometry for smooth expression morphs.
 *
 * Every reference SVG eye (path or rounded rect, incl. its rotate transform) is
 * resampled to [N] points by arc length, wound clockwise and started at the point
 * straight above its centre, so any two expressions can be interpolated point by
 * point (plus a small best-fit cyclic shift per pair). Blinks morph toward the
 * reference "Eyes closed" bar instead of squashing the eye flat.
 */
object GenericalFace {
    const val N = 96

    class Outline(val x: FloatArray, val y: FloatArray) {
        val cx: Float get() = x.average().toFloat()
        val cy: Float get() = y.average().toFloat()
    }

    class Face(val left: Outline, val right: Outline)

    private val faces = HashMap<String, Face>()
    private val shifts = HashMap<String, Int>()

    /**
     * Every face is Julian's hand-made design from generical-eyes.json (engine13: his Figma
     * "Generical expressions" set; "confused" is his raised-eyebrow frame (his Sad/hurt/confused frame is unused), no longer
     * synthesised).
     */
    fun face(exprId: String): Face? {
        faces[exprId]?.let { return it }
        if (!AvatarPackData.isLoaded()) return null
        // "confused left" = the confused frame mirrored about the face centre.
        if (exprId == SIDE_EYE_LEFT) {
            val r = face(SIDE_EYE) ?: return null
            return Face(mirror500(r.right), mirror500(r.left)).also { faces[exprId] = it }
        }
        val key = exprId
        val expr = AvatarPackData.generical.expressions[key]
            ?: AvatarPackData.generical.expressions["Neutral"]
            ?: return null
        var l = sample(expr.left) ?: return null
        var r = sample(expr.right) ?: return null
        // Engine18: Angry and Sad toned down a touch — 10 % of the way back toward Neutral.
        TONED[exprId]?.let { k ->
            val n = face("Neutral")
            if (n != null) {
                l = towards(l, n.left, k, shiftFor(exprId, "Neutral", 0, l, n.left))
                r = towards(r, n.right, k, shiftFor(exprId, "Neutral", 1, r, n.right))
            }
        }
        // Engine14: no re-centring; every frame sits exactly where Julian drew it.
        return Face(l, r).also { faces[exprId] = it }
    }

    private val TONED = mapOf("Angry" to 0.10f, "Sad" to 0.10f)

    private fun towards(a: Outline, b: Outline, k: Float, sh: Int): Outline {
        val xs = FloatArray(N); val ys = FloatArray(N)
        for (i in 0 until N) {
            val j = ((i + sh) % N + N) % N
            xs[i] = a.x[i] + (b.x[j] - a.x[i]) * k; ys[i] = a.y[i] + (b.y[j] - a.y[i]) * k
        }
        return Outline(xs, ys)
    }

    const val SIDE_EYE = "confused"
    const val SIDE_EYE_LEFT = "confused left"

    /** Mirrored about the face centre x = 250 (point order kept clockwise). */
    private fun mirror500(o: Outline): Outline {
        val xs = FloatArray(N) { 500f - o.x[it] }
        val ys = o.y.copyOf()
        xs.reverse(); ys.reverse()
        return reStart(Outline(xs, ys))
    }

    /** Mirrored left-right about its own box centre (point order kept clockwise). */
    private fun flipH(o: Outline): Outline {
        val c = bboxCx(o)
        val xs = FloatArray(N) { 2f * c - o.x[it] }
        val ys = o.y.copyOf()
        xs.reverse(); ys.reverse()
        return reStart(Outline(xs, ys))
    }

    /** Move so the box is centred on [cx] with its bottom at [bottom]. */
    private fun place(o: Outline, cx: Float, bottom: Float) =
        translate(o, cx - bboxCx(o), bottom - o.y.max())

    /** Rotate point order to start straight above the centre (as [sample] does). */
    private fun reStart(o: Outline): Outline {
        val cx = o.cx; val cy = o.cy
        var best = 0; var bestD = Float.MAX_VALUE
        for (i in 0 until N) {
            var d = atan2(o.y[i] - cy, o.x[i] - cx) + (PI / 2).toFloat()
            while (d > PI) d -= (2 * PI).toFloat()
            while (d < -PI) d += (2 * PI).toFloat()
            if (abs(d) < bestD) { bestD = abs(d); best = i }
        }
        return Outline(FloatArray(N) { o.x[(it + best) % N] }, FloatArray(N) { o.y[(it + best) % N] })
    }

    private fun bboxCx(o: Outline) = (o.x.min() + o.x.max()) / 2f
    private fun bboxCy(o: Outline) = (o.y.min() + o.y.max()) / 2f

    private fun translate(o: Outline, dx: Float, dy: Float) =
        Outline(FloatArray(N) { o.x[it] + dx }, FloatArray(N) { o.y[it] + dy })

    private fun sample(eye: GenericalEye): Outline? {
        val path = when {
            eye.kind == "rect" && eye.x != null && eye.y != null && eye.width != null && eye.height != null -> {
                val rx = (eye.rx ?: 0f).coerceAtMost(min(eye.width, eye.height) / 2f)
                SquircleEyeGeometry.roundedRectPath(eye.x, eye.y, eye.width, eye.height, rx)
            }
            !eye.pathData.isNullOrBlank() -> AvatarPackData.parseSvgPath(eye.pathData)
            else -> return null
        }
        val pm = PathMeasure()
        pm.setPath(path, true)
        val len = pm.length
        if (!(len > 1f)) return null
        val xs = FloatArray(N)
        val ys = FloatArray(N)
        val ang = eye.rotateAngle?.let { (it * PI / 180.0).toFloat() }
        val pcx = eye.rotateCx ?: 0f
        val pcy = eye.rotateCy ?: 0f
        for (i in 0 until N) {
            val p = pm.getPosition(len * i / N)
            var x = p.x
            var y = p.y
            if (ang != null) {
                val dx = x - pcx
                val dy = y - pcy
                x = pcx + dx * cos(ang) - dy * sin(ang)
                y = pcy + dx * sin(ang) + dy * cos(ang)
            }
            xs[i] = x; ys[i] = y
        }
        // Clockwise on screen (positive shoelace area in y-down coordinates).
        var area = 0f
        for (i in 0 until N) {
            val j = (i + 1) % N
            area += xs[i] * ys[j] - xs[j] * ys[i]
        }
        if (area < 0f) {
            xs.reverse(); ys.reverse()
        }
        // Start at the point straight above the centre.
        val cx = xs.average().toFloat()
        val cy = ys.average().toFloat()
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in 0 until N) {
            val a = atan2(ys[i] - cy, xs[i] - cx)
            var d = a + (PI / 2).toFloat()
            while (d > PI) d -= (2 * PI).toFloat()
            while (d < -PI) d += (2 * PI).toFloat()
            if (abs(d) < bestD) {
                bestD = abs(d); best = i
            }
        }
        return Outline(FloatArray(N) { xs[(it + best) % N] }, FloatArray(N) { ys[(it + best) % N] })
    }

    /** Best cyclic shift aligning [b] to [a] (shape only, centres removed). */
    private fun shiftFor(keyA: String, keyB: String, side: Int, a: Outline, b: Outline): Int {
        val k = "$keyA|$keyB|$side"
        shifts[k]?.let { return it }
        val acx = a.cx; val acy = a.cy; val bcx = b.cx; val bcy = b.cy
        var best = 0
        var bestCost = Float.MAX_VALUE
        for (s in -N / 6..N / 6) {
            var c = 0f
            for (i in 0 until N) {
                val j = ((i + s) % N + N) % N
                val dx = (a.x[i] - acx) - (b.x[j] - bcx)
                val dy = (a.y[i] - acy) - (b.y[j] - bcy)
                c += dx * dx + dy * dy
            }
            if (c < bestCost) {
                bestCost = c; best = s
            }
        }
        shifts[k] = best
        return best
    }

    private fun lerpInto(outX: FloatArray, outY: FloatArray, a: Outline, b: Outline, shift: Int, t: Float) {
        for (i in 0 until N) {
            val j = ((i + shift) % N + N) % N
            outX[i] = a.x[i] + (b.x[j] - a.x[i]) * t
            outY[i] = a.y[i] + (b.y[j] - a.y[i]) * t
        }
    }

    /**
     * Engine19: outline for a weight vector over faces (Σ w = 1): every face is aligned to
     * the Neutral outline's indexing (best cyclic shift) and the points are averaged, so the
     * shape is continuous in every weight and any mix of faces morphs from where it is.
     */
    fun outlineWeighted(side: Int, weights: List<Pair<String, Float>>, closure: Float): Outline? {
        val refF = face("Neutral") ?: return null
        val ref = if (side == 0) refF.left else refF.right
        val xs = FloatArray(N); val ys = FloatArray(N)
        var sum = 0f
        for ((name, w) in weights) {
            if (w <= 0f) continue
            val f = face(name) ?: continue
            val o = if (side == 0) f.left else f.right
            val sh = if (name == "Neutral") 0 else shiftFor("Neutral", name, side, ref, o)
            for (i in 0 until N) {
                val j = ((i + sh) % N + N) % N
                xs[i] += o.x[j] * w; ys[i] += o.y[j] * w
            }
            sum += w
        }
        if (sum <= 1e-6f) return outline(side, "Neutral", "Neutral", 1f, closure)
        for (i in 0 until N) { xs[i] /= sum; ys[i] /= sum }
        applyClosure(xs, ys, "Neutral", ref, side, closure)
        return Outline(xs, ys)
    }

    private fun applyClosure(xs: FloatArray, ys: FloatArray, key: String, ref: Outline, side: Int, closure: Float) {
        val c = closure.coerceIn(0f, 1f)
        if (c <= 0.001f) return
        val closedFace = face("Eyes closed") ?: return
        val closed = if (side == 0) closedFace.left else closedFace.right
        val mx = xs.average().toFloat()
        val ymin = ys.min(); val ymax = ys.max()
        val my = ymin + (ymax - ymin) * 0.58f
        val width = min((xs.max() - xs.min()).coerceAtLeast(30f), closed.x.max() - closed.x.min())
        val txs = closed.x.copyOf(); val tys = closed.y.copyOf()
        SquircleEyeGeometry.resizeClosedBar(txs, tys, mx, my, width)
        val sa = shiftFor(key, "Eyes closed", side, ref, closed)
        for (i in 0 until N) {
            val j = ((i + sa) % N + N) % N
            xs[i] += (txs[j] - xs[i]) * c
            ys[i] += (tys[j] - ys[i]) * c
        }
    }

    /**
     * Morphed eye outline in the 500 reference viewBox (y down).
     * [closure] 0 = as designed, 1 = fully closed bar (blink).
     */
    fun outline(side: Int, from: String, to: String, t: Float, closure: Float, top: String? = null, topT: Float = 0f): Outline? {
        val fa = face(from) ?: return null
        val fb = face(to) ?: return null
        val a = if (side == 0) fa.left else fa.right
        val b = if (side == 0) fb.left else fb.right
        val xs = FloatArray(N)
        val ys = FloatArray(N)
        val tt = t.coerceIn(0f, 1f)
        // Key of the face whose point indexing [xs] uses (needed to align later layers).
        var key: String; var ref: Outline
        if (from == to || tt >= 1f) {
            System.arraycopy(b.x, 0, xs, 0, N); System.arraycopy(b.y, 0, ys, 0, N); key = to; ref = b
        } else if (tt <= 0f) {
            System.arraycopy(a.x, 0, xs, 0, N); System.arraycopy(a.y, 0, ys, 0, N); key = from; ref = a
        } else {
            lerpInto(xs, ys, a, b, shiftFor(from, to, side, a, b), tt); key = from; ref = a
        }
        // Engine17: interrupted morph — grow the newest face over the current blend.
        val tw = topT.coerceIn(0f, 1f)
        if (top != null && top != key && tw > 0f) {
            face(top)?.let { ft ->
                val c = if (side == 0) ft.left else ft.right
                val sh = shiftFor(key, top, side, ref, c)
                for (i in 0 until N) {
                    val j = ((i + sh) % N + N) % N
                    xs[i] += (c.x[j] - xs[i]) * tw; ys[i] += (c.y[j] - ys[i]) * tw
                }
            }
        }
        applyClosure(xs, ys, key, ref, side, closure)
        return Outline(xs, ys)
    }
}

/**
 * Seats Generical eyes ON the body surface with the same seat math as the Grok eyes
 * (desktop `sx` branches for mesh solids, quaternion sphere mapping for the sphere),
 * so they wrap, foreshorten and roll with the 3D body instead of sliding as a flat
 * sticker.
 */
object GenericalSeat {
    private val seatCache = HashMap<String, WhMesh.Seat>()

    class SeatedEye(val path: Path, val gradStart: Offset, val gradEnd: Offset)

    fun seatFor(shape: GrokShape, he: Float, bodyPath: Path): WhMesh.Seat =
        seatCache.getOrPut(shape.id) { WhMesh.buildSeat(shape, he, bodyPath) }

    /** Reference-viewBox outline → normalized face loop (y up, face centre 0,0). */
    private fun toLoop(o: GenericalFace.Outline, gap: Float, fit: Float, leadX: Float, leadY: Float): List<WhMesh.V2> {
        val cx = o.cx
        val cy = o.cy
        val ecx = 250f + (cx - 250f) * gap
        return List(GenericalFace.N) { i ->
            val x = ecx + (o.x[i] - cx) * fit
            val y = cy + (o.y[i] - cy) * fit
            WhMesh.V2((x - 250f) / 250f + leadX, -(y - 250f) / 250f - leadY)
        }
    }

    /**
     * Seat both eyes. Returns (left, right); null when an eye faces away.
     * Angles in degrees; output in He-centered px (y down).
     */
    fun seat(
        shape: GrokShape,
        he: Float,
        bodyPath: Path,
        left: GenericalFace.Outline,
        right: GenericalFace.Outline,
        gap: Float,
        fit: Float,
        leadX: Float,
        leadY: Float,
        pitchDeg: Float,
        yawDeg: Float,
        rollDeg: Float,
    ): Pair<SeatedEye?, SeatedEye?> {
        val loops = listOf(
            toLoop(left, gap, fit, leadX, leadY),
            toLoop(right, gap, fit, leadX, leadY),
        )
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
        val seated: List<List<WhMesh.V2>?>
        val probes: List<List<WhMesh.V2>?>
        if (shape.solid.kind == "sphere") {
            val r = WhSphereEyes.xn(WhSphereEyes.fk(pitch, yaw, roll))
            seated = loops.map { sphereMap(it, r, he) }
            probes = loops.map { sphereMap(probe(it), r, he, keepAll = true) }
        } else {
            val seat = seatFor(shape, he, bodyPath)
            val shiftY = WhEyeSeat.faceTune(shape.id).shiftY * 0.35f
            val ctx = WhMesh.xhHe(yaw, pitch, roll, he)
            val both = WhMesh.sx(seat, WhMesh.EyeLoops(loops, shiftY), ctx)
            seated = if (both.size == 2) both else loops.map { loop ->
                WhMesh.sx(seat, WhMesh.EyeLoops(listOf(loop), shiftY), ctx).firstOrNull()
            }
            probes = loops.map { loop ->
                WhMesh.sx(seat, WhMesh.EyeLoops(listOf(probe(loop)), shiftY), ctx).firstOrNull()
            }
        }
        fun build(i: Int): SeatedEye? {
            val poly = seated.getOrNull(i) ?: return null
            if (poly.size < 3) return null
            val path = SmoothPath.closed(poly.size, x = { poly[it].x }, y = { poly[it].y })
            var mx = 0f; var my = 0f
            for (p in poly) { mx += p.x; my += p.y }
            mx /= poly.size; my /= poly.size
            // Gradient runs along the eye's own "down" on the surface.
            var dx = 0f; var dy = 1f
            val pr = probes.getOrNull(i)
            if (pr != null && pr.size >= 2) {
                val vx = pr[0].x - pr[1].x
                val vy = pr[0].y - pr[1].y
                val l = hypot(vx, vy)
                if (l > 1e-4f) { dx = vx / l; dy = vy / l }
            }
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            for (p in poly) {
                val d = (p.x - mx) * dx + (p.y - my) * dy
                lo = min(lo, d); hi = max(hi, d)
            }
            if (hi - lo < 1e-3f) hi = lo + 1f
            // His gradients run from 4 above to 4 below the eye box (the outline's half width).
            val ext = 4f * fit * he / 250f; lo -= ext; hi += ext
            return SeatedEye(path, Offset(mx + dx * lo, my + dy * lo), Offset(mx + dx * hi, my + dy * hi))
        }
        return build(0) to build(1)
    }

    /** Small up-pointing probe at the loop centre: [centre, centre + up]. */
    private fun probe(loop: List<WhMesh.V2>): List<WhMesh.V2> {
        var cx = 0f; var cy = 0f
        for (p in loop) { cx += p.x; cy += p.y }
        cx /= loop.size; cy /= loop.size
        return listOf(WhMesh.V2(cx, cy), WhMesh.V2(cx, cy + 0.06f), WhMesh.V2(cx + 0.06f, cy))
    }

    /**
     * Decal on the unit sphere: arc-length (exponential) map around the face centre,
     * rotated with the same quaternion the Grok sphere eyes use.
     */
    private fun sphereMap(loop: List<WhMesh.V2>, r: FloatArray, he: Float, keepAll: Boolean = false): List<WhMesh.V2>? {
        val out = ArrayList<WhMesh.V2>(loop.size)
        for (p in loop) {
            // Engine14: orthographic lift, so at rest the decal projects back exactly onto
            // Julian's flat frame (1:1). Turning still rides the sphere.
            val d = hypot(p.x, p.y)
            val x: Float; val y: Float; val z: Float
            if (d < 0.999f) { x = p.x; y = p.y; z = sqrt(1f - d * d) }
            else { x = p.x / d * 0.999f; y = p.y / d * 0.999f; z = 0.0447f }
            val rx = r[0] * x + r[1] * y + r[2] * z
            val ry = r[3] * x + r[4] * y + r[5] * z
            val rz = r[6] * x + r[7] * y + r[8] * z
            if (keepAll || rz > 0f) out += WhMesh.V2(he * rx, -he * ry)
        }
        return if (out.size >= 3 || (keepAll && out.size >= 2)) out else null
    }

    /**
     * [eyeColor] fills the eye and its outline (white by default); the fade toward the
     * eye's own "down" on the surface is [fade] — the eye colour tinted by the body
     * surface ([MarkColors.genericalEyeFade]).
     */
    fun draw(scope: DrawScope, eye: SeatedEye, eyeColor: Color, fade: Color, he: Float, fit: Float = 1f) {
        val brush = Brush.linearGradient(
            colors = listOf(eyeColor, fade),
            start = eye.gradStart,
            end = eye.gradEnd,
        )
        scope.drawPath(eye.path, brush)
        // Julian's Figma: outline 8 on the 500 viewBox, scaled with the eye size.
        // Engine16: the outline is exactly the eye colour (same as the fill and the top of the
        // shade) for every colour and with the shade on or off — never contrast-shifted.
        scope.drawPath(
            eye.path,
            color = eyeColor,
            style = Stroke(width = 8f * fit * he / 250f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    @Suppress("unused")
    private fun len(x: Float, y: Float) = sqrt(x * x + y * y)
}
