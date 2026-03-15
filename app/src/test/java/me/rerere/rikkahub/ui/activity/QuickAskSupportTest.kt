package me.rerere.rikkahub.ui.activity

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAskSupportTest {
    @Test
    fun `buildQuickAskMessageParts creates a plain text user message`() {
        val parts = buildQuickAskMessageParts(
            text = "Explain this paragraph",
            attachments = emptyList()
        )

        assertEquals(1, parts.size)
        assertEquals("Explain this paragraph", (parts.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `buildQuickAskMessageParts keeps text and attachments together`() {
        val parts = buildQuickAskMessageParts(
            text = "What is in this file",
            attachments = listOf(
                QuickAskAttachment(
                    uri = "file:///tmp/doc.pdf",
                    fileName = "doc.pdf",
                    mimeType = "application/pdf"
                )
            )
        )

        assertEquals(2, parts.size)
        assertTrue(parts[0] is UIMessagePart.Text)
        assertTrue(parts[1] is UIMessagePart.Document)
        assertEquals("doc.pdf", (parts[1] as UIMessagePart.Document).fileName)
    }

    @Test
    fun `buildQuickAskTextContent appends custom prompt context`() {
        val text = buildQuickAskTextContent(
            text = "Source text",
            customPrompt = "Translate to Japanese"
        )

        assertEquals("Source text\n\nQuestion: Translate to Japanese", text)
    }

    @Test
    fun `continuation data preserves attachments and assistant choice`() {
        val continuation = QuickAskContinuationData(
            text = "Hello",
            attachments = listOf(
                QuickAskAttachment(
                    uri = "file:///tmp/image.png",
                    fileName = "image.png",
                    mimeType = "image/png"
                )
            ),
            aiResponse = "Hi",
            userPrompt = "Summarize it",
            assistantId = "assistant-123"
        )

        assertEquals("Hello", continuation.text)
        assertEquals(1, continuation.attachments.size)
        assertEquals("assistant-123", continuation.assistantId)
        assertEquals("Summarize it", continuation.userPrompt)
    }
}
