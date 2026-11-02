package me.rerere.rikkahub.ui.components.avatar.animated

/** One attention/lean phrase. Intermediate poses are passed through, never latched. */
internal class LeanPhrase(val knots: List<Knot>) {
    data class Knot(val t: Float, val roll: Float, val yaw: Float, val gazeX: Float, val gazeY: Float)
    val duration = knots.last().t

    fun sample(time: Float): Knot {
        if (time <= 0f) return knots.first()
        if (time >= duration) return knots.last()
        val i = knots.indexOfLast { it.t <= time }.coerceAtMost(knots.lastIndex - 1)
        val a = knots[i]; val b = knots[i + 1]
        val span = b.t - a.t
        val u = (time - a.t) / span
        fun value(get: (Knot) -> Float): Float {
            fun tangent(j: Int): Float {
                if (j == 0 || j == knots.lastIndex) return 0f
                return (get(knots[j + 1]) - get(knots[j - 1])) / (knots[j + 1].t - knots[j - 1].t)
            }
            return motionHermite(u, get(a), get(b), tangent(i) * span, tangent(i + 1) * span)
        }
        return Knot(time, value { it.roll }, value { it.yaw }, value { it.gazeX }, value { it.gazeY })
    }
}

/** A purposeful look: prompt arrival, a quiet focus, then a gentle release. */
internal class FocusedLook(
    private val start: LeanPhrase.Knot,
    private val goal: LeanPhrase.Knot,
    val duration: Float,
    val attack: Float = 0.24f,
    val release: Float = 0.65f,
) {
    fun channel(from: Float, to: Float, home: Float, time: Float): Float = when {
        time <= 0f -> from
        time < attack -> from + (to - from) * MotionDirector.smootherstep(time / attack)
        time <= duration - release -> to
        time < duration -> to + (home - to) * MotionDirector.smootherstep((time - duration + release) / release)
        else -> home
    }

    fun sample(time: Float) = LeanPhrase.Knot(time,
        channel(start.roll, goal.roll, 0f, time), channel(start.yaw, goal.yaw, 0f, time),
        channel(start.gazeX, goal.gazeX, 0f, time), channel(start.gazeY, goal.gazeY, 0.02f, time))
}
