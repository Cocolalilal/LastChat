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
