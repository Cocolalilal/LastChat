package me.rerere.rikkahub.data.ai.local

import com.llamatik.library.platform.LlamaBridge
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

private const val DEFAULT_TOP_K = 40
private const val DEFAULT_TOP_P = 0.95f
private const val DEFAULT_TEMPERATURE = 0.8f
private const val DEFAULT_MAX_TOKENS = 768

class LlamaCppRuntimeEngine : LocalRuntimeEngine {
    private val activeGeneration = AtomicBoolean(false)
    private val bridgeMutex = Mutex()

    @Volatile
    private var loadedPath: String? = null

    @Volatile
    private var loadedMode: LoadedMode? = null

    @Volatile
    private var lastStats: LocalRuntimeStats? = null

    override suspend fun load(install: LocalModelInstallEntity) {
        val entry = install.toCatalogEntryOrNull()
            ?: throw LocalRuntimeUnavailableException("Missing GGUF model metadata.")
        val path = install.resolvePrimaryGgufPath()
        bridgeMutex.withLock {
            if (loadedPath == path && loadedMode == LoadedMode.GENERATION) return
            try {
                withContext(Dispatchers.IO) { 
                    LlamaBridge.shutdown()
                    val ok = LlamaBridge.initGenerateModel(path) 
                    if (!ok) {
                        loadedPath = null
                        loadedMode = null
                        throw LocalRuntimeUnavailableException("llama.cpp could not open ${entry.displayName}.")
                    }
                }
                loadedPath = path
                loadedMode = LoadedMode.GENERATION
            } catch (t: Throwable) {
                loadedPath = null
                loadedMode = null
                throw LocalRuntimeUnavailableException("llama.cpp crashed during load: ${t.message}")
            }
        }
    }

    override suspend fun warmup(install: LocalModelInstallEntity) = Unit

    override suspend fun generate(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        if (!activeGeneration.compareAndSet(false, true)) {
            throw LocalRuntimeBusyException()
        }
        val startedAt = System.currentTimeMillis()
        try {
            load(install)
            updateParams(params)
            val prompt = messages.toLlamaPrompt()
            val text = try {
                LlamaBridge.generateWithContext(
                    systemPrompt = prompt.systemPrompt,
                    contextBlock = prompt.contextBlock,
                    userPrompt = prompt.userPrompt,
                )
            } catch (t: Throwable) {
                throw LocalRuntimeUnavailableException("llama.cpp generation crashed: ${t.message}")
            }
            if (text.isBlank()) {
                throw LocalRuntimeUnavailableException("llama.cpp finished without producing text.")
            }
            text.toTextChunk(
                requestId = Uuid.random().toString(),
                modelId = params.model.modelId,
                asDelta = false,
            )
        } catch (t: Throwable) {
            if (t !is LocalRuntimeUnavailableException && t !is kotlinx.coroutines.CancellationException) {
                throw LocalRuntimeUnavailableException("llama.cpp generation error: ${t.message}")
            }
            throw t
        } finally {
            lastStats = LocalRuntimeStats(durationMs = System.currentTimeMillis() - startedAt)
            activeGeneration.set(false)
        }
    }

