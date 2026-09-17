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
     * Vector blob avatar: a coloured body shape plus an eye pack.
     * Lifecycle (idle/thinking/working/waiting/blocked/done) is carried on the
     * face at render time — it is not persisted.
     */
    @Serializable
    @SerialName("blob")
    data class Blob(
        val color: String = DEFAULT_GENERICAL_COLOR,
        val shape: BlobShape = BlobShape.Circle,
        val eyes: BlobEyePack = BlobEyePack.Generical,
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
        fun clampedEyeRoundness(): Float = eyeRoundness.coerceIn(0.15f, 1f)
        fun clampedLookAround(): Float = lookAround.coerceIn(0f, 1.5f)
        fun clampedGlowStrength(): Float = glowStrength.coerceIn(0f, 1f)

        companion object {
            const val DEFAULT_GENERICAL_COLOR = "#009FE0"
            const val DEFAULT_GENERICAL_GLOW = "#87D2E9"
            const val DEFAULT_GENERICAL_ACCENT = "#E8F7FF"
            const val DEFAULT_GROK_COLOR = "#0A0A0C"

            fun generical() = Blob()

            fun grok() = Blob(
                color = DEFAULT_GROK_COLOR,
                eyes = BlobEyePack.Grok,
                glowEnabled = false,
                lookAround = 1f,
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
    SoftHex,
    Triangle,
    Cloud,
    Droplet,
    Pill,
    Cookie,
    Arch,
}

@Serializable
enum class BlobEyePack {
    Generical,
    Grok,
}
