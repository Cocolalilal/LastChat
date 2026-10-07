package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Desktop Grok Bot 0.68.1 `Px` / `uv` multipolygon boolean union.
 *
 * Pipeline: Ix → Ax → $x → _x → Rx → Ox (Ga for single).
 * Quantized at `dr=1024`. Not largest-area hull.
 */
object WhBooleanUnion {

    private const val DR = 1024 // dr
    private const val TL = 1 shl 23 // Tl
    private const val EX = 1 shl 25 // Ex
    private const val UT = 1L shl 26 // Ut

    private data class Edge(
        val ax: Int,
        val ay: Int,
        val bx: Int,
        val by: Int,
        val count: Int,
    )

    private class Uo {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        private val map = HashMap<Long, Int>()
        fun id(t: Int, n: Int): Int {
            val r = (t.toLong() + TL) * EX + (n.toLong() + TL)
            map[r]?.let { return it }
            val s = xs.size
            xs += t
            ys += n
            map[r] = s
            return s
        }
    }

    private fun yt(e: Float): String = String.format("%.2f", (Math.round(e * 100.0) / 100.0))

    private fun lt(e: Int, t: Int, n: Int, r: Int, s: Int, o: Int): Int =
        (n - e) * (o - t) - (r - t) * (s - e)

    private fun ltL(e: Long, t: Long, n: Long, r: Long, s: Long, o: Long): Long =
        (n - e) * (o - t) - (r - t) * (s - e)

    private fun nxArea(e: IntArray): Int {
        var t = 0
        var n = 0
        while (n < e.size) {
            val r = (n + 2) % e.size
            t += e[n] * e[r + 1] - e[r] * e[n + 1]
            n += 2
        }
        return t
    }

    private fun el(
        e: Int, t: Int, n: Int, r: Int, s: Int, o: Int, i: Int, a: Int,
    ): IntArray? {
        val c = sign(lt(e, t, n, r, s, o).toFloat()).toInt()
        val u = sign(lt(e, t, n, r, i, a).toFloat()).toInt()
        val l = lt(s, o, i, a, e, t)
        val d = lt(s, o, i, a, n, r)
        if (!(c * u < 0 && sign(l.toFloat()).toInt() * sign(d.toFloat()).toInt() < 0)) return null
        val h = l.toFloat() / (l - d).toFloat()
        return intArrayOf(
            Math.round(e + (n - e) * h),
            Math.round(t + (r - t) * h),
        )
    }

    private fun lx(e: IntArray): List<IntArray>? {
        if (e.size != 8) return null
        val t = e[0]; val n = e[1]; val r = e[2]; val s = e[3]
        val o = e[4]; val i = e[5]; val a = e[6]; val c = e[7]
        val u = el(t, n, r, s, o, i, a, c)
        if (u != null) {
            return listOf(
                intArrayOf(t, n, u[0], u[1], a, c),
                intArrayOf(u[0], u[1], r, s, o, i),
            )
        }
        val l = el(r, s, o, i, a, c, t, n) ?: return null
        return listOf(
            intArrayOf(r, s, l[0], l[1], t, n),
            intArrayOf(l[0], l[1], o, i, a, c),
        )
    }

    private fun ei(e: Edge): Int = min(e.ax, e.bx)

    private fun cx(pts: List<Pair<Int, Int>>, t: Float): Int {
        var n = 0
        var r = pts.size
        while (n < r) {
            val s = (n + r) ushr 1
            if (pts[s].first < t) n = s + 1 else r = s
        }
        return n
    }

    /** Desktop `Ix` — polygon edges with winding counts. */
    private fun ix(polys: List<FloatArray>): List<Edge> {
        val t = Uo()
        val n = HashMap<Long, Int>()
        val scratch = ArrayList<Int>()

        fun o(c: IntArray) {
            val u = nxArea(c)
            if (u == 0) return
            val l = c.size / 2
            scratch.clear()
            for (d in 0 until l) {
                val h = if (u > 0) d else l - 1 - d
                scratch += t.id(c[2 * h], c[2 * h + 1])
            }
            for (d in 0 until l) {
                val h = scratch[d]
                val f = scratch[(d + 1) % l]
                if (h == f) continue
                val p = f.toLong() * UT + h
                val m = n[p] ?: 0
                when {
                    m > 1 -> n[p] = m - 1
                    m == 1 -> n.remove(p)
                    else -> {
                        val key = h.toLong() * UT + f
                        n[key] = (n[key] ?: 0) + 1
                    }
                }
            }
        }

        for (c in polys) {
            if (c.size < 6) continue
            val r = IntArray(c.size) { Math.round(c[it] * DR) }
            val u = if (r.size == 8) lx(r) else null
            if (u == null) o(r) else for (l in u) o(l)
        }

        return n.map { (c, u) ->
            val l = (c / UT).toInt()
            val d = (c - l.toLong() * UT).toInt()
            Edge(t.xs[l], t.ys[l], t.xs[d], t.ys[d], u)
        }
    }

