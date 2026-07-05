package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryActivityDao
import me.rerere.rikkahub.data.db.dao.MemoryEdgeDao
import me.rerere.rikkahub.data.db.dao.MemoryNodeDao
import me.rerere.rikkahub.data.db.dao.MemoryProvenanceDao
import me.rerere.rikkahub.data.db.entity.MemActivityKind
import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemReality
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeFtsEntity
import me.rerere.rikkahub.data.db.entity.MemoryProvenanceEntity
import me.rerere.rikkahub.data.model.MemoryApplyResult
import me.rerere.rikkahub.data.model.MemoryExtra
import me.rerere.rikkahub.data.model.MemoryGoalState
import me.rerere.rikkahub.data.model.MemoryOp
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

/**
 * Context for one applier run: who is writing, where it came from, and the source classification
 * that decides scope + initial status.
 */
data class MemoryApplyContext(
    val assistantId: String,
    val conversationId: String? = null,
    val messageIds: List<String> = emptyList(),
    val source: Int = MemSource.EXTRACTED,
    /** If false the character does not participate in the shared layer; irrelevant here because
     *  extraction always writes CHARACTER scope, but kept for future promotion callers. */
    val useSharedUserMemory: Boolean = true,
    val now: Long = System.currentTimeMillis(),
    val maxAdds: Int = 6,
    /**
     * Bitemporal override for imports: when set, new nodes get this as `recordedAt` (when the fact
     * was originally learned) while `lastAccessedAt`/`lastConfirmedAt` still use [now] — so decay
     * restarts at import time instead of instantly demoting old-but-important imports.
     */
    val recordedAt: Long? = null,
    /**
     * Manual writes from the Shared Memory page target GLOBAL_USER scope. Honored ONLY when
     * [source] == MemSource.MANUAL — extraction/import can structurally never write the shared layer.
     */
    val manualGlobalScope: Boolean = false,
)

/**
 * The ONLY code that writes memory nodes/edges (§2). Every op — from extraction, tools, import, or
 * the sleep pass — passes through here, so the guardrails and dedup gate are structural, not a
 * matter of prompt discipline:
 *
 *  - ops referencing unknown ids are dropped (anti-hallucination);
 *  - extraction never writes GLOBAL_USER (scope forced to CHARACTER; promotion is a separate path);
 *  - every non-entity node gets ≥1 ABOUT edge (entity hubs resolved/created here);
 *  - the dedup gate may downgrade an ADD to a REINFORCE, or flag a similar-but-different pair for
 *    sleep-pass adjudication — similarity is never treated as identity;
 *  - caps are clamped (≤ maxAdds inserts/pass, importance 1..5, confidence 0..1);
 *  - provenance + one activity row + the FTS mirror are kept in sync inside a single transaction.
 */
