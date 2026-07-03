package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Per-day, per-category ledger of background model calls (schema v34).
 *
 * `MemoryBudget` admission control reads and increments this before every background model call so
 * the memory system has a hard, explainable cost ceiling. The deterministic paths (retrieval,
 * decay, expiry, size enforcement) never touch it — they always run.
 *
 * `day` is a UTC day key (epochMillis / 86_400_000) so a row uniquely identifies (day, category).
 */
@Entity(
    tableName = "memory_budget_ledger",
    primaryKeys = ["day", "category"],
    indices = [Index(value = ["day"])]
)
data class MemoryBudgetLedgerEntity(
    @ColumnInfo(name = "day")
    val day: Long,
    @ColumnInfo(name = "category")
    val category: String, // MemBudgetCategory
    @ColumnInfo(name = "calls")
    val calls: Int = 0,
    @ColumnInfo(name = "tokens_in")
    val tokensIn: Long = 0,
    @ColumnInfo(name = "tokens_out")
    val tokensOut: Long = 0,
)
