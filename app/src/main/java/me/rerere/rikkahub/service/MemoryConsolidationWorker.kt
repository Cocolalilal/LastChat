package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.memory.TemporalMemoryRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Compatibility entry point for existing WorkManager requests and UI actions.
 *
 * The old implementation implicitly used the currently selected assistant and could therefore
 * attribute a forced conversation to the wrong character. Memory v3 always resolves the owner from
 * the conversation and delegates to a scope-keyed ingest job.
 */
class MemoryConsolidationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val temporalMemoryRepository: TemporalMemoryRepository by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { enqueueScopedWork() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { throwable ->
                    PlatformLog.w(TAG, "Unable to queue scoped memory work: ${throwable.message}")
                    if (runAttemptCount < 2) Result.retry() else Result.failure()
                },
            )
    }

    private suspend fun enqueueScopedWork() {
        val settings = settingsStore.settingsFlow.value
        val forcedConversationId = inputData.getString(KEY_FORCE_CONVERSATION_ID)
        if (forcedConversationId != null) {
            val conversation = conversationRepository.getConversationById(Uuid.parse(forcedConversationId)) ?: return
            val assistant = settings.getAssistantById(conversation.assistantId) ?: return
            if (!assistant.enableMemory) return
            temporalMemoryRepository.importLegacyMemories(assistant.id.toString())
            TemporalMemoryIngestWorker.enqueue(
                applicationContext,
                assistant.id.toString(),
                conversation.id.toString(),
            )
            return
        }

        val fullScan = inputData.getBoolean(KEY_FULL_SCAN, false)
        settings.assistants
            .asSequence()
            .filter { it.enableMemory }
            .forEach { assistant ->
                temporalMemoryRepository.importLegacyMemories(assistant.id.toString())
                val conversations = if (fullScan) {
                    conversationRepository.getConversationsOfAssistant(assistant.id).first()
                } else {
                    conversationRepository.getRecentConversations(assistant.id, 10)
                }
                conversations.forEach { conversation ->
                    TemporalMemoryIngestWorker.enqueue(
                        applicationContext,
                        assistant.id.toString(),
                        conversation.id.toString(),
                        indexOnly = fullScan,
                    )
                }
            }
    }

    companion object {
        private const val TAG = "MemoryConsolidation"
        const val KEY_FORCE_CONVERSATION_ID = "FORCE_CONVERSATION_ID"
        const val KEY_FULL_SCAN = "FULL_SCAN"
    }
}
