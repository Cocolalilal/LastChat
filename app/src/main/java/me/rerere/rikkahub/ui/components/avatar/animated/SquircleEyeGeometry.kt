package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Figma 100% corner-smoothing squircle geometry and isotropic per-lid eye shaping for Generical eyes.
 *
 * Implements Figma's continuous-curvature (G2) corner-smoothing construction (`smoothing = 1f`)
 * for authored `rect` eyes (`Neutral`, `Surprised`, `Eyes closed`, `Sleeping`), and provides
 * isotropic corner-band resizing so lid presses, squints, and scale changes tighten corners
 * naturally without ever squeezing them into ellipses or introducing linear mid-corner kinks.
 */
internal object SquircleEyeGeometry {
    /** Full Figma corner smoothing (100%). */
    const val FIGMA_SMOOTHING = 1f

    private data class CornerParams(
        val r: Float,
        val p: Float,
        val a: Float,
        val xInt: Float,
        val px: Float,
        val py: Float,
        val angleAlphaDeg: Float,
        val angleBetaDeg: Float,
    )

    private fun cornerParams(radius: Float, maxP: Float, smoothing: Float): CornerParams {
        val r = radius.coerceIn(0f, maxP)
        if (r <= 1e-4f) return CornerParams(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val s = smoothing.coerceIn(0f, 1f)
        val p = min((1f + s) * r, maxP)
        val halfBudget = maxP * 0.5f
        val effectiveS = if (r > halfBudget && halfBudget > 1e-4f) {
            val pct = ((r - halfBudget) / halfBudget).coerceIn(0f, 1f)
            s * (1f - pct)
        } else {
            s
        }
        val angleAlphaDeg = 45f * effectiveS
        val angleBetaDeg = 90f * (1f - effectiveS)
        val alpha = (angleAlphaDeg * PI / 180.0).toFloat()
        val px = r * (1f - sin(alpha))
        val py = r * (1f - cos(alpha))
        val c = if (alpha > 1e-5f) r * tan(alpha * 0.5f) * cos(alpha) else 0f
        val xInt = px + c
        // Exact G2 curvature match (kappa == 1/r) at the join with the central circular arc.
        val bRaw = if (alpha > 1e-4f) {
            val t = tan(alpha * 0.5f)
            1.5f * r * t * t / sin(alpha)
        } else 0f
        val maxB = (p - xInt).coerceAtLeast(0f)
        val b = bRaw.coerceIn(0f, maxB)
        val a = (maxB - b).coerceAtLeast(0f)
        return CornerParams(r, p, a, xInt, px, py, angleAlphaDeg, angleBetaDeg)
    }

    /**
     * Builds a closed Figma-smoothed squircle rectangle path (clockwise in y-down coordinates).
     * At [smoothing] = 1f (100% Figma corner smoothing), straight edges transition with G2
     * continuity into the corner curve. Pills (`radius == min(w, h) / 2`) keep clean semicircular caps.
     */
    fun roundedRectPath(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
        smoothing: Float = FIGMA_SMOOTHING,
    ): Path {
        val right = left + width
        val bottom = top + height
        val maxP = min(width, height) * 0.5f
        val cp = cornerParams(radius, maxP, smoothing)
        val path = Path()
        if (cp.r <= 1e-4f) {
            path.moveTo(left, top)
            path.lineTo(right, top)
            path.lineTo(right, bottom)
            path.lineTo(left, bottom)
            path.close()
            return path
        }
        val r = cp.r; val p = cp.p; val a = cp.a
        val xInt = cp.xInt; val px = cp.px; val py = cp.py
        val alphaDeg = cp.angleAlphaDeg; val betaDeg = cp.angleBetaDeg

        // Start on top edge after top-left corner and walk clockwise.
        path.moveTo(left + p, top)

        // Top edge → Top-right corner
        path.lineTo(right - p, top)
        if (alphaDeg > 0.05f) {
            path.cubicTo(right - (p - a), top, right - xInt, top, right - px, top + py)
        }
        if (betaDeg > 0.05f) {
            path.arcTo(
                rect = Rect(right - 2f * r, top, right, top + 2f * r),
                startAngleDegrees = 270f + alphaDeg,
                sweepAngleDegrees = betaDeg,
                forceMoveTo = false,
            )
        }
        if (alphaDeg > 0.05f) {
            path.cubicTo(right, top + xInt, right, top + (p - a), right, top + p)
        }

        // Right edge → Bottom-right corner
        path.lineTo(right, bottom - p)
        if (alphaDeg > 0.05f) {
            path.cubicTo(right, bottom - (p - a), right, bottom - xInt, right - py, bottom - px)
        }
        if (betaDeg > 0.05f) {
            path.arcTo(
                rect = Rect(right - 2f * r, bottom - 2f * r, right, bottom),
                startAngleDegrees = 0f + alphaDeg,
                sweepAngleDegrees = betaDeg,
                forceMoveTo = false,
            )
        }
        if (alphaDeg > 0.05f) {
            path.cubicTo(right - xInt, bottom, right - (p - a), bottom, right - p, bottom)
        }

        // Bottom edge → Bottom-left corner
        path.lineTo(left + p, bottom)
        if (alphaDeg > 0.05f) {
            path.cubicTo(left + (p - a), bottom, left + xInt, bottom, left + px, bottom - py)
        }
        if (betaDeg > 0.05f) {
            path.arcTo(
                rect = Rect(left, bottom - 2f * r, left + 2f * r, bottom),
                startAngleDegrees = 90f + alphaDeg,
                sweepAngleDegrees = betaDeg,
                forceMoveTo = false,
            )
        }
        if (alphaDeg > 0.05f) {
            path.cubicTo(left, bottom - xInt, left, bottom - (p - a), left, bottom - p)
        }

        // Left edge → Top-left corner
        path.lineTo(left, top + p)
        if (alphaDeg > 0.05f) {
            path.cubicTo(left, top + (p - a), left, top + xInt, left + py, top + px)
        }
        if (betaDeg > 0.05f) {
            path.arcTo(
                rect = Rect(left, top, left + 2f * r, top + 2f * r),
                startAngleDegrees = 180f + alphaDeg,
                sweepAngleDegrees = betaDeg,
                forceMoveTo = false,
            )
        }
        if (alphaDeg > 0.05f) {
            path.cubicTo(left + xInt, top, left + (p - a), top, left + p, top)
        }

        path.close()
        return path
    }

    /**
     * Smoothly estimates the top and bottom full squircle corner spans `(pTop, pBot)` from
     * each half's corner area deficit. Continuous in every vertex coordinate, so morphing
     * between expressions never jumps.
     */
    internal fun cornerSpans(xs: FloatArray, ys: FloatArray, minX: Float, maxX: Float, minY: Float, maxY: Float): Pair<Float, Float> {
        val w = (maxX - minX).coerceAtLeast(1e-3f)
        val h = (maxY - minY).coerceAtLeast(1e-3f)
        val midY = (minY + maxY) * 0.5f
        val n = xs.size
        // Shoelace area clipped to the top half (y <= midY) and bottom half (y >= midY).
        fun clippedArea(keepTop: Boolean): Float {
            var area = 0f
            fun inside(y: Float) = if (keepTop) y <= midY else y >= midY
            var prevX = xs[n - 1]; var prevY = ys[n - 1]
            var prevIn = inside(prevY)
            // Build clipped polygon on the fly via Sutherland-Hodgman against y = midY.
            var firstX = 0f; var firstY = 0f; var hasFirst = false
            var lastX = 0f; var lastY = 0f
            fun emit(x: Float, y: Float) {
                if (!hasFirst) {
                    firstX = x; firstY = y; hasFirst = true
                } else {
                    area += lastX * y - x * lastY
                }
                lastX = x; lastY = y
            }
            for (i in 0 until n) {
                val cx = xs[i]; val cy = ys[i]
                val currIn = inside(cy)
                if (currIn != prevIn) {
                    val dy = cy - prevY
                    val t = if (abs(dy) > 1e-6f) (midY - prevY) / dy else 0f
                    emit(prevX + (cx - prevX) * t, midY)
                }
                if (currIn) emit(cx, cy)
                prevX = cx; prevY = cy; prevIn = currIn
            }
            if (hasFirst) area += lastX * firstY - firstX * lastY
            return abs(area) * 0.5f
        }
        val halfBox = w * (h * 0.5f)
        val maxSpan = min(w, h) * 0.5f
        // For both circular arcs (0.2146 R²) and Figma 100% squircles (~0.220 R²), the missing
        // area per corner is ≈ 0.220 * R², and full 100% squircle span is p = min(maxSpan, 2 * R).
        fun spanFromArea(polyArea: Float): Float {
            val missing = (halfBox - polyArea).coerceAtLeast(0f)
            val nominalR = sqrt(missing / (2f * 0.220f))
            return min(maxSpan, nominalR * 2f).coerceIn(0f, maxSpan)
        }
        return spanFromArea(clippedArea(true)) to spanFromArea(clippedArea(false))
    }

    /**
     * Shapes a Generical eye outline in place with unsqueezed squircle corners:
     *  - Pressed lids (`upper < 1` or `lower < 1`) tighten their own corners isotropically
     *    (`dx` and `dy` scale by the same factor), keeping the unpressed edge round.
     *  - Soft lids (`slant < -0.05`, e.g. Sad/Worried) preserve rounder corners.
     *  - Size changes (`sx`, `sy`) stretch straight edges while scaling corners isotropically.
     *  - Rotated thin bars (`Sleeping`) are resized in their own principal axis frame.
     */
    fun shapeOutline(xs: FloatArray, ys: FloatArray, side: Int, p: EyeShape) {
        if (p.isIdentity() || xs.isEmpty()) return
        val n = xs.size
        val originX = (xs.min() + xs.max()) * 0.5f
        val originY = (ys.min() + ys.max()) * 0.5f
        val rawW = (xs.max() - xs.min()).coerceAtLeast(1e-3f)
        val rawH = (ys.max() - ys.min()).coerceAtLeast(1e-3f)
        // Thin authored bars can already be rotated (Sleeping): un-rotate smoothly into their own frame.
        val aspect = rawW / rawH
        val amount = ((aspect - 1.5f) / 1.5f).coerceIn(0f, 1f)
        var xx = 0f; var xy = 0f; var yy = 0f
        for (i in 0 until n) {
            val x = xs[i] - originX; val y = ys[i] - originY
            xx += x * x; xy += x * y; yy += y * y
        }
        val authoredAngle = 0.5f * atan2(2f * xy, xx - yy) * amount * amount * (3f - 2f * amount)
        val ac = cos(authoredAngle); val ass = sin(authoredAngle)
        if (authoredAngle != 0f) {
            for (i in 0 until n) {
                val x = xs[i] - originX; val y = ys[i] - originY
                xs[i] = originX + x * ac + y * ass
                ys[i] = originY - x * ass + y * ac
            }
        }

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) {
            minX = min(minX, xs[i]); maxX = max(maxX, xs[i])
            minY = min(minY, ys[i]); maxY = max(maxY, ys[i])
        }
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)
        val cx = (minX + maxX) * 0.5f
        val hw = w * 0.5f
        val inward = if (side == 0) 1f else -1f

