package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamingFollowTest {
    @Test
    fun alreadyAtTheBottomDoesNotMove() {
        // Item ends where the bottom padding starts.
        assertEquals(0, streamingBottomDistance(itemEnd = 400, viewportEnd = 600, afterContentPadding = 200))
    }

    @Test
    fun aNewLineIsMeasuredAgainstTheRealBottomNotTheScreenEdge() {
        // A 60px line still sits above the screen edge, but the list is 60px from its resting bottom.
        assertEquals(60, streamingBottomDistance(itemEnd = 460, viewportEnd = 600, afterContentPadding = 200))
    }

    @Test
    fun aShortConversationDoesNotScrollUp() {
        assertEquals(0, streamingBottomDistance(itemEnd = 120, viewportEnd = 600, afterContentPadding = 200))
    }

    @Test
    fun aNewLineEasesInInsteadOfJumping() {
        val motion = StreamingFollowMotion()
        val frame = 1f / 120f
        val first = motion.step(distance = 64f, frameSeconds = frame, omega = 10f)
        assertTrue("first frame barely moves, got $first", first in 0f..1f)
        var remaining = 64f - first
        var maxFrameMove = first
        var frames = 1
        while (remaining > 0.5f && frames < 240) {
            val delta = motion.step(distance = remaining, frameSeconds = frame, omega = 10f)
            maxFrameMove = maxOf(maxFrameMove, delta)
            remaining -= delta
            frames++
        }
        // Spread over roughly half a second, never a big per-frame hop.
        assertTrue("took $frames frames", frames in 40..120)
        assertTrue("largest frame move $maxFrameMove", maxFrameMove < 3f)
    }

    @Test
    fun steadyLinesBlendIntoOneMotion() {
        val motion = StreamingFollowMotion()
        val frame = 1f / 120f
        var remaining = 0f
        var stoppedFrames = 0
        for (i in 0 until 600) {
            if (i % 36 == 0) remaining += 64f // a new line every 300 ms
            val delta = motion.step(distance = remaining, frameSeconds = frame, omega = 10f)
            remaining -= delta
            if (i > 120 && delta < 0.01f) stoppedFrames++
            assertTrue(remaining >= 0f)
        }
        assertEquals(0, stoppedFrames)
        // The lag stays around a line, so the newest text stays in view.
        assertTrue("lag $remaining", remaining < 96f)
    }

    @Test
    fun noDistanceDoesNotScrollAndDropsVelocity() {
        val motion = StreamingFollowMotion()
        motion.step(distance = 64f, frameSeconds = 0.05f, omega = 10f)
        assertEquals(0f, motion.step(distance = 0f, frameSeconds = 0.016f, omega = 10f), 0.001f)
        assertEquals(0f, motion.velocity, 0.001f)
    }

    @Test
    fun tallJumpsCatchUpFaster() {
        assertTrue(streamingFollowOmega(distancePx = 1200f, linePx = 60f) > streamingFollowOmega(distancePx = 60f, linePx = 60f))
    }
}
