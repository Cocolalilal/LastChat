package me.rerere.rikkahub.data.ai.local

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.Role
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.tool as liteRtTool
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

private const val TAG = "LiteRtRuntimeEngine"
private const val DEFAULT_TOP_K = 40
private const val DEFAULT_TOP_P = 0.95
private const val DEFAULT_TEMPERATURE = 0.8
private const val MIN_CONTEXT_TOKENS = 1024
private const val DEFAULT_CONTEXT_TOKENS = 2048
private const val MAX_CONTEXT_TOKENS = 8192

@OptIn(ExperimentalApi::class)
class LiteRtRuntimeEngine(
    private val context: Context,
    private val compatibilityEstimator: LocalCompatibilityEstimator,
) : LocalRuntimeEngine {
    private val activeGeneration = AtomicBoolean(false)
    private val engineMutex = Mutex()

    @Volatile
    private var loadedEngine: LoadedEngine? = null

    @Volatile
    private var currentConversation: Conversation? = null

    @Volatile
    private var lastStats: LocalRuntimeStats? = null

    override suspend fun load(install: LocalModelInstallEntity) {
        val entry = install.toCatalogEntryOrNull()
            ?: throw LocalRuntimeUnavailableException("Missing local model metadata.")
        val modelPath = install.resolvePrimaryModelPath()
        engineMutex.withLock {
            val existing = loadedEngine
            if (existing?.modelPath == modelPath) {
                return
            }

            currentConversation?.closeQuietly()
            currentConversation = null
            existing?.engine?.closeQuietly()

            ExperimentalFlags.enableBenchmark = true
            ExperimentalFlags.enableConversationConstrainedDecoding = true

            val contextTokens = resolveContextTokens(entry)
            val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }
            val backend = Backend.CPU()
            val visionBackend = Backend.GPU()
            val engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = if (Modality.IMAGE in entry.inputModalities) visionBackend else null,
                    audioBackend = if ("audio" in entry.featureTags) backend else null,
                    maxNumTokens = contextTokens,
                    maxNumImages = if (Modality.IMAGE in entry.inputModalities) 8 else null,
                    cacheDir = cacheDir.absolutePath,
                )
            )
            engine.initialize()
            loadedEngine = LoadedEngine(
                catalogId = install.catalogId,
                modelPath = modelPath,
                engine = engine,
                contextTokens = contextTokens,
            )
        }
    }

    override suspend fun warmup(install: LocalModelInstallEntity) {
        // Loading the engine is enough for v1 warmup.
    }

    override suspend fun createEmbeddings(
        install: LocalModelInstallEntity,
        input: List<String>,
    ): List<List<Float>> {
        throw LocalRuntimeUnavailableException(
            "The LiteRT-LM runtime does not support embedding generation. " +
            "Use a GGUF embedding model (e.g. Nomic Embed v1.5) instead."
        )
    }

    override suspend fun generate(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        ensureSingleActiveSession()
        val startedAt = System.currentTimeMillis()
        var conversation: Conversation? = null
        try {
            val prepared = prepareConversation(
                install = install,
                messages = messages,
                params = params,
            )
            conversation = prepared.conversation
            currentConversation = conversation
            val response = conversation.sendMessage(
                contents = prepared.outgoingContents,
                extraContext = prepared.extraContext,
            )
            response.toMessageChunk(
                requestId = Uuid.random().toString(),
                modelId = params.model.modelId,
                asDelta = false,
            )
        } finally {
            lastStats = buildStats(conversation, startedAt)
            currentConversation = null
            conversation?.closeQuietly()
            activeGeneration.set(false)
        }
    }

    override fun stream(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        ensureSingleActiveSession()
        val startedAt = System.currentTimeMillis()
        val requestId = Uuid.random().toString()
        var conversation: Conversation? = null
        try {
            val prepared = prepareConversation(
                install = install,
                messages = messages,
                params = params,
            )
            conversation = prepared.conversation
            currentConversation = conversation
            conversation.sendMessageAsync(
                contents = prepared.outgoingContents,
                extraContext = prepared.extraContext,
            ).collect { partial ->
                emit(
                    partial.toMessageChunk(
                        requestId = requestId,
                        modelId = params.model.modelId,
                        asDelta = true,
                    )
                )
            }
        } finally {
            lastStats = buildStats(conversation, startedAt)
            currentConversation = null
            conversation?.closeQuietly()
            activeGeneration.set(false)
        }
    }

    override suspend fun cancel() {
        currentConversation?.cancelProcess()
    }

    override suspend fun unload() {
        currentConversation?.closeQuietly()
        currentConversation = null
        engineMutex.withLock {
            loadedEngine?.engine?.closeQuietly()
            loadedEngine = null
        }
        activeGeneration.set(false)
    }

    override suspend fun getStats(): LocalRuntimeStats? = lastStats

    private fun ensureSingleActiveSession() {
        if (!activeGeneration.compareAndSet(false, true)) {
            throw LocalRuntimeBusyException()
        }
    }

    private suspend fun prepareConversation(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): PreparedConversation = withContext(Dispatchers.IO) {
        val entry = install.toCatalogEntryOrNull()
            ?: throw LocalRuntimeUnavailableException("Missing local model metadata.")
        val engine = engineMutex.withLock {
            loadedEngine?.takeIf { it.catalogId == install.catalogId }?.engine
                ?: throw LocalRuntimeUnavailableException("Local runtime is not loaded.")
        }

        val systemInstruction = messages
            .filter { message -> message.role == MessageRole.SYSTEM }
            .flatMap { message -> message.buildLiteRtContents(context, entry).contents }
            .takeIf { contents -> contents.isNotEmpty() }
            ?.let { contents -> Contents.of(contents) }

        val liteRtMessages = messages
            .filter { message -> message.role != MessageRole.SYSTEM }
            .mapNotNull { message ->
                message.toLiteRtMessage(
                    context = context,
                    entry = entry,
                )
            }
        require(liteRtMessages.isNotEmpty()) {
            "Local inference requires at least one message."
        }

        val outgoingIndex = liteRtMessages.indexOfLast { message -> message.role == Role.USER }
        require(outgoingIndex >= 0) {
            "Local inference requires a user message to generate from."
        }

        val outgoingMessage = liteRtMessages[outgoingIndex]

        // --- Context window truncation ---
        // Use the resolved context size from load(), which adapts to model + device.
        val contextTokens = engineMutex.withLock {
            loadedEngine?.contextTokens ?: DEFAULT_CONTEXT_TOKENS
        }
        // Reserve tokens for the model's output so we don't fill the entire window with input.
        val generationReserve = contextTokens / 4  // 25% for generation
        val inputBudget = contextTokens - generationReserve

        val systemTokens = systemInstruction?.contents
            ?.sumOf { content -> estimateContentTokens(content) } ?: 0
        val outgoingTokens = outgoingMessage.contents.contents
            .sumOf { content -> estimateContentTokens(content) }

        var remainingBudget = inputBudget - systemTokens - outgoingTokens

        // Keep as many recent history messages as fit, dropping the oldest first.
        val allHistory = liteRtMessages.take(outgoingIndex)
        val trimmedHistory = mutableListOf<Message>()
        for (message in allHistory.reversed()) {
            val cost = message.contents.contents.sumOf { content -> estimateContentTokens(content) }
            if (remainingBudget >= cost) {
                trimmedHistory.add(0, message)
                remainingBudget -= cost
            } else {
                // Once we can't fit a message, stop (older messages are even less important).
                break
            }
        }
        if (trimmedHistory.size < allHistory.size) {
            Log.i(TAG, "Trimmed local context: kept ${trimmedHistory.size}/${allHistory.size} history messages " +
                "(budget=$inputBudget, system=$systemTokens, outgoing=$outgoingTokens)")
        }
        // --- End context window truncation ---

        val conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = systemInstruction,
                automaticToolCalling = false,
                initialMessages = trimmedHistory,
                samplerConfig = buildSamplerConfig(params),
                tools = buildLiteRtTools(params.tools, params),
                channels = if (params.thinkingBudget != null) listOf(
                    com.google.ai.edge.litertlm.Channel(
                        channelName = "thinking",
                        start = "<think>",
                        end = "</think>"
                    )
                ) else null,
            )
        )
        PreparedConversation(
            conversation = conversation,
            outgoingContents = outgoingMessage.contents,
            extraContext = buildExtraContext(params),
        )
    }

    private fun buildLiteRtTools(
        tools: List<Tool>,
        params: TextGenerationParams,
    ): List<ToolProvider> {
        if (tools.isEmpty() || ModelAbility.TOOL !in params.model.abilities) {
            return emptyList()
        }
        return tools.map { tool -> liteRtTool(LiteRtOpenApiTool(tool)) }
    }

    private fun buildExtraContext(params: TextGenerationParams): Map<String, Any> {
        return emptyMap()
    }

    private fun buildSamplerConfig(params: TextGenerationParams): SamplerConfig? {
        if (params.temperature == null && params.topP == null) {
            return null
        }
        return SamplerConfig(
            topK = DEFAULT_TOP_K,
            topP = params.topP?.toDouble() ?: DEFAULT_TOP_P,
            temperature = params.temperature?.toDouble() ?: DEFAULT_TEMPERATURE,
        )
    }

    private fun buildStats(
        conversation: Conversation?,
        startedAt: Long,
    ): LocalRuntimeStats {
        val benchmark = runCatching { conversation?.getBenchmarkInfo() }.getOrNull()
        return LocalRuntimeStats(
            promptTokens = benchmark?.lastPrefillTokenCount?.toInt(),
            completionTokens = benchmark?.lastDecodeTokenCount?.toInt(),
            durationMs = System.currentTimeMillis() - startedAt,
        )
    }

    /**
     * Resolves the context window size for a model based on:
     * 1. Per-model [LocalModelCatalogEntry.maxContextTokens] override (if set)
     * 2. Available device RAM relative to the model's memory footprint
     * 3. Low-RAM device flag
     * 4. Model size tier (small models get more headroom per token)
     */
    private fun resolveContextTokens(entry: LocalModelCatalogEntry): Int {
        // 1. If the model explicitly declares a context size, honour it (clamped to safe bounds).
        entry.maxContextTokens?.let { explicit ->
            val clamped = explicit.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)
            Log.i(TAG, "resolveContextTokens: using model override $clamped for ${entry.displayName}")
            return clamped
        }

        // 2. Adaptive heuristic based on device profile.
        val profile = runCatching { compatibilityEstimator.currentDeviceProfile() }.getOrNull()
        if (profile == null) {
            Log.w(TAG, "resolveContextTokens: device profile unavailable, using default $DEFAULT_CONTEXT_TOKENS")
            return DEFAULT_CONTEXT_TOKENS
        }

        // How much free RAM is available beyond what the model needs to run.
        val ramHeadroom = profile.availableRamBytes - entry.minimumRamBytes
        val isSmallModel = "small" in entry.featureTags || entry.minimumRamBytes <= 3L * 1024 * 1024 * 1024

        val resolved = when {
            // Very tight: device is low-RAM or barely has enough memory to load the model.
            profile.lowRamDevice || ramHeadroom < 512L * 1024 * 1024 -> {
                if (isSmallModel) DEFAULT_CONTEXT_TOKENS else MIN_CONTEXT_TOKENS
            }
            // Comfortable: plenty of headroom, small model -> go big.
            isSmallModel && ramHeadroom > 2L * 1024 * 1024 * 1024 -> 4096
            // Moderate headroom with small model.
            isSmallModel -> DEFAULT_CONTEXT_TOKENS * 2  // 4096
            // Larger model with good headroom.
            ramHeadroom > 4L * 1024 * 1024 * 1024 -> DEFAULT_CONTEXT_TOKENS * 2  // 4096
            // Larger model, moderate headroom.
            ramHeadroom > 1L * 1024 * 1024 * 1024 -> DEFAULT_CONTEXT_TOKENS
            // Tight but not critical.
            else -> MIN_CONTEXT_TOKENS
        }.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)

        Log.i(TAG, "resolveContextTokens: ${entry.displayName} -> $resolved tokens " +
            "(availRAM=${profile.availableRamBytes / (1024*1024)}MB, " +
            "modelMin=${entry.minimumRamBytes / (1024*1024)}MB, " +
            "headroom=${ramHeadroom / (1024*1024)}MB, " +
            "lowRAM=${profile.lowRamDevice}, small=$isSmallModel)")
        return resolved
    }
}

