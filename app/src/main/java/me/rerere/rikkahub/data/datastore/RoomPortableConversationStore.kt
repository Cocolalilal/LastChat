package me.rerere.rikkahub.data.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.rerere.ai.generation.PortableConversationPage
import me.rerere.ai.generation.PortableConversationQuery
import me.rerere.ai.generation.PortableConversationQueries
import me.rerere.ai.generation.PortableConversationRecord
import me.rerere.ai.generation.PortableConversationStore
import me.rerere.ai.generation.PortableDailyActivity
import me.rerere.ai.generation.PortableDeleteOptions
import me.rerere.ai.generation.PortableMessageSearchHit
import me.rerere.ai.generation.PortableSaveOptions
import me.rerere.ai.generation.PortableUsageTotals
import me.rerere.rikkahub.data.db.entity.DailyActivityEntity
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
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

    override suspend fun list(): List<PortableConversationRecord> {
        return conversationRepo.getAllConversations().first().map { it.toPortableRecord() }
    }

    override suspend fun listByAssistant(
        assistantId: String,
        limit: Int,
    ): List<PortableConversationRecord> {
        val uuid = runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return emptyList()
        return conversationRepo.getRecentConversations(uuid, limit).map { it.toPortableRecord() }
    }

    override suspend fun delete(
        id: String,
        options: PortableDeleteOptions,
    ) {
        val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: return
        val existing = conversationRepo.getConversationById(uuid) ?: return
        conversationRepo.deleteConversation(existing, deleteFiles = options.deleteFiles)
    }

    override suspend fun finalizeDeletion(id: String) {
        val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: return
        conversationRepo.finalizeConversationDeletion(uuid)
    }

    override suspend fun page(query: PortableConversationQuery): PortableConversationPage {
        val assistantId = query.assistantId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        if (query.assistantId != null && assistantId == null) {
            return PortableConversationPage(items = emptyList(), totalCount = 0, nextOffset = null)
        }
        val (items, total) = conversationRepo.pageConversations(
            assistantId = assistantId,
            query = query.query,
            offset = query.offset,
            limit = query.limit,
            includeMessages = query.includeMessages,
        )
        val nextOffset = (query.offset.coerceAtLeast(0) + items.size).takeIf { it < total }
        return PortableConversationPage(
            items = items.map { it.toPortableRecord() },
            totalCount = total,
            nextOffset = nextOffset,
        )
    }

    override suspend fun searchMessages(query: PortableConversationQuery): List<PortableMessageSearchHit> {
        val assistantId = query.assistantId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        if (query.assistantId != null && assistantId == null) return emptyList()
        val (items, _) = conversationRepo.pageConversations(
            assistantId = assistantId,
            query = query.query,
            offset = 0,
            limit = 200,
            includeMessages = true,
        )
        return PortableConversationQueries.searchMessages(
            records = items.map { it.toPortableRecord() },
            query = query.query,
            limit = query.limit.coerceAtMost(PortableConversationQueries.MAX_SEARCH_HITS),
        )
    }

    override suspend fun usageTotals(): PortableUsageTotals {
        return conversationRepo.getUsageStatsLast12MonthsFlow().first().toPortableUsageTotals()
    }

    override fun observeListVersion(): Flow<Long> = conversationRepo.observeConversationListVersion()

    override fun observeUsageTotals(): Flow<PortableUsageTotals> {
        return conversationRepo.getUsageStatsLast12MonthsFlow().map { it.toPortableUsageTotals() }
    }

    override suspend fun recordDailyActivity(date: String, timestampEpochMs: Long) {
        conversationRepo.recordDailyActivity(date, timestampEpochMs)
    }

    override suspend fun mergeDailyActivity(entries: List<PortableDailyActivity>) {
        entries.forEach { entry ->
            conversationRepo.mergeDailyActivity(entry.date, entry.messageCount, entry.lastMessageEpochMs)
        }
    }

    override suspend fun dailyActivity(): List<PortableDailyActivity> {
        return conversationRepo.getAllDailyActivityFlow().first().map { it.toPortableDailyActivity() }
    }

    override fun observeDailyActivity(): Flow<List<PortableDailyActivity>> {
        return conversationRepo.getAllDailyActivityFlow().map { rows ->
            rows.map { it.toPortableDailyActivity() }
        }
    }

    override suspend fun backfillDailyActivityIfNeeded() {
        conversationRepo.backfillDailyActivityFromConversationHistoryIfNeeded()
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

fun UsageStatsEntity.toPortableUsageTotals(): PortableUsageTotals = PortableUsageTotals(
    conversationCount = totalConversations,
    messageCount = totalMessages,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cachedTokens = cachedTokens,
)

fun PortableUsageTotals.toUsageStatsEntity(): UsageStatsEntity = UsageStatsEntity(
    totalConversations = conversationCount,
    totalMessages = messageCount,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cachedTokens = cachedTokens,
)

fun DailyActivityEntity.toPortableDailyActivity(): PortableDailyActivity = PortableDailyActivity(
    date = date,
    messageCount = messageCount,
    lastMessageEpochMs = lastMessageTime,
)
