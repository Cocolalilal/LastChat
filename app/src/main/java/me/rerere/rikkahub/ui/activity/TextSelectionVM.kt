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
import me.rerere.rikkahub.data.ai.QuickAskIsolationOptions
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getTextSelectionActionModel
import me.rerere.rikkahub.data.datastore.resolveTextSelectionAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.TextSelectionAction
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.defaultChatInputTransformers
import me.rerere.rikkahub.service.defaultChatOutputTransformers
import kotlin.uuid.Uuid

private const val TAG = "TextSelectionVM"

/**
 * UI State for Ask LastChat text selection / share quick-ask feature.
 */
sealed interface TextSelectionState {
    data object ActionSelection : TextSelectionState
    data class CustomPrompt(val action: TextSelectionAction) : TextSelectionState
    data class Loading(
        val action: TextSelectionAction,
        val actionName: String,
        val modelName: String,
        val assistantName: String,
    ) : TextSelectionState

    data class Result(
        val action: TextSelectionAction,
        val actionName: String,
        val modelName: String,
        val assistantName: String,
        val responseText: String,
        val reasoningText: String = "",
        val reasoningStartedAtMs: Long? = null,
        val reasoningFinishedAtMs: Long? = null,
        val reasoningDurationMs: Long? = null,
        val isStreaming: Boolean = true,
        val isReasoning: Boolean = false,
        val isThinkingExpanded: Boolean = false,
    ) : TextSelectionState

    data class Error(
        val message: String,
        val action: TextSelectionAction? = null,
    ) : TextSelectionState
}

