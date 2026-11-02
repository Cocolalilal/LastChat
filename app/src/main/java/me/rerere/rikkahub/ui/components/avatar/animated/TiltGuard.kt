package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Engine21.2 — Julian's rule: two eyes leaning the same way (// or \\) is never good. Mirrored
 * tilts (/\ or \/) are fine, and so is one tilted eye next to an upright one.
 *
 * The authored rest faces obey that by construction (ParallelTiltTest). What remains are the
 * in-between frames: a blend between two differently tilted faces, a face plus an accent, and
 * the perspective lean of a diagonal Grok gaze. This guard measures each eye's tilt (+ = "/")
 * and removes only the part both eyes share (the "common lean"), continuously — a face with no
 * common lean is untouched, mirrored or one-eye tilts pass through, and nothing ever pops.
 *  - Generical: the common lean of the two top lids is sheared out of the lids only (top moves,
 *    the bottom edge stays level and in place).
 *  - Grok: each pill is turned about its own centre by the common lean, measured relative to the
 *    head roll (a tilted head tilts both eyes, which is fine).
 */
object TiltGuard {
    /** Common lean below this is kept (it is invisible), so the guard is quiet on clean faces. */
    const val DEAD = 1.5f
    private const val SAMPLES = 48

    /** The lean both tilts share (0 for mirrored / one-eye tilts), minus the dead zone. Continuous. */
    fun common(l: Float, r: Float): Float {
        if (l == 0f || r == 0f || sign(l) != sign(r)) return 0f
        val m = min(abs(l), abs(r)) - DEAD
        return if (m > 0f) sign(l) * m else 0f
    }

    private fun deg(rad: Double) = Math.toDegrees(rad).toFloat()

    /** Tilt (deg, + = "/", y down) of the top edge across the middle 60 % of the eye. */
    fun topTilt(x: FloatArray, y: FloatArray): Float {
        val n = x.size
        if (n < 4) return 0f
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        for (v in x) { if (v < minX) minX = v; if (v > maxX) maxX = v }
        val w = maxX - minX
        if (w < 1e-3f) return 0f
        val bins = 10
        val by = FloatArray(bins) { Float.MAX_VALUE }
        val x0 = minX + 0.2f * w; val bw = 0.6f * w / bins
        for (i in 0 until n) {
            if (x[i] < x0) continue
            val b = ((x[i] - x0) / bw).toInt()
            if (b !in 0 until bins) continue
            if (y[i] < by[b]) by[b] = y[i]
        }
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0; var k = 0
        for (b in 0 until bins) if (by[b] != Float.MAX_VALUE) {
            val cx = x0 + (b + 0.5f) * bw
            sx += cx; sy += by[b]; sxx += cx * cx; sxy += cx * by[b]; k++
        }
        if (k < 3) return 0f
        val den = k * sxx - sx * sx
        if (abs(den) < 1e-9) return 0f
        val slope = (k * sxy - sx * sy) / den          // dy/dx, y down: < 0 when the right end is higher
        return -deg(atan(slope))
    }

    /** Shears the top of one eye by [deg] (top fully, bottom not at all); bottoms stay level. */
    private fun shearTop(x: FloatArray, y: FloatArray, deg: Float) {
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var cx = 0f
        for (i in x.indices) { if (y[i] < minY) minY = y[i]; if (y[i] > maxY) maxY = y[i]; cx += x[i] }
        cx /= x.size
        val h = maxY - minY
        if (h < 1e-3f) return
        val t = tan(Math.toRadians(deg.toDouble())).toFloat()
        for (i in x.indices) {
            val wTop = ((maxY - y[i]) / h).coerceIn(0f, 1f)
            y[i] += t * (x[i] - cx) * wTop
        }
    }

    /** Generical: removes the common top-lid lean in place (500-viewBox outlines, after shaping). */
    fun generical(lx: FloatArray, ly: FloatArray, rx: FloatArray, ry: FloatArray) {
        repeat(2) {
            val m = common(topTilt(lx, ly), topTilt(rx, ry))
            if (m == 0f) return
            shearTop(lx, ly, m); shearTop(rx, ry, m)
        }
    }

    /**
     * Tilt (deg, + = "/") of an eye's long axis: an upright pill leaning "/" and a level bar
     * whose right end is higher both read positive. Faded out for roundish shapes, whose
     * "axis" is not something anyone can see.
     */
    fun axisTilt(x: FloatArray, y: FloatArray, n: Int = x.size): Float = axis(x, y, n).first

