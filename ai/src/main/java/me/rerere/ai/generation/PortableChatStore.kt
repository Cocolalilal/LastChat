package me.rerere.ai.generation

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.MessageNode

/**
 * Conversation persistence that both Android Room and the iOS file store implement.
 * Room cannot move to KMP in this slice, so hosts keep their engines and adapt here.
 * Android ChatService uses a Room-backed adapter as the live save path.
 */
interface PortableConversationStore {
    suspend fun get(id: String): PortableConversationRecord?
    suspend fun save(
        conversation: PortableConversationRecord,
        options: PortableSaveOptions = PortableSaveOptions(),
    )
    suspend fun list(): List<PortableConversationRecord> = emptyList()
    suspend fun delete(id: String) {}
}

data class PortableSaveOptions(
    val preserveConsolidation: Boolean = false,
    val syncAttachments: Boolean = true,
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

    init {
        initial.forEach { conversations[it.id] = it }
    }

    override suspend fun get(id: String): PortableConversationRecord? = conversations[id]

    override suspend fun save(
        conversation: PortableConversationRecord,
        options: PortableSaveOptions,
    ) {
        conversations[conversation.id] = conversation
    }

    override suspend fun list(): List<PortableConversationRecord> = conversations.values.toList()

    override suspend fun delete(id: String) {
        conversations.remove(id)
    }
}
