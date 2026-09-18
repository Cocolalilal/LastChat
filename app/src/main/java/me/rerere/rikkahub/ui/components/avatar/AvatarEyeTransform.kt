package me.rerere.rikkahub.ui.components.avatar

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen-space affine for one eye, shared by every renderer.
 *
 * The eye is a rounded rectangle centred at the origin with half-extents
 * ([hwPx],[hhPx]); apply the 2x3 matrix `[m00 m01 tx / m10 m11 ty]` to place it.
 * Tilt is folded into the matrix; the blink is a vertical squash in *screen*
 * space (only the y-row is scaled), matching the measured reference.
 */
internal data class EyeAffine(
    val m00: Float, val m01: Float, val tx: Float,
    val m10: Float, val m11: Float, val ty: Float,
    val hwPx: Float, val hhPx: Float,
    val cornerPx: Float,
    val visible: Boolean,
)

internal fun eyeAffine(
    eye: LaidEye,
    centerX: Float,
    centerY: Float,
    radius: Float,
    lid: Float,
    roundness: Float,
): EyeAffine {
    val vScale = blinkScale(lid) * eye.open.coerceIn(0f, 1f)
    val ct = cos(eye.tiltRad)
    val st = sin(eye.tiltRad)
    val hwPx = eye.hw * radius
    val hhPx = eye.hh * radius
    val corner = min(hwPx, hhPx) * roundness.coerceIn(0.1f, 1f)
    return EyeAffine(
        m00 = eye.a * ct + eye.c * st,
        m01 = eye.c * ct - eye.a * st,
        tx = centerX + eye.cx * radius,
        m10 = vScale * (eye.b * ct + eye.d * st),
        m11 = vScale * (eye.d * ct - eye.b * st),
        ty = centerY + eye.cy * radius,
        hwPx = hwPx,
        hhPx = hhPx,
        cornerPx = corner,
        visible = eye.visible,
    )
}
