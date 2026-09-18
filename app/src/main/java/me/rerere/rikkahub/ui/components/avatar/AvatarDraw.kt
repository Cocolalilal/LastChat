package me.rerere.rikkahub.ui.components.avatar

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import me.rerere.rikkahub.data.model.BlobEyePack
import kotlin.math.min

/**
 * The single Android drawing routine for a [BlobFrame]. Used by the Compose
 * avatar (via `nativeCanvas`) and by [BlobBitmap] (export / widgets / shortcuts),
 * so on-device rendering has exactly one implementation. The head-less AWT
 * contact-sheet renderer mirrors this using the same geometry helpers.
 */
internal object AvatarDraw {
    fun draw(canvas: Canvas, frame: BlobFrame, width: Float, height: Float) {
        val minDim = min(width, height)
        val radius = minDim * 0.5f * BLOB_DRAW_FIT * frame.breath
        val cx = width / 2f + frame.cx * radius
        val cy = height / 2f + frame.cy * radius

        val bodyColor = parseArgb(frame.colorHex)
        val eyeColor = parseArgb(frame.eyeHex, 0xFFFBFDFF.toInt())
        val accentColor = parseArgb(frame.accentHex, 0xFFEAF7FF.toInt())
        val glowColor = parseArgb(frame.glowHex, 0xFF87D2E9.toInt())

        val bodyOutline = BlobShapes.outline(frame.radii, cx, cy, radius, frame.squashX, frame.squashY)
        val bodyPath = bodyOutline.toAndroidPath()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // Glow halo (Generical only) behind the body.
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

        // Flat body fill.
        fill.color = bodyColor
        fill.shader = null
        canvas.drawPath(bodyPath, fill)

        // Subtle 3-D: a key-light highlight + a soft rim shade, clipped to body.
        if (frame.flat3d && frame.lightStrength > 0f) {
            val hx = cx + frame.lightX * radius
            val hy = cy + frame.lightY * radius
            val dark = luminanceOf(bodyColor) < 0.5f
            val highlightA = if (dark) 0.16f else 0.22f
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
                intArrayOf(argbWithAlpha(0xFF000000.toInt(), 0f), argbWithAlpha(0xFF000000.toInt(), 0f), argbWithAlpha(0xFF000000.toInt(), if (dark) 0.22f else 0.12f)),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(bodyPath, rim)
            canvas.restore()
        }

        // Eyes, clipped to the body so they never spill past the silhouette.
        canvas.save()
        canvas.clipPath(bodyPath)
        drawEye(canvas, frame.left, cx, cy, radius, frame, eyeColor, accentColor)
        drawEye(canvas, frame.right, cx, cy, radius, frame, eyeColor, accentColor)
        canvas.restore()
    }

    private fun drawEye(
        canvas: Canvas,
        eye: LaidEye,
        cx: Float,
        cy: Float,
        radius: Float,
        frame: BlobFrame,
        eyeColor: Int,
        accentColor: Int,
    ) {
        val af = eyeAffine(eye, cx, cy, radius, frame.lid, frame.eyeRoundness)
        if (!af.visible) return
        val matrix = Matrix().apply {
            setValues(floatArrayOf(af.m00, af.m01, af.tx, af.m10, af.m11, af.ty, 0f, 0f, 1f))
        }
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eyeColor }
        val eyePath = roundRectPath(af.hwPx, af.hhPx, af.cornerPx).apply { transform(matrix) }
        canvas.drawPath(eyePath, eyePaint)

        if (frame.pack == BlobEyePack.Generical) {
            val glossHalfW = af.hwPx * 0.6f
            val top = -af.hhPx + af.hhPx * 0.14f
            val bottom = top + af.hhPx * 0.6f
            val gloss = Path().apply {
                addRoundRect(
                    RectF(-glossHalfW, top, glossHalfW, bottom),
                    glossHalfW * 0.8f, glossHalfW * 0.8f, Path.Direction.CW,
                )
                transform(matrix)
            }
            canvas.drawPath(gloss, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argbWithAlpha(accentColor, 0.5f) })
        }
    }

    private fun roundRectPath(hw: Float, hh: Float, corner: Float): Path = Path().apply {
        addRoundRect(RectF(-hw, -hh, hw, hh), corner, corner, Path.Direction.CW)
    }

    private fun BodyOutline.toAndroidPath(): Path = Path().apply {
        moveTo(startX, startY)
        for (s in segs) cubicTo(s.c1x, s.c1y, s.c2x, s.c2y, s.x, s.y)
        close()
    }
}
