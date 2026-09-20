package me.rerere.ai.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.MessageNode

/**
 * Conversation persistence that both Android Room and the iOS file store implement.
 * Room cannot move to KMP in this slice, so hosts keep their engines and adapt here.
 * Android ChatService, UI paging, usage stats, and web conversation lists use this
 * as the live API; Room keeps efficient SQL behind the adapter.
 */
interface PortableConversationStore {
    suspend fun get(id: String): PortableConversationRecord?
    suspend fun save(
        conversation: PortableConversationRecord,
        options: PortableSaveOptions = PortableSaveOptions(),
    )
    suspend fun list(): List<PortableConversationRecord> = emptyList()
    suspend fun listByAssistant(
        assistantId: String,
        limit: Int = Int.MAX_VALUE,
    ): List<PortableConversationRecord> = list()
        .filter { it.assistantId == assistantId }
        .sortedByDescending { it.updatedAtEpochMs }
        .let { records -> if (limit == Int.MAX_VALUE) records else records.take(limit) }
    suspend fun delete(
        id: String,
        options: PortableDeleteOptions = PortableDeleteOptions(),
    ) {}
    suspend fun finalizeDeletion(id: String) {}

    suspend fun page(query: PortableConversationQuery): PortableConversationPage =
        PortableConversationQueries.page(list(), query)

    suspend fun searchMessages(query: PortableConversationQuery): List<PortableMessageSearchHit> {
        val records = if (query.assistantId == null) {
            list()
        } else {
            listByAssistant(query.assistantId, Int.MAX_VALUE)
        }
        return PortableConversationQueries.searchMessages(
            records = records,
            query = query.query,
            limit = query.limit.coerceAtMost(PortableConversationQueries.MAX_SEARCH_HITS),
        )
    }

    suspend fun usageTotals(): PortableUsageTotals = PortableConversationQueries.usageTotals(list())

    fun observeListVersion(): Flow<Long> = flowOf(0L)

    fun observeUsageTotals(): Flow<PortableUsageTotals> = flow { emit(usageTotals()) }
}

data class PortableSaveOptions(
    val preserveConsolidation: Boolean = false,
    val syncAttachments: Boolean = true,
)

data class PortableDeleteOptions(
    val deleteFiles: Boolean = true,
)

data class PortableConversationQuery(
    val assistantId: String? = null,
    val query: String = "",
    val offset: Int = 0,
    val limit: Int = PortableConversationQueries.DEFAULT_PAGE_SIZE,
    val includeMessages: Boolean = false,
)

data class PortableConversationPage(
    val items: List<PortableConversationRecord>,
    val totalCount: Int,
    val nextOffset: Int? = null,
)

data class PortableMessageSearchHit(
    val conversationId: String,
    val conversationTitle: String,
    val nodeId: String,
    val messageId: String,
    val snippet: String,
    val updatedAtEpochMs: Long,
)

data class PortableUsageTotals(
    val conversationCount: Long = 0,
    val messageCount: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedTokens: Long = 0,
)

@Serializable
data class PortableConversationRecord(
    val id: String,
    val assistantId: String?,
    val title: String,
    val messageNodes: List<MessageNode>,
    val isPinned: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val enabledSkillIds: Set<String>? = null,
    val enabledLorebookIds: Set<String>? = null,
    val truncateIndex: Int = -1,
    val contextSummary: String? = null,
    val contextSummaryUpToIndex: Int = -1,
    val chatSuggestions: List<String> = emptyList(),
    val createdAtEpochMs: Long = 0L,
    val isConsolidated: Boolean = false,
    val lastPruneTime: Long = 0L,
    val lastPruneMessageCount: Int = 0,
    val lastRefreshTime: Long = 0L,
    val isFork: Boolean = false,
    val memoryLastMessageId: String? = null,
)

enum class PortablePersistenceMode {
    NORMAL,
    TEMPORARY,
    PERSIST_ON_REPLY,
}

/**
 * Assistant/provider settings the generation host needs. Android SettingsStore and the
 * iOS preference file both sit behind this so the orchestrator does not own two configs.
 */
interface PortableSettingsStore {
    suspend fun selectedChatModelId(): String?
    suspend fun assistantIdForConversation(conversationId: String): String?
}

class InMemoryPortableConversationStore(
    initial: List<PortableConversationRecord> = emptyList(),
) : PortableConversationStore {
    private val conversations = LinkedHashMap<String, PortableConversationRecord>()
    private val listVersion = MutableStateFlow(0L)

    init {
        initial.forEach { conversations[it.id] = it }
    }

    override suspend fun get(id: String): PortableConversationRecord? = conversations[id]

    override suspend fun save(
        conversation: PortableConversationRecord,
        options: PortableSaveOptions,
    ) {
        conversations[conversation.id] = conversation
        bumpListVersion()
    }

    override suspend fun list(): List<PortableConversationRecord> =
        conversations.values.sortedByDescending { it.updatedAtEpochMs }

    override suspend fun listByAssistant(
        assistantId: String,
        limit: Int,
    ): List<PortableConversationRecord> {
        val records = conversations.values
            .filter { it.assistantId == assistantId }
            .sortedByDescending { it.updatedAtEpochMs }
        return if (limit == Int.MAX_VALUE) records else records.take(limit)
    }

    override suspend fun delete(
        id: String,
        options: PortableDeleteOptions,
    ) {
        conversations.remove(id)
        bumpListVersion()
    }

    override suspend fun finalizeDeletion(id: String) {
        conversations.remove(id)
        bumpListVersion()
    }

    override fun observeListVersion(): Flow<Long> = listVersion.asStateFlow()

    private fun bumpListVersion() {
        listVersion.value = listVersion.value + 1
    }
}
