package me.rerere.common.platform.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.rerere.document.PlatformDocumentParser
import me.rerere.document.PortableDocumentText
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosPlatformDocumentParser : PlatformDocumentParser {
    override fun parse(fileName: String, mimeType: String, bytes: ByteArray): String {
        return PortableDocumentText.parse(
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            pdfParser = ::parsePdf,
        )
    }

    private fun parsePdf(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "The PDF file is empty"
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val document = PDFDocument(data = data)
            ?: return "Unable to open the PDF document"
        val pageCount = document.pageCount.toInt()
        if (pageCount <= 0) return "The PDF document has no pages"
        return buildString {
            for (index in 0 until pageCount) {
                val page = document.pageAtIndex(index.toULong()) ?: continue
                val text = page.string?.trim().orEmpty()
                if (text.isBlank()) continue
                if (isNotEmpty()) appendLine().appendLine()
                append("Page ${index + 1}")
                appendLine()
                append(text)
            }
        }.ifBlank { "The PDF document did not contain extractable text" }
    }
}
