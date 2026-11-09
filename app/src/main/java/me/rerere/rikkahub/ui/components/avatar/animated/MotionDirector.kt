package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Procedural motion that is gentle throughout: a busy phase may hand over any time. */
class CalmProceduralSource(
    override val name: String,
    override val duration: Float,
    private val fn: (Float) -> BodyPose,
) : MotionSource {
    override fun pose(t: Float): BodyPose = fn((t / duration).coerceIn(0f, 1f))
}

/**
 * One body pose. Rotation in degrees (pitch: + looks down, yaw: + looks right, roll),
 * posY = clip bob (desktop units), squash per axis, scale relative to rest size
 * (1 = rest), lidOpen 0..1 from clip lid tracks.
 */
data class BodyPose(
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val posY: Float = 0f,
    val squashX: Float = 1f,
    val squashY: Float = 1f,
    val scale: Float = 1f,
    val lidOpen: Float = 1f,
) {
    /** Shortest-arc blend toward [o] by [w] (0..1). */
    fun lerpTo(o: BodyPose, w: Float): BodyPose {
        if (w <= 0f) return this
        return BodyPose(
            pitch = pitch + MarkClipEngine.wrap180(o.pitch - pitch) * w,
            yaw = yaw + MarkClipEngine.wrap180(o.yaw - yaw) * w,
            roll = roll + MarkClipEngine.wrap180(o.roll - roll) * w,
            posY = posY + (o.posY - posY) * w,
            squashX = squashX + (o.squashX - squashX) * w,
            squashY = squashY + (o.squashY - squashY) * w,
            scale = scale + (o.scale - scale) * w,
            lidOpen = lidOpen + (o.lidOpen - lidOpen) * w,
        )
    }

    companion object {
        val REST = BodyPose()
    }
}

/** Something a layer can play: a carved clip, a still "settle" pose, or a small gesture. */
interface MotionSource {
    val name: String
    /** Natural end in local seconds. */
    val duration: Float
    fun pose(t: Float): BodyPose
    /** Clip-authored expression at [t] (null = this source has no opinion). */
    fun expression(t: Float): String? = null
    /** Clip-authored blink key crossed in (t0, t1]. */
    fun blinkIn(t0: Float, t1: Float): Boolean = false
    /** Pose at [t] is near rest, so leaving here never cuts a gesture. */
    fun calmAt(t: Float): Boolean = true
}

/**
 * Plays a desktop clip with per-play variation (amplitude, mirror). Looking UP is
 * the Grok brand cue, so upward pitch is softened continuously (never a step).
 */
class ClipSource(
    val clip: MarkClip,
    private val amp: Float,
    private val mirror: Boolean,
    windowEnd: Float,
    private val squashAmp: Float = 0.5f,
) : MotionSource {
    private val spin = MarkClipEngine.spinAxes(clip, windowEnd + 1.5f)
    private val hasExpr = !clip.tracks["expression"].isNullOrEmpty()
    private val calmTimes: List<Float> by lazy {
        val out = ArrayList<Float>()
        var t = 0f
        while (t <= clip.duration) {
            if (MarkClipEngine.isCalmAt(clip, min(t, clip.duration - 1e-3f))) out += t
            t += 0.05f
        }
        out
    }
    override val name: String get() = clip.name
    override val duration: Float get() = clip.duration

    override fun pose(t: Float): BodyPose {
        val s = MarkClipEngine.sample(clip, t.coerceIn(0f, clip.duration - 1e-3f))
        var p = s.rotationDeg[0]
        var y = s.rotationDeg[1]
        var r = s.rotationDeg[2]
        if (!spin[1]) y *= amp
        if (!spin[2]) r *= amp
        if (mirror) {
            y = -y; r = -r
        }
        if (!spin[0]) p *= amp
        return BodyPose(
            pitch = p,
            yaw = y,
            roll = r,
            posY = s.position[1] * amp,
            squashX = 1f + (s.squash[0] - 1f) * squashAmp * amp,
            squashY = 1f + (s.squash[1] - 1f) * squashAmp * amp,
            // Desktop zoom swings (Working_B dips to 0.42) read as the avatar changing
            // size. Exclude it from the live character.
            scale = 1f, // Exclude clip camera zoom from live avatar size.
            lidOpen = s.lids,
        )
    }

    override fun expression(t: Float): String? =
        if (hasExpr) MarkClipEngine.sample(clip, t.coerceIn(0f, clip.duration - 1e-3f)).expression else null

    override fun blinkIn(t0: Float, t1: Float): Boolean {
        val keys = clip.tracks["expression"] ?: return false
        return keys.any { it.expression == "blink" && it.t > t0 && it.t <= t1 }
    }

    override fun calmAt(t: Float): Boolean {
        val tt = t.coerceIn(0f, clip.duration)
        return calmTimes.any { abs(it - tt) <= 0.051f }
    }

    /** First calm time ≥ [t] (or null). */
    fun nextCalm(t: Float): Float? = calmTimes.firstOrNull { it >= t }
    fun calmStarts(maxT: Float): List<Float> = calmTimes.filter { it <= maxT }
}

class HoldSource(override val name: String, private val p: BodyPose, override val duration: Float) : MotionSource {
    override fun pose(t: Float): BodyPose = p
}

class ProceduralSource(
    override val name: String,
    override val duration: Float,
    private val fn: (Float) -> BodyPose,
) : MotionSource {
    override fun pose(t: Float): BodyPose = fn((t / duration).coerceIn(0f, 1f))
    override fun calmAt(t: Float): Boolean = t >= duration
}

/**
 * Motion director — replaces the old clip loop + 0.35 s bridge.
 *
 * Root causes of the hard cuts it fixes:
 *  - the bridge eased to the next clip's t=0 pose and then jumped to a random phase;
 *  - same-clip "soft re-phase" set clipElapsed directly (a jump);
 *  - brand-stare suppression was a step function (pose snapped mid-clip);
 *  - loops wrapped (`t % duration`) even when last key ≠ first key;
 *  - pose knobs (lid/eyeGap/position gain) switched instantly with lifecycle.
 *
 * Now every beat is a layer that fades in over the still-playing previous layer
 * (smootherstep crossfade, shortest-arc euler), clips play a varied window once and
 * never wrap, the output runs through a light critically-damped spring, and busy
 * clips finish their phrase (or reach a calm pose) before handing back to idle.
 */
