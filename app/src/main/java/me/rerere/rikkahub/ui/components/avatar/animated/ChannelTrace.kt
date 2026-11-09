package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Engine19: per-frame channel trace — what every channel shows, how fast it moves, who owns
 * the face and why. Pure Kotlin (no Android types), so the same recorder and metrics run on
 * any engine version that produces [MotionFrame]s (baselines are taken from older engines
 * with legacy pair fields only; owner / cause / blink columns are then empty).
 */
class TraceRow(
    val t: Float,
    val pitch: Float, val yaw: Float, val roll: Float, val posY: Float,
    val headX: Float, val headY: Float, val leadX: Float, val leadY: Float,
    val lid: Float,
    /** Face weight vector (Σ = 1). */
    val weights: Map<String, Float>,
    val target: String,
    val spin: Float,
    val owner: String = "",
    val ownerPrio: Int = -1,
    val cause: String = "",
    val accent: String = "",
    val saccade: Boolean = false,
    /** Body: current base beat + active additive gestures (debug). */
    val beat: String = "",
)

class ChannelTrace {
    val rows = ArrayList<TraceRow>()
    val pokes = ArrayList<Float>()
    /** Blink starts: (time, second half of a double, other-lid-closer openness 0…1). */
    val blinks = ArrayList<Triple<Float, Boolean, Float>>()
    /** Face target changes: (time, target, cause, priority). */
    val targets = ArrayList<Triple<Float, String, String>>()
    val targetPrio = ArrayList<Int>()

    fun poke(t: Float) { pokes += t }
    fun blink(t: Float, chained: Boolean, lidHeld: Float) { blinks += Triple(t, chained, lidHeld) }
    fun add(r: TraceRow) {
        val last = rows.lastOrNull()
        if (last == null || last.target != r.target) { targets += Triple(r.t, r.target, r.cause); targetPrio += r.ownerPrio }
        rows += r
    }

    fun csv(): String = buildString {
        appendLine("t,pitch,yaw,roll,posY,headX,headY,leadX,leadY,lid,wNeutral,dom,wDom,target,spin,owner,prio,cause,accent,sacc,ev,beat")
        var pi = 0; var bi = 0
        for (r in rows) {
            val dom = r.weights.maxByOrNull { it.value }
            var ev = ""
            while (pi < pokes.size && pokes[pi] <= r.t + 1e-4f) { ev += "P"; pi++ }
            while (bi < blinks.size && blinks[bi].first <= r.t + 1e-4f) { ev += if (blinks[bi].second) "b" else if (blinks[bi].third < 0.98f) "X" else "B"; bi++ }
            appendLine(listOf(
                "%.4f".format(r.t), "%.3f".format(r.pitch), "%.3f".format(r.yaw), "%.3f".format(r.roll), "%.4f".format(r.posY),
                "%.4f".format(r.headX), "%.4f".format(r.headY), "%.4f".format(r.leadX), "%.4f".format(r.leadY), "%.3f".format(r.lid),
                "%.4f".format(r.weights["Neutral"] ?: 0f), dom?.key ?: "", "%.4f".format(dom?.value ?: 0f), r.target, "%.2f".format(r.spin),
                r.owner, r.ownerPrio.toString(), r.cause.replace(',', ';'), r.accent, if (r.saccade) "1" else "0", ev, r.beat,
            ).joinToString(","))
        }
    }

    companion object {
        /** Legacy (pair / top layer) frame → weight vector. */
        fun legacyWeights(f: MotionFrame): Map<String, Float> {
            f.exprWeights?.let { ws -> return ws.toMap() }
            val m = HashMap<String, Float>()
            val top = f.exprTop
            val tw = if (top != null) f.exprTopT.coerceIn(0f, 1f) else 0f
            val t = f.exprT.coerceIn(0f, 1f)
            m.merge(f.exprFrom, (1f - t) * (1f - tw), Float::plus)
            m.merge(f.exprTo, t * (1f - tw), Float::plus)
            if (top != null) m.merge(top, tw, Float::plus)
            return m
        }

        fun fromFrame(t: Float, f: MotionFrame, target: String? = null): TraceRow = TraceRow(
            t = t, pitch = f.body.pitch, yaw = f.body.yaw, roll = f.body.roll, posY = f.body.posY,
            headX = f.headLookX, headY = f.headLookY, leadX = f.leadX, leadY = f.leadY, lid = f.lid,
            weights = legacyWeights(f), target = target ?: (f.exprTop ?: f.exprTo), spin = f.spinYaw,
        )
    }
}

