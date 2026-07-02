package me.rerere.rikkahub.service

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.StringRes
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.utils.LogUtil
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.ai.ui.truncate
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.buildSuggestionGenerationParams
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.ai.buildTitleGenerationParams
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.prompts.buildSuggestionPromptContent
import me.rerere.rikkahub.data.ai.prompts.parseSuggestionLines
import me.rerere.rikkahub.data.ai.shouldUseBuiltInSearch
import me.rerere.rikkahub.data.ai.tools.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.AskUserAnswerPayload
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.ai.tools.normalizeAskUserAnswerPayload
import me.rerere.rikkahub.data.ai.tools.parseAskUserQuestionnaire
import me.rerere.rikkahub.data.ai.tools.parseJsonElementWithRecovery
import me.rerere.rikkahub.data.ai.tools.toJsonElement
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.resolveConversationContext
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.shouldAutoSummarizeMessages
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.appLocale
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.search.SearchService
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.search.SearchServiceOptions
import java.time.Instant
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatService"
private const val STREAMING_CHECKPOINT_INTERVAL_MS = 1_000L
private const val AUTO_RESUME_MAX_RETRIES = 3
private const val AUTO_RESUME_RETRY_DELAY_MS = 700L

internal fun shouldPreserveInMemoryConversation(
    conversation: Conversation?,
    persistenceMode: ChatPersistenceMode,
): Boolean {
    return conversation != null &&
        (
            conversation.messageNodes.isNotEmpty() ||
                persistenceMode != ChatPersistenceMode.NORMAL
            )
}

internal fun normalizeConversation(conversation: Conversation): Conversation {
    val sanitizedNodes = conversation.messageNodes.mapNotNull { node ->
        val messages = node.messages.filterNot { message -> message.isEmptyOcrPlaceholder() }
        if (messages.isEmpty()) {
            null
        } else {
            val selectedId = node.messages.getOrNull(node.selectIndex)?.id
            val selectedIndex = selectedId
                ?.let { id -> messages.indexOfFirst { message -> message.id == id } }
                ?.takeIf { it >= 0 }
                ?: node.selectIndex.coerceIn(0, messages.lastIndex)
            node.copy(
                messages = messages,
                selectIndex = selectedIndex,
            )
        }
    }

    if (sanitizedNodes.isEmpty()) {
        return conversation.copy(messageNodes = emptyList())
    }

    val normalizedNodes = sanitizedNodes.toMutableList()
    var index = 0
    while (index < normalizedNodes.size) {
        if (normalizedNodes[index].role == MessageRole.USER) {
            index++
            continue
        }

        val turnStart = index
        while (index < normalizedNodes.size && normalizedNodes[index].role != MessageRole.USER) {
            index++
        }
        repairAssistantTurnSelections(normalizedNodes, turnStart, index)
    }

    return conversation.copy(messageNodes = normalizedNodes)
}

private fun UIMessage.isEmptyOcrPlaceholder(): Boolean {
    return role == MessageRole.ASSISTANT &&
        parts.isEmpty() &&
        annotations.isNotEmpty() &&
        annotations.all { annotation -> annotation is UIMessageAnnotation.OcrActivity }
}

internal fun selectConversationTurnVersion(
    conversation: Conversation,
    nodeId: Uuid,
    selectIndex: Int,
): Conversation {
    val normalizedConversation = normalizeConversation(conversation)
    val nodeIndex = normalizedConversation.messageNodes.indexOfFirst { it.id == nodeId }
    if (nodeIndex == -1) {
        return normalizedConversation
    }

    val node = normalizedConversation.messageNodes[nodeIndex]
    val clampedSelectIndex = selectIndex.coerceIn(0, node.messages.lastIndex)
    if (node.role == MessageRole.USER) {
        return normalizedConversation.copy(
            messageNodes = normalizedConversation.messageNodes.mapIndexed { index, current ->
                if (index == nodeIndex) {
                    current.copy(selectIndex = clampedSelectIndex)
                } else {
                    current
                }
            }
        )
    }

    val targetTag = node.messages[clampedSelectIndex].versionTag
    val turnStartIndex = normalizedConversation.messageNodes
        .subList(0, nodeIndex + 1)
        .indexOfLast { it.role == MessageRole.USER } + 1
    val turnEndIndex = normalizedConversation.messageNodes
        .subList(nodeIndex, normalizedConversation.messageNodes.size)
        .indexOfFirst { it.role == MessageRole.USER }
        .let { if (it == -1) normalizedConversation.messageNodes.size else nodeIndex + it }
    val versionDelta = clampedSelectIndex - node.selectIndex

    val updatedNodes = normalizedConversation.messageNodes.mapIndexed { index, current ->
        when {
            index == nodeIndex -> current.copy(selectIndex = clampedSelectIndex)
            index in turnStartIndex until turnEndIndex && current.role != MessageRole.USER -> {
                val matchingIndex = current.messages.indexOfLast { it.versionTag == targetTag }
                val fallbackIndex = if (matchingIndex >= 0) {
                    matchingIndex
                } else {
                    (current.selectIndex + versionDelta).coerceIn(0, current.messages.lastIndex)
                }
                current.copy(selectIndex = fallbackIndex)
            }

            else -> current
        }
    }

    return normalizeConversation(
        normalizedConversation.copy(messageNodes = updatedNodes)
    )
}

internal suspend fun buildForkConversationSnapshot(
    conversation: Conversation,
    messageId: Uuid,
    copyAttachmentUrl: suspend (String) -> String,
    newConversationId: Uuid = Uuid.random(),
    now: Instant = Instant.now(),
): Conversation? {
    val targetNode = conversation.getMessageNodeByMessageId(messageId) ?: return null
    val targetIndex = conversation.messageNodes.indexOf(targetNode)
    if (targetIndex < 0) {
        return null
    }

    val copiedNodes = conversation.messageNodes
        .subList(0, targetIndex + 1)
        .map { node ->
            node.copy(
                messages = node.messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            when (part) {
                                is UIMessagePart.Image -> part.copy(url = copyAttachmentUrl(part.url))
                                is UIMessagePart.Document -> part.copy(url = copyAttachmentUrl(part.url))
                                is UIMessagePart.Video -> part.copy(url = copyAttachmentUrl(part.url))
                                is UIMessagePart.Audio -> part.copy(url = copyAttachmentUrl(part.url))
                                else -> part
                            }
                        }
                    )
                }
            )
        }

    return normalizeConversation(
        conversation.copy(
            id = newConversationId,
            messageNodes = copiedNodes,
            createAt = now,
            updateAt = now,
            isFork = true,
        )
    )
}

private fun repairAssistantTurnSelections(
    nodes: MutableList<MessageNode>,
    turnStart: Int,
    turnEndExclusive: Int,
) {
    if (turnStart !in nodes.indices || turnEndExclusive <= turnStart) return

    val turnNodes = nodes.subList(turnStart, turnEndExclusive)
    val selectedTag = chooseAssistantTurnVersionTag(turnNodes)
    for (index in turnStart until turnEndExclusive) {
        val node = nodes[index]
        nodes[index] = node.copy(selectIndex = findBestMessageIndex(node, selectedTag))
    }
}

private fun chooseAssistantTurnVersionTag(turnNodes: List<MessageNode>): String? {
    if (turnNodes.isEmpty()) return null
    
    // The first node in the turn acts as the source of truth for the turn's active version.
    // If it has a tag, we enforce that tag across all sibling nodes in this turn.
    // If it has a null tag (e.g. from an older generation or a standard model that didn't output a tag),
    // we must STILL treat 'null' as the source of truth to avoid forcefully switching back to a newer version
    // during fallback scoring.
    val firstNode = turnNodes.first()
    if (firstNode.messages.isEmpty()) {
        return null
    }

    return firstNode.currentMessage.versionTag
}

