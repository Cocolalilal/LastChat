package me.rerere.rikkahub.ui.pages.memory

import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the Graph tab's off-thread pipeline (§10.2): [MemoryGraphBuilder] must bound a
 * huge store to a drawable neighborhood, and [MemoryForceLayout] must settle to finite, deterministic
 * positions. These are the parts that must be correct for the custom-simulation path to hold (§14 Q2).
 */
class MemoryGraphLayoutTest {

    private val now = 1_700_000_000_000L

    private fun node(
        id: String,
        type: Int,
        status: Int = MemStatus.ACTIVE,
        importance: Int = 3,
        pinned: Boolean = false,
        content: String = id,
    ) = MemoryNodeEntity(
        id = id,
        type = type,
        scope = MemScope.CHARACTER,
        ownerAssistantId = "assistant",
        content = content,
        importance = importance,
        status = status,
        pinned = pinned,
        recordedAt = now,
        lastConfirmedAt = now,
        lastAccessedAt = now,
        source = MemSource.EXTRACTED,
    )

    private fun edge(from: String, to: String, type: Int = MemEdgeType.ABOUT) =
        MemoryEdgeEntity(id = "$from->$to", fromId = from, toId = to, type = type, createdAt = now)

    /** 50 entity hubs × 100 facts each, wired via ABOUT edges — ~5,050 nodes / ~5,000 edges. */
    private fun largeStore(): MemoryGraphInput {
        val nodes = ArrayList<MemoryNodeEntity>(5_050)
        val edges = ArrayList<MemoryEdgeEntity>(5_000)
        for (h in 0 until 50) {
            val hubId = "e$h"
            nodes += node(hubId, MemNodeType.ENTITY, content = "Entity $h")
            for (f in 0 until 100) {
                val factId = "f${h}_$f"
                nodes += node(factId, MemNodeType.FACT, importance = (f % 5) + 1)
                edges += edge(factId, hubId)
            }
        }
        return MemoryGraphInput(nodes, edges)
    }

    @Test
    fun builderBoundsLargeStoreToNeighborhood() {
        val input = largeStore()
        val prepared = MemoryGraphBuilder.build(input, now = now)

        assertTrue("must not exceed the node budget", prepared.nodes.size <= MemoryGraphBuilder.DEFAULT_MAX_NODES)
        assertTrue("should surface something", prepared.nodes.isNotEmpty())
        // truncated accounts for exactly the live nodes left out of the view.
        assertEquals(input.nodes.size - prepared.nodes.size, prepared.truncated)
        // Every edge references a selected node index (builder must not emit dangling edges).
        val bound = prepared.nodes.indices
        assertTrue(prepared.edges.all { it.from in bound && it.to in bound && it.from != it.to })
        // Hubs are selected first, so the densest entities appear.
        assertTrue(prepared.nodes.any { it.isHub })
    }

    @Test
    fun forcedNodesAreAlwaysIncluded() {
        val input = largeStore()
        // A deep fact that hub-first selection would otherwise truncate away.
        val target = "f49_99"
        val prepared = MemoryGraphBuilder.build(input, forcedIds = setOf(target), now = now)
        assertTrue("forced (pinned/matched) node must appear", prepared.nodes.any { it.id == target })
    }

    @Test
    fun excludesSupersededAndForgotten() {
        val input = MemoryGraphInput(
            nodes = listOf(
                node("a", MemNodeType.ENTITY),
                node("b", MemNodeType.FACT, status = MemStatus.SUPERSEDED),
                node("c", MemNodeType.FACT, status = MemStatus.FORGOTTEN),
                node("d", MemNodeType.FACT, status = MemStatus.DORMANT),
            ),
            edges = emptyList(),
        )
        val prepared = MemoryGraphBuilder.build(input, now = now)
        val ids = prepared.nodes.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("a", "d")))
        assertFalse(ids.contains("b"))
        assertFalse(ids.contains("c"))
        assertTrue(prepared.nodes.first { it.id == "d" }.faded) // DORMANT renders faded
    }

    @Test
    fun emptyInputYieldsEmptyGraph() {
        val prepared = MemoryGraphBuilder.build(MemoryGraphInput.EMPTY)
        assertTrue(prepared.isEmpty)
        assertEquals(0, prepared.edges.size)
    }

    @Test
    fun simulationSettlesToFinitePositions() {
        val prepared = MemoryGraphBuilder.build(largeStore(), now = now)
        val sim = MemoryForceLayout(prepared)
        val started = System.nanoTime()
        sim.runToSettle()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        // One-shot cost is off-thread; this just guards against a runaway (the neighborhood is bounded).
        assertTrue("settle took ${elapsedMs}ms", elapsedMs < 4_000)

        val pos = sim.snapshot()
        assertEquals(prepared.nodes.size * 2, pos.size)
        assertTrue("positions must be finite", pos.all { it.isFinite() })
    }

    @Test
    fun simulationIsDeterministic() {
        val prepared = MemoryGraphBuilder.build(largeStore(), now = now)
        val a = MemoryForceLayout(prepared).apply { repeat(60) { step() } }.snapshot()
        val b = MemoryForceLayout(prepared).apply { repeat(60) { step() } }.snapshot()
        assertTrue(a.contentEquals(b))
    }

    // ---------------- degree-based sizing ----------------

    @Test
    fun radiusGrowsWithDegree() {
        val low = MemoryGraphBuilder.radiusFor(degree = 1, maxDegree = 10, retention = 3.0, pinned = false, isHub = false)
        val mid = MemoryGraphBuilder.radiusFor(degree = 4, maxDegree = 10, retention = 3.0, pinned = false, isHub = false)
        val high = MemoryGraphBuilder.radiusFor(degree = 10, maxDegree = 10, retention = 3.0, pinned = false, isHub = false)
        assertTrue("more relations → larger", mid > low && high > mid)
    }

    @Test
    fun radiusClampedToRange() {
        val r = MemoryGraphBuilder.radiusFor(degree = 100, maxDegree = 100, retention = 8.0, pinned = true, isHub = true)
        assertTrue(r >= MemoryGraphBuilder.MIN_RADIUS)
        // Hub scale + pinned bump can exceed MAX_RADIUS slightly; guard against runaway only.
        assertTrue(r < MemoryGraphBuilder.MAX_RADIUS * 2)
    }

    @Test
    fun degreeDrivesSizeInBuiltGraph() {
        // A hub with many facts must render larger than one of its leaf facts.
        val prepared = MemoryGraphBuilder.build(largeStore(), now = now)
        val hub = prepared.nodes.filter { it.isHub }.maxByOrNull { it.degree }!!
        val leaf = prepared.nodes.filter { !it.isHub }.minByOrNull { it.degree }!!
        assertTrue("hub degree should exceed leaf", hub.degree > leaf.degree)
        assertTrue("hub radius should exceed leaf", hub.radius > leaf.radius)
    }
}
