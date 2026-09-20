package me.rerere.ai.generation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * File-backed [PortableConversationStore] used by iOS. Android keeps Room.
 * Conversation CRUD goes through this interface on both hosts.
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
    }

    override suspend fun list(): List<PortableConversationRecord> {
        ensureLoaded()
        return conversations.values.toList()
    }

    override suspend fun delete(id: String) {
        ensureLoaded()
        conversations.remove(id)
        persist()
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
