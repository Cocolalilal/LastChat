package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Human behaviour model (engine11). See docs/avatar-character-engine.md, "Human behavior model".
 *
 *   Temperament   slow mood / energy state (minutes)      → activity, tempo, amplitude
 *   Attention     where the avatar looks + how absorbed     → whether / when it notices
 *   Repertoire    weighted, history-aware responses         → what it does (often nothing)
 *   IdlePlanner   human fixation timing (2–8 s), drifts     → where it looks when nothing happens
 *   MotionVocab   curve type per action                     → how each move travels
 *
 * Everything here only decides; AvatarMotionState / MotionDirector execute.
 */

// ---------------------------------------------------------------- motion vocabulary

/** How a move travels from A to B. */
internal enum class Curve {
    /** Sine ease-in-out: deliberate, self-initiated moves (slow drifts, settles). */
    EaseInOut,
    /** Fast start, soft landing: reactive moves that still feel controlled. */
    EaseOut,
    /** Constant velocity, then a flat hold: smooth pursuit, a frozen stare. */
    LinearHold,
    /** Critically damped spring: arrives fast, never overshoots (head turns, saccades). */
    CritSpring,
    /** Underdamped spring: a little overshoot — reserved for playful / happy beats and startle. */
    UnderSpring,
    /** Instant snap then rigid hold: dumbfounded freeze. */
    SnapHold,
}

internal enum class Action {
    Saccade, MicroSaccade, SmoothPursuit, HeadTurn, HeadTurnPlayful, Nod, PlayfulNod, Startle,
    Settle, Breathing, Blink, DoubleBlink, DumbfoundedFreeze, Sigh, Fidget, DoubleTake, CuriousTilt,
}

/** [omega] in rad/s for springs; [dur] in seconds for eased / timed curves. */
internal data class MotionSpec(val curve: Curve, val dur: Float, val omega: Float, val zeta: Float, val why: String)

internal object MotionVocab {
    val table: Map<Action, MotionSpec> = mapOf(
        Action.Saccade to MotionSpec(Curve.CritSpring, 0.06f, 38f, 1f,
            "Real saccades are ballistic: 30–80 ms, no overshoot you can see."),
        Action.MicroSaccade to MotionSpec(Curve.CritSpring, 0.04f, 38f, 1f,
            "Tiny re-fixations inside a stare; same ballistic profile, 1/10 the size."),
        Action.SmoothPursuit to MotionSpec(Curve.LinearHold, 2.5f, 0f, 1f,
            "Eyes drifting across something: near-constant velocity, then rest."),
        Action.HeadTurn to MotionSpec(Curve.CritSpring, 0.45f, 7.5f, 1f,
            "Heads are heavy and well-controlled: ~0.4 s, no wobble. Eyes lead by ~100 ms."),
        Action.HeadTurnPlayful to MotionSpec(Curve.UnderSpring, 0.4f, 9f, 0.62f,
            "Only in a good, lively mood: a small overshoot reads as bounce/joy."),
        Action.Nod to MotionSpec(Curve.EaseInOut, 0.7f, 0f, 1f,
            "Acknowledging nod: one smooth dip and return, no ringing."),
        Action.PlayfulNod to MotionSpec(Curve.UnderSpring, 0.7f, 0f, 0.5f,
            "Happy agreement: springy nod with follow-through (existing springNodPose)."),
        Action.Startle to MotionSpec(Curve.UnderSpring, 0.85f, 0f, 0.35f,
            "Startle reflex: ~70 ms pop, squash landing, spring settle; eyes held wide 0.6 s."),
        Action.Settle to MotionSpec(Curve.EaseInOut, 1.2f, 0f, 1f,
            "Returning to rest after anything: slow, quiet, never springy."),
        Action.Breathing to MotionSpec(Curve.EaseInOut, 4.2f, 0f, 1f,
            "Sinusoid at 12–16 breaths/min; slower when calm/tired. Barely visible on purpose."),
        Action.Blink to MotionSpec(Curve.EaseOut, 0.2f, 0f, 1f,
            "Close fast (~60 ms, ease-in), open slower (~120 ms, ease-out)."),
        Action.DoubleBlink to MotionSpec(Curve.EaseOut, 0.45f, 0f, 1f,
            "Rare (~5%): surprise, refocusing or catching oneself drifting off."),
        Action.DumbfoundedFreeze to MotionSpec(Curve.SnapHold, 1.4f, 0f, 1f,
            "Stylistic stiffness: snap to wide eyes, everything rigid, then a slow blink to thaw."),
        Action.Sigh to MotionSpec(Curve.EaseInOut, 2.2f, 0f, 1f,
            "Bored/tired: one slow deep breath (squash) with the head dropping a few degrees."),
        Action.Fidget to MotionSpec(Curve.CritSpring, 0.6f, 6f, 1f,
            "Small posture shift (2–4°) every so often; weight moving, not a gesture."),
        Action.DoubleTake to MotionSpec(Curve.CritSpring, 0.35f, 10f, 1f,
            "Glance, start to return, snap back: the second look is faster than the first."),
        Action.CuriousTilt to MotionSpec(Curve.EaseInOut, 1.3f, 0f, 1f,
            "Head tilt held while looking at the thing; eased in and out."),
    )