    /** Desktop `Ax` — insert crossing vertices. */
    private fun ax(edges: List<Edge>): List<Pair<Int, Int>> {
        val t = Uo()
        for (o in edges) {
            t.id(o.ax, o.ay)
            t.id(o.bx, o.by)
        }
        val n = edges.indices.sortedBy { ei(edges[it]) }
        n.forEachIndexed { i, o ->
            val a = edges[o]
            val c = max(a.ax, a.bx)
            for (u in i + 1 until n.size) {
                val l = edges[n[u]]
                if (ei(l) > c) break
                if (min(l.ay, l.by) > max(a.ay, a.by) || max(l.ay, l.by) < min(a.ay, a.by)) continue
                val d = sign(lt(a.ax, a.ay, a.bx, a.by, l.ax, l.ay).toFloat()).toInt()
                val h = sign(lt(a.ax, a.ay, a.bx, a.by, l.bx, l.by).toFloat()).toInt()
                val f = lt(l.ax, l.ay, l.bx, l.by, a.ax, a.ay)
                val p = lt(l.ax, l.ay, l.bx, l.by, a.bx, a.by)
                if (d * h >= 0 || sign(f.toFloat()).toInt() * sign(p.toFloat()).toInt() >= 0) continue
                val m = f.toFloat() / (f - p).toFloat()
                t.id(
                    Math.round(a.ax + (a.bx - a.ax) * m),
                    Math.round(a.ay + (a.by - a.ay) * m),
                )
            }
        }
        return t.xs.indices.map { Pair(t.xs[it], t.ys[it]) }.sortedBy { it.first }
    }

    /** Desktop `$x` — split edge at vertices on it. */
    private fun dollarX(e: Edge, verts: List<Pair<Int, Int>>): List<Edge> {
        val n = e.bx - e.ax
        val r = e.by - e.ay
        val s = abs(n) + abs(r)
        val o = min(e.ay, e.by) - 0.5f
        val i = max(e.ay, e.by) + 0.5f
        val a = max(e.ax, e.bx) + 0.5f
        val c = ArrayList<Triple<Float, Int, Int>>()
        var l = cx(verts, min(e.ax, e.bx) - 0.5f)
        while (l < verts.size && verts[l].first <= a) {
            val (d, h) = verts[l]
            if (h.toFloat() in o..i) {
                if (2 * abs(lt(e.ax, e.ay, e.bx, e.by, d, h)) <= s) {
                    val denom = (n * n + r * r).toFloat().coerceAtLeast(1e-9f)
                    val tt = ((d - e.ax) * n + (h - e.ay) * r) / denom
                    c += Triple(tt, d, h)
                }
            }
            l++
        }
        c.sortBy { it.first }
        val u = ArrayList<Edge>()
        for (idx in 1 until c.size) {
            val d = c[idx - 1]
            val h = c[idx]
            if (d.second != h.second || d.third != h.third) {
                u += Edge(d.second, d.third, h.second, h.third, e.count)
            }
        }
        return u
    }

    /** Desktop `_x` — merge opposite edges. */
    private fun underX(edges: List<Edge>): List<Edge> {
        val t = Uo()
        val n = HashMap<Long, Int>()
        for (i in edges) {
            val a = t.id(i.ax, i.ay)
            val c = t.id(i.bx, i.by)
            val u = min(a, c).toLong() * UT + max(a, c)
            n[u] = (n[u] ?: 0) + if (a < c) i.count else -i.count
        }
        val o = ArrayList<Edge>()
        for ((i, a) in n) {
            if (a == 0) continue
            val c = (i / UT).toInt()
            val u = (i - c.toLong() * UT).toInt()
            o += Edge(t.xs[c], t.ys[c], t.xs[u], t.ys[u], a)
        }
        return o
    }

