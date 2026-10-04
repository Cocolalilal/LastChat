package me.rerere.rikkahub.ui.components.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityPillMinimizeTest {
    @Test
    fun siblingsStayTuckedUntilTheRevealStarts() {
        assertEquals(0f, multiStepSiblingFly(0f, 0, 3), 0.0001f)
        assertEquals(0f, multiStepSiblingFly(0f, 2, 3), 0.0001f)
        assertEquals(1f, multiStepSiblingFly(1f, 0, 3), 0.0001f)
        assertEquals(1f, multiStepSiblingFly(1f, 2, 3), 0.0001f)
    }

    @Test
    fun everySiblingMovesAsSoonAsTheRevealLeavesZero() {
        val early = multiStepSiblingFly(0.05f, 0, 3)
        val late = multiStepSiblingFly(0.05f, 2, 3)
        assertTrue(early > late)
        assertTrue(late > 0f)
    }

    @Test
    fun earlierSiblingsLeadWithoutADeadStart() {
        val early = multiStepSiblingFly(0.2f, 0, 3)
        val late = multiStepSiblingFly(0.2f, 2, 3)
        assertTrue(early > late)
        assertTrue(late > 0.1f)
    }

    @Test
    fun aSingleSiblingUsesTheWholeReveal() {
        assertTrue(multiStepSiblingFly(0.5f, 0, 1) in 0.4f..0.6f)
    }

    @Test
    fun theSpringPastRestIsOnlyASoftOvershoot() {
        val past = multiStepSiblingFly(1.04f, 0, 3)
        assertTrue(past > 1f)
        assertTrue(past < 1.08f)
        assertEquals(1f, multiStepSiblingFly(1.04f, 0, 3).coerceIn(0f, 1f), 0.0001f)
    }

    @Test
    fun siblingsAreDarkestUnderTheAnchorAndClearAsTheyEmerge() {
        assertEquals(1f, multiStepSiblingShade(0f), 0.0001f)
        assertTrue(multiStepSiblingShade(0.12f) > 0.55f)
        assertEquals(0f, multiStepSiblingShade(0.48f), 0.0001f)
        assertEquals(0f, multiStepSiblingShade(1.04f), 0.0001f)
    }

    @Test
    fun openCompactPillsStayUpWhileSiblingsAreStillOut() {
        val hiddenByProgress = multiStepCompactAlpha(0.5f)
        assertEquals(0f, hiddenByProgress, 0.0001f)
        val held = multiStepExpandCompactAlpha(0.5f, siblingReveal = 0.8f)
        assertTrue(held > 0.5f)
        assertEquals(0f, multiStepExpandCompactAlpha(1f, siblingReveal = 0f), 0.0001f)
    }
}
