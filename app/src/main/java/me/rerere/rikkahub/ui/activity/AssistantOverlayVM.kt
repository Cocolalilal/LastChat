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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getChatModelForAssistant
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import me.rerere.rikkahub.service.defaultChatInputTransformers
import me.rerere.rikkahub.service.defaultChatOutputTransformers
import me.rerere.rikkahub.data.ai.shouldUseBuiltInSearch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchServiceOptions
import me.rerere.search.SearchService
import me.rerere.workspace.WorkspaceShellStatus
import kotlin.uuid.Uuid

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
    private val localTools: LocalTools,
    private val workspaceRepository: WorkspaceRepository,
    private val mcpManager: McpManager,
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
    private var lastAttachments: List<QuickAskAttachment> = emptyList()

    /** [parts] comes straight from ChatInputState.getContents(). */
    internal fun send(
        parts: List<UIMessagePart>,
        screenshotDataUrl: String?,
    ) {
        val cleanedParts = parts.filterNot { it is UIMessagePart.Text && it.text.isBlank() }
        if (cleanedParts.isEmpty() && screenshotDataUrl == null) return
        val text = cleanedParts.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }.trim()
        currentJob?.cancel()
        state = OverlayState.Generating
        lastUserText = text
        lastAttachments = cleanedParts.toContinuationAttachments()

        currentJob = viewModelScope.launch {
            try {
                val settings = settingsStore.settingsFlow.value
                val assistant = settings.resolveAssistantOverlayAssistant()
                lastAssistantId = assistant.id.toString()
                assistantName = assistant.name

                val model = settings.assistantOverlayConfig.modelId?.let { settings.findModelById(it) }
                    ?: settings.getChatModelForAssistant(assistant)
                if (model == null) {
                    state = OverlayState.Error("No chat model is configured for this assistant.")
                    return@launch
                }

                // Screenshot is no longer auto-attached as an image part.
                // It is available to the model via the look_at_screen tool.
                val messageParts = cleanedParts
                if (messageParts.isEmpty() && screenshotDataUrl == null) {
                    state = OverlayState.Error("Nothing to ask.")
                    return@launch
                }

                val memories = resolveMemories(settings, assistant, text)

                // Build tools — mirrors ChatService.buildConversationTools()
                val tools = buildOverlayTools(
                    settings = settings,
                    assistant = assistant,
                    model = model,
                    screenshotDataUrl = screenshotDataUrl,
                )

                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = listOf(UIMessage(role = MessageRole.USER, parts = messageParts)),
                    inputTransformers = buildList {
                        addAll(defaultChatInputTransformers)
                        add(templateTransformer)
                    },
                    outputTransformers = defaultChatOutputTransformers,
                    assistant = assistant,
                    memories = memories,
                    tools = tools,
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
            attachments = lastAttachments,
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

    /**
     * Build the tool list for the overlay — mirrors [me.rerere.rikkahub.service.ChatService.buildConversationTools].
     * Additionally adds a `look_at_screen` tool when a screenshot is available.
     */
    private suspend fun buildOverlayTools(
        settings: Settings,
        assistant: Assistant,
        model: me.rerere.ai.provider.Model,
        screenshotDataUrl: String?,
    ): List<Tool> {
        return buildList {
            // Search tools (if assistant searchMode is Provider and not using built-in search)
            val useBuiltInSearch = shouldUseBuiltInSearch(model, assistant)
            when (val searchMode = assistant.searchMode) {
                is AssistantSearchMode.Provider -> {
                    if (!useBuiltInSearch) {
                        addAll(createSearchTool(settings, searchMode.index))
                    }
                }

                is AssistantSearchMode.BuiltIn -> Unit
                is AssistantSearchMode.Off -> Unit
            }

            // Local tools
            addAll(
                localTools.getTools(
                    options = assistant.localTools,
                    assistantId = assistant.id,
                    conversationId = Uuid.random(), // overlay has no real conversation
                )
            )

            // Workspace tools (if model supports TOOL ability and workspace is ready)
            val workspaceId = assistant.workspaceId?.toString()
            val workspace = workspaceId?.let { workspaceRepository.getById(it) }
            if (
                model.abilities.contains(ModelAbility.TOOL) &&
                workspace != null &&
                workspace.shellStatus == WorkspaceShellStatus.READY.name
            ) {
                addAll(
                    createWorkspaceTools(
                        workspaceId = workspaceId,
                        workspaceRepository = workspaceRepository,
                    )
                )
            }

            // MCP tools
            mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                add(
                    Tool(
                        name = tool.name,
                        description = tool.description ?: "",
                        parameters = { tool.inputSchema },
                        execute = {
                            mcpManager.callTool(serverId, tool.name, it.jsonObject)
                        },
                    )
                )
            }

            // look_at_screen tool (overlay-specific)
            if (screenshotDataUrl != null) {
                add(createLookAtScreenTool(screenshotDataUrl, model, settings))
            }
        }
    }

    /**
     * Search tool — mirrors ChatService.createSearchTool but simplified.
     */
    private fun createSearchTool(settings: Settings, providerIndex: Int?): Set<Tool> {
        val effectiveIndex = providerIndex ?: settings.searchServiceSelected
        return setOf(
            Tool(
                name = "search_web",
                description = "search web for latest information",
                parameters = {
                    val options = settings.searchServices.getOrElse(
                        index = effectiveIndex,
                        defaultValue = { SearchServiceOptions.DEFAULT },
                    )
                    val service = SearchService.getService(options)
                    service.parameters
                },
                execute = {
                    val options = settings.searchServices.getOrElse(
                        index = effectiveIndex,
                        defaultValue = { SearchServiceOptions.DEFAULT },
                    )
                    val service = SearchService.getService(options)
                    val result = service.search(
                        params = it.jsonObject,
                        commonOptions = settings.searchCommonOptions,
                        serviceOptions = options,
                    )
                    kotlinx.serialization.json.Json.encodeToJsonElement(
                        me.rerere.search.SearchResult.serializer(),
                        result.getOrThrow()
                    )
                },
            ),
        )
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}

/**
 * `look_at_screen` tool: lets the model inspect the screenshot captured when the
 * assistant was summoned.
 *
 * - If the model supports image input ([Modality.IMAGE]), returns the screenshot as
 *   an image data URL the model can see directly.
 * - If the model doesn't support image input, falls back to OCR using the configured
 *   OCR model (settings.ocrModelId) and returns the extracted text.
 */
private fun createLookAtScreenTool(
    screenshotDataUrl: String,
    model: me.rerere.ai.provider.Model,
    settings: Settings,
): Tool = Tool(
    name = "look_at_screen",
    description = "Look at a screenshot of the user's current screen captured when " +
        "the assistant was summoned. Use this when you need visual context about " +
        "what the user is looking at.",
    parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
    approvalMode = me.rerere.ai.core.ToolApprovalMode.Auto,
    execute = {
        if (model.inputModalities.contains(Modality.IMAGE)) {
            // Model supports vision — return the image data URL
            buildJsonObject {
                put("type", JsonPrimitive("image"))
                put("image", JsonPrimitive(screenshotDataUrl))
                put("description", JsonPrimitive("Screenshot of the user's screen at summon time"))
            }
        } else {
            // Model doesn't support images — use OCR fallback
            val ocrText = try {
                OcrTransformer.performOcr(
                    UIMessagePart.Image(url = screenshotDataUrl),
                )
            } catch (e: Throwable) {
                null
            }
            buildJsonObject {
                put("type", JsonPrimitive("ocr_text"))
                if (ocrText.isNullOrBlank()) {
                    put("text", JsonPrimitive(""))
                    put("note", JsonPrimitive("OCR could not extract text from the screenshot."))
                } else {
                    put("text", JsonPrimitive(ocrText))
                }
            }
        }
    },
)

private fun List<UIMessagePart>.toContinuationAttachments(): List<QuickAskAttachment> {
    return mapNotNull { part ->
        when (part) {
            is UIMessagePart.Image -> QuickAskAttachment(
                uri = part.url,
                fileName = part.url.substringAfterLast('/'),
                mimeType = "image/*",
            )

            is UIMessagePart.Video -> QuickAskAttachment(
                uri = part.url,
                fileName = part.url.substringAfterLast('/'),
                mimeType = "video/*",
            )

            is UIMessagePart.Audio -> QuickAskAttachment(
                uri = part.url,
                fileName = part.url.substringAfterLast('/'),
                mimeType = "audio/*",
            )

            is UIMessagePart.Document -> QuickAskAttachment(
                uri = part.url,
                fileName = part.fileName,
                mimeType = part.mime,
            )

            else -> null
        }
    }
}
