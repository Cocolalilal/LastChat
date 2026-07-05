package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.anyMemoryEnabled
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * WorkManager wrapper around [MemoryEmbeddingBackfill] (§12.4).
 *
 * Enqueued when the configured embedding model changes (see `LastChatApp`). Requires network (the
 * embed call) and battery-not-low. Each run processes a bounded number of batches, then — if ACTIVE
 * nodes still lack the current model's vector — re-enqueues itself with a delay so a large store is
 * worked off steadily across days as the SLEEP budget allows. It stops re-enqueuing once the store is
 * aligned (nothing remaining) so it never busy-loops.
 */
class MemoryEmbeddingBackfillWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val backfill: MemoryEmbeddingBackfill by inject()
    private val settingsStore: SettingsStore by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!settingsStore.settingsFlow.value.anyMemoryEnabled) return@withContext Result.success()
            when (val outcome = backfill.run()) {
                is MemoryEmbeddingBackfill.Outcome.Idle -> Unit
                is MemoryEmbeddingBackfill.Outcome.Ran -> {
                    if (outcome.remaining > 0) {
                        // More to do: continue soon if we made progress, later if we were budget-blocked
                        // (the SLEEP cap resets daily, so there is no point retrying tightly).
                        val delayMinutes = if (outcome.budgetBlocked) 6 * 60L else 20L
                        reschedule(applicationContext, delayMinutes)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            PlatformLog.e(TAG, "embedding backfill failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MemoryEmbeddingBackfill"
        const val WORK_NAME = "memory_embedding_backfill"

        private fun request(delayMinutes: Long = 0) =
            OneTimeWorkRequestBuilder<MemoryEmbeddingBackfillWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .apply { if (delayMinutes > 0) setInitialDelay(delayMinutes, TimeUnit.MINUTES) }
                .build()

        /**
         * Enqueue a fresh backfill (REPLACE): a model change supersedes any in-flight run so the pass
         * always targets the newest model. Called from the `LastChatApp` embedding-model observer.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request())
        }

        /** Self-continuation for a large backfill; KEEP so a model-change REPLACE always wins. */
        private fun reschedule(context: Context, delayMinutes: Long) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request(delayMinutes))
        }
    }
}
