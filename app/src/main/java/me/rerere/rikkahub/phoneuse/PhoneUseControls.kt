package me.rerere.rikkahub.phoneuse

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy
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
import me.rerere.rikkahub.ui.components.ui.FloatingOverlayLayout
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
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
    !task.running -> ""
    task.paused -> "Paused"
    else -> phoneActionLabel(task.action)
}

@Composable
internal fun PhoneUseOverlayContent(
    manager: PhoneUseManager,
    settingsStore: SettingsStore,
    onOpen: (PhoneUseTask) -> Unit,
    drag: PhoneOverlayDragCallbacks,
    onReplyFocus: (Boolean) -> Unit,
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val reduceMotion = rememberSystemMotionPolicy().reduceMotion
    val nativeBlur by manager.overlayBlur.collectAsStateWithLifecycle()
    SideEffect { drag.reduceMotion = reduceMotion }
    val task by manager.session.collectAsStateWithLifecycle()
    var retainedTask by remember { mutableStateOf(task) }
    SideEffect { if (task != null) retainedTask = task }
    val current = task ?: retainedTask ?: return
    val assistant = settings.assistants.firstOrNull { it.id == current.assistantId }
    if (assistant == null) {
        LaunchedEffect(current.conversationId) { manager.stop() }
        return
    }
    val chatService = koinInject<ChatService>()
    val conversation by chatService.getConversationFlow(current.conversationId).collectAsStateWithLifecycle()
    val generation by chatService.getGenerationJobStateFlow(current.conversationId)
        .collectAsStateWithLifecycle(initialValue = null)
    val popupTask = if (!current.running && generation != null) current.copy(running = true, action = "reply") else current
    val lastUser = conversation.currentMessages.indexOfLast { it.role == MessageRole.USER }
    val latestReply = conversation.currentMessages.drop((lastUser + 1).coerceAtLeast(0))
        .lastOrNull { it.role == MessageRole.ASSISTANT }?.toContentText().orEmpty()
    val needsReply = conversation.hasPendingToolApprovals()
    val toaster = rememberAppToasterState()
    val context = LocalContext.current
    val nav = remember(context) { NavHostController(context) }
    val highlighter = koinInject<Highlighter>()
    CompositionLocalProvider(
        me.rerere.rikkahub.ui.modifier.LocalLastChatBlur provides me.rerere.rikkahub.ui.modifier.LastChatBlur(
            enabled = nativeBlur, windowBlur = nativeBlur),
        LocalSettings provides settings, LocalToaster provides toaster,
        LocalHighlighter provides highlighter, LocalNavController provides nav,
    ) {
        RikkahubTheme {
            AssistantChatTheme(assistant) {
                FloatingOverlayLayout(content = {
                    if (current.systemAssistant) {
                        AssistantPhoneUseReply(current, assistant, manager, conversation.currentMessages,
                            chatService, onOpen = { onOpen(current) },
                            drag = drag.takeUnless { current.returnRequested }, onReplyFocus = onReplyFocus)
                    } else PhoneUseControls(
                        popupTask, assistant, manager, { onOpen(current) }, floating = true,
                        drag = drag, needsReply = needsReply,
                        reply = latestReply.ifBlank { current.reply },
                        onReplyFocus = onReplyFocus,
                        onStopGeneration = { chatService.stopGeneration(current.conversationId) },
                        onSendReply = { text ->
                            manager.resumeReply(current.conversationId) { chatService.stopGeneration(current.conversationId) }
                            onReplyFocus(false)
                            chatService.sendMessage(current.conversationId, listOf(UIMessagePart.Text(text)),
                                suppressCompletionNotification = true)
                        },
                    )
                }, decoration = { AppToasterHost(toaster) })
            }
        }
    }
}

