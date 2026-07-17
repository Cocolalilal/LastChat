package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
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
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.memory.GraphMemoryRepository
import me.rerere.rikkahub.data.model.resolvedMemoryEngineId
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.common.platform.PlatformLog
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
        try {
            ingestPendingMessages()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            val conversationId = inputData.getString(KEY_CONVERSATION_ID)
            val assistantId = conversationId?.let { id ->
                runCatching { conversationRepository.getConversationById(Uuid.parse(id))?.assistantId?.toString() }.getOrNull()
            }
            val willRetry = runAttemptCount + 1 < MAX_ATTEMPTS
            PlatformLog.w(
                TAG,
                "Graph memory ingest failed attempt=${runAttemptCount + 1} retry=$willRetry error=${throwable::class.simpleName}",
            )
            if (assistantId != null) {
                runCatching {
                    dao.insertActivity(
                        MemoryActivityEntity(
                            assistantId = assistantId,
                            objectId = conversationId,
                            objectKind = "CONVERSATION",
                            event = "ERROR",
                            summary = if (willRetry) {
                                "Graph memory extraction failed and will retry."
                            } else {
                                "Graph memory extraction failed after $MAX_ATTEMPTS attempts."
                            },
                            origin = "CHAT",
                            modelId = settingsStore.settingsFlow.value.summarizerModelId?.toString(),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            if (willRetry) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (throwable.message ?: throwable::class.simpleName.orEmpty()).take(300)))
            }
        }
    }

    private suspend fun ingestPendingMessages(): Result {
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: return Result.failure()
        val conversation = conversationRepository.getConversationById(Uuid.parse(conversationId))
            ?: return Result.success()
        val assistant = settingsStore.settingsFlow.value.getAssistantById(conversation.assistantId)
            ?: return Result.success()
        if (assistant.resolvedMemoryEngineId() != BuiltInMemoryEngines.GRAPH) return Result.success()
        if (!assistant.graphLearnFromChats && !assistant.enableSessionMemory) return Result.success()

        val messages = conversation.currentMessages.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        val cursor = dao.getSessionCursor(conversationId)
        val lastIndex = cursor?.lastMessageFingerprint?.let { fingerprint ->
            messages.indexOfLast { messageFingerprint(it.id.toString(), it.toText()) == fingerprint }
        } ?: -1
        val pending = messages.drop(lastIndex + 1).filter { it.toText().isNotBlank() }
        if (pending.isEmpty()) return Result.success()

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
        return Result.success()
    }

    private fun messageFingerprint(id: String, text: String) = Mem0Hash.md5("$id\n$text")

    companion object {
        private const val TAG = "GraphMemoryIngestWorker"
        private const val MAX_ATTEMPTS = 3
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_ERROR = "error"
    }
}
