package me.rerere.ai.util

/**
 * Lightweight logger intentionally kept free of Android dependencies so core provider logic
 * can be moved to shared/KMP source sets with minimal churn.
 */
object AppLogger {
    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val prefix = "[$level][$tag]"
        println("$prefix $message")
        throwable?.printStackTrace()
    }

    fun d(tag: String, message: String) = log("DEBUG", tag, message)

    fun i(tag: String, message: String) = log("INFO", tag, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log("WARN", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log("ERROR", tag, message, throwable)
}