    override fun stream(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = callbackFlow {
        if (!activeGeneration.compareAndSet(false, true)) {
            close(LocalRuntimeBusyException())
            return@callbackFlow
        }

        val startedAt = System.currentTimeMillis()
        val requestId = Uuid.random().toString()
        val prompt = messages.toLlamaPrompt()
        // Use the callbackFlow's own scope so exceptions propagate correctly
        // instead of crashing the process in a detached CoroutineScope.
        val job = launch(Dispatchers.IO) {
            try {
                load(install)
                updateParams(params)
                LlamaBridge.generateWithContextStream(
                    system = prompt.systemPrompt,
                    context = prompt.contextBlock,
                    user = prompt.userPrompt,
                    onDelta = { delta ->
                        if (delta.isNotEmpty()) {
                            trySend(delta.toTextChunk(requestId, params.model.modelId, asDelta = true))
                        }
                    },
                    onDone = {
                        lastStats = LocalRuntimeStats(durationMs = System.currentTimeMillis() - startedAt)
                        activeGeneration.set(false)
                        close()
                    },
                    onError = { message ->
                        lastStats = LocalRuntimeStats(durationMs = System.currentTimeMillis() - startedAt)
                        activeGeneration.set(false)
                        close(LocalRuntimeUnavailableException(message))
                    },
                )
            } catch (throwable: Throwable) {
                lastStats = LocalRuntimeStats(durationMs = System.currentTimeMillis() - startedAt)
                activeGeneration.set(false)
                close(throwable)
            }
        }

        awaitClose {
            try {
                LlamaBridge.nativeCancelGenerate()
            } catch (t: Throwable) {
                // Ignore
            }
            job.cancel()
            activeGeneration.set(false)
        }
    }

    override suspend fun createEmbeddings(
        install: LocalModelInstallEntity,
        input: List<String>,
    ): List<List<Float>> = withContext(Dispatchers.IO) {
        // The native LlamaBridge.embed() implementation is currently unstable and causes
        // hard native segfaults on many devices. Until the upstream library is fixed,
        // we are disabling GGUF embeddings to prevent the app from force-closing.
        throw LocalRuntimeUnavailableException(
            "GGUF embeddings are temporarily disabled due to native stability issues. " +
            "Please use a cloud provider for RAG memory embeddings."
        )
    }

    override suspend fun cancel() {
        try {
            LlamaBridge.nativeCancelGenerate()
        } catch (t: Throwable) {
            // Ignore
        }
        activeGeneration.set(false)
    }

    override suspend fun unload() {
        bridgeMutex.withLock {
            try {
                LlamaBridge.shutdown()
            } catch (t: Throwable) {
                // Ignore crash on shutdown
            } finally {
                loadedPath = null
                loadedMode = null
            }
        }
        activeGeneration.set(false)
    }

    override suspend fun getStats(): LocalRuntimeStats? = lastStats

    private fun updateParams(params: TextGenerationParams) {
        LlamaBridge.updateGenerateParams(
            temperature = params.temperature ?: DEFAULT_TEMPERATURE,
            maxTokens = params.maxTokens ?: DEFAULT_MAX_TOKENS,
            topP = params.topP ?: DEFAULT_TOP_P,
            topK = DEFAULT_TOP_K,
            repeatPenalty = 1.1f,
        )
    }
}

class RoutingLocalRuntimeEngine(
    private val liteRt: LiteRtRuntimeEngine,
    private val llamaCpp: LlamaCppRuntimeEngine,
) : LocalRuntimeEngine {
    override suspend fun load(install: LocalModelInstallEntity) = route(install).load(install)

    override suspend fun warmup(install: LocalModelInstallEntity) = route(install).warmup(install)

    override suspend fun generate(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = route(install).generate(install, messages, params)

    override fun stream(
        install: LocalModelInstallEntity,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = route(install).stream(install, messages, params)

    override suspend fun createEmbeddings(
        install: LocalModelInstallEntity,
        input: List<String>,
    ): List<List<Float>> = route(install).createEmbeddings(install, input)

    override suspend fun cancel() {
        liteRt.cancel()
        llamaCpp.cancel()
    }

    override suspend fun unload() {
        liteRt.unload()
        llamaCpp.unload()
    }

    override suspend fun getStats(): LocalRuntimeStats? = liteRt.getStats() ?: llamaCpp.getStats()

    private fun route(install: LocalModelInstallEntity): LocalRuntimeEngine {
        val backend = runCatching { LocalRuntimeBackend.valueOf(install.runtimeBackend) }.getOrDefault(LocalRuntimeBackend.LITERT)
        return when (backend) {
            LocalRuntimeBackend.LITERT -> liteRt
            LocalRuntimeBackend.LLAMA_CPP -> llamaCpp
        }
    }
}

private enum class LoadedMode {
    GENERATION,
    EMBEDDING,
}

private data class LlamaPrompt(
    val systemPrompt: String,
    val contextBlock: String,
    val userPrompt: String,
)

private fun LocalModelInstallEntity.resolvePrimaryGgufPath(): String {
    val primary = runCatching { JsonInstant.decodeFromString<List<String>>(filePathsJson) }
        .getOrDefault(emptyList())
        .map(::File)
        .filter(File::exists)
        .sortedWith(
            compareBy<File> { !it.name.endsWith(".gguf", ignoreCase = true) }
                .thenBy { !it.name.contains(modelId, ignoreCase = true) }
                .thenBy { it.name.length }
        )
        .firstOrNull()
        ?: throw LocalRuntimeUnavailableException("GGUF model file is missing on disk.")
    return primary.absolutePath
}

private fun List<UIMessage>.toLlamaPrompt(): LlamaPrompt {
    val system = filter { it.role == MessageRole.SYSTEM }
        .joinToString("\n\n") { it.toPlainPromptText() }
        .ifBlank { "You are a helpful assistant." }
    val nonSystem = filter { it.role != MessageRole.SYSTEM }
    val lastUserIndex = nonSystem.indexOfLast { it.role == MessageRole.USER }
    require(lastUserIndex >= 0) { "Local GGUF inference requires a user message." }
    val history = nonSystem.take(lastUserIndex).joinToString("\n\n") { message ->
        "${message.role.name.lowercase()}: ${message.toPlainPromptText()}"
    }
    return LlamaPrompt(
        systemPrompt = system,
        contextBlock = history,
        userPrompt = nonSystem[lastUserIndex].toPlainPromptText(),
    )
}

private fun UIMessage.toPlainPromptText(): String {
    return parts.joinToString("\n") { part ->
        when (part) {
            is UIMessagePart.Text -> part.text
            is UIMessagePart.ToolCall -> "Tool call: ${part.toolName}(${part.arguments})"
            is UIMessagePart.ToolResult -> "Tool result from ${part.toolName}: ${part.content}"
            is UIMessagePart.Reasoning -> part.reasoning
            is UIMessagePart.Image -> "[Image attachment omitted: this GGUF runtime is text-only without a vision projector.]"
            is UIMessagePart.Audio -> "[Audio attachment omitted: this GGUF runtime is text-only.]"
            is UIMessagePart.Document -> "Attached document: ${part.fileName}"
            else -> ""
        }
    }.ifBlank { "" }
}

private fun String.toTextChunk(
    requestId: String,
    modelId: String,
    asDelta: Boolean,
): MessageChunk {
    val uiMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(this)),
    )
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
