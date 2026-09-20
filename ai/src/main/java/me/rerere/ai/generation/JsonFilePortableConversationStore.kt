package me.rerere.ai.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * File-backed [PortableConversationStore] used by iOS. Android keeps Room.
 * Conversation CRUD, paging, FTS-style search, and usage totals go through
 * this interface on both hosts.
 */
class JsonFilePortableConversationStore(
    private val loadBytes: suspend () -> ByteArray?,
    private val saveBytes: suspend (ByteArray) -> Unit,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : PortableConversationStore {
    private val conversations = LinkedHashMap<String, PortableConversationRecord>()
    private val activity = LinkedHashMap<String, PortableDailyActivity>()
    private val listVersion = MutableStateFlow(0L)
    private val activityFlow = MutableStateFlow<List<PortableDailyActivity>>(emptyList())
    private var loaded = false

    override suspend fun get(id: String): PortableConversationRecord? {
        ensureLoaded()
        return conversations[id]
    }

    override suspend fun save(
        conversation: PortableConversationRecord,
        options: PortableSaveOptions,
    ) {
        ensureLoaded()
        conversations[conversation.id] = conversation
        persist()
        bumpListVersion()
    }

    override suspend fun list(): List<PortableConversationRecord> {
        ensureLoaded()
        return conversations.values.sortedByDescending { it.updatedAtEpochMs }
    }

    override suspend fun listByAssistant(
        assistantId: String,
        limit: Int,
    ): List<PortableConversationRecord> {
        ensureLoaded()
        val records = conversations.values
            .filter { it.assistantId == assistantId }
            .sortedByDescending { it.updatedAtEpochMs }
        return if (limit == Int.MAX_VALUE) records else records.take(limit)
    }

    override suspend fun delete(
        id: String,
        options: PortableDeleteOptions,
    ) {
        ensureLoaded()
        conversations.remove(id)
        persist()
        bumpListVersion()
    }

    override suspend fun finalizeDeletion(id: String) {
        ensureLoaded()
        conversations.remove(id)
        persist()
        bumpListVersion()
    }

    override suspend fun usageTotals(): PortableUsageTotals {
        ensureLoaded()
        return PortableConversationQueries.displayUsageTotals(list(), dailyActivity())
    }

    override fun observeListVersion(): Flow<Long> = listVersion.asStateFlow()

    override fun observeUsageTotals(): Flow<PortableUsageTotals> =
        combine(listVersion, activityFlow) { _, days ->
            PortableConversationQueries.displayUsageTotals(
                conversations.values.toList(),
                days,
            )
        }

    override suspend fun recordDailyActivity(date: String, timestampEpochMs: Long) {
        ensureLoaded()
        replaceActivity(PortableConversationQueries.incrementDailyActivity(dailyActivity(), date, timestampEpochMs))
        persist()
    }

    override suspend fun mergeDailyActivity(entries: List<PortableDailyActivity>) {
        ensureLoaded()
        replaceActivity(PortableConversationQueries.mergeDailyActivity(dailyActivity(), entries))
        persist()
    }

    override suspend fun dailyActivity(): List<PortableDailyActivity> {
        ensureLoaded()
        return activityFlow.value
    }

    override fun observeDailyActivity(): Flow<List<PortableDailyActivity>> = activityFlow.asStateFlow()

    private fun replaceActivity(entries: List<PortableDailyActivity>) {
        activity.clear()
        entries.forEach { activity[it.date] = it }
        publishActivity()
        bumpListVersion()
    }

    private fun bumpListVersion() {
        listVersion.value = listVersion.value + 1
    }

    private fun publishActivity() {
        activityFlow.value = activity.values.sortedBy { it.date }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val bytes = loadBytes() ?: return
        val decoded = runCatching {
            json.decodeFromString(ConversationFile.serializer(), bytes.decodeToString())
        }.getOrNull() ?: return
        conversations.clear()
        decoded.conversations.forEach { conversations[it.id] = it }
        activity.clear()
        decoded.dailyActivity.forEach { activity[it.date] = it }
        publishActivity()
    }

    private suspend fun persist() {
        val payload = json.encodeToString(
            ConversationFile.serializer(),
            ConversationFile(
                conversations = conversations.values.toList(),
                dailyActivity = activity.values.sortedBy { it.date },
            ),
        )
        saveBytes(payload.encodeToByteArray())
    }

    @Serializable
    private data class ConversationFile(
        val conversations: List<PortableConversationRecord> = emptyList(),
        val dailyActivity: List<PortableDailyActivity> = emptyList(),
    )
}
