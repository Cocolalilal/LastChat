package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTextTest {
    @Test
    fun `large paste detection handles both long lines and many short lines`() {
        assertFalse(isLongTextPaste("a".repeat(7_999)))
        assertTrue(isLongTextPaste("a".repeat(8_000)))
        assertFalse(isLongTextPaste("\n".repeat(118)))
        assertTrue(isLongTextPaste("\n".repeat(119)))
        assertFalse(isLongTextPaste("Short clipboard text"))
    }

    @Test
    fun `million character message starts with a bounded preview`() {
        val text = "a".repeat(1_000_000)
        assertEquals(4_000, longMessagePreviewEnd(text))
        assertEquals(8_000, longMessagePreviewEnd(text, 4_000))
    }

    @Test
    fun `collapsed preview stays compact for paragraphs and blank lines`() {
        assertEquals(1_200, longMessagePreviewEnd("a".repeat(100_000), maxChars = 1_200, maxLines = 12))
        assertEquals(12, longMessagePreviewEnd("\n".repeat(100_000), maxChars = 1_200, maxLines = 12))
    }

    @Test
    fun `many blank lines cannot create an enormous collapsed bubble`() {
        assertEquals(60, longMessagePreviewEnd("\n".repeat(100_000)))
    }

    @Test
    fun `expansion reaches the exact original text without skipping or repeating characters`() {
        val text = ("Hello 👋 world\n".repeat(900)) + "Final line"
        val chunks = mutableListOf<String>()
        var position = 0
        while (position < text.length) {
            val end = longMessagePreviewEnd(text, position)
            assertTrue(end > position)
            assertTrue(end - position <= LONG_MESSAGE_CHUNK_SIZE)
            chunks += text.substring(position, end)
            position = end
        }
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun `preview preserves emoji at chunk boundary`() {
        val text = "a".repeat(3_999) + "😀" + "tail"
        val end = longMessagePreviewEnd(text)
        assertEquals(3_999, end)
        assertEquals("😀tail", text.substring(end))
    }

    @Test
    fun `short messages are fully visible`() {
        assertEquals(0, longMessagePreviewEnd(""))
        assertEquals(5, longMessagePreviewEnd("hello"))
        assertEquals(4_000, longMessagePreviewEnd("a".repeat(4_000)))
    }
}