/** Engine19 §7 metrics over a [ChannelTrace]. */
object ChannelMetrics {
    class Result(val values: LinkedHashMap<String, Float>) {
        operator fun get(k: String) = values[k] ?: Float.NaN
        override fun toString() = values.entries.joinToString(" ") { "%s=%.3f".format(it.key, it.value) }
    }

    private fun wrap(d: Float): Float { var x = d; while (x > 180f) x -= 360f; while (x < -180f) x += 360f; return x }

    /** Visible turn: body yaw plus the head-look turn (as the renderer composes it). */
    fun visYaw(r: TraceRow) = r.yaw + 18f * r.headX

    fun exprSpeed(a: TraceRow, b: TraceRow): Float {
        val dt = (b.t - a.t).coerceAtLeast(1e-4f)
        var s = 0f
        for (k in a.weights.keys + b.weights.keys) s += abs((b.weights[k] ?: 0f) - (a.weights[k] ?: 0f))
        return s * 0.5f / dt
    }

    /** Series of a channel's speed (per frame, aligned to rows[1..]). */
    private fun speeds(rows: List<TraceRow>, ch: String): FloatArray = FloatArray(max(0, rows.size - 1)) { i ->
        val a = rows[i]; val b = rows[i + 1]; val dt = (b.t - a.t).coerceAtLeast(1e-4f)
        when (ch) {
            "expr" -> exprSpeed(a, b)
            "yaw" -> abs(wrap(b.yaw - a.yaw)) / dt
            "pitch" -> abs(wrap(b.pitch - a.pitch)) / dt
            "head" -> kotlin.math.hypot(b.headX - a.headX, b.headY - a.headY) / dt
            "gaze" -> kotlin.math.hypot(b.leadX - a.leadX, b.leadY - a.leadY) / dt
            else -> 0f
        }
    }

    private fun signed(rows: List<TraceRow>, ch: String): FloatArray = FloatArray(max(0, rows.size - 1)) { i ->
        val a = rows[i]; val b = rows[i + 1]; val dt = (b.t - a.t).coerceAtLeast(1e-4f)
        when (ch) {
            "yaw" -> wrap(b.yaw - a.yaw) / dt
            "pitch" -> wrap(b.pitch - a.pitch) / dt
            "head" -> (b.headX - a.headX) / dt
            else -> 0f
        }
    }

    /** Moving (> hi) → stopped (< lo) → moving again within 0.4 s. Spins excluded. */
    fun stopGo(rows: List<TraceRow>, ch: String, lo: Float, hi: Float): Int {
        val v = speeds(rows, ch)
        var n = 0
        var lastMove = -99f
        var stopAt = -1f
        for (i in v.indices) {
            val t = rows[i + 1].t
            if (abs(rows[i + 1].spin) > 0.5f) { stopAt = -1f; continue }
            if (v[i] > hi) {
                if (stopAt >= 0f && t - stopAt <= 0.4f) n++
                stopAt = -1f
                lastMove = t
            } else if (v[i] < lo && stopAt < 0f && t - lastMove < 0.1f) stopAt = t
        }
        return n
    }

