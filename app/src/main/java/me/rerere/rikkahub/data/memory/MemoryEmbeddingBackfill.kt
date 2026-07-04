package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.toByteArray
import me.rerere.rikkahub.data.datastore.DISABLED_MODEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryActivityDao
import me.rerere.rikkahub.data.db.dao.MemoryNodeDao
import me.rerere.rikkahub.data.db.dao.MemoryStoreMetaDao
import me.rerere.rikkahub.data.db.entity.MemActivityKind
import me.rerere.rikkahub.data.db.entity.MemBudgetCategory
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaEntity
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaKeys
import kotlin.uuid.Uuid

/**
 * Embedding backfill (§6/§12.4) — the "vectors are only an accelerator" half of the store's
 * embedding-independence guarantee.
 *
 * Every node stores the id of the embedding model that produced its vector. When the user configures
 * an embedding model for the first time, or switches to a different one, the store's vectors are
 * either missing or stale. This pass re-embeds ACTIVE nodes whose `embedding_model_id` is null or
 * mismatched, oldest-salient first, in bounded batches — while FTS serves *every* dedup/recall path
 * unchanged in the interim, so a model switch is a temporary recall-quality dip, never data loss.
 *
 * Design constraints mirror [MemorySleepPass]:
 *  - **Metered as budget category SLEEP** (§12.4): each network embed batch consumes one SLEEP slot;
 *    when the daily cap is spent the pass simply stops and resumes on a later run — nothing is lost.
 *  - **The network call is made outside the per-scope write lock**; writes re-fetch each node under
 *    the lock and use a targeted column update so a concurrent extraction never gets clobbered.
 *  - **Zero-config safe**: with no embedding model configured the pass is a no-op and FTS covers
 *    everything.
 */
class MemoryEmbeddingBackfill(
    private val db: AppDatabase,
    private val nodeDao: MemoryNodeDao,
    private val activityDao: MemoryActivityDao,
    private val storeMetaDao: MemoryStoreMetaDao,
    private val scopeLocks: MemoryScopeLocks,
    private val budget: MemoryBudget,
    private val embeddingService: EmbeddingService,
    private val settingsStore: SettingsStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "MemoryEmbeddingBackfill"

        /** Nodes embedded per model call. Kept modest to stay within provider batch limits. */
        const val EMBED_BATCH = 32

        /** Batches processed per worker run before yielding (self-reschedules if more remain). */
        const val MAX_BATCHES_PER_RUN = 4
    }

    sealed interface Outcome {
        /** Nothing to do: memory off, no embedding model configured, or store already aligned. */
        data object Idle : Outcome

        /**
         * Ran at least one batch (or was stopped by the budget). [remaining] ACTIVE nodes still need
         * this model's vector; [budgetBlocked] = stopped because the SLEEP cap was spent, not because
         * work is done. The worker uses these to decide whether/how soon to reschedule.
         */
        data class Ran(val embedded: Int, val remaining: Int, val budgetBlocked: Boolean) : Outcome
    }

    private fun scopeKeyOf(node: MemoryNodeEntity): String =
        if (node.scope == MemScope.GLOBAL_USER) MemoryScopeLocks.GLOBAL_SCOPE else (node.ownerAssistantId ?: node.id)

    suspend fun run(): Outcome {
        val settings = settingsStore.settingsFlow.value
        if (!settings.memory.enabled) return Outcome.Idle

        // Resolve the currently-configured embedding model. Unconfigured (random/disabled placeholder,
        // or not present in any provider) → FTS-only; leave existing vectors untouched.
        val modelId = embeddingService.getEmbeddingModelId()
        val modelUuid = runCatching { Uuid.parse(modelId) }.getOrNull()
        if (modelUuid == null || modelUuid == DISABLED_MODEL_ID || settings.findModelById(modelUuid) == null) {
            return Outcome.Idle
        }

        val caps = MemoryBudgetCaps.of(MemoryPreset.fromNameOrDefault(settings.memory.preset))
        if (caps.sleepDailyCap <= 0) return Outcome.Idle // OFF/Eco-with-no-sleep-budget: FTS covers it

        var totalEmbedded = 0
        var budgetBlocked = false
        var batches = 0
        while (batches < MAX_BATCHES_PER_RUN) {
            val batch = nodeDao.getActiveNeedingEmbedding(modelId, EMBED_BATCH)
            if (batch.isEmpty()) break

            // Budget admission BEFORE the network call so a spent cap never issues a request.
            if (!budget.tryConsumeDaily(MemBudgetCategory.SLEEP, caps.sleepDailyCap)) {
                budgetBlocked = true
                break
            }

            val vectors = try {
                embeddingService.embedBatch(batch.map { it.content }).embeddings
            } catch (e: Exception) {
                PlatformLog.e(TAG, "embed batch failed: ${e.message}")
                budgetBlocked = true // stop this run; retry on a later schedule
                break
            }
            if (vectors.size != batch.size) {
                PlatformLog.e(TAG, "embed batch size mismatch: got ${vectors.size} for ${batch.size} nodes")
                break
            }

            totalEmbedded += writeBatch(batch, vectors, modelId)
            batches++
        }

        val remaining = nodeDao.countActiveNeedingEmbedding(modelId)
        if (remaining == 0) {
            // Store is fully aligned with the current model — record it so the LastChatApp trigger
            // stops re-enqueuing until the model changes again.
            storeMetaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.EMBED_BACKFILL_MODEL, modelId))
        }
        if (totalEmbedded == 0 && !budgetBlocked) return Outcome.Idle
        return Outcome.Ran(embedded = totalEmbedded, remaining = remaining, budgetBlocked = budgetBlocked)
    }

    /**
     * Persist the batch's vectors. Grouped by scope so each write happens under that scope's write
     * lock; each node is re-fetched and re-checked (still ACTIVE, still needs this model, content
     * unchanged) so a concurrent extraction/sleep write between the network call and here wins.
     */
    private suspend fun writeBatch(
        batch: List<MemoryNodeEntity>,
        vectors: List<List<Float>>,
        modelId: String,
    ): Int {
        val now = clock()
        val byScope = batch.indices.groupBy { scopeKeyOf(batch[it]) }
        var written = 0
        for ((scopeKey, indices) in byScope) {
            scopeLocks.withScope(scopeKey) {
                db.withTransaction {
                    for (i in indices) {
                        val snapshot = batch[i]
                        val current = nodeDao.getById(snapshot.id) ?: continue
                        if (current.status != MemStatus.ACTIVE) continue
                        if (current.embeddingModelId == modelId) continue // already embedded (raced)
                        if (current.content != snapshot.content) continue // content changed; embed later
                        val blob = listOf(vectors[i].toFloatArray()).toByteArray()
                        nodeDao.setEmbedding(snapshot.id, blob, modelId)
                        written++
                    }
                }
            }
        }
        if (written > 0) {
            activityDao.insert(
                MemoryActivityEntity(
                    id = Uuid.random().toString(),
                    at = now,
                    scope = MemScope.GLOBAL_USER,
                    ownerAssistantId = null,
                    kind = MemActivityKind.EMBEDDED,
                    summary = "$written memories re-embedded",
                )
            )
        }
        return written
    }
}
