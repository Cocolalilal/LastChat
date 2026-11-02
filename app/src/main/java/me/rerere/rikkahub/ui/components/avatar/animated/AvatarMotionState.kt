package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

val LocalAvatarMotionHints = compositionLocalOf { AvatarMotionHints() }

/**
 * Whether animated avatars below should move. Chat sets it to false for every message
 * except the newest one (per role), so older avatars rest as still marks.
 */
val LocalAvatarAnimate = compositionLocalOf { true }

/**
 * Everything the renderer needs for one frame. Gaze is in screen convention
 * (x: + right, y: + down, both -1..1); lead is the eyes' offset on the body surface
 * (normalized face units, y down) so eyes move a beat before the head does.
 */
data class MotionFrame(
    val body: BodyPose = BodyPose.REST,
    val headLookX: Float = 0f,
    val headLookY: Float = 0f,
    val leadX: Float = 0f,
    val leadY: Float = 0f,
    /** Final eye openness incl. blink / drowsiness (0 shut … 1 open). */
    val lid: Float = 1f,
    val exprFrom: String = "Neutral",
    val exprTo: String = "Neutral",
    /** Eased 0..1 morph progress from [exprFrom] to [exprTo]. */
    val exprT: Float = 1f,
    /** Engine17: optional third layer blended over the pair (interrupted morph). */
    val exprTop: String? = null,
    val exprTopT: Float = 0f,
    /**
     * Engine19: the face as a weight vector over the expression basis (Σ = 1, stable name
     * order). When set, the renderer blends these shapes directly and ignores the legacy
     * pair fields above (kept for logs / debug).
     */
    val exprWeights: List<Pair<String, Float>>? = null,
    val lifecycle: MarkLifecycle = MarkLifecycle.Idle,
    val eyeGap: Float = 0.94f,
    val eyeScale: Float = 1f,
    /** Engine21.1: microscopic reopen settle (fraction of eye height, ±~1.5 %), 0 at rest. */
    val eyePop: Float = 0f,
    val timeSec: Float = 0f,
    /** Parametric per-eye shaping on top of the expression (see [FaceRig]). */
    val face: FaceParams = FaceParams.ID,
    /** Character-moment spin (deg), added after the per-shape turn cap (see drawMark). */
    val spinYaw: Float = 0f,
    /** Engine16: spin about other axes (deg): forward flip / cartwheel / tilted-axis paths. */
    val spinPitch: Float = 0f,
    val spinRoll: Float = 0f,
)

/**
 * Gaze (engine11): eyes saccade (critically damped, ~60 ms, no overshoot) or drift by
 * smooth pursuit; the head follows only bigger shifts, ~100 ms later, critically damped
 * (springy only in a playful mood). Fixations come from [IdlePlanner] (human timing).
 */
internal class GazeDirector(
    private val rng: Random,
    private val planner: IdlePlanner,
    private val temper: Temperament,
) {
    private var tx = 0f
    private var ty = 0f
    /** Debug: counts planner fixations (one log line each). */
    var fixSeq = 0; private set
    var lastFix: Fixation? = null; private set
    var eyeX = 0f; private set
    var eyeY = 0f; private set
    var evx = 0f; private set
    var evy = 0f; private set
    private var htx = 0f
    private var hty = 0f
    private var pendingHx = 0f
    private var pendingHy = 0f
    private var headDelay = 0f
    private var postponed = 0f
    var headX = 0f; private set
    var headY = 0f; private set
    private var hvx = 0f
    private var hvy = 0f
    private var holdLeft = 1.5f
    private var focusLeft = 0f
    private var focusPriority = 0
    private var returnToCenter = false
    private var microLeft = 2f
    private var mx = 0f
    private var my = 0f
    // Engine17: fixational drift — slow, smooth, never-repeating wander (incommensurate
    // sines with random phases), so a hold is alive instead of a dead plateau.
    private var driftClock = 0f
    private val driftPh = FloatArray(6) { rng.nextFloat() * 6.2832f }
    // Smooth pursuit.
    private var pursuitT = 1f
    private var pursuitDur = 0f
    private var p0x = 0f; private var p0y = 0f; private var p1x = 0f; private var p1y = 0f

    /** Playful mood: head moves get a small overshoot (set each tick by the owner). */
    var playful = false
    /** Last fixation kind (debug / behaviour log). */
    var kind = "rest"; private set

    /** Set when a big gaze jump happens (blink sometimes rides it). */
    var bigShift = false; private set
    /** Attention event from the idle episode ("notice" / "ack"), consumed by the owner. */
    private var event: String? = null
    fun takeEvent(): String? = event.also { event = null }
    val episode: String get() = planner.episodeName

    fun focus(gx: Float, gy: Float, holdSec: Float, priority: Int, withHead: Boolean = true, travel: Float = 0f, force: Boolean = false) {
        if (!force && priority < focusPriority && focusLeft > 0f) return
        // Engine17.1: a glance must go somewhere. A target within MIN_GLANCE of rest while
        // the eyes are already resting is not worth a look (it read as a twitch + snap-back).
        val dRest = hypot(gx - REST_X, gy - REST_Y)
        if (!force && travel <= 0f && dRest < MIN_GLANCE * 0.8f && hypot(tx - REST_X, ty - REST_Y) < 0.12f) {
            auditNearRestRaw++; return
        }
        if (travel <= 0f && dRest >= MIN_GLANCE * 0.8f) { auditGlances++; auditMinGlance = min(auditMinGlance, dRest) }
        focusPriority = priority
        // Up-right is only ever a short glance.
        val upRight = gx > 0.15f && gy < -0.08f
        // Hold proportional to the move (a far look is held longer); up-right stays a glance.
        focusLeft = if (upRight) holdSec.coerceAtMost(0.9f) else holdSec.coerceAtLeast(0.35f + 1.6f * dRest)
        if (dRest > 0.2f) returnToCenter = true // come back as a glide, never a snap
        if (upRight) returnToCenter = true
        returning = false
        if (travel > 0f) {
            // Smooth pursuit (a pan), the head following along.
            p0x = tx; p0y = ty; p1x = gx.coerceIn(-1f, 1f); p1y = gy.coerceIn(-1f, 1f)
            pursuitDur = travel; pursuitT = 0f; pending = false
            focusLeft += travel
        } else {
            pursuitT = 1f
            eyesOnly = force && !withHead
            setTarget(gx, gy, withHead)
            eyesOnly = false
        }
    }
    /** Engine19: an eyes-only look (poke reflex): the head never joins, however far. */
    private var eyesOnly = false

    fun releaseFocus(awayX: Float? = null) {
        focusLeft = 0f
        focusPriority = 0
        if (awayX != null) {
            pursuitT = 1f
            setTarget(awayX, rng.nextFloat() * 0.2f, true)
            holdLeft = 0.8f + rng.nextFloat() * 0.8f
            returnToCenter = true
        } else {
            holdLeft = 0.4f + rng.nextFloat() * 0.6f
        }
    }

    val focused: Boolean get() = focusLeft > 0f

    // Engine20: intersaccadic refractory. A new self-paced look that arrives while the last
    // shift is still in flight waits for it to land (≥ 0.24 s), so a look is never redirected
    // mid-way (a visible step / reversal). An eyes-only reflex (poke) is never delayed.
    private var sinceShift = 9f
    private var pendX = 0f; private var pendY = 0f; private var pendHead = false; private var pending = false
    private fun setTarget(x: Float, y: Float, withHead: Boolean) {
        if (!eyesOnly && sinceShift < SHIFT_REFRACTORY && hypot(x - tx, y - ty) > 0.06f) {
            pendX = x; pendY = y; pendHead = withHead; pending = true; return
        }
        // Engine21: a big self-paced look against the head's still-running turn waits for the
        // head to slow (its momentum would drag the look backwards first: a visible reversal).
        if (!eyesOnly && hypot(hvx, hvy) > 0.3f && hypot(x - tx, y - ty) > 0.4f && (x - tx) * hvx + (y - ty) * hvy < 0f) {
            pendX = x; pendY = y; pendHead = withHead; pending = true; return
        }
        pending = false
        sinceShift = 0f
        val gx = x.coerceIn(-1f, 1f)
        val gy = y.coerceIn(-1f, 1f)
        val jump = hypot(gx - tx, gy - ty)
        returning = false // a new saccade ends any return glide (audit)
        pursuitT = 1f // …and the glide itself (it would overwrite the new target next frame)
        if (jump > 0.4f) bigShift = true
        tx = gx; ty = gy
        // Eye–head coordination: small shifts are eyes only; bigger ones bring the head
        // ~75 % of the way, starting ~80–140 ms after the eyes.
        val headJoins = withHead || (jump > 0.45f && !eyesOnly)
        // An eyes-only snap of attention also cancels a head turn that had not started yet.
        // …and a head turn already under way settles where it is (it does not carry on across
        // centre while the eyes go to the finger).
        if (eyesOnly) {
            htx = headX + hvx * 0.06f; hty = headY + hvy * 0.06f
            headDelay = 0f; pendingHx = htx; pendingHy = hty
        }
        if (headJoins) {
            // Engine20: one coordinated shift — the eyes flick first but the head sets off
            // almost with them (20–45 ms), so the look travels A → B in one go instead of
            // eye-step, pause, head-step. The eyes then counter-roll as the head arrives.
            pendingHx = gx * 0.8f; pendingHy = gy * 0.72f
            headDelay = 0.02f + rng.nextFloat() * 0.025f
        }
    }

    fun update(dt: Float, life: MarkLifecycle, drowsy: Boolean, allowIdle: Boolean = true) {
        bigShift = false
        sinceShift += dt
        if (pending && sinceShift >= SHIFT_REFRACTORY && pursuitT >= 1f) setTarget(pendX, pendY, pendHead)
        if (focusLeft > 0f) {
            focusLeft -= dt
            if (focusLeft <= 0f) {
                focusPriority = 0
                holdLeft = if (returnToCenter) 0.15f else 0.3f + rng.nextFloat() * 0.5f
            }
        } else {
            holdLeft -= dt
            if (holdLeft <= 0f && allowIdle) chooseFixation(life, drowsy)
        }
        // Smooth pursuit: target glides with an ease-in-out.
        if (returning) {
            auditReturnPeak = max(auditReturnPeak, hypot(evx, evy))
            if (pursuitT >= 1f) returning = false
        }
        if (pursuitT < 1f) {
            pursuitT = (pursuitT + dt / pursuitDur).coerceAtMost(1f)
            val e = MotionVocab.easeInOutSine(pursuitT)
            tx = p0x + (p1x - p0x) * e; ty = p0y + (p1y - p0y) * e
            htx = tx * 0.75f; hty = ty * 0.7f
        }
        // Micro-saccades: small, irregular (1.2–4 s), eyes only.
        microLeft -= dt
        if (microLeft <= 0f) {
            microLeft = 1.2f + rng.nextFloat() * 2.8f
            val amp = if (drowsy) 0.008f else 0.022f
            mx = (rng.nextFloat() - 0.5f) * 2f * amp
            my = (rng.nextFloat() - 0.5f) * 2f * amp * 0.7f
        }
        if (headDelay > 0f) {
            headDelay -= dt
            if (headDelay <= 0f) { htx = pendingHx; hty = pendingHy }
        }
        driftClock += dt
        val dc = driftClock; val dk = if (drowsy) 0.5f else 1f
        val dex = dk * (0.006f * sin(0.83f * dc + driftPh[0]) + 0.004f * sin(1.97f * dc + driftPh[1]))
        val dey = dk * (0.005f * sin(0.61f * dc + driftPh[2]) + 0.003f * sin(1.43f * dc + driftPh[3]))
        val dhx = 0.014f * sin(0.37f * dc + driftPh[4]) + 0.006f * sin(0.89f * dc + driftPh[5])
        val dhy = 0.010f * sin(0.29f * dc + driftPh[5]) + 0.005f * sin(0.73f * dc + driftPh[0])
        val sacc = MotionVocab[Action.Saccade]
        val headSpec = MotionVocab[if (playful) Action.HeadTurnPlayful else Action.HeadTurn]
        val eyeW = if (drowsy) 12f else sacc.omega
        val headW = if (drowsy) 3.5f else headSpec.omega * temper.tempo
        val headZ = if (drowsy) 1f else headSpec.zeta
        var rem = dt
        while (rem > 1e-6f) {
            val h = min(rem, 1f / 120f)
            // Engine20: ζ 0.8 — fast start, eased landing with a ~1.5 % overshoot-and-settle.
            val ax = eyeW * eyeW * (tx + mx + dex - eyeX) - 1.6f * eyeW * evx
            val ay = eyeW * eyeW * (ty + my + dey - eyeY) - 1.6f * eyeW * evy
            evx += ax * h; evy += ay * h
            eyeX += evx * h; eyeY += evy * h
            val bx = headW * headW * (htx + dhx - headX) - 2f * headZ * headW * hvx
            val by = headW * headW * (hty + dhy - headY) - 2f * headZ * headW * hvy
            hvx += bx * h; hvy += by * h
            headX += hvx * h; headY += hvy * h
            rem -= h
        }
    }

    // ---- Engine17.1 gaze audit (read by tests) ----
    var auditNearRestRaw = 0; private set   // planner asked for a near-rest "interest" target
    var auditNearRest = 0; private set      // …that still went out near rest (must stay 0)
    var auditGlances = 0; private set
    /** Engine18: short-term memory (no repeated looks) and its clock. */
    var memory: ShortTermMemory? = null
    var clock: () -> Float = { 0f }
    var auditRepeatAvoided = 0; private set
    var auditMinGlance = 9f; private set
    var auditReturns = 0; private set
    var auditReturnPeak = 0f; private set   // max eye speed (gaze units/s) during return glides
    private var returning = false

    /** Back to the rest point as an eased glide (never a snap-back), slower for longer trips. */
    /**
     * Engine21.1: snap the (hidden) gaze to rest. Only called while the avatar is fully settled
     * (the gaze is multiplied by 0 on screen), so nothing visible moves — but when it resumes,
     * the look starts from rest instead of a stale off-centre head swinging back into view
     * (head × rising weight read as a look one way, then the other).
     */
    fun snapRest() {
        tx = REST_X; ty = REST_Y; eyeX = REST_X; eyeY = REST_Y; evx = 0f; evy = 0f
        htx = 0f; hty = 0f; pendingHx = 0f; pendingHy = 0f; headDelay = 0f
        headX = 0f; headY = 0f; hvx = 0f; hvy = 0f
        pending = false; returning = false; pursuitT = 1f
    }

    private fun glideHome(x: Float, y: Float) {
        val d = hypot(x - tx, y - ty)
        if (d < 0.06f) { setTarget(x, y, true); return }
        p0x = tx; p0y = ty; p1x = x; p1y = y
        pursuitDur = 0.55f + 1.1f * d.coerceAtMost(0.55f); pursuitT = 0f; pending = false
        returning = true; auditReturns++
    }

    private fun chooseFixation(life: MarkLifecycle, drowsy: Boolean) {
        fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        // Engine20: a self-started look waits for the head to finish its last move (a person
        // doesn't start a new look while still turning); otherwise the head's momentum drags
        // the new look backwards first.
        if (hypot(hvx, hvy) > 0.12f && postponed < 0.8f) { postponed += 0.05f; holdLeft = 0.05f; return }
        postponed = 0f
        if (returnToCenter) {
            returnToCenter = false
            kind = "return"
            glideHome(rr(-0.08f, 0.08f), rr(-0.03f, 0.06f))
            holdLeft = pursuitDur + rr(2f, 4.5f)
            return
        }
        var f = planner.next(life, temper, drowsy)
        // Engine17.1: an interest glance must be a meaningful look somewhere — no near-rest
        // micro targets (tiny drifts during holds are separate). Push it out along its own
        // direction, never into the up-right.
        if (f.kind in GLANCE_KINDS && f.travel <= 0f) {
            val dx = f.x - REST_X; val dy = f.y - REST_Y
            val d = hypot(dx, dy)
            if (d < MIN_GLANCE) {
                auditNearRestRaw++
                val ang = if (d > 0.02f) kotlin.math.atan2(dy, dx) else (if (rng.nextBoolean()) 0f else PI.toFloat()) + rr(-0.5f, 0.5f)
                var nx = REST_X + cos(ang) * MIN_GLANCE * rr(1f, 1.15f)
                var ny = REST_Y + sin(ang) * MIN_GLANCE * rr(1f, 1.15f)
                if (nx > 0.15f && ny < -0.08f) ny = -ny
                f = f.copy(x = nx.coerceIn(-1f, 1f), y = ny.coerceIn(-1f, 1f))
            }
            // Engine18: don't look at the same spot again so soon — look the other way instead.
            val mem = memory
            if (mem != null && mem.lookedRecently(f.x, f.y, clock())) {
                auditRepeatAvoided++
                var nx = -f.x; var ny = f.y
                if (nx > 0.15f && ny < -0.08f) ny = -ny
                f = f.copy(x = nx, y = ny)
            }
            mem?.looked(f.x, f.y, clock())
            val d2 = hypot(f.x - REST_X, f.y - REST_Y)
            auditGlances++; auditMinGlance = min(auditMinGlance, d2)
            if (d2 < MIN_GLANCE * 0.95f) auditNearRest++
            // Hold proportional to the move: a bigger look is worth looking at longer.
            f = f.copy(hold = f.hold.coerceAtLeast(0.45f + 2.2f * d2))
        }
        kind = f.kind
        fixSeq++; lastFix = f
        if (f.event != null) event = f.event
        if (f.thenCenter) returnToCenter = true
        val homeward = f.travel <= 0f && hypot(f.x - REST_X, f.y - REST_Y) < 0.12f && hypot(tx - REST_X, ty - REST_Y) > 0.18f
        if (homeward) {
            glideHome(f.x, f.y)
            holdLeft = f.hold + pursuitDur
        } else if (f.travel > 0f) {
            p0x = tx; p0y = ty; p1x = f.x; p1y = f.y
            pursuitDur = f.travel; pursuitT = 0f; pending = false
            holdLeft = f.hold + f.travel
        } else {
            pursuitT = 1f
            setTarget(f.x, f.y, f.withHead)
            holdLeft = f.hold
        }
    }
}

/**
 * Expression transitions (engine12): quick, smooth and strictly monotonic, style per change.
 *  - [Style.Blink]: the face swaps while the lids are actually shut (waits for the blink to
 *    close, swaps in ~50 ms, the blink reopens on the new face). Falls back to a snap if the
 *    lids don't close within 180 ms.
 *  - [Style.Snap]: 120–220 ms ease-out — reactions.
 *  - [Style.Soft]: 280–380 ms ease-in-out — unhurried, self-initiated changes.
 *  - [Style.Gradual]: 0.9–1.4 s ease-in-out — mood drift, drowsiness.
 * engine11's "hesitate" (partial move + plateau) read as a stutter and is gone. One system
 * owns the morph; a new target never interrupts a running morph (it queues).
 */
/** Engine17.1 gaze rules. */
private val GLANCE_KINDS = setOf("interest", "explore", "think", "ack", "pan")
private const val MIN_GLANCE = 0.24f
private const val SHIFT_REFRACTORY = 0.24f
private const val REST_X = 0f
private const val REST_Y = 0.02f

internal class ExpressionDirector(@Suppress("unused") private val rng: Random) {
    /**
     * Engine19: named response profiles (no random picker, no masking blinks). [Blink] is kept
     * only as an alias of [Snap] for old call sites.
     */
    enum class Style { Blink, Snap, Soft, Gradual, Settle }

    /**
     * Engine19: the face is a weight vector over the expression basis, each weight driven by
     * one critically damped spring with a shared ω (exact closed-form step). Retargeting only
     * changes the target vector, so a new face grows straight out of whatever in-between pose
     * is showing, with the current velocity carried over: no layers, no queue, no waiting for
     * a morph to finish, never a detour through Neutral. Shared ω keeps Σw = 1 exactly.
     */
    private val names = ArrayList<String>()
    private val x = ArrayList<Float>()
    private val v = ArrayList<Float>()
    var target = "Neutral"; private set
    var omega = OMEGA_SOFT; private set
    var style = Style.Soft; private set
    var retargets = 0; private set
    /** Context for the next change (set by the owner just before [want]). */
    var hint: Style? = null

    init { names += "Neutral"; x += 1f; v += 0f }

    fun want(raw: String) {
        val t = normalize(raw)
        val h = hint
        hint = null
        if (t == target) return
        val from = target
        target = t
        style = h ?: Style.Soft
        // Engine21: a default change is timed by its emotional size (TransitionRules).
        omega = if (h == null || h == Style.Soft) TransitionRules.softOmega(TransitionRules.distance(from, t)) else omegaFor(style)
        if (t !in names) { names += t; x += 0f; v += 0f }
        retargets++
    }

    /** Snap the face to [raw] with no motion (static avatars / tests). */
    fun jump(raw: String) {
        val t = normalize(raw)
        names.clear(); x.clear(); v.clear()
        names += t; x += 1f; v += 0f; target = t
    }

    /**
     * Engine20: swap the face while the lids are shut. The change waits (up to [SWAP_WAIT] s) for
     * the blink that was just started to close, then lands in one step behind the lids; if the
     * lids never close it simply morphs.
     */
    fun wantBehindBlink(raw: String) {
        val before = target
        want(raw)
        if (target != before) swapLeft = SWAP_WAIT
    }
    private var swapLeft = 0f
    var swaps = 0; private set

