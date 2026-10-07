package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Samples Grok Bot 0.68.1 motion clips (rotation/position/expression/squash/scale/lids)
 * with cubic-bezier easings from the carved desktop tracks.
 *
 * Desktop zoom rest is `vn = 0.6`. Missing scale tracks must sample 0.6 (not 1.0)
 * or Idle_A → Idle_B size jumps. Display normalizes against [DEFAULT_ZOOM].
 *
 * Clip switches use a short bridge (desktop `Sw` / `Li = 0.35s`) so euler/scale/pos
 * crossfade instead of hard-cutting.
 */
data class ClipKeyframe(
    val t: Float,
    val values: FloatArray? = null,
    val expression: String? = null,
    val ease: FloatArray = floatArrayOf(0.42f, 0f, 0.58f, 1f),
)

data class MarkClip(
    val name: String,
    val duration: Float,
    val body: String,
    val squashAnchor: Float,
    val inspectFollow: Boolean,
    val tracks: Map<String, List<ClipKeyframe>>,
)

data class ClipSample(
    val clipName: String,
    val time: Float,
    val duration: Float,
    val squashAnchor: Float,
    /** Euler degrees: pitch, yaw, roll (desktop rotation track). */
    val rotationDeg: FloatArray = floatArrayOf(0f, 0f, 0f),
    /** Normalized position offset from clip (x, y). */
    val position: FloatArray = floatArrayOf(0f, 0f),
    val squash: FloatArray = floatArrayOf(1f, 1f),
    val scale: Float = MarkClipEngine.DEFAULT_ZOOM,
    val lids: Float = 1f,
    val expression: String = "neutral",
    val inspectFollow: Boolean = true,
)

object MarkClipEngine {

    /** Desktop `vn` — rest zoom for marks. */
    const val DEFAULT_ZOOM = 0.6f

    /** Desktop `Li` — bridge clip duration (seconds). */
    const val BRIDGE_SEC = 0.35f

    /** Desktop bridge ease `pw = [.4, 0, .2, 1]`. */
    private val BRIDGE_EASE = floatArrayOf(0.4f, 0f, 0.2f, 1f)

    fun clipsForLifecycle(life: MarkLifecycle): List<String> {
        if (!AvatarPackData.isLoaded()) return emptyList()
        val key = when (life) {
            MarkLifecycle.Idle -> "Idle"
            MarkLifecycle.Listening -> "Listening"
            MarkLifecycle.Thinking -> "Thinking"
            MarkLifecycle.Streaming -> "Streaming"
            MarkLifecycle.Working -> "Working"
            MarkLifecycle.Finished -> "Finished"
            MarkLifecycle.Failed -> "Failed"
            MarkLifecycle.Sleeping -> "Sleeping"
        }
        return AvatarPackData.clipStateMap[key]
            ?: AvatarPackData.clipStateMap["Idle"]
            ?: listOf("Idle_A")
    }

    /**
     * Never pick clips that REST in Grok up-right brand euler
     * (Idle_D / Streaming_B / Working_E rest ≈ pitch -16°, yaw +33°).
     * Idle: Idle_B mostly, Idle_A for look-around/spin. Sleeping: Idle_B.
     */
    private val BRAND_STARE_CLIPS = setOf("Idle_D", "Streaming_B", "Working_E")

    fun pickClip(life: MarkLifecycle, random: Random = Random.Default): MarkClip? {
        val raw = clipsForLifecycle(life)
        if (raw.isEmpty()) return null
        val names = raw.filter { it !in BRAND_STARE_CLIPS }.ifEmpty { raw }
        val name = when (life) {
            MarkLifecycle.Idle -> {
                val r = random.nextFloat()
                when {
                    r < 0.68f && names.contains("Idle_B") -> "Idle_B"
                    names.contains("Idle_A") -> "Idle_A"
                    else -> names[random.nextInt(names.size)]
                }
            }
            MarkLifecycle.Sleeping -> when {
                names.contains("Idle_B") -> "Idle_B"
                names.contains("Idle_A") -> "Idle_A"
                else -> names[random.nextInt(names.size)]
            }
            else -> names[random.nextInt(names.size)]
        }
        return AvatarPackData.clips[name]
    }

