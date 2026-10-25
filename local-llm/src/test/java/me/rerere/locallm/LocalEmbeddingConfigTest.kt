package me.rerere.locallm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEmbeddingConfigTest {
    @Test
    fun `only self-contained LiteRT bundles use the embedding engine`() {
        assertTrue(usesLiteRtEmbeddingEngine("/models/embeddinggemma-2-text-270m.litertlm"))
        assertFalse(usesLiteRtEmbeddingEngine("/models/embeddinggemma-300m.tflite"))
    }

    @Test
    fun `x86 devices can run text bundles but not retired tflite files`() {
        assertTrue(supportsLocalEmbedding("embedding.litertlm", listOf("x86_64", "x86")))
        assertFalse(supportsLocalEmbedding("embedding.tflite", listOf("x86_64", "x86")))
        assertFalse(supportsLocalEmbedding("embedding.litertlm", listOf("armeabi-v7a")))
    }

    @Test
    fun `arm64 devices support text bundles and reject retired tflite files`() {
        assertTrue(supportsLocalEmbedding("embedding.litertlm", listOf("arm64-v8a")))
        assertFalse(supportsLocalEmbedding("embedding.tflite", listOf("arm64-v8a")))
    }

    @Test
    fun `embedding input uses the documented symmetric task and trims outer whitespace`() {
        assertEquals(
            "task: sentence similarity | text: A note\nwith two lines",
            embeddingGemma2Text(" \nA note\nwith two lines\t "),
        )
    }
}
