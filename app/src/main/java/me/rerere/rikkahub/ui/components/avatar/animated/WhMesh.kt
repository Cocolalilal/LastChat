package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Desktop Grok Bot 0.68.1 Wh body mesh — line-ported primitives:
 * `bh` / `ex` / `tx` / `rx` / `vh` / `xh` / `Jb` / `zb` / `qb` / `Bb` / `Zo` / `nx` / `Sh`.
 *
 * Soft-3D paint = rotated ring-mesh faces with Xx-style Lambertian (`nt`/`Ws`),
 * NOT plate+rim twins and NOT Bb radial fakes as the body renderer.
 */
object WhMesh {

    // --- desktop constants (index.eager-platform-AatQIEPx.js) ---
    private const val FH = 0.72f          // fh
    private const val AL = 16             // al loft latitudes
    private const val CL = 96             // cl silhouette resample (desktop 160; 96 for mobile)
    private const val MR = 4              // mr extrusion bevel steps
    private const val JE = 480            // Je angular bins for rx
    private const val ZB = 8              // Zb radix for nx
    private const val KB = 1.01f
    private const val XB = 1e-5f
    private const val YB = 0.01f
    private const val IN = 180            // In Bb azimuth bins
    private const val WS = 0.18f          // Ws ambient
    private const val UX = 0.4f           // Ux ink→lit mix toward white channel
    private const val KX = 1e-9f
    private val NT = floatArrayOf(-0.50140591f, 0.60168709f, 0.62174333f) // normalize([-.5,.6,.62])
    private const val GR = (PI * 2).toFloat()

    data class V3(val x: Float, val y: Float, val z: Float)
    data class V2(val x: Float, val y: Float)

    sealed class Silhouette {
        data class Loft(val rings: List<List<V3>>) : Silhouette()
        data class Extrusion(val rings: List<List<V3>>) : Silhouette()
        data class Solid(val samples: List<V3>, val balls: List<FloatArray>, val isLoop: Boolean) : Silhouette()
    }

    sealed class Seat {
        data class Front(val faceZ: Float) : Seat()
        data class LoftSeat(val surfaceZ: (Float, Float) -> Float) : Seat()
        data class Wrap(
            val wrapR: Float,
            val wrapY0: Float,
            val wrapY1: Float,
            val eyeY: Float,
            val radiusAt: ((Float) -> Float)? = null,
            /** Desktop `puffAt` — bean inscribed-ball puff (`Ob`). */
            val puffAt: ((Float, Float) -> BallHit)? = null,
        ) : Seat()

        /** Desktop `Ob` hit — nearest inscribed ball under (x,y). */
        data class BallHit(val x: Float, val y: Float, val z: Float, val r: Float)
        data class WrapX(
            val wrapR: Float,
            val wrapX0: Float,
            val wrapX1: Float,
            val radiusAtX: (Float) -> Float,
        ) : Seat()
    }

    data class ProjectCtx(
        val rotate: (V3) -> V3,
        val project: (V3) -> V2,
        val origin: V2,
        val radius: Float,
    )

    data class LitFace(val polygon: List<V2>, val fill: Color, val depth: Float)
    data class MeshDraw(
        val faces: List<LitFace>,
        val hull: Path,
        val polygons: List<FloatArray>,
    )

    private val unitJe: Array<V2> by lazy {
        Array(JE) { i ->
            val a = i.toFloat() / JE * GR
            V2(cos(a), sin(a))
        }
    }
    private val unitWb: Array<V2> by lazy {
        Array(160) { i ->
            val a = i.toFloat() / 160 * GR
            V2(cos(a), sin(a))
        }
    }

    // --- Jb / Yo / xh ---


    /**
     * Desktop `Jb(yaw,pitch,roll)` — rotate (x,y,z) by yaw→pitch→roll.
     * Port of the exact multiply chain in eager-platform.
     */
    /** Desktop `Jb` ≡ `Yx` — yaw → pitch → roll. */
    fun jb(yaw: Float, pitch: Float, roll: Float): (V3) -> V3 {
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val cr = cos(roll); val sr = sin(roll)
        return { p ->
            val l = p.x * cy + p.z * sy
            val d = p.z * cy - p.x * sy
            val h = p.y * cp - d * sp
            val f = p.y * sp + d * cp
            V3(l * cr - h * sr, l * sr + h * cr, f)
        }
    }

    /** He-centered project: desktop `r ± s*a` with r=0, s=he. */
    fun xhHe(yaw: Float, pitch: Float, roll: Float, he: Float): ProjectCtx {
        val rot = jb(yaw, pitch, roll)
        return ProjectCtx(
            rotate = rot,
            project = { p ->
                val a = rot(p)
                V2(he * a.x, -he * a.y)
            },
            origin = V2(0f, 0f),
            radius = he,
        )
    }

    // --- zb / qb / Bb ---

