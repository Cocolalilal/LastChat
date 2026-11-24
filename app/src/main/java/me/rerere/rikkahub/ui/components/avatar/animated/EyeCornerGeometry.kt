package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Nine-slice eye resizing: corner arcs scale uniformly, while straight edges take the stretch. */
internal object EyeCornerGeometry {
    fun radius(xs: FloatArray, ys: FloatArray): Float {
        val limit = min(xs.max() - xs.min(), ys.max() - ys.min()) / 2f
        if (limit <= 0f) return 0f
        val radii = ArrayList<Float>()
        for (i in xs.indices) {
            val a = (i + xs.size - 2) % xs.size
            val b = (i + 2) % xs.size
            val ax = xs[a] - xs[i]; val ay = ys[a] - ys[i]
            val bx = xs[b] - xs[i]; val by = ys[b] - ys[i]
            val cross = abs(ax * by - ay * bx)
            val ab = hypot(ax, ay); val bc = hypot(bx, by)
            if (cross < ab * bc * 0.04f) continue
            val r = ab * bc * hypot(xs[a] - xs[b], ys[a] - ys[b]) / (2f * cross)
            if (r.isFinite() && r >= limit * 0.1f && r <= limit * 2f) radii += r
        }
        if (radii.isEmpty()) return limit
        radii.sort()
        return radii[radii.size / 3].coerceAtMost(limit)
    }

    fun resize(xs: FloatArray, ys: FloatArray, left: Float, top: Float, width: Float, height: Float) {
        val oldLeft = xs.min(); val oldTop = ys.min()
        val oldWidth = xs.max() - oldLeft; val oldHeight = ys.max() - oldTop
        val r = radius(xs, ys)
        val newR = min(r, min(width, height) / 2f)
        fun map(value: Float, start: Float, span: Float, target: Float, size: Float): Float {
            if (span <= 1e-5f) return target + size / 2f
            val v = value - start
            if (r <= 1e-5f) return target + v / span * size
            return when {
                v <= r -> target + v / r * newR
                v >= span - r -> target + size - (span - v) / r * newR
                else -> target + newR + (v - r) / (span - 2f * r) * (size - 2f * newR)
            }
        }
        for (i in xs.indices) {
            xs[i] = map(xs[i], oldLeft, oldWidth, left, width)
            ys[i] = map(ys[i], oldTop, oldHeight, top, height)
        }
    }
}
