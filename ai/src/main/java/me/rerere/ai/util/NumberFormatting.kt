package me.rerere.ai.util

import kotlin.math.abs
import kotlin.math.round

internal fun formatFixed2(value: Float): String = formatFixed2(value.toDouble())

internal fun formatFixed2(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val scaled = round(abs(value) * 100.0).toLong()
    val sign = if (value < 0.0) "-" else ""
    val whole = scaled / 100
    val fraction = (scaled % 100).toString().padStart(2, '0')
    return "$sign$whole.$fraction"
}
