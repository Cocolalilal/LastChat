package me.rerere.rikkahub.ui.activity

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getChatModelForAssistant
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.defaultChatInputTransformers
import me.rerere.rikkahub.service.defaultChatOutputTransformers

private const val TAG = "AssistantOverlayVM"

/**
 * Drives a single assistant-overlay turn. Mirrors [TextSelectionVM]'s "one-shot
 * generation outside the main chat" pattern, but for a free-form chat message with an
 * optional screenshot attachment.
 *
 * STT/TTS are owned by the overlay Compose layer (rememberCustomSttState /
 * rememberCustomTtsState) — this VM only handles model generation and hand-off.
 */
class AssistantOverlayVM(
    private val settingsStore: SettingsStore,
    private val generationHandler: GenerationHandler,
    private val memoryRepository: MemoryRepository,
    private val templateTransformer: TemplateTransformer,
) : ViewModel() {

    sealed interface OverlayState {
        data object Idle : OverlayState
        data object Generating : OverlayState
        data class Result(
            val responseText: String,
            val isStreaming: Boolean = true,
            val isReasoning: Boolean = false,
        ) : OverlayState

        data class Error(val message: String) : OverlayState
    }

    var state by mutableStateOf<OverlayState>(OverlayState.Idle)
        private set

    /** Name of the assistant serving the overlay, for the input hint. */
    var assistantName by mutableStateOf(
        settingsStore.settingsFlow.value.resolveAssistantOverlayAssistant().name
    )
        private set

    private var currentJob: Job? = null
    private var lastUserText: String = ""
    private var lastAssistantId: String? = null

    fun send(userText: String, screenshotDataUrl: String?) {
        val text = userText.trim()
        if (text.isBlank() && screenshotDataUrl == null) return
        currentJob?.cancel()
        state = OverlayState.Generating
        lastUserText = text

        currentJob = viewModelScope.launch {
            try {
                val settings = settingsStore.settingsFlow.value
                val assistant = settings.resolveAssistantOverlayAssistant()
                lastAssistantId = assistant.id.toString()
                assistantName = assistant.name

                val model = settings.getChatModelForAssistant(assistant)
                if (model == null) {
                    state = OverlayState.Error("No chat model is configured for this assistant.")
                    return@launch
                }

                val parts = buildList {
                    if (text.isNotBlank()) add(UIMessagePart.Text(text))
                    if (screenshotDataUrl != null) add(UIMessagePart.Image(url = screenshotDataUrl))
                }
                if (parts.isEmpty()) {
                    state = OverlayState.Error("Nothing to ask.")
                    return@launch
                }

                val memories = resolveMemories(settings, assistant, text)

                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = listOf(UIMessage(role = MessageRole.USER, parts = parts)),
                    inputTransformers = buildList {
                        addAll(defaultChatInputTransformers)
                        add(templateTransformer)
                    },
                    outputTransformers = defaultChatOutputTransformers,
                    assistant = assistant,
                    memories = memories,
                ).catch { error ->
                    Log.e(TAG, "Stream error", error)
                    state = OverlayState.Error(error.message ?: "Unknown error")
                }.collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages -> handleGeneratedMessages(chunk.messages)
                    }
                }

                (state as? OverlayState.Result)?.let { state = it.copy(isStreaming = false) }
            } catch (error: Exception) {
                Log.e(TAG, "Error generating overlay reply", error)
                state = OverlayState.Error(error.message ?: "Unknown error")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        (state as? OverlayState.Result)?.let { state = it.copy(isStreaming = false) }
    }

    fun reset() {
        currentJob?.cancel()
        state = OverlayState.Idle
        lastUserText = ""
    }

    /** Payload for "Open in app": reopens this exchange as a real conversation. */
    internal fun buildContinuationData(): QuickAskContinuationData? {
        val result = state as? OverlayState.Result ?: return null
        if (lastUserText.isBlank() && result.responseText.isBlank()) return null
        return QuickAskContinuationData(
            text = lastUserText,
            aiResponse = result.responseText.takeIf { it.isNotBlank() },
            userPrompt = null,
            assistantId = lastAssistantId,
        )
    }

    private fun handleGeneratedMessages(messages: List<UIMessage>) {
        val lastAssistantMessage = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        val responseText = lastAssistantMessage?.toContentText() ?: ""
        val isReasoning = lastAssistantMessage?.parts?.any {
            it is UIMessagePart.Reasoning && it.finishedAt == null
        } ?: false
        state = OverlayState.Result(
            responseText = responseText,
            isStreaming = true,
            isReasoning = isReasoning,
        )
    }

    private suspend fun resolveMemories(
        settings: Settings,
        assistant: Assistant,
        queryText: String,
    ): List<AssistantMemory> {
        if (!assistant.enableMemory) return emptyList()
        if (!assistant.useRagMemoryRetrieval || queryText.isBlank()) {
            return memoryRepository.getMemoriesOfAssistant(assistant.id.toString()).take(50)
        }
        return memoryRepository.retrieveRelevantMemories(
            assistantId = assistant.id.toString(),
            query = queryText,
            limit = 50,
            similarityThreshold = assistant.ragSimilarityThreshold,
            includeCore = assistant.ragIncludeCore,
            includeEpisodes = assistant.ragIncludeEpisodes,
        )
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}
