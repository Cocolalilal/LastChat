package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import me.rerere.ai.generation.PortableBackgroundTask
import me.rerere.ai.generation.PortableTaskHandler
import me.rerere.ai.generation.PortableTaskRequest
import me.rerere.ai.generation.PortableTaskScheduler
import java.util.concurrent.TimeUnit

class WorkManagerPortableTaskScheduler(
    private val context: Context,
) : PortableTaskScheduler {
    override fun register(task: PortableBackgroundTask, handler: PortableTaskHandler) {
        // WorkManager workers are the Android handlers.
    }

    override fun enqueue(request: PortableTaskRequest) {
        val workManager = context.workManagerOrNull() ?: return
        when (request.task) {
            PortableBackgroundTask.SPONTANEOUS_MESSAGES -> {
                val interval = (request.intervalMs ?: (SPONTANEOUS_WORK_INTERVAL_MINUTES * 60_000L))
                    .coerceAtLeast(15L * 60_000L)
                workManager.enqueueUniquePeriodicWork(
                    request.uniqueName.ifBlank { SPONTANEOUS_WORK_NAME },
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<SpontaneousWorker>(
                        interval,
                        TimeUnit.MILLISECONDS,
                    ).setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build(),
                )
            }
            PortableBackgroundTask.SCHEDULED_MESSAGES -> {
                val assistantId = request.extras[ScheduledMessageWorkSpec.KEY_ASSISTANT_ID] ?: return
                val conversationId = request.extras[ScheduledMessageWorkSpec.KEY_CONVERSATION_ID] ?: return
                val reason = request.extras[ScheduledMessageWorkSpec.KEY_REASON].orEmpty()
                val createdAt = request.extras[ScheduledMessageWorkSpec.KEY_CREATED_AT]?.toLongOrNull()
                    ?: System.currentTimeMillis()
                val scheduledAt = request.extras[ScheduledMessageWorkSpec.KEY_SCHEDULED_AT]?.toLongOrNull()
                    ?: (createdAt + request.delayMs)
                val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                    .setInitialDelay(request.delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .setInputData(
                        ScheduledMessageWorkSpec.buildInputData(
                            assistantId = assistantId,
                            conversationId = conversationId,
                            reason = reason,
                            createdAtMillis = createdAt,
                            scheduledAtMillis = scheduledAt,
                        ),
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
                workManager.enqueueUniqueWork(
                    request.uniqueName,
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }
            PortableBackgroundTask.MEMORY_CONSOLIDATION -> {
                if (request.periodic) {
                    val interval = (request.intervalMs ?: (6L * 60L * 60L * 1000L))
                        .coerceAtLeast(15L * 60_000L)
                    workManager.enqueueUniquePeriodicWork(
                        request.uniqueName.ifBlank { PERIODIC_MEMORY_WORK_NAME },
                        ExistingPeriodicWorkPolicy.UPDATE,
                        PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(
                            interval,
                            TimeUnit.MILLISECONDS,
                        ).build(),
                    )
                    return
                }
                val conversationId = request.extras[KEY_CONVERSATION_ID]
                if (conversationId.isNullOrBlank()) {
                    workManager.enqueueUniqueWork(
                        request.uniqueName.ifBlank { CATCH_UP_WORK_NAME },
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<MemoryConsolidationWorker>().build(),
                    )
                    return
                }
                val workRequest = OneTimeWorkRequestBuilder<MemoryConsolidationWorker>()
                    .setInputData(workDataOf(KEY_CONVERSATION_ID to conversationId))
                    .setInitialDelay(request.delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .build()
                workManager.enqueueUniqueWork(
                    request.uniqueName,
                    ExistingWorkPolicy.REPLACE,
                    workRequest,
                )
            }
        }
    }

    override fun cancel(uniqueName: String) {
        context.workManagerOrNull()?.cancelUniqueWork(uniqueName)
    }

    override suspend fun run(
        task: PortableBackgroundTask,
        request: PortableTaskRequest,
    ): Boolean {
        enqueue(request)
        return true
    }

    companion object {
        const val KEY_CONVERSATION_ID = "CONVERSATION_ID"
        const val CATCH_UP_WORK_NAME = "memory_consolidation_catch_up"
        const val CONVERSATION_WORK_PREFIX = "memory_consolidation_conversation_"
        const val PERIODIC_MEMORY_WORK_NAME = "memory_consolidation_automatic"
    }
}