class MemoryOpApplier(
    private val db: AppDatabase,
    private val nodeDao: MemoryNodeDao,
    private val edgeDao: MemoryEdgeDao,
    private val provenanceDao: MemoryProvenanceDao,
    private val activityDao: MemoryActivityDao,
    private val scopeLocks: MemoryScopeLocks,
) {

    /**
     * Apply a batch of ops atomically. Held under the per-scope write lock (§7.2) — not just the DB
     * transaction — so a future sleep pass's read-modify-write cannot interleave with this apply on
     * the same scope. The transaction still bounds atomicity: a crash mid-apply leaves the watermark
     * unmoved and the pass re-runs idempotently (the dedup gate absorbs the replay).
     */
    suspend fun apply(ops: List<MemoryOp>, ctx: MemoryApplyContext): MemoryApplyResult {
        val lockKey = if (ctx.manualGlobalScope && ctx.source == MemSource.MANUAL) {
            MemoryScopeLocks.GLOBAL_SCOPE
        } else {
            ctx.assistantId
        }
        return scopeLocks.withScope(lockKey) {
            db.withTransaction { Pass(ctx).run(ops) }
        }
    }

    /**
     * Ensure the character's profile frames (§6.1) exist as FRAME hub nodes, idempotently by
     * normalized label. Routed through the applier so it stays the single writer of nodes/edges; runs
     * under the per-scope write lock. Updates a frame's roleplay flag/descriptor if the profile
     * changed it. Extraction's `op.frame` strings then reuse these hubs by label (see [Pass.resolveFrame]).
     */
    suspend fun ensureCharacterFrames(
        assistantId: String,
        frames: List<me.rerere.rikkahub.data.memory.CharacterFrame>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (frames.isEmpty()) return
        scopeLocks.withScope(assistantId) {
            db.withTransaction {
                val existing = nodeDao.getVisibleByType(assistantId, MemNodeType.FRAME, listOf(MemStatus.ACTIVE))
                    .filter { it.ownerAssistantId == assistantId }
                for (frame in frames) {
                    val label = frame.label.trim()
                    if (label.isBlank()) continue
                    val norm = MemoryText.normalize(label)
                    val match = existing.firstOrNull { MemoryText.normalize(it.displayLabel ?: it.content) == norm }
                    if (match != null) {
                        val extra = decodeExtra(match.extra)
                        if (extra.frameRoleplay != frame.roleplay || extra.frameLabel != label) {
                            nodeDao.update(match.copy(extra = JsonInstant.encodeToString(extra.copy(frameLabel = label, frameRoleplay = frame.roleplay))))
                        }
                        continue
                    }
                    val id = newId()
                    val node = MemoryNodeEntity(
                        id = id,
                        type = MemNodeType.FRAME,
                        scope = MemScope.CHARACTER,
                        ownerAssistantId = assistantId,
                        content = label,
                        displayLabel = label,
                        importance = 3,
                        confidence = 1f,
                        status = MemStatus.ACTIVE,
                        recordedAt = now,
                        lastConfirmedAt = now,
                        lastAccessedAt = now,
                        source = MemSource.DERIVED,
                        extra = JsonInstant.encodeToString(MemoryExtra(frameLabel = label, frameRoleplay = frame.roleplay)),
                    )
                    nodeDao.upsert(node)
                    nodeDao.insertFts(MemoryNodeFtsEntity(id, label, label))
                }
            }
        }
    }

    /** One transactional pass. Keeps a small in-memory entity cache so hubs created mid-pass are reused. */
    private inner class Pass(val ctx: MemoryApplyContext) {
        /** GLOBAL_USER writes are possible only for user-authored ops from the Shared Memory page. */
        private val globalWrite: Boolean = ctx.manualGlobalScope && ctx.source == MemSource.MANUAL
        private val writeScope: Int = if (globalWrite) MemScope.GLOBAL_USER else MemScope.CHARACTER
        private val writeOwner: String? = if (globalWrite) null else ctx.assistantId

        private val entities = mutableListOf<MemoryNodeEntity>()
        private var entitiesLoaded = false
        private val frames = mutableListOf<MemoryNodeEntity>()
        private var framesLoaded = false

        private val added = mutableListOf<String>()
        private val reinforced = mutableListOf<String>()
        private val updated = mutableListOf<String>()
        private val closed = mutableListOf<String>()
        private val goalsOpened = mutableListOf<String>()
        private val goalsResolved = mutableListOf<String>()
        private val flagged = mutableListOf<String>()
        private var dropped = 0
        private var addsAccepted = 0

        suspend fun run(ops: List<MemoryOp>): MemoryApplyResult {
            for (op in ops) {
                when (op) {
                    is MemoryOp.AddNode -> handleAdd(op)
                    is MemoryOp.AddEdge -> handleAddEdge(op)
                    is MemoryOp.Reinforce -> handleReinforce(op.id, op)
                    is MemoryOp.UpdateNode -> handleUpdate(op)
                    is MemoryOp.CloseNode -> handleClose(op)
                    is MemoryOp.OpenGoal -> handleOpenGoal(op)
                    is MemoryOp.ResolveGoal -> handleResolveGoal(op)
                }
            }
            val result = MemoryApplyResult(
                added = added, reinforced = reinforced, updated = updated, closed = closed,
                goalsOpened = goalsOpened, goalsResolved = goalsResolved,
                flaggedForAdjudication = flagged, dropped = dropped,
            )
            if (result.changed) writeActivity(result)
            return result
        }

        // ---------------- ADD ----------------

        private suspend fun handleAdd(op: MemoryOp.AddNode) {
            if (op.content.isBlank()) { dropped++; return }

            if (op.type == MemNodeType.ENTITY) {
                // Model emitted an entity directly; treat as hub resolution/creation.
                resolveEntity(op.content.trim(), op.aliases)
                return
            }

            val entityIds = if (op.entities.isEmpty()) {
                listOf(resolveUserEntity())
            } else {
                op.entities.filter { it.isNotBlank() }.map { resolveEntity(it.trim(), emptyList()) }
                    .ifEmpty { listOf(resolveUserEntity()) }
            }
            // Attach proposed aliases to the primary (first) entity, with hygiene.
            if (op.aliases.isNotEmpty()) attachAliases(entityIds.first(), op.aliases)

            val neighbors = collectNeighbors(entityIds, op.type)
            when (val decision = MemoryDedupGate.decide(op.content, neighbors)) {
                is MemoryDedupGate.Decision.Reinforce -> reinforceNode(decision.targetId, op.rationale, op.excerpt)
                is MemoryDedupGate.Decision.Flag -> insertNode(op, entityIds, flaggedRelatedId = decision.relatedId)
                MemoryDedupGate.Decision.Insert -> insertNode(op, entityIds, flaggedRelatedId = null)
            }
        }

        private suspend fun insertNode(op: MemoryOp.AddNode, entityIds: List<String>, flaggedRelatedId: String?) {
            if (addsAccepted >= ctx.maxAdds) { dropped++; return }
            addsAccepted++

            val id = newId()
            val extra = MemoryExtra(
                category = op.category,
                validityHorizonMillis = op.validityHorizonMillis,
            )
            val node = MemoryNodeEntity(
                id = id,
                type = op.type,
                scope = writeScope, // extraction never writes GLOBAL_USER; only MANUAL shared adds do
                ownerAssistantId = writeOwner,
                content = op.content.trim(),
                displayLabel = null,
                importance = op.importance.coerceIn(1, 5),
                confidence = op.confidence.coerceIn(0f, 1f),
                sensitivity = if (op.sensitivity == MemSensitivity.SENSITIVE) MemSensitivity.SENSITIVE else MemSensitivity.NORMAL,
                status = initialStatus(),
                pinned = false,
                reality = if (op.reality == MemReality.FICTION) MemReality.FICTION else MemReality.REAL,
                eventStart = op.eventStart,
                eventEnd = op.eventEnd,
                validFrom = op.validFrom,
                validUntil = op.validUntil,
                recordedAt = ctx.recordedAt ?: ctx.now,
                lastConfirmedAt = ctx.now,
                lastAccessedAt = ctx.now,
                source = ctx.source,
                extra = JsonInstant.encodeToString(extra),
                adjudicationPending = flaggedRelatedId != null,
            )
            nodeDao.upsert(node)
            nodeDao.insertFts(MemoryNodeFtsEntity(id, node.content, node.displayLabel ?: ""))
            // ≥1 ABOUT edge to each subject entity — the dedup backbone.
            for (eid in entityIds.distinct()) addEdge(id, eid, MemEdgeType.ABOUT)
            if (flaggedRelatedId != null) addEdge(id, flaggedRelatedId, MemEdgeType.RELATES_TO)
            if (op.frame != null) {
                val frameId = resolveFrame(op.frame)
                addEdge(id, frameId, MemEdgeType.IN_FRAME)
            }
            writeProvenance(id, op.rationale, op.excerpt)
            added += id
            if (flaggedRelatedId != null) flagged += id
        }

        // ---------------- REINFORCE ----------------

        private suspend fun handleReinforce(id: String, op: MemoryOp) {
            if (nodeDao.getById(id) == null) { dropped++; return }
            reinforceNode(id, op.rationale, op.excerpt)
        }

        private suspend fun reinforceNode(id: String, rationale: String, excerpt: String) {
            // Restatement can never resurrect a belief the user corrected away: route to the head
            // of the supersession chain.
            val targetId = followSupersedeChain(id) ?: id
            val fresh = nodeDao.getById(targetId) ?: run { dropped++; return }
            // REINFORCE reactivates: a re-selected branch or re-stated fact pulls a PROVISIONAL or a
            // branch-demoted DORMANT node back to ACTIVE (§7.3).
            val promote = (fresh.status == MemStatus.PROVISIONAL || fresh.status == MemStatus.DORMANT) &&
                !fresh.adjudicationPending
            nodeDao.update(
                fresh.copy(
                    timesReinforced = fresh.timesReinforced + 1,
                    lastConfirmedAt = ctx.now,
                    lastAccessedAt = ctx.now,
                    status = if (promote) MemStatus.ACTIVE else fresh.status,
                )
            )
            writeProvenance(fresh.id, rationale, excerpt)
            if (fresh.id !in reinforced) reinforced += fresh.id
        }

        // ---------------- UPDATE (append-only supersede) ----------------

        private suspend fun handleUpdate(op: MemoryOp.UpdateNode) {
            val old = nodeDao.getById(op.oldId) ?: run { dropped++; return }
            val entityIds = aboutEntitiesOf(old.id).ifEmpty { listOf(resolveUserEntity()) }

            // Never auto-supersede a user-authored belief; record a conflict for the user to settle.
            if (old.source == MemSource.MANUAL || old.source == MemSource.CONFIRMED_BY_USER) {
                if (addsAccepted >= ctx.maxAdds) { dropped++; return }
                addsAccepted++
                val id = newId()
                val node = old.copy(
                    id = id,
                    content = op.content.trim(),
                    importance = (op.importance ?: old.importance).coerceIn(1, 5),
                    confidence = (op.confidence ?: old.confidence).coerceIn(0f, 1f),
                    status = initialStatus(),
                    source = ctx.source,
                    recordedAt = ctx.now,
                    lastConfirmedAt = ctx.now,
                    lastAccessedAt = ctx.now,
                    timesReinforced = 0,
                    timesRetrieved = 0,
                    adjudicationPending = true,
                )
                nodeDao.upsert(node)
                nodeDao.insertFts(MemoryNodeFtsEntity(id, node.content, node.displayLabel ?: ""))
                for (eid in entityIds.distinct()) addEdge(id, eid, MemEdgeType.ABOUT)
                addEdge(id, old.id, MemEdgeType.CONTRADICTS)
                writeProvenance(id, op.rationale, op.excerpt)
                added += id
                flagged += id
                return
            }

            if (addsAccepted >= ctx.maxAdds) { dropped++; return }
            addsAccepted++
            val id = newId()
            val node = old.copy(
                id = id,
                content = op.content.trim(),
                importance = (op.importance ?: old.importance).coerceIn(1, 5),
                confidence = (op.confidence ?: old.confidence).coerceIn(0f, 1f),
                status = initialStatus(),
                source = ctx.source,
                validUntil = op.validUntil ?: old.validUntil,
                recordedAt = ctx.now,
                lastConfirmedAt = ctx.now,
                lastAccessedAt = ctx.now,
                timesReinforced = 0,
                timesRetrieved = 0,
                adjudicationPending = false,
            )
            nodeDao.upsert(node)
            nodeDao.insertFts(MemoryNodeFtsEntity(id, node.content, node.displayLabel ?: ""))
            for (eid in entityIds.distinct()) addEdge(id, eid, MemEdgeType.ABOUT)
            addEdge(id, old.id, MemEdgeType.SUPERSEDES)
            nodeDao.update(old.copy(status = MemStatus.SUPERSEDED))
            writeProvenance(id, op.rationale, op.excerpt)
            updated += id
        }

        // ---------------- CLOSE ----------------

        private suspend fun handleClose(op: MemoryOp.CloseNode) {
            val node = nodeDao.getById(op.id) ?: run { dropped++; return }
            val end = op.endedAt ?: ctx.now
            nodeDao.update(
                node.copy(
                    status = MemStatus.CLOSED,
                    validUntil = if (node.type == MemNodeType.FACT) (node.validUntil ?: end) else node.validUntil,
                    eventEnd = if (node.type == MemNodeType.EPISODE) (node.eventEnd ?: end) else node.eventEnd,
                    lastAccessedAt = ctx.now,
                )
            )
            closed += node.id
        }

        // ---------------- GOALS ----------------

        private suspend fun handleOpenGoal(op: MemoryOp.OpenGoal) {
            if (op.question.isBlank()) { dropped++; return }
            val entityIds = op.entities.filter { it.isNotBlank() }.map { resolveEntity(it.trim(), emptyList()) }
            val id = newId()
            val extra = MemoryExtra(
                goalState = MemoryGoalState.OPEN,
                goalQuestion = op.question.trim(),
                valueNote = op.valueNote,
            )
            val node = MemoryNodeEntity(
                id = id,
                type = MemNodeType.GOAL,
                scope = MemScope.CHARACTER,
                ownerAssistantId = ctx.assistantId,
                content = op.question.trim(),
                importance = 3,
                confidence = 1f,
                status = MemStatus.ACTIVE,
                recordedAt = ctx.now,
                lastConfirmedAt = ctx.now,
                lastAccessedAt = ctx.now,
                source = ctx.source,
                extra = JsonInstant.encodeToString(extra),
            )
            nodeDao.upsert(node)
            for (eid in entityIds.distinct()) addEdge(id, eid, MemEdgeType.ABOUT)
            writeProvenance(id, op.rationale, op.excerpt)
            goalsOpened += id
        }

        private suspend fun handleResolveGoal(op: MemoryOp.ResolveGoal) {
            val node = nodeDao.getById(op.id) ?: run { dropped++; return }
            if (node.type != MemNodeType.GOAL) { dropped++; return }
            // Normalize the model's outcome through the back-off state machine: DECLINED retires the
            // goal forever, CONFIRMED banks the answer, anything else leaves it for the ignore sweep.
            val newState = CuriosityLogic.stateOnOutcome(op.outcome)
            val extra = decodeExtra(node.extra).copy(goalState = newState)
            nodeDao.update(node.copy(extra = JsonInstant.encodeToString(extra), lastAccessedAt = ctx.now))
            goalsResolved += node.id

            // A confirmed goal that taught us something enters the store as a high-confidence,
            // user-confirmed FACT through the same dedup guardrails (§6.4) — only on CONFIRMED.
            val learned = op.learned?.trim()
            if (newState != MemoryGoalState.CONFIRMED || learned.isNullOrBlank() || addsAccepted >= ctx.maxAdds) return
            val entityIds = aboutEntitiesOf(node.id).ifEmpty { listOf(resolveUserEntity()) }
            val neighbors = collectNeighbors(entityIds, MemNodeType.FACT)
            when (val decision = MemoryDedupGate.decide(learned, neighbors)) {
                is MemoryDedupGate.Decision.Reinforce -> reinforceNode(decision.targetId, op.rationale, op.excerpt)
                else -> {
                    addsAccepted++
                    val id = newId()
                    val flaggedRelated = (decision as? MemoryDedupGate.Decision.Flag)?.relatedId
                    val factNode = MemoryNodeEntity(
                        id = id,
                        type = MemNodeType.FACT,
                        scope = MemScope.CHARACTER,
                        ownerAssistantId = ctx.assistantId,
                        content = learned,
                        importance = 4,
                        confidence = 0.95f,
                        status = MemStatus.ACTIVE,
                        reality = MemReality.REAL,
                        recordedAt = ctx.now,
                        lastConfirmedAt = ctx.now,
                        lastAccessedAt = ctx.now,
                        source = MemSource.CONFIRMED_BY_USER,
                        extra = JsonInstant.encodeToString(MemoryExtra()),
                        adjudicationPending = flaggedRelated != null,
                    )
                    nodeDao.upsert(factNode)
                    nodeDao.insertFts(MemoryNodeFtsEntity(id, learned, ""))
                    for (eid in entityIds.distinct()) addEdge(id, eid, MemEdgeType.ABOUT)
                    if (flaggedRelated != null) addEdge(id, flaggedRelated, MemEdgeType.RELATES_TO)
                    writeProvenance(id, op.rationale.ifBlank { "confirmed via a curiosity question" }, op.excerpt)
                    added += id
                    if (flaggedRelated != null) flagged += id
                }
            }
        }

        // ---------------- EDGE (extraction: RELATES_TO / CONTRADICTS only) ----------------

        private suspend fun handleAddEdge(op: MemoryOp.AddEdge) {
            if (op.type != MemEdgeType.RELATES_TO && op.type != MemEdgeType.CONTRADICTS) { dropped++; return }
            if (op.fromId == op.toId) { dropped++; return }
            if (nodeDao.getById(op.fromId) == null || nodeDao.getById(op.toId) == null) { dropped++; return }
            addEdge(op.fromId, op.toId, op.type)
        }

        // ---------------- helpers ----------------

        private fun initialStatus(): Int =
            if (ctx.source == MemSource.EXTRACTED) MemStatus.PROVISIONAL else MemStatus.ACTIVE

        private suspend fun ensureEntitiesLoaded() {
            if (entitiesLoaded) return
            entities.addAll(nodeDao.getVisibleEntities(ctx.assistantId))
            entitiesLoaded = true
        }

        private suspend fun resolveEntity(label: String, aliases: List<String>): String {
            ensureEntitiesLoaded()
            val norm = MemoryText.normalize(label)
            val existing = entities.firstOrNull { entityMatches(it, norm) }
            if (existing != null) {
                if (aliases.isNotEmpty()) attachAliases(existing.id, aliases)
                return existing.id
            }
            val id = newId()
            val extra = MemoryExtra(aliases = hygienicAliases(aliases, canonical = label))
            val node = MemoryNodeEntity(
                id = id,
                type = MemNodeType.ENTITY,
                scope = writeScope,
                ownerAssistantId = writeOwner,
                content = label,
                displayLabel = label,
                importance = 3,
                confidence = 1f,
                status = MemStatus.ACTIVE,
                recordedAt = ctx.now,
                lastConfirmedAt = ctx.now,
                lastAccessedAt = ctx.now,
                source = ctx.source,
                extra = JsonInstant.encodeToString(extra),
            )
            nodeDao.upsert(node)
            nodeDao.insertFts(MemoryNodeFtsEntity(id, label, label))
            entities.add(node)
            return id
        }

        private suspend fun resolveUserEntity(): String = resolveEntity("User", listOf("me", "I"))

        private suspend fun ensureFramesLoaded() {
            if (framesLoaded) return
            // Reuse profile-created / previously-extracted FRAME hubs so episodes attach to the same
            // frame across passes (§6.1/6.2) — getVisibleEntities returns only ENTITY nodes, so frames
            // need their own cache.
            frames.addAll(nodeDao.getVisibleByType(ctx.assistantId, MemNodeType.FRAME, listOf(MemStatus.ACTIVE))
                .filter { it.ownerAssistantId == ctx.assistantId })
            framesLoaded = true
        }

        private suspend fun resolveFrame(label: String): String {
            ensureFramesLoaded()
            val norm = MemoryText.normalize(label)
            val existing = frames.firstOrNull { MemoryText.normalize(it.displayLabel ?: it.content) == norm }
            if (existing != null) return existing.id
            val id = newId()
            val node = MemoryNodeEntity(
                id = id,
                type = MemNodeType.FRAME,
                scope = MemScope.CHARACTER,
                ownerAssistantId = ctx.assistantId,
                content = label,
                displayLabel = label,
                importance = 3,
                confidence = 1f,
                status = MemStatus.ACTIVE,
                recordedAt = ctx.now,
                lastConfirmedAt = ctx.now,
                lastAccessedAt = ctx.now,
                source = ctx.source,
                extra = JsonInstant.encodeToString(MemoryExtra(frameLabel = label)),
            )
            nodeDao.upsert(node)
            nodeDao.insertFts(MemoryNodeFtsEntity(id, label, label))
            frames.add(node)
            return id
        }

        private fun entityMatches(entity: MemoryNodeEntity, normalizedLabel: String): Boolean {
            if (MemoryText.normalize(entity.displayLabel ?: entity.content) == normalizedLabel) return true
            return decodeExtra(entity.extra).aliases.any { MemoryText.normalize(it) == normalizedLabel }
        }

        private suspend fun attachAliases(entityId: String, aliases: List<String>) {
            val entity = entities.firstOrNull { it.id == entityId } ?: nodeDao.getById(entityId) ?: return
            val canonical = entity.displayLabel ?: entity.content
            val extra = decodeExtra(entity.extra)
            val merged = (extra.aliases + hygienicAliases(aliases, canonical)).distinctBy { MemoryText.normalize(it) }
            if (merged.size == extra.aliases.size) return
            val newEntity = entity.copy(extra = JsonInstant.encodeToString(extra.copy(aliases = merged)))
            nodeDao.update(newEntity)
            entities.replaceAll { if (it.id == entityId) newEntity else it }
        }

        /** Alias hygiene: drop aliases that exactly match this entity's own canonical label, or any
         *  other in-scope entity's canonical label (an alias that IS another entity is meaningless). */
        private fun hygienicAliases(aliases: List<String>, canonical: String): List<String> {
            val canonicalNorm = MemoryText.normalize(canonical)
            val otherLabels = entities.map { MemoryText.normalize(it.displayLabel ?: it.content) }.toSet()
            return aliases
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { MemoryText.normalize(it) != canonicalNorm }
                .filter { MemoryText.normalize(it) !in otherLabels }
                .distinctBy { MemoryText.normalize(it) }
        }

        private suspend fun collectNeighbors(entityIds: List<String>, type: Int): List<MemoryDedupGate.NeighborView> {
            if (entityIds.isEmpty()) return emptyList()
            val aboutEdges = edgeDao.getAboutEdgesForEntities(entityIds)
            val neighborIds = aboutEdges.map { it.fromId }.distinct()
            if (neighborIds.isEmpty()) return emptyList()
            return nodeDao.getByIds(neighborIds)
                .filter { it.type == type && it.status != MemStatus.SUPERSEDED && it.status != MemStatus.FORGOTTEN }
                .map { MemoryDedupGate.NeighborView(it.id, it.content) }
        }

        private suspend fun aboutEntitiesOf(nodeId: String): List<String> =
            edgeDao.getOutgoing(nodeId).filter { it.type == MemEdgeType.ABOUT }.map { it.toId }

        private suspend fun followSupersedeChain(id: String, maxHops: Int = 16): String? {
            var current = nodeDao.getById(id) ?: return null
            val visited = HashSet<String>()
            var hops = 0
            while (current.status == MemStatus.SUPERSEDED && hops < maxHops) {
                if (!visited.add(current.id)) break
                val next = edgeDao.getIncoming(current.id)
                    .firstOrNull { it.type == MemEdgeType.SUPERSEDES }
                    ?.fromId
                    ?.let { nodeDao.getById(it) } ?: break
                current = next
                hops++
            }
            return current.id
        }

        private suspend fun addEdge(from: String, to: String, type: Int) {
            edgeDao.upsert(MemoryEdgeEntity(id = newId(), fromId = from, toId = to, type = type, createdAt = ctx.now))
        }

        private suspend fun writeProvenance(nodeId: String, rationale: String, excerpt: String) {
            provenanceDao.insert(
                MemoryProvenanceEntity(
                    id = newId(),
                    nodeId = nodeId,
                    conversationId = ctx.conversationId,
                    messageIds = JsonInstant.encodeToString(ctx.messageIds),
                    excerpt = excerpt.take(300),
                    rationale = rationale.take(300),
                    createdAt = ctx.now,
                )
            )
        }

        private suspend fun writeActivity(result: MemoryApplyResult) {
            val kind = when (ctx.source) {
                MemSource.IMPORTED -> MemActivityKind.IMPORTED
                MemSource.MANUAL -> MemActivityKind.MANUAL_ADDED
                MemSource.CONFIRMED_BY_USER -> MemActivityKind.EXTRACTED
                else -> MemActivityKind.EXTRACTED
            }
            val parts = buildList {
                if (result.added.isNotEmpty()) add("${result.added.size} new")
                if (result.reinforced.isNotEmpty()) add("${result.reinforced.size} reinforced")
                if (result.updated.isNotEmpty()) add("${result.updated.size} updated")
                if (result.closed.isNotEmpty()) add("${result.closed.size} closed")
            }
            activityDao.insert(
                MemoryActivityEntity(
                    id = newId(),
                    at = ctx.now,
                    scope = writeScope,
                    ownerAssistantId = writeOwner,
                    kind = kind,
                    summary = if (parts.isEmpty()) "memory updated" else parts.joinToString(", "),
                    nodeIds = JsonInstant.encodeToString(result.touchedNodeIds),
                    conversationId = ctx.conversationId,
                )
            )
        }
    }

    private fun decodeExtra(json: String): MemoryExtra =
        runCatching { JsonInstant.decodeFromString<MemoryExtra>(json) }.getOrDefault(MemoryExtra())

    private fun newId(): String = Uuid.random().toString()
}