    operator fun get(a: Action): MotionSpec = table.getValue(a)

    fun easeInOutSine(u: Float): Float = 0.5f - 0.5f * cos(PI.toFloat() * u.coerceIn(0f, 1f))
    fun easeOutCubic(u: Float): Float { val v = 1f - u.coerceIn(0f, 1f); return 1f - v * v * v }
}

// ---------------------------------------------------------------- temperament

/**
 * Slow mood/energy state. Drifts over minutes (Ornstein–Uhlenbeck toward a calm baseline
 * set by the personality), nudged by conversation tone, events and long quiet. Never
 * hyperactive: [activity] tops out at 0.8.
 */
internal class Temperament(private val rng: Random) {
    /** 0 = exhausted … 1 = lively. */
    var energy = 0.42f; private set
    /** 0 = engaged … 1 = very bored. */
    var boredom = 0.1f; private set
    /** Interest in surroundings. */
    var curiosity = 0.4f; private set
    /** Pleasantness (−1…1), follows the mood engine. */
    var valence = 0f

    private var noiseE = 0f
    private var noiseC = 0f

    enum class State { Tired, Bored, Calm, Curious, Lively }

    val state: State get() = when {
        energy < 0.25f -> State.Tired
        boredom > 0.6f -> State.Bored
        energy > 0.62f && valence > 0.15f -> State.Lively
        curiosity > 0.6f -> State.Curious
        else -> State.Calm
    }

    /** Overall behaviour rate (0.1…0.8): how often things happen. */
    val activity: Float get() = (0.12f + 0.55f * energy + 0.2f * curiosity - 0.25f * boredom).coerceIn(0.1f, 0.8f)
    /** Speed multiplier for moves (0.75…1.1). */
    val tempo: Float get() = (0.75f + 0.4f * energy - 0.1f * boredom).coerceIn(0.7f, 1.1f)
    /** Size multiplier for turns / gestures (0.5…1). */
    val amplitude: Float get() = (0.5f + 0.4f * energy + 0.15f * curiosity).coerceIn(0.5f, 1f)
    /** Good, lively mood: the only time springy overshoot is used for ordinary moves. */
    fun playful(p: Personality): Boolean = valence > 0.3f && energy > 0.5f && p.playfulness > 0.45f

