package me.rerere.rikkahub.ui.activity

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.phoneuse.PhoneUseTask
import kotlin.uuid.Uuid

/** The quick startup cache deliberately has no system-assistant preferences. */
internal suspend fun StateFlow<Settings>.awaitAssistantOverlayAssistant(): Assistant =
    first { !it.init }.resolveAssistantOverlayAssistant()

/** Fresh assist gestures may resume their own active work, never an unrelated phone bubble. */
internal fun shouldReuseAssistantScene(
    requestedConversation: Uuid?,
    retainedConversation: Uuid?,
    retainedPresentation: AssistantPresentation?,
    configuredAssistant: Uuid,
    task: PhoneUseTask?,
    recreating: Boolean,
): Boolean {
    if (retainedPresentation == null || retainedPresentation == AssistantPresentation.CLOSED) return false
    if (recreating) return true
    if (requestedConversation != null) return requestedConversation == retainedConversation
    return task?.let {
        // finish_tool can settle before the final answer and Activity return are shown.
        // A summon during that handoff belongs to the same system interaction.
        val pendingReturn = !it.resultPresented &&
            (retainedPresentation == AssistantPresentation.PHONE || it.returnRequested)
        it.systemAssistant && (it.running || pendingReturn) && it.conversationId == retainedConversation &&
            it.assistantId == configuredAssistant
    } == true
}
