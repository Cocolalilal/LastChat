package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationHandlerOcrPlaceholderTest {
    @Test
    fun `upsertOcrPlaceholder appends trailing assistant placeholder when absent`() {
        val messages = listOf(UIMessage.user("hello"))
        val annotations = listOf(
            UIMessageAnnotation.OcrActivity(
                source = UIMessageAnnotation.OcrActivity.Source.PDF,
                fileName = "scan.pdf",
                pageNumbers = listOf(1),
            )
        )

        val updated = messages.upsertOcrPlaceholder(annotations)

        assertEquals(2, updated.size)
        val placeholder = updated.last()
        assertTrue(placeholder.isTrailingOcrPlaceholder())
        assertEquals(annotations, placeholder.annotations)
    }

    @Test
    fun `upsertOcrPlaceholder replaces existing OCR placeholder annotations`() {
        val progressAnnotations = listOf(
            UIMessageAnnotation.OcrActivity(
                source = UIMessageAnnotation.OcrActivity.Source.PDF,
                fileName = "scan.pdf",
                pageNumbers = listOf(1),
            )
        )
        val finalAnnotations = listOf(
            UIMessageAnnotation.OcrActivity(
                source = UIMessageAnnotation.OcrActivity.Source.PDF,
                fileName = "scan.pdf",
                pageNumbers = listOf(1, 2),
            )
        )

        val updated = listOf(UIMessage.user("hello"))
            .upsertOcrPlaceholder(progressAnnotations)
            .upsertOcrPlaceholder(finalAnnotations)

        assertEquals(2, updated.size)
        assertEquals(finalAnnotations, updated.last().annotations)
    }

    @Test
    fun `upsertOcrPlaceholder reuses blank assistant draft`() {
        val blankAssistant = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = emptyList(),
            versionTag = "regen-v2",
        )
        val annotations = listOf(
            UIMessageAnnotation.OcrActivity(
                source = UIMessageAnnotation.OcrActivity.Source.IMAGE,
                fileName = "photo.png",
            )
        )

        val updated = listOf(UIMessage.user("hello"), blankAssistant).upsertOcrPlaceholder(annotations)

        assertEquals(2, updated.size)
        assertEquals(blankAssistant.id, updated.last().id)
        assertEquals("regen-v2", updated.last().versionTag)
        assertEquals(annotations, updated.last().annotations)
    }

    @Test
    fun `dropTrailingOcrPlaceholder removes OCR-only assistant placeholder`() {
        val placeholder = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = emptyList(),
            annotations = listOf(
                UIMessageAnnotation.OcrActivity(
                    source = UIMessageAnnotation.OcrActivity.Source.IMAGE,
                    fileName = "photo.png",
                )
            ),
        )

        val updated = listOf(UIMessage.user("hello"), placeholder).dropTrailingOcrPlaceholder()

        assertEquals(1, updated.size)
        assertFalse(updated.last().isTrailingOcrPlaceholder())
    }

    @Test
    fun `dropTrailingOcrPlaceholder keeps non-placeholder assistant messages`() {
        val assistantReply = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("done")),
            annotations = listOf(
                UIMessageAnnotation.OcrActivity(
                    source = UIMessageAnnotation.OcrActivity.Source.IMAGE,
                    fileName = "photo.png",
                )
            ),
        )

        val updated = listOf(UIMessage.user("hello"), assistantReply).dropTrailingOcrPlaceholder()

        assertEquals(2, updated.size)
        assertEquals("done", (updated.last().parts.single() as UIMessagePart.Text).text)
    }
}