    /** Desktop `zb(contour, halfDepth, bevel)` — extrusion rings. */
    fun zb(contour: List<V3>, halfDepth: Float, bevel: Float): List<List<V3>> {
        if (contour.isEmpty()) return emptyList()
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (p in contour) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val u = max(1e-4f, min((maxX - minX) * 0.5f, (maxY - minY) * 0.5f))
        fun ringAt(step: Int, sign: Float): Pair<Float, Float> {
            val e = step.toFloat() / MR * (PI.toFloat() / 2f)
            val zAbs = halfDepth - bevel + bevel * sin(e)
            val scale = 1f - (bevel / u) * (1f - cos(e))
            return sign * zAbs to scale
        }
        val specs = ArrayList<Pair<Float, Float>>(MR * 2 + 2)
        for (h in MR downTo 0) specs += ringAt(h, -1f)
        for (h in 0..MR) specs += ringAt(h, +1f)
        return specs.map { (z, scale) ->
            contour.map { p -> V3(cx + (p.x - cx) * scale, cy + (p.y - cy) * scale, z) }
        }
    }

    /** Desktop `qb(contour, depth)` — loft latitude rings. */
    fun qb(contour: List<V3>, depth: Float): Pair<List<List<V3>>, V3> {
        val r = if (contour.size > CL) {
            List(CL) { contour[floor(it.toFloat() * contour.size / CL).toInt()] }
        } else contour
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (p in r) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        val c = V3((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, 0f)
        val rings = ArrayList<List<V3>>(AL + 1)
        for (l in 0..AL) {
            val d = -PI.toFloat() / 2f + PI.toFloat() * l / AL
            val h = max(0f, cos(d)).toDouble().let { Math.pow(it, FH.toDouble()).toFloat() }
            val f = depth * sin(d)
            rings += r.map { p -> V3(c.x + (p.x - c.x) * h, c.y + (p.y - c.y) * h, f) }
        }
        return rings to c
    }

    /** Desktop `Bb({rings,center,depth})` → surfaceZ(x,y). */
    fun bb(rings: List<List<V3>>, center: V3, depth: Float): (Float, Float) -> Float {
        val mid = rings[rings.size / 2]
        val s = FloatArray(IN)
        for (i in 0 until IN) {
            val a = (i + 0.5f) / IN * PI.toFloat() * 2f
            val c = cos(a); val u = sin(a)
            var l = 0f
            for (d in mid.indices) {
                val h = mid[d]; val f = mid[(d + 1) % mid.size]
                val p = f.x - h.x; val m = f.y - h.y
                val g = c * m - u * p
                if (abs(g) < 1e-9f) continue
                val y = h.x - center.x; val w = h.y - center.y
                val S = (y * m - w * p) / g
                val v = (y * u - w * c) / g
                if (S > l && v >= 0f && v <= 1f) l = S
            }
            s[i] = l
        }
        return fun(ix: Float, ia: Float): Float {
            val c = ix - center.x; val u = ia - center.y
            val l = hypot(c, u)
            if (l < 1e-8f) return 0f
            val h = ((atan2(u, c) + PI.toFloat() * 2f) % (PI.toFloat() * 2f)) / (PI.toFloat() * 2f) * IN
            val f = floor(h).toInt() % IN
            val p = h - floor(h)
            val m = s[f]; val g = s[(f + 1) % IN]
            val o = min(1f, l / max(1e-8f, m * (1f - p) + g * p))
            return depth * sqrt(max(0f, 1f - Math.pow(o.toDouble(), 2.0 / FH).toFloat()))
        }
    }

    // --- nx / Zo / vh / tx / rx / ex / bh ---

    /** Desktop `nx` — bucket sort by x for hull prep. */
    private fun nx(points: List<V2>): List<V2> {
        if (points.isEmpty()) return points
        var t = Float.POSITIVE_INFINITY; var n = Float.NEGATIVE_INFINITY
        for (p in points) { if (p.x < t) t = p.x; if (p.x > n) n = p.x }
        val r = ceil(points.size.toFloat() / ZB).toInt().coerceAtLeast(1)
        val s = if (n > t) r / (n - t) else 0f
        fun o(u: Float) = min(r - 1, floor((u - t) * s).toInt())
        val i = IntArray(r + 1)
        for (p in points) i[o(p.x) + 1]++
        for (u in 0 until r) i[u + 1] += i[u]
        val a = i.copyOf(r)
        val c = arrayOfNulls<V2>(points.size)
        for (p in points) c[a[o(p.x)]++] = p
        val out = ArrayList<V2>(points.size)
        for (u in 0 until r) {
            val bucket = ArrayList<V2>()
            for (l in i[u] until i[u + 1]) c[l]?.let { bucket += it }
            bucket.sortWith(compareBy({ it.x }, { it.y }))
            out.addAll(bucket)
        }
        return out
    }

    /** Desktop `Zo` — monotone chain convex hull. */
    fun zo(points: List<V2>): List<V2> {
        val t = nx(points.filter { it.x.isFinite() && it.y.isFinite() })
        if (t.size < 3) return t
        fun cross(o: V2, i: V2, a: V2) = (i.x - o.x) * (a.y - o.y) - (i.y - o.y) * (a.x - o.x)
        val lower = ArrayList<V2>()
        for (o in t) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], o) <= 0f) {
                lower.removeAt(lower.lastIndex)
            }
            lower += o
        }
        val upper = ArrayList<V2>()
        for (idx in t.indices.reversed()) {
            val i = t[idx]
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], i) <= 0f) {
                upper.removeAt(upper.lastIndex)
            }
            upper += i
        }
        if (lower.isNotEmpty()) lower.removeAt(lower.lastIndex)
        if (upper.isNotEmpty()) upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    /** Desktop `vh` — polygon → flat float array if ≥3 pts. */
    fun vh(poly: List<V2>): List<FloatArray> {
        if (poly.size < 2) return emptyList()
        val first = poly[0]
        if (!first.x.isFinite() || !first.y.isFinite()) return emptyList()
        val n = ArrayList<Float>(poly.size * 2)
        for (r in poly) {
            if (r.x.isFinite() && r.y.isFinite()) {
                n += r.x; n += r.y
            }
        }
        return if (n.size >= 6) listOf(n.toFloatArray()) else emptyList()
    }

    /** Desktop `tx` — solid silhouette projection (+ optional balls). */
    fun tx(solid: Silhouette.Solid, ctx: ProjectCtx): List<V2> {
        val s = ArrayList<V2>()
        for (c in solid.samples) {
            val u = ctx.project(c)
            if (u.x.isFinite() && u.y.isFinite()) s += u
        }
        if (solid.isLoop) return s
        if (solid.balls.isEmpty()) return zo(s)
        val o = solid.balls.mapNotNull { b ->
            val c = ctx.project(V3(b[0], b[1], b[2]))
            val r = b[3] * ctx.radius
            if (c.x.isFinite() && c.y.isFinite() && r > 1e-6f) Triple(c.x, c.y, r) else null
        }
        var ix = ctx.origin.x; var iy = ctx.origin.y
        if (o.isNotEmpty()) {
            ix = 0f; iy = 0f
            for (c in o) { ix += c.first; iy += c.second }
            ix /= o.size; iy /= o.size
        }
        for (dir in unitWb) {
            var l = 0f
            for (d in o) {
                val h = d.first - ix; val f = d.second - iy
                val p = dir.x * h + dir.y * f
                val m = p * p - (h * h + f * f) + d.third * d.third
                if (m <= 0f) continue
                val g = p + sqrt(m)
                if (g > l) l = g
            }
            if (l > 0f) s += V2(ix + dir.x * l, iy + dir.y * l)
        }
        if (solid.samples.isNotEmpty()) return zo(s)
        if (s.size >= 3) return s
        for (c in o) {
            for (u in 0 until 48) {
                val l = u / 48f * GR
                s += V2(c.first + c.third * cos(l), c.second + c.third * sin(l))
            }
        }
        return if (s.size >= 3) zo(s) else s
    }

    /** Desktop `rx` — loft ring angular silhouette hull. */
    fun rx(rings: List<List<V3>>, ctx: ProjectCtx): List<V2> {
        if (rings.isEmpty()) return emptyList()
        val r = rings.map { ring -> ring.map { ctx.project(it) } }
        fun centroid(g: List<V2>): V2 {
            var y = 0f; var w = 0f
            for (s in g) { y += s.x; w += s.y }
            return V2(y / g.size, w / g.size)
        }
        val o = centroid(r.first()); val i = centroid(r.last())
        if (hypot(i.x - o.x, i.y - o.y) < 1e-4f) {
            // collapsed loft — largest area ring
            var best = r[0]; var bestArea = 0f
            for (w in r) {
                var S = 0f
                for (v in w.indices) {
                    val x = w[v]; val E = w[(v + 1) % w.size]
                    S += x.x * E.y - E.x * x.y
                }
                if (abs(S) > bestArea) { best = w; bestArea = abs(S) }
            }
            return best
        }
        val nOrigin = ctx.origin
        val a = r.map { g -> g.map { y ->
            val dx = y.x - nOrigin.x; val dy = y.y - nOrigin.y
            dx * dx + dy * dy
        } }
        val c = a.map { it.maxOrNull() ?: 0f }
        val u = XB * (c.maxOrNull() ?: 0f) + YB
        fun lReject(v: Float, f: Float) = run {
            val w = (f - u) / KB
            w > 0f && v < w * w
        }
        val d = r.map { g ->
            g.map { y ->
                val w = (atan2(y.y - nOrigin.y, y.x - nOrigin.x) + GR) % GR
                floor(w / GR * JE).toInt() % JE
            }
        }
        val h = FloatArray(JE)
        var f = 0f
        fun p(g: V2, y: V2, w: Int, S: Int, v: Float) {
            if (lReject(v, f)) return
            var x = S - w
            if (x > JE / 2) x -= JE else if (x < -JE / 2) x += JE
            val E = if (x < 0) -1 else 1
            val A = abs(x)
            var j = Float.POSITIVE_INFINITY
            for (I in -1..A + 1) {
                val `$` = (w + E * I + JE) % JE
                if (h[`$`] < j) j = h[`$`]
            }
            if (lReject(v, j)) return
            val F = y.x - g.x; val O = y.y - g.y
            val L = g.x - nOrigin.x; val T = g.y - nOrigin.y
            for (I in -1..A + 1) {
                val `$` = (w + E * I + JE) % JE
                val R = unitJe[`$`].x; val U = unitJe[`$`].y
                val W = R * O - U * F
                if (abs(W) < 1e-9f) continue
                val G = (L * O - T * F) / W
                val J = (L * U - T * R) / W
                if (G > h[`$`] && J >= 0f && J <= 1f) h[`$`] = G
            }
        }
        val m = c.indices.sortedByDescending { c[it] }
        for (g in m) {
            val y = r[g]
            for (w in y.indices) {
                val S = (w + 1) % y.size
                p(y[w], y[S], d[g][w], d[g][S], max(a[g][w], a[g][S]))
            }
            f = h.minOrNull() ?: 0f
        }
        for (g in 0 until r.size - 1) {
            val y = r[g]; val w = r[g + 1]
            for (S in 0 until min(y.size, w.size)) {
                p(y[S], w[S], d[g][S], d[g + 1][S], max(a[g][S], a[g + 1][S]))
            }
        }
        val out = ArrayList<V2>()
        for (w in unitJe.indices) {
            if (h[w] > 0f) {
                val g = unitJe[w]
                out += V2(nOrigin.x + g.x * h[w], nOrigin.y + g.y * h[w])
            }
        }
        return out
    }

    /**
     * Desktop `ex(silhouette, xhCtx)` — returns list of polygons (caps + side quads).
     */
    fun ex(sil: Silhouette, ctx: ProjectCtx): List<List<V2>> {
        return when (sil) {
            is Silhouette.Loft -> listOf(rx(sil.rings, ctx))
            is Silhouette.Solid -> listOf(tx(sil, ctx))
            is Silhouette.Extrusion -> {
                val n = sil.rings.map { ring -> ring.map { ctx.project(it) } }
                if (n.isEmpty()) return emptyList()
                val front = n.first()
                val back = n.last()
                val o = ArrayList<List<V2>>()
                o += back
                o += front
                for (i in 1 until n.size) {
                    val a = n[i - 1]; val c = n[i]
                    val len = min(a.size, c.size)
                    if (len == 0) continue
                    for (u in 0 until len) {
                        val l = (u + 1) % len
                        o += listOf(a[u], a[l], c[l], c[u])
                    }
                }
                o
            }
        }
    }

    /** Desktop `bh` = ex(...).flatMap(vh). */
    fun bh(sil: Silhouette, ctx: ProjectCtx): List<FloatArray> =
        ex(sil, ctx).flatMap { vh(it) }

    // --- path sampling / silhouette build ---

    fun samplePathHeCentered(path: Path, he: Float, samples: Int = CL): List<V3> {
        val am = android.graphics.PathMeasure(path.asAndroidPath(), false)
        val length = am.length
        if (length <= 1f) return emptyList()
        val pos = FloatArray(2)
        val out = ArrayList<V3>(samples)
        for (i in 0 until samples) {
            am.getPosTan(length * i / samples.toFloat(), pos, null)
            // He-centered Compose (y down) → desktop normalized (y up)
            out += V3(pos[0] / he, -pos[1] / he, 0f)
        }
        return out
    }

    fun buildSilhouette(path: Path, solid: SolidSpec, he: Float): Silhouette {
        val contour = samplePathHeCentered(path, he)
        if (contour.isEmpty()) return Silhouette.Solid(emptyList(), emptyList(), false)
        return when (solid.kind) {
            "loft" -> Silhouette.Loft(qb(contour, solid.depth).first)
            "extrusion", "roundedSlab" ->
                Silhouette.Extrusion(zb(contour, solid.halfDepth, solid.bevel))
            "pill" -> {
                val pairs = contour.map { floatArrayOf(it.x, it.y) }
                val balls = WhMx.wx(pairs, solid.axis).map { it.asArray() }
                Silhouette.Solid(emptyList(), balls, isLoop = false)
            }
            "inscribedBalls" -> {
                val balls = WhMx.inscribedBalls(contour.map { V2(it.x, it.y) }).map { it.asArray() }
                Silhouette.Solid(emptyList(), balls, isLoop = false)
            }
            "crystal" -> Silhouette.Solid(WhMx.crystalPoints(), emptyList(), isLoop = false)
            "revolve" -> {
                // Hull via projected revolve mesh verts
                val mesh = WhMx.yx(contour.map { V2(it.x, it.y) })
                Silhouette.Solid(mesh.vertices, emptyList(), isLoop = false)
            }
            "sphere" -> Silhouette.Solid(emptyList(), emptyList(), isLoop = false)
            else -> Silhouette.Loft(qb(contour, 0.85f).first)
        }
    }

    /**
     * Exact `_b` seat factories — `mh` / `dl` / `pl` tablet balls, not path-bounds rebuilds.
     */
    fun buildSeat(shape: GrokShape, he: Float, path: Path): Seat {
        val solid = shape.solid
        val contour = samplePathHeCentered(path, he, 96)
        val engine = WhEyeSeat.engineKey(shape.id)
        return when (solid.kind) {
            "loft" -> {
                val (rings, c) = qb(contour, solid.depth)
                Seat.LoftSeat(bb(rings, c, solid.depth))
            }
            "extrusion", "roundedSlab" -> Seat.Front(solid.halfDepth)
            "sphere" -> WhSphere.eyeSeat()
            "crystal" -> Seat.Front(0.64f * (sqrt(3f) / 2f))
            "pill" -> {
                // Desktop tablet: pl("tablet","x") + wrapX; capsule: pl y + wrap
                val halfX = contourBoundsHalf(contour).first
                val halfY = contourBoundsHalf(contour).second
                if (solid.axis == 0) {
                    // Exact `_b.tablet` wrapX
                    val s = halfX
                    val o = halfY
                    val i = max(0f, s - o)
                    Seat.WrapX(
                        wrapR = o,
                        wrapX0 = -s + 0.02f,
                        wrapX1 = s - 0.02f,
                        radiusAtX = { a ->
                            val c = abs(a) - i
                            if (c <= 0f) o else sqrt(max(0f, o * o - c * c))
                        },
                    )
                } else {
                    // `_b.capsule`
                    val balls = WhMx.pl(halfX, halfY, "y")
                    val wrapY0 = shapeY0(shape, he)
                    val wrapY1 = shapeY1(shape, he)
                    val hx = balls.maxOf { abs(it.cx) + it.r }
                    val hz = balls.maxOf { abs(it.cz) + it.r }
                    Seat.Wrap(
                        wrapR = max(0.35f, (hx + hz) * 0.5f),
                        wrapY0 = wrapY0,
                        wrapY1 = wrapY1,
                        eyeY = (wrapY0 + wrapY1) * 0.5f,
                    )
                }
            }
            "inscribedBalls" -> {
                // `_b.bean` = mh("bean", jb balls) with puffAt
                mhSeat(shape, he, contour, withPuff = true)
            }
            "revolve" -> {
                // `_b` An / teardrop / wedge style wrap via mh without puff
                mhSeat(shape, he, contour, withPuff = false)
            }
            else -> Seat.Front(0.36f)
        }.also { @Suppress("UNUSED_VARIABLE") val _e = engine }
    }

    private fun contourBoundsHalf(contour: List<V3>): Pair<Float, Float> {
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (p in contour) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        return (maxX - minX) * 0.5f to (maxY - minY) * 0.5f
    }

    private fun shapeY0(shape: GrokShape, he: Float): Float {
        val bottom = shape.bottom ?: (2f * he)
        return -(bottom - he) / he
    }

    private fun shapeY1(shape: GrokShape, he: Float): Float {
        val top = shape.top ?: 0f
        return -(top - he) / he
    }

    /**
     * Desktop `mh(e,t)` — wrap seat with optional `puffAt` from inscribed balls `t`.
     * `radiusAt` = `kh` half-width at y (from contour spans).
     */
    private fun mhSeat(
        shape: GrokShape,
        he: Float,
        contour: List<V3>,
        withPuff: Boolean,
    ): Seat {
        val wrapY0 = shapeY0(shape, he)
        val wrapY1 = shapeY1(shape, he)
        val belt = ((shape.beltRadius ?: he) / he).coerceAtLeast(0.2f)
        val radiusAt: (Float) -> Float = { yNorm ->
            // Desktop Hi: half chord width at SVG y
            val ySvg = he - yNorm * he
            var best = belt
            var minD = Float.POSITIVE_INFINITY
            // Scan contour pairs for span at y
            for (i in contour.indices) {
                val a = contour[i]
                val b = contour[(i + 1) % contour.size]
                // Convert to svg-ish y: svgY = he - y*he → y = (he-svgY)/he; we already have y-up norm
                val ya = a.y; val yb = b.y
                if ((ya <= yNorm && yb >= yNorm) || (yb <= yNorm && ya >= yNorm)) {
                    if (abs(yb - ya) < 1e-9f) continue
                    val t = (yNorm - ya) / (yb - ya)
                    val x = a.x + (b.x - a.x) * t
                    best = max(best, abs(x))
                    minD = 0f
                } else {
                    val d = min(abs(ya - yNorm), abs(yb - yNorm))
                    if (d < minD) {
                        minD = d
                        best = max(abs(a.x), abs(b.x)).coerceAtLeast(0.12f)
                    }
                }
            }
            best.coerceAtLeast(0.02f)
        }
        val eyeY = 0f.coerceIn(wrapY0 + 0.02f, wrapY1 - 0.02f)
        val puff: ((Float, Float) -> Seat.BallHit)? = if (withPuff) {
            val balls = WhMx.inscribedBalls(contour.map { V2(it.x, it.y) })
            if (balls.isEmpty()) null
            else { x, y -> ob(balls, x, y) }
        } else null
        return Seat.Wrap(
            wrapR = belt,
            wrapY0 = wrapY0,
            wrapY1 = wrapY1,
            eyeY = eyeY,
            radiusAt = radiusAt,
            puffAt = puff,
        )
    }

    /** Desktop `Ob(balls, x, y)` — nearest inscribed ball under query. */
    fun ob(balls: List<WhMx.Ball>, x: Float, y: Float): Seat.BallHit {
        var r = balls[0]
        var s = Float.NEGATIVE_INFINITY
        var o = Float.POSITIVE_INFINITY
        var i = false
        for (a in balls) {
            val c = hypot(x - a.cx, y - a.cy)
            if (c < a.r) {
                val u = a.cz + sqrt(a.r * a.r - c * c)
                if (!i || u > s) {
                    i = true; s = u; r = a
                }
            } else if (!i && c - a.r < o) {
                o = c - a.r; r = a
            }
        }
        return Seat.BallHit(r.cx, r.cy, r.cz, r.r)
    }

    // --- sx / ox / ix / ax / cx (eye seat branches) ---

    data class EyeLoops(val loops: List<List<V2>>, val shiftY: Float)

    fun sx(seat: Seat, eyes: EyeLoops, ctx: ProjectCtx): List<List<V2>> =
        when (seat) {
            is Seat.Front -> ox(seat.faceZ, eyes, ctx)
            is Seat.LoftSeat -> ix(seat.surfaceZ, eyes, ctx)
            is Seat.Wrap -> ax(seat, eyes, ctx)
            is Seat.WrapX -> cx(seat, eyes, ctx)
        }

    /** Desktop `ox` — front face plane. */
    fun ox(faceZ: Float, eyes: EyeLoops, ctx: ProjectCtx): List<List<V2>> {
        if (ctx.rotate(V3(0f, 0f, 1f)).z <= 0.03f) return emptyList()
        val o = sin(eyes.shiftY)
        return eyes.loops.filter { it.size >= 3 }.map { loop ->
            loop.map { a -> ctx.project(V3(a.x, a.y - o, faceZ)) }
        }
    }

    /** Desktop `ix` — loft surface with tangent plane. */
    fun ix(surfaceZ: (Float, Float) -> Float, eyes: EyeLoops, ctx: ProjectCtx): List<List<V2>> {
        if (ctx.rotate(V3(0f, 0f, 1f)).z < 0.05f) return emptyList()
        val i = sin(eyes.shiftY)
        val a = ArrayList<List<V2>>()
        for (c in eyes.loops) {
            if (c.size < 3) continue
            val u = c.map { y -> V2(y.x, y.y - i) }
            var l = 0f; var d = 0f
            for (y in u) { l += y.x; d += y.y }
            l /= u.size; d /= u.size
            val h = 0.004f
            val f = (surfaceZ(l + h, d) - surfaceZ(l - h, d)) / (2f * h)
            val p = (surfaceZ(l, d + h) - surfaceZ(l, d - h)) / (2f * h)
            val m = hypot(hypot(f, p), 1f)
            if (ctx.rotate(V3(-f / m, -p / m, 1f / m)).z < 0.12f) continue
            val g = surfaceZ(l, d)
            a += u.map { y -> ctx.project(V3(y.x, y.y, g + (y.x - l) * f + (y.y - d) * p)) }
        }
        return a
    }

    /** Desktop `ax` — wrap around Y cylinder with full `puffAt`/`Ob` bean branch. */
    fun ax(seat: Seat.Wrap, eyes: EyeLoops, ctx: ProjectCtx): List<List<V2>> {
        val o = seat.wrapR
        val i = seat.wrapY0
        val a = seat.wrapY1
        val c = seat.radiusAt
        val u = seat.puffAt
        val l = if (c != null) 0.02f else 0.06f
        val d = min(a - l, max(i + l, seat.eyeY - eyes.shiftY))
        fun h(v: Float) = min(a, max(i, v))
        fun mapCyl(v: List<V2>): List<V2> {
            val x = ArrayList<V2>()
            for (E in v) {
                val A = h(d + E.y)
                val j = c?.invoke(A) ?: o
                val F = max(-2.6f, min(2.6f, E.x / max(0.12f, j)))
                var O = 0f
                if (c != null) {
                    O = -(c(min(a, A + 0.012f)) - c(max(i, A - 0.012f))) / (2f * 0.012f)
                }
                if (ctx.rotate(V3(sin(F), O, cos(F))).z >= 0.05f) {
                    x += ctx.project(V3(j * sin(F), A, j * cos(F)))
                }
            }
            return x
        }
        fun mapPuff(v: List<V2>): List<V2> {
            if (u == null || v.isEmpty()) return emptyList()
            var x = 0f; var E = 0f
            for (O in v) { x += O.x; E += O.y }
            val A = u(x / v.size, h(d + E / v.size))
            val j = ctx.rotate(V3(0f, 0f, 1f)).z
            val F = ArrayList<V2>()
            for (O in v) {
                val L = O.x - A.x
                val T = h(d + O.y) - A.y
                val I = hypot(L, T)
                var dollar = min(I / A.r, PI.toFloat() - 0.02f)
                val R = if (I > 1e-9f) L / I else 1f
                val U = if (I > 1e-9f) T / I else 0f
                val W = ctx.rotate(V3(R, U, 0f)).z
                fun G(K: Float) = W * sin(K) + j * cos(K)
                if (G(dollar) < 0.06f && j > 0.06f) {
                    var K = 0f; var ae = dollar
                    repeat(20) {
                        val te = (K + ae) / 2f
                        if (G(te) >= 0.06f) K = te else ae = te
                    }
                    dollar = K
                }
                val J = A.r * sin(dollar)
                val ye = V3(R * sin(dollar), U * sin(dollar), cos(dollar))
                if (ctx.rotate(ye).z >= 0.05f) {
                    F += ctx.project(V3(A.x + R * J, A.y + U * J, A.z + A.r * cos(dollar)))
                }
            }
            return F
        }
        var m = Float.POSITIVE_INFINITY; var g = Float.NEGATIVE_INFINITY
        for (v in eyes.loops) for (x in v) {
            if (x.y < m) m = x.y; if (x.y > g) g = x.y
        }
        var y = 0f
        if (g - m <= a - i) {
            if (d + m < i) y = i - (d + m)
            else if (d + g > a) y = a - (d + g)
        }
        val w = if (y == 0f) eyes.loops else eyes.loops.map { v -> v.map { x -> V2(x.x, x.y + y) } }
        val S: (List<V2>) -> List<V2> = if (u != null) ::mapPuff else ::mapCyl
        return w.map(S).filter { it.size > 2 }
    }

    /** Desktop `cx` — wrapX (tablet). */
    fun cx(seat: Seat.WrapX, eyes: EyeLoops, ctx: ProjectCtx): List<List<V2>> {
        val o = seat.wrapR
        val i = seat.wrapX0
        val a = seat.wrapX1
        val c = seat.radiusAtX
        val u = max(-2.4f, min(2.4f, -eyes.shiftY / o))
        var l = Float.POSITIVE_INFINITY; var d = Float.NEGATIVE_INFINITY
        for (p in eyes.loops) for (m in p) {
            if (m.x < l) l = m.x; if (m.x > d) d = m.x
        }
        var h = 0f
        if (d - l <= a - i) {
            if (l < i) h = i - l else if (d > a) h = a - d
        }
        val loops = if (h == 0f) eyes.loops else eyes.loops.map { p -> p.map { m -> V2(m.x + h, m.y) } }
        return loops.map { p ->
            val m = ArrayList<V2>()
            for (g in p) {
                val y = min(a, max(i, g.x))
                val w = c(y)
                val S = u + max(-2.6f, min(2.6f, g.y / max(0.12f, o)))
                val v = 0.012f
                val x = -(c(min(a, y + v)) - c(max(i, y - v))) / (2f * v)
                if (ctx.rotate(V3(x, sin(S), cos(S))).z >= 0.05f) {
                    m += ctx.project(V3(y, w * sin(S), w * cos(S)))
                }
            }
            m
        }.filter { it.size > 2 }
    }

    // --- lit mesh faces (Xx/Zx-style on zb/qb rings) ---

    private fun shadeInk(ink: Color, normal: V3): Color {
        val d = hypot(hypot(normal.x, normal.y), normal.z)
        if (d < 1e-8f) return ink
        val nx = normal.x / d; val ny = normal.y / d; val nz = normal.z / d
        val lambert = max(0f, nx * NT[0] + ny * NT[1] + nz * NT[2])
        val p = WS + (1f - WS) * lambert
        // Mix ink toward lit: channel = ink + (1-ink)*Ux then *p  (desktop on ink 0..1 channels)
        fun ch(v: Float): Float {
            val lifted = v + (1f - v) * UX
            return (lifted * p).coerceIn(0f, 1f)
        }
        return Color(ch(ink.red), ch(ink.green), ch(ink.blue), ink.alpha)
    }

    private fun faceNormal(verts: List<V3>, corners: IntArray): V3 {
        // Desktop Th Newell
        var n = 0f; var r = 0f; var s = 0f
        for (o in corners.indices) {
            val i = verts[corners[o]]
            val a = verts[corners[(o + 1) % corners.size]]
            n += (i.y - a.y) * (i.z + a.z)
            r += (i.z - a.z) * (i.x + a.x)
            s += (i.x - a.x) * (i.y + a.y)
        }
        return V3(n, r, s)
    }

    /**
     * Build lit faces from ring mesh (extrusion/loft) — actual triangulation paint.
     */
    fun litFacesFromRings(
        rings: List<List<V3>>,
        rotate: (V3) -> V3,
        ink: Color,
        closedCaps: Boolean,
        he: Float,
    ): List<LitFace> {
        if (rings.size < 2) return emptyList()
        val n = rings[0].size
        if (n < 3) return emptyList()
        val stacked = ArrayList<V3>(rings.size * n)
        for (ring in rings) {
            require(ring.size == n) { "ring size mismatch" }
            for (p in ring) stacked += rotate(p)
        }
        fun vid(ring: Int, col: Int) = ring * n + (col % n)
        val out = ArrayList<LitFace>()
        fun emitDirect(cornerIds: IntArray) {
            val nn = faceNormal(stacked, cornerIds)
            val d = hypot(hypot(nn.x, nn.y), nn.z)
            if (d == 0f || nn.z <= KX * d) return
            val fill = shadeInk(ink, nn)
            var depth = 0f
            val poly = ArrayList<V2>(cornerIds.size)
            for (id in cornerIds) {
                val v = stacked[id]
                // He-centered Compose (y down): desktop project r+s*a.x, r-s*a.y with r=0,s=he
                poly += V2(he * v.x, -he * v.y)
                depth += v.z
            }
            depth /= cornerIds.size
            out += LitFace(poly, fill, depth)
        }
        if (closedCaps) {
            emitDirect(IntArray(n) { vid(0, n - 1 - it) })
        }
        for (i in 1 until rings.size) {
            for (a in 0 until n) {
                emitDirect(intArrayOf(vid(i - 1, a), vid(i - 1, a + 1), vid(i, a + 1), vid(i, a)))
            }
        }
        if (closedCaps) {
            emitDirect(IntArray(n) { vid(rings.size - 1, it) })
        }
        return out.sortedBy { it.depth }
    }

    /**
     * Full mesh draw packet for a body path + solid + euler.
     * Face coords are He-centered (multiplied by he).
     */
    fun buildMeshDraw(
        path: Path,
        solid: SolidSpec,
        yaw: Float,
        pitch: Float,
        roll: Float,
        he: Float,
        ink: Color,
        shapeId: String = "",
    ): MeshDraw {
        return WhMx.buildCached(shapeId, path, solid, yaw, pitch, roll, he, ink)
    }

    /**
     * Desktop `uv`/`Px` multipolygon boolean union — NOT largest-area hull.
     */
    fun pathFromPolygons(polys: List<FloatArray>): Path? {
        if (polys.isEmpty()) return null
        return WhBooleanUnion.uv(polys)
    }

    fun DrawScope.drawLitMesh(faces: List<LitFace>) {
        for (f in faces) {
            if (f.polygon.size < 3) continue
            val p = Path().apply {
                moveTo(f.polygon[0].x, f.polygon[0].y)
                for (i in 1 until f.polygon.size) lineTo(f.polygon[i].x, f.polygon[i].y)
                close()
            }
            drawPath(p, f.fill)
        }
    }

    /** Qb-equivalent: seat eye loops → He-centered paths. */
    fun seatEyePolygons(
        seat: Seat,
        loopsNorm: List<List<V2>>,
        shiftY: Float,
        yaw: Float,
        pitch: Float,
        roll: Float,
        he: Float,
    ): List<Path> {
        val ctx = xhHe(yaw, pitch, roll, he)
        val seated = sx(seat, EyeLoops(loopsNorm, shiftY), ctx)
        return seated.mapNotNull { loop ->
            val flat = vh(loop).firstOrNull() ?: return@mapNotNull null
            Path().apply {
                moveTo(flat[0], flat[1])
                for (i in 1 until flat.size / 2) lineTo(flat[i * 2], flat[i * 2 + 1])
                close()
            }
        }
    }
}
