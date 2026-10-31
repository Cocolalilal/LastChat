package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Mark colours. One rule everywhere: what a swatch shows is what the mark renders.
 *
 *  - Body: the preset hex (or the custom hex) is the flat body fill, in both eye modes.
 *  - Grok eyes: the eye colour is the capsule fill.
 *  - Generical eyes: the eye colour fills the eye and its outline (the part that used
 *    to be fixed white); the soft fade toward the bottom of each eye is the body
 *    surface showing through, so it follows the body colour.
 */
object MarkColors {
    /** Body swatches, in picker order. */
    val PRESETS = linkedMapOf(
        "black" to "#000000",
        "brown" to "#A27952",
        "red" to "#FF3E51",
        "orange" to "#FF781C",
        "yellow" to "#FFAF38",
        "green" to "#00C972",
        "cyan" to "#1CC3B0",
        "generical" to me.rerere.rikkahub.data.model.Avatar.Animated.GENERICAL_BLUE_HEX,
        // Legacy azure stays available for avatars that explicitly chose it.
        "azure" to "#009FE0",
        "blue" to "#2A92FE",
        "indigo" to "#7C5CFF",
        "violet" to "#A97EFE",
        "magenta" to "#FF5EB1",
        "gray" to "#959595",
    )

    const val GENERICAL_BLUE_HEX = me.rerere.rikkahub.data.model.Avatar.Animated.GENERICAL_BLUE_HEX
    private const val LEGACY_AZURE_HEX = "#009FE0"
    const val DEFAULT_GENERICAL_BODY_HEX = me.rerere.rikkahub.data.model.Avatar.Animated.GENERICAL_LIGHT_BLUE_HEX
    const val DEFAULT_GENERICAL_BODY_PRESET = me.rerere.rikkahub.data.model.Avatar.Animated.GENERICAL_LIGHT_BLUE_PRESET

    /** Mode default eye colours (what `eyeColorHex == null` renders as). */
    const val DEFAULT_GENERICAL_EYE_HEX = "#FFFFFF"
    // Engine16.2c: both styles default to white eyes.
    const val DEFAULT_GROK_EYE_HEX = "#FFFFFF"

