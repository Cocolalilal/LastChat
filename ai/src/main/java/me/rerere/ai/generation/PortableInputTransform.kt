package me.rerere.ai.generation

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.document.PortableDocumentText

/**
 * Context-free input transformer used by [PortableGenerationPrepare]. Hosts inject
 * document/OCR/placeholder runtimes instead of Android Context.
 */
fun interface PortableInputTransformer {
    suspend fun transform(
        ctx: PortableTransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage>
}

class PortableTransformerContext(
    val model: Model,
    val workspaceEnabled: Boolean = false,
    val placeholders: Map<String, String> = emptyMap(),
    val documentRuntime: PortableDocumentRuntime? = null,
    val ocrRuntime: PortableOcrRuntime? = null,
    private val generationAnnotations: MutableList<UIMessageAnnotation> = mutableListOf(),
    private val progressAnnotations: MutableList<UIMessageAnnotation> = mutableListOf(),
    private val onProgressAnnotationsChanged: (suspend (List<UIMessageAnnotation>) -> Unit)? = null,
) {
    val annotations: List<UIMessageAnnotation>
        get() = generationAnnotations.toList()

    suspend fun recordGenerationAnnotation(annotation: UIMessageAnnotation) {
        generationAnnotations.add(annotation)
    }

    suspend fun upsertProgressAnnotation(
        annotation: UIMessageAnnotation,
        matches: (UIMessageAnnotation) -> Boolean,
    ) {
        val existingIndex = progressAnnotations.indexOfFirst(matches)
        if (existingIndex >= 0) {
            progressAnnotations[existingIndex] = annotation
        } else {
            progressAnnotations.add(annotation)
        }
        onProgressAnnotationsChanged?.invoke(progressAnnotations.toList())
    }
}

fun interface PortableDocumentRuntime {
    suspend fun parse(url: String, fileName: String, mime: String): String?
}

fun interface PortableOcrRuntime {
    suspend fun describeImage(url: String): String?
}

data class PortableInputTransformResult(
    val messages: List<UIMessage>,
    val annotations: List<UIMessageAnnotation>,
)

suspend fun List<UIMessage>.applyPortableInputTransformers(
    transformers: List<PortableInputTransformer>,
    ctx: PortableTransformerContext,
): PortableInputTransformResult {
    val messages = transformers.fold(this) { acc, transformer ->
        transformer.transform(ctx, acc)
    }
    return PortableInputTransformResult(messages = messages, annotations = ctx.annotations)
}

fun documentPromptText(fileName: String, extracted: String): String = """
    ## user sent a file: $fileName
    <content>
    ${extracted.ifBlank { "[No readable content found]" }}
    </content>
""".trimIndent()

fun buildImageOcrPrompt(content: String): String = """
    <image_file_ocr>
       $content
    </image_file_ocr>
    * The image_file_ocr tag contains the text description of an uploaded image. For models without direct image input, use this as the image content for this turn.
""".trimIndent()

object PortablePlaceholderTransformer : PortableInputTransformer {
    override suspend fun transform(
        ctx: PortableTransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.placeholders.isEmpty()) return messages
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(text = replacePlaceholders(part.text, ctx.placeholders))
                    } else {
                        part
                    }
                },
            )
        }
    }

    internal fun replacePlaceholders(text: String, placeholders: Map<String, String>): String {
        var result = text
        placeholders.forEach { (key, value) ->
            result = result
                .replace("{{$key}}", value, ignoreCase = true)
                .replace("{$key}", value, ignoreCase = true)
        }
        return result
    }
}