private fun findBestMessageIndex(node: MessageNode, versionTag: String?): Int {
    val clampedIndex = node.selectIndex.coerceIn(0, node.messages.lastIndex)
    if (node.messages[clampedIndex].versionTag == versionTag) {
        return clampedIndex
    }

    val matchingIndex = node.messages.indexOfLast { it.versionTag == versionTag }
    if (matchingIndex >= 0) {
        return matchingIndex
    }

    if (versionTag == null) {
        val legacyIndex = node.messages.indexOfLast { it.versionTag == null }
        if (legacyIndex >= 0) {
            return legacyIndex
        }
    }

    return clampedIndex
}

internal fun Conversation.hasPendingToolApprovals(): Boolean {
    return currentMessages.any { message ->
        message.getToolCalls().any { it.approvalState is ToolApprovalState.Pending }
    }
}

internal fun UIMessage.hasDurableAssistantProgress(): Boolean {
    if (role != MessageRole.ASSISTANT) return false

    return parts.any { part ->
        when (part) {
            is UIMessagePart.Text -> part.text.isNotBlank()
            is UIMessagePart.Reasoning -> part.reasoning.isNotBlank()
            is UIMessagePart.Thinking -> part.thinking.isNotBlank()
            is UIMessagePart.Image -> part.url.isNotBlank()
            is UIMessagePart.Document -> part.url.isNotBlank()
            is UIMessagePart.Video -> part.url.isNotBlank()
            is UIMessagePart.Audio -> part.url.isNotBlank()
            is UIMessagePart.ToolCall -> part.toolName.isNotBlank() || part.arguments.isNotBlank()
            is UIMessagePart.ToolResult -> true
            else -> false
        }
    }
}

internal fun UIMessage.needsAssistantReplyResume(): Boolean {
    if (role != MessageRole.ASSISTANT) return false

    val hasToolCall = parts.any { part -> part is UIMessagePart.ToolCall }
    val hasReasoningProgress = parts.any { part ->
        when (part) {
            is UIMessagePart.Reasoning -> part.reasoning.isNotBlank()
            is UIMessagePart.Thinking -> part.thinking.isNotBlank()
            else -> false
        }
    }

    return hasReasoningProgress && !hasVisibleAssistantReply() && !hasToolCall
}

private fun UIMessage.hasVisibleAssistantReply(): Boolean {
    if (role != MessageRole.ASSISTANT) return false

    return parts.any { part ->
        when (part) {
            is UIMessagePart.Text -> part.text.isNotBlank()
            is UIMessagePart.Image -> part.url.isNotBlank()
            is UIMessagePart.Document -> part.url.isNotBlank()
            is UIMessagePart.Video -> part.url.isNotBlank()
            is UIMessagePart.Audio -> part.url.isNotBlank()
            else -> false
        }
    }
}

internal fun Conversation.needsAssistantReplyAfterToolResult(): Boolean {
    if (hasPendingToolApprovals()) return false

    val selectedMessages = currentMessages
    val lastMessage = selectedMessages.lastOrNull() ?: return false
    if (lastMessage.role == MessageRole.TOOL) {
        return lastMessage.getToolResults().isNotEmpty()
    }

    if (lastMessage.role != MessageRole.ASSISTANT) return false
    if (lastMessage.hasVisibleAssistantReply()) return false
    if (lastMessage.getToolCalls().isNotEmpty()) return false

    val previousMessage = selectedMessages.dropLast(1).lastOrNull() ?: return false
    return previousMessage.role == MessageRole.TOOL &&
        previousMessage.getToolResults().isNotEmpty()
}

internal fun Conversation.canAutoResumeAssistantReply(): Boolean {
    if (hasPendingToolApprovals()) return false
    val lastMessage = currentMessages.lastOrNull() ?: return false
    return lastMessage.role == MessageRole.ASSISTANT && lastMessage.hasDurableAssistantProgress()
}

internal fun shouldPersistStreamingCheckpoint(
    conversation: Conversation,
    nowMs: Long,
    lastPersistMs: Long,
): Boolean {
    if (lastPersistMs > 0L && nowMs - lastPersistMs < STREAMING_CHECKPOINT_INTERVAL_MS) {
        return false
    }
    return conversation.currentMessages.lastOrNull()?.hasDurableAssistantProgress() == true
}

internal fun mergeLiveMessagesIfIncomingIsStale(
    liveConversation: Conversation?,
    incomingConversation: Conversation,
): Conversation {
    if (liveConversation == null) return incomingConversation
    if (liveConversation.id != incomingConversation.id) return incomingConversation
    if (!incomingConversation.updateAt.isBefore(liveConversation.updateAt)) return incomingConversation
    if (!incomingConversation.losesDurableAssistantProgressFrom(liveConversation)) return incomingConversation

    return liveConversation.copy(
        assistantId = incomingConversation.assistantId,
        title = incomingConversation.title,
        truncateIndex = incomingConversation.truncateIndex,
        chatSuggestions = incomingConversation.chatSuggestions,
        isPinned = incomingConversation.isPinned,
        enabledModeIds = incomingConversation.enabledModeIds,
        enabledLorebookIds = incomingConversation.enabledLorebookIds,
        updateAt = liveConversation.updateAt,
        isConsolidated = incomingConversation.isConsolidated,
        contextSummary = incomingConversation.contextSummary,
        contextSummaryUpToIndex = incomingConversation.contextSummaryUpToIndex,
        lastPruneTime = incomingConversation.lastPruneTime,
        lastPruneMessageCount = incomingConversation.lastPruneMessageCount,
        lastRefreshTime = incomingConversation.lastRefreshTime,
        isFork = incomingConversation.isFork,
    )
}

private fun Conversation.losesDurableAssistantProgressFrom(liveConversation: Conversation): Boolean {
    val incomingMessagesById = currentMessages.associateBy { it.id }
    return liveConversation.currentMessages.any { liveMessage ->
        liveMessage.role == MessageRole.ASSISTANT &&
            liveMessage.hasDurableAssistantProgress() &&
            incomingMessagesById[liveMessage.id]?.hasAtLeastAssistantProgressOf(liveMessage) != true
    }
}

private fun UIMessage.hasAtLeastAssistantProgressOf(liveMessage: UIMessage): Boolean {
    if (role != liveMessage.role) return false
    if (!hasDurableAssistantProgress()) return false
    return assistantProgressScore() >= liveMessage.assistantProgressScore()
}

private fun UIMessage.assistantProgressScore(): Int {
    return parts.sumOf { part ->
        when (part) {
            is UIMessagePart.Text -> part.text.length
            is UIMessagePart.Reasoning -> part.reasoning.length
            is UIMessagePart.Thinking -> part.thinking.length
            is UIMessagePart.Image -> part.url.length
            is UIMessagePart.Document -> part.url.length + part.fileName.length
            is UIMessagePart.Video -> part.url.length
            is UIMessagePart.Audio -> part.url.length
            is UIMessagePart.ToolCall -> part.toolCallId.length + part.toolName.length + part.arguments.length
            is UIMessagePart.ToolResult -> part.toolCallId.length + part.toolName.length + part.content.toString().length
            else -> 0
        }
    }
}

