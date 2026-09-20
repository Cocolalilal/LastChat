package me.rerere.ai.generation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableTaskSchedulerTest {
    @Test
    fun inProcessSchedulerRunsRegisteredHandler() = runBlocking {
        val scheduler = InProcessPortableTaskScheduler(CoroutineScope(SupervisorJob()))
        var ran = 0
        scheduler.register(PortableBackgroundTask.SPONTANEOUS_MESSAGES) {
            ran += 1
            true
        }
        assertTrue(scheduler.run(PortableBackgroundTask.SPONTANEOUS_MESSAGES))
        assertEquals(1, ran)
    }

    @Test
    fun missingHandlerReturnsFalse() = runBlocking {
        val scheduler = InProcessPortableTaskScheduler(CoroutineScope(SupervisorJob()))
        assertEquals(false, scheduler.run(PortableBackgroundTask.CHAT_STORAGE_MAINTENANCE))
    }

    @Test
    fun enqueueRunsHandlerWithoutDelay() = runBlocking {
        val scheduler = InProcessPortableTaskScheduler(this)
        var ran = 0
        scheduler.register(PortableBackgroundTask.SCHEDULED_MESSAGES) { request ->
            ran += 1
            request.extras["id"] == "msg-1"
        }
        scheduler.enqueue(
            PortableTaskRequest(
                task = PortableBackgroundTask.SCHEDULED_MESSAGES,
                uniqueName = "scheduled:msg-1",
                extras = mapOf("id" to "msg-1"),
            ),
        )
        delay(50)
        scheduler.cancel("scheduled:msg-1")
        assertEquals(1, ran)
    }
}
