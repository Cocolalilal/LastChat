package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitThinkTest {
    @Test
    fun `plain text has no reasoning`() {
        val (reasoning, text) = splitThink("Hello world")
        assertEquals("", reasoning)
        assertEquals("Hello world", text)
    }

    @Test
    fun `closed think tag splits reasoning and answer`() {
        val (reasoning, text) = splitThink("<think>let me think</think>The answer is 42")
        assertEquals("let me think", reasoning)
        assertEquals("The answer is 42", text)
    }

    @Test
    fun `unclosed think keeps trailing content as reasoning`() {
        val (reasoning, text) = splitThink("<think>still reasoning")
        assertEquals("still reasoning", reasoning)
        assertEquals("", text)
    }
}
