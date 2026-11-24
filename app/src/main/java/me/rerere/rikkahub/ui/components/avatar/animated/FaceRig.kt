package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Parametric eye shaping layered on top of the authored expressions (see
 * docs/avatar-aliveness.md). Each eye gets a small set of continuous parameters —
 * scale, offset, rotation and upper / lower lids with a slant — that are smoothed by
 * springs, so any change (an accent, the mood, a saccade, a blink) morphs instead
 * of switching. Grok capsules take the parameters that keep them capsules (scale,
 * offset, rotation, openness); Generical outlines take the full lid treatment.
 */
data class EyeShape(
    val sx: Float = 1f,
    val sy: Float = 1f,
    /** Offset in head radii (x right, y down). */
    val dx: Float = 0f,
    val dy: Float = 0f,
    /** Rotation in degrees, positive = clockwise on screen. */
    val rot: Float = 0f,
    /** Openness of the top / bottom half (1 = as authored, 0 = shut to the midline). */
    val upper: Float = 1f,
    val lower: Float = 1f,
    /** Upper-lid slant: + = inner corner lower (cross), - = inner corner higher (worried). */
    val slant: Float = 0f,
) {
    fun isIdentity(): Boolean =
        abs(sx - 1f) < 1e-3f && abs(sy - 1f) < 1e-3f && abs(dx) < 1e-4f && abs(dy) < 1e-4f &&
            abs(rot) < 0.05f && abs(upper - 1f) < 1e-3f && abs(lower - 1f) < 1e-3f && abs(slant) < 1e-3f

    companion object {
        val ID = EyeShape()
        const val COUNT = 8
    }

    fun toArray(out: FloatArray, at: Int) {
        out[at] = sx; out[at + 1] = sy; out[at + 2] = dx; out[at + 3] = dy
        out[at + 4] = rot; out[at + 5] = upper; out[at + 6] = lower; out[at + 7] = slant
    }
}

fun eyeShapeOf(a: FloatArray, at: Int) = EyeShape(a[at], a[at + 1], a[at + 2], a[at + 3], a[at + 4], a[at + 5], a[at + 6], a[at + 7])

data class FaceParams(val left: EyeShape = EyeShape.ID, val right: EyeShape = EyeShape.ID) {
    fun eye(side: Int) = if (side == 0) left else right
    companion object { val ID = FaceParams() }
}

/** Named parametric looks. Values are offsets from identity (see [FaceRig.accentTarget]). */
enum class FaceAccent {
    None, Curious, Annoyed, Shy, Startled, Dizzy, Smug, Focused, Drowsy, Worried, Glee, WakeSquint, Sad, Puzzled,
}

/** Two-axis mood (valence −1…1, arousal 0…1) with slow decay toward a baseline. */
class MoodEngine(private val rng: Random) {
    var valence = 0.1f; private set
    var arousal = 0.5f; private set
    /** Baseline arousal: lower late at night / early morning. Set from the clock. */
    var arousalBase = 0.5f

    /** Engine18: events push where the mood is heading; the mood itself drifts over minutes. */
    private var pushV = 0f
    private var pushA = 0f

    fun nudge(dv: Float, da: Float) {
        pushV = (pushV + dv).coerceIn(-1f, 1f)
        pushA = (pushA + da).coerceIn(-0.6f, 0.6f)
    }

    fun update(dt: Float, quietSec: Float) {
        // Pushes fade over ~4 min; valence / arousal follow their target with τ ≈ 90 / 120 s
        // and never faster than MAX_RATE per second — mood moves over minutes, not frames.
        val boredom = ((quietSec - 30f) / 120f).coerceIn(0f, 0.3f)
        pushV *= exp(-dt / 240f); pushA *= exp(-dt / 240f)
        val tv = (0.1f + pushV).coerceIn(-1f, 1f)
        val ta = (arousalBase - boredom + pushA).coerceIn(0f, 1f)
        fun step(x: Float, t: Float, tau: Float): Float {
            val d = (t - x) * (1f - exp(-dt / tau))
            val m = MAX_RATE * dt
            return x + d.coerceIn(-m, m)
        }
        valence = step(valence, tv, 90f)
        arousal = step(arousal, ta, 120f)
    }

    companion object {
        /** Fastest mood drift (units per second): a full swing takes minutes. */
        const val MAX_RATE = 0.006f
        /** Arousal baseline by local hour: alert in the day, sleepy after midnight. */
        fun baseForHour(h: Int): Float = when (h) {
            in 0..5 -> 0.28f
            in 6..8 -> 0.42f
            in 9..20 -> 0.55f
            21, 22 -> 0.45f
            else -> 0.36f
        }
    }
}