    /** Combined, normalised motion of everything visible (face, turn, head, eyes, bob). */
    fun combined(a: TraceRow, b: TraceRow): Float {
        val dt = (b.t - a.t).coerceAtLeast(1e-4f)
        return exprSpeed(a, b) / 0.5f + abs(wrap(b.yaw - a.yaw)) / dt / 6f + abs(wrap(b.pitch - a.pitch)) / dt / 6f +
            kotlin.math.hypot(b.headX - a.headX, b.headY - a.headY) / dt / 0.12f +
            kotlin.math.hypot(b.leadX - a.leadX, b.leadY - a.leadY) / dt / 0.04f + abs(b.posY - a.posY) / dt / 0.05f
    }

    /** Stop-then-go of the whole figure: moving → (nearly) dead for 0.12–0.4 s → moving again. */
    fun stopGoAll(rows: List<TraceRow>): Pair<Int, Float> {
        var n = 0; var dead = 0
        var lastMove = -99f; var stopAt = -1f
        for (i in 1 until rows.size) {
            val r = rows[i]
            if (abs(r.spin) > 0.5f) { stopAt = -1f; continue }
            val v = combined(rows[i - 1], r)
            if (v < 0.25f) dead++
            if (v > 1f) {
                if (stopAt >= 0f && r.t - stopAt in 0.12f..0.4f) n++ // shorter = a smooth reversal, not a stop
                stopAt = -1f; lastMove = r.t
            } else if (v < 0.25f && stopAt < 0f && r.t - lastMove < 0.1f) stopAt = r.t
        }
        return n to dead / rows.size.toFloat()
    }

    private fun jerk(rows: List<TraceRow>, ch: String): Pair<Float, Float> {
        val v = signed(rows, ch)
        if (v.size < 4) return 0f to 0f
        var ss = 0f; var pk = 0f; var n = 0
        val js = ArrayList<Float>()
        // Spin-speed turns (> 120°/s, classic spins only) and 0.3 s either side are not idle
        // motion: their onset can precede the spin flag by a frame or two.
        val fast = BooleanArray(v.size)
        if (ch == "yaw") for (i in v.indices) if (abs(v[i]) > 120f) {
            var k = i; while (k >= 0 && rows[i].t - rows[k].t < 0.3f) { fast[k] = true; k-- }
            k = i; while (k < v.size && rows[k].t - rows[i].t < 0.3f) { fast[k] = true; k++ }
        }
        for (i in 2 until v.size) {
            if (abs(rows[i + 1].spin) > 0.5f || abs(rows[i - 1].spin) > 0.5f || fast[i] || fast[i - 2]) continue
            val dt = (rows[i + 1].t - rows[i].t).coerceAtLeast(1e-4f)
            val j = (v[i] - 2f * v[i - 1] + v[i - 2]) / (dt * dt)
            ss += j * j; n++; js += abs(j)
        }
        js.sort()
        pk = if (js.isEmpty()) 0f else js[(js.size * 0.995f).toInt().coerceAtMost(js.size - 1)]
        return (if (n > 0) sqrt(ss / n) else 0f) to pk
    }

    /** Sign reversals per second of a low-passed velocity (|v| above [eps]). */
    private fun reversals(rows: List<TraceRow>, ch: String, eps: Float): Float {
        val v = signed(rows, ch)
        if (v.isEmpty()) return 0f
        var lp = 0f; var lastSign = 0; var n = 0
        for (i in v.indices) {
            val dt = (rows[i + 1].t - rows[i].t).coerceAtLeast(1e-4f)
            lp += (v[i] - lp) * (1f - kotlin.math.exp(-dt / 0.05f))
            if (abs(rows[i + 1].spin) > 0.5f) continue
            val s = if (lp > eps) 1 else if (lp < -eps) -1 else 0
            if (s != 0) { if (lastSign != 0 && s != lastSign) n++; lastSign = s }
        }
        val dur = (rows.last().t - rows.first().t).coerceAtLeast(1f)
        return n / dur
    }

