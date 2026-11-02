package me.rerere.rikkahub.ui.components.avatar.animated

/**
 * Keeps a Happy-to-open change behind one full blink instead of exposing the
 * eye-shape morph. Shared by Grok transitions and Generical Happy-to-Neutral.
 * Expression names supplied here are already normalized.
 *
 * [request] returns true when the caller should keep its current expression. Once a
 * full blink can start (or an existing full blink can be reused), call [beginMask].
 * Feed the shared blink's closure and busy flag into [update]; its returned target
 * is committed with `ExpressionDirector.jump` behind the canonical closed-eye shape.
 *
 * The renderer must use the same canonical closed-eye shape for both expressions,
 * reaching it before [COMMIT_CLOSURE]. A Happy arc ordinarily ignores blink lids;
 * the normal lid lane alone cannot hide this transition.
 */
internal class HappyTransitionCoordinator {
    var pendingTarget: String? = null
        private set

    /** Remains true through reopening, including when an intent cancels or supersedes the swap. */
    var maskActive: Boolean = false
        private set

    val awaitingBlink: Boolean get() = pendingTarget != null && !maskActive

    /**
     * Return true to defer the ordinary morph; false means the caller can apply its
     * usual expression change now. A newer request replaces the pending target.
     * Urgent reactions do not wait for a blink, but an in-flight mask is allowed to
     * finish opening so its visual influence never disappears in a single frame.
     */
    fun request(
        weights: List<Pair<String, Float>>,
        currentTarget: String,
        requestedTarget: String,
        urgent: Boolean = false,
    ): Boolean {
        val happyWeight = if (weights.isEmpty()) {
            if (currentTarget == "Happy") 1f else 0f
        } else {
            weights.sumOf { (name, weight) -> if (name == "Happy") weight.toDouble() else 0.0 }.toFloat()
        }
        val needsMask = !urgent && happyWeight > VISIBLE_HAPPY && requestedTarget in OPEN_FACES
        pendingTarget = requestedTarget.takeIf { needsMask }
        return needsMask
    }

    /** A shared full blink has actually started; merely requesting one is insufficient. */
    fun beginMask(): Boolean {
        if (!awaitingBlink) return false
        maskActive = true
        return true
    }

    /**
     * Return the latest target once, only behind the fully closed canonical shape.
     * If a coarse frame misses full closure, retain the pending request for another
     * safe opportunity rather than falling back to an exposed geometric morph.
     */
    fun update(closed: Float, blinkBusy: Boolean): String? {
        if (!maskActive) return null
        val commit = pendingTarget.takeIf { blinkBusy && closed >= COMMIT_CLOSURE }
        if (commit != null) pendingTarget = null
        if (!blinkBusy) maskActive = false
        return commit
    }

    /** Cancel the deferred change without cutting away a mask that is already visible. */
    fun cancel() {
        pendingTarget = null
    }

    companion object {
        const val COMMIT_CLOSURE = 0.98f
        private const val VISIBLE_HAPPY = 0.02f
        private val OPEN_FACES = setOf(
            "Neutral", "Surprised", "Sad", "Worried", "Angry", "angered or concentrated",
            "confused", "confused left", "Bored", "pouty",
        )
    }
}
