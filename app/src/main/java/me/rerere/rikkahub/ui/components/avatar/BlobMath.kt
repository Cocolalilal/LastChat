package me.rerere.rikkahub.ui.components.avatar

internal const val TAU = (Math.PI * 2).toFloat()

internal fun clamp(v: Float, lo: Float = 0f, hi: Float = 1f): Float {
    return if (v < lo) lo else if (v > hi) hi else v
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun easeOutQuint(t: Float): Float {
    val u = 1f - t
    return 1f - u * u * u * u * u
}

internal fun deg(d: Float): Float = (d * Math.PI / 180.0).toFloat()

/**
 * Seamless 1D noise that loops on [period]. Amplitude is bounded to ±1.
 */
internal fun loopNoise(t: Float, period: Float, seed: Float = 0f): Float {
    val p = (t / period) * TAU
    return 0.55f * kotlin.math.sin(p + seed) +
        0.3f * kotlin.math.sin(2f * p + seed * 1.7f + 1.1f) +
        0.15f * kotlin.math.sin(3f * p + seed * 2.3f + 2.4f)
}

/**
 * mulberry32 — same 32-bit wrapping as the Grok Bot recreation (bloub).
 */
internal fun createRng(seed: Int): () -> Float {
    var a = seed
    return {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (a or 1)
        t = (t + ((t xor (t ushr 7)) * (t or 61))) xor t
        (t xor (t ushr 14)).toUInt().toFloat() / 4294967296f
    }
}
