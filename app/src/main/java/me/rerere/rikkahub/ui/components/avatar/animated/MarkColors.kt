package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

object MarkColors {
    /**
     * Persona fills from babygrok-geometry / mark-export colors.json (light theme).
     * Generical default body stays #009FE0 (designer pick), separate from Grok cyan #1CC3B0.
     */
    val PRESETS = linkedMapOf(
        "black" to "#000000",
        "brown" to "#A27952",
        "red" to "#FF3E51",
        "orange" to "#FF781C",
        "yellow" to "#FFAF38",
        "green" to "#00C972",
        "cyan" to "#1CC3B0",
        "blue" to "#2A92FE",
        "violet" to "#A97EFE",
        "magenta" to "#FF5EB1",
        "gray" to "#959595",
    )

    const val DEFAULT_GENERICAL_HEX = "#009FE0"
    const val DEFAULT_GENERICAL_EYE_TINT = "#87D2E9"

    /** Shared eye-color swatches for Grok + Generical customization. */
    val EYE_PRESETS = linkedMapOf(
        "default" to "",  // mode default
        "ink" to "#1A1A1A",
        "white" to "#F5F5F5",
        "sky" to "#87D2E9",
        "mint" to "#7EE0C8",
        "gold" to "#FFD27A",
        "rose" to "#FF8BB5",
        "lilac" to "#C4A8FF",
        "coral" to "#FF7A6E",
    )

    fun hexToColor(hex: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(normalizeHex(hex)))
        } catch (_: Exception) {
            Color(0xFF009FE0)
        }
    }

    fun colorToHex(color: Color): String {
        return String.format("#%06X", 0xFFFFFF and color.toArgb())
    }

    fun normalizeHex(hex: String): String {
        val trimmed = hex.trim()
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    fun deriveGenericalEyeTint(baseColor: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
        hsl[1] = (hsl[1] * 0.55f + 0.15f).coerceIn(0.2f, 0.75f)
        hsl[2] = (hsl[2] * 0.35f + 0.72f).coerceIn(0.65f, 0.92f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun presetHexOrNull(name: String?): String? =
        name?.lowercase()?.let { PRESETS[it] }

    /** Resolve fill: prefer named persona gradient light fill when preset matches. */
    fun resolveFill(colorHex: String, colorPreset: String?): Color {
        val fromPreset = colorPreset?.lowercase()?.let { key ->
            if (AvatarPackData.isLoaded()) {
                AvatarPackData.grok.gradients[key]?.light ?: PRESETS[key]
            } else {
                PRESETS[key]
            }
        }
        return hexToColor(fromPreset ?: colorHex)
    }

    fun shade(color: Color, mul: Float): Color {
        return Color(
            red = (color.red * mul).coerceIn(0f, 1f),
            green = (color.green * mul).coerceIn(0f, 1f),
            blue = (color.blue * mul).coerceIn(0f, 1f),
            alpha = color.alpha,
        )
    }

    /** Black persona uses inverted eye fill (light theme → near-black slits). */
    fun grokEyeFill(colorPreset: String?, colorHex: String, darkTheme: Boolean = false): Color {
        val key = colorPreset?.lowercase()
            ?: PRESETS.entries.firstOrNull {
                it.value.equals(normalizeHex(colorHex), ignoreCase = true)
            }?.key
        return if (key == "black") {
            if (darkTheme) Color(0xFFF5F5F5) else Color(0xFF111111)
        } else {
            Color(0xFF1A1A1A)
        }
    }
}