/** The capsule and reply share one spring, so their geometry and opacity cannot drift apart. */
@Composable
fun PhoneUseControls(
    task: PhoneUseTask,
    assistant: Assistant,
    manager: PhoneUseManager,
    onOpen: (() -> Unit)? = null,
    floating: Boolean = false,
    drag: PhoneOverlayDragCallbacks? = null,
    needsReply: Boolean = false,
    haptics: PremiumHaptics = rememberPremiumHaptics(),
    reply: String = task.reply,
    onReplyFocus: (Boolean) -> Unit = {},
    onSendReply: ((String) -> Unit)? = null,
    onStopGeneration: (() -> Unit)? = null,
) {
    var expanded by remember(task.conversationId) {
        mutableStateOf(task.systemAssistant || !floating || !task.running || task.awaitingReply || needsReply)
    }
    val reduceMotion = rememberSystemMotionPolicy().reduceMotion
    val progress by androidx.compose.animation.core.animateFloatAsState(
        if (expanded) 1f else 0f,
        if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS),
        label = "phonePopupExpansion",
    )
    val expansion = progress.coerceIn(0f, 1f)
    val expandedWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 16)
        .coerceIn(104, 360).dp
    // A tool finishing is not the end of generation. Keep the capsule in place through the
    // model's final answer; only the actual settled turn (or an approval) requests expansion.
    LaunchedEffect(needsReply, task.running, task.awaitingReply) {
        if (needsReply || !task.running || task.awaitingReply) expanded = true
    }
    Surface(
        // Avatar radius 20 + inset 8: both caps remain optically concentric.
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = me.rerere.rikkahub.ui.modifier.blurredContainerColor(MaterialTheme.colorScheme.surfaceContainerLow),
        border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerLow),
        tonalElevation = 3.dp, shadowElevation = if (floating) 6.dp else 0.dp,
        modifier = Modifier.padding(8.dp),
    ) {
        PhonePopupExpansionLayout(
            expansion = expansion, expandedWidth = expandedWidth,
            header = {
                Box(Modifier.fillMaxWidth().height(56.dp)
                    .phoneOverlayDrag(if (floating) drag else null, haptics)) {
                    Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                        .semantics { contentDescription = "${assistant.name}, phone use controls" }
                        .clickable(role = Role.Button,
                            onClickLabel = if (expanded) "Collapse controls" else "Expand controls") {
                            haptics.perform(HapticPattern.Tick)
                            expanded = !expanded
                            if (!expanded) onReplyFocus(false)
                        }) {
                        PhoneUseAvatar(assistant, Modifier.size(40.dp), task.running && !task.paused)
                    }
                    // Always keep the same header children measured at their final width.
                    // The surface reveals them as it grows; no width threshold inserts controls.
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 56.dp)
                        .clipToBounds()) {
                        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(expandedWidth - 112.dp)
                            .graphicsLayer { alpha = expansion },
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(assistant.name, style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val status = if (needsReply) "Reply needed" else phoneTaskLabel(task)
                                Text(status, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val pauseAlpha by androidx.compose.animation.core.animateFloatAsState(
                                if (task.running) 1f else 0f,
                                if (reduceMotion) snap() else spring(stiffness = PHONE_MOTION_STIFFNESS),
                                label = "phonePauseVisibility",
                            )
                            IconButton(
                                modifier = Modifier.size(40.dp).graphicsLayer { alpha = pauseAlpha },
                                enabled = task.running && expansion > 0.95f,
                                onClick = { haptics.perform(HapticPattern.Tick); manager.togglePause() },
                            ) {
                                Icon(if (task.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    if (task.paused) "Resume phone use" else "Pause phone use")
                            }
                            if (onOpen != null) IconButton(modifier = Modifier.size(40.dp),
                                enabled = expansion > 0.95f, onClick = onOpen) {
                                Icon(Icons.Rounded.OpenInFull, "Open conversation")
                            }
                        }
                    }
                    IconButton(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(40.dp),
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            if (task.running && task.action == "reply" && onStopGeneration != null) onStopGeneration()
                            else manager.stop()
                        }) {
                        Icon(if (task.running) Icons.Rounded.Stop else Icons.Rounded.Close,
                            if (task.running) "Stop phone use" else "Dismiss")
                    }
                }
            },
            body = {
                // A collapsed popup has no Markdown renderer. Once opened, its viewport stays
                // stable throughout streaming and follow-up turns, including composer focus.
                if (expanded || expansion > 0f) Column(Modifier.fillMaxWidth().graphicsLayer { alpha = expansion }) {
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).height(220.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)) {
                        task.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium) }
                        if (reply.isNotBlank()) SelectionContainer {
                            MarkdownBlock(reply, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (onSendReply != null) PhoneUseReplyComposer(
                        task, manager, onReplyFocus, onSendReply,
                        modifier = Modifier.padding(12.dp), onStopGeneration = onStopGeneration, nested = true,
                    ) else Spacer(Modifier.height(12.dp))
                }
            },
        )
    }
}