internal fun dropDanglingAutoToolCallNodes(messageNodes: List<MessageNode>): List<MessageNode> {
    return messageNodes.mapIndexed { index, node ->
        val toolCalls = node.currentMessage.getToolCalls()
        val nextMessage = messageNodes.getOrNull(index + 1)?.currentMessage
        val hasFollowingToolResult = nextMessage?.hasPart<UIMessagePart.ToolResult>() == true
        val shouldDropCurrentMessage = toolCalls.isNotEmpty() &&
            toolCalls.all { toolCall -> toolCall.approvalState is ToolApprovalState.Auto } &&
            !hasFollowingToolResult

        if (!shouldDropCurrentMessage) {
            node
        } else {
            node.copy(
                messages = node.messages.filter { message -> message.id != node.currentMessage.id },
                selectIndex = node.selectIndex - 1,
            )
        }
    }
}

private fun Conversation.findCurrentToolCall(toolCallId: String): UIMessagePart.ToolCall? {
    return currentMessages
        .flatMap { it.getToolCalls() }
        .firstOrNull { it.toolCallId == toolCallId }
}

private fun Conversation.removeTrailingEmptyOcrPlaceholder(): Conversation {
    val lastNode = messageNodes.lastOrNull() ?: return this
    val lastMessage = lastNode.currentMessage
    val isOcrOnlyPlaceholder = lastMessage.role == MessageRole.ASSISTANT &&
        lastMessage.parts.isEmpty() &&
        lastMessage.annotations.isNotEmpty() &&
        lastMessage.annotations.all { annotation ->
            annotation is UIMessageAnnotation.OcrActivity
        }

    if (!isOcrOnlyPlaceholder) {
        return this
    }

    return copy(
        messageNodes = messageNodes.dropLast(1),
        updateAt = Instant.now(),
    )
}