        val (pTop0, pBot0) = cornerSpans(xs, ys, minX, maxX, minY, maxY)
        val straightY0 = (h - pTop0 - pBot0).coerceAtLeast(0f)

        // Smooth blend into soft-lid shaping across slant in [-0.08, -0.02].
        val softU = ((-p.slant - 0.02f) / 0.06f).coerceIn(0f, 1f)
        val softW = softU * softU * (3f - 2f * softU)

        val fl = p.lower.coerceIn(0.05f, 1.2f)
        val lidBottom = minY + fl * h
        val hb = (lidBottom - minY).coerceAtLeast(1f)
        val maxUpper = max(1f, p.upper).coerceAtMost(1.25f)

        val newW = (w * p.sx).coerceAtLeast(1f)
        val newHw = newW * 0.5f
        val sizeScale = min(p.sx, p.sy).coerceIn(0.5f, 1.35f)

        // Use one uniform top corner span across x (based on the more pressed corner) so
        // slanted lids remain strictly convex with zero mid-edge inflection or wave.
        val fLeft = (p.upper - p.slant * (-inward)).coerceIn(0.05f, maxUpper)
        val fRight = (p.upper - p.slant * (+inward)).coerceIn(0.05f, maxUpper)
        val fMin = min(fLeft, fRight)
        val hMin = (hb * fMin * p.sy).coerceAtLeast(1f)
        val topTightFactor = 0.72f - 0.52f * softW
        val botTightFactor = 0.68f - 0.45f * softW
        val topScale = if (fMin < 1f) (1f - topTightFactor * (1f - fMin)).coerceIn(0.35f, 1f) else 1f
        val botScale = if (fl < 1f) (1f - botTightFactor * (1f - fl)).coerceIn(0.35f, 1f) else 1f
        var pTop = (pTop0 * sizeScale * topScale).coerceAtMost(newHw)
        var pBot = (pBot0 * sizeScale * botScale).coerceAtMost(newHw)
        val spanSum = pTop + pBot
        if (spanSum > hMin && spanSum > 1e-4f) {
            val k = hMin / spanSum
            pTop *= k; pBot *= k
        }