/** Measure rich content once at its final width; one progress value reveals the whole surface. */
@Composable
private fun PhonePopupExpansionLayout(
    expansion: Float,
    expandedWidth: androidx.compose.ui.unit.Dp,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        modifier = Modifier.clipToBounds(),
        content = { Box { header() }; Box { body() } },
    ) { measurables, constraints ->
        val expanded = expandedWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val compact = 104.dp.roundToPx().coerceAtMost(expanded)
        val width = androidx.compose.ui.util.lerp(compact, expanded, expansion)
        val headerPlaceable = measurables[0].measure(
            constraints.copy(minWidth = width, maxWidth = width, minHeight = 0))
        val bodyPlaceable = measurables[1].measure(
            constraints.copy(minWidth = expanded, maxWidth = expanded, minHeight = 0,
                maxHeight = (constraints.maxHeight - headerPlaceable.height).coerceAtLeast(0)))
        val revealedBody = (bodyPlaceable.height * expansion).toInt()
        layout(width, headerPlaceable.height + revealedBody) {
            headerPlaceable.placeRelative(0, 0)
            bodyPlaceable.placeRelative(0, headerPlaceable.height)
        }
    }
}

/** A visible composer after completion; focusing it is deliberate and never opens the IME on its own. */
@Composable
internal fun PhoneUseReplyComposer(
    task: PhoneUseTask,
    manager: PhoneUseManager,
    onReplyFocus: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    onStopGeneration: (() -> Unit)? = null,
    nested: Boolean = false,
) {
    var input by remember(task.conversationId) { mutableStateOf(manager.replyDraft(task.conversationId)) }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val appVisible by manager.appUiVisible.collectAsStateWithLifecycle()
    LaunchedEffect(appVisible) {
        if (appVisible) { focus.clearFocus(); onReplyFocus(false) }
    }
    LaunchedEffect(focused) {
        onReplyFocus(focused)
        if (focused) { withFrameNanos { }; keyboard?.show() }
    }
    DisposableEffect(Unit) { onDispose { onReplyFocus(false) } }
    fun send() {
        val message = input.trim()
        if (message.isEmpty() || task.running) return
        input = ""
        manager.updateReplyDraft(task.conversationId, "")
        focus.clearFocus(); keyboard?.hide(); onReplyFocus(false)
        onSend(message)
    }
    // A nested composer follows the card's curve at a 12 dp inset; separate bars keep 28 dp ends.
    Surface(modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(
        if (nested) 28.dp - 12.dp else 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = input, onValueChange = { input = it; manager.updateReplyDraft(task.conversationId, it) }, maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 120.dp)
                    .pointerInput(onReplyFocus) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            onReplyFocus(true)
                        }
                    }.onFocusChanged { focused = it.isFocused },
                decorationBox = { field ->
                    Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge)
                        field()
                    }
                },
            )
            IconButton(enabled = if (task.running) onStopGeneration != null else input.isNotBlank(), onClick = {
                if (task.running) onStopGeneration?.invoke() else send()
            }) {
                Icon(if (task.running) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                    if (task.running) "Stop generating" else "Send message")
            }
        }
    }
}
