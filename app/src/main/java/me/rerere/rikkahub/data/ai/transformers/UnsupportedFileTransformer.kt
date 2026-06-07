package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.provider.Modality
import me.rerere.rikkahub.data.ai.tools.LocalToolOption

/**
 * Transforms residual unsupported attachments into text references after the
 * dedicated document and OCR transformers have already had a chance to consume them.
 */
object UnsupportedFileTransformer : InputMessageTransformer {
    internal fun buildResidualImageFallbackText(
        fileName: String,
        sourceUrl: String,
        pythonEnabled: Boolean,
        linuxEnabled: Boolean = false,
    ): String {
        return if (linuxEnabled) {
            "\n[Image attachment: $fileName - The selected model cannot inspect image pixels directly in this turn because no OCR text was available. If the user explicitly wants tool-based file processing, Linux can use the original image. Use list_sandbox_files to inspect preloaded workspace files or import_attachment with this original URL if needed. URL: $sourceUrl]\n"
        } else if (pythonEnabled) {
            "\n[Image attachment: $fileName - The selected model cannot inspect image pixels directly in this turn because no OCR text was available. If the user explicitly wants tool-based file processing, Python can use the original image. Use list_sandbox_files to inspect preloaded sandbox files or import_attachment with this original URL if needed. URL: $sourceUrl]\n"
        } else {
            "\n[Image attachment: $fileName - The selected model cannot inspect image pixels directly in this turn, and no OCR text was available. Do not infer image contents from this attachment alone.]\n"
        }
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val isPythonEnabled = ctx.assistant.localTools.any { tool ->
            tool is LocalToolOption.PythonEngine
        }
        val isLinuxEnabled = ctx.assistant.localTools.any { tool ->
            tool is LocalToolOption.LinuxEnvironment
        }
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

                            if (!isNative && isLinuxEnabled) {
                                UIMessagePart.Text("\n[Attachment: ${part.fileName} (${part.mime}) - Linux can use this file. If run_linux_command reports a preloaded workspace filename, open that filename directly from /workspace. Otherwise use import_attachment with this original URL. URL: ${part.url}]\n")
                            } else if (!isNative && isPythonEnabled) {
                                UIMessagePart.Text("\n[Attachment: ${part.fileName} (${part.mime}) - Python can use this file. If eval_python reports a preloaded sandbox filename, open that filename directly in Python. Otherwise use import_attachment with this original URL. URL: ${part.url}]\n")
                            } else {
                                part
                            }
                        }
                        is UIMessagePart.Image -> {
                            if (!modelSupportsImages) {
                                val filename = part.url.substringAfterLast("/").substringBefore("?").ifEmpty { "image.jpg" }
                                UIMessagePart.Text(
                                    buildResidualImageFallbackText(
                                        fileName = filename,
                                        sourceUrl = part.url,
                                        pythonEnabled = isPythonEnabled,
                                        linuxEnabled = isLinuxEnabled,
                                    )
                                )
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

