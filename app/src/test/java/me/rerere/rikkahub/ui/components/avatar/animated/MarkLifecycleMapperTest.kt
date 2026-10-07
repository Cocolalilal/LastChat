package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkLifecycleMapperTest {
    @Test
    fun resolve_streamingWhenGeneratingWithTokens() {
        val life = MarkLifecycleMapper.resolve(
            AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
        )
        assertEquals(MarkLifecycle.Streaming, life)
        assertEquals("Happy", MarkLifecycleMapper.defaultExpression(life))
    }

    @Test
    fun resolve_thinkingWhenGeneratingWithoutTokens() {
        val life = MarkLifecycleMapper.resolve(
            AvatarMotionHints(isGenerating = true, hasStreamedTokens = false)
        )
        assertEquals(MarkLifecycle.Thinking, life)
        assertEquals("angered or concentrated", MarkLifecycleMapper.defaultExpression(life))
    }

    @Test
    fun resolve_listeningWhileTyping() {
        val life = MarkLifecycleMapper.resolve(
            AvatarMotionHints(isTyping = true)
        )
        assertEquals(MarkLifecycle.Listening, life)
    }

    @Test
    fun resolve_sleepingAfterLongIdle() {
        val life = MarkLifecycleMapper.resolve(
            AvatarMotionHints(idleMs = 120_000L)
        )
        assertEquals(MarkLifecycle.Sleeping, life)
        assertEquals("Sleeping", MarkLifecycleMapper.defaultExpression(life))
    }

    @Test
    fun glanceAtInput_onlyWhileTyping() {
        assertFalse(MarkLifecycleMapper.shouldGlanceAtInput(isTyping = false, random01 = 0.1f))
        assertTrue(MarkLifecycleMapper.shouldGlanceAtInput(isTyping = true, random01 = 0.1f))
        assertFalse(MarkLifecycleMapper.shouldGlanceAtInput(isTyping = true, random01 = 0.5f))
    }

    /**
     * Finished used to grow the mark (scale > 1), which read as a size jump. The
     * celebration is now a happy expression + small hop, so every state keeps rest size.
     */
    @Test
    fun pose_finishedCelebratesWithoutChangingSize() {
        val pose = MarkLifecycleMapper.pose(MarkLifecycle.Finished)
        assertEquals("center", pose.gazeMode)
        assertEquals("Happy", MarkLifecycleMapper.defaultExpression(MarkLifecycle.Finished))
        for (life in MarkLifecycle.entries) {
            assertEquals("$life scale", 1f, MarkLifecycleMapper.pose(life).scale, 1e-4f)
        }
    }
}