        // Map each vertex using isotropic corner bands (same scale in X and Y inside every corner).
        // Compute outX first and evaluate slant against outX so slanted lids are strictly linear
        // in final X with zero shoulder kink between the corner band and the straight middle.
        val outX = FloatArray(n)
        val outY = FloatArray(n)
        for (i in 0 until n) {
            val x = xs[i]; val y = ys[i]
            val dyTop = (y - minY).coerceAtLeast(0f)
            val dyBot = (maxY - y).coerceAtLeast(0f)

            // Vertical blend for horizontal corner span (0 inside top corner, 1 inside bottom corner).
            val vBlend = if (straightY0 > 1e-4f) {
                ((dyTop - pTop0) / straightY0).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            } else {
                (dyTop / h).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            }
            val p0 = pTop0 + (pBot0 - pTop0) * vBlend
            val p1 = pTop + (pBot - pTop) * vBlend
            val sideSign = if (x >= cx) 1f else -1f
            val dxEdge = (hw - abs(x - cx)).coerceAtLeast(0f)
            val straightX0 = (hw - p0).coerceAtLeast(0f)
            val straightX1 = (newHw - p1).coerceAtLeast(0f)
            val dxNew = when {
                dxEdge <= p0 && p0 > 1e-4f -> dxEdge * (p1 / p0)
                straightX0 > 1e-4f -> p1 + (dxEdge - p0) / straightX0 * straightX1
                else -> (dxEdge / hw) * newHw
            }
            val nx = cx + sideSign * (newHw - dxNew)
            outX[i] = nx

            val uX = ((nx - (cx - newHw)) / newW).coerceIn(0f, 1f) // 0 = left, 1 = right
            val xn = (uX * 2f - 1f) * inward // +1 = inner corner
            val f = (p.upper - p.slant * xn).coerceIn(0.05f, maxUpper)
            val newH = (hb * f * p.sy).coerceAtLeast(1f)
            val newTop = lidBottom - newH

            val straightY1 = (newH - pTop - pBot).coerceAtLeast(0f)
            outY[i] = when {
                dyTop <= pTop0 && pTop0 > 1e-4f -> newTop + dyTop * (pTop / pTop0)
                dyBot <= pBot0 && pBot0 > 1e-4f -> lidBottom - dyBot * (pBot / pBot0)
                straightY0 > 1e-4f -> newTop + pTop + (dyTop - pTop0) / straightY0 * straightY1
                else -> newTop + (dyTop / h) * newH
            }
        }