    fun sample(clip: MarkClip, timeSec: Float): ClipSample {
        val dur = max(clip.duration, 0.001f)
        var t = timeSec % dur
        if (t < 0f) t += dur

        val rot = sampleVec3(clip.tracks["rotation"], t, floatArrayOf(0f, 0f, 0f))
        val pos = sampleVec2(clip.tracks["position"], t, floatArrayOf(0f, 0f))
        val squash = sampleVec2(clip.tracks["squash"], t, floatArrayOf(1f, 1f))
        val scale = sampleScalar(clip.tracks["scale"], t, DEFAULT_ZOOM)
        // Desktop `lids` keys are lid CLOSURE (0 = open, 1 = shut — Working_F blinks
        // 0→1→0). Before this was read as openness, so Working_F hid the eyes for
        // most of the clip. Expose openness (1 = open) like every other caller expects.
        val lids = clip.tracks["lids"]?.takeIf { it.isNotEmpty() }
            ?.let { (1f - sampleScalar(it, t, 0f)).coerceIn(0f, 1f) } ?: 1f
        val expr = sampleExpression(clip.tracks["expression"], t, "neutral")

        return ClipSample(
            clipName = clip.name,
            time = t,
            duration = dur,
            squashAnchor = clip.squashAnchor,
            // Raw clip euler. Brand-stare softening is continuous and lives in
            // MotionDirector (a step function here snapped poses mid-clip).
            rotationDeg = rot,
            position = pos,
            squash = squash,
            scale = scale,
            lids = lids,
            expression = sanitizeClipExpression(expr),
            inspectFollow = clip.inspectFollow,
        )
    }

    /** Strip inspecting / looking-to-the-right from clip expression tracks. */
    private fun sanitizeClipExpression(expr: String): String {
        val e = expr.lowercase().trim()
        return when (e) {
            "inspecting", "looking to the right", "looking", "lookright", "looking_right" ->
                "neutral"
            else -> expr
        }
    }

    /** Clip names that rest in the Grok up-right brand pose — never scheduled. */
    val brandStareClips: Set<String> get() = BRAND_STARE_CLIPS

    /**
     * Axes (0 pitch, 1 yaw, 2 roll) that contain a full turn (|v| > 200°) in keys
     * at or before [untilSec]. Amplitude scaling / up-damping must not touch a
     * spinning axis or a 360° turn would stop half way.
     */
    fun spinAxes(clip: MarkClip, untilSec: Float = Float.MAX_VALUE): BooleanArray {
        val out = BooleanArray(3)
        val keys = clip.tracks["rotation"] ?: return out
        for (k in keys) {
            if (k.t > untilSec + 1e-3f) continue
            val v = k.values ?: continue
            for (a in 0 until minOf(3, v.size)) if (abs(v[a]) > 200f) out[a] = true
        }
        return out
    }