    fun update(dt: Float, p: Personality, busy: Boolean, quietSec: Float, timeFatigue: Float) {
        val baseE = (0.32f + 0.3f * p.energy - 0.25f * timeFatigue).coerceIn(0.1f, 0.7f)
        val baseC = 0.25f + 0.35f * p.curiosity
        // OU noise: slow wander (tau ≈ 2 min), small.
        noiseE += (-noiseE / 120f) * dt + 0.035f * gauss() * sqrt(dt)
        noiseC += (-noiseC / 90f) * dt + 0.04f * gauss() * sqrt(dt)
        noiseE = noiseE.coerceIn(-0.2f, 0.2f); noiseC = noiseC.coerceIn(-0.2f, 0.2f)
        energy += ((baseE + noiseE) - energy) * (1f - exp(-dt / 60f))
        curiosity += ((baseC + noiseC + if (busy) 0.15f else 0f) - curiosity) * (1f - exp(-dt / 45f))
        // Boredom: grows during long quiet (minutes), falls fast when something happens.
        val bTarget = if (busy) 0f else (quietSec / 150f).coerceIn(0f, 0.85f)
        boredom += (bTarget - boredom) * (1f - exp(-dt / if (bTarget > boredom) 40f else 6f))
        energy = energy.coerceIn(0.05f, 0.85f)
        curiosity = curiosity.coerceIn(0f, 1f)
    }

    fun onEvent(salience: Float) {
        curiosity = (curiosity + 0.12f * salience).coerceAtMost(1f)
        energy = (energy + 0.05f * salience).coerceAtMost(0.85f)
        boredom = (boredom - 0.3f * salience).coerceAtLeast(0f)
    }

    fun nudge(dEnergy: Float, dCuriosity: Float) {
        energy = (energy + dEnergy).coerceIn(0.05f, 0.85f)
        curiosity = (curiosity + dCuriosity).coerceIn(0f, 1f)
    }

    internal fun debugSet(energy: Float? = null, boredom: Float? = null, curiosity: Float? = null) {
        energy?.let { this.energy = it }; boredom?.let { this.boredom = it }; curiosity?.let { this.curiosity = it }
    }

    private fun gauss(): Float {
        val u1 = rng.nextFloat().coerceAtLeast(1e-4f)
        return sqrt(-2f * ln(u1)) * cos(2f * PI.toFloat() * rng.nextFloat())
    }
}

// ---------------------------------------------------------------- attention / perception

/** Something happened. [dirX]/[dirY] in gaze units (−1…1, +y down); [inView] false = "behind" / off-screen. */
internal data class Stimulus(
    val kind: String,
    val dirX: Float,
    val dirY: Float,
    val salience: Float,
    val inView: Boolean = true,
    /** Touch / direct contact: always felt, fast. */
    val direct: Boolean = false,
)

/** A noticed stimulus: when it lands and how. */
internal data class Percept(val stim: Stimulus, val delay: Float, val lateNotice: Boolean, val strength: Float)

/**
 * Perception gate. The avatar notices what falls near its gaze, more readily when not
 * absorbed in something else; latency 150–600 ms (longer when tired / absorbed / far
 * from gaze). Off-gaze or out-of-view events need noticing first (0.7–1.5 s, double-take)
 * and are often missed. Low-salience things can go unnoticed entirely.
 */
internal class Attention(private val rng: Random) {
    /** 0 = mind wandering … 1 = absorbed in a task. */
    var absorption = 0.3f

