package me.rerere.ai.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val listVersion = MutableStateFlow(0L)
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

    override fun observeListVersion(): Flow<Long> = listVersion.asStateFlow()

    private fun bumpListVersion() {
        listVersion.value = listVersion.value + 1
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
    }

    private suspend fun persist() {
        val payload = json.encodeToString(
            ConversationFile.serializer(),
            ConversationFile(conversations.values.toList()),
        )
        saveBytes(payload.encodeToByteArray())
    }

    @Serializable
    private data class ConversationFile(
        val conversations: List<PortableConversationRecord> = emptyList(),
    )
}
