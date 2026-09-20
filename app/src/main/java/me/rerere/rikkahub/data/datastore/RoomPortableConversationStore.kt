package me.rerere.rikkahub.data.datastore

import me.rerere.ai.generation.PortableConversationRecord
import me.rerere.ai.generation.PortableConversationStore
import me.rerere.ai.generation.PortableSaveOptions
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.Instant
import kotlin.uuid.Uuid

class RoomPortableConversationStore(
    private val conversationRepo: ConversationRepository,
) : PortableConversationStore {
    override suspend fun get(id: String): PortableConversationRecord? {
        val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: return null
        return conversationRepo.getConversationById(uuid)?.toPortableRecord()
    }

    override suspend fun save(
        conversation: PortableConversationRecord,
        options: PortableSaveOptions,
    ) {
        val mapped = conversation.toConversation()
        val existing = conversationRepo.getConversationById(mapped.id)
        if (existing == null) {
            conversationRepo.insertConversation(mapped)
        } else {
            conversationRepo.updateConversation(
                conversation = mapped,
                preserveConsolidation = options.preserveConsolidation,
                syncAttachments = options.syncAttachments,
            )
        }
    }
}

fun Conversation.toPortableRecord(): PortableConversationRecord = PortableConversationRecord(
    id = id.toString(),
    assistantId = assistantId.toString(),
    title = title,
    messageNodes = messageNodes,
    isPinned = isPinned,
    updatedAtEpochMs = updateAt.toEpochMilli(),
    enabledSkillIds = enabledModeIds.map { it.toString() }.toSet(),
    enabledLorebookIds = enabledLorebookIds?.map { it.toString() }?.toSet(),
    truncateIndex = truncateIndex,
    contextSummary = contextSummary,
    contextSummaryUpToIndex = contextSummaryUpToIndex,
    chatSuggestions = chatSuggestions,
    createdAtEpochMs = createAt.toEpochMilli(),
    isConsolidated = isConsolidated,
    lastPruneTime = lastPruneTime,
    lastPruneMessageCount = lastPruneMessageCount,
    lastRefreshTime = lastRefreshTime,
    isFork = isFork,
)

fun PortableConversationRecord.toConversation(): Conversation = Conversation(
    id = Uuid.parse(id),
    assistantId = assistantId?.let { Uuid.parse(it) } ?: DEFAULT_ASSISTANT_ID,
    title = title,
    messageNodes = messageNodes,
    truncateIndex = truncateIndex,
    chatSuggestions = chatSuggestions,
    isPinned = isPinned,
    enabledModeIds = enabledSkillIds.orEmpty().mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet(),
    enabledLorebookIds = enabledLorebookIds?.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }?.toSet(),
    createAt = Instant.ofEpochMilli(createdAtEpochMs),
    updateAt = Instant.ofEpochMilli(updatedAtEpochMs),
    isConsolidated = isConsolidated,
    contextSummary = contextSummary,
    contextSummaryUpToIndex = contextSummaryUpToIndex,
    lastPruneTime = lastPruneTime,
    lastPruneMessageCount = lastPruneMessageCount,
    lastRefreshTime = lastRefreshTime,
    isFork = isFork,
)
