package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Avatar {
    @Serializable
    data object Dummy : Avatar()

    @Serializable
    data class Emoji(val content: String) : Avatar()

    @Serializable
    data class Image(val url: String) : Avatar()

    @Serializable
    data class Resource(val id: Int) : Avatar()

    /**
     * Vector "blob" avatar: a coloured body shape plus one of two eye packs.
     *
     * The body engine is shared between packs; only the eye layer differs
     * ([BlobEyePack.Grok] = small slanted black slits sitting on the flat
     * coloured mark, [BlobEyePack.Generical] = white-bordered rounded glyphs
     * with a pale vertical gradient, morphing by path/pose only). Lifecycle
     * (idle/thinking/working/...) is derived at render time from chat activity
     * — it is NOT persisted here.
     *
     * All colours are `#RRGGBB` / `#AARRGGBB` hex. Knob fields are clamped by the
     * `clamped*` helpers before use so out-of-range persisted values can't break
     * the renderer.
     */
    @Serializable
    @SerialName("blob")
    data class Blob(
        val color: String = DEFAULT_GENERICAL_COLOR,
        val shape: BlobShape = BlobShape.Circle,
        val eyes: BlobEyePack = BlobEyePack.Generical,
        /** Eye colour. Generical uses this as the white stroke; Grok defaults dark. */
        val eyeColor: String = DEFAULT_EYE_COLOR,
        /** Subtle lighting-based volume (key light + rim). Never a 2-D spin. */
        val flat3d: Boolean = true,
        val glowEnabled: Boolean = false,
        val glowColor: String = DEFAULT_GENERICAL_GLOW,
        val glowStrength: Float = 0.45f,
        val eyeSize: Float = 1f,
        val eyeSpacing: Float = 1f,
        val eyeRoundness: Float = 1f,
        val lookAround: Float = 1f,
        val accentColor: String = DEFAULT_GENERICAL_ACCENT,
    ) : Avatar() {
        fun clampedEyeSize(): Float = eyeSize.coerceIn(0.6f, 1.5f)
        fun clampedEyeSpacing(): Float = eyeSpacing.coerceIn(0.65f, 1.4f)
        fun clampedEyeRoundness(): Float = eyeRoundness.coerceIn(0.1f, 1f)
        fun clampedLookAround(): Float = lookAround.coerceIn(0f, 1.5f)
        fun clampedGlowStrength(): Float = glowStrength.coerceIn(0f, 1f)

        companion object {
            const val DEFAULT_GENERICAL_COLOR = "#009FE0"
            const val DEFAULT_GENERICAL_GLOW = "#87D2E9"
            const val DEFAULT_GENERICAL_ACCENT = "#EAF7FF"
            const val DEFAULT_EYE_COLOR = "#FBFDFF"
            /** Mid coloured mark so Grok's black slits read (not a near-black body). */
            const val DEFAULT_GROK_COLOR = "#4A7DC7"
            const val DEFAULT_GROK_EYE_COLOR = "#171717"

            fun generical() = Blob()

            fun grok() = Blob(
                color = DEFAULT_GROK_COLOR,
                eyes = BlobEyePack.Grok,
                eyeColor = DEFAULT_GROK_EYE_COLOR,
                glowEnabled = false,
                eyeRoundness = 1f,
            )
        }
    }
}

@Serializable
enum class BlobShape {
    Circle,
    Pebble,
    Squircle,
    Capsule,
    RoundSquare,
    Hexagon,
    Triangle,
    Cloud,
    Droplet,
}

@Serializable
enum class BlobEyePack {
    Generical,
    Grok,
}
