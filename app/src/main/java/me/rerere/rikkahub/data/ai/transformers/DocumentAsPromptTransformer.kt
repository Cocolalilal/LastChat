package me.rerere.rikkahub.data.ai.transformers

import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.document.DocxParser
import me.rerere.document.PdfPageContent
import me.rerere.document.PdfParser
import java.io.File
import java.security.MessageDigest

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
    pages: List<PdfPageContent>,
    renderPage: (Int) -> File,
    ocrPage: suspend (Int, File) -> OcrExecutionResult,
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
                    ocrResult.promptText!!
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
            messages.map { message ->
                message.copy(
                    parts = message.parts.toMutableList().apply {
                        val documents = filterIsInstance<UIMessagePart.Document>()
                        if (documents.isNotEmpty()) {
                            documents.forEach { document ->
                                val liveOcrPageNumbers = mutableListOf<Int>()
                                val file = document.url.toUri().toFile()
                                val prompt = when (document.mime) {
                                    "application/pdf" -> parsePdfPrompt(
                                        file = file,
                                        fileName = document.fileName,
                                        cacheDir = ctx.context.cacheDir,
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
                                        file
                                    )
                                        .let { buildTextDocumentPrompt(document.fileName, it) }

                                    else -> buildTextDocumentPrompt(document.fileName, file.readText())
                                }
                                add(0, UIMessagePart.Text(prompt))
                            }
                        }
                    }
                )
            }
        }
    }

    private suspend fun parsePdfPrompt(
        file: File,
        fileName: String,
        cacheDir: File,
        onLiveOcrPage: suspend (Int) -> Unit = {},
    ): PdfPromptBuildResult {
        val pages = PdfParser.extractPages(file)
        return buildPdfPrompt(
            fileName = fileName,
            pages = pages,
            renderPage = { pageIndex ->
                renderPdfPageToCache(
                    pdfFile = file,
                    pageIndex = pageIndex,
                    cacheDir = cacheDir,
                )
            },
            ocrPage = { pageNumber, renderedFile ->
                OcrTransformer.performOcrWithMetadata(
                    UIMessagePart.Image(renderedFile.toUri().toString()),
                    onBeforeProviderCall = { onLiveOcrPage(pageNumber) },
                )
            }
        )
    }

    private fun parseDocxAsText(file: File): String {
        return DocxParser.parse(file)
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

    private fun renderPdfPageToCache(
        pdfFile: File,
        pageIndex: Int,
        cacheDir: File,
    ): File {
        val cacheKey = buildPdfRenderCacheKey(pdfFile)
        val outputFile = File(
            File(cacheDir, "pdf_ocr/$cacheKey"),
            "page-${pageIndex + 1}.png"
        )
        if (!outputFile.exists()) {
            PdfParser.renderPageAsPng(
                file = pdfFile,
                pageIndex = pageIndex,
                outputFile = outputFile,
            )
        }
        return outputFile
    }

    private fun buildPdfRenderCacheKey(file: File): String {
        val input = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
