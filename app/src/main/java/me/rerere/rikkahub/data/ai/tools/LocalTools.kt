package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ImageGenerationMethod
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.datastore.TtsFilterMode
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.utils.createImageFileFromBase64
import me.rerere.rikkahub.utils.getImagesDir
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.TtsController
import me.rerere.tts.provider.TTSManager
import java.io.File
import java.security.MessageDigest
import kotlin.uuid.Uuid

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("device_control")
    data object Notifications : LocalToolOption()

    @Serializable
    @SerialName("python_engine")
    data object PythonEngine : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("character_questions")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("image_generation")
    data object ImageGeneration : LocalToolOption()
}

object LocalToolOptionListSerializer :
    KSerializer<List<LocalToolOption>> {
    private val delegate = ListSerializer(LocalToolOption.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<LocalToolOption>) {
        encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): List<LocalToolOption> {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(delegate)
        val localTools = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()

        return buildList {
            localTools.forEach { toolElement ->
                runCatching {
                    jsonDecoder.json.decodeFromJsonElement(LocalToolOption.serializer(), toolElement)
                }.getOrNull()?.let(::add)
            }
        }
    }
}

data class PythonAttachmentReference(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val promptVisible: Boolean = true,
)

internal data class PreloadedSandboxAttachment(
    val sandboxName: String,
    val originalFileName: String,
    val mimeType: String,
    val sourceUrl: String,
    val promptVisible: Boolean,
)

internal fun buildSandboxAttachmentFilename(originalName: String, sourceUrl: String): String {
    val extension = originalName
        .substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it != originalName }
        ?.lowercase()
    val baseName = sanitizeSandboxBaseName(originalName.substringBeforeLast('.', originalName))
    val hash = shortStableHash(sourceUrl)

    return buildString {
        append(baseName)
        append('-')
        append(hash)
        extension?.let {
            append('.')
            append(it)
        }
    }
}

internal fun buildPreloadedPythonDescription(preloadedFiles: List<PreloadedSandboxAttachment>): String {
    val visibleFiles = visiblePreloadedSandboxAttachments(preloadedFiles)
    if (visibleFiles.isEmpty()) return ""
    val fileList = visibleFiles.joinToString(", ") { "'${it.sandboxName}'" }
    return " Latest user attachments are already copied into the Python sandbox as $fileList. Open these files directly by sandbox filename in Python. Use import_attachment only for original chat attachment URLs."
}

internal fun visiblePreloadedSandboxAttachments(
    preloadedFiles: List<PreloadedSandboxAttachment>,
): List<PreloadedSandboxAttachment> {
    return preloadedFiles.filter { it.promptVisible }
}

internal fun detectSandboxPseudoImportFilename(
    url: String,
    sandboxFileNames: Set<String>,
    pathExists: (String) -> Boolean = { path -> File(path).exists() },
): String? {
    if (!url.startsWith("file:///")) return null
    val path = url.removePrefix("file:///")
    if (path.isBlank() || path.contains('/')) return null
    if (!sandboxFileNames.contains(path)) return null
    return path.takeUnless { pathExists("/$it") }
}

private fun sanitizeSandboxBaseName(rawName: String): String {
    val cleaned = rawName
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-', '.', '_')
        .take(40)
    return cleaned.ifBlank { "attachment" }
}

private fun shortStableHash(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(8)
}

