package me.rerere.common.platform

object PlatformLog {
    fun d(tag: String, message: String) = log("D", tag, message)

    fun i(tag: String, message: String) = log("I", tag, message)

    fun w(tag: String, message: String) = log("W", tag, message)

    fun e(tag: String, message: String) = log("E", tag, message)

    private fun log(level: String, tag: String, message: String) {
        println("$level/$tag: $message")
    }
}