    fun perceive(s: Stimulus, gazeX: Float, gazeY: Float, t: Temperament, p: Personality): Percept? {
        val sal = s.salience.coerceIn(0f, 1f)
        if (s.direct) {
            val d = 0.1f + 0.12f * (1f - t.energy) + 0.06f * rng.nextFloat()
            return Percept(s, d, false, sal)
        }
        val off = hypot(s.dirX - gazeX, (s.dirY - gazeY) * 1.2f)
        // Visual acuity falls off with eccentricity; "behind" is only heard, not seen.
        val cone = if (!s.inView) 0.35f else (1f - off / 1.6f).coerceIn(0.15f, 1f)
        val alert = 0.8f + 0.25f * t.energy - 0.2f * t.boredom
        val distract = 1f - 0.4f * absorption
        val behind = if (s.inView) 1f else 0.8f
        val pNotice = ((0.45f + 0.9f * sal) * (0.6f + 0.4f * cone) * alert * distract * behind *
            (0.9f + 0.2f * p.attentiveness)).coerceIn(0f, 0.97f)
        if (rng.nextFloat() > pNotice) return null
        val late = !s.inView || off > 0.9f
        val base = 0.15f + 0.3f * (1f - t.energy) + 0.15f * absorption + 0.12f * (off / 1.6f).coerceAtMost(1f)
        val jitter = 0.08f * (rng.nextFloat() * 2f - 1f)
        var delay = (base * (1.15f - 0.3f * sal) + jitter).coerceIn(0.15f, 0.6f)
        // Unseen ("behind") events are noticed late. Peripheral motion that is in view pops out
        // quickly (more so the more salient it is) — a short extra beat, not a ~1 s stall.
        if (late) delay += if (!s.inView) 0.55f + 0.8f * rng.nextFloat() else (0.1f + 0.35f * (1f - sal)) + 0.15f * rng.nextFloat()
        return Percept(s, delay, late, sal * (0.6f + 0.4f * cone))
    }
}

// ---------------------------------------------------------------- response repertoire

internal enum class Response {
    None, EyesOnly, Glance, GlanceBlink, Acknowledge, Smile, CuriousTilt, Startle, Freeze, DoubleTake, SlowBlink, Sigh,
}

/**
 * Weighted, history-aware choice. The same stimulus does not get the same answer twice
 * in a row (last pick ×0.25, anything in the last 3 ×0.6); "no visible reaction" is always
 * an option and grows with tiredness, boredom and low salience.
 */
internal class Repertoire(private val rng: Random) {
    private val history = HashMap<String, ArrayDeque<Response>>()

    fun choose(pc: Percept, t: Temperament, p: Personality): Response {
        val s = pc.stim
        val k = pc.strength
        val e = t.energy
        val opts = ArrayList<Pair<Response, Float>>()
        if (pc.lateNotice) {
            // Noticed late, off to the side: mostly a double-take ("wait, what was that?").
            opts += Response.DoubleTake to 0.7f + 0.3f * k
            opts += Response.Glance to 0.25f
            opts += Response.EyesOnly to 0.1f
        } else {
            opts += Response.None to (0.25f + 0.4f * t.boredom + 0.3f * (1f - e) + 0.3f * (1f - k)) * (1.3f - p.expressiveness * 0.6f)
            opts += Response.EyesOnly to 0.45f
            opts += Response.Glance to 0.35f + 0.2f * t.curiosity
            opts += Response.GlanceBlink to 0.2f
            opts += Response.Acknowledge to 0.15f * (0.5f + p.warmth)
            opts += Response.Smile to 0.12f * (0.4f + p.warmth) * (0.6f + t.valence.coerceAtLeast(0f))
            opts += Response.CuriousTilt to 0.12f * (0.4f + t.curiosity) * (0.5f + p.expressiveness)
            opts += Response.SlowBlink to 0.1f * (1.2f - e)
            if (t.boredom > 0.4f) opts += Response.Sigh to 0.08f * t.boredom
            if (k > 0.65f) {
                // Big things: startle mostly when caught off guard (bored / mind wandering).
                opts += Response.Startle to 0.35f * k * (0.4f + t.boredom + 0.4f * (1f - e)) * (0.5f + p.expressiveness)
                opts += Response.Freeze to 0.08f * k * (0.5f + p.expressiveness)
            }
        }
        val h = history.getOrPut(s.kind) { ArrayDeque() }
        val weighted = opts.map { (r, w) ->
            val f = when {
                h.lastOrNull() == r && r != Response.None -> 0.25f
                h.contains(r) -> 0.6f
                else -> 1f
            }
            r to w * f
        }
        val total = weighted.sumOf { it.second.toDouble() }.toFloat()
        var x = rng.nextFloat() * total
        var pick = weighted.last().first
        for ((r, w) in weighted) { x -= w; if (x <= 0f) { pick = r; break } }
        h.addLast(pick); while (h.size > 3) h.removeFirst()
        return pick
    }
}

