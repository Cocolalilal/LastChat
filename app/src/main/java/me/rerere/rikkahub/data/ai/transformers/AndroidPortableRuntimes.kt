package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.generation.PortableDocumentRuntime
import me.rerere.ai.generation.PortableOcrRuntime
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository

fun androidPortableDocumentRuntime(context: Context): PortableDocumentRuntime {
    val parser = AndroidDocumentPromptParser(context)
    return PortableDocumentRuntime { url, fileName, mime ->
        when {
            mime == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true) -> {
                parser.extractPdfPages(url).joinToString("\n\n") { page ->
                    "--- Page ${page.pageNumber}:\n${page.text.trimEnd().ifBlank { "[No readable content found on this page]" }}"
                }
            }
            mime.contains("wordprocessingml") || fileName.endsWith(".docx", ignoreCase = true) ->
                parser.parseDocx(url)
            isSupportedTextDocument(url, fileName, mime) -> parser.readText(url)
            else -> null
        }
    }
}

fun androidPortableOcrRuntime(
    chatAttachmentRepository: ChatAttachmentRepository,
): PortableOcrRuntime = PortableOcrRuntime { url ->
    chatAttachmentRepository.resolveAttachmentOcrText(
        part = UIMessagePart.Image(url),
        ensureAvailable = true,
    )
}
