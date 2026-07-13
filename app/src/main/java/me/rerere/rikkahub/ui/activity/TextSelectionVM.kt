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
import me.rerere.rikkahub.data.datastore.findTextSelectionAction
import me.rerere.rikkahub.data.datastore.getTextSelectionActionModel
import me.rerere.rikkahub.data.datastore.resolveTextSelectionAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.defaultChatInputTransformers
import me.rerere.rikkahub.service.defaultChatOutputTransformers

private const val TAG = "TextSelectionVM"

/**
 * Quick action types for text selection
 */
enum class QuickAction {
    TRANSLATE,
    EXPLAIN,
    SUMMARIZE,
    CUSTOM
}

/**
 * UI State for text selection feature
 */
sealed interface TextSelectionState {
    data object ActionSelection : TextSelectionState
    data object CustomPrompt : TextSelectionState
    data object Loading : TextSelectionState
    data class Result(
        val responseText: String,
        val isStreaming: Boolean = true,
        val isReasoning: Boolean = false
    ) : TextSelectionState
    data class Error(val message: String) : TextSelectionState
}

class TextSelectionVM(
    private val settingsStore: SettingsStore,
    private val generationHandler: GenerationHandler,
    private val memoryRepository: MemoryRepository,
    private val templateTransformer: TemplateTransformer,
) : ViewModel() {

    internal var inputData by mutableStateOf(QuickAskInputData())
        private set

    var state by mutableStateOf<TextSelectionState>(TextSelectionState.ActionSelection)
        private set

    var customPrompt by mutableStateOf("")
        private set

    var lastAction by mutableStateOf<QuickAction?>(null)
        private set

    var lastAssistantId by mutableStateOf<String?>(null)
        private set

    val selectedText: String
        get() = inputData.text

    private var currentJob: Job? = null
    private var generatedMessages = emptyList<UIMessage>()

    fun updateSelectedText(text: String) {
        updateInput(QuickAskInputData(text = text))
    }

    internal fun updateInput(input: QuickAskInputData) {
        currentJob?.cancel()
        inputData = input
        state = TextSelectionState.ActionSelection
        customPrompt = ""
        lastAction = null
        lastAssistantId = null
        generatedMessages = emptyList()
    }

    fun onActionSelected(action: QuickAction, customPromptText: String = "") {
        when (action) {
            QuickAction.CUSTOM -> {
                customPrompt = customPromptText
                state = TextSelectionState.CustomPrompt
            }

            else -> executeAction(action, customPromptText)
        }
    }

    fun updateCustomPrompt(prompt: String) {
        customPrompt = prompt
    }

    fun submitCustomPrompt() {
        if (customPrompt.isNotBlank()) {
            executeAction(QuickAction.CUSTOM, customPrompt)
        }
    }

    fun backToActionSelection() {
        currentJob?.cancel()
        state = TextSelectionState.ActionSelection
        customPrompt = ""
        generatedMessages = emptyList()
    }

    fun cancelGeneration() {
        currentJob?.cancel()
        val currentState = state
        if (currentState is TextSelectionState.Result) {
            state = currentState.copy(isStreaming = false)
        }
    }

    internal fun buildContinuationData(): QuickAskContinuationData? {
        val currentState = state as? TextSelectionState.Result ?: return null
        return QuickAskContinuationData(
            text = inputData.text,
            attachments = inputData.attachments,
            aiResponse = currentState.responseText.takeIf { it.isNotBlank() },
            userPrompt = customPrompt.takeIf {
                lastAction == QuickAction.CUSTOM && it.isNotBlank()
            },
            assistantId = lastAssistantId
        )
    }

    private fun executeAction(action: QuickAction, customPrompt: String = "") {
        currentJob?.cancel()
        state = TextSelectionState.Loading
        lastAction = action
        generatedMessages = emptyList()

        currentJob = viewModelScope.launch {
            try {
                val settings = settingsStore.settingsFlow.value
                val assistant = resolveAssistant(settings)
                val actionConfig = settings.findTextSelectionAction(action.actionId)
                val model = settings.getTextSelectionActionModel(
                    actionId = action.actionId,
                    assistant = assistant,
                )
                if (model == null) {
                    state = TextSelectionState.Error(
                        "No model is available for this Ask LastChat action. Please choose one in Android Integration settings."
                    )
                    return@launch
                }

                lastAssistantId = assistant.id.toString()
                val actionPrompt = buildQuickAskAssistantSystemPrompt(
                    action = action,
                    customPrompt = customPrompt,
                    assistantPrompt = assistant.systemPrompt,
                    translateLanguage = settings.textSelectionConfig.translateLanguage,
                    userDefinedPrompt = actionConfig?.prompt.orEmpty()
                )
                val generationAssistant = assistant.copy(systemPrompt = actionPrompt)
                val userParts = buildQuickAskMessageParts(
                    text = inputData.text,
                    attachments = inputData.attachments,
                )
                if (userParts.isEmpty()) {
                    state = TextSelectionState.Error("Nothing to ask about.")
                    return@launch
                }

                val memories = resolveMemories(
                    settings = settings,
                    assistant = assistant,
                    queryText = buildQuickAskTextContent(
                        text = inputData.text,
                        customPrompt = customPrompt.takeIf { action == QuickAction.CUSTOM }
                    )
                )

                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = listOf(
                        UIMessage(
                            role = MessageRole.USER,
                            parts = userParts
                        )
                    ),
                    inputTransformers = buildList {
                        addAll(defaultChatInputTransformers)
                        add(templateTransformer)
                    },
                    outputTransformers = defaultChatOutputTransformers,
                    assistant = generationAssistant,
                    memories = memories,
                ).catch { error ->
                    Log.e(TAG, "Stream error", error)
                    state = TextSelectionState.Error(error.message ?: "Unknown error")
                }.collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages -> handleGeneratedMessages(chunk.messages)
                    }
                }

                val currentState = state
                if (currentState is TextSelectionState.Result) {
                    state = currentState.copy(isStreaming = false)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Error executing action", error)
                state = TextSelectionState.Error(error.message ?: "Unknown error")
            }
        }
    }

    private suspend fun resolveMemories(
        settings: Settings,
        assistant: Assistant,
        queryText: String,
    ): List<AssistantMemory> {
        if (!assistant.enableMemory) {
            return emptyList()
        }
        if (!assistant.useRagMemoryRetrieval) {
            return memoryRepository.getMemoriesOfAssistant(assistant.id.toString()).take(50)
        }
        if (queryText.isBlank()) {
            return memoryRepository.getMemoriesOfAssistant(assistant.id.toString()).take(50)
        }
        val results = memoryRepository.retrieveRelevantMemories(
            assistantId = assistant.id.toString(),
            query = queryText,
            limit = 50,
            similarityThreshold = assistant.ragSimilarityThreshold,
            includeCore = assistant.ragIncludeCore,
            includeEpisodes = assistant.ragIncludeEpisodes
        )
        if (settings.enableRagLogging) {
            Log.d(TAG, "Resolved ${results.size} quick ask memories for ${assistant.id}")
        }
        return results
    }

    private fun handleGeneratedMessages(messages: List<UIMessage>) {
        generatedMessages = messages
        val lastAssistantMessage = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        val responseText = lastAssistantMessage?.toContentText() ?: ""
        val isReasoning = lastAssistantMessage?.parts?.any {
            it is UIMessagePart.Reasoning && it.finishedAt == null
        } ?: false

        state = TextSelectionState.Result(
            responseText = responseText,
            isStreaming = true,
            isReasoning = isReasoning
        )
    }

    private fun resolveAssistant(settings: Settings): Assistant {
        return settings.resolveTextSelectionAssistant()
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}