/**
 * Springs every eye parameter toward the sum of: the active accent, the mood bias,
 * saccade stretch, blink squash and micro jitter. Underdamped (ζ≈0.62) for a little
 * follow-through overshoot when an accent lands; offsets are critically damped.
 */
class FaceRig(private val rng: Random) {
    private val x = FloatArray(16)
    private val v = FloatArray(16)
    private val target = FloatArray(16)
    var accent: FaceAccent = FaceAccent.None; private set
    private var accentLeft = 0f
    private var accentSide = 1f
    private var t = 0f
    private var jitterIn = 0.5f
    private val jitter = FloatArray(4)

    init {
        EyeShape.ID.toArray(x, 0); EyeShape.ID.toArray(x, 8)
    }

    val active: Boolean get() = accent != FaceAccent.None

    fun play(a: FaceAccent, sec: Float) {
        accent = a
        accentLeft = sec
        accentSide = if (rng.nextBoolean()) 1f else -1f
    }

    fun clear() { accent = FaceAccent.None; accentLeft = 0f }

    /** The settled look of an accent (harness galleries). */
    internal fun staticFace(a: FaceAccent, time: Float = 0f): FaceParams =
        FaceParams(clampEye(accentTarget(a, 0, time)), clampEye(accentTarget(a, 1, time)))

    /**
     * [eyeVx]/[eyeVy]: gaze eye velocity (gaze units/s) — eyes stretch along a fast
     * saccade. [blink] 0…1 closure — eyes squash wider as the lids meet.
     * [valence]/[arousal] from [MoodEngine]; [calm] false while busy (mood bias softer).
     */
    fun update(dt: Float, eyeVx: Float, eyeVy: Float, blink: Float, valence: Float, arousal: Float, still: Boolean): FaceParams {
        t += dt
        if (accentLeft > 0f) {
            accentLeft -= dt
            if (accentLeft <= 0f) accent = FaceAccent.None
        }
        for (side in 0..1) {
            val e = accentTarget(accent, side, t)
            val o = side * 8
            e.toArray(target, o)
            // Mood: happy lifts the lower lids a touch, low mood slants them worried,
            // high arousal opens the eyes a little, low arousal lowers the upper lids.
            val pos = max(0f, valence); val neg = max(0f, -valence)
            target[o + 6] -= 0.1f * pos
            target[o + 7] -= 0.12f * neg
            target[o + 0] *= 1f + 0.05f * (arousal - 0.5f)
            target[o + 1] *= 1f + 0.07f * (arousal - 0.5f)
            target[o + 5] -= 0.22f * ((0.38f - arousal) / 0.38f).coerceIn(0f, 1f)
            // Saccade stretch (along the motion) — a few percent at most.
            val sp = kotlin.math.hypot(eyeVx, eyeVy)
            if (sp > 0.5f) {
                val k = (sp * 0.006f).coerceAtMost(0.07f)
                val fx = abs(eyeVx) / sp; val fy = abs(eyeVy) / sp
                target[o + 0] *= 1f + k * fx - k * 0.5f * fy
                target[o + 1] *= 1f + k * fy - k * 0.5f * fx
            }
            // Blink squash: wider and a hair lower as the lids meet.
            target[o + 0] *= 1f + 0.07f * blink
            target[o + 3] += 0.012f * blink
            // Micro jitter (tiny, only while still).
            if (still) {
                target[o + 2] += jitter[side * 2]
                target[o + 3] += jitter[side * 2 + 1]
            }
        }
        jitterIn -= dt
        if (jitterIn <= 0f) {
            jitterIn = 0.35f + rng.nextFloat() * 1.2f
            for (i in 0..3) jitter[i] = (rng.nextFloat() - 0.5f) * 0.006f
            jitter[2] = jitter[0] * 0.7f + jitter[2] * 0.3f // both eyes move mostly together
            jitter[3] = jitter[1] * 0.7f + jitter[3] * 0.3f
        }
        var rem = dt
        while (rem > 1e-6f) {
            val h = min(rem, 1f / 120f)
            for (i in 0 until 16) {
                val k = i % 8
                // Engine17: softer, less wobbly accent channels (was ω17 ζ0.62 — snappy pops).
                val w = if (k == 2 || k == 3) 14f else 11f
                val zeta = if (k == 2 || k == 3) 1f else 0.82f
                val a = w * w * (target[i] - x[i]) - 2f * zeta * w * v[i]
                v[i] += a * h
                x[i] += v[i] * h
            }
            rem -= h
        }
        return FaceParams(clampEye(eyeShapeOf(x, 0)), clampEye(eyeShapeOf(x, 8)))
    }