    /**
     * Neutral returns: the face comes within ε of Neutral (w ≥ 0.95) after showing something
     * else, and a non-neutral target arrives within 1.5 s. [chainOnly]: only count it when the
     * new target belongs to the same cause chain as the previous face or is self-started
     * (a return a reaction should have morphed through); otherwise every dip counts.
     */
    fun neutralReturns(tr: ChannelTrace, chainOnly: Boolean): Int {
        val rows = tr.rows
        var n = 0
        var shown = false
        var prevCause = ""
        var dipAt = -1f
        var ti = 0
        for (r in rows) {
            val wn = r.weights["Neutral"] ?: 0f
            if (wn < 0.5f) {
                if (dipAt >= 0f && r.target != "Neutral") {
                    // Find the target change that ended the dip.
                    while (ti < tr.targets.size - 1 && tr.targets[ti + 1].first <= r.t) ti++
                    val tc = tr.targets.getOrNull(ti)
                    val since = (tc?.first ?: r.t) - dipAt
                    val prio = tr.targetPrio.getOrNull(ti) ?: -1
                    val sameChain = tc != null && (tc.third == prevCause || prio in 0..1 /* ≤ MindRules.P_LIFE */)
                    if (since in 0f..1.5f && (!chainOnly || sameChain)) n++
                }
                dipAt = -1f
                shown = true
                if (r.cause.isNotEmpty() && r.target != "Neutral") prevCause = r.cause
            } else if (wn >= 0.95f && shown && dipAt < 0f) { dipAt = r.t; shown = false }
        }
        return n
    }

    /**
     * Engine20: every gaze shift (saccade flag → next one, ≤ 0.6 s) should travel A → B in one
     * go. stepgo: after its peak the visible gaze (head + eyes) nearly stops (< 15 % of peak)
     * short of 85 % of the way, then speeds up again (> 40 %). reversal: progress falls back by
     * > 10 % of the distance before reaching 85 % (a landing overshoot is fine).
     * Returns (stepgo, reversals, shifts measured).
     */
    /** A shift's own flight; a new look decided later than this is a new shift, not a reversal. */
    const val SHIFT_FLIGHT = 0.4f
    fun gazeShifts(rows: List<TraceRow>, pokes: List<Float> = emptyList()): Triple<Int, Int, Int> {
        val starts = rows.indices.filter { rows[it].saccade }
        var sg = 0; var rv = 0; var n = 0
        fun gx(i: Int) = rows[i].headX + rows[i].leadX
        fun gy(i: Int) = rows[i].headY + rows[i].leadY
        fun vel(i: Int): Pair<Float, Float> {
            val dt = (rows[i].t - rows[i - 1].t).coerceAtLeast(1e-4f)
            return (gx(i) - gx(i - 1)) / dt to (gy(i) - gy(i - 1)) / dt
        }
        for ((k, s0) in starts.withIndex()) {
            if (s0 < 1) continue
            val endT = minOf(rows[s0].t + 0.6f, starts.getOrNull(k + 1)?.let { rows[it].t } ?: Float.MAX_VALUE)
            var e = s0; while (e + 1 < rows.size && rows[e + 1].t <= endT) e++
            if (e - s0 < 4 || (s0..e).any { abs(rows[it].spin) > 0.5f }) continue
            // A poke mid-shift is a new stimulus: the reflex redirect is meant to happen.
            if (pokes.any { it >= rows[s0].t - 0.05f && it <= rows[e].t }) continue
            // Landing: the first near-stop after the speed peak.
            var peak = 0f; var land = e; var stopped = false
            for (i in s0..e) {
                if (rows[i].t - rows[s0].t > SHIFT_FLIGHT) { land = i; break } // later = a new decision
                val (vx, vy) = vel(i); val v = kotlin.math.hypot(vx, vy)
                if (v > peak) peak = v
                else if (v < 0.15f * peak && rows[i].t - rows[s0].t > 0.05f) { land = i; stopped = true; break }
            }
            val ax = gx(s0 - 1); val ay = gy(s0 - 1)
            val dx = gx(land) - ax; val dy = gy(land) - ay
            val d = kotlin.math.hypot(dx, dy)
            if (d < 0.06f) continue
            n++
            // Reversal: progress falls back > 10 % before landing.
            var best = 0f
            for (i in s0..land) {
                val prog = ((gx(i) - ax) * dx + (gy(i) - ay) * dy) / (d * d)
                if (best >= 0.85f) break // past here it is the landing (overshoot-and-settle is intended)
                if (prog < best - 0.1f) { rv++; break }
                best = maxOf(best, prog)
            }
            // Step: after landing the look sets off again the same way within 0.15 s (it
            // stopped part-way and finished in a second step).
            if (stopped) for (i in land + 1..e) {
                if (rows[i].t - rows[land].t > 0.15f) break
                val (vx, vy) = vel(i)
                if (kotlin.math.hypot(vx, vy) > 0.4f * peak && (vx * dx + vy * dy) > 0f) { sg++; break }
            }
        }
        return Triple(sg, rv, n)
    }

