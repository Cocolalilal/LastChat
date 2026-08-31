package me.rerere.rikkahub.data.ai.local

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.rerere.common.inference.LocalInferenceWorkload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLocalInferenceManagerTest {
    private fun manager(
        evict: suspend (LocalInferenceWorkload) -> Unit = {},
    ) = AndroidLocalInferenceManager(
        evictWorkload = evict,
        idleTimeoutMs = { Long.MAX_VALUE },
    )

    @Test
    fun `generation lease covers the full operation and queues the next chat`() = runBlocking {
        val manager = manager()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val first = async {
            manager.withLease(LocalInferenceWorkload.CHAT, "model-a") {
                order += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first-end"
            }
        }
        firstEntered.await()

        val second = async {
            manager.withLease(LocalInferenceWorkload.CHAT, "model-b") {
                order += "second-start"
            }
        }
        awaitWaiting(manager, "model-b")

        assertEquals("model-a", manager.queueState.value.active?.modelId)
        assertEquals(listOf("model-b"), manager.queueState.value.waiting.map { it.modelId })
        assertEquals(listOf("first-start"), order)

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second-start"), order)
        assertNull(manager.queueState.value.active)
        assertTrue(manager.queueState.value.waiting.isEmpty())
    }

    @Test
    fun `cancelling a queued request removes it without disturbing the active lease`() = runBlocking {
        val manager = manager()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = async {
            manager.withLease(LocalInferenceWorkload.CHAT, "model-a") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val queued = async {
            manager.withLease(LocalInferenceWorkload.CHAT, "model-b") { Unit }
        }
        awaitWaiting(manager, "model-b")
        queued.cancelAndJoin()

        assertEquals("model-a", manager.queueState.value.active?.modelId)
        assertTrue(manager.queueState.value.waiting.isEmpty())

        releaseFirst.complete(Unit)
        first.await()
    }

    @Test
    fun `cancelling the active generation releases the next queued chat`() = runBlocking {
        val manager = manager()
        val firstEntered = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val first = async {
            manager.withLease(LocalInferenceWorkload.CHAT, "model-a") {
                firstEntered.complete(Unit)
                neverCompletes.await()
            }
        }
        firstEntered.await()
        val second = async {
            manager.withLease(LocalInferenceWorkload.CHAT, "model-b") {
                secondEntered.complete(Unit)
            }
        }
        awaitWaiting(manager, "model-b")

        first.cancelAndJoin()
        secondEntered.await()
        second.await()

        assertNull(manager.queueState.value.active)
        assertTrue(manager.queueState.value.waiting.isEmpty())
    }

    @Test
    fun `switching workload evicts other native runtimes before entering`() = runBlocking {
        val events = mutableListOf<String>()
        val manager = manager { events += "evict-${it.name.lowercase()}" }

        manager.withLease(LocalInferenceWorkload.EMBEDDING, "embed") {
            events += "embedding-start"
        }

        assertTrue(events.indexOf("evict-chat") < events.indexOf("embedding-start"))
        assertTrue(events.indexOf("evict-speech") < events.indexOf("embedding-start"))
    }

    private suspend fun awaitWaiting(manager: AndroidLocalInferenceManager, modelId: String) {
        repeat(1_000) {
            if (manager.queueState.value.waiting.any { it.modelId == modelId }) return
            yield()
        }
        error("Request $modelId never entered the queue")
    }
}