private data class LoadedEngine(
    val catalogId: String,
    val modelPath: String,
    val engine: Engine,
    val contextTokens: Int,
)

private data class PreparedConversation(
    val conversation: Conversation,
    val outgoingContents: Contents,
    val extraContext: Map<String, Any>,
)

/**
 * Rough token estimation for a single [Content] element.
 * Uses ~4 characters per token for text (same heuristic as [GenerationHandler]).
 * Non-text content (images, audio) gets a flat conservative estimate.
 */
private fun estimateContentTokens(content: Content): Int {
    return when (content) {
        is Content.Text -> (content.text.length + 3) / 4  // ceil division
        is Content.ImageBytes -> 256  // conservative flat cost for an image
        is Content.AudioBytes -> 256
        is Content.ToolResponse -> (content.response.toString().length + 3) / 4
        else -> 64  // unknown content type fallback
    }
}

private class LiteRtOpenApiTool(
    private val tool: Tool,
) : OpenApiTool {
    override fun execute(paramsJsonString: String): String {
        Log.w(TAG, "LiteRT requested in-runtime tool execution for ${tool.name}, which is disabled.")
        return "{}"
    }

    override fun getToolDescriptionJsonString(): String {
        val parameters = when (val schema = tool.parameters()) {
            is InputSchema.Obj -> buildJsonObject {
                put("type", "object")
                put("properties", schema.properties)
                schema.required?.let { required ->
                    put("required", buildJsonArray {
                        required.forEach { value ->
                            add(JsonPrimitive(value))
                        }
                    })
                }
            }

            null -> buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
            }
        }
        return buildJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", parameters)
        }.toString()
    }
}

