package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/** Durable, unique background work. No foreground service or notification is used. */
class ContextCompressionWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {
    private val chatService: ChatService by inject()

    override suspend fun doWork(): Result {
        val conversationId = inputData.getString("conversationId")?.let {
            runCatching { Uuid.parse(it) }.getOrNull()
        } ?: return Result.failure()
        val outcome = chatService.summarizeAndRefresh(conversationId, inputData.getBoolean("automatic", false))
        return if (outcome.success) Result.success() else Result.failure()
    }

    companion object {
        internal fun name(id: Uuid) = "context-compression-$id"

        fun enqueue(context: Context, conversationId: Uuid, automatic: Boolean): Boolean {
            val manager = context.workManagerOrNull() ?: return false
            manager.enqueueUniqueWork(
                name(conversationId), ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ContextCompressionWorker>()
                    .setInputData(workDataOf("conversationId" to conversationId.toString(), "automatic" to automatic))
                    .build(),
            )
            return true
        }

        fun cancel(context: Context, conversationId: Uuid) {
            context.workManagerOrNull()?.cancelUniqueWork(name(conversationId))
        }
    }
}
