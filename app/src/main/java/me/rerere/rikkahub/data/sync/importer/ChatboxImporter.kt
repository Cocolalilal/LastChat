package me.rerere.rikkahub.data.sync.importer

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant as KotlinInstant
import kotlin.uuid.Uuid

object ChatboxImporter {
    data class Result(
        val providers: List<ProviderSetting>,
        val conversations: List<Conversation>,
        val assistants: List<Assistant>,
    )

    fun import(file: File): Result {
        return import(file.readText())
    }

    fun import(json: String): Result {
        val root = JsonInstant.parseToJsonElement(json).jsonObject
        val providerParseResult = parseProviders(root["settings"]?.let { it.jsonObjectOrNull })
        val assistantParseResult = parseAssistants(root["myCopilots"]?.let { it.jsonArrayOrNull })
        val conversations = parseConversations(
            root = root,
            copilotIdToAssistantId = assistantParseResult.copilotIdToAssistantId,
            modelIdLookup = providerParseResult.modelIdLookup,
        )

        return Result(
            providers = providerParseResult.providers,
            conversations = conversations,
            assistants = assistantParseResult.assistants,
        )
    }

    private data class ProviderParseResult(
        val providers: List<ProviderSetting>,
        val modelIdLookup: Map<ModelLookupKey, Uuid>,
    )

    private data class AssistantParseResult(
        val assistants: List<Assistant>,
        val copilotIdToAssistantId: Map<String, Uuid>,
    )

    private data class ModelLookupKey(
        val provider: String,
        val model: String,
    )

    private fun parseProviders(settings: JsonObject?): ProviderParseResult {
        val providersRoot = settings?.get("providers")?.jsonObjectOrNull ?: return ProviderParseResult(emptyList(), emptyMap())
        val modelIdLookup = mutableMapOf<ModelLookupKey, Uuid>()
        val providers = providersRoot.mapNotNull { (providerKey, element) ->
            val provider = element.jsonObjectOrNull ?: return@mapNotNull null
            parseProvider(providerKey, provider, modelIdLookup)
        }
        return ProviderParseResult(providers, modelIdLookup)
    }

    private fun parseProvider(
        providerKey: String,
        provider: JsonObject,
        modelIdLookup: MutableMap<ModelLookupKey, Uuid>,
    ): ProviderSetting? {
        val apiKey = provider.string("apiKey", "api_key", "key").orEmpty()
        if (apiKey.isBlank()) return null

        val models = parseModels(
            providerKey = providerKey,
            models = provider["models"]?.jsonArrayOrNull,
            modelIdLookup = modelIdLookup,
        )
        val name = provider.string("name", "nickname")?.ifBlank { null }
            ?: providerKey.replaceFirstChar { it.titlecase() }
        val apiHost = provider.string("apiHost", "api_host", "baseUrl", "baseURL", "url").orEmpty()
        val enabled = provider["enabled"]?.jsonPrimitiveOrNull?.booleanOrNull ?: true

        return when (providerKey.lowercase()) {
            "claude", "anthropic" -> ProviderSetting.Claude(
                name = name,
                enabled = enabled,
                baseUrl = normalizeBaseUrl(apiHost, "/v1", ProviderSetting.Claude().baseUrl),
                apiKey = apiKey,
                models = models,
            )

            "gemini", "google", "google-ai", "googleai" -> ProviderSetting.Google(
                name = name,
                enabled = enabled,
                baseUrl = normalizeBaseUrl(apiHost, "/v1beta", ProviderSetting.Google().baseUrl),
                apiKey = apiKey,
                models = models,
            )

            else -> ProviderSetting.OpenAI(
                name = name,
                enabled = enabled,
                baseUrl = normalizeOpenAIBaseUrl(providerKey, apiHost),
                apiKey = apiKey,
                models = models,
            )
        }
    }

    private fun parseModels(
        providerKey: String,
        models: JsonArray?,
        modelIdLookup: MutableMap<ModelLookupKey, Uuid>,
    ): List<Model> {
        if (models == null) return emptyList()
        return models.mapNotNull { element ->
            val modelObj = element.jsonObjectOrNull ?: return@mapNotNull null
            val modelId = modelObj.string("modelId", "id")?.trim().orEmpty()
            if (modelId.isBlank()) return@mapNotNull null
            val displayName = modelObj.string("name", "nickname", "displayName")?.ifBlank { modelId } ?: modelId
            val capabilities = modelObj["capabilities"]?.jsonArrayOrNull
                ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
                .orEmpty()
            val model = Model(
                modelId = modelId,
                displayName = displayName,
                inputModalities = buildList {
                    add(Modality.TEXT)
                    if (capabilities.any { it.equals("vision", ignoreCase = true) }) add(Modality.IMAGE)
                },
                abilities = buildList {
                    if (capabilities.any { it.equals("tool_use", ignoreCase = true) }) add(ModelAbility.TOOL)
                    if (capabilities.any { it.equals("reasoning", ignoreCase = true) }) add(ModelAbility.REASONING)
                },
            )

            addModelLookup(modelIdLookup, providerKey, modelId, model.id)
            addModelLookup(modelIdLookup, providerKey, displayName, model.id)
            model
        }
    }

