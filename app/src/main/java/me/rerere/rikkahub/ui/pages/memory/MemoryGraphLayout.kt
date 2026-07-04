package me.rerere.rikkahub.ui.pages.memory

import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.memory.MemorySleepLogic
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure (Android/Compose-free, unit-testable) graph preparation + force-directed layout for the
 * Memory Center Graph tab (§10.2). Kept off the UI thread by callers — nothing here touches
 * Compose state, so it can run wholesale on `Dispatchers.Default`.
 *
 * The store can hold thousands of nodes, but the tab never draws them all: [MemoryGraphBuilder]
 * selects a bounded *neighborhood* clustered around ENTITY/FRAME hubs, so the simulation and the
 * frozen render both stay small. Pan/zoom is a `graphicsLayer` transform in the composable and
 * never re-enters this code. That is why the custom-simulation path (§14 open-question 2) is
 * viable rather than the static fallback — the 5k risk is bounded away, not simulated through.
 */

/** Immutable raw graph the Graph tab renders from — a scope's nodes plus all edges among them. */
data class MemoryGraphInput(
    val nodes: List<MemoryNodeEntity>,
    val edges: List<MemoryEdgeEntity>,
) {
    companion object {
        val EMPTY = MemoryGraphInput(emptyList(), emptyList())
    }
}

/** One drawable node. Index-based so the simulation can work on flat float arrays. */
data class GraphVizNode(
    val id: String,
    val type: Int,
    val status: Int,
    val label: String,
    /** Rendered radius in world units, ∝ retention (pinned/important nodes are larger). */
    val radius: Float,
    val pinned: Boolean,
    /** PROVISIONAL → drawn with a dashed ring. */
    val provisional: Boolean,
    /** DORMANT / CLOSED → drawn faded. */
    val faded: Boolean,
    /** ENTITY or FRAME — a cluster hub (larger, anchored by the simulation). */
    val isHub: Boolean,
    /** Index (into [PreparedGraph.nodes]) of the hub this node clusters under; -1 if orphaned. */
    val clusterHub: Int,
)

data class GraphVizEdge(val from: Int, val to: Int, val type: Int, val weight: Float)

/** The bounded neighborhood selected for display, plus how many live nodes were left out of it. */
data class PreparedGraph(
    val nodes: List<GraphVizNode>,
    val edges: List<GraphVizEdge>,
    val truncated: Int,
) {
    val isEmpty: Boolean get() = nodes.isEmpty()
}

object MemoryGraphBuilder {

    /** Upper bound on nodes simulated/drawn at once — keeps the neighborhood view fast and legible. */
    const val DEFAULT_MAX_NODES = 180

    /** Statuses the graph surfaces (live belief set + closed history; excludes SUPERSEDED/FORGOTTEN). */
    val GRAPH_STATUSES = intArrayOf(MemStatus.ACTIVE, MemStatus.PROVISIONAL, MemStatus.DORMANT, MemStatus.CLOSED)

    private const val MIN_RADIUS = 7f
    private const val MAX_RADIUS = 20f
    private const val HUB_SCALE = 1.35f

    // Retention maps to radius over this window; pinned nodes are pinned to the top.
    private const val RETENTION_FLOOR = -1.0
    private const val RETENTION_CEIL = 8.0
    private const val PINNED_RETENTION = RETENTION_CEIL

    private fun isHubType(type: Int) = type == MemNodeType.ENTITY || type == MemNodeType.FRAME

    private fun retention(node: MemoryNodeEntity, now: Long): Double =
        if (node.pinned) PINNED_RETENTION
        else MemorySleepLogic.retentionScore(
            importance = node.importance,
            timesReinforced = node.timesReinforced,
            timesRetrieved = node.timesRetrieved,
            lastAccessedAt = node.lastAccessedAt,
            now = now,
        )

    private fun radiusFor(retention: Double, isHub: Boolean): Float {
        val t = ((retention - RETENTION_FLOOR) / (RETENTION_CEIL - RETENTION_FLOOR)).coerceIn(0.0, 1.0)
        val base = MIN_RADIUS + t.toFloat() * (MAX_RADIUS - MIN_RADIUS)
        return if (isHub) base * HUB_SCALE else base
    }