    /** Advance the springs. Returns false (Engine19 never asks for a masking blink). */
    fun update(dt: Float, lidsClosed: Float = 0f): Boolean {
        if (dt <= 0f) return false
        if (swapLeft > 0f) {
            swapLeft -= dt
            if (lidsClosed >= 0.9f) {
                // Lids shut: the new face is simply there when they open.
                val t = target
                names.clear(); x.clear(); v.clear(); names += t; x += 1f; v += 0f
                swapLeft = 0f; swaps++
                return false
            }
            if (swapLeft > 0f) return false // hold the old face a moment for the lids
        }
        val w = omega
        val k = exp(-w * dt)
        for (i in names.indices) {
            val xs = if (names[i] == target) 1f else 0f
            val e = x[i] - xs
            val c = v[i] + w * e
            x[i] = xs + (e + c * dt) * k
            v[i] = (v[i] - w * c * dt) * k
        }
        // Drop faces that have fully gone (weight and speed both negligible).
        var i = names.size - 1
        while (i >= 0) {
            if (names[i] != target && abs(x[i]) < 2e-4f && abs(v[i]) < 2e-3f) { names.removeAt(i); x.removeAt(i); v.removeAt(i) }
            i--
        }
        return false
    }

    /** Render weights (clamped ≥ 0, renormalised), in a stable (name) order. */
    val weights: List<Pair<String, Float>> get() {
        var s = 0f
        val out = ArrayList<Pair<String, Float>>(names.size)
        for (i in names.indices) { val c = x[i].coerceAtLeast(0f); if (c > 1e-5f) { out += names[i] to c; s += c } }
        if (s <= 1e-6f) return listOf(target to 1f)
        return out.map { it.first to it.second / s }.sortedBy { it.first }
    }

    fun weightOf(name: String): Float { val i = names.indexOf(name); return if (i < 0) 0f else x[i] }
    val speed: Float get() = v.maxOfOrNull { abs(it) } ?: 0f
    val distance: Float get() = names.indices.maxOfOrNull { abs(x[it] - if (names[it] == target) 1f else 0f) } ?: 0f
    val morphing: Boolean get() = speed > 0.05f || distance > 0.01f

    /** Legacy pair view (debug / non-weighted consumers): strongest other face → target. */
    val to: String get() = target
    val from: String get() {
        var best = target; var bw = -1f
        for (i in names.indices) if (names[i] != target && x[i] > bw) { bw = x[i]; best = names[i] }
        return if (bw > 1e-3f) best else target
    }
    val pairTo: String get() = target
    val eased: Float get() {
        val f = from; if (f == target) return 1f
        val a = weightOf(target).coerceAtLeast(0f); val b = weightOf(f).coerceAtLeast(0f)
        return if (a + b <= 1e-6f) 1f else a / (a + b)
    }
    val top: String? get() = null
    val topEased: Float get() = 0f

    companion object {
        const val MIN_DWELL = 0.25f
        const val SWAP_WAIT = 0.16f
        // Engine20: snappier — people change faces quickly (≈ 90 % in 0.2 / 0.3 / 0.6 / 0.75 s).
        const val OMEGA_SNAP = 20f
        const val OMEGA_SOFT = 13f
        const val OMEGA_GRADUAL = 6.5f
        const val OMEGA_SETTLE = 5.2f

        fun omegaFor(s: Style) = when (s) {
            Style.Snap, Style.Blink -> OMEGA_SNAP
            Style.Soft -> OMEGA_SOFT
            Style.Gradual -> OMEGA_GRADUAL
            Style.Settle -> OMEGA_SETTLE
        }

        fun normalize(raw: String): String =
            EyeStyle.resolveGenericalExpressionId(raw)
    }
}

/**
 * Procedural blink, deliberately irregular: log-normal gaps (mostly 2–6 s, sometimes a
 * long stare), each blink with its own speed and depth (some don't close fully),
 * occasional double / rare triple blinks, fewer blinks while concentrating, and blinks
 * that ride gaze shifts, turns and expression changes. After any blink the next
 * spontaneous one is re-drawn, so requested and spontaneous blinks never stack into
 * a rhythm.
 */
internal class Blinker(private val rng: Random) {
    var closed = 0f; private set
    /**
     * Engine21.1 steering (b): a microscopic spring settle after a full blink reopens — the
     * eye opening overshoots by ~1.5 % then settles (one damped wobble, ~0.4 s). Added to the
     * lid opening, so it is barely perceptible by design. 0 at rest.
     */
    var settle = 0f; private set
    private var settleT = -1f
    private var settleUsed = false
    /** A blink (or its double) is under way. */
    val busy: Boolean get() = phase >= 0f || gapLeft >= 0f
    private var phase = -1f
    private var closeT = 0.07f
    private var holdT = 0.03f
    private var openT = 0.12f
    private var depth = 1f
    private var next = 0.8f + rng.nextFloat() * 3f
    private var repeats = 0
    private var gapLeft = -1f
    private var sinceLast = 10f
    private var sinceDouble = 99f
    private var clock = 0f

    /**
     * Engine19: the ONE blink scheduler. Spontaneous blinks (refractory ≥ [REFRACTORY] s,
     * never more than [MAX_INTERVAL] s apart), saccade-coupled blinks and behavioural requests
     * all go through here; it never starts a blink while another lid closer (head shake, slow
     * blink, resting eyes) holds the lids, and rations doubles. Expressions never ask for
     * masking blinks any more.
     */
    var allow: () -> Boolean = { true }
    var onDefer: () -> Unit = {}
    /** (time, double-part) for every blink start (trace / audit). */
    var onStart: (Boolean) -> Unit = {}
    private var deferred = -1
    var auditMidMorph = 0; private set
    var starts = 0; private set
    var doubles = 0; private set
    var overlaps = 0; private set

    /** Keep the eyes open for [sec] (a startle widens them; the blink comes after). */
    fun inhibit(sec: Float) { quiet = maxOf(quiet, sec); if (next < sec) next = sec + 0.15f }
    private var quiet = 0f

    /** Start a blink right now if one may start (no deferral). True when it started. */
    /** Engine21: a full blink is closing right now — a face change can ride it. */
    val closingFull: Boolean get() = phase >= 0f && phase < closeT && depth >= 0.99f

    fun tryNow(minGap: Float = 1.2f): Boolean {
        if (quiet > 0f || phase >= 0f || gapLeft >= 0f || sinceLast < max(minGap, MIN_REQUEST_GAP) || !allow()) return false
        begin(0, drowsy = false, mask = true)
        return true
    }

    fun request(double: Boolean = false, minGap: Float = 1.4f, @Suppress("UNUSED_PARAMETER") mask: Boolean = false) {
        if (quiet > 0f) { deferred = maxOf(deferred, 0); return }
        if (phase >= 0f || gapLeft >= 0f || sinceLast < max(minGap, MIN_REQUEST_GAP)) return
        // Asked-for doubles (wake, recover, nodding off) are rationed too: at most one per
        // [REQUEST_DOUBLE_GAP] s, otherwise a single blink.
        val dbl = double && sinceDouble >= REQUEST_DOUBLE_GAP
        if (!allow()) { deferred = maxOf(deferred, if (dbl) 1 else 0); onDefer(); return }
        begin(if (dbl) 1 else 0, drowsy = false)
    }

    /** A big saccade is starting: blink with it most of the time (p [SACCADE_P]). */
    fun saccade() {
        if (quiet > 0f || rng.nextFloat() >= SACCADE_P) return
        if (phase >= 0f || gapLeft >= 0f || sinceLast < 0.35f) return
        if (!allow()) return
        begin(0, drowsy = false)
    }

    private fun begin(extra: Int, drowsy: Boolean, chained: Boolean = false, mask: Boolean = false) {
        if (phase >= 0f) overlaps++
        settleT = -1f; settle = 0f; settleUsed = false
        phase = 0f
        repeats = extra
        if (!chained) { starts++; if (extra > 0) { doubles++; sinceDouble = 0f } }
        onStart(chained)
        val speed = 0.8f + rng.nextFloat() * 0.5f // per-blink tempo
        closeT = if (drowsy) 0.15f + rng.nextFloat() * 0.08f else (0.05f + rng.nextFloat() * 0.03f) * speed
        holdT = if (drowsy) 0.1f + rng.nextFloat() * 0.12f else (0.01f + rng.nextFloat() * 0.05f) * speed
        openT = if (drowsy) 0.25f + rng.nextFloat() * 0.12f else (0.13f + rng.nextFloat() * 0.08f) * speed // eased tail
        // ~1 in 8 blinks is a lazy half-blink.
        depth = if (!drowsy && !mask && rng.nextFloat() < 0.12f) 0.65f + rng.nextFloat() * 0.25f else 1f
        // Engine21: a face-masking blink always closes fully and holds a beat (≥ 35 ms) so the
        // swap lands behind shut lids; it opens a touch slower onto the new face.
        if (mask) { holdT = max(holdT, 0.035f); openT *= 1.12f }
    }

    fun update(dt: Float, life: MarkLifecycle, drowsy: Boolean) {
        clock += dt
        if (settleT >= 0f) {
            settleT += dt
            settle = if (settleT < SETTLE_DUR) SETTLE_AMP * kotlin.math.exp(-settleT / 0.09f) * kotlin.math.sin(2f * PI_F * settleT / 0.2f) else 0f
            if (settleT >= SETTLE_DUR) { settleT = -1f; settle = 0f }
        }
        sinceLast += dt
        sinceDouble += dt
        quiet -= dt
        // A blink held back by another lid closer lands right after it lets go.
        if (deferred >= 0 && quiet <= 0f && phase < 0f && gapLeft < 0f && allow() && sinceLast >= MIN_REQUEST_GAP) { begin(if (sinceDouble >= REQUEST_DOUBLE_GAP) deferred else 0, drowsy); deferred = -1 }
        if (phase < 0f) {
            if (gapLeft >= 0f) {
                gapLeft -= dt
                if (gapLeft < 0f) begin(repeats, drowsy, chained = true)
            } else {
                next -= dt
                if (next <= 0f && !allow()) next = 0.12f // lids are held by something else
                if (next <= 0f && life != MarkLifecycle.Sleeping) {
                    // Doubles are rare (~4 %), after a longer look, never two within 6 s.
                    val extra = if (!drowsy && sinceLast > 3f && sinceDouble >= DOUBLE_GAP && rng.nextFloat() < DOUBLE_P) 1 else 0
                    begin(extra, drowsy = drowsy)
                }
            }
        }
        if (phase >= 0f) {
            phase += dt
            val total = closeT + holdT + openT
            closed = depth * when {
                phase < closeT -> (phase / closeT).let { it * it }
                phase < closeT + holdT -> 1f
                // Engine20: the lids spring open — fast off the mark, easing into place (the old
                // it·(2−it) on the reversed clock started slow and *landed* at full speed,
                // which read as a stiff snap-open).
                phase < total -> 1f - ((phase - closeT - holdT) / openT).let { u ->
                    // Engine21.1: the settle springs out of the opening's tail (only full, brisk
                    // blinks; not a half-blink, a drowsy one or the first of a double).
                    if (u >= 0.72f && settleT < 0f && !settleUsed && depth >= 0.99f && !drowsy && repeats == 0) { settleT = 0f; settleUsed = true }
                    1f - (1f - u) * (1f - u) * (1f - u)
                }
                else -> 0f
            }
            if (phase >= total) {
                phase = -1f
                closed = 0f
                sinceLast = 0f
                if (repeats > 0) {
                    repeats--
                    gapLeft = 0.07f + rng.nextFloat() * 0.12f
                } else {
                    next = nextInterval(life, drowsy)
                }
            }
        }
    }

    private fun nextInterval(life: MarkLifecycle, drowsy: Boolean): Float {
        // Log-normal, median ~3.4 s; Box–Muller from two uniforms (no java.util.Random).
        val u1 = rng.nextFloat().coerceAtLeast(1e-4f)
        val u2 = rng.nextFloat()
        val n = kotlin.math.sqrt(-2f * kotlin.math.ln(u1)) * kotlin.math.cos(2f * PI_F * u2)
        var base = 3.0f * kotlin.math.exp(0.65f * n)
        if (rng.nextFloat() < 0.08f) base += 1.5f + rng.nextFloat() * 2f // a longer look now and then
        base = when {
            drowsy -> base * 1.4f
            // Concentrating: blinks get rarer (they come at gaze shifts instead).
            life == MarkLifecycle.Working || life == MarkLifecycle.Thinking -> base * (1.15f + rng.nextFloat() * 0.2f)
            life == MarkLifecycle.Streaming -> base * 1.1f
            else -> base
        }
        return base.coerceIn(REFRACTORY, MAX_INTERVAL)
    }

    companion object {
        const val PI_F = 3.1415927f
        const val REFRACTORY = 1.8f
        const val MAX_INTERVAL = 7f
        const val MIN_REQUEST_GAP = 0.9f
        const val DOUBLE_P = 0.04f
        const val DOUBLE_GAP = 6f
        const val SACCADE_P = 0.75f
        const val REQUEST_DOUBLE_GAP = 40f
        /** Reopen settle: amplitude (peak ≈ 0.57 × this) and length. */
        const val SETTLE_AMP = 0.028f
        const val SETTLE_DUR = 0.4f
        /** Test hook: switch the reopen settle off (frame-strip comparisons). */
        @JvmStatic var settleEnabled = true
    }
}

/**
 * Mark motion: [MotionDirector] (body), gaze, expressions, blinks, plus a small
 * "alive" layer — rare social glances between on-screen avatars, glances at app
 * events, poke reactions, a rare spin, drowsiness after a long quiet spell and a
 * little happy beat when a reply finishes. All transitions are continuous.
 */
@Stable
class AvatarMotionState(private val rng: Random = Random.Default) {
    var frame by mutableStateOf(MotionFrame())
        private set

    /**
     * False = shown static (e.g. an older chat message): the mark eases to its rest
     * pose and neutral face, then the frame loop stops. True again = it eases back
     * into motion from that rest pose. Never a cut either way.
     */
    var animate by mutableStateOf(true)
        internal set

    /** 0 = fully live … 1 = fully at rest. */
    private var settle = 0f

    /** At rest, neutral, nothing in flight: safe to stop ticking. */
    internal val isSettled: Boolean
        get() = !animate && settle >= 1f && expr.to == "Neutral" && !expr.morphing

    /** Start already at rest (an avatar that first appears as a static one). */
    internal fun startSettled() {
        animate = false
        settle = 1f
    }

    val lifecycle: MarkLifecycle get() = director.lifecycle

    internal val director = MotionDirector(rng)

    /** Character engine (traits, emotions, drives, spontaneous beats). */
    internal val aliveness = AlivenessEngine(rng)

    /** Character traits; set per avatar (see [Personality.defaultFor]). */
    var personality: Personality
        get() = aliveness.personality
        set(v) { aliveness.personality = v }

    /** Harness hooks. */
    internal fun debugConfused() = setReaction("confused", 1.1f)
    // Scripted faces behave like event reactions (reactive owner: retarget at once).
    internal fun debugReact(expr: String, sec: Float) = because("scripted reaction", MindRules.P_EVENT) { setReaction(expr, sec) }
    internal fun debugBoredom(v: Float) = aliveness.debugBoredom(v)

    /** Per-message tone from outside (e.g. a classifier); colours mood and face. */
    fun feedTone(t: ToneHint) {
        aliveness.feedTone(t)
        // A nice message sometimes earns a happy wiggle (never every time, never twice in a row).
        if (t.valence > 0.5f && t.intensity > 0.5f && rng.nextFloat() < 0.25f + 0.25f * personality.playfulness &&
            moments.allow(Moment.Wiggle, 40f)
        ) {
            if (temper.playful(personality) && rng.nextFloat() < 0.3f && lastSpinAgo > 45f) { remember("nice"); trySpin("delighted by a nice message") }
            else { moments.note(Moment.Wiggle); pendingWiggleIn = 0.25f + rng.nextFloat() * 0.5f }
        }
    }
    private var pendingWiggleIn = -1f
    // ---- human behaviour model (engine11) ----
    internal val temper = Temperament(rng)
    private val attention = Attention(rng)
    private val repertoire = Repertoire(rng)
    private val planner = IdlePlanner(rng)
    private val gaze = GazeDirector(rng, planner, temper).also { g -> g.clock = { time } }
    /** Engine17.1: gaze audit counters (tests). */
    internal val debugGaze: GazeDirector get() = gaze
    private val expr = ExpressionDirector(rng)
    private val blink = Blinker(rng)

    // ---- engine18: the mind
    internal val mind = MindDirector()
    internal val stm = ShortTermMemory()
    /** Personality knobs for the mind (LC Smart Core API); null = derived from [personality]. */
    var mindKnobs: MindKnobs? = null
    val knobs: MindKnobs get() = mindKnobs ?: MindKnobs.fromPersonality(personality)
    /** Cause + priority of whatever is being handled right now (set at every entry point). */
    private var why = "unspecified"
    private var whyPrio = MindRules.P_IDLE
    private inline fun <T> because(c: String, p: Int, block: () -> T): T {
        val w = why; val wp = whyPrio; why = c; whyPrio = p
        try { return block() } finally { why = w; whyPrio = wp }
    }
    private var reactionPrio = MindRules.P_LIFE
    private var reactionCause = ""
    private var reactionWait = 0f
    private var lastNegativeAt = -99f
    private var busySec = 0f
    private var glanceBackIn = 6f
    private var spinningFace = false
    internal val auditSpinsNegative get() = spinsWhileNegative
    private var spinsWhileNegative = 0
    internal val auditIdleSpins get() = idleSpinsStarted
    private var idleSpinsStarted = 0

    private fun negativeName(n: String?) = n != null && ExpressionDirector.normalize(n) in MindRules.NEGATIVE_FACES
    /** Angry, sad, pouty, annoyed … (face, accent or mood), or only just out of it. */
    private fun negativeNow(): Boolean =
        negativeName(expr.to) || negativeName(expr.from) || negativeName(expr.top) || negativeName(reaction) ||
            rig.accent in MindRules.NEGATIVE_ACCENTS || mood.valence < -0.25f || time - lastNegativeAt < 2f

    private val crossFaces = setOf("Angry", "pouty")
    private fun isCrossFace(name: String?): Boolean =
        name != null && (name in crossFaces || ExpressionDirector.normalize(name) in crossFaces)

    /**
     * True when the face currently on screen or scheduled is in the angry family (Angry, pouty,
     * Annoyed accent, cross reaction, or clearly negative mood from being annoyed).
     */
    internal fun crossNow(): Boolean =
        isCrossFace(mind.owner) || isCrossFace(expr.to) || isCrossFace(expr.from) || isCrossFace(expr.top) ||
            isCrossFace(reaction) || isCrossFace(reactionQueue?.first) ||
            rig.accent == FaceAccent.Annoyed || mind.owner == "Annoyed" ||
            forgiveIn > 0f || mood.valence < -0.25f

    internal fun debugMoodNudge(dv: Float, da: Float = 0f) = mood.nudge(dv, da)

    // Smoothed pose knobs (never switch instantly with lifecycle).
    private var lidKnob = 1f
    private var gapKnob = 0.94f
    private var scaleKnob = 1f
    private var drowsyLid = 1f
    private var time = 0f

    // Stage binding (set by the composable).
    internal var stage: AvatarStage? = null
    internal var stageId: Long = 0L
    private var lastEventSeq = -1L

    // Alive-layer state.
    private var prevHints: AvatarMotionHints? = null
    private var quietSec = 0f
    private var drowsyAfter = 100f + rng.nextFloat() * 60f
    private var drowsy = false
    private var droopIn = 12f
    private var perkIn = 0f
    private var spinIn = 30f + rng.nextFloat() * 30f
    private var lastSpinAgo = 20f
    private var nextSpinAt = -1f
    private var lastShownTarget = "Neutral"
    /** Engine19: held lid spring (everything but the blink). */
    private var lidHeldX = 1f
    private var lidHeldV = 0f
    private var faceCause = "start"
    /** Engine19: per-frame channel trace (tests / audits; null in the app). */
    internal var trace: ChannelTrace? = null
    /** Other lid closers (head shake, slow blink, resting eyes) — 1 = not holding the lids. */
    private fun lidHeld(): Float = if (wsT >= 0f) 0f else min(min(shakeLid, slowBlinkLid()), min(restLid, momentLid))
    private var lastFixSeq = 0
    private var lastGazeKind = ""
    private var lastReactionShown: String? = null
    private var socialIn = 8f + rng.nextFloat() * 12f
    private var eventCooldown = 0f
    private var typingCooldown = 0f
    private var moodIn = 20f + rng.nextFloat() * 30f
    private var reaction: String? = null
    private var reactionLeft = 0f
    private var reactionQueue: Pair<String, Float>? = null
        set(v) { field = v; if (v != null) { rqWhy = why; rqPrio = whyPrio } }
    private var rqWhy = "queued"; private var rqPrio = 0
    private var reactionQueueIn = 0f
    private var pokeCount = 0
    private var pokeWindow = 0f
    private var pendingPokes = 0
    private var followLeft = 0f
    private var followCheckIn = 6f
    private var curiousDone = false
    private var attendedTypingAt = -100f

