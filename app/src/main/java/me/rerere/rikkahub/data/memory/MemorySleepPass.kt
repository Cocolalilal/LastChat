package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.anyMemoryEnabled
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryActivityDao
import me.rerere.rikkahub.data.db.dao.MemoryEdgeDao
import me.rerere.rikkahub.data.db.dao.MemoryNodeDao
import me.rerere.rikkahub.data.db.dao.MemoryProvenanceDao
import me.rerere.rikkahub.data.db.dao.MemoryStoreMetaDao
import me.rerere.rikkahub.data.db.entity.MemActivityKind
import me.rerere.rikkahub.data.db.entity.MemActivityState
import me.rerere.rikkahub.data.db.entity.MemBudgetCategory
import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeFtsEntity
import me.rerere.rikkahub.data.db.entity.MemoryProvenanceEntity
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaEntity
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaKeys
import me.rerere.rikkahub.data.model.MemoryExtra
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

/**
 * The periodic "sleep pass" (§5.4, §9) — the consolidation half of the human-like memory system.
 * It runs staged over the whole store; [MemorySleepWorker] is the thin WorkManager wrapper.
 *
 * Design constraints made structural here:
 *  - **Every mutating stage holds the [MemoryScopeLocks] write lock** for its scope, so a sleep merge
 *    can never interleave with an in-flight extraction apply on the same scope (§7.2). The pass is
 *    the store's first *second* writer; the mutex was built for exactly this.
 *  - **Deterministic stages (1 decay/promotion, 2 expiry, 8 size enforcement) never touch the
 *    network or budget** — an offline, budget-exhausted device still ages and bounds its graph.
 *  - **Model-assisted stages (3–6) draw from [MemoryBudget]** and simply return early when the
 *    budget is exhausted or the model call fails: never block, never surface an error to the app.
 *  - Everything the pass changes lands a `memory_activity` row.
 *
 * Model calls are made *outside* the scope lock (reads a snapshot → calls the model → re-acquires the
 * lock and re-validates each node before applying) so a slow network call never blocks extraction.
 */
