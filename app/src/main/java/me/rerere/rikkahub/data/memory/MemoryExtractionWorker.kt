package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.anyMemoryEnabled
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/**
 * Runs the [MemoryEncoder] for one conversation, off the generation hot path (§5.1, §7.2).
 *
 * Deduped per conversation by unique work name. Works off any backlog oldest-first (each pass
 * advances the watermark by at most one chunk) but stops as soon as the encoder skips or defers, so
 * budget exhaustion just leaves the watermark where it is — memory is delayed, never lost.
 * Extraction failures never surface as chat errors: they are logged and retried later.
 */
class MemoryExtractionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val encoder: MemoryEncoder by inject()
    private val conversationRepo: ConversationRepository by inject()
    private val settingsStore: SettingsStore by inject()
    private val graphRepository: MemoryGraphRepository by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val conversationId = inputData.getString(KEY_CONVERSATION_ID)
                ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                ?: return@withContext Result.success()

            val settings = settingsStore.settingsFlow.value
            if (!settings.anyMemoryEnabled) return@withContext Result.success()

            var iterations = 0
            while (iterations < MAX_ITERATIONS) {
                iterations++
                val conversation = conversationRepo.getConversationById(conversationId) ?: break
                val assistant = settings.assistants.find { it.id == conversation.assistantId } ?: break
                when (encoder.encode(conversation, assistant, settings)) {
                    is MemoryEncoder.Outcome.Encoded -> continue // work off any remaining backlog
                    MemoryEncoder.Outcome.Skipped, MemoryEncoder.Outcome.Deferred -> break
                }
            }

            // Opportunistic sleep: extraction is what produces the merge/contradiction backlog, so if
            // it has grown large, ask for a sleep run sooner than the next 12h tick (§5.4).
            runCatching {
                if (graphRepository.countPendingAdjudications() >= MemorySleepWorker.OPPORTUNISTIC_BACKLOG_THRESHOLD) {
                    MemorySleepWorker.enqueueExpedited(applicationContext)
                }
            }
            Result.success()
        } catch (e: Exception) {
            PlatformLog.e(TAG, "extraction worker failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MemoryExtractionWorker"
        private const val MAX_ITERATIONS = 8
        const val KEY_CONVERSATION_ID = "conversation_id"

        fun uniqueName(conversationId: String): String = "memory_extract_$conversationId"
    }
}
