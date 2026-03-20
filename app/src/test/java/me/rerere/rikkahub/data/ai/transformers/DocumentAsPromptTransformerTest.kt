package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.runBlocking
import me.rerere.document.PdfPageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DocumentAsPromptTransformerTest {
    @Test
    fun `shouldUsePdfPageOcr ignores whitespace when applying threshold`() {
        assertTrue(shouldUsePdfPageOcr(" short text "))
        assertFalse(shouldUsePdfPageOcr("This page has enough extracted text to skip OCR fallback."))
    }

    @Test
    fun `buildPdfPrompt mixes extracted text pages and OCR pages`() = runBlocking {
        val tempDir = Files.createTempDirectory("pdf-prompt-test").toFile()
        val renderedPageIndexes = mutableListOf<Int>()

        val prompt = buildPdfPrompt(
            fileName = "question.pdf",
            pages = listOf(
                PdfPageContent(
                    pageNumber = 1,
                    text = "This page already has enough extracted text for the model."
                ),
                PdfPageContent(
                    pageNumber = 2,
                    text = "  \nscan\n "
                )
            ),
            renderPage = { pageIndex ->
                renderedPageIndexes += pageIndex
                tempDir.resolve("page-${pageIndex + 1}.png").apply {
                    writeText("rendered")
                }
            },
            ocrPage = { pageNumber, renderedFile ->
                OcrExecutionResult(
                    promptText = "<image_file_ocr>OCR page $pageNumber from ${renderedFile.name}</image_file_ocr>",
                    status = OcrStatus.CACHE_HIT,
                )
            }
        )

        assertTrue(prompt.prompt.contains("## user sent a file: question.pdf"))
        assertTrue(prompt.prompt.contains("--- Page 1:"))
        assertTrue(prompt.prompt.contains("This page already has enough extracted text"))
        assertTrue(prompt.prompt.contains("--- Page 2:"))
        assertTrue(prompt.prompt.contains("<image_file_ocr>OCR page 2 from page-2.png</image_file_ocr>"))
        assertEquals(listOf(2), prompt.ocrPageNumbers)
        assertEquals(listOf(1), renderedPageIndexes)
    }
}
