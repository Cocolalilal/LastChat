package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Caller cancellation only stops waiting; the application owns the underlying operation. */
internal class ContextCompressionJobs<K, V>(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val jobs = mutableMapOf<K, Deferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val job = mutex.withLock {
            jobs[key]?.takeUnless { it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) {
                block()
            }.also { task ->
                jobs[key] = task
                task.invokeOnCompletion {
                    scope.launch { mutex.withLock { if (jobs[key] === task) jobs.remove(key) } }
                }
                task.start()
            }
        }
        return job.await()
    }

    suspend fun cancel(key: K) {
        val job = mutex.withLock { jobs.remove(key) }
        job?.cancelAndJoin()
    }
}
