package me.rerere.rikkahub.ui.components.avatar

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import me.rerere.rikkahub.data.model.BlobEyePack
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The single Android drawing routine for a [BlobFrame]. Used by the Compose
 * avatar (via `nativeCanvas`) and by [BlobBitmap] (export / widgets / shortcuts),
 * so on-device rendering has exactly one implementation.
 *
 * Generical eyes: white (or custom) stroke + vertical pale gradient fill, matching
 * Julian's painted refs. Grok eyes: small dark slits on the flat coloured mark.
 */
internal object AvatarDraw {
    fun draw(canvas: Canvas, frame: BlobFrame, width: Float, height: Float) {
        val minDim = min(width, height)
        val radius = minDim * 0.5f * BLOB_DRAW_FIT * frame.breath
        val cx = width / 2f + frame.cx * radius
        val cy = height / 2f + frame.cy * radius

        val bodyColor = parseArgb(frame.colorHex)
        val eyeColor = parseArgb(frame.eyeHex, 0xFFFBFDFF.toInt())
        val glowColor = parseArgb(frame.glowHex, 0xFF87D2E9.toInt())

        val bodyOutline = BlobShapes.outline(frame.radii, cx, cy, radius, frame.squashX, frame.squashY)
        val bodyPath = bodyOutline.toAndroidPath()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // Glow halo (Generical only) behind the body — never on the eyes themselves.
        if (frame.glowEnabled && frame.pack == BlobEyePack.Generical && frame.glowStrength > 0f) {
            val glowR = radius * (1.12f + 0.3f * frame.glowStrength)
            val a = (frame.glowAlpha * 0.7f + 0.15f * frame.glowStrength).coerceIn(0f, 0.8f)
            fill.shader = RadialGradient(
                cx, cy, glowR,
                intArrayOf(argbWithAlpha(glowColor, a), argbWithAlpha(glowColor, 0f)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, glowR, fill)
            fill.shader = null
        }

        fill.color = bodyColor
        fill.shader = null
        canvas.drawPath(bodyPath, fill)

        if (frame.flat3d && frame.lightStrength > 0f) {
            val hx = cx + frame.lightX * radius
            val hy = cy + frame.lightY * radius
            val dark = luminanceOf(bodyColor) < 0.5f
            val highlightA = if (dark) 0.12f else 0.12f
            canvas.save()
            canvas.clipPath(bodyPath)
            val hi = Paint(Paint.ANTI_ALIAS_FLAG)
            hi.shader = RadialGradient(
                hx, hy, radius * 0.95f,
                intArrayOf(argbWithAlpha(0xFFFFFFFF.toInt(), highlightA), argbWithAlpha(0xFFFFFFFF.toInt(), 0f)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(bodyPath, hi)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG)
            rim.shader = RadialGradient(
                cx - frame.lightX * radius * 0.4f, cy - frame.lightY * radius * 0.4f, radius * 1.15f,
                intArrayOf(
                    argbWithAlpha(0xFF000000.toInt(), 0f),
                    argbWithAlpha(0xFF000000.toInt(), 0f),
                    argbWithAlpha(0xFF000000.toInt(), if (dark) 0.22f else 0.12f),
                ),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(bodyPath, rim)
            canvas.restore()
        }

        canvas.save()
        canvas.clipPath(bodyPath)
        drawEye(canvas, frame.left, cx, cy, radius, frame, bodyColor, eyeColor)
        drawEye(canvas, frame.right, cx, cy, radius, frame, bodyColor, eyeColor)
        canvas.restore()
    }

    private fun drawEye(
        canvas: Canvas,
        eye: LaidEye,
        cx: Float,
        cy: Float,
        radius: Float,
        frame: BlobFrame,
        bodyColor: Int,
        eyeColor: Int,
    ) {
        val af = eyeAffine(eye, cx, cy, radius, frame.lid, frame.eyeRoundness)
        if (!af.visible) return
        val matrix = Matrix().apply {
            setValues(floatArrayOf(af.m00, af.m01, af.tx, af.m10, af.m11, af.ty, 0f, 0f, 1f))
        }
        val glyph = eyeGlyphPath(af).apply { transform(matrix) }

        if (frame.pack == BlobEyePack.Grok) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = resolveGrokSlitColor(bodyColor, eyeColor)
            }
            canvas.drawPath(glyph, paint)
            return
        }

        val stroke = if (luminanceOf(eyeColor) > 0.55f) eyeColor else 0xFFFBFDFF.toInt()
        val top = mixArgb(0xFFFFFFFF.toInt(), stroke, 0.12f)
        val bottom = genericalEyeFillBottom(bodyColor, top)
        val shader = LinearGradient(
            0f, -af.hhPx, 0f, af.hhPx,
            intArrayOf(top, bottom),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        ).apply { setLocalMatrix(matrix) }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.shader = shader
        }
        canvas.drawPath(glyph, fill)

        val minHalf = min(af.hwPx, af.hhPx)
        val strokePx = min(minHalf * 0.85f, max(1.2f, minHalf * 0.42f))
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = stroke
            strokeWidth = strokePx
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(glyph, outline)
    }

