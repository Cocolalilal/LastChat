package me.rerere.common.android

import me.rerere.common.log.PortableDebugLog

object Logging {
    fun log(tag: String, message: String) = PortableDebugLog.log(tag, message)

    fun getRecentLogs(): List<String> = PortableDebugLog.getRecentLogs()
}
