package me.rerere.rikkahub.ui.components.avatar

import kotlin.math.cos
import kotlin.math.sin

/**
 * Blink schedule and idle liveliness for blob avatars. Pure Kotlin, clock-free.
 *
 * Sphere-projected capsules (the `bloub` recreation of the x.ai homepage bot)
 * were rejected for the Grok pack: Grokky Character marks are small slanted
 * black slits sitting on a flat coloured shape. [eyePoses] is kept as a
 * reference of the measured rest gaze but is not used to lay out Grok eyes.
 */

internal data class HeadGaze(
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
)

/** Orthographic projection of one eye's tangent frame. */
internal data class EyePose(
    val x: Float,
    val y: Float,
    /** tangent 2x2 matrix, SVG-style [a b c d] */
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    /** normal z; > 0 = front-facing = visible */
    val depth: Float,
)

internal data class Liveliness(
    val dYaw: Float,
    val dPitch: Float,
    val dRoll: Float,
    val lid: Float,
    val driftX: Float,
    val driftY: Float,
    val breath: Float,
)

/** Half-gap of the eyes on the sphere, degrees (total separation ~31°). */
internal const val EYE_SPLIT = 15.46f
/** Rest eye size, ball-radius units. */
internal const val EYE_W = 0.186f
internal const val EYE_H = 0.412f

internal val REST_GAZE = HeadGaze(yaw = 28.49f, pitch = 28.62f, roll = -13f)

private fun spin(u: FloatArray, v: FloatArray, angle: Float): Pair<FloatArray, FloatArray> {
    val c = cos(angle)
    val s = sin(angle)
    return floatArrayOf(
        u[0] * c + v[0] * s,
        u[1] * c + v[1] * s,
        u[2] * c + v[2] * s,
    ) to floatArrayOf(
        v[0] * c - u[0] * s,
        v[1] * c - u[1] * s,
        v[2] * c - u[2] * s,
    )
}

/**
 * Head frame then both eyes. Screen frame: x right, y down, z toward viewer.
 * Index 0 = inner eye, 1 = outer eye.
 */
internal fun eyePoses(gaze: HeadGaze, scale: Float, split: Float = EYE_SPLIT): Pair<EyePose, EyePose> {
    var f = floatArrayOf(0f, 0f, 1f)
    var right = floatArrayOf(1f, 0f, 0f)
    var down = floatArrayOf(0f, 1f, 0f)

    spin(f, right, deg(gaze.yaw)).also { f = it.first; right = it.second }
    spin(down, f, deg(gaze.pitch)).also { down = it.first; f = it.second }
    spin(right, down, deg(gaze.roll)).also { right = it.first; down = it.second }

    fun build(side: Float): EyePose {
        val (ef, er) = spin(f, right, deg(split * side))
        return EyePose(
            x = ef[0] * scale,
            y = ef[1] * scale,
            a = er[0],
            b = er[1],
            c = down[0],
            d = down[1],
            depth = ef[2],
        )
    }
    return build(-1f) to build(1f)
}

private val BLINK_RNG = createRng(0x5EED)
private val BLINKS: FloatArray = run {
    val out = ArrayList<Float>(400)
    var t = 1.4f
    while (t < 900f) {
        out.add(t)
        t += 1.9f + BLINK_RNG() * 2.7f
        if (BLINK_RNG() < 0.18f) {
            out.add(t)
            t += 0.24f
        }
    }
    out.toFloatArray()
}

/** Measured: 1–2 frames at 10 fps. */
internal const val BLINK_DUR = 0.18f

internal fun blinkLid(t: Float): Float {
    for (start in BLINKS) {
        if (t < start) break
        val k = (t - start) / BLINK_DUR
        if (k in 0f..1f) {
            return if (k < 0.45f) 1f - k / 0.45f else (k - 0.45f) / 0.55f
        }
    }
    return 1f
}

internal fun liveliness(
    t: Float,
    wander: Float = 1f,
    blink: Boolean = true,
    float: Boolean = true,
): Liveliness = Liveliness(
    dYaw = (loopNoise(t, 11.3f, 0.4f) * 5.5f + loopNoise(t, 3.7f, 2.1f) * 1.6f) * wander,
    dPitch = (loopNoise(t, 9.1f, 1.3f) * 4.2f + loopNoise(t, 4.3f, 0.7f) * 1.3f) * wander,
    dRoll = loopNoise(t, 13.7f, 3.2f) * 2.2f * wander,
    lid = if (blink) blinkLid(t) else 1f,
    driftX = if (float) loopNoise(t, 7.9f, 1.9f) * 0.006f else 0f,
    driftY = if (float) loopNoise(t, 5.3f, 0.3f) * 0.007f else 0f,
    breath = if (float) 1f + sin((t / 3.4f) * TAU) * 0.005f else 1f,
)

/** Vertical squash in screen space; bbox width is preserved. */
internal fun blinkScale(lid: Float): Float = 0.06f + 0.94f * clamp(lid)

internal fun firstBlinkTime(): Float = BLINKS.first()
