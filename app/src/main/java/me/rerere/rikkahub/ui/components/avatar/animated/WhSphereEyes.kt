package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Desktop Grok Bot 0.68.1 sphere eye seating — `Dh` / `SS` / `J1` / `_v`.
 *
 * Idle clips use `body:"sphere"` → `wS`→`Dh`→`SS`, NOT mesh `sx`.
 * Eyes live on capsule frames J1 and ride the head quat, so look-around
 * is clip euler (and optional setGaze→Ou), not 2D sticker translate.
 */
object WhSphereEyes {

    /** Desktop `ur` — neutral eye width for En(ur,.57,0,0). */
    const val UR = 0.26f
    /** Desktop `Iu` — default half-width for expression strokes. */
    const val IU = 0.1305f
    /** Stroke half-widths for arc eyes (happy ^, closed ‿). */
    const val HAPPY_W = 0.112f // engine21: pill-weight stroke (was 0.097, read as a thin line)
    const val CLOSED_W = 0.105f // engine21: closed eyes are soft pill strokes, not hairlines (was 0.07)

    data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
        operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
        fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    }

    data class CapsuleFrame(val M: Vec3, val T: Vec3, val C: Vec3)

    /** Precomputed desktop `J1` (from `_a` → `cr` → `gk`). */
    val J1: List<CapsuleFrame> = listOf(
        CapsuleFrame(
            M = Vec3(-0.31534576f, 0.00041354723f, 0.94897676f),
            T = Vec3(-0.0012276245f, 0.9999989f, -0.0008437224f),
            C = Vec3(-0.94897605f, -0.0014310514f, -0.3153449f),
        ),
        CapsuleFrame(
            M = Vec3(0.31534576f, -0.00041354723f, 0.94897676f),
            T = Vec3(0.001217998f, 0.99999926f, 0.00003104021f),
            C = Vec3(0.94897606f, -0.0011460633f, -0.31534603f),
        ),
    )

    /** Desktop `gS = J1.map(MS)` — reverse winding per eye. */
    private val GS = booleanArrayOf(true, false)

    data class EyeState(
        val offX: Float,
        val offY: Float,
        val tilt: Float,
        val wid: Float,
        val lidY: Float,
        val pts: List<FloatArray>, // spine [x,y]
        val oval: FloatArray = floatArrayOf(0f, 0f),
        /** Engine17.1 blink: 0 open … 1 lids met (soft lid bar). Replaces the lidY squash. */
        val closeK: Float = 0f,
    )

    /** Desktop `Ou(body, gazeX, gazeY)` — gaze ∈ [-1,1] → euler deg + pos. */
    data class GazeDelta(val pitchDeg: Float, val yawDeg: Float, val px: Float, val py: Float)

    fun ou(bodyKind: String, gazeX: Float, gazeY: Float): GazeDelta {
        val gx = gazeX.coerceIn(-1f, 1f)
        val gy = gazeY.coerceIn(-1f, 1f)
        val nonSphere = bodyKind != "sphere"
        val s = 1f
        return GazeDelta(
            pitchDeg = -(if (nonSphere) 19f else 14f) * s * gy,
            yawDeg = (if (nonSphere) 26f else 18f) * s * gx,
            px = (if (nonSphere) 0.1f else 0.04f) * gx,
            py = (if (nonSphere) 0.075f else 0.03f) * gy,
        )
    }

    /** Desktop `fk` — quat from pitch/yaw/roll radians. */
    fun fk(pitch: Float, yaw: Float, roll: Float): FloatArray {
        val qx = ps(1f, 0f, 0f, pitch)
        val qy = ps(0f, 1f, 0f, yaw)
        val qz = ps(0f, 0f, 1f, roll)
        return fi(qz, fi(qy, qx))
    }

    private fun ps(ax: Float, ay: Float, az: Float, angle: Float): FloatArray {
        val h = angle * 0.5f
        val s = sin(h)
        return floatArrayOf(cos(h), ax * s, ay * s, az * s)
    }

    private fun fi(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
        a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
        a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
        a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0],
    )

    /** Desktop `Xn` — quat → 3×3 row-major. */
    fun xn(q: FloatArray): FloatArray {
        val t = q[0]; val n = q[1]; val r = q[2]; val s = q[3]
        return floatArrayOf(
            1f - 2f * (r * r + s * s), 2f * (n * r - t * s), 2f * (n * s + t * r),
            2f * (n * r + t * s), 1f - 2f * (n * n + s * s), 2f * (r * s - t * n),
            2f * (n * s - t * r), 2f * (r * s + t * n), 1f - 2f * (n * n + r * r),
        )
    }

    private fun wt(R: FloatArray, v: Vec3): Vec3 = Vec3(
        R[0] * v.x + R[1] * v.y + R[2] * v.z,
        R[3] * v.x + R[4] * v.y + R[5] * v.z,
        R[6] * v.x + R[7] * v.y + R[8] * v.z,
    )

    /** Desktop `En(width, height, tiltDeg, offX)`. */
    fun en(width: Float, height: Float, tiltDeg: Float, offX: Float): EyeState {
        val o = max(0f, (height - width) / 2f)
        val i = (tiltDeg * PI / 180.0).toFloat()
        val a = sin(i) * o
        val c = cos(i) * o
        return pr(
            offX = offX, offY = 0f, tilt = 0f, wid = width / 2f,
            p0 = floatArrayOf(-a, -c), p1 = floatArrayOf(0f, 0f), p2 = floatArrayOf(a, c),
        )
    }

    fun pr(
        offX: Float, offY: Float, tilt: Float, wid: Float,
        p0: FloatArray, p1: FloatArray, p2: FloatArray, lidY: Float = 1f,
    ): EyeState {
        val path = mk(p0, p1, p2)
        return EyeState(offX, offY, tilt, wid, lidY, eh(path))
    }

    /** Neutral resting pair — desktop `ln.neutral`. */
    fun neutralPair(): List<EyeState> {
        val e = en(UR, 0.57f, 0f, 0f)
        return listOf(e, e)
    }

    /**
     * Seats [short] so its bottom edge (lowest spine point minus cap radius [EyeState.wid])
     * lines up with [open]'s bottom edge in eye-local space (+y = up): a half-closed eye
     * reads as a lid coming down from above rather than a small shape floated to the top.
     */
    private fun bottomAligned(open: EyeState, short: EyeState): EyeState {
        fun halfExtentDown(e: EyeState): Float {
            val l = cos(e.tilt)
            val d = sin(e.tilt)
            val minSpineY = e.pts.minOfOrNull { -d * it[0] + l * it[1] * e.lidY } ?: 0f
            return e.wid - minSpineY
        }
        return short.copy(offY = open.offY - (halfExtentDown(open) - halfExtentDown(short)))
    }

    /**
     * Engine21.2 Grok confused, explored in pill language (sheet: grok_confused_options.png).
     * [left, right] for "confused"; "confused left" is the mirror. All keep the eyes apart and
     * never lean both the same way.
     *  - A "brow up": one pill as at rest, the other a little shorter and raised — the pill
     *    analogue of one raised eyebrow. (chosen)
     *  - B "one squint": the other eye a short, slightly lowered pill — a skeptical squint.
     *  - C "tipped brow": the other eye a touch shorter, raised and tipped 14° (one tilted, one upright).
     *  - D "soft lopsided": both a hair tipped as a mirrored pair, one shorter and raised.
     *  - O: the Engine21.1 o_O (comparison sheet only).
     */
    internal var confusedVariant = 'A'

    internal fun confusedPair(v: Char): List<EyeState> {
        fun up(e: EyeState, dy: Float) = e.copy(offY = e.offY + dy)
        fun pair(open: EyeState, short: EyeState) = listOf(open, bottomAligned(open, short))
        return when (v) {
            // The Engine21.1 o_O, kept only for the comparison sheet.
            'O' -> listOf(en(UR, 0.64f, 0f, 0f), up(en(UR, 0.4f, -24f, 0f), 0.12f))
            'B' -> pair(en(UR, 0.57f, 0f, 0f), en(UR, 0.36f, 0f, 0f))
            'C' -> pair(en(UR, 0.57f, 0f, 0f), en(UR, 0.5f, 14f, 0f))
            'D' -> pair(en(UR, 0.6f, 7f, 0f), en(UR, 0.46f, 7f, 0f))
            else -> pair(en(UR, 0.57f, 0f, 0f), en(UR, 0.46f, 0f, 0f))
        }
    }

    fun expressionPair(name: String, lid: Float): List<EyeState> {
        val lidY = lid.coerceIn(0.02f, 1.25f)
        val base = when (name.lowercase()) {
            // Clean ^ ^ arcs: a gentle upward-curved stroke whose bend radius stays
            // well above the stroke half-width, so the outline never folds into a bean.
            // Engine21: a bent pill (^ ^) at pill weight — the happy squeeze of the neutral eye,
            // with a gentler bend so the thicker stroke never folds into a bean.
            "happy" -> {
                val e = pr(0f, 0.04f, 0f, HAPPY_W,
                    floatArrayOf(0.18f, -0.04f), floatArrayOf(0f, 0.12f), floatArrayOf(-0.18f, -0.04f))
                listOf(e, e)
            }
            // Engine21.1: wide open — taller AND wider than neutral (the whites show).
            "surprised" -> {
                val e = en(0.44f, 0.9f, 0f, 0f)
                listOf(e, e)
            }
            "sad" -> {
                // A full open pill with a pronounced mirrored lean; sadness is not a squint.
                val e = en(UR, 0.62f, -51f, 0f).let { it.copy(offY = it.offY - 0.035f) }
                listOf(e, e)
            }
            // Engine21: asleep = soft downward-curved pill strokes (relaxed lids).
            "sleepy", "sleeping" -> {
                val e = pr(0f, 0.02f, 0f, CLOSED_W * 0.88f,
                    floatArrayOf(-0.155f, 0.035f), floatArrayOf(0f, -0.07f), floatArrayOf(0.155f, 0.035f))
                listOf(e, e)
            }
            // Engine21: eyes simply closed (resting, concentrating) = the neutral pill with its
            // lid shut (as at the bottom of a blink), a little wider: a flat pill that morphs
            // in and out by the lid alone — no spine rotation through a tilted bean.
            "closed" -> {
                val n = en(UR, 0.57f, 0f, 0f)
                val e = n.copy(wid = n.wid * 1.22f, lidY = 0.2f)
                listOf(e, e)
            }
            // Bored: heavy half-closed lids — flat bars, set a little low (looking at nothing).
            "squinted", "bored", "calm" -> {
                val e = en(UR, 0.5f, 90f, 0f).let { it.copy(offY = it.offY - 0.03f) }
                listOf(e, e)
            }
            // Pouty keeps the same open pill language, with a pronounced sulking lean.
            "pouty" -> {
                val e = en(UR, 0.60f, 46f, 0f).let { it.copy(offY = it.offY - 0.05f) }
                listOf(e, e)
            }
            // Confused: same eyes as every other face, one lid tilted, both on one level.
            // Same-size pills (like Sad, mirrored on one side only): one tilts, neither shrinks.
            "confused" -> {
                val open = en(UR, 0.6f, 0f, 0f)
                listOf(open, bottomAligned(open, en(UR, 0.58f, -18f, 0f)))
            }
            // Engine17.1: concentrated / focused — calm, restrained: shorter pills with a
            // slight inward tilt (half of angry's), clearly not the flat bored bars.
            // Engine21: concentrating = clearly shorter pills (a squint), no tilt — distinct
            // from both neutral and angry.
            "focused" -> {
                val e = en(UR, 0.36f, 0f, 0f)
                listOf(e, e)
            }
            // Engine16.1: symmetric, slightly shorter pills — a quiet "hm".
            // Engine21: "hm?" — both pills tipped the same way (symmetric size, no lopsided
            // growth), the old shorter pills were indistinguishable from neutral.
            // Engine21.2 confused: see [confusedPair] (the Engine21.1 o_O read as ugly).
            "hm" -> confusedPair(confusedVariant)
            // The mirror: the same two eyes swapped (per-side tilt is mirrored already).
            "hmleft" -> confusedPair(confusedVariant).reversed()
            // Strong mirrored frown, with open eyes rather than shortened or closed bars.
            "angry" -> {
                val e = en(UR, 0.62f, 51f, 0f)
                listOf(e, e)
            }
            // Playful (Group 3): one eye a hair shorter and tipped, the other open.
            "playful" -> {
                val open = en(UR, 0.6f, 0f, 0f)
                listOf(open, bottomAligned(open, en(UR, 0.5f, -10f, 0f)))
            }
            // >< squeeze (dizzy).
            "dizzy" -> {
                val e = pr(-0.1f, 0f, 0f, IU,
                    floatArrayOf(0.01f, 0.16f), floatArrayOf(-0.17f, 0f), floatArrayOf(0.01f, -0.16f))
                listOf(e, e)
            }
            // Playful wink: one soft closed arc, one open eye.
            // Engine21.1: the closed eye is the happy ^ pill (a cute squeeze), the other open.
            // A wink's arc is a *closed* eye, not a half-lid: it stays centred on the open eye
            // (a closed arc parked at the other eye's bottom edge read as a droop, not a wink).
            "wink" -> listOf(
                pr(0f, 0.04f, 0f, HAPPY_W,
                    floatArrayOf(0.18f, -0.04f), floatArrayOf(0f, 0.12f), floatArrayOf(-0.18f, -0.04f)),
                en(UR, 0.57f, 0f, 0f),
            )
            // Grok brand stare — never used in LastChat. Soften to neutral.
            "inspecting", "looking to the right", "looking", "lookright", "looking_right" ->
                neutralPair()
            else -> neutralPair()
        }
        // Wider set only where the contours reach toward each other (closed arcs,
        // the >< chevrons, flat squints): at the neutral spacing those read as one
        // joined shape. Morphs blend offX, so the spacing glides with the expression.
        // Engine14: no per-expression spread (spacing is the neutral one; the gap guard only
        // prevents touching).
        val spread = NO_SPREAD
        return base.mapIndexed { i, e ->
            e.copy(offX = e.offX + spread.getOrElse(i) { 0f }, lidY = e.lidY * lidY)
        }
    }

    /**
     * Extra outward offset per eye (left, right), eye-local units; 0 = neutral spacing.
     * Only the contours that reach toward each other at the neutral spacing get any:
     * closed arcs and happy arcs touch, flat squint dashes merge into one bar and the
     * >< chevrons cross into an X. Each value is the smallest that leaves a clear gap
     * (checked on every picker shape); everything else keeps the original spacing.
     */
    private fun spreadFor(name: String): FloatArray = when (name) {
        "happy" -> floatArrayOf(0.022f, 0.022f)
        "sleepy", "sleeping" -> floatArrayOf(0.028f, 0.028f)
        "squinted", "bored", "calm" -> floatArrayOf(0.024f, 0.024f)
        "dizzy" -> floatArrayOf(0.085f, 0.085f)
        "sad" -> floatArrayOf(0.03f, 0.03f)
        "angry" -> floatArrayOf(0.02f, 0.02f)
        else -> NO_SPREAD
    }

    private val NO_SPREAD = floatArrayOf(0f, 0f)

    /** Generical expression id → Grok capsule expression. */
    fun grokNameFor(expressionId: String): String = when (expressionId) {
        "Happy" -> "happy"
        "Surprised" -> "surprised"
        "Sad", "Worried" -> "sad"
        // Engine21 audit: every Grok face has one readable meaning in pill language.
        //  happy ^ ^ (also hurt-or-cute / playful: merged, they read the same in pills)
        //  surprised tall · sad / \ low (Worried merged) · angry \ / · focused short squint
        //  bored = flat bars (pouty merged: an unimpressed sulk) · confused = tipped pills
        //  closed = short flat pills · sleeping = soft ∪ strokes.
        "Sleeping" -> "sleepy"
        "Eyes closed" -> "closed"
        "Bored" -> "squinted"
        "angered or concentrated" -> "focused"
        "confused" -> "hm"
        "confused left" -> "hmleft"
        "pouty" -> "pouty"
        // Engine21.1: hurt / cute is Julian's wink frame — one open pill, one closed arc.
        "hurt or cute", "Group 3" -> "wink"
        "Angry" -> "angry"
        else -> "neutral"
    }

    /** Point-by-point blend of two capsule pairs (same spine sample counts). */
    fun blendPairs(a: List<EyeState>, b: List<EyeState>, t: Float): List<EyeState> {
        val u = t.coerceIn(0f, 1f)
        if (u <= 0f) return a
        if (u >= 1f) return b
        fun mix(x: Float, y: Float) = x + (y - x) * u
        return a.indices.map { i ->
            val e0 = a[i]
            val e1r = b.getOrElse(i) { b.last() }
            val n = min(e0.pts.size, e1r.pts.size)
            // Spines drawn in opposite directions would cross mid-blend and collapse to a
            // dot: match the direction first.
            fun d(p: FloatArray, q: FloatArray) = kotlin.math.hypot(p[0] - q[0], p[1] - q[1])
            val e1 = if (n >= 2 && d(e0.pts[0], e1r.pts[n - 1]) + d(e0.pts[n - 1], e1r.pts[0]) <
                d(e0.pts[0], e1r.pts[0]) + d(e0.pts[n - 1], e1r.pts[n - 1])) e1r.copy(pts = e1r.pts.take(n).reversed()) else e1r
            var pts = List(n) { k -> floatArrayOf(mix(e0.pts[k][0], e1.pts[k][0]), mix(e0.pts[k][1], e1.pts[k][1])) }
            // Length floor: a blend frame is never shorter than 60% of the shorter endpoint.
            fun span(p: List<FloatArray>): Float = if (p.size < 2) 0f else
                kotlin.math.hypot(p.maxOf { it[0] } - p.minOf { it[0] }, p.maxOf { it[1] } - p.minOf { it[1] })
            val floor = 0.6f * minOf(span(e0.pts.take(n)), span(e1.pts))
            val sp = span(pts)
            if (sp > 1e-5f && sp < floor) {
                val cx = pts.sumOf { it[0].toDouble() }.toFloat() / n; val cy = pts.sumOf { it[1].toDouble() }.toFloat() / n
                val k = floor / sp
                pts = pts.map { floatArrayOf(cx + (it[0] - cx) * k, cy + (it[1] - cy) * k) }
            }
            EyeState(
                offX = mix(e0.offX, e1.offX),
                offY = mix(e0.offY, e1.offY),
                tilt = mix(e0.tilt, e1.tilt),
                wid = mix(e0.wid, e1.wid),
                lidY = mix(e0.lidY, e1.lidY),
                pts = pts,
                closeK = mix(e0.closeK, e1.closeK),
            )
        }
    }

    /** Morphed Grok pair for a Generical-named expression transition, with lids applied. */
    fun morphPair(from: String, to: String, t: Float, lid: Float, top: String? = null, topT: Float = 0f): List<EyeState> {
        val a = expressionPair(grokNameFor(from), 1f)
        val b = expressionPair(grokNameFor(to), 1f)
        val c = top?.let { expressionPair(grokNameFor(it), 1f) }
        val l = lid.coerceIn(0.02f, 1.25f)
        val u = t.coerceIn(0f, 1f)
        val w = if (c == null) 0f else topT.coerceIn(0f, 1f)
        return a.indices.map { i ->
            val e0 = a[i]; val e1 = b.getOrElse(i) { b.last() }
            val e01 = cleanBlend(e0, e1, u)
            // Engine17: interrupted morph — the newest face grows over the current blend.
            val e2 = c?.getOrElse(i) { c.last() }
            val e = if (e2 != null && w > 0f) cleanBlend(e01, e2, w) else e01
            // Arc eyes (^ ‿) are already "closed-looking": a blink only thins them a
            // little instead of flattening the arcs into one long line.
            val arc0 = (if (curved(e0)) 1f - u else 0f) + (if (curved(e1)) u else 0f)
            val arc = arc0 * (1f - w) + (if (e2 != null && curved(e2)) w else 0f)
            val le = l + (1f - l).coerceAtLeast(0f) * arc * 0.8f
            if (l >= 1f) e.copy(lidY = e.lidY * l) else {
                // Engine17.1: straight eyes close into a soft lid bar (closeK); arcs only thin.
                val a = arc.coerceIn(0f, 1f)
                e.copy(lidY = e.lidY * (1f + (le - 1f) * a), closeK = ((1f - l) * (1f - a)).coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Engine19: N-way blend for a weight vector (Σ w = 1). Weights are merged by Grok face,
     * then folded pairwise in a stable name order with the same continuous capsule blend as
     * [morphPair] — continuous in every weight, identical to [morphPair] for two faces.
     */
    fun morphWeighted(weights: List<Pair<String, Float>>, lid: Float): List<EyeState> {
        val merged = java.util.TreeMap<String, Float>()
        for ((n, w) in weights) if (w > 0f) merged.merge(grokNameFor(n), w, Float::plus)
        if (merged.isEmpty()) return morphPair("Neutral", "Neutral", 1f, lid)
        val shapes = merged.entries.map { (k, w) -> expressionPair(k, 1f) to w }
        val l = lid.coerceIn(0.02f, 1.25f)
        val n0 = shapes[0].first.size
        return (0 until n0).map { i ->
            var e = shapes[0].first[i]
            var acc = shapes[0].second
            var arc = (if (curved(e)) acc else 0f)
            for (k in 1 until shapes.size) {
                val (sh, w) = shapes[k]
                val ek = sh.getOrElse(i) { sh.last() }
                acc += w
                e = cleanBlend(e, ek, (w / acc).coerceIn(0f, 1f))
                if (curved(ek)) arc += w
            }
            arc = (arc / acc.coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            val le = l + (1f - l).coerceAtLeast(0f) * arc * 0.8f
            if (l >= 1f) e.copy(lidY = e.lidY * l) else e.copy(lidY = e.lidY * (1f + (le - 1f) * arc), closeK = ((1f - l) * (1f - arc)).coerceIn(0f, 1f))
        }
    }

    /** Spine bends (an arc eye) rather than running straight (a pill). */
    private fun curved(e: EyeState): Boolean {
        val p = e.pts; if (p.size < 3) return false
        val a = p.first(); val b = p.last()
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        val len = hypot(dx, dy).coerceAtLeast(1e-4f)
        return p.maxOf { kotlin.math.abs((it[0] - a[0]) * dy - (it[1] - a[1]) * dx) / len } > 0.02f
    }

    /**
     * Engine17.1: ONE continuous morph for every capsule pair (arc ↔ pill ↔ bar ↔ tilt).
     * Each spine is decomposed into chord centre, chord angle, half-length and a bend
     * profile (offsets perpendicular to the chord, at normalised positions along it); every
     * parameter blends smoothly at once — an arc straightens *while* it turns and resizes, no
     * stages, no hitches. When the angle changes a lot (pill ↔ bar), a single smooth length
     * dip (sin πu) shortens the stroke toward a soft round so it never visibly swings through
     * a \ / diagonal. C∞ in u; identical endpoints.
     */
    private fun cleanBlend(e0: EyeState, e1: EyeState, u: Float): EyeState = smoothBlend(e0, e1, u)

    private class Spine(val cx: Float, val cy: Float, val ang: Float, val half: Float, val s: FloatArray, val b: FloatArray)

    private fun spineOf(pts: List<FloatArray>): Spine {
        val a = pts.first(); val z = pts.last()
        val dx = z[0] - a[0]; val dy = z[1] - a[1]
        val len = hypot(dx, dy)
        val ang = if (len > 1e-5f) kotlin.math.atan2(dy, dx) else (PI / 2).toFloat()
        val ux = cos(ang); val uy = sin(ang)
        val cx = (a[0] + z[0]) / 2f; val cy = (a[1] + z[1]) / 2f
        val half = (len / 2f).coerceAtLeast(1e-5f)
        val n = pts.size
        val sArr = FloatArray(n); val bArr = FloatArray(n)
        for (k in 0 until n) {
            val rx = pts[k][0] - cx; val ry = pts[k][1] - cy
            sArr[k] = (rx * ux + ry * uy) / half
            bArr[k] = -rx * uy + ry * ux
        }
        return Spine(cx, cy, ang, half, sArr, bArr)
    }

    private fun smoothBlend(e0: EyeState, e1r: EyeState, t: Float): EyeState {
        val u = t.coerceIn(0f, 1f)
        if (u <= 0f) return e0
        if (u >= 1f) return e1r
        fun mix(x: Float, y: Float) = x + (y - x) * u
        val n = min(e0.pts.size, e1r.pts.size)
        if (n < 2) return blendPairs(listOf(e0), listOf(e1r), u)[0]
        val p0 = e0.pts.take(n)
        var p1 = e1r.pts.take(n)
        val A = spineOf(p0)
        var B = spineOf(p1)
        // Same travel direction (a reversed spine would flip through 180°).
        var dAng = B.ang - A.ang
        while (dAng > PI) dAng -= (2 * PI).toFloat()
        while (dAng < -PI) dAng += (2 * PI).toFloat()
        if (kotlin.math.abs(dAng) > PI / 2) {
            p1 = p1.reversed(); B = spineOf(p1)
            dAng = B.ang - A.ang
            while (dAng > PI) dAng -= (2 * PI).toFloat()
            while (dAng < -PI) dAng += (2 * PI).toFloat()
        }
        // Length dip for large turns: 0 for ≤ ~20°, ~0.72 at 90°.
        val turn = ((kotlin.math.abs(dAng) - 0.35f) / (PI.toFloat() / 2f - 0.35f)).coerceIn(0f, 1f)
        val dip = 1f - 0.72f * turn * turn * sin(PI.toFloat() * u)
        val ang = A.ang + dAng * u
        val half = mix(A.half, B.half) * dip
        val ux = cos(ang); val uy = sin(ang)
        val cx = mix(A.cx, B.cx); val cy = mix(A.cy, B.cy)
        val pts = List(n) { k ->
            val sk = mix(A.s[k], B.s[k]) * half
            val bk = mix(A.b[k], B.b[k]) * dip
            floatArrayOf(cx + ux * sk - uy * bk, cy + uy * sk + ux * bk)
        }
        return EyeState(
            offX = mix(e0.offX, e1r.offX), offY = mix(e0.offY, e1r.offY),
            tilt = mix(e0.tilt, e1r.tilt), wid = mix(e0.wid, e1r.wid),
            lidY = mix(e0.lidY, e1r.lidY), pts = pts,
            closeK = mix(e0.closeK, e1r.closeK),
        )
    }

    // --- spine helpers (desktop Mk / eh, linear branch) ---

    private fun mk(p0: FloatArray, p1: FloatArray, p2: FloatArray): FloatArray {
        val l = FloatArray(18)
        fun d(y: Int, w: FloatArray) { l[y * 6] = w[0]; l[y * 6 + 1] = w[1] }
        fun h(y: Int, w: FloatArray) { l[y * 6 + 2] = w[0] - l[y * 6]; l[y * 6 + 3] = w[1] - l[y * 6 + 1] }
        fun f(y: Int, w: FloatArray) { l[y * 6 + 4] = w[0] - l[y * 6]; l[y * 6 + 5] = w[1] - l[y * 6 + 1] }
        val s = p0; val i = p1; val c = p2
        val p = floatArrayOf((s[0] + i[0]) / 2f, (s[1] + i[1]) / 2f)
        val m = floatArrayOf((i[0] + c[0]) / 2f, (i[1] + c[1]) / 2f)
        val g = floatArrayOf((p[0] + m[0]) / 2f, (p[1] + m[1]) / 2f)
        d(0, s); d(1, g); d(2, c)
        f(0, floatArrayOf(s[0] + 2f / 3f * (p[0] - s[0]), s[1] + 2f / 3f * (p[1] - s[1])))
        h(1, floatArrayOf(g[0] + 2f / 3f * (p[0] - g[0]), g[1] + 2f / 3f * (p[1] - g[1])))
        f(1, floatArrayOf(g[0] + 2f / 3f * (m[0] - g[0]), g[1] + 2f / 3f * (m[1] - g[1])))
        h(2, floatArrayOf(c[0] + 2f / 3f * (m[0] - c[0]), c[1] + 2f / 3f * (m[1] - c[1])))
        return l
    }

    private fun eh(path: FloatArray): List<FloatArray> {
        val t = (21 + 1) shr 1
        val n = ArrayList<FloatArray>()
        fun ur(i: Int) = floatArrayOf(path[i * 6], path[i * 6 + 1])
        fun kr(i: Int, out: Boolean): FloatArray {
            val r = if (out) 4 else 2
            return floatArrayOf(path[i * 6] + path[i * 6 + r], path[i * 6 + 1] + path[i * 6 + r + 1])
        }
        fun seg(s: FloatArray, o: FloatArray, i: FloatArray, a: FloatArray, from: Int) {
            for (u in from until t) {
                val l = u / (t - 1f)
                val d = 1f - l
                val h = d * d * d
                val f = 3f * d * d * l
                val p = 3f * d * l * l
                val m = l * l * l
                n += floatArrayOf(
                    h * s[0] + f * o[0] + p * i[0] + m * a[0],
                    h * s[1] + f * o[1] + p * i[1] + m * a[1],
                )
            }
        }
        seg(ur(0), kr(0, true), kr(1, false), ur(1), 0)
        seg(ur(1), kr(1, true), kr(2, false), ur(2), 1)
        return n
    }

    /** Capsule outline from spine + half-width (desktop sS) with rounded caps. */
    fun stadiumOutline(eye: EyeState): List<FloatArray> {
        val c = eye.closeK
        if (c <= 0.001f) return stadiumOpen(eye)
        val open = stadiumOpen(eye.copy(closeK = 0f))
        if (open.size < 3) return open
        return lidSquash(open, eye.wid, c.coerceIn(0f, 1f))
    }

    /**
     * Engine17.1 blink: the eye squashes about its lid line (lids meet at the lower middle),
     * widening a little, never thinner than a soft lid bar, and bends into a gentle ‿ only as
     * it closes — the Grok cousin of Generical's closing eye. Shape stays smooth and convex.
     */
    private fun lidSquash(open: List<FloatArray>, wid: Float, c: Float): List<FloatArray> {
        val minX = open.minOf { it[0] }; val maxX = open.maxOf { it[0] }
        val minY = open.minOf { it[1] }; val maxY = open.maxOf { it[1] }
        val h = (maxY - minY).coerceAtLeast(1e-4f)
        val cx = (minX + maxX) / 2f
        val yl = minY + h * 0.4f
        val bar = 2f * (wid * 0.42f).coerceIn(0.035f, 0.06f)
        val k = c * c * (3f - 2f * c)
        val sy = max(1f - k, (bar / h).coerceAtMost(1f) * k + (1f - k))
        val sx = 1f + 0.22f * k
        val halfW = ((maxX - minX) / 2f * sx).coerceAtLeast(1e-4f)
        val bend = 0.2f * halfW * k * k
        return open.map { p ->
            val x = cx + (p[0] - cx) * sx
            val f = (x - cx) / halfW
            floatArrayOf(x, yl + (p[1] - yl) * sy + bend * f * f)
        }
    }

    private fun stadiumOpen(eye: EyeState): List<FloatArray> {
        val pts = eye.pts
        if (pts.size < 2) return emptyList()
        val w = eye.wid.coerceAtLeast(0.02f)
        val left = ArrayList<FloatArray>()
        val right = ArrayList<FloatArray>()
        val tangents = ArrayList<FloatArray>()
        for (i in pts.indices) {
            val p0 = pts[max(0, i - 1)]
            val p1 = pts[min(pts.lastIndex, i + 1)]
            var tx = p1[0] - p0[0]
            var ty = p1[1] - p0[1]
            val L = hypot(tx, ty).coerceAtLeast(1e-6f)
            tx /= L; ty /= L
            tangents += floatArrayOf(tx, ty)
            val nx = -ty; val ny = tx
            left += floatArrayOf(pts[i][0] + nx * w, pts[i][1] + ny * w)
            right += floatArrayOf(pts[i][0] - nx * w, pts[i][1] - ny * w)
        }
        // Rounded caps at both ends. Each cap must continue the outline in the
        // SAME direction it is travelling: end cap goes +n → -n (left side → right
        // side, bulging forward), start cap goes -n → +n (right side → left side,
        // bulging backward). polish3 ran the start cap +n → -n, so the outline
        // crossed itself at the bottom — the "cut line + separate half-circle".
        val steps = 12
        fun cap(center: FloatArray, tangent: FloatArray, into: ArrayList<FloatArray>, fromSign: Float, bulge: Float) {
            val tx = tangent[0]; val ty = tangent[1]
            val nx = -ty; val ny = tx
            for (k in 1 until steps) {
                val a = PI.toFloat() * k / steps
                val ca = cos(a) * fromSign
                val sa = sin(a) * bulge
                into += floatArrayOf(
                    center[0] + (nx * ca + tx * sa) * w,
                    center[1] + (ny * ca + ty * sa) * w,
                )
            }
        }
        val out = ArrayList<FloatArray>()
        out.addAll(left)
        cap(pts.last(), tangents.last(), out, fromSign = +1f, bulge = +1f)
        out.addAll(right.asReversed())
        cap(pts.first(), tangents.first(), out, fromSign = -1f, bulge = -1f)
        return out
    }

    private data class Seg(val kind: String, val toX: Float, val toY: Float, val sweep: Float = 0f)

    /** Desktop `_v` — clip eye polygon to front hemisphere. */
    private fun clipFront(pts3: List<FloatArray>, refAtan: Float): List<Seg>? {
        val n = pts3.size
        if (n == 0) return null
        var r = -1
        for (a in 0 until n) {
            if (pts3[a][2] <= 0f && pts3[(a + 1) % n][2] > 0f) { r = a; break }
        }
        if (r < 0) {
            return if (pts3[0][2] <= 0f) null
            else pts3.map { Seg("line", it[0], it[1]) }
        }
        val s = ArrayList<Seg>()
        var oX = 1f; var oY = 0f
        var iX: Float? = null; var iY: Float? = null
        for (a in 0 until n) {
            val c = pts3[(r + a) % n]
            val u = pts3[(r + a + 1) % n]
            val l = c[2] > 0f
            val d = u[2] > 0f
            if (l && d) {
                s += Seg("line", u[0], u[1])
            } else if (l) {
                val rim = rl(c, u)
                iX = rim[0]; iY = rim[1]
                s += Seg("line", rim[0], rim[1])
            } else if (d) {
                val h = rl(c, u)
                if (iX == null) { oX = h[0]; oY = h[1] }
                else s += ol(iX, iY!!, h[0], h[1], refAtan)
                s += Seg("line", u[0], u[1])
            }
        }
        if (iX != null) s += ol(iX, iY!!, oX, oY, refAtan)
        return s
    }

    private fun rl(e: FloatArray, t: FloatArray): FloatArray {
        val n = e[2] / (e[2] - t[2])
        val r = e[0] + (t[0] - e[0]) * n
        val s = e[1] + (t[1] - e[1]) * n
        val o = hypot(r, s).coerceAtLeast(1e-6f)
        return floatArrayOf(r / o, s / o)
    }

    private fun nl(e: Float): Float {
        var t = ((e + PI.toFloat()) % (2f * PI.toFloat()) + 2f * PI.toFloat()) % (2f * PI.toFloat()) - PI.toFloat()
        return t
    }

    private fun ol(ex: Float, ey: Float, tx: Float, ty: Float, n: Float): Seg {
        val r = nl(atan2(ty, tx) - n) - nl(atan2(ey, ex) - n)
        return Seg("arc", tx, ty, r)
    }

    /**
     * Desktop `SS` — map one eye outline onto sphere via quat×J1 frame.
     * Returns He-centered 2D path (y-down Compose), or null if fully back-facing.
     */
    fun projectEye(
        eye: EyeState,
        frame: CapsuleFrame,
        reverse: Boolean,
        R: FloatArray,
        he: Float,
        localOutline: List<FloatArray>? = null,
    ): Path? {
        val outline = localOutline ?: stadiumOutline(eye)
        if (outline.isEmpty()) return null
        val i = wt(R, frame.M)
        val a = wt(R, frame.C)
        val c = wt(R, frame.T)
        val u = atan2(i.y, i.x)
        val l = cos(eye.tilt)
        val d = sin(eye.tilt)
        val h = eye.offX
        val f = eye.offY
        fun p(g: Float, y: Float): FloatArray {
            val w = y * eye.lidY
            val S = h + l * g + d * w
            val v = f - d * g + l * w
            val x = hypot(S, v)
            val E = if (x > 1e-12f) sin(x) / x else 1f
            val A = cos(x)
            return floatArrayOf(
                A * i.x + E * (S * a.x + v * c.x),
                A * i.y + E * (S * a.y + v * c.y),
                A * i.z + E * (S * a.z + v * c.z),
            )
        }
        val mapped = outline.map { p(it[0], it[1]) }.toMutableList()
        if (reverse) mapped.reverse()
        // Never tessellate clipFront rim arcs — those produced the pink/red diagonal
        // cut / dark semi-circle artifact on sphere eyes (seen in review). Keep only
        // front-hemisphere samples and close as a simple NonZero capsule.
        val front = mapped.filter { it[2] > 0.02f }
        val flat: List<FloatArray> = when {
            front.size >= 3 -> front.map { floatArrayOf(it[0], it[1]) }
            mapped.all { it[2] > -0.05f } -> mapped.map { floatArrayOf(it[0], it[1]) }
            else -> {
                // Mostly back-facing — drop eye rather than draw a rim slash.
                return null
            }
        }
        if (flat.size < 3) return null
        @Suppress("UNUSED_VARIABLE") val _u = u
        // Unit sphere XY → He-centered Compose (y-down), smooth curve (engine17).
        return SmoothPath.closed(flat.size, Path().apply { fillType = androidx.compose.ui.graphics.PathFillType.NonZero },
            x = { flat[it][0] * he }, y = { -flat[it][1] * he })
    }

    /**
     * Project a pair of eye states (Grok expression capsules) for sphere body.
     */
    fun projectPair(
        eyes: List<EyeState>,
        pitchDeg: Float,
        yawDeg: Float,
        rollDeg: Float,
        he: Float,
    ): Pair<Path, Path> {
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
        val R = xn(fk(pitch, yaw, roll))
        val left = eyes.getOrNull(0)?.let { projectEye(it, J1[0], GS[0], R, he) } ?: Path()
        val right = eyes.getOrNull(1)?.let { projectEye(it, J1[1], GS[1], R, he) } ?: Path()
        return left to right
    }

    /**
     * Project arbitrary local outlines (Generical eye paths in eye-local units)
     * through the same J1×quat seating.
     */
    fun projectLocalOutlines(
        leftLocal: List<FloatArray>,
        rightLocal: List<FloatArray>,
        lidY: Float,
        pitchDeg: Float,
        yawDeg: Float,
        rollDeg: Float,
        he: Float,
    ): Pair<Path, Path> {
        val eyeL = EyeState(0f, 0f, 0f, IU, lidY, emptyList())
        val eyeR = EyeState(0f, 0f, 0f, IU, lidY, emptyList())
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
        val R = xn(fk(pitch, yaw, roll))
        val left = projectEye(eyeL, J1[0], GS[0], R, he, leftLocal) ?: Path()
        val right = projectEye(eyeR, J1[1], GS[1], R, he, rightLocal) ?: Path()
        return left to right
    }
}