    /** Eye swatches shared by Grok + Generical (mode default is listed first by the editor). */
    val EYE_PRESETS = linkedMapOf(
        "white" to "#FFFFFF",
        "ink" to "#1A1A1A",
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
            Color(0xFF0078D7)
        }
    }

    fun colorToHex(color: Color): String {
        return String.format("#%06X", 0xFFFFFF and color.toArgb())
    }

    fun normalizeHex(hex: String): String {
        val trimmed = hex.trim()
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    /**
     * Saved body colour → (hex, preset) as the editor and renderer should see it.
     * Older builds stored the Generical blue as `preset = "cyan", hex = #009FE0`:
     * Generical mode rendered it blue while the selected swatch was teal (Grok mode
     * rendered it teal). Keep what each mode actually showed: in Generical mode that
     * pair is the "azure" swatch now; a real cyan pick (`#1CC3B0`) stays cyan.
     */
    fun canonicalBody(colorHex: String?, colorPreset: String?, eyeType: String = "generical"): Pair<String, String?> {
        val hex = colorHex?.takeIf { it.isNotBlank() }?.let { normalizeHex(it).uppercase() }
        val preset = colorPreset?.lowercase()?.takeIf { it in PRESETS }
        val generical = !eyeType.equals("grok", true)
        return when {
            generical && preset == "cyan" && (hex == null || hex == LEGACY_AZURE_HEX) -> LEGACY_AZURE_HEX to "azure"
            preset != null -> PRESETS.getValue(preset) to preset
            hex != null -> hex to PRESETS.entries.firstOrNull { it.value.equals(hex, true) }?.key
            else -> DEFAULT_GENERICAL_BODY_HEX to DEFAULT_GENERICAL_BODY_PRESET
        }
    }

    /** Flat body fill — identical to the swatch, in both eye modes. */
    fun bodyColor(colorHex: String?, colorPreset: String?, eyeType: String = "generical"): Color =
        hexToColor(canonicalBody(colorHex, colorPreset, eyeType).first)

    fun defaultEyeHex(eyeType: String): String =
        if (eyeType.equals("grok", true)) DEFAULT_GROK_EYE_HEX else DEFAULT_GENERICAL_EYE_HEX

    /** The colour the eyes are drawn with (custom pick or the mode default). */
    fun eyeColor(eyeType: String, eyeColorHex: String?, colorHex: String?, colorPreset: String?): Color {
        eyeColorHex?.takeIf { it.isNotBlank() }?.let { return hexToColor(it) }
        // Engine16.2c: white by default in both styles, whatever the body colour.
        return hexToColor(defaultEyeHex(eyeType))
    }

    /** Pastel of the body colour: light bouncing off the surface into the eye. */
    fun deriveGenericalEyeTint(baseColor: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
        hsl[1] = (hsl[1] * 0.55f + 0.15f).coerceIn(0.2f, 0.75f)
        hsl[2] = (hsl[2] * 0.35f + 0.72f).coerceIn(0.65f, 0.92f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    /**
     * Bottom shade of a Generical eye: the body surface itself bleeding into the
     * eye (the live body fill, so it follows colour changes). The eye colour fills
     * the main area above it.
     */
    /**
     * Eye outline colour: the eye colour, nudged just enough toward white/black to
     * keep a minimum contrast against the body (e.g. near-black eyes on a black body).
     */
    fun outlineOn(eye: Color, body: Color): Color {
        fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        val d = lum(eye) - lum(body)
        val need = 0.16f
        if (kotlin.math.abs(d) >= need) return eye
        val toLight = lum(body) < 0.5f
        val target = if (toLight) Color.White else Color.Black
        // Mix toward the target until the luminance gap reaches `need`.
        val lb = lum(body); val le = lum(eye); val lt = lum(target)
        val goal = if (toLight) lb + need else lb - need
        val t = ((goal - le) / (lt - le)).coerceIn(0f, 1f)
        return Color(
            red = eye.red + (target.red - eye.red) * t,
            green = eye.green + (target.green - eye.green) * t,
            blue = eye.blue + (target.blue - eye.blue) * t,
            alpha = eye.alpha,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun genericalEyeFade(body: Color, eye: Color, strength: Float = 1f): Color {
        // Engine14: matches Julian's Figma (white → #87D2E9 on #009FE0 ≈ 50% toward the surface).
        val t = (EYE_SHADE_MIX * strength).coerceIn(0f, 1f)
        return Color(
            red = eye.red + (body.red - eye.red) * t,
            green = eye.green + (body.green - eye.green) * t,
            blue = eye.blue + (body.blue - eye.blue) * t,
            alpha = 1f,
        )
    }

    const val EYE_SHADE_MIX = 0.5f

    fun presetHexOrNull(name: String?): String? =
        name?.lowercase()?.let { PRESETS[it] }

    fun shade(color: Color, mul: Float): Color {
        return Color(
            red = (color.red * mul).coerceIn(0f, 1f),
            green = (color.green * mul).coerceIn(0f, 1f),
            blue = (color.blue * mul).coerceIn(0f, 1f),
            alpha = color.alpha,
        )
    }

    /** Black body uses inverted eye fill (light theme → near-black slits). */
    fun grokEyeFill(colorPreset: String?, colorHex: String, darkTheme: Boolean = false): Color {
        val key = colorPreset?.lowercase()
            ?: PRESETS.entries.firstOrNull {
                it.value.equals(normalizeHex(colorHex), ignoreCase = true)
            }?.key
        return if (key == "black") {
            if (darkTheme) Color(0xFFF5F5F5) else Color(0xFF111111)
        } else {
            hexToColor(DEFAULT_GROK_EYE_HEX)
        }
    }

    /**
     * Colours of an animated avatar that could seed the character theme: the body,
     * then the eyes, each only when it is a real colour (not near-white, near-black
     * or grey) so the theme doesn't go flat.
     */
    fun themeCandidates(
        eyeType: String,
        colorHex: String?,
        colorPreset: String?,
        eyeColorHex: String?,
    ): List<Color> {
        val body = bodyColor(colorHex, colorPreset, eyeType)
        val eye = eyeColor(eyeType, eyeColorHex, colorHex, colorPreset)
        return listOf(body, eye).filter { isThemeFitting(it) }
    }

    fun isThemeFitting(c: Color): Boolean {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c.toArgb(), hsl)
        return hsl[1] >= 0.18f && hsl[2] in 0.14f..0.88f
    }
}
