package me.rerere.ai.generation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock

enum class PortableBackgroundTask {
    SPONTANEOUS_MESSAGES,
    SCHEDULED_MESSAGES,
    MEMORY_CONSOLIDATION,
}

data class PortableTaskRequest(
    val task: PortableBackgroundTask,
    val uniqueName: String = task.name.lowercase(),
    val delayMs: Long = 0L,
    val periodic: Boolean = false,
    val intervalMs: Long? = null,
    val extras: Map<String, String> = emptyMap(),
)

fun interface PortableTaskHandler {
    suspend fun execute(request: PortableTaskRequest): Boolean
}

interface PortableBackgroundWake {
    fun schedule(task: PortableBackgroundTask, earliestEpochMs: Long)
    fun cancel(task: PortableBackgroundTask)
}

object NoOpPortableBackgroundWake : PortableBackgroundWake {
    override fun schedule(task: PortableBackgroundTask, earliestEpochMs: Long) {}
    override fun cancel(task: PortableBackgroundTask) {}
}

interface PortableTaskScheduler {
    fun register(task: PortableBackgroundTask, handler: PortableTaskHandler)
    fun enqueue(request: PortableTaskRequest)
    fun cancel(uniqueName: String)
    suspend fun run(task: PortableBackgroundTask, request: PortableTaskRequest = PortableTaskRequest(task)): Boolean
}

/**
 * Foreground coroutine scheduler used by iOS and tests. [wake] maps to
 * BGTaskScheduler; the in-process timer is the fallback when the OS declines.
 */
@OptIn(ExperimentalAtomicApi::class)
class InProcessPortableTaskScheduler(
    private val scope: CoroutineScope,
    private val wake: PortableBackgroundWake = NoOpPortableBackgroundWake,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : PortableTaskScheduler {
    private val handlers = AtomicReference<Map<PortableBackgroundTask, PortableTaskHandler>>(emptyMap())
    private val jobs = AtomicReference<Map<String, Job>>(emptyMap())

    override fun register(task: PortableBackgroundTask, handler: PortableTaskHandler) {
        while (true) {
            val current = handlers.load()
            if (handlers.compareAndSet(current, current + (task to handler))) return
        }
    }

    override fun enqueue(request: PortableTaskRequest) {
        val job = scope.launch {
            val delayMs = request.delayMs.coerceAtLeast(0L)
            if (delayMs > 0L) {
                wake.schedule(request.task, nowEpochMs() + delayMs)
                delay(delayMs)
            } else if (request.periodic) {
                wake.schedule(request.task, nowEpochMs() + (request.intervalMs ?: 0L))
            }
            val succeeded = runCatching { run(request.task, request) }.getOrDefault(false)
            if (request.periodic) {
                val interval = (request.intervalMs ?: PortableSpontaneousMessaging.WORK_INTERVAL_MS)
                    .coerceAtLeast(1_000L)
                enqueue(
                    request.copy(
                        delayMs = if (succeeded) interval else interval / 2,
                        extras = request.extras,
                    ),
                )
            } else if (succeeded) {
                wake.cancel(request.task)
            }
        }
        replaceJob(request.uniqueName, job)
    }

    override fun cancel(uniqueName: String) {
        replaceJob(uniqueName, null)
    }

    override suspend fun run(
        task: PortableBackgroundTask,
        request: PortableTaskRequest,
    ): Boolean {
        val handler = handlers.load()[task] ?: return false
        return handler.execute(request)
    }

    private fun replaceJob(uniqueName: String, next: Job?) {
        while (true) {
            val current = jobs.load()
            val previous = current[uniqueName]
            val updated = if (next == null) current - uniqueName else current + (uniqueName to next)
            if (jobs.compareAndSet(current, updated)) {
                if (previous !== next) previous?.cancel()
                return
            }
        }
    }
}
