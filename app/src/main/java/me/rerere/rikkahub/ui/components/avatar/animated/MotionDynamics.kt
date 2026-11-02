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

/** One continuous wind-up, turn, body settle and delayed eye follow-through. Angles stay unwrapped. */
internal class SpinTrajectory(
    val windup: Float,
    val turn: Float,
    val windupDegrees: Float,
    val crouch: Float,
    val overshoot: Float,
    val follow: Float,
    val followOut: Float,
    val followBack: Float,
) {
    val turnEnd = windup + turn
    val duration = turnEnd + maxOf(0.9f, followOut + followBack)
    var angle = 0f; private set
    var compression = 0f; private set
    var eyeLead = 0f; private set

    fun sample(time: Float) {
        val t = time.coerceAtLeast(0f)
        val u = ((t - windup) / turn).coerceIn(0f, 1f)
        angle = when {
            t < windup -> -windupDegrees * MotionDirector.smootherstep(t / windup)
            t < turnEnd -> {
                // Integral of v(u) ∝ u²(1-u)³: zero velocity/acceleration at both ends.
                val c = u * u * u * (20f + u * (-45f + u * (36f - 10f * u)))
                -windupDegrees + (360f + overshoot + windupDegrees) * c
            }
            else -> {
                val s = t - turnEnd
                val tail = 1f - MotionDirector.smootherstep((s - 0.45f) / 0.45f)
                360f + overshoot * (1f + 10f * s) * exp(-10f * s) * tail
            }
        }
        val launch = t - windup
        compression = when {
            t < windup -> crouch * MotionDirector.smootherstep(t / windup)
            launch < 0.18f -> crouch * motionHermite(launch / 0.18f, 1f, -0.6f)
            launch < 0.48f -> -0.6f * crouch * (1f - MotionDirector.smootherstep((launch - 0.18f) / 0.3f))
            else -> 0f
        }
        val pre = 0.65f
        val q = ((u - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val endSpeed = pre * 0.5f / (0.55f * turn)
        val ft = t - turnEnd
        eyeLead = when {
            t < windup -> -0.025f * MotionDirector.smootherstep(t / windup)
            t < turnEnd -> -0.025f * (1f - MotionDirector.smootherstep(u / 0.3f)) +
                follow * motionHermite(q, 0f, pre, v1 = 0.5f * pre)
            ft < followOut -> follow * motionHermite(ft / followOut, pre, 1f, v0 = endSpeed * followOut)
            ft < followOut + followBack -> follow * (1f - MotionDirector.smootherstep((ft - followOut) / followBack))
            else -> 0f
        }
    }
}
