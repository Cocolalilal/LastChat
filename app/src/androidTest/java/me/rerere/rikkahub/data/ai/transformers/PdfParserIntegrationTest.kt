package me.rerere.rikkahub.data.ai.transformers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.document.PdfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfParserIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun scannedPdfPagesRenderAndTriggerOcrFallback() = runBlocking {
        val pdfFile = createPdf("scanned-only.pdf") { pdf ->
            pdf.addBitmapPage("Scanned OCR sample page")
        }

        val pages = PdfParser.extractPages(pdfFile)
        assertEquals(1, pages.size)
        assertTrue(shouldUsePdfPageOcr(pages.single().text))

        val renderedFile = pdfFile.parentFile!!.resolve("rendered-page-1.png")
        PdfParser.renderPageAsPng(pdfFile, 0, renderedFile)
        assertTrue(renderedFile.exists())
        assertTrue(renderedFile.length() > 0L)

        val prompt = buildPdfPrompt(
            fileName = pdfFile.name,
            pages = pages.map { page ->
                DocumentTextPage(
                    pageNumber = page.pageNumber,
                    text = page.text,
                )
            },
            renderPage = { pageIndex ->
                pdfFile.parentFile!!.resolve("ocr-page-${pageIndex + 1}.png").also { file ->
                    PdfParser.renderPageAsPng(pdfFile, pageIndex, file)
                }.absolutePath
            },
            ocrPage = { pageNumber, _ ->
                OcrExecutionResult(
                    promptText = "<image_file_ocr>ocr page $pageNumber</image_file_ocr>",
                    status = OcrStatus.SUCCESS,
                )
            }
        )

        assertTrue(prompt.prompt.contains("<image_file_ocr>ocr page 1</image_file_ocr>"))
        assertEquals(listOf(1), prompt.ocrPageNumbers)
    }

    @Test
    fun mixedPdfKeepsTextPagesAndFallsBackForScannedPages() = runBlocking {
        val pdfFile = createPdf("mixed.pdf") { pdf ->
            pdf.addTextPage("Extractable text from page one should stay as extracted text.")
            pdf.addBitmapPage("Scanned image page two")
        }

        val pages = PdfParser.extractPages(pdfFile)
        assertEquals(2, pages.size)
        assertTrue(pages[0].text.contains("Extractable text"))
        assertTrue(shouldUsePdfPageOcr(pages[1].text))

        val prompt = buildPdfPrompt(
            fileName = pdfFile.name,
            pages = pages.map { page ->
                DocumentTextPage(
                    pageNumber = page.pageNumber,
                    text = page.text,
                )
            },
            renderPage = { pageIndex ->
                pdfFile.parentFile!!.resolve("mixed-ocr-page-${pageIndex + 1}.png").also { file ->
                    PdfParser.renderPageAsPng(pdfFile, pageIndex, file)
                }.absolutePath
            },
            ocrPage = { pageNumber, _ ->
                OcrExecutionResult(
                    promptText = "<image_file_ocr>ocr page $pageNumber</image_file_ocr>",
                    status = OcrStatus.SUCCESS,
                )
            }
        )

        assertTrue(prompt.prompt.contains("Extractable text from page one"))
        assertTrue(prompt.prompt.contains("<image_file_ocr>ocr page 2</image_file_ocr>"))
        assertEquals(listOf(2), prompt.ocrPageNumbers)
    }

    private fun createPdf(
        fileName: String,
        builder: (PdfDocumentBuilder) -> Unit,
    ): File {
        val directory = File(context.cacheDir, "pdf-parser-tests").apply { mkdirs() }
        val file = File(directory, fileName)
        val pdf = PdfDocument()
        try {
            builder(PdfDocumentBuilder(pdf))
            file.outputStream().use { output ->
                pdf.writeTo(output)
            }
        } finally {
            pdf.close()
        }
        return file
    }

    private class PdfDocumentBuilder(private val pdf: PdfDocument) {
        private var pageNumber = 1

        fun addTextPage(text: String) {
            val page = startPage()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 18f
            }
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText(text, 40f, 100f, paint)
            finishPage(page)
        }

        fun addBitmapPage(text: String) {
            val page = startPage()
            val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 42f
            }
            canvas.drawColor(Color.WHITE)
            canvas.drawText(text, 40f, 200f, paint)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            finishPage(page)
            bitmap.recycle()
        }

        private fun startPage(): PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            return pdf.startPage(pageInfo)
        }

        private fun finishPage(page: PdfDocument.Page) {
            pdf.finishPage(page)
            pageNumber += 1
        }
    }
}
