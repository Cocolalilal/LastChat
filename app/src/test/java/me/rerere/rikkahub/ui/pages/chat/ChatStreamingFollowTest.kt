package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamingFollowTest {
    @Test
    fun alreadyAtTheBottomDoesNotMove() {
        assertEquals(0, streamingBottomOverflow(itemOffset = 400, itemSize = 200, viewportEnd = 600))
    }

    @Test
    fun onlyTheHiddenTailScrolls() {
        assertEquals(40, streamingBottomOverflow(itemOffset = 100, itemSize = 540, viewportEnd = 600))
    }

    @Test
    fun aShortLastTurnDoesNotScrollUp() {
        assertEquals(0, streamingBottomOverflow(itemOffset = 480, itemSize = 80, viewportEnd = 600))
    }

    @Test
    fun aNewLineGlidesInsteadOfJumpingTheWholeOverflow() {
        val delta = streamingFollowScrollDelta(overflow = 64, frameMillis = 16f)
        assertTrue(delta > 20f)
        assertTrue(delta < 50f)
    }

    @Test
    fun noOverflowDoesNotScroll() {
        assertEquals(0f, streamingFollowScrollDelta(overflow = 0, frameMillis = 16f), 0.001f)
    }
}