    fun compute(tr: ChannelTrace): Result {
        val rows = tr.rows
        val blinks = tr.blinks
        val m = LinkedHashMap<String, Float>()
        if (rows.size < 3) return Result(m)
        val minutes = ((rows.last().t - rows.first().t) / 60f).coerceAtLeast(1f / 60f)
        m["neutral_dips"] = neutralReturns(tr, chainOnly = false).toFloat()
        m["neutral_returns"] = neutralReturns(tr, chainOnly = true).toFloat()
        m["stopgo_expr_min"] = stopGo(rows, "expr", 0.12f, 0.5f) / minutes
        m["stopgo_yaw_min"] = stopGo(rows, "yaw", 1.5f, 6f) / minutes
        m["stopgo_head_min"] = stopGo(rows, "head", 0.03f, 0.12f) / minutes
        val (sga, deadF) = stopGoAll(rows)
        m["stopgo_all_min"] = sga / minutes
        m["dead_frac"] = deadF
        val (jr, jp) = jerk(rows, "yaw")
        m["jerk_yaw_rms"] = jr; m["jerk_yaw_p995"] = jp
        val (pr, pp) = jerk(rows, "pitch")
        m["jerk_pitch_rms"] = pr; m["jerk_pitch_p995"] = pp
        m["rev_yaw_s"] = reversals(rows, "yaw", 2f)
        m["rev_head_s"] = reversals(rows, "head", 0.05f)
        // Owner flips per second (face channel).
        var flips = 0
        for (i in 1 until rows.size) if (rows[i].owner != rows[i - 1].owner && rows[i].owner.isNotEmpty()) flips++
        m["owner_flips_s"] = flips / (rows.last().t - rows.first().t).coerceAtLeast(1f)
        m["face_targets_min"] = tr.targets.size / minutes
        m["unexplained"] = tr.targets.drop(1).count { it.third.isBlank() || it.third == "unspecified" }.toFloat()
        // Blinks.
        val starts = tr.blinks.filter { !it.second }
        m["blinks_min"] = starts.size / minutes
        m["blink_overlap"] = tr.blinks.count { it.third < 0.98f }.toFloat()
        m["double_rate"] = if (starts.isEmpty()) 0f else tr.blinks.count { it.second } / starts.size.toFloat()
        // Longest open-eyed stretch without a blink (eyes resting / dozing / spinning are not gaps).
        var maxGap = 0f
        var ri = 0
        for (i in 1 until starts.size) {
            val a = starts[i - 1].first; val b = starts[i].first
            while (ri < rows.size && rows[ri].t < a) ri++
            var j = ri; var closed = false
            while (j < rows.size && rows[j].t < b) { if (rows[j].lid < 0.5f || abs(rows[j].spin) > 0.5f) { closed = true; break }; j++ }
            if (!closed) maxGap = max(maxGap, b - a)
        }
        m["blink_max_gap"] = maxGap
        val sacc = rows.filter { it.saccade }.map { it.t }
        m["saccades"] = sacc.size.toFloat()
        m["sacc_blink"] = if (sacc.isEmpty()) Float.NaN else sacc.count { s -> tr.blinks.any { abs(it.first - s) <= 0.06f } } / sacc.size.toFloat()
        // Reaction to pokes.
        var gz = 0f; var fc = 0f; var rec = 0; var nP = 0
        for ((pk, p) in tr.pokes.withIndex()) {
            // Reaction time is measured on the first poke of a burst (later ones land on a
            // face and eyes that are already reacting).
            if (pk > 0 && p - tr.pokes[pk - 1] < 2f) continue
            val i0 = rows.indexOfFirst { it.t >= p }
            if (i0 < 1) continue
            nP++
            var gReact = Float.NaN; var fReact = Float.NaN
            val y0 = visYaw(rows[i0 - 1])
            var minAbs = abs(y0)
            for (i in i0 until rows.size) {
                val r = rows[i]; val dtp = r.t - p
                if (dtp > 0.6f) break
                val a = rows[i - 1]; val dt = (r.t - a.t).coerceAtLeast(1e-4f)
                if (gReact.isNaN() && kotlin.math.hypot(r.leadX - a.leadX, r.leadY - a.leadY) / dt > 0.05f) gReact = dtp
                val blinked = blinks.any { it.first > a.t && it.first <= r.t + 1e-4f }
                if (fReact.isNaN() && (r.target != a.target || r.accent != a.accent && r.accent.isNotEmpty() || exprSpeed(a, r) > 0.3f || blinked ||
                    r.beat.contains("+pop") && !a.beat.contains("+pop"))) fReact = dtp // the body bounce is a visible reaction too
                if (dtp <= 0.3f) minAbs = minOf(minAbs, abs(visYaw(r)))
            }
            // Recenter = the poke itself starts a turn toward centre (motion already heading
            // there before the poke is the pan carrying on, not a recenter).
            val iPre = rows.indexOfFirst { it.t >= p - 0.1f }.coerceAtLeast(0)
            val vPre = (abs(y0) - abs(visYaw(rows[iPre]))) / (rows[i0 - 1].t - rows[iPre].t).coerceAtLeast(1e-3f)
            if (abs(y0) > 8f && abs(y0) - minAbs > 2.5f && vPre > -1f) rec++
            gz = max(gz, if (gReact.isNaN()) 9f else gReact)
            fc = max(fc, if (fReact.isNaN()) 9f else fReact)
        }
        m["pokes"] = nP.toFloat()
        m["react_gaze_max"] = gz
        m["react_face_max"] = fc
        m["recenter_before_react"] = rec.toFloat()
        // Engine20 emotional dwell: a spontaneous face (below P_EVENT) that is replaced by an
        // equal-or-weaker cause before its dwell; negative faces without an appraised cause.
        var short = 0; var negNoCause = 0
        for (i in tr.targets.indices) {
            val (t0, face, cause) = tr.targets[i]
            val prio = tr.targetPrio.getOrNull(i) ?: -1
            if (face in MindRules.APPRAISED_NEGATIVE && !MindRules.hasEventCause(prio, cause)) negNoCause++
            val nx = tr.targets.getOrNull(i + 1) ?: continue
            val nPrio = tr.targetPrio.getOrNull(i + 1) ?: -1
            val dwell = nx.first - t0
            val poked = tr.pokes.any { it in t0..nx.first + 0.05f }
            if (prio < MindRules.P_EVENT && nPrio <= prio && !poked && !MindRules.hasEventCause(nPrio, nx.third) &&
                dwell < 0.85f * MindRules.minDwell(face)) short++
        }
        m["emo_short"] = short.toFloat()
        m["neg_uncaused"] = negNoCause.toFloat()
        val (gs, gr, gn) = gazeShifts(rows, tr.pokes)
        m["gaze_shifts"] = gn.toFloat()
        m["gaze_stepgo"] = gs.toFloat()
        m["gaze_reversal"] = gr.toFloat()
        // Idle energy: mean |yaw velocity| (deg/s).
        val vy = speeds(rows, "yaw")
        m["energy_yaw"] = vy.average().toFloat()
        return Result(m)
    }
}
