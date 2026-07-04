package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Simple key/value bag for memory-store bookkeeping (schema v34): the store's own semantic
 * `store_schema_version`, the import watermark, the last sleep-run timestamp, and per-assistant
 * character-profile hashes. Separate from the Room DDL `version` so semantic migrations that Room
 * can't express (re-scoring, re-classification) have somewhere to record progress.
 */
@Entity(tableName = "memory_store_meta")
data class MemoryStoreMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
)

object MemoryStoreMetaKeys {
    const val STORE_SCHEMA_VERSION = "store_schema_version"
    const val IMPORT_COMPLETED = "import_completed"
    const val IMPORT_WATERMARK = "import_watermark"
    const val LAST_SLEEP_RUN = "last_sleep_run"
    const val PROFILE_HASH_PREFIX = "profile_hash_" // + assistantId
    const val PROFILE_JSON_PREFIX = "profile_json_" // + assistantId → serialized CharacterMemoryProfile

    /**
     * Embedding model id the backfill worker (§12.4) last brought the store into alignment with.
     * When the configured embedding model differs from this, a backfill is (re)enqueued to re-embed
     * mismatched/missing-vector ACTIVE nodes while FTS covers the gap.
     */
    const val EMBED_BACKFILL_MODEL = "embed_backfill_model"

    /** Current semantic schema version of the memory store. Bump for in-store migrations. */
    const val CURRENT_STORE_SCHEMA_VERSION = "1"
}
