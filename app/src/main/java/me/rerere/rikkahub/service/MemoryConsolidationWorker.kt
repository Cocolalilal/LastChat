package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
 * Durable reconciliation entry point for temporal-memory work.
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
        val fullScan = inputData.getBoolean(KEY_FULL_SCAN, false)
        settings.assistants
            .asSequence()
            .filter { it.enableMemory }
            .forEach { assistant ->
                temporalMemoryRepository.importLegacyMemories(assistant.id.toString())
                val conversations = if (fullScan) {
                    conversationRepository.getConversationsOfAssistant(assistant.id).first()
                } else {
                    // Oldest-first bounded reconciliation prevents a repeatedly failing or very
                    // active chat from starving older work. Successful ingest clears the flag.
                    conversationRepository.getPendingMemoryConversations(assistant.id, RECONCILE_BATCH_SIZE)
                }
                conversations.forEach { conversation ->
                    TemporalMemoryIngestWorker.enqueueReconciliation(
                        applicationContext,
                        assistant.id.toString(),
                        conversation.id.toString(),
                        indexOnly = fullScan,
                    )
                }
            }
    }

    companion object {
        private const val TAG = "MemoryMaintenance"
        const val KEY_FULL_SCAN = "FULL_SCAN"
        private const val RECONCILE_BATCH_SIZE = 25
    }
}
