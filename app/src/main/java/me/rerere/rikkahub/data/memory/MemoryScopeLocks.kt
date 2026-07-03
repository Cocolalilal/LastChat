package me.rerere.rikkahub.data.memory

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * App-level single-writer-per-scope serialization for memory writes (§7.2).
 *
 * `withTransaction` gives DB-level atomicity but not logical serialization: two coroutines can each
 * read the graph, decide independently, then apply in separate transactions and clobber each other's
 * read-modify-write. `enqueueUniqueWork` only serializes extraction-vs-extraction per conversation.
 * This mutex closes the gap so a future sleep pass (P3) cannot interleave its merge/decide/apply with
 * an in-flight extraction on the same scope.
 *
 * Scope key is the writing owner (assistant id for CHARACTER writes, a fixed key for the global
 * layer). Mutexes are NOT reentrant — never call [withScope] from within a section that already
 * holds the same scope's lock.
 */
class MemoryScopeLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forScope(scopeKey: String): Mutex = locks.getOrPut(scopeKey) { Mutex() }

    suspend fun <T> withScope(scopeKey: String, block: suspend () -> T): T =
        forScope(scopeKey).withLock { block() }

    companion object {
        const val GLOBAL_SCOPE = "__global_user__"
    }
}
