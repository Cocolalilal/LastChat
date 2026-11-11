package me.rerere.rikkahub.phoneuse

import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.uuid.Uuid

/** Connected only after the user enables LastChat in Android accessibility settings. */
class PhoneUseManager {
    companion object {
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    private val _controller = MutableStateFlow<PhoneController?>(null)
    val controller: StateFlow<PhoneController?> = _controller.asStateFlow()
    private val _acting = MutableStateFlow(false)
    val acting: StateFlow<Boolean> = _acting.asStateFlow()
    private val _session = MutableStateFlow<PhoneUseTask?>(null)
    val session: StateFlow<PhoneUseTask?> = _session.asStateFlow()
    private val _capturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()
    val appUiVisible = MutableStateFlow(false)
    val assistantUiVisible = MutableStateFlow(false)
    val otherAppUiVisible = MutableStateFlow(false)
    val assistantReturning = MutableStateFlow(false)
    val controlsReady = MutableStateFlow<Uuid?>(null)
    // The shared panel is already in its compact pose when another window takes over.
    val assistantDocked = MutableStateFlow<Uuid?>(null)
    val overlayBlur = MutableStateFlow(false)
    private val stateLock = Any()
    private val assistantConversations = mutableSetOf<Uuid>()
    private val gate = Mutex()
    private var cancelTask: (() -> Unit)? = null
    private var lastSpokenReply: Uuid? = null
    private var draftOwner: Uuid? = null
    private var replyDraft = ""

    fun replyDraft(id: Uuid): String = synchronized(stateLock) {
        if (draftOwner == id) replyDraft else ""
    }

    fun updateReplyDraft(id: Uuid, text: String) = synchronized(stateLock) {
        if (_session.value?.conversationId == id) { draftOwner = id; replyDraft = text }
    }

    fun takeReplyDraft(id: Uuid): String = synchronized(stateLock) {
        if (draftOwner != id) return@synchronized ""
        replyDraft.also { replyDraft = ""; draftOwner = null }
    }

    fun attach(controller: PhoneController) { _controller.value = controller }
    fun detach(controller: PhoneController) {
        if (_controller.compareAndSet(controller, null)) stop()
    }
    fun setActing(acting: Boolean, capturing: Boolean = false) {
        _capturing.value = acting && capturing
        _acting.value = acting
    }

    fun isAssistantConversation(id: Uuid): Boolean = synchronized(stateLock) {
        id in assistantConversations || _session.value?.let { it.conversationId == id && it.systemAssistant } == true
    }

    fun ownsCompletion(id: Uuid): Boolean =
        (isAssistantConversation(id) && assistantUiVisible.value) ||
            (_session.value?.let {
                it.conversationId == id && (it.running || !it.resultPresented) &&
                    (it.systemAssistant || controlsReady.value == id)
            } == true)

    fun claimSpokenReply(id: Uuid): Boolean = synchronized(stateLock) {
        if (lastSpokenReply == id) false else { lastSpokenReply = id; true }
    }

    fun claimAssistantReturn(id: Uuid): Boolean = synchronized(stateLock) {
        val current = _session.value ?: return@synchronized false
        if (current.conversationId != id || current.returnRequested) false else {
            _session.value = current.copy(returnRequested = true, returnFailed = false)
            true
        }
    }

    fun markAssistantPresented(id: Uuid) = synchronized(stateLock) {
        _session.value = _session.value?.let {
            if (it.conversationId == id && !it.running) it.copy(resultPresented = true) else it
        }
    }

    fun markReturnFailed(id: Uuid) = synchronized(stateLock) {
        assistantReturning.value = false
        _session.value = _session.value?.let { if (it.conversationId == id) it.copy(returnFailed = true) else it }
    }

    fun resumeReply(id: Uuid, cancel: () -> Unit) = synchronized(stateLock) {
        val current = _session.value ?: return@synchronized
        if (current.conversationId != id) return@synchronized
        cancelTask = cancel
        _session.value = current.copy(running = true, paused = false, awaitingReply = false,
            action = "reply", error = null, returnRequested = false, returnFailed = false, resultPresented = false)
    }

    fun setError(id: Uuid, message: String?) = synchronized(stateLock) {
        _session.value = _session.value?.let { if (it.conversationId == id && it.running) it.copy(error = message) else it }
    }

    fun generationSettled(id: Uuid, awaitingReply: Boolean, reply: String) {
        synchronized(stateLock) {
            val current = _session.value ?: return
            if (current.conversationId != id || !current.running) return
            _session.value = current.copy(
                running = awaitingReply, awaitingReply = awaitingReply, paused = false,
                reply = reply, returnRequested = false,
            )
            if (!awaitingReply) cancelTask = null
        }
    }
    fun registerAssistantConversation(id: Uuid) { synchronized(stateLock) { assistantConversations.add(id) } }
    fun unregisterAssistantConversation(id: Uuid) { synchronized(stateLock) { assistantConversations.remove(id) } }

    suspend fun <T> runAction(
        id: Uuid,
        assistantId: Uuid,
        action: String,
        cancel: () -> Unit,
        block: suspend () -> T,
    ): T = gate.withLock {
        check(controller.value != null) { "Enable LastChat Phone use in Android accessibility settings first." }
        val starting = synchronized(stateLock) {
            val current = _session.value
            check(current == null || !current.running || current.conversationId == id) {
                "Another conversation is using the phone. Stop that task before starting this one."
            }
            if (current?.conversationId != id || !current.running) {
                cancelTask = cancel
                controlsReady.value = null
                if (draftOwner != id) { replyDraft = ""; draftOwner = null }
                _session.value = PhoneUseTask(id, assistantId, id in assistantConversations, action)
                true
            } else {
                _session.value = current.copy(action = action, awaitingReply = false, returnRequested = false, returnFailed = false, error = null)
                false
            }
        }
        withTimeout(5000) {
            combine(controlsReady, session) { ready, task ->
                ready == id || task?.conversationId != id || !task.running
            }.first { it }
        }
        val wasPaused = _session.value?.paused == true
        session.first { it?.conversationId != id || !it.paused }
        if (_session.value?.systemAssistant == true && (starting || wasPaused || assistantUiVisible.value)) {
            withTimeout(5000) { assistantUiVisible.first { !it } }
            // Wait for the activity-to-dock handoff to finish before acting on the target app.
            delay(40)
        }
        session.first { it?.conversationId != id || !it.paused }
        check(_session.value?.let { it.conversationId == id && it.running } == true) { "Phone use stopped." }
        block()
    }

    fun togglePause() = synchronized(stateLock) {
        _session.value = _session.value?.let { if (it.running) it.copy(paused = !it.paused) else it }
    }
    fun finish(id: Uuid) {
        synchronized(stateLock) {
            val current = _session.value ?: return
            if (current.conversationId != id) return
            _session.value = current.copy(running = false, paused = false)
            cancelTask = null
        }
    }
    /** Cancel the work while retaining the assistant surface and its reply/input ownership. */
    fun cancelAndReturn() {
        val cancel = synchronized(stateLock) {
            val current = _session.value ?: return
            val callback = cancelTask
            cancelTask = null
            if (current.systemAssistant) {
                _session.value = current.copy(running = false, paused = false, awaitingReply = false,
                    returnRequested = false, returnFailed = false, resultPresented = false)
            } else {
                _session.value = null
                replyDraft = ""
                draftOwner = null
                controlsReady.value = null
            }
            callback
        }
        cancel?.invoke()
    }

    fun stop() {
        val cancel = synchronized(stateLock) {
            val callback = cancelTask
            cancelTask = null
            _session.value = null
            replyDraft = ""; draftOwner = null
            assistantDocked.value = null
            assistantReturning.value = false
            controlsReady.value = null
            callback
        }
        cancel?.invoke()
    }
}

data class PhoneUseTask(
    val conversationId: Uuid,
    val assistantId: Uuid,
    val systemAssistant: Boolean,
    val action: String,
    val running: Boolean = true,
    val paused: Boolean = false,
    val awaitingReply: Boolean = false,
    val reply: String = "",
    val error: String? = null,
    val returnRequested: Boolean = false,
    val returnFailed: Boolean = false,
    val resultPresented: Boolean = false,
)
