package me.rerere.rikkahub.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.ai.MemorySummaryService
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MemorySummaryDao
import me.rerere.rikkahub.data.memory.MemorySummaryCadence
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MemorySummaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val memorySummaryService: MemorySummaryService by inject()
    private val memorySummaryDao: MemorySummaryDao by inject()
    private val memoryDAO: MemoryDAO by inject()
    private val settingsStore: SettingsStore by inject()

    override suspend fun doWork(): Result {
        val assistantId = inputData.getString(KEY_ASSISTANT_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val triggerMode = inputData.getString(KEY_TRIGGER_MODE) ?: MODE_PERIODIC
        val changeSnippet = inputData.getString(KEY_CHANGE_SNIPPET)
        val triggeredAtMs = inputData.getLong(KEY_TRIGGERED_AT_MS, System.currentTimeMillis())

        return try {
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getAssistantById(assistantId) ?: return Result.success()
            if (!assistant.enableMemory || !assistant.enableMemorySummary) {
                return Result.success()
            }
            if (assistant.memorySummaryCadence == MemorySummaryCadence.MANUAL) {
                return Result.success()
            }

            // Short-circuit periodic runs when no memories changed since last sync (saves RAM, battery, API tokens)
            val summaryEntity = memorySummaryDao.getSummaryOnce(assistantId)
            val isPeriodic = triggerMode == MODE_HOURLY || triggerMode == MODE_DAILY || triggerMode == MODE_PERIODIC
            if (isPeriodic && summaryEntity != null) {
                val hasNewMemories = memoryDAO.getMemoriesCreatedAfter(
                    assistantId = assistantId,
                    sinceTimestamp = summaryEntity.lastSyncedAt,
                ).isNotEmpty()
                if (summaryEntity.dirtySince <= 0L && !hasNewMemories) {
                    return Result.success()
                }
            }

            val jobStartMs = maxOf(triggeredAtMs, System.currentTimeMillis())
            val incrementalOnly = (triggerMode == MODE_IMMEDIATE)

            val committed = memorySummaryService.runBackgroundConsolidation(
                assistantId = assistantId,
                incrementalOnly = incrementalOnly,
                changeSnippet = changeSnippet,
                jobStartedAtMs = jobStartMs,
            )

            if (committed) {
                Result.success()
            } else if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Log.w(TAG, "Max retries reached after revision conflicts for $assistantId")
                Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "MemorySummaryWorker failed (attempt $runAttemptCount) for $assistantId", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "MemorySummaryWorker"
        private const val MAX_RETRY_ATTEMPTS = 3

        const val KEY_ASSISTANT_ID = "assistant_id"
        const val KEY_TRIGGER_MODE = "trigger_mode"
        const val KEY_CHANGE_TYPE = "change_type"
        const val KEY_CHANGE_SNIPPET = "change_snippet"
        const val KEY_TRIGGERED_AT_MS = "triggered_at_ms"

        const val MODE_IMMEDIATE = "IMMEDIATE"
        const val MODE_DEBOUNCE_10M = "DEBOUNCE_10M"
        const val MODE_HOURLY = "HOURLY"
        const val MODE_DAILY = "DAILY"
        const val MODE_PERIODIC = "PERIODIC"
    }
}
