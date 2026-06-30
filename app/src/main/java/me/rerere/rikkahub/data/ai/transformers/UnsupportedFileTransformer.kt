package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.provider.Modality

/**
 * Transforms residual unsupported attachments into text references after the
 * dedicated document and OCR transformers have already had a chance to consume them.
 */
object UnsupportedFileTransformer : InputMessageTransformer {
    internal fun buildResidualImageFallbackText(
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

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val isWorkspaceEnabled = ctx.assistant.workspaceId != null
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

                            if (!isNative && isWorkspaceEnabled) {
                                UIMessagePart.Text("\n[Attachment: ${part.fileName} (${part.mime}) - The bound Linux workspace can process this file. Use workspace_shell or workspace_read_file with the original URL/content as needed. URL: ${part.url}]\n")
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
                                        workspaceEnabled = isWorkspaceEnabled,
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