// ---------------------------------------------------------------- idle planner

/** One fixation the idle planner wants: where, for how long, and how to get there. */
internal data class Fixation(
    val x: Float,
    val y: Float,
    val hold: Float,
    /** Seconds to travel there (> 0 = smooth pursuit / slow drift, 0 = saccade). */
    val travel: Float = 0f,
    /** Head joins the move (big shifts); small shifts are eyes only. */
    val withHead: Boolean = true,
    val kind: String,
    /** Return near centre afterwards (side glances, up-right). */
    val thenCenter: Boolean = false,
    /** Attention event to the owner when this fixation lands: "notice" or "ack". */
    val event: String? = null,
)

/**
 * Human idle timing: long fixations (log-normal, median 2.5–5 s, 2–8 s range), thinking
 * stares, slow drifts, occasional glances. Up-right is only ever a short glance.
 */
internal class IdlePlanner(private val rng: Random) {
    private var wasSide = false
    /** Engine15: idle look-around is an attention episode, queued fixation by fixation. */
    private val episode = ArrayDeque<Fixation>()
    var episodeName = "rest"; private set

    private fun logN(median: Float, sigma: Float, lo: Float, hi: Float): Float {
        val u1 = rng.nextFloat().coerceAtLeast(1e-4f)
        val n = sqrt(-2f * ln(u1)) * cos(2f * PI.toFloat() * rng.nextFloat())
        return (median * exp(sigma * n)).coerceIn(lo, hi)
    }
    private fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()

    // Engine16.2b: "gaze-staying" moments (long holds, off-front rests, area exploration) are
    // very rare specials - roughly one per 15-35 min of idle, behind a shared cooldown.
    private var clock = 0f
    private var lastSpecial = -rr(0f, 600f)
    private var specialGap = rr(900f, 2100f)
    private fun special(): Boolean {
        if (clock - lastSpecial < specialGap || rng.nextFloat() >= 0.02f) return false
        lastSpecial = clock; specialGap = rr(900f, 2100f)
        return true
    }

    fun next(life: MarkLifecycle, t: Temperament, drowsy: Boolean): Fixation =
        nextInner(life, t, drowsy).also { clock += it.hold + it.travel }

    private fun nextInner(life: MarkLifecycle, t: Temperament, drowsy: Boolean): Fixation {
        val act = t.activity
        val med = 5.2f - 3.4f * act // 4.8 s calm … 2.5 s lively
        if (drowsy || t.state == Temperament.State.Tired) {
            return Fixation(rr(-0.2f, 0.2f), rr(0.15f, 0.4f), logN(6f, 0.35f, 3f, 10f), travel = rr(1.2f, 2.2f), withHead = true, kind = "heavy")
        }
        if (wasSide) {
            wasSide = false
            return Fixation(rr(-0.08f, 0.08f), rr(-0.03f, 0.06f), logN(med, 0.4f, 2f, 8f), kind = "rest")
        }
        val busy = MotionDirector.isBusy(life)
        val r = rng.nextFloat()
        return when {
            busy && life == MarkLifecycle.Thinking && r < 0.4f -> {
                // Thinking stare: off to one side, a little up or down, held.
                val x = (if (rng.nextBoolean()) 1f else -1f) * rr(0.25f, 0.5f)
                val y = if (rng.nextFloat() < 0.5f) rr(-0.25f, -0.08f) else rr(0.15f, 0.35f)
                fix(x, y, logN(2.6f, 0.35f, 1.5f, 5f), kind = "think", head = rng.nextFloat() < 0.5f)
            }
            busy && life == MarkLifecycle.Streaming && r < 0.55f ->
                fix(rr(-0.25f, 0.25f), rr(0.3f, 0.5f), logN(2.2f, 0.35f, 1.2f, 4.5f), kind = "read", head = false)
            busy -> fix(rr(-0.1f, 0.1f), rr(0f, 0.12f), logN(2.4f, 0.4f, 1.2f, 5f), kind = "rest")
            // Idle (engine15): attention, not random glances.
            else -> nextAttention(t, med)
        }
    }

