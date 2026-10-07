package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import java.util.LinkedHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Desktop Grok Bot 0.68.1 `mx` / `px` mesh assets + `Qx` ball paint + `cv` LRU cache.
 *
 * - inscribedBalls → `vx`/`jb` ball soup (not qb loft)
 * - crystal → `bx`/`Nb` + `Mh`/`Jw` convex hull mesh
 * - revolve → `yx` true lathe mesh
 * - pill → `wx`/`pl` ball chain
 * - roundedSlab → `kx`/`Sx` point cloud → Mh
 * - loft/extrusion → ring meshes via existing zb/qb (`xx`/`gx` equivalent)
 */
object WhMx {

    private const val GL = 64 // gl revolve azimuth
    private const val YL = 96 // yl Sx rays
    private const val WL = 16 // wl loft latitudes
    private const val BL = 33 // bl pill balls
    private const val IL = 33 // il pl balls
    private const val CB = 40 // Cb inscribed grid
    private const val HX = 40 // hx vx grid
    private const val KL = 4 // kl slab bevel
    private const val JO = 4 // Jo extrusion bevel
    private const val DX = 0.72f // dx loft power
    private const val XL = 1e-9f
    private const val VL = 1e-9f
    private const val FX = 1e-15f
    private const val JR = 6 // Jr Qx polar
    private const val GS = 24 // gs Qx azimuth
    private const val VI = 0.004f // Vi Qx epsilon
    private const val UH = 1e-10f
    private const val QW = 1L shl 26
    private const val B = 114.2705f // desktop mark radius
    private const val GR = (PI * 2).toFloat()
    private const val RV = 16_000_000 // rv cv budget
    private val NT = floatArrayOf(-0.50140591f, 0.60168709f, 0.62174333f)
    private const val WS = 0.18f
    private const val UX = 0.4f
    private const val KX = 1e-9f

    data class Ball(val cx: Float, val cy: Float, val cz: Float, val r: Float) {
        fun asArray(): FloatArray = floatArrayOf(cx, cy, cz, r)
        val center: WhMesh.V3 get() = WhMesh.V3(cx, cy, cz)
    }

    data class MeshFace(val corners: IntArray, val isBounding: Boolean)

    data class Mesh(
        val vertices: List<WhMesh.V3>,
        val faces: List<MeshFace>,
    )

    sealed class Asset {
        data class Rings(val rings: List<List<WhMesh.V3>>, val closedCaps: Boolean) : Asset()
        data class MeshAsset(val mesh: Mesh) : Asset()
        data class Balls(val balls: List<Ball>) : Asset()
    }

    // --- cv LRU (desktop Rn / ri / rv) ---

    private data class CacheKey(
        val shapeId: String,
        val kind: String,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
        val he: Int,
        val depth: Float,
        val halfDepth: Float,
        val bevel: Float,
        val axis: Int,
    )