class MotionDirector(
    private val rng: Random = Random.Default,
    private val clipLookup: (String) -> MarkClip? = { if (AvatarPackData.isLoaded()) AvatarPackData.clips[it] else null },
) {
    enum class Gesture { Spin, Hop, Nod, Recoil, TurnAway, Droop, Tilt, Attend }

    private class Layer(
        val source: MotionSource,
        var t: Float,
        val tEnd: Float,
        val speed: Float,
        val fadeIn: Float,
        val kind: String,
        val gesture: Boolean = false,
    ) {
        var progress = 0f
        val weight: Float get() = smootherstep(progress)
        fun pose() = source.pose(t)
        fun isCalm() = source.calmAt(t)
    }

    var lifecycle: MarkLifecycle = MarkLifecycle.Idle
        private set
    private var desired: MarkLifecycle = MarkLifecycle.Idle
    private val layers = ArrayList<Layer>()

    /** Set by the owner each tick: a face morph is in progress (no nods start then). */
    var exprMorphing = false

    /** A nod (gesture or busy beat) is playing; expression changes wait for it. */
    val nodding: Boolean get() = adds.any { it.name == "nod" || it.name == "nodB" }
    private var lifeSec = 0f
    private var pendingSince = -1f
    private val recentKinds = ArrayDeque<String>()
    private val recentClips = ArrayDeque<String>()

    /** Mood from the motion state (valence −1…1, arousal 0…1): biases idle choices. */
    var moodValence: Float = 0f
    var moodArousal: Float = 0.5f

    /** Temperament activity (0.1 calm … 0.8 lively): pacing, hold lengths, amplitude. */
    var activity: Float = 0.35f
    /** Playful mood: springy overshoot and anticipation allowed; otherwise critically damped. */
    var playful: Boolean = false
    private fun lerpA(a: Float, b: Float) = a + (b - a) * ((activity - 0.1f) / 0.7f).coerceIn(0f, 1f)

    /** Set by the alive brain: long idle → slower, drooping holds. */
    var drowsy: Boolean = false

    /** One-shot flag: a busy phase just handed back to Idle (reply finished). */
    var finishedBusy: Boolean = false

    /** Expression authored by the dominant clip layer (null = none). */
    var clipExpression: String? = null
        private set

    /** A clip-authored blink key was crossed this frame. */
    var clipBlink: Boolean = false
        private set

    /** True on frames where a layer was pushed or dropped (tests: no jump allowed there). */
    var stackChanged: Boolean = false
        private set

    /** Blended pose before the output spring (tests check this is continuous too). */
    var rawPose: BodyPose = BodyPose.REST
        private set

    internal fun debugLayers(): String = layers.joinToString("|") { "%s@%.2f/%.2f w%.2f".format(it.source.name, it.t, it.tEnd, it.weight) }

    /** Base beat + additive gestures (trace). */
    val traceName: String get() = activeName + adds.joinToString("") { "+" + it.name } + (if (pops.isNotEmpty()) "+pop" else "")

    /** Name of the dominant layer (debug / tests). */
    val activeName: String get() = layers.lastOrNull()?.source?.name ?: "-"

    // Post-filter (critically damped spring) state: pitch, yaw, roll, posY, sqX, sqY, scale, lid
    private val fx = FloatArray(8)
    private val fv = FloatArray(8)
    private val rawUnwrapped = FloatArray(3)
    private val lastRaw = FloatArray(3)
    private var filterReady = false
    private var stretchSm = 0f

    /** Engine19: something just grabbed attention — no new idle beat starts for [sec]. */
    fun holdBeats(sec: Float) { holdOff = maxOf(holdOff, sec) }
    private var holdOff = 0f

    /**
     * Engine19 startle freeze: the base beat's clock eases almost to a stop for [sec] (a
     * critically damped time-scale, so the motion decelerates rather than halts) and then
     * picks up again. Additive gestures (the startle pop, a nod) are not slowed.
     */
    fun freezeBase(sec: Float) {
        // Hold where the body actually is (the spring's position), not where its target is
        // heading: mid-turn the target is ahead, and an idle clip may be on its way back to centre.
        if (freezeLeft <= 0f && filterReady) { frozenPose = poseOf(fx); freezeW = 0f }
        freezeLeft = maxOf(freezeLeft, sec); holdBeats(sec + 0.3f)
    }
    private var frozenPose: BodyPose? = null
    private var freezeW = 0f
    /** Poke freeze: the base clock slows to this rate… */
    private val FREEZE_GOAL = 0.4f
    /** …and the body holds this much of where it actually was (never a full stop-start). */
    private val FREEZE_HOLD = 0.7f
    private var freezeLeft = 0f
    private var tScale = 1f
    private var tScaleV = 0f

    fun setDesired(l: MarkLifecycle) {
        desired = l
    }

    /**
     * Engine19: gestures are ADDITIVE, zero-mean offsets on top of the steered base pose —
     * they never replace it (so a nod during a turn nods from the turned pose, and nothing
     * writes a rest pose). They may start in any lifecycle; only the idle-only ones (spin,
     * hop, droop) need a calm phase, and a gesture of the same kind never stacks.
     * [holdSec] is only used by [Gesture.Attend] (how long to keep still and look).
     */
    fun gesture(g: Gesture, holdSec: Float = 0f): Boolean {
        val calm = lifecycle == MarkLifecycle.Idle || lifecycle == MarkLifecycle.Listening ||
            lifecycle == MarkLifecycle.Sleeping
        if (!calm && (g == Gesture.Spin || g == Gesture.Hop || g == Gesture.Droop)) return false
        val name = g.name.lowercase()
        if (g != Gesture.Recoil && adds.any { it.name == name }) return false
        when (g) {
            Gesture.Spin -> {
                val dir = if (rng.nextBoolean()) 1f else -1f
                val d = range(1.45f, 1.8f)
                adds += Add(name, d) { u, _ ->
                    val e = easeInOutCubic(u)
                    BodyPose(pitch = sin(PI.toFloat() * u) * 3f, yaw = dir * 360f * e, roll = -dir * sin(PI.toFloat() * u) * 4f)
                }
            }
            Gesture.Hop -> {
                val clip = clipLookup("Done_B") ?: return false
                val src = ClipSource(clip, 0.42f, rng.nextBoolean(), 0.95f, squashAmp = 1f)
                val p0 = src.pose(0f)
                val sp = range(0.9f, 1.05f)
                val d = 0.95f / sp
                adds += Add(name, d) { u, _ ->
                    val q = src.pose(u * 0.95f)
                    val w = window(u, 0.12f, 0.2f)
                    BodyPose(
                        pitch = MarkClipEngine.wrap180(q.pitch - p0.pitch) * w, yaw = MarkClipEngine.wrap180(q.yaw - p0.yaw) * w,
                        roll = MarkClipEngine.wrap180(q.roll - p0.roll) * w, posY = (q.posY - p0.posY) * w,
                        squashX = 1f + (q.squashX - p0.squashX) * w, squashY = 1f + (q.squashY - p0.squashY) * w,
                    )
                }
            }
            Gesture.Nod -> {
                // Natural nod: eyes dip first, head follows in one eased arc, soft settle.
                val d = range(0.62f, 0.8f)
                val deep = range(6f, 9f)
                gazeCue = GazeCue(0f, 0.28f, d * 0.55f, "nod")
                val springy = playful
                adds += Add(name, d) { u, _ ->
                    if (springy) springNodPose(u, deep)
                    else BodyPose(pitch = deep * nodCurve(u, 0.08f), posY = 0.012f * nodCurve(u, 0f))
                }
            }
            Gesture.Recoil -> {
                // Startle: a snappy pop up and back (stretch), a squashy landing, then a springy
                // settle with overshoot. A poke during a pop adds a smaller pop on top (never
                // restarts the curve), so rapid pokes stay continuous.
                val d = range(0.8f, 0.95f)
                val live = pops.sumOf { abs(startlePop(it[0])).toDouble() }.toFloat().coerceIn(0f, 1f)
                pops += floatArrayOf(0f, d, 1f - 0.6f * live)
            }
            Gesture.TurnAway -> {
                // Turn further away from where it already faces (never back through centre).
                val cur = MarkClipEngine.wrap180(tgt[1])
                val side = if (abs(cur) > 3f) kotlin.math.sign(cur) else if (rng.nextBoolean()) 1f else -1f
                val d = range(1.2f, 1.8f) + 0.5f
                val yaw = side * range(16f, 22f)
                adds += Add(name, d) { u, t -> envPose(t, d, 0.32f, 0.5f, BodyPose(yaw = yaw, roll = -side * 3f, pitch = 2f)) }
            }
            Gesture.Tilt -> {
                // Tilt toward the side it already faces (a tilt never swings it back to centre).
                val cur = MarkClipEngine.wrap180(tgt[1])
                val side = if (abs(cur) > 3f) kotlin.math.sign(cur) else if (rng.nextBoolean()) 1f else -1f
                val d = range(1.2f, 1.6f) + 0.45f
                val roll = side * range(6f, 9f); val yaw = side * range(2f, 5f)
                adds += Add(name, d) { _, t -> envPose(t, d, 0.3f, 0.45f, BodyPose(roll = roll, yaw = yaw, pitch = 1f)) }
            }
            Gesture.Attend -> {
                // Settle the idle sway so a look at something reads as a deliberate look.
                val d = holdSec.coerceIn(0.8f, 4f) + 0.4f
                attendPose = poseOf(tgt).copy(pitch = MarkClipEngine.wrap180(tgt[0]) + 1f)
                attendT = 0f; attendDur = d
            }
            Gesture.Droop -> {
                val d = range(2.2f, 3.4f)
                val rise = range(1.6f, 2.4f)
                val pitch = range(10f, 14f); val roll = range(-5f, 5f)
                adds += Add(name, d + 1.2f) { _, t -> envPose(t, d + 1.2f, rise, 1.2f, BodyPose(pitch = pitch, roll = roll)) }
            }
        }
        return true
    }

    fun update(dtIn: Float): BodyPose {
        val dt = dtIn.coerceIn(0f, 0.1f)
        lifeSec += dt
        clock += dt
        clipBlink = false
        stackChanged = false
        handOverDelta = 0f
        if (layers.isEmpty()) {
            push(nextBeat(desired, entering = true).also { it.progress = 1f })
            lifecycle = desired
        }

        // --- lifecycle hand-over (finish the phrase when leaving busy) ---
        if (desired != lifecycle) {
            val leavingBusy = isBusy(lifecycle) && !isBusy(desired) && desired != MarkLifecycle.Failed
            var commit = true
            if (leavingBusy) {
                if (pendingSince < 0f) pendingSince = lifeSec
                val waited = lifeSec - pendingSince
                val top = layers.last()
                val topDone = top.t >= top.tEnd
                commit = lifeSec >= MIN_BUSY_SEC && (top.isCalm() || topDone || waited >= MAX_FINISH_WAIT)
            }
            if (commit) {
                if (leavingBusy && desired == MarkLifecycle.Idle) finishedBusy = true
                lifecycle = desired
                lifeSec = 0f
                pendingSince = -1f
                push(nextBeat(lifecycle, entering = true))
            }
        } else {
            pendingSince = -1f
        }

        // --- advance the base beat (on its own, possibly frozen, clock) ---
        run {
            freezeLeft -= dt
            val goal = if (freezeLeft > 0f) FREEZE_GOAL else 1f
            val w = if (freezeLeft > 0f) 10f else 2.6f // resume gently: idle drifts back in, no restart
            val k = exp(-w * dt)
            val e = tScale - goal; val c = tScaleV + w * e
            tScale = goal + (e + c * dt) * k; tScaleV = (tScaleV - w * c * dt) * k
        }
        val bdt = dt * tScale.coerceIn(0f, 1f)
        val top0 = layers.last()
        run {
            val t0 = top0.t
            top0.t += bdt * top0.speed
            top0.progress = min(1f, top0.progress + dt / top0.fadeIn.coerceAtLeast(0.02f))
            if (top0.source.blinkIn(t0, top0.t)) clipBlink = true
        }

        // --- schedule the next beat when the top window ends ---
        val waitingToLeaveBusy = desired != lifecycle
        holdOff -= dt
        if (top0.t >= top0.tEnd && holdOff <= 0f && frozenPose == null && !(waitingToLeaveBusy && isBusy(lifecycle))) {
            push(nextBeat(lifecycle, entering = false))
        }

        // --- compose: inertialized base target (C² across beat switches) ---
        val top = layers.last()
        var pose = steer(top, dt, bdt)
        frozenPose?.let { fp ->
            // Grip in ~0.1 s (eased), let go as the clock recovers.
            freezeW = if (freezeLeft > 0f) (freezeW + dt / 0.1f).coerceAtMost(1f) else minOf(freezeW, (1f - tScale) / (1f - FREEZE_GOAL).coerceAtLeast(1e-3f))
            val fw = FREEZE_HOLD * freezeW * freezeW * (3f - 2f * freezeW)
            if (fw > 1e-3f) pose = pose.lerpTo(fp, fw) else if (freezeLeft <= 0f) frozenPose = null
        }
        clipExpression = top.source.expression(top.t)
        rawPose = pose

        // Spins ride the base target (the output spring caps the turn speed); everything else
        // is added after the spring so its shape is exact.
        val out = filter(addGestures(pose, dt, pre = true), dt)
        return addGestures(out, dt, pre = false)
    }

    // ------------------------------------------------------- engine19 execution layer

    private class Add(val name: String, val dur: Float, val fn: (Float, Float) -> BodyPose) {
        var t = 0f
        val pre: Boolean get() = name.startsWith("spin")
    }
    private val adds = ArrayList<Add>()
    /** Startle pops: [u, dur, amp]. */
    private val pops = ArrayList<FloatArray>()
    private var attendPose: BodyPose? = null
    private var attendT = 0f
    private var attendDur = 0f

    /** Base target (angles unwrapped) and its velocity / acceleration, per axis. */
    private val tgt = FloatArray(8)
    private val tgtV = FloatArray(8)
    private val tgtA = FloatArray(8)
    private var tgtReady = false
    private val base = FloatArray(8)
    private val lastBaseRaw = FloatArray(3)
    private var switched = false
    private var switchFade = 0.6f
    /** Quintic inertialization: offset polynomial coefficients per axis (x0, v0, a0, A, B, C) and its clock. */
    private val inC = Array(8) { FloatArray(6) }
    private var inT = 0f
    private var inDur = 0f
    /** Engine21.1: each channel's own blend length (setQuintic may shorten it). */
    private val inDurC = FloatArray(8)

    /** Debug / tests: the base target just before the output spring. */
    internal val steerTarget: FloatArray get() = tgt

    private fun arr(p: BodyPose, out: FloatArray) {
        out[0] = p.pitch; out[1] = p.yaw; out[2] = p.roll; out[3] = p.posY
        out[4] = p.squashX; out[5] = p.squashY; out[6] = p.scale; out[7] = p.lidOpen
    }

    private fun poseOf(a: FloatArray) = BodyPose(
        pitch = MarkClipEngine.wrap180(a[0]), yaw = MarkClipEngine.wrap180(a[1]), roll = MarkClipEngine.wrap180(a[2]),
        posY = a[3], squashX = a[4], squashY = a[5], scale = a[6], lidOpen = a[7],
    )

    /**
     * Engine19: the base pose is the CURRENT beat's pose plus a decaying offset. When a beat
     * changes (a new hold, clip or look), the difference between where the body is going and
     * where the new beat starts — in position, velocity and acceleration — becomes an offset
     * that dies out on a quintic over the beat's fade (inertialization). The new beat plays
     * exactly as authored; the move into it carries the old momentum, with no crossfade
     * blend, no velocity reset and no detour through a rest pose.
     */
    private fun steer(top: Layer, dt: Float, bdt: Float = dt): BodyPose {
        val raw = FloatArray(8)
        arr(top.pose(), raw)
        if (!tgtReady) {
            for (i in 0 until 8) { tgt[i] = raw[i]; base[i] = raw[i]; tgtV[i] = 0f; tgtA[i] = 0f }
            for (a in 0..2) lastBaseRaw[a] = raw[a]
            tgtReady = true; switched = false
            return poseOf(tgt)
        }
        if (switched) {
            switched = false
            // New beat: align its angles to the nearest turn of the current target.
            for (a in 0..2) { base[a] = tgt[a] - MarkClipEngine.wrap180(tgt[a] - raw[a]); lastBaseRaw[a] = raw[a] }
            for (i in 3 until 8) base[i] = raw[i]
            // The new beat's own velocity right now (finite difference on its source).
            val h = 0.01f
            val q = FloatArray(8)
            arr(top.source.pose((top.t - h * top.speed).coerceAtLeast(0f)), q)
            val T = switchFade.coerceIn(0.22f, 2.6f)
            for (i in 0 until 8) {
                val dq = if (i < 3) MarkClipEngine.wrap180(raw[i] - q[i]) else raw[i] - q[i]
                val bv = if (top.t > h * top.speed) dq / h * top.speed * tScale.coerceIn(0f, 1f) else 0f
                val pred = tgt[i] + tgtV[i] * dt
                inDurC[i] = setQuintic(inC[i], pred - base[i], tgtV[i] - bv, tgtA[i].coerceIn(-4000f, 4000f), T)
            }
            // Engine21.1: start at −bdt so this frame evaluates the blend at 0 (it already holds
            // the predicted step; evaluating at bdt counted the velocity twice — a one-frame kick).
            inT = -bdt; inDur = inDurC.max()
        } else {
            for (a in 0..2) { base[a] += MarkClipEngine.wrap180(raw[a] - lastBaseRaw[a]); lastBaseRaw[a] = raw[a] }
            for (i in 3 until 8) base[i] = raw[i]
        }
        inT += bdt
        val inv = 1f / dt.coerceAtLeast(1e-4f)
        for (i in 0 until 8) {
            // Engine21.1 fix (the e21_ui "spasm"): a channel's quintic is only valid up to its OWN
            // (possibly shortened) length; past it the t⁵ term explodes — it was evaluated up to the
            // nominal fade and drove the pitch into a multi-turn whirl.
            val off = if (inT < inDurC[i]) evalQuintic(inC[i], inT.coerceAtLeast(0f)) else 0f
            val x = base[i] + off
            val v = (x - tgt[i]) * inv
            tgtA[i] = (v - tgtV[i]) * inv
            tgtV[i] = v
            tgt[i] = x
        }
        // Keep the unwrapped angles bounded (spins add ±360 per turn).
        for (a in 0..2) if (abs(tgt[a]) > 720f) {
            val k = 360f * kotlin.math.round(tgt[a] / 360f); tgt[a] -= k; base[a] -= k
        }
        var p = poseOf(tgt)
        // Attend: ease the sway out toward a still look, and back (a smooth weight, not a cut).
        attendPose?.let { ap ->
            attendT += dt
            val w = window(attendT / attendDur, 0.25f / attendDur, 0.4f / attendDur) * 0.85f
            p = p.lerpTo(ap, w)
            if (attendT >= attendDur) attendPose = null
        }
        return p
    }

    private fun addGestures(out: BodyPose, dt: Float, pre: Boolean): BodyPose {
        var pitch = out.pitch; var yaw = out.yaw; var roll = out.roll; var posY = out.posY
        var sx = out.squashX; var sy = out.squashY
        val it = adds.iterator()
        while (it.hasNext()) {
            val g = it.next()
            if (g.pre != pre) continue
            g.t += dt
            val u = (g.t / g.dur).coerceIn(0f, 1f)
            val d = g.fn(u, g.t)
            pitch += d.pitch; yaw += d.yaw; roll += d.roll; posY += d.posY
            sx *= d.squashX; sy *= d.squashY
            if (g.t >= g.dur) it.remove()
        }
        val pi = pops.iterator()
        while (!pre && pi.hasNext()) {
            val pp = pi.next()
            pp[0] = (pp[0] + dt / pp[1]).coerceAtMost(1f)
            val sp = startlePose(pp[0])
            val k = pp[2]
            pitch += sp.pitch * k; posY += sp.posY * k
            sx *= 1f + (sp.squashX - 1f) * k; sy *= 1f + (sp.squashY - 1f) * k
            if (pp[0] >= 1f) pi.remove()
        }
        return out.copy(
            pitch = MarkClipEngine.wrap180(pitch), yaw = MarkClipEngine.wrap180(yaw), roll = MarkClipEngine.wrap180(roll),
            posY = posY, squashX = sx, squashY = sy,
        )
    }

    private fun envPose(t: Float, dur: Float, rise: Float, fall: Float, p: BodyPose): BodyPose {
        val w = smootherstep(t / rise) * smootherstep((dur - t) / fall)
        return BodyPose(pitch = p.pitch * w, yaw = p.yaw * w, roll = p.roll * w, posY = p.posY * w,
            squashX = 1f + (p.squashX - 1f) * w, squashY = 1f + (p.squashY - 1f) * w)
    }

    /** The body holds where it is now (a beat that keeps the current pose; gestures ride it). */
    private fun holdHere(name: String, dur: Float, kind: String): Layer =
        Layer(HoldSource(name, poseOf(tgt), dur), 0f, dur, 1f, 0.35f, kind)

    // ------------------------------------------------------------------ beats

    /** Engine19: dominant beat pose (no crossfade). */
    private fun composeNow(): BodyPose = layers.last().pose()

    /** Largest jump of the base target caused by a beat switch (stays ~0: inertialized). */
    internal var handOverDelta = 0f
        private set

    private fun push(lIn: Layer) {
        stackChanged = true
        // Snappy attack: awake beats move in quickly and the output spring shapes the move.
        // Drowsy / sleep stay slow.
        val slow = drowsy || lifecycle == MarkLifecycle.Sleeping
        val fade = if (slow) lIn.fadeIn else (lIn.fadeIn * lerpA(1f, 0.55f)).coerceIn(0.35f, 1.3f)
        layers.clear()
        layers += lIn
        switched = true
        switchFade = fade
        recentKinds.addLast(lIn.kind)
        while (recentKinds.size > 3) recentKinds.removeFirst()
        (lIn.source as? ClipSource)?.let {
            recentClips.addLast(it.name)
            while (recentClips.size > 2) recentClips.removeFirst()
        }
    }

    private fun nextBeat(life: MarkLifecycle, entering: Boolean): Layer {
        val fade = if (entering) range(0.9f, 1.3f) else range(0.75f, 1.2f)
        return when (life) {
            MarkLifecycle.Idle -> idleBeat(fade)
            MarkLifecycle.Listening -> if (rng.nextFloat() < 0.65f || lastKind() != "hold") {
                hold("listen", BodyPose(pitch = range(2f, 5f), yaw = gauss(3f), roll = gauss(2f)), range(1.2f, 3f), fade)
            } else clipBeat("Idle_B", 0.3f, 0.5f, fade, "idleB") ?: hold("listen", BodyPose(pitch = 3f), 3f, fade)
            MarkLifecycle.Thinking, MarkLifecycle.Working, MarkLifecycle.Streaming -> busyBeat(life, fade)
            MarkLifecycle.Finished -> clipBeat(if (rng.nextBoolean()) "Done_A" else "Done_C", 0.4f, 0.55f, fade, "done")
                ?: idleBeat(fade)
            MarkLifecycle.Failed -> clipBeat(if (lastKind() == "blocked") "Blocked_B" else "Blocked_A", 0.6f, 0.8f, fade, "blocked")
                ?: hold("sad", BodyPose(pitch = 12f), 3f, fade)
            MarkLifecycle.Sleeping -> hold("sleep", BodyPose(pitch = range(7f, 11f), roll = gauss(4f)), range(4f, 9f), range(1.4f, 2.2f))
        }
    }

    private fun idleBeat(fade: Float): Layer {
        if (drowsy) {
            return if (rng.nextFloat() < 0.75f) {
                hold("drowsy", BodyPose(pitch = range(5f, 9f), yaw = gauss(4f), roll = gauss(4f)), range(4f, 9f), range(1.4f, 2.2f))
            } else clipBeat("Idle_B", 0.25f, 0.4f, range(1.2f, 1.8f), "idleB", speed = range(0.65f, 0.8f))
                ?: hold("drowsy", BodyPose(pitch = 7f), 5f, 1.6f)
        }
        val last = lastKind()
        val opts = ArrayList<Pair<String, Float>>()
        // Mood: low arousal holds still more, high arousal looks around more; a good
        // mood tilts the head now and then.
        val a = moodArousal.coerceIn(0f, 1f)
        val pos = moodValence.coerceAtLeast(0f)
        if (last != "hold") opts += "hold" to 0.62f * (1.5f - a)
        if (last != "idleB" || recentKinds.count { it == "idleB" } < 2) opts += "idleB" to 0.12f
        if (last != "look") opts += "look" to 0.06f * (0.5f + a)
        if (last != "tilt" && last != "hold") opts += "tilt" to 0.09f * (0.7f + 0.6f * a + pos)
        return when (weighted(opts)) {
            "idleB" -> clipBeat("Idle_B", 0.55f, 1f, fade, "idleB")
            "look" -> {
                val clip = clipLookup("Idle_A")
                if (clip != null) {
                    // Look-around part only (the authored ending is a full spin; spins are
                    // a rare gesture now, not every time Idle_A comes round).
                    val end = range(3.0f, 3.85f)
                    val start = range(0.2f, 0.45f)
                    Layer(ClipSource(clip, range(0.35f, 0.55f), rng.nextBoolean(), end), start, end,
                        range(0.75f, 0.95f), fade, "look")
                } else null
            }
            "tilt" -> {
                val side = if (rng.nextBoolean()) 1f else -1f
                hold("tilt", BodyPose(pitch = range(0f, 3f), yaw = side * range(3f, 7f), roll = side * range(5f, 9f)),
                    range(0.9f, 2.0f), fade, kind = "tilt")
            }
            else -> null
        } ?: hold("settle", BodyPose(pitch = range(0f, 3f), yaw = gauss(3.5f).coerceIn(-7f, 7f), roll = gauss(2.5f).coerceIn(-5f, 5f)),
            range(1.2f, 3.6f) * (1.3f - 0.6f * moodArousal.coerceIn(0f, 1f)), fade)
    }

    // ------------------------------------------------------------ busy repertoire
    //
    // No authored Working/Streaming clips any more: their slow side-to-side sway was
    // the one thing that read as a loop. A busy phase is a stream of small, distinct
    // beats picked by weighted chance with per-kind cooldowns, each with its own
    // random size, side, timing and fade, so nothing lines up into a rhythm.

    /** A look the body beat would like the eyes to make (consumed by the motion state). */
    data class GazeCue(val x: Float, val y: Float, val holdSec: Float, val kind: String = "")

    /** Set when a beat starts that wants the eyes somewhere; cleared on read. */
    var gazeCue: GazeCue? = null
        private set

    fun takeGazeCue(): GazeCue? = gazeCue.also { gazeCue = null }

    private val lastUsed = HashMap<String, Float>()
    private var clock = 0f
    /** Turn side we are currently on (beats prefer to go somewhere new). */
    private var lastSide = 0f

    private fun ago(kind: String) = clock - (lastUsed[kind] ?: -1000f)

    private fun busyBeat(life: MarkLifecycle, fade: Float): Layer {
        val busyFor = lifeSec
        val last = lastKind()
        // Base weights per phase; cooldowns scale them down after use.
        val base: List<Pair<String, Float>> = when (life) {
            MarkLifecycle.Thinking -> listOf(
                "turn" to 0.24f, "lookAround" to 0.24f, "ponder" to 0.09f, "pause" to 0.19f,
                "tilt" to 0.12f, "nod" to 0.05f, "pill" to 0.05f,
            )
            MarkLifecycle.Streaming -> listOf(
                "read" to 0.28f, "turn" to 0.16f, "pause" to 0.18f, "nod" to 0.1f,
                "tilt" to 0.1f, "lookAround" to 0.12f, "bob" to 0.06f,
            )
            else -> listOf(
                "turn" to 0.26f, "lookAround" to 0.2f, "pause" to 0.15f, "ponder" to 0.05f,
                "tilt" to 0.1f, "nod" to 0.07f, "pill" to 0.1f, "bob" to 0.06f, "spin" to 0.02f,
            )
        }
        val cool = mapOf(
            "turn" to 3f, "lookAround" to 7f, "ponder" to 9f, "pause" to 2f, "tilt" to 6f,
            "nod" to 8f, "pill" to 14f, "read" to 3f, "bob" to 12f, "spin" to 45f,
        )
        val opts = base.mapNotNull { (k, w) ->
            if (k == last && k != "pause") return@mapNotNull null
            if (k == "spin" && (busyFor < 6f || ago("spin") < 45f)) return@mapNotNull null
            // The activity pill only gets attention once the work has run a while.
            if (k == "pill" && busyFor < 5f) return@mapNotNull null
            val c = cool[k] ?: 3f
            val a = ago(k)
            val f = if (a >= c) 1f else (a / c).coerceIn(0.05f, 1f)
            // Two pauses in a row are fine now and then, three never.
            val extra = if (k == "pause" && recentKinds.takeLast(2).all { it == "pause" }) 0f else 1f
            (k to w * f * f * extra).takeIf { it.second > 0f }
        }.ifEmpty { listOf("pause" to 1f) }
        val kind = weighted(opts)
        lastUsed[kind] = clock
        val side = pickSide()
        val layer = when (kind) {
            "turn" -> {
                // A real turn: ease to an angle, settle there a moment.
                val yaw = side * range(10f, 20f)
                val p = BodyPose(pitch = range(-1f, 4f), yaw = yaw, roll = -side * range(0f, 4f))
                gazeCue = GazeCue(side * range(0.3f, 0.7f), range(-0.05f, 0.25f), range(0.5f, 1.2f), "turn")
                hold("turn", p, logRange(0.6f, 1.8f), range(0.55f, 1.1f), kind = "turn")
            }
            "lookAround" -> keyframed("lookAround", fade) {
                // 2–4 stops at irregular places and irregular times.
                // 2–3 stops; consecutive stops never more than ~16° apart (no head shake).
                val stops = 2 + rng.nextInt(2)
                var y = side * range(6f, 14f)
                List(stops) { k ->
                    if (k > 0) {
                        val step = range(7f, 14f)
                        y = if (rng.nextFloat() < 0.55f) y - kotlin.math.sign(y) * step else y + kotlin.math.sign(y) * step * 0.5f
                        y = y.coerceIn(-16f, 16f)
                    }
                    BodyPose(pitch = range(0f, 5f), yaw = y, roll = gauss(2f)) to logRange(0.55f, 1.2f)
                }
            }
            "ponder" -> {
                // Thoughtful: head a little up and to one side, eyes away (either side).
                val ps = if (rng.nextBoolean()) 1f else -1f
                val p = BodyPose(pitch = -range(1f, 4f), yaw = ps * range(6f, 16f), roll = -ps * range(3f, 8f))
                gazeCue = GazeCue(ps * range(0.35f, 0.65f), -range(0.05f, 0.25f), range(0.7f, 1.4f), "ponder")
                hold("ponder", p, logRange(0.8f, 1.8f), range(0.8f, 1.3f), kind = "ponder")
            }
            "tilt" -> hold("tiltB", BodyPose(pitch = range(0f, 3f), yaw = side * range(2f, 8f), roll = side * range(5f, 10f)),
                logRange(0.6f, 1.5f), range(0.6f, 1f), kind = "tilt")
            "pill" -> {
                // Glance down-right at the activity pill / timeline, as if checking on it.
                val p = BodyPose(pitch = range(6f, 11f), yaw = range(4f, 12f), roll = -range(0f, 3f))
                gazeCue = GazeCue(range(0.15f, 0.45f), range(0.45f, 0.7f), range(0.7f, 1.4f), "pill")
                hold("pill", p, logRange(0.7f, 1.5f), range(0.6f, 0.95f), kind = "pill")
            }
            "read" -> {
                // Following the text as it arrives: down, drifting along a line or two.
                val y0 = range(6f, 10f)
                gazeCue = GazeCue(range(-0.1f, 0.35f), range(0.35f, 0.55f), range(0.6f, 1.3f), "read")
                keyframed("read", range(0.5f, 0.8f)) {
                    var x = range(-6f, -1f)
                    List(1 + rng.nextInt(3)) {
                        x += range(3f, 9f)
                        BodyPose(pitch = y0 + gauss(1f), yaw = x, roll = gauss(1.5f)) to logRange(0.4f, 1.0f)
                    }
                }
            }
            "nod" -> {
                // Nodding along: one or two smooth nods (the second smaller), eyes leading —
                // added on top of wherever the head is (never back to a rest pose first).
                val d = range(0.6f, 0.78f)
                val deep = range(4f, 7f)
                val twice = rng.nextFloat() < 0.35f
                val total = d * if (twice) 1.85f else 1f
                gazeCue = GazeCue(0f, 0.25f, total * 0.5f, "nod")
                adds += Add("nodB", total) { u, _ ->
                    val p = if (!twice) springNodPose(u, deep)
                    else if (u < 0.5f) springNodPose(u * 2f, deep) else springNodPose(u * 2f - 1f, deep * 0.6f)
                    p.copy(yaw = side * 1.5f * kotlin.math.abs(p.pitch / deep))
                }
                holdHere("nodHold", total + 0.2f, "nod")
            }
            "bob" -> {
                // A small "got it" bounce (squash, not a size change), on the current pose.
                val d = range(0.55f, 0.8f)
                adds += Add("bob", d) { u, _ ->
                    val k = sin(PI.toFloat() * u).let { it * it }
                    BodyPose(posY = -0.03f * k, squashX = 1f - 0.03f * k, squashY = 1f + 0.03f * k, pitch = -2f * k)
                }
                holdHere("bobHold", d + 0.2f, "bob")
            }
            "spin" -> {
                val d = range(1.4f, 1.9f)
                adds += Add("spinB", d) { u, _ ->
                    val e = easeInOutCubic(u)
                    BodyPose(pitch = sin(PI.toFloat() * u) * 3f, yaw = side * 360f * e, roll = -side * sin(PI.toFloat() * u) * 4f)
                }
                holdHere("spinHold", d + 0.2f, "spin")
            }
            else -> hold("pause", BodyPose(pitch = range(0f, 4f), yaw = gauss(3f).coerceIn(-6f, 6f), roll = gauss(2f).coerceIn(-4f, 4f)),
                logRange(0.5f, 1.8f), range(0.7f, 1.3f), kind = "pause")
        }
        return layer
    }

    /** Prefer the other side, but not always (a run of same-side beats is natural too). */
    private fun pickSide(): Float {
        val s = if (lastSide == 0f) (if (rng.nextBoolean()) 1f else -1f)
        else if (rng.nextFloat() < 0.68f) -lastSide else lastSide
        lastSide = s
        return s
    }

    /** Log-uniform: short values common, long ones possible. */
    private fun logRange(a: Float, b: Float): Float =
        (kotlin.math.ln(a) + (kotlin.math.ln(b) - kotlin.math.ln(a)) * rng.nextFloat()).let { kotlin.math.exp(it) }

    /**
     * Stops reached with eased moves and held; total length is the sum. Moves take a
     * random 35–55 % of each segment so the pace keeps changing.
     */
    private fun keyframed(name: String, fade: Float, build: () -> List<Pair<BodyPose, Float>>): Layer {
        val sc = lerpA(1.9f, 1.2f); val am = lerpA(0.55f, 0.85f)
        val stops = build().map { (p, d) -> p.copy(pitch = p.pitch * am, yaw = p.yaw * am, roll = p.roll * am) to (d * sc).let { if (p.yaw > 3f && p.pitch < -2f) it.coerceAtMost(0.9f) else it } }
        val starts = FloatArray(stops.size)
        val moveFrac = FloatArray(stops.size) { if (drowsy) range(0.35f, 0.55f) else range(0.16f, 0.28f) }
        var acc = 0f
        for (i in stops.indices) { starts[i] = acc; acc += stops[i].second }
        val total = acc
        val src = CalmProceduralSource(name, total) { u ->
            val t = u * total
            val i = (0 until stops.size).lastOrNull { starts[it] <= t } ?: 0
            val from = if (i == 0) stops[0].first else stops[i - 1].first
            val seg = stops[i].second
            val k = ((t - starts[i]) / (seg * moveFrac[i])).coerceIn(0f, 1f)
            if (i == 0) stops[0].first else from.lerpTo(stops[i].first, smootherstep(k))
        }
        return Layer(src, 0f, total, 1f, fade, name)
    }

    private fun clipBeat(
        name: String,
        ampMin: Float,
        ampMax: Float,
        fade: Float,
        kind: String,
        // Authored clips play a little brisker than recorded (they read sluggish on a small avatar).
        speed: Float = range(0.8f, 1.0f),
    ): Layer? {
        val clip = clipLookup(name) ?: return null
        val dur = clip.duration
        val probe = ClipSource(clip, 1f, false, dur)
        // Start at the top or at a calm point in the first third; play to the end
        // (or stop a little early — the crossfade carries the motion out).
        val starts = probe.calmStarts(dur * 0.35f)
        val start = if (starts.isNotEmpty() && rng.nextFloat() < 0.5f) starts[rng.nextInt(starts.size)] else 0f
        val end = if (rng.nextFloat() < 0.35f) max(start + dur * 0.5f, dur * range(0.82f, 0.95f)) else dur
        return Layer(ClipSource(clip, range(ampMin, ampMax), rng.nextBoolean(), end), start, end, speed, fade, kind)
    }

    private fun hold(name: String, p: BodyPose, dur: Float, fade: Float, kind: String = "hold"): Layer {
        // Human pacing: calm holds last ~2× longer; turns shrink with low energy.
        var d = dur * lerpA(2.3f, 1.3f)
        // Up-right (screen) is a glance, never a resting pose.
        if (p.yaw > 3f && p.pitch < -2f) d = d.coerceAtMost(0.9f)
        val a = lerpA(0.55f, 0.85f)
        return Layer(HoldSource(name, p.copy(pitch = p.pitch * a, yaw = p.yaw * a, roll = p.roll * a), d), 0f, d, 1f, fade, kind)
    }

    private fun lastKind(): String? = recentKinds.lastOrNull()

    private fun weighted(opts: List<Pair<String, Float>>): String {
        val total = opts.sumOf { it.second.toDouble() }.toFloat()
        var r = rng.nextFloat() * total
        for ((k, w) in opts) {
            r -= w
            if (r <= 0f) return k
        }
        return opts.last().first
    }

    private fun range(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
    private fun gauss(sd: Float): Float {
        // Irwin–Hall approximation — no java.util.Random dependency for tests.
        var s = 0f
        repeat(4) { s += rng.nextFloat() }
        return (s - 2f) * sd * 1.73f
    }

    // ----------------------------------------------------------------- filter

    /** A startle pop is playing (added on top of the filtered pose). */
    val startling: Boolean get() = pops.isNotEmpty()

    private fun filter(p: BodyPose, dt: Float): BodyPose {
        // Any direction is allowed (no up-right suppression).
        val raw = floatArrayOf(p.pitch, p.yaw, p.roll)
        if (!filterReady) {
            for (a in 0..2) {
                rawUnwrapped[a] = raw[a]; lastRaw[a] = raw[a]
            }
        } else {
            for (a in 0..2) {
                rawUnwrapped[a] += MarkClipEngine.wrap180(raw[a] - lastRaw[a])
                lastRaw[a] = raw[a]
            }
        }
        // Engine21.1 guard: the output may trail a spin by at most about one turn. A bigger gap
        // can only come from a corrupt input (wraps counted the wrong way) and used to make it
        // whirl at the turn-speed cap for seconds; drop whole extra turns instead.
        if (filterReady) for (a in 0..2) {
            while (rawUnwrapped[a] - fx[a] > 540f) rawUnwrapped[a] -= 360f
            while (rawUnwrapped[a] - fx[a] < -540f) rawUnwrapped[a] += 360f
        }
        val target = floatArrayOf(
            rawUnwrapped[0], rawUnwrapped[1], rawUnwrapped[2],
            p.posY, p.squashX, p.squashY, p.scale, p.lidOpen,
        )
        if (!filterReady) {
            for (i in target.indices) {
                fx[i] = target[i]; fv[i] = 0f
            }
            filterReady = true
        } else {
            var rem = dt
            while (rem > 1e-6f) {
                val h = min(rem, 1f / 120f)
                for (i in target.indices) {
                    val w = when {
                        i == 7 -> 30f
                        drowsy -> OMEGA_DROWSY
                        i >= 3 -> if (playful) 20f else 14f
                        else -> lerpA(8f, 12f) * (if (playful) 1.15f else 1f)
                    }
                    // Angles a little underdamped (snappy, small overshoot); the bob is
                    // bouncier (follow-through); squash / scale / lids critically damped.
                    val z = when {
                        drowsy || i >= 4 -> 1f
                        // Critically damped unless in a playful mood (then a small overshoot).
                        i < 3 -> if (playful) ZETA_TURN else 1f
                        else -> if (playful) ZETA_BOUNCE else 1f
                    }
                    val a = w * w * (target[i] - fx[i]) - 2f * z * w * fv[i]
                    fv[i] += a * h
                    // Desktop whip-spins (Working_A/F flips, Working_B turn) peak at
                    // 60–80°/frame; cap turn speed so they read as a quick spin.
                    if (i < 3) fv[i] = fv[i].coerceIn(-MAX_TURN_DEG_S, MAX_TURN_DEG_S)
                    fx[i] += fv[i] * h
                }
                rem -= h
            }
        }
        // Keep the unwrapped accumulators bounded (spins add ±360 per turn).
        for (a in 0..2) {
            if (abs(fx[a]) > 720f) {
                val k = 360f * kotlin.math.round(fx[a] / 360f)
                fx[a] -= k; rawUnwrapped[a] -= k
            }
        }
        // Secondary motion: a quick turn stretches the body a touch along the move.
        // Low-passed so a velocity kick never steps the squash.
        val spd = kotlin.math.hypot(fv[0], fv[1])
        val stretchTarget = (spd * 0.00005f).coerceAtMost(0.03f)
        stretchSm += (stretchTarget - stretchSm) * (1f - kotlin.math.exp(-dt / 0.06f))
        val stretch = stretchSm
        return BodyPose(
            pitch = MarkClipEngine.wrap180(fx[0]),
            yaw = MarkClipEngine.wrap180(fx[1]),
            roll = MarkClipEngine.wrap180(fx[2]),
            posY = fx[3],
            squashX = fx[4] * (1f - stretch * 0.6f),
            squashY = fx[5] * (1f + stretch),
            // Live character size is independent of authored camera zoom.
            scale = 1f,
            lidOpen = fx[7].coerceIn(0f, 1f),
        )
    }

    companion object {
        /** Spring stiffness for the output filter (rad/s): ~60 ms settle, no lag you can see. */
        const val OMEGA = 17f
        const val OMEGA_DROWSY = 9f
        /** Damping ratio for turns: < 1 gives a small, quick overshoot. */
        const val ZETA_TURN = 0.7f
        const val ZETA_BOUNCE = 0.6f
        /** Awake beat crossfades are this fraction of their authored length. */
        const val FADE_SNAP = 0.3f
        /** Counter-move velocity per degree of upcoming turn (deg/s per deg). */
        const val ANTICIPATION = 1.8f
        /** Fastest the body may turn (deg/s) — a full spin still takes ≥ 0.5 s. */
        const val MAX_TURN_DEG_S = 700f
        /** A busy phase lasts at least this long, even if the reply came back instantly. */
        const val MIN_BUSY_SEC = 1.3f
        /** Longest we wait for a busy clip to reach a calm pose before crossfading anyway. */
        const val MAX_FINISH_WAIT = 2.6f

        fun isBusy(l: MarkLifecycle) =
            l == MarkLifecycle.Thinking || l == MarkLifecycle.Working || l == MarkLifecycle.Streaming

        /**
         * Gesture curve on u ∈ 0…1, starting and ending at exactly 0: an optional
         * counter-move of size [antic] in the first 15 %, a fast swing to 1, then a
         * rebound of about [rebound] that settles (damped, windowed to end at 0).
         */
        fun snapCurve(u: Float, antic: Float, rebound: Float): Float {
            val a = 0.15f
            if (antic > 0f && u < a) return -antic * sin(PI.toFloat() * u / a)
            val v = if (antic > 0f) (u - a) / (1f - a) else u
            // Fast ease-out attack to 1 …
            if (v < 0.3f) return sin(PI.toFloat() * (v / 0.3f) / 2f)
            // … then a damped swing through −rebound, ending exactly at 0.
            val x = (v - 0.3f) / 0.7f
            val k = -kotlin.math.ln(rebound.coerceIn(0.02f, 0.9f)) / 0.667f
            return kotlin.math.exp(-k * x) * kotlin.math.cos(1.5f * PI.toFloat() * x)
        }

        /**
         * Natural nod on u ∈ 0…1 (0 → 1 → 0): ease-in-out down, ease-in-out back up
         * a touch past rest (−[settle]), then a soft settle. One smooth arc, no ringing.
         */
        fun nodCurve(u: Float, settle: Float = 0.12f): Float {
            val x = u.coerceIn(0f, 1f)
            fun ease(v: Float) = 0.5f - 0.5f * kotlin.math.cos(PI.toFloat() * v.coerceIn(0f, 1f))
            return when {
                x < 0.4f -> ease(x / 0.4f)
                x < 0.75f -> 1f - (1f + settle) * ease((x - 0.4f) / 0.35f)
                else -> -settle * (1f - ease((x - 0.75f) / 0.25f))
            }
        }

        /**
         * Springy nod on u ∈ 0…1 (starts and ends at exactly 0): a quick dip to 1, then a
         * damped spring through an overshoot back to rest. Squash rides along: the body
         * compresses on the way down and stretches on the rebound.
         */
        fun springNod(u: Float): Float {
            val x = u.coerceIn(0f, 1f)
            val a = 0.26f
            if (x < a) return sin(PI.toFloat() * 0.5f * x / a).let { it * it }
            val v = (x - a) / (1f - a)
            return kotlin.math.exp(-3.2f * v) * kotlin.math.cos(2.6f * PI.toFloat() * v) * (1f - v * v)
        }

        fun springNodPose(u: Float, deep: Float): BodyPose {
            val k = springNod(u)
            return BodyPose(
                pitch = deep * k,
                posY = 0.014f * k,
                squashX = 1f + 0.05f * k,
                squashY = 1f - 0.065f * k,
            )
        }

        /** Startle pop: stretch up and back fast, squash on landing, springy settle. */
        fun startlePose(u: Float): BodyPose {
            val x = u.coerceIn(0f, 1f)
            // Pop channel: rises in ~70 ms, then a damped spring that ends at 0.
            val pop = if (x < 0.09f) sin(PI.toFloat() * 0.5f * x / 0.09f)
                else { val v = (x - 0.09f) / 0.91f; kotlin.math.exp(-3.6f * v) * kotlin.math.cos(2.4f * PI.toFloat() * v) * (1f - v * v) }
            return BodyPose(
                pitch = -15f * pop,
                posY = -0.06f * pop,
                squashX = 1f - 0.1f * pop,
                squashY = 1f + 0.15f * pop,
            )
        }

        /** Startle pop channel alone (0 at both ends). */
        fun startlePop(u: Float): Float {
            val x = u.coerceIn(0f, 1f)
            return if (x < 0.09f) sin(PI.toFloat() * 0.5f * x / 0.09f)
                else { val v = (x - 0.09f) / 0.91f; kotlin.math.exp(-3.6f * v) * kotlin.math.cos(2.4f * PI.toFloat() * v) * (1f - v * v) }
        }

        /** 0 → 1 → 0 window on u with smootherstep edges of [a] and [b] (fractions of the span). */
        fun window(u: Float, a: Float, b: Float): Float =
            smootherstep(u / a.coerceAtLeast(1e-3f)) * smootherstep((1f - u) / b.coerceAtLeast(1e-3f))

        /**
         * Quintic inertialization (pos, vel, acc → 0 at [T]); the offset never overshoots
         * zero on its way home (Bollo's clamps). Stored as (x0, v0, a0, A, B, C).
         */
        /** Inertialization blend x0 → 0 over ≤ [Tin] s; returns the length actually used. */
        fun setQuintic(c: FloatArray, x0In: Float, v0In: Float, a0In: Float, Tin: Float): Float {
            val s = if (x0In < 0f) -1f else 1f
            val x0 = x0In * s; var v0 = v0In * s; var a0 = a0In * s
            var T = Tin
            if (v0 < 0f && x0 > 1e-5f) T = min(T, -5f * x0 / v0).coerceAtLeast(0.12f)
            a0 = max(a0, (-8f * v0 * T - 20f * x0) / (T * T))
            val T2 = T * T; val T3 = T2 * T
            c[0] = x0 * s; c[1] = v0 * s; c[2] = a0 * s
            c[3] = -(a0 * T2 + 6f * v0 * T + 12f * x0) / (2f * T3 * T2) * s
            c[4] = (3f * a0 * T2 + 16f * v0 * T + 30f * x0) / (2f * T2 * T2) * s
            c[5] = -(3f * a0 * T2 + 12f * v0 * T + 20f * x0) / (2f * T3) * s
            return T
        }

        fun evalQuintic(c: FloatArray, t: Float): Float =
            (((((c[3] * t + c[4]) * t + c[5]) * t + c[2] * 0.5f) * t + c[1]) * t + c[0])

        fun smootherstep(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * t * (t * (t * 6f - 15f) + 10f)
        }

        fun easeInOutSine(x: Float): Float = (0.5f - 0.5f * kotlin.math.cos(Math.PI.toFloat() * x.coerceIn(0f, 1f)))

        fun easeInOutCubic(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
        }

        /**
         * Upward pitch (negative) is the Grok brand cue, worst when combined with a
         * right turn. Soften continuously so a pose never snaps.
         */
        fun softenUp(pitch: Float, yaw: Float): Float {
            if (pitch >= 0f) return pitch
            val right = ((yaw - 6f) / 20f).coerceIn(0f, 1f)
            val k = 0.45f * (1f - 0.5f * right * right * (3f - 2f * right))
            return pitch * k
        }
    }
}