    /** Wrap degrees to (-180, 180]. */
    fun wrap180(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * True when the clip pose at [t] is close to forward/rest (good place to hand
     * over to another clip without cutting a gesture).
     */
    fun isCalmAt(clip: MarkClip, t: Float): Boolean {
        val s = sample(clip, t)
        val r = s.rotationDeg
        val rot = maxOf(abs(wrap180(r[0])), abs(wrap180(r[1])), abs(wrap180(r[2])))
        val sq = maxOf(abs(s.squash[0] - 1f), abs(s.squash[1] - 1f))
        return rot < 9f && abs(s.position[1]) < 0.04f && sq < 0.025f
    }

    /** Sample at clip rest (t=0 / workArea start) — bridge target. */
    fun sampleRest(clip: MarkClip): ClipSample = sample(clip, 0f)

    /**
     * Desktop `Sw` — one-shot bridge from [from] pose to [to] rest over [BRIDGE_SEC].
     * Euler unwrapped for shortest arc (desktop `Vo`).
     */
    fun bridgeSample(from: ClipSample, toRest: ClipSample, bridgeElapsed: Float): ClipSample {
        val u = (bridgeElapsed / BRIDGE_SEC).coerceIn(0f, 1f)
        val e = cubicBezierY(u, BRIDGE_EASE)
        val toRot = floatArrayOf(
            unwrapDeg(toRest.rotationDeg[0], from.rotationDeg[0]),
            unwrapDeg(toRest.rotationDeg[1], from.rotationDeg[1]),
            unwrapDeg(toRest.rotationDeg[2], from.rotationDeg[2]),
        )
        return ClipSample(
            clipName = "bridge→${toRest.clipName}",
            time = bridgeElapsed,
            duration = BRIDGE_SEC,
            squashAnchor = from.squashAnchor,
            rotationDeg = floatArrayOf(
                lerp(from.rotationDeg[0], toRot[0], e),
                lerp(from.rotationDeg[1], toRot[1], e),
                lerp(from.rotationDeg[2], toRot[2], e),
            ),
            position = floatArrayOf(
                lerp(from.position[0], toRest.position[0], e),
                lerp(from.position[1], toRest.position[1], e),
            ),
            squash = floatArrayOf(
                lerp(from.squash[0], toRest.squash[0], e),
                lerp(from.squash[1], toRest.squash[1], e),
            ),
            scale = lerp(from.scale, toRest.scale, e),
            lids = lerp(from.lids, toRest.lids, e),
            expression = if (e < 0.5f) from.expression else toRest.expression,
            inspectFollow = toRest.inspectFollow,
        )
    }

    fun bridgeDone(bridgeElapsed: Float): Boolean = bridgeElapsed >= BRIDGE_SEC - 1e-4f

    /** Display scale relative to desktop rest zoom — keeps silhouette size stable. */
    fun visualScale(clipScale: Float): Float =
        (clipScale / DEFAULT_ZOOM).coerceIn(0.92f, 1.08f)

    /** Random phase so Idle loops don't all sync on the same cycle boundary. */
    fun randomPhase(clip: MarkClip, random: Random = Random.Default): Float {
        if (clip.duration <= 0.05f) return 0f
        return random.nextFloat() * clip.duration * 0.85f
    }

    private fun unwrapDeg(target: Float, from: Float): Float =
        target + 360f * kotlin.math.round((from - target) / 360f)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun sampleExpression(
        keys: List<ClipKeyframe>?,
        t: Float,
        fallback: String,
    ): String {
        if (keys.isNullOrEmpty()) return fallback
        var expr = fallback
        for (k in keys) {
            if (k.t <= t + 1e-4f) {
                val v = k.expression ?: continue
                if (v != "blink") expr = v
            }
        }
        return expr
    }

    private fun sampleScalar(keys: List<ClipKeyframe>?, t: Float, fallback: Float): Float {
        val v = sampleVec(keys, t, floatArrayOf(fallback), 1)
        return v[0]
    }

    private fun sampleVec2(keys: List<ClipKeyframe>?, t: Float, fallback: FloatArray): FloatArray =
        sampleVec(keys, t, fallback, 2)

    private fun sampleVec3(keys: List<ClipKeyframe>?, t: Float, fallback: FloatArray): FloatArray =
        sampleVec(keys, t, fallback, 3)

    private fun sampleVec(
        keys: List<ClipKeyframe>?,
        t: Float,
        fallback: FloatArray,
        dim: Int,
    ): FloatArray {
        if (keys.isNullOrEmpty()) return fallback.copyOf(dim)
        if (keys.size == 1) {
            val v = keys[0].values ?: return fallback.copyOf(dim)
            return pad(v, dim)
        }
        if (t <= keys.first().t) {
            return pad(keys.first().values ?: fallback, dim)
        }
        if (t >= keys.last().t) {
            return pad(keys.last().values ?: fallback, dim)
        }
        var i = 0
        while (i < keys.size - 1 && keys[i + 1].t < t) i++
        val a = keys[i]
        val b = keys[i + 1]
        val span = max(b.t - a.t, 1e-6f)
        val u = ((t - a.t) / span).coerceIn(0f, 1f)
        val e = cubicBezierY(u, a.ease)
        val av = pad(a.values ?: fallback, dim)
        val bv = pad(b.values ?: fallback, dim)
        val out = FloatArray(dim)
        for (d in 0 until dim) {
            out[d] = av[d] + (bv[d] - av[d]) * e
        }
        return out
    }

    private fun pad(v: FloatArray, dim: Int): FloatArray {
        if (v.size >= dim) return v.copyOf(dim)
        val out = FloatArray(dim)
        for (i in 0 until dim) out[i] = if (i < v.size) v[i] else 0f
        if (dim == 2 && v.isEmpty()) {
            out[0] = 1f; out[1] = 1f
        }
        return out
    }

    /**
     * Solve cubic bezier x(t)=u for parameter, return y.
     * Control points (0,0), (x1,y1), (x2,y2), (1,1).
     */
    fun cubicBezierY(u: Float, ease: FloatArray): Float {
        val x1 = ease.getOrElse(0) { 0.42f }
        val y1 = ease.getOrElse(1) { 0f }
        val x2 = ease.getOrElse(2) { 0.58f }
        val y2 = ease.getOrElse(3) { 1f }
        var t = u
        repeat(6) {
            val x = bezier(t, x1, x2)
            val dx = bezierDeriv(t, x1, x2)
            if (abs(dx) < 1e-5f) return@repeat
            t = (t - (x - u) / dx).coerceIn(0f, 1f)
        }
        return bezier(t, y1, y2).coerceIn(-0.2f, 1.2f).let { min(1.15f, max(-0.15f, it)) }
    }

    private fun bezier(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
    }

    private fun bezierDeriv(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * p1 + 6f * u * t * (p2 - p1) + 3f * t * t * (1f - p2)
    }
}
