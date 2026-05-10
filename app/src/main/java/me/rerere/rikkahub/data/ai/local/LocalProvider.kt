package me.rerere.rikkahub.data.ai.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.uuid.Uuid

class LocalProvider(
    private val repository: LocalModelRepository,
    private val runtimeEngine: LocalRuntimeEngine,
    private val compatibilityEstimator: LocalCompatibilityEstimator,
) : Provider<ProviderSetting.Local> {
    override suspend fun listModels(providerSetting: ProviderSetting.Local): List<Model> {
        return repository.getReadyModels()
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.Local,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        return try {
            val install = repository.getReadyInstallForModel(params.model)
                ?: error("Local model is not installed.")
            val entry = install.toCatalogEntryOrNull()
                ?: error("Local model metadata is missing.")
            val compatibility = compatibilityEstimator.estimate(entry)
            check(compatibility.canRunInference) {
                compatibility.reasons.joinToString("\n")
            }
            runtimeEngine.load(install)
            runtimeEngine.warmup(install)
            val result = runtimeEngine.generate(
                install = install,
                messages = messages,
                params = params,
            )
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            localErrorChunk(params.model.modelId, throwable)
        }
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.Local,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        return flow {
            try {
                emit(localActivityChunk(params.model.modelId, title = "Checking device", detail = "Checking fit."))
                val install = repository.getReadyInstallForModel(params.model)
                    ?: error("Local model is not installed.")
                val entry = install.toCatalogEntryOrNull()
                    ?: error("Local model metadata is missing.")
                val compatibility = compatibilityEstimator.estimate(entry)
                check(compatibility.canRunInference) {
                    compatibility.reasons.joinToString("\n")
                }
                val runtimeName = "LiteRT"
                emit(localActivityChunk(params.model.modelId, title = "Loading model", detail = "${entry.displayName} · $runtimeName"))
                runtimeEngine.load(install)
                emit(localActivityChunk(params.model.modelId, title = "Warming runtime", detail = "Preparing the local session before generation starts."))
                runtimeEngine.warmup(install)
                emit(localActivityChunk(params.model.modelId, title = "Generating", detail = "Running locally."))
                var emittedOutput = false
                runtimeEngine.stream(
                    install = install,
                    messages = messages,
                    params = params,
                ).collect { chunk ->
                    emittedOutput = emittedOutput || chunk.hasVisibleAssistantOutput()
                    emit(chunk)
                }
                if (!emittedOutput) {
                    emit(
                        localErrorChunk(
                            modelId = params.model.modelId,
                            throwable = LocalRuntimeUnavailableException("The local runtime finished without producing any text.")
                        )
                    )
                }

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                emit(localErrorChunk(params.model.modelId, throwable))
            }
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult {
        error("Local image generation is not supported.")
    }

    override suspend fun createEmbedding(
        providerSetting: ProviderSetting.Local,
        input: List<String>,
        model: Model,
    ): List<List<Float>> {
        val install = repository.getReadyInstallForModel(model)
            ?: error("Local embedding model is not installed.")
        val entry = install.toCatalogEntryOrNull()
            ?: error("Local embedding model metadata is missing.")
        val compatibility = compatibilityEstimator.estimate(entry)
        check(compatibility.canRunInference) {
            compatibility.reasons.joinToString("\n")
        }
        return runtimeEngine.createEmbeddings(install, input)
    }

    private fun localActivityChunk(
        modelId: String,
        title: String,
        detail: String,
    ): MessageChunk {
        val requestId = Uuid.random().toString()
        return MessageChunk(
            id = requestId,
            model = modelId,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Reasoning(
                                reasoning = "$detail\n",
                                createdAt = Clock.System.now(),
                                finishedAt = null,
                                metadata = buildJsonObject {
                                    put("local_activity", true)
                                    put("title", title)
                                },
                            )
                        ),
                    ),
                    message = null,
                    finishReason = null,
                )
            ),
        )
    }

    private fun localErrorChunk(
        modelId: String,
        throwable: Throwable,
    ): MessageChunk {
        val requestId = Uuid.random().toString()
        return MessageChunk(
            id = requestId,
            model = modelId,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Text(
                                text = "Local run failed.\n\n${throwable.toLocalUserMessage()}"
                            )
                        ),
                    ),
                    message = null,
                    finishReason = "error",
                )
            ),
        )
    }

    private fun Throwable.toLocalUserMessage(): String {
        return when (this) {
            is LocalRuntimeBusyException -> "Another on-device generation is already running. Wait for it to finish or cancel it, then try again."
            is LocalRuntimeUnavailableException -> message?.takeIf { it.isNotBlank() }
                ?: "The local runtime could not open the installed model package."
            is IllegalStateException,
            is IllegalArgumentException -> message?.takeIf { it.isNotBlank() }
                ?: "The selected local model is not ready for inference."
            else -> message?.takeIf { it.isNotBlank() }
                ?: "The local runtime stopped before producing any text."
        }
    }

    private fun MessageChunk.hasVisibleAssistantOutput(): Boolean {
        val message = choices.firstOrNull()?.delta ?: choices.firstOrNull()?.message ?: return false
        return message.parts.any { part ->
            when (part) {
                is UIMessagePart.Text -> part.text.isNotBlank()
                is UIMessagePart.ToolCall -> part.toolName.isNotBlank() || part.arguments.isNotBlank()
                else -> false
            }
        }
    }
}
