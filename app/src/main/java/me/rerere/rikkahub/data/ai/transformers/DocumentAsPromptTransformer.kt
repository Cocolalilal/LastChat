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

                                    else -> buildTextDocumentPrompt(document.fileName, parser.readText(document.url))
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
