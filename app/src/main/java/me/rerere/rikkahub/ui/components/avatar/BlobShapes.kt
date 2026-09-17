package me.rerere.rikkahub.ui.components.avatar

import androidx.compose.ui.graphics.Path
import me.rerere.rikkahub.data.model.BlobShape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val BLOB_PROFILE_SAMPLES = 64

/** Draw inside this fraction of the view so organic shapes never clip the box. */
internal const val BLOB_DRAW_FIT = 0.90f

/**
 * Radial body profiles. Theta = 0 points right and increases clockwise (y down),
 * matching Compose / Android Canvas.
 *
 * Every profile is peak-normalized to 1 so the silhouette fits the draw box.
 * Lifecycle never morphs the body — only the user's chosen shape is drawn,
 * with an optional short morph when they pick a different shape.
 */
internal object BlobShapes {
    fun radii(shape: BlobShape): FloatArray {
        return when (shape) {
            BlobShape.Circle -> superellipse(2f, 1f, 1f)
            BlobShape.Squircle -> superellipse(4.2f, 1f, 1f)
            BlobShape.RoundSquare -> superellipse(8f, 1f, 1f)
            BlobShape.Capsule -> normalizePeak(hullOfCircles(-0.42f, 0f, 0.62f, 0.42f, 0f, 0.62f))
            BlobShape.Pebble -> pebble()
            BlobShape.SoftHex -> normalizePeak(regularPolygon(6, 1.04f, 0.26f, 0f))
            BlobShape.Triangle -> normalizePeak(regularPolygon(3, 1.12f, 0.34f, -90f))
            BlobShape.Cloud -> cloud()
            BlobShape.Droplet -> normalizePeak(hullOfCircles(0f, 0.28f, 0.66f, 0f, -0.96f, 0.05f))
            BlobShape.Pill -> superellipse(2.6f, 0.72f, 1f)
            BlobShape.Cookie -> cookie()
            BlobShape.Arch -> normalizePeak(hullOfCircles(0f, -0.48f, 0.52f, 0f, 0.38f, 0.74f))
        }
    }

    fun radiusAtAngle(radii: FloatArray, angle: Float): Float {
        val n = radii.size
        if (n == 0) return 1f
        val turns = ((angle / TAU) % 1f + 1f) % 1f
        val x = turns * n
        val i = x.toInt() % n
        val j = (i + 1) % n
        val t = x - x.toInt()
        return lerp(radii[i], radii[j], t)
    }

    fun rotated(radii: FloatArray, spinRad: Float): FloatArray {
        if (spinRad == 0f) return radii
        val out = FloatArray(radii.size)
        for (i in radii.indices) {
            val theta = i * TAU / radii.size
            out[i] = radiusAtAngle(radii, theta - spinRad)
        }
        return out
    }

    fun toPath(
        radii: FloatArray,
        cx: Float,
        cy: Float,
        scale: Float,
        squashX: Float = 1f,
        squashY: Float = 1f,
    ): Path {
        val pts = Array(BLOB_PROFILE_SAMPLES) { i ->
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            val r = radii[i] * scale
            (cx + r * cos(theta) * squashX) to (cy + r * sin(theta) * squashY)
        }
        return closedCatmullRom(pts)
    }

    private fun superellipse(n: Float, a: Float, b: Float): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        val exp = 2f / n
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            val c = cos(theta)
            val s = sin(theta)
            val x = a * signedPow(c, exp)
            val y = b * signedPow(s, exp)
            out[i] = hypot(x, y)
        }
        return normalizePeak(out)
    }

    private fun pebble(): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            out[i] = 1f +
                0.075f * cos(2f * theta + 0.5f) +
                0.035f * cos(3f * theta + 2.1f)
        }
        return normalizePeak(out)
    }

    private fun cookie(): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            out[i] = 1f + 0.17f * cos(6f * theta)
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
                val t = b + kotlin.math.sqrt(disc)
                if (t > best) best = t
            }
            out[i] = best
        }
        return normalizePeak(out)
    }

    private fun hullOfCircles(
        x1: Float,
        y1: Float,
        r1: Float,
        x2: Float,
        y2: Float,
        r2: Float,
    ): FloatArray {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx, dy).coerceAtLeast(1e-6f)
        val base = atan2(dy, dx)
        val spread = kotlin.math.acos(((r1 - r2) / dist).coerceIn(-1f, 1f))
        val steps = 48
        val pts = ArrayList<Pair<Float, Float>>(steps + 2)
        for (i in 0..steps / 2) {
            val a = base + spread + ((TAU - 2f * spread) * i) / (steps / 2f)
            pts.add((x1 + cos(a) * r1) to (y1 + sin(a) * r1))
        }
        for (i in 0..steps / 2) {
            val a = base - spread + (2f * spread * i) / (steps / 2f)
            pts.add((x2 + cos(a) * r2) to (y2 + sin(a) * r2))
        }
        return profileFromPolygon(pts)
    }

    private fun regularPolygon(sides: Int, radius: Float, rc: Float, rotationDeg: Float): FloatArray {
        val rot = deg(rotationDeg)
        val verts = Array(sides) { i ->
            val a = rot + i * TAU / sides
            val rr = (radius - rc).coerceAtLeast(0.05f)
            (cos(a) * rr) to (sin(a) * rr)
        }
        val arcSteps = 8
        val pts = ArrayList<Pair<Float, Float>>(sides * (arcSteps + 1))
        fun normal(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
            val dx = b.first - a.first
            val dy = b.second - a.second
            val len = hypot(dx, dy).coerceAtLeast(1e-6f)
            return atan2(-dx / len, dy / len)
        }
        for (i in 0 until sides) {
            val prev = verts[(i - 1 + sides) % sides]
            val cur = verts[i]
            val next = verts[(i + 1) % sides]
            val a0 = normal(prev, cur)
            val a1 = normal(cur, next)
            var d = a1 - a0
            while (d > Math.PI) d -= TAU
            while (d < -Math.PI) d += TAU
            for (k in 0..arcSteps) {
                val a = a0 + d * k / arcSteps
                pts.add((cur.first + cos(a) * rc) to (cur.second + sin(a) * rc))
            }
        }
        return profileFromPolygon(pts)
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
                if (kotlin.math.abs(denom) < 1e-8f) continue
                val t = (a.first * ey - a.second * ex) / denom
                val u = (a.first * dy - a.second * dx) / denom
                if (t > 0f && u in 0f..1f && t > best) best = t
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

    private fun signedPow(v: Float, exp: Float): Float {
        val mag = kotlin.math.abs(v).toDouble().powSafe(exp.toDouble()).toFloat()
        return if (v < 0f) -mag else mag
    }

    private fun Double.powSafe(exp: Double): Double {
        if (this == 0.0) return 0.0
        return kotlin.math.abs(this).let { kotlin.math.exp(exp * kotlin.math.ln(it)) }
    }

    private fun closedCatmullRom(pts: Array<Pair<Float, Float>>, tension: Float = 1f / 6f): Path {
        val n = pts.size
        val path = Path()
        if (n < 3) return path
        path.moveTo(pts[0].first, pts[0].second)
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val p3 = pts[(i + 2) % n]
            val c1x = p1.first + (p2.first - p0.first) * tension
            val c1y = p1.second + (p2.second - p0.second) * tension
            val c2x = p2.first - (p3.first - p1.first) * tension
            val c2y = p2.second - (p3.second - p1.second) * tension
            path.cubicTo(c1x, c1y, c2x, c2y, p2.first, p2.second)
        }
        path.close()
        return path
    }
}

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
