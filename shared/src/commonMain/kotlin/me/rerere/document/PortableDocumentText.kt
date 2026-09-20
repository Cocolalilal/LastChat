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

    internal fun extractZipEntry(archive: ByteArray, name: String): ByteArray? {
        val eocd = findEndOfCentralDirectory(archive) ?: return null
        val entryCount = readU16(archive, eocd + 10)
        val directorySize = readU32(archive, eocd + 12)
        val directoryOffset = readU32(archive, eocd + 16)
        if (directoryOffset <= 0 || directoryOffset + directorySize > archive.size) return null
        var offset = directoryOffset.toInt()
        var remaining = entryCount
        while (remaining > 0 && offset + 46 <= archive.size) {
            if (readU32(archive, offset) != CEN_SIGNATURE) return null
            val method = readU16(archive, offset + 10)
            val compressedSize = readU32(archive, offset + 20)
            val uncompressedSize = readU32(archive, offset + 24)
            val nameLength = readU16(archive, offset + 28)
            val extraLength = readU16(archive, offset + 30)
            val commentLength = readU16(archive, offset + 32)
            val localHeaderOffset = readU32(archive, offset + 42).toInt()
            val entryName = archive.decodeToString(offset + 46, offset + 46 + nameLength)
            if (entryName == name) {
                if (localHeaderOffset + 30 > archive.size) return null
                val localNameLength = readU16(archive, localHeaderOffset + 26)
                val localExtraLength = readU16(archive, localHeaderOffset + 28)
                val dataStart = localHeaderOffset + 30 + localNameLength + localExtraLength
                val dataEnd = dataStart + compressedSize.toInt()
                if (dataEnd > archive.size) return null
                val compressed = archive.copyOfRange(dataStart, dataEnd)
                return when (method) {
                    METHOD_STORE -> compressed
                    METHOD_DEFLATE -> inflateRawDeflate(compressed, uncompressedSize.toInt().coerceAtLeast(compressed.size))
                    else -> null
                }
            }
            offset += 46 + nameLength + extraLength + commentLength
            remaining--
        }
        return null
    }
}

internal expect fun inflateRawDeflate(data: ByteArray, maxBytes: Int): ByteArray?

private const val EOCD_SIGNATURE = 0x06054b50L
private const val CEN_SIGNATURE = 0x02014b50L
private const val METHOD_STORE = 0
private const val METHOD_DEFLATE = 8

private fun findEndOfCentralDirectory(archive: ByteArray): Int? {
    val min = (archive.size - 22 - 65_535).coerceAtLeast(0)
    for (offset in archive.size - 22 downTo min) {
        if (readU32(archive, offset) == EOCD_SIGNATURE) return offset
    }
    return null
}

private fun readU16(data: ByteArray, offset: Int): Int {
    if (offset + 1 >= data.size) return 0
    return (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)
}

private fun readU32(data: ByteArray, offset: Int): Long {
    if (offset + 3 >= data.size) return 0
    return (data[offset].toLong() and 0xff) or
        ((data[offset + 1].toLong() and 0xff) shl 8) or
        ((data[offset + 2].toLong() and 0xff) shl 16) or
        ((data[offset + 3].toLong() and 0xff) shl 24)
}

private fun unescapeXml(value: String): String = value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
