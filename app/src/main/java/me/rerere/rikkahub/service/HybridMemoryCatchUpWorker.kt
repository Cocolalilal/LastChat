package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HybridMemoryCatchUpWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()

    override suspend fun doWork(): Result {
        settingsStore.settingsFlow.value.assistants.filter { it.enableMemory }.forEach { assistant ->
            conversationRepository.getConversationsOfAssistant(assistant.id).first().forEach { conversation ->
                val request = OneTimeWorkRequestBuilder<HybridMemoryProcessingWorker>()
                    .setInputData(
                        workDataOf(
                            HybridMemoryProcessingWorker.KEY_CONVERSATION_ID to conversation.id.toString(),
                            HybridMemoryProcessingWorker.KEY_ASSISTANT_ID to assistant.id.toString(),
                        )
                    )
                    .addTag("hybrid_memory_assistant_${assistant.id}")
                    .build()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "hybrid_memory_${conversation.id}",
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }
        }
        return Result.success()
    }
}
