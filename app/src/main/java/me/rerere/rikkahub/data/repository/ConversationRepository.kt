package me.rerere.rikkahub.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.DailyActivityDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.deleteChatFiles
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

class ConversationRepository(
    private val context: Context,
    private val conversationDAO: ConversationDAO,
    private val chatEpisodeDAO: me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO,
    private val dailyActivityDAO: DailyActivityDAO,
) {
    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_LOAD_SIZE = 40
    }

    suspend fun getRecentConversations(assistantId: Uuid, limit: Int = 10): List<Conversation> {
        return conversationDAO.getRecentConversationsOfAssistant(
            assistantId = assistantId.toString(),
            limit = limit
        ).map { conversationEntityToConversation(it) }
    }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> {
        return conversationDAO
            .getConversationsOfAssistant(assistantId.toString())
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    fun getAllLightConversations(): Flow<List<Conversation>> {
        return conversationDAO.getAllLight()
            .map { list ->
                list.map { conversationSummaryToConversation(it) }
            }
    }

    fun getConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversations(titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    fun searchConversationsPaging(titleKeyword: String): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.searchConversationsPaging(titleKeyword) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    fun searchConversationsOfAssistant(assistantId: Uuid, titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversationsOfAssistant(assistantId.toString(), titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    fun searchConversationsOfAssistantPaging(assistantId: Uuid, titleKeyword: String): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.searchConversationsOfAssistantPaging(assistantId.toString(), titleKeyword) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString())
        return if (entity != null) {
            conversationEntityToConversation(entity)
        } else null
    }

    suspend fun insertConversation(conversation: Conversation) {
        conversationDAO.insert(
            conversationToConversationEntity(conversation)
        )
    }

    suspend fun updateConversation(conversation: Conversation) {
        // Invalidation Logic: If a consolidated conversation is updated (e.g. new message),
        // we must invalidate the old memory episode to allow re-consolidation.
        if (conversation.isConsolidated) {
            val updatedConversation = conversation.copy(isConsolidated = false)

            conversationDAO.update(
                conversationToConversationEntity(updatedConversation)
            )

            // Delete the old episode based on conversation ID if possible.
            // If deletion by ID returns 0 (e.g. legacy episode without conversationId),
            // fallback to best-effort deletion based on time range.
            val deletedCount = chatEpisodeDAO.deleteEpisodeByConversationId(conversation.id.toString())
            if (deletedCount == 0) {
                chatEpisodeDAO.deleteEpisodeByTimeRange(
                    assistantId = conversation.assistantId.toString(),
                    startTime = conversation.createAt.toEpochMilli(),
                    endTime = Long.MAX_VALUE
                )
            }
        } else {
            conversationDAO.update(
                conversationToConversationEntity(conversation)
            )
        }
    }

    suspend fun deleteConversation(conversation: Conversation, deleteFiles: Boolean = true) {
        conversationDAO.delete(
            conversationToConversationEntity(conversation)
        )
        chatEpisodeDAO.deleteEpisodeByConversationId(conversation.id.toString())
        if (deleteFiles) {
            context.deleteChatFiles(conversation.files)
        }
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        getConversationsOfAssistant(assistantId).first().forEach { conversation ->
            deleteConversation(conversation)
        }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            nodes = JsonInstant.encodeToString(conversation.messageNodes),
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            assistantId = conversation.assistantId.toString(),
            truncateIndex = conversation.truncateIndex,
            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions),
            isPinned = conversation.isPinned,
            isConsolidated = conversation.isConsolidated,
            enabledModeIds = JsonInstant.encodeToString(conversation.enabledModeIds.map { it.toString() }),
            contextSummary = conversation.contextSummary ?: "",
            contextSummaryUpToIndex = conversation.contextSummaryUpToIndex,
            lastPruneTime = conversation.lastPruneTime,
            lastPruneMessageCount = conversation.lastPruneMessageCount,
            lastRefreshTime = conversation.lastRefreshTime,
        )
    }

    fun conversationEntityToConversation(conversationEntity: ConversationEntity): Conversation {
        val messageNodes = JsonInstant
            .decodeFromString<List<MessageNode>>(conversationEntity.nodes)
            .filter { it.messages.isNotEmpty() }
        val enabledModeIds = try {
            JsonInstant.decodeFromString<List<String>>(conversationEntity.enabledModeIds)
                .map { Uuid.parse(it) }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
        return Conversation(
            id = Uuid.parse(conversationEntity.id),
            title = conversationEntity.title,
            messageNodes = messageNodes,
            createAt = Instant.ofEpochMilli(conversationEntity.createAt),
            updateAt = Instant.ofEpochMilli(conversationEntity.updateAt),
            assistantId = Uuid.parse(conversationEntity.assistantId),
            truncateIndex = conversationEntity.truncateIndex,
            chatSuggestions = JsonInstant.decodeFromString(conversationEntity.chatSuggestions),
            isPinned = conversationEntity.isPinned,
            isConsolidated = conversationEntity.isConsolidated,
            enabledModeIds = enabledModeIds,
            contextSummary = conversationEntity.contextSummary.takeIf { it.isNotBlank() },
            contextSummaryUpToIndex = conversationEntity.contextSummaryUpToIndex,
            lastPruneTime = conversationEntity.lastPruneTime,
            lastPruneMessageCount = conversationEntity.lastPruneMessageCount,
            lastRefreshTime = conversationEntity.lastRefreshTime,
        )
    }

    fun getPinnedConversations(): Flow<List<Conversation>> {
        return conversationDAO
            .getPinnedConversations()
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    suspend fun togglePinStatus(conversationId: Uuid) {
        conversationDAO.updatePinStatus(
            id = conversationId.toString(),
            isPinned = !(getConversationById(conversationId)?.isPinned ?: false)
        )
    }

    suspend fun markAsConsolidated(conversationId: Uuid) {
        conversationDAO.updateConsolidatedStatus(
            id = conversationId.toString(),
            isConsolidated = true
        )
    }

    suspend fun markAsNotConsolidated(conversationId: Uuid) {
        conversationDAO.updateConsolidatedStatus(
            id = conversationId.toString(),
            isConsolidated = false
        )
    }

    suspend fun getEpisodeCount(): Int {
        return chatEpisodeDAO.getCount()
    }

    fun getEpisodeCountFlow(): Flow<Int> {
        return chatEpisodeDAO.getCountFlow()
    }

    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDAO.getAll()
            .map { list ->
                list.map { conversationEntityToConversation(it) }
            }
    }

    // Optimized stats queries - delegate to SQL for performance
    fun getConversationCountFlow(): Flow<Int> = conversationDAO.getConversationCountFlow()

    fun getDistinctCreateDatesFlow(): Flow<List<String>> = conversationDAO.getDistinctCreateDatesFlow()

    fun getMostActiveAssistantIdFlow(): Flow<String?> = conversationDAO.getMostActiveAssistantFlow()
        .map { it?.assistantId }

    fun getConversationHoursFlow(): Flow<List<Int>> = conversationDAO.getConversationHoursFlow()

    fun getConversationCountByAssistantFlow(assistantId: String): Flow<Int> = 
        conversationDAO.getConversationCountByAssistantFlow(assistantId)

    /**
     * Get the most frequently used model ID for an assistant by analyzing message nodes.
     * Returns the model UUID as string, or null if no model found.
     */
    fun getMostUsedModelIdForAssistantFlow(assistantId: String): Flow<String?> = 
        conversationDAO.getConversationsOfAssistant(assistantId)
            .map { conversations ->
                // Extract all modelIds from message nodes
                val modelCounts = mutableMapOf<String, Int>()
                
                for (conversation in conversations) {
                    try {
                        val nodesJson = JsonInstant.parseToJsonElement(conversation.nodes)
                        if (nodesJson is JsonArray) {
                            for (nodeElement in nodesJson) {
                                val node = nodeElement.jsonObject
                                // Check all variants of the current node
                                val messages = node["messages"]?.jsonArray ?: continue
                                for (messageElement in messages) {
                                    val message = messageElement.jsonObject
                                    val modelId = message["modelId"]?.jsonPrimitive?.content
                                    if (modelId != null && modelId != "null") {
                                        modelCounts[modelId] = (modelCounts[modelId] ?: 0) + 1
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed conversations
                    }
                }
                
                // Return the most used model ID
                modelCounts.maxByOrNull { it.value }?.key
            }

    // ===== Daily Activity Tracking (for persistent streaks) =====
    
    /**
     * Record that the user was active today (sent a message).
     * This persists independently of conversations, so streak data
     * is preserved even when chats are deleted.
     */
    suspend fun recordDailyActivity() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        dailyActivityDAO.recordActivity(today)
    }
    
    /**
     * Get all activity dates for streak calculation.
     * Returns dates in ISO format (YYYY-MM-DD), ordered most recent first.
     */
    fun getDailyActivityDatesFlow(): Flow<List<String>> = dailyActivityDAO.getAllDatesFlow()
    
    fun getWeeklyActivityFlow(startDate: String): Flow<List<me.rerere.rikkahub.data.db.entity.DailyActivityEntity>> =
        dailyActivityDAO.getWeeklyActivityFlow(startDate)
    
    /**
     * Check if user has sent a message today.
     */
    fun hasChattedTodayFlow(): Flow<Boolean> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dailyActivityDAO.hasActivityForDateFlow(today)
    }
    
    /**
     * Migrate existing conversation dates to the daily activity table.
     * Called once during app initialization to preserve existing streaks.
     */
    suspend fun migrateConversationDatesToActivity() {
        val existingDates = conversationDAO.getDistinctCreateDatesFlow().first()
        for (dateStr in existingDates) {
            try {
                // Parse and re-format to ensure ISO format
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val isoDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                // Use a timestamp in the middle of that day for migration
                val timestamp = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
                dailyActivityDAO.insertDateIfNotExists(isoDate, timestamp)
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }
    }

    private fun conversationSummaryToConversation(entity: LightConversationEntity): Conversation {
        return Conversation(
            id = Uuid.parse(entity.id),
            assistantId = Uuid.parse(entity.assistantId),
            title = entity.title,
            isPinned = entity.isPinned,
            createAt = Instant.ofEpochMilli(entity.createAt),
            updateAt = Instant.ofEpochMilli(entity.updateAt),
            messageNodes = emptyList(),
            isConsolidated = entity.isConsolidated,
        )
    }
    fun getAverageMessageLength(assistantId: Uuid): Flow<Int> {
        return conversationDAO.getConversationsOfAssistant(assistantId.toString())
            .map { list ->
                val recent = list.take(50)
                if (recent.isEmpty()) return@map 100 // Default estimate

                var totalLength = 0L
                var messageCount = 0

                recent.forEach { entity ->
                    try {
                        val nodes = JsonInstant.decodeFromString<List<MessageNode>>(entity.nodes)
                        nodes.forEach { node ->
                            node.messages.forEach { msg ->
                                totalLength += msg.toText().length
                                messageCount++
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (messageCount > 0) {
                    (totalLength / messageCount).toInt()
                } else {
                    100 // Default
                }
            }
    }

}

/**
 * 轻量级的会话查询结果，不包含 nodes 和 suggestions 字段
 */
data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val isConsolidated: Boolean,
)
