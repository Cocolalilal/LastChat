package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import kotlin.math.ln

/**
 * Pure, deterministic decisions for the sleep pass (§5.4, §9) — the half that must be correct even
 * when the model budget is zero. Everything here is model-independent, dependency-free (no Room,
 * no Android), and unit-tested on the JVM: retention scoring, the decay/forget ladder, provisional
 * promotion gates, validity expiry, MANUAL protection, deterministic MERGE field combination, and
 * the lowest-retention-first eviction order used by size enforcement.
 *
 * The orchestration around these decisions ([MemorySleepPass]) does the DB reads/writes under the
 * per-scope write lock; this object never touches state.
 */
object MemorySleepLogic {

    // ---- retention score weights (§5.4 stage 1) ----
    // R = importance·w1 + log(1+reinforced)·w2 + log(1+retrieved)·w3 − ageDecay(last_accessed)
    const val W_IMPORTANCE = 1.0
    const val W_REINFORCED = 0.6
    const val W_RETRIEVED = 0.8

    /** Age decay: the node loses ~1 retention point for every [DECAY_TAU_DAYS] of not being recalled. */
    const val DECAY_TAU_DAYS = 30.0

    private const val DAY_MS = 86_400_000.0

    // ---- decay ladder thresholds ----
    /** An ACTIVE node with retention below this (and old enough) demotes to DORMANT. */
    const val DORMANT_THRESHOLD = 1.0
    /** A DORMANT node with retention below this is FORGOTTEN (enters the reversible grace window). */
    const val FORGET_THRESHOLD = -1.0
    /** ACTIVE nodes are never demoted before this idle age — protects freshly-learned facts. */
    const val MIN_DECAY_AGE_DAYS = 14.0
    /** FORGOTTEN nodes stay restorable for this long before the size stage may hard-delete them. */
    const val FORGET_GRACE_MS = 30L * 24 * 60 * 60 * 1000

    // ---- provisional auto-promotion gates ----
    const val PROMOTION_MIN_AGE_DAYS = 7.0
    const val PROMOTION_MIN_IMPORTANCE = 3

    /**
     * Minimal projection of a node's fields the decay/promotion decisions need. Keeps the logic pure
     * and testable without constructing a Room entity (mirrors [MemoryBranchLogic.ProvRow]).
     */
    data class NodeState(
        val status: Int,
        val type: Int,
        val pinned: Boolean,
        val importance: Int,
        val timesReinforced: Int,
        val timesRetrieved: Int,
        val lastAccessedAt: Long,
        val recordedAt: Long,
        val source: Int,
    )

    /** What the decay sweep should do with a node this run. */
    enum class DecayAction { KEEP, DEMOTE_DORMANT, FORGET, DELETE }

    /**
     * Retention score. `times_retrieved`/`last_accessed_at` must count only genuine recall (never
     * core-sheet injection) — that invariant is upheld at the call sites (only query-recall bumps
     * them); this function just consumes the stored values.
     */
    fun retentionScore(
        importance: Int,
        timesReinforced: Int,
        timesRetrieved: Int,
        lastAccessedAt: Long,
        now: Long,
    ): Double {
        val ageDays = (now - lastAccessedAt).coerceAtLeast(0) / DAY_MS
        return importance * W_IMPORTANCE +
            ln(1.0 + timesReinforced) * W_REINFORCED +
            ln(1.0 + timesRetrieved) * W_RETRIEVED -
            ageDays / DECAY_TAU_DAYS
    }

    fun retentionScore(state: NodeState, now: Long): Double =
        retentionScore(state.importance, state.timesReinforced, state.timesRetrieved, state.lastAccessedAt, now)

    /**
     * The decay ladder (§5.4 stage 1). Pinned and ENTITY nodes are exempt (entities die only when
     * orphaned, handled separately). ACTIVE→DORMANT→FORGOTTEN→delete; SUPERSEDED/CLOSED history is
     * left untouched here (CLOSED facts stay retrievable as "used to …").
     *
     * ACTIVE→DORMANT does not reset `last_accessed_at`, so the idle clock keeps running and a node
     * ages naturally across successive runs. DORMANT→FORGOTTEN *does* stamp the grace start (the
     * caller sets `last_accessed_at = now`), so the 30-day restore window is measured from then.
     *
     * PROVISIONAL nodes age on the same ACTIVE ladder so that trivia stated once (and never promoted
     * because it is low-importance or flagged) still decays away instead of accumulating forever; the
     * caller runs the promotion check *before* decay, so an eligible provisional is promoted rather
     * than demoted, and [MIN_DECAY_AGE_DAYS] (> the 7-day promotion age) protects one awaiting promotion.
     */
    fun decayAction(state: NodeState, now: Long): DecayAction {
        if (state.pinned) return DecayAction.KEEP
        if (state.type == MemNodeType.ENTITY) return DecayAction.KEEP
        return when (state.status) {
            MemStatus.ACTIVE, MemStatus.PROVISIONAL -> {
                val idleDays = (now - state.lastAccessedAt).coerceAtLeast(0) / DAY_MS
                if (idleDays >= MIN_DECAY_AGE_DAYS && retentionScore(state, now) < DORMANT_THRESHOLD) {
                    DecayAction.DEMOTE_DORMANT
                } else DecayAction.KEEP
            }

            MemStatus.DORMANT ->
                if (retentionScore(state, now) < FORGET_THRESHOLD) DecayAction.FORGET else DecayAction.KEEP

            MemStatus.FORGOTTEN ->
                if (now - state.lastAccessedAt > FORGET_GRACE_MS) DecayAction.DELETE else DecayAction.KEEP

            else -> DecayAction.KEEP
        }
    }

