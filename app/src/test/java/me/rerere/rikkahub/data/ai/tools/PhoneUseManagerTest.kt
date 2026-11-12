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

    @Test fun `opening assistant in app transfers work without cancelling or reopening the overlay`() = runTest {
        startTask(system = true)
        var cancelled = false
        manager.resumeReply(id) { cancelled = true }
        manager.assistantDocked.value = id
        manager.assistantReturning.value = true
        manager.releaseAssistantOverlay(id)
        assertFalse(cancelled)
        assertTrue(manager.session.value!!.running)
        assertFalse(manager.session.value!!.systemAssistant)
        assertFalse(manager.isAssistantConversation(id))
        assertNull(manager.assistantDocked.value)
        assertFalse(manager.assistantReturning.value)
        manager.generationSettled(id, true, "Which account?")
        assertFalse(shouldReturnToAssistant(manager.session.value!!))
        manager.stop()
        // Transferring presentation must leave generation cancellation attached to the task.
        assertTrue(cancelled)
    }

    @Test fun `transferring a different assistant does not change active phone work`() = runTest {
        startTask(system = true)
        val before = manager.session.value
        manager.releaseAssistantOverlay(Uuid.random())
        assertEquals(before, manager.session.value)
        assertTrue(manager.isAssistantConversation(id))
    }
    @Test fun `a reply visible in the service still owns completion after Activity return is denied`() = runTest {
        startTask(system = true)
        manager.generationSettled(id, false, "Done")
        manager.markAssistantPresented(id)
        manager.assistantUiVisible.value = false
        manager.assistantReplyVisible.value = id
        assertTrue(manager.ownsCompletion(id))
        assertFalse(manager.ownsCompletion(Uuid.random()))
        manager.assistantReplyVisible.value = null
        assertFalse(manager.ownsCompletion(id))
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

    @Test fun `an unsent fallback reply transfers once to the same assistant conversation`() = runTest {
        startTask(system = true)
        manager.generationSettled(id, false, "Done")
        manager.updateReplyDraft(id, "One more thing")
        assertEquals("", manager.takeReplyDraft(Uuid.random()))
        assertEquals("One more thing", manager.replyDraft(id))
        assertEquals("One more thing", manager.takeReplyDraft(id))
        assertEquals("", manager.takeReplyDraft(id))
        manager.updateReplyDraft(id, "Discard on dismiss")
        manager.stop()
        assertEquals("", manager.replyDraft(id))
    }

    @Test fun `a reply retry renews assistant return ownership before fallback speech`() = runTest {
        startTask(system = true)
        manager.generationSettled(id, false, "Done")
        manager.markReturnFailed(id)
        assertTrue(manager.session.value!!.returnFailed)
        manager.resumeReply(id) {}
        assertFalse(manager.session.value!!.returnFailed)
        manager.generationSettled(id, false, "Another reply")
        assertTrue(manager.claimAssistantReturn(id))
        assertFalse(manager.session.value!!.returnFailed)
        assertFalse(manager.claimAssistantReturn(id))
        manager.markReturnFailed(id)
        manager.prepareAssistantReturn(id)
        assertFalse(manager.session.value!!.returnFailed)
        assertTrue(manager.session.value!!.returnRequested)
        assertEquals(id, manager.assistantDocked.value)
    }

    @Test fun `cancelling phone work retains the assistant and restores return ownership`() = runTest {
        startTask(system = true)
        var cancelled = false
        manager.resumeReply(id) { cancelled = true }
        manager.assistantDocked.value = id
        manager.cancelAndReturn()
        assertTrue(cancelled)
        assertEquals(id, manager.session.value!!.conversationId)
        assertFalse(manager.session.value!!.running)
        assertFalse(manager.session.value!!.paused)
        assertEquals(id, manager.assistantDocked.value)
        assertTrue(manager.claimAssistantReturn(id))
        manager.stop()
        assertNull(manager.assistantDocked.value)
        assertNull(manager.session.value)
    }

}
