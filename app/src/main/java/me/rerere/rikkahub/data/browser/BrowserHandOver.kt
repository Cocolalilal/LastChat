package me.rerere.rikkahub.data.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Human-in-the-loop browser hand-over coordinator.
 * Suspends the `browser_use(action="hand_over")` tool call until the user taps Done
 * in `BrowserHandOverCard` / `BrowserHandOverBanner` or the configured timeout expires.
 */
object BrowserHandOver {
    data class Pending(
        val conversationId: String,
        val reason: String,
        val url: String,
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    @Volatile
    private var waiter: CompletableDeferred<Boolean>? = null

    fun finish() {
        waiter?.complete(true)
    }

    fun cancel() {
        waiter?.complete(false)
    }

    suspend fun wait(
        conversationId: String,
        reason: String,
        url: String,
        timeoutMinutes: Int = 15,
    ): Boolean {
        val mine = CompletableDeferred<Boolean>()
        waiter?.complete(false)
        waiter = mine
        _pending.value = Pending(conversationId = conversationId, reason = reason, url = url)
        val timeoutMs = timeoutMinutes.coerceIn(1, 60) * 60_000L
        return try {
            withTimeoutOrNull(timeoutMs) { mine.await() } ?: false
        } finally {
            if (waiter === mine) {
                waiter = null
                _pending.value = null
            }
        }
    }
}
