package me.rerere.common.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bounded in-memory ring used by Android [me.rerere.common.android.Logging]
 * and the portable AI request logger. Newest-first callers use [addNewestFirst];
 * generation logs append oldest-first via [add].
 */
class PortableLogRing<T>(private val maxEntries: Int) {
    private val logs = MutableStateFlow<List<T>>(emptyList())

    fun observe(): StateFlow<List<T>> = logs.asStateFlow()

    fun snapshot(): List<T> = logs.value

    fun add(entry: T) {
        val next = logs.value + entry
        logs.value = if (next.size > maxEntries) next.takeLast(maxEntries) else next
    }

    fun addNewestFirst(entry: T) {
        logs.value = listOf(entry) + logs.value.take(maxEntries - 1)
    }

    fun clear() {
        logs.value = emptyList()
    }
}

object PortableDebugLog {
    const val MAX_RECENT_LOGS = 100
    private val ring = PortableLogRing<String>(MAX_RECENT_LOGS)

    fun log(tag: String, message: String) {
        ring.addNewestFirst("$tag: $message")
    }

    fun getRecentLogs(): List<String> = ring.snapshot()

    fun clear() {
        ring.clear()
    }
}
