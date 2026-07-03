package me.rerere.rikkahub.data.memory

/**
 * The write-gate that makes the graph the deduplication mechanism (§5.3).
 *
 * Core rule: **similarity is not identity**. A near-duplicate is disproportionately often an update
 * or contradiction, so high similarity must never silently collapse into reinforcement of the old
 * belief — that would reintroduce destructive overwrites inside the very mechanism meant to prevent
 * duplicates. Therefore only a *restatement* (no new information) becomes REINFORCE; anything
 * similar-but-different is inserted and flagged for sleep-pass adjudication.
 *
 * This class is pure: the applier supplies the already-collected 1-hop neighborhood (same type +
 * scope), and the decision is fully reproducible and model-independent.
 */
object MemoryDedupGate {

    /** Minimum content-token overlap for two nodes to count as "similar but different". */
    const val SIMILAR_OVERLAP = 0.5f

    data class NeighborView(val id: String, val content: String)

    sealed interface Decision {
        /** Candidate is a pure restatement of [targetId]; route to REINFORCE. */
        data class Reinforce(val targetId: String) : Decision

        /** Candidate shares a subject and overlaps [relatedId] but carries new/changed content;
         *  insert as a new node + RELATES_TO edge + adjudication flag. Never absorbed at write time. */
        data class Flag(val relatedId: String, val overlap: Float) : Decision

        /** Genuinely novel; insert plainly. */
        data object Insert : Decision
    }

    /**
     * @param content the candidate node's content
     * @param neighbors 1-hop non-entity neighbors of the same type + scope (from ABOUT edges)
     */
    fun decide(content: String, neighbors: List<NeighborView>): Decision {
        if (neighbors.isEmpty()) return Decision.Insert

        // 1. REINFORCE only on a restatement: normalized exact match, or candidate tokens ⊆ existing.
        neighbors.firstOrNull { MemoryText.normalizedEquals(content, it.content) }
            ?.let { return Decision.Reinforce(it.id) }
        neighbors.firstOrNull { MemoryText.isRestatement(content, it.content) }
            ?.let { return Decision.Reinforce(it.id) }

        // 2. Similar but different → flag the highest-overlap neighbor for sleep-pass adjudication.
        val best = neighbors
            .map { it to MemoryText.overlap(content, it.content) }
            .filter { it.second >= SIMILAR_OVERLAP }
            .maxByOrNull { it.second }
        if (best != null) return Decision.Flag(best.first.id, best.second)

        // 3. Novel.
        return Decision.Insert
    }
}
