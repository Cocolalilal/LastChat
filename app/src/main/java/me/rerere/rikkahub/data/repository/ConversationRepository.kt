package me.rerere.rikkahub.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.DailyActivityDAO
import me.rerere.rikkahub.data.db.dao.UsageStatsDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid


class ConversationRepository(
    private val context: Context,
    private val conversationDAO: ConversationDAO,
    private val chatEpisodeDAO: me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO,
    private val dailyActivityDAO: DailyActivityDAO,
    private val usageStatsDAO: UsageStatsDAO,
    private val chatAttachmentRepository: ChatAttachmentRepository,
) {
    companion object {
        private const val TAG = "ConversationRepository"
        private const val PAGE_SIZE = 20
        private const val INITIAL_LOAD_SIZE = 40
        private val ISO_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
    }

    private data class HistoricalUsageTotals(
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val cachedTokens: Long = 0L,
        val selectedMessageCount: Int = 0
    ) {
        operator fun plus(other: HistoricalUsageTotals): HistoricalUsageTotals = HistoricalUsageTotals(
            inputTokens = inputTokens + other.inputTokens,
            outputTokens = outputTokens + other.outputTokens,
            cachedTokens = cachedTokens + other.cachedTokens,
            selectedMessageCount = selectedMessageCount + other.selectedMessageCount
        )
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
            .map { list ->
                list.map { entity ->
                    conversationEntityToConversation(entity)
                }.filter { conversation ->
                    conversation.title.contains(titleKeyword, ignoreCase = true) ||
                        conversation.messageNodes.any { node ->
                            node.currentMessage.toText().contains(titleKeyword, ignoreCase = true)
                        }
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
        }.filter { conversation ->
            if (conversation.title.contains(titleKeyword, ignoreCase = true)) {
                true
            } else {
                val fullEntity = withContext(Dispatchers.IO) {
                    conversationDAO.getConversationById(conversation.id.toString())
                }
                if (fullEntity != null) {
                    val messageNodes = runCatching {
                        JsonInstant.decodeFromString<List<MessageNode>>(fullEntity.nodes)
                    }.getOrNull() ?: emptyList()
                    
                    messageNodes.any { node ->
                        node.currentMessage.toText().contains(titleKeyword, ignoreCase = true)
                    }
                } else {
                    false
                }
            }
        }
    }

    fun searchConversationsOfAssistant(assistantId: Uuid, titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversationsOfAssistant(assistantId.toString(), titleKeyword)
            .map { list ->
                list.map { entity ->
                    conversationEntityToConversation(entity)
                }.filter { conversation ->
                    conversation.title.contains(titleKeyword, ignoreCase = true) ||
                        conversation.messageNodes.any { node ->
                            node.currentMessage.toText().contains(titleKeyword, ignoreCase = true)
                        }
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
        }.filter { conversation ->
            if (conversation.title.contains(titleKeyword, ignoreCase = true)) {
                true
            } else {
                val fullEntity = withContext(Dispatchers.IO) {
                    conversationDAO.getConversationById(conversation.id.toString())
                }
                if (fullEntity != null) {
                    val messageNodes = runCatching {
                        JsonInstant.decodeFromString<List<MessageNode>>(fullEntity.nodes)
                    }.getOrNull() ?: emptyList()
                    
                    messageNodes.any { node ->
                        node.currentMessage.toText().contains(titleKeyword, ignoreCase = true)
                    }
                } else {
                    false
                }
            }
        }
    }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString())
        return if (entity != null) {
            conversationEntityToConversation(entity)
        } else null
    }

    suspend fun insertConversation(conversation: Conversation) {
        val syncedConversation = chatAttachmentRepository.syncConversationAttachments(conversation)
        conversationDAO.insert(
            conversationToConversationEntity(syncedConversation)
        )
        // Increment persistent conversation counter
        try { usageStatsDAO.incrementConversations() } catch (_: Exception) {}
    }

    suspend fun updateConversation(conversation: Conversation, preserveConsolidation: Boolean = false) {
        val syncedConversation = chatAttachmentRepository.syncConversationAttachments(conversation)
        // Invalidation Logic: If a consolidated conversation is updated (e.g. new message),
        // we must invalidate the old memory episode to allow re-consolidation.
        if (shouldInvalidateConsolidation(syncedConversation, preserveConsolidation)) {
            val updatedConversation = syncedConversation.copy(isConsolidated = false)

            conversationDAO.update(
                conversationToConversationEntity(updatedConversation)
            )

            // Delete the old episode based on conversation ID if possible.
            // If deletion by ID returns 0 (e.g. legacy episode without conversationId),
            // fallback to best-effort deletion based on time range.
            val deletedCount = chatEpisodeDAO.deleteEpisodeByConversationId(conversation.id.toString())
            if (deletedCount == 0) {
                chatEpisodeDAO.deleteEpisodeByTimeRange(
                    assistantId = syncedConversation.assistantId.toString(),
                    startTime = syncedConversation.createAt.toEpochMilli(),
                    endTime = Long.MAX_VALUE
                )
            }
        } else {
            conversationDAO.update(
                conversationToConversationEntity(syncedConversation)
            )
        }
    }

    suspend fun deleteConversation(conversation: Conversation, deleteFiles: Boolean = true) {
        conversationDAO.delete(
            conversationToConversationEntity(conversation)
        )
        chatEpisodeDAO.deleteEpisodeByConversationId(conversation.id.toString())
        chatAttachmentRepository.removeConversationReferences(conversation.id)
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        getConversationsOfAssistant(assistantId).first().forEach { conversation ->
            deleteConversation(conversation)
        }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        // Extract last used model ID from the last assistant message
        val lastModelId = conversation.messageNodes
            .asReversed()
            .firstOrNull { node ->
                node.messages.any { it.role == me.rerere.ai.core.MessageRole.ASSISTANT && it.modelId?.toString()?.isNotBlank() == true }
            }
            ?.messages
            ?.lastOrNull { it.role == me.rerere.ai.core.MessageRole.ASSISTANT && it.modelId?.toString()?.isNotBlank() == true }
            ?.modelId
            ?.toString()
            ?: ""

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
            enabledLorebookIds = conversation.enabledLorebookIds
                ?.let { JsonInstant.encodeToString(it.map { id -> id.toString() }) }
                .orEmpty(),
            contextSummary = conversation.contextSummary ?: "",
            contextSummaryUpToIndex = conversation.contextSummaryUpToIndex,
            lastPruneTime = conversation.lastPruneTime,
            lastPruneMessageCount = conversation.lastPruneMessageCount,
            lastRefreshTime = conversation.lastRefreshTime,
            isFork = conversation.isFork,
            lastModelId = lastModelId,
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
        val enabledLorebookIds = if (conversationEntity.enabledLorebookIds.isBlank()) {
            null
        } else {
            try {
                JsonInstant.decodeFromString<List<String>>(conversationEntity.enabledLorebookIds)
                    .map { Uuid.parse(it) }
                    .toSet()
            } catch (e: Exception) {
                null
            }
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
            enabledLorebookIds = enabledLorebookIds,
            contextSummary = conversationEntity.contextSummary.takeIf { it.isNotBlank() },
            contextSummaryUpToIndex = conversationEntity.contextSummaryUpToIndex,
            lastPruneTime = conversationEntity.lastPruneTime,
            lastPruneMessageCount = conversationEntity.lastPruneMessageCount,
            lastRefreshTime = conversationEntity.lastRefreshTime,
            isFork = conversationEntity.isFork,
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

    suspend fun updateTitle(conversationId: Uuid, title: String, updateAt: Instant) {
        conversationDAO.updateTitle(
            id = conversationId.toString(),
            title = title,
            updateAt = updateAt.toEpochMilli(),
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

    suspend fun hasSuccessfulAssistantReply(): Boolean = withContext(Dispatchers.IO) {
        conversationDAO.hasUserAssistantConversation()
    }

    /**
     * Get the most frequently used model ID for an assistant using the last_model_id column.
     * Returns the model UUID as string, or null if no model found.
     */
    fun getMostUsedModelIdForAssistantFlow(assistantId: String): Flow<String?> = 
        kotlinx.coroutines.flow.flow { emit(conversationDAO.getMostUsedModelIdForAssistant(assistantId)) }

    // ===== Daily Activity Tracking (for the activity heatmap) =====
    
    /**
     * Record that the user was active today (sent a message).
     * This persists independently of conversations so the activity
     * heatmap can stay useful even when chats are deleted.
     */
    suspend fun recordDailyActivity() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val timestamp = System.currentTimeMillis()
        val delaysMs = longArrayOf(40L, 120L, 240L)

        repeat(delaysMs.size + 1) { attempt ->
            try {
                dailyActivityDAO.recordActivity(today, timestamp)
                return
            } catch (e: SQLiteException) {
                if (!e.isTransientSqliteFailure() || attempt == delaysMs.size) {
                    Log.w(TAG, "recordDailyActivity: ignored non-critical SQLite failure", e)
                    return
                }
                delay(delaysMs[attempt])
            }
        }
    }
    
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
     * Reconstruct missing historical activity days from conversation history.
     * This is safe to run repeatedly and fills gaps caused by imports/restores.
     */
    suspend fun backfillDailyActivityFromConversationHistoryIfNeeded() {
        val totalConversations = conversationDAO.getConversationCountFlow().first()
        if (totalConversations == 0) return

        val existingDates = dailyActivityDAO.getAllDatesFlow().first().toHashSet()
        val dateCounts = mutableMapOf<String, Int>()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        val batchSize = 5
        var offset = 0
        while (offset < totalConversations) {
            val batch = conversationDAO.getBackfillDataBatch(limit = batchSize, offset = offset)
            if (batch.isEmpty()) break

            batch.forEach { entity ->
                val fallbackDate = Instant.ofEpochMilli(entity.createAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter)

                val selectedDates = extractSelectedMessageDates(entity.nodes)
                if (selectedDates.isEmpty()) {
                    dateCounts[fallbackDate] = (dateCounts[fallbackDate] ?: 0) + 1
                } else {
                    selectedDates.forEach { date ->
                        dateCounts[date] = (dateCounts[date] ?: 0) + 1
                    }
                }
            }
            offset += batchSize
        }

        if (dateCounts.isEmpty()) return
        if (dateCounts.keys.all { it in existingDates }) return

        dateCounts.forEach { (date, count) ->
            val timestamp = runCatching {
                LocalDate.parse(date, formatter)
                    .atStartOfDay()
                    .toEpochSecond(java.time.ZoneOffset.UTC) * 1000
            }.getOrDefault(System.currentTimeMillis())
            dailyActivityDAO.insertBackfilledActivityIfMissing(
                date = date,
                count = count,
                timestamp = timestamp
            )
            dailyActivityDAO.mergeBackfilledActivity(
                date = date,
                count = count,
                timestamp = timestamp
            )
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
            isFork = entity.isFork,
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

    // ===== Persistent Usage Stats =====
    
    /** Initialize the usage stats row if it doesn't exist yet */
    suspend fun initUsageStats() {
        usageStatsDAO.initIfEmpty()
    }
    
    /** Get usage stats as a Flow for reactive UI */
    fun getUsageStatsFlow(): Flow<UsageStatsEntity?> = usageStatsDAO.getStatsFlow()

    /**
     * 12-month rolling usage stats for the statistics page.
     * Window is calendar-aligned: current month + previous 11 months.
     */
    fun getUsageStatsLast12MonthsFlow(): Flow<UsageStatsEntity> = combine(
        conversationDAO.getAll(),
        dailyActivityDAO.getAllActivityFlow(),
        usageStatsDAO.getStatsFlow()
    ) { allConversations, allActivity, persistedStats ->
        val today = LocalDate.now()
        val windowStart = today.withDayOfMonth(1).minusMonths(11)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        val conversationCountInWindow = allConversations.count { entity ->
            val createdDate = Instant.ofEpochMilli(entity.createAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            !createdDate.isBefore(windowStart) && !createdDate.isAfter(today)
        }.toLong()

        val usageTotalsInWindow = allConversations.fold(HistoricalUsageTotals()) { acc, entity ->
            val fallbackDate = Instant.ofEpochMilli(entity.createAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            acc + extractHistoricalUsage(
                nodesJson = entity.nodes,
                windowStart = windowStart,
                windowEnd = today,
                fallbackDate = fallbackDate
            )
        }

        val messagesFromActivityInWindow = allActivity.sumOf { entity ->
            val date = runCatching { LocalDate.parse(entity.date, formatter) }.getOrNull()
            if (date != null && !date.isBefore(windowStart) && !date.isAfter(today)) {
                entity.messageCount.toLong()
            } else {
                0L
            }
        }

        val bestMessageCount = maxOf(
            messagesFromActivityInWindow,
            usageTotalsInWindow.selectedMessageCount.toLong()
        )

        UsageStatsEntity(
            id = 1,
            totalConversations = conversationCountInWindow,
            totalMessages = bestMessageCount,
            inputTokens = usageTotalsInWindow.inputTokens,
            outputTokens = usageTotalsInWindow.outputTokens,
            cachedTokens = usageTotalsInWindow.cachedTokens,
            appLaunches = persistedStats?.appLaunches ?: 0L
        )
    }

    /**
     * One-time backfill for legacy/imported databases where usage_stats may exist but lacks token data.
     * Keeps counters monotonic by never decreasing existing values.
     */
    suspend fun backfillUsageStatsFromHistoryIfNeeded() {
        usageStatsDAO.initIfEmpty()
        val currentStats = usageStatsDAO.getStats() ?: return
        val conversationCount = conversationDAO.getConversationCountFlow().first().toLong()

        if (conversationCount <= 0L) return

        val needsConversationBackfill = currentStats.totalConversations < conversationCount
        val hasNoTokenHistory = currentStats.inputTokens <= 0L &&
            currentStats.outputTokens <= 0L &&
            currentStats.cachedTokens <= 0L

        if (!needsConversationBackfill && !hasNoTokenHistory) return

        val batchSize = 5
        var offset = 0
        var historicalTotals = HistoricalUsageTotals()
        
        while (offset < conversationCount) {
            val batch = conversationDAO.getBackfillDataBatch(limit = batchSize, offset = offset)
            if (batch.isEmpty()) break
            
            val batchTotals = batch.fold(HistoricalUsageTotals()) { acc, entity ->
                acc + extractHistoricalUsage(entity.nodes)
            }
            historicalTotals += batchTotals
            offset += batchSize
        }

        val messagesFromActivity = runCatching { dailyActivityDAO.getTotalMessageCountFlow().first() }
            .getOrDefault(0L)
        val bestMessageCount = maxOf(messagesFromActivity, historicalTotals.selectedMessageCount.toLong())

        usageStatsDAO.overwriteCoreStats(
            totalConversations = maxOf(currentStats.totalConversations, conversationCount),
            totalMessages = maxOf(currentStats.totalMessages, bestMessageCount),
            inputTokens = maxOf(currentStats.inputTokens, historicalTotals.inputTokens),
            outputTokens = maxOf(currentStats.outputTokens, historicalTotals.outputTokens),
            cachedTokens = maxOf(currentStats.cachedTokens, historicalTotals.cachedTokens)
        )
    }
    
    /** Get all daily activity entries for heatmap */
    fun getAllDailyActivityFlow() = dailyActivityDAO.getAllActivityFlow()
    
    /** Increment the persistent conversation counter */
    suspend fun incrementConversationCount() {
        usageStatsDAO.incrementConversations()
    }
    
    /** Add token usage to persistent cumulative counters */
    suspend fun addTokenUsage(inputTokens: Long, outputTokens: Long, cachedTokens: Long) {
        usageStatsDAO.addTokenUsage(inputTokens, outputTokens, cachedTokens)
    }
    
    /** Increment persistent message counter */
    suspend fun incrementMessageCount(count: Int = 1) {
        usageStatsDAO.incrementMessages(count)
    }
    
    /** Increment app launch counter */
    suspend fun incrementAppLaunches() {
        usageStatsDAO.incrementAppLaunches()
    }

    private fun extractHistoricalUsage(
        nodesJson: String,
        windowStart: LocalDate? = null,
        windowEnd: LocalDate? = null,
        fallbackDate: LocalDate? = null
    ): HistoricalUsageTotals {
        val root = runCatching { JsonInstant.parseToJsonElement(nodesJson) }.getOrNull()
        if (root !is JsonArray) return HistoricalUsageTotals()

        var inputTokens = 0L
        var outputTokens = 0L
        var cachedTokens = 0L
        var selectedMessageCount = 0

        root.forEach { nodeElement ->
            val node = nodeElement as? JsonObject ?: return@forEach
            val messages = node["messages"] as? JsonArray ?: return@forEach
            if (messages.isEmpty()) return@forEach

            val selectedIndex = node["selectIndex"]?.jsonPrimitiveOrNull?.intOrNull ?: 0
            val selectedMessage = (messages.getOrNull(selectedIndex) ?: messages.lastOrNull()) as? JsonObject
                ?: return@forEach

            val messageDate = parseDateString(
                selectedMessage["createdAt"]?.jsonPrimitiveOrNull?.contentOrNull
            )?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
                ?: fallbackDate
            if (windowStart != null) {
                if (messageDate == null || messageDate.isBefore(windowStart)) return@forEach
            }
            if (windowEnd != null) {
                if (messageDate == null || messageDate.isAfter(windowEnd)) return@forEach
            }

            selectedMessageCount += 1

            val usage = selectedMessage["usage"] as? JsonObject ?: return@forEach
            inputTokens += usage.readUsageValue("promptTokens", "inputTokens", "prompt_tokens", "input_tokens")
            outputTokens += usage.readUsageValue(
                "completionTokens",
                "outputTokens",
                "completion_tokens",
                "output_tokens"
            )
            cachedTokens += usage.readUsageValue("cachedTokens", "cached_tokens")
        }

        return HistoricalUsageTotals(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedTokens = cachedTokens,
            selectedMessageCount = selectedMessageCount
        )
    }

    private fun JsonObject.readUsageValue(vararg keys: String): Long {
        keys.forEach { key ->
            val value = this[key]?.jsonPrimitiveOrNull?.contentOrNull?.toLongOrNull()
            if (value != null) return value
        }
        return 0L
    }

    private fun extractSelectedMessageDates(nodesJson: String): List<String> {
        val root = runCatching { JsonInstant.parseToJsonElement(nodesJson) }.getOrNull()
        if (root !is JsonArray) return emptyList()

        return root.mapNotNull { nodeElement ->
            val node = nodeElement as? JsonObject ?: return@mapNotNull null
            val messages = node["messages"] as? JsonArray ?: return@mapNotNull null
            if (messages.isEmpty()) return@mapNotNull null

            val selectedIndex = node["selectIndex"]?.jsonPrimitiveOrNull?.intOrNull ?: 0
            val selectedMessage = (messages.getOrNull(selectedIndex) ?: messages.lastOrNull()) as? JsonObject
                ?: return@mapNotNull null
            parseDateString(selectedMessage["createdAt"]?.jsonPrimitiveOrNull?.contentOrNull)
        }
    }

    private fun parseDateString(raw: String?): String? {
        val match = raw?.let { ISO_DATE_REGEX.find(it)?.value } ?: return null
        return runCatching { LocalDate.parse(match, DateTimeFormatter.ISO_LOCAL_DATE).format(DateTimeFormatter.ISO_LOCAL_DATE) }
            .getOrNull()
    }

    private fun SQLiteException.isTransientSqliteFailure(): Boolean {
        if (this is SQLiteDatabaseLockedException) return true
        val text = "${message.orEmpty()} ${cause?.message.orEmpty()}".lowercase()
        return "database is locked" in text ||
            "database is busy" in text ||
            "eagain" in text ||
            "try again" in text ||
            "os error - 11" in text
    }
}

internal fun shouldInvalidateConsolidation(
    conversation: Conversation,
    preserveConsolidation: Boolean,
): Boolean = conversation.isConsolidated && !preserveConsolidation

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
    val isFork: Boolean,
)
