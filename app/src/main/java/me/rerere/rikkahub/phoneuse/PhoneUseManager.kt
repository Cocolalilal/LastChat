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
    val assistantUiVisible = MutableStateFlow(false)
    val controlsReady = MutableStateFlow<Uuid?>(null)
    private val stateLock = Any()
    private val assistantConversations = mutableSetOf<Uuid>()
    private val gate = Mutex()
    private var cancelTask: (() -> Unit)? = null

    fun attach(controller: PhoneController) { _controller.value = controller }
    fun detach(controller: PhoneController) {
        if (_controller.compareAndSet(controller, null)) stop()
    }
    fun setActing(acting: Boolean) { _acting.value = acting }
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
                _session.value = PhoneUseTask(id, assistantId, id in assistantConversations, action)
                true
            } else {
                _session.value = current.copy(action = action)
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
            // ProcessLifecycleOwner delays ON_STOP; let it catch up before checking self-operation.
            delay(800)
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
    fun stop() {
        val cancel = synchronized(stateLock) {
            val callback = cancelTask
            cancelTask = null
            _session.value = null
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
)
