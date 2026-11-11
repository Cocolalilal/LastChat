package me.rerere.rikkahub.phoneuse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import me.rerere.rikkahub.ui.activity.AssistantInputReveal
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.datastore.getEffectiveTTSProvider
import me.rerere.rikkahub.utils.copyMessageToClipboard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.activity.TranscriptPanel
import me.rerere.rikkahub.ui.components.chat.ActivityState
import me.rerere.rikkahub.ui.context.LocalSettings

/** The exact transcript header used by the system assistant, condensed while it operates another app. */
@Composable
internal fun AssistantPhoneUseDock(
    task: PhoneUseTask,
    assistant: Assistant,
    manager: PhoneUseManager,
    onOpen: () -> Unit,
    drag: PhoneOverlayDragCallbacks? = null,
) {
    Box(Modifier.fillMaxWidth()) {
            TranscriptPanel(
                assistantAvatar = { PhoneUseAvatar(assistant, it, task.running && !task.paused) },
                messages = emptyList(), activityState = ActivityState.Waiting,
                isGenerating = task.running, isSpeaking = false, onOpenInApp = onOpen,
                onCopy = {}, onRegenerate = {}, onToggleTts = {}, compact = true,
                windowDrag = drag, openDescription = "Open assistant",
                phoneTask = task, onPausePhone = { manager.togglePause() }, onStopPhone = { manager.cancelAndReturn() },
            )
    }
}

/** Shared reply and composer while the assistant returns, or if Android blocks that return. */
@Composable
internal fun AssistantPhoneUseReply(
    task: PhoneUseTask,
    assistant: Assistant,
    manager: PhoneUseManager,
    messages: List<me.rerere.ai.ui.UIMessage>,
    chatService: me.rerere.rikkahub.service.ChatService,
    onOpen: () -> Unit,
    drag: PhoneOverlayDragCallbacks? = null,
    onReplyFocus: (Boolean) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = LocalSettings.current
    val tts = me.rerere.rikkahub.ui.hooks.rememberCustomTtsState()
    val speaking by tts.isSpeaking.collectAsStateWithLifecycle()
    val appVisible by manager.appUiVisible.collectAsStateWithLifecycle()
    val generation by chatService.getGenerationJobStateFlow(task.conversationId)
        .collectAsStateWithLifecycle(initialValue = null)
    val reply = messages.lastOrNull()?.takeIf { it.role == me.rerere.ai.core.MessageRole.ASSISTANT }
    LaunchedEffect(task.running, reply?.id, appVisible, task.returnFailed) {
        if (!appVisible && !task.running && task.returnFailed && task.error == null && settings.assistantOverlayConfig.autoReadReply &&
            reply != null && reply.toContentText().isNotBlank() && manager.claimSpokenReply(reply.id)) {
            tts.speak(reply.toContentText(), overrideSetting = settings.getEffectiveTTSProvider(assistant))
        }
    }
    // Keep the same panel instance and compact pose while the activity returns. Only an
    // OS-denied return expands this host, so two differently sized surfaces never swap.
    val compact = !task.returnFailed && task.action != "reply"
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(compact) {
        if (compact) { focus.clearFocus(); keyboard?.hide(); onReplyFocus(false) }
    }
    Column(Modifier.fillMaxWidth()) {
        TranscriptPanel(
            assistantAvatar = { PhoneUseAvatar(assistant, it, generation != null && !task.paused) },
            messages = messages.filter { it.toContentText().isNotBlank() },
            activityState = if (task.running) ActivityState.Waiting else ActivityState.Hidden,
            isGenerating = generation != null, isSpeaking = speaking, onOpenInApp = onOpen,
            onCopy = { context.copyMessageToClipboard(it) },
            onRegenerate = {
                manager.resumeReply(task.conversationId) { chatService.stopGeneration(task.conversationId) }
                chatService.regenerateAtMessage(task.conversationId, it, regenerateAssistantMsg = true,
                    suppressCompletionNotification = true)
            },
            onToggleTts = {
                if (speaking) tts.stop()
                else tts.speak(it.toContentText(), overrideSetting = settings.getEffectiveTTSProvider(assistant))
            },
            compact = compact,
            modifier = Modifier.weight(1f, fill = false),
            phoneTask = task, windowDrag = drag, onDismiss = { manager.stop() },
            openDescription = "Open assistant",
            onPausePhone = { manager.togglePause() }, onStopPhone = {
                if (task.action != "reply") manager.cancelAndReturn()
                chatService.stopGeneration(task.conversationId)
            },
        )
        AssistantInputReveal(visible = !compact) {
            PhoneUseReplyComposer(task, manager, onReplyFocus, onSend = { text ->
                manager.resumeReply(task.conversationId) { chatService.stopGeneration(task.conversationId) }
                chatService.sendMessage(task.conversationId, listOf(me.rerere.ai.ui.UIMessagePart.Text(text)),
                    suppressCompletionNotification = true)
            }, modifier = Modifier.padding(top = 8.dp),
                onStopGeneration = { chatService.stopGeneration(task.conversationId) })
        }
    }
}
