package me.rerere.rikkahub.ui.components.avatar

/**
 * Pure `#RGB` / `#RRGGBB` / `#AARRGGBB` → ARGB int parser, so the geometry engine
 * and the head-less AWT renderer never touch `android.graphics.Color`.
 */
internal fun parseArgb(hex: String, fallback: Int = 0xFF009FE0.toInt()): Int {
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

internal fun argbWithAlpha(color: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    return (a shl 24) or (color and 0x00FFFFFF)
}

internal fun luminanceOf(color: Int): Float {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
