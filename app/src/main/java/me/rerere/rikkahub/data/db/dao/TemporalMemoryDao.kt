package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryClaimEntity
import me.rerere.rikkahub.data.db.entity.MemoryClaimFtsEntity
import me.rerere.rikkahub.data.db.entity.MemoryEpisodeV3Entity
import me.rerere.rikkahub.data.db.entity.MemoryEpisodeV3FtsEntity
import me.rerere.rikkahub.data.db.entity.MemoryIngestStateEntity
import me.rerere.rikkahub.data.db.entity.MemoryProjectionEntity
import me.rerere.rikkahub.data.db.entity.MemorySourceV3Entity
import me.rerere.rikkahub.data.db.entity.MemorySourceV3FtsEntity

@Dao
interface TemporalMemoryDao {
    @Insert
    suspend fun insertClaim(claim: MemoryClaimEntity): Long

    @Update
    suspend fun updateClaim(claim: MemoryClaimEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaimFts(row: MemoryClaimFtsEntity)

    @Query("DELETE FROM memory_claim_fts WHERE rowid = :id")
    suspend fun deleteClaimFts(id: Long)

    @Query("SELECT * FROM memory_claim WHERE id = :id")
    suspend fun getClaim(id: Long): MemoryClaimEntity?

    @Query("SELECT * FROM memory_claim WHERE legacy_memory_id = :legacyId LIMIT 1")
    suspend fun getClaimByLegacyId(legacyId: Int): MemoryClaimEntity?

    @Query("SELECT * FROM memory_claim WHERE assistant_id = :assistantId AND legacy_memory_id IS NOT NULL")
    suspend fun getLegacyClaims(assistantId: String): List<MemoryClaimEntity>

    @Query("DELETE FROM memory_claim WHERE id = :id")
    suspend fun deleteClaim(id: Long)

    @Query("SELECT * FROM memory_claim WHERE assistant_id = :assistantId AND status IN (0, 1) ORDER BY importance DESC, last_confirmed_at DESC LIMIT :limit")
    suspend fun getCurrentClaims(assistantId: String, limit: Int): List<MemoryClaimEntity>

    @Query("SELECT * FROM memory_claim WHERE assistant_id = :assistantId ORDER BY recorded_at")
    suspend fun getAllClaims(assistantId: String): List<MemoryClaimEntity>

    @Query("SELECT * FROM memory_claim WHERE assistant_id = :assistantId AND legacy_memory_id IS NULL AND status IN (0, 1, 3) ORDER BY COALESCE(valid_from, observed_at) DESC")
    fun observeBrowsableClaims(assistantId: String): Flow<List<MemoryClaimEntity>>

    @Query("SELECT * FROM memory_claim WHERE assistant_id = :assistantId AND subject = :subject AND predicate = :predicate AND status IN (0, 1) ORDER BY last_confirmed_at DESC")
    suspend fun findCurrentClaims(assistantId: String, subject: String, predicate: String): List<MemoryClaimEntity>

    @Query("SELECT memory_claim.* FROM memory_claim JOIN memory_claim_fts ON memory_claim.id = memory_claim_fts.rowid WHERE memory_claim.assistant_id = :assistantId AND memory_claim.status IN (0, 1, 3) AND memory_claim_fts MATCH :query ORDER BY memory_claim.importance DESC, memory_claim.last_confirmed_at DESC LIMIT :limit")
    suspend fun searchClaimsFts(assistantId: String, query: String, limit: Int): List<MemoryClaimEntity>

    @Query("SELECT * FROM memory_claim WHERE assistant_id = :assistantId AND status IN (0, 1, 3) AND ((valid_from IS NULL OR valid_from <= :endAt) AND (valid_until IS NULL OR valid_until >= :startAt)) ORDER BY importance DESC, COALESCE(valid_from, observed_at) DESC LIMIT :limit")
    suspend fun searchClaimsByTime(assistantId: String, startAt: Long, endAt: Long, limit: Int): List<MemoryClaimEntity>

    @Query("UPDATE memory_claim SET last_accessed_at = :now, times_retrieved = times_retrieved + 1 WHERE id IN (:ids)")
    suspend fun markClaimsRetrieved(ids: List<Long>, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: MemorySourceV3Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceFts(row: MemorySourceV3FtsEntity)

    @Query("SELECT * FROM memory_source_v3 WHERE message_id = :messageId LIMIT 1")
    suspend fun getSourceByMessageId(messageId: String): MemorySourceV3Entity?

    @Query("SELECT memory_source_v3.* FROM memory_source_v3 JOIN memory_source_v3_fts ON memory_source_v3.id = memory_source_v3_fts.rowid WHERE memory_source_v3.assistant_id = :assistantId AND memory_source_v3.selected_branch = 1 AND memory_source_v3_fts MATCH :query ORDER BY memory_source_v3.observed_at DESC LIMIT :limit")
    suspend fun searchSourcesFts(assistantId: String, query: String, limit: Int): List<MemorySourceV3Entity>

    @Query("UPDATE memory_source_v3 SET selected_branch = 0 WHERE conversation_id = :conversationId")
    suspend fun demoteConversationSources(conversationId: String)

    @Query("UPDATE memory_source_v3 SET selected_branch = 1 WHERE message_id IN (:messageIds)")
    suspend fun selectSources(messageIds: List<String>)

    @Query("UPDATE memory_claim SET status = 4 WHERE source_conversation_id = :conversationId AND source_message_id IS NOT NULL AND source_message_id NOT IN (:selectedMessageIds) AND status IN (0, 1)")
    suspend fun demoteClaimsOutsideBranch(conversationId: String, selectedMessageIds: List<String>)

    @Query("UPDATE memory_claim SET status = 1 WHERE source_conversation_id = :conversationId AND source_message_id IN (:selectedMessageIds) AND status = 4")
    suspend fun reactivateClaimsInBranch(conversationId: String, selectedMessageIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIngestState(state: MemoryIngestStateEntity)

    @Query("SELECT * FROM memory_ingest_state WHERE conversation_id = :conversationId")
    suspend fun getIngestState(conversationId: String): MemoryIngestStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjection(projection: MemoryProjectionEntity)

    @Query("SELECT * FROM memory_projection WHERE assistant_id = :assistantId")
    suspend fun getProjection(assistantId: String): MemoryProjectionEntity?

    @Query("SELECT * FROM memory_projection WHERE assistant_id = :assistantId")
    fun observeProjection(assistantId: String): Flow<MemoryProjectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisode(episode: MemoryEpisodeV3Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeFts(row: MemoryEpisodeV3FtsEntity)

    @Query("SELECT * FROM memory_episode_v3 WHERE conversation_id = :conversationId AND scene_key = :sceneKey LIMIT 1")
    suspend fun getEpisode(conversationId: String, sceneKey: String): MemoryEpisodeV3Entity?

    @Query("SELECT * FROM memory_episode_v3 WHERE assistant_id = :assistantId AND status IN (0, 1) ORDER BY event_start DESC LIMIT :limit")
    suspend fun getRecentEpisodes(assistantId: String, limit: Int): List<MemoryEpisodeV3Entity>

    @Query("SELECT * FROM memory_episode_v3 WHERE assistant_id = :assistantId ORDER BY event_start")
    suspend fun getAllEpisodes(assistantId: String): List<MemoryEpisodeV3Entity>

    @Query("SELECT * FROM memory_episode_v3 WHERE assistant_id = :assistantId AND status IN (0, 1, 3) ORDER BY event_start DESC")
    fun observeBrowsableEpisodes(assistantId: String): Flow<List<MemoryEpisodeV3Entity>>

    @Query("SELECT memory_episode_v3.* FROM memory_episode_v3 JOIN memory_episode_v3_fts ON memory_episode_v3.id = memory_episode_v3_fts.rowid WHERE memory_episode_v3.assistant_id = :assistantId AND memory_episode_v3.status IN (0, 1, 3) AND memory_episode_v3_fts MATCH :query ORDER BY memory_episode_v3.importance DESC, memory_episode_v3.event_start DESC LIMIT :limit")
    suspend fun searchEpisodesFts(assistantId: String, query: String, limit: Int): List<MemoryEpisodeV3Entity>

    @Query("UPDATE memory_episode_v3 SET last_accessed_at = :now, times_retrieved = times_retrieved + 1 WHERE id IN (:ids)")
    suspend fun markEpisodesRetrieved(ids: List<Long>, now: Long)

    @Transaction
    suspend fun insertClaimIndexed(claim: MemoryClaimEntity): Long {
        val id = insertClaim(claim)
        upsertClaimFts(
            MemoryClaimFtsEntity(
                rowId = id,
                statement = claim.statement,
                subject = claim.subject,
                predicate = claim.predicate,
                objectValue = claim.objectValue.orEmpty(),
            )
        )
        return id
    }

    @Transaction
    suspend fun updateClaimIndexed(claim: MemoryClaimEntity) {
        updateClaim(claim)
        upsertClaimFts(
            MemoryClaimFtsEntity(
                rowId = claim.id,
                statement = claim.statement,
                subject = claim.subject,
                predicate = claim.predicate,
                objectValue = claim.objectValue.orEmpty(),
            )
        )
    }

    @Transaction
    suspend fun deleteClaimIndexed(id: Long) {
        deleteClaimFts(id)
        deleteClaim(id)
    }

    @Transaction
    suspend fun upsertEpisodeIndexed(episode: MemoryEpisodeV3Entity): Long {
        val id = upsertEpisode(episode)
        val effectiveId = if (episode.id == 0L) id else episode.id
        upsertEpisodeFts(MemoryEpisodeV3FtsEntity(effectiveId, episode.title, episode.summary))
        return effectiveId
    }

    @Transaction
    suspend fun insertSourceIndexed(source: MemorySourceV3Entity): Long {
        val inserted = insertSource(source)
        val effective = if (inserted > 0) inserted else getSourceByMessageId(source.messageId)?.id ?: return inserted
        upsertSourceFts(MemorySourceV3FtsEntity(effective, source.excerpt))
        return effective
    }
}
