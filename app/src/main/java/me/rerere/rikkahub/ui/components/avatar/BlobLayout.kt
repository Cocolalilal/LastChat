package me.rerere.rikkahub.ui.components.avatar

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
)

internal fun layoutEyes(
    frame: BlobFrame,
    centerX: Float,
    centerY: Float,
    radius: Float,
): Pair<LaidOutEye, LaidOutEye> {
    val scale = radius * frame.breath
    return if (frame.pack == me.rerere.rikkahub.data.model.BlobEyePack.Grok) {
        val (lp, rp) = eyePoses(frame.gaze, 1f, frame.split)
        grokEye(lp, frame.left, frame, centerX, centerY, scale) to
            grokEye(rp, frame.right, frame, centerX, centerY, scale)
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
    val fit = BlobShapes.radiusAtAngle(frame.radii, kotlin.math.atan2(pose.y, pose.x))
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
    )
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
        )
    }
    return one(-1f, frame.left) to one(1f, frame.right)
}

/**
 * Maps a local eye-space point through tilt, the sphere tangent frame, blink
 * squash, and translation to screen pixels.
 */
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