    /**
     * Idle attention episodes. A long rest looking roughly ahead, then one interest:
     * hold a target 1–3 s (micro-saccades ride on top), then soft drift away, or a slow
     * pan across a few related points, or an acknowledge (nod + blink), then back to rest.
     * Never random teleport glances; up-right is never a rest.
     */
    private fun nextAttention(t: Temperament, med: Float): Fixation {
        episode.removeFirstOrNull()?.let { return it }
        // Between interests: a long, quiet rest near the front.
        if (episodeName != "rest") {
            episodeName = "rest"
            return restFixation(t, med)
        }
        // Interest target: an imagined point anywhere (full range), biased to the sides/below.
        val side = if (rng.nextBoolean()) 1f else -1f
        val x = side * rr(0.25f, 0.85f)
        var y = if (rng.nextFloat() < 0.6f) rr(0.05f, 0.5f) else rr(-0.4f, 0f)
        if (x > 0.15f && y < -0.08f && rng.nextFloat() < 0.6f) y = -y * 0.5f // mostly not up-right
        val noticed = rng.nextFloat() < 0.25f + 0.2f * t.curiosity
        val first = fix(x, y, logN(1.8f, 0.3f, 1.0f, 3.0f), kind = "interest", head = true)
            .copy(event = if (noticed) "notice" else null)
        val r = rng.nextFloat()
        when {
            r < 0.3f -> {
                // Soft drift away from it, then rest.
                episodeName = "drift"
                val dx = x + side * rr(-0.3f, -0.12f); val dy = y + rr(-0.12f, 0.12f)
                episode += Fixation(dx, dy, logN(1.4f, 0.3f, 0.8f, 2.4f), travel = rr(1.2f, 2.2f) / t.tempo,
                    withHead = true, kind = "drift")
            }
            r < 0.48f -> {
                // Slow pan across 2–3 related points (one direction, pursuit between).
                episodeName = "pan"
                val n = 2 + rng.nextInt(2)
                val dirX = -side * rr(0.18f, 0.32f); val dirY = rr(-0.08f, 0.08f)
                var px = x; var py = y
                repeat(n) {
                    px = (px + dirX).coerceIn(-0.9f, 0.9f); py = (py + dirY).coerceIn(-0.4f, 0.5f)
                    episode += Fixation(px, py, logN(0.9f, 0.3f, 0.5f, 1.4f), travel = rr(0.8f, 1.4f) / t.tempo,
                        withHead = true, kind = "pan")
                }
            }
            r < 0.62f && special() -> {
                // Engine16.2 area exploration: stay in this region a while, eyes wandering
                // over a few nearby points (head holds), before moving on.
                episodeName = "explore"
                // The region is a comfortable one (not the far edge, not low), and the whole
                // wander stays under ~9 s.
                val n = 3 + rng.nextInt(3)
                val rx = x.coerceIn(-0.5f, 0.5f)
                val ry = y.coerceIn(-0.15f, 0.14f)
                repeat(n) {
                    var ex = (rx + rr(-0.12f, 0.12f)).coerceIn(-0.6f, 0.6f)
                    var ey = (ry + rr(-0.08f, 0.08f)).coerceIn(-0.2f, 0.2f)
                    if (ex > 0.15f && ey < -0.08f) ey = rr(-0.04f, 0.06f)
                    episode += Fixation(ex, ey, logN(1.5f, 0.35f, 0.6f, 3.2f), travel = if (rng.nextFloat() < 0.3f) rr(0.35f, 0.7f) else 0f,
                        withHead = false, kind = "explore")
                }
            }
            r < 0.85f -> {
                // Acknowledge it: a small nod + blink, then away.
                episodeName = "ack"
                episode += Fixation(x * 0.95f, y * 0.95f, rr(0.8f, 1.2f), withHead = true, kind = "ack", event = "ack")
            }
            else -> episodeName = "look"
        }
        return first
    }

