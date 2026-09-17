package me.rerere.rikkahub.ui.components.avatar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.graphics.createBitmap
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import kotlin.math.min

/**
 * Static idle (or explicit lifecycle) raster of a blob avatar.
 * Glance widgets and shortcuts cannot run the Compose clock, so they freeze
 * one frame. This is intentional, not unfinished animation.
 */
object BlobBitmap {
    fun render(
        spec: Avatar.Blob,
        sizePx: Int,
        lifecycle: BlobLifecycle = BlobLifecycle.Idle,
        tSeconds: Float = 1.1f,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val runtime = BlobRuntime()
        val frame = runtime.sample(
            tSeconds = tSeconds,
            spec = spec,
            requested = lifecycle,
            reduceMotion = true,
        )
        drawFrame(canvas, frame, sizePx.toFloat())
        return bitmap
    }

    internal fun drawFrame(canvas: Canvas, frame: BlobFrame, size: Float) {
        val radius = size / 2f
        val cx = size / 2f
        val cy = size / 2f
        val body = BlobShapes.toPath(frame.radii, cx, cy, radius).asAndroidPath()
        val bodyColor = parseHexArgb(frame.colorHex)
        val highlight = lerpArgb(bodyColor, 0xFFFFFFFF.toInt(), 0.35f)
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * 0.18f,
                cy - radius * 0.22f,
                radius * 1.35f,
                intArrayOf(highlight, bodyColor),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        val (left, right) = layoutEyes(frame, cx, cy, radius)
        val blink = blinkScale(frame.lid)
        if (frame.pack == BlobEyePack.Grok) {
            val layer = canvas.saveLayer(0f, 0f, size, size, null)
            canvas.drawPath(body, bodyPaint)
            val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            if (left.visible) canvas.drawPath(androidEyePath(left, blink * left.open), cut)
            if (right.visible) canvas.drawPath(androidEyePath(right, blink * right.open), cut)
            canvas.restoreToCount(layer)
        } else {
            canvas.drawPath(body, bodyPaint)
            drawGenerical(canvas, left, blink, frame, size)
            drawGenerical(canvas, right, blink, frame, size)
        }
    }

    private fun drawGenerical(
        canvas: Canvas,
        eye: LaidOutEye,
        blink: Float,
        frame: BlobFrame,
        size: Float,
    ) {
        if (!eye.visible) return
        val lid = blink * eye.open
        val path = androidEyePath(eye, lid)
        if (frame.glowEnabled) {
            val glowR = maxOf(eye.hw, eye.hh) * 1.55f
            val glow = parseHexArgb(frame.glowHex, 0xFF87D2E9.toInt())
            val a = (frame.glowAlpha * 255f).toInt().coerceIn(0, 255)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    eye.cx,
                    eye.cy,
                    glowR,
                    intArrayOf((a shl 24) or (glow and 0x00FFFFFF), glow and 0x00FFFFFF),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
                maskFilter = BlurMaskFilter(glowR * 0.15f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(eye.cx, eye.cy, glowR, glowPaint)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                eye.cx,
                eye.cy - eye.hh,
                eye.cx,
                eye.cy + eye.hh,
                0xFFFFFFFF.toInt(),
                0xFFD7F4FF.toInt(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(path, fill)
        val strokeW = (size * 0.028f).coerceAtLeast(1.2f)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = 0xFFFFFFFF.toInt()
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, stroke)
    }

    private fun androidEyePath(eye: LaidOutEye, blink: Float): AndroidPath {
        val corner = min(eye.hw, eye.hh)
        val path = AndroidPath()
        path.addRoundRect(
            RectF(-eye.hw, -eye.hh, eye.hw, eye.hh),
            corner,
            corner,
            AndroidPath.Direction.CW,
        )
        val ct = kotlin.math.cos(eye.tiltRad)
        val st = kotlin.math.sin(eye.tiltRad)
        val m00 = eye.a * ct + eye.c * st
        val m01 = -eye.a * st + eye.c * ct
        val m10 = blink * (eye.b * ct + eye.d * st)
        val m11 = blink * (-eye.b * st + eye.d * ct)
        val matrix = Matrix()
        matrix.setValues(
            floatArrayOf(
                m00, m01, eye.cx,
                m10, m11, eye.cy,
                0f, 0f, 1f,
            )
        )
        path.transform(matrix)
        return path
    }

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val k = clamp(t)
        fun ch(shift: Int): Int {
            val av = (a ushr shift) and 0xFF
            val bv = (b ushr shift) and 0xFF
            return (av + ((bv - av) * k)).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