    /** Desktop `Rx` — keep boundary edges by winding. */
    private fun rx(edges: List<Edge>): List<Edge> {
        val t = ArrayList<Edge>()
        for (n in edges) {
            val r = n.ax + n.bx
            val s = n.ay + n.by
            var o = 0
            for (u in edges) {
                if (u === n) continue
                val l = 2 * u.ay
                val d = 2 * u.by
                when {
                    l <= s && d > s && ltL(2L * u.ax, l.toLong(), 2L * u.bx, d.toLong(), r.toLong(), s.toLong()) > 0 ->
                        o += u.count
                    l > s && d <= s && ltL(2L * u.ax, l.toLong(), 2L * u.bx, d.toLong(), r.toLong(), s.toLong()) < 0 ->
                        o -= u.count
                }
            }
            val a = if (n.ay < n.by || (n.ay == n.by && n.ax > n.bx)) o + n.count else o
            val c = a - n.count
            if ((a != 0) != (c != 0)) {
                t += if (a != 0) n.copy(count = 1)
                else Edge(n.bx, n.by, n.ax, n.ay, 1)
            }
        }
        return t
    }

    /** Desktop `Ox` — stitch edges into SVG path string; we build Compose Path. */
    private fun oxToPath(edges: List<Edge>): Path {
        val t = Uo()
        val n = ArrayList<ArrayList<Int>>()
        for (i in edges) {
            val a = t.id(i.ax, i.ay)
            val c = t.id(i.bx, i.by)
            while (n.size <= max(a, c)) n.add(ArrayList())
            repeat(abs(i.count)) {
                if (i.count > 0) n[a].add(c) else n[c].add(a)
            }
        }
        val path = Path()
        n.forEachIndexed { a, i ->
            while (i.isNotEmpty()) {
                path.moveTo(t.xs[a].toFloat() / DR, t.ys[a].toFloat() / DR)
                var c: Int? = i.removeAt(i.lastIndex)
                while (c != null && c != a) {
                    path.lineTo(t.xs[c].toFloat() / DR, t.ys[c].toFloat() / DR)
                    c = if (n[c].isEmpty()) null else n[c].removeAt(n[c].lastIndex)
                }
                path.close()
            }
        }
        return path
    }

    private fun ga(polys: List<FloatArray>): Path {
        val path = Path()
        for (n in polys) {
            if (n.size < 6) continue
            path.moveTo(n[0], n[1])
            for (r in 2 until n.size step 2) path.lineTo(n[r], n[r + 1])
            path.close()
        }
        return path
    }

    /** Desktop `Px`. */
    fun px(polys: List<FloatArray>): Path {
        val t = ix(polys)
        val n = ax(t)
        val split = t.flatMap { dollarX(it, n) }
        return oxToPath(rx(underX(split)))
    }

    /**
     * Desktop `uv` — union multipolygons to a single fillable Path.
     *
     * The body is ONE flat fill, so the union only has to fill correctly — no
     * boundary tracing needed. Every face polygon is re-wound to the same (CCW)
     * orientation and added under NonZero: overlaps accumulate winding ≥ 1 and
     * can never cancel, so this is the exact union. The `Px` boundary-trace port
     * left inner contours behind on rotated extrusions/lofts, which painted as
     * ring-shaped holes (square/sparkle/clover/flower/house/hex in look-around).
     */
    fun uv(polys: List<FloatArray>): Path? {
        if (polys.isEmpty()) return null
        val path = Path().apply { fillType = androidx.compose.ui.graphics.PathFillType.NonZero }
        var any = false
        for (n in polys) {
            val count = n.size / 2
            if (count < 3) continue
            var area2 = 0f
            for (i in 0 until count) {
                val j = (i + 1) % count
                area2 += n[i * 2] * n[j * 2 + 1] - n[j * 2] * n[i * 2 + 1]
            }
            if (kotlin.math.abs(area2) < 1e-4f) continue // degenerate edge-on quad
            val reverse = area2 < 0f
            fun x(k: Int) = n[(if (reverse) count - 1 - k else k) * 2]
            fun y(k: Int) = n[(if (reverse) count - 1 - k else k) * 2 + 1]
            path.moveTo(x(0), y(0))
            for (k in 1 until count) path.lineTo(x(k), y(k))
            path.close()
            any = true
        }
        return if (any) path else ga(polys)
    }

    /** Legacy boundary-trace union (kept for reference; holes on rotated solids). */
    @Suppress("unused")
    fun uvTrace(polys: List<FloatArray>): Path? {
        if (polys.isEmpty()) return null
        if (polys.size == 1) return ga(polys)
        return px(polys)
    }
}
