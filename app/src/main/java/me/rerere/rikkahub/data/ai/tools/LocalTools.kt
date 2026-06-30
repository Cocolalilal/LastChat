package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
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
import kotlinx.serialization.json.JsonObject
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
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.datastore.TtsFilterMode
import me.rerere.rikkahub.data.datastore.getEffectiveTTSProvider
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.TtsController
import me.rerere.tts.provider.android.TTSManager
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



internal fun scheduledMessageToolJson(result: ScheduledLocalToolMessage): JsonObject {
    return buildJsonObject {
        put("status", result.status)
        result.scheduledAt?.let { put("scheduled_at", it) }
        result.workName?.let { put("work_name", it) }
    }
}

internal fun notificationsToolJson(notifications: List<LocalToolNotificationSnapshot>): JsonObject {
    return buildJsonObject {
        put(
            "notifications",
            JsonArray(
                notifications.map { notification ->
                    buildJsonObject {
                        put("package", notification.packageName)
                        put("title", notification.title)
                        put("content", notification.content)
                        put("time", notification.postTime)
                    }
                }
            )
        )
    }
}

private fun sanitizeSandboxBaseName(rawName: String): String {
    val cleaned = rawName
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-', '.', '_')
        .take(40)
    return cleaned.ifBlank { "attachment" }
}

class LocalTools(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val ttsManager: TTSManager,
    private val providerManager: ProviderManager,
    genMediaRepository: GenMediaRepository,
    private val notificationPlatform: LocalToolNotificationPlatform = AndroidLocalToolNotificationPlatform(context),
    private val generatedToolImageSaver: GeneratedToolImageSaver = AndroidGeneratedToolImageSaver(
        context = context,
        genMediaRepository = genMediaRepository,
    ),
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
                val provider = settingsStore.settingsFlow.value.getEffectiveTTSProvider()
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
                    generatedToolImageSaver.save(
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
                                buildJsonObject {
                                    put("uri", file.uri)
                                    put("path", file.path)
                                    put("markdown_image", file.markdownImage)
                                }
                            }
                        )
                    )
                    put("note", "Include images[].markdown_image in your reply so the generated image appears in chat.")
                }
            }
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

                    buildJsonObject {
                        put(
                            "status",
                            notificationPlatform.sendNotification(
                                conversationId = conversationId,
                                title = title,
                                content = content,
                            )
                        )
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

                    scheduledMessageToolJson(
                        notificationPlatform.scheduleMessage(
                            assistantId = assistantId,
                            conversationId = conversationId,
                            reason = reason,
                            delayMinutes = delayMinutes,
                        )
                    )
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
                    notificationsToolJson(notificationPlatform.getRecentNotifications(limit))
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
     */
    fun getTools(
        options: List<LocalToolOption>,
        assistantId: Uuid,
        conversationId: Uuid,
    ): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.Notifications)) {
            tools.addAll(getNotificationTools(assistantId, conversationId))
        }
        // PythonEngine is kept only for backwards-compatible settings deserialization.
        // Linux workspace tools replace the old Chaquopy sandbox and are wired through
        // createWorkspaceTools when an assistant has a bound workspace.
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
}
