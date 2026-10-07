package me.rerere.rikkahub.ui.components.avatar.animated

/**
 * Pure lifecycle / expression mapping for animated mark avatars.
 * Kept free of Compose so unit tests can exercise it on the JVM.
 */
enum class MarkLifecycle {
    Idle,
    Listening,
    Thinking,
    Streaming,
    Working,
    Finished,
    Failed,
    Sleeping,
}

data class AvatarMotionHints(
    val isGenerating: Boolean = false,
    val hasStreamedTokens: Boolean = false,
    val isUsingTools: Boolean = false,
    val inputFocused: Boolean = false,
    val isTyping: Boolean = false,
    val hasError: Boolean = false,
    val idleMs: Long = 0L,
)

/**
 * Per-state pose knobs (APK / avatar-engine.js language).
 * lid = lidOpen base; eyeGap / eyeScale affect Grok slit seating.
 * Scale stays ~1 across states so clip switches never jump size
 * (desktop zoom is normalized separately against vn=0.6).
 */
data class MarkPose(
    val scale: Float = 1f,
    val tiltDeg: Float = 0f,
    val bright: Float = 1f,
    val lid: Float = 1f,
    val eyeGap: Float = 1f,
    val eyeScale: Float = 1f,
    val swayAmp: Float = 1f,
    val swayFreq: Float = 1f,
    val blinkMinMs: Long = 1800,
    val blinkMaxMs: Long = 3600,
    /** wander | focus | down | downIn | center — never upRight (Grok brand stare). */
    val gazeMode: String = "wander",
)

object MarkLifecycleMapper {
    private const val SLEEP_AFTER_MS = 90_000L

    fun resolve(hints: AvatarMotionHints): MarkLifecycle {
        return when {
            hints.hasError -> MarkLifecycle.Failed
            hints.isUsingTools -> MarkLifecycle.Working
            hints.isGenerating && hints.hasStreamedTokens -> MarkLifecycle.Streaming
            hints.isGenerating -> MarkLifecycle.Thinking
            hints.isTyping || hints.inputFocused -> MarkLifecycle.Listening
            hints.idleMs >= SLEEP_AFTER_MS -> MarkLifecycle.Sleeping
            else -> MarkLifecycle.Idle
        }
    }

    /**
     * Generical expression ids match generical-eyes.json keys
     * (Neutral, Happy, Sleeping, …).
     */
    fun defaultExpression(lifecycle: MarkLifecycle): String = when (lifecycle) {
        MarkLifecycle.Idle -> "Neutral"
        MarkLifecycle.Listening -> "Neutral"
        MarkLifecycle.Thinking -> "angered or concentrated"
        MarkLifecycle.Streaming -> "Happy"
        MarkLifecycle.Working -> "angered or concentrated"
        MarkLifecycle.Finished -> "Happy"
        MarkLifecycle.Failed -> "Worried"
        MarkLifecycle.Sleeping -> "Sleeping"
    }

    fun pose(lifecycle: MarkLifecycle): MarkPose = when (lifecycle) {
        MarkLifecycle.Thinking -> MarkPose(
            scale = 1f, tiltDeg = -2f, bright = 1.04f,
            lid = 1.02f, eyeGap = 0.96f, eyeScale = 1.02f,
            swayAmp = 0.55f, swayFreq = 0.65f,
            blinkMinMs = 2600, blinkMaxMs = 4600,
            gazeMode = "wander",
        )
        MarkLifecycle.Working -> MarkPose(
            scale = 1f, tiltDeg = 0.5f, bright = 1.06f,
            lid = 1.04f, eyeGap = 0.94f, eyeScale = 1.03f,
            swayAmp = 0.4f, swayFreq = 1.5f,
            blinkMinMs = 1200, blinkMaxMs = 2200,
            gazeMode = "focus",
        )
        MarkLifecycle.Streaming -> MarkPose(
            scale = 1f, tiltDeg = 0f, bright = 1.05f,
            lid = 1.0f, eyeGap = 0.96f, eyeScale = 1.02f,
            swayAmp = 0.55f, swayFreq = 1.1f,
            blinkMinMs = 1600, blinkMaxMs = 3200,
            gazeMode = "wander",
        )
        MarkLifecycle.Listening -> MarkPose(
            scale = 1f, tiltDeg = 1f, bright = 1.0f,
            lid = 1.0f, eyeGap = 0.95f, eyeScale = 1.0f,
            swayAmp = 0.35f, swayFreq = 0.7f,
            blinkMinMs = 2200, blinkMaxMs = 4200,
            gazeMode = "down",
        )
        MarkLifecycle.Finished -> MarkPose(
            scale = 1f, tiltDeg = -0.5f, bright = 1.08f,
            lid = 1.0f, eyeGap = 0.98f, eyeScale = 1.04f,
            swayAmp = 0.65f, swayFreq = 1.1f,
            blinkMinMs = 2400, blinkMaxMs = 4200,
            gazeMode = "center",
        )
        MarkLifecycle.Failed -> MarkPose(
            scale = 1f, tiltDeg = 4f, bright = 0.78f,
            lid = 0.5f, eyeGap = 0.85f, eyeScale = 0.95f,
            swayAmp = 0.3f, swayFreq = 0.85f,
            blinkMinMs = 2000, blinkMaxMs = 3600,
            gazeMode = "downIn",
        )
        MarkLifecycle.Sleeping -> MarkPose(
            scale = 1f, tiltDeg = 2f, bright = 0.92f,
            lid = 0.22f, eyeGap = 0.95f, eyeScale = 0.95f,
            swayAmp = 0.2f, swayFreq = 0.4f,
            blinkMinMs = 6000, blinkMaxMs = 10000,
            gazeMode = "down",
        )
        MarkLifecycle.Idle -> MarkPose(
            eyeGap = 0.94f,
            gazeMode = "wander",
        )
    }

    fun shouldGlanceAtInput(isTyping: Boolean, random01: Float): Boolean {
        if (!isTyping) return false
        return random01 < 0.22f
    }
}