class LocalTools(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val ttsManager: TTSManager,
    private val providerManager: ProviderManager,
    private val genMediaRepository: GenMediaRepository,
) {
    val askUserTool by lazy {
        Tool(
            name = ASK_USER_TOOL_NAME,
            description = "Ask the user a short structured questionnaire when a clarification or tradeoff would genuinely help. Use this sparingly. Ask at most 5 questions, with up to 3 concise options per question. Each option may include a short description. Do not use this just for chit-chat.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("questions", buildJsonObject {
                            put("type", "array")
                            put("description", "A short questionnaire for the user. Maximum 5 questions.")
                            put("items", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("id", buildJsonObject {
                                        put("type", "string")
                                        put("description", "Stable question identifier.")
                                    })
                                    put("question", buildJsonObject {
                                        put("type", "string")
                                        put("description", "The question to ask the user.")
                                    })
                                    put("options", buildJsonObject {
                                        put("type", "array")
                                        put("description", "Up to 3 suggested replies.")
                                        put("items", buildJsonObject {
                                            put("type", "object")
                                            put("properties", buildJsonObject {
                                                put("label", buildJsonObject {
                                                    put("type", "string")
                                                    put("description", "Short reply option text.")
                                                })
                                                put("description", buildJsonObject {
                                                    put("type", "string")
                                                    put("description", "Optional one-sentence explanation.")
                                                })
                                            })
                                            put("required", JsonArray(listOf(JsonPrimitive("label"))))
                                        })
                                    })
                                })
                                put(
                                    "required",
                                    JsonArray(
                                        listOf(
                                            JsonPrimitive("id"),
                                            JsonPrimitive("question"),
                                        )
                                    )
                                )
                            })
                        })
                    },
                    required = listOf("questions")
                )
            },
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("## tool: ask_user")
                    appendLine("- Use this only when a genuine clarification or meaningful tradeoff would improve your next answer.")
                    appendLine("- It is appropriate when the user's request is ambiguous, underspecified, or could reasonably go in multiple directions.")
                    appendLine("- Ask at most one questionnaire per turn.")
                    appendLine("- Keep it short: at most 5 questions, and at most 3 options per question.")
                    appendLine("- Options should be concise. Add a one-sentence description only when it helps the user distinguish them.")
                    appendLine("- Do not use this for small talk, routine confirmations, or information you can infer safely.")
                }
            },
            approvalMode = ToolApprovalMode.RequiresApproval,
            execute = {
                parseAskUserQuestionnaire(it)?.toJsonElement() ?: buildJsonObject {
                    put("questions", JsonArray(emptyList()))
                }
            }
        )
    }

    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = "Execute JavaScript code with QuickJS. If use this tool to calculate math, better to add `toFixed` to the code.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                )
            },
            execute = {
                val context = QuickJSContext.create()
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate(code)
                buildJsonObject {
                    put(
                        "result", when (result) {
                            is QuickJSObject -> JsonPrimitive(result.stringify())
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
            }
        )
    }

    private val pythonSandbox by lazy { PythonSandbox(context) }
    private val ttsController by lazy { TtsController(context, ttsManager) }

    val ttsTool by lazy {
        Tool(
            name = "text_to_speech",
            description = "Read text aloud using the currently selected LastChat TTS provider. Use this when the user explicitly wants spoken output.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "The text to speak aloud")
                        })
                    },
                    required = listOf("text")
                )
            },
            execute = {
                val rawText = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val provider = settingsStore.settingsFlow.value.getSelectedTTSProvider()
                if (provider == null) {
                    buildJsonObject {
                        put("success", false)
                        put("error", "No TTS provider selected")
                    }
                } else {
                    val processedText = prepareTtsText(rawText)
                    if (processedText.isBlank()) {
                        buildJsonObject {
                            put("success", false)
                            put("error", "Nothing left to speak after TTS filtering")
                        }
                    } else {
                        ttsController.speakWithProvider(processedText, provider, true)
                        buildJsonObject {
                            put("success", true)
                            put("provider", provider.name.ifBlank { "TTS" })
                        }
                    }
                }
            }
        )
    }

    val imageGenerationTool by lazy {
        Tool(
            name = "generate_image",
            description = "Generate an image with LastChat's selected image generation model and save it to the image gallery. Use this only when the user asks for an image. Improve vague user requests into a concrete visual prompt before calling.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("prompt", buildJsonObject {
                            put("type", "string")
                            put("description", "Detailed visual prompt to generate")
                        })
                        put("aspect_ratio", buildJsonObject {
                            put("type", "string")
                            put("description", "square, landscape, or portrait")
                        })
                        put("count", buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of images to generate, 1 to 4")
                        })
                    },
                    required = listOf("prompt")
                )
            },
            systemPrompt = { _, _ ->
                """
                ## Image generation tool
                When the user asks you to create, draw, render, or generate an image, call `generate_image`.
                - Rewrite short or vague requests into a richer visual prompt before calling the tool.
                - After the tool returns, include each returned `markdown_image` in your reply.
                - Do not call this tool for ordinary image analysis.
                """.trimIndent()
            },
            execute = { args ->
                val params = args.jsonObject
                val prompt = params["prompt"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (prompt.isBlank()) {
                    error("prompt is required")
                }
                val aspectRatio = when (params["aspect_ratio"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                    "landscape", "wide" -> me.rerere.ai.ui.ImageAspectRatio.LANDSCAPE
                    "portrait", "tall" -> me.rerere.ai.ui.ImageAspectRatio.PORTRAIT
                    else -> me.rerere.ai.ui.ImageAspectRatio.SQUARE
                }
                val count = params["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 4) ?: 1
                val settings = settingsStore.settingsFlow.value
                val model = settings.findModelById(settings.imageGenerationModelId)
                    ?: error("No image generation model selected")
                val provider = model.findProvider(settings.providers)
                    ?: error("Image generation provider not found")

                val items = when (model.imageGenerationMethod ?: ImageGenerationMethod.DIFFUSION) {
                    ImageGenerationMethod.DIFFUSION -> {
                        val result = providerManager.getProviderByType(provider).generateImage(
                            providerSetting = provider,
                            params = ImageGenerationParams(
                                model = model,
                                prompt = prompt,
                                numOfImages = count,
                                aspectRatio = aspectRatio,
                                customHeaders = model.customHeaders,
                                customBody = model.customBodies,
                            )
                        )
                        result.items
                    }

                    ImageGenerationMethod.MULTIMODAL -> {
                        val modelWithImageOutput = model.copy(outputModalities = model.outputModalities + Modality.IMAGE)
                        val result = providerManager.getProviderByType(provider).generateText(
                            providerSetting = provider,
                            messages = listOf(me.rerere.ai.ui.UIMessage.user(prompt)),
                            params = me.rerere.ai.provider.TextGenerationParams(
                                model = modelWithImageOutput,
                                tools = emptyList(),
                                customHeaders = model.customHeaders,
                                customBody = model.customBodies,
                            )
                        )
                        result.choices.flatMap { choice ->
                            choice.message?.parts.orEmpty().filterIsInstance<me.rerere.ai.ui.UIMessagePart.Image>()
                        }.map { part ->
                            me.rerere.ai.ui.ImageGenerationItem(data = part.url, mimeType = "image/png")
                        }.ifEmpty {
                            error("No images generated")
                        }
                    }
                }

                val files = items.take(count).mapIndexed { index, item ->
                    saveGeneratedImageFromTool(
                        item = item,
                        prompt = prompt,
                        modelName = model.displayName.ifBlank { model.modelId },
                        index = index,
                    )
                }

                buildJsonObject {
                    put("success", true)
                    put("saved_to_gallery", true)
                    put(
                        "images",
                        JsonArray(
                            files.map { file ->
                                val uri = "file://${file.absolutePath}"
                                buildJsonObject {
                                    put("uri", uri)
                                    put("path", file.absolutePath)
                                    put("markdown_image", "![Generated image]($uri)")
                                }
                            }
                        )
                    )
                    put("note", "Include images[].markdown_image in your reply so the generated image appears in chat.")
                }
            }
        )
    }

    /**
     * Get Python tools for the conversation.
     * @param conversationId The conversation UUID
     * @param attachments Image/document attachments from the most recent user message - will be auto-imported
     */
    fun getPythonTools(
        conversationId: Uuid,
        attachments: List<PythonAttachmentReference> = emptyList(),
    ): List<Tool> {
        val workingDir = pythonSandbox.getConversationDir(conversationId).absolutePath

        val preloadedFiles = mutableListOf<PreloadedSandboxAttachment>()
        attachments.forEachIndexed { index, attachment ->
            runCatching {
                val originalFileName = attachment.fileName.ifBlank { "attachment_$index" }
                val filename = buildSandboxAttachmentFilename(
                    originalName = originalFileName,
                    sourceUrl = attachment.url,
                )
                pythonSandbox.importFile(
                    conversationId = conversationId,
                    sourceUri = android.net.Uri.parse(attachment.url),
                    filename = filename,
                )
                preloadedFiles.add(
                    PreloadedSandboxAttachment(
                        sandboxName = filename,
                        originalFileName = originalFileName,
                        mimeType = attachment.mimeType,
                        sourceUrl = attachment.url,
                        promptVisible = attachment.promptVisible,
                    )
                )
            }.onFailure { e ->
                android.util.Log.w("LocalTools", "Failed to auto-import attachment $index: ${e.message}")
            }
        }

        val preloadedInfo = buildPreloadedPythonDescription(preloadedFiles)

        return listOf(
            Tool(
                name = "eval_python",
                description = "Execute Python code. Has access to numpy, pandas, matplotlib and Pillow. Use for calculations, data processing and chart/image generation.$preloadedInfo After execution, check `generated_files` and include any `markdown_link` in your reply (for images prefer Markdown image syntax like `![chart](content://...)`).",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("code", buildJsonObject {
                                put("type", "string")
                                put("description", "The Python code to execute")
                            })
                        },
                        required = listOf("code")
                    )
                },
                execute = {
                    val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val filesBefore = pythonSandbox.listFiles(conversationId)
                        val beforeNames = filesBefore.map { file -> file.name }.toSet()

                        val python = com.chaquo.python.Python.getInstance()
                        val executor = python.getModule("executor")
                        val resultJson = executor.callAttr("execute", code, workingDir).toString()
                        val baseResultObj = kotlinx.serialization.json.Json.parseToJsonElement(resultJson).jsonObject

                        val filesAfter = pythonSandbox.listFiles(conversationId)
                        val generatedFiles = filesAfter
                            .filter { file -> !beforeNames.contains(file.name) }
                            .map { file ->
                                val uri = pythonSandbox.getFileUri(conversationId, file.name)
                                buildJsonObject {
                                    put("name", file.name)
                                    put("size", file.size)
                                    put("is_image", file.isImage)
                                    put("mime", file.mimeType)
                                    put("uri", uri.toString())
                                    put("markdown_link", if (file.isImage) "![${file.name}]($uri)" else "[${file.name}]($uri)")
                                }
                            }

                        val finalResultObj = buildJsonObject {
                            baseResultObj.forEach { (k, v) -> put(k, v) }
                            val visiblePreloadedFiles = visiblePreloadedSandboxAttachments(preloadedFiles)
                            if (visiblePreloadedFiles.isNotEmpty()) {
                                put(
                                    "preloaded_attachments",
                                    JsonArray(
                                        visiblePreloadedFiles.map { file ->
                                            buildJsonObject {
                                                put("name", file.sandboxName)
                                                put("original_name", file.originalFileName)
                                                put("mime", file.mimeType)
                                                put("source_url", file.sourceUrl)
                                            }
                                        }
                                    )
                                )
                            }
                            if (generatedFiles.isNotEmpty()) {
                                put("generated_files", JsonArray(generatedFiles))
                                put("note", "Use generated_files[].markdown_link in your reply so users can open/download outputs directly in chat.")
                            }
                        }

                        // Truncate output if too long
                        val output = finalResultObj.toString()
                        if (output.length > 2000) {
                            buildJsonObject {
                                put("output", output.take(2000) + "... (truncated)")
                                put("note", "Output truncated to save context window. Use print() sparingly or save to file, and use list_sandbox_files to inspect files.")
                            }
                        } else {
                            finalResultObj
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Unknown error") }
                    }
                }
            ),
            Tool(
                name = "list_sandbox_files",
                description = "List all files in the Python sandbox for this conversation. Returns file names, sizes, whether they are images, and direct markdown links you can include in your response.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject { },
                        required = emptyList()
                    )
                },
                execute = {
                    try {
                        val files = pythonSandbox.listFiles(conversationId)
                        buildJsonObject {
                            put("files", kotlinx.serialization.json.JsonArray(
                                files.map { file ->
                                    buildJsonObject {
                                        val uri = pythonSandbox.getFileUri(conversationId, file.name)
                                        put("name", file.name)
                                        put("size", file.size)
                                        put("is_image", file.isImage)
                                        put("mime", file.mimeType)
                                        put("uri", uri.toString())
                                        put("markdown_link", if (file.isImage) "![${file.name}]($uri)" else "[${file.name}]($uri)")
                                    }
                                }
                            ))
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to list files") }
                    }
                }
            ),
            Tool(
                name = "read_sandbox_file",
                description = "Read a text file from the Python sandbox.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("path", buildJsonObject {
                                put("type", "string")
                                put("description", "Relative path to the file in the sandbox")
                            })
                        },
                        required = listOf("path")
                    )
                },
                execute = {
                    val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val python = com.chaquo.python.Python.getInstance()
                        val executor = python.getModule("executor")
                        val resultJson = executor.callAttr("read_file", path, workingDir).toString()
                        kotlinx.serialization.json.Json.parseToJsonElement(resultJson).jsonObject
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to read file") }
                    }
                }
            ),
            Tool(
                name = "write_sandbox_file",
                description = "Write content to a file in the Python sandbox. Returns `markdown_link` which you MUST include in your response to let the user download the file. Example: 'Here is your file: [output.txt](content://...)'",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("path", buildJsonObject {
                                put("type", "string")
                                put("description", "Relative path for the file (e.g. 'output.txt', 'images/result.png')")
                            })
                            put("content", buildJsonObject {
                                put("type", "string")
                                put("description", "Content to write to the file")
                            })
                        },
                        required = listOf("path", "content")
                    )
                },
                execute = {
                    val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    val content = it.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val python = com.chaquo.python.Python.getInstance()
                        val executor = python.getModule("executor")
                        val resultJson = executor.callAttr("write_file", path, content, workingDir).toString()
                        val resultObj = kotlinx.serialization.json.Json.parseToJsonElement(resultJson).jsonObject
                        
                        if (resultObj["success"]?.jsonPrimitive?.booleanOrNull == true) {
                            // Use original relative path since getFileUri constructs full path
                            val uri = pythonSandbox.getFileUri(conversationId, path)
                            kotlinx.serialization.json.buildJsonObject {
                                resultObj.forEach { (k, v) -> put(k, v) }
                                put("uri", uri.toString())
                                put("markdown_link", "[$path]($uri)")
                            }
                        } else {
                            resultObj
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to write file") }
                    }
                }
            ),
            Tool(
                name = "delete_sandbox_file",
                description = "Delete a file from the Python sandbox.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("path", buildJsonObject {
                                put("type", "string")
                                put("description", "Relative path to the file to delete")
                            })
                        },
                        required = listOf("path")
                    )
                },
                execute = {
                    val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val deleted = pythonSandbox.deleteFile(conversationId, path)
                        buildJsonObject {
                            put("success", deleted)
                            put("path", path)
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to delete file") }
                    }
                }
            ),
            Tool(
                name = "import_attachment",
                description = "Import an original chat attachment URL into the Python sandbox. Use this for file/content URLs from message attachments, not for sandbox filenames already listed in preloaded_attachments.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("url", buildJsonObject {
                                put("type", "string")
                                put("description", "The file URL from the message attachment (e.g. 'file:///...' or 'content://...')")
                            })
                            put("filename", buildJsonObject {
                                put("type", "string")
                                put("description", "Filename to save as in the sandbox (e.g. 'input.jpg', 'data.csv')")
                            })
                        },
                        required = listOf("url", "filename")
                    )
                },
                execute = {
                    val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    val filename = it.jsonObject["filename"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val pseudoSandboxFile = detectSandboxPseudoImportFilename(
                            url = url,
                            sandboxFileNames = pythonSandbox.listFiles(conversationId).map { file -> file.name }.toSet(),
                        )
                        if (pseudoSandboxFile != null) {
                            buildJsonObject {
                                put("success", false)
                                put("error", "The URL '$url' points to a sandbox filename, not an original chat attachment URL.")
                                put("hint", "Use the existing sandbox file '$pseudoSandboxFile' directly in Python instead of calling import_attachment.")
                                put("sandbox_filename", pseudoSandboxFile)
                            }
                        } else {
                            val uriArg = android.net.Uri.parse(url)
                            val savedPath = pythonSandbox.importFile(conversationId, uriArg, filename)
                            val fileUri = pythonSandbox.getFileUri(conversationId, filename)
                            buildJsonObject {
                                put("success", true)
                                put("path", savedPath)
                                put("filename", filename)
                                put("uri", fileUri.toString())
                                put("markdown_link", "[$filename]($fileUri)")
                            }
                        }
                    } catch (e: Exception) {
                        buildJsonObject {
                            put("success", false)
                            put("error", e.message ?: "Failed to import file")
                        }
                    }
                }
            )
        )
    }

    fun getNotificationTools(assistantId: Uuid, conversationId: Uuid): List<Tool> {
        return listOf(
            Tool(
                name = "send_notification",
                description = "Send a notification to the user",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("title", buildJsonObject {
                                put("type", "string")
                                put("description", "Notification title")
                            })
                            put("content", buildJsonObject {
                                put("type", "string")
                                put("description", "Notification content")
                            })
                        },
                        required = listOf("title", "content")
                    )
                },
                execute = {
                    val title = it.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Notification"
                    val content = it.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val channelId = "assistant_notification"
                    val channel = android.app.NotificationChannel(
                        channelId,
                        "Assistant Notification",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(channel)
                    
                    // Create pending intent to open the conversation when notification is clicked
                    val intent = android.content.Intent(context, me.rerere.rikkahub.RouteActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("conversationId", conversationId.toString())
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        conversationId.hashCode(),
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(me.rerere.rikkahub.R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()
                        
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
                        buildJsonObject { put("status", "success") }
                    } else {
                        buildJsonObject { put("status", "error: permission denied") }
                    }
                }
            ),
            Tool(
                name = "schedule_message",
                description = "Schedule a follow-up notification message after a delay. Delivery time is approximate and may vary with Android system optimizations.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("reason", buildJsonObject {
                                put("type", "string")
                                put("description", "The reason for scheduling this message (e.g., 'Remind user to drink water')")
                            })
                            put("delay_minutes", buildJsonObject {
                                put("type", "integer")
                                put("description", "Delay in minutes before sending the message")
                            })
                        },
                        required = listOf("reason", "delay_minutes")
                    )
                },
                execute = {
                    val reason = it.jsonObject["reason"]?.jsonPrimitive?.contentOrNull ?: ""
                    val delayMinutes = (it.jsonObject["delay_minutes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1L)
                        .coerceAtLeast(0L)
                    
                    try {
                        val createdAt = System.currentTimeMillis()
                        val scheduledAt = createdAt + (delayMinutes * 60 * 1000)
                        val uniqueWorkName = me.rerere.rikkahub.service.ScheduledMessageWorkSpec.buildUniqueWorkName(
                            assistantId = assistantId.toString(),
                            conversationId = conversationId.toString(),
                            reason = reason,
                            scheduledAtMillis = scheduledAt
                        )
                        val workRequest = androidx.work.OneTimeWorkRequestBuilder<me.rerere.rikkahub.service.ScheduledMessageWorker>()
                            .setInitialDelay(delayMinutes, java.util.concurrent.TimeUnit.MINUTES)
                            .setBackoffCriteria(
                                androidx.work.BackoffPolicy.EXPONENTIAL,
                                30,
                                java.util.concurrent.TimeUnit.SECONDS
                            )
                            .setConstraints(
                                androidx.work.Constraints.Builder()
                                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                    .build()
                            )
                            .setInputData(
                                me.rerere.rikkahub.service.ScheduledMessageWorkSpec.buildInputData(
                                    assistantId = assistantId.toString(),
                                    conversationId = conversationId.toString(),
                                    reason = reason,
                                    createdAtMillis = createdAt,
                                    scheduledAtMillis = scheduledAt
                                )
                            )
                            .build()

                        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                            uniqueWorkName,
                            androidx.work.ExistingWorkPolicy.KEEP,
                            workRequest
                        )
                        
                        buildJsonObject { 
                            put("status", "success")
                            put("scheduled_at", java.time.Instant.ofEpochMilli(scheduledAt).toString())
                            put("work_name", uniqueWorkName)
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "get_notifications",
                description = "Get recent notifications from the device",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("limit", buildJsonObject {
                                put("type", "integer")
                                put("description", "Max number of notifications to retrieve (default 10)")
                            })
                        }
                    )
                },
                execute = {
                    val limit = it.jsonObject["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
                    val notifications = me.rerere.rikkahub.service.AssistantNotificationListener.notifications.value.take(limit)
                    
                    buildJsonObject {
                        put("notifications", kotlinx.serialization.json.JsonArray(notifications.map { notification ->
                            buildJsonObject {
                                put("package", notification.packageName)
                                put("title", notification.title)
                                put("content", notification.content)
                                put("time", notification.postTime)
                            }
                        }))
                    }
                }
            )
        )
    }

    private fun prepareTtsText(text: String): String {
        return applyTtsTextFilters(text).stripMarkdown().trim()
    }

    private fun applyTtsTextFilters(text: String): String {
        val settings = settingsStore.settingsFlow.value
        val rules = settings.displaySetting.ttsTextFilterRules.filter { it.enabled }
        if (rules.isEmpty()) return text

        var result = text
        val onlyReadRules = rules.filter { it.mode == TtsFilterMode.ONLY_READ }
        if (onlyReadRules.isNotEmpty()) {
            val extracted = StringBuilder()
            onlyReadRules.forEach { rule ->
                val pattern = Regex.escape(rule.pattern)
                val regex = Regex("$pattern(.+?)$pattern")
                regex.findAll(result).forEach { match ->
                    if (extracted.isNotEmpty()) extracted.append(" ")
                    extracted.append(match.groupValues.getOrNull(1).orEmpty())
                }
            }
            result = extracted.toString()
        }

        rules.filter { it.mode == TtsFilterMode.SKIP }.forEach { rule ->
            val pattern = Regex.escape(rule.pattern)
            val regex = Regex("$pattern.+?$pattern")
            result = result.replace(regex, "")
        }

        return result
    }
    
    /**
     * Get all enabled local tools for the conversation.
     * @param attachments Image/document attachments from the most recent user message (for Python auto-import)
     */
    fun getTools(
        options: List<LocalToolOption>,
        assistantId: Uuid,
        conversationId: Uuid,
        attachments: List<PythonAttachmentReference> = emptyList(),
    ): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.Notifications)) {
            tools.addAll(getNotificationTools(assistantId, conversationId))
        }
        // Find Python engine option if present - pass latest user attachments for auto-import
        if (options.contains(LocalToolOption.PythonEngine)) {
            tools.addAll(getPythonTools(conversationId, attachments))
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.ImageGeneration)) {
            tools.add(imageGenerationTool)
        }
        return tools
    }

    private suspend fun saveGeneratedImageFromTool(
        item: me.rerere.ai.ui.ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
    ): File = withContext(Dispatchers.IO) {
        val imagesDir = context.getImagesDir()
        val timestamp = System.currentTimeMillis()
        val safeModelName = modelName.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(48).ifBlank { "image" }
        val imageFile = File(imagesDir, "${timestamp}_${safeModelName}_tool_$index.png")
        val createdFile = context.createImageFileFromBase64(item.data, imageFile.absolutePath)
        genMediaRepository.insertMedia(
            GenMediaEntity(
                path = "images/${imageFile.name}",
                modelId = modelName,
                prompt = prompt,
                createAt = timestamp,
            )
        )
        createdFile
    }
}