    // Mood + parametric face.
    private val mood = MoodEngine(rng)
    private val rig = FaceRig(rng)
    private var hourCheckIn = 0f
    private var accentCooldown = 0f
    private var mirror: Pair<FaceAccent, Float>? = null
    private var mirrorIn = 0f
    private var flingCooldown = 0f
    private var forgiveIn = -1f
    private var contactSec = 0f

    /** Mood readout (tests / harness). */
    internal val moodValence: Float get() = mood.valence
    internal val moodArousal: Float get() = mood.arousal
    internal val currentAccent: FaceAccent get() = rig.accent

    /** Harness: show a parametric look right away. */
    internal fun debugAccent(a: FaceAccent, sec: Float = 3f) = rig.play(a, sec)

    private class Social(
        val other: Long,
        val lookAt: Float,
        val skepticAt: Float,
        val end: Float,
        var t: Float = 0f,
        var looking: Boolean = false,
        var skepticDone: Boolean = false,
    )
    private var social: Social? = null

    // ------------------------------------------------ editor cues (engine16.2)
    private class CueFire(var at: Float, val cue: EditorCue)
    private val cueQueue = ArrayList<CueFire>()
    private val cueTimes = ArrayDeque<Float>()
    private var lastCueAt = -99f
    private var lastCueKind: EditorCue.Kind? = null
    private var lastGlanceAt = -99f
    private var curiousAt = -99f
    private var afterDone = true

    /** The editor changed something: notice it after a human latency, not on every tap. */
    fun editorCue(cue: EditorCue) {
        if (!animate) return
        cueQueue += CueFire(0.25f + rng.nextFloat() * 0.25f, cue)
    }

