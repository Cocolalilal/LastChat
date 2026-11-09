package me.rerere.rikkahub.ui.components.avatar.animated

/**
 * Fixed style temperament for the original procedural character engine.
 * These internal values drive its timing, gaze habits and reactions; they are
 * not exposed as settings, stored overrides or model outputs.
 */
data class Personality(
    /** Pace and liveliness: how often and how briskly it moves. */
    val energy: Float = 0.5f,
    /** Friendliness and social interest; low = reserved / shy. */
    val warmth: Float = 0.5f,
    /** Playful / mischievous streak: winks, hops, hobbies, easter eggs. */
    val playfulness: Float = 0.5f,
    /** How strongly feelings show on the face. */
    val expressiveness: Float = 0.5f,
) {
    fun clamped() = Personality(c(energy), c(warmth), c(playfulness), c(expressiveness))

    // ---- derived traits (internal mapping; not set directly) ----
    val curiosity: Float get() = c(0.25f + 0.35f * energy + 0.25f * playfulness + 0.15f * warmth)
    val shyness: Float get() = c(0.75f - 0.55f * warmth - 0.25f * expressiveness + 0.1f * (1f - energy))
    val attentiveness: Float get() = c(0.3f + 0.45f * warmth + 0.25f * (1f - playfulness) * 0.8f)
    val mischief: Float get() = c(0.1f + 0.7f * playfulness - 0.15f * warmth + 0.15f)
    val calmness: Float get() = c(0.95f - 0.7f * energy - 0.15f * playfulness)

    /** Beat frequency multiplier (higher = more frequent). */
    val tempo: Float get() = 0.6f + 0.8f * energy - 0.3f * (calmness - 0.5f)
    /** Expression strength / how readily non-neutral faces appear. */
    val showiness: Float get() = 0.5f + expressiveness
    /** Blink rate multiplier. */
    val blinkRate: Float get() = 0.8f + 0.4f * energy + 0.3f * shyness - 0.2f * calmness

    private fun c(v: Float) = v.coerceIn(0f, 1f)

    companion object {
        /** Generical: warm, curious, a little playful. */
        val GENERICAL = Personality(energy = 0.6f, warmth = 0.8f, playfulness = 0.55f, expressiveness = 0.65f)
        /** Grok-eyed marks: composed, drier, more mischievous. */
        val GROK = Personality(energy = 0.5f, warmth = 0.5f, playfulness = 0.65f, expressiveness = 0.45f)

        fun defaultFor(eyeType: String): Personality =
            if (eyeType.equals("grok", ignoreCase = true)) GROK else GENERICAL


    }
}

/**
 * Per-message tone hint (from a classifier/decision model later). Valence −1…1,
 * arousal 0…1, plus an optional named emotion that briefly colours the face.
 */
data class ToneHint(
    val valence: Float,
    val arousal: Float,
    val emotion: Emotion? = null,
    val intensity: Float = 0.6f,
)

enum class Emotion { Joy, Amusement, Interest, Surprise, Concern, Sadness, Confusion, Pride, Calm }
