package me.rerere.tts.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TextChunkerTest {
    @Test
    fun `split assigns stable ordering and unique portable ids`() {
        val chunks = TextChunker(maxChunkLength = 8).split("Hello. World.")

        assertEquals(listOf(0, 1), chunks.map { it.index })
        assertEquals(listOf("Hello.", "World."), chunks.map { it.text })
        assertNotEquals(chunks[0].id, chunks[1].id)
    }
}
