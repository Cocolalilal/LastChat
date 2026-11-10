package me.rerere.rikkahub.phoneuse

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.avatar.animated.*
import me.rerere.rikkahub.ui.components.ui.ReadOnlyAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AssistantChatTheme
import me.rerere.rikkahub.ui.theme.RikkahubTheme

internal fun phoneActionLabel(action: String): String = when (action) {
    "observe" -> "Reading screen"
    "screenshot" -> "Looking at screen"
    "tap" -> "Tapping"
    "long_press" -> "Holding"
    "swipe", "scroll" -> "Scrolling"
    "type" -> "Typing"
    "press" -> "Navigating"
    "open_app" -> "Opening app"
    "list_apps" -> "Finding apps"
    "wait" -> "Waiting"
    else -> "Using phone"
}

@Composable
internal fun PhoneUseOverlayContent(
    manager: PhoneUseManager,
    settingsStore: SettingsStore,
    onOpen: (PhoneUseTask) -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val task by manager.session.collectAsStateWithLifecycle()
    val current = task ?: return
    val assistant = settings.assistants.firstOrNull { it.id == current.assistantId }
    if (assistant == null) {
        LaunchedEffect(current.conversationId) { manager.stop() }
        return
    }
    val chatService = org.koin.compose.koinInject<me.rerere.rikkahub.service.ChatService>()
    val conversation by chatService.getConversationFlow(current.conversationId).collectAsStateWithLifecycle()
    val needsReply = conversation.currentMessages.any { message ->
        message.parts.any { it is me.rerere.ai.ui.UIMessagePart.ToolCall &&
            it.approvalState == me.rerere.ai.ui.ToolApprovalState.Pending }
    }
    CompositionLocalProvider(LocalSettings provides settings) {
        RikkahubTheme {
            AssistantChatTheme(assistant) {
                PhoneUseControls(current, assistant, manager, { onOpen(current) }, floating = true, onDrag = onDrag, needsReply = needsReply)
            }
        }
    }
}

/** Shared with the system assistant: same character, surfaces, spacing and actions. */
@Composable
fun PhoneUseControls(
    task: PhoneUseTask,
    assistant: Assistant,
    manager: PhoneUseManager,
    onOpen: (() -> Unit)? = null,
    floating: Boolean = false,
    onDrag: ((Float, Float) -> Unit)? = null,
    needsReply: Boolean = false,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics = rememberPremiumHaptics(),
) {
    val motion = rememberSystemMotionPolicy()
    val active = task.running && !task.paused
    var expanded by remember(task.conversationId) { mutableStateOf(task.systemAssistant || !floating) }
    LaunchedEffect(needsReply) { if (needsReply) expanded = true }
    Surface(
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = if (floating) 6.dp else 0.dp,
        modifier = Modifier.padding(horizontal = if (task.systemAssistant) 16.dp else 8.dp, vertical = 8.dp)
            .then(if (task.systemAssistant) Modifier.fillMaxWidth() else Modifier.width(if (expanded) 360.dp else 112.dp))
            .then(if (floating && !task.systemAssistant && onDrag != null) Modifier.pointerInput(onDrag) {
                detectDragGestures { change, delta -> change.consume(); onDrag(delta.x, delta.y) }
            } else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val avatar = assistant.avatar
            Box(Modifier.then(if (floating && !task.systemAssistant) Modifier
                .semantics { contentDescription = "${assistant.name}, phone use controls" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse controls" else "Expand controls") {
                    expanded = !expanded
                } else Modifier)) {
            CompositionLocalProvider(LocalAvatarAnimate provides !motion.reduceMotion) {
                if (avatar is Avatar.Animated) {
                    // The existing generating pose; no orbit, bounce, or extra activity animation.
                    ProvideAvatarMotionHints(AvatarMotionHints(isGenerating = active)) {
                        AnimatedMarkAvatar(
                            shapeId = avatar.shape, eyeType = avatar.eyeType, colorHex = avatar.colorHex,
                            colorPreset = avatar.colorPreset, eyeColorHex = avatar.eyeColorHex,
                            eyeShade = avatar.eyeShade, personality = Personality.resolve(avatar.eyeType, avatar.traits),
                            isLoading = active, animate = !motion.reduceMotion, modifier = Modifier.size(44.dp),
                        )
                    }
                } else ReadOnlyAvatar(assistant.name, avatar, Modifier.size(40.dp), loading = active && !motion.reduceMotion)
            }
            }
            if (expanded) Column(Modifier.weight(1f)) {
                Text(assistant.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when { !task.running -> "Finished"; needsReply -> "Reply needed"; task.paused -> "Paused"; else -> phoneActionLabel(task.action) },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded && onOpen != null) IconButton(onClick = onOpen) {
                Icon(Icons.Rounded.OpenInFull, "Open conversation")
            }
            if (expanded && task.running) IconButton(onClick = { haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Tick); manager.togglePause() }) {
                Icon(if (task.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    if (task.paused) "Resume phone use" else "Pause phone use")
            }
            IconButton(onClick = { haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Tick); manager.stop() }) {
                Icon(if (task.running) Icons.Rounded.Stop else Icons.Rounded.Close,
                    if (task.running) "Stop phone use" else "Dismiss")
            }
        }
    }
}
