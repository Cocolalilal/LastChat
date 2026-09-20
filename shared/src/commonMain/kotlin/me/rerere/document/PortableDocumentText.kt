package me.rerere.document

/**
 * Turns local document bytes into prompt text. PDF rendering stays platform-owned
 * (MuPDF on Android, PDFKit on iOS); DOCX/text extraction is portable.
 */
interface PlatformDocumentParser {
    fun parse(fileName: String, mimeType: String, bytes: ByteArray): String
}

object PortableDocumentText {
    fun parse(fileName: String, mimeType: String, bytes: ByteArray, pdfParser: ((ByteArray) -> String)? = null): String {
        val name = fileName.lowercase()
        val mime = mimeType.lowercase()
        return when {
            mime.contains("pdf") || name.endsWith(".pdf") ->
                pdfParser?.invoke(bytes) ?: "PDF parsing is unavailable on this platform."
            mime.contains("wordprocessingml") || name.endsWith(".docx") -> parseDocx(bytes)
            mime.startsWith("text/") ||
                name.endsWith(".txt") ||
                name.endsWith(".md") ||
                name.endsWith(".json") ||
                name.endsWith(".csv") -> bytes.decodeToString()
            else -> "Unsupported document type: ${fileName.ifBlank { mimeType }}"
        }
    }

    fun parseDocx(bytes: ByteArray): String {
        val xml = extractZipEntry(bytes, "word/document.xml")
            ?: return "Unable to find document content in DOCX file"
        return docxXmlToMarkdown(xml.decodeToString())
    }

    internal fun docxXmlToMarkdown(xml: String): String {
        val body = Regex("<w:body[\\s\\S]*?</w:body>", RegexOption.IGNORE_CASE)
            .find(xml)
            ?.value
            ?: xml
        val paragraphs = Regex("<w:p[\\s>][\\s\\S]*?</w:p>", RegexOption.IGNORE_CASE)
            .findAll(body)
            .map { match ->
                val heading = Regex("""<w:pStyle[^>]*w:val="Heading(\d+)"""", RegexOption.IGNORE_CASE)
                    .find(match.value)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                val text = Regex("<w:t[\\s>][\\s\\S]*?</w:t>|<w:t/>", RegexOption.IGNORE_CASE)
                    .findAll(match.value)
                    .joinToString("") { run ->
                        unescapeXml(run.value.replace(Regex("<[^>]+>"), ""))
                    }
                    .trim()
                if (text.isEmpty()) ""
                else if (heading != null) "${"#".repeat(heading.coerceIn(1, 6))} $text"
                else text
            }
            .filter { it.isNotBlank() }
            .toList()
        return paragraphs.joinToString("\n\n").ifBlank { unescapeXml(body.replace(Regex("<[^>]+>"), " ")).trim() }
    }

    internal fun extractZipEntry(archive: ByteArray, name: String): ByteArray? =
        PortableZip.extractEntry(archive, name)
}

internal fun inflateRawDeflate(data: ByteArray, maxBytes: Int): ByteArray? =
    inflateDeflate(data, maxBytes, raw = true)

/** zlib-wrapped deflate used by PNG `zTXt` / compressed `iTXt`. Same inflater as ZIP raw deflate. */
internal fun inflateZlib(data: ByteArray, maxBytes: Int): ByteArray? =
    inflateDeflate(data, maxBytes, raw = false)

internal expect fun inflateDeflate(data: ByteArray, maxBytes: Int, raw: Boolean): ByteArray?

private fun unescapeXml(value: String): String = value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