    /**
     * Build the drawable neighborhood.
     *
     * @param forcedIds nodes that must appear (pinned + current search matches) — added first, with
     *   their 1-hop neighbors, so a match is never shown floating alone.
     * @param expandedIds nodes the user tapped to grow the neighborhood around.
     */
    fun build(
        input: MemoryGraphInput,
        expandedIds: Set<String> = emptySet(),
        forcedIds: Set<String> = emptySet(),
        maxNodes: Int = DEFAULT_MAX_NODES,
        now: Long = System.currentTimeMillis(),
    ): PreparedGraph {
        val statuses = GRAPH_STATUSES
        val live = input.nodes.filter { statuses.contains(it.status) }
        if (live.isEmpty()) return PreparedGraph(emptyList(), emptyList(), 0)
        val byId = live.associateBy { it.id }

        // Undirected adjacency restricted to live endpoints.
        val adj = HashMap<String, MutableList<String>>(live.size * 2)
        val liveEdges = ArrayList<MemoryEdgeEntity>(input.edges.size)
        for (e in input.edges) {
            if (e.fromId == e.toId) continue
            if (!byId.containsKey(e.fromId) || !byId.containsKey(e.toId)) continue
            liveEdges += e
            adj.getOrPut(e.fromId) { ArrayList() }.add(e.toId)
            adj.getOrPut(e.toId) { ArrayList() }.add(e.fromId)
        }
        fun degree(id: String) = adj[id]?.size ?: 0

        val hubs = live.asSequence()
            .filter { isHubType(it.type) }
            .sortedWith(compareByDescending<MemoryNodeEntity> { degree(it.id) }.thenByDescending { retention(it, now) })
            .toList()

        val selected = LinkedHashSet<String>(min(maxNodes, live.size) * 2)
        fun tryAdd(id: String): Boolean {
            if (id in selected) return true
            if (selected.size >= maxNodes) return false
            selected.add(id)
            return true
        }
        fun neighborsByRetention(id: String): List<String> =
            adj[id]?.distinct()?.sortedByDescending { retention(byId.getValue(it), now) } ?: emptyList()

        // 1. Forced (pinned + matches) and user-expanded seeds, each with its neighborhood.
        for (seed in (forcedIds + expandedIds)) {
            if (seed !in byId) continue
            if (!tryAdd(seed)) break
            for (m in neighborsByRetention(seed)) if (!tryAdd(m)) break
        }
        // 2. Hub clusters, highest-connected first.
        for (h in hubs) {
            if (selected.size >= maxNodes) break
            tryAdd(h.id)
            for (m in neighborsByRetention(h.id)) if (!tryAdd(m)) break
        }
        // 3. Backfill with any remaining high-retention live nodes (isolated ones included).
        if (selected.size < maxNodes) {
            for (n in live.sortedByDescending { retention(it, now) }) if (!tryAdd(n.id)) break
        }

        val order = selected.toList()
        val indexOf = HashMap<String, Int>(order.size * 2)
        order.forEachIndexed { i, id -> indexOf[id] = i }

        // Strongest selected hub neighbor → cluster assignment (seeds the layout & tints members).
        fun clusterHubFor(id: String): Int {
            val self = byId.getValue(id)
            if (isHubType(self.type)) return indexOf.getValue(id)
            val hub = adj[id]?.asSequence()
                ?.filter { indexOf.containsKey(it) && isHubType(byId.getValue(it).type) }
                ?.maxByOrNull { degree(it) }
            return hub?.let { indexOf.getValue(it) } ?: -1
        }

        val vizNodes = order.map { id ->
            val n = byId.getValue(id)
            val hub = isHubType(n.type)
            val ret = retention(n, now)
            GraphVizNode(
                id = id,
                type = n.type,
                status = n.status,
                label = (n.displayLabel?.takeIf { it.isNotBlank() } ?: n.content).trim().let { s ->
                    if (s.length <= GRAPH_LABEL_MAX) s else s.take(GRAPH_LABEL_MAX - 1).trimEnd() + "…"
                },
                radius = radiusFor(ret, hub),
                pinned = n.pinned,
                provisional = n.status == MemStatus.PROVISIONAL,
                faded = n.status == MemStatus.DORMANT || n.status == MemStatus.CLOSED,
                isHub = hub,
                clusterHub = clusterHubFor(id),
            )
        }

        val seenEdge = HashSet<Long>(liveEdges.size * 2)
        val vizEdges = ArrayList<GraphVizEdge>(liveEdges.size)
        for (e in liveEdges) {
            val a = indexOf[e.fromId] ?: continue
            val b = indexOf[e.toId] ?: continue
            // Dedupe undirected duplicates (a↔b) across mirrored/multiple edge rows.
            val key = if (a < b) a.toLong() * 1_000_003L + b else b.toLong() * 1_000_003L + a
            if (!seenEdge.add(key)) continue
            vizEdges += GraphVizEdge(a, b, e.type, e.weight)
        }

        return PreparedGraph(vizNodes, vizEdges, (live.size - order.size).coerceAtLeast(0))
    }

    const val GRAPH_LABEL_MAX = 28
}

/**
 * Fruchterman–Reingold-style force layout over a [PreparedGraph]. Deterministic given a seed. The
 * caller steps this on a background dispatcher and swaps whole immutable position frames into Compose
 * state (never a `SnapshotStateList` mutated per element), then freezes once [step] reports settled.
 */
