package me.rerere.common.android

private const val MAX_RECENT_LOGS = 100

object Logging {
    private val lock = Any()
    private val recentLogs = ArrayDeque<String>(MAX_RECENT_LOGS)

    fun log(tag: String, message: String) = synchronized(lock) {
        if (recentLogs.size >= MAX_RECENT_LOGS) {
            recentLogs.removeLast()
        }
        recentLogs.addFirst("$tag: $message")
    }

    fun getRecentLogs(): List<String> = synchronized(lock) {
        recentLogs.toList()
    }
}
