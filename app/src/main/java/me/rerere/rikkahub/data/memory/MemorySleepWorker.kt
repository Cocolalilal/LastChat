package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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

/**
 * Thin WorkManager wrapper around [MemorySleepPass] — the periodic "sleep pass" (§5.4, §9).
 *
 * Scheduling (see `LastChatApp`): a unique **periodic** run every ~12h with only a
 * `battery-not-low` constraint (no network requirement — the deterministic decay/expiry/size stages
 * must age the graph even offline; the model-assisted stages self-skip when the network/budget is
 * unavailable). A failed run simply retries with backoff. The pass may also be run **opportunistically
 * sooner** via [enqueueExpedited] when the merge/contradiction backlog grows past a threshold — that
 * is triggered from the extraction worker, which is where the backlog is produced.
 *
 * This retires the legacy `MemoryConsolidationWorker` for memory-enabled users (§ "Implementation
 * status"): the graph sleep pass now owns decay, consolidation, and bounded growth.
 */
class MemorySleepWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val sleepPass: MemorySleepPass by inject()
    private val settingsStore: SettingsStore by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!settingsStore.settingsFlow.value.anyMemoryEnabled) return@withContext Result.success()
            sleepPass.run()
            Result.success()
        } catch (e: Exception) {
            PlatformLog.e(TAG, "sleep pass failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MemorySleepWorker"

        const val PERIODIC_WORK_NAME = "memory_sleep"
        const val ONESHOT_WORK_NAME = "memory_sleep_oneshot"

        /**
         * Backlog of flagged merge/contradiction pairs beyond which the extraction worker asks for an
         * opportunistic sleep run instead of waiting for the next 12h tick.
         */
        const val OPPORTUNISTIC_BACKLOG_THRESHOLD = 12

        /**
         * Enqueue a one-shot sleep run (deduped, KEEP). Used for the "sooner if the backlog is large"
         * path; no network constraint so the deterministic stages always make progress.
         */
        fun enqueueExpedited(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MemorySleepWorker>()
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }
    }
}