private fun LocalModelInstallEntity.resolvePrimaryModelPath(): String {
    val paths = runCatching {
        JsonInstant.decodeFromString<List<String>>(filePathsJson)
    }.getOrDefault(emptyList())
        .map(::File)
        .filter(File::exists)
    val primary = paths
        .sortedWith(
            compareBy<File> { !it.name.endsWith(".litertlm", ignoreCase = true) }
                .thenBy { !it.name.contains(modelId, ignoreCase = true) }
                .thenBy { it.name.length }
        )
        .firstOrNull()
        ?: throw LocalRuntimeUnavailableException("Model files are missing on disk.")
    return primary.absolutePath
}

private fun UIMessage.toLiteRtMessage(
    context: Context,
    entry: LocalModelCatalogEntry,
): Message? {
    val message = when (role) {
        MessageRole.SYSTEM -> Message.system(contents = buildLiteRtContents(context, entry))
        MessageRole.USER -> Message.user(contents = buildLiteRtContents(context, entry))
        MessageRole.ASSISTANT -> Message.model(
            contents = buildLiteRtContents(context, entry),
            toolCalls = parts.filterIsInstance<UIMessagePart.ToolCall>().mapNotNull { part ->
                val argumentsElement = runCatching {
                    JsonInstant.parseToJsonElement(part.arguments)
                }.getOrDefault(JsonObject(emptyMap()))
                @Suppress("UNCHECKED_CAST")
                val arguments = argumentsElement.toLiteRtValue() as? Map<String, *>
                    ?: emptyMap<String, Any?>()
                ToolCall(name = part.toolName, arguments = arguments)
            },
        )

        MessageRole.TOOL -> Message.tool(contents = buildLiteRtToolContents())
    }

    return message.takeIf {
        it.contents.contents.isNotEmpty() || it.toolCalls.isNotEmpty() || it.role == Role.SYSTEM
    }
}

