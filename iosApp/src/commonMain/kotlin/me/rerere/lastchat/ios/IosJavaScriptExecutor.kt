package me.rerere.lastchat.ios

/**
 * Isolates the JavaScript runtime from shared UI and orchestration code.
 *
 * Android keeps using QuickJS. iOS uses JavaScriptCore, which is an app-local
 * JavaScript VM and does not expose browser, filesystem, or network globals.
 */
fun interface IosJavaScriptExecutor {
    fun execute(code: String): String
}