object PortableDocumentAsPromptTransformer : PortableInputTransformer {
    override suspend fun transform(
        ctx: PortableTransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val runtime = ctx.documentRuntime ?: return messages
        return messages.map { message ->
            val documents = message.parts.filterIsInstance<UIMessagePart.Document>()
            if (documents.isEmpty()) return@map message
            val extra = documents.mapNotNull { document ->
                val extracted = runtime.parse(document.url, document.fileName, document.mime)
                    ?: return@mapNotNull null
                UIMessagePart.Text(documentPromptText(document.fileName, extracted))
            }
            if (extra.isEmpty()) message else message.copy(parts = extra + message.parts)
        }
    }
}

object PortableOcrTransformer : PortableInputTransformer {
    override suspend fun transform(
        ctx: PortableTransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) return messages
        val runtime = ctx.ocrRuntime ?: return messages
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Image && part.url.isNotBlank()) {
                        val described = runtime.describeImage(part.url)
                        if (described.isNullOrBlank()) {
                            part
                        } else {
                            UIMessagePart.Text(
                                if (described.contains("<image_file_ocr>")) {
                                    described
                                } else {
                                    buildImageOcrPrompt(described)
                                },
                            )
                        }
                    } else {
                        part
                    }
                },
            )
        }
    }
}

object PortableUnsupportedFileTransformer : PortableInputTransformer {
    override suspend fun transform(
        ctx: PortableTransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val modelSupportsImages = ctx.model.inputModalities.contains(Modality.IMAGE)
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Document -> {
                            val mime = part.mime.lowercase()
                            val isNative = mime.startsWith("image/") ||
                                mime.startsWith("text/") ||
                                mime.startsWith("video/") ||
                                mime.startsWith("audio/") ||
                                mime == "application/pdf" ||
                                mime.contains("wordprocessingml")
                            if (isNative) {
                                part
                            } else if (ctx.workspaceEnabled) {
                                UIMessagePart.Text(
                                    "\n[Attachment: ${part.fileName} (${part.mime}) - The bound Linux workspace can process this file. Use workspace_shell or workspace_read_file with the original URL/content as needed. URL: ${part.url}]\n",
                                )
                            } else {
                                UIMessagePart.Text(
                                    "\n[Attachment: ${part.fileName} (${part.mime}) - This file format cannot be processed directly by the model. A Linux workspace must be bound to this assistant to extract or process archive and binary files.]\n",
                                )
                            }
                        }
                        is UIMessagePart.Image -> {
                            if (modelSupportsImages) {
                                part
                            } else {
                                val fileName = part.url.substringAfterLast("/")
                                    .substringBefore("?")
                                    .ifEmpty { "image.jpg" }
                                UIMessagePart.Text(
                                    residualImageFallbackText(fileName, part.url, ctx.workspaceEnabled),
                                )
                            }
                        }
                        else -> part
                    }
                },
            )
        }
    }

    fun residualImageFallbackText(
        fileName: String,
        sourceUrl: String,
        workspaceEnabled: Boolean,
    ): String {
        return if (workspaceEnabled) {
            "\n[Image attachment: $fileName - The selected model cannot inspect image pixels directly in this turn because no OCR text was available. If the user explicitly wants tool-based file processing, use the bound Linux workspace tools and import/read the original attachment URL as needed. URL: $sourceUrl]\n"
        } else {
            "\n[Image attachment: $fileName - The selected model cannot inspect image pixels directly in this turn, and no OCR text was available. Do not infer image contents from this attachment alone.]\n"
        }
    }
}

fun defaultPortableInputTransformers(): List<PortableInputTransformer> = listOf(
    PortablePlaceholderTransformer,
    PortableDocumentAsPromptTransformer,
    PortableOcrTransformer,
    PortableUnsupportedFileTransformer,
)

fun bytesDocumentRuntime(
    readBytes: suspend (url: String) -> ByteArray?,
    pdfParser: ((ByteArray) -> String)? = null,
): PortableDocumentRuntime = PortableDocumentRuntime { url, fileName, mime ->
    val bytes = readBytes(url) ?: return@PortableDocumentRuntime null
    PortableDocumentText.parse(fileName, mime, bytes, pdfParser)
}
