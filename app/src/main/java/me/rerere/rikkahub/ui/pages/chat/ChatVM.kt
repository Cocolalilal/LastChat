package me.rerere.rikkahub.ui.pages.chat
import me.rerere.ai.ui.*





import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ConversationContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.resolveConversationContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatPersistenceMode
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UpdateInfo
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

internal data class ChatListScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatAttachmentRepository: ChatAttachmentRepository,
    private val chatService: ChatService,
    val updateChecker: UpdateChecker,
    private val appScope: me.rerere.rikkahub.AppScope,
    private val appStorageRepository: AppStorageRepository,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    private val _conversationInitialized = MutableStateFlow(false)
    val conversationInitialized: StateFlow<Boolean> = _conversationInitialized
    internal var chatListScrollPosition: ChatListScrollPosition? = null
        private set

    fun updateChatListScrollPosition(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        chatListScrollPosition = ChatListScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        )
    }
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    // 异步任务 (从ChatService获取，响应式)
    val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val conversationPersistenceMode: StateFlow<ChatPersistenceMode> =
        chatService
            .getConversationPersistenceModeFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.Lazily, ChatPersistenceMode.NORMAL)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // Track recently restored conversations for fade-in animation
    val recentlyRestoredIds: StateFlow<Set<Uuid>> = chatService.recentlyRestoredIds

    // Track recently restored message nodes for fade-in animation
    private val _recentlyRestoredNodeIds = MutableStateFlow<Set<Uuid>>(emptySet())
    val recentlyRestoredNodeIds: StateFlow<Set<Uuid>> = _recentlyRestoredNodeIds

    fun markNodesAsRestored(nodeIds: Set<Uuid>) {
        _recentlyRestoredNodeIds.value = _recentlyRestoredNodeIds.value + nodeIds
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _recentlyRestoredNodeIds.value = _recentlyRestoredNodeIds.value - nodeIds
        }
    }

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId)

        // 初始化对话
        viewModelScope.launch {
            try {
                chatService.initializeConversation(_conversationId)
            } finally {
                _conversationInitialized.value = true
            }
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeStringPreference("lastConversationId", _conversationId.toString())
    }

    override fun onCleared() {
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId)
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    val conversationContext: StateFlow<ConversationContext> =
        combine(settings, conversation) { currentSettings, currentConversation ->
            currentSettings.resolveConversationContext(currentConversation)
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            Settings.dummy().resolveConversationContext(conversation.value)
        )

    val conversationAssistant: StateFlow<Assistant> =
        conversationContext
            .map { it.assistant }
            .stateIn(viewModelScope, SharingStarted.Lazily, conversationContext.value.assistant)

    // 网络搜索 - 从当前会话助手的searchMode派生
    val enableWebSearch = conversationContext.map { context ->
        when (context.searchMode) {
            is me.rerere.rikkahub.data.model.AssistantSearchMode.Off -> false
            is me.rerere.rikkahub.data.model.AssistantSearchMode.BuiltIn -> true
            is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider -> true
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // 获取当前会话助手的searchMode
    val currentSearchMode = conversationContext.map { it.searchMode }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            me.rerere.rikkahub.data.model.AssistantSearchMode.Off
        )

    // 更新当前会话助手的searchMode
    fun updateAssistantSearchMode(searchMode: me.rerere.rikkahub.data.model.AssistantSearchMode) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == conversation.value.assistantId) {
                            it.copy(searchMode = searchMode)
                        } else {
                            it
                        }
                    }
                )
            }
        }
    }

    // 搜索关键词
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // 聊天列表 (使用 Paging 分页加载)
    val conversations: Flow<PagingData<ConversationListItem>> =
        combine(
            conversation.map { it.assistantId }.distinctUntilChanged(),
            _searchQuery
        ) { assistantId, query -> assistantId to query }
            .flatMapLatest { (assistantId, query) ->
                // 根据搜索关键词决定使用哪个数据源
                if (query.isBlank()) {
                    conversationRepo.getConversationsOfAssistantPaging(assistantId)
                } else {
                    conversationRepo.searchConversationsOfAssistantPaging(assistantId, query)
                }
            }
            .map { pagingData ->

                pagingData
                    .map { ConversationListItem.Item(it) }
                    .insertSeparators { before, after ->
                        when {
                            // 列表开头：检查第一项是否置顶
                            before == null && after is ConversationListItem.Item -> {
                                if (after.conversation.isPinned) {
                                    ConversationListItem.PinnedHeader
                                } else {
                                    val afterDate = after.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    ConversationListItem.DateHeader(
                                        date = afterDate,
                                        label = getDateLabel(afterDate)
                                    )
                                }
                            }

                            // 中间项：检查置顶状态变化和日期变化
                            before is ConversationListItem.Item && after is ConversationListItem.Item -> {
                                // 从置顶切换到非置顶，显示日期头部
                                if (before.conversation.isPinned && !after.conversation.isPinned) {
                                    val afterDate = after.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    ConversationListItem.DateHeader(
                                        date = afterDate,
                                        label = getDateLabel(afterDate)
                                    )
                                }
                                // 对于非置顶项，检查日期变化
                                else if (!after.conversation.isPinned) {
                                    val beforeDate = before.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    val afterDate = after.conversation.updateAt
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()

                                    if (beforeDate != afterDate) {
                                        ConversationListItem.DateHeader(
                                            date = afterDate,
                                            label = getDateLabel(afterDate)
                                        )
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                            else -> null
                        }
                    }
            }
            .catch { e ->
                e.printStackTrace()
                emit(PagingData.empty())
            }
            .cachedIn(viewModelScope)

    // 更新搜索关键词
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // 当前模型
    val currentChatModel = conversationContext.map { it.chatModel }
        .stateIn(viewModelScope, SharingStarted.Lazily, conversationContext.value.chatModel)

    // 错误流 (从ChatService获取)
    val errorFlow: SharedFlow<Throwable> = chatService.errorFlow

    // 生成完成 (从ChatService获取)
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器 (从ChatService获取)
    val mcpManager = chatService.mcpManager

    // 更新设置
    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            val oldSettings = settings.value
            // 检查用户头像是否有变化，如果有则删除旧头像
            checkUserAvatarDelete(oldSettings, newSettings)
            settingsStore.update(newSettings)
            appStorageRepository.deleteFilesIfUnreferenced(
                collectRemovedUserAvatarRefs(oldSettings, newSettings)
            )
        }
    }

    // 检查用户头像删除
    private suspend fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        // Cleanup now happens after the settings update through orphan-aware storage checks.
    }

    fun updateConversationAssistant(updatedAssistant: Assistant) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == updatedAssistant.id) {
                            updatedAssistant
                        } else {
                            it
                        }
                    })
            }
        }
    }

    // 设置聊天模型
    fun setChatModel(model: Model) {
        val assistant = conversationAssistant.value
        updateConversationAssistant(
            assistant.copy(chatModelId = model.id)
        )
    }

    fun setSelectedAssistant(assistantId: Uuid) {
        viewModelScope.launch {
            settingsStore.updateAssistant(assistantId)
        }
    }

    suspend fun createConversationForAssistant(assistantId: Uuid): Conversation {
        settingsStore.updateAssistant(assistantId)
        return chatService.createConversation(assistantId)
    }

    // --- Update checker ---
    val isForcedCheck: StateFlow<Boolean> = updateChecker.isForcedCheck

    val updateState: StateFlow<UiState<UpdateInfo>> = updateChecker.checkTrigger
        .flatMapLatest { updateChecker.checkUpdate() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val updateCheckerInstance = updateChecker

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     * @param isTemporaryChat 是否为临时对话（不保存历史、不使用记忆）
     */
    fun handleMessageSend(
        content: List<UIMessagePart>,
        answer: Boolean = true,
        persistenceMode: ChatPersistenceMode = ChatPersistenceMode.NORMAL,
    ) {
        if (content.isEmptyInputMessage()) return

        val assistant = conversationAssistant.value
        val processedContent = if (assistant != null) {
            content.map { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        part.copy(
                            text = part.text.replaceRegexes(
                                assistant = assistant,
                                scope = AssistantAffectScope.USER,
                                visual = false
                            )
                        )
                    }

                    else -> part
                }
            }
        } else {
            content
        }

        chatService.sendMessage(_conversationId, processedContent, answer, persistenceMode)
    }

    fun applyRoutePersistenceMode(mode: ChatPersistenceMode?) {
        if (mode == null) return
        chatService.ensureConversationPersistenceMode(_conversationId, mode)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return

        val assistant = conversationAssistant.value
        val processedParts = if (assistant != null) {
            parts.map { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        part.copy(
                            text = part.text.replaceRegexes(
                                assistant = assistant,
                                scope = AssistantAffectScope.USER,
                                visual = false
                            )
                        )
                    }

                    else -> part
                }
            }
        } else {
            parts
        }

        viewModelScope.launch {
            chatService.editMessage(_conversationId, messageId, processedParts)
        }
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        viewModelScope.launch {
            chatService.handleToolApproval(
                conversationId = _conversationId,
                toolCallId = toolCallId,
                approved = approved,
                reason = reason,
                answer = answer,
            )
        }
    }

    fun handleMessageTruncate() {
        viewModelScope.launch {
            val lastTruncateIndex = conversation.value.messageNodes.lastIndex + 1
            // 如果截断在最后一个索引，则取消截断，否则更新 truncateIndex 到最后一个截断位置
            val newConversation = conversation.value.copy(
                truncateIndex = if (conversation.value.truncateIndex == lastTruncateIndex) -1 else lastTruncateIndex,
                title = "",
                chatSuggestions = emptyList(), // 清空建议
            )
            chatService.saveConversation(conversationId = _conversationId, conversation = newConversation)
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation {
        return chatService.forkConversationAtMessage(_conversationId, message.id)
    }

    suspend fun deleteMessage(message: UIMessage): Set<Uuid> {
        val previousNodeIds = conversation.value.messageNodes.map { it.id }.toSet()
        chatService.deleteMessage(_conversationId, message.id)
        val updatedNodeIds = conversation.value.messageNodes.map { it.id }.toSet()
        return previousNodeIds - updatedNodeIds
    }

    fun selectMessageNode(nodeId: Uuid, selectIndex: Int) {
        viewModelScope.launch {
            chatService.selectMessageNode(_conversationId, nodeId, selectIndex)
        }
    }

    /**
     * Checks if regenerating this message will preserve version history (simple message)
     * or wipe the old version (complex message with tool calls).
     *
     * A message is considered "simple" if the turn contains only:
     * - Text/Thinking/Reasoning parts (no tool calls)
     *
     * A message is "complex" if the turn contains:
     * - Any tool calls or tool results
     *
     * @return true if the turn is simple (can go back), false if complex (will wipe)
     */
    fun canPreserveVersionHistory(message: UIMessage): Boolean {
        val currentMessages = conversation.value.messageNodes.map { it.currentMessage }

        // Find the index of the message
        val messageIndex = currentMessages.indexOfFirst { it.id == message.id }
        if (messageIndex == -1) return false

        // Find the start of the turn (last user message before this assistant message)
        val lastUserIndex = currentMessages
            .subList(0, messageIndex + 1)
            .indexOfLast { it.role == me.rerere.ai.core.MessageRole.USER }

        // Get all messages in this turn (from user to end of turn or next user)
        val turnStart = if (lastUserIndex >= 0) lastUserIndex else 0
        val turnEnd = currentMessages
            .subList(messageIndex, currentMessages.size)
            .indexOfFirst { it.role == me.rerere.ai.core.MessageRole.USER }
            .let { if (it == -1) currentMessages.size else messageIndex + it }

        // Check if any message in the turn has tool calls or tool results
        for (i in turnStart until turnEnd) {
            val msg = currentMessages[i]
            if (msg.parts.any { it is UIMessagePart.ToolCall || it is UIMessagePart.ToolResult }) {
                return false // Complex turn - cannot preserve version history
            }
        }

        return true // Simple turn - can preserve version history
    }

    fun regenerateAtMessage(
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true,
        forceWipe: Boolean = false
    ) {
        chatService.regenerateAtMessage(_conversationId, message, regenerateAssistantMsg, forceWipe)
    }

    fun saveConversationAsync() {
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, conversation.value)
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch {
            val updatedConversation = conversation.value.copy(title = title)
            chatService.saveConversation(
                conversationId = _conversationId,
                conversation = updatedConversation,
                preserveConsolidation = true,
            )
        }
    }

    fun deleteConversation(conversation: Conversation) {
        chatService.deleteConversation(conversation)
    }

    fun undoDeleteConversation(conversationId: Uuid) {
        chatService.undoDeleteConversation(conversationId)
    }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepo.togglePinStatus(conversation.id)
        }
    }

    fun updateConversationTitle(conversation: Conversation, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepo.updateTitle(
                conversationId = conversation.id,
                title = title,
                updateAt = conversation.updateAt,
            )
        }
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            val conversationFull = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversation.id)
            } ?: return@launch
            chatService.generateTitle(conversation.id, conversationFull, force)
        }
    }

    fun consolidateConversation(conversation: Conversation) {
        viewModelScope.launch {
            // Mark conversation as not consolidated so it will be picked up by the worker
            withContext(Dispatchers.IO) {
                conversationRepo.markAsNotConsolidated(conversation.id)
            }
            
            // Trigger a consolidation run with specific conversation ID
            val request = androidx.work.OneTimeWorkRequestBuilder<me.rerere.rikkahub.service.MemoryConsolidationWorker>()
                .setInputData(
                    androidx.work.workDataOf(
                        "FORCE_CONVERSATION_ID" to conversation.id.toString()
                    )
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(request)
        }
    }

    fun generateSuggestion(conversation: Conversation) {
        viewModelScope.launch {
            chatService.generateSuggestion(conversation.id, conversation)
        }
    }

    fun updateConversation(newConversation: Conversation) {
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, newConversation)
        }
    }

    fun deleteFile(uri: Uri) {
        appScope.launch(Dispatchers.IO) {
            chatAttachmentRepository.discardImportedUri(uri)
        }
    }

    // Context Refresh - summarize conversation and update context
    suspend fun refreshContext(): ChatService.ContextRefreshResult {
        return chatService.summarizeAndRefresh(_conversationId)
    }

    private fun getDateLabel(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        return when (date) {
            today -> context.getString(R.string.chat_page_today)
            yesterday -> context.getString(R.string.chat_page_yesterday)
            else -> date.toLocalString(date.year != today.year)
        }
    }
}

private fun collectRemovedUserAvatarRefs(
    oldSettings: Settings,
    newSettings: Settings,
): List<String> {
    val oldAvatar = oldSettings.displaySetting.userAvatar as? Avatar.Image ?: return emptyList()
    val newAvatar = newSettings.displaySetting.userAvatar as? Avatar.Image
    return if (oldAvatar.url != newAvatar?.url) listOf(oldAvatar.url) else emptyList()
}
