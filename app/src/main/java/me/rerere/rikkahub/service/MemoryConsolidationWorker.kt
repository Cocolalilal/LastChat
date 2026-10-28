package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.memory.MemoryExperience
import me.rerere.rikkahub.data.memory.MemoryLearningWorker
import me.rerere.rikkahub.data.memory.memoryMode
import me.rerere.rikkahub.data.model.Conversation
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Compatibility entry point for persisted old WorkManager requests. Never creates old episodes. */
class MemoryConsolidationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val settings: SettingsStore by inject()
    override suspend fun doWork(): Result {
        settings.settingsFlow.first { !it.init }.assistants.filter {
            it.enableMemory && (it.memoryDreaming || it.memoryMode() == MemoryExperience.ADVANCED)
        }.forEach { MemoryLearningWorker.enqueue(applicationContext, it.id.toString()) }
        return Result.success()
    }
    companion object {
        fun enqueueCatchUp(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("memory-catch-up", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MemoryConsolidationWorker>().build())
        }
        fun enqueueForConversation(context: Context, conversation: Conversation, consolidationDelayMinutes: Int) {
            MemoryLearningWorker.enqueue(context, conversation.assistantId.toString(), consolidationDelayMinutes.toLong().coerceAtLeast(0) * 60)
        }
    }
}
