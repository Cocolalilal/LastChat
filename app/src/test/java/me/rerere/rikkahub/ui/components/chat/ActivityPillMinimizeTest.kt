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
    fun earlierSiblingsLeaveTheAnchorBeforeLaterOnes() {
        val early = multiStepSiblingFly(0.2f, 0, 3)
        val late = multiStepSiblingFly(0.2f, 2, 3)
        assertTrue(early > late)
        assertEquals(0f, late, 0.0001f)
    }

    @Test
    fun aSingleSiblingUsesTheWholeReveal() {
        assertTrue(multiStepSiblingFly(0.5f, 0, 1) in 0.4f..0.6f)
    }
}
