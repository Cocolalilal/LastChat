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
        val glowEnabled: Boolean = true,
        val glowColor: String = DEFAULT_GENERICAL_GLOW,
    ) : Avatar() {
        companion object {
            const val DEFAULT_GENERICAL_COLOR = "#009FE0"
            const val DEFAULT_GENERICAL_GLOW = "#87D2E9"
            const val DEFAULT_GROK_COLOR = "#0A0A0C"

            fun generical() = Blob()

            fun grok() = Blob(
                color = DEFAULT_GROK_COLOR,
                eyes = BlobEyePack.Grok,
                glowEnabled = false,
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
}

@Serializable
enum class BlobEyePack {
    Generical,
    Grok,
}
