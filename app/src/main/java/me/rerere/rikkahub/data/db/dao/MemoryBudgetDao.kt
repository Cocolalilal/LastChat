package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryBudgetLedgerEntity

/** DAO for `memory_budget_ledger` — daily background-call metering (Memory v2, plan §4.11, §12). */
@Dao
interface MemoryBudgetDao {
    @Upsert
    suspend fun upsert(row: MemoryBudgetLedgerEntity)

    @Query("SELECT * FROM memory_budget_ledger WHERE day = :day AND category = :category")
    suspend fun get(day: String, category: String): MemoryBudgetLedgerEntity?

    @Query("SELECT * FROM memory_budget_ledger WHERE day = :day")
    suspend fun getForDay(day: String): List<MemoryBudgetLedgerEntity>

    @Query("SELECT COALESCE(SUM(calls), 0) FROM memory_budget_ledger WHERE day = :day AND category = :category")
    suspend fun callsUsed(day: String, category: String): Int

    @Query("DELETE FROM memory_budget_ledger WHERE day < :beforeDay")
    suspend fun pruneBefore(beforeDay: String)
}