        // Re-apply authored bar angle + explicit rotation about bottom centre, plus offset.
        val r = authoredAngle + Math.toRadians(p.rot.toDouble()).toFloat()
        val cr = cos(r); val sr = sin(r)
        val pivotX = originX + (cx - originX) * ac - (lidBottom - originY) * ass
        val pivotY = originY + (cx - originX) * ass + (lidBottom - originY) * ac
        for (i in 0 until n) {
            val x0 = outX[i] - cx
            val y0 = outY[i] - lidBottom
            xs[i] = pivotX + x0 * cr - y0 * sr + p.dx * 250f
            ys[i] = pivotY + x0 * sr + y0 * cr + p.dy * 250f
        }
    }

    /**
     * Narrows a closed-eye bar horizontally to [targetWidth] centred at ([centerX], [centerY])
     * while keeping its semicircular end caps isotropically unsqueezed.
     */
    fun resizeClosedBar(xs: FloatArray, ys: FloatArray, centerX: Float, centerY: Float, targetWidth: Float) {
        if (xs.isEmpty()) return
        val oldLeft = xs.min(); val oldRight = xs.max()
        val oldTop = ys.min(); val oldBottom = ys.max()
        val oldW = (oldRight - oldLeft).coerceAtLeast(1f)
        val oldH = (oldBottom - oldTop).coerceAtLeast(1f)
        val oldCx = (oldLeft + oldRight) * 0.5f
        val oldCy = (oldTop + oldBottom) * 0.5f
        val hw = oldW * 0.5f
        val newHw = (targetWidth * 0.5f).coerceAtLeast(1f)
        val r0 = min(hw, oldH * 0.5f)
        val r1 = min(r0, newHw)
        val straightX0 = (hw - r0).coerceAtLeast(0f)
        val straightX1 = (newHw - r1).coerceAtLeast(0f)
        for (i in xs.indices) {
            val sideSign = if (xs[i] >= oldCx) 1f else -1f
            val dxEdge = (hw - abs(xs[i] - oldCx)).coerceAtLeast(0f)
            val dxNew = when {
                dxEdge <= r0 && r0 > 1e-4f -> dxEdge * (r1 / r0)
                straightX0 > 1e-4f -> r1 + (dxEdge - r0) / straightX0 * straightX1
                else -> (dxEdge / hw) * newHw
            }
            xs[i] = centerX + sideSign * (newHw - dxNew)
            ys[i] = centerY + (ys[i] - oldCy)
        }
    }
}