class MemorySleepPass(
    private val db: AppDatabase,
    private val nodeDao: MemoryNodeDao,
    private val edgeDao: MemoryEdgeDao,
    private val provenanceDao: MemoryProvenanceDao,
    private val activityDao: MemoryActivityDao,
    private val storeMetaDao: MemoryStoreMetaDao,
    private val scopeLocks: MemoryScopeLocks,
    private val budget: MemoryBudget,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
    private val curiosityEngine: CuriosityEngine,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "MemorySleepPass"

        // §9 per-scope node budgets (non-entity live nodes). Validated against real DBs during P3.
        const val CHARACTER_NODE_BUDGET = 1_500
        const val GLOBAL_NODE_BUDGET = 1_000

        const val ACTIVITY_KEEP_PER_SCOPE = 500
        const val PROVENANCE_KEEP_PER_NODE = 4 // first + last 3

        // §6.5/§10.1: at most this many pending scope-promotion chips per character at once.
        const val MAX_PENDING_PROMOTION_CHIPS = 3

        // compression / habit clustering
        private const val COMPRESSION_AGE_MS = 45L * 24 * 60 * 60 * 1000
        private const val ERA_BUCKET_MS = 30L * 24 * 60 * 60 * 1000
        private const val CLUSTER_MIN = 3
        private const val LOW_IMPORTANCE_MAX = 2

        // statuses the deterministic stages examine (SUPERSEDED history is left untouched)
        private val SWEEP_STATUSES = listOf(
            MemStatus.PROVISIONAL, MemStatus.ACTIVE, MemStatus.DORMANT, MemStatus.CLOSED, MemStatus.FORGOTTEN,
        )
    }

    sealed interface Outcome {
        data object Skipped : Outcome
        data class Ran(val scopes: Int) : Outcome
    }

    /** One scope's identity + node budget. Global layer has a fixed key and no owner. */
    private data class ScopeRef(val key: String, val scope: Int, val ownerId: String?, val nodeBudget: Int)

    private fun scopeRefOf(node: MemoryNodeEntity): ScopeRef =
        if (node.scope == MemScope.GLOBAL_USER) {
            ScopeRef(MemoryScopeLocks.GLOBAL_SCOPE, MemScope.GLOBAL_USER, null, GLOBAL_NODE_BUDGET)
        } else {
            ScopeRef(node.ownerAssistantId ?: node.id, MemScope.CHARACTER, node.ownerAssistantId, CHARACTER_NODE_BUDGET)
        }

    suspend fun run(): Outcome {
        val settings = settingsStore.settingsFlow.value
        if (!settings.anyMemoryEnabled) return Outcome.Skipped
        val now = clock()

        val all = nodeDao.getAllByStatuses(SWEEP_STATUSES)
        val groups = all.groupBy { scopeRefOf(it).key }
        if (groups.isEmpty()) {
            storeMetaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.LAST_SLEEP_RUN, now.toString()))
            return Outcome.Ran(0)
        }

        for ((_, scopeNodes) in groups) {
            val ref = scopeRefOf(scopeNodes.first())
            val ids = scopeNodes.map { it.id }
            // Per-scope caps: a character scope runs on its own preset; the global layer on the max
            // preset among memory-enabled assistants.
            val caps = MemoryBudgetCaps.of(MemoryModels.presetFor(settings, ref.ownerId))

            // §5.4 stages 1 + 2 — deterministic, no network/budget.
            try {
                deterministicStages(ref, ids, now)
            } catch (e: Exception) {
                PlatformLog.e(TAG, "deterministic stages failed for ${ref.key}: ${e.message}")
            }

            // §5.4 stages 3 + 4 — one batched model call (merge / supersede / coexist).
            try {
                adjudicationStage(ref, settings, caps, now)
            } catch (e: Exception) {
                PlatformLog.e(TAG, "adjudication stage failed for ${ref.key}: ${e.message}")
            }

            // §5.4 stages 5 + 6 — compression + habit induction (same call), pressure/toggle gated.
            try {
                compressionAndHabitStage(ref, settings, caps, now)
            } catch (e: Exception) {
                PlatformLog.e(TAG, "compression stage failed for ${ref.key}: ${e.message}")
            }

            // §5.4 stage 7 — scope-promotion review (§6.5) + curiosity goals (§6.4). CHARACTER-only:
            // the global layer has nothing to promote *into*, and curiosity is per-character.
            if (ref.scope == MemScope.CHARACTER && ref.ownerId != null) {
                val assistant = settings.assistants.find { it.id.toString() == ref.ownerId }
                try {
                    promotionReviewStage(ref, assistant, now)
                } catch (e: Exception) {
                    PlatformLog.e(TAG, "promotion review failed for ${ref.key}: ${e.message}")
                }
                if (assistant != null) {
                    try {
                        curiosityEngine.runSleepPass(assistant, settings, caps, now)
                    } catch (e: Exception) {
                        PlatformLog.e(TAG, "curiosity pass failed for ${ref.key}: ${e.message}")
                    }
                }
            }

            // §9 stage 8 — size enforcement, always (independent of budget).
            try {
                enforceSize(ref, now)
                activityDao.pruneScope(ref.scope, ref.ownerId, ACTIVITY_KEEP_PER_SCOPE)
            } catch (e: Exception) {
                PlatformLog.e(TAG, "size enforcement failed for ${ref.key}: ${e.message}")
            }
        }

        // store-wide housekeeping
        runCatching { capProvenance() }
        runCatching { budget.pruneOld(now) }
        storeMetaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.LAST_SLEEP_RUN, now.toString()))
        return Outcome.Ran(groups.size)
    }

    // ================= §5.4 stages 1 + 2: decay, promotion, expiry (deterministic) =================

    private suspend fun deterministicStages(ref: ScopeRef, candidateIds: List<String>, now: Long) {
        scopeLocks.withScope(ref.key) {
            db.withTransaction {
                var demoted = 0
                var forgotten = 0
                var deleted = 0
                var promoted = 0
                var expired = 0

                for (id in candidateIds) {
                    val node = nodeDao.getById(id) ?: continue // re-fetch: extraction may have changed it

                    // Stage 2: validity expiry (facts only). A just-closed fact is not also decayed this run.
                    val dueAt = MemorySleepLogic.expiryDueAt(
                        status = node.status,
                        type = node.type,
                        validUntil = node.validUntil,
                        recordedAt = node.recordedAt,
                        validityHorizonMillis = decodeExtra(node.extra).validityHorizonMillis,
                        now = now,
                    )
                    if (dueAt != null) {
                        nodeDao.update(node.copy(status = MemStatus.CLOSED, validUntil = node.validUntil ?: dueAt, lastAccessedAt = node.lastAccessedAt))
                        expired++
                        continue
                    }

                    // Stage 1a: provisional auto-promotion (importance≥3, ≥7d, no contradiction/adjudication flag).
                    if (node.status == MemStatus.PROVISIONAL) {
                        val hasContradicts = edgeDao.getTouching(node.id).any { it.type == MemEdgeType.CONTRADICTS }
                        if (MemorySleepLogic.canAutoPromote(node.toSleepState(), hasContradicts, node.adjudicationPending, now)) {
                            nodeDao.update(node.copy(status = MemStatus.ACTIVE))
                            promoted++
                            continue
                        }
                    }

                    // Stage 1b: decay ladder.
                    when (MemorySleepLogic.decayAction(node.toSleepState(), now)) {
                        MemorySleepLogic.DecayAction.DEMOTE_DORMANT -> {
                            nodeDao.update(node.copy(status = MemStatus.DORMANT)) // idle clock keeps running
                            demoted++
                        }

                        MemorySleepLogic.DecayAction.FORGET -> {
                            // Stamp the grace start on last_accessed_at; restorable for 30 days.
                            nodeDao.update(node.copy(status = MemStatus.FORGOTTEN, lastAccessedAt = now))
                            forgotten++
                        }

                        MemorySleepLogic.DecayAction.DELETE -> {
                            hardDelete(node.id)
                            deleted++
                        }

                        MemorySleepLogic.DecayAction.KEEP -> Unit
                    }
                }

                writeMaintenanceActivity(ref, now, buildList {
                    if (promoted > 0) add(MemActivityKind.PROMOTED to "$promoted promoted")
                    if (expired > 0) add(MemActivityKind.EXPIRED to "$expired expired")
                    if (demoted > 0) add(MemActivityKind.DECAYED to "$demoted → dormant")
                    if (forgotten > 0) add(MemActivityKind.FORGOTTEN to "$forgotten forgotten")
                    if (deleted > 0) add(MemActivityKind.FORGOTTEN to "$deleted purged")
                })
            }
        }
    }

    // ================= §5.4 stages 3 + 4: identity adjudication + contradiction =================

    /** A pair the dedup gate flagged (RELATES_TO + adjudication flag) or that carries a CONTRADICTS edge. */
    private data class Pair2(val aId: String, val bId: String)

    private suspend fun adjudicationStage(ref: ScopeRef, settings: Settings, caps: MemoryBudgetCaps, now: Long) {
        // 1. Gather candidate pairs (read only).
        val nodes = nodeDao.getVisibleWithStatuses(ownerKeyForRead(ref), MemoryGraphRepository.INJECTABLE_STATUSES)
            .filter { inScope(it, ref) }
        val byId = nodes.associateBy { it.id }
        val pairs = LinkedHashSet<Pair2>()
        for (node in nodes) {
            val flagged = node.adjudicationPending
            for (edge in edgeDao.getTouching(node.id)) {
                if (edge.type != MemEdgeType.RELATES_TO && edge.type != MemEdgeType.CONTRADICTS) continue
                if (edge.type == MemEdgeType.RELATES_TO && !flagged) continue
                val otherId = if (edge.fromId == node.id) edge.toId else edge.fromId
                if (byId[otherId] == null) continue
                val (a, b) = listOf(node.id, otherId).sorted()
                pairs += Pair2(a, b)
            }
        }
        if (pairs.isEmpty()) return

        // 2. Budget admission for the single batched call (resolve the model first so a missing model
        // doesn't burn a budget slot).
        val resolved = resolveModel(settings) ?: return
        if (!budget.tryConsumeDaily(MemBudgetCategory.SLEEP, caps.sleepDailyCap)) return

        // 3. One model call, outside the lock.
        val prompt = buildAdjudicationPrompt(pairs.toList(), byId)
        val response = try {
            callModel(resolved.first, resolved.second, prompt, settings)
        } catch (e: Exception) {
            PlatformLog.e(TAG, "adjudication call failed: ${e.message}")
            return
        }
        val decisions = SleepDecisionParser.parseAdjudications(response)
        if (decisions.isEmpty()) return

        // 4. Apply under the lock, re-validating every node.
        scopeLocks.withScope(ref.key) {
            db.withTransaction {
                var merged = 0
                var superseded = 0
                var coexisted = 0
                for (d in decisions) {
                    val a = nodeDao.getById(d.aId) ?: continue
                    val b = nodeDao.getById(d.bId) ?: continue
                    // Skip if either side was already resolved (no longer live) since the snapshot.
                    if (!isLive(a) || !isLive(b)) continue
                    when (d.decision) {
                        SleepDecisionParser.Verdict.MERGE ->
                            if (applyMerge(ref, a, b, d.mergedContent, now)) merged++ else coexisted += clearFlags(a, b)
                        SleepDecisionParser.Verdict.SUPERSEDE ->
                            if (applySupersede(ref, a, b, now)) superseded++ else coexisted += clearFlags(a, b)
                        SleepDecisionParser.Verdict.COEXIST -> coexisted += clearFlags(a, b)
                    }
                }
                writeMaintenanceActivity(ref, now, buildList {
                    if (merged > 0) add(MemActivityKind.MERGED to "$merged merged")
                    if (superseded > 0) add(MemActivityKind.UPDATED to "$superseded superseded")
                    if (coexisted > 0) add(MemActivityKind.ADJUDICATED to "$coexisted kept distinct")
                })
            }
        }
    }

    /** Deterministic MERGE (§5.4). Returns false (caller falls back to coexist) if MANUAL-protected. */
    private suspend fun applyMerge(ref: ScopeRef, a: MemoryNodeEntity, b: MemoryNodeEntity, mergedContent: String?, now: Long): Boolean {
        if (MemorySleepLogic.isManualProtected(a.source) || MemorySleepLogic.isManualProtected(b.source)) return false

        val fields = MemorySleepLogic.mergeFields(a.toMergeInput(), b.toMergeInput())
        val content = mergedContent?.trim()?.takeIf { it.isNotBlank() }
            ?: if (a.recordedAt >= b.recordedAt) a.content else b.content
        val entities = (aboutEntitiesOf(a.id) + aboutEntitiesOf(b.id)).distinct()

        val id = Uuid.random().toString()
        val mergedNode = a.copy(
            id = id,
            content = content,
            importance = fields.importance,
            confidence = fields.confidence,
            pinned = fields.pinned,
            timesReinforced = fields.timesReinforced,
            timesRetrieved = maxOf(a.timesRetrieved, b.timesRetrieved),
            status = MemStatus.ACTIVE,
            source = MemSource.MERGED,
            recordedAt = fields.recordedAt,
            lastConfirmedAt = fields.lastConfirmedAt,
            lastAccessedAt = now,
            adjudicationPending = false,
        )
        nodeDao.upsert(mergedNode)
        nodeDao.insertFts(MemoryNodeFtsEntity(id, mergedNode.content, mergedNode.displayLabel ?: ""))
        for (eid in entities) addEdge(id, eid, MemEdgeType.ABOUT, now)
        // both sources SUPERSEDED into the merged node
        addEdge(id, a.id, MemEdgeType.SUPERSEDES, now)
        addEdge(id, b.id, MemEdgeType.SUPERSEDES, now)
        nodeDao.update(a.copy(status = MemStatus.SUPERSEDED, adjudicationPending = false))
        nodeDao.update(b.copy(status = MemStatus.SUPERSEDED, adjudicationPending = false))
        // provenance union (bounded)
        unionProvenance(id, a.id, b.id, now)
        return true
    }

    /** Newer SUPERSEDES older. If the loser is MANUAL-protected, keep the CONTRADICTS conflict instead. */
    private suspend fun applySupersede(ref: ScopeRef, a: MemoryNodeEntity, b: MemoryNodeEntity, now: Long): Boolean {
        val newer = if (a.recordedAt >= b.recordedAt) a else b
        val older = if (newer === a) b else a
        if (MemorySleepLogic.isManualProtected(older.source)) return false // never auto-supersede a user belief
        addEdge(newer.id, older.id, MemEdgeType.SUPERSEDES, now)
        nodeDao.update(older.copy(status = MemStatus.SUPERSEDED, adjudicationPending = false))
        nodeDao.update(newer.copy(adjudicationPending = false))
        return true
    }

    /** Coexist: clear the adjudication flags so both stop being suppressed at injection; edges stay. */
    private suspend fun clearFlags(a: MemoryNodeEntity, b: MemoryNodeEntity): Int {
        if (a.adjudicationPending) nodeDao.update(a.copy(adjudicationPending = false))
        if (b.adjudicationPending) nodeDao.update(b.copy(adjudicationPending = false))
        return 1
    }

    // ================= §5.4 stages 5 + 6: episode compression + habit induction =================

    private suspend fun compressionAndHabitStage(ref: ScopeRef, settings: Settings, caps: MemoryBudgetCaps, now: Long) {
        val nodes = nodeDao.getVisibleWithStatuses(ownerKeyForRead(ref), MemoryGraphRepository.SEARCHABLE_STATUSES)
            .filter { inScope(it, ref) }

        val episodes = nodes.filter { it.type == MemNodeType.EPISODE }
        // Compression clusters: old, low-importance episodes grouped by frame + primary entity + era.
        // A non-empty cluster IS the "age pressure" trigger (§5.4 stage 5); size pressure only makes
        // the pass reach here sooner because more clusters qualify as the store fills.
        val compressible = episodeClusters(episodes, now).filter { it.value.size >= CLUSTER_MIN }

        // Habit groups: ≥3 episodes about the same primary entity (toggleable per character; the
        // global scope induces habits when any memory-enabled assistant wants them). Primary
        // entities are resolved first (a suspend DB read) so the grouping lambda stays non-suspend.
        val habitInduction = if (ref.ownerId != null) {
            settings.assistants.firstOrNull { it.id.toString() == ref.ownerId }?.memoryHabitInduction ?: true
        } else {
            MemoryModels.globalHabitInduction(settings)
        }
        val habitGroups: Map<String?, List<MemoryNodeEntity>> = if (habitInduction) {
            val withEntity = episodes.map { it to primaryEntityId(it) }.filter { it.second != null }
            withEntity.groupBy { it.second }
                .mapValues { entry -> entry.value.map { it.first } }
                .filter { it.value.size >= CLUSTER_MIN }
        } else emptyMap()

        if (compressible.isEmpty() && habitGroups.isEmpty()) return
        val resolved = resolveModel(settings) ?: return
        if (!budget.tryConsumeDaily(MemBudgetCategory.SLEEP, caps.sleepDailyCap)) return

        val prompt = buildCompressionPrompt(compressible, habitGroups)
        val response = try {
            callModel(resolved.first, resolved.second, prompt, settings)
        } catch (e: Exception) {
            PlatformLog.e(TAG, "compression call failed: ${e.message}")
            return
        }
        val plan = SleepDecisionParser.parseCompression(response)
        if (plan.gists.isEmpty() && plan.habits.isEmpty()) return

        scopeLocks.withScope(ref.key) {
            db.withTransaction {
                var gists = 0
                var habits = 0
                for (g in plan.gists) {
                    val episodeNodes = g.episodeIds.mapNotNull { nodeDao.getById(it) }.filter { isLive(it) && it.type == MemNodeType.EPISODE }
                    if (episodeNodes.size < CLUSTER_MIN) continue
                    if (createGist(ref, g.summary, episodeNodes, now)) gists++
                }
                for (h in plan.habits) {
                    val episodeNodes = h.instanceIds.mapNotNull { nodeDao.getById(it) }.filter { isLive(it) }
                    if (episodeNodes.size < CLUSTER_MIN) continue
                    if (createHabit(ref, h.statement, episodeNodes, now)) habits++
                }
                writeMaintenanceActivity(ref, now, buildList {
                    if (gists > 0) add(MemActivityKind.COMPRESSED to "$gists gist(s)")
                    if (habits > 0) add(MemActivityKind.HABIT_INDUCED to "$habits habit(s)")
                })
            }
        }
    }

    private suspend fun createGist(ref: ScopeRef, summary: String, episodes: List<MemoryNodeEntity>, now: Long): Boolean {
        val content = summary.trim().ifBlank { return false }
        val id = Uuid.random().toString()
        val starts = episodes.mapNotNull { it.eventStart }
        val ends = episodes.mapNotNull { it.eventEnd ?: it.eventStart }
        val gist = MemoryNodeEntity(
            id = id,
            type = MemNodeType.GIST,
            scope = ref.scope,
            ownerAssistantId = ref.ownerId,
            content = content,
            importance = episodes.maxOf { it.importance },
            confidence = 1f,
            status = MemStatus.ACTIVE,
            reality = episodes.first().reality,
            eventStart = starts.minOrNull(),
            eventEnd = ends.maxOrNull(),
            recordedAt = now,
            lastConfirmedAt = now,
            lastAccessedAt = now,
            source = MemSource.DERIVED,
            extra = JsonInstant.encodeToString(MemoryExtra()),
        )
        nodeDao.upsert(gist)
        nodeDao.insertFts(MemoryNodeFtsEntity(id, content, ""))
        // ABOUT the union of the episodes' entities; DERIVED_FROM each original; originals → FORGOTTEN (grace).
        val entities = episodes.flatMap { aboutEntitiesOf(it.id) }.distinct()
        for (eid in entities) addEdge(id, eid, MemEdgeType.ABOUT, now)
        for (ep in episodes) {
            addEdge(id, ep.id, MemEdgeType.DERIVED_FROM, now)
            if (!ep.pinned) nodeDao.update(ep.copy(status = MemStatus.FORGOTTEN, lastAccessedAt = now))
        }
        writeProvenanceCopy(id, "compressed from ${episodes.size} episodes", now)
        return true
    }

    private suspend fun createHabit(ref: ScopeRef, statement: String, episodes: List<MemoryNodeEntity>, now: Long): Boolean {
        val content = statement.trim().ifBlank { return false }
        val id = Uuid.random().toString()
        val habit = MemoryNodeEntity(
            id = id,
            type = MemNodeType.HABIT,
            scope = ref.scope,
            ownerAssistantId = ref.ownerId,
            content = content,
            importance = 3,
            confidence = 1f,
            status = MemStatus.ACTIVE,
            recordedAt = now,
            lastConfirmedAt = now,
            lastAccessedAt = now,
            source = MemSource.DERIVED,
            extra = JsonInstant.encodeToString(MemoryExtra(habitCadence = content)),
        )
        nodeDao.upsert(habit)
        nodeDao.insertFts(MemoryNodeFtsEntity(id, content, ""))
        val entities = episodes.flatMap { aboutEntitiesOf(it.id) }.distinct()
        for (eid in entities) addEdge(id, eid, MemEdgeType.ABOUT, now)
        for (ep in episodes) addEdge(ep.id, id, MemEdgeType.INSTANCE_OF, now) // episode → habit
        writeProvenanceCopy(id, "induced from ${episodes.size} episodes", now)
        return true
    }

    // ================= §5.4 stage 7: scope-promotion review (§6.5) =================

    /**
     * Promote identity-level user facts to the shared GLOBAL_USER layer, and offer the rest as opt-in
     * chips. Conservative by construction ([PromotionLogic]): only ACTIVE, NORMAL user FACTs on the
     * closed identity whitelist auto-promote; SENSITIVE never promotes and never chips; everything
     * else becomes a PROMOTION_SUGGESTED chip (non-action stays private). A character opted out of the
     * shared layer (`useSharedUserMemory=false`) is skipped entirely — no promotion, no chips.
     */
    private suspend fun promotionReviewStage(ref: ScopeRef, assistant: me.rerere.rikkahub.data.model.Assistant?, now: Long) {
        if (assistant?.useSharedUserMemory == false) return
        val ownerId = ref.ownerId ?: return

        val nodes = nodeDao.getVisibleWithStatuses(ownerKeyForRead(ref), listOf(MemStatus.ACTIVE)).filter { inScope(it, ref) }
        val userEntityIds = nodes
            .filter { it.type == MemNodeType.ENTITY && MemoryText.normalize(it.displayLabel ?: it.content) == "user" }
            .map { it.id }.toSet()
        if (userEntityIds.isEmpty()) return
        val facts = nodes.filter { it.type == MemNodeType.FACT }
        if (facts.isEmpty()) return

        val existingPending = activityDao.getPendingSuggestions(ownerId, MemActivityKind.PROMOTION_SUGGESTED, MemActivityState.PENDING)
        val alreadySuggested = existingPending.flatMap { parseNodeIdList(it.nodeIds) }.toSet()
        var suggestionSlots = (MAX_PENDING_PROMOTION_CHIPS - existingPending.size).coerceAtLeast(0)

        scopeLocks.withScope(ref.key) {
            db.withTransaction {
                var promoted = 0
                for (fact in facts) {
                    val fresh = nodeDao.getById(fact.id) ?: continue
                    if (fresh.scope != MemScope.CHARACTER || fresh.status != MemStatus.ACTIVE) continue
                    val isAboutUser = aboutEntitiesOf(fresh.id).any { it in userEntityIds }
                    val category = decodeExtra(fresh.extra).category
                    when (PromotionLogic.classify(fresh.scope, fresh.status, fresh.sensitivity, fresh.type, isAboutUser, category)) {
                        PromotionLogic.Action.AUTO -> {
                            nodeDao.update(fresh.copy(scope = MemScope.GLOBAL_USER, ownerAssistantId = null, lastAccessedAt = now))
                            promoted++
                            activityDao.insert(
                                MemoryActivityEntity(
                                    id = Uuid.random().toString(), at = now, scope = MemScope.GLOBAL_USER,
                                    ownerAssistantId = null, kind = MemActivityKind.PROMOTED,
                                    summary = "shared with all characters: ${fresh.content.take(80)}",
                                    nodeIds = JsonInstant.encodeToString(listOf(fresh.id)),
                                )
                            )
                        }

                        PromotionLogic.Action.SUGGEST -> {
                            if (suggestionSlots > 0 && fresh.id !in alreadySuggested) {
                                suggestionSlots--
                                activityDao.insert(
                                    MemoryActivityEntity(
                                        id = Uuid.random().toString(), at = now, scope = MemScope.CHARACTER,
                                        ownerAssistantId = ownerId, kind = MemActivityKind.PROMOTION_SUGGESTED,
                                        summary = "Share “${fresh.content.take(80)}” with all characters?",
                                        nodeIds = JsonInstant.encodeToString(listOf(fresh.id)),
                                        state = MemActivityState.PENDING,
                                    )
                                )
                            }
                        }

                        PromotionLogic.Action.SKIP -> Unit
                    }
                }
                if (promoted > 0) {
                    // A summary row on the character scope so the character's own feed reflects it too.
                    activityDao.insert(
                        MemoryActivityEntity(
                            id = Uuid.random().toString(), at = now, scope = MemScope.CHARACTER,
                            ownerAssistantId = ownerId, kind = MemActivityKind.PROMOTED,
                            summary = "$promoted fact(s) shared with all characters",
                            nodeIds = "[]",
                        )
                    )
                }
            }
        }
    }

    private fun parseNodeIdList(json: String): List<String> = runCatching {
        JsonInstant.decodeFromString<List<String>>(json)
    }.getOrDefault(emptyList())

    // ================= §9 stage 8: size enforcement (always) =================

    private suspend fun enforceSize(ref: ScopeRef, now: Long) {
        scopeLocks.withScope(ref.key) {
            db.withTransaction {
                // Hard-delete FORGOTTEN-past-grace with edges (also enforced by the decay stage, repeated
                // here so a purely size-triggered run reclaims space even if decay found nothing).
                val forgotten = nodeDao.getAllByStatuses(listOf(MemStatus.FORGOTTEN)).filter { inScope(it, ref) }
                var purged = 0
                for (node in forgotten) {
                    if (!node.pinned && now - node.lastAccessedAt > MemorySleepLogic.FORGET_GRACE_MS) {
                        hardDelete(node.id); purged++
                    }
                }

                // Evict lowest-retention DORMANT nodes if over budget. "Footprint" = the retrievable
                // non-entity set (ACTIVE/PROVISIONAL/DORMANT/CLOSED); evicting DORMANT→FORGOTTEN removes
                // it from that set, so this genuinely lowers the counted size (§9).
                val scopeNodes = nodeDao.getVisibleWithStatuses(ownerKeyForRead(ref), MemoryGraphRepository.SEARCHABLE_STATUSES)
                    .filter { inScope(it, ref) && it.type != MemNodeType.ENTITY }
                var evicted = 0
                val footprint = scopeNodes.size
                if (footprint > ref.nodeBudget) {
                    val overflow = footprint - ref.nodeBudget
                    val dormant = scopeNodes.filter { it.status == MemStatus.DORMANT && !it.pinned }.map { it.toRetentionRef() }
                    for (id in MemorySleepLogic.evictionOrder(dormant, now).take(overflow)) {
                        val n = nodeDao.getById(id) ?: continue
                        nodeDao.update(n.copy(status = MemStatus.FORGOTTEN, lastAccessedAt = now))
                        evicted++
                    }
                }

                writeMaintenanceActivity(ref, now, buildList {
                    if (purged > 0) add(MemActivityKind.FORGOTTEN to "$purged purged")
                    if (evicted > 0) add(MemActivityKind.EVICTED to "$evicted evicted (over budget)")
                })
            }
        }
    }

    /** Provenance cap (§9): keep the first + last 3 rows per over-full node, store-wide. */
    private suspend fun capProvenance() {
        val overfull = provenanceDao.nodesExceedingProvenance(PROVENANCE_KEEP_PER_NODE)
        for (nodeId in overfull) {
            val rows = provenanceDao.getForNode(nodeId) // ordered created_at ASC
            if (rows.size <= PROVENANCE_KEEP_PER_NODE) continue
            val keep = (listOf(rows.first()) + rows.takeLast(3)).map { it.id }.toSet()
            val drop = rows.filter { it.id !in keep }.map { it.id }
            if (drop.isNotEmpty()) provenanceDao.deleteByIds(drop)
        }
    }

    // ================= clustering helpers =================

    private fun isOldLowImportance(node: MemoryNodeEntity, now: Long): Boolean =
        node.importance <= LOW_IMPORTANCE_MAX &&
            (now - (node.eventStart ?: node.recordedAt)) > COMPRESSION_AGE_MS

    /** Group old, low-importance episodes by frame + primary entity + coarse era for gist compression. */
    private suspend fun episodeClusters(episodes: List<MemoryNodeEntity>, now: Long): Map<String, List<MemoryNodeEntity>> {
        val out = mutableMapOf<String, MutableList<MemoryNodeEntity>>()
        for (ep in episodes.filter { isOldLowImportance(it, now) }) {
            val frame = firstEdgeTarget(ep.id, MemEdgeType.IN_FRAME) ?: "-"
            val entity = primaryEntityId(ep) ?: "-"
            val era = (ep.eventStart ?: ep.recordedAt) / ERA_BUCKET_MS
            out.getOrPut("$frame|$entity|$era") { mutableListOf() }.add(ep)
        }
        return out
    }

    private suspend fun primaryEntityId(node: MemoryNodeEntity): String? = aboutEntitiesOf(node.id).firstOrNull()

    // ================= low-level graph helpers =================

    private fun isLive(node: MemoryNodeEntity): Boolean =
        node.status == MemStatus.ACTIVE || node.status == MemStatus.PROVISIONAL ||
            node.status == MemStatus.DORMANT || node.status == MemStatus.CLOSED

    private fun inScope(node: MemoryNodeEntity, ref: ScopeRef): Boolean =
        node.scope == ref.scope && node.ownerAssistantId == ref.ownerId

    /** For CHARACTER scope the read query key is the assistant id; for GLOBAL a synthetic id that
     *  matches no owner, so the `scope=0 OR owner=..` predicate returns exactly the global layer. */
    private fun ownerKeyForRead(ref: ScopeRef): String = ref.ownerId ?: "__global_only__"

    private suspend fun aboutEntitiesOf(nodeId: String): List<String> =
        edgeDao.getOutgoing(nodeId).filter { it.type == MemEdgeType.ABOUT }.map { it.toId }

    private suspend fun firstEdgeTarget(nodeId: String, type: Int): String? =
        edgeDao.getOutgoing(nodeId).firstOrNull { it.type == type }?.toId

    private suspend fun addEdge(from: String, to: String, type: Int, now: Long) {
        if (from == to) return
        edgeDao.upsert(MemoryEdgeEntity(id = Uuid.random().toString(), fromId = from, toId = to, type = type, createdAt = now))
    }

    private suspend fun hardDelete(id: String) {
        edgeDao.deleteTouching(id)
        provenanceDao.deleteForNode(id)
        nodeDao.deleteFtsByNode(id)
        nodeDao.deleteById(id)
    }

    private suspend fun unionProvenance(mergedId: String, aId: String, bId: String, now: Long) {
        val rows = (provenanceDao.getForNode(aId) + provenanceDao.getForNode(bId)).sortedBy { it.createdAt }
        val bounded = if (rows.size <= PROVENANCE_KEEP_PER_NODE) rows else listOf(rows.first()) + rows.takeLast(3)
        for (row in bounded) {
            provenanceDao.insert(row.copy(id = Uuid.random().toString(), nodeId = mergedId))
        }
    }

    private suspend fun writeProvenanceCopy(nodeId: String, rationale: String, now: Long) {
        provenanceDao.insert(
            MemoryProvenanceEntity(id = Uuid.random().toString(), nodeId = nodeId, rationale = rationale, createdAt = now)
        )
    }

    private suspend fun writeMaintenanceActivity(ref: ScopeRef, now: Long, parts: List<Pair<String, String>>) {
        if (parts.isEmpty()) return
        val kind = parts.first().first
        activityDao.insert(
            MemoryActivityEntity(
                id = Uuid.random().toString(),
                at = now,
                scope = ref.scope,
                ownerAssistantId = ref.ownerId,
                kind = kind,
                summary = parts.joinToString(", ") { it.second },
                nodeIds = "[]",
            )
        )
    }

    // ================= model plumbing =================

    // No fallback chain: an unset consolidation model simply pauses the model-assisted stages.
    private fun resolveModel(settings: Settings): kotlin.Pair<ProviderSetting, Model>? =
        MemoryModels.resolveConsolidation(settings)

    private suspend fun callModel(provider: ProviderSetting, model: Model, prompt: String, settings: Settings): String {
        val handler = providerManager.getProviderByType(provider)
        val response = handler.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(prompt)),
            params = settings.buildSummarizerGenerationParams(model = model, temperature = 0.2f),
        )
        return response.choices.firstOrNull()?.message?.toContentText().orEmpty()
    }

    private fun decodeExtra(json: String): MemoryExtra =
        runCatching { JsonInstant.decodeFromString<MemoryExtra>(json) }.getOrDefault(MemoryExtra())

    // ================= prompt builders =================

    private fun buildAdjudicationPrompt(pairs: List<Pair2>, byId: Map<String, MemoryNodeEntity>): String = buildString {
        appendLine("You resolve near-duplicate / conflicting memories about a user. For each PAIR, decide:")
        appendLine("- MERGE: they state the same belief (restatements) → combine into one.")
        appendLine("- SUPERSEDE: one corrects/replaces the other (e.g. changed city, changed major).")
        appendLine("- COEXIST: both are independently true (e.g. coffee at work, tea at home).")
        appendLine("Output ONLY a JSON array. For MERGE include a concise \"merged_content\".")
        appendLine()
        pairs.forEach { p ->
            val a = byId[p.aId]; val b = byId[p.bId]
            appendLine("PAIR a=${p.aId} b=${p.bId}")
            appendLine("  a: ${a?.content}")
            appendLine("  b: ${b?.content}")
        }
        appendLine()
        appendLine("""Format: [{"a":"<id>","b":"<id>","decision":"MERGE|SUPERSEDE|COEXIST","merged_content":"..."}]""")
    }

    private fun buildCompressionPrompt(
        clusters: Map<String, List<MemoryNodeEntity>>,
        habitGroups: Map<String?, List<MemoryNodeEntity>>,
    ): String = buildString {
        appendLine("You compress old episodic memories into gists and induce habits from repeated episodes.")
        appendLine("Output ONLY a JSON object: {\"gists\":[...],\"habits\":[...]}.")
        if (clusters.isNotEmpty()) {
            appendLine()
            appendLine("EPISODE CLUSTERS to compress — write one short past-tense gist per cluster:")
            clusters.forEach { (_, eps) ->
                appendLine("  cluster episodes:")
                eps.forEach { appendLine("    [${it.id}] ${it.content}") }
            }
        }
        if (habitGroups.isNotEmpty()) {
            appendLine()
            appendLine("HABIT CANDIDATES — if ≥3 episodes reveal a recurring pattern, state it (\"usually …\"):")
            habitGroups.forEach { (_, eps) ->
                appendLine("  group:")
                eps.forEach { appendLine("    [${it.id}] ${it.content}") }
            }
        }
        appendLine()
        appendLine("""Format: {"gists":[{"summary":"...","episode_ids":["id",...]}],"habits":[{"statement":"usually ...","instance_ids":["id",...]}]}""")
    }

    // ================= entity → sleep-logic projections =================

    private fun MemoryNodeEntity.toSleepState() = MemorySleepLogic.NodeState(
        status = status, type = type, pinned = pinned, importance = importance,
        timesReinforced = timesReinforced, timesRetrieved = timesRetrieved,
        lastAccessedAt = lastAccessedAt, recordedAt = recordedAt, source = source,
    )

    private fun MemoryNodeEntity.toMergeInput() = MemorySleepLogic.MergeInput(
        pinned = pinned, timesReinforced = timesReinforced, importance = importance,
        confidence = confidence, recordedAt = recordedAt, lastConfirmedAt = lastConfirmedAt,
    )

    private fun MemoryNodeEntity.toRetentionRef() = MemorySleepLogic.RetentionRef(
        id = id, importance = importance, timesReinforced = timesReinforced,
        timesRetrieved = timesRetrieved, lastAccessedAt = lastAccessedAt,
    )
}

