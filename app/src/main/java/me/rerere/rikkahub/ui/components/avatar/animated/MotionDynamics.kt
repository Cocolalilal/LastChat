package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** A damped oscillator. Retargeting changes force, never position or velocity. */
internal class DampedMotion(var value: Float = 0f, var velocity: Float = 0f) {
    /** Exact step for a constant target; behaves identically at 30, 60 and 120 Hz. */
    fun advance(target: Float, dt: Float, omega: Float, damping: Float = 1f) {
        if (dt <= 0f) return
        val x = value - target
        val decay = exp(-damping * omega * dt)
        if (damping >= 0.9999f) {
            val c = velocity + omega * x
            value = target + (x + c * dt) * decay
            velocity = (velocity - omega * c * dt) * decay
        } else {
            val wd = omega * sqrt(1f - damping * damping)
            val c = cos(wd * dt)
            val s = sin(wd * dt) / wd
            value = target + decay * (x * c + (velocity + damping * omega * x) * s)
            velocity = decay * (velocity * c - (damping * omega * velocity + omega * omega * x) * s)
        }
    }
}

/** Hermite segment with endpoint velocities expressed per unit of normalized time. */
internal fun motionHermite(u: Float, from: Float, to: Float, v0: Float = 0f, v1: Float = 0f): Float {
    val t = u.coerceIn(0f, 1f)
    val t2 = t * t
    val t3 = t2 * t
    return (2f * t3 - 3f * t2 + 1f) * from + (t3 - 2f * t2 + t) * v0 +
        (-2f * t3 + 3f * t2) * to + (t3 - t2) * v1
}

/** Full-turn rebasing is invisible; eye constraints depend on angular distance from the front. */
internal fun spinSafetyWeight(yaw: Float, pitch: Float, roll: Float): Float {
    val away = maxOf(abs(MarkClipEngine.wrap180(yaw)), abs(MarkClipEngine.wrap180(pitch)),
        abs(MarkClipEngine.wrap180(roll)))
    return 1f - MotionDirector.smootherstep(away / 35f)
}

/** Angular momentum and eye inertia, rather than a sequence of eased keyframes. */
internal class SpinTrajectory(
    val windup: Float,
    val turn: Float,
    val windupDegrees: Float,
    val overshoot: Float,
    val follow: Float,
) {
    private val damping = run {
        val l = kotlin.math.ln((overshoot / 360f).coerceIn(0.005f, 0.06f))
        -l / sqrt(l * l + Math.PI.toFloat() * Math.PI.toFloat())
    }
    private val omega = 5.7f / turn
    val duration = windup + 11f / (damping * omega)
    private val rotation = DampedMotion()
    private val eyes = DampedMotion()
    private var clock = 0f
    val angle get() = rotation.value
    val velocity get() = rotation.velocity
    val eyeLead get() = eyes.value

    fun sample(time: Float) {
        val end = time.coerceIn(0f, duration)
        if (end < clock) { rotation.value = 0f; rotation.velocity = 0f; eyes.value = 0f; eyes.velocity = 0f; clock = 0f }
        while (clock < end - 1e-7f) {
            val h = minOf(1f / 240f, end - clock, if (clock < windup) windup - clock else end - clock)
            val preparing = clock < windup
            val w = if (preparing) 9f else omega
            val z = if (preparing) 0.8f else damping
            val target = if (preparing) -windupDegrees else 360f
            val acceleration = w * w * (target - rotation.value) - 2f * z * w * rotation.velocity
            rotation.advance(target, h, w, z)
            // Acceleration pulls the eyes behind; braking lets them continue ahead.
            // This same spring carries through the stop, without a separate follow-through cue.
            val leadTarget = (-acceleration * 0.00010f).coerceIn(-follow, follow)
            eyes.advance(leadTarget, h, 6.5f, 0.76f)
            clock += h
        }
        if (time >= duration) { rotation.value = 360f; rotation.velocity = 0f; eyes.value = 0f; eyes.velocity = 0f }
    }
}
