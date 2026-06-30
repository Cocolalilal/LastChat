package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import androidx.core.net.toFile
import androidx.core.net.toUri
import me.rerere.document.DocxParser
import me.rerere.document.PdfParser
import java.io.File
import java.security.MessageDigest

internal data class DocumentTextPage(
    val pageNumber: Int,
    val text: String,
)

internal interface DocumentPromptParser {
    fun extractPdfPages(documentUrl: String): List<DocumentTextPage>

    fun renderPdfPageAsImageUrl(documentUrl: String, pageIndex: Int): String

    fun parseDocx(documentUrl: String): String

    fun readText(documentUrl: String): String
}

internal class AndroidDocumentPromptParser(
    private val context: Context,
) : DocumentPromptParser {
    override fun extractPdfPages(documentUrl: String): List<DocumentTextPage> {
        return PdfParser.extractPages(documentUrl.toFile()).map { page ->
            DocumentTextPage(
                pageNumber = page.pageNumber,
                text = page.text,
            )
        }
    }

    override fun renderPdfPageAsImageUrl(documentUrl: String, pageIndex: Int): String {
        val pdfFile = documentUrl.toFile()
        val cacheKey = buildPdfRenderCacheKey(pdfFile)
        val outputFile = File(
            File(context.cacheDir, "pdf_ocr/$cacheKey"),
            "page-${pageIndex + 1}.png"
        )
        if (!outputFile.exists()) {
            PdfParser.renderPageAsPng(
                file = pdfFile,
                pageIndex = pageIndex,
                outputFile = outputFile,
            )
        }
        return outputFile.toUri().toString()
    }

    override fun parseDocx(documentUrl: String): String {
        return DocxParser.parse(documentUrl.toFile())
    }

    override fun readText(documentUrl: String): String {
        return documentUrl.toFile().readText()
    }

    private fun String.toFile(): File {
        return toUri().toFile()
    }

    private fun buildPdfRenderCacheKey(file: File): String {
        val input = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
