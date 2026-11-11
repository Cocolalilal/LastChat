package me.rerere.rikkahub.phoneuse

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.hasPendingToolApprovals
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AssistantChatTheme
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.compose.koinInject

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
    "finish" -> "Finishing"
    "reply" -> "Replying"
    else -> "Using phone"
}

internal fun phoneTaskLabel(task: PhoneUseTask): String = when {
    task.error != null -> "Interrupted"
    task.awaitingReply -> "Reply needed"
    !task.running -> "Reply ready"
    task.paused -> "Paused"
    else -> phoneActionLabel(task.action)
}

@Composable
internal fun PhoneUseOverlayContent(
    manager: PhoneUseManager,
    settingsStore: SettingsStore,
    onOpen: (PhoneUseTask) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onReplyFocus: (Boolean) -> Unit,
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val task by manager.session.collectAsStateWithLifecycle()
    val current = task ?: return
    val assistant = settings.assistants.firstOrNull { it.id == current.assistantId }
    if (assistant == null) {
        LaunchedEffect(current.conversationId) { manager.stop() }
        return
    }
    val chatService = koinInject<ChatService>()
    val conversation by chatService.getConversationFlow(current.conversationId).collectAsStateWithLifecycle()
    val lastUser = conversation.currentMessages.indexOfLast { it.role == MessageRole.USER }
    val latestReply = conversation.currentMessages.drop((lastUser + 1).coerceAtLeast(0))
        .lastOrNull { it.role == MessageRole.ASSISTANT }?.toContentText().orEmpty()
    val needsReply = conversation.hasPendingToolApprovals()
    val toaster = rememberAppToasterState()
    val context = LocalContext.current
    val nav = remember(context) { NavHostController(context) }
    val highlighter = koinInject<Highlighter>()
    CompositionLocalProvider(
        LocalSettings provides settings, LocalToaster provides toaster,
        LocalHighlighter provides highlighter, LocalNavController provides nav,
    ) {
        RikkahubTheme {
            AssistantChatTheme(assistant) {
                if (current.systemAssistant) {
                    if (current.returnFailed || current.action == "reply") {
                        AssistantPhoneUseReply(current, assistant, manager, conversation.currentMessages,
                            chatService) { onOpen(current) }
                    } else AssistantPhoneUseDock(current, assistant, manager) { onOpen(current) }
                } else PhoneUseControls(
                    current, assistant, manager, { onOpen(current) }, floating = true,
                    onDrag = onDrag, needsReply = needsReply,
                    reply = latestReply.ifBlank { current.reply },
                    onReplyFocus = onReplyFocus,
                    onSendReply = { text ->
                        manager.resumeReply(current.conversationId) { chatService.stopGeneration(current.conversationId) }
                        onReplyFocus(false)
                        chatService.sendMessage(current.conversationId, listOf(UIMessagePart.Text(text)),
                            suppressCompletionNotification = true)
                    },
                )
                AppToasterHost(toaster)
            }
        }
    }
}

/** One avatar and one continuously sized surface, including the completion reply. */
@Composable
fun PhoneUseControls(
    task: PhoneUseTask,
    assistant: Assistant,
    manager: PhoneUseManager,
    onOpen: (() -> Unit)? = null,
    floating: Boolean = false,
    onDrag: ((Float, Float) -> Unit)? = null,
    needsReply: Boolean = false,
    haptics: PremiumHaptics = rememberPremiumHaptics(),
    reply: String = task.reply,
    onReplyFocus: (Boolean) -> Unit = {},
    onSendReply: ((String) -> Unit)? = null,
) {
    var expanded by remember(task.conversationId) { mutableStateOf(task.systemAssistant || !floating) }
    var editing by remember(task.conversationId) { mutableStateOf(false) }
    var input by remember(task.conversationId) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(needsReply, task.running) {
        if (needsReply || !task.running || task.action == "reply") expanded = true
        if (task.running) editing = false
    }
    val appVisible by manager.appUiVisible.collectAsStateWithLifecycle()
    LaunchedEffect(appVisible) {
        if (appVisible) { editing = false; onReplyFocus(false) }
    }
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            focus.requestFocus()
            keyboard?.show()
        } else onReplyFocus(false)
    }
    Surface(
        shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp, shadowElevation = if (floating) 6.dp else 0.dp,
        modifier = Modifier.padding(8.dp)
            .widthIn(max = 360.dp)
            .width(if (expanded) 360.dp else 112.dp)
            .animateContentSize(),
    ) {
        Column {
            Row(
                Modifier.then(if (floating && onDrag != null) Modifier.pointerInput(onDrag) {
                    detectDragGestures { change, delta -> change.consume(); onDrag(delta.x, delta.y) }
                } else Modifier).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier
                    .semantics { contentDescription = "${assistant.name}, phone use controls" }
                    .clickable(role = Role.Button,
                        onClickLabel = if (expanded) "Collapse controls" else "Expand controls") {
                        haptics.perform(HapticPattern.Tick); expanded = !expanded
                    }) {
                    PhoneUseAvatar(assistant, Modifier.size(44.dp), task.running && !task.paused)
                }
                if (expanded) Column(Modifier.weight(1f)) {
                    Text(assistant.name, style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (needsReply) "Reply needed" else phoneTaskLabel(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (expanded && onOpen != null) IconButton(onClick = onOpen) {
                    Icon(Icons.Rounded.OpenInFull, "Open conversation")
                }
                if (expanded && task.running) IconButton(onClick = {
                    haptics.perform(HapticPattern.Tick); manager.togglePause()
                }) {
                    Icon(if (task.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (task.paused) "Resume phone use" else "Pause phone use")
                }
                IconButton(onClick = { haptics.perform(HapticPattern.Tick); manager.stop() }) {
                    Icon(if (task.running) Icons.Rounded.Stop else Icons.Rounded.Close,
                        if (task.running) "Stop phone use" else "Dismiss")
                }
            }
            if (expanded && (!task.running || task.action == "reply")) {
                Column(Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    task.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium) }
                    if (reply.isNotBlank()) SelectionContainer {
                        MarkdownBlock(reply, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                if (!task.running && onSendReply != null) {
                    if (editing) Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f).focusRequester(focus),
                            placeholder = { Text("Reply") }, maxLines = 3, shape = AppShapes.InputField)
                        IconButton(enabled = input.isNotBlank(), onClick = {
                            val text = input.trim(); input = ""; editing = false
                            keyboard?.hide(); onSendReply(text)
                        }) { Icon(Icons.Rounded.ArrowUpward, "Send reply") }
                    } else TextButton(onClick = { onReplyFocus(true); editing = true },
                        modifier = Modifier.padding(horizontal = 8.dp)) { Text("Reply") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
