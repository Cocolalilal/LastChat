package me.rerere.ai.generation

import kotlinx.coroutines.Job
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/**
 * The shared chat orchestrator. Android [me.rerere.rikkahub.service.ChatService] /
 * [me.rerere.rikkahub.data.ai.GenerationHandler] and iOS IosAppController both call
 * this class for generation, job tracking, persistence mode, and node mutations.
 * There is one [PortableGenerationLoop] inside it — not two engines behind an interface.
 */
@OptIn(ExperimentalAtomicApi::class)
class PortableChatEngine(
    val loop: PortableGenerationLoop = PortableGenerationLoop(),
    private val store: PortableConversationStore = InMemoryPortableConversationStore(),
) {
    private val jobs = AtomicReference<Map<String, Job>>(emptyMap())
    private val persistenceModes = AtomicReference<Map<String, PortablePersistenceMode>>(emptyMap())

    suspend fun generate(session: PortableGenerationSession): PortableGenerationResult {
        return loop.run(session)
    }

    fun attachJob(conversationId: String, job: Job) {
        while (true) {
            val current = jobs.load()
            val previous = current[conversationId]
            val next = current + (conversationId to job)
            if (jobs.compareAndSet(current, next)) {
                if (previous !== job) previous?.cancel()
                job.invokeOnCompletion { detachJob(conversationId, job) }
                return
            }
        }
    }

    fun stop(conversationId: String) {
        while (true) {
            val current = jobs.load()
            val job = current[conversationId] ?: return
            val next = current - conversationId
            if (jobs.compareAndSet(current, next)) {
                job.cancel()
                return
            }
        }
    }

    fun isGenerating(conversationId: String): Boolean {
        return jobs.load()[conversationId]?.isActive == true
    }

    fun setPersistenceMode(conversationId: String, mode: PortablePersistenceMode) {
        while (true) {
            val current = persistenceModes.load()
            val next = if (mode == PortablePersistenceMode.NORMAL) {
                current - conversationId
            } else {
                current + (conversationId to mode)
            }
            if (persistenceModes.compareAndSet(current, next)) return
        }
    }

    fun persistenceMode(conversationId: String): PortablePersistenceMode {
        return persistenceModes.load()[conversationId] ?: PortablePersistenceMode.NORMAL
    }

    suspend fun loadConversation(id: String): PortableConversationRecord? = store.get(id)

    suspend fun saveConversation(
        conversation: PortableConversationRecord,
        options: PortableSaveOptions = PortableSaveOptions(),
    ) {
        store.save(conversation, options)
    }

    suspend fun deleteConversation(
        id: String,
        options: PortableDeleteOptions = PortableDeleteOptions(),
    ) {
        store.delete(id, options)
    }

    suspend fun finalizeConversationDeletion(id: String) {
        store.finalizeDeletion(id)
    }

    suspend fun listConversations(
        assistantId: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<PortableConversationRecord> {
        val records = if (assistantId == null) {
            store.list()
        } else {
            store.listByAssistant(assistantId, limit)
        }
        return if (assistantId == null && limit != Int.MAX_VALUE) records.take(limit) else records
    }

    suspend fun pageConversations(
        query: PortableConversationQuery,
    ): PortableConversationPage = store.page(query)

    suspend fun searchMessages(
        query: PortableConversationQuery,
    ): List<PortableMessageSearchHit> = store.searchMessages(query)

    suspend fun usageTotals(): PortableUsageTotals = store.usageTotals()

    fun observeListVersion() = store.observeListVersion()

    fun observeUsageTotals() = store.observeUsageTotals()

    fun editMessage(nodes: List<MessageNode>, messageId: Uuid, parts: List<UIMessagePart>): List<MessageNode> {
        return nodes.withEditedMessage(messageId, parts)
    }

    fun deleteMessage(nodes: List<MessageNode>, messageId: Uuid): List<MessageNode> {
        return nodes.withDeletedMessage(messageId)
    }

    fun forkThroughMessage(
        nodes: List<MessageNode>,
        messageId: Uuid,
        remapNodeId: (Uuid) -> Uuid = { it },
        remapPartUrl: (String) -> String = { it },
    ): List<MessageNode>? {
        return nodes.forkThroughMessage(messageId, remapNodeId, remapPartUrl)
    }

    fun selectTurnVersion(nodes: List<MessageNode>, nodeId: Uuid, selectIndex: Int): List<MessageNode> {
        return nodes.selectTurnVersion(nodeId, selectIndex)
    }

    fun seedAssistantRegeneration(
        nodes: List<MessageNode>,
        turnStartIndex: Int,
        placeholder: UIMessage,
    ): List<MessageNode> {
        return nodes.seedAssistantRegeneration(turnStartIndex, placeholder)
    }

    fun mergeRegeneratedTurn(
        nodes: List<MessageNode>,
        turnStartIndex: Int,
        versionTag: String,
        generatedMessages: List<UIMessage>,
    ): List<MessageNode> {
        return nodes.mergeRegeneratedTurn(turnStartIndex, versionTag, generatedMessages)
    }

    fun applyToolApprovalState(
        nodes: List<MessageNode>,
        toolCallId: String,
        approvalState: ToolApprovalState,
    ): List<MessageNode> {
        return nodes.applyToolApprovalState(toolCallId, approvalState)
    }

    private fun detachJob(conversationId: String, job: Job) {
        while (true) {
            val current = jobs.load()
            if (current[conversationId] !== job) return
            if (jobs.compareAndSet(current, current - conversationId)) return
        }
    }
}
