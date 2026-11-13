package me.rerere.rikkahub.phoneuse

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
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
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAction
import me.rerere.rikkahub.ui.components.chat.LastChatComposerActionButton
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
import kotlin.math.roundToInt

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
    val popupTask = if (!current.running && generation != null) {
        current.copy(running = true, action = "reply")
    } else current
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
                            manager.resumeReply(current.conversationId) {
                                chatService.stopGeneration(current.conversationId)
                            }
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
        if (reduceMotion) snap() else spring(
            dampingRatio = PHONE_MOTION_DAMPING,
            stiffness = PHONE_MOTION_STIFFNESS,
            // The default 0.01 fraction snaps the final few pixels of a 300dp surface.
            visibilityThreshold = 0.0005f,
        ),
        label = "phonePopupExpansion",
    )
    val expansion = progress.coerceIn(0f, 1f)
    val expandedWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 32)
        .coerceIn(104, 336).dp
    // Individual tool results keep their compact pose. Only an actual answer or approval
    // opens the conversation, and an empty answer reveals just the composer.
    LaunchedEffect(needsReply, task.running, task.awaitingReply) {
        if (needsReply || !task.running || task.awaitingReply) expanded = true
    }
    Surface(
        shape = RoundedCornerShape((28f + 8f * expansion).dp),
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
                    // Header contents keep their final measure through the morph. The avatar
                    // and stop/dismiss control stay anchored to the two moving capsule ends.
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 56.dp)
                        .clipToBounds()) {
                        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(expandedWidth - 112.dp)
                            .graphicsLayer { alpha = expansion },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.weight(1f)) { PhoneUseActivityPill(task, needsReply) }
                            if (task.running) IconButton(
                                modifier = Modifier.size(36.dp),
                                enabled = expanded,
                                onClick = { haptics.perform(HapticPattern.Tick); manager.togglePause() },
                            ) {
                                Icon(if (task.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    if (task.paused) "Resume phone use" else "Pause phone use")
                            }
                            if (onOpen != null) IconButton(modifier = Modifier.size(36.dp),
                                enabled = expanded, onClick = onOpen) {
                                Icon(Icons.Rounded.OpenInFull, "Open conversation")
                            }
                        }
                    }
                    if (!task.systemAssistant || task.running) {
                        IconButton(
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(40.dp),
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                if (task.running && task.action == "reply" && onStopGeneration != null) {
                                    onStopGeneration()
                                } else manager.stop()
                            },
                        ) {
                            Crossfade(task.running,
                                animationSpec = if (reduceMotion) snap() else tween(180),
                                label = "phoneStopOrDismiss") { running ->
                                Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.Close,
                                    if (running) "Stop phone use" else "Dismiss")
                            }
                        }
                    }
                }
            },
            body = {
                // Keep the same measured body at both endpoints; dropping Markdown at the
                // last spring frame causes a visible tail hitch on collapse. Empty working
                // controls have no reserved transcript area or unusable message field.
                Column(Modifier.fillMaxWidth()
                    .animateContentSize(if (reduceMotion) snap() else spring(
                        dampingRatio = PHONE_MOTION_DAMPING, stiffness = PHONE_MOTION_STIFFNESS,
                        visibilityThreshold = IntSize(1, 1)))
                    .graphicsLayer { alpha = expansion }) {
                    if (task.error != null || reply.isNotBlank()) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)) {
                            task.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium) }
                            if (reply.isNotBlank()) SelectionContainer {
                                MarkdownBlock(reply, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (onSendReply != null && !task.running) PhoneUseReplyComposer(
                        task, manager, onReplyFocus, onSendReply,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 8.dp),
                        onStopGeneration = onStopGeneration, nested = true, haptics = haptics,
                    )
                }
            },
        )
    }
}

/** Shared status treatment for both the system assistant and the regular phone popup. */
@Composable
internal fun PhoneUseActivityPill(
    task: PhoneUseTask,
    needsReply: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val status = if (needsReply) "Reply needed" else phoneTaskLabel(task)
    val reduceMotion = rememberSystemMotionPolicy().reduceMotion
    val opacity by androidx.compose.animation.core.animateFloatAsState(
        if (status.isBlank()) 0f else 1f,
        if (reduceMotion) snap() else tween(180),
        label = "phoneActivityVisibility",
    )
    Surface(
        modifier = modifier.height(30.dp)
            .animateContentSize(if (reduceMotion) snap() else spring(
                dampingRatio = 1f, stiffness = 500f, visibilityThreshold = IntSize(1, 1)))
            .graphicsLayer { alpha = opacity },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Crossfade(status,
            animationSpec = if (reduceMotion) snap() else tween(180),
            label = "phoneActivityStatus") { label ->
            Box(Modifier.height(30.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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
        val expanded = expandedWidth.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
        val compact = 104.dp.roundToPx().coerceAtMost(expanded)
        val width = androidx.compose.ui.util.lerp(compact, expanded, expansion)
        val headerPlaceable = measurables[0].measure(
            constraints.copy(minWidth = width, maxWidth = width, minHeight = 0))
        val bodyPlaceable = measurables[1].measure(
            constraints.copy(minWidth = expanded, maxWidth = expanded, minHeight = 0,
                maxHeight = (constraints.maxHeight - headerPlaceable.height).coerceAtLeast(0)))
        val revealedBody = (bodyPlaceable.height * expansion).roundToInt()
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
    haptics: PremiumHaptics = rememberPremiumHaptics(),
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
        haptics.perform(HapticPattern.Send)
        onSend(message)
    }
    // A 36dp card with 12dp inset gives a concentric 24dp, single-line pill composer.
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(if (nested) 24.dp else 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = input,
                onValueChange = { input = it; manager.updateReplyDraft(task.conversationId, it) },
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
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
            val enabled = if (task.running) onStopGeneration != null else input.isNotBlank()
            val action = if (task.running) LastChatComposerAction.Loading else LastChatComposerAction.Send
            LastChatComposerActionButton(
                action = action,
                onClick = {
                    if (enabled) {
                        if (task.running) {
                            haptics.perform(HapticPattern.Tick)
                            onStopGeneration?.invoke()
                        } else send()
                    }
                },
                modifier = Modifier.size(36.dp)
                    .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                    .semantics {
                        contentDescription = if (task.running) "Stop generating" else "Send message"
                        if (!enabled) disabled()
                    },
                containerColorOverride = if (enabled) null else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) { current ->
                Icon(if (current == LastChatComposerAction.Loading) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                    contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (current == LastChatComposerAction.Loading) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
