package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart

internal const val PDF_PAGE_OCR_TEXT_THRESHOLD = 32

internal data class PdfPromptBuildResult(
    val prompt: String,
    val ocrPageNumbers: List<Int> = emptyList(),
)

internal fun shouldUsePdfPageOcr(text: String, threshold: Int = PDF_PAGE_OCR_TEXT_THRESHOLD): Boolean {
    return text.count { !it.isWhitespace() } < threshold
}

internal suspend fun buildPdfPrompt(
    fileName: String,
    pages: List<DocumentTextPage>,
    renderPage: (Int) -> String,
    ocrPage: suspend (Int, String) -> OcrExecutionResult,
): PdfPromptBuildResult {
    val ocrPageNumbers = mutableListOf<Int>()
    val content = buildString {
        pages.forEach { page ->
            appendLine("--- Page ${page.pageNumber}:")
            val pageContent = if (shouldUsePdfPageOcr(page.text)) {
                val renderedPage = renderPage(page.pageNumber - 1)
                val ocrResult = ocrPage(page.pageNumber, renderedPage)
                if (ocrResult.consumesImageInput()) {
                    ocrPageNumbers += page.pageNumber
                    ocrResult.promptText.orEmpty()
                } else {
                    page.text.trimEnd().ifBlank { "[No readable content found on this page]" }
                }
            } else {
                page.text.trimEnd()
            }
            appendLine(pageContent.trimEnd())
            appendLine()
        }
    }.trimEnd().ifBlank { "[No readable content found]" }

    return PdfPromptBuildResult(
        prompt = """
            ## user sent a file: $fileName
            <content>
            $content
            </content>
        """.trimIndent(),
        ocrPageNumbers = ocrPageNumbers,
    )
}

object DocumentAsPromptTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return withContext(Dispatchers.IO) {
            val parser = AndroidDocumentPromptParser(ctx.context)
            messages.map { message ->
                message.copy(
                    parts = message.parts.toMutableList().apply {
                        val documents = filterIsInstance<UIMessagePart.Document>()
                        if (documents.isNotEmpty()) {
                            documents.forEach { document ->
                                val liveOcrPageNumbers = mutableListOf<Int>()
                                val prompt = when (document.mime) {
                                    "application/pdf" -> parsePdfPrompt(
                                        parser = parser,
                                        documentUrl = document.url,
                                        fileName = document.fileName,
                                        onLiveOcrPage = { pageNumber ->
                                            if (!liveOcrPageNumbers.contains(pageNumber)) {
                                                liveOcrPageNumbers += pageNumber
                                            }
                                            ctx.upsertProgressAnnotation(
                                                annotation = UIMessageAnnotation.OcrActivity(
                                                    source = UIMessageAnnotation.OcrActivity.Source.PDF,
                                                    fileName = document.fileName,
                                                    pageNumbers = liveOcrPageNumbers.toList(),
                                                ),
                                                matches = { annotation ->
                                                    annotation is UIMessageAnnotation.OcrActivity &&
                                                        annotation.source == UIMessageAnnotation.OcrActivity.Source.PDF &&
                                                        annotation.fileName == document.fileName
                                                }
                                            )
                                        },
                                    ).also { result ->
                                        if (result.ocrPageNumbers.isNotEmpty()) {
                                            ctx.recordGenerationAnnotation(
                                                UIMessageAnnotation.OcrActivity(
                                                    source = UIMessageAnnotation.OcrActivity.Source.PDF,
                                                    fileName = document.fileName,
                                                    pageNumbers = result.ocrPageNumbers,
                                                )
                                            )
                                        }
                                    }.prompt
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> parseDocxAsText(
                                        parser = parser,
                                        documentUrl = document.url,
                                    )
                                        .let { buildTextDocumentPrompt(document.fileName, it) }

                                    else -> {
                                        if (isSupportedTextDocument(document.url, document.fileName, document.mime)) {
                                            buildTextDocumentPrompt(document.fileName, parser.readText(document.url))
                                        } else {
                                            null
                                        }
                                    }
                                }
                                if (prompt != null) {
                                    add(0, UIMessagePart.Text(prompt))
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private suspend fun parsePdfPrompt(
        parser: DocumentPromptParser,
        documentUrl: String,
        fileName: String,
        onLiveOcrPage: suspend (Int) -> Unit = {},
    ): PdfPromptBuildResult {
        val pages = parser.extractPdfPages(documentUrl)
        return buildPdfPrompt(
            fileName = fileName,
            pages = pages,
            renderPage = { pageIndex ->
                parser.renderPdfPageAsImageUrl(documentUrl, pageIndex)
            },
            ocrPage = { pageNumber, renderedImageUrl ->
                OcrTransformer.performOcrWithMetadata(
                    UIMessagePart.Image(renderedImageUrl),
                    onBeforeProviderCall = { onLiveOcrPage(pageNumber) },
                )
            }
        )
    }

    private fun parseDocxAsText(
        parser: DocumentPromptParser,
        documentUrl: String,
    ): String {
        return parser.parseDocx(documentUrl)
    }

    private fun buildTextDocumentPrompt(fileName: String, content: String): String {
        return """
            ## user sent a file: $fileName
            <content>
            ```
            $content
            ```
            </content>
        """.trimIndent()
    }
}

internal const val MAX_TEXT_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB

internal val ARCHIVE_AND_BINARY_EXTENSIONS = setOf(
    "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "z", "lz", "lzma", "lzh",
    "apk", "jar", "war", "ear", "aab", "dex", "class",
    "iso", "img", "dmg", "vmdk", "qcow2",
    "bin", "exe", "dll", "so", "dylib", "elf", "o", "obj", "pyc", "pyo", "pyd",
    "db", "sqlite", "sqlite3", "dat", "bak"
)

internal val EXPLICIT_BINARY_MIME_TYPES = setOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-tar",
    "application/gzip",
    "application/x-gzip",
    "application/x-bzip2",
    "application/x-7z-compressed",
    "application/x-rar-compressed",
    "application/octet-stream",
    "application/vnd.android.package-archive",
    "application/x-executable",
    "application/x-sharedlib",
    "application/java-archive"
)

fun isArchiveOrBinaryFile(fileName: String, mime: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext in ARCHIVE_AND_BINARY_EXTENSIONS) return true
    if (mime.lowercase() in EXPLICIT_BINARY_MIME_TYPES) return true
    return false
}

internal fun isSupportedTextDocument(
    documentUrl: String,
    fileName: String,
    mime: String,
): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext in ARCHIVE_AND_BINARY_EXTENSIONS) return false
    if (mime.lowercase() in EXPLICIT_BINARY_MIME_TYPES) return false

    val file: java.io.File = runCatching {
        java.io.File(java.net.URI(documentUrl))
    }.getOrNull() ?: runCatching {
        java.io.File(documentUrl.removePrefix("file://"))
    }.getOrNull() ?: return false

    if (!file.exists() || !file.isFile) return false

    // Size limit check (max 5MB for direct text embedding into prompt)
    if (file.length() > MAX_TEXT_FILE_SIZE_BYTES) return false

    // Binary byte sample check (first 4KB for null bytes)
    return runCatching {
        file.inputStream().use { stream ->
            val buffer = ByteArray(4096)
            val read = stream.read(buffer)
            if (read > 0) {
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) {
                        return@use false // Null byte indicates binary file
                    }
                }
            }
            true
        }
    }.getOrDefault(false)
}