/**
 * Tolerant JSON parsers for the sleep pass's two batched model calls (adjudication, compression).
 * Pure and dependency-free like [MemoryOpParser]: one malformed entry never discards the batch.
 */
object SleepDecisionParser {
    enum class Verdict { MERGE, SUPERSEDE, COEXIST }

    data class Adjudication(val aId: String, val bId: String, val decision: Verdict, val mergedContent: String?)

    data class GistPlan(val summary: String, val episodeIds: List<String>)
    data class HabitPlan(val statement: String, val instanceIds: List<String>)
    data class CompressionPlan(val gists: List<GistPlan>, val habits: List<HabitPlan>)

    fun parseAdjudications(text: String): List<Adjudication> {
        val array = extractJsonArray(text) ?: return emptyList()
        val out = mutableListOf<Adjudication>()
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val a = obj.str("a") ?: continue
            val b = obj.str("b") ?: continue
            val verdict = when (obj.str("decision")?.uppercase()) {
                "MERGE" -> Verdict.MERGE
                "SUPERSEDE", "SUPERSEDES" -> Verdict.SUPERSEDE
                "COEXIST", "KEEP", "DISTINCT" -> Verdict.COEXIST
                else -> continue
            }
            out += Adjudication(a, b, verdict, obj.str("merged_content"))
        }
        return out
    }

    fun parseCompression(text: String): CompressionPlan {
        val obj = extractJsonObject(text) ?: return CompressionPlan(emptyList(), emptyList())
        val gists = (obj["gists"] as? JsonArray).orEmptyObjects().mapNotNull { g ->
            val summary = g.str("summary") ?: return@mapNotNull null
            val ids = g.strList("episode_ids")
            if (ids.isEmpty()) null else GistPlan(summary, ids)
        }
        val habits = (obj["habits"] as? JsonArray).orEmptyObjects().mapNotNull { h ->
            val statement = h.str("statement") ?: return@mapNotNull null
            val ids = h.strList("instance_ids")
            if (ids.isEmpty()) null else HabitPlan(statement, ids)
        }
        return CompressionPlan(gists, habits)
    }

    private fun extractJsonArray(text: String): JsonArray? {
        val start = text.indexOf('['); val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JsonInstant.parseToJsonElement(text.substring(start, end + 1)) as? JsonArray }.getOrNull()
    }

    private fun extractJsonObject(text: String): JsonObject? {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JsonInstant.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }

    private fun JsonArray?.orEmptyObjects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject } ?: emptyList()
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } } ?: emptyList()
}
