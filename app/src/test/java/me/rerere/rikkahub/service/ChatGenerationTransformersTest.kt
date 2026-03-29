package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.UnsupportedFileTransformer
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGenerationTransformersTest {
    @Test
    fun `default input transformers run OCR before unsupported file fallback`() {
        assertEquals(
            listOf(
                PlaceholderTransformer,
                DocumentAsPromptTransformer,
                OcrTransformer,
                UnsupportedFileTransformer,
            ),
            defaultChatInputTransformers,
        )
    }
}
