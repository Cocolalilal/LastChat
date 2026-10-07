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
     * Procedural soft 3D mark avatar (Grok / Generical eyes).
     * Not circular-cropped; floating shape rendered by Compose Canvas.
     */
    @Serializable
    @SerialName("animated")
    data class Animated(
        val shape: String = "blob",
        /** "grok" or "generical" */
        val eyeType: String = "generical",
        val colorHex: String = "#009FE0",
        val colorPreset: String? = "cyan",
        /**
         * Optional eye fill override (hex). Null = mode default
         * (Grok dark cutout / Generical derived cyan tint).
         */
        val eyeColorHex: String? = null,
    ) : Avatar() {
        companion object {
            fun defaultGenerical(
                shape: String = "blob",
            ) = Animated(
                shape = shape,
                eyeType = "generical",
                colorHex = "#009FE0",
                colorPreset = "cyan",
                eyeColorHex = null,
            )
        }
    }
}
