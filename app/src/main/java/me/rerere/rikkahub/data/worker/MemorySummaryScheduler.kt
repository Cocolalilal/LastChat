package me.rerere.rikkahub.data.worker

import android.content.Context
import androidx.work.*
import me.rerere.rikkahub.data.memory.MemoryChangeType
import me.rerere.rikkahub.data.memory.MemorySummaryCadence
import me.rerere.rikkahub.service.workManagerOrNull
import java.util.concurrent.TimeUnit

class MemorySummaryScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager: WorkManager? by lazy { appContext.workManagerOrNull() }

    private fun immediateWorkName(assistantId: String) = "memory_summary_immediate_$assistantId"
    private fun debounceWorkName(assistantId: String) = "memory_summary_debounce_$assistantId"
    private fun periodicWorkName(assistantId: String) = "memory_summary_periodic_$assistantId"

    private val networkConnectedConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private val periodicBatterySafeConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun onMemoryChanged(
        assistantId: String,
        cadence: MemorySummaryCadence,
        changeType: MemoryChangeType,
        snippet: String,
        triggeredAtMs: Long,
    ) {
        val wm = workManager ?: return
        when (cadence) {
            MemorySummaryCadence.IMMEDIATE -> {
                wm.cancelUniqueWork(debounceWorkName(assistantId))
                // Micro-coalesce 3s window with REPLACE so rapid multi-memory turns merge into 1 API call
                val data = workDataOf(
                    MemorySummaryWorker.KEY_ASSISTANT_ID to assistantId,
                    MemorySummaryWorker.KEY_TRIGGER_MODE to MemorySummaryWorker.MODE_IMMEDIATE,
                    MemorySummaryWorker.KEY_CHANGE_TYPE to changeType.name,
                    MemorySummaryWorker.KEY_CHANGE_SNIPPET to snippet.take(1_500),
                    MemorySummaryWorker.KEY_TRIGGERED_AT_MS to triggeredAtMs,
                )
                val request = OneTimeWorkRequestBuilder<MemorySummaryWorker>()
                    .setInputData(data)
                    .setInitialDelay(3, TimeUnit.SECONDS)
                    .setConstraints(networkConnectedConstraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag(TAG_MEMORY_SUMMARY)
                    .build()

                wm.enqueueUniqueWork(
                    immediateWorkName(assistantId),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }

            MemorySummaryCadence.DEBOUNCE_10M -> {
                wm.cancelUniqueWork(immediateWorkName(assistantId))
                val data = workDataOf(
                    MemorySummaryWorker.KEY_ASSISTANT_ID to assistantId,
                    MemorySummaryWorker.KEY_TRIGGER_MODE to MemorySummaryWorker.MODE_DEBOUNCE_10M,
                    MemorySummaryWorker.KEY_CHANGE_TYPE to changeType.name,
                    MemorySummaryWorker.KEY_CHANGE_SNIPPET to snippet.take(1_500),
                    MemorySummaryWorker.KEY_TRIGGERED_AT_MS to triggeredAtMs,
                )
                val request = OneTimeWorkRequestBuilder<MemorySummaryWorker>()
                    .setInputData(data)
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .setConstraints(networkConnectedConstraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .addTag(TAG_MEMORY_SUMMARY)
                    .build()

                // REPLACE resets the 10-minute timer on every new memory change across process restarts
                wm.enqueueUniqueWork(
                    debounceWorkName(assistantId),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }

            MemorySummaryCadence.HOURLY -> {
                ensurePeriodicWork(
                    assistantId = assistantId,
                    repeatHours = 1L,
                    flexMinutes = 15L,
                    mode = MemorySummaryWorker.MODE_HOURLY,
                    policy = ExistingPeriodicWorkPolicy.KEEP,
                )
            }

            MemorySummaryCadence.DAILY -> {
                ensurePeriodicWork(
                    assistantId = assistantId,
                    repeatHours = 24L,
                    flexMinutes = 120L,
                    mode = MemorySummaryWorker.MODE_DAILY,
                    policy = ExistingPeriodicWorkPolicy.KEEP,
                )
            }

            MemorySummaryCadence.MANUAL -> {
                cancelAllForAssistant(assistantId)
            }
        }
    }

    /**
     * Called when assistant settings are updated or imported via SettingsMerge / PreferencesStore.
     * Immediately reconciles periodic/debounced schedules with the new cadence setting.
     */
    fun syncCadenceSchedule(assistantId: String, enabled: Boolean, cadence: MemorySummaryCadence) {
        val wm = workManager ?: return
        if (!enabled || cadence == MemorySummaryCadence.MANUAL) {
            cancelAllForAssistant(assistantId)
            return
        }
        when (cadence) {
            MemorySummaryCadence.IMMEDIATE -> {
                wm.cancelUniqueWork(periodicWorkName(assistantId))
                wm.cancelUniqueWork(debounceWorkName(assistantId))
            }
            MemorySummaryCadence.DEBOUNCE_10M -> {
                wm.cancelUniqueWork(periodicWorkName(assistantId))
                wm.cancelUniqueWork(immediateWorkName(assistantId))
            }
            MemorySummaryCadence.HOURLY -> {
                wm.cancelUniqueWork(immediateWorkName(assistantId))
                wm.cancelUniqueWork(debounceWorkName(assistantId))
                ensurePeriodicWork(
                    assistantId = assistantId,
                    repeatHours = 1L,
                    flexMinutes = 15L,
                    mode = MemorySummaryWorker.MODE_HOURLY,
                    policy = ExistingPeriodicWorkPolicy.UPDATE,
                )
            }
            MemorySummaryCadence.DAILY -> {
                wm.cancelUniqueWork(immediateWorkName(assistantId))
                wm.cancelUniqueWork(debounceWorkName(assistantId))
                ensurePeriodicWork(
                    assistantId = assistantId,
                    repeatHours = 24L,
                    flexMinutes = 120L,
                    mode = MemorySummaryWorker.MODE_DAILY,
                    policy = ExistingPeriodicWorkPolicy.UPDATE,
                )
            }
            MemorySummaryCadence.MANUAL -> cancelAllForAssistant(assistantId)
        }
    }

    private fun ensurePeriodicWork(
        assistantId: String,
        repeatHours: Long,
        flexMinutes: Long,
        mode: String,
        policy: ExistingPeriodicWorkPolicy,
    ) {
        val wm = workManager ?: return
        val data = workDataOf(
            MemorySummaryWorker.KEY_ASSISTANT_ID to assistantId,
            MemorySummaryWorker.KEY_TRIGGER_MODE to mode,
        )
        val request = PeriodicWorkRequestBuilder<MemorySummaryWorker>(
            repeatInterval = repeatHours,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = flexMinutes,
            flexTimeIntervalUnit = TimeUnit.MINUTES,
        )
            .setInputData(data)
            .setConstraints(periodicBatterySafeConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(TAG_MEMORY_SUMMARY)
            .build()

        wm.enqueueUniquePeriodicWork(
            periodicWorkName(assistantId),
            policy,
            request,
        )
    }

    fun cancelAllForAssistant(assistantId: String) {
        val wm = workManager ?: return
        wm.cancelUniqueWork(immediateWorkName(assistantId))
        wm.cancelUniqueWork(debounceWorkName(assistantId))
        wm.cancelUniqueWork(periodicWorkName(assistantId))
    }

    private companion object {
        const val TAG_MEMORY_SUMMARY = "lastchat_memory_summary"
    }
}