    /**
     * Whether a PROVISIONAL node should auto-promote to ACTIVE (§5.4 stage 1): importance ≥ 3, at
     * least 7 days old, and *no* CONTRADICTS edge, supersession, or pending adjudication flag — so a
     * fact stated plainly once becomes a real memory without re-mention, while an unadjudicated pair
     * waits for stage 3 rather than promoting under a starved budget.
     */
    fun canAutoPromote(
        state: NodeState,
        hasContradictsEdge: Boolean,
        adjudicationPending: Boolean,
        now: Long,
    ): Boolean {
        if (state.status != MemStatus.PROVISIONAL) return false
        if (state.type == MemNodeType.ENTITY) return false
        if (state.importance < PROMOTION_MIN_IMPORTANCE) return false
        if (hasContradictsEdge || adjudicationPending) return false
        val ageDays = (now - state.recordedAt).coerceAtLeast(0) / DAY_MS
        return ageDays >= PROMOTION_MIN_AGE_DAYS
    }

    /**
     * Validity expiry (§5.4 stage 2, no model): a live FACT whose `valid_until`, or whose proposed
     * validity horizon (`recordedAt + horizon`), is past due → CLOSED. Returns the effective end
     * instant to stamp, or null if nothing is due.
     */
    fun expiryDueAt(
        status: Int,
        type: Int,
        validUntil: Long?,
        recordedAt: Long,
        validityHorizonMillis: Long?,
        now: Long,
    ): Long? {
        if (type != MemNodeType.FACT) return null
        if (status == MemStatus.CLOSED || status == MemStatus.SUPERSEDED || status == MemStatus.FORGOTTEN) return null
        val due = when {
            validUntil != null -> validUntil
            validityHorizonMillis != null -> recordedAt + validityHorizonMillis
            else -> return null
        }
        return if (due <= now) due else null
    }

    /**
     * A user-authored belief is never merged-away or auto-superseded by the sleep pass (§5.4 stages
     * 3–4); conflicts against it stay as CONTRADICTS edges for the user to settle.
     */
    fun isManualProtected(source: Int): Boolean =
        source == MemSource.MANUAL || source == MemSource.CONFIRMED_BY_USER

    // ---- deterministic MERGE semantics (§5.4 stage 3) ----

    /** The subset of fields a MERGE combines deterministically (content/scope/entities are decided elsewhere). */
    data class MergeInput(
        val pinned: Boolean,
        val timesReinforced: Int,
        val importance: Int,
        val confidence: Float,
        val recordedAt: Long,
        val lastConfirmedAt: Long,
    )

    /**
     * Combine two sources into the MERGED node's numeric fields: `pinned` = either, `reinforced` =
     * sum, `importance/confidence` = max, `recordedAt` = earliest, `lastConfirmedAt` = latest. (The
     * provenance union and the SUPERSEDED-into-merge edges are handled by the pass.)
     */
    fun mergeFields(a: MergeInput, b: MergeInput): MergeInput = MergeInput(
        pinned = a.pinned || b.pinned,
        timesReinforced = a.timesReinforced + b.timesReinforced,
        importance = maxOf(a.importance, b.importance),
        confidence = maxOf(a.confidence, b.confidence),
        recordedAt = minOf(a.recordedAt, b.recordedAt),
        lastConfirmedAt = maxOf(a.lastConfirmedAt, b.lastConfirmedAt),
    )

    // ---- size-enforcement eviction order (§9) ----

    data class RetentionRef(
        val id: String,
        val importance: Int,
        val timesReinforced: Int,
        val timesRetrieved: Int,
        val lastAccessedAt: Long,
    )

    /**
     * Order DORMANT candidates for eviction when a scope is over its node budget: lowest retention
     * first (ties broken by id for determinism). The pass evicts from the front until back under
     * budget.
     */
    fun evictionOrder(nodes: List<RetentionRef>, now: Long): List<String> =
        nodes
            .sortedWith(compareBy({ retentionScore(it.importance, it.timesReinforced, it.timesRetrieved, it.lastAccessedAt, now) }, { it.id }))
            .map { it.id }
}