    private fun clampEye(e: EyeShape) = e.copy(
        sx = e.sx.coerceIn(0.7f, 1.3f), sy = e.sy.coerceIn(0.6f, 1.35f),
        dx = e.dx.coerceIn(-0.08f, 0.08f), dy = e.dy.coerceIn(-0.08f, 0.08f),
        rot = e.rot.coerceIn(-25f, 25f),
        upper = e.upper.coerceIn(0.15f, 1.15f), lower = e.lower.coerceIn(0.3f, 1.15f),
        slant = e.slant.coerceIn(-0.5f, 0.5f),
    )

    /** Engine21: the full-strength accent shape (contact sheets / audits). */
    internal fun accentPreview(a: FaceAccent): FaceParams { accentSide = 1f; return FaceParams(clampEye(accentTarget(a, 0, 0f)), clampEye(accentTarget(a, 1, 0f))) }

    /** side 0 = left (screen), 1 = right. "inner" is toward the other eye. */
    private fun lead(side: Int) = (side == 0) == (accentSide > 0f)

    private fun accentTarget(a: FaceAccent, side: Int, time: Float): EyeShape {
        val s = accentSide
        return when (a) {
            FaceAccent.None -> EyeShape.ID
            // Engine21.1 (Julian): asymmetry is welcome; eyes never grow / shrink from the
            // bottom. Lids come down from the top, both bottoms stay level (see [shapeOutline]).
            // Curious = his "Looking to the right": the lead eye opens up a touch, the other is
            // lidded from the top (flatter top corners), bottoms level.
            FaceAccent.Curious -> if (lead(side)) EyeShape(sy = 1.06f) else EyeShape(upper = 0.74f, slant = 0.06f)
            // Annoyed = a light Angry frame: lids down on the inner corners.
            FaceAccent.Annoyed -> EyeShape(upper = 0.8f, slant = 0.09f)
            // Shy = a soft Happy: the lower lid rises (top untouched), set a hair low.
            FaceAccent.Shy -> EyeShape(lower = 0.7f, dy = 0.015f)
            FaceAccent.Startled -> EyeShape(sx = 1.2f, sy = 1.4f, dy = 0.02f) // grows up from the bottom, which drops a touch (Figma Surprised)
            FaceAccent.Dizzy -> {
                // Eyes circle in opposite phase, slowly settling.
                val ph = time * 7f + side * PI.toFloat()
                EyeShape(dx = cos(ph) * 0.03f, dy = sin(ph) * 0.03f, rot = sin(ph) * 6f, sy = 0.95f)
            }
            // Smug = Suspicious, lopsided: the lead eye lidded heavily (flat top), the other a
            // little; a hint of the happy lower lid on both.
            FaceAccent.Smug -> if (lead(side)) EyeShape(upper = 0.56f, lower = 0.9f, slant = 0.08f) else EyeShape(upper = 0.78f, lower = 0.9f)
            // Focused = a light Suspicious: both lids in a little, flat-ish top and bottom.
            FaceAccent.Focused -> EyeShape(upper = 0.76f, lower = 0.9f)
            // Drowsy = a light Bored: heavy flat upper lids, bottoms level.
            FaceAccent.Drowsy -> EyeShape(upper = 0.52f)
            // Worried = his Worried frame: soft lids, inner corners raised (round corners kept).
            FaceAccent.Worried -> EyeShape(slant = -0.1f, upper = 0.97f)
            // Glee = Happy: the lower lid lifts high, flat bottom, round top.
            FaceAccent.Glee -> EyeShape(lower = 0.6f, sx = 1.03f)
            // WakeSquint = part Eyes closed: lids nearly met from the top, lower lid a little up;
            // the lead eye opens first.
            FaceAccent.WakeSquint -> if (lead(side)) EyeShape(upper = 0.52f, lower = 0.84f) else EyeShape(upper = 0.38f, lower = 0.8f)
            // Sad = his Sad frame: soft, outer corners dropped, round corners kept.
            FaceAccent.Sad -> EyeShape(slant = -0.1f, upper = 0.93f, dy = 0.015f)
            // Puzzled = his Sad/hurt/confused frame, lopsided: the lead eye lidded with a soft
            // inner rise, the other a little lidded with the lid tipped the other way.
            // Engine21.2: never a parallel tilt (the old pair leaned // ) and no soft slide (its
            // corner kink). One eye lidded from the top with a hard, brow-down lid (inner corner
            // lower, top corners flattened), the other upright and open a touch wider; bottoms level.
            FaceAccent.Puzzled -> if (lead(side)) EyeShape(upper = 0.7f, slant = 0.1f) else EyeShape(sy = 1.04f)
        }
    }

