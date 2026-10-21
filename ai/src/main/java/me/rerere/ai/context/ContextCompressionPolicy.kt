package me.rerere.ai.context

import kotlinx.serialization.Serializable

@Serializable
enum class ContextCompressionStyle {
    MORE_DETAIL, BALANCED, MORE_ROOM;

    val summaryFraction: Float get() = when (this) {
        MORE_DETAIL -> 0.4f
        BALANCED -> 0.25f
        MORE_ROOM -> 0.18f
    }
}

data class ContextCompressionPolicy(
    val style: ContextCompressionStyle = ContextCompressionStyle.BALANCED,
    val recentTurns: Int? = null,
) {
    fun protectedTurns(archetype: ContextScaleArchetype): Int = recentTurns?.coerceIn(2, 24) ?: when (archetype) {
        ContextScaleArchetype.MICRO -> 2
        ContextScaleArchetype.COMPACT -> 4
        ContextScaleArchetype.VAST -> 6
    }

    fun triggerFraction(base: Float): Float = (base + when (style) {
        ContextCompressionStyle.MORE_DETAIL -> 0.06f
        ContextCompressionStyle.BALANCED -> 0f
        ContextCompressionStyle.MORE_ROOM -> -0.05f
    }).coerceIn(0.6f, 0.92f)

    fun targetFraction(base: Float): Float = (base + when (style) {
        ContextCompressionStyle.MORE_DETAIL -> 0.08f
        ContextCompressionStyle.BALANCED -> 0f
        ContextCompressionStyle.MORE_ROOM -> -0.1f
    }).coerceIn(0.45f, 0.80f)
}
