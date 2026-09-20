package me.rerere.ai.generation

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.MessageTemplateContext
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
    val messageTemplate: String = "{{ message }}",
    val templateRuntime: PortableTemplateRuntime? = null,
    val templateTime: String = "",
    val templateDate: String = "",
    val workspaceReminder: PortableWorkspaceReminder? = null,
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

data class PortableWorkspaceReminder(
    val name: String,
    val ready: Boolean,
    val toolCapable: Boolean,
    val cwd: String? = null,
    val unavailableReason: String? = null,
)

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

object PortableTemplateTransformer : PortableInputTransformer {
    override suspend fun transform(
        ctx: PortableTransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val template = ctx.messageTemplate.ifBlank { "{{ message }}" }
        val runtime = ctx.templateRuntime ?: DefaultPortableTemplateRuntime
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        val context = MessageTemplateContext.build(
                            message = part.text,
                            role = message.role,
                            time = ctx.templateTime,
                            date = ctx.templateDate,
                        ).asMap()
                        part.copy(text = runtime.render(template, context))
                    } else {
                        part
                    }
                },
            )
        }
    }
}

object PortableWorkspaceReminderTransformer : PortableInputTransformer {
    override suspend fun transform(
        ctx: PortableTransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val reminder = ctx.workspaceReminder ?: return messages
        val prompt = buildPortableWorkspaceReminderPrompt(reminder)
        val withSystem = appendSystemPrompt(messages, prompt)
        val cwd = reminder.cwd
        if (cwd.isNullOrBlank()) return withSystem
        return withSystem.map { message ->
            if (message.role != MessageRole.USER) {
                message
            } else {
                val synced = message.parts.mapNotNull { part ->
                    val url = when (part) {
                        is UIMessagePart.Image -> part.url
                        is UIMessagePart.Document -> part.url
                        else -> null
                    } ?: return@mapNotNull null
                    if (!url.startsWith("file://")) return@mapNotNull null
                    val fileName = when (part) {
                        is UIMessagePart.Document -> part.fileName
                        else -> null
                    } ?: url.substringAfterLast("/").substringBefore("?")
                    "$cwd/uploads/$fileName"
                }
                if (synced.isEmpty()) {
                    message
                } else {
                    appendText(
                        message,
                        "\n[System: The attachments in this message have been synced to your workspace at: ${synced.joinToString(", ")}]\n",
                    )
                }
            }
        }
    }
}

fun buildPortableWorkspaceReminderPrompt(reminder: PortableWorkspaceReminder): String {
    if (!reminder.toolCapable || !reminder.ready) {
        val reason = reminder.unavailableReason ?: when {
            !reminder.toolCapable ->
                "The selected model is not marked as tool-capable, so workspace_shell, workspace_read_file, workspace_write_file, and workspace_edit_file are not available in this chat."
            else ->
                "The workspace rootfs is not ready. The user must install or repair the rootfs before shell and file tools can run."
        }
        return buildString {
            appendLine("<workspace>")
            appendLine("A Linux workspace named \"${reminder.name}\" is configured, but it is not currently usable.")
            appendLine("- $reason")
            appendLine("- Do not claim that you can run workspace commands, inspect Python, read/write workspace files, or create files in the workspace during this turn.")
            appendLine("- If the user asks about the Linux environment, explain this limitation briefly and tell them what needs to be enabled or fixed.")
            append("</workspace>")
        }
    }
    return buildString {
        appendLine("<workspace>")
        appendLine("You have access to a persistent Linux workspace named \"${reminder.name}\", running in a sandboxed proot rootfs environment.")
        appendLine("- The workspace files area is mounted at `/workspace`. Use it as your working directory; files written there persist across turns of this conversation.")
        appendLine("- All paths passed to workspace tools must be absolute and inside the Rootfs (for example `/workspace/notes.md`).")
        appendLine("- Available tools:")
        appendLine("  - `workspace_read_file`: read file contents.")
        appendLine("  - `workspace_write_file` / `workspace_edit_file`: create files, or make precise edits to existing files.")
        appendLine("  - `workspace_shell`: run shell commands (the files area is mounted at /workspace).")
        appendLine("- If you need to inspect the environment, call `workspace_shell`. Do not claim that you checked, installed, read, wrote, or generated anything unless a workspace tool result is present in the conversation.")
        appendLine("- If a workspace tool call is pending user approval, wait for the approval/result instead of guessing the outcome.")
        appendLine("- Prefer `workspace_shell` for tasks that standard Unix tools handle well, and prefer `workspace_edit_file` for targeted edits over rewriting whole files.")
        appendLine("- The skills directory is mounted at `/skills`. Each skill is a subdirectory `/skills/<skill-name>/` containing a `SKILL.md` (with `name` and `description` frontmatter) plus any supporting files. Read a skill's `SKILL.md` before using it, and follow its instructions.")
        if (!reminder.cwd.isNullOrBlank()) {
            appendLine("- Current working directory: `${reminder.cwd}`. Use this as the default context for file operations and shell commands.")
        }
        append("</workspace>")
    }
}

private fun appendSystemPrompt(messages: List<UIMessage>, prompt: String): List<UIMessage> {
    val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
    return if (systemIndex >= 0) {
        messages.toMutableList().apply {
            this[systemIndex] = appendText(this[systemIndex], "\n\n$prompt")
        }
    } else {
        listOf(UIMessage.system(prompt)) + messages
    }
}

private fun appendText(message: UIMessage, extra: String): UIMessage {
    val updatedParts = message.parts.toMutableList()
    val firstTextIndex = updatedParts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex >= 0) {
        val text = updatedParts[firstTextIndex] as UIMessagePart.Text
        updatedParts[firstTextIndex] = text.copy(text = text.text + extra)
    } else {
        updatedParts.add(UIMessagePart.Text(extra))
    }
    return message.copy(parts = updatedParts)
}

fun defaultPortableInputTransformers(): List<PortableInputTransformer> = listOf(
    PortablePlaceholderTransformer,
    PortableTemplateTransformer,
    PortableDocumentAsPromptTransformer,
    PortableOcrTransformer,
    PortableUnsupportedFileTransformer,
    PortableWorkspaceReminderTransformer,
)

fun bytesDocumentRuntime(
    readBytes: suspend (url: String) -> ByteArray?,
    pdfParser: ((ByteArray) -> String)? = null,
): PortableDocumentRuntime = PortableDocumentRuntime { url, fileName, mime ->
    val bytes = readBytes(url) ?: return@PortableDocumentRuntime null
    PortableDocumentText.parse(fileName, mime, bytes, pdfParser)
}
