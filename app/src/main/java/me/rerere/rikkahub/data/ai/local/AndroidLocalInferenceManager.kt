package me.rerere.rikkahub.data.ai.local

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.asr.local.SherpaSttRuntime
import me.rerere.common.inference.LocalInferenceManager
import me.rerere.common.inference.LocalInferenceQueueState
import me.rerere.common.inference.LocalInferenceRequest
import me.rerere.common.inference.LocalInferenceWorkload
import me.rerere.locallm.LiteRtEmbedder
import me.rerere.locallm.LiteRtRuntime

/** Owns admission and residency for every heavyweight local native runtime in the app process. */
class AndroidLocalInferenceManager internal constructor(
    private val evictWorkload: suspend (LocalInferenceWorkload) -> Unit,
    private val idleTimeoutMs: (LocalInferenceWorkload) -> Long = ::defaultIdleTimeoutMs,
) : LocalInferenceManager {
    constructor(
        liteRtRuntime: LiteRtRuntime,
        embedder: LiteRtEmbedder,
        speechRuntime: SherpaSttRuntime,
    ) : this(
        evictWorkload = { workload ->
            when (workload) {
                LocalInferenceWorkload.CHAT -> liteRtRuntime.unload()
                LocalInferenceWorkload.EMBEDDING -> embedder.unload()
                LocalInferenceWorkload.SPEECH -> speechRuntime.unload()
            }
        },
    )

    private val gate = Mutex()
    private val stateMutex = Mutex()
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("LocalInferenceManager")
    )
    private val evictionJobs = mutableMapOf<LocalInferenceWorkload, Job>()
    private var nextRequestId = 0L

    private val _queueState = MutableStateFlow(LocalInferenceQueueState())
    override val queueState: StateFlow<LocalInferenceQueueState> = _queueState.asStateFlow()

    override suspend fun <T> withLease(
        workload: LocalInferenceWorkload,
        modelId: String,
        block: suspend () -> T,
    ): T {
        val request = stateMutex.withLock {
            val item = LocalInferenceRequest(++nextRequestId, workload, modelId)
            _queueState.value = _queueState.value.copy(waiting = _queueState.value.waiting + item)
            item
        }

        var acquired = false
        try {
            gate.lock()
            acquired = true
            stateMutex.withLock {
                evictionJobs.remove(workload)?.cancel()
                val current = _queueState.value
                _queueState.value = current.copy(
                    active = request,
                    waiting = current.waiting.filterNot { it.id == request.id },
                )
            }
            evictOtherWorkloads(workload)
            return block()
        } finally {
            // A cancelled generation must still release the process-wide gate. Without a
            // non-cancellable cleanup, pressing Stop while streaming could strand every future
            // local request in the queue.
            withContext(NonCancellable) {
                stateMutex.withLock {
                    val current = _queueState.value
                    _queueState.value = current.copy(
                        active = if (current.active?.id == request.id) null else current.active,
                        waiting = current.waiting.filterNot { it.id == request.id },
                    )
                }
                if (acquired) {
                    gate.unlock()
                    scheduleIdleEviction(workload)
                }
            }
        }
    }

    override fun requestEviction(workload: LocalInferenceWorkload?) {
        scope.launch {
            gate.withLock {
                if (workload == null) evictAll() else evict(workload)
            }
        }
    }

    private suspend fun evictOtherWorkloads(active: LocalInferenceWorkload) {
        LocalInferenceWorkload.entries
            .filterNot { it == active }
            .forEach { evict(it) }
    }

    private suspend fun evictAll() {
        LocalInferenceWorkload.entries.forEach { evict(it) }
    }

    private suspend fun evict(workload: LocalInferenceWorkload) {
        evictWorkload(workload)
    }

    private suspend fun scheduleIdleEviction(workload: LocalInferenceWorkload) {
        val delayMs = idleTimeoutMs(workload)
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            if (delayMs > 0) delay(delayMs)
            gate.withLock { evict(workload) }
            stateMutex.withLock {
                if (evictionJobs[workload] === job) evictionJobs.remove(workload)
            }
        }
        stateMutex.withLock {
            evictionJobs.remove(workload)?.cancel()
            evictionJobs[workload] = job
        }
        job.start()
    }

    companion object {
        private const val CHAT_IDLE_TIMEOUT_MS = 60_000L
        private const val EMBEDDING_IDLE_TIMEOUT_MS = 5_000L

        private fun defaultIdleTimeoutMs(workload: LocalInferenceWorkload): Long = when (workload) {
            LocalInferenceWorkload.CHAT -> CHAT_IDLE_TIMEOUT_MS
            LocalInferenceWorkload.EMBEDDING -> EMBEDDING_IDLE_TIMEOUT_MS
            LocalInferenceWorkload.SPEECH -> 0L
        }
    }
}
