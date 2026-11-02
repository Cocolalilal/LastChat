package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * An occasional small re-fixation inside one held look. This owns a destination,
 * not a displacement animation: the caller's existing gaze spring moves toward the
 * returned offset, preserving its position and velocity on every retarget. A subordinate
 * head follow can use the same destination through the caller's slower head spring.
 *
 * Feed the unmodified gaze target as [anchorX]/[anchorY]. A changed owner/anchor or
 * [allowed] = false clears the offset target; a temporarily unsettled spring merely
 * pauses the opportunity clock, so a refixation cannot cancel its own movement.
 * No head drift, sinusoid, automatic return or continuous per-frame target noise.
 */
internal class IntentionalRefixation(private val rng: Random) {
    data class Target(val x: Float, val y: Float)

    var sequence = 0
        private set

    private var anchorX = 0f
    private var anchorY = 0f
    private var hasAnchor = false
    private var active = false
    private var quietLeft = 0f
    private var target = ZERO

    fun update(
        dt: Float,
        anchorX: Float,
        anchorY: Float,
        settled: Boolean,
        allowed: Boolean,
    ): Target {
        val ax = anchorX.coerceIn(-1f, 1f)
        val ay = anchorY.coerceIn(-1f, 1f)
        if (!hasAnchor || hypot(ax - this.anchorX, ay - this.anchorY) > ANCHOR_CHANGE) {
            this.anchorX = ax
            this.anchorY = ay
            hasAnchor = true
            active = false
            target = ZERO
        }
        if (!allowed) {
            active = false
            target = ZERO
            return target
        }
        if (!active) {
            active = true
            quietLeft = nextGap()
        }
        if (!settled || dt <= 0f) return target
        quietLeft -= dt
        if (quietLeft > 0f) return target
        quietLeft = nextGap()
        // Quiet is an option too: no visible-life quota and no metronomic adjustment.
        if (rng.nextFloat() < SKIP_CHANCE) return target

        repeat(8) {
            val direction = rng.nextFloat() * 2f * PI.toFloat()
            val distance = MIN_TRAVEL + rng.nextFloat() * (MAX_TRAVEL - MIN_TRAVEL)
            var x = target.x + cos(direction) * distance
            var y = target.y + sin(direction) * distance * 0.75f
            val radius = hypot(x, y)
            if (radius > MAX_RADIUS) { x *= MAX_RADIUS / radius; y *= MAX_RADIUS / radius }
            x = x.coerceIn(-1f - ax, 1f - ax)
            y = y.coerceIn(-1f - ay, 1f - ay)
            // Clamping a point near an edge must not create a token twitch.
            if (hypot(x - target.x, y - target.y) >= MIN_TRAVEL) {
                target = Target(x, y)
                sequence++
                return target
            }
        }
        return target
    }

    private fun nextGap() = MIN_QUIET_SEC + rng.nextFloat() * (MAX_QUIET_SEC - MIN_QUIET_SEC)

    companion object {
        const val MIN_QUIET_SEC = 5f
        const val MAX_QUIET_SEC = 10f
        const val MIN_TRAVEL = 0.05f
        const val MAX_TRAVEL = 0.10f
        const val MAX_RADIUS = 0.12f
        private const val ANCHOR_CHANGE = 0.06f
        private const val SKIP_CHANCE = 0.18f
        private val ZERO = Target(0f, 0f)
    }
}