private fun Conversation.updateToolApprovalState(
    toolCallId: String,
    approvalState: ToolApprovalState,
): Conversation {
    val updatedNodes = messageNodes.map { node ->
        val updatedMessages = node.messages.map { message ->
            val updatedParts = message.parts.map { part ->
                if (part is UIMessagePart.ToolCall && part.toolCallId == toolCallId) {
                    part.copy(approvalState = approvalState)
                } else {
                    part
                }
            }
            if (updatedParts == message.parts) {
                message
            } else {
                message.copy(parts = updatedParts)
            }
        }
        if (updatedMessages == node.messages) {
            node
        } else {
            node.copy(messages = updatedMessages)
        }
    }
    return copy(
        messageNodes = updatedNodes,
        updateAt = Instant.now(),
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatAttachmentRepository: ChatAttachmentRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    private val workspaceRepository: WorkspaceRepository,
    val mcpManager: McpManager,
) {
    // 存储每个对话的状态
    private val conversationsLock = Any()
    private val conversations = mutableMapOf<Uuid, MutableStateFlow<Conversation>>()

    // 记录哪些conversation有VM引用
    private val conversationReferencesLock = Any()
    private val conversationReferences = mutableMapOf<Uuid, Int>()

    // 记录哪些对话是临时对话（不持久化、不使用记忆）
    private val _conversationPersistenceModes = MutableStateFlow<Map<Uuid, ChatPersistenceMode>>(emptyMap())
    private val conversationPersistenceModes: StateFlow<Map<Uuid, ChatPersistenceMode>> =
        _conversationPersistenceModes.asStateFlow()

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
    private fun getConversationState(conversationId: Uuid): MutableStateFlow<Conversation>? =
        synchronized(conversationsLock) {
            conversations[conversationId]
        }

    private fun getOrCreateConversationState(
        conversationId: Uuid,
        initialConversation: () -> Conversation,
    ): MutableStateFlow<Conversation> = synchronized(conversationsLock) {
        conversations.getOrPut(conversationId) {
            MutableStateFlow(initialConversation())
        }
    }

    private fun conversationIdsSnapshot(): List<Uuid> = synchronized(conversationsLock) {
        conversations.keys.toList()
    }

    private fun removeConversationState(conversationId: Uuid) = synchronized(conversationsLock) {
        conversations.remove(conversationId)
    }

    private fun conversationReferenceCount(): Int = synchronized(conversationReferencesLock) {
        conversationReferences.size
    }

    private fun hasConversationReference(conversationId: Uuid): Boolean =
        synchronized(conversationReferencesLock) {
            conversationReferences.containsKey(conversationId)
        }

    fun addConversationReference(conversationId: Uuid) {
        val referenceCount = synchronized(conversationReferencesLock) {
            val nextCount = conversationReferences.getOrDefault(conversationId, 0) + 1
            conversationReferences[conversationId] = nextCount
            nextCount
        }
        LogUtil.d(
            TAG,
            "Added reference for $conversationId (current references: $referenceCount)"
        )
    }

    // 移除引用
    fun removeConversationReference(conversationId: Uuid) {
        val referenceCount = synchronized(conversationReferencesLock) {
            conversationReferences[conversationId]?.let { count ->
                if (count > 1) {
                    val nextCount = count - 1
                    conversationReferences[conversationId] = nextCount
                    nextCount
                } else {
                    conversationReferences.remove(conversationId)
                    0
                }
            } ?: 0
        }
        LogUtil.d(
            TAG,
            "Removed reference for $conversationId (current references: $referenceCount)"
        )
        appScope.launch {
            delay(500)
            checkAllConversationsReferences()
        }
    }

    // 检查是否有引用
    private fun hasReference(conversationId: Uuid): Boolean {
        return hasConversationReference(conversationId) || _generationJobs.value.containsKey(
            conversationId
        )
    }

    // 检查所有conversation的引用情况（生成结束后调用）
    fun checkAllConversationsReferences() {
        conversationIdsSnapshot().forEach { conversationId ->
            if (!hasReference(conversationId)) {
                cleanupConversation(conversationId)
            }
        }
    }

    // 获取对话的StateFlow
    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        val settings = settingsStore.settingsFlow.value
        return getOrCreateConversationState(conversationId) {
            Conversation.ofId(
                id = conversationId,
                assistantId = settings.getCurrentAssistant().id
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

    fun getConversationPersistenceModeFlow(conversationId: Uuid): Flow<ChatPersistenceMode> {
        return conversationPersistenceModes.map { modes ->
            modes[conversationId] ?: ChatPersistenceMode.NORMAL
        }
    }

    fun getConversationPersistenceMode(conversationId: Uuid): ChatPersistenceMode {
        return conversationPersistenceModes.value[conversationId] ?: ChatPersistenceMode.NORMAL
    }

    fun ensureConversationPersistenceMode(conversationId: Uuid, mode: ChatPersistenceMode) {
        val currentMode = getConversationPersistenceMode(conversationId)
        if (currentMode == ChatPersistenceMode.NORMAL && mode != ChatPersistenceMode.NORMAL) {
            setConversationPersistenceMode(conversationId, mode)
        }
    }

    private fun setConversationPersistenceMode(conversationId: Uuid, mode: ChatPersistenceMode) {
        _conversationPersistenceModes.value = _conversationPersistenceModes.value.toMutableMap().apply {
            if (mode == ChatPersistenceMode.NORMAL) {
                remove(conversationId)
            } else {
                this[conversationId] = mode
            }
        }.toMap()
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
        val inMemoryConversation = getConversationState(conversationId)?.value
        if (shouldPreserveInMemoryConversation(
                conversation = inMemoryConversation,
                persistenceMode = getConversationPersistenceMode(conversationId),
            )
        ) {
            val preservedConversation = inMemoryConversation ?: return
            updateConversation(conversationId, preservedConversation)
            return
        }

        val conversation = withContext(Dispatchers.IO) {
            conversationRepo.getConversationById(conversationId)
        }?.let { loadedConversation ->
            withContext(Dispatchers.IO) {
                chatAttachmentRepository.syncConversationAttachments(loadedConversation)
            }
        }
        if (conversation != null) {
            updateConversation(conversationId, conversation)
        } else {
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
            ).updateCurrentMessages(assistant.presetMessages)
            setConversationPersistenceMode(conversationId, ChatPersistenceMode.PERSIST_ON_REPLY)
            updateConversation(conversationId, newConversation)
        }
    }

    suspend fun createConversation(assistantId: Uuid): Conversation {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(assistantId) ?: settings.getCurrentAssistant()
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            assistantId = assistant.id,
        ).updateCurrentMessages(assistant.presetMessages)
        setConversationPersistenceMode(conversation.id, ChatPersistenceMode.PERSIST_ON_REPLY)
        saveConversation(conversation.id, conversation)
        return conversation
    }

    suspend fun seedSpontaneousDraftConversation(
        assistantId: Uuid,
        content: String,
        conversationId: Uuid = Uuid.random(),
    ): Conversation {
        val trimmedContent = content.trim()
        require(trimmedContent.isNotBlank()) { "Spontaneous message content cannot be blank" }

        val conversation = Conversation.ofId(
            id = conversationId,
            assistantId = assistantId,
            messages = listOf(MessageNode.of(UIMessage.assistant(trimmedContent))),
        )
        setConversationPersistenceMode(conversationId, ChatPersistenceMode.PERSIST_ON_REPLY)
        updateConversation(conversationId, conversation)
        return conversation
    }

    suspend fun persistSpontaneousAssistantMessage(
        assistantId: Uuid,
        content: String,
        conversationId: Uuid? = null,
    ): Conversation {
        val trimmedContent = content.trim()
        require(trimmedContent.isNotBlank()) { "Spontaneous message content cannot be blank" }

        val existingConversation = conversationId?.let { ensureConversationLoaded(it) }
        val assistantMessage = MessageNode.of(
            message = UIMessage.assistant(trimmedContent),
            forceTurnBreakBefore = existingConversation != null,
        )

        val conversation = if (existingConversation != null) {
            existingConversation.copy(
                messageNodes = existingConversation.messageNodes + assistantMessage,
                updateAt = Instant.now(),
            )
        } else {
            Conversation.ofId(
                id = conversationId ?: Uuid.random(),
                assistantId = assistantId,
                messages = listOf(assistantMessage),
            )
        }

        saveConversation(conversation.id, conversation)
        return conversation
    }

    private suspend fun persistConversationToRepository(
        conversation: Conversation,
        preserveConsolidation: Boolean = false,
    ): Boolean {
        val normalizedConversation = normalizeConversation(conversation)
        if (normalizedConversation.title.isBlank() && normalizedConversation.messageNodes.isEmpty()) return false

        val retryDelaysMs = longArrayOf(40L, 120L, 240L)
        repeat(retryDelaysMs.size + 1) { attempt ->
            try {
                withContext(Dispatchers.IO) {
                    if (conversationRepo.getConversationById(normalizedConversation.id) == null) {
                        conversationRepo.insertConversation(normalizedConversation)
                    } else {
                        conversationRepo.updateConversation(
                            conversation = normalizedConversation,
                            preserveConsolidation = preserveConsolidation,
                        )
                    }
                }
                return true
            } catch (e: Exception) {
                if (attempt == retryDelaysMs.size) {
                    e.printStackTrace()
                    return false
                }
                delay(retryDelaysMs[attempt])
            }
        }
        return false
    }

    suspend fun getConversationSnapshot(conversationId: Uuid): Conversation? {
        val currentState = getConversationState(conversationId)?.value
        return currentState ?: withContext(Dispatchers.IO) {
            conversationRepo.getConversationById(conversationId)
        }?.let(::normalizeConversation)
    }

    suspend fun ensureConversationLoaded(conversationId: Uuid): Conversation? {
        val currentState = getConversationState(conversationId)?.value
        if (currentState != null) return currentState

        val persistedConversation = withContext(Dispatchers.IO) {
            conversationRepo.getConversationById(conversationId)
        } ?: return null
        val normalizedConversation = normalizeConversation(persistedConversation)
        updateConversation(conversationId, normalizedConversation)
        return normalizedConversation
    }

    fun stopGeneration(conversationId: Uuid) {
        getGenerationJob(conversationId)?.cancel()
    }

    fun isGenerating(conversationId: Uuid): Boolean {
        return getGenerationJob(conversationId)?.isActive == true
    }

    // 发送消息
    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>,
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = ensureConversationLoaded(conversationId) ?: return
        val assistant = settingsStore.settingsFlow.value.getAssistantById(currentConversation.assistantId)
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

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.map { node ->
                val originalMessage = node.messages.find { it.id == messageId } ?: return@map node
                node.copy(
                    messages = node.messages + UIMessage(
                        role = originalMessage.role,
                        parts = processedParts,
                        versionTag = originalMessage.versionTag,
                    ),
                    selectIndex = node.messages.size,
                )
            },
            updateAt = Instant.now(),
        )

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid,
    ): Conversation {
        val currentConversation = ensureConversationLoaded(conversationId)
            ?: return Conversation.ofId(Uuid.random())
        val forkConversation = withContext(Dispatchers.IO) {
            buildForkConversationSnapshot(
                conversation = currentConversation,
                messageId = messageId,
                copyAttachmentUrl = ::copyAttachmentUrl,
            )
        } ?: return Conversation.ofId(Uuid.random())
        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
    ) {
        val currentConversation = ensureConversationLoaded(conversationId) ?: return
        val message = currentConversation.messageNodes
            .flatMap { it.messages }
            .firstOrNull { it.id == messageId }
            ?: return

        val updatedConversation = if (message.role == MessageRole.USER) {
            // User message: delete this message and ALL messages after it
            val node = currentConversation.getMessageNodeByMessageId(messageId) ?: return
            val nodeIndex = currentConversation.messageNodes.indexOf(node)
            if (nodeIndex == -1) return

            if (node.messages.size > 1) {
                // Multi-version node: remove just this version and truncate everything after
                val remainingMessages = node.messages.filter { it.id != messageId }
                val updatedNode = node.copy(
                    messages = remainingMessages,
                    selectIndex = if (node.selectIndex >= remainingMessages.size) {
                        remainingMessages.lastIndex
                    } else {
                        node.selectIndex
                    }
                )
                currentConversation.copy(
                    messageNodes = currentConversation.messageNodes.subList(0, nodeIndex) +
                        listOf(updatedNode)
                )
            } else {
                // Single-version node: truncate everything from this node onward
                currentConversation.copy(
                    messageNodes = currentConversation.messageNodes.subList(0, nodeIndex)
                )
            }
        } else {
            // Assistant/Tool message: existing behavior
            val relatedMessages = collectRelatedMessages(currentConversation, message)
            var result = deleteMessageInternal(currentConversation, message)
            relatedMessages.forEach { related ->
                result = deleteMessageInternal(result, related)
            }
            result
        }

        saveConversation(
            conversationId = conversationId,
            conversation = updatedConversation.copy(updateAt = Instant.now()),
        )
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int,
    ) {
        val currentConversation = ensureConversationLoaded(conversationId) ?: return
        val updatedConversation = selectConversationTurnVersion(
            conversation = currentConversation,
            nodeId = nodeId,
            selectIndex = selectIndex,
        ).copy(
            updateAt = Instant.now(),
        )
        saveConversation(conversationId, updatedConversation)
    }

    suspend fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String,
        answer: String?,
    ) {
        getGenerationJob(conversationId)?.cancel()

        val job = appScope.launch {
            try {
                val currentConversation = ensureConversationLoaded(conversationId)
                    ?: error("Conversation not found")
                val pendingToolCall = currentConversation.findCurrentToolCall(toolCallId)
                    ?: error("Tool call $toolCallId not found")
                if (pendingToolCall.approvalState !is ToolApprovalState.Pending) {
                    Log.w(TAG, "Ignoring tool approval for non-pending call: $toolCallId")
                    return@launch
                }

                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.assistants.find { it.id == currentConversation.assistantId }
                    ?: settings.getCurrentAssistant()
                val modelId = assistant.chatModelId ?: settings.chatModelId
                val model = settings.findModelById(modelId)
                    ?: error("Conversation model not found")
                val tools = buildConversationTools(
                    settings = settings,
                    assistant = assistant,
                    conversation = currentConversation,
                    model = model,
                )

                val resolution = createToolApprovalResolution(
                    toolCall = pendingToolCall,
                    approved = approved,
                    reason = reason,
                    answer = answer,
                    tools = tools,
                )

                val updatedConversation = currentConversation
                    .updateToolApprovalState(
                        toolCallId = toolCallId,
                        approvalState = resolution.approvalState,
                    )
                    .let { conversationWithApproval ->
                        conversationWithApproval.updateCurrentMessages(
                            conversationWithApproval.currentMessages + UIMessage(
                            role = MessageRole.TOOL,
                            parts = listOf(resolution.toolResult),
                        )
                        )
                    }

                saveConversation(conversationId, updatedConversation)
                handleMessageComplete(
                    conversationId = conversationId,
                    suppressCompletionNotification = true,
                )

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorFlow.emit(e)
            }
        }

        setGenerationJob(conversationId, job)
        job.invokeOnCompletion {
            setGenerationJob(conversationId, null)
            appScope.launch {
                delay(500)
                checkAllConversationsReferences()
            }
        }
    }

    fun sendMessage(
        conversationId: Uuid,
        content: List<UIMessagePart>,
        answer: Boolean = true,
        persistenceMode: ChatPersistenceMode = ChatPersistenceMode.NORMAL,
        suppressCompletionNotification: Boolean = false,
    ) {
        // 标记为临时对话
        val currentMode = getConversationPersistenceMode(conversationId)
        val effectiveMode = if (persistenceMode == ChatPersistenceMode.NORMAL) {
            currentMode
        } else {
            persistenceMode
        }
        setConversationPersistenceMode(conversationId, effectiveMode)

        // 取消现有的生成任务
        getGenerationJob(conversationId)?.cancel()

        val job = appScope.launch {
            try {
                var effectiveContent = content
                val currentConversation = getConversationFlow(conversationId).value
                val settings = settingsStore.settingsFlow.first()
                val conversationContext = settings.resolveConversationContext(currentConversation)
                val workspaceId = conversationContext.assistant?.workspaceId?.toString()

                if (workspaceId != null) {
                    withContext(Dispatchers.IO) {
                        effectiveContent.forEach { part ->
                            val url = when(part) {
                                is UIMessagePart.Image -> part.url
                                is UIMessagePart.Video -> part.url
                                is UIMessagePart.Audio -> part.url
                                is UIMessagePart.Document -> part.url
                                else -> null
                            }
                            val fileName = when(part) {
                                is UIMessagePart.Document -> part.fileName ?: url?.substringAfterLast("/")?.substringBefore("?")
                                else -> url?.substringAfterLast("/")?.substringBefore("?")
                            }
                            if (url != null && url.startsWith("file://")) {
                                try {
                                    val uri = java.net.URI(url)
                                    val file = java.io.File(uri)
                                    if (file.exists()) {
                                        file.inputStream().use { stream ->
                                            workspaceRepository.importFile(
                                                id = workspaceId,
                                                area = me.rerere.workspace.WorkspaceStorageArea.FILES,
                                                destinationPath = getWorkspaceCwd(
                                                    assistantName = conversationContext.assistant.name,
                                                    chatTitle = currentConversation.title,
                                                    chatId = currentConversation.id.toString()
                                                ).removePrefix("/workspace/").removePrefix("/workspace") + "/uploads",
                                                fileName = fileName ?: file.name,
                                                inputStream = stream
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = effectiveContent,
                    ).toMessageNode(),
                )
                if (effectiveMode == ChatPersistenceMode.PERSIST_ON_REPLY) {
                    updateConversation(conversationId, newConversation)
                    persistConversationToRepository(newConversation)
                    setConversationPersistenceMode(conversationId, ChatPersistenceMode.NORMAL)
                } else {
                    saveConversation(conversationId, newConversation)
                }

                // Record daily activity for the heatmap (persists even if chat is deleted)
                withContext(Dispatchers.IO) {
                    conversationRepo.recordDailyActivity()
                }

                // 开始补全
                if(answer){
                    handleMessageComplete(
                        conversationId = conversationId,
                        suppressCompletionNotification = suppressCompletionNotification,
                    )
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
        forceWipe: Boolean = false,
        suppressCompletionNotification: Boolean = false,
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
                    saveConversation(
                        conversationId = conversationId,
                        conversation = newConversation,
                        preserveConsolidation = true,
                    )
                    handleMessageComplete(
                        conversationId = conversationId,
                        preserveConsolidation = true,
                        suppressCompletionNotification = suppressCompletionNotification,
                    )
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
                                saveConversation(
                                    conversationId = conversationId,
                                    conversation = newConversation,
                                    preserveConsolidation = true,
                                )
                                handleMessageComplete(
                                    conversationId = conversationId,
                                    messageRange = 0..lastUserIndex,
                                    preserveConsolidation = true,
                                    suppressCompletionNotification = suppressCompletionNotification,
                                )
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
                                saveConversation(
                                    conversationId = conversationId,
                                    conversation = newConversation,
                                    preserveConsolidation = true,
                                )
                                handleMessageComplete(
                                    conversationId = conversationId,
                                    messageRange = 0..lastUserIndex,
                                    preserveConsolidation = true,
                                    suppressCompletionNotification = suppressCompletionNotification,
                                )
                            }
                        } else {
                            // No user message found, regenerate from the clicked node
                            handleMessageComplete(
                                conversationId = conversationId,
                                messageRange = 0..clickedIndex, // Ensure we encompass up to clickedIndex 
                                preserveConsolidation = true,
                                suppressCompletionNotification = suppressCompletionNotification,
                            )
                        }
                    } else {
                        saveConversation(
                            conversationId = conversationId,
                            conversation = conversation,
                            preserveConsolidation = true,
                        )
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
        messageRange: ClosedRange<Int>? = null,
        preserveConsolidation: Boolean = false,
        suppressCompletionNotification: Boolean = false,
    ) {
        val settings = settingsStore.settingsFlow.first()

        // Track generation start time for tokens/sec calculation
        // Set on first token arrival to exclude TTFT (time to first token) from the calculation
        var firstTokenTime: Long? = null
        var lastStreamingPersistMs = 0L
        var autoResumeAttempts = 0

        runCatching {
            var conversation = normalizeConversation(getConversationFlow(conversationId).value)
            val conversationContext = settings.resolveConversationContext(conversation)
            val assistant = conversationContext.assistant
            val model = conversationContext.chatModel ?: return

            // reset suggestions
            updateConversation(conversationId, conversation.copy(chatSuggestions = emptyList()))
            conversation = getConversationFlow(conversationId).value

            // Check if model supports tools when external tools are configured
            val tools = buildConversationTools(
                settings = settings,
                assistant = assistant,
                conversation = conversation,
                model = model,
            )
            val hasExternalTools = tools.isNotEmpty()
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (hasExternalTools) {
                    _errorFlow.emit(IllegalStateException(context.getString(R.string.tools_warning)))
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            conversation = getConversationFlow(conversationId).value

            while (true) {
                conversation = getConversationFlow(conversationId).value
                try {
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
                assistant = assistant,
                memories = if (
                    assistant.enableMemory &&
                    getConversationPersistenceMode(conversationId) == ChatPersistenceMode.NORMAL
                ) {
                    if (assistant.useRagMemoryRetrieval) {
                        // RAG mode: retrieve relevant memories based on context
                        val lastUserMessage = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText() ?: ""
                        
                        if (settings.enableRagLogging) {
                            Log.d("RAG", "Query: $lastUserMessage")
                        }

                        if (lastUserMessage.isNotBlank()) {
                            val results = memoryRepository.retrieveRelevantMemories(
                                assistantId = conversation.assistantId.toString(),
                                query = lastUserMessage,
                                limit = if (assistant.ragLimit > 50) 9999 else assistant.ragLimit,
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
                            if (settings.enableRagLogging) Log.d("RAG", "Empty query, using recent memories")
                            memoryRepository.getMemoriesOfAssistant(conversation.assistantId.toString())
                                .take(50)
                        }
                    } else {
                        // Simple mode: inject recent memories
                        memoryRepository.getMemoriesOfAssistant(conversation.assistantId.toString())
                            .take(50)
                    }
                } else {
                    emptyList()
                },
                inputTransformers = buildList {
                    addAll(defaultChatInputTransformers)
                    val cwd = getWorkspaceCwd(
                        assistantName = assistant?.name ?: "",
                        chatTitle = conversation.title,
                        chatId = conversation.id.toString()
                    )
                    add(WorkspaceReminderTransformer(workspaceRepository, cwd))
                    add(templateTransformer)
                },
                outputTransformers = defaultChatOutputTransformers,
                tools = tools,
                truncateIndex = conversation.truncateIndex,
                enabledModeIds = conversation.enabledModeIds,
                enabledLorebookIds = conversation.enabledLorebookIds,
                activeConversationId = conversation.id,
            ).onCompletion { cause ->
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
                val cleanedConversation = updatedConversation.removeTrailingEmptyOcrPlaceholder()
                updateConversation(conversationId, cleanedConversation)
                val completionPersisted = if (getConversationPersistenceMode(conversationId) == ChatPersistenceMode.NORMAL) {
                    persistConversationToRepository(
                        conversation = cleanedConversation,
                        preserveConsolidation = preserveConsolidation,
                    )
                } else {
                    true
                }

                // Show notification if app is not in foreground
                if (
                    cause == null &&
                    completionPersisted &&
                    !cleanedConversation.hasPendingToolApprovals() &&
                    cleanedConversation.currentMessages.lastOrNull()?.hasDurableAssistantProgress() == true &&
                    cleanedConversation.currentMessages.lastOrNull()?.needsAssistantReplyResume() != true &&
                    !suppressCompletionNotification &&
                    !isForeground.value &&
                    settings.displaySetting.enableNotificationOnMessageGeneration
                ) {
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
                            .copy(updateAt = Instant.now())
                        updateConversation(conversationId, updatedConversation)

                        val nowMs = System.currentTimeMillis()
                        if (
                            getConversationPersistenceMode(conversationId) == ChatPersistenceMode.NORMAL &&
                            shouldPersistStreamingCheckpoint(
                                conversation = updatedConversation,
                                nowMs = nowMs,
                                lastPersistMs = lastStreamingPersistMs,
                            )
                        ) {
                            if (persistConversationToRepository(
                                    conversation = updatedConversation,
                                    preserveConsolidation = preserveConsolidation,
                                )
                            ) {
                                lastStreamingPersistMs = nowMs
                            }
                        }
                    }
                }
            }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    val latestConversation = getConversationFlow(conversationId).value
                    if (
                        autoResumeAttempts < AUTO_RESUME_MAX_RETRIES &&
                        latestConversation.canAutoResumeAssistantReply()
                    ) {
                        autoResumeAttempts++
                        Log.w(TAG, "Auto-resuming interrupted assistant reply ($autoResumeAttempts/$AUTO_RESUME_MAX_RETRIES)", error)
                        firstTokenTime = null
                        delay(AUTO_RESUME_RETRY_DELAY_MS)
                        continue
                    }
                    throw error
                }

                val latestConversation = getConversationFlow(conversationId).value
                if (
                    autoResumeAttempts < AUTO_RESUME_MAX_RETRIES &&
                    (
                        latestConversation.currentMessages.lastOrNull()?.needsAssistantReplyResume() == true ||
                            latestConversation.needsAssistantReplyAfterToolResult()
                        )
                ) {
                    autoResumeAttempts++
                    Log.w(TAG, "Auto-resuming assistant reply with no visible response ($autoResumeAttempts/$AUTO_RESUME_MAX_RETRIES)")
                    firstTokenTime = null
                    delay(AUTO_RESUME_RETRY_DELAY_MS)
                    continue
                }

                break
            }
        }.onFailure {
            it.printStackTrace()
            _errorFlow.emit(it)
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(
                conversationId = conversationId,
                conversation = finalConversation,
                preserveConsolidation = preserveConsolidation,
            )
            if (finalConversation.hasPendingToolApprovals()) {
                return@onSuccess
            }

            addConversationReference(conversationId) // 添加引用
            appScope.launch {
                coroutineScope {
                    launch {
                        // Fetch fresh conversation from DB to ensure we have the latest state
                        // This matches the manual regeneration pattern which works correctly
                        val freshConversation = withContext(Dispatchers.IO) {
                            conversationRepo.getConversationById(conversationId)
                        }
                        if (freshConversation != null) {
                            generateTitle(conversationId, freshConversation)
                        } else {
                            Log.w(TAG, "generateTitle: conversation not found in DB for $conversationId")
                        }
                    }
                    launch {
                        generateSuggestion(
                            conversationId = conversationId,
                            conversation = finalConversation,
                            preserveConsolidation = preserveConsolidation,
                        )
                    }
                    
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
    private data class ToolApprovalResolution(
        val approvalState: ToolApprovalState,
        val toolResult: UIMessagePart.ToolResult,
    )

    private suspend fun buildConversationTools(
        settings: Settings,
        assistant: me.rerere.rikkahub.data.model.Assistant,
        conversation: Conversation,
        model: Model,
    ): List<Tool> {
        return buildList {
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

            addAll(
                localTools.getTools(
                    options = assistant.localTools,
                    assistantId = assistant.id,
                    conversationId = conversation.id,
                )
            )

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

            mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                add(
                    Tool(
                        name = tool.name,
                        description = tool.description ?: "",
                        parameters = { tool.inputSchema },
                        execute = {
                            mcpManager.callTool(serverId, tool.name, it.jsonObject).truncateLargeJsonText()
                        },
                    )
                )
            }
        }
    }

    private suspend fun createToolApprovalResolution(
        toolCall: UIMessagePart.ToolCall,
        approved: Boolean,
        reason: String,
        answer: String?,
        tools: List<Tool>,
    ): ToolApprovalResolution {
        val parsedArguments = parseToolArguments(toolCall.arguments)
        if (!approved) {
            return ToolApprovalResolution(
                approvalState = ToolApprovalState.Denied(reason = reason),
                toolResult = UIMessagePart.ToolResult(
                    toolCallId = toolCall.toolCallId,
                    toolName = toolCall.toolName,
                    content = buildJsonObject {
                        put("denied", true)
                        if (reason.isNotBlank()) {
                            put("reason", reason)
                        }
                    },
                    arguments = parsedArguments,
                    metadata = toolCall.metadata,
                )
            )
        }

        if (toolCall.toolName == ASK_USER_TOOL_NAME) {
            val questionnaire = parseAskUserQuestionnaire(parsedArguments)
                ?: error("Invalid ask_user questionnaire")
            val payload = normalizeAskUserAnswerPayload(
                questionnaire = questionnaire,
                rawAnswer = answer,
            )
            val payloadJson = payload.toJsonElement()
            val payloadText = JsonInstantPretty.encodeToString(JsonElement.serializer(), payloadJson)
            return ToolApprovalResolution(
                approvalState = ToolApprovalState.Answered(answer = payloadText),
                toolResult = UIMessagePart.ToolResult(
                    toolCallId = toolCall.toolCallId,
                    toolName = toolCall.toolName,
                    content = payloadJson,
                    arguments = parsedArguments,
                    metadata = toolCall.metadata,
                )
            )
        }

        val tool = tools.find { it.name == toolCall.toolName }
        val result = runCatching {
            checkNotNull(tool) { "Tool ${toolCall.toolName} not found" }
            tool.execute(parsedArguments)
        }.getOrElse { throwable ->
            buildJsonObject {
                put(
                    "error",
                    "[${throwable::class.simpleName ?: "Throwable"}] ${throwable.message.orEmpty()}".trim()
                )
            }
        }

        return ToolApprovalResolution(
            approvalState = ToolApprovalState.Approved,
            toolResult = UIMessagePart.ToolResult(
                toolCallId = toolCall.toolCallId,
                toolName = toolCall.toolName,
                content = result,
                arguments = parsedArguments,
                metadata = toolCall.metadata,
            )
        )
    }

    private fun parseToolArguments(arguments: String): JsonElement {
        return parseJsonElementWithRecovery(arguments, JsonInstantPretty) ?: JsonPrimitive(arguments)
    }

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
        val conversation = normalizeConversation(getConversationFlow(conversationId).value)
        val messagesNodes = dropDanglingAutoToolCallNodes(conversation.messageNodes)

        updateConversation(
            conversationId,
            normalizeConversation(conversation.copy(messageNodes = messagesNodes))
        )
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
            val conversationContext = settings.resolveConversationContext(conversation)
            val model =
                settings.findModelById(settings.titleModelId) ?: conversationContext.chatModel
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
                            "locale" to context.appLocale().displayName,
                            "content" to contentForTitle)
                    ),
                ),
                params = settings.buildTitleGenerationParams(model),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            getConversationSnapshot(conversationId)?.let {
                saveConversation(
                    conversationId = conversationId,
                    conversation = it.copy(title = result.choices[0].message?.toContentText()?.trim() ?: ""),
                    preserveConsolidation = true,
                )
            }
        }.onFailure {
            Log.e(TAG, "generateTitle failed: ${it.message}", it)
        }
    }

    // 生成建议
    suspend fun generateSuggestion(
        conversationId: Uuid,
        conversation: Conversation,
        preserveConsolidation: Boolean = false,
    ) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.suggestionModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            updateConversation(
                conversationId,
                getConversationFlow(conversationId).value.copy(chatSuggestions = emptyList())
            )

            val providerHandler = providerManager.getProviderByType(provider)
            val promptContent = buildSuggestionPromptContent(
                messages = conversation.currentMessages,
                truncateIndex = conversation.truncateIndex,
            )
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to context.appLocale().displayName,
                            "content" to promptContent,
                        ),
                    )
                ),
                params = settings.buildSuggestionGenerationParams(model),
            )
            val suggestions = parseSuggestionLines(
                result.choices.firstOrNull()?.message?.toContentText().orEmpty()
            )

            // Apply suggestions to the current live snapshot so a stale DB checkpoint cannot overwrite messages.
            getConversationSnapshot(conversationId)?.let { freshConversation ->
                saveConversation(
                    conversationId = conversationId,
                    conversation = freshConversation.copy(chatSuggestions = suggestions),
                    preserveConsolidation = preserveConsolidation,
                )
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    private val conversationDeletionLock = Any()
    private val conversationDeletionJobs = mutableMapOf<Uuid, Job>()
    private val recentlyDeletedConversations = mutableMapOf<Uuid, Conversation>()

    private fun cancelPendingConversationDeletion(conversationId: Uuid) {
        val job = synchronized(conversationDeletionLock) {
            conversationDeletionJobs.remove(conversationId)
        }
        job?.cancel()
    }

    private fun rememberDeletedConversation(conversationId: Uuid, conversation: Conversation) =
        synchronized(conversationDeletionLock) {
            recentlyDeletedConversations[conversationId] = conversation
        }

    private fun rememberConversationDeletionJob(conversationId: Uuid, job: Job) =
        synchronized(conversationDeletionLock) {
            conversationDeletionJobs[conversationId] = job
        }

    private fun forgetDeletedConversation(conversationId: Uuid) = synchronized(conversationDeletionLock) {
        conversationDeletionJobs.remove(conversationId)
        recentlyDeletedConversations.remove(conversationId)
    }

    private fun takeRecentlyDeletedConversation(conversationId: Uuid): Conversation? =
        synchronized(conversationDeletionLock) {
            val conversation = recentlyDeletedConversations[conversationId]
            if (conversation != null) {
                conversationDeletionJobs.remove(conversationId)
                recentlyDeletedConversations.remove(conversationId)
            }
            conversation
        }

    // Track recently restored conversations for fade-in animation
    private val _recentlyRestoredIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Uuid>>(emptySet())
    val recentlyRestoredIds: kotlinx.coroutines.flow.StateFlow<Set<Uuid>> = _recentlyRestoredIds

    fun deleteConversation(conversation: Conversation) {
        appScope.launch {
            val conversationFull = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversation.id)
            } ?: return@launch

            // Cancel any pending deletion for this conversation
            cancelPendingConversationDeletion(conversation.id)

            // Soft delete (DB only, preserve files)
            withContext(Dispatchers.IO) {
                conversationRepo.deleteConversation(conversationFull, deleteFiles = false)
            }
            rememberDeletedConversation(conversation.id, conversationFull)

            // Finalize the soft-delete window after a short undo grace period.
            val job = appScope.launch {
                kotlinx.coroutines.delay(4000)
                forgetDeletedConversation(conversation.id)
            }
            rememberConversationDeletionJob(conversation.id, job)
        }
    }

    fun undoDeleteConversation(conversationId: Uuid) {
        cancelPendingConversationDeletion(conversationId)

        val conversation = takeRecentlyDeletedConversation(conversationId)
        if (conversation != null) {
            appScope.launch {
                withContext(Dispatchers.IO) {
                    conversationRepo.insertConversation(conversation)
                }

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
        val contentPreview = conversation.currentMessages.lastOrNull()?.toContentText()?.take(50).orEmpty()
        val notification =
            NotificationCompat.Builder(context, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_chat_done_title))
                .setContentText(contentPreview)
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
    private suspend fun copyAttachmentUrl(url: String): String {
        if (!url.startsWith("file:") && !url.startsWith("content:")) {
            return url
        }

        return chatAttachmentRepository.copyOrReuseUrl(url)
    }

    private fun collectRelatedMessages(
        conversation: Conversation,
        message: UIMessage,
    ): List<UIMessage> {
        val currentMessages = conversation.currentMessages
        val index = currentMessages.indexOfFirst { it.id == message.id }
        if (index == -1) return emptyList()

        val relatedMessages = linkedSetOf<UIMessage>()
        for (i in index - 1 downTo 0) {
            if (currentMessages[i].hasPart<UIMessagePart.ToolCall>() || currentMessages[i].hasPart<UIMessagePart.ToolResult>()) {
                relatedMessages += currentMessages[i]
            } else {
                break
            }
        }
        for (i in index + 1 until currentMessages.size) {
            if (currentMessages[i].hasPart<UIMessagePart.ToolCall>() || currentMessages[i].hasPart<UIMessagePart.ToolResult>()) {
                relatedMessages += currentMessages[i]
            } else {
                break
            }
        }
        return relatedMessages.toList()
    }

    private fun deleteMessageInternal(
        conversation: Conversation,
        message: UIMessage,
    ): Conversation {
        val node = conversation.getMessageNodeByMessageId(message.id) ?: return conversation
        val nodeIndex = conversation.messageNodes.indexOf(node)
        if (nodeIndex == -1) return conversation

        val deleteVersionTag = message.versionTag
        val turnStartIndex = conversation.messageNodes
            .subList(0, nodeIndex + 1)
            .indexOfLast { it.role == MessageRole.USER } + 1
        val turnEndIndex = conversation.messageNodes
            .subList(nodeIndex, conversation.messageNodes.size)
            .indexOfFirst { it.role == MessageRole.USER }
            .let { if (it == -1) conversation.messageNodes.size else nodeIndex + it }

        return if (node.messages.size == 1 && deleteVersionTag == null) {
            conversation.copy(
                messageNodes = conversation.messageNodes.filterIndexed { index, _ -> index != nodeIndex }
            )
        } else {
            val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, messageNode ->
                val canDeleteByVersionTag = deleteVersionTag != null &&
                    index in turnStartIndex until turnEndIndex &&
                    messageNode.role != MessageRole.USER

                val remainingMessages = messageNode.messages.filter { currentMessage ->
                    if (canDeleteByVersionTag && currentMessage.versionTag == deleteVersionTag) {
                        false
                    } else {
                        currentMessage.id != message.id
                    }
                }

                if (remainingMessages.isEmpty()) {
                    null
                } else {
                    messageNode.copy(
                        messages = remainingMessages,
                        selectIndex = if (messageNode.selectIndex >= remainingMessages.size) {
                            remainingMessages.lastIndex
                        } else {
                            messageNode.selectIndex
                        }
                    )
                }
            }
            conversation.copy(messageNodes = updatedNodes)
        }
    }

    private suspend fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val normalizedConversation = normalizeConversation(conversation)
        getOrCreateConversationState(conversationId) { normalizedConversation }.value =
            normalizedConversation
    }

    // 检查文件删除
    private suspend fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        // Attachment lifecycle is synchronized through ChatAttachmentRepository.
    }

    // Context Refresh result
    data class ContextRefreshResult(
        val success: Boolean,
        val summary: String = "",
        val messagesSummarized: Int = 0,
        val tokensSaved: Int = 0,
        @param:StringRes val errorResId: Int? = null,
        val errorArgs: List<Any> = emptyList()
    )

    private fun contextRefreshError(
        @StringRes errorResId: Int,
        vararg errorArgs: Any
    ): ContextRefreshResult = ContextRefreshResult(
        success = false,
        errorResId = errorResId,
        errorArgs = errorArgs.toList(),
    )

    // Check if auto-summarization threshold is reached and trigger if needed
    private suspend fun checkAndAutoSummarize(
        conversationId: Uuid,
        conversation: Conversation,
        settings: Settings
    ) {
        try {
            val assistant = settings.resolveConversationContext(conversation).assistant
            
            // Check if auto-summarization is enabled and configured with a limit
            if (!assistant.shouldAutoSummarizeMessages()) {
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
                    Log.w(TAG, "Auto-summarization failed: ${result.errorResId}, args=${result.errorArgs}")
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
            val conversation = normalizeConversation(
                conversationRepo.getConversationById(conversationId)
                    ?: return@withContext contextRefreshError(R.string.context_refresh_error_conversation_not_found)
            )
            val conversationContext = settings.resolveConversationContext(conversation)
            val assistant = conversationContext.assistant

            // Check for empty messages FIRST before model lookup
            val messages = conversation.currentMessages
            if (messages.isEmpty()) {
                return@withContext contextRefreshError(R.string.context_refresh_error_no_messages)
            }

            // Get the summarizer model (fall back to chat model)
            val model = settings.summarizerModelId?.let(settings::findModelById)
                ?: conversationContext.chatModel
                ?: return@withContext contextRefreshError(R.string.context_refresh_error_no_model)
            val provider = model.findProvider(settings.providers)
                ?: return@withContext contextRefreshError(R.string.context_refresh_error_no_provider)



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
                return@withContext contextRefreshError(R.string.context_refresh_error_no_new_messages)
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
                params = settings.buildSummarizerGenerationParams(
                    model = model,
                    temperature = 0.3f,
                )
            )

            val summary = response.choices.firstOrNull()?.message?.toContentText()
                ?: return@withContext contextRefreshError(R.string.context_refresh_error_empty_response)

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
            val errorMessage = e.message?.takeIf { it.isNotBlank() }
            if (errorMessage != null) {
                contextRefreshError(R.string.context_refresh_error_unexpected, errorMessage)
            } else {
                contextRefreshError(R.string.context_refresh_error_unknown)
            }
        }
    }


    // 保存对话
    suspend fun saveConversation(
        conversationId: Uuid,
        conversation: Conversation,
        preserveConsolidation: Boolean = false,
    ) {
        val normalizedConversation = mergeLiveMessagesIfIncomingIsStale(
            liveConversation = getConversationState(conversationId)?.value,
            incomingConversation = normalizeConversation(conversation),
        )
        val synchronizedConversation = withContext(Dispatchers.IO) {
            chatAttachmentRepository.syncConversationAttachments(normalizedConversation)
        }

        // 临时对话不持久化到数据库
        if (getConversationPersistenceMode(conversationId) != ChatPersistenceMode.NORMAL) {
            updateConversation(conversationId, synchronizedConversation)
            return
        }

        val updatedConversation = synchronizedConversation.copy()
        // Always update in-memory state (even for empty conversations)
        // This ensures mode toggles work on new chats before first message
        updateConversation(conversationId, updatedConversation)

        // Skip database persist for empty conversations (no messages and no title)
        if (updatedConversation.title.isBlank() && updatedConversation.messageNodes.isEmpty()) return

        persistConversationToRepository(
            conversation = updatedConversation,
            preserveConsolidation = preserveConsolidation,
        )
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
        removeConversationState(conversationId)
        setConversationPersistenceMode(conversationId, ChatPersistenceMode.NORMAL)

        Log.i(
            TAG,
            "cleanupConversation: removed $conversationId (current references: ${conversationReferenceCount()}, generation jobs: ${_generationJobs.value.size})"
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
    }
}

private fun getWorkspaceCwd(assistantName: String, chatTitle: String, chatId: String): String {
    val sanitizedTitle = chatTitle.replace(Regex("[^a-zA-Z0-9_\\\\-\\u4e00-\\u9fa5]"), "_").take(30)
    return "/workspace/chats/${sanitizedTitle}_${chatId.take(8)}"
}