    private fun eyeGlyphPath(af: EyeAffine): Path {
        val hw = af.hwPx
        val hh = af.hhPx
        if (hw < 0.4f || hh < 0.4f) {
            return Path().apply {
                addRoundRect(RectF(-hw, -hh, hw, hh), af.cornerPx, af.cornerPx, Path.Direction.CW)
            }
        }
        val leftDrop = if (af.innerSign >= 0f) af.outerTopDrop else af.innerTopDrop
        val rightDrop = if (af.innerSign >= 0f) af.innerTopDrop else af.outerTopDrop
        val yTL = -hh + leftDrop.coerceIn(0f, 0.85f) * 2f * hh
        val yTR = -hh + rightDrop.coerceIn(0f, 0.85f) * 2f * hh
        val leftH = (hh - yTL).coerceAtLeast(0.5f)
        val rightH = (hh - yTR).coerceAtLeast(0.5f)
        val maxR = min(hw, min(leftH, rightH)) * (af.cornerPx / min(hw, hh).coerceAtLeast(0.5f)).coerceIn(0.1f, 1f)
        val rtl = (maxR * af.topRound.coerceIn(0.08f, 1f)).coerceAtLeast(0.4f)
        val rtr = rtl
        val rbr = (maxR * af.bottomRound.coerceIn(0.08f, 1f)).coerceAtLeast(0.4f)
        val rbl = rbr

        if (abs(yTL + hh) < 0.35f && abs(yTR + hh) < 0.35f) {
            return Path().apply {
                addRoundRect(
                    RectF(-hw, -hh, hw, hh),
                    floatArrayOf(rtl, rtl, rtr, rtr, rbr, rbr, rbl, rbl),
                    Path.Direction.CW,
                )
            }
        }
        return roundedQuadPath(
            -hw, yTL, hw, yTR, hw, hh, -hw, hh,
            rtl, rtr, rbr, rbl,
        )
    }

    private fun roundedQuadPath(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        r0: Float, r1: Float, r2: Float, r3: Float,
    ): Path {
        val xs = floatArrayOf(x0, x1, x2, x3)
        val ys = floatArrayOf(y0, y1, y2, y3)
        val rs = floatArrayOf(r0, r1, r2, r3)
        val path = Path()
        for (i in 0..3) {
            val ip = (i + 3) % 4
            val inn = (i + 1) % 4
            val vx0 = xs[i] - xs[ip]
            val vy0 = ys[i] - ys[ip]
            val vx1 = xs[inn] - xs[i]
            val vy1 = ys[inn] - ys[i]
            val l0 = hypot(vx0, vy0).coerceAtLeast(1e-3f)
            val l1 = hypot(vx1, vy1).coerceAtLeast(1e-3f)
            val r = min(rs[i], min(l0, l1) * 0.48f)
            val ax = xs[i] - vx0 / l0 * r
            val ay = ys[i] - vy0 / l0 * r
            val bx = xs[i] + vx1 / l1 * r
            val by = ys[i] + vy1 / l1 * r
            if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
            path.quadTo(xs[i], ys[i], bx, by)
        }
        path.close()
        return path
    }

    private fun BodyOutline.toAndroidPath(): Path = Path().apply {
        moveTo(startX, startY)
        for (s in segs) cubicTo(s.c1x, s.c1y, s.c2x, s.c2y, s.x, s.y)
        close()
    }
}
