package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val CHAT_STORAGE_MAINTENANCE_WORK_NAME = "chat_storage_maintenance"

class ChatStorageMaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val chatAttachmentRepository: ChatAttachmentRepository by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            chatAttachmentRepository.runMaintenance()
            Result.success()
        } catch (throwable: Throwable) {
            Log.e(CHAT_STORAGE_MAINTENANCE_WORK_NAME, "Chat storage maintenance failed", throwable)
            Result.retry()
        }
    }
}