    private fun parseAssistants(copilots: JsonArray?): AssistantParseResult {
        if (copilots == null) return AssistantParseResult(emptyList(), emptyMap())

        val assistants = mutableListOf<Assistant>()
        val copilotIdToAssistantId = mutableMapOf<String, Uuid>()
        copilots.forEach { element ->
            val copilot = element.jsonObjectOrNull ?: return@forEach
            val copilotId = copilot.string("id")?.takeIf { it.isNotBlank() } ?: return@forEach
            val assistantId = Uuid.random()
            copilotIdToAssistantId[copilotId] = assistantId
            assistants += Assistant(
                id = assistantId,
                name = copilot.string("name")?.ifBlank { null } ?: "Imported Copilot",
                systemPrompt = copilot["config"]
                    ?.jsonObjectOrNull
                    ?.string("systemPrompt", "system_prompt")
                    .orEmpty(),
            )
        }

        return AssistantParseResult(assistants, copilotIdToAssistantId)
    }

    private fun parseConversations(
        root: JsonObject,
        copilotIdToAssistantId: Map<String, Uuid>,
        modelIdLookup: Map<ModelLookupKey, Uuid>,
    ): List<Conversation> {
        return root.mapNotNull { (key, element) ->
            if (!key.startsWith("session:")) return@mapNotNull null
            val session = element.jsonObjectOrNull ?: return@mapNotNull null
            parseConversation(session, copilotIdToAssistantId, modelIdLookup)
        }
    }

    private fun parseConversation(
        session: JsonObject,
        copilotIdToAssistantId: Map<String, Uuid>,
        modelIdLookup: Map<ModelLookupKey, Uuid>,
    ): Conversation? {
        val messagesArray = session["messages"]?.jsonArrayOrNull ?: return null
        val provider = session["settings"]?.jsonObjectOrNull?.string("provider")
        val sessionModel = session["settings"]?.jsonObjectOrNull?.string("modelId")
        val uiMessages = messagesArray.mapNotNull { element ->
            parseMessage(
                msg = element.jsonObjectOrNull ?: return@mapNotNull null,
                sessionProvider = provider,
                sessionModel = sessionModel,
                modelIdLookup = modelIdLookup,
            )
        }
        if (uiMessages.isEmpty()) return null

        val timestamps = messagesArray.mapNotNull { it.jsonObjectOrNull?.long("timestamp") }
        val now = System.currentTimeMillis()
        val createAt = timestamps.minOrNull() ?: now
        val updateAt = timestamps.maxOrNull() ?: createAt
        val assistantId = session.string("copilotId")
            ?.let { copilotIdToAssistantId[it] }
            ?: DEFAULT_ASSISTANT_ID

        return Conversation(
            id = Uuid.random(),
            assistantId = assistantId,
            title = session.string("name")?.ifBlank { null } ?: "Chatbox Import",
            messageNodes = uiMessages.map(MessageNode::of),
            isPinned = session["starred"]?.jsonPrimitiveOrNull?.booleanOrNull ?: false,
            createAt = Instant.ofEpochMilli(createAt),
            updateAt = Instant.ofEpochMilli(updateAt),
        )
    }

    private fun parseMessage(
        msg: JsonObject,
        sessionProvider: String?,
        sessionModel: String?,
        modelIdLookup: Map<ModelLookupKey, Uuid>,
    ): UIMessage? {
        val parts = parseMessageParts(msg)
        if (parts.isEmptyUIMessage()) return null

        val provider = msg.string("aiProvider") ?: sessionProvider
        val rawModel = msg.string("model", "modelId") ?: sessionModel
        val timestamp = msg.long("timestamp") ?: System.currentTimeMillis()

        return UIMessage(
            id = msg.string("id")?.toUuidOrNull() ?: Uuid.random(),
            role = when (msg.string("role")?.lowercase()) {
                "assistant" -> MessageRole.ASSISTANT
                "system" -> MessageRole.SYSTEM
                else -> MessageRole.USER
            },
            parts = parts,
            createdAt = KotlinInstant.fromEpochMilliseconds(timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
            modelId = resolveModelId(provider, rawModel, modelIdLookup),
            usage = parseUsage(msg["usage"]?.jsonObjectOrNull),
            generationDurationMs = msg.long("generationDurationMs", "duration"),
        )
    }

    private fun parseMessageParts(msg: JsonObject): List<UIMessagePart> {
        val contentParts = msg["contentParts"]?.jsonArrayOrNull
        if (contentParts == null) {
            return msg.string("content")
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(UIMessagePart.Text(it)) }
                .orEmpty()
        }

        return contentParts.mapNotNull { element ->
            val part = element.jsonObjectOrNull ?: return@mapNotNull null
            when (part.string("type")?.lowercase()) {
                "text" -> part.string("text")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(UIMessagePart::Text)

                "image" -> part.string("url")
                    ?.cleanChatboxUrl()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(UIMessagePart::Image)

                "reasoning" -> part.string("text")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { reasoning ->
                        val startedAt = part.long("startTime")
                            ?.let(KotlinInstant::fromEpochMilliseconds)
                            ?: kotlin.time.Clock.System.now()
                        UIMessagePart.Reasoning(
                            reasoning = reasoning,
                            createdAt = startedAt,
                            finishedAt = part.long("duration")?.let { startedAt + it.milliseconds },
                        )
                    }

                "thinking" -> part.string("text")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(UIMessagePart::Thinking)

                "tool-call" -> parseToolPart(part)
                else -> null
            }
        }
    }

