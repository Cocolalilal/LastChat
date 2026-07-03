package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryBudgetLedgerEntity

@Dao
interface MemoryBudgetDao {

    @Query("SELECT * FROM memory_budget_ledger WHERE day = :day AND category = :category LIMIT 1")
    suspend fun get(day: Long, category: String): MemoryBudgetLedgerEntity?

    @Query("SELECT COALESCE(SUM(calls), 0) FROM memory_budget_ledger WHERE day = :day AND category = :category")
    suspend fun callsToday(day: Long, category: String): Int

    @Query("SELECT * FROM memory_budget_ledger WHERE day = :day")
    suspend fun getDay(day: Long): List<MemoryBudgetLedgerEntity>

    /**
     * Atomically record one metered call. UPSERT via INSERT ... ON CONFLICT so concurrent workers
     * can't lose an increment.
     */
    @Query(
        """
        INSERT INTO memory_budget_ledger(day, category, calls, tokens_in, tokens_out)
        VALUES(:day, :category, 1, :tokensIn, :tokensOut)
        ON CONFLICT(day, category) DO UPDATE SET
            calls = calls + 1,
            tokens_in = tokens_in + :tokensIn,
            tokens_out = tokens_out + :tokensOut
        """
    )
    suspend fun record(day: Long, category: String, tokensIn: Long, tokensOut: Long)

    @Query("DELETE FROM memory_budget_ledger WHERE day < :beforeDay")
    suspend fun pruneOlderThan(beforeDay: Long)
}