    /**
     * Engine16.2: where it rests is not always straight ahead. Front is most common, then a
     * little to one side, slightly low to one side, or up-left; never up-right, and low rests
     * stay short so it doesn't look droopy. Now and then a really long hold (12–24 s).
     */
    private fun restFixation(t: Temperament, med: Float): Fixation {
        val s = if (rng.nextBoolean()) 1f else -1f
        // Normal: roughly ahead. Only a rare special moment rests elsewhere / holds long.
        if (!special()) return Fixation(rr(-0.07f, 0.07f), rr(-0.02f, 0.07f), logN(med * 1.5f, 0.35f, 4f, 12f),
            travel = rr(0.9f, 1.6f) / t.tempo, withHead = true, kind = "rest")
        val r = rng.nextFloat()
        val long = rng.nextFloat() < 0.35f
        var hold = if (long) rr(12f, 24f) else logN(med * 1.5f, 0.35f, 4f, 12f)
        val (x, y, k) = when {
            r < 0.15f -> Triple(rr(-0.07f, 0.07f), rr(-0.02f, 0.07f), "rest")
            r < 0.6f -> Triple(s * rr(0.18f, 0.4f), rr(-0.04f, 0.1f), "rest-side")
            r < 0.78f -> { hold = hold.coerceAtMost(9f); Triple(s * rr(0.12f, 0.35f), rr(0.12f, 0.2f), "rest-low") }
            r < 0.95f -> { hold = hold.coerceAtMost(10f); Triple(-rr(0.15f, 0.35f), -rr(0.06f, 0.15f), "rest-upleft") }
            else -> Triple(rr(-0.05f, 0.03f), -rr(0.04f, 0.09f), "rest-up")
        }
        return Fixation(x, y, hold, travel = rr(0.9f, 1.6f) / t.tempo, withHead = true, kind = if (long) "$k-long" else k)
    }

    private fun fix(x: Float, y: Float, hold: Float, kind: String, head: Boolean = true): Fixation {
        // Up-right is a glance, never a rest.
        val upRight = x > 0.15f && y < -0.08f
        if (upRight) wasSide = true
        return Fixation(x, y, if (upRight) hold.coerceAtMost(0.9f) else hold, withHead = head || hypot(x, y) > 0.45f, kind = kind, thenCenter = upRight)
    }
}

// ---------------------------------------------------------------- character moments (engine12)

/**
 * Rare, motivated "character moments" — what makes it feel like someone lives in there.
 * Each has a reason in the mood/attention state, its own cooldown, and a global spacing so
 * they never pile up (≈ one every 15–40 s depending on activity). Recently used moments
 * lose weight; each execution randomises its variant (side, count, size, face).
 */
internal enum class Moment {
    /** Playful or happy: a full spin that overshoots and settles. Bored: a lazy one. */
    Spin,
    /** Curious: deliberate scan of 2–4 points with purposeful holds (checking out the room). */
    Scan,
    /** Curious: leans and tilts to peek at something to one side. */
    Peek,
    /** Long quiet + bored or tired: a big stretch, eyes shut, then settle. */
    Stretch,
    /** Tired / very bored: head sinks slowly, lids heavy … then perks up with a double blink. */
    SleepyDrift,
    /** Curious about the UI: looks at the activity pill / its own reflection, tilts, maybe smiles. */
    NoticePill,
    /** Event-driven: a happy wiggle after a nice message. */
    Wiggle,
    /** Event-driven: a small self-satisfied nod after finishing a reply. */
    SmugNod,
}

internal class MomentPlanner(private val rng: Random) {
    private var clock = 0f
    private var lastAny = -20f
    private var checkIn = 6f
    private val lastAt = HashMap<Moment, Float>()
    private val recent = ArrayDeque<Moment>()

    private fun cool(m: Moment, sec: Float): Float {
        val f = ((clock - (lastAt[m] ?: -1e4f)) / sec).coerceIn(0f, 1f)
        return f * f
    }

