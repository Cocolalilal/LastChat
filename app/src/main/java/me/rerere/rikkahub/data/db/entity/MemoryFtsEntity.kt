package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * `memory_fts` — the primary retrieval index (Memory v2, plan §4.9, §19.6).
 *
 * A standalone FTS4 table, **manually synced by the applier** (no Room triggers, no external
 * content). One unified table so a single MATCH query covers all recall paths: `text` holds a fact
 * statement, an episode title+summary, or an entity name+aliases, tagged by `rowKind`
 * ([MemFtsRowKind]) and pointing back via `rowRefId`.
 *
 * Tokenizer is `unicode61` with `remove_diacritics=2` for diacritics-folding across the user's
 * EN/IT/RO usage; CJK degrades to alias exact-match + vectors (documented, not silent — §19.6).
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
)
@Entity(tableName = "memory_fts")
data class MemoryFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int? = null,
    @ColumnInfo(name = "row_kind")
    val rowKind: Int,
    @ColumnInfo(name = "row_ref_id")
    val rowRefId: String,
    @ColumnInfo(name = "text")
    val text: String,
)
