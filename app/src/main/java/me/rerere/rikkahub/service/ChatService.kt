package me.rerere.rikkahub.service

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.utils.LogUtil
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.truncate
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.deleteChatFiles
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

private val inputTransformers by lazy {
    listOf(
        PlaceholderTransformer,
        me.rerere.rikkahub.data.ai.transformers.UnsupportedFileTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
) {
    // 存储每个对话的状态
    private val conversations = ConcurrentHashMap<Uuid, MutableStateFlow<Conversation>>()

    // 记录哪些conversation有VM引用
    private val conversationReferences = ConcurrentHashMap<Uuid, Int>()

    // 记录哪些对话是临时对话（不持久化、不使用记忆）
    private val temporaryConversations = ConcurrentHashMap.newKeySet<Uuid>()

    // 存储每个对话的生成任务状态
    private val _generationJobs = MutableStateFlow<Map<Uuid, Job?>>(emptyMap())
    private val generationJobs: StateFlow<Map<Uuid, Job?>> = _generationJobs
        .asStateFlow()

    // 错误流
    private val _errorFlow = MutableSharedFlow<Throwable>()
    val errorFlow: SharedFlow<Throwable> = _errorFlow.asSharedFlow()

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> _isForeground.value = true
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> {}
        }
    }

    init {
        // 添加生命周期观察者
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        _generationJobs.value.values.forEach { it?.cancel() }
    }

    // 添加引用
    fun addConversationReference(conversationId: Uuid) {
        conversationReferences[conversationId] = conversationReferences.getOrDefault(conversationId, 0) + 1
        LogUtil.d(
            TAG,
            "Added reference for $conversationId (current references: ${conversationReferences[conversationId] ?: 0})"
        )
    }

    // 移除引用
    fun removeConversationReference(conversationId: Uuid) {
        conversationReferences[conversationId]?.let { count ->
            if (count > 1) {
                conversationReferences[conversationId] = count - 1
            } else {
                conversationReferences.remove(conversationId)
            }
        }
        LogUtil.d(
            TAG,
            "Removed reference for $conversationId (current references: ${conversationReferences[conversationId] ?: 0})"
        )
        appScope.launch {
            delay(500)
            checkAllConversationsReferences()
        }
    }

    // 检查是否有引用
    private fun hasReference(conversationId: Uuid): Boolean {
        return conversationReferences.containsKey(conversationId) || _generationJobs.value.containsKey(
            conversationId
        )
    }

    // 检查所有conversation的引用情况（生成结束后调用）
    fun checkAllConversationsReferences() {
        conversations.keys.forEach { conversationId ->
            if (!hasReference(conversationId)) {
                cleanupConversation(conversationId)
            }
        }
    }

    // 获取对话的StateFlow
    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        val settings = settingsStore.settingsFlow.value
        return conversations.getOrPut(conversationId) {
            MutableStateFlow(
                Conversation.ofId(
                    id = conversationId,
                    assistantId = settings.getCurrentAssistant().id
                )
            )
        }
    }

    // 获取生成任务状态流
    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        return generationJobs.map { jobs -> jobs[conversationId] }
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return generationJobs
    }

    private fun setGenerationJob(conversationId: Uuid, job: Job?) {
        if (job == null) {
            removeGenerationJob(conversationId)
            return
        }
        _generationJobs.value = _generationJobs.value.toMutableMap().apply {
            this[conversationId] = job
        }.toMap() // 确保创建新的不可变Map实例
    }

    private fun getGenerationJob(conversationId: Uuid): Job? {
        return _generationJobs.value[conversationId]
    }

    private fun removeGenerationJob(conversationId: Uuid) {
        _generationJobs.value = _generationJobs.value.toMutableMap().apply {
            remove(conversationId)
        }.toMap() // 确保创建新的不可变Map实例
    }

    // 初始化对话
    suspend fun initializeConversation(conversationId: Uuid) {
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
        } else {
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
            ).updateCurrentMessages(assistant.presetMessages)
            updateConversation(conversationId, newConversation)
        }
    }

    // 发送消息
    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean=true, isTemporaryChat: Boolean = false) {
        // 标记为临时对话
        if (isTemporaryChat) {
            temporaryConversations.add(conversationId)
        }
        
        // 取消现有的生成任务
        getGenerationJob(conversationId)?.cancel()

        val job = appScope.launch {
            try {
                val currentConversation = getConversationFlow(conversationId).value

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = content,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)

                // Record daily activity for streak tracking (persists even if chat is deleted)
                conversationRepo.recordDailyActivity()

                // 开始补全
                if(answer){
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorFlow.emit(e)
            }
        }
        setGenerationJob(conversationId, job)
        job.invokeOnCompletion {
            setGenerationJob(conversationId, null)
            // 取消生成任务后，检查是否有其他任务在进行
            appScope.launch {
                delay(500)
                checkAllConversationsReferences()
            }
        }
    }

    // Regenerate message - TURN-BASED for assistant messages
    // When regenerating an assistant message, we regenerate the entire turn
    // by finding the last user message and regenerating from there
    //
    // forceWipe: If true, completely removes the old turn and replaces it with a new one.
    //            If false, preserves old versions for simple messages (text only).
    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true,
        forceWipe: Boolean = false
    ) {
        getGenerationJob(conversationId)?.cancel()

        val job = appScope.launch {
            try {
                val conversation = getConversationFlow(conversationId).value

                if (message.role == MessageRole.USER) {
                    // If user message, truncate to that message and regenerate
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    // TURN-BASED REGENERATION for assistant messages
                    if (regenerateAssistantMsg) {
                        val clickedNode = conversation.getMessageNodeByMessage(message)
                        val clickedIndex = conversation.messageNodes.indexOf(clickedNode)

                        // Find the last user message before this point
                        val lastUserIndex = conversation.messageNodes
                            .subList(0, clickedIndex + 1)
                            .indexOfLast { it.role == MessageRole.USER }

                        if (lastUserIndex >= 0) {
                            // Find turn boundaries (first assistant node to end of turn)
                            val firstAssistantIndex = lastUserIndex + 1
                            val turnEndIndex = conversation.messageNodes
                                .subList(firstAssistantIndex, conversation.messageNodes.size)
                                .indexOfFirst { it.role == MessageRole.USER }
                                .let { if (it == -1) conversation.messageNodes.size else firstAssistantIndex + it }

                            if (forceWipe) {
                                // WIPE MODE: Completely remove old turn and replace with new assistant node
                                // This is used for complex messages with tool calls
                                val nodesBeforeTurn = conversation.messageNodes.subList(0, lastUserIndex + 1).toMutableList()

                                // Create a fresh assistant node
                                val freshAssistantNode = MessageNode(
                                    id = kotlin.uuid.Uuid.random(),
                                    messages = listOf(
                                        UIMessage(
                                            role = MessageRole.ASSISTANT,
                                            parts = emptyList(),
                                            versionTag = null  // No versioning when wiping
                                        )
                                    ),
                                    selectIndex = 0
                                )
                                nodesBeforeTurn.add(freshAssistantNode)

                                // Add any nodes after this turn
                                if (turnEndIndex < conversation.messageNodes.size) {
                                    nodesBeforeTurn.addAll(
                                        conversation.messageNodes.subList(turnEndIndex, conversation.messageNodes.size)
                                    )
                                }

                                val newConversation = conversation.copy(
                                    messageNodes = nodesBeforeTurn
                                )
                                saveConversation(conversationId, newConversation)
                                handleMessageComplete(conversationId)
                            } else {
                                // VERSION HISTORY MODE: Keep ALL nodes in the turn but add new versions with shared versionTag
                                // This is used for simple messages (text/reasoning only)
                                val versionTag = kotlin.uuid.Uuid.random().toString()

                                // Keep nodes up to user, then just the first assistant node with new version
                                val nodesBeforeTurn = conversation.messageNodes.subList(0, lastUserIndex + 1).toMutableList()

                                // For the first assistant node, add a new blank version with versionTag
                                val oldFirstAssistant = conversation.messageNodes.getOrNull(firstAssistantIndex)
                                if (oldFirstAssistant != null) {
                                    val blankMessage = UIMessage(
                                        role = MessageRole.ASSISTANT,
                                        parts = emptyList(),  // Empty parts = Waiting state
                                        versionTag = versionTag
                                    )
                                    val newMessages = oldFirstAssistant.messages + blankMessage
                                    val newAssistantNode = oldFirstAssistant.copy(
                                        messages = newMessages,
                                        selectIndex = newMessages.lastIndex
                                    )
                                    nodesBeforeTurn.add(newAssistantNode)

                                    // NOTE: We DON'T keep old tool nodes - they'll be replaced by new ones
                                    // during streaming. The versionTag filtering in UI will handle showing
                                    // correct content for each version.

                                    // Add any nodes after this turn (other user/assistant turns)
                                    if (turnEndIndex < conversation.messageNodes.size) {
                                        nodesBeforeTurn.addAll(
                                            conversation.messageNodes.subList(turnEndIndex, conversation.messageNodes.size)
                                        )
                                    }
                                }

                                val newConversation = conversation.copy(
                                    messageNodes = nodesBeforeTurn
                                )
                                saveConversation(conversationId, newConversation)
                                handleMessageComplete(conversationId)
                            }
                        } else {
                            // No user message found, regenerate from the clicked node
                            handleMessageComplete(conversationId, messageRange = 0..<clickedIndex)
                        }
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                _errorFlow.emit(e)
            }
        }

        setGenerationJob(conversationId, job)
        job.invokeOnCompletion {
            setGenerationJob(conversationId, null)
            // 取消生成任务后，检查是否有其他任务在进行
            appScope.launch {
                delay(500)
                checkAllConversationsReferences()
            }
        }
    }

    // 处理消息补全
    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.getCurrentChatModel() ?: return

        // Track generation start time for tokens/sec calculation
        // Set on first token arrival to exclude TTFT (time to first token) from the calculation
        var firstTokenTime: Long? = null

        runCatching {
            val conversation = getConversationFlow(conversationId).value

            // reset suggestions
            updateConversation(conversationId, conversation.copy(chatSuggestions = emptyList()))

            // Check if model supports tools when external tools are configured
            val assistant = settings.getCurrentAssistant()
            val hasExternalTools = (assistant.searchMode !is AssistantSearchMode.Off) || mcpManager.getAllAvailableTools().isNotEmpty()
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (hasExternalTools) {
                    _errorFlow.emit(IllegalStateException(context.getString(R.string.tools_warning)))
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)

            // start generating
            generationHandler.generateText(
                settings = settings,
                model = model,
                messages = conversation.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                },
                assistant = settings.getCurrentAssistant(),
                memories = if (settings.getCurrentAssistant().enableMemory && !temporaryConversations.contains(conversationId)) {
                    val assistant = settings.getCurrentAssistant()
                    if (assistant.useRagMemoryRetrieval) {
                        // RAG mode: retrieve relevant memories based on context
                        val lastUserMessage = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText() ?: ""
                        
                        if (settings.enableRagLogging) {
                            Log.d("RAG", "Query: $lastUserMessage")
                        }

                        if (lastUserMessage.isNotBlank()) {
                            val results = memoryRepository.retrieveRelevantMemories(
                                assistantId = settings.assistantId.toString(),
                                query = lastUserMessage,
                                limit = 50, // Hardcoded high limit for dynamic context
                                similarityThreshold = assistant.ragSimilarityThreshold,
                                includeCore = assistant.ragIncludeCore,
                                includeEpisodes = assistant.ragIncludeEpisodes
                            )
                            if (settings.enableRagLogging) {
                                Log.d("RAG", "Retrieved ${results.size} memories")
                                results.forEach { Log.d("RAG", " - [${it.type}] ${it.content.take(50)}...") }
                            }
                            results
                        } else {
                            if (settings.enableRagLogging) Log.d("RAG", "Empty query, using all memories")
                            memoryRepository.getMemoriesOfAssistant(settings.assistantId.toString())
                        }
                    } else {
                        // Simple mode: inject all memories
                        memoryRepository.getMemoriesOfAssistant(settings.assistantId.toString())
                    }
                } else {
                    emptyList()
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                },
                outputTransformers = outputTransformers,
                tools = buildList {
                    // Check if we should use built-in search instead of external tools
                    // Built-in search is used when:
                    // 1. preferBuiltInSearch is enabled on assistant
                    // 2. Model supports built-in search (Gemini series with search grounding)
                    val modelSupportsBuiltIn = model.tools.isNotEmpty() || 
                        me.rerere.ai.registry.ModelRegistry.GEMINI_SERIES.match(model.modelId)
                    val useBuiltInSearch = assistant.preferBuiltInSearch && modelSupportsBuiltIn
                    
                    // Use assistant's searchMode for external tools (only if NOT using built-in)
                    when (val searchMode = assistant.searchMode) {
                        is AssistantSearchMode.Provider -> {
                            // Only add external search tools if NOT using built-in search
                            if (!useBuiltInSearch) {
                                addAll(createSearchTool(settings, searchMode.index))
                            }
                        }
                        is AssistantSearchMode.BuiltIn -> {
                            // Built-in search is handled via model.tools, no external tool needed
                        }
                        is AssistantSearchMode.Off -> {
                            // No search tools
                        }
                    }
                    addAll(localTools.getTools(
                        options = settings.getCurrentAssistant().localTools,
                        assistantId = settings.getCurrentAssistant().id,
                        conversationId = conversation.id,
                        // Extract image URLs from last user message for Python auto-import
                        userImageUrls = conversation.currentMessages
                            .lastOrNull { it.role == MessageRole.USER }
                            ?.parts
                            ?.filterIsInstance<me.rerere.ai.ui.UIMessagePart.Image>()
                            ?.map { it.url }
                            ?: emptyList()
                    ))
                    mcpManager.getAllAvailableTools().forEach { tool ->
                        add(
                            Tool(
                                name = tool.name,
                                description = tool.description ?: "",
                                parameters = { tool.inputSchema },
                                execute = {
                                    mcpManager.callTool(tool.name, it.jsonObject).truncateLargeJsonText()
                                },
                            )
                        )
                    }
                },
                truncateIndex = conversation.truncateIndex,
                enabledModeIds = conversation.enabledModeIds,
            ).onCompletion {
                // Calculate generation duration from first token (excludes TTFT)
                val generationDurationMs = firstTokenTime?.let { System.currentTimeMillis() - it }

                // 可能被取消了，或者意外结束，兜底更新
                val currentConversation = getConversationFlow(conversationId).value
                val updatedConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes.mapIndexed { index, node ->
                        val isLastNode = index == currentConversation.messageNodes.lastIndex
                        node.copy(messages = node.messages.map { msg ->
                            val finishedMsg = msg.finishReasoning()
                            // Add generation duration to the last assistant message
                            if (isLastNode && finishedMsg.role == MessageRole.ASSISTANT && finishedMsg.generationDurationMs == null) {
                                // Debug usage
                                if (finishedMsg.usage == null) {
                                    Log.w(TAG, "Assistant message usage is null in onCompletion")
                                }
                                finishedMsg.copy(generationDurationMs = generationDurationMs)
                            } else {
                                finishedMsg
                            }
                        })
                    },
                    updateAt = Instant.now()
                )
                updateConversation(conversationId, updatedConversation)

                // Show notification if app is not in foreground
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                    sendGenerationDoneNotification(conversationId)
                }
            }.collect { chunk ->
                // Set first token time on first chunk arrival (excludes TTFT from tok/s)
                if (firstTokenTime == null) {
                    firstTokenTime = System.currentTimeMillis()
                }
                
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
            _errorFlow.emit(it)
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)

            addConversationReference(conversationId) // 添加引用
            appScope.launch {
                coroutineScope {
                    launch {
                        // Fetch fresh conversation from DB to ensure we have the latest state
                        // This matches the manual regeneration pattern which works correctly
                        val freshConversation = conversationRepo.getConversationById(conversationId)
                        if (freshConversation != null) {
                            generateTitle(conversationId, freshConversation)
                        } else {
                            Log.w(TAG, "generateTitle: conversation not found in DB for $conversationId")
                        }
                    }
                    launch { generateSuggestion(conversationId, finalConversation) }
                    
                    // Auto-summarization check
                    launch {
                        checkAndAutoSummarize(conversationId, finalConversation, settings)
                    }
                }
            }.invokeOnCompletion {
                removeConversationReference(conversationId) // 移除引用
            }
        }
    }

    // 创建搜索工具
    private fun createSearchTool(settings: Settings, providerIndex: Int? = null): Set<Tool> {
        // Use the provided providerIndex (from assistant's searchMode) or fall back to global selection
        val effectiveIndex = providerIndex ?: settings.searchServiceSelected
        return buildSet {
            add(
                Tool(
                    name = "search_web",
                    description = "search web for latest information",
                    parameters = {
                        val options = settings.searchServices.getOrElse(
                            index = effectiveIndex,
                            defaultValue = { SearchServiceOptions.DEFAULT })
                        val service = SearchService.getService(options)
                        service.parameters
                    },
                    execute = {
                        val options = settings.searchServices.getOrElse(
                            index = effectiveIndex,
                            defaultValue = { SearchServiceOptions.DEFAULT })
                        val service = SearchService.getService(options)
                        val result = service.search(
                            params = it.jsonObject,
                            commonOptions = settings.searchCommonOptions,
                            serviceOptions = options,
                        )
                        val results =
                            JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject.let { json ->
                                val map = json.toMutableMap()
                                val items = map["items"]
                                if (items is JsonArray) {
                                    map["items"] = JsonArray(items.mapIndexed { index, item ->
                                        if (item is JsonObject) {
                                            JsonObject(item.toMutableMap().apply {
                                                put("id", JsonPrimitive(Uuid.random().toString().take(6)))
                                                put("index", JsonPrimitive(index + 1))
                                            })
                                        } else {
                                            item
                                        }
                                    })
                                }
                                JsonObject(map)
                            }
                        results
                    }, systemPrompt = { model, messages ->
                        if (model.tools.isNotEmpty()) return@Tool ""
                        val hasToolCall =
                            messages.any { it.getToolCalls().any { toolCall -> toolCall.toolName == "search_web" } }
                        val prompt = StringBuilder()
                        prompt.append(
                            """
                    ## tool: search_web

                    ### usage
                    - You can use the search_web tool to search the internet for the latest news or to confirm some facts.
                    - You can perform multiple search if needed
                    - Generate keywords based on the user's question
                    - Today is {{cur_date}}
                    """.trimIndent()
                        )
                        if (hasToolCall) {
                            prompt.append(
                                """
                        ### result example
                        ```json
                        {
                            "items": [
                                {
                                    "id": "random id in 6 characters",
                                    "title": "Title",
                                    "url": "https://example.com",
                                    "text": "Some relevant snippets"
                                }
                            ]
                        }
                        ```

                        ### citation
                        After using the search tool, when replying to users, you need to add a reference format to the referenced search terms in the content.
                        When citing facts or data from search results, you need to add a citation marker after the sentence: `[citation,domain](id of the search result)`.

                        For example:
                        ```
                        The capital of France is Paris. [citation,example.com](id of the search result)

                        The population of Paris is about 2.1 million. [citation,example.com](id of the search result) [citation,example2.com](id of the search result)
                        ```

                        If no search results are cited, you do not need to add a citation marker.
                        """.trimIndent()
                            )
                        }
                        prompt.toString()
                    }
                )
            )

            val options = settings.searchServices.getOrElse(
                index = effectiveIndex,
                defaultValue = { SearchServiceOptions.DEFAULT })
            val service = SearchService.getService(options)
            if (service.scrapingParameters != null) {
                add(
                    Tool(
                        name = "scrape_web",
                        description = "scrape web for content",
                        parameters = {
                            val options = settings.searchServices.getOrElse(
                                index = effectiveIndex,
                                defaultValue = { SearchServiceOptions.DEFAULT })
                            val service = SearchService.getService(options)
                            service.scrapingParameters
                        },
                        execute = {
                            val options = settings.searchServices.getOrElse(
                                index = effectiveIndex,
                                defaultValue = { SearchServiceOptions.DEFAULT })
                            val service = SearchService.getService(options)
                            val result = service.scrape(
                                params = it.jsonObject,
                                commonOptions = settings.searchCommonOptions,
                                serviceOptions = options,
                            )
                            JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject
                        },
                        systemPrompt = { model, messages ->
                            return@Tool """
                            ## tool: scrape_web

                            ### usage
                            - You can use the scrape_web tool to scrape url for detailed content.
                            - You can perform multiple scrape if needed.
                            - For common problems, try not to use this tool unless the user requests it.
                        """.trimIndent()
                        }
                    ))
            }
        }
    }

    // 检查无效消息
    private suspend fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // Step 1: 移除空消息节点 (do this FIRST to prevent exceptions)
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        // Step 2: 更新无效的selectIndex (do this BEFORE accessing currentMessage)
        messagesNodes = messagesNodes.map { node ->
            if (node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // Step 3: 移除无效tool call (now safe to access currentMessage)
        messagesNodes = messagesNodes.mapIndexed { index, node ->
            val next = if (index < messagesNodes.size - 1) messagesNodes[index + 1] else null
            if (node.currentMessage.hasPart<UIMessagePart.ToolCall>()) {
                if (next?.currentMessage?.hasPart<UIMessagePart.ToolResult>() != true) {
                    return@mapIndexed node.copy(
                        messages = node.messages.filter { it.id != node.currentMessage.id },
                        selectIndex = node.selectIndex - 1
                    )
                }
            }
            node
        }

        // Step 4: Final cleanup after tool call removal
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }
        messagesNodes = messagesNodes.map { node ->
            if (node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0.coerceAtMost(node.messages.lastIndex))
            } else {
                node
            }
        }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    // 生成标题
    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) {
            LogUtil.d(TAG, "generateTitle: skipped (title='${conversation.title.take(20)}', force=$force)")
            return
        }
        LogUtil.d(TAG, "generateTitle: starting for conversation ${conversation.id}, messages=${conversation.messageNodes.size}")

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model =
                settings.findModelById(settings.titleModelId) ?: settings.getCurrentChatModel()
            if (model == null) {
                Log.w(TAG, "generateTitle: No model found for titleModelId=${settings.titleModelId} and no current chat model")
                return
            }
            val provider = model.findProvider(settings.providers)
            if (provider == null) {
                Log.w(TAG, "generateTitle: No provider found for model ${model.displayName}")
                return
            }

            val providerHandler = providerManager.getProviderByType(provider)
            
            // Check if we have content to generate a title from
            val contentForTitle = conversation.currentMessages.truncate(conversation.truncateIndex)
                .joinToString("\n\n") { it.summaryAsText() }
            
            if (contentForTitle.isBlank()) {
                Log.w(TAG, "generateTitle: No content available for title generation (messages=${conversation.messageNodes.size}, truncateIndex=${conversation.truncateIndex})")
                return
            }
            
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to contentForTitle)
                    ),
                ),
                params = TextGenerationParams(
                    model = model, temperature = 0.3f, thinkingBudget = 0
                ),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toContentText()?.trim() ?: "")
                )
            }
        }.onFailure {
            Log.e(TAG, "generateTitle failed: ${it.message}", it)
        }
    }

    // 生成建议
    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.suggestionModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            updateConversation(
                conversationId,
                getConversationFlow(conversationId).value.copy(chatSuggestions = emptyList())
            )

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages.truncate(conversation.truncateIndex)
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    temperature = 1.0f,
                    thinkingBudget = 0,
                ),
            )
            val suggestions =
                result.choices[0].message?.toContentText()?.split("\n")?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            // Fetch fresh conversation from DB to avoid overwriting concurrent updates (e.g., title generation)
            conversationRepo.getConversationById(conversationId)?.let { freshConversation ->
                saveConversation(
                    conversationId,
                    freshConversation.copy(chatSuggestions = suggestions)
                )
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    private val conversationDeletionJobs = java.util.concurrent.ConcurrentHashMap<Uuid, Job>()
    private val recentlyDeletedConversations = java.util.concurrent.ConcurrentHashMap<Uuid, Conversation>()

    // Track recently restored conversations for fade-in animation
    private val _recentlyRestoredIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Uuid>>(emptySet())
    val recentlyRestoredIds: kotlinx.coroutines.flow.StateFlow<Set<Uuid>> = _recentlyRestoredIds

    fun deleteConversation(conversation: Conversation) {
        appScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch

            // Cancel any pending deletion for this conversation
            conversationDeletionJobs[conversation.id]?.cancel()

            // Soft delete (DB only, preserve files)
            conversationRepo.deleteConversation(conversationFull, deleteFiles = false)
            recentlyDeletedConversations[conversation.id] = conversationFull

            // Schedule file deletion
            val job = appScope.launch {
                kotlinx.coroutines.delay(4000)
                context.deleteChatFiles(conversationFull.files)
                conversationDeletionJobs.remove(conversation.id)
                recentlyDeletedConversations.remove(conversation.id)
            }
            conversationDeletionJobs[conversation.id] = job
        }
    }

    fun undoDeleteConversation(conversationId: Uuid) {
        conversationDeletionJobs[conversationId]?.cancel()
        conversationDeletionJobs.remove(conversationId)

        val conversation = recentlyDeletedConversations[conversationId]
        if (conversation != null) {
            appScope.launch {
                conversationRepo.insertConversation(conversation)
                recentlyDeletedConversations.remove(conversationId)

                // Track for fade-in animation
                _recentlyRestoredIds.value = _recentlyRestoredIds.value + conversationId

                // Remove from tracking after animation completes
                kotlinx.coroutines.delay(1000)
                _recentlyRestoredIds.value = _recentlyRestoredIds.value - conversationId
            }
        }
    }


    // 发送生成完成通知
    private fun sendGenerationDoneNotification(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        val notification =
            NotificationCompat.Builder(context, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_chat_done_title))
                .setContentText(conversation.currentMessages.lastOrNull()?.toText()?.take(50) ?: "")
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(getPendingIntent(context, conversationId))

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(1, notification.build())
    }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // 更新对话
    private suspend fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        checkFilesDelete(conversation, getConversationFlow(conversationId).value)
        conversations.getOrPut(conversationId) { MutableStateFlow(conversation) }.value =
            conversation
    }

    // 检查文件删除
    private suspend fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            context.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    // Context Refresh result
    data class ContextRefreshResult(
        val success: Boolean,
        val summary: String = "",
        val messagesSummarized: Int = 0,
        val tokensSaved: Int = 0,
        val errorMessage: String? = null
    )

    // Check if auto-summarization threshold is reached and trigger if needed
    private suspend fun checkAndAutoSummarize(
        conversationId: Uuid,
        conversation: Conversation,
        settings: Settings
    ) {
        try {
            val assistant = settings.getCurrentAssistant()
            
            // Check if auto-summarization is enabled
            if (!assistant.enableContextRefresh || !assistant.autoRegenerateSummary) {
                return
            }
            
            // Get max history messages setting (null = unlimited, don't auto-summarize)
            val maxMessages = assistant.maxHistoryMessages ?: return
            
            // Calculate new messages since last summary
            val messages = conversation.currentMessages
            val lastSummaryIndex = conversation.contextSummaryUpToIndex
            val hasPreviousSummary = !conversation.contextSummary.isNullOrBlank() && lastSummaryIndex >= 0
            
            val messagesToKeep = 2 // Keep last user+assistant exchange
            val messagesToSummarizeCount = if (hasPreviousSummary && lastSummaryIndex < messages.size) {
                // Messages after last summary, minus the ones we keep
                (messages.size - lastSummaryIndex - 1 - messagesToKeep).coerceAtLeast(0)
            } else {
                // No previous summary - all messages minus kept ones
                (messages.size - messagesToKeep).coerceAtLeast(0)
            }
            
            // Check if we've reached the max history messages limit
            if (messagesToSummarizeCount >= maxMessages) {
                Log.i(TAG, "Auto-summarization triggered: $messagesToSummarizeCount messages >= max $maxMessages")
                val result = summarizeAndRefresh(conversationId)
                if (result.success) {
                    Log.i(TAG, "Auto-summarization completed: ${result.messagesSummarized} messages summarized, ${result.tokensSaved} tokens saved")
                } else {
                    Log.w(TAG, "Auto-summarization failed: ${result.errorMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAndAutoSummarize failed", e)
        }
    }

    // Summarize and refresh context
    suspend fun summarizeAndRefresh(conversationId: Uuid): ContextRefreshResult = withContext(Dispatchers.IO) {
        try {
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getCurrentAssistant()
            val conversation = conversationRepo.getConversationById(conversationId)
                ?: return@withContext ContextRefreshResult(false, errorMessage = "Conversation not found")

            // Check for empty messages FIRST before model lookup
            val messages = conversation.currentMessages
            if (messages.isEmpty()) {
                return@withContext ContextRefreshResult(false, errorMessage = "No messages to summarize")
            }

            // Get the summarizer model (fall back to chat model)
            val summarizerModelId = assistant.summarizerModelId ?: assistant.chatModelId ?: settings.chatModelId
            val model = settings.findModelById(summarizerModelId)
                ?: return@withContext ContextRefreshResult(false, errorMessage = "No model configured")
            val provider = model.findProvider(settings.providers)
                ?: return@withContext ContextRefreshResult(false, errorMessage = "No provider found")



            // Determine which messages to summarize
            val previousSummary = conversation.contextSummary
            val lastSummaryIndex = conversation.contextSummaryUpToIndex
            val hasPreviousSummary = !previousSummary.isNullOrBlank() && lastSummaryIndex >= 0
            
            // Keep the last 2 messages (user + assistant exchange) so the AI remembers what was just said
            val messagesToKeep = 2
            val lastIndexToSummarize = (messages.size - messagesToKeep - 1).coerceAtLeast(0)
            
            // Only get messages AFTER the last summary index, but before the last 2 messages
            val startIndex = if (hasPreviousSummary && lastSummaryIndex < messages.size) {
                (lastSummaryIndex + 1).coerceAtMost(messages.size)
            } else {
                0 // No previous summary, summarize from beginning
            }
            
            val messagesToSummarize = if (startIndex <= lastIndexToSummarize) {
                messages.subList(startIndex, lastIndexToSummarize + 1)
            } else {
                emptyList()
            }
            
            if (messagesToSummarize.isEmpty()) {
                return@withContext ContextRefreshResult(false, errorMessage = "No new messages to summarize (keeping last exchange)")
            }

            // Build summarization prompt - only include NEW messages
            val messagesText = messagesToSummarize.joinToString("\n") { msg ->
                "${msg.role}: ${msg.toText().take(500)}" // Limit each message
            }
            
            val prompt = if (hasPreviousSummary) {
                """
                    You have a previous summary of this conversation. Update and expand it with new information from the recent messages.
                    
                    **Previous Summary:**
                    $previousSummary
                    
                    **New Messages (${messagesToSummarize.size} messages since last summary):**
                    $messagesText
                    
                    Create an updated summary that:
                    - Preserves important context from the previous summary
                    - Incorporates new information from recent messages
                    - Keeps the summary under 500 words
                    - Focuses on: main topics, key decisions, pending tasks, user preferences
                    
                    Updated Summary:
                """.trimIndent()
            } else {
                """
                    Summarize this conversation concisely, preserving the key context, decisions, and important information that would be needed to continue the conversation. Focus on:
                    - Main topics discussed
                    - Key decisions or conclusions
                    - Any pending questions or tasks
                    - Important user preferences revealed
                    
                    Keep the summary under 500 words.
                    
                    Conversation:
                    $messagesText
                    
                    Summary:
                """.trimIndent()
            }

            // Estimate tokens saved (based on messages being summarized)
            val originalTokens = messagesToSummarize.sumOf { msg ->
                msg.parts.sumOf { part ->
                    when (part) {
                        is UIMessagePart.Text -> part.text.length / 4
                        else -> 50
                    }
                }
            }

            // Call the model
            val providerHandler = providerManager.getProviderByType(provider)
            val response = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(model = model, temperature = 0.3f)
            )

            val summary = response.choices.firstOrNull()?.message?.toContentText()
                ?: return@withContext ContextRefreshResult(false, errorMessage = "Empty response from model")

            // Estimate new tokens
            val summaryTokens = summary.length / 4

            // Update conversation with summary
            val now = System.currentTimeMillis()
            val updatedConversation = conversation.copy(
                contextSummary = summary,
                contextSummaryUpToIndex = lastIndexToSummarize, // Index of last message included in summary
                lastRefreshTime = now
            )

            // Persist changes
            conversationRepo.updateConversation(updatedConversation)
            updateConversation(conversationId, updatedConversation)

            Log.i(TAG, "summarizeAndRefresh: Summarized ${messagesToSummarize.size} new messages, saved ~${originalTokens - summaryTokens} tokens")

            ContextRefreshResult(
                success = true,
                summary = summary,
                messagesSummarized = messagesToSummarize.size,
                tokensSaved = (originalTokens - summaryTokens).coerceAtLeast(0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "summarizeAndRefresh failed", e)
            ContextRefreshResult(false, errorMessage = e.message ?: "Unknown error")
        }
    }


    // 保存对话
    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        // 临时对话不持久化到数据库
        if (temporaryConversations.contains(conversationId)) {
            updateConversation(conversationId, conversation)
            return
        }

        val updatedConversation = conversation.copy()
        // Always update in-memory state (even for empty conversations)
        // This ensures mode toggles work on new chats before first message
        updateConversation(conversationId, updatedConversation)

        // Skip database persist for empty conversations (no messages and no title)
        if (conversation.title.isBlank() && conversation.messageNodes.isEmpty()) return

        try {
            if (conversationRepo.getConversationById(conversation.id) == null) {
                conversationRepo.insertConversation(updatedConversation)
            } else {
                conversationRepo.updateConversation(updatedConversation)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 翻译消息
    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()
                val conversation = getConversationFlow(conversationId).value
                val assistant = settings.getAssistantById(conversation.assistantId)
                val chatModelId = assistant?.chatModelId ?: settings.chatModelId

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage,
                    modelIdOverride = chatModelId,
                ) { translatedText ->
                    // Update translation field in real-time
                    appScope.launch {
                        updateTranslationField(conversationId, message.id, translatedText)
                    }
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                _errorFlow.emit(e)
            }
        }
    }

    private suspend fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 清理对话相关资源
    fun cleanupConversation(conversationId: Uuid) {
        getGenerationJob(conversationId)?.cancel()
        removeGenerationJob(conversationId)
        conversations.remove(conversationId)

        Log.i(
            TAG,
            "cleanupConversation: removed $conversationId (current references: ${conversationReferences.size}, generation jobs: ${_generationJobs.value.size})"
        )
    }
}

private fun kotlinx.serialization.json.JsonElement.truncateLargeJsonText(maxLength: Int = 32000): kotlinx.serialization.json.JsonElement {
    return when (this) {
        is kotlinx.serialization.json.JsonPrimitive -> {
            if (this.isString) {
                val content = this.content
                if (content.length > maxLength) {
                    kotlinx.serialization.json.JsonPrimitive(content.take(maxLength) + "... (truncated ${content.length - maxLength} chars)")
                } else {
                    this
                }
            } else {
                this
            }
        }
        is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(this.mapValues { it.value.truncateLargeJsonText(maxLength) })
        is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(this.map { it.truncateLargeJsonText(maxLength) })
        else -> this
    }
}