    private fun editorUpdate(dt: Float): Boolean {
        var any = false
        val it = cueQueue.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.at -= dt
            if (c.at > 0f) continue
            it.remove(); any = true
            onEditorCue(c.cue)
        }
        if (!afterDone && time - lastCueAt > 1.3f) {
            afterDone = true
            val r = rng.nextFloat()
            when {
                lastCueKind == EditorCue.Kind.Shape && r < 0.3f -> {
                    log("editor after-shape: looks down at itself")
                    gaze.focus(rr(-0.08f, 0.08f), rr(0.38f, 0.46f), rr(1.0f, 1.5f), priority = 2)
                }
                r < 0.5f -> { log("editor after: pleased"); accent(FaceAccent.Glee, rr(0.9f, 1.3f)) }
                r < 0.65f -> { log("editor after: nod"); director.gesture(MotionDirector.Gesture.Nod) }
                else -> log("editor after: just settles")
            }
        }
        return any
    }

    private fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()

    private fun onEditorCue(cue: EditorCue) = because("editor change ${cue.kind}", MindRules.P_EVENT) { onEditorCue0(cue) }
    private fun onEditorCue0(cue: EditorCue) {
        sinceStimulus = 0f
        lastCueAt = time; lastCueKind = cue.kind; afterDone = false
        cueTimes.addLast(time); while (cueTimes.isNotEmpty() && time - cueTimes.first() > 4f) cueTimes.removeFirst()
        val (gx, gy) = cue.at?.let { lookAt(it) } ?: (0f to 0.4f)
        stm.happened(gx, gy, time, "editor ${cue.kind}")
        if (cue.kind == EditorCue.Kind.EyeStyle && rng.nextFloat() < 0.5f) blink.request()
        if (cueTimes.size >= 3 && time - curiousAt > 8f) {
            curiousAt = time
            log("editor: browsing fast → curious")
            accent(FaceAccent.Curious, rr(1.3f, 1.9f))
            gaze.focus(gx, gy, rr(1.2f, 1.8f), priority = 2)
            lastGlanceAt = time
            return
        }
        if (time - lastGlanceAt > 1.6f && rng.nextFloat() < 0.55f) {
            lastGlanceAt = time
            log("editor: glance at ${cue.kind}")
            gaze.focus(gx, gy, rr(0.8f, 1.5f), priority = 2)
        } else log("editor: ${cue.kind} noticed, no move")
    }

    /** Called from the pointer handler (main thread). */
    fun poke(x: Float = 0f, y: Float = 0f) {
        if (animate) { pendingPokes++; pokeX = x.coerceIn(-1f, 1f); pokeY = y.coerceIn(-1f, 1f) }
    }
    @Volatile private var pokeX = 0f
    @Volatile private var pokeY = 0f

    internal fun tick(dtIn: Float, rawHints: AvatarMotionHints, nowMs: Long) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        time += dt
        // ---- engine18: the mind ticks first (holds, expiry, gaze-lead clock).
        if (gaze.memory == null) {
            gaze.memory = stm
            // Engine19: blinks no longer wait for morphs; only another lid closer holds them.
            blink.allow = { lidHeld() >= 0.98f }
            blink.onDefer = { mind.deferredBlink() }
            blink.onStart = { chained -> trace?.blink(time, chained, lidHeld()) }
        }
        mind.knobs = knobs
        mind.update(dt, expr.morphing, gaze.bigShift)
        stm.update(dt)
        why = "unspecified"; whyPrio = MindRules.P_IDLE
        val live = animate
        settle = if (live) max(0f, settle - dt / SETTLE_IN_SEC) else min(1f, settle + dt / SETTLE_OUT_SEC)
        if (!live && settle >= 1f) gaze.snapRest() // invisible now; resumes from rest
        // A static avatar winds down as if nothing were going on.
        val hints = if (live) rawHints else QUIET_HINTS
        val desired = MarkLifecycleMapper.resolve(hints)
        director.setDesired(desired)

        // ---- stimulus / drowsiness ----
        val prev = prevHints
        var stimulus = prev != null && prev != hints
        if (live && prev != null && hints.isTyping && !prev.isTyping) stimulus(Stimulus("typing", 0f, 0.85f, 0.45f), { r -> if (r != Response.None) onTypingStarted() })
        prevHints = hints
        stimulus = processStageEvents(live) || stimulus
        if (live) stimulus = editorUpdate(dt) || stimulus
        if (live) typingUpdate(dt, hints, nowMs)
        if (pendingPokes > 0) {
            if (live) {
                stimulus = true
                // Engine19 reflex: attention goes to the finger at once — eyes only, from
                // wherever they are (no recenter); the head stays on what it was doing.
                trace?.poke(time)
                readingInterrupted("poke")
                director.freezeBase(0.35f)
                blink.inhibit(0.7f) // the startle is seen with open eyes
                upRightPause = 1.5f // attention is on the finger; the up-right ease-out waits
                val gx0 = (pokeX * 0.6f).coerceIn(-0.7f, 0.7f)
                var gy0 = (0.18f + pokeY * 0.35f).coerceIn(-0.05f, 0.6f)
                // Already looking right there: still a small look down at the finger.
                if (hypot(gx0 - gaze.eyeX, gy0 - gaze.eyeY) < 0.08f) gy0 = (gy0 + 0.12f).coerceAtMost(0.6f)
                gaze.focus(gx0, gy0,
                    0.9f + rng.nextFloat() * 0.4f, priority = 3, withHead = false, force = true)
                // …and the eyes register the touch at once (a quick widen — only a flicker once it
                // is used to being poked); the appraised reaction then morphs on from there.
                // Engine20: the body always bounces (the springy pop); the eyes vary — a full widen,
                // a small one, or none (they just look) — less often once it is used to pokes.
                director.gesture(MotionDirector.Gesture.Recoil)
                val usedToIt = stm.pokeFamiliar > 5.5f && pokeWindow <= 0f
                val er = rng.nextFloat() * (if (usedToIt) 1.4f else 1f)
                when {
                    er < 0.45f -> because("poke reflex", MindRules.P_USER) { accent(FaceAccent.Startled, if (usedToIt) 0.2f else 0.32f, force = true) }
                    er < 0.72f -> because("poke reflex", MindRules.P_USER) { accent(FaceAccent.Startled, 0.14f, force = true) }
                    else -> log("poke reflex: body only")
                }
                repeat(pendingPokes) { stimulus(Stimulus("poke", 0f, 0f, 0.9f, direct = true), { r -> because("poke", MindRules.P_USER) { onPoke(r) } }, allowNone = false) }
            }
            pendingPokes = 0
        }
        val calmLife = director.lifecycle == MarkLifecycle.Idle && desired == MarkLifecycle.Idle
        // Engine21: the user counts as active for a while after any interaction.
        if (live && (stimulus || hints.isTyping || hints.inputFocused)) lastUserAt = time
        ctx = ContextRules.of(if (desired != MarkLifecycle.Idle && MotionDirector.isBusy(desired)) desired else director.lifecycle,
            time - lastUserAt < ContextRules.USER_ACTIVE_SEC)
        if (live) contextUpdate(dt)
        if (stimulus || !calmLife) {
            if (drowsy) wake()
            if (quietSec > 20f) log("quiet reset after ${quietSec.toInt()} s (stimulus=$stimulus calm=$calmLife)")
            quietSec = 0f
        } else {
            quietSec += dt
        }
        if (!drowsy && calmLife && quietSec > drowsyAfter * (0.55f + mood.arousal)) {
            drowsy = true
            log("drowsy onset after ${quietSec.toInt()} s untouched")
            droopIn = 6f + rng.nextFloat() * 6f
        }
        director.drowsy = drowsy

        if (live) {
            aliveUpdate(dt, nowMs)
        } else {
            reaction = null
            reactionQueue = null
            train.clear()
            followLeft = 0f
            if (social != null) {
                social = null
                gaze.releaseFocus()
            }
        }

        // ---- body ----
        val body = director.update(dt)
        if (director.clipBlink) blink.request()
        director.takeGazeCue()?.let { c ->
            // A busy beat asked to look somewhere (turn, the pill, the text, away in thought).
            if (live && social == null && followLeft <= 0f && !gaze.focused && recoverT <= 0f) {
                // Look at the real thing when it is on screen.
                val real = when (c.kind) {
                    "pill" -> targetCenter(AvatarStage.Target.ActivityPill)
                    "read" -> stage?.target(AvatarStage.Target.StreamingText)?.let { r ->
                        // Somewhere along the last lines of the arriving text.
                        Offset(r.left + r.width * (0.15f + 0.7f * rng.nextFloat()), r.bottom - minOf(r.height, 120f) * rng.nextFloat())
                    }
                    else -> null
                }
                if (real != null) {
                    val (gx, gy) = lookAt(real)
                    gaze.focus(gx, gy, c.holdSec, priority = 1)
                } else {
                    gaze.focus(c.x, c.y, c.holdSec, priority = 1)
                }
                if (c.kind == "ponder" && rng.nextFloat() < 0.2f) because("ponders while looking away", MindRules.P_IDLE) { accent(FaceAccent.Puzzled, 1.4f + rng.nextFloat()) }
            }
        }

        // ---- gaze ----
        gaze.update(dt, director.lifecycle, drowsy, allowIdle = spT < 0f)
        // Big eye jumps often carry a blink (people blink as they look somewhere new).
        if (gaze.bigShift) blink.saccade()
        gaze.takeEvent()?.let { ev -> if (live) attentionEvent(ev) }
        if (gaze.fixSeq != lastFixSeq) {
            lastFixSeq = gaze.fixSeq
            gaze.lastFix?.let { f -> log("fix ${f.kind} → (%.2f,%.2f) hold %.1fs".format(f.x, f.y, f.hold)) }
        }
        if (gaze.kind != lastGazeKind) {
            lastGazeKind = gaze.kind
            log("gaze ${gaze.kind} (%.2f,%.2f)".format(gaze.eyeX, gaze.eyeY))
        }

        // ---- expression ----
        // Never swap faces mid-nod (a morph under a nod reads as a size pop).
        // Engine17.1: the face keeps living through a spin (a frozen mid-morph read as a
        // stall); changes that start during a turn use the soft blend, never a blink swap.
        val spinFace = abs(spinYaw) > 0.5f || abs(spinV) > 1f
        if (spinFace && !spinningFace) {
            if (negativeNow()) spinsWhileNegative++
            if (director.lifecycle == MarkLifecycle.Idle && whyPrio < MindRules.P_EVENT && time - stm.lastPokeAt > 20f && !recently("nice", 30f)) idleSpinsStarted++
        }
        spinningFace = spinFace
        run {
            val target = faceTarget()
            if (target != lastShownTarget) {
                // Every face change is logged with its cause (reactions log their own).
                if (reaction == null || reaction != target) log("face ${lastShownTarget}→$target cause=" + when {
                    reaction == null && lastShownTarget == lastReactionShown -> "reaction ends, relax to baseline"
                    MotionDirector.isBusy(director.lifecycle) -> "busy phase (${director.lifecycle})"
                    else -> "lifecycle ${director.lifecycle}"
                })
                lastShownTarget = target
                lastReactionShown = reaction
            }
            if (ExpressionDirector.normalize(target) != expr.to) {
                // Engine19: one owner per channel. A reactive change retargets the face spring
                // at once, from whatever in-between pose is showing; spontaneous changes respect
                // the owner's dwell. Never waits for a morph, a nod or a blink to finish.
                val isReaction = reaction != null && target == reaction
                val relax = !isReaction && reaction == null && lastReactionShown != null
                val prio = if (isReaction) reactionPrio else MindRules.P_LIFE
                val cause = when {
                    isReaction -> reactionCause.ifBlank { "reaction" }
                    relax -> "reaction ends, relax to baseline"
                    MotionDirector.isBusy(director.lifecycle) -> "busy phase (${director.lifecycle})"
                    drowsy -> "drowsy"
                    else -> "lifecycle ${director.lifecycle}"
                }
                val baseline = !isReaction && ExpressionDirector.normalize(target) == "Neutral"
                val blockedBySpin = spinFace && negativeName(target)
                // Engine20 emotional dwell: a spontaneous face is not replaced by another
                // spontaneous change before it has been felt for its minimum dwell.
                val dwellHold = animate && prio < MindRules.P_EVENT && shownPrio < MindRules.P_EVENT &&
                    time - shownAt < MindRules.minDwell(expr.to) && !MindRules.hasEventCause(prio, cause)
                // Engine21: leaving a face the context forbids (asleep / bored / long-shut eyes while
                // generating or with the user active) is never held back by dwell or budget.
                val ctxForced = animate && !ContextRules.faceOk(ctx, expr.to, heldSec(expr.to))
                if (ctxForced || !blockedBySpin && !dwellHold && (!animate || mind.faceSafe(prio, expr.morphing) &&
                    mind.budgetOk(prio, MotionDirector.isBusy(director.lifecycle), mood.arousal, settle = baseline || relax))
                ) {
                    expr.hint = when {
                        !animate -> ExpressionDirector.Style.Soft
                        isReaction -> reactionStyle.also { reactionStyle = null }
                        time < briskUntil -> ExpressionDirector.Style.Soft
                        relax || baseline -> ExpressionDirector.Style.Settle
                        drowsy || temper.state == Temperament.State.Tired -> ExpressionDirector.Style.Gradual
                        else -> null
                    }
                    // Engine20: now and then the face changes behind a blink (the lids close on
                    // one face and open on the next) — when it fits: a calm, not-snapped change
                    // with the eyes open and free to blink.
                    // Engine21: the blink-mask rule (TransitionRules.mask) — most changes hide behind a
                    // blink; fast reactions, closed-eye faces and tiny tweaks morph directly.
                    val tgtN = ExpressionDirector.normalize(target)
                    val dEmo = TransitionRules.distance(expr.to, tgtN)
                    val maskKind = TransitionRules.mask(expr.to, tgtN, expr.hint == ExpressionDirector.Style.Snap, prio >= MindRules.P_USER)
                    val hide = animate && maskKind == TransitionRules.Mask.Blink && !drowsy && spT < 0f &&
                        rng.nextFloat() < TransitionRules.maskChance(dEmo) && (blink.closingFull || blink.tryNow(minGap = TransitionRules.MASK_MIN_GAP))
                    if (hide) { expr.wantBehindBlink(target); log("face change behind a blink → $target") } else {
                        if (animate) log("face morph direct → $target (${maskKind.name.lowercase()}, d=${"%.2f".format(dEmo)})")
                        expr.want(target)
                    }
                    shownAt = time; shownPrio = prio
                    faceCause = cause
                    mind.landFace(target, prio, cause, MindRules.hold(mood.arousal, knobs), countBudget = !(baseline || relax))
                }
            }
        }
        // A queued face intention (reaction / accent) lands when the face is free.
        mind.pump(Lane.Face) { i ->
            if (i.name.startsWith("accent:")) !rig.active && mind.faceSafe(i.priority, expr.morphing) &&
                mind.budgetOk(i.priority, MotionDirector.isBusy(director.lifecycle), mood.arousal)
            else (reaction == null || reactionPrio <= i.priority || reactionLeft <= 0f)
        }
        mind.pump(Lane.Body) { planSafePoint() }
        mind.pump(Lane.Spin) { !negativeNow() && spT < 0f && !drowsy }
        if (negativeName(expr.to) || negativeName(expr.from) || rig.accent in MindRules.NEGATIVE_ACCENTS) lastNegativeAt = time
        if (MotionDirector.isBusy(director.lifecycle)) busySec += dt
        expr.update(dt, blink.closed)
        director.exprMorphing = expr.morphing

        // ---- blink + lids ----
        blink.update(dt, director.lifecycle, drowsy)
        val pose = MarkLifecycleMapper.pose(director.lifecycle)
        val k = 1f - exp(-dt / 0.35f)
        lidKnob += (pose.lid - lidKnob) * k
        gapKnob += (pose.eyeGap - gapKnob) * k
        scaleKnob += (pose.eyeScale - scaleKnob) * k
        val drowsyTarget = if (drowsy) 0.55f else 1f
        drowsyLid += (drowsyTarget - drowsyLid) * (1f - exp(-dt / if (drowsy) 2.5f else 0.3f))
        // Startle window: eyes snap wide open and stay open (~0.55 s) — no blink, no droop.
        if (director.startling && !wasStartling) startleHoldT = 0.6f
        wasStartling = director.startling
        startleHoldT -= dt
        val inStartle = startleHoldT > 0f
        // Engine19: the held lid (everything but the blink) is one spring; a startle retargets
        // it wide open instead of stepping it, and the blink rides on top unchanged.
        val lidHeldTarget = if (inStartle) 1f
            else (lidKnob.coerceAtMost(1f) * body.lidOpen * drowsyLid * momentLid * restLid * slowBlinkLid() * shakeLid).coerceIn(0f, 1f)
        run {
            val w0 = if (inStartle) 30f else 22f
            val kk = exp(-w0 * dt)
            val e = lidHeldX - lidHeldTarget; val c = lidHeldV + w0 * e
            lidHeldX = lidHeldTarget + (e + c * dt) * kk; lidHeldV = (lidHeldV - w0 * c * dt) * kk
        }
        val lid = if (inStartle) lidHeldX.coerceIn(0f, 1f) else (lidHeldX * (1f - blink.closed)).coerceIn(0f, 1f)

        // ---- mood + parametric face ----
        hourCheckIn -= dt
        if (hourCheckIn <= 0f) {
            hourCheckIn = 60f
            val h = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }.get(java.util.Calendar.HOUR_OF_DAY)
            mood.arousalBase = MoodEngine.baseForHour(h)
        }
        mood.update(dt, quietSec)
        director.moodValence = mood.valence
        director.moodArousal = mood.arousal
        val busyNow = MotionDirector.isBusy(director.lifecycle)
        val faceLive = rig.update(
            dt,
            gaze.evx, gaze.evy,
            blink.closed,
            valence = if (live) mood.valence * (if (busyNow) 0.5f else 1f) else 0f,
            arousal = if (live) mood.arousal else 0.5f,
            still = live && !gaze.bigShift && social == null,
        )

        stage?.setCalm(stageId, calmLife && social == null && reaction == null && !drowsy)

        // Blend toward the static rest pose (weight eased so it starts and lands softly).
        val w = MotionDirector.easeInOutCubic(settle)
        val keep = 1f - w
        val awakeLife = if (drowsy || director.lifecycle == MarkLifecycle.Sleeping) 0.5f else 1f
        humanUpdate(dt, live)
        // Breathing: slow sinusoid (≈ 4.2 s, slower when calm / tired), barely visible.
        val brPeriod = MotionVocab[Action.Breathing].dur * (1.25f - 0.35f * temper.energy)
        breathPh += dt * 2f * PI.toFloat() / brPeriod
        val br = sin(breathPh) * keep * awakeLife
        // Sigh: one slow deep breath with the head sinking a little (ease-in-out bump).
        val sighK = if (sighU < 1f) sin(PI.toFloat() * MotionVocab.easeInOutSine(sighU)) * keep else 0f
        // Fidget: a small posture shift with gentle follow-through.
        run {
            val om = MotionVocab[Action.Fidget].omega
            fidgetYawMotion.advance(fidgetYawT, dt, om, 0.88f)
            fidgetRollMotion.advance(fidgetRollT, dt, om, 0.88f)
        }
        val bodyBase0 = body.lerpTo(BodyPose.REST, w)
        // Moment channels: wiggle (damped roll oscillation), stretch (rise + hold + settle),
        // sleepy drift (head sinks, then perks up), peek lean.
        val wig = if (wiggleU < 1f) wiggleA * sin(2f * PI.toFloat() * 1.5f * wiggleU) * (1f - wiggleU) * (1f - wiggleU) else 0f
        val stK = if (stretchU < 1f) when {
            // Small crouch first (anticipation), then the rise.
            stretchU < 0.12f -> -0.3f * sin(PI.toFloat() * stretchU / 0.12f)
            stretchU < 0.35f -> MotionVocab.easeInOutSine((stretchU - 0.12f) / 0.23f)
            stretchU < 0.6f -> 1f
            else -> 1f - MotionVocab.easeInOutSine((stretchU - 0.6f) / 0.4f)
        } else 0f
        val drK = if (driftU < 1f) when {
            driftU < 0.8f -> MotionVocab.easeInOutSine(driftU / 0.8f)
            else -> 1f - MotionVocab.easeOutCubic((driftU - 0.8f) / 0.2f) // perk: quick lift
        } else 0f
        val bodyBase = bodyBase0.copy(
            yaw = bodyBase0.yaw + (fidgetYaw + peekYaw + shakeYaw) * keep,
            roll = bodyBase0.roll + (fidgetRoll + peekRoll + wig) * keep,
            pitch = bodyBase0.pitch + (4f * sighK + driftDepth * drK - 3f * stK - 7f * yawnK() + 5f * restK) * keep,
        )
        // Engine20 secondary motion: a soft under-damped spring the body rides — the lids
        // closing give it a tiny squash, a big look gives it a small bob — so blinks and
        // glances carry through the figure instead of living only in the eyes.
        run {
            if (gaze.bigShift && live) lifeMotion.velocity += 0.12f
            val om = 24f; val z = 0.42f
            val tgt = if (live) 0.010f * blink.closed else 0f
            lifeMotion.advance(tgt, dt, om, z)
        }
        val lifeK = lifeMotion.value * keep
        var bodyOut = bodyBase.copy(
            posY = bodyBase.posY + 0.3f * lifeK - 0.008f * br + 0.012f * sighK - 0.03f * stK * keep + 0.4f * spinCrouchK * keep - 0.02f * yawnK() * keep + 0.012f * restK * keep,
            squashX = bodyBase.squashX * (1f + 0.5f * lifeK - 0.008f * br + 0.018f * sighK - 0.06f * stK * keep + 0.6f * spinCrouchK * keep - 0.04f * yawnK() * keep + 0.012f * restK * keep),
            squashY = bodyBase.squashY * (1f - 0.8f * lifeK + 0.012f * br - 0.03f * sighK + 0.1f * stK * keep - spinCrouchK * keep + 0.07f * yawnK() * keep - 0.015f * restK * keep),
        )
        momentLid = (1f - 0.75f * stK.coerceAtLeast(0f) - 0.45f * drK - 0.85f * yawnK()).coerceIn(0f, 1f)
        val hx = (gaze.headX * keep).coerceIn(-1f, 1f)
        var hy = (gaze.headY * keep).coerceIn(-1f, 1f)
        // Eyes lag a head shake a touch (secondary motion) and lag a wiggle.
        val lx = ((gaze.eyeX - gaze.headX) * 0.07f + gaze.eyeX * 0.03f) * keep - (shakeYaw * 0.004f + wig * 0.003f) * keep
        var ly = ((gaze.eyeY - gaze.headY) * 0.06f + gaze.eyeY * 0.025f) * keep
        // Up-right is a glance, never a rest: after ~0.75 s the up component eases out.
        val upNow = bodyOut.yaw + 18f * hx > 3f && bodyOut.pitch + 14f * hy < -2f
        upRightPause -= dt
        upRightT = if (upNow && upRightPause <= 0f) upRightT + dt else if (upNow) upRightT else (upRightT - 2f * dt).coerceAtLeast(0f)
        if (upRightT > 0.75f && upK < 0.05f) gaze.focus(0.05f * (rng.nextFloat() - 0.5f), 0.04f, 0.8f, priority = 5)
        // Engine19: a smootherstep ramp (zero velocity at both ends), not a first-order lag,
        // which started the ease-out at full speed (a visible kick).
        upRamp = if (upRightT > 0.75f) (upRamp + dt / 0.45f).coerceAtMost(1f) else (upRamp - dt / 0.45f).coerceAtLeast(0f)
        upK = upRamp * upRamp * upRamp * (upRamp * (upRamp * 6f - 15f) + 10f)
        if (upK > 1e-3f) {
            val f = 1f - upK
            if (hy < 0f) hy *= f
            if (ly < 0f) ly *= f
            if (bodyOut.pitch < 0f) bodyOut = bodyOut.copy(pitch = bodyOut.pitch * f)
            if (bodyOut.yaw > 0f) bodyOut = bodyOut.copy(yaw = bodyOut.yaw * (1f - 0.5f * upK))
        }
        val bodyRc = bodyOut
        val built = MotionFrame(
            body = bodyRc,
            headLookX = hx,
            headLookY = hy,
            leadX = lx,
            leadY = ly,
            lid = lid + (1f - lid) * w,
            exprFrom = expr.from,
            exprTo = expr.pairTo,
            exprT = expr.eased,
            exprTop = expr.top,
            exprTopT = expr.topEased,
            exprWeights = if (w > 0.999f) null else expr.weights,
            lifecycle = director.lifecycle,
            eyeGap = gapKnob + (REST_FRAME.eyeGap - gapKnob) * w,
            eyeScale = scaleKnob + (REST_FRAME.eyeScale - scaleKnob) * w,
            eyePop = if (Blinker.settleEnabled && live) blink.settle * keep else 0f,
            timeSec = time,
            face = lerpFace(faceLive, w),
            spinYaw = spinAngles().first * keep,
            spinPitch = spinAngles().second * keep,
            spinRoll = spinAngles().third * keep,
        )
        frame = built
        withFollow(keep)
        trace?.let { tr ->
            val f = frame
            tr.add(TraceRow(
                t = time, pitch = f.body.pitch, yaw = f.body.yaw, roll = f.body.roll, posY = f.body.posY,
                headX = f.headLookX, headY = f.headLookY, leadX = f.leadX, leadY = f.leadY, lid = f.lid,
                weights = expr.weights.toMap(), target = expr.to, spin = f.spinYaw,
                owner = mind.owner, ownerPrio = mind.ownerPriority, cause = faceCause,
                accent = if (rig.active) rig.accent.name else "", saccade = gaze.bigShift, beat = director.traceName,
            ))
        }
    }
    private fun withFollow(keep: Float) {
        if (spinFollowX == 0f) return
        // The face keeps sliding a touch along the spin direction (vertical for a flip).
        frame = if (spPath == SpinPath.Flip) frame.copy(leadY = frame.leadY + spinFollowX * 0.8f * keep)
            else frame.copy(leadX = frame.leadX + spinFollowX * keep)
    }
    private fun spinAngles(): Triple<Float, Float, Float> =
        if (abs(spinYaw) < 1e-4f) ZERO3 else spPath.angles(spinYaw / (360f * spDir), spDir)
    private val ZERO3 = Triple(0f, 0f, 0f)

    private var breathPh = 0f
    private var startleHoldT = 0f
    private var momentLid = 1f
    private var wasStartling = false

    // ------------------------------------------------------- human behaviour (engine11)

    // ---- character moments (engine12) ----
    private val moments = MomentPlanner(rng)
    private var spinYaw = 0f; private var spinV = 0f
    // A spin adds continuous motion to the live pose; no layer is latched or handed back.
    /** After a spin (and any shake-off): a short "recovering" pause. No new glances or
     *  moments, eyes rest near centre with only micro-saccades; real stimuli still win. */
    private var recoverT = 0f
    /** Seconds since the last perceived outside event: no self-started moment right on top of a reaction. */
    private var sinceStimulus = 99f
    private fun beginRecover() = because("spin recovery", MindRules.P_EVENT) { beginRecover0() }
    private fun beginRecover0() {
        recoverT = 0.8f + rng.nextFloat() * 1.7f
        gaze.focus((rng.nextFloat() - 0.5f) * 0.08f, (rng.nextFloat() - 0.5f) * 0.06f, recoverT, priority = 3)
        // Engine16: varied recovery, picked with a reason (how big the spin was, mood).
        if (spShake) return // the shake-off already was the recovery
        val big = spPath != SpinPath.Turn && spPath != SpinPath.Top
        val r = rng.nextFloat()
        when {
            big && r < 0.3f -> { log("recover dizzy (after a $spPath)"); accent(FaceAccent.Dizzy, 0.9f + 0.5f * rng.nextFloat(), force = true) }
            r < 0.45f -> { log("recover blink-blink"); blink.request(double = true, minGap = 0f) }
            r < 0.6f && mood.valence > 0.1f -> { log("recover pleased"); accent(FaceAccent.Glee, 1.0f, force = true) }
            r < 0.7f -> { log("recover nod"); director.gesture(MotionDirector.Gesture.Nod) }
            else -> log("recover still")
        }
    }
    private var spT = -1f; private var spDir = 1f
    private var spRun = 0f; private var spRunDeg = 0f; private var spCrouch = 0f
    private var spMain = 1.2f
    private var spFollow = 0f; private var spFollowOut = 0.2f; private var spFollowBack = 0.5f
    private var spShake = false; private var spShakeDelay = 0.15f; private var spShakeAmp = 5f
    private var spLastRun = false; private var spLastShake = false
    private var spDirRun = 0
    private var spinCrouchK = 0f
    private var spinFollowX = 0f
    private var spinTrajectory: SpinTrajectory? = null
    private var shakeYaw = 0f
    private var forceRun: Boolean? = null; private var forceShake: Boolean? = null
    private var forcePath: SpinPath? = null
    internal var spPath = SpinPath.Turn; private set
    private var spLastPath: SpinPath? = null
    private val spPathHist = ArrayDeque<SpinPath>()
    internal fun debugSpin(runUp: Boolean?, shake: Boolean?, path: SpinPath? = null) { forceRun = runUp; forceShake = shake; forcePath = path; moments.note(Moment.Spin); playMoment(Moment.Spin) }

    /** Engine16: spins only in lively contexts (never self-started while idle). */
    private fun trySpin(cause: String): Boolean {
        if (spT >= 0f || lastSpinAgo < 20f || drowsy || restPhase > 0) return false
        // Engine18: never spin while angry / sad / annoyed, never self-started while idle.
        if (negativeNow()) { mind.blockedSpin(false); log("spin blocked: negative face or mood (cause=$cause)"); return false }
        if (director.lifecycle == MarkLifecycle.Idle && whyPrio < MindRules.P_EVENT) { mind.blockedSpin(true); log("spin blocked: self-started while idle (cause=$cause)"); return false }
        if (!mind.budgetOk(whyPrio, MotionDirector.isBusy(director.lifecycle), mood.arousal)) return false
        mind.noteAct(Lane.Spin, "spin", whyPrio, cause)
        log("spin cause=$cause")
        moments.note(Moment.Spin); playMoment(Moment.Spin)
        lastSpinAgo = 0f; nextSpinAt = -1f
        return true
    }

    private fun startSpin(playful: Boolean) {
        fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        // Direction: random, but never three the same way in a row.
        var dir = if (rng.nextBoolean()) 1f else -1f
        if (abs(spDirRun) >= 2 && kotlin.math.sign(dir) == kotlin.math.sign(spDirRun.toFloat())) dir = -dir
        spDirRun = if (dir > 0f) (if (spDirRun > 0) spDirRun + 1 else 1) else (if (spDirRun < 0) spDirRun - 1 else -1)
        spDir = dir
        // History-aware variant choice: what happened last time is less likely now.
        val pRun = (if (playful) 0.6f else 0.45f) * (if (spLastRun) 0.55f else 1.25f)
        // Engine20: every spin winds up — a small counter-turn and crouch (anticipation), then a
        // springy launch. Only its size varies (bigger when it is a deliberate run-up).
        var run = forceRun ?: (rng.nextFloat() < pRun)
        spRun = if (run) rr(0.36f, 0.46f) else rr(0.3f, 0.36f)
        spRunDeg = if (run) rr(9f, 13f) else rr(6f, 9f)
        spCrouch = if (run) rr(0.022f, 0.035f) else rr(0.014f, 0.022f)
        // A readable wind-up, then a decisive turn with a long braking arc.
        spMain = if (playful) rr(1.55f, 1.7f) else rr(1.7f, 1.85f)
        spFollow = rr(0.075f, 0.11f) * (1.6f - 0.3f * spMain) // eyes carry on past the stop (faster spin → more)
        spFollowOut = rr(0.22f, 0.3f); spFollowBack = rr(0.65f, 0.85f)
        val pShake = (0.18f + 0.22f * (if (playful) 1f else 0f) + 0.15f * (1f - temper.energy)) * (if (spLastShake) 0.45f else 1.2f)
        spShake = forceShake ?: (rng.nextFloat() < pShake)
        spShakeDelay = rr(0.05f, 0.25f); spShakeAmp = rr(3.5f, 6.5f)
        // Never the same way twice in a row: if the variant would repeat, change one thing.
        if (forceRun == null && forceShake == null && run == spLastRun && spShake == spLastShake && rng.nextFloat() < 0.8f) {
            if (rng.nextBoolean()) { spShake = !spShake } else {
                run = !run
                spRun = if (run) rr(0.36f, 0.46f) else rr(0.3f, 0.36f)
                spRunDeg = if (run) rr(9f, 13f) else rr(6f, 9f)
                spCrouch = if (run) rr(0.022f, 0.035f) else rr(0.014f, 0.022f)
            }
        }
        // Engine16: the axis / path varies (never the same path twice in a row).
        spPath = forcePath ?: run {
            val opts = SpinPath.entries.map { it to it.weight * (if (it == spLastPath) 0.1f else if (it in spPathHist) 0.6f else 1f) }
            var x = rng.nextFloat() * opts.sumOf { it.second.toDouble() }.toFloat()
            opts.firstOrNull { x -= it.second; x <= 0f }?.first ?: SpinPath.Turn
        }
        spLastPath = spPath; spPathHist.addLast(spPath); while (spPathHist.size > 3) spPathHist.removeFirst()
        forcePath = null
        // Flips and cartwheels carry more visible mass: a touch slower.
        if (spPath == SpinPath.Flip) spMain *= rr(0.94f, 1.0f).let { 1f / it }.coerceAtMost(1.06f)
        spLastRun = run; spLastShake = spShake
        forceRun = null; forceShake = null
        spinTrajectory = SpinTrajectory(spRun, spMain, spRunDeg, spCrouch, rr(6f, 10f), spFollow, spFollowOut, spFollowBack)
        spT = 0f
        log("spin path=$spPath dir=${dir.toInt()} runUp=${spRun > 0f} shake=$spShake main=${"%.2f".format(spMain)}")
    }

    /** The trajectory runs through its settle before the whole-turn angle is rebased. */
    private fun spinUpdate(dt: Float) {
        val prev = spinYaw
        spinCrouchK = 0f; spinFollowX = 0f; shakeYaw = 0f
        val trajectory = spinTrajectory
        if (spT >= 0f && trajectory != null) {
            spT += dt
            trajectory.sample(spT)
            spinYaw = spDir * trajectory.angle
            spinCrouchK = trajectory.compression
            spinFollowX = spDir * trajectory.eyeLead
            if (spT >= trajectory.duration + (if (spShake) spShakeDelay else 0f)) {
                spinYaw = 0f // exact whole turn, after all displacement and velocity have settled
                spT = -1f
                spinTrajectory = null
                if (spShake) wakeShake(spShakeAmp, cause = "spin shake-off") else beginRecover()
            }
        }
        spinV = if (dt > 0f) MarkClipEngine.wrap180(spinYaw - prev) / dt else 0f
    }
    private var wiggleU = 1f; private var wiggleA = 0f; private var wiggleDur = 0.9f
    private var stretchU = 1f; private var stretchDur = 2.6f
    private var driftU = 1f; private var driftDur = 4f; private var driftDepth = 9f
    private val peekRollMotion = DampedMotion()
    private val peekRoll get() = peekRollMotion.value
    private var peekRollT = 0f
    private val peekYawMotion = DampedMotion()
    private val peekYaw get() = peekYawMotion.value
    private var peekYawT = 0f
    private var peekLeft = 0f
    private var peekDelay = 0f; private var peekPendRoll = 0f; private var peekPendYaw = 0f
    private var pillTiltIn = -1f
    private val scan = ArrayList<FloatArray>() // [startAt, x, y, hold]
    private var scanT = 0f
    private var upRightT = 0f
    private var upRamp = 0f
    private var shownAt = -99f
    private var shownPrio = -1
    private val lifeMotion = DampedMotion()
    private var upRightPause = 0f
    private var upK = 0f

    /** Harness: let a moment fire on the next check / play one now. */
    internal fun debugMomentsReady() = moments.debugReady()
    internal fun debugMoment(m: Moment) { moments.note(m); playMoment(m) }

    private fun playMoment(m: Moment) = because("moment $m", maxOf(whyPrio, MindRules.P_IDLE)) { playMoment0(m) }
    private fun playMoment0(m: Moment) {
        fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        val side = if (rng.nextBoolean()) 1f else -1f
        log("moment $m")
        when (m) {
            Moment.Spin -> {
                val playful = temper.playful(personality) || temper.valence > 0.2f
                startSpin(playful)
                if (playful && rng.nextFloat() < 0.5f) setReaction("Happy", rr(1.6f, 2.4f), ExpressionDirector.Style.Soft, cause = "spin joy")
            }
            Moment.Scan -> {
                val n = 2 + rng.nextInt(3)
                var at = 0f
                var x = side * rr(0.35f, 0.6f)
                scan.clear(); scanT = 0f
                if (rng.nextFloat() < 0.3f) blink.request(minGap = 0f) // blinks often ride big gaze shifts
                // Engine15: a slow pan across related points (one direction, small steps),
                // not a teleport between random spots.
                var y = rr(-0.1f, 0.3f)
                val stepX = -side * rr(0.22f, 0.34f); val stepY = rr(-0.06f, 0.06f)
                repeat(n) { k ->
                    val hold = rr(0.7f, 1.5f)
                    scan += floatArrayOf(at, x, y, hold)
                    at += hold + rr(0.1f, 0.25f)
                    x = (x + stepX).coerceIn(-0.85f, 0.85f); y = (y + stepY).coerceIn(-0.3f, 0.45f)
                    if (k == n - 1) scan += floatArrayOf(at, rr(-0.06f, 0.06f), rr(-0.02f, 0.05f), rr(1.5f, 3f))
                }
            }
            Moment.Peek -> {
                // Eyes go first; the lean follows ~150 ms later (critically damped, no bounce).
                peekPendRoll = -side * rr(5f, 8f); peekPendYaw = side * rr(5f, 8f); peekDelay = rr(0.12f, 0.22f)
                peekLeft = rr(1.2f, 2f) + peekDelay
                gaze.focus(side * rr(0.55f, 0.75f), rr(0f, 0.25f), peekLeft, priority = 2)
                if (rng.nextFloat() < 0.5f) accent(FaceAccent.Curious, peekLeft)
            }
            Moment.Stretch -> {
                stretchU = 0f; stretchDur = rr(2.3f, 3f)
            }
            Moment.SleepyDrift -> {
                driftU = 0f; driftDur = rr(3.5f, 5f); driftDepth = rr(7f, 11f)
            }
            Moment.NoticePill -> {
                val pill = targetCenter(AvatarStage.Target.ActivityPill)
                val (gx, gy) = pill?.let { lookAt(it) } ?: (0.3f to 0.5f)
                gaze.focus(gx, gy, rr(1.3f, 2.2f), priority = 2)
                if (rng.nextFloat() < 0.45f) pillTiltIn = rr(0.25f, 0.45f) // head tilts after the eyes land
                if (rng.nextFloat() < 0.35f) accent(FaceAccent.Glee, rr(0.9f, 1.4f))
            }
            Moment.Wiggle -> {
                wiggleU = 0f; wiggleA = rr(3f, 5f) * side; wiggleDur = rr(0.7f, 0.95f)
                remember("nice")
                setReaction("Happy", rr(1.3f, 2f), ExpressionDirector.Style.Snap, cause = "nice message")
            }
            Moment.SmugNod -> {
                accent(FaceAccent.Smug, rr(1.2f, 1.8f))
                director.gesture(MotionDirector.Gesture.Nod)
            }
        }
    }

    private fun momentsUpdate(dt: Float) {
        val calm = director.lifecycle == MarkLifecycle.Idle && reaction == null && social == null &&
            freezeLeft <= 0f && takeT < 0f && scan.isEmpty() && driftU >= 1f && stretchU >= 1f &&
            spT < 0f && abs(spinYaw) < 0.5f && recoverT <= 0f && sinceStimulus > 3f
        sinceStimulus += dt
        recoverT = (recoverT - dt).coerceAtLeast(0f)
        // Engine16: spontaneous idle beats come from the episode planner (see aliveUpdate).
        moments.update(dt, temper, personality, false, quietSec, false)
        spinUpdate(dt)
        channelsUpdate(dt)
        // A lean carries its velocity through retargets, with a small, weighty overshoot.
        if (peekDelay > 0f) { peekDelay -= dt; if (peekDelay <= 0f) { peekRollT = peekPendRoll; peekYawT = peekPendYaw } }
        if (peekLeft > 0f) { peekLeft -= dt; if (peekLeft <= 0f) { peekRollT = 0f; peekYawT = 0f } }
        if (pillTiltIn > 0f) { pillTiltIn -= dt; if (pillTiltIn <= 0f) director.gesture(MotionDirector.Gesture.Tilt) }
        val om = 7f
        peekRollMotion.advance(peekRollT, dt, om, 0.78f)
        peekYawMotion.advance(peekYawT, dt, om, 0.82f)
        // Scan timeline.
        if (scan.isNotEmpty()) {
            val t0 = scanT; scanT += dt
            val it = scan.iterator()
            while (it.hasNext()) {
                val p = it.next()
                if (t0 <= p[0] && scanT > p[0]) { gaze.focus(p[1], p[2], p[3], priority = 2, travel = if (p[0] > 0f) 0.7f else 0f); it.remove() }
            }
        }
        if (wiggleU < 1f) wiggleU = (wiggleU + dt / wiggleDur).coerceAtMost(1f)
        if (stretchU < 1f) {
            val before = stretchU
            stretchU = (stretchU + dt / stretchDur).coerceAtMost(1f)
            if (before < 0.85f && stretchU >= 0.85f) {
                blink.request(minGap = 0f)
                if (rng.nextFloat() < 0.5f && sighU >= 1f) sighU = 0f // a relaxed exhale to finish
            }
        }
        if (driftU < 1f) {
            val before = driftU
            driftU = (driftU + dt / driftDur).coerceAtMost(1f)
            if (before < 0.82f && driftU >= 0.82f) {
                // Caught itself: perk up.
                blink.request(double = rng.nextFloat() < 0.35f, minGap = 0f)
                because("caught itself nodding off", MindRules.P_LIFE) { accent(FaceAccent.WakeSquint, 0.7f, force = true) }
            }
        }
    }

    private var freezeLeft = 0f
    private var sighU = 1f
    private var sighIn = 40f
    private var fidgetIn = 12f
    private val fidgetYawMotion = DampedMotion()
    private val fidgetYaw get() = fidgetYawMotion.value
    private var fidgetYawT = 0f
    private val fidgetRollMotion = DampedMotion()
    private val fidgetRoll get() = fidgetRollMotion.value
    private var fidgetRollT = 0f
    private class Pending(var left: Float, val pc: Percept, val handler: ((Response) -> Unit)?, val allowNone: Boolean)
    private val pending = ArrayList<Pending>()
    private var takeT = -1f
    private var takeX = 0f
    private var takeY = 0f

    /** Debug / harness: what the behaviour layer perceived and decided ("t,what"). */
    internal val behaviorLog = ArrayList<String>()
    private fun log(msg: String) { if (AvatarDebugMetrics.enabled) behaviorLog += "%.2f,%s".format(time, msg) }

    /** Harness: play a specific response now (e.g. the startle clip). */
    internal fun debugPerform(r: Response) = perform(r, Stimulus("debug", 0f, 0f, 1f))

    /** Harness: present a stimulus (direction in gaze units). */
    internal fun debugStimulus(kind: String, dirX: Float, dirY: Float, salience: Float, inView: Boolean = true) =
        stimulus(Stimulus(kind, dirX, dirY, salience, inView), null)

    /**
     * Route a happening through perception: maybe missed, otherwise it lands after a
     * human latency and a response is picked from the repertoire ([handler] gets it).
     */
    private fun stimulus(st: Stimulus, handler: ((Response) -> Unit)?, allowNone: Boolean = true) {
        sinceStimulus = 0f
        val pc = attention.perceive(st, gaze.eyeX, gaze.eyeY, temper, personality)
        if (pc == null) { log("missed ${st.kind} sal=%.2f".format(st.salience)); return }
        temper.onEvent(st.salience * 0.5f)
        // Engine18 perception: the same event is noticed sooner when alert, later when tired.
        val sc = MindRules.perceptionScale(mood.arousal, temper.energy, drowsy, knobs)
        // A touch is a reflex (≤ 0.1 s); everything else is noticed after a perception delay.
        val d = if (st.direct && st.kind == "poke") (0.05f + 0.03f * (sc - 1f)).coerceIn(0.04f, 0.1f)
            else (pc.delay * sc).coerceIn(0.08f, 2.2f)
        log("will-notice ${st.kind} in %.2fs%s (perception ×%.2f)".format(d, if (pc.lateNotice) " (late)" else "", sc))
        if (abs(st.dirX) + abs(st.dirY) > 0.2f && !st.direct) stm.happened(st.dirX, st.dirY, time, st.kind)
        pending += Pending(d, pc, handler, allowNone)
    }

    private fun humanUpdate(dt: Float, live: Boolean) {
        val busy = MotionDirector.isBusy(director.lifecycle)
        temper.valence = mood.valence
        temper.update(dt, personality, busy, quietSec, if (drowsy) 0.4f else 0f)
        attention.absorption = when {
            followLeft > 0f -> 0.7f
            director.lifecycle == MarkLifecycle.Thinking -> 0.75f
            director.lifecycle == MarkLifecycle.Streaming -> 0.6f
            director.lifecycle == MarkLifecycle.Working -> 0.65f
            gaze.kind == "stare" || gaze.kind == "think" -> 0.5f
            else -> 0.3f
        } - 0.15f * temper.boredom
        director.activity = temper.activity
        director.playful = temper.playful(personality)
        aliveness.activity = temper.activity
        gaze.playful = director.playful
        if (!live) { pending.clear(); return }

        val it = pending.iterator()
        val landed = ArrayList<Pending>()
        while (it.hasNext()) {
            val pd = it.next()
            pd.left -= dt
            if (pd.left <= 0f) { it.remove(); landed += pd }
        }
        for (pd in landed) {
            var r = repertoire.choose(pd.pc, temper, personality)
            if (!pd.allowNone && r == Response.None) r = Response.GlanceBlink
            log("noticed ${pd.pc.stim.kind} -> $r")
            val h = pd.handler
            if (h != null) h(r) else perform(r, pd.pc.stim)
        }
        // Double-take timeline: glance … start back … snap back and hold.
        if (takeT >= 0f) {
            val t0 = takeT
            takeT += dt
            if (t0 < 0.45f && takeT >= 0.45f) gaze.focus(takeX * 0.2f, takeY * 0.2f, 0.3f, priority = 4)
            if (t0 < 0.7f && takeT >= 0.7f) {
                gaze.focus(takeX, takeY, 1.6f + rng.nextFloat(), priority = 4)
                blink.request(minGap = 0f)
                if (rng.nextFloat() < 0.5f) setReaction("Surprised", 0.7f, ExpressionDirector.Style.Snap, cause = "double take")
                takeT = -1f
            }
        }
        if (freezeLeft > 0f) {
            freezeLeft -= dt
            if (freezeLeft <= 0f) blink.request(minGap = 0f) // thaw with a blink
        }
        if (sighU < 1f) sighU = (sighU + dt / MotionVocab[Action.Sigh].dur).coerceAtMost(1f)
        sighIn -= dt
        if (sighIn <= 0f) {
            sighIn = 25f + rng.nextFloat() * 40f
            if (!busy && (temper.boredom > 0.4f || temper.energy < 0.3f) && rng.nextFloat() < 0.5f) {
                sighU = 0f; log("sigh")
            }
        }
        fidgetIn -= dt
        if (fidgetIn <= 0f) {
            fidgetIn = (10f + rng.nextFloat() * 18f) / (0.4f + temper.activity)
            val a = temper.amplitude
            fidgetYawT = (rng.nextFloat() * 2f - 1f) * 3.5f * a
            fidgetRollT = (rng.nextFloat() * 2f - 1f) * 2.5f * a
            log("fidget")
        }
        if (pendingWiggleIn >= 0f) { pendingWiggleIn -= dt; if (pendingWiggleIn < 0f) playMoment(Moment.Wiggle) }
        momentsUpdate(dt)
    }

    /** Execute a picked response to [st]. */
    private fun perform(r: Response, st: Stimulus) = because("response $r to ${st.kind}", maxOf(whyPrio, MindRules.P_EVENT)) { perform0(r, st) }
    private fun perform0(r: Response, st: Stimulus) {
        fun hold(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        when (r) {
            Response.None -> Unit
            Response.EyesOnly -> gaze.focus(st.dirX * 0.9f, st.dirY * 0.9f, hold(0.8f, 2.2f), priority = 2, withHead = false)
            Response.Glance -> gaze.focus(st.dirX, st.dirY, hold(1.2f, 2.8f), priority = 2)
            Response.GlanceBlink -> { gaze.focus(st.dirX, st.dirY, hold(1f, 2.4f), priority = 2); blink.request(minGap = 0.3f) }
            Response.Acknowledge -> { gaze.focus(st.dirX, st.dirY, hold(1.2f, 2.2f), priority = 2); director.gesture(MotionDirector.Gesture.Nod) }
            Response.Smile -> { gaze.focus(st.dirX, st.dirY, hold(1f, 2f), priority = 2); accent(FaceAccent.Glee, hold(1f, 1.8f)) }
            Response.CuriousTilt -> {
                gaze.focus(st.dirX, st.dirY, hold(1.4f, 2.4f), priority = 2)
                director.gesture(MotionDirector.Gesture.Tilt)
                // Something odd caught its attention: now and then a brief confused look.
                val r = rng.nextFloat()
                if (r < 0.08f && reaction == null) setReaction("confused", hold(0.8f, 1.2f), cause = "odd stimulus")
                else if (r < 0.6f) accent(FaceAccent.Curious, hold(1f, 1.6f))
            }
            Response.Startle -> {
                director.gesture(MotionDirector.Gesture.Recoil)
                accent(FaceAccent.Startled, 0.75f, force = true)
                gaze.focus(st.dirX, st.dirY, hold(1.2f, 2f), priority = 3)
            }
            Response.Freeze -> {
                freezeLeft = hold(1.1f, 1.7f)
                director.freezeBase(freezeLeft)
                accent(FaceAccent.Startled, freezeLeft + 0.2f, force = true)
            }
            Response.DoubleTake -> {
                takeX = st.dirX.coerceIn(-0.9f, 0.9f); takeY = st.dirY.coerceIn(-0.6f, 0.6f)
                takeT = 0f
                gaze.focus(takeX * 0.8f, takeY * 0.8f, 0.45f, priority = 4)
            }
            Response.SlowBlink -> blink.request(minGap = 0.4f)
            Response.Sigh -> if (sighU >= 1f) sighU = 0f
        }
    }

    private fun lerpFace(f: FaceParams, w: Float): FaceParams {
        if (w <= 0f) return f
        fun e(p: EyeShape) = EyeShape(
            sx = p.sx + (1f - p.sx) * w, sy = p.sy + (1f - p.sy) * w,
            dx = p.dx * (1f - w), dy = p.dy * (1f - w), rot = p.rot * (1f - w),
            upper = p.upper + (1f - p.upper) * w, lower = p.lower + (1f - p.lower) * w,
            slant = p.slant * (1f - w),
        )
        return FaceParams(e(f.left), e(f.right))
    }

    /** Play a parametric look (with a cooldown so faces don't pile up) and let neighbours see it. */
    private fun accent(a: FaceAccent, sec: Float, force: Boolean = false): Boolean {
        if (!force && (accentCooldown > 0f || rig.active)) return false
        val prio = if (force) maxOf(whyPrio, MindRules.P_EVENT) else whyPrio
        val cause = why
        val busy = MotionDirector.isBusy(director.lifecycle)
        if (spinningFace && a in MindRules.NEGATIVE_ACCENTS) return false
        if (a.name in MindRules.APPRAISED_NEGATIVE_ACCENTS && !MindRules.hasEventCause(prio, cause)) { log("accent $a dropped: no appraised cause ($cause)"); return false }
        if (!ContextRules.accentOk(ctx, a)) { log("accent $a dropped: not in context $ctx"); return false }
        @Suppress("NAME_SHADOWING") val sec = if (prio >= MindRules.P_EVENT) sec else max(sec, MindRules.minDwell(a.name)) // spontaneous: full dwell
        if (!mind.faceSafe(prio, expr.morphing) || (!force && !mind.budgetOk(prio, busy, mood.arousal))) {
            mind.propose(Intention(Lane.Face, "accent:$a", prio, cause, mind.now, if (force) 2.5f else 1.2f) {
                rig.play(a, sec); mind.landAccent(a.name, prio, cause, sec); log("accent $a cause=$cause (after waiting)")
            })
            accentCooldown = sec + 1.5f
            return true
        }
        mind.landAccent(a.name, prio, cause, sec)
        log("accent $a cause=$cause sec=${"%.2f".format(sec)}")
        rig.play(a, sec)
        accentCooldown = sec + 1.5f + rng.nextFloat() * 2f
        stage?.let { st ->
            st.entry(stageId)?.let { me -> st.emit(AvatarStage.EventKind.AvatarAccent, at = me.center, source = stageId, extra = a.ordinal) }
        }
        return true
    }

    private fun targetCenter(kind: AvatarStage.Target): Offset? = stage?.target(kind)?.center

    // ----------------------------------------------------------- alive layer

    /**
     * Engine19: what the face channel steers to. When a reaction ends while its follow-up is
     * already planned (a queued reaction, a story beat, a waiting intention, a stimulus about
     * to be noticed) the face keeps its current target, so the next beat morphs straight from
     * it instead of relaxing toward Neutral and starting again.
     */
    private fun faceTarget(): String {
        // Engine20: a static avatar heads straight for its rest face (busy faces now hold their
        // emotional dwell, which must not keep a frozen avatar from settling).
        if (!animate && reaction == null) return "Neutral"
        val t = currentExpressionTarget()
        if (animate && reaction == null && ExpressionDirector.normalize(t) == "Neutral" && expr.to != "Neutral" &&
            (reactionQueue != null || train.isNotEmpty() || mind.queued(Lane.Face) > 0 || pending.isNotEmpty())
        ) return expr.to
        return t
    }

    // ------------------------------------------------- engine21: context appropriateness
    private var lastUserAt = -999f
    /** Engine21: waking because the context needs attention — eyes open promptly, no shut-eyed shake. */
    private var briskWake = false
    private var briskUntil = -1f
    /** Current context (see [ContextRules.TABLE]). */
    internal var ctx = ContextRules.Ctx.Idle; private set
    /** How long the shown face has been a closed-eye one (s). */
    private var closedHeld = 0f
    /** Engine21.1: the brief "I was reading that" Bored beat runs until then (context-allowed). */
    private var briefBoredUntil = -1f
    /** How long a face counts as held for the context table. */
    private fun heldSec(face: String): Float = when (face) {
        "Eyes closed" -> closedHeld
        "Bored" -> if (time < briefBoredUntil) 0f else 99f
        else -> 99f
    }
    /** Engine21.1: true while the timeline-pan story is actually reading (eyes on the timeline). */
    private var readingUntil = -1f
    private var readingInterruptCooldown = 0f

    /**
     * Engine21.1 steering (a): the user interrupted its timeline reading (collapsed it, sent a
     * message, poked…). Reading stops; sometimes — not always, with a cooldown — a short
     * unimpressed Bored beat: "hey, I was reading that". Only while it is actually reading.
     */
    private fun readingInterrupted(what: String) {
        if (time >= readingUntil) return
        readingUntil = -1f
        if (train.any { it.what.startsWith("timeline-pan") }) train.clear()
        // Eyes leave the timeline and come back to the user (a poke then takes them to the finger).
        gaze.focus(0f, 0.05f, 1.2f, priority = 2, travel = 0.35f)
        val go = debugHooksAlways || readingInterruptCooldown <= 0f && rng.nextFloat() < 0.4f
        log("reading interrupted by $what" + if (go) ": unimpressed beat" else "")
        if (!go) return
        readingInterruptCooldown = 45f
        val sec = 1.2f + rng.nextFloat() * 0.5f
        briefBoredUntil = time + sec + 0.6f
        because("reading interrupted by $what", MindRules.P_EVENT) {
            setReaction("Bored", sec, ExpressionDirector.Style.Soft, cause = "reading interrupted by $what")
        }
    }

    /**
     * Engine21: re-check the running state against the context every frame. A rest, doze,
     * drowsy spell or low-energy face that was fine while idle ends the moment the avatar
     * should be attentive (generating, streaming, the user active).
     */
    private fun contextUpdate(dt: Float) {
        closedHeld = if (expr.to == "Eyes closed" || expr.to == "Sleeping") closedHeld + dt else 0f
        if (!ContextRules.attentive(ctx)) return
        if (restPhase > 0 || drowsy) { log("context $ctx: ends rest/drowsy"); briskWake = true; wake(); briskWake = false; briskUntil = time + 1.5f }
        val r = reaction
        if (r != null && !ContextRules.faceOk(ctx, r, heldSec(r))) {
            log("context $ctx: ends $r (held ${"%.2f".format(closedHeld)} s)")
            reaction = null; reactionLeft = 0f
        }
    }

    private fun currentExpressionTarget(): String {
        val t = currentExpressionTarget0()
        if (!animate || reaction != null) return t
        return if (ContextRules.faceOk(ctx, t, heldSec(t))) t else ContextRules.substitute(ctx, t)
    }

    private fun currentExpressionTarget0(): String {
        if (!animate) return "Neutral"
        reaction?.let { return it }
        val life = director.lifecycle
        if (drowsy) return "Neutral"
        val clipExpr = director.clipExpression
        if (clipExpr != null && life != MarkLifecycle.Idle && life != MarkLifecycle.Listening) {
            mapClipExpression(clipExpr)?.let { return it }
        }
        // Busy phases rotate through a varied, non-repeating set of faces instead of
        // holding one "thinking" face the whole time.
        if (MotionDirector.isBusy(life)) return aliveness.busyFace
        return MarkLifecycleMapper.defaultExpression(life)
    }

    private fun playBeat(b: AlivenessEngine.Beat) = because("aliveness beat ${b::class.simpleName}", MindRules.P_IDLE) { playBeat0(b) }
    private fun playBeat0(b: AlivenessEngine.Beat) {
        when (b) {
            is AlivenessEngine.Beat.Gesture -> if (b.g == MotionDirector.Gesture.Spin) {
                if (director.lifecycle != MarkLifecycle.Idle || recently("nice", 12f) || pokeCount > 0) trySpin("lively beat")
            } else director.gesture(b.g)
            is AlivenessEngine.Beat.Accent -> if (b.appraised) because("tone appraisal", MindRules.P_IDLE) { accent(b.a, b.sec, force = b.a == FaceAccent.Startled) }
                else accent(b.a, b.sec, force = b.a == FaceAccent.Startled)
            is AlivenessEngine.Beat.Face -> if (reaction == null) setReaction(b.expr, b.sec, cause = if (b.appraised) "tone appraisal" else "feeling (fatigue/rhythm)")
            is AlivenessEngine.Beat.Blink -> blink.request(minGap = 0.8f)
            is AlivenessEngine.Beat.DoubleBlink -> blink.request(double = true, minGap = 0.8f)
            is AlivenessEngine.Beat.Glance -> {
                if (gaze.focused) return
                val real = if (b.kind == "read") stage?.target(AvatarStage.Target.StreamingText)?.let { r ->
                    Offset(r.left + r.width * (0.15f + 0.7f * rng.nextFloat()), r.bottom - minOf(r.height, 120f) * rng.nextFloat())
                } else null
                if (real != null) {
                    val (gx, gy) = lookAt(real)
                    gaze.focus(gx, gy, b.sec, priority = 0)
                } else gaze.focus(b.x, b.y, b.sec, priority = 0)
            }
        }
    }

    // ---------------------------------------------------- engine15: causal trains

    /** One delayed beat of a micro-story (notice → react → settle). */
    private class TrainStep(var at: Float, val what: String, val act: () -> Unit, val cause: String = "", val prio: Int = 0)
    private val train = ArrayList<TrainStep>()
    /** Recent-event memory: what happened, and when (seconds of [time]). */
    private val memory = ArrayDeque<Pair<String, Float>>()
    private fun remember(what: String) { memory.addLast(what to time); while (memory.size > 8) memory.removeFirst() }
    private fun recently(what: String, sec: Float) = memory.any { it.first == what && time - it.second < sec }

    /**
     * Engine18 planner: a beat plan (notice → pause → react → settle). A running plan is only
     * replaced at a safe point — between beats with time to spare and no morph under way;
     * otherwise the new plan waits as an intention (and expires if it never gets a turn).
     */
    private fun planSafePoint() = train.isEmpty() || (train[0].at > 0.35f && !expr.morphing)

    private fun tell(story: String, vararg steps: Pair<Float, String>, acts: List<() -> Unit>) {
        if (!planSafePoint()) {
            val c = why; val p = whyPrio
            log("plan $story waits for a safe point")
            mind.propose(Intention(Lane.Body, "plan", p, c, mind.now, 2.5f) { because(c, p) { tellNow(story, *steps, acts = acts) } })
            return
        }
        tellNow(story, *steps, acts = acts)
    }

    private fun tellNow(story: String, vararg steps: Pair<Float, String>, acts: List<() -> Unit>) {
        train.clear()
        steps.forEachIndexed { i, (d, w) -> train += TrainStep(d, "$story/$w", acts[i], if (why == "unspecified") "story $story" else why, whyPrio) }
        log("story $story: " + steps.joinToString(" → ") { "%.2f %s".format(it.first, it.second) })
    }

    private fun trainUpdate(dt: Float) {
        if (train.isEmpty()) return
        train.forEach { it.at -= dt }
        while (train.isNotEmpty() && train[0].at <= 0f) {
            val st = train.removeAt(0)
            log("beat ${st.what}")
            because(st.cause, st.prio) { st.act() }
        }
    }

    /**
     * The idle attention episode landed on something. React after a perception delay, and
     * only with a reason: notice → (curious) → maybe pleased + nod; or acknowledge it.
     */
    private fun attentionEvent(ev: String) = because("idle attention: $ev", MindRules.P_IDLE) { attentionEvent0(ev) }
    private fun attentionEvent0(ev: String) {
        fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        val free = director.lifecycle == MarkLifecycle.Idle && !drowsy && reaction == null &&
            social == null && train.isEmpty() && spT < 0f && recoverT <= 0f
        if (!free) return
        remember(ev)
        // The eyes are only just leaving: arrival (~0.25 s) plus perception delay.
        val d = rr(0.45f, 0.75f)
        when (ev) {
            "notice" -> {
                val r = rng.nextFloat()
                when {
                    mood.valence > 0.15f && r < 0.3f && !recently("face:Happy", 30f) -> {
                        val c = d + rr(0.7f, 1.0f)
                        tell("notice→pleased", d to "curious", c to "Happy", c + 0.25f to "nod", acts = listOf(
                            { accent(FaceAccent.Curious, c - d + 0.3f) },
                            { setReaction("Happy", rr(1.2f, 1.8f), ExpressionDirector.Style.Soft, cause = "thought completes: likes what it noticed") },
                            { director.gesture(MotionDirector.Gesture.Nod) },
                        ))
                    }
                    r < 0.65f -> tell("notice→curious", d to "curious", acts = listOf({
                        accent(FaceAccent.Curious, rr(0.9f, 1.5f))
                        if (rng.nextFloat() < 0.3f) director.gesture(MotionDirector.Gesture.Tilt)
                    }))
                    else -> tell("notice→blink", d to "blink", acts = listOf({ blink.request(minGap = 0.4f) }))
                }
            }
            "ack" -> tell("acknowledge", d to "nod", d + 0.35f to "blink", acts = listOf(
                { director.gesture(MotionDirector.Gesture.Nod) },
                { blink.request(minGap = 0.3f) },
            ))
        }
    }

    private val lowFaces = setOf("Bored", "Sleeping", "Sad", "Eyes closed")
    private val brightFaces = setOf("Happy", "Group 3", "playful")

    private fun setReaction(e0: String, sec: Float, style: ExpressionDirector.Style? = null, cause: String = "unspecified") {
        // Engine19: every face morphs straight into the next (bored → happy, happy → sad):
        // no bridge through Neutral.
        val from = expr.to
        setReactionRaw(e0, sec, style, cause, from)
    }

    private fun setReactionRaw(e0: String, sec: Float, style: ExpressionDirector.Style?, cause0: String, from: String) {
        val cause = if (cause0 == "unspecified" || cause0 == "queued") (if (why != "unspecified") why else cause0) else cause0
        val prio = whyPrio
        if (e0 in MindRules.APPRAISED_NEGATIVE && !MindRules.hasEventCause(prio, cause)) { log("expr $e0 dropped: no appraised cause ($cause)"); return }
        @Suppress("NAME_SHADOWING") var sec = if (prio >= MindRules.P_EVENT) sec else max(sec, MindRules.minDwell(e0)) // spontaneous: full dwell
        // Engine21: context table. Attentive: eyes may only shut briefly; no sleepy / bored.
        if (e0 == "Eyes closed" && ContextRules.attentive(ctx)) sec = min(sec, ContextRules.MAX_CLOSED_ATTENTIVE)
        if (!ContextRules.faceOk(ctx, e0, sec)) { log("expr $e0 dropped: not in context $ctx ($cause)"); return }
        // Engine18: one intention owns the face. A weaker one waits (queued, expires).
        val cur = reaction
        if (cur != null && reactionLeft > 0f && prio < reactionPrio) {
            log("intention queued: $e0 (p$prio) behind $cur (p$reactionPrio) cause=$cause")
            mind.propose(Intention(Lane.Face, "reaction", prio, cause, mind.now, 3f) { because(cause, prio) { setReactionRaw(e0, sec, style, cause, from) } })
            return
        }
        reactionPrio = prio; reactionCause = cause; reactionWait = 0f
        log("expr $from→$e0 cause=$cause")
        remember("face:$e0")
        // Confused has a left/right variant: the raised brow goes on the side it is looking
        // toward; when looking ahead, either side (not the same one every time).
        val e = if (e0 != "confused") e0 else {
            val hx = frame.headLookX
            val left = when { hx < -0.12f -> true; hx > 0.12f -> false; else -> !lastConfusedLeft || rng.nextFloat() < 0.35f }
            lastConfusedLeft = left
            if (left) "confused left" else "confused"
        }
        reaction = e
        reactionLeft = sec
        reactionStyle = style ?: when (e) {
            "Surprised" -> ExpressionDirector.Style.Snap
            "Bored", "Sleeping" -> ExpressionDirector.Style.Gradual
            else -> null
        }
    }
    private var reactionStyle: ExpressionDirector.Style? = null
    private var lastConfusedLeft = false

    private fun aliveUpdate(dt: Float, nowMs: Long) {
        lastSpinAgo += dt
        trainUpdate(dt)
        accentCooldown -= dt
        flingCooldown -= dt
        eventCooldown -= dt
        readingInterruptCooldown -= dt
        mirror?.let { (a, sec) ->
            mirrorIn -= dt
            if (mirrorIn <= 0f) { mirror = null; because("mirrors a neighbour's face", MindRules.P_IDLE) { accent(a, sec) } }
        }
        if (forgiveIn > 0f) {
            forgiveIn -= dt
            if (forgiveIn <= 0f) because("can't stay cross: forgives", MindRules.P_USER) {
                // Can't stay cross for long: a blink and a small smile.
                blink.request(minGap = 0.3f)
                accent(FaceAccent.Glee, 1.4f, force = true)
                mood.nudge(0.25f, 0f)
            }
        }
        // ---- aliveness engine: spontaneous, context-aware beats ----
        val engineCalm = social == null && reaction == null && followLeft <= 0f && !drowsy && train.isEmpty()
        aliveness.update(dt, director.lifecycle, mood.valence, mood.arousal, engineCalm)?.let { b ->
            val small = b is AlivenessEngine.Beat.Blink || b is AlivenessEngine.Beat.DoubleBlink
            if (small || mind.budgetOk(MindRules.P_IDLE, MotionDirector.isBusy(director.lifecycle), mood.arousal)) {
                if (!small) mind.noteAct(Lane.Body, "beat ${b::class.simpleName}", MindRules.P_IDLE, "aliveness")
                playBeat(b)
            }
        }
        aliveness.takeMoodNudge()?.let { (v, a) -> mood.nudge(v, a) }
        typingCooldown -= dt
        pokeWindow -= dt
        if (pokeWindow <= 0f) pokeCount = 0

        if (perkIn > 0f) {
            perkIn -= dt
            // Caught yourself nodding off: a quick double blink.
            if (perkIn <= 0f) blink.request(double = true, minGap = 0.2f)
        }
        reaction?.let { re ->
            val shown = ExpressionDirector.normalize(re) == expr.to
            if (shown) { reactionWait = 0f; reactionLeft -= dt } else reactionWait += dt
            if (reactionLeft <= 0f) reaction = null
            else if (reactionWait > 3f) { log("intention expired: face $re (never reached a safe point)"); reaction = null; reactionWait = 0f }
        }
        reactionQueue?.let { (e, sec) ->
            reactionQueueIn -= dt
            if (reactionQueueIn <= 0f) {
                if (!(crossNow() && (e == "Happy" || ExpressionDirector.normalize(e) == "Happy"))) {
                    because("$rqWhy (follow-up)", rqPrio) { setReaction(e, sec) }
                }
                reactionQueue = null
            }
        }

        val calm = director.lifecycle == MarkLifecycle.Idle

        // Reply finished → a little happy beat (not every time).
        if (director.finishedBusy) because("reply end: ${Appraisal.replyEnd(busySec, mood.valence)}", MindRules.P_EVENT) {
            director.finishedBusy = false
            val meaning = Appraisal.replyEnd(busySec, mood.valence)
            log("appraisal reply end after ${busySec.toInt()} s → $meaning")
            busySec = 0f
            if (meaning == Meaning.Relieved && sighU >= 1f) sighU = 0f
            val r = rng.nextFloat()
            if (r < 0.14f && mood.valence > 0.15f && lastSpinAgo > 50f && trySpin("reply finished, good mood")) {
                remember("nice")
            } else if (r < 0.6f) {
                remember("nice")
                setReaction("Happy", 1.6f + rng.nextFloat(), cause = "reply finished")
                val g = rng.nextFloat()
                when {
                    g < 0.35f -> director.gesture(MotionDirector.Gesture.Hop)
                    g < 0.6f -> director.gesture(MotionDirector.Gesture.Nod)
                }
            } else {
                if (rng.nextFloat() < 0.25f && moments.allow(Moment.SmugNod, 50f)) {
                    moments.note(Moment.SmugNod); playMoment(Moment.SmugNod)
                } else aliveness.onReplyFinished()?.let { playBeat(it) } ?: blink.request()
            }
        }

        // Social glances.
        social?.let { s -> runSocial(s, dt) }
        val st = stage
        if (st != null && social == null) {
            st.takeGlanceFor(stageId)?.let { g ->
                if (calm && !drowsy && reaction == null) {
                    social = Social(
                        other = g.from,
                        lookAt = 0.3f + rng.nextFloat() * 0.4f,
                        skepticAt = if (g.skeptic == stageId) 1.0f + rng.nextFloat() * 0.3f else -1f,
                        end = 2.0f + rng.nextFloat() * 0.8f,
                    )
                }
            }
            socialIn -= dt
            if (social == null && socialIn <= 0f) {
                socialIn = 8f + rng.nextFloat() * 12f
                if (calm && !drowsy && reaction == null && rng.nextFloat() < 0.2f) {
                    val me = st.entry(stageId)
                    val maxD = me?.let { max(it.windowW, it.windowH) * 0.7f } ?: 0f
                    st.tryStartGlance(stageId, maxD, nowMs)?.let { g ->
                        social = Social(
                            other = g.to,
                            lookAt = 0f,
                            skepticAt = if (g.skeptic == stageId) 1.1f + rng.nextFloat() * 0.3f else -1f,
                            end = 2.2f + rng.nextFloat() * 0.8f,
                        )
                    }
                }
            }
        }

        if (!calm) return

        // Engine16: no spins while idle. Instead, the episode planner (rare, causal beats).
        episodeUpdate(dt)
        // Engine18 memory: now and then glance back at where something happened.
        glanceBackIn -= dt
        if (glanceBackIn <= 0f) {
            glanceBackIn = 3f + rng.nextFloat() * 3f
            val p = stm.glanceBackCandidate(time)
            if (p != null && !drowsy && reaction == null && social == null && train.isEmpty() && !gaze.focused && spT < 0f &&
                rng.nextFloat() < 0.15f + 0.35f * knobs.curiosity && mind.budgetOk(MindRules.P_IDLE, false, mood.arousal)
            ) {
                p.revisited = true
                mind.noteAct(Lane.Gaze, "glance back", MindRules.P_IDLE, "memory: where ${p.what} happened")
                log("glance back at where ${p.what} happened (%.0f s ago)".format(time - p.t))
                gaze.focus(p.x * 0.9f, p.y * 0.9f, 0.9f + rng.nextFloat() * 0.5f, priority = 1)
            }
        }

        // Drowsy: now and then nod off a little, then catch yourself.
        if (drowsy) {
            droopIn -= dt
            if (droopIn <= 0f) {
                droopIn = 9f + rng.nextFloat() * 9f
                if (restPhase == 0 && rng.nextFloat() < 0.45f && director.gesture(MotionDirector.Gesture.Droop)) {
                    perkIn = 2.4f + rng.nextFloat() * 0.8f
                }
            }
        }

        // Engine15: no random mood faces. The face changes on its own only with a reason.
        moodIn -= dt
        if (moodIn <= 0f) {
            moodIn = 8f + rng.nextFloat() * 6f
            if (!drowsy && reaction == null && social == null && train.isEmpty()) {
                val v = mood.valence; val a = mood.arousal
                when {
                    // Boredom settles after a long stillness.
                    quietSec > 50f && aliveness.boredom > 0.45f && a < 0.5f && !recently("face:Bored", 60f) ->
                        setReaction("Bored", 3f + rng.nextFloat() * 3f, ExpressionDirector.Style.Gradual, cause = "boredom settles after stillness")
                    // Afterglow: a recent nice thing still warms the face, once.
                    v > 0.35f && (recently("nice", 25f)) && !recently("face:Happy", 30f) ->
                        setReaction("Happy", 1.6f + rng.nextFloat(), ExpressionDirector.Style.Gradual, cause = "afterglow of a nice moment")
                }
            }
        }
    }

    private fun runSocial(s: Social, dt: Float) {
        s.t += dt
        val st = stage
        val other = st?.entry(s.other)
        if (other == null || director.lifecycle != MarkLifecycle.Idle) {
            social = null
            gaze.releaseFocus()
            return
        }
        if (!s.looking && s.t >= s.lookAt) {
            s.looking = true
            director.gesture(MotionDirector.Gesture.Attend, s.end - s.t + 0.3f)
            val (gx, gy) = lookAt(other.center)
            gaze.focus(gx, gy, s.end - s.t + 0.2f, priority = 3)
        }
        if (s.looking) {
            // Keep tracking if either avatar moves (scrolling).
            val (gx, gy) = lookAt(other.center)
            gaze.focus(gx, gy, max(0.05f, s.end - s.t), priority = 3)
        }
        if (s.skepticAt > 0f && !s.skepticDone && s.t >= s.skepticAt) {
            s.skepticDone = true
            setReaction("confused", 1.1f + rng.nextFloat() * 0.5f, cause = "skeptic of neighbour")
            if (rng.nextFloat() < 0.7f) director.gesture(MotionDirector.Gesture.Tilt)
        }
        if (s.looking) contactSec += dt
        if (s.t >= s.end) {
            social = null
            // Held eye contact a moment: sometimes a shy look away.
            if (contactSec > 1f && s.skepticAt < 0f && rng.nextFloat() < 0.3f) {
                accent(FaceAccent.Shy, 1.4f)
                gaze.focus(if (lookAt(other.center).first > 0f) -0.45f else 0.45f, 0.4f, 1.0f, priority = 2)
                contactSec = 0f
                return
            }
            contactSec = 0f
            val (gx, _) = lookAt(other.center)
            gaze.releaseFocus(awayX = if (gx > 0f) -(0.3f + rng.nextFloat() * 0.3f) else 0.3f + rng.nextFloat() * 0.3f)
            if (rng.nextFloat() < 0.5f) blink.request()
        }
    }

    private fun lookAt(target: Offset): Pair<Float, Float> {
        val me = stage?.entry(stageId) ?: return 0f to 0f
        val dx = target.x - me.center.x
        val dy = target.y - me.center.y
        val d = max(hypot(dx, dy), me.radius * 2.2f).coerceAtLeast(1f)
        return (dx / d * 0.85f) to (dy / d * 0.6f)
    }

    private fun windowBottom(): Offset? {
        targetCenter(AvatarStage.Target.Composer)?.let { return it }
        val me = stage?.entry(stageId) ?: return null
        if (me.windowH <= 0f) return null
        return Offset(me.windowW * 0.5f, me.windowH * 0.93f)
    }

    // ------------------------------------------------------- composer (typing)

    /** Where the caret roughly is: the bottom input, left → right as the line grows. */
    private fun caretPoint(): Offset? {
        val st = stage ?: return null
        val perLine = CARET_CHARS_PER_LINE
        val frac = (st.composerLineLen % perLine) / perLine.toFloat()
        st.target(AvatarStage.Target.Composer)?.let { r ->
            // The real input field: caret runs left → right inside it.
            return Offset(r.left + r.width * (0.08f + 0.8f * frac), r.center.y)
        }
        val me = st.entry(stageId) ?: return null
        if (me.windowW <= 0f || me.windowH <= 0f) return null
        return Offset(me.windowW * (0.12f + 0.76f * frac), me.windowH * 0.93f)
    }

    private fun startFollow(sec: Float) {
        if (social != null || caretPoint() == null) return
        followLeft = sec
        attendedTypingAt = time
    }

    private fun onTypingStarted() = because("user started typing", MindRules.P_EVENT) { onTypingStarted0() }
    private fun onTypingStarted0() {
        if (typingCooldown > 0f) return
        val r = rng.nextFloat()
        when {
            // Now and then: read along while you type.
            r < 0.14f -> {
                typingCooldown = 8f
                startFollow(2.5f + rng.nextFloat() * 2.5f)
            }
            // Often just a quick look down at the input.
            r < 0.5f -> {
                typingCooldown = 6f
                val at = windowBottom() ?: return
                val (gx, gy) = lookAt(at)
                gaze.focus(gx, gy, 1.0f + rng.nextFloat() * 0.8f, priority = 2)
                attendedTypingAt = time
            }
            // Otherwise, nothing at all.
        }
    }

    /**
     * Rare, quiet reactions to the composer: following the caret for a few seconds,
     * one small curious tilt when a message gets long. Never more than one at once.
     */
    private fun typingUpdate(dt: Float, hints: AvatarMotionHints, nowMs: Long) {
        val st = stage ?: return
        if (followLeft > 0f) {
            followLeft -= dt
            val paused = nowMs - st.composerLastKeyMs > 1600L
            if (!hints.isTyping || paused || followLeft <= 0f || social != null) {
                followLeft = 0f
                // Done reading along: drift back up, sometimes with a blink.
                gaze.releaseFocus()
                if (rng.nextFloat() < 0.3f) blink.request()
            } else {
                caretPoint()?.let { p ->
                    val (gx, gy) = lookAt(p)
                    gaze.focus(gx, gy, 0.35f, priority = 2)
                }
            }
        }
        if (!hints.isTyping) {
            if (st.composerLen == 0) curiousDone = false
            followCheckIn = 5f + rng.nextFloat() * 5f
            return
        }
        // Mid-message: a slim chance to start reading along.
        followCheckIn -= dt
        if (followCheckIn <= 0f) {
            followCheckIn = 6f + rng.nextFloat() * 8f
            if (followLeft <= 0f && social == null && reaction == null && rng.nextFloat() < 0.1f) {
                startFollow(2f + rng.nextFloat() * 2.5f)
            }
        }
        // A long message: once per message, maybe, a small "oh, interesting" tilt.
        val longMessage = st.composerLen > 160 ||
            (st.composerBurstStartMs > 0L && nowMs - st.composerBurstStartMs > 20_000L)
        if (!curiousDone && longMessage) {
            curiousDone = true
            if (rng.nextFloat() < 0.35f && social == null && followLeft <= 0f && reaction == null) {
                caretPoint()?.let { p ->
                    val (gx, gy) = lookAt(p)
                    gaze.focus(gx, gy, 1.3f + rng.nextFloat() * 0.6f, priority = 2)
                }
                director.gesture(MotionDirector.Gesture.Tilt)
                if (rng.nextFloat() < 0.45f) setReaction("Surprised", 0.55f, cause = "typing started")
                attendedTypingAt = time
            }
        }
    }

    /** Message sent: sometimes a short look at it, a blink, a tiny pleased beat. */
    private fun onMessageSent(e: AvatarStage.Event) {
        if (social != null) return
        if (followLeft > 0f) {
            followLeft = 0f
            gaze.releaseFocus()
        }
        val wasWatching = time - attendedTypingAt < 10f
        if (rng.nextFloat() > if (wasWatching) 0.5f else 0.25f) return
        val me = stage?.entry(stageId)
        // If the sender's avatar is on screen below, look at it (a turn-taking look).
        val sender = me?.let { m ->
            stage?.neighbors(stageId, max(m.windowW, m.windowH))?.firstOrNull { it.center.y > m.center.y + m.radius }
        }
        val at = e.at ?: sender?.center ?: targetCenter(AvatarStage.Target.Composer)
            ?: me?.let { Offset(it.windowW * 0.72f, it.windowH * 0.8f) } ?: return
        val (gx, gy) = lookAt(at)
        gaze.focus(gx, gy, 0.6f + rng.nextFloat() * 0.3f, priority = 2)
        if (rng.nextFloat() < 0.5f) blink.request(minGap = 0.4f)
        val calm = director.lifecycle == MarkLifecycle.Idle || director.lifecycle == MarkLifecycle.Listening
        if (calm && reaction == null && rng.nextFloat() < 0.3f) setReaction("Happy", 0.7f, cause = "message arrived")
        eventCooldown = 6f
    }

    /** Returns true if any event arrived (counts as a stimulus). */
    private fun processStageEvents(live: Boolean): Boolean {
        val st = stage ?: return false
        if (lastEventSeq < 0L) {
            lastEventSeq = st.latestSeq
            return false
        }
        val evs = st.eventsAfter(lastEventSeq)
        if (evs.isEmpty()) return false
        lastEventSeq = evs.last().seq
        if (!live) return false
        var any = false
        for (e in evs) {
            // The avatar's own reply tone is not perceived, it is felt: straight through.
            if (e.kind == AvatarStage.EventKind.ReplyTone) { legacyEvent(e); continue }
            // Its own face changes are not something it "notices" (that read as a double take).
            if (e.kind == AvatarStage.EventKind.AvatarAccent && e.source == stageId) continue
            any = true
            if (e.kind != AvatarStage.EventKind.AvatarAccent && e.kind != AvatarStage.EventKind.PillExpanded &&
                e.kind != AvatarStage.EventKind.MessageArrived) readingInterrupted(e.kind.name)
            if (debugHooksAlways) { log("demo: ${e.kind} noticed"); legacyEvent(e); continue }
            val (dx, dy) = e.at?.let { lookAt(it) } ?: (0f to 0.6f)
            val st = Stimulus(e.kind.name, dx, dy, salienceFor(e.kind), direct = e.kind == AvatarStage.EventKind.AppResumed || e.kind == AvatarStage.EventKind.Error)
            stimulus(st, { r ->
                when {
                    // Felt, not seen (errors, app resume): always registers, own handling.
                    st.direct -> legacyEvent(e)
                    r == Response.None -> Unit
                    r == Response.Startle || r == Response.Freeze || r == Response.DoubleTake -> { aliveness.onEvent(e); perform(r, st) }
                    else -> legacyEvent(e)
                }
            }, allowNone = !st.direct)
        }
        // Engine16: only outside happenings count as activity (its own accents don't keep it awake).
        return any
    }

    private fun salienceFor(k: AvatarStage.EventKind): Float = when (k) {
        AvatarStage.EventKind.Error -> 1f
        AvatarStage.EventKind.AppResumed -> 0.9f
        AvatarStage.EventKind.MessageSent -> 0.75f
        AvatarStage.EventKind.Deleted -> 0.7f
        AvatarStage.EventKind.MessageArrived -> 0.6f
        AvatarStage.EventKind.PillExpanded -> 0.6f
        AvatarStage.EventKind.Fling -> 0.5f
        AvatarStage.EventKind.KeyboardOpened, AvatarStage.EventKind.MenuOpened, AvatarStage.EventKind.Regenerated -> 0.5f
        AvatarStage.EventKind.UserTyping -> 0.5f
        AvatarStage.EventKind.AvatarAccent -> 0.4f
        AvatarStage.EventKind.Copied -> 0.4f
        AvatarStage.EventKind.Scroll -> 0.3f
        AvatarStage.EventKind.AttachmentAdded -> 0.6f
        AvatarStage.EventKind.EditorOpened -> 0.55f
        AvatarStage.EventKind.ModelPickerOpened -> 0.5f
        AvatarStage.EventKind.SettingToggled -> 0.35f
        else -> 0.4f
    }

    /** The pre-engine11 handling of one stage event, run once it has been perceived. */
    private fun legacyEvent(e: AvatarStage.Event) = because("app event ${e.kind}", if (e.kind == AvatarStage.EventKind.Error) MindRules.P_URGENT else MindRules.P_EVENT) { legacyEvent0(e) }
    private fun legacyEvent0(e: AvatarStage.Event) {
        val calm = director.lifecycle == MarkLifecycle.Idle || director.lifecycle == MarkLifecycle.Listening
        run {
            if (e.kind == AvatarStage.EventKind.MessageSent) {
                onMessageSent(e)
                return
            }
            if (onUiEvent(e, calm)) return
            if (!calm || social != null || eventCooldown > 0f) return
            when (e.kind) {
                AvatarStage.EventKind.MessageArrived -> if (rng.nextFloat() < 0.45f) {
                    val at = e.at ?: windowBottom() ?: return
                    val (gx, gy) = lookAt(at)
                    gaze.focus(gx, gy, 0.9f + rng.nextFloat() * 0.7f, priority = 2)
                    eventCooldown = 8f
                }
                AvatarStage.EventKind.Scroll -> if (hookGo("scroll", 12f, 0.25f)) {
                    // Engine21: follows the content a little — a glance the way it moves, now and
                    // then a short pursuit along it, or a curious look at what came into view.
                    val r = rng.nextFloat()
                    val gx = (rng.nextFloat() - 0.5f) * 0.3f
                    when {
                        r < 0.55f -> gaze.focus(gx, -0.32f * e.direction, 0.6f + rng.nextFloat() * 0.4f, priority = 1)
                        r < 0.85f -> tell("scroll-follow", 0f to "eyes to the content", 0.35f to "follow it", acts = listOf(
                            { gaze.focus(gx, 0.3f * e.direction, 0.4f, priority = 1) },
                            { gaze.focus(gx * 0.5f, -0.3f * e.direction, 0.7f, priority = 1, travel = 0.9f) },
                        ))
                        else -> { gaze.focus(gx, -0.25f * e.direction, 0.9f, priority = 1); accent(FaceAccent.Curious, 0.9f) }
                    }
                    eventCooldown = 12f
                }
                AvatarStage.EventKind.UserTyping -> onTypingStarted()
                else -> Unit
            }
        }
    }

    private fun onPoke(r: Response) {
        pokeCount++
        pokeWindow = 2.2f
        mood.nudge(0f, 0.12f)
        stm.poked(time)
        // Engine18 appraisal: what this poke means here (burst, habituation, mood, patience).
        val meaning = Appraisal.poke(pokeCount, stm.pokeFamiliar, mood.valence, knobs)
        log("appraisal poke #$pokeCount familiar=%.1f → $meaning".format(stm.pokeFamiliar))
        when {
            meaning == Meaning.FedUp -> {
                // Fine, you win: all is forgiven first; the spin comes only once the face is no
                // longer cross (never spin while angry), or not at all.
                if (reactionQueue?.first == "Happy") reactionQueue = null
                pokeCount = 0
                forgiveIn = 0.3f
                if (lastSpinAgo > 30f) mind.propose(Intention(Lane.Spin, "spin", MindRules.P_USER, "poked a lot: fine, you win", mind.now, 6f) {
                    because("poked a lot: fine, you win", MindRules.P_USER) { trySpin("poked a lot: fine, you win") }
                })
            }
            meaning == Meaning.Habituated && pokeCount == 1 -> {
                // Used to it by now: a calm blink and a look (already at the finger), no bounce.
                blink.request(minGap = 0f)
            }
            meaning == Meaning.Amused && pokeCount == 1 -> {
                if (crossNow()) {
                    blink.request(minGap = 0f)
                    val crossFace = if (mind.owner == "Angry" || ExpressionDirector.normalize(expr.to) == "Angry") "Angry" else "pouty"
                    setReaction(crossFace, 1.2f, cause = "poked while cross")
                } else {
                    accent(FaceAccent.Glee, 1.2f, force = true)
                }
            }
            meaning == Meaning.Annoyed -> {
                if (reactionQueue?.first == "Happy") reactionQueue = null
                director.gesture(MotionDirector.Gesture.TurnAway)
                setReaction("pouty", 1.6f, cause = "poked too much")
                accent(FaceAccent.Annoyed, 1.8f, force = true)
                mood.nudge(-0.12f, 0.05f)
                forgiveIn = 2.6f + rng.nextFloat() * 1.5f
            }
            pokeCount >= 2 -> {
                setReaction("Surprised", 0.9f, cause = "poke")
                blink.request(minGap = 0.2f)
            }
            // First poke: not the same reaction every time.
            else -> when (r) {
                Response.Startle -> {
                    accent(FaceAccent.Startled, 0.45f, force = true)
                    if (crossNow()) {
                        val crossFace = if (mind.owner == "Angry" || ExpressionDirector.normalize(expr.to) == "Angry") "Angry" else "pouty"
                        setReaction(crossFace, 1.2f, cause = "poked while cross")
                    } else if (rng.nextFloat() < 0.4f) {
                        reactionQueue = "Happy" to 1.2f
                        reactionQueueIn = 0.6f
                    }
                }
                Response.Freeze -> perform(Response.Freeze, Stimulus("poke", 0f, 0f, 0.9f))
                Response.Smile -> {
                    blink.request(minGap = 0f)
                    if (crossNow()) {
                        director.gesture(MotionDirector.Gesture.TurnAway)
                        val crossFace = if (mind.owner == "Angry" || ExpressionDirector.normalize(expr.to) == "Angry") "Angry" else "pouty"
                        setReaction(crossFace, 1.2f, cause = "poked while cross")
                    } else {
                        accent(FaceAccent.Glee, 1.2f, force = true)
                    }
                }
                Response.CuriousTilt -> { director.gesture(MotionDirector.Gesture.Tilt); accent(FaceAccent.Curious, 1.2f) }
                Response.Acknowledge -> director.gesture(MotionDirector.Gesture.Nod)
                // A startling poke: the body pop is already playing; the eyes widen only sometimes.
                else -> if (meaning == Meaning.Startled && rng.nextFloat() < 0.5f) {
                    accent(FaceAccent.Startled, 0.4f, force = true)
                } else blink.request(minGap = 0f)
            }
        }
    }

    /**
     * App / UI happenings. Returns true when handled (some apply while busy too).
     * Every avatar on screen hears the same event, so a surprise lands on all of
     * them at once.
     */
    // ------------------------------------------------------ engine21: UI hooks
    private val hookLast = HashMap<String, Float>()
    private val hookUses = HashMap<String, Int>()
    private val variantLast = HashMap<String, Int>()

    /**
     * Engine21: should this UI hook react? Rare-ish and never repetitive: a per-hook cooldown,
     * base chance [p] scaled by the context (busy = half) and decayed each time it fired in
     * the last few minutes; the UiReaction behaviour must be allowed in the context.
     */
    /** Harness: every UI hook notices (demo clips only). */
    internal var debugHooksAlways = false

    private fun hookGo(key: String, cooldown: Float, p: Float): Boolean {
        if (!ContextRules.allowed(ctx, ContextRules.Behaviour.UiReaction)) return false
        if (debugHooksAlways) { log("hook $key fires (demo)"); return true }
        val last = hookLast[key]
        if (last != null && time - last < cooldown) return false
        val uses = if (last != null && time - last > 300f) 0 else hookUses[key] ?: 0
        val busy = ctx == ContextRules.Ctx.Thinking || ctx == ContextRules.Ctx.Streaming
        val chance = p * (if (busy) 0.5f else 1f) * (1f / (1f + 0.6f * uses))
        if (rng.nextFloat() >= chance) { log("hook $key: not noticed (p=${"%.2f".format(chance)})"); return false }
        hookLast[key] = time; hookUses[key] = uses + 1
        log("hook $key fires (use ${uses + 1})")
        return true
    }

    /** A variant index in 0 until [n], never the same as last time. */
    private fun pickVariant(key: String, n: Int): Int {
        val prev = variantLast[key] ?: -1
        var v = rng.nextInt(n)
        if (v == prev) v = (v + 1 + rng.nextInt(n - 1)) % n
        variantLast[key] = v
        return v
    }

    /**
     * Engine21: reads the expanded activity timeline — waits for the expansion, eyes to the
     * top, a slow pursuit to the bottom (speed by height), a blink, back to rest.
     */
    private fun timelinePan(at: Offset?) {
        fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        val wait = rr(0.35f, 0.55f)
        var down = rr(1.3f, 1.8f)
        readingUntil = time + wait + 0.45f + down + 0.3f
        tell("timeline-pan", wait to "eyes to the top", wait + 0.45f to "pan down", acts = listOf(
            {
                val r = stage?.target(AvatarStage.Target.ActivityPill)
                val top = if (r != null) Offset(r.center.x, r.top + minOf(r.height * 0.1f, 40f)) else at
                if (top != null) { val (gx, gy) = lookAt(top); gaze.focus(gx, gy, 0.5f, priority = 2) }
            },
            {
                val r = stage?.target(AvatarStage.Target.ActivityPill)
                val bottom = if (r != null) Offset(r.center.x, r.bottom - minOf(r.height * 0.1f, 40f)) else at?.let { Offset(it.x, it.y + 400f) }
                if (r != null) down = (0.9f + r.height / 900f).coerceIn(1.1f, 2.2f) * rr(0.9f, 1.1f)
                readingUntil = time + down + 0.3f
                if (bottom != null) {
                    val (gx, gy) = lookAt(bottom)
                    gaze.focus(gx, gy, down + 0.5f, priority = 2, travel = down)
                    tell("timeline-pan-end", down + 0.25f to "blink", down + 0.6f to "back", acts = listOf(
                        { blink.request(minGap = 0.2f) },
                        { gaze.focus(0f, 0.03f, 1.2f, priority = 2, travel = 0.5f) },
                    ))
                }
            },
        ))
    }

    private fun onUiEvent(e: AvatarStage.Event, calm: Boolean): Boolean {
        if (e.source != stageId) aliveness.onEvent(e)
        fun glance(at: Offset?, hold: Float, prio: Int = 2) {
            val p = at ?: return
            val (gx, gy) = lookAt(p)
            gaze.focus(gx, gy, hold, priority = prio)
        }
        when (e.kind) {
            AvatarStage.EventKind.Error -> {
                mood.nudge(-0.4f, 0.15f)
                // Engine17.1: the worried-neutral face is retired — a calm, restrained concentration.
                setReaction("angered or concentrated", 2.4f, ExpressionDirector.Style.Soft, cause = "error event: concentrates on it")
                blink.request(minGap = 0.3f)
            }
            AvatarStage.EventKind.Deleted -> {
                mood.nudge(-0.1f, 0.1f)
                // Watch it go, then a small reaction.
                glance(e.at, 0.9f, prio = 3)
                if (rng.nextFloat() < 0.5f) accent(FaceAccent.Startled, 0.4f, force = true)
                reactionQueue = (if (rng.nextFloat() < 0.6f) "Sad" else "Surprised") to 0.9f
                reactionQueueIn = 0.45f
            }
            AvatarStage.EventKind.Copied -> {
                mood.nudge(0.12f, 0.05f)
                // Engine21: varied — pleased, a proud little nod, or a glance at what was copied.
                if (calm && hookGo("copied", 10f, 0.55f)) when (pickVariant("copied", 3)) {
                    0 -> { accent(FaceAccent.Glee, 1.1f); if (rng.nextFloat() < 0.4f) director.gesture(MotionDirector.Gesture.Nod) }
                    1 -> tell("copied-proud", 0f to "nod", 0.3f to "smile", acts = listOf(
                        { director.gesture(MotionDirector.Gesture.Nod) }, { accent(FaceAccent.Smug, 1.0f) }))
                    else -> { glance(e.at, 0.8f); blink.request(minGap = 0.3f) }
                }
            }
            AvatarStage.EventKind.Regenerated -> {
                // Engine21: "another go?" — puzzled, a curious tilt, or a determined blink.
                if (hookGo("regen", 6f, 0.85f)) when (pickVariant("regen", 3)) {
                    0 -> { accent(FaceAccent.Puzzled, 0.9f, force = true); blink.request(minGap = 0.3f) }
                    1 -> { accent(FaceAccent.Curious, 1.0f, force = true); director.gesture(MotionDirector.Gesture.Tilt) }
                    else -> { blink.request(minGap = 0.2f); accent(FaceAccent.Focused, 1.1f, force = true) }
                }
            }
            AvatarStage.EventKind.AttachmentAdded -> if (social == null && hookGo("attach", 20f, 0.6f)) {
                // Something new arrived in the composer: look at it, then react.
                val at = targetCenter(AvatarStage.Target.Composer) ?: windowBottom()
                glance(at, 1.1f + rng.nextFloat() * 0.5f)
                val many = e.extra >= 3
                when (if (many) 0 else pickVariant("attach", 3)) {
                    0 -> accent(FaceAccent.Startled, if (many) 0.35f else 0.25f, force = true).also {
                        reactionQueue = "Surprised" to 0.6f; reactionQueueIn = 0.5f
                    }
                    1 -> tell("attach-curious", 0.45f to "curious", acts = listOf({ accent(FaceAccent.Curious, 1.2f) }))
                    else -> tell("attach-ack", 0.5f to "nod", acts = listOf({ director.gesture(MotionDirector.Gesture.Nod) }))
                }
            }
            AvatarStage.EventKind.ModelPickerOpened -> if (social == null && hookGo("model", 45f, 0.45f)) {
                // "Who am I going to be?" — glance at the sheet, puzzled or curious.
                glance(windowBottom(), 1.2f)
                tell("model-picker", 0.4f to if (rng.nextBoolean()) "puzzled" else "curious", acts = listOf({
                    accent(if (rng.nextBoolean()) FaceAccent.Puzzled else FaceAccent.Curious, 1.2f)
                    if (rng.nextFloat() < 0.4f) director.gesture(MotionDirector.Gesture.Tilt)
                }))
            }
            AvatarStage.EventKind.EditorOpened -> if (social == null && hookGo("editor", 20f, 0.6f)) {
                // Reads along as the old message comes back into the composer.
                val at = targetCenter(AvatarStage.Target.Composer) ?: windowBottom()
                glance(at, 1.4f + rng.nextFloat() * 0.6f)
                if (rng.nextFloat() < 0.6f) tell("editor-read", 0.5f to "focused", acts = listOf({ accent(FaceAccent.Focused, 1.3f) }))
            }
            AvatarStage.EventKind.SettingToggled -> if (calm && social == null && hookGo("toggle", 15f, 0.4f)) {
                if (e.direction > 0 && rng.nextFloat() < 0.5f) accent(FaceAccent.Glee, 0.8f)
                else blink.request(minGap = 0.3f)
                if (rng.nextFloat() < 0.3f) director.gesture(MotionDirector.Gesture.Nod)
            }
            AvatarStage.EventKind.AppResumed -> {
                // Back from the background: a sleepy squint that opens up, blink-blink.
                mood.nudge(0f, 0.15f)
                if (rng.nextFloat() < 0.7f) {
                    accent(FaceAccent.WakeSquint, 0.9f + rng.nextFloat() * 0.5f, force = true)
                    perkIn = 0.9f
                }
                if (drowsy) wake()
            }
            AvatarStage.EventKind.KeyboardOpened -> {
                mood.nudge(0f, 0.08f)
                if (calm && social == null && rng.nextFloat() < 0.45f) {
                    glance(windowBottom(), 0.8f + rng.nextFloat() * 0.6f)
                    if (rng.nextFloat() < 0.3f) accent(FaceAccent.Curious, 1.0f)
                }
            }
            AvatarStage.EventKind.KeyboardClosed -> {
                if (followLeft > 0f) { followLeft = 0f; gaze.releaseFocus() }
                if (rng.nextFloat() < 0.3f) blink.request()
            }
            AvatarStage.EventKind.Fling -> {
                // A really fast fling, rarely: a little dizzy.
                if (calm && flingCooldown <= 0f && e.magnitude > 18f && rng.nextFloat() < 0.3f) {
                    flingCooldown = 90f
                    accent(FaceAccent.Dizzy, 1.3f, force = true)
                    director.gesture(MotionDirector.Gesture.Tilt)
                    reactionQueue = "confused" to 0.8f
                    reactionQueueIn = 1.1f
                }
            }
            AvatarStage.EventKind.MenuOpened -> {
                if (calm && social == null && rng.nextFloat() < 0.5f) {
                    glance(e.at, 1.0f)
                    if (rng.nextFloat() < 0.35f) accent(FaceAccent.Curious, 1.0f)
                }
            }
            AvatarStage.EventKind.PillExpanded -> {
                // Engine21: when it notices, it reads the opened timeline — eyes to its top, then a
                // slow pan down to the bottom; otherwise a peek at what opened.
                if (social == null && hookGo("pill", 8f, 0.7f)) {
                    if (hookGo("timeline-pan", 35f, if (calm) 0.6f else 0.4f)) timelinePan(e.at)
                    else {
                        glance(e.at ?: targetCenter(AvatarStage.Target.ActivityPill), 1.3f + rng.nextFloat() * 0.6f)
                        if (calm) director.gesture(MotionDirector.Gesture.Attend, 1.4f)
                    }
                }
            }
            AvatarStage.EventKind.AvatarAccent -> {
                // Now and then mirror a neighbour's face, a beat later.
                if (e.source != stageId && calm && social == null && mirror == null && rng.nextFloat() < 0.12f) {
                    val a = FaceAccent.entries.getOrNull(e.extra)
                    if (a != null && a != FaceAccent.Dizzy && a != FaceAccent.None) {
                        glance(e.at, 0.8f)
                        mirror = a to 1.0f
                        mirrorIn = 0.35f + rng.nextFloat() * 0.3f
                    }
                }
            }
            else -> return false
        }
        return true
    }

    private fun wake() = because("wakes: activity returned", MindRules.P_EVENT) { wake0() }
    private fun wake0() {
        if (restPhase > 0) {
            log("wake from ${if (restPhase == 2) "doze" else "resting eyes"} cause=activity returned")
            if (restPhase == 2 && reaction == "Sleeping") { reaction = null; log("expr Sleeping→Neutral cause=woken gently") }
            restPhase = 0; restLeft = 0f
            if (!briskWake && rng.nextFloat() < 0.35f) wakeShake(3.5f, cause = "wake")
            sinceStimulus = -6f // just woke up: give it a moment before any self-started beat
        }
        drowsy = false
        drowsyAfter = 100f + rng.nextFloat() * 60f
        blink.request(double = true, minGap = 0.2f)
        accent(FaceAccent.WakeSquint, 0.8f, force = true)
    }

    // ------------------------------------------------- engine16: idle episodes + channels

    internal val episodes = EpisodePlanner(rng)
    /** 0 = awake, 1 = resting eyes, 2 = dozing. */
    internal var restPhase = 0; private set
    private var restLeft = 0f
    private var restLid = 1f
    private var restK = 0f
    private var yawnU = 1f; private var yawnDur = 2.8f
    private var sbT = -1f; private val SB_DUR = 1.35f
    private var wsT = -1f; private var wsAmp = 4f; private var wsHz = 4.6f; private var wsCycles = 2.5f
    private var busySpinIn = 25f

    private fun yawnK(): Float = if (yawnU >= 1f) 0f else when {
        yawnU < 0.35f -> MotionVocab.easeInOutSine(yawnU / 0.35f)
        yawnU < 0.65f -> 1f
        else -> 1f - MotionVocab.easeInOutSine((yawnU - 0.65f) / 0.35f)
    }

    private fun slowBlinkLid(): Float {
        if (sbT < 0f) return 1f
        val u = sbT / SB_DUR
        val k = when {
            u < 0.32f -> MotionVocab.easeInOutSine(u / 0.32f)
            u < 0.55f -> 1f
            else -> 1f - MotionVocab.easeInOutSine((u - 0.55f) / 0.45f)
        }
        return 1f - 0.93f * k
    }

    /**
     * Engine17.1: THE head shake (wake shake, come-to-senses, yawn, spin shake-off) — a
     * deliberate "no-no" shake, not a vibration: 2–3 slow, larger swings (~1.7–2.1 Hz,
     * 8–11°) with a soft attack and decaying envelope, eyes squeezed shut throughout;
     * then the eyes open with the head's small settle and a blink. [amp] is the legacy
     * strength (3.5…6.5), mapped onto the new swing size.
     */
    private fun wakeShake(amp: Float, cause: String = "wake") {
        wsT = 0f
        wsAmp = (7.5f + (amp - 3.5f) * 0.9f).coerceIn(7.5f, 11f) * (0.92f + 0.16f * rng.nextFloat())
        wsHz = 1.7f + 0.4f * rng.nextFloat()
        wsCycles = 2f + rng.nextFloat()
        wsDir = if (rng.nextBoolean()) 1f else -1f
        log("head-shake cause=$cause swings=${"%.1f".format(wsCycles)} amp=${"%.1f".format(wsAmp)}°")
    }
    private var wsDir = 1f
    /** Eye closure during a head shake (1 = open). */
    private var shakeLid = 1f

    private fun lean(roll: Float, yaw: Float) { peekRollT = roll; peekYawT = yaw }

    /** Runs every frame (idle or not): yawn / slow blink / wake shake / rest / busy-spin timers. */
    private fun channelsUpdate(dt: Float) {
        if (yawnU < 1f) yawnU = (yawnU + dt / yawnDur).coerceAtMost(1f)
        // Engine18 lid lane: a slow blink or a shut-eyed head shake starts only once the
        // face has settled (never closes the eyes over a running morph).
        if (sbT >= 0f && !(sbT == 0f && expr.morphing)) { sbT += dt; if (sbT >= SB_DUR) sbT = -1f }
        if (wsT >= 0f && !(wsT == 0f && expr.morphing)) {
            val dur = wsCycles / wsHz
            val squeezeIn = 0.14f
            if (wsT < squeezeIn + dur) {
                // Eyes squeeze shut first, then the head swings.
                val st = wsT - squeezeIn
                if (st > 0f) {
                    val u = st / dur
                    val attack = (st / 0.12f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                    val env = attack * (1f - u).let { it * it * (1f + 0.6f * u) }.coerceIn(0f, 1f)
                    shakeYaw += wsDir * wsAmp * env * sin(2f * PI.toFloat() * wsHz * st)
                }
                val lidT = 0.04f
                shakeLid += (lidT - shakeLid) * (1f - exp(-dt / 0.05f))
                wsT += dt
            } else {
                wsT = -1f
                blink.request(minGap = 0.25f) // eyes open with a settle, then a blink
            }
        } else if (shakeLid < 1f) {
            // Open gently (the head is still settling).
            shakeLid += (1f - shakeLid) * (1f - exp(-dt / 0.12f))
            if (shakeLid > 0.995f) shakeLid = 1f
        }
        // Resting eyes / dozing.
        val lidT = when (restPhase) { 1 -> 0.06f; 2 -> 0f; else -> 1f }
        restLid += (lidT - restLid) * (1f - exp(-dt / if (lidT < restLid) 1.1f else if (time < briskUntil) 0.18f else 0.45f))
        val kT = if (restPhase > 0) 1f else 0f
        restK += (kT - restK) * (1f - exp(-dt / if (time < briskUntil) 0.25f else 1.6f))
        if (restPhase > 0) {
            restLeft -= dt
            if (restPhase == 2 && reaction == "Sleeping") reactionLeft = 5f
            if (restLeft <= 0f) {
                if (restPhase == 1 && (forceDoze || (quietSec > 150f && rng.nextFloat() < 0.5f))) {
                    forceDoze = false
                    restPhase = 2; restLeft = 35f + rng.nextFloat() * 45f
                    gaze.focus(0f, 0.18f, restLeft + 1f, priority = 3, travel = 1.5f) // asleep: the head stays put
                    setReaction("Sleeping", 6f, ExpressionDirector.Style.Gradual, cause = "dozed off while resting eyes (untouched ${quietSec.toInt()} s)")
                } else {
                    // Opens again on its own, slowly: blink-blink, sometimes a yawn after a doze.
                    val was = restPhase
                    restPhase = 0
                    if (was == 2 && reaction == "Sleeping") { reaction = null; log("expr Sleeping→Neutral cause=wakes up from a doze on its own") }
                    log("rest ends (opens eyes on its own)")
                    blink.request(double = true, minGap = 0f)
                    because("rest ends: opens eyes on its own", MindRules.P_LIFE) { accent(FaceAccent.WakeSquint, 0.9f, force = true) }
                    if (was == 2 && rng.nextFloat() < 0.4f && episodes.ready(Episode.Yawn)) { episodes.note(Episode.Yawn); tell("after-doze", 1.2f to "yawn", acts = listOf({ playEpisode(Episode.Yawn) })) }
                }
            }
        }
        // Engine16: the only self-started spins — rarely, while answering in a really good mood.
        if (director.lifecycle == MarkLifecycle.Streaming) {
            busySpinIn -= dt
            if (busySpinIn <= 0f) {
                busySpinIn = 20f + rng.nextFloat() * 20f
                if (temper.playful(personality) && mood.valence > 0.3f && lastSpinAgo > 90f && rng.nextFloat() < 0.15f)
                    trySpin("lively while answering")
            }
        }
    }

    private fun episodeUpdate(dt: Float) {
        val free = director.lifecycle == MarkLifecycle.Idle && reaction == null && social == null && train.isEmpty() &&
            freezeLeft <= 0f && takeT < 0f && scan.isEmpty() && driftU >= 1f && stretchU >= 1f && yawnU >= 1f &&
            spT < 0f && recoverT <= 0f && sinceStimulus > 8f && lastSpinAgo > 8f && restPhase == 0 && followLeft <= 0f && !gaze.focused && peekLeft <= 0f
        val ctx = EpisodePlanner.Ctx(temper, quietSec, drowsy, mood.valence, targetCenter(AvatarStage.Target.ActivityPill) != null,
            recently("nice", 120f), personality.playfulness)
        val room = mind.budgetOk(MindRules.P_IDLE, false, mood.arousal)
        episodes.update(dt, ctx, free && room)?.let { mind.noteAct(Lane.Body, "episode $it", MindRules.P_IDLE, it.why); playEpisode(it) }
    }

    internal fun debugEpisode(e: Episode) { episodes.note(e); playEpisode(e) }
    private var forceDoze = false
    /** Harness: as if untouched for a long while — rest the eyes now ([doze] = then doze off). */
    internal fun debugRest(doze: Boolean) {
        drowsy = true; quietSec = max(quietSec, 200f); forceDoze = doze
        episodes.note(Episode.RestEyes); playEpisode(Episode.RestEyes); restLeft = 4.5f
    }

    private fun playEpisode(e: Episode) = because("episode $e: ${e.why}", MindRules.P_IDLE) { playEpisode0(e) }
    private fun playEpisode0(e: Episode) {
        fun rr(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        val needs = when (e) {
            Episode.Yawn -> ContextRules.Behaviour.Yawn
            Episode.RestEyes -> ContextRules.Behaviour.EyeRest
            Episode.SleepyDrift -> ContextRules.Behaviour.Sleepy
            Episode.Stretch -> ContextRules.Behaviour.LongEyesClosed
            else -> ContextRules.Behaviour.IdleEpisode
        }
        if (!ContextRules.allowed(ctx, needs)) { log("episode $e skipped: $needs not in context $ctx"); return }
        val s = if (rng.nextBoolean()) 1f else -1f
        log("episode $e (${e.tier}) why=${e.why}")
        when (e) {
            Episode.CuriousLR -> {
                val a = rr(1.0f, 1.3f); val b = a + rr(1.0f, 1.3f); val curious = rng.nextFloat() < 0.35f
                tell("curious-lr", 0f to "look ${if (s > 0) "right" else "left"}", a to "look other way", b to "back to rest", b + 0.1f to "maybe blink", acts = listOf(
                    { gaze.focus(s * rr(0.5f, 0.65f), rr(0f, 0.1f), a + 0.1f, priority = 2) },
                    { gaze.focus(-s * rr(0.5f, 0.65f), rr(0f, 0.1f), b - a + 0.1f, priority = 2); if (curious) accent(FaceAccent.Curious, 1.1f) },
                    { gaze.focus(rr(-0.05f, 0.05f), rr(0f, 0.05f), rr(1.5f, 2.5f), priority = 2, travel = 0.5f) },
                    { if (rng.nextFloat() < 0.5f) blink.request(minGap = 0.3f) },
                ))
            }
            Episode.CheckOnYou -> tell("check-on-you", 0f to "look at you", 0.7f to "react", acts = listOf(
                { gaze.focus(rr(-0.04f, 0.04f), rr(0.08f, 0.16f), rr(2f, 3f), priority = 2, travel = 0.6f) },
                { if (mood.valence > 0.25f) accent(FaceAccent.Glee, rr(1f, 1.5f)) else blink.request(minGap = 0.3f) },
            ))
            Episode.TiltChain -> {
                val r1 = s * rr(5f, 7f); val r2 = -s * rr(4f, 6f)
                tell("tilt-chain", 0f to "eyes to it", 0.25f to "tilt", 0.45f to "curious", 1.6f to "tilt other way", 2.8f to "straighten", acts = listOf(
                    { gaze.focus(s * rr(0.25f, 0.35f), rr(0f, 0.12f), 3.6f, priority = 2) },
                    { lean(r1, s * 2f) },
                    { if (rng.nextFloat() < 0.5f) accent(FaceAccent.Curious, 2f) },
                    { lean(r2, -s * 1.5f) },
                    { lean(0f, 0f) },
                ))
            }
            Episode.SkepticalLR -> {
                val a = rr(1.6f, 1.9f)
                tell("skeptical-lr", 0f to "look left", 0.6f to "brow up (left)", a to "look right", a + 0.6f to "other brow up (right)",
                    a + 1.9f to "back to rest", a + 2.1f to "blink", acts = listOf(
                    { gaze.focus(-rr(0.5f, 0.6f), rr(0f, 0.06f), a, priority = 2) },
                    { setReaction("confused", a - 0.75f, cause = "skeptical: sizing something up on the left") },
                    { gaze.focus(rr(0.5f, 0.6f), rr(0f, 0.06f), 1.9f, priority = 2) },
                    { setReaction("confused", 1.1f, cause = "skeptical: and the right doesn't convince either") },
                    { gaze.focus(rr(-0.04f, 0.04f), rr(0f, 0.05f), 2f, priority = 2, travel = 0.5f) },
                    { blink.request(minGap = 0.2f) },
                ))
            }
            Episode.HmmLean -> {
                val nod = rng.nextFloat() < 0.5f
                tell("hmm", 0f to "look down-side", 0.3f to "lean", 0.5f to "puzzled", 2.3f to "straighten", 2.45f to if (nod) "got it: nod" else "blink", acts = listOf(
                    { gaze.focus(s * rr(0.28f, 0.4f), rr(0.18f, 0.3f), 2.4f, priority = 2) },
                    { lean(s * rr(2.5f, 3.5f), s * rr(3f, 4.5f)) },
                    { accent(FaceAccent.Puzzled, 1.7f) },
                    { lean(0f, 0f) },
                    { if (nod) director.gesture(MotionDirector.Gesture.Nod) else blink.request(minGap = 0.2f) },
                ))
            }
            Episode.DoubleTake -> perform(Response.DoubleTake, Stimulus("idle-flicker", s * rr(0.6f, 0.75f), rr(0f, 0.15f), 0.6f))
            Episode.Peek -> playMoment(Moment.Peek)
            Episode.Scan -> playMoment(Moment.Scan)
            Episode.NoticePill -> playMoment(Moment.NoticePill)
            Episode.Stretch -> playMoment(Moment.Stretch)
            Episode.SleepyDrift -> playMoment(Moment.SleepyDrift)
            Episode.Smug -> playMoment(Moment.SmugNod)
            Episode.FollowSomething -> {
                val tilt = rng.nextFloat() < 0.5f
                tell("follow", 0f to "spots it", 0.6f to "tracks it across", 3.2f to "lost it: blink", 3.4f to if (tilt) "tilt" else "-", 3.6f to "back to rest", acts = listOf(
                    { gaze.focus(s * rr(0.65f, 0.75f), rr(-0.08f, 0.05f), 0.65f, priority = 2) },
                    { gaze.focus(-s * rr(0.55f, 0.65f), rr(0.05f, 0.15f), 0.6f, priority = 2, travel = rr(2.2f, 2.6f)) },
                    { blink.request(minGap = 0f) },
                    { if (tilt) director.gesture(MotionDirector.Gesture.Tilt) },
                    { gaze.focus(0f, 0.03f, 1.5f, priority = 2, travel = 0.6f) },
                ))
            }
            Episode.LookUpRemember -> tell("remember", 0f to "look up-left", 0.45f to "puzzled", 1.8f to "ah: back", 1.95f to "nod", acts = listOf(
                { gaze.focus(-rr(0.35f, 0.45f), -rr(0.22f, 0.3f), 1.8f, priority = 2) },
                { accent(FaceAccent.Puzzled, 1.3f) },
                { gaze.focus(0f, 0.05f, 1.6f, priority = 2) },
                { director.gesture(MotionDirector.Gesture.Nod); if (mood.valence > 0.05f) accent(FaceAccent.Glee, 0.9f, force = true) },
            ))
            Episode.ContentSlowBlink -> tell("content", 0f to "slow blink", 0.25f to "soft smile", acts = listOf(
                { sbT = 0f },
                { accent(FaceAccent.Glee, 1.6f) },
            ))
            Episode.ConcernGlance -> {
                val nod = rng.nextFloat() < 0.4f
                tell("concern", 0f to "glance down-side", 0.55f to "concentrated", 2f to "blink", 2.1f to "back", 2.5f to if (nod) "reassured nod" else "-", acts = listOf(
                    { gaze.focus(s * rr(0.4f, 0.5f), rr(0.35f, 0.45f), 2f, priority = 2) },
                    { setReaction("angered or concentrated", 1.5f, ExpressionDirector.Style.Soft, cause = "concern glance: concentrated") },
                    { blink.request(minGap = 0f) },
                    { gaze.focus(0f, 0.03f, 1.6f, priority = 2, travel = 0.5f) },
                    { if (nod) director.gesture(MotionDirector.Gesture.Nod) },
                ))
            }
            Episode.ComeToSenses -> tell("come-to-senses", 0f to "blink-blink", 0.3f to "shake", 0.35f to "squint", 1.3f to "present", acts = listOf(
                { blink.request(double = true, minGap = 0f) },
                { wakeShake(rr(4f, 5.5f), cause = "come to senses") },
                { accent(FaceAccent.WakeSquint, 0.8f, force = true) },
                { gaze.focus(0f, 0.02f, 1.4f, priority = 2) },
            ))
            Episode.Yawn -> {
                yawnU = 0f; yawnDur = rr(2.6f, 3.2f)
                val shake = rng.nextFloat() < 0.5f
                val end = yawnDur
                tell("yawn", 0.15f * end to "eyes squeeze", end * 0.98f to if (shake) "shake to wake up" else "eyes open slowly", end + 0.2f to if (shake) "squint" else "-", acts = listOf(
                    { setReaction("Eyes closed", 0.8f * end, ExpressionDirector.Style.Gradual, cause = "yawn (tired)") },
                    { if (shake) wakeShake(rr(4.5f, 6f), cause = "yawn") },
                    { if (shake) { blink.request(double = true, minGap = 0f); accent(FaceAccent.WakeSquint, 0.8f, force = true) } },
                ))
            }
            Episode.RestEyes -> {
                restPhase = 1; restLeft = rr(5f, 10f)
                gaze.focus(0f, 0.15f, restLeft + 1f, priority = 3, travel = 1.2f)
                log("rest eyes for %.1fs cause=untouched ${quietSec.toInt()} s, drowsy".format(restLeft))
            }
            Episode.LookAround360 -> tell("survey", 0f to "far left", 1.1f to "middle", 2.2f to "far right", 3.5f to "tilt", 4f to "back", 4.1f to "blink", acts = listOf(
                { gaze.focus(-0.85f, 0.05f, 1.1f, priority = 2) },
                { gaze.focus(-0.25f, 0f, 0.6f, priority = 2, travel = 0.8f) },
                { gaze.focus(0.85f, 0.08f, 1.3f, priority = 2, travel = 1.1f) },
                { director.gesture(MotionDirector.Gesture.Tilt) },
                { gaze.focus(0f, 0.03f, 2f, priority = 2, travel = 0.7f) },
                { blink.request(minGap = 0.2f) },
            ))
            Episode.Sneeze -> tell("sneeze", 0f to "scrunch", 0.45f to "achoo", 0.55f to "blink-blink", acts = listOf(
                { accent(FaceAccent.WakeSquint, 0.6f, force = true) },
                { director.gesture(MotionDirector.Gesture.Recoil) },
                { blink.request(double = true, minGap = 0f) },
            ))
        }
    }

    private companion object {
        const val SETTLE_OUT_SEC = 0.8f
        const val SETTLE_IN_SEC = 0.6f
        const val CARET_CHARS_PER_LINE = 34
        val QUIET_HINTS = AvatarMotionHints()
        val REST_FRAME = MotionFrame()
    }

    private fun mapClipExpression(e: String): String? = when (e.lowercase()) {
        // Engine19: a clip gap or a "neutral" key means "no opinion" (the busy face carries on).
        "neutral" -> null
        "happy" -> "Happy"
        "surprised" -> "Surprised"
        "sad" -> "Sad"
        "sleepy" -> "Sleeping"
        "squinted", "calm" -> "Bored"
        "confused" -> "confused"
        // Never the Grok brand glance.
        else -> null
    }

    internal suspend fun run(hints: State<AvatarMotionHints>) {
        var last = withFrameNanos { it }
        while (true) {
            if (isSettled) {
                // Static and at rest: no frames at all until it should move again.
                snapshotFlow { animate }.first { it }
                last = withFrameNanos { it }
            }
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000.0).toFloat()
                last = now
                tick(dt, hints.value, System.currentTimeMillis())
            }
        }
    }
}

@Composable
fun rememberAvatarMotionState(
    isLoading: Boolean = false,
    isTyping: Boolean = false,
    hintsOverride: AvatarMotionHints? = null,
    /** False for Create Avatar calm tiles — no loops at all. */
    enabled: Boolean = true,
    /** False = static (eases to rest, then stops); see [AvatarMotionState.animate]. */
    animate: Boolean = true,
): AvatarMotionState {
    val local = LocalAvatarMotionHints.current
    val hints = (hintsOverride ?: local).let { base ->
        base.copy(
            isGenerating = base.isGenerating || isLoading,
            isTyping = base.isTyping || isTyping,
            inputFocused = base.inputFocused || isTyping,
        )
    }
    // Hints flow through a State so a change never restarts (and never cancels
    // mid-blink / mid-glance) the motion loop — that restart was one of the hard cuts.
    val hintsState = rememberUpdatedState(hints)
    val state = remember { AvatarMotionState().also { if (!animate) it.startSettled() } }
    SideEffect { state.animate = animate }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        state.run(hintsState)
    }
    return state
}

@Composable
fun ProvideAvatarMotionHints(
    hints: AvatarMotionHints,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAvatarMotionHints provides hints, content = content)
}
