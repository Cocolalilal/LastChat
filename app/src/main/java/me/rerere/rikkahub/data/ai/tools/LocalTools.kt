package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.android.QuickJSLoader
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
import me.rerere.rikkahub.data.repository.StudioChecklistEntry
import me.rerere.rikkahub.data.repository.StudioHubItem
import me.rerere.rikkahub.data.repository.StudioHubRepository
import me.rerere.rikkahub.data.repository.StudioItemType
import me.rerere.rikkahub.data.datastore.TtsFilterMode
import me.rerere.rikkahub.data.datastore.getEffectiveTTSProvider
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.TtsController
import me.rerere.tts.controller.AudioPlayer
import me.rerere.tts.provider.android.TTSManager
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
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

    @Serializable
    @SerialName("tmdb")
    data object Tmdb : LocalToolOption()

    @Serializable
    @SerialName("studio_hub")
    data object StudioHub : LocalToolOption()
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

data class LocalToolDefinitionPreview(
    val name: String,
    val description: String,
    val schema: InputSchema?,
)

fun LocalToolOption.toDefinitionPreviews(): List<LocalToolDefinitionPreview> = when (this) {
    LocalToolOption.JavascriptEngine -> listOf(
        LocalToolDefinitionPreview(
            name = "eval_javascript",
            description = "Execute JavaScript code with QuickJS. If use this tool to calculate math, better to add `toFixed` to the code.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("code", buildJsonObject {
                        put("type", "string")
                        put("description", "The JavaScript code to execute")
                    })
                },
            )
        )
    )
    LocalToolOption.Notifications -> listOf(
        LocalToolDefinitionPreview(
            name = "send_notification",
            description = "Send a notification to the user",
            schema = InputSchema.Obj(
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
        ),
        LocalToolDefinitionPreview(
            name = "schedule_message",
            description = "Schedule a follow-up notification message after a delay. Delivery time is approximate and may vary with Android system optimizations.",
            schema = InputSchema.Obj(
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
        ),
        LocalToolDefinitionPreview(
            name = "get_notifications",
            description = "Get recent notifications from the device",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max number of notifications to retrieve (default 10)")
                    })
                }
            )
        )
    )
    LocalToolOption.PythonEngine -> emptyList()
    LocalToolOption.Tts -> listOf(
        LocalToolDefinitionPreview(
            name = "text_to_speech",
            description = "Read text aloud using the currently selected LastChat TTS provider. Use this when the user explicitly wants spoken output.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "The text to speak aloud")
                    })
                },
                required = listOf("text")
            )
        )
    )
    LocalToolOption.AskUser -> listOf(
        LocalToolDefinitionPreview(
            name = ASK_USER_TOOL_NAME,
            description = "Ask the user a short structured questionnaire when a clarification or tradeoff would genuinely help. Use this sparingly. Ask at most 5 questions, with up to 3 concise options per question. Each option may include a short description. Do not use this just for chit-chat.",
            schema = InputSchema.Obj(
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
        )
    )
    LocalToolOption.ImageGeneration -> listOf(
        LocalToolDefinitionPreview(
            name = "generate_image",
            description = "Generate an image with LastChat's selected image generation model and save it to the image gallery. Use this only when the user asks for an image. Improve vague user requests into a concrete visual prompt before calling.",
            schema = InputSchema.Obj(
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
        )
    )
    LocalToolOption.Tmdb -> listOf(
        LocalToolDefinitionPreview(
            name = "search_media",
            description = "Search for movies, TV series, anime, and shows on TMDB to find titles, years, ratings, and IDs.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Search query (title, franchise name, or actor)")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "Search scope: 'multi' (default), 'movie', or 'tv'")
                    })
                },
                required = listOf("query")
            )
        ),
        LocalToolDefinitionPreview(
            name = "get_media_info",
            description = "Get comprehensive details of a movie or TV show including synopsis, episode count, seasons, genres, cast, and user ratings.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The TMDB numeric media ID")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "'tv' (default) or 'movie'")
                    })
                },
                required = listOf("id")
            )
        ),
        LocalToolDefinitionPreview(
            name = "get_media_art",
            description = "Fetch multiple high-resolution posters, 16:9 backdrops, and stills for a show or movie to display to the user.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The TMDB numeric media ID")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "'tv' (default) or 'movie'")
                    })
                },
                required = listOf("id")
            )
        ),
        LocalToolDefinitionPreview(
            name = "get_media_recommendations",
            description = "Get algorithmic recommendations similar to a given movie or TV show.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The TMDB numeric media ID")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "'tv' (default) or 'movie'")
                    })
                },
                required = listOf("id")
            )
        ),
        LocalToolDefinitionPreview(
            name = "manage_watchlist",
            description = "Get the user's real TMDB watchlist or add a movie/show to their watchlist on their behalf.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("description", "Action to perform: 'get' (default) to read watchlist, 'add' or 'remove' to modify")
                    })
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "TMDB media ID (required for add/remove)")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "'tv' (default) or 'movie'")
                    })
                },
                required = listOf("action")
            )
        )
    )
    LocalToolOption.StudioHub -> listOf(
        LocalToolDefinitionPreview(
            name = "studio_list_items",
            description = "List matching Studio Hub items (notes, task lists, reminders, voice memos, and stories) with IDs, titles, tags, status, and brief summaries.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional filter: NOTE, TASK_LIST, REMINDER, IMAGE_NOTE, VOICE_MEMO, STORY, or ALL")
                    })
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional search text across titles, summaries, bodies, checklists, and tags")
                    })
                    put("includeArchived", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to include archived items (default false)")
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Maximum results to return (default 20)")
                    })
                },
                required = emptyList()
            )
        ),
        LocalToolDefinitionPreview(
            name = "studio_read_item",
            description = "Read the full content, checklist entries, attachments, and metadata of a Studio Hub item by ID.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Studio item ID")
                    })
                },
                required = listOf("id")
            )
        ),
        LocalToolDefinitionPreview(
            name = "studio_create_item",
            description = "Create a new Note, Task List, or Reminder in the user's unified Studio Hub feed.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "Item type: NOTE, TASK_LIST, or REMINDER")
                    })
                    put("title", buildJsonObject {
                        put("type", "string")
                        put("description", "Title of the note, task list, or reminder")
                    })
                    put("body", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional body text")
                    })
                    putJsonObject("checklist") {
                        put("type", "array")
                        put("description", "Optional list of checklist item strings")
                        putJsonObject("items") { put("type", "string") }
                    }
                    putJsonObject("tags") {
                        put("type", "array")
                        put("description", "Optional list of tags")
                        putJsonObject("items") { put("type", "string") }
                    }
                    put("reminderAtIso", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional ISO-8601 timestamp (e.g. 2026-11-28T09:00:00 or 2026-11-28T09:00:00Z) to schedule a reminder notification")
                    })
                    put("pinned", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to pin the item to the top")
                    })
                    put("color", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional subtle tonal color: amber, sage, sky, rose, or lavender")
                    })
                },
                required = listOf("type", "title")
            )
        ),
        LocalToolDefinitionPreview(
            name = "studio_update_item",
            description = "Update an existing Studio Hub item's title, body, checklist entries, tags, reminder, pin, completion, or archive state.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Studio item ID to update")
                    })
                    put("title", buildJsonObject {
                        put("type", "string")
                        put("description", "Updated title")
                    })
                    put("body", buildJsonObject {
                        put("type", "string")
                        put("description", "Updated body")
                    })
                    putJsonObject("addChecklistItems") {
                        put("type", "array")
                        put("description", "Checklist item texts to append")
                        putJsonObject("items") { put("type", "string") }
                    }
                    putJsonObject("toggleChecklistItemTexts") {
                        put("type", "array")
                        put("description", "Checklist item texts (exact or substring match) to toggle checked state")
                        putJsonObject("items") { put("type", "string") }
                    }
                    putJsonObject("tags") {
                        put("type", "array")
                        put("description", "Replacement tag list")
                        putJsonObject("items") { put("type", "string") }
                    }
                    put("reminderAtIso", buildJsonObject {
                        put("type", "string")
                        put("description", "Updated ISO-8601 reminder time, or empty string to clear")
                    })
                    put("pinned", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Updated pinned state")
                    })
                    put("completed", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Updated completion state")
                    })
                    put("archived", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Updated archived state")
                    })
                },
                required = listOf("id")
            )
        ),
        LocalToolDefinitionPreview(
            name = "studio_delete_item",
            description = "Delete a Studio Hub item by ID.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Studio item ID to delete")
                    })
                },
                required = listOf("id")
            )
        )
    )
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
    private val tmdbService: me.rerere.rikkahub.data.service.TmdbService? = null,
    private val studioHubRepository: StudioHubRepository? = null,
    private val storyStudioRepository: me.rerere.rikkahub.data.repository.StoryStudioRepository? = null,
    private val voiceMemoRepository: me.rerere.rikkahub.data.repository.VoiceMemoRepository? = null,
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
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                QuickJSLoader.init()
                val resultText = QuickJSContext.create().use { context ->
                    when (val result = context.evaluate(code)) {
                        is QuickJSObject -> result.stringify()
                        else -> result.toString()
                    }
                }
                buildJsonObject {
                    put("result", resultText)
                }
            }
        )
    }

    private val ttsController by lazy { TtsController(ttsManager, AudioPlayer(context)) }

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

    private fun Tool.withAlias(aliasName: String): Tool = Tool(
        name = aliasName,
        description = description,
        parameters = parameters,
        approvalMode = approvalMode,
        execute = execute
    )
    
    val searchMediaTool by lazy {
        Tool(
            name = "search_media",
            description = "Search for movies, TV series, anime, and shows on TMDB to find titles, years, ratings, and IDs.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "Search query (title, franchise name, or actor)")
                        })
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "Search scope: 'multi' (default), 'movie', or 'tv'")
                            put("default", "multi")
                        })
                    },
                    required = listOf("query")
                )
            },
            approvalMode = ToolApprovalMode.Auto,
            execute = { args ->
                val tmdb = tmdbService ?: error("TMDB service is not available")
                val params = args.jsonObject
                val query = params["query"]?.jsonPrimitive?.contentOrNull
                    ?: params["title"]?.jsonPrimitive?.contentOrNull
                    ?: params["search"]?.jsonPrimitive?.contentOrNull
                    ?: params["name"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val type = params["type"]?.jsonPrimitive?.contentOrNull
                    ?: params["media_type"]?.jsonPrimitive?.contentOrNull
                    ?: "multi"
                tmdb.searchMedia(query = query, type = type)
            }
        )
    }

    val mediaInfoTool by lazy {
        Tool(
            name = "get_media_info",
            description = "Get comprehensive details of a movie or TV show including synopsis, episode count, seasons, genres, cast, and user ratings.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "The TMDB numeric media ID (from search_media)")
                        })
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "'tv' (default) or 'movie'")
                            put("default", "tv")
                        })
                    },
                    required = listOf("id")
                )
            },
            approvalMode = ToolApprovalMode.Auto,
            execute = { args ->
                val tmdb = tmdbService ?: error("TMDB service is not available")
                val params = args.jsonObject
                val id = params["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: params["media_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: params["tmdb_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: error("Numeric 'id' is required")
                val type = params["type"]?.jsonPrimitive?.contentOrNull
                    ?: params["media_type"]?.jsonPrimitive?.contentOrNull
                    ?: "tv"
                tmdb.getDetails(id = id, type = type)
            }
        )
    }

    val mediaArtTool by lazy {
        Tool(
            name = "get_media_art",
            description = "Fetch multiple high-resolution posters, 16:9 backdrops, and stills for a show or movie to display to the user.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "The TMDB numeric media ID")
                        })
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "'tv' (default) or 'movie'")
                            put("default", "tv")
                        })
                    },
                    required = listOf("id")
                )
            },
            approvalMode = ToolApprovalMode.Auto,
            execute = { args ->
                val tmdb = tmdbService ?: error("TMDB service is not available")
                val params = args.jsonObject
                val id = params["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: params["media_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: params["tmdb_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: error("Numeric 'id' is required")
                val type = params["type"]?.jsonPrimitive?.contentOrNull
                    ?: params["media_type"]?.jsonPrimitive?.contentOrNull
                    ?: "tv"
                tmdb.getImages(id = id, type = type)
            }
        )
    }

    val mediaRecommendationsTool by lazy {
        Tool(
            name = "get_media_recommendations",
            description = "Get algorithmic recommendations similar to a given movie or TV show.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "The TMDB numeric media ID")
                        })
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "'tv' (default) or 'movie'")
                            put("default", "tv")
                        })
                    },
                    required = listOf("id")
                )
            },
            approvalMode = ToolApprovalMode.Auto,
            execute = { args ->
                val tmdb = tmdbService ?: error("TMDB service is not available")
                val params = args.jsonObject
                val id = params["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: params["media_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: params["tmdb_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: error("Numeric 'id' is required")
                val type = params["type"]?.jsonPrimitive?.contentOrNull
                    ?: params["media_type"]?.jsonPrimitive?.contentOrNull
                    ?: "tv"
                tmdb.getRecommendations(id = id, type = type)
            }
        )
    }

    val mediaWatchlistTool by lazy {
        Tool(
            name = "manage_watchlist",
            description = "Get the user's real TMDB watchlist or add/remove a movie or show on their behalf.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put("description", "Action to perform: 'get' (default) to read watchlist, 'add' or 'remove' to modify")
                            put("default", "get")
                        })
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "TMDB media ID (required for add/remove)")
                        })
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "'tv' (default) or 'movie'")
                            put("default", "tv")
                        })
                    },
                    required = listOf("action")
                )
            },
            approvalMode = ToolApprovalMode.Auto,
            execute = { args ->
                val tmdb = tmdbService ?: error("TMDB service is not available")
                val params = args.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: "get"
                val id = params["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: params["media_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val type = params["type"]?.jsonPrimitive?.contentOrNull
                    ?: params["media_type"]?.jsonPrimitive?.contentOrNull
                    ?: "tv"
                tmdb.manageWatchlist(action = action, id = id, type = type)
            }
        )
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
        if (options.contains(LocalToolOption.Tmdb) && tmdbService != null) {
            // Clean named tools
            tools.add(searchMediaTool)
            tools.add(mediaInfoTool)
            tools.add(mediaArtTool)
            tools.add(mediaRecommendationsTool)
            tools.add(mediaWatchlistTool)

            // Transparent legacy aliases so both naming styles work
            tools.add(searchMediaTool.withAlias("tmdb_search"))
            tools.add(mediaInfoTool.withAlias("tmdb_get_details"))
            tools.add(mediaArtTool.withAlias("tmdb_get_images"))
            tools.add(mediaRecommendationsTool.withAlias("tmdb_get_recommendations"))
            tools.add(mediaWatchlistTool.withAlias("tmdb_watchlist"))
        }
        if (options.contains(LocalToolOption.StudioHub) && studioHubRepository != null) {
            tools.addAll(
                buildStudioHubTools(
                    studioHubRepository = studioHubRepository,
                    storyStudioRepository = storyStudioRepository,
                    voiceMemoRepository = voiceMemoRepository,
                )
            )
        }
        return tools
    }
}

