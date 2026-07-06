package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryFactEntity
import me.rerere.rikkahub.data.db.entity.MemoryFactLinkEntity

/**
 * DAO for `memory_fact` edges and their `memory_fact_link` belief chains (Memory v2, plan §4.3–4.4).
 *
 * The structural dedup gate (§6.4) keys off `(subject_id, predicate)`; [findBySubjectPredicate]
 * is the indexed lookup that seeds it.
 */
@Dao
interface MemoryFactDao {
    // --- facts ---

    @Upsert
    suspend fun upsertFact(fact: MemoryFactEntity)

    @Upsert
    suspend fun upsertFacts(facts: List<MemoryFactEntity>)

    @Query("SELECT * FROM memory_fact WHERE id = :id")
    suspend fun getFact(id: String): MemoryFactEntity?

    @Query("SELECT * FROM memory_fact WHERE id IN (:ids)")
    suspend fun getFacts(ids: List<String>): List<MemoryFactEntity>

    /** Candidate set for the structural dedup gate (§6.4). */
    @Query(
        "SELECT * FROM memory_fact WHERE subject_id = :subjectId AND predicate = :predicate " +
            "AND status != 5"
    )
    suspend fun findBySubjectPredicate(subjectId: String, predicate: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_fact WHERE subject_id = :entityId OR object_id = :entityId")
    suspend fun findByEndpoint(entityId: String): List<MemoryFactEntity>

    @Query(
        "SELECT * FROM memory_fact " +
            "WHERE status = :status " +
            "AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))"
    )
    suspend fun findByStatusForAssistant(status: Int, assistantId: String): List<MemoryFactEntity>

    @Query("UPDATE memory_fact SET status = :status WHERE id = :id")
    suspend fun setFactStatus(id: String, status: Int)

    @Delete
    suspend fun deleteFact(fact: MemoryFactEntity)

    @Query("DELETE FROM memory_fact WHERE id = :id")
    suspend fun deleteFactById(id: String)

    @Query("DELETE FROM memory_fact WHERE scope = 1 AND owner_assistant_id = :assistantId")
    suspend fun deleteFactsOfAssistant(assistantId: String)

    // --- fact links ---

    @Upsert
    suspend fun upsertLink(link: MemoryFactLinkEntity)

    @Query("SELECT * FROM memory_fact_link WHERE fact_id = :factId OR other_fact_id = :factId")
    suspend fun getLinksFor(factId: String): List<MemoryFactLinkEntity>

    @Query("SELECT * FROM memory_fact_link WHERE fact_id = :factId AND type = :type")
    suspend fun getLinksOfType(factId: String, type: Int): List<MemoryFactLinkEntity>

    @Query("DELETE FROM memory_fact_link WHERE fact_id = :factId OR other_fact_id = :factId")
    suspend fun deleteLinksFor(factId: String)
}