private fun UIMessage.buildLiteRtContents(
    context: Context,
    entry: LocalModelCatalogEntry,
): Contents {
    val contents = buildList {
        parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    if (part.text.isNotBlank()) {
                        add(Content.Text(part.text))
                    }
                }

                is UIMessagePart.Image -> {
                    readAttachmentBytes(context, part.url)?.let { bytes ->
                        add(Content.ImageBytes(bytes))
                    }
                }

                is UIMessagePart.Audio -> {
                    if ("audio" in entry.featureTags) {
                        readAttachmentBytes(context, part.url)?.let { bytes ->
                            add(Content.AudioBytes(bytes))
                        }
                    }
                }

                is UIMessagePart.Document -> {
                    add(Content.Text("Attached document: ${part.fileName}"))
                }

                else -> Unit
            }
        }
    }
    return Contents.of(contents)
}

private fun UIMessage.buildLiteRtToolContents(): Contents {
    val contents = parts.filterIsInstance<UIMessagePart.ToolResult>().map { result ->
        Content.ToolResponse(
            name = result.toolName,
            response = result.content.toLiteRtValue(),
        )
    }
    return Contents.of(contents)
}

private fun Message.toMessageChunk(
    requestId: String,
    modelId: String,
    asDelta: Boolean,
): MessageChunk {
    val uiMessage = toUiMessage(requestId = requestId, streaming = asDelta)
    return MessageChunk(
        id = requestId,
        model = modelId,
        choices = listOf(
            UIMessageChoice(
                index = 0,
                delta = if (asDelta) uiMessage else null,
                message = if (asDelta) null else uiMessage,
                finishReason = null,
            )
        ),
    )
}

