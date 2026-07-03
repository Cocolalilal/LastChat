package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MemoryStoreMetaDao
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaEntity
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaKeys
import me.rerere.rikkahub.data.model.MemoryOp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * One-shot, resumable importer that folds the legacy `MemoryEntity` (core facts) and
 * `ChatEpisodeEntity` (episodes) into the graph store (§12.2).
 *
 * Everything goes through [MemoryOpApplier] with `source = IMPORTED`, so the import inherits the
 * same dedup gate and entity resolution as live extraction instead of needing its own quality
 * logic. No model calls are made; ordinary sleep passes refine the imports later. Chunked and safe
 * to kill/resume: progress is a watermark in `memory_store_meta`.
 */
class MemoryImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val memoryDao: MemoryDAO by inject()
    private val episodeDao: ChatEpisodeDAO by inject()
    private val applier: MemoryOpApplier by inject()
    private val metaDao: MemoryStoreMetaDao by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (metaDao.get(MemoryStoreMetaKeys.IMPORT_COMPLETED) == "true") return@withContext Result.success()

            val start = (metaDao.get(MemoryStoreMetaKeys.IMPORT_WATERMARK)?.toIntOrNull()) ?: 0

            // Phase 1: legacy core memories → FACT nodes.
            val coreCount = memoryDao.countAll()
            var processed = start.coerceAtMost(coreCount)
            while (processed < coreCount) {
                val batch = memoryDao.getMemoriesPaged(CHUNK, processed)
                if (batch.isEmpty()) break
                for (m in batch) {
                    if (m.content.isBlank()) continue
                    applier.apply(
                        ops = listOf(
                            MemoryOp.AddNode(
                                type = MemNodeType.FACT,
                                content = m.content,
                                importance = 3,
                                confidence = 0.9f,
                                rationale = "imported from previous memory system",
                            )
                        ),
                        ctx = MemoryApplyContext(
                            assistantId = m.assistantId,
                            source = MemSource.IMPORTED,
                            now = if (m.createdAt > 0) m.createdAt else System.currentTimeMillis(),
                        ),
                    )
                }
                processed += batch.size
                metaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.IMPORT_WATERMARK, processed.toString()))
            }

            // Phase 2: legacy episodes → EPISODE nodes (offset continues past the core block).
            val episodeStart = (processed - coreCount).coerceAtLeast(0)
            val episodeCount = episodeDao.getCount()
            var epProcessed = episodeStart
            while (epProcessed < episodeCount) {
                val batch = episodeDao.getEpisodesPaged(CHUNK, epProcessed)
                if (batch.isEmpty()) break
                for (e in batch) {
                    if (e.content.isBlank()) continue
                    applier.apply(
                        ops = listOf(
                            MemoryOp.AddNode(
                                type = MemNodeType.EPISODE,
                                content = e.content,
                                importance = mapSignificance(e.significance),
                                confidence = 0.9f,
                                eventStart = e.startTime,
                                eventEnd = e.endTime,
                                rationale = "imported from previous memory system",
                            )
                        ),
                        ctx = MemoryApplyContext(
                            assistantId = e.assistantId,
                            conversationId = e.conversationId?.takeIf { it.isNotBlank() },
                            source = MemSource.IMPORTED,
                            now = if (e.endTime > 0) e.endTime else System.currentTimeMillis(),
                        ),
                    )
                }
                epProcessed += batch.size
                metaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.IMPORT_WATERMARK, (coreCount + epProcessed).toString()))
            }

            metaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.IMPORT_COMPLETED, "true"))
            metaDao.put(
                MemoryStoreMetaEntity(
                    MemoryStoreMetaKeys.STORE_SCHEMA_VERSION,
                    MemoryStoreMetaKeys.CURRENT_STORE_SCHEMA_VERSION,
                )
            )
            PlatformLog.i(TAG, "Memory import complete: $coreCount facts, $episodeCount episodes")
            Result.success()
        } catch (e: Exception) {
            PlatformLog.e(TAG, "Memory import failed: ${e.message}")
            Result.retry()
        }
    }

    /** Legacy significance is 1..10; graph importance is 1..5. */
    private fun mapSignificance(significance: Int): Int = ((significance + 1) / 2).coerceIn(1, 5)

    companion object {
        private const val TAG = "MemoryImportWorker"
        private const val CHUNK = 200
        const val WORK_NAME = "memory_import"
    }
}
