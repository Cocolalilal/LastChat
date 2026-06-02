package me.rerere.ai.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.LocalModelAccelerator
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class LocalProvider : Provider<ProviderSetting.Local> {
    private val sessions = ConcurrentHashMap<String, LiteRtLmSession>()

    override suspend fun listModels(providerSetting: ProviderSetting.Local): List<Model> {
        return providerSetting.models
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.Local,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val session = sessionFor(params.model, params)
        val response = session.send(localPrompt(messages))
        response.toMessageChunk(params.model, complete = true)
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.Local,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        val session = sessionFor(params.model, params)
        session.stream(localPrompt(messages)).collect { delta ->
            emit(delta.toMessageChunk(params.model, complete = false))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult {
        error("Local LiteRT-LM models do not generate image output")
    }

    private fun sessionFor(model: Model, params: TextGenerationParams): LiteRtLmSession {
        val modelPath = model.localModelPath?.takeIf { it.isNotBlank() }
            ?: error("Local model ${model.displayName.ifBlank { model.modelId }} is not downloaded")
        val key = buildString {
            append(model.id)
            append('|')
            append(modelPath)
            append('|')
            append(resolveAccelerator(model).name)
            append('|')
            append(params.topK ?: model.localSamplerDefaults?.topK)
            append('|')
            append(params.topP ?: model.localSamplerDefaults?.topP)
            append('|')
            append(params.temperature ?: model.localSamplerDefaults?.temperature)
            append('|')
            append(params.maxTokens ?: model.localSamplerDefaults?.maxTokens)
        }
        return sessions.getOrPut(key) {
            LiteRtLmSession(
                modelPath = modelPath,
                accelerator = resolveAccelerator(model),
                topK = params.topK ?: model.localSamplerDefaults?.topK ?: 64,
                topP = params.topP ?: model.localSamplerDefaults?.topP ?: 0.95f,
                temperature = params.temperature ?: model.localSamplerDefaults?.temperature ?: 1.0f,
            ).also { it.initialize() }
        }
    }

    private fun resolveAccelerator(model: Model): LocalModelAccelerator {
        val requested = model.localAccelerator ?: LocalModelAccelerator.AUTO
        if (requested != LocalModelAccelerator.AUTO) return requested
        return model.localSamplerDefaults?.accelerators?.firstOrNull()
            ?: LocalModelAccelerator.GPU
    }

    private fun localPrompt(messages: List<UIMessage>): String {
        return messages.joinToString("\n\n") { message ->
            val role = when (message.role) {
                MessageRole.SYSTEM -> "System"
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.TOOL -> "Tool"
            }
            val content = message.parts.joinToString("\n") { part ->
                when (part) {
                    is UIMessagePart.Text -> part.text
                    is UIMessagePart.Image -> "[Image: ${part.url}]"
                    is UIMessagePart.Audio -> "[Audio: ${part.url}]"
                    is UIMessagePart.Document -> "[Document: ${part.fileName}]"
                    is UIMessagePart.ToolCall -> "[Tool call ${part.toolName}: ${part.arguments}]"
                    is UIMessagePart.ToolResult -> "[Tool result ${part.toolName}: ${part.content}]"
                    else -> ""
                }
            }.trim()
            "$role:\n$content"
        }
    }
}

private class LiteRtLmSession(
    private val modelPath: String,
    private val accelerator: LocalModelAccelerator,
    private val topK: Int,
    private val topP: Float,
    private val temperature: Float,
) : Closeable {
    private var engine: Any? = null
    private var conversation: Any? = null

    fun initialize() {
        val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
        val engineConfig = createEngineConfig(modelPath, accelerator)
        engine = engineClass.getConstructor(engineConfig.javaClass).newInstance(engineConfig)
        engineClass.methods.first { it.name == "initialize" && it.parameterCount == 0 }.invoke(engine)
        conversation = createConversation(engine!!)
    }

    fun send(prompt: String): String {
        val conv = conversation ?: error("LiteRT-LM conversation was not initialized")
        val method = conv.javaClass.methods.firstOrNull {
            it.name == "sendMessage" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
        } ?: error("LiteRT-LM sendMessage(String) API was not found")
        return method.invoke(conv, prompt)?.toString().orEmpty()
    }

    fun stream(prompt: String): Flow<String> = flow {
        val conv = conversation ?: error("LiteRT-LM conversation was not initialized")
        val asyncMethod = conv.javaClass.methods.firstOrNull {
            it.name == "sendMessageAsync" &&
                it.parameterCount == 1 &&
                it.returnType.name == "kotlinx.coroutines.flow.Flow"
        }
        val asyncResult = asyncMethod?.invoke(conv, prompt)
        if (asyncResult is Flow<*>) {
            asyncResult.collect { message ->
                emit(message?.toString().orEmpty())
            }
        } else {
            emit(send(prompt))
        }
    }

    override fun close() {
        (conversation as? AutoCloseable)?.close()
        (engine as? AutoCloseable)?.close()
        (conversation as? Closeable)?.close()
        (engine as? Closeable)?.close()
        conversation = null
        engine = null
    }

    private fun createEngineConfig(
        modelPath: String,
        accelerator: LocalModelAccelerator,
    ): Any {
        val configClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
        val backend = createBackend(accelerator)

        configClass.constructors.firstOrNull { constructor ->
            constructor.parameterTypes.size >= 1 &&
                constructor.parameterTypes[0] == String::class.java
        }?.let { constructor ->
            val args = Array<Any?>(constructor.parameterCount) { null }
            args[0] = modelPath
            if (constructor.parameterCount > 1 && backend != null) args[1] = backend
            return constructor.newInstance(*args)
        }

        val builder = configClass.methods.firstOrNull { it.name == "builder" && it.parameterCount == 0 }
            ?.invoke(null)
            ?: error("LiteRT-LM EngineConfig constructor/builder was not found")
        builder.javaClass.methods.firstOrNull { it.name == "setModelPath" && it.parameterCount == 1 }
            ?.invoke(builder, modelPath)
        if (backend != null) {
            builder.javaClass.methods.firstOrNull { it.name == "setBackend" && it.parameterCount == 1 }
                ?.invoke(builder, backend)
        }
        return builder.javaClass.methods.first { it.name == "build" && it.parameterCount == 0 }.invoke(builder)
            ?: error("LiteRT-LM EngineConfig builder returned null")
    }

    private fun createConversation(engine: Any): Any {
        val sampler = createSamplerConfig()
        val conversationConfig = createConversationConfig(sampler)
        return engine.javaClass.methods.firstOrNull {
            it.name == "createConversation" && it.parameterCount == 1
        }?.invoke(engine, conversationConfig)
            ?: engine.javaClass.methods.first { it.name == "createConversation" && it.parameterCount == 0 }.invoke(engine)
    }

    private fun createSamplerConfig(): Any? = runCatching {
        val samplerClass = Class.forName("com.google.ai.edge.litertlm.SamplerConfig")
        samplerClass.constructors.firstOrNull()?.let { constructor ->
            val args = Array<Any?>(constructor.parameterCount) { index ->
                when (constructor.parameterTypes[index]) {
                    Int::class.javaPrimitiveType, Int::class.javaObjectType -> topK
                    Float::class.javaPrimitiveType, Float::class.javaObjectType -> if (index == 0) temperature else topP
                    Double::class.javaPrimitiveType, Double::class.javaObjectType -> if (index == 0) temperature.toDouble() else topP.toDouble()
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
                    else -> null
                }
            }
            return@runCatching constructor.newInstance(*args)
        }
        null
    }.getOrNull()

    private fun createConversationConfig(sampler: Any?): Any? = runCatching {
        val configClass = Class.forName("com.google.ai.edge.litertlm.ConversationConfig")
        configClass.constructors.firstOrNull()?.let { constructor ->
            val args = Array<Any?>(constructor.parameterCount) { index ->
                val type = constructor.parameterTypes[index]
                if (sampler != null && type.isAssignableFrom(sampler.javaClass)) sampler else null
            }
            return@runCatching constructor.newInstance(*args)
        }
        null
    }.getOrNull()

    private fun createBackend(accelerator: LocalModelAccelerator): Any? = runCatching {
        val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
        val nestedName = when (accelerator) {
            LocalModelAccelerator.CPU -> "CPU"
            LocalModelAccelerator.GPU -> "GPU"
            LocalModelAccelerator.NPU, LocalModelAccelerator.TPU -> "NPU"
            LocalModelAccelerator.AUTO -> "GPU"
        }
        val nested = backendClass.declaredClasses.firstOrNull { it.simpleName == nestedName }
        nested?.constructors?.firstOrNull()?.let { constructor ->
            val args = Array<Any?>(constructor.parameterCount) { null }
            constructor.newInstance(*args)
        }
    }.getOrNull()
}

private fun String.toMessageChunk(model: Model, complete: Boolean): MessageChunk {
    val parts = toLocalMessageParts()
    return MessageChunk(
        id = "local-${System.nanoTime()}",
        model = model.modelId,
        choices = listOf(
            UIMessageChoice(
                index = 0,
                delta = if (complete) null else UIMessage(role = MessageRole.ASSISTANT, parts = parts),
                message = if (complete) UIMessage(role = MessageRole.ASSISTANT, parts = parts) else null,
                finishReason = if (complete) "stop" else null,
            )
        ),
    )
}

private fun String.toLocalMessageParts(): List<UIMessagePart> {
    val match = Regex("<think(?:ing)?>([\\s\\S]*?)(?:</think(?:ing)?>|$)", RegexOption.IGNORE_CASE)
        .find(this)
        ?: return listOf(UIMessagePart.Text(this))
    val reasoning = match.groupValues.getOrNull(1).orEmpty().trim()
    val text = replace(match.value, "").trim()
    return buildList {
        if (reasoning.isNotBlank()) {
            add(UIMessagePart.Reasoning(reasoning = reasoning))
        }
        if (text.isNotBlank()) {
            add(UIMessagePart.Text(text))
        }
    }.ifEmpty {
        listOf(UIMessagePart.Text(this@toLocalMessageParts))
    }
}
