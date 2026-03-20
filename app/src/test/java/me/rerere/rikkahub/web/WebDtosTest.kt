package me.rerere.rikkahub.web

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDtosTest {
    @Test
    fun toUiMessageParts_preservesDocumentFields() {
        val converted = listOf(
            WebMessagePartDto.Document(
                url = "file:///tmp/report.pdf",
                fileName = "report.pdf",
                mime = "application/pdf",
            )
        ).toUiMessageParts()

        assertEquals(1, converted.size)
        assertTrue(converted.single() is UIMessagePart.Document)

        val document = converted.single() as UIMessagePart.Document
        assertEquals("file:///tmp/report.pdf", document.url)
        assertEquals("report.pdf", document.fileName)
        assertEquals("application/pdf", document.mime)
    }
}
