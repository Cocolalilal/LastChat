package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.provider.Modality
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.tools.LocalToolOption

/**
 * Transforms unsupported file attachments into text references when Python tool is enabled.
 * This ensures the LLM sees the file availability without crashing on binary content.
 * 
 * Also handles images when the model doesn't support image input - converts them to
 * text annotations so the model can use import_attachment to access them in Python.
 */
object UnsupportedFileTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val isPythonEnabled = ctx.assistant.localTools.any { tool -> 
             tool is LocalToolOption.PythonEngine 
        }

        if (!isPythonEnabled) return messages
        
        val modelSupportsImages = ctx.model.inputModalities.contains(Modality.IMAGE)

        return messages.map { msg ->
            if (msg.role == me.rerere.ai.core.MessageRole.USER) {
                msg.copy(parts = msg.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Document -> {
                            // Supported native types (Images/Video/Audio/Text/PDF)
                            val isNative = part.mime.startsWith("image/") || 
                                         part.mime.startsWith("text/") ||
                                         part.mime.startsWith("video/") ||
                                         part.mime.startsWith("audio/") ||
                                         part.mime == "application/pdf"
                            
                            if (!isNative) {
                                 UIMessagePart.Text("\n[Attachment: ${part.fileName} (${part.mime}) - Available for Python via eval_python.attachments (auto-import) or import_attachment tool. After running, use list_sandbox_files to verify files. URL: ${part.url}]\n")
                            } else {
                                part
                            }
                        }
                        is UIMessagePart.Image -> {
                            // If model doesn't support images BUT Python is enabled,
                            // convert to text annotation so model can import via Python
                            if (!modelSupportsImages) {
                                val filename = part.url.substringAfterLast("/").substringBefore("?").ifEmpty { "image.jpg" }
                                UIMessagePart.Text("\n[Image attachment: $filename - Available for Python via eval_python.attachments (auto-import) or import_attachment tool. After running, use list_sandbox_files to verify files. URL: ${part.url}]\n")
                            } else {
                                part
                            }
                        }
                        else -> part
                    }
                })
            } else {
                msg
            }
        }
    }
}

