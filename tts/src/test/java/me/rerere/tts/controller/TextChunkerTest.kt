package me.rerere.tts.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    @Test
    fun `split assigns stable ordering and unique portable ids`() {
        val chunks = TextChunker(maxChunkLength = 8).split("Hello. World.")

        assertEquals(listOf(0, 1), chunks.map { it.index })
        assertEquals(listOf("Hello.", "World."), chunks.map { it.text })
        assertNotEquals(chunks[0].id, chunks[1].id)
    }

    @Test
    fun `split preserves paragraph breaks`() {
        val text = "Paragraph one sentence one. Paragraph one sentence two.\n\nParagraph two sentence one."
        val chunks = TextChunker(maxChunkLength = 300).split(text)

        assertEquals(2, chunks.size)
        assertEquals("Paragraph one sentence one. Paragraph one sentence two.", chunks[0].text)
        assertEquals("Paragraph two sentence one.", chunks[1].text)
    }

    @Test
    fun `split groups sentences within same paragraph up to max length`() {
        val text = "Short one. Short two. Short three."
        val chunks = TextChunker(maxChunkLength = 100).split(text)

        assertEquals(1, chunks.size)
        assertEquals("Short one. Short two. Short three.", chunks[0].text)
    }

    @Test
    fun `split avoids breaking on decimal numbers and versions`() {
        val text = "LastChat v1.4.5 was released with 3.14x speedup. The price is $19.99 today."
        val chunks = TextChunker(maxChunkLength = 300).split(text)

        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0].text)
    }

    @Test
    fun `split avoids breaking on common abbreviations`() {
        val text = "Dr. Smith and Mr. Brown arrived at 8 a.m. for the meeting. They discussed e.g. future plans."
        val chunks = TextChunker(maxChunkLength = 300).split(text)

        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0].text)
    }

    @Test
    fun `split handles CJK sentences properly`() {
        val text = "这是第一句话！这是第二句话。这是第三句话吗？"
        val chunks = TextChunker(maxChunkLength = 10).split(text)

        assertEquals(3, chunks.size)
        assertEquals("这是第一句话！", chunks[0].text)
        assertEquals("这是第二句话。", chunks[1].text)
        assertEquals("这是第三句话吗？", chunks[2].text)
    }

    @Test
    fun `split handles oversized sentences by splitting on clause boundaries`() {
        val longSentence = "This is a very long sentence that has multiple clauses, connected together with commas, and it continues to explain things in great detail, so that it exceeds the limit."
        val chunks = TextChunker(maxChunkLength = 70).split(longSentence)

        assertTrue(chunks.size > 1)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.text.length} should be <= 70", chunk.text.length <= 70)
        }
    }

    @Test
    fun `split returns empty for blank text`() {
        assertTrue(TextChunker().split("").isEmpty())
        assertTrue(TextChunker().split("   \n\n  \t ").isEmpty())
    }
}