    /** (tilt, upright?) */
    private fun axis(x: FloatArray, y: FloatArray, n: Int): Pair<Float, Boolean> {
        if (n < 3) return 0f to true
        var mx = 0.0; var my = 0.0
        for (i in 0 until n) { mx += x[i]; my += y[i] }
        mx /= n; my /= n
        var a = 0.0; var b = 0.0; var c = 0.0
        for (i in 0 until n) { val dx = x[i] - mx; val dy = y[i] - my; a += dx * dx; b += dx * dy; c += dy * dy }
        val tr = a + c; val disc = sqrt(((a - c) / 2) * ((a - c) / 2) + b * b)
        val l1 = tr / 2 + disc; val l2 = tr / 2 - disc
        if (l1 <= 1e-9) return 0f to true
        val conf = if (l2 <= 1e-9) 1f else (((l1 / l2) - 1.5) / 1.5).toFloat().coerceIn(0f, 1f)
        if (conf == 0f) return 0f to true
        // Major-axis direction (b, l1 - a); angle in [0, 180) with y down.
        var d = if (abs(b) < 1e-12 && abs(l1 - a) < 1e-12) 0f else deg(atan2(l1 - a, b))
        if (d < 0f) d += 180f
        if (d >= 180f) d -= 180f
        return when {
            d <= 45f -> -d * conf to false              // level, right end lower = "\" = −
            d <= 135f -> (d - 90f) * conf to true        // upright, top leaning right = "/" = +
            else -> (180f - d) * conf to false           // level, right end higher = "/" = +
        }
    }

    private val lxs = FloatArray(SAMPLES); private val lys = FloatArray(SAMPLES)
    private val rxs = FloatArray(SAMPLES); private val rys = FloatArray(SAMPLES)

    private fun sample(p: Path, xs: FloatArray, ys: FloatArray, rollDeg: Float): Int {
        val pm = android.graphics.PathMeasure(p.asAndroidPath(), true)
        val len = pm.length
        if (len <= 0f) return 0
        val pos = FloatArray(2)
        for (i in 0 until SAMPLES) { pm.getPosTan(len * i / SAMPLES, pos, null); xs[i] = pos[0]; ys[i] = pos[1] }
        if (rollDeg != 0f) {
            // Measured relative to the head's roll (projectPair rolls the face by −roll on screen).
            val r = Math.toRadians(rollDeg.toDouble()); val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
            for (i in 0 until SAMPLES) { val x = xs[i]; val y = ys[i]; xs[i] = x * cs - y * sn; ys[i] = x * sn + y * cs }
        }
        return SAMPLES
    }

    /** Grok tilts of two seated eye paths, relative to [rollDeg]. */
    fun grokTilts(left: Path, right: Path, rollDeg: Float = 0f): Pair<Float, Float> {
        val nl = sample(left, lxs, lys, rollDeg); val nr = sample(right, rxs, rys, rollDeg)
        return axisTilt(lxs, lys, nl) to axisTilt(rxs, rys, nr)
    }

    /**
     * Grok: turns both pills about their own centres so they no longer share a lean (roll kept).
     * Returns the inputs unchanged when there is nothing to remove.
     */
    fun grok(left: Path, right: Path, rollDeg: Float = 0f, strength: Float = 1f): Pair<Path, Path> {
        if (left.isEmpty || right.isEmpty) return left to right
        val nl = sample(left, lxs, lys, rollDeg); val nr = sample(right, rxs, rys, rollDeg)
        if (nl == 0 || nr == 0) return left to right
        val (tl, ul) = axis(lxs, lys, nl); val (tr, ur) = axis(rxs, rys, nr)
        val m = common(tl, tr) * strength
        if (m == 0f) return left to right
        fun turn(p: Path, upright: Boolean): Path {
            val b = p.getBounds()
            // android.graphics.Matrix: positive degrees turn clockwise on screen (y down).
            val by = if (upright) -m else m
            return Path().apply {
                addPath(p)
                asAndroidPath().transform(android.graphics.Matrix().apply { setRotate(by, b.center.x, b.center.y) })
            }
        }
        return turn(left, ul) to turn(right, ur)
    }
}
