package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlin.uuid.Uuid
import me.rerere.ai.memory.BuiltInMemoryEngines
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.resolvedMemoryEngineId
import me.rerere.rikkahub.data.db.dao.MemoryGraphDao
import me.rerere.rikkahub.data.db.entity.MemoryTransferJobEntity
import me.rerere.rikkahub.service.MemoryTransferWorker

class MemoryTransferManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val dao: MemoryGraphDao,
) {
    suspend fun switchEngine(assistantId: Uuid, targetEngine: String): String? {
        require(targetEngine in setOf(BuiltInMemoryEngines.OFF, BuiltInMemoryEngines.SIMPLE, BuiltInMemoryEngines.GRAPH))
        val assistant = settingsStore.settingsFlow.value.getAssistantById(assistantId) ?: return null
        val resolvedSource = assistant.resolvedMemoryEngineId()
        val sourceEngine = if (resolvedSource == BuiltInMemoryEngines.OFF) {
            assistant.lastActiveMemoryEngineId ?: BuiltInMemoryEngines.SIMPLE
        } else {
            resolvedSource
        }
        if (resolvedSource != BuiltInMemoryEngines.OFF && sourceEngine == targetEngine) return null

        if (resolvedSource == BuiltInMemoryEngines.OFF && sourceEngine == targetEngine) {
            settingsStore.update { current ->
                current.copy(assistants = current.assistants.map {
                    if (it.id == assistantId) it.copy(
                        enableMemory = true,
                        memoryEngineId = targetEngine,
                        lastActiveMemoryEngineId = targetEngine,
                        pendingMemoryEngineId = null,
                    ) else it
                })
            }
            return null
        }

        if (targetEngine == BuiltInMemoryEngines.OFF) {
            settingsStore.update { current ->
                current.copy(assistants = current.assistants.map {
                    if (it.id == assistantId) it.copy(
                        enableMemory = false,
                        memoryEngineId = BuiltInMemoryEngines.OFF,
                        lastActiveMemoryEngineId = sourceEngine,
                        pendingMemoryEngineId = null,
                    ) else it
                })
            }
            return null
        }

        val now = System.currentTimeMillis()
        val jobId = Uuid.random().toString()
        dao.upsertTransferJob(
            MemoryTransferJobEntity(
                id = jobId,
                assistantId = assistantId.toString(),
                sourceEngine = sourceEngine,
                targetEngine = targetEngine,
                state = "QUEUED",
                stage = "PREPARING",
                createdAt = now,
                updatedAt = now,
            ),
        )
        settingsStore.update { current ->
            current.copy(assistants = current.assistants.map {
                if (it.id == assistantId) it.copy(pendingMemoryEngineId = targetEngine) else it
            })
        }
        enqueue(jobId)
        return jobId
    }

    suspend fun pause(jobId: String) {
        val job = dao.getTransferJob(jobId) ?: return
        dao.upsertTransferJob(job.copy(state = "PAUSED", updatedAt = System.currentTimeMillis()))
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(jobId))
    }

    suspend fun resume(jobId: String) {
        val job = dao.getTransferJob(jobId) ?: return
        dao.upsertTransferJob(
            job.copy(
                state = "QUEUED",
                stage = "RETRYING",
                lastError = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        enqueue(jobId)
    }

    /** Retry one stale initial enqueue without creating an endless automatic retry loop. */
    suspend fun recoverStaleQueued(jobId: String, expectedUpdatedAt: Long) {
        val job = dao.getTransferJob(jobId) ?: return
        if (
            job.state != "QUEUED" ||
            job.updatedAt != expectedUpdatedAt ||
            job.stage != "PREPARING"
        ) return
        dao.upsertTransferJob(
            job.copy(
                stage = "AUTO_RETRY",
                lastError = "Android delayed starting the background transfer. Retrying once…",
                updatedAt = System.currentTimeMillis(),
            ),
        )
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(jobId))
        enqueue(jobId)
    }

    private fun enqueue(jobId: String) {
        val request = OneTimeWorkRequestBuilder<MemoryTransferWorker>()
            .setInputData(Data.Builder().putString(MemoryTransferWorker.KEY_JOB_ID, jobId).build())
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(jobId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueName(jobId: String) = "memory_transfer_$jobId"

    companion object {
        const val TAG = "memory_transfer"
    }
}
