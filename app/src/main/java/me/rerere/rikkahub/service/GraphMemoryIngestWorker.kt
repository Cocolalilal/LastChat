package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.memory.BuiltInMemoryEngines
import me.rerere.ai.memory.Mem0Hash
import me.rerere.ai.memory.MemoryInputMessage
import me.rerere.ai.memory.MemoryOrigin
import me.rerere.ai.memory.MemoryScope
import me.rerere.ai.memory.MemoryScopeKind
import me.rerere.ai.memory.MemorySpeaker
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.dao.MemoryGraphDao
import me.rerere.rikkahub.data.db.entity.SessionMemoryCursorEntity
import me.rerere.rikkahub.data.memory.GraphMemoryRepository
import me.rerere.rikkahub.data.model.resolvedMemoryEngineId
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GraphMemoryIngestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val graphRepository: GraphMemoryRepository by inject()
    private val dao: MemoryGraphDao by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: return@withContext Result.failure()
        val conversation = conversationRepository.getConversationById(Uuid.parse(conversationId))
            ?: return@withContext Result.success()
        val assistant = settingsStore.settingsFlow.value.getAssistantById(conversation.assistantId)
            ?: return@withContext Result.success()
        if (assistant.resolvedMemoryEngineId() != BuiltInMemoryEngines.GRAPH) return@withContext Result.success()
        if (!assistant.graphLearnFromChats && !assistant.enableSessionMemory) return@withContext Result.success()

        val messages = conversation.currentMessages.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        val cursor = dao.getSessionCursor(conversationId)
        val lastIndex = cursor?.lastMessageFingerprint?.let { fingerprint ->
            messages.indexOfLast { messageFingerprint(it.id.toString(), it.toText()) == fingerprint }
        } ?: -1
        val pending = messages.drop(lastIndex + 1).filter { it.toText().isNotBlank() }
        if (pending.isEmpty()) return@withContext Result.success()

        val input = pending.map { message ->
            MemoryInputMessage(
                role = if (message.role == MessageRole.USER) MemorySpeaker.USER else MemorySpeaker.ASSISTANT,
                content = message.toText(),
                messageId = message.id.toString(),
                observedAtEpochMillis = message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            )
        }
        if (assistant.enableSessionMemory) {
            graphRepository.ingest(
                scope = MemoryScope(assistant.id.toString(), MemoryScopeKind.SESSION, conversationId),
                messages = input,
                origin = MemoryOrigin.SESSION,
            )
        }
        if (assistant.graphLearnFromChats) {
            graphRepository.ingest(
                scope = MemoryScope(assistant.id.toString()),
                messages = input,
                origin = MemoryOrigin.CHAT,
            )
        }
        val last = pending.last()
        dao.upsertSessionCursor(
            SessionMemoryCursorEntity(
                conversationId = conversationId,
                assistantId = assistant.id.toString(),
                lastMessageFingerprint = messageFingerprint(last.id.toString(), last.toText()),
                pendingCount = 0,
                lastQueuedAt = cursor?.lastQueuedAt ?: System.currentTimeMillis(),
                lastProcessedAt = System.currentTimeMillis(),
            ),
        )
        Result.success()
    }

    private fun messageFingerprint(id: String, text: String) = Mem0Hash.md5("$id\n$text")

    companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
    }
}
