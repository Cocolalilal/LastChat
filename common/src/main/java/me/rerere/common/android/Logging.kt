package me.rerere.common.android

private const val MAX_RECENT_LOGS = 100

object Logging {
    private val recentLogs = arrayListOf<String>()

    fun log(tag: String, message: String) {
        recentLogs.add(0, "$tag: $message")
        if (recentLogs.size > MAX_RECENT_LOGS) {
            recentLogs.removeLastOrNull()
        }
    }

    fun getRecentLogs(): List<String> {
        return recentLogs
    }
}
