package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `memory_budget_ledger` — daily metering of background model calls (Memory v2, plan §4.11, §12).
 *
 * Every background model call is metered against one [MemBudgetCategory]. Autonomous categories
 * (SLEEP/PROFILE/CURIOSITY) are hard-capped and defer when exhausted; EXTRACTION has only a safety
 * ceiling. Keyed by `(day, category)`.
 */
@Entity(
    tableName = "memory_budget_ledger",
    primaryKeys = ["day", "category"],
)
data class MemoryBudgetLedgerEntity(
    @ColumnInfo(name = "day")
    val day: String, // e.g. yyyy-MM-dd
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "calls", defaultValue = "0")
    val calls: Int = 0,
    @ColumnInfo(name = "tokens_in", defaultValue = "0")
    val tokensIn: Long = 0,
    @ColumnInfo(name = "tokens_out", defaultValue = "0")
    val tokensOut: Long = 0,
)