    private fun parseToolPart(part: JsonObject): UIMessagePart? {
        val toolCallId = part.string("toolCallId", "id") ?: return null
        val toolName = part.string("toolName", "name").orEmpty()
        val args = part["args"] ?: JsonObject(emptyMap())
        val result = part["result"]

        return if (result != null && result !is JsonNull) {
            UIMessagePart.ToolResult(
                toolCallId = toolCallId,
                toolName = toolName,
                arguments = args,
                content = result,
            )
        } else {
            UIMessagePart.ToolCall(
                toolCallId = toolCallId,
                toolName = toolName,
                arguments = JsonInstant.encodeToString(JsonElement.serializer(), args),
            )
        }
    }

    private fun parseUsage(usage: JsonObject?): TokenUsage? {
        if (usage == null) return null
        val promptTokens = usage.int("promptTokens", "inputTokens", "prompt_tokens", "input_tokens")
        val completionTokens = usage.int("completionTokens", "outputTokens", "completion_tokens", "output_tokens")
        val cachedTokens = usage.int("cachedTokens", "cachedInputTokens", "cached_tokens", "cached_input_tokens")
        val totalTokens = usage.int("totalTokens", "total_tokens")
            ?: listOfNotNull(promptTokens, completionTokens).sum().takeIf { it > 0 }
        if (promptTokens == null && completionTokens == null && cachedTokens == null && totalTokens == null) return null

        return TokenUsage(
            promptTokens = promptTokens ?: 0,
            completionTokens = completionTokens ?: 0,
            cachedTokens = cachedTokens ?: 0,
            totalTokens = totalTokens ?: 0,
        )
    }

    private fun resolveModelId(
        provider: String?,
        rawModel: String?,
        modelIdLookup: Map<ModelLookupKey, Uuid>,
    ): Uuid? {
        if (provider.isNullOrBlank() || rawModel.isNullOrBlank()) return null
        return modelIdLookup[ModelLookupKey(provider.normalizedKey(), rawModel.normalizedKey())]
    }

    private fun addModelLookup(
        lookup: MutableMap<ModelLookupKey, Uuid>,
        provider: String,
        model: String,
        uuid: Uuid,
    ) {
        if (provider.isBlank() || model.isBlank()) return
        lookup[ModelLookupKey(provider.normalizedKey(), model.normalizedKey())] = uuid
    }

    private fun normalizeOpenAIBaseUrl(providerKey: String, apiHost: String): String {
        val fallback = when (providerKey.lowercase()) {
            "openrouter" -> "https://openrouter.ai/api/v1"
            else -> ProviderSetting.OpenAI().baseUrl
        }
        return normalizeBaseUrl(apiHost, "/v1", fallback)
    }

    private fun normalizeBaseUrl(apiHost: String, suffix: String, fallback: String): String {
        val normalizedHost = apiHost.trim().trimEnd('/')
        if (normalizedHost.isBlank()) return fallback
        return if (normalizedHost.endsWith(suffix)) normalizedHost else "$normalizedHost$suffix"
    }

    private fun JsonObject.string(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitiveOrNull?.contentOrNull
        }
    }

    private fun JsonObject.long(vararg keys: String): Long? {
        return keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitiveOrNull?.longOrNull
                ?: this[key]?.jsonPrimitiveOrNull?.contentOrNull?.toLongOrNull()
        }
    }

    private fun JsonObject.int(vararg keys: String): Int? {
        return keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitiveOrNull?.intOrNull
                ?: this[key]?.jsonPrimitiveOrNull?.contentOrNull?.toIntOrNull()
        }
    }

    private fun String.normalizedKey(): String = trim().lowercase()

    private fun String.toUuidOrNull(): Uuid? {
        return runCatching { Uuid.parse(this) }.getOrNull()
    }

    private fun String.cleanChatboxUrl(): String {
        val trimmed = trim()
        val markdownUrl = Regex("""^\[[^\]]*]\(([^)]+)\)$""").matchEntire(trimmed)
        return markdownUrl?.groupValues?.getOrNull(1) ?: trimmed
    }
}
