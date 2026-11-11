package me.rerere.rikkahub.phoneuse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.datastore.getEffectiveTTSProvider
import me.rerere.rikkahub.utils.copyMessageToClipboard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.activity.SilkGlowLayer
import me.rerere.rikkahub.ui.activity.TranscriptPanel
import me.rerere.rikkahub.ui.components.chat.ActivityState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy

/** The exact transcript header used by the system assistant, condensed while it operates another app. */
@Composable
internal fun AssistantPhoneUseDock(
    task: PhoneUseTask,
    assistant: Assistant,
    manager: PhoneUseManager,
    onOpen: () -> Unit,
) {
    val config = LocalSettings.current.assistantOverlayConfig
    Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.BottomCenter) {
        if (!rememberSystemMotionPolicy().reduceMotion) SilkGlowLayer(
            active = false, waveSignal = 0, originX = config.waveOriginX, originY = config.waveOriginY,
            modifier = Modifier.fillMaxWidth().height(112.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            TranscriptPanel(
                assistantAvatar = { PhoneUseAvatar(assistant, it, task.running && !task.paused) },
                messages = emptyList(), activityState = ActivityState.Waiting,
                isGenerating = task.running, isSpeaking = false, onOpenInApp = onOpen,
                onCopy = {}, onRegenerate = {}, onToggleTts = {}, compact = true,
                phoneTask = task, onPausePhone = { manager.togglePause() }, onStopPhone = { manager.stop() },
            )
        }
    }
}

/** Fallback when Android cannot foreground the assistant activity; still the same transcript UI. */
@Composable
internal fun AssistantPhoneUseReply(
    task: PhoneUseTask,
    assistant: Assistant,
    manager: PhoneUseManager,
    messages: List<me.rerere.ai.ui.UIMessage>,
    chatService: me.rerere.rikkahub.service.ChatService,
    onOpen: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = LocalSettings.current
    val tts = me.rerere.rikkahub.ui.hooks.rememberCustomTtsState()
    val speaking by tts.isSpeaking.collectAsStateWithLifecycle()
    val reply = messages.lastOrNull()?.takeIf { it.role == me.rerere.ai.core.MessageRole.ASSISTANT }
    LaunchedEffect(task.running, reply?.id) {
        if (!task.running && task.error == null && settings.assistantOverlayConfig.autoReadReply &&
            reply != null && reply.toContentText().isNotBlank() && manager.claimSpokenReply(reply.id)) {
            tts.speak(reply.toContentText(), overrideSetting = settings.getEffectiveTTSProvider(assistant))
        }
    }
    Box(Modifier.padding(16.dp)) {
        TranscriptPanel(
            assistantAvatar = { PhoneUseAvatar(assistant, it, task.running && !task.paused) },
            messages = messages.filter { it.toContentText().isNotBlank() },
            activityState = if (task.running) ActivityState.Waiting else ActivityState.Hidden,
            isGenerating = task.running, isSpeaking = speaking, onOpenInApp = onOpen,
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
            phoneTask = task,
            onPausePhone = { manager.togglePause() }, onStopPhone = { manager.stop() },
        )
    }
}
