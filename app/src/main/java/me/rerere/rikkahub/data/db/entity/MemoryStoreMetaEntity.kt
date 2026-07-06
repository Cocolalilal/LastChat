package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `memory_store_meta` — free-form key/value store metadata (Memory v2, plan §4.11).
 *
 * Holds the [MemStoreMetaKeys.STORE_SCHEMA_VERSION] (for future *semantic* migrations independent
 * of the Room schema version), import watermarks, last sleep run, per-assistant profile hashes, and
 * the embedding backfill watermark.
 */
@Entity(tableName = "memory_store_meta")
data class MemoryStoreMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value", defaultValue = "")
    val value: String = "",
)
