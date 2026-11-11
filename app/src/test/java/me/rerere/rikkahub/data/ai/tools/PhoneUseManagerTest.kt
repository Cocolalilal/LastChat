package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.phoneuse.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PhoneUseManagerTest {
    private val manager = PhoneUseManager()
    private val id = Uuid.random()
    private val assistantId = Uuid.random()
    private val controller = object : PhoneController {
        override fun visiblePackages() = emptySet<String>()
        override suspend fun captureScreen(): PhoneScreen = error("Unused")
        override suspend fun screenshot(maxSide: Int) = byteArrayOf()
        override suspend fun gesture(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) = true
        override suspend fun setText(elementIndex: Int?, text: String) = true
        override suspend fun pressKey(key: PhoneKey) = true
        override fun listApps() = emptyList<PhoneApp>()
        override fun openApp(packageName: String) = true
    }

    @Test fun `no action runs until stop controls attach`() = runTest {
        manager.attach(controller)
        var executed = false
        val action = async { manager.runAction(id, assistantId, "tap", {}) { executed = true } }
        runCurrent()
        assertFalse(executed)
        manager.controlsReady.value = id
        action.await()
        assertTrue(executed)
    }

    @Test fun `pause waits and a different conversation cannot take over`() = runTest {
        manager.attach(controller)
        backgroundScope.launch {
            manager.session.filterNotNull().collect { manager.controlsReady.value = it.conversationId }
        }
        manager.runAction(id, assistantId, "observe", {}) {}
        val error = runCatching { manager.runAction(Uuid.random(), assistantId, "tap", {}) {} }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        manager.togglePause()
        var executed = false
        val action = async { manager.runAction(id, assistantId, "tap", {}) { executed = true } }
        runCurrent()
        assertFalse(executed)
        manager.togglePause()
        action.await()
        assertTrue(executed)
    }

    @Test fun `disconnect cancels the owner and clears controls`() = runTest {
        manager.attach(controller)
        backgroundScope.launch {
            manager.session.filterNotNull().collect { manager.controlsReady.value = it.conversationId }
        }
        var cancelled = false
        manager.runAction(id, assistantId, "observe", { cancelled = true }) {}
        manager.detach(controller)
        assertTrue(cancelled)
        assertNull(manager.session.value)
        assertNull(manager.controlsReady.value)
    }

    @Test fun `system assistant source survives finishing and reopening the task`() = runTest {
        manager.attach(controller)
        manager.registerAssistantConversation(id)
        backgroundScope.launch {
            manager.session.filterNotNull().collect { manager.controlsReady.value = it.conversationId }
        }
        manager.runAction(id, assistantId, "observe", {}) {}
        assertTrue(manager.session.value!!.systemAssistant)
        manager.finish(Uuid.random())
        assertTrue(manager.session.value!!.running)
        manager.finish(id)
        assertFalse(manager.session.value!!.running)
        assertEquals(id, manager.session.value!!.conversationId)
    }
    private suspend fun startTask(system: Boolean = false) {
        manager.attach(controller)
        if (system) manager.registerAssistantConversation(id)
        manager.controlsReady.value = id
        // runAction clears readiness for a new owner; the simulated host attaches below.
        kotlinx.coroutines.coroutineScope {
            val action = async { manager.runAction(id, assistantId, "observe", {}) {} }
            manager.session.first { it != null }
            manager.controlsReady.value = id
            action.await()
        }
    }

    @Test fun `waiting for approval retains task and can continue`() = runTest {
        startTask()
        manager.generationSettled(id, awaitingReply = true, reply = "Which account?")
        assertTrue(manager.session.value!!.running)
        assertTrue(manager.session.value!!.awaitingReply)
        manager.runAction(id, assistantId, "tap", {}) {}
        assertFalse(manager.session.value!!.awaitingReply)
        manager.generationSettled(id, awaitingReply = false, reply = "Done")
        assertFalse(manager.session.value!!.running)
        assertEquals("Done", manager.session.value!!.reply)
    }

    @Test fun `completion belongs to visible assistant or attached phone controls`() = runTest {
        manager.registerAssistantConversation(id)
        assertFalse(manager.ownsCompletion(id))
        manager.assistantUiVisible.value = true
        assertTrue(manager.ownsCompletion(id))
        manager.assistantUiVisible.value = false
        startTask(system = true)
        assertTrue(manager.ownsCompletion(id))
        assertTrue(manager.claimAssistantReturn(id))
        assertFalse(manager.claimAssistantReturn(id))
        manager.generationSettled(id, false, "Done")
        manager.markAssistantPresented(id)
        assertFalse(shouldShowPhoneOverlay(manager.session.value, false, false))
        assertFalse(manager.ownsCompletion(id))
        // An ordinary later chat turn must not reopen a completed phone task.
        manager.generationSettled(id, false, "A later chat reply")
        assertEquals("Done", manager.session.value!!.reply)
        assertTrue(manager.session.value!!.resultPresented)
    }

    @Test fun `gestures keep overlay visible while screenshots and app UI hide it`() = runTest {
        startTask()
        manager.setActing(true)
        assertTrue(shouldShowPhoneOverlay(manager.session.value, false, manager.capturing.value))
        assertFalse(shouldShowPhoneOverlay(manager.session.value, true, false))
        manager.setActing(true, capturing = true)
        assertFalse(shouldShowPhoneOverlay(manager.session.value, false, manager.capturing.value))
        manager.setActing(false)
        assertFalse(manager.capturing.value)
        assertTrue(shouldShowPhoneOverlay(manager.session.value, false, false))
    }

    @Test fun `reply speech and follow up cancellation have a single owner`() = runTest {
        val replyId = Uuid.random()
        assertTrue(manager.claimSpokenReply(replyId))
        assertFalse(manager.claimSpokenReply(replyId))
        startTask()
        manager.generationSettled(id, false, "Done")
        var cancelled = false
        manager.resumeReply(id) { cancelled = true }
        assertTrue(manager.session.value!!.running)
        assertEquals("reply", manager.session.value!!.action)
        manager.stop()
        assertTrue(cancelled)
    }

}