internal fun buildStudioHubTools(
    studioHubRepository: StudioHubRepository,
    storyStudioRepository: me.rerere.rikkahub.data.repository.StoryStudioRepository? = null,
    voiceMemoRepository: me.rerere.rikkahub.data.repository.VoiceMemoRepository? = null,
): List<Tool> {
    val previews = LocalToolOption.StudioHub.toDefinitionPreviews().associateBy { it.name }
    fun parseIsoMillis(raw: String): Long? {
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
    }
    fun formatIso(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(millis))
    }

    val listDef = previews.getValue("studio_list_items")
    val listTool = Tool(
        name = listDef.name,
        description = listDef.description,
        parameters = { listDef.schema },
        execute = { args ->
            val params = args.jsonObject
            val typeFilter = params["type"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
            val query = params["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
            val includeArchived = params["includeArchived"]?.jsonPrimitive?.booleanOrNull ?: false
            val limit = (params["limit"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 100)

            val hubItems = studioHubRepository.getItems().filter { item ->
                (includeArchived || !item.isArchived) &&
                    (typeFilter.isNullOrBlank() || typeFilter == "ALL" || item.type.name == typeFilter) &&
                    (query.isBlank() ||
                        item.title.lowercase().contains(query) ||
                        item.body.lowercase().contains(query) ||
                        item.briefSummary.lowercase().contains(query) ||
                        item.tags.any { it.lowercase().contains(query) } ||
                        item.checklist.any { it.text.lowercase().contains(query) })
            }

            val voiceMemos = if (typeFilter.isNullOrBlank() || typeFilter == "ALL" || typeFilter == "VOICE_MEMO") {
                voiceMemoRepository?.recordingsFlow?.value.orEmpty().filter { memo ->
                    query.isBlank() ||
                        memo.title.lowercase().contains(query) ||
                        memo.summary?.lowercase()?.contains(query) == true ||
                        memo.transcript?.lowercase()?.contains(query) == true
                }
            } else emptyList()

            val stories = if (typeFilter.isNullOrBlank() || typeFilter == "ALL" || typeFilter == "STORY") {
                storyStudioRepository?.projectsFlow?.value.orEmpty().filter { story ->
                    query.isBlank() ||
                        story.title.lowercase().contains(query) ||
                        story.premise.lowercase().contains(query) ||
                        story.genre.lowercase().contains(query)
                }
            } else emptyList()

            buildJsonObject {
                putJsonArray("items") {
                    hubItems.take(limit).forEach { item ->
                        add(buildJsonObject {
                            put("id", item.id)
                            put("type", item.type.name)
                            put("title", item.title)
                            put("brief_summary", item.briefSummary)
                            put("pinned", item.isPinned)
                            put("completed", item.isCompleted)
                            put("archived", item.isArchived)
                            put("workspace_path", "/workspace/studio/${item.id}.md")
                            if (item.reminderAtMillis != null) {
                                put("reminder_at", formatIso(item.reminderAtMillis))
                            }
                            putJsonArray("tags") { item.tags.forEach { add(JsonPrimitive(it)) } }
                        })
                    }
                }
                if (voiceMemos.isNotEmpty()) {
                    putJsonArray("voice_memos") {
                        voiceMemos.take(limit).forEach { memo ->
                            add(buildJsonObject {
                                put("id", memo.id)
                                put("type", "VOICE_MEMO")
                                put("title", memo.title)
                                put("brief_summary", (memo.summary ?: memo.transcript ?: "").take(140))
                                put("workspace_path", "/workspace/transcripts/${memo.id}.md")
                            })
                        }
                    }
                }
                if (stories.isNotEmpty()) {
                    putJsonArray("stories") {
                        stories.take(limit).forEach { story ->
                            add(buildJsonObject {
                                put("id", story.id)
                                put("type", "STORY")
                                put("title", story.title)
                                put("status", story.status.name)
                                put("total_words", story.totalWords)
                                put("brief_summary", story.premise.take(140))
                                put("workspace_path", "/workspace/studio/stories/${story.id}.md")
                            })
                        }
                    }
                }
            }
        }
    )

    val readDef = previews.getValue("studio_read_item")
    val readTool = Tool(
        name = readDef.name,
        description = readDef.description,
        parameters = { readDef.schema },
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val item = studioHubRepository.getItem(id)
            if (item != null) {
                buildJsonObject {
                    put("id", item.id)
                    put("type", item.type.name)
                    put("title", item.title)
                    put("body", item.body)
                    put("brief_summary", item.briefSummary)
                    put("pinned", item.isPinned)
                    put("completed", item.isCompleted)
                    put("archived", item.isArchived)
                    put("created_by", item.createdBy)
                    put("workspace_path", "/workspace/studio/${item.id}.md")
                    item.colorToken?.let { put("color", it) }
                    item.sourceUrl?.let { put("source_url", it) }
                    item.reminderAtMillis?.let { put("reminder_at", formatIso(it)) }
                    putJsonArray("tags") { item.tags.forEach { add(JsonPrimitive(it)) } }
                    putJsonArray("attachments") { item.attachmentPaths.forEach { add(JsonPrimitive(it)) } }
                    putJsonArray("checklist") {
                        item.checklist.forEach { entry ->
                            add(buildJsonObject {
                                put("id", entry.id)
                                put("text", entry.text)
                                put("checked", entry.isChecked)
                            })
                        }
                    }
                }
            } else {
                buildJsonObject {
                    put("error", "Studio item '$id' not found. Check /workspace/studio/index.json or call studio_list_items.")
                }
            }
        }
    )

    val createDef = previews.getValue("studio_create_item")
    val createTool = Tool(
        name = createDef.name,
        description = createDef.description,
        parameters = { createDef.schema },
        execute = { args ->
            val params = args.jsonObject
            val rawType = params["type"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase() ?: "NOTE"
            val type = runCatching { StudioItemType.valueOf(rawType) }.getOrDefault(StudioItemType.NOTE)
            val title = params["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val body = params["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val checklist = params["checklist"]?.jsonArray?.mapNotNull { el ->
                el.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let {
                    StudioChecklistEntry(text = it)
                }
            }.orEmpty()
            val tags = params["tags"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.trim()?.takeIf { t -> t.isNotBlank() }
            }.orEmpty()
            val reminderIso = params["reminderAtIso"]?.jsonPrimitive?.contentOrNull?.trim()
            val reminderMillis = reminderIso?.takeIf { it.isNotBlank() }?.let(::parseIsoMillis)
            if (!reminderIso.isNullOrBlank() && reminderMillis == null) {
                return@Tool buildJsonObject {
                    put("error", "Invalid reminderAtIso '$reminderIso'. Use ISO-8601 format like 2026-11-28T09:00:00 or 2026-11-28T09:00:00Z.")
                }
            }
            val pinned = params["pinned"]?.jsonPrimitive?.booleanOrNull ?: false
            val color = params["color"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                ?.takeIf { it in setOf("amber", "sage", "sky", "rose", "lavender") }

            val created = studioHubRepository.createItem(
                type = type,
                title = title,
                body = body,
                checklist = checklist,
                tags = tags,
                colorToken = color,
                isPinned = pinned,
                reminderAtMillis = reminderMillis,
                createdBy = "assistant",
            )
            buildJsonObject {
                put("status", "created")
                put("id", created.id)
                put("type", created.type.name)
                put("title", created.title)
                put("brief_summary", created.briefSummary)
                put("workspace_path", "/workspace/studio/${created.id}.md")
            }
        }
    )

    val updateDef = previews.getValue("studio_update_item")
    val updateTool = Tool(
        name = updateDef.name,
        description = updateDef.description,
        parameters = { updateDef.schema },
        execute = { args ->
            val params = args.jsonObject
            val id = params["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val existing = studioHubRepository.getItem(id) ?: return@Tool buildJsonObject {
                put("error", "Studio item '$id' not found.")
            }

            val newTitle = params["title"]?.jsonPrimitive?.contentOrNull ?: existing.title
            val newBody = params["body"]?.jsonPrimitive?.contentOrNull ?: existing.body
            val addItems = params["addChecklistItems"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotBlank() }?.let { txt ->
                    StudioChecklistEntry(text = txt)
                }
            }.orEmpty()
            val toggleTargets = params["toggleChecklistItemTexts"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.trim()?.lowercase()?.takeIf { s -> s.isNotBlank() }
            }.orEmpty()

            val updatedChecklist = (existing.checklist + addItems).map { entry ->
                if (toggleTargets.any { target -> entry.text.lowercase().contains(target) }) {
                    entry.copy(isChecked = !entry.isChecked)
                } else {
                    entry
                }
            }

            val newTags = params["tags"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.trim()?.takeIf { t -> t.isNotBlank() }
            } ?: existing.tags

            val hasReminderParam = params.containsKey("reminderAtIso")
            val reminderMillis = if (hasReminderParam) {
                val raw = params["reminderAtIso"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (raw.isBlank()) {
                    null
                } else {
                    parseIsoMillis(raw) ?: return@Tool buildJsonObject {
                        put("error", "Invalid reminderAtIso '$raw'. Use ISO-8601 format.")
                    }
                }
            } else {
                existing.reminderAtMillis
            }

            val updated = studioHubRepository.upsertItem(
                existing.copy(
                    title = newTitle,
                    body = newBody,
                    checklist = updatedChecklist,
                    tags = newTags,
                    reminderAtMillis = reminderMillis,
                    isPinned = params["pinned"]?.jsonPrimitive?.booleanOrNull ?: existing.isPinned,
                    isCompleted = params["completed"]?.jsonPrimitive?.booleanOrNull ?: existing.isCompleted,
                    isArchived = params["archived"]?.jsonPrimitive?.booleanOrNull ?: existing.isArchived,
                )
            )
            buildJsonObject {
                put("status", "updated")
                put("id", updated.id)
                put("title", updated.title)
                put("brief_summary", updated.briefSummary)
            }
        }
    )

    val deleteDef = previews.getValue("studio_delete_item")
    val deleteTool = Tool(
        name = deleteDef.name,
        description = deleteDef.description,
        parameters = { deleteDef.schema },
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val deleted = studioHubRepository.deleteItem(id)
            buildJsonObject {
                put("status", if (deleted) "deleted" else "not_found")
                put("id", id)
            }
        }
    )

    return listOf(listTool, readTool, createTool, updateTool, deleteTool)
}