    companion object {
        /** Capsule rotation per unit of slant (sign chosen so + = inner corner lower). */
        const val SLANT_DEG = 14f

        /** Move the lids and resize straight edges, retaining uniformly rounded corners. */
        fun shapeOutline(xs: FloatArray, ys: FloatArray, side: Int, p: EyeShape) {
            if (p.isIdentity() || xs.isEmpty()) return
            val originX = (xs.min() + xs.max()) / 2f
            val originY = (ys.min() + ys.max()) / 2f
            // Thin authored bars can already be rotated (Sleeping). Resize in their
            // own frame so the circular end caps do not straddle the slice bands.
            val aspect = (xs.max() - xs.min()) / (ys.max() - ys.min()).coerceAtLeast(0.01f)
            val amount = ((aspect - 1.5f) / 1.5f).coerceIn(0f, 1f)
            var xx = 0f; var xy = 0f; var yy = 0f
            for (i in xs.indices) {
                val x = xs[i] - originX; val y = ys[i] - originY
                xx += x * x; xy += x * y; yy += y * y
            }
            val authoredAngle = 0.5f * atan2(2f * xy, xx - yy) * amount * amount * (3f - 2f * amount)
            val ac = cos(authoredAngle); val ass = sin(authoredAngle)
            if (authoredAngle != 0f) for (i in xs.indices) {
                val x = xs[i] - originX; val y = ys[i] - originY
                xs[i] = originX + x * ac + y * ass
                ys[i] = originY - x * ass + y * ac
            }
            val left = xs.min(); val right = xs.max()
            val top = ys.min(); val bottom = ys.max()
            val cx = (left + right) / 2f
            // Lower lid rises from the bottom; the upper lid descends with the bottom fixed.
            val lidBottom = top + (bottom - top) * p.lower.coerceIn(0.05f, 1f)
            val width = ((right - left) * p.sx).coerceAtLeast(0.01f)
            val height = ((lidBottom - top) * p.upper.coerceIn(0.05f, 1.25f) * p.sy).coerceAtLeast(0.01f)
            EyeCornerGeometry.resize(xs, ys, cx - width / 2f, lidBottom - height, width, height)
            // Seat a slanted lid by rotation rather than shearing its circular corners.
            val inward = if (side == 0) 1f else -1f
            val r = authoredAngle + Math.toRadians((p.rot + p.slant * SLANT_DEG * inward).toDouble()).toFloat()
            val cr = cos(r); val sr = sin(r)
            val pivotX = originX + (cx - originX) * ac - (lidBottom - originY) * ass
            val pivotY = originY + (cx - originX) * ass + (lidBottom - originY) * ac
            for (i in xs.indices) {
                val x = xs[i] - cx; val y = ys[i] - lidBottom
                xs[i] = pivotX + x * cr - y * sr + p.dx * 250f
                ys[i] = pivotY + x * sr + y * cr + p.dy * 250f
            }
        }

        /** Shape a Grok capsule: stays a capsule (scale, offset, rotation, openness). */
        private fun isBent(pts: List<FloatArray>): Boolean {
            if (pts.size < 3) return false
            val a = pts.first(); val b = pts.last(); val m = pts[pts.size / 2]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-4f)
            return kotlin.math.abs((m[0] - a[0]) * dy - (m[1] - a[1]) * dx) / len > 0.04f
        }

        fun shapeCapsule(e: WhSphereEyes.EyeState, side: Int, p: EyeShape): WhSphereEyes.EyeState {
            if (p.isIdentity()) return e
            // Capsule-local x points outward for each eye (left eye mirrored), y up.
            val mirror = if (side == 0) -1f else 1f
            val open = ((p.upper + p.lower) / 2f).coerceIn(0.1f, 1.2f)
            // Slant reads as a small symmetric rotation (mirrored frame does the rest).
            val rotDeg = p.rot * mirror + p.slant * SLANT_DEG
            // Upper lid closing more than the lower moves the visible eye down.
            val shiftY = (p.upper - p.lower) * 0.03f
            return e.copy(
                offX = e.offX + p.dx * mirror,
                offY = e.offY - p.dy + shiftY,
                tilt = e.tilt + Math.toRadians(rotDeg.toDouble()).toFloat(),
                wid = e.wid * p.sx.coerceIn(0.8f, 1.2f),
                lidY = e.lidY * open,
                // Bent spines (^ arcs) scale uniformly so the arc stays clean and the two
                // eyes stay mirror images; straight capsules may stretch freely.
                pts = if (isBent(e.pts)) { val u = (p.sx + p.sy) / 2f; e.pts.map { floatArrayOf(it[0] * u, it[1] * u) } }
                    else e.pts.map { floatArrayOf(it[0] * p.sx, it[1] * p.sy) },
            )
        }
    }
}
