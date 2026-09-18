package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.BlobShape
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radial body profiles `r(θ)`, all sampled at the same angle count so morphing
 * between any two shapes is a linear interpolation of radii (no path-morph lib).
 * Every profile is peak-normalized to 1 so shapes fit the same draw box.
 *
 * Pure Kotlin: consumed by the Compose renderer, the android.graphics bitmap
 * renderer, and the head-less AWT contact-sheet renderer alike.
 */
internal const val BLOB_PROFILE_SAMPLES = 72

/** Draw inside this fraction of the box so organic shapes never clip the edge. */
internal const val BLOB_DRAW_FIT = 0.92f

/** One cubic segment of a smoothed outline, in screen units. */
internal data class CubicSeg(
    val c1x: Float, val c1y: Float,
    val c2x: Float, val c2y: Float,
    val x: Float, val y: Float,
)

/** A closed outline: a start point plus cubic segments back to it. */
internal data class BodyOutline(
    val startX: Float,
    val startY: Float,
    val segs: List<CubicSeg>,
)

internal object BlobShapes {
    private val cache = HashMap<BlobShape, FloatArray>()

    fun radii(shape: BlobShape): FloatArray = cache.getOrPut(shape) {
        when (shape) {
            BlobShape.Circle -> FloatArray(BLOB_PROFILE_SAMPLES) { 1f }
            BlobShape.Squircle -> normalizePeak(superellipse(4.2f))
            BlobShape.RoundSquare -> normalizePeak(superellipse(8f))
            BlobShape.Capsule -> normalizePeak(profileFromPolygon(hullOfCircles(-0.42f, 0f, 0.62f, 0.42f, 0f, 0.62f)))
            BlobShape.Pebble -> pebble()
            BlobShape.Hexagon -> normalizePeak(profileFromPolygon(regularPolygon(6, 1.04f, 0.26f, 0f)))
            BlobShape.Triangle -> normalizePeak(profileFromPolygon(regularPolygon(3, 1.12f, 0.34f, -90f)))
            BlobShape.Cloud -> cloud()
            BlobShape.Droplet -> normalizePeak(profileFromPolygon(hullOfCircles(0f, 0.28f, 0.66f, 0f, -0.96f, 0.05f)))
        }
    }

    fun radiusAtAngle(radii: FloatArray, angle: Float): Float {
        val n = radii.size
        if (n == 0) return 1f
        val turns = ((angle / TAU) % 1f + 1f) % 1f
        val x = turns * n
        val i = x.toInt() % n
        val j = (i + 1) % n
        return lerp(radii[i], radii[j], x - x.toInt())
    }

    /** Smoothed closed outline (centripetal-ish Catmull-Rom → cubics). */
    fun outline(
        radii: FloatArray,
        cx: Float,
        cy: Float,
        scale: Float,
        squashX: Float = 1f,
        squashY: Float = 1f,
    ): BodyOutline {
        val n = BLOB_PROFILE_SAMPLES
        val px = FloatArray(n)
        val py = FloatArray(n)
        for (i in 0 until n) {
            val theta = i * TAU / n
            val r = radii[i] * scale
            px[i] = cx + r * cos(theta) * squashX
            py[i] = cy + r * sin(theta) * squashY
        }
        val tension = 1f / 6f
        val segs = ArrayList<CubicSeg>(n)
        for (i in 0 until n) {
            val p0x = px[(i - 1 + n) % n]; val p0y = py[(i - 1 + n) % n]
            val p1x = px[i]; val p1y = py[i]
            val p2x = px[(i + 1) % n]; val p2y = py[(i + 1) % n]
            val p3x = px[(i + 2) % n]; val p3y = py[(i + 2) % n]
            segs.add(
                CubicSeg(
                    c1x = p1x + (p2x - p0x) * tension,
                    c1y = p1y + (p2y - p0y) * tension,
                    c2x = p2x - (p3x - p1x) * tension,
                    c2y = p2y - (p3y - p1y) * tension,
                    x = p2x, y = p2y,
                )
            )
        }
        return BodyOutline(px[0], py[0], segs)
    }

