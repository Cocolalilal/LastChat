package me.rerere.rikkahub.ui.components.avatar

import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure-Kotlin math primitives for the avatar engine. No Android imports on
 * purpose: this file (and the rest of the geometry engine) must run on the plain
 * JVM so it can be unit-tested and rendered head-less (AWT) for contact sheets.
 */

internal const val TAU: Float = (PI * 2).toFloat()

internal fun clamp(v: Float, lo: Float = 0f, hi: Float = 1f): Float =
    if (v < lo) lo else if (v > hi) hi else v

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun deg(d: Float): Float = (d * PI / 180.0).toFloat()

/** Measured: transitions are exponential ease-outs, the body never overshoots. */
internal fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

internal fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - pow(-2f * t + 2f, 3) / 2f

internal fun easeOutQuint(t: Float): Float {
    val u = 1f - t
    return 1f - u * u * u * u * u
}

private fun pow(v: Float, n: Int): Float {
    var r = 1f
    repeat(n) { r *= v }
    return r
}

/**
 * 1-D periodic noise that loops seamlessly over [period]. Used for gaze drift and
 * the slow light tumble. Deterministic — same input, same output.
 */
internal fun loopNoise(t: Float, period: Float, seed: Float = 0f): Float {
    val p = (t / period) * TAU
    return 0.55f * sin(p + seed) +
        0.3f * sin(2f * p + seed * 1.7f + 1.1f) +
        0.15f * sin(3f * p + seed * 2.3f + 2.4f)
}

/** Deterministic PRNG (mulberry32): the same sequence on every read. */
internal fun createRng(seed: Int): () -> Float {
    var a = seed
    return {
        a += 0x6D2B79F5.toInt()
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}