    private var cacheBytes = 0
    private val meshCache = object : LinkedHashMap<CacheKey, WhMesh.MeshDraw>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, WhMesh.MeshDraw>?): Boolean {
            // Size-cap; byte budget enforced in putTracked
            return size > 48
        }
    }

    private fun estimateBytes(d: WhMesh.MeshDraw): Int =
        d.faces.sumOf { 24 + it.polygon.size * 8 } + d.polygons.sumOf { it.size * 4 } + 64

    private fun putTracked(key: CacheKey, value: WhMesh.MeshDraw): WhMesh.MeshDraw {
        val prev = meshCache.put(key, value)
        if (prev != null) cacheBytes -= estimateBytes(prev)
        cacheBytes += estimateBytes(value)
        while (cacheBytes > RV && meshCache.isNotEmpty()) {
            val it = meshCache.entries.iterator()
            if (!it.hasNext()) break
            val e = it.next()
            cacheBytes -= estimateBytes(e.value)
            it.remove()
        }
        return value
    }

    fun clearCache() {
        synchronized(meshCache) {
            meshCache.clear()
            cacheBytes = 0
        }
    }

    // --- vector helpers for Jw ---

    private fun mt(e: FloatArray, t: FloatArray) =
        floatArrayOf(e[0] - t[0], e[1] - t[1], e[2] - t[2])

    private fun zi(e: FloatArray, t: FloatArray) = floatArrayOf(
        e[1] * t[2] - e[2] * t[1],
        e[2] * t[0] - e[0] * t[2],
        e[0] * t[1] - e[1] * t[0],
    )

    private fun hs(e: FloatArray, t: FloatArray) = e[0] * t[0] + e[1] * t[1] + e[2] * t[2]
    private fun di(e: FloatArray) = hypot(hypot(e[0], e[1]), e[2])
    private fun ju(e: Int, t: Int) = e.toLong() * QW + t

    private data class Plane(val corners: IntArray, val normal: FloatArray, val offset: Float)

    private fun uo(verts: List<FloatArray>, corners: IntArray): Plane {
        val n0 = verts[corners[0]]
        val r = zi(mt(verts[corners[1]], n0), mt(verts[corners[2]], n0))
        val s = di(r).coerceAtLeast(1e-12f)
        val o = floatArrayOf(r[0] / s, r[1] / s, r[2] / s)
        return Plane(corners, o, hs(o, n0))
    }

    private fun qu(p: Plane, t: FloatArray) = hs(p.normal, t) - p.offset

    private fun tb(pts: List<FloatArray>): FloatArray {
        val t = pts.size.toFloat()
        return floatArrayOf(
            pts.sumOf { it[0].toDouble() }.toFloat() / t,
            pts.sumOf { it[1].toDouble() }.toFloat() / t,
            pts.sumOf { it[2].toDouble() }.toFloat() / t,
        )
    }

    private fun ko(e: List<FloatArray>, score: (FloatArray) -> Float): Int {
        var n = 0; var r = Float.NEGATIVE_INFINITY
        e.forEachIndexed { o, s ->
            val i = score(s)
            if (i > r) { r = i; n = o }
        }
        return n
    }

    private fun eb(e: List<FloatArray>): IntArray? {
        if (e.size < 4) return null
        var t = 0
        e.forEachIndexed { a, i -> if (i[0] < e[t][0]) t = a }
        val n = ko(e) { di(mt(it, e[t])) }
        val r = ko(e) { di(zi(mt(e[n], e[t]), mt(it, e[t]))) }
        val s = zi(mt(e[n], e[t]), mt(e[r], e[t]))
        val o = ko(e) { abs(hs(s, mt(it, e[t]))) }
        if (abs(hs(s, mt(e[o], e[t]))) < UH) return null
        return intArrayOf(t, n, r, o)
    }

    /** Desktop `Jw` — incremental 3D convex hull faces. */
    fun jw(points: List<WhMesh.V3>): List<IntArray> {
        val e = points.map { floatArrayOf(it.x, it.y, it.z) }
        val t = eb(e) ?: return emptyList()
        val n = tb(t.map { e[it] })
        var a: MutableList<Plane> = mutableListOf(
            intArrayOf(t[0], t[1], t[2]),
            intArrayOf(t[0], t[1], t[3]),
            intArrayOf(t[0], t[2], t[3]),
            intArrayOf(t[1], t[2], t[3]),
        ).map { c ->
            val d = uo(e, c)
            if (qu(d, n) > 0f) uo(e, intArrayOf(c[0], c[2], c[1])) else d
        }.toMutableList()

        e.forEachIndexed { u, c ->
            if (t.contains(u)) return@forEachIndexed
            val l = a.map { qu(it, c) > UH }
            if (!l.contains(true)) return@forEachIndexed
            val d = HashSet<Long>()
            a.forEachIndexed { p, f ->
                if (!l[p]) {
                    for (m in 0 until 3) d.add(ju(f.corners[m], f.corners[(m + 1) % 3]))
                }
            }
            val h = ArrayList<Plane>()
            a.forEachIndexed { p, f ->
                if (!l[p]) {
                    h += f
                    return@forEachIndexed
                }
                for (m in 0 until 3) {
                    val g = f.corners[m]
                    val y = f.corners[(m + 1) % 3]
                    if (d.contains(ju(y, g))) h += uo(e, intArrayOf(g, y, u))
                }
            }
            a = h
        }
        return a.map { it.corners.copyOf() }
    }

    /** Desktop `Mh`. */
    fun mh(points: List<WhMesh.V3>): Mesh {
        val faces = jw(points).map { MeshFace(it, isBounding = true) }
        return Mesh(points, faces)
    }

    // --- mx building blocks ---

    /** Desktop `Sx` — polar silhouette radii. */
    fun sx(contour: List<FloatArray>): List<FloatArray> {
        return List(YL) { n ->
            val r = n / YL.toFloat() * GR
            val s = cos(r); val o = -sin(r)
            var i = 0f
            contour.forEachIndexed { c, a ->
                val u = contour[(c + 1) % contour.size]
                val l = u[0] - a[0]; val d = u[1] - a[1]
                val h = s * d - o * l
                if (abs(h) < FX) return@forEachIndexed
                val f = (a[0] * d - a[1] * l) / h
                val p = (a[0] * o - a[1] * s) / h
                if (f > i && p >= -VL && p <= 1f + VL) i = f
            }
            floatArrayOf(s * i, o * i)
        }
    }

    /** Desktop `Eh` — ensure CCW. */
    private fun eh(e: List<FloatArray>): List<FloatArray> {
        var t = 0f
        e.forEachIndexed { r, n ->
            val s = e[(r + 1) % e.size]
            t += n[0] * s[1] - s[0] * n[1]
        }
        return if (t < 0f) e.asReversed() else e
    }

    private fun va(e: List<FloatArray>): FloatArray {
        var t = Float.POSITIVE_INFINITY; var n = Float.NEGATIVE_INFINITY
        var r = Float.POSITIVE_INFINITY; var s = Float.NEGATIVE_INFINITY
        for ((o, i) in e) {
            t = min(t, o); n = max(n, o); r = min(r, i); s = max(s, i)
        }
        return floatArrayOf(t, n, r, s)
    }

    /** Desktop `wx` — pill ball chain along axis. */
    fun wx(contour: List<FloatArray>, axis: Int): List<Ball> {
        val n = contour.maxOf { abs(it[1 - axis]) }
        val r = contour.maxOf { abs(it[axis]) } - n
        return List(BL) { o ->
            val i = -r + 2f * r * (o / (BL - 1f))
            if (axis == 0) Ball(i, 0f, 0f, n) else Ball(0f, i, 0f, n)
        }
    }

    /** Desktop `pl` — tablet/capsule ball factories from bounds. */
    fun pl(halfX: Float, halfY: Float, axis: String): List<Ball> {
        val i = halfX; val a = halfY
        val c = if (axis == "x") a else i
        val u = max(0f, (if (axis == "x") i else a) - c)
        val l = -u
        return List(IL) { h ->
            val f = l + (u - l) * (h / (IL - 1f))
            if (axis == "x") Ball(f, 0f, 0f, c) else Ball(0f, f, 0f, c)
        }
    }

    /**
     * Desktop `jb` / `vx` — inscribed balls inside a 2D contour (SVG space → normalized).
     * Contour is He-normalized y-up pairs.
     */
    fun inscribedBalls(contourNorm: List<WhMesh.V2>, gridDiv: Int = CB): List<Ball> {
        if (contourNorm.size < 3) return emptyList()
        // Work in SVG-ish space like desktop: [b+b*l, b-b*d]
        val t = contourNorm.map { floatArrayOf(B + B * it.x, B - B * it.y) }
        val (n, r, s, o) = run {
            val b = va(t)
            arrayOf(b[0], b[1], b[2], b[3])
        }
        fun inside(l: Float, d: Float): Boolean {
            var h = false
            var f = 0; var p = t.size - 1
            while (f < t.size) {
                val m = t[f][0]; val g = t[f][1]
                val y = t[p][0]; val w = t[p][1]
                if ((g > d) != (w > d) && l < m + (y - m) * (d - g) / (w - g)) h = !h
                p = f; f++
            }
            return h
        }
        val a = max(r - n, o - s) / gridDiv
        if (a <= 1e-6f) return emptyList()
        val c = ArrayList<Triple<Float, Float, Float>>()
        var l = s + a / 2f
        while (l < o) {
            var d = n + a / 2f
            while (d < r) {
                if (inside(d, l)) {
                    var h = Float.POSITIVE_INFINITY
                    for ((p, m) in t) {
                        h = min(h, (p - d) * (p - d) + (m - l) * (m - l))
                    }
                    val f = sqrt(h)
                    if (f > a) c += Triple(d, l, f)
                }
                d += a
            }
            l += a
        }
        c.sortByDescending { it.third }
        val u = ArrayList<Triple<Float, Float, Float>>()
        for (ball in c) {
            if (u.none { d ->
                    hypot(ball.first - d.first, ball.second - d.second) + ball.third <= d.third + a * 0.25f
                }
            ) u += ball
        }
        return u.map { (x, y, rad) ->
            Ball((x - B) / B, -(y - B) / B, 0f, rad / B)
        }
    }

    /** Desktop `Nb` / `bx` crystal sample cloud. */
    fun crystalPoints(): List<WhMesh.V3> {
        val s = 1.1600000000000001f
        val t = 0.12f
        val centers = ArrayList<WhMesh.V3>()
        centers += WhMesh.V3(0f, s - t, 0f)
        centers += WhMesh.V3(0f, -s + t, 0f)
        val i = 0.54f - t * 0.25f
        val a = 0.64f - t
        for (sign in floatArrayOf(-1f, 1f)) {
            for (h in 0 until 6) {
                val f = h / 6f * GR
                centers += WhMesh.V3(a * cos(f), sign * i, a * sin(f))
            }
        }
        val c = ArrayList<WhMesh.V3>()
        val u = 8; val l = 14
        for (d in centers) {
            for (h in 0..u) {
                val f = h / u.toFloat() * PI.toFloat()
                val p = sin(f)
                val m = if (h == 0 || h == u) 1 else l
                for (g in 0 until m) {
                    val y = g / m.toFloat() * GR
                    c += WhMesh.V3(
                        d.x + t * p * sin(y),
                        d.y + t * cos(f),
                        d.z + t * p * cos(y),
                    )
                }
            }
        }
        return c
    }

    /** Desktop `yx` — true revolve lathe mesh from normalized contour. */
    fun yx(contour: List<WhMesh.V2>): Mesh {
        val t = LinkedHashMap<Float, Float>() // y → max |x|
        for (p in contour) {
            if (p.x > -XL) {
                val y = p.y
                t[y] = max(t[y] ?: 0f, p.x)
            }
        }
        val verts = ArrayList<WhMesh.V3>()
        fun push(v: WhMesh.V3): Int { verts += v; return verts.lastIndex }
        val rings = t.entries.sortedByDescending { it.key }.map { (a, c) ->
            if (c <= XL) listOf(push(WhMesh.V3(0f, a, 0f)))
            else List(GL) { l ->
                val d = l / GL.toFloat() * GR
                push(WhMesh.V3(c * sin(d), a, c * cos(d)))
            }
        }
        if (rings.isEmpty()) return Mesh(emptyList(), emptyList())
        val faces = ArrayList<MeshFace>()
        // Cap rings that are multi-vertex
        listOf(rings.first(), rings.last()).forEach { ring ->
            if (ring.size > 1) {
                // fan from first — Tx will fix winding; emit sequential quads later
            }
        }
        for (c in 0 until rings.size - 1) {
            val e = rings[c]; val nxt = rings[c + 1]
            when {
                e.size == 1 && nxt.size == 1 -> {}
                e.size == 1 -> {
                    for (r in nxt.indices) {
                        faces += MeshFace(intArrayOf(e[0], nxt[r], nxt[(r + 1) % nxt.size]), true)
                    }
                }
                nxt.size == 1 -> {
                    for (r in e.indices) {
                        faces += MeshFace(intArrayOf(e[r], nxt[0], e[(r + 1) % e.size]), true)
                    }
                }
                else -> {
                    for (r in e.indices) {
                        faces += MeshFace(
                            intArrayOf(e[r], nxt[r], nxt[(r + 1) % nxt.size], e[(r + 1) % e.size]),
                            true,
                        )
                    }
                }
            }
        }
        // Tx-style outward winding
        return txWinding(verts, faces)
    }

    private fun th(verts: List<WhMesh.V3>, corners: IntArray): WhMesh.V3 {
        var n = 0f; var r = 0f; var s = 0f
        for (o in corners.indices) {
            val i = verts[corners[o]]
            val a = verts[corners[(o + 1) % corners.size]]
            n += (i.y - a.y) * (i.z + a.z)
            r += (i.z - a.z) * (i.x + a.x)
            s += (i.x - a.x) * (i.y + a.y)
        }
        return WhMesh.V3(n, r, s)
    }

    private fun txWinding(verts: List<WhMesh.V3>, faces: List<MeshFace>): Mesh {
        if (verts.isEmpty()) return Mesh(emptyList(), emptyList())
        val n = verts.size.toFloat()
        val centroid = WhMesh.V3(
            verts.sumOf { it.x.toDouble() }.toFloat() / n,
            verts.sumOf { it.y.toDouble() }.toFloat() / n,
            verts.sumOf { it.z.toDouble() }.toFloat() / n,
        )
        val out = faces.map { face ->
            val (i, a, c) = th(verts, face.corners).let { Triple(it.x, it.y, it.z) }
            val u = floatArrayOf(0f, 0f, 0f)
            for (idx in face.corners) {
                u[0] += verts[idx].x; u[1] += verts[idx].y; u[2] += verts[idx].z
            }
            val l = face.corners.size.toFloat()
            val facing = i * (u[0] / l - centroid.x) + a * (u[1] / l - centroid.y) + c * (u[2] / l - centroid.z)
            val corners = if (facing < 0f) face.corners.reversedArray() else face.corners
            MeshFace(corners, isBounding = true)
        }
        return Mesh(verts, out)
    }

    /** Desktop `kx` — roundedSlab point cloud → Mh. */
    fun kx(contour: List<FloatArray>, halfDepth: Float, edge: Float): Mesh {
        val s = sx(contour).let { eh(it) }
        val r = s.size
        val normals = s.mapIndexed { a, i ->
            val c = s[(a - 1 + r) % r]; val u = s[(a + 1) % r]
            var l = u[1] - c[1]; var d = -(u[0] - c[0])
            val h = hypot(l, d).coerceAtLeast(1f)
            l /= h; d /= h
            if (l * i[0] + d * i[1] < 0f) floatArrayOf(-l, -d) else floatArrayOf(l, d)
        }
        val o = ArrayList<WhMesh.V3>()
        s.forEachIndexed { c, i ->
            for (u in 0..KL) {
                val l = u / KL.toFloat() * (PI.toFloat() / 2f)
                val d = edge * (1f - cos(l))
                val h = halfDepth - edge + edge * sin(l)
                val f = i[0] - normals[c][0] * d
                val p = i[1] - normals[c][1] * d
                o += WhMesh.V3(f, p, h)
                o += WhMesh.V3(f, p, -h)
            }
        }
        return mh(o)
    }

    // --- Qx ball renderer ---

    private fun tv(e: FloatArray, t: FloatArray) =
        (e[0] - t[0]).let { it * it } + (e[1] - t[1]).let { it * it } + (e[2] - t[2]).let { it * it }

    private fun ev(radiusPx: Float): Int {
        if (radiusPx <= VI) return 1
        val t = 2f * acos((1f - VI / radiusPx).coerceIn(-1f, 1f))
        return max(1, ceil(GR / GS / t).toInt())
    }

    private fun shade(ink: Color, nx: Float, ny: Float, nz: Float): Color {
        val d = hypot(hypot(nx, ny), nz)
        if (d < 1e-8f) return ink
        val lambert = max(0f, (nx / d) * NT[0] + (ny / d) * NT[1] + (nz / d) * NT[2])
        val p = WS + (1f - WS) * lambert
        fun ch(v: Float): Float {
            val lifted = v + (1f - v) * UX
            return (lifted * p).coerceIn(0f, 1f)
        }
        return Color(ch(ink.red), ch(ink.green), ch(ink.blue), ink.alpha)
    }

    /**
     * Desktop `Qx` — lit ball faces with occlusion culling.
     * Returns LitFaces in He-centered Compose coords.
     */
    fun qx(
        balls: List<Ball>,
        rotate: (WhMesh.V3) -> WhMesh.V3,
        he: Float,
        ink: Color,
    ): List<WhMesh.LitFace> {
        if (balls.isEmpty()) return emptyList()
        data class RB(val c: FloatArray, val r: Float)
        val o = balls.map { b ->
            val c = rotate(b.center)
            RB(floatArrayOf(c.x, c.y, c.z), b.r)
        }
        val order = o.indices.sortedBy { o[it].c[2] + o[it].r }
        val a = VI / he
        val out = ArrayList<WhMesh.LitFace>()
        // Merge same-fill patches lightly: emit each cell as LitFace
        for (c in order) {
            val u = o[c].c; val l = o[c].r
            val d = o.filterIndexed { y, _ -> y != c && o[y].r > a }
            fun hidden(pts: List<FloatArray>): Boolean =
                d.any { y -> pts.all { w -> tv(w, y.c) < (y.r - a) * (y.r - a) } }
            fun f(g: Float, y: Float) = floatArrayOf(
                u[0] + l * sin(g) * cos(y),
                u[1] + l * sin(g) * sin(y),
                u[2] + l * cos(g),
            )
            val p = ev(l * he)
            for (g in 0 until JR) {
                val y = g / JR.toFloat() * (PI.toFloat() / 2f)
                val w = (g + 1) / JR.toFloat() * (PI.toFloat() / 2f)
                val S = if (g == JR - 1) p else 1
                for (v in 0 until GS) {
                    val x = v / GS.toFloat() * GR
                    val E = (v + 1) / GS.toFloat() * GR
                    val A = ArrayList<FloatArray>()
                    if (g == 0) A += f(0f, 0f)
                    else A += f(y, x)
                    for (T in 0..S) A += f(w, x + (E - x) * T / S.toFloat())
                    if (g != 0) A += f(y, E)
                    if (hidden(A)) continue
                    val j = (y + w) / 2f
                    val F = (x + E) / 2f
                    val nx = sin(j) * cos(F)
                    val ny = sin(j) * sin(F)
                    val nz = cos(j)
                    // front-face cull in view space (z toward camera after rotate)
                    val viewN = rotate(WhMesh.V3(nx, ny, nz)) // approx: normal also rotated
                    // Actually desktop shades with unrotated local normal via s([...])
                    // then projects rotated positions. Use local normal for shade.
                    if (viewN.z <= KX) continue
                    val fill = shade(ink, nx, ny, nz)
                    var depth = 0f
                    val poly = A.map {
                        depth += it[2]
                        WhMesh.V2(he * it[0], -he * it[1])
                    }
                    depth /= A.size
                    if (poly.size >= 3) out += WhMesh.LitFace(poly, fill, depth)
                }
            }
        }
        return out.sortedBy { it.depth }
    }

    /** Lit faces from arbitrary Mesh (Zx-style). */
    fun zx(
        mesh: Mesh,
        rotate: (WhMesh.V3) -> WhMesh.V3,
        he: Float,
        ink: Color,
    ): List<WhMesh.LitFace> {
        val s = mesh.vertices.map { rotate(it) }
        val out = ArrayList<WhMesh.LitFace>()
        for (a in mesh.faces) {
            val nn = th(s, a.corners)
            val d = hypot(hypot(nn.x, nn.y), nn.z)
            if (d == 0f || nn.z <= KX * d) continue
            val fill = shade(ink, nn.x / d, nn.y / d, nn.z / d)
            var depth = 0f
            val poly = a.corners.map { idx ->
                val v = s[idx]
                depth += v.z
                WhMesh.V2(he * v.x, -he * v.y)
            }
            depth /= a.corners.size
            if (poly.size >= 3) out += WhMesh.LitFace(poly, fill, depth)
        }
        return out.sortedBy { it.depth }
    }

    /**
     * Desktop `mx` — build asset for a solid kind from He-normalized contour.
     */
    fun mx(contour: List<WhMesh.V3>, solid: SolidSpec): Asset {
        val pairs = contour.map { floatArrayOf(it.x, it.y) }
        val v2 = contour.map { WhMesh.V2(it.x, it.y) }
        return when (solid.kind) {
            "inscribedBalls" -> Asset.Balls(inscribedBalls(v2, HX))
            "pill" -> Asset.Balls(wx(pairs, solid.axis))
            "crystal" -> Asset.MeshAsset(mh(crystalPoints()))
            "revolve" -> Asset.MeshAsset(yx(v2))
            "roundedSlab" -> Asset.MeshAsset(kx(pairs, solid.halfDepth, solid.bevel.coerceAtLeast(0.08f)))
            "extrusion" -> {
                val rings = WhMesh.zb(contour, solid.halfDepth, solid.bevel)
                Asset.Rings(rings, closedCaps = true)
            }
            "loft" -> {
                val (rings, _) = WhMesh.qb(contour, solid.depth)
                Asset.Rings(rings, closedCaps = false)
            }
            "sphere" -> Asset.Rings(emptyList(), false) // handled by WhSphere
            else -> {
                val (rings, _) = WhMesh.qb(contour, 0.85f)
                Asset.Rings(rings, closedCaps = false)
            }
        }
    }

    /**
     * Build MeshDraw with cv cache. Sphere returns empty faces (caller uses WhSphere).
     */
    fun buildCached(
        shapeId: String,
        path: Path,
        solid: SolidSpec,
        yaw: Float,
        pitch: Float,
        roll: Float,
        he: Float,
        ink: Color,
    ): WhMesh.MeshDraw {
        val key = CacheKey(
            shapeId = shapeId,
            kind = solid.kind,
            yaw = (yaw * 1000).toInt() / 1000f,
            pitch = (pitch * 1000).toInt() / 1000f,
            roll = (roll * 1000).toInt() / 1000f,
            he = he.toInt(),
            depth = solid.depth,
            halfDepth = solid.halfDepth,
            bevel = solid.bevel,
            axis = solid.axis,
        )
        synchronized(meshCache) {
            meshCache[key]?.let { return it }
        }
        val draw = buildUncached(path, solid, yaw, pitch, roll, he, ink)
        synchronized(meshCache) {
            return putTracked(key, draw)
        }
    }

    fun buildUncached(
        path: Path,
        solid: SolidSpec,
        yaw: Float,
        pitch: Float,
        roll: Float,
        he: Float,
        ink: Color,
    ): WhMesh.MeshDraw {
        if (solid.kind == "sphere") {
            return WhMesh.MeshDraw(emptyList(), Path().apply { addPath(path) }, emptyList())
        }
        val contour = WhMesh.samplePathHeCentered(path, he)
        if (contour.isEmpty()) {
            return WhMesh.MeshDraw(emptyList(), Path().apply { addPath(path) }, emptyList())
        }
        val ctx = WhMesh.xhHe(yaw, pitch, roll, he)
        val rot = WhMesh.jb(yaw, pitch, roll)
        val asset = mx(contour, solid)

        val (faces, sil) = when (asset) {
            is Asset.Rings -> {
                val sil = if (asset.closedCaps) WhMesh.Silhouette.Extrusion(asset.rings)
                else WhMesh.Silhouette.Loft(asset.rings)
                val faces = WhMesh.litFacesFromRings(asset.rings, rot, ink, asset.closedCaps, he)
                faces to sil
            }
            is Asset.MeshAsset -> {
                val faces = zx(asset.mesh, rot, he, ink)
                // Project hull via solid samples of mesh verts
                val samples = asset.mesh.vertices
                val sil = WhMesh.Silhouette.Solid(samples, emptyList(), isLoop = false)
                faces to sil
            }
            is Asset.Balls -> {
                val faces = qx(asset.balls, rot, he, ink)
                val ballArr = asset.balls.map { it.asArray() }
                val sil = WhMesh.Silhouette.Solid(emptyList(), ballArr, isLoop = false)
                faces to sil
            }
        }
        val polys = WhMesh.bh(sil, ctx)
        val hull = WhMesh.pathFromPolygons(polys) ?: Path().apply { addPath(path) }
        return WhMesh.MeshDraw(faces, hull, polys)
    }
}