private val QuickAction.actionId: String
    get() = when (this) {
        QuickAction.TRANSLATE -> "translate"
        QuickAction.EXPLAIN -> "explain"
        QuickAction.SUMMARIZE -> "summarize"
        QuickAction.CUSTOM -> "custom"
    }

internal fun buildQuickAskAssistantSystemPrompt(
    action: QuickAction,
    customPrompt: String,
    assistantPrompt: String,
    translateLanguage: String,
    userDefinedPrompt: String,
): String {
    val processedPrompt = when (action) {
        QuickAction.TRANSLATE -> userDefinedPrompt.replace("{{language}}", translateLanguage)
        QuickAction.CUSTOM -> userDefinedPrompt.replace("{{custom_prompt}}", customPrompt)
        else -> userDefinedPrompt
    }

    if (action == QuickAction.TRANSLATE) {
        return processedPrompt.ifBlank {
            """
                You are a translator. Translate the user's text to $translateLanguage.
                Only output the translation, nothing else. Do not include any explanations or notes.
            """.trimIndent()
        }
    }

    val actionPrompt = processedPrompt.ifBlank {
        when (action) {
            QuickAction.EXPLAIN -> """
                Explain the following text in simple, easy-to-understand terms.
                Be concise but thorough. Use examples if helpful.
            """.trimIndent()

            QuickAction.SUMMARIZE -> """
                Provide a clear, concise summary of the following text.
                Capture the key points and main ideas. Be brief but complete.
            """.trimIndent()

            QuickAction.CUSTOM -> """
                Answer the user's question about the provided text.
                User's question: $customPrompt
            """.trimIndent()

            QuickAction.TRANSLATE -> ""
        }
    }

    return if (assistantPrompt.isNotBlank()) {
        """
            $assistantPrompt

            $actionPrompt
        """.trimIndent()
    } else {
        actionPrompt
    }
}