private fun Message.toUiMessage(
    requestId: String,
    streaming: Boolean,
): UIMessage {
    val parts = buildList {
        contents.contents.forEach { content ->
            when (content) {
                is Content.Text -> add(UIMessagePart.Text(content.text))
                else -> Unit
            }
        }
        channels.forEach { (name, value) ->
            if (value.isBlank()) return@forEach
            add(
                UIMessagePart.Reasoning(
                    reasoning = value,
                    finishedAt = if (streaming) null else kotlin.time.Clock.System.now(),
                    metadata = buildJsonObject {
                        put("channel", name)
                    },
                )
            )
        }
        toolCalls.forEachIndexed { index, toolCall ->
            add(
                UIMessagePart.ToolCall(
                    toolCallId = buildToolCallId(requestId, index, toolCall),
                    toolName = toolCall.name,
                    arguments = toolCall.arguments.toJsonElement().toString(),
                )
            )
        }
    }
    return UIMessage(
        role = when (role) {
            Role.USER -> MessageRole.USER
            Role.MODEL -> MessageRole.ASSISTANT
            Role.SYSTEM -> MessageRole.SYSTEM
            Role.TOOL -> MessageRole.TOOL
        },
        parts = parts,
    )
}

private fun buildToolCallId(
    requestId: String,
    index: Int,
    toolCall: ToolCall,
): String {
    val fingerprint = buildString {
        append(toolCall.name)
        append(':')
        append(toolCall.arguments.toJsonElement())
    }.hashCode().toUInt().toString(16)
    return "local-$requestId-$index-$fingerprint"
}

private fun JsonElement.toLiteRtValue(): Any? {
    return when (this) {
        is JsonObject -> entries.associate { (key, value) -> key to value.toLiteRtValue() }
        is JsonArray -> map { it.toLiteRtValue() }
        is JsonPrimitive -> when {
            isString -> contentOrNull
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> contentOrNull
        }
    }
}

private fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        null -> JsonPrimitive(null as String?)
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Map<*, *> -> buildJsonObject {
            entries.forEach { (key, value) ->
                if (key is String) {
                    put(key, value.toJsonElement())
                }
            }
        }

        is Iterable<*> -> buildJsonArray {
            for (item in this@toJsonElement) {
                add(item.toJsonElement())
            }
        }

        else -> JsonPrimitive(toString())
    }
}

private fun readAttachmentBytes(
    context: Context,
    value: String,
): ByteArray? {
    if (value.isBlank()) return null
    if (value.startsWith("data:", ignoreCase = true)) {
        val base64 = value.substringAfter(',', "")
        if (base64.isBlank()) return null
        return runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
    }

    val uri = runCatching { Uri.parse(value) }.getOrNull()
    if (uri != null) {
        when (uri.scheme?.lowercase()) {
            null, "" -> {
                val file = File(value)
                if (file.exists()) {
                    return runCatching { file.readBytes() }.getOrNull()
                }
            }

            "file" -> {
                val file = uri.path?.let(::File)
                if (file?.exists() == true) {
                    return runCatching { file.readBytes() }.getOrNull()
                }
            }

            else -> {
                return runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes()
                    }
                }.getOrNull()
            }
        }
    }

    val fallback = File(value)
    return if (fallback.exists()) {
        runCatching { fallback.readBytes() }.getOrNull()
    } else {
        null
    }
}

private fun AutoCloseable.closeQuietly() {
    runCatching { close() }
        .onFailure { throwable -> Log.w(TAG, "Failed to close LiteRT resource", throwable) }
}