    fun note(m: Moment) {
        lastAt[m] = clock; lastAny = clock
        recent.addLast(m); while (recent.size > 4) recent.removeFirst()
    }

    /** Event-driven moments ask here (respects cooldowns and global spacing loosely). */
    fun allow(m: Moment, cooldown: Float): Boolean =
        clock - lastAny > 6f && cool(m, cooldown) > 0.95f

    /** Spontaneous moment, or null. [calm] = idle, nothing else driving the face. */
    fun update(dt: Float, t: Temperament, p: Personality, calm: Boolean, quietSec: Float, hasPill: Boolean): Moment? {
        clock += dt
        checkIn -= dt
        if (checkIn > 0f || !calm) return null
        checkIn = 4f + rng.nextFloat() * 6f
        val spacing = 26f - 16f * ((t.activity - 0.1f) / 0.7f).coerceIn(0f, 1f) // 26 s calm … 10 s lively
        if (clock - lastAny < spacing) return null
        val playful = t.playful(p) || (t.valence > 0.25f && p.playfulness > 0.55f)
        val opts = listOf(
            // Engine15: spins have their own cadence in the owner (no planner spins).
            Moment.Spin to 0f,
            Moment.Scan to (0.4f * t.curiosity + 0.25f * t.boredom) * cool(Moment.Scan, 25f),
            Moment.Peek to 0.3f * t.curiosity * (0.5f + p.playfulness) * cool(Moment.Peek, 30f),
            Moment.Stretch to (if (quietSec > 40f && (t.boredom > 0.3f || t.energy < 0.35f)) 0.6f else 0f) * cool(Moment.Stretch, 180f),
            Moment.SleepyDrift to (if (t.energy < 0.32f || t.boredom > 0.55f) 0.45f else 0f) * cool(Moment.SleepyDrift, 90f),
            Moment.NoticePill to (if (hasPill) 0.3f * t.curiosity else 0f) * cool(Moment.NoticePill, 120f),
            // Doing nothing is a real option.
            null to 0.2f + 0.2f * (1f - t.activity),
        ).map { (m, w) -> m to w * (if (m != null && recent.lastOrNull() == m) 0.2f else if (m != null && m in recent) 0.6f else 1f) }
        val total = opts.sumOf { it.second.toDouble() }.toFloat()
        var x = rng.nextFloat() * total
        for ((m, w) in opts) { x -= w; if (x <= 0f) { m?.let { note(it) }; return m } }
        return null
    }

    internal fun debugReady() { lastAny = -1e4f; checkIn = 0f; lastAt.clear() }
}

// ---------------------------------------------------------------- spin paths (engine16)

/**
 * Engine16: a spin is no longer always a turn about the vertical axis. Each path maps the
 * eased spin progress c (0…1 of one full turn) to (yaw, pitch, roll) degrees; every path
 * returns exactly to (0,0,0) at c = 1 so the rest pose is unchanged.
 */
internal enum class SpinPath(val weight: Float, val why: String) {
    /** Classic turn about the vertical axis. */
    Turn(0.45f, "turn around on the spot"),
    /** Turn about an axis tipped toward the viewer: leans into it like a spinning top. */
    Top(0.33f, "lean into a turn like a top"),
    /** Forward (or backward) somersault about the horizontal axis. */
    Flip(0.22f, "forward/back flip");
    // Engine16.1 (Julian): removed Barrel (reads as a flat clock-hand rotation), Tumble (too
    // crazy, goes upside down) and Cartwheel (pure screen-plane roll = also clock-like).

    fun angles(c: Float, dir: Float): Triple<Float, Float, Float> {
        val full = 360f * c * dir
        val s = kotlin.math.sin(PI.toFloat() * c)
        return when (this) {
            Turn -> Triple(full, 0f, 0f)
            Top -> Triple(full, 0f, -16f * dir * s)
            Flip -> Triple(0f, full, 0f)
        }
    }
}
