package me.rerere.rikkahub.ui.components.avatar

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath

internal fun parseHexArgb(hex: String, fallback: Int = 0xFF009FE0.toInt()): Int {
    val raw = hex.trim().removePrefix("#")
    return when (raw.length) {
        3 -> {
            val r = raw[0].digitToIntOrNull(16) ?: return fallback
            val g = raw[1].digitToIntOrNull(16) ?: return fallback
            val b = raw[2].digitToIntOrNull(16) ?: return fallback
            (0xFF shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
        }
        6 -> {
            val v = raw.toLongOrNull(16) ?: return fallback
            (0xFF000000.toInt()) or v.toInt()
        }
        8 -> raw.toLongOrNull(16)?.toInt() ?: fallback
        else -> fallback
    }
}

internal data class LaidOutEye(
    val cx: Float,
    val cy: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val hw: Float,
    val hh: Float,
    val tiltRad: Float,
    val open: Float,
    val visible: Boolean,
    val roundness: Float = 1f,
)

/** Inset so sphere-rest eyes stay inside pebble/capsule instead of clipping the rim. */
private const val GROK_FIT = 0.82f
/** Keep the pair's midpoint from drifting past this fraction of the body radius. */
private const val GROK_PAIR_LIMIT = 0.38f

internal fun layoutEyes(
    frame: BlobFrame,
    centerX: Float,
    centerY: Float,
    radius: Float,
): Pair<LaidOutEye, LaidOutEye> {
    val scale = radius * frame.breath
    return if (frame.pack == me.rerere.rikkahub.data.model.BlobEyePack.Grok) {
        val (lp, rp) = eyePoses(frame.gaze, 1f, frame.split)
        val left = grokEye(lp, frame.left, frame, centerX, centerY, scale)
        val right = grokEye(rp, frame.right, frame, centerX, centerY, scale)
        pullGrokPairInside(left, right, centerX, centerY, scale)
    } else {
        genericalEyes(frame, centerX, centerY, scale)
    }
}

private fun grokEye(
    pose: EyePose,
    cfg: EyeCfg,
    frame: BlobFrame,
    centerX: Float,
    centerY: Float,
    scale: Float,
): LaidOutEye {
    val fit = BlobShapes.radiusAtAngle(frame.radii, kotlin.math.atan2(pose.y, pose.x)) * GROK_FIT
    return LaidOutEye(
        cx = centerX + (pose.x * fit + frame.cx) * scale,
        cy = centerY + (pose.y * fit + frame.cy) * scale,
        a = pose.a,
        b = pose.b,
        c = pose.c,
        d = pose.d,
        hw = cfg.w * scale / 2f,
        hh = cfg.h * scale / 2f,
        tiltRad = deg(cfg.tilt),
        open = cfg.open,
        visible = pose.depth > 0.02f,
        roundness = frame.eyeRoundness,
    )
}

private fun pullGrokPairInside(
    left: LaidOutEye,
    right: LaidOutEye,
    centerX: Float,
    centerY: Float,
    scale: Float,
): Pair<LaidOutEye, LaidOutEye> {
    val mx = ((left.cx + right.cx) * 0.5f - centerX) / scale
    val my = ((left.cy + right.cy) * 0.5f - centerY) / scale
    val d = kotlin.math.hypot(mx, my)
    if (d <= GROK_PAIR_LIMIT || d < 1e-5f) return left to right
    val s = (d - GROK_PAIR_LIMIT) / d
    val dx = mx * s * scale
    val dy = my * s * scale
    return left.copy(cx = left.cx - dx, cy = left.cy - dy) to
        right.copy(cx = right.cx - dx, cy = right.cy - dy)
}

private fun genericalEyes(
    frame: BlobFrame,
    centerX: Float,
    centerY: Float,
    scale: Float,
): Pair<LaidOutEye, LaidOutEye> {
    val (mx, my) = nearestInside(frame.lookX, frame.lookY, frame.radii, 0.42f)
    fun one(side: Float, cfg: EyeCfg): LaidOutEye {
        val (x, y) = nearestInside(side * frame.split + mx, my, frame.radii, 0.58f)
        return LaidOutEye(
            cx = centerX + (x + frame.cx) * scale,
            cy = centerY + (y + frame.cy) * scale,
            a = 1f,
            b = 0f,
            c = 0f,
            d = 1f,
            hw = cfg.w * scale / 2f,
            hh = cfg.h * scale / 2f,
            tiltRad = deg(cfg.tilt),
            open = cfg.open,
            visible = true,
            roundness = frame.eyeRoundness,
        )
    }
    return one(-1f, frame.left) to one(1f, frame.right)
}

internal fun blobDrawRadius(minDim: Float): Float = minDim * 0.5f * BLOB_DRAW_FIT

internal fun eyeCapsulePath(
    eye: LaidOutEye,
    blink: Float,
    inset: Float = 0f,
    lift: Float = 0f,
): Path {
    val hw = eye.hw * (1f - inset)
    val hh = eye.hh * (1f - inset)
    val roundness = eye.roundness.coerceIn(0.12f, 1f)
    val corner = minOf(hw, hh) * roundness
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(-hw, -hh, hw, hh),
                radiusX = corner,
                radiusY = corner,
            )
        )
    }
    val ct = kotlin.math.cos(eye.tiltRad)
    val st = kotlin.math.sin(eye.tiltRad)
    val m00 = eye.a * ct + eye.c * st
    val m01 = -eye.a * st + eye.c * ct
    val m10 = blink * (eye.b * ct + eye.d * st)
    val m11 = blink * (-eye.b * st + eye.d * ct)
    val liftX = m01 * lift * eye.hh
    val liftY = m11 * lift * eye.hh
    val androidMatrix = android.graphics.Matrix()
    androidMatrix.setValues(
        floatArrayOf(
            m00, m01, eye.cx + liftX,
            m10, m11, eye.cy + liftY,
            0f, 0f, 1f,
        )
    )
    path.asAndroidPath().transform(androidMatrix)
    return path
}

internal inline fun mapEyePoint(
    u: Float,
    v: Float,
    eye: LaidOutEye,
    blink: Float,
    out: (x: Float, y: Float) -> Unit,
) {
    val ct = kotlin.math.cos(eye.tiltRad)
    val st = kotlin.math.sin(eye.tiltRad)
    val ur = u * ct - v * st
    val vr = u * st + v * ct
    val x = eye.a * ur + eye.c * vr
    val y = (eye.b * ur + eye.d * vr) * blink
    out(eye.cx + x, eye.cy + y)
}
