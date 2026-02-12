package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.deleteChatFiles
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatService: ChatService,
    val updateChecker: UpdateChecker,
    private val appScope: me.rerere.rikkahub.AppScope
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    // 异步任务 (从ChatService获取，响应式)
    val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

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
            chatService.initializeConversation(_conversationId)
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

    // New chat stats for widgets - includes per-assistant stats
    @Suppress("UNCHECKED_CAST")
    val newChatStats: StateFlow<me.rerere.rikkahub.ui.components.chat.NewChatStats> = settings
        .flatMapLatest { currentSettings ->
            val assistantId = currentSettings.assistantId.toString()
            val baseFlow = kotlinx.coroutines.flow.combine(
                conversationRepo.getConversationCountFlow(),
                conversationRepo.getDailyActivityDatesFlow(), // Uses persistent activity table instead of conversation dates
                conversationRepo.getConversationHoursFlow(),
                conversationRepo.getConversationCountByAssistantFlow(assistantId)
            ) { totalChats, distinctDates, hours, assistantChats ->
                kotlin.Pair(
                    Triple(totalChats, distinctDates, hours),
                    assistantChats
                )
            }
            
            kotlinx.coroutines.flow.combine(
                baseFlow,
                conversationRepo.getMostUsedModelIdForAssistantFlow(assistantId)
            ) { (base, assistantChats), mostUsedModelId ->
                val (totalChats, distinctDates, hours) = base
                
                val today = java.time.LocalDate.now()
                val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                
                val dates = (distinctDates as List<String>).mapNotNull { 
                    try { java.time.LocalDate.parse(it, formatter) } catch (e: Exception) { null }
                }.sortedDescending()
                
                val hasChattedToday = dates.contains(today)
                val yesterday = today.minusDays(1)
                
                // Calculate streak
                val startDate = when {
                    hasChattedToday -> today
                    dates.contains(yesterday) -> yesterday
                    else -> null
                }
                
                val streak = if (startDate != null) {
                    var count = 0
                    var current: java.time.LocalDate = startDate
                    while (dates.contains(current)) {
                        count++
                        current = current.minusDays(1)
                    }
                    count
                } else 0
                
                // Calculate time label from conversation hours
                val timeLabel = calculateTimeLabel(hours as List<Int>)
                
                // Look up model name from settings providers
                val modelName = mostUsedModelId?.let { id ->
                    try {
                        val uuid = kotlin.uuid.Uuid.parse(id)
                        // Search through all providers to find the model
                        currentSettings.providers.flatMap { it.models }
                            .find { it.id == uuid }
                            ?.displayName
                    } catch (e: Exception) {
                        null
                    }
                }
                
                me.rerere.rikkahub.ui.components.chat.NewChatStats(
                    dailyStreak = streak,
                    totalChats = totalChats as Int,
                    timeLabel = timeLabel,
                    hasChattedToday = hasChattedToday,
                    assistantChats = assistantChats,
                    mostUsedModelName = modelName
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), me.rerere.rikkahub.ui.components.chat.NewChatStats())
    
    private fun calculateTimeLabel(hours: List<Int>): me.rerere.rikkahub.ui.pages.menu.TimeLabel {
        if (hours.isEmpty()) return me.rerere.rikkahub.ui.pages.menu.TimeLabel.DAYTIME_CHATTER
        
        var earlyBird = 0   // 5am-11am
        var daytime = 0     // 11am-6pm
        var nightOwl = 0    // 6pm-5am
        
        for (hour in hours) {
            when (hour) {
                in 5..10 -> earlyBird++
                in 11..17 -> daytime++
                else -> nightOwl++
            }
        }
        
        return when {
            earlyBird >= daytime && earlyBird >= nightOwl -> me.rerere.rikkahub.ui.pages.menu.TimeLabel.EARLY_BIRD
            daytime >= earlyBird && daytime >= nightOwl -> me.rerere.rikkahub.ui.pages.menu.TimeLabel.DAYTIME_CHATTER
            else -> me.rerere.rikkahub.ui.pages.menu.TimeLabel.NIGHT_OWL
        }
    }

    // 网络搜索 - 从当前助手的searchMode派生
    val enableWebSearch = settings.map { settings ->
        val assistant = settings.assistants.find { it.id == settings.assistantId }
        when (assistant?.searchMode) {
            is me.rerere.rikkahub.data.model.AssistantSearchMode.Off -> false
            is me.rerere.rikkahub.data.model.AssistantSearchMode.BuiltIn -> true
            is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider -> true
            null -> false
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    // 获取当前助手的searchMode
    val currentSearchMode = settings.map { settings ->
        val assistant = settings.assistants.find { it.id == settings.assistantId }
        assistant?.searchMode ?: me.rerere.rikkahub.data.model.AssistantSearchMode.Off
    }.stateIn(viewModelScope, SharingStarted.Lazily, me.rerere.rikkahub.data.model.AssistantSearchMode.Off)
    
    // 更新当前助手的searchMode
    fun updateAssistantSearchMode(searchMode: me.rerere.rikkahub.data.model.AssistantSearchMode) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                val assistantId = settings.assistantId
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistantId) {
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
            settings.map { it.assistantId }.distinctUntilChanged(),
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
    val currentChatModel = settings.map { settings ->
        settings.getCurrentChatModel()
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

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
        }
    }

    // 检查用户头像删除
    private suspend fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar

        if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
            context.deleteChatFiles(listOf(oldAvatar.url.toUri()))
        }
    }

    // 设置聊天模型
    fun setChatModel(assistant: Assistant, model: Model) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            it.copy(
                                chatModelId = model.id
                            )
                        } else {
                            it
                        }
                    })
            }
        }
    }

    // Update checker
    val updateState =
        updateChecker.checkUpdate().stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     * @param isTemporaryChat 是否为临时对话（不保存历史、不使用记忆）
     */
    fun handleMessageSend(content: List<UIMessagePart>, answer: Boolean = true, isTemporaryChat: Boolean = false) {
        if (content.isEmptyInputMessage()) return

        val assistant = settings.value.assistants.find { it.id == settings.value.assistantId }
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

        chatService.sendMessage(_conversationId, processedContent, answer, isTemporaryChat)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return

        val assistant = settings.value.assistants.find { it.id == settings.value.assistantId }
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

        val newConversation = conversation.value.copy(
            messageNodes = conversation.value.messageNodes.map { node ->
                if (!node.messages.any { it.id == messageId }) {
                    return@map node // 如果这个node没有这个消息，则不修改
                }
                val originalMessage = node.messages.find { it.id == messageId }
                node.copy(
                    messages = node.messages + UIMessage(
                        role = node.role,
                        parts = processedParts,
                        versionTag = originalMessage?.versionTag,  // Preserve versionTag for filtering
                    ), selectIndex = node.messages.size
                )
            },
        )
        // Use updateConversation to immediately update UI state, then save async
        updateConversation(newConversation)
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
        val node = conversation.value.getMessageNodeByMessage(message)
        val nodes = conversation.value.messageNodes.subList(
            0, conversation.value.messageNodes.indexOf(node) + 1
        ).map { messageNode ->
            messageNode.copy(
                messages = messageNode.messages.map { msg ->
                    msg.copy(
                        parts = msg.parts.map { part ->
                            when (part) {
                                is UIMessagePart.Image -> {
                                    val url = part.url
                                    if (url.startsWith("file:")) {
                                        val copied = context.createChatFilesByContents(
                                            listOf(url.toUri())
                                        ).firstOrNull()
                                        if (copied != null) part.copy(url = copied.toString()) else part
                                    } else part
                                }

                                is UIMessagePart.Document -> {
                                    val url = part.url
                                    if (url.startsWith("file:")) {
                                        val copied = context.createChatFilesByContents(
                                            listOf(url.toUri())
                                        ).firstOrNull()
                                        if (copied != null) part.copy(url = copied.toString()) else part
                                    } else part
                                }

                                is UIMessagePart.Video -> {
                                    val url = part.url
                                    if (url.startsWith("file:")) {
                                        val copied = context.createChatFilesByContents(
                                            listOf(url.toUri())
                                        ).firstOrNull()
                                        if (copied != null) part.copy(url = copied.toString()) else part
                                    } else part
                                }

                                is UIMessagePart.Audio -> {
                                    val url = part.url
                                    if (url.startsWith("file:")) {
                                        val copied = context.createChatFilesByContents(
                                            listOf(url.toUri())
                                        ).firstOrNull()
                                        if (copied != null) part.copy(url = copied.toString()) else part
                                    } else part
                                }

                                else -> part
                            }
                        }
                    )
                }
            )
        }
        val newConversation = Conversation(
            id = Uuid.random(),
            assistantId = settings.value.assistantId,
            messageNodes = nodes
        )
        chatService.saveConversation(newConversation.id, newConversation)
        return newConversation
    }

    fun deleteMessage(message: UIMessage) {
        val relatedMessages = collectRelatedMessages(message)
        deleteMessageInternal(message)
        relatedMessages.forEach { deleteMessageInternal(it) }
        saveConversationAsync()
    }

    private fun deleteMessageInternal(message: UIMessage) {
        val conversation = conversation.value
        // Use ID-based lookup instead of object equality to avoid issues after recomposition
        val node = conversation.getMessageNodeByMessageId(message.id) ?: return
        val nodeIndex = conversation.messageNodes.indexOf(node)
        if (nodeIndex == -1) return

        // Get the versionTag from the message being deleted - we'll delete all matching versions
        val deleteVersionTag = message.versionTag

        // Limit version-tag deletion to the current turn only.
        // A versionTag can be propagated during streaming, so deleting globally can wipe
        // later turns and even collapse the whole conversation unexpectedly.
        val turnStartIndex = conversation.messageNodes
            .subList(0, nodeIndex + 1)
            .indexOfLast { it.role == me.rerere.ai.core.MessageRole.USER } + 1
        val turnEndIndex = conversation.messageNodes
            .subList(nodeIndex, conversation.messageNodes.size)
            .indexOfFirst { it.role == me.rerere.ai.core.MessageRole.USER }
            .let { if (it == -1) conversation.messageNodes.size else nodeIndex + it }

        val newConversation = if (node.messages.size == 1 && deleteVersionTag == null) {
            // Single message node without versionTag - just remove the node
            conversation.copy(
                messageNodes = conversation.messageNodes.filterIndexed { index, _ -> index != nodeIndex })
        } else {
            // Delete message(s) by ID or versionTag
            val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, n ->
                // Only delete by versionTag inside the current turn. Outside the turn,
                // preserve all versions and only match by explicit message ID.
                val canDeleteByVersionTag =
                    deleteVersionTag != null &&
                        index in turnStartIndex until turnEndIndex &&
                        n.role != me.rerere.ai.core.MessageRole.USER

                val newMessages = n.messages.filter { msg ->
                    // Keep messages that don't match the delete criteria
                    if (canDeleteByVersionTag && msg.versionTag == deleteVersionTag) {
                        false // Delete all messages with this versionTag
                    } else {
                        msg.id != message.id // Also delete the original message by ID
                    }
                }
                if (newMessages.isEmpty()) {
                    null // Remove node entirely if no messages left
                } else {
                    val newSelectIndex = if (n.selectIndex >= newMessages.size) {
                        newMessages.lastIndex
                    } else {
                        n.selectIndex
                    }
                    n.copy(
                        messages = newMessages,
                        selectIndex = newSelectIndex
                    )
                }
            }
            conversation.copy(messageNodes = updatedNodes)
        }
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, newConversation)
        }
    }

    private fun collectRelatedMessages(message: UIMessage): List<UIMessage> {
        val currentMessages = conversation.value.currentMessages
        // Use ID-based lookup instead of object equality
        val index = currentMessages.indexOfFirst { it.id == message.id }
        if (index == -1) return emptyList()

        val relatedMessages = hashSetOf<UIMessage>()
        for (i in index - 1 downTo 0) {
            if (currentMessages[i].hasPart<UIMessagePart.ToolCall>() || currentMessages[i].hasPart<UIMessagePart.ToolResult>()) {
                relatedMessages.add(currentMessages[i])
            } else {
                break
            }
        }
        for (i in index + 1 until currentMessages.size) {
            if (currentMessages[i].hasPart<UIMessagePart.ToolCall>() || currentMessages[i].hasPart<UIMessagePart.ToolResult>()) {
                relatedMessages.add(currentMessages[i])
            } else {
                break
            }
        }
        return relatedMessages.toList()
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
            chatService.saveConversation(_conversationId, updatedConversation)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        chatService.deleteConversation(conversation)
    }

    fun undoDeleteConversation(conversationId: Uuid) {
        chatService.undoDeleteConversation(conversationId)
    }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.togglePinStatus(conversation.id)
        }
    }

    fun updateConversationTitle(conversation: Conversation, title: String) {
        viewModelScope.launch {
            conversationRepo.updateConversation(conversation.copy(title = title))
        }
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch
            chatService.generateTitle(_conversationId, conversationFull, force)
        }
    }

    fun consolidateConversation(conversation: Conversation) {
        viewModelScope.launch {
            // Mark conversation as not consolidated so it will be picked up by the worker
            conversationRepo.markAsNotConsolidated(conversation.id)
            
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
            chatService.generateSuggestion(_conversationId, conversation)
        }
    }

    fun updateConversation(newConversation: Conversation) {
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, newConversation)
        }
    }

    fun deleteFile(uri: Uri) {
        appScope.launch {
            context.deleteChatFiles(listOf(uri))
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