    private fun superellipse(n: Float): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        val expn = 2f / n
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            val x = signedPow(cos(theta), expn)
            val y = signedPow(sin(theta), expn)
            out[i] = hypot(x, y)
        }
        return out
    }

    private fun pebble(): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            out[i] = 1f + 0.075f * cos(2f * theta + 0.5f) + 0.035f * cos(3f * theta + 2.1f)
        }
        return normalizePeak(out)
    }

    private fun cloud(): FloatArray {
        data class Cir(val x: Float, val y: Float, val r: Float)
        val circles = listOf(
            Cir(-0.44f, 0.2f, 0.54f),
            Cir(0.46f, 0.2f, 0.5f),
            Cir(0.02f, 0.3f, 0.6f),
            Cir(-0.24f, -0.3f, 0.48f),
            Cir(0.3f, -0.24f, 0.44f),
        )
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val dx = cos(i * TAU / BLOB_PROFILE_SAMPLES)
            val dy = sin(i * TAU / BLOB_PROFILE_SAMPLES)
            var best = 0f
            for (c in circles) {
                val b = dx * c.x + dy * c.y
                val disc = b * b - (c.x * c.x + c.y * c.y - c.r * c.r)
                if (disc < 0f) continue
                val tt = b + sqrt(disc)
                if (tt > best) best = tt
            }
            out[i] = best
        }
        return normalizePeak(out)
    }

    private fun hullOfCircles(
        x1: Float, y1: Float, r1: Float,
        x2: Float, y2: Float, r2: Float,
    ): List<Pair<Float, Float>> {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx, dy).coerceAtLeast(1e-6f)
        val base = atan2(dy, dx)
        val spread = acos(((r1 - r2) / dist).coerceIn(-1f, 1f))
        val steps = 64
        val pts = ArrayList<Pair<Float, Float>>(steps + 2)
        for (i in 0..steps / 2) {
            val a = base + spread + ((TAU - 2f * spread) * i) / (steps / 2f)
            pts.add((x1 + cos(a) * r1) to (y1 + sin(a) * r1))
        }
        for (i in 0..steps / 2) {
            val a = base - spread + (2f * spread * i) / (steps / 2f)
            pts.add((x2 + cos(a) * r2) to (y2 + sin(a) * r2))
        }
        return pts
    }

    private fun regularPolygon(sides: Int, radius: Float, rc: Float, rotationDeg: Float): List<Pair<Float, Float>> {
        val rot = deg(rotationDeg)
        val verts = Array(sides) { i ->
            val a = rot + i * TAU / sides
            val rr = (radius - rc).coerceAtLeast(0.05f)
            (cos(a) * rr) to (sin(a) * rr)
        }
        val arcSteps = 10
        val pts = ArrayList<Pair<Float, Float>>(sides * (arcSteps + 1))
        fun normalAngle(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
            val dx = b.first - a.first
            val dy = b.second - a.second
            val len = hypot(dx, dy).coerceAtLeast(1e-6f)
            return atan2(-dx / len, dy / len)
        }
        for (i in 0 until sides) {
            val prev = verts[(i - 1 + sides) % sides]
            val cur = verts[i]
            val next = verts[(i + 1) % sides]
            val a0 = normalAngle(prev, cur)
            val a1 = normalAngle(cur, next)
            var d = a1 - a0
            while (d > Math.PI) d -= TAU
            while (d < -Math.PI) d += TAU
            for (k in 0..arcSteps) {
                val a = a0 + d * k / arcSteps
                pts.add((cur.first + cos(a) * rc) to (cur.second + sin(a) * rc))
            }
        }
        return pts
    }

    private fun profileFromPolygon(pts: List<Pair<Float, Float>>): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        val n = pts.size
        if (n < 3) return out.also { it.fill(1f) }
        for (s in 0 until BLOB_PROFILE_SAMPLES) {
            val ang = s * TAU / BLOB_PROFILE_SAMPLES
            val dx = cos(ang)
            val dy = sin(ang)
            var best = 0f
            for (i in 0 until n) {
                val a = pts[i]
                val b = pts[(i + 1) % n]
                val ex = b.first - a.first
                val ey = b.second - a.second
                val denom = dx * ey - dy * ex
                if (abs(denom) < 1e-8f) continue
                val tt = (a.first * ey - a.second * ex) / denom
                val u = (a.first * dy - a.second * dx) / denom
                if (tt > 0f && u in 0f..1f && tt > best) best = tt
            }
            out[s] = if (best > 0f) best else 1f
        }
        return out
    }

    private fun normalizePeak(radii: FloatArray): FloatArray {
        var peak = 0.01f
        for (r in radii) if (r > peak) peak = r
        val out = FloatArray(radii.size)
        for (i in radii.indices) out[i] = radii[i] / peak
        return out
    }

    private fun signedPow(v: Float, expn: Float): Float {
        if (v == 0f) return 0f
        val mag = exp((expn.toDouble()) * ln(abs(v).toDouble())).toFloat()
        return if (v < 0f) -mag else mag
    }
}

/** Clamp a point to stay within [maxFrac] of the profile radius in its direction. */
internal fun nearestInside(
    x: Float,
    y: Float,
    radii: FloatArray,
    maxFrac: Float,
): Pair<Float, Float> {
    val angle = atan2(y, x)
    val limit = BlobShapes.radiusAtAngle(radii, angle) * maxFrac
    val d = hypot(x, y)
    if (d <= limit || d < 1e-5f) return x to y
    val s = limit / d
    return (x * s) to (y * s)
}
