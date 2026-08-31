package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkTagTransformerTest {
    @Test
    fun testClosedThinkTag() {
        val input = listOf(
            UIMessage.assistant("<think>This is my thinking</think>Here is the final answer.")
        )

        val transformed = ThinkTagTransformer.transformMessages(input, finishUnclosed = false)
        val message = transformed.single()
        assertEquals(2, message.parts.size)

        val reasoning = message.parts[0] as UIMessagePart.Reasoning
        assertEquals("This is my thinking", reasoning.reasoning)
        assertNotNull(reasoning.finishedAt)

        val text = message.parts[1] as UIMessagePart.Text
        assertEquals("Here is the final answer.", text.text)
    }

    @Test
    fun testUnclosedThinkTagStreamingAndThenFinish() {
        val streamingInput = listOf(
            UIMessage.assistant("<think>Still thinking right now...")
        )

        // During streaming (finishUnclosed = false): finishedAt should be null
        val streamingTransformed = ThinkTagTransformer.transformMessages(streamingInput, finishUnclosed = false)
        val streamingMsg = streamingTransformed.single()
        val streamingReasoning = streamingMsg.parts.first() as UIMessagePart.Reasoning
        assertEquals("Still thinking right now...", streamingReasoning.reasoning)
        assertNull(streamingReasoning.finishedAt)

        // When generation finishes (finishUnclosed = true):
        // Running on the already-visually-transformed message must seal the reasoning part!
        val finishedTransformed = ThinkTagTransformer.transformMessages(streamingTransformed, finishUnclosed = true)
        val finishedMsg = finishedTransformed.single()
        val finishedReasoning = finishedMsg.parts.first() as UIMessagePart.Reasoning
        assertEquals("Still thinking right now...", finishedReasoning.reasoning)
        assertNotNull(finishedReasoning.finishedAt)
    }

    @Test
    fun testMultipleThinkTags() {
        val input = listOf(
            UIMessage.assistant("<think>First step</think>Middle text<think>Second step</think>Final conclusion")
        )

        val transformed = ThinkTagTransformer.transformMessages(input, finishUnclosed = false)
        val message = transformed.single()

        val reasoningParts = message.parts.filterIsInstance<UIMessagePart.Reasoning>()
        val textParts = message.parts.filterIsInstance<UIMessagePart.Text>()

        assertEquals(2, reasoningParts.size)
        assertEquals("First step", reasoningParts[0].reasoning)
        assertEquals("Second step", reasoningParts[1].reasoning)

        assertEquals(1, textParts.size)
        assertTrue(textParts[0].text.contains("Middle text"))
        assertTrue(textParts[0].text.contains("Final conclusion"))
    }
}
