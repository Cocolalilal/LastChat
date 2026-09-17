package me.rerere.rikkahub.ui.components.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.graphics.createBitmap
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import kotlin.math.cos
import kotlin.math.sin

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
        val radius = blobDrawRadius(size)
        val cx = size / 2f
        val cy = size / 2f
        val body = BlobShapes.toPath(
            radii = frame.radii,
            cx = cx,
            cy = cy,
            scale = radius,
            squashX = frame.squashX,
            squashY = frame.squashY,
        ).asAndroidPath()
        val bodyColor = parseHexArgb(frame.colorHex)
        val hx = cx + cos(frame.highlightAngle) * radius * 0.30f
        val hy = cy + sin(frame.highlightAngle) * radius * 0.26f

        val (left, right) = layoutEyes(frame, cx, cy, radius)
        val blink = blinkScale(frame.lid)
        if (frame.pack == BlobEyePack.Grok) {
            val layer = canvas.saveLayer(0f, 0f, size, size, null)
            val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bodyColor }
            canvas.drawPath(body, solid)
            val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    hx,
                    hy,
                    radius * 0.20f,
                    intArrayOf(0x14FFFFFF, 0x00FFFFFF),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawPath(body, shine)
            val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            if (left.visible) canvas.drawPath(eyeCapsulePath(left, blink * left.open).asAndroidPath(), cut)
            if (right.visible) canvas.drawPath(eyeCapsulePath(right, blink * right.open).asAndroidPath(), cut)
            canvas.restoreToCount(layer)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (size * 0.012f).coerceAtLeast(1f)
                color = 0x6BFFFFFF
                strokeCap = Paint.Cap.ROUND
            }
            if (left.visible) canvas.drawPath(eyeCapsulePath(left, blink * left.open).asAndroidPath(), rim)
            if (right.visible) canvas.drawPath(eyeCapsulePath(right, blink * right.open).asAndroidPath(), rim)
        } else {
            if (frame.glowEnabled) {
                val glow = parseHexArgb(frame.glowHex, 0xFF87D2E9.toInt())
                val a = (frame.glowAlpha * 0.62f * 255f).toInt().coerceIn(0, 255)
                val glowR = radius * (1.10f + 0.28f * frame.glowStrength)
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx,
                        cy,
                        glowR,
                        intArrayOf((a shl 24) or (glow and 0x00FFFFFF), glow and 0x00FFFFFF),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawCircle(cx, cy, glowR, glowPaint)
            }
            val highlight = lerpArgb(bodyColor, 0xFFFFFFFF.toInt(), 0.28f)
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - radius * 0.16f,
                    cy - radius * 0.20f,
                    radius * 1.28f,
                    intArrayOf(highlight, bodyColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawPath(body, bodyPaint)
            drawGenerical(canvas, left, blink, parseHexArgb(frame.accentHex, 0xFFE8F7FF.toInt()))
            drawGenerical(canvas, right, blink, parseHexArgb(frame.accentHex, 0xFFE8F7FF.toInt()))
        }
    }

    private fun drawGenerical(
        canvas: Canvas,
        eye: LaidOutEye,
        blink: Float,
        accent: Int,
    ) {
        if (!eye.visible) return
        val lid = blink * eye.open
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawPath(eyeCapsulePath(eye, lid).asAndroidPath(), fill)
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (0x8C shl 24) or (accent and 0x00FFFFFF)
        }
        canvas.drawPath(eyeCapsulePath(eye, lid, inset = 0.38f, lift = -0.28f).asAndroidPath(), gloss)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0D000000 }
        canvas.drawPath(eyeCapsulePath(eye, lid, inset = 0.22f, lift = 0.42f).asAndroidPath(), shade)
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
