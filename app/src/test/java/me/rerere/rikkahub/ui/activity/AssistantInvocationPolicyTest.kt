package me.rerere.rikkahub.ui.activity

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantOverlayConfig
import me.rerere.rikkahub.phoneuse.PhoneUseTask
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantInvocationPolicyTest {
    @Test fun `cold invocation waits for the saved system character instead of the cached app character`() = runTest {
        val app = Assistant(name = "App")
        val configured = Assistant(name = "System")
        val preferences = MutableStateFlow(Settings(init = true, assistantId = app.id, assistants = listOf(app)))
        val selection = async { preferences.awaitAssistantOverlayAssistant() }
        runCurrent()
        assertFalse(selection.isCompleted)
        preferences.value = Settings(assistantId = app.id, assistants = listOf(app, configured),
            assistantOverlayConfig = AssistantOverlayConfig(assistantId = configured.id))
        assertEquals(configured.id, selection.await().id)
    }

    @Test fun `fresh assist never adopts the ordinary phone bubble even when its character matches`() {
        val conversation = Uuid.random()
        val character = Uuid.random()
        val task = PhoneUseTask(conversation, character, systemAssistant = false, action = "tap")
        assertFalse(shouldReuseAssistantScene(null, conversation, AssistantPresentation.PHONE,
            character, task, false))
        assertTrue(shouldReuseAssistantScene(null, conversation, AssistantPresentation.PHONE,
            character, task.copy(systemAssistant = true), false))
        assertFalse(shouldReuseAssistantScene(null, conversation, AssistantPresentation.PHONE,
            Uuid.random(), task.copy(systemAssistant = true), false))
    }

    @Test fun `summoning during the final reply handoff preserves its system conversation`() {
        val conversation = Uuid.random()
        val character = Uuid.random()
        val returning = PhoneUseTask(conversation, character, true, "finish", running = false)
        assertTrue(shouldReuseAssistantScene(null, conversation, AssistantPresentation.PHONE,
            character, returning, false))
        assertTrue(shouldReuseAssistantScene(null, conversation, AssistantPresentation.NORMAL,
            character, returning.copy(returnRequested = true), false))
        assertFalse(shouldReuseAssistantScene(null, conversation, AssistantPresentation.PHONE,
            character, returning.copy(resultPresented = true), false))
    }

    @Test fun `completed phone replies do not replace a fresh configured invocation`() {
        val conversation = Uuid.random()
        val character = Uuid.random()
        val completed = PhoneUseTask(conversation, character, true, "finish", running = false)
        assertFalse(shouldReuseAssistantScene(null, conversation, AssistantPresentation.NORMAL,
            character, completed, false))
        // Explicit return and Activity recreation preserve the existing conversation/draft.
        assertTrue(shouldReuseAssistantScene(conversation, conversation, AssistantPresentation.HIDDEN,
            Uuid.random(), completed, false))
        assertTrue(shouldReuseAssistantScene(null, conversation, AssistantPresentation.HIDDEN,
            Uuid.random(), completed, true))
        assertFalse(shouldReuseAssistantScene(Uuid.random(), conversation, AssistantPresentation.NORMAL,
            character, completed, false))
    }
}