class MemoryForceLayout(
    private val prepared: PreparedGraph,
    seed: Long = 0x5EED,
) {
    val count: Int = prepared.nodes.size

    // Flat world-space coordinates: [x0, y0, x1, y1, ...].
    private val pos = FloatArray(count * 2)
    private val disp = FloatArray(count * 2)
    private val mass = FloatArray(count) { if (prepared.nodes[it].isHub) HUB_MASS else 1f }

    /** Ideal edge length; also sets the world scale used for seeding. */
    private val k: Float = IDEAL_EDGE_LENGTH
    private var temperature: Float = k * 2.4f

    init {
        val rnd = Random(seed)
        val hubIndices = prepared.nodes.indices.filter { prepared.nodes[it].isHub }
        val hubRing = (k * 2.2f) * sqrt(hubIndices.size.coerceAtLeast(1).toFloat())
        val hubAngle = HashMap<Int, Float>(hubIndices.size * 2)
        hubIndices.forEachIndexed { ord, idx ->
            val a = (2.0 * Math.PI * ord / hubIndices.size.coerceAtLeast(1)).toFloat()
            hubAngle[idx] = a
            setPos(idx, cos(a) * hubRing, sin(a) * hubRing)
        }
        var orphanOrd = 0
        for (i in 0 until count) {
            if (prepared.nodes[i].isHub) continue
            val hub = prepared.nodes[i].clusterHub
            if (hub >= 0) {
                val hx = pos[hub * 2]; val hy = pos[hub * 2 + 1]
                val a = rnd.nextFloat() * (2f * Math.PI.toFloat())
                val r = k * (0.6f + rnd.nextFloat() * 0.9f)
                setPos(i, hx + cos(a) * r, hy + sin(a) * r)
            } else {
                val a = (2.0 * Math.PI * orphanOrd++ / count).toFloat() + rnd.nextFloat() * 0.3f
                val r = hubRing * (1.15f + rnd.nextFloat() * 0.25f)
                setPos(i, cos(a) * r, sin(a) * r)
            }
        }
    }

    private fun setPos(i: Int, x: Float, y: Float) {
        pos[i * 2] = x; pos[i * 2 + 1] = y
    }

    /** Advance one iteration. Returns true once the layout has settled (max move below epsilon). */
    fun step(): Boolean {
        if (count <= 1) return true
        java.util.Arrays.fill(disp, 0f)

        // Repulsion (all pairs — count is bounded by MemoryGraphBuilder.DEFAULT_MAX_NODES).
        val kk = k * k
        for (i in 0 until count) {
            val ix = pos[i * 2]; val iy = pos[i * 2 + 1]
            for (j in i + 1 until count) {
                var dx = ix - pos[j * 2]
                var dy = iy - pos[j * 2 + 1]
                var d2 = dx * dx + dy * dy
                if (d2 < MIN_DIST_SQ) {
                    // Jitter coincident nodes deterministically so the pair separates.
                    dx = ((i - j) and 1) * 2f - 1f + 0.01f
                    dy = ((i + j) and 1) * 2f - 1f + 0.01f
                    d2 = dx * dx + dy * dy
                }
                val dist = sqrt(d2)
                val force = kk / dist
                val fx = dx / dist * force
                val fy = dy / dist * force
                disp[i * 2] += fx; disp[i * 2 + 1] += fy
                disp[j * 2] -= fx; disp[j * 2 + 1] -= fy
            }
        }

        // Attraction along edges.
        for (e in prepared.edges) {
            val a = e.from; val b = e.to
            val dx = pos[a * 2] - pos[b * 2]
            val dy = pos[a * 2 + 1] - pos[b * 2 + 1]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val force = dist * dist / k
            val fx = dx / dist * force
            val fy = dy / dist * force
            disp[a * 2] -= fx; disp[a * 2 + 1] -= fy
            disp[b * 2] += fx; disp[b * 2 + 1] += fy
        }

        // Weak gravity toward the origin keeps disconnected components from drifting off.
        for (i in 0 until count) {
            disp[i * 2] -= pos[i * 2] * GRAVITY
            disp[i * 2 + 1] -= pos[i * 2 + 1] * GRAVITY
        }

        // Integrate, capped by the cooling temperature; heavier hubs move less.
        var maxMove = 0f
        for (i in 0 until count) {
            val dx = disp[i * 2]; val dy = disp[i * 2 + 1]
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-4f) continue
            val capped = min(len, temperature) / mass[i]
            val mx = dx / len * capped
            val my = dy / len * capped
            pos[i * 2] += mx; pos[i * 2 + 1] += my
            val move = sqrt(mx * mx + my * my)
            if (move > maxMove) maxMove = move
        }
        temperature *= COOLING
        return maxMove < SETTLE_EPSILON
    }

    /** Run until settled or [maxIterations] reached (used for the reduced-motion / static path). */
    fun runToSettle(maxIterations: Int = MAX_ITERATIONS) {
        var i = 0
        while (i < maxIterations) {
            if (step()) break
            i++
        }
    }

    /** A defensive copy of the current positions for handing to the UI thread. */
    fun snapshot(): FloatArray = pos.copyOf()

    companion object {
        const val IDEAL_EDGE_LENGTH = 46f
        const val HUB_MASS = 2.4f
        const val GRAVITY = 0.012f
        const val COOLING = 0.955f
        const val SETTLE_EPSILON = 0.55f
        const val MAX_ITERATIONS = 420
        private const val MIN_DIST_SQ = 0.01f
    }
}
