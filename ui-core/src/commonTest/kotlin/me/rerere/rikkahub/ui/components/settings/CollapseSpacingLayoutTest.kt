package me.rerere.rikkahub.ui.components.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class CollapseSpacingLayoutTest {
    @Test
    fun solidRowsKeepAFullGap() {
        val layout = collapseSpacingLayout(
            heights = intArrayOf(10, 20),
            expandProgress = arrayOf(null, null),
            gap = 4,
        )
        assertEquals(0, layout.positions[0])
        assertEquals(14, layout.positions[1])
        assertEquals(34, layout.totalHeight)
    }

    @Test
    fun openSectionMatchesSpacedBy() {
        val layout = collapseSpacingLayout(
            heights = intArrayOf(10, 20, 10),
            expandProgress = arrayOf(null, 1f, null),
            gap = 4,
        )
        assertEquals(0, layout.positions[0])
        assertEquals(14, layout.positions[1])
        assertEquals(38, layout.positions[2])
        assertEquals(48, layout.totalHeight)
    }

    @Test
    fun closedMiddleSectionMatchesRemoval() {
        val closed = collapseSpacingLayout(
            heights = intArrayOf(10, 0, 10),
            expandProgress = arrayOf(null, 0f, null),
            gap = 4,
        )
        val removed = collapseSpacingLayout(
            heights = intArrayOf(10, 10),
            expandProgress = arrayOf(null, null),
            gap = 4,
        )
        assertEquals(removed.totalHeight, closed.totalHeight)
        assertEquals(removed.positions[0], closed.positions[0])
        assertEquals(removed.positions[1], closed.positions[2])
    }

    @Test
    fun closedTrailingSectionDoesNotLeaveAGap() {
        val closed = collapseSpacingLayout(
            heights = intArrayOf(10, 0),
            expandProgress = arrayOf(null, 0f),
            gap = 12,
        )
        assertEquals(10, closed.totalHeight)
        assertEquals(10, closed.positions[1])
    }

    @Test
    fun halfOpenSectionTakesHalfOfItsExtraGap() {
        val layout = collapseSpacingLayout(
            heights = intArrayOf(10, 10, 8),
            expandProgress = arrayOf(null, 0.5f, null),
            gap = 8,
        )
        // structural gap stays, the gap after the section is half, height is already scaled
        assertEquals(18, layout.positions[1])
        assertEquals(18 + 10 + 4, layout.positions[2])
        assertEquals(layout.positions[2] + 8, layout.totalHeight)
    }
}
