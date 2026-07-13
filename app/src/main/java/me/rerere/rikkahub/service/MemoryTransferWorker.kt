package me.rerere.rikkahub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.memory.BuiltInMemoryEngines
import me.rerere.ai.memory.Mem0Hash
import me.rerere.ai.memory.MemoryInputMessage
import me.rerere.ai.memory.MemoryOrigin
import me.rerere.ai.memory.MemoryScope
import me.rerere.ai.memory.MemorySpeaker
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.MemoryGraphDao
import me.rerere.rikkahub.data.db.entity.MemoryEngineStateEntity
import me.rerere.rikkahub.data.db.entity.MemoryTransferLinkEntity
import me.rerere.rikkahub.data.memory.GraphMemoryRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MemoryTransferWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val dao: MemoryGraphDao by inject()
    private val graphRepository: GraphMemoryRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val settingsStore: SettingsStore by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@withContext Result.failure()
        val initial = dao.getTransferJob(jobId) ?: return@withContext Result.failure()
        if (initial.state == "PAUSED") return@withContext Result.success()
        try {
            // Persist startup before asking Android for foreground execution. If promotion
            // fails, the catch block can expose it instead of leaving the job QUEUED forever.
            updateJob(jobId, state = "RUNNING", stage = "STARTING")
            setForeground(progressForeground(initial.processed, initial.total))
            val total = when (initial.sourceEngine) {
                BuiltInMemoryEngines.SIMPLE -> memoryRepository.getMemoryEntitiesOfAssistant(initial.assistantId).size
                BuiltInMemoryEngines.GRAPH -> graphRepository.snapshot(initial.assistantId).memories.size
                else -> 0
            }
            updateJob(jobId, state = "RUNNING", stage = "TRANSFERRING", total = total)
            when {
                initial.sourceEngine == BuiltInMemoryEngines.SIMPLE && initial.targetEngine == BuiltInMemoryEngines.GRAPH -> {
                    transferSimpleToGraph(jobId, initial.assistantId)
                }
                initial.sourceEngine == BuiltInMemoryEngines.GRAPH && initial.targetEngine == BuiltInMemoryEngines.SIMPLE -> {
                    transferGraphToSimple(jobId, initial.assistantId)
                }
            }
            val completedAt = System.currentTimeMillis()
            val completed = dao.getTransferJob(jobId) ?: initial
            dao.upsertTransferJob(completed.copy(state = "COMPLETED", stage = "READY", processed = total, total = total, updatedAt = completedAt))
            dao.upsertEngineState(
                MemoryEngineStateEntity(
                    assistantId = initial.assistantId,
                    engineId = initial.targetEngine,
                    status = "READY",
                    lastSyncedAt = completedAt,
                ),
            )
            val assistantId = Uuid.parse(initial.assistantId)
            settingsStore.update { current ->
                current.copy(assistants = current.assistants.map { assistant ->
                    if (assistant.id == assistantId) assistant.copy(
                        enableMemory = true,
                        memoryEngineId = initial.targetEngine,
                        lastActiveMemoryEngineId = initial.targetEngine,
                        pendingMemoryEngineId = null,
                    ) else assistant
                })
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val job = dao.getTransferJob(jobId) ?: initial
            val message = error.message ?: error::class.simpleName.orEmpty()
            dao.upsertTransferJob(job.copy(state = "FAILED", stage = "FAILED", lastError = message, updatedAt = System.currentTimeMillis()))
            dao.upsertEngineState(
                MemoryEngineStateEntity(
                    assistantId = initial.assistantId,
                    engineId = initial.targetEngine,
                    status = "FAILED",
                    lastError = message,
                    lastErrorModel = settingsStore.settingsFlow.value.summarizerModelId?.toString(),
                ),
            )
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun transferSimpleToGraph(jobId: String, assistantId: String) {
        val source = memoryRepository.getMemoryEntitiesOfAssistant(assistantId).sortedBy { it.id }
        val oldLinks = dao.getTransferLinks(assistantId, BuiltInMemoryEngines.SIMPLE, BuiltInMemoryEngines.GRAPH)
            .associateBy { it.sourceId }
        source.forEachIndexed { index, item ->
            ensureRunning(jobId)
            val sourceId = "memory:${item.id}"
            val hash = Mem0Hash.md5(item.content)
            val prior = oldLinks[sourceId]
            if (prior?.sourceHash != hash) {
                val target = if (prior == null) {
                    val result = graphRepository.ingest(
                        scope = MemoryScope(assistantId),
                        messages = listOf(
                            MemoryInputMessage(
                                role = MemorySpeaker.USER,
                                content = item.content,
                                messageId = sourceId,
                                observedAtEpochMillis = item.createdAt,
                            ),
                        ),
                        origin = MemoryOrigin.IMPORTED,
                    )
                    result.added.firstOrNull() ?: graphRepository.search(MemoryScope(assistantId), item.content, topK = 1, threshold = 0f).firstOrNull()?.memory
                } else {
                    graphRepository.update(prior.targetId, item.content)
                }
                target?.let {
                    dao.upsertTransferLink(
                        MemoryTransferLinkEntity(
                            assistantId = assistantId,
                            sourceEngine = BuiltInMemoryEngines.SIMPLE,
                            sourceId = sourceId,
                            targetEngine = BuiltInMemoryEngines.GRAPH,
                            targetId = it.id,
                            sourceHash = hash,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            updateProgress(jobId, index + 1, source.size)
        }
    }

    private suspend fun transferGraphToSimple(jobId: String, assistantId: String) {
        val source = graphRepository.snapshot(assistantId).memories.sortedBy { it.createdAt }
        val oldLinks = dao.getTransferLinks(assistantId, BuiltInMemoryEngines.GRAPH, BuiltInMemoryEngines.SIMPLE)
            .associateBy { it.sourceId }
        source.forEachIndexed { index, item ->
            ensureRunning(jobId)
            val hash = Mem0Hash.md5(item.content)
            val prior = oldLinks[item.id]
            if (prior?.sourceHash != hash) {
                val target = if (prior == null) {
                    memoryRepository.addMemory(assistantId, item.content)
                } else {
                    memoryRepository.updateContent(prior.targetId.toInt(), item.content)
                }
                dao.upsertTransferLink(
                    MemoryTransferLinkEntity(
                        assistantId = assistantId,
                        sourceEngine = BuiltInMemoryEngines.GRAPH,
                        sourceId = item.id,
                        targetEngine = BuiltInMemoryEngines.SIMPLE,
                        targetId = target.id.toString(),
                        sourceHash = hash,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            updateProgress(jobId, index + 1, source.size)
        }
    }

    private suspend fun ensureRunning(jobId: String) {
        if (dao.getTransferJob(jobId)?.state == "PAUSED") throw CancellationException("Memory transfer paused")
    }

    private suspend fun updateProgress(jobId: String, processed: Int, total: Int) {
        updateJob(jobId, processed = processed, total = total)
        setProgress(androidx.work.workDataOf("processed" to processed, "total" to total))
        setForeground(progressForeground(processed, total))
    }

    private suspend fun updateJob(
        jobId: String,
        state: String? = null,
        stage: String? = null,
        processed: Int? = null,
        total: Int? = null,
    ) {
        val current = dao.getTransferJob(jobId) ?: return
        dao.upsertTransferJob(
            current.copy(
                state = state ?: current.state,
                stage = stage ?: current.stage,
                processed = processed ?: current.processed,
                total = total ?: current.total,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun progressForeground(processed: Int, total: Int): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Memory transfer", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val progress = if (total > 0) (processed * 100 / total).coerceIn(0, 100) else 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Preparing memory")
            .setContentText(if (total > 0) "$processed of $total memories" else "Starting…")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val CHANNEL_ID = "memory_transfer"
        private const val NOTIFICATION_ID = 7042
    }
}
