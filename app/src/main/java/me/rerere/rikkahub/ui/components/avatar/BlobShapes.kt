package me.rerere.rikkahub.ui.components.avatar

import androidx.compose.ui.graphics.Path
import me.rerere.rikkahub.data.model.BlobShape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val BLOB_PROFILE_SAMPLES = 64

/**
 * Radial body profiles. Theta = 0 points right and increases clockwise (y down),
 * matching Compose / Android Canvas.
 *
 * Lifecycle never morphs the body — only the user's chosen shape is drawn,
 * with an optional short morph when they pick a different shape.
 */
internal object BlobShapes {
    fun radii(shape: BlobShape): FloatArray {
        return when (shape) {
            BlobShape.Circle -> superellipse(2f, 1f, 1f)
            BlobShape.Squircle -> superellipse(4.5f, 1f, 1f)
            BlobShape.RoundSquare -> superellipse(8f, 1f, 1f)
            BlobShape.Capsule -> superellipse(2.4f, 0.78f, 1f)
            BlobShape.Pebble -> pebble()
            BlobShape.SoftHex -> softHex()
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

    fun toPath(radii: FloatArray, cx: Float, cy: Float, scale: Float): Path {
        val pts = Array(BLOB_PROFILE_SAMPLES) { i ->
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            val r = radii[i] * scale
            (cx + r * cos(theta)) to (cy + r * sin(theta))
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
        // Normalize so the mean radius is 1 — keeps eye scale stable across shapes.
        val mean = out.average().toFloat().coerceAtLeast(0.01f)
        for (i in out.indices) out[i] /= mean
        return out
    }

    private fun pebble(): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            out[i] = 1f +
                0.07f * cos(2f * theta + 0.5f) +
                0.035f * sin(3f * theta + 1.2f) +
                0.015f * cos(5f * theta + 2.1f)
        }
        return out
    }

    private fun softHex(): FloatArray {
        val out = FloatArray(BLOB_PROFILE_SAMPLES)
        for (i in 0 until BLOB_PROFILE_SAMPLES) {
            val theta = i * TAU / BLOB_PROFILE_SAMPLES
            // Rounded hex: flatten every 60°, then blend toward a circle.
            val hex = 1f / kotlin.math.cos(((theta + TAU / 12f) % (TAU / 6f)) - TAU / 12f)
            out[i] = lerp(1f, hex, 0.42f)
        }
        val mean = out.average().toFloat().coerceAtLeast(0.01f)
        for (i in out.indices) out[i] /= mean
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