class TextSelectionVM(
    private val settingsStore: SettingsStore,
    private val generationHandler: GenerationHandler,
    private val memoryRepository: MemoryRepository,
    private val templateTransformer: TemplateTransformer,
    private val chatService: ChatService,
) : ViewModel() {

    internal var inputData by mutableStateOf(QuickAskInputData())
        private set

    var state by mutableStateOf<TextSelectionState>(TextSelectionState.ActionSelection)
        private set

    var customPrompt by mutableStateOf("")
        private set

    var activeAction by mutableStateOf<TextSelectionAction?>(null)
        private set

    var lastCustomPrompt by mutableStateOf("")
        private set

    var lastAssistantId by mutableStateOf<String?>(null)
        private set

    val selectedText: String
        get() = inputData.text

    private var currentJob: Job? = null
    private var generatedMessages = emptyList<UIMessage>()
    private var activeActionName: String = ""
    private var activeModelName: String = ""
    private var activeAssistantName: String = ""
    private var reasoningStartWallMs: Long? = null
    private var isThinkingExpandedUserOverride: Boolean? = null

    fun updateSelectedText(text: String) {
        updateInput(QuickAskInputData(text = text))
    }

    internal fun updateInput(input: QuickAskInputData) {
        currentJob?.cancel()
        inputData = input
        state = TextSelectionState.ActionSelection
        customPrompt = ""
        lastCustomPrompt = ""
        activeAction = null
        lastAssistantId = null
        generatedMessages = emptyList()
        reasoningStartWallMs = null
        isThinkingExpandedUserOverride = null
    }

    fun onActionSelected(action: TextSelectionAction, customPromptText: String = "") {
        activeAction = action
        if (action.isCustomPrompt && customPromptText.isBlank()) {
            customPrompt = ""
            state = TextSelectionState.CustomPrompt(action)
        } else {
            executeAction(action, customPromptText)
        }
    }

    fun updateCustomPrompt(prompt: String) {
        customPrompt = prompt
    }

    fun submitCustomPrompt() {
        val action = activeAction ?: return
        if (customPrompt.isNotBlank()) {
            executeAction(action, customPrompt)
        }
    }

    fun regenerate() {
        val action = activeAction ?: return
        executeAction(action, lastCustomPrompt)
    }

    fun toggleThinkingExpanded() {
        val currentState = state as? TextSelectionState.Result ?: return
        val next = !currentState.isThinkingExpanded
        isThinkingExpandedUserOverride = next
        state = currentState.copy(isThinkingExpanded = next)
    }

    fun backToActionSelection() {
        currentJob?.cancel()
        state = TextSelectionState.ActionSelection
        customPrompt = ""
        lastCustomPrompt = ""
        generatedMessages = emptyList()
        reasoningStartWallMs = null
        isThinkingExpandedUserOverride = null
    }

    fun cancelGeneration() {
        currentJob?.cancel()
        val currentState = state
        if (currentState is TextSelectionState.Result) {
            state = currentState.copy(
                isStreaming = false,
                isReasoning = false,
            )
        } else if (currentState is TextSelectionState.Loading) {
            state = TextSelectionState.ActionSelection
        }
    }

    internal fun buildContinuationData(): QuickAskContinuationData {
        val currentState = state as? TextSelectionState.Result
        return QuickAskContinuationData(
            text = inputData.text,
            attachments = inputData.attachments,
            aiResponse = currentState?.responseText?.takeIf { it.isNotBlank() },
            reasoningText = currentState?.reasoningText?.takeIf { it.isNotBlank() },
            reasoningDurationMs = currentState?.reasoningDurationMs,
            userPrompt = lastCustomPrompt.takeIf {
                activeAction?.isCustomPrompt == true && it.isNotBlank()
            },
            assistantId = lastAssistantId,
            actionName = currentState?.actionName ?: activeAction?.name,
        )
    }

    suspend fun persistAndPrepareChatContinuation(): Pair<Uuid, Uuid>? {
        val currentState = state as? TextSelectionState.Result
        if (currentState?.isStreaming == true) {
            cancelGeneration()
        }
        val settings = settingsStore.settingsFlow.value
        val assistantId = lastAssistantId
            ?.let { raw -> runCatching { Uuid.parse(raw) }.getOrNull() }
            ?: settings.resolveTextSelectionAssistant().id

        val continuationData = buildContinuationData()
        val conversationId = Uuid.random()
        val conversation = buildQuickAskPersistedConversation(
            conversationId = conversationId,
            assistantId = assistantId,
            data = continuationData,
        ) ?: return null

        settingsStore.updateAssistant(assistantId)
        settingsStore.markAssistantUsed(assistantId)
        chatService.saveConversation(conversationId, conversation)
        return conversationId to assistantId
    }

    private fun executeAction(action: TextSelectionAction, customPromptText: String = "") {
        currentJob?.cancel()
        activeAction = action
        lastCustomPrompt = customPromptText
        generatedMessages = emptyList()
        reasoningStartWallMs = null
        isThinkingExpandedUserOverride = null

        val settings = settingsStore.settingsFlow.value
        val config = settings.textSelectionConfig
        val assistant = resolveAssistant(settings)
        val model = settings.getTextSelectionActionModel(
            action = action,
            assistant = assistant,
        )
        if (model == null) {
            state = TextSelectionState.Error(
                message = "No model is available for this Ask LastChat action. Please choose one in Android Integration settings.",
                action = action,
            )
            return
        }

        activeActionName = action.name
        activeModelName = model.displayName.ifBlank { model.modelId }
        activeAssistantName = assistant.name.ifBlank { "Assistant" }
        lastAssistantId = assistant.id.toString()

        state = TextSelectionState.Loading(
            action = action,
            actionName = activeActionName,
            modelName = activeModelName,
            assistantName = activeAssistantName,
        )

        currentJob = viewModelScope.launch {
            try {
                val effectiveMemoryRead = action.enableMemoryReadOverride ?: config.enableMemoryRead
                val effectiveTools = action.enableToolsOverride ?: config.enableTools
                val effectiveMemoryWrite = config.enableMemoryWrite && effectiveTools
                val effectiveExtraPrompts = config.enableExtraPromptInjections

                val actionPrompt = buildQuickAskPresetSystemPrompt(
                    action = action,
                    customPrompt = customPromptText,
                    assistantPrompt = assistant.systemPrompt,
                    translateLanguage = config.translateLanguage,
                    inheritAssistantSystemPrompt = config.inheritAssistantSystemPrompt,
                )

                val generationAssistant = assistant.copy(
                    systemPrompt = actionPrompt,
                    enableMemory = effectiveMemoryRead,
                    enableMemorySummary = effectiveMemoryRead && assistant.enableMemorySummary,
                    enableRecentChatsReference = false,
                    enableMemorySearchTool = effectiveMemoryRead && effectiveTools,
                    enableAutomaticSkillInvocation = effectiveTools,
                    enableTimeAwareness = effectiveExtraPrompts && assistant.enableTimeAwareness,
                    learningMode = effectiveExtraPrompts && assistant.learningMode,
                    localTools = if (effectiveTools) assistant.localTools else emptyList(),
                    mcpServers = if (effectiveTools) assistant.mcpServers else emptySet(),
                    enabledSkillIds = if (effectiveExtraPrompts) assistant.enabledSkillIds else emptySet(),
                    enabledLorebookIds = if (effectiveExtraPrompts) assistant.enabledLorebookIds else emptySet(),
                    enabledInstructionIds = if (effectiveExtraPrompts) assistant.enabledInstructionIds else emptySet(),
                    searchMode = if (effectiveTools) assistant.searchMode else AssistantSearchMode.Off,
                    streamOutput = true,
                )

                val userParts = buildQuickAskMessageParts(
                    text = inputData.text,
                    attachments = inputData.attachments,
                    customPrompt = customPromptText.takeIf { action.isCustomPrompt },
                )
                if (userParts.isEmpty()) {
                    state = TextSelectionState.Error("Nothing to ask about.", action = action)
                    return@launch
                }

                val memories = if (effectiveMemoryRead) {
                    resolveMemories(
                        settings = settings,
                        assistant = assistant,
                        queryText = buildQuickAskTextContent(
                            text = inputData.text,
                            customPrompt = customPromptText.takeIf { action.isCustomPrompt },
                        ),
                    )
                } else {
                    emptyList()
                }

                val isolation = QuickAskIsolationOptions(
                    allowMemoryTools = effectiveTools && effectiveMemoryRead,
                    allowMemoryWriteTools = effectiveMemoryWrite,
                    allowSkillTool = effectiveTools,
                    allowGlobalPromptInjections = effectiveExtraPrompts,
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
                    maxSteps = if (effectiveTools) 8 else 1,
                    quickAskIsolation = isolation,
                ).catch { error ->
                    Log.e(TAG, "Stream error", error)
                    state = TextSelectionState.Error(error.message ?: "Unknown error", action = action)
                }.collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages -> handleGeneratedMessages(
                            action = action,
                            messages = chunk.messages,
                            defaultThinkingExpanded = config.showThinkingByDefault,
                        )
                    }
                }

                val currentState = state
                if (currentState is TextSelectionState.Result) {
                    val finishedMs = currentState.reasoningFinishedAtMs
                        ?: if (currentState.reasoningText.isNotBlank()) System.currentTimeMillis() else null
                    val durationMs = currentState.reasoningDurationMs ?: run {
                        val start = currentState.reasoningStartedAtMs
                        if (start != null && finishedMs != null) (finishedMs - start).coerceAtLeast(0L) else null
                    }
                    state = currentState.copy(
                        isStreaming = false,
                        isReasoning = false,
                        reasoningFinishedAtMs = finishedMs,
                        reasoningDurationMs = durationMs,
                    )
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(TAG, "Error executing action", error)
                state = TextSelectionState.Error(error.message ?: "Unknown error", action = action)
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
        if (!assistant.useRagMemoryRetrieval || queryText.isBlank()) {
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

    private fun handleGeneratedMessages(
        action: TextSelectionAction,
        messages: List<UIMessage>,
        defaultThinkingExpanded: Boolean,
    ) {
        generatedMessages = messages
        val lastAssistantMessage = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        val responseText = lastAssistantMessage?.toContentText().orEmpty()

        val reasoningParts = lastAssistantMessage?.parts.orEmpty().mapNotNull { part ->
            when (part) {
                is UIMessagePart.Reasoning -> part.reasoning.takeIf { it.isNotBlank() }
                is UIMessagePart.Thinking -> part.thinking.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        val reasoningText = reasoningParts.joinToString("\n\n")

        val hasOpenReasoning = lastAssistantMessage?.parts?.any { part ->
            (part is UIMessagePart.Reasoning && part.finishedAt == null) ||
                (part is UIMessagePart.Thinking && part.finishedAt == null)
        } == true

        val partStartMs = lastAssistantMessage?.parts.orEmpty().mapNotNull { part ->
            when (part) {
                is UIMessagePart.Reasoning -> part.createdAt.toEpochMilliseconds()
                is UIMessagePart.Thinking -> part.createdAt.toEpochMilliseconds()
                else -> null
            }
        }.minOrNull()

        val partFinishMs = lastAssistantMessage?.parts.orEmpty().mapNotNull { part ->
            when (part) {
                is UIMessagePart.Reasoning -> part.finishedAt?.toEpochMilliseconds()
                is UIMessagePart.Thinking -> part.finishedAt?.toEpochMilliseconds()
                else -> null
            }
        }.maxOrNull()

        if ((hasOpenReasoning || reasoningText.isNotBlank()) && reasoningStartWallMs == null) {
            reasoningStartWallMs = partStartMs ?: System.currentTimeMillis()
        }

        val startedAtMs = partStartMs ?: reasoningStartWallMs
        val isStillReasoning = hasOpenReasoning || (reasoningText.isNotBlank() && responseText.isBlank() && partFinishMs == null)
        val finishedAtMs = if (!isStillReasoning && reasoningText.isNotBlank()) {
            partFinishMs ?: System.currentTimeMillis()
        } else {
            null
        }
        val durationMs = if (startedAtMs != null && finishedAtMs != null) {
            (finishedAtMs - startedAtMs).coerceAtLeast(0L)
        } else {
            null
        }

        val expanded = isThinkingExpandedUserOverride ?: defaultThinkingExpanded

        state = TextSelectionState.Result(
            action = action,
            actionName = activeActionName,
            modelName = activeModelName,
            assistantName = activeAssistantName,
            responseText = responseText,
            reasoningText = reasoningText,
            reasoningStartedAtMs = startedAtMs,
            reasoningFinishedAtMs = finishedAtMs,
            reasoningDurationMs = durationMs,
            isStreaming = true,
            isReasoning = isStillReasoning,
            isThinkingExpanded = expanded,
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
