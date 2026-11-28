package me.rerere.rikkahub.data.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Coordinates in-flight user approval for risky browser clicks and agent secret writes.
 * Surfaced inside `MinimalChatInput` using LastChat's `ToolApprovalActionRow` style.
 */
object BrowserRiskGate {
    const val DEFAULT_TIMEOUT_MS = 180_000L

    enum class Kind {
        RISKY_CLICK,
        SAVE_SECRET,
    }

    data class PendingApproval(
        val id: String = UUID.randomUUID().toString(),
        val conversationId: String,
        val kind: Kind,
        val title: String,
        val summary: String,
        val detail: String? = null,
    )

    private val _pending = MutableStateFlow<PendingApproval?>(null)
    val pending: StateFlow<PendingApproval?> = _pending.asStateFlow()

    private val mutex = Mutex()
    private val waiters = HashMap<String, CompletableDeferred<Boolean>>()

    suspend fun requestApproval(
        conversationId: String,
        kind: Kind,
        title: String,
        summary: String,
        detail: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Boolean {
        val req = PendingApproval(
            conversationId = conversationId,
            kind = kind,
            title = title,
            summary = summary,
            detail = detail,
        )
        val deferred = CompletableDeferred<Boolean>()
        mutex.withLock {
            waiters[req.id] = deferred
            if (_pending.value == null) {
                _pending.value = req
            }
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        } finally {
            mutex.withLock {
                waiters.remove(req.id)
                if (_pending.value?.id == req.id) {
                    _pending.value = null
                }
            }
        }
    }

    fun respond(id: String, approved: Boolean) {
        val d = waiters[id]
        if (_pending.value?.id == id) {
            _pending.value = null
        }
        d?.complete(approved)
    }

    fun cancelForConversation(conversationId: String) {
        val current = _pending.value
        if (current != null && current.conversationId == conversationId) {
            respond(current.id, false)
        }
    }
}
