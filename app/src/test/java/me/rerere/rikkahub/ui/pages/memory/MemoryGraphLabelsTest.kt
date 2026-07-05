package me.rerere.rikkahub.ui.pages.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the graph's level-of-detail label selection ([MemoryGraphLabels]). The hard
 * guarantee is **no two visible labels overlap**, and selection is deterministic (no flicker).
 */
class MemoryGraphLabelsTest {

    private fun size(w: Float = 40f, h: Float = 14f) = MemoryGraphLabels.LabelSize(w, h)

    @Test
    fun noVisibleLabelsOverlap() {
        // 20 nodes packed close together — many labels would overlap if all shown.
        val n = 20
        val xs = FloatArray(n) { (it % 5) * 12f }
        val ys = FloatArray(n) { (it / 5) * 8f }
        val radii = FloatArray(n) { 10f }
        val sizes = List(n) { size() }
        val visible = MemoryGraphLabels.computeVisibleLabels(
            worldX = xs, worldY = ys, radii = radii, labelSizes = sizes,
            scale = 2f, offsetX = 0f, offsetY = 0f, minScreenRadiusPx = 5f,
        )
        // Assert pairwise non-overlap of the accepted screen boxes.
        for (i in visible.indices) for (j in i + 1 until visible.size) {
            val a = visible[i]; val b = visible[j]
            assertFalse("labels $a and $b overlap", boxesOverlap(xs, ys, radii, sizes, a, b, scale = 2f))
        }
    }

    @Test
    fun deterministicAcrossInputPermutations() {
        val n = 12
        val xs = FloatArray(n) { it * 9f }
        val ys = FloatArray(n) { 0f }
        val radii = FloatArray(n) { 8f + it }
        val sizes = List(n) { size() }
        val first = MemoryGraphLabels.computeVisibleLabels(xs, ys, radii, sizes, 1.5f, 0f, 0f, 5f)
        val again = MemoryGraphLabels.computeVisibleLabels(xs, ys, radii, sizes, 1.5f, 0f, 0f, 5f)
        assertTrue(first.contentEquals(again))
    }

    @Test
    fun labelAppearsOnlyPastScreenRadiusThreshold() {
        val xs = floatArrayOf(0f)
        val ys = floatArrayOf(0f)
        val radii = floatArrayOf(10f)
        val sizes = listOf(size())
        // At scale 0.4, screen radius = 4 < threshold 5 → hidden.
        assertEquals(0, MemoryGraphLabels.computeVisibleLabels(xs, ys, radii, sizes, 0.4f, 0f, 0f, 5f).size)
        // At scale 1.0, screen radius = 10 >= threshold → shown.
        assertEquals(1, MemoryGraphLabels.computeVisibleLabels(xs, ys, radii, sizes, 1.0f, 0f, 0f, 5f).size)
    }

    @Test
    fun largerNodeWinsOverlapPriority() {
        // Two nodes whose labels overlap; the larger-radius node must keep its label.
        val xs = floatArrayOf(0f, 5f)
        val ys = floatArrayOf(0f, 0f)
        val radii = floatArrayOf(20f, 8f)
        val sizes = listOf(size(w = 60f), size(w = 60f))
        val visible = MemoryGraphLabels.computeVisibleLabels(xs, ys, radii, sizes, 1f, 0f, 0f, 5f)
        assertTrue("bigger node keeps its label", visible.contains(0))
        assertFalse("smaller overlapping node is dropped", visible.contains(1))
    }

    private fun boxesOverlap(
        xs: FloatArray, ys: FloatArray, radii: FloatArray, sizes: List<MemoryGraphLabels.LabelSize>,
        i: Int, j: Int, scale: Float,
    ): Boolean {
        fun box(k: Int): FloatArray {
            val cx = xs[k] * scale
            val cy = ys[k] * scale
            val top = cy + radii[k] * scale + 2f
            val left = cx - sizes[k].width / 2f
            return floatArrayOf(left, top, left + sizes[k].width, top + sizes[k].height)
        }
        val a = box(i); val b = box(j)
        return a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]
    }
}
