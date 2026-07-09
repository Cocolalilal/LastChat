package me.rerere.rikkahub.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getEffectiveTTSProvider
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import me.rerere.rikkahub.ui.components.chat.ActivityState
import me.rerere.rikkahub.ui.components.chat.ActivityType
import me.rerere.rikkahub.ui.components.chat.categorizeToolName
import me.rerere.rikkahub.ui.components.chat.deriveActivityState
import me.rerere.rikkahub.ui.components.ai.MinimalChatInput
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSTTState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberChatInputState
import me.rerere.rikkahub.ui.hooks.rememberCustomSttState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.LastChatBlur
import me.rerere.rikkahub.ui.modifier.LocalLastChatBlur
import me.rerere.rikkahub.ui.modifier.blurredContainerColor
import me.rerere.rikkahub.ui.modifier.fadeEdges
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatBlurSource
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.copyMessageToClipboard
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The digital-assistant overlay. Backed by a REAL [me.rerere.rikkahub.service.ChatService]
 * conversation (via [AssistantOverlayVM]) so it supports full multi-turn back-and-forth,
 * tools, MCP, search and tool-approval — the same pipeline as the chat page.
 *
 * - the summon-time screen stays as a crisp, still backdrop (haze source),
 * - a vivid Material You glow "washes" over the screen on open and again whenever the model
 *   looks at the screen,
 * - the app's real [MinimalChatInput] floats at the bottom (search toggle, pickers, STT…),
 * - the exchange appears in a blurred, growing transcript panel with a compact activity pill
 *   and "Open in app" (which opens this exact chat).
 */
@Composable
fun AssistantOverlayScreen(
    viewModel: AssistantOverlayVM,
    onDismiss: () -> Unit,
    onOpenInApp: () -> Unit,
) {
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val mcpManager = koinInject<McpManager>()
    val toaster = LocalToaster.current
    val config = settings.assistantOverlayConfig
    val assistant = remember(settings) { settings.resolveAssistantOverlayAssistant() }
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()

    // ---- Enter / exit motion (premium fade + slide, native crossfade) --------------------
    // The backdrop screenshot is opaque immediately on enter (no doubling with the live app),
    // and it is NEVER translated (no "screenshot sliding down"). Only the glow/panel/input
    // fade + slide; on dismiss the whole overlay fades out to reveal the real screen.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    val dismiss = remember { Animatable(0f) }
    // Custom predictive-back: 0 = open, 1 = fully swiped. Follows the finger (shrink + drift
    // toward the swipe edge + fade), commits to a dismiss on release, springs back on cancel.
    val backProgress = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(0) }
    val isDismissing = remember { mutableStateOf(false) }
    fun dismissWithAnimation() {
        if (isDismissing.value) return
        isDismissing.value = true
        scope.launch {
            dismiss.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 260f))
            onDismiss()
        }
    }
    PredictiveBackHandler(enabled = !isDismissing.value) { backFlow ->
        try {
            backFlow.collect { event ->
                backSwipeEdge = event.swipeEdge
                backProgress.snapTo(event.progress)
            }
            dismissWithAnimation()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            backProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
        }
    }
    BackHandler(enabled = !isDismissing.value) { dismissWithAnimation() }

    // ---- Conversation (real ChatService chat, one per summon) ----------------------------
    val conversationId by viewModel.conversationId.collectAsStateWithLifecycle()

    val stt = rememberCustomSttState()
    val tts = rememberCustomTtsState()
    val sttState by stt.state.collectAsStateWithLifecycle()
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    val inputState = rememberChatInputState()

    // Same blur system as the rest of the app; the backdrop below is the haze source.
    val hazeState = rememberHazeState()
    val blur = remember(settings.displaySetting.enableBlurEffect, hazeState) {
        LastChatBlur(enabled = settings.displaySetting.enableBlurEffect, hazeState = hazeState)
    }

    val backdrop = remember { AssistScreenHolder.bitmapOrNull()?.asImageBitmap() }
    val screenReadSignal by AssistScreenHolder.screenReadSignal.collectAsStateWithLifecycle()

    // Surface ChatService errors as toasts.
    LaunchedEffect(Unit) {
        viewModel.errorFlow.collect { error ->
            toaster.show(error.message ?: "Error", type = ToastType.Error)
        }
    }

    fun doSend() {
        if (sttState.isRecording) stt.stop()
        if (inputState.isEmpty()) return
        haptics.perform(HapticPattern.Send)
        viewModel.send(inputState.getContents())
        inputState.clearInput()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) stt.start { inputState.setMessageText(it) }
    }
    LaunchedEffect(Unit) {
        if (config.autoStartStt) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) stt.start { inputState.setMessageText(it) }
            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val voiceActive = isSpeaking || sttState.isRecording

    CompositionLocalProvider(
        LocalSTTState provides stt,
        LocalLastChatBlur provides blur,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Whole-overlay fade + custom predictive-back transform. NO translationY, so
                    // the backdrop never slides.
                    val bp = backProgress.value
                    val scale = androidx.compose.ui.util.lerp(1f, 0.85f, bp)
                    scaleX = scale
                    scaleY = scale
                    // Pivot + drift toward the swipe edge (EDGE_RIGHT == 1).
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        if (backSwipeEdge == 1) 1f else 0f, 0.5f,
                    )
                    translationX = (if (backSwipeEdge == 1) -1f else 1f) * bp * size.width * 0.05f
                    alpha = ((1f - dismiss.value) * (1f - bp * 0.2f)).coerceIn(0f, 1f)
                }
        ) {
            // Backdrop = haze source: crisp still capture of the summoning screen + glow. NOT
            // dimmed — opening the assistant should not darken the screen behind it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .lastChatBlurSource()
            ) {
                if (backdrop != null) {
                    Image(
                        bitmap = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Transparent tap-to-dismiss layer (no scrim).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { dismissWithAnimation() },
                )
                SilkGlowLayer(
                    active = voiceActive,
                    waveSignal = screenReadSignal,
                    originX = config.waveOriginX,
                    originY = config.waveOriginY,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Foreground: transcript panel + input. Bottom-anchored and wrapping its content, so
            // when the keyboard opens with the panel fully expanded the input still rises to the
            // keyboard and the panel simply overflows off the top of the screen.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // unbounded so the column can be TALLER than the screen: the panel overflows
                    // off the top and the input bar stays pinned above the keyboard.
                    .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                    .graphicsLayer {
                        alpha = appear.value
                        translationY = (1f - appear.value) * 48.dp.toPx() +
                            dismiss.value * 80.dp.toPx()
                    },
            ) {
                val convId = conversationId
                if (convId != null) {
                    val conversation by viewModel.conversationFlow(convId)
                        .collectAsStateWithLifecycle()
                    val generationJob by viewModel.generationJobFlow(convId)
                        .collectAsStateWithLifecycle(initialValue = null)
                    val isGenerating = generationJob != null

                    // Read the reply aloud once generation settles.
                    var wasGenerating by remember { mutableStateOf(false) }
                    LaunchedEffect(isGenerating) {
                        if (wasGenerating && !isGenerating && config.autoReadReply) {
                            val text = conversation.currentMessages
                                .lastOrNull { it.role == MessageRole.ASSISTANT }
                                ?.toContentText().orEmpty()
                            if (text.isNotBlank()) {
                                tts.speak(text, overrideSetting = settings.getEffectiveTTSProvider(assistant))
                            }
                        }
                        wasGenerating = isGenerating
                    }

                    // Auto-send when transcription settles.
                    var wasRecording by remember { mutableStateOf(false) }
                    LaunchedEffect(sttState.status) {
                        if (sttState.status == ASRStatus.Listening ||
                            sttState.status == ASRStatus.Stopping
                        ) {
                            wasRecording = true
                        } else if (sttState.status == ASRStatus.Idle && wasRecording) {
                            wasRecording = false
                            if (config.autoSendOnSttFinish && !inputState.isEmpty() && !isGenerating) {
                                doSend()
                            }
                        }
                    }

                    LaunchedEffect(isGenerating) { inputState.loading = isGenerating }

                    val transcript = remember(conversation.currentMessages) {
                        conversation.currentMessages.filter {
                            (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) &&
                                it.hasDisplayableContent()
                        }
                    }
                    val lastAssistant = transcript.lastOrNull { it.role == MessageRole.ASSISTANT }
                    val activityState = remember(lastAssistant, isGenerating) {
                        when {
                            lastAssistant != null -> deriveActivityState(
                                parts = lastAssistant.parts,
                                annotations = lastAssistant.annotations,
                                loading = isGenerating,
                            )
                            isGenerating -> ActivityState.Waiting
                            else -> ActivityState.Hidden
                        }
                    }

                    AnimatedVisibility(
                        visible = transcript.isNotEmpty(),
                        enter = slideInVertically(
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                            initialOffsetY = { it / 2 },
                        ) + fadeIn(tween(360, easing = FastOutSlowInEasing)),
                        exit = slideOutVertically(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                            targetOffsetY = { it / 2 },
                        ) + fadeOut(tween(240, easing = FastOutSlowInEasing)),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        TranscriptPanel(
                            assistantAvatar = { modifier ->
                                UIAvatar(name = assistant.name, value = assistant.avatar, modifier = modifier)
                            },
                            messages = transcript,
                            activityState = activityState,
                            isGenerating = isGenerating,
                            isSpeaking = isSpeaking,
                            onOpenInApp = onOpenInApp,
                            onCopy = { message -> context.copyMessageToClipboard(message) },
                            onRegenerate = { message -> viewModel.regenerate(message) },
                            onToggleTts = { message ->
                                if (isSpeaking) {
                                    tts.stop()
                                } else {
                                    tts.speak(
                                        message.toContentText(),
                                        overrideSetting = settings.getEffectiveTTSProvider(assistant),
                                    )
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    val enableSearch = assistant.searchMode is AssistantSearchMode.Provider
                    MinimalChatInput(
                        modifier = Modifier.fillMaxWidth(),
                        state = inputState,
                        conversation = conversation,
                        settings = settings,
                        mcpManager = mcpManager,
                        enableSearch = enableSearch,
                        onToggleSearch = {
                            if (enableSearch) {
                                viewModel.updateOverlayAssistant { it.copy(searchMode = AssistantSearchMode.Off) }
                            } else if (settings.searchServices.isNotEmpty()) {
                                val idx = settings.searchServiceSelected
                                    .coerceIn(0, settings.searchServices.lastIndex)
                                viewModel.updateOverlayAssistant {
                                    it.copy(searchMode = AssistantSearchMode.Provider(idx))
                                }
                            }
                        },
                        onUpdateChatModel = { model ->
                            viewModel.updateOverlayAssistant { it.copy(chatModelId = model.id) }
                        },
                        onUpdateAssistant = { updated ->
                            viewModel.updateOverlayAssistant { updated }
                        },
                        onUpdateConversation = { viewModel.saveConversation(it) },
                        onToolApproval = { toolCallId, approved, reason, answer ->
                            viewModel.handleToolApproval(toolCallId, approved, reason, answer)
                        },
                        onUpdateSearchService = { index ->
                            viewModel.updateOverlayAssistant {
                                it.copy(searchMode = AssistantSearchMode.Provider(index))
                            }
                        },
                        onClearContext = {},
                        onCancelClick = { generationJob?.cancel() },
                        onSendClick = { doSend() },
                        onLongSendClick = { doSend() },
                        bottomPadding = 16.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptPanel(
    assistantAvatar: @Composable (Modifier) -> Unit,
    messages: List<UIMessage>,
    activityState: ActivityState,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    onOpenInApp: () -> Unit,
    onCopy: (UIMessage) -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onToggleTts: (UIMessage) -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val minHeight = 150f
    val maxHeight = configuration.screenHeightDp * 0.66f
    val dragFloor = remember { Animatable(minHeight) }
    val scroll = rememberScrollState()

    val blur = LocalLastChatBlur.current
    val panelShape = RoundedCornerShape(28.dp)
    val containerColor = if (blur.enabled && blur.hazeState != null) {
        blurredContainerColor(MaterialTheme.colorScheme.surfaceContainerLow)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)
    }
    val contentColor = MaterialTheme.colorScheme.onSurface

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }
    val lastAssistantId = remember(messages) {
        messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
    }
    var expandedIds by remember { mutableStateOf(emptySet<kotlin.uuid.Uuid>()) }

    val transcriptColumn: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(headerHeightDp))
            messages.forEach { message ->
                val isAssistant = message.role == MessageRole.ASSISTANT
                val actionsVisible = isAssistant && !isGenerating &&
                    (message.id == lastAssistantId || message.id in expandedIds)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = if (isAssistant) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            expandedIds = if (message.id in expandedIds) {
                                expandedIds - message.id
                            } else {
                                expandedIds + message.id
                            }
                        }
                    } else Modifier,
                ) {
                    MessageRowContent(message = message, contentColor = contentColor)
                    if (isAssistant) {
                        AnimatedVisibility(visible = actionsVisible) {
                            AssistantActionsRow(
                                isSpeaking = isSpeaking,
                                showRegenerate = message.id == lastAssistantId,
                                onCopy = { onCopy(message) },
                                onRegenerate = { onRegenerate(message) },
                                onToggleTts = { onToggleTts(message) },
                            )
                        }
                    }
                }
            }
        }
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = panelShape,
        border = BorderStroke(1.dp, LightHairline()),
        modifier = Modifier
            .fillMaxWidth()
            .lastChatBlurEffect(containerColor = containerColor, shape = panelShape),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Scrolling transcript with a chat-style top fade — as text scrolls up under the
            // header it fades to TRANSPARENT (revealing the panel behind, not a solid colour or
            // black), exactly like the chat page's edge fade.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dragFloor.value.dp, max = maxHeight.dp)
                    .fadeEdges(
                        fadeTop = true,
                        fadeBottom = false,
                        fadeHeight = headerHeightPx.toFloat(),
                    )
                    .verticalScroll(scroll),
            ) {
                transcriptColumn()
            }

            // Floating header (drag handle + avatar + compact activity pill + open-in-app).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    val deltaDp = dragAmount / density.density
                                    scope.launch {
                                        dragFloor.snapTo(
                                            (dragFloor.value - deltaDp).coerceIn(minHeight, maxHeight)
                                        )
                                    }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        val threshold = minHeight + (maxHeight - minHeight) * 0.3f
                                        val target = if (dragFloor.value > threshold) maxHeight else minHeight
                                        dragFloor.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 180f))
                                    }
                                },
                            )
                        }
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(contentColor.copy(alpha = 0.35f), CircleShape),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                ) {
                    assistantAvatar(Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    // Weighted box fills the leftover width so the button sits at the far right.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        CompactActivityPill(state = activityState)
                    }
                    Spacer(Modifier.width(8.dp))
                    // "Open in app" — same size as the avatar, right-aligned, level with it.
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onOpenInApp() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.assistant_overlay_open_in_app),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Keep the transcript pinned to the latest text while generating.
    LaunchedEffect(messages, isGenerating) {
        scroll.scrollTo(scroll.maxValue)
    }
}

// Only messages with actual text are shown: tool-call-only assistant turns (which would
// render as "…") and the injected screenshot USER image message are hidden — tool activity is
// conveyed by the header pill instead.
private fun UIMessage.hasDisplayableContent(): Boolean = toContentText().isNotBlank()

@Composable
private fun MessageRowContent(message: UIMessage, contentColor: Color) {
    val text = message.toContentText()
    if (message.role == MessageRole.USER) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    } else {
        SelectionContainer {
            MarkdownBlock(
                content = text.ifBlank { "…" },
                style = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                streamingTextReveal = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Minimal action bar under assistant messages — copy, regenerate, TTS (chat-style icons). */
@Composable
private fun AssistantActionsRow(
    isSpeaking: Boolean,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleTts: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(Icons.Rounded.ContentCopy, stringResource(R.string.copy), onCopy)
        if (showRegenerate) {
            ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.regenerate), onRegenerate)
        }
        ActionIcon(
            if (isSpeaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
            stringResource(R.string.tts),
            onToggleTts,
        )
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(7.dp)
            .size(16.dp),
    )
}

/**
 * A deliberately COMPACT activity pill — icon + short label — that stays small in every
 * state (thinking / reasoning / tool / replying), unlike the chat pill which can expand to
 * a reasoning/timeline preview. Morphs smoothly between states.
 */
@Composable
private fun CompactActivityPill(state: ActivityState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state !is ActivityState.Hidden,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(30.dp),
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(90)) + scaleOut(targetScale = 0.92f))
                },
                contentKey = { it.compactKey() },
                label = "compact_pill",
            ) { target ->
                val (icon, label, loading) = target.compactContent()
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (loading) Modifier.shimmer(isLoading = true) else Modifier,
                    )
                }
            }
        }
    }
}

private fun ActivityState.compactKey(): Any = when (this) {
    is ActivityState.Waiting -> "waiting"
    is ActivityState.Ocr -> "ocr"
    is ActivityState.Reasoning -> "reasoning"
    is ActivityState.ToolUse -> "tool_${categorizeToolName(toolName)}"
    is ActivityState.LoadingModel -> "loading"
    is ActivityState.Replying -> "replying"
    is ActivityState.CompletedSingle -> "done_$type"
    is ActivityState.CompletedMultiple -> "done_multi"
    is ActivityState.Hidden -> "hidden"
}

private data class CompactContent(
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val label: String,
    val loading: Boolean,
)

private fun ActivityState.compactContent(): CompactContent = when (this) {
    is ActivityState.Waiting -> CompactContent(null, "Thinking", true)
    is ActivityState.Ocr -> CompactContent(Icons.Rounded.Visibility, "Reading", true)
    is ActivityState.Reasoning -> CompactContent(Icons.Rounded.Lightbulb, "Reasoning", true)
    is ActivityState.ToolUse -> {
        if (toolName == "look_at_screen") CompactContent(Icons.Rounded.Visibility, "Looking", true)
        else toolCategoryContent(categorizeToolName(toolName), live = true)
    }
    is ActivityState.LoadingModel -> CompactContent(Icons.Rounded.Memory, "Loading", true)
    is ActivityState.Replying -> CompactContent(null, "Replying", true)
    is ActivityState.CompletedSingle -> toolCategoryContent(type, live = false)
    is ActivityState.CompletedMultiple -> CompactContent(Icons.Rounded.Build, "Done", false)
    is ActivityState.Hidden -> CompactContent(null, "", false)
}

private fun toolCategoryContent(type: ActivityType, live: Boolean): CompactContent = when (type) {
    ActivityType.REASONING -> CompactContent(Icons.Rounded.Lightbulb, if (live) "Reasoning" else "Reasoned", live)
    ActivityType.OCR -> CompactContent(Icons.Rounded.Visibility, if (live) "Reading" else "Read", live)
    ActivityType.SEARCH -> CompactContent(Icons.Rounded.Public, if (live) "Searching" else "Searched", live)
    ActivityType.MEMORY_RECALL -> CompactContent(Icons.Rounded.Memory, if (live) "Recalling" else "Recalled", live)
    ActivityType.PYTHON -> CompactContent(Icons.Rounded.Terminal, if (live) "Running" else "Ran code", live)
    ActivityType.WORKSPACE -> CompactContent(Icons.Rounded.Computer, if (live) "Working" else "Worked", live)
    ActivityType.SKILL -> CompactContent(Icons.Rounded.Category, if (live) "Skills" else "Skills", live)
    ActivityType.MCP -> CompactContent(Icons.Rounded.Build, if (live) "Tool" else "Tool", live)
    ActivityType.LOADING_MODEL -> CompactContent(Icons.Rounded.Memory, "Loading", live)
    ActivityType.TOOL_OTHER -> CompactContent(Icons.Rounded.Build, if (live) "Using tool" else "Used tool", live)
}

/**
 * The vivid glow + silk dots, plus a clearly visible "wave" of light that washes across the
 * screen on open and again every time [waveSignal] changes (i.e. the model looked at the
 * screen).
 */
@Composable
private fun SilkGlowLayer(
    active: Boolean,
    waveSignal: Long,
    originX: Float,
    originY: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "silkGlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowPhase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowBreath",
    )
    val boost by animateFloatAsState(
        targetValue = if (active) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "glowBoost",
    )

    // Entrance reveal (runs once) + morphs into the glow.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }

    // A bright travelling "wave" band: replays on every screen-read signal.
    val wave = remember { Animatable(1f) }
    LaunchedEffect(waveSignal) {
        if (waveSignal > 0L) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
        }
    }

    val scheme = MaterialTheme.colorScheme
    val edgeColors = remember(scheme) {
        listOf(
            scheme.primary.vivid(),
            scheme.tertiary.vivid(),
            scheme.secondary.vivid(),
            scheme.primary.vivid(),
            scheme.tertiary.vivid(),
            scheme.secondary.vivid(),
        )
    }
    val waveColor = remember(scheme) { scheme.primary.vivid() }
    val density = LocalDensity.current
    val dotSpacingPx = with(density) { 22.dp.toPx() }
    val dotRadiusPx = with(density) { 1.5.dp.toPx() }
    val dotDriftPx = with(density) { 2.dp.toPx() }

    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val p = entrance.value
                if (p < 1f) {
                    // Soft, rounded reveal sweeping out from the chosen origin.
                    val maxR = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 1.25f
                    val r = (p * maxR).coerceAtLeast(1f)
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to Color.Black,
                            0.62f to Color.Black,
                            0.9f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                            center = Offset(size.width * originX, size.height * originY),
                            radius = r,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        // Edge glow.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { }
                .drawBehind {
                    // Steady strength matches the level during the wave (no end-of-wave flick):
                    // the reveal is purely geometric (DstIn mask above), not an alpha ramp.
                    val alpha = (0.62f * breath * boost).coerceIn(0f, 1f)
                    glowBlobs(size.width, size.height, phase, edgeColors).forEach { blob ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    blob.color.copy(alpha = alpha),
                                    blob.color.copy(alpha = alpha * 0.35f),
                                    Color.Transparent,
                                ),
                                center = blob.center,
                                radius = blob.radius,
                            ),
                            radius = blob.radius,
                            center = blob.center,
                        )
                    }
                },
        )

        // Silk dot grid, masked by the glow field itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val t = phase * (2.0 * PI).toFloat()
                    val blobs = glowBlobs(w, h, phase, edgeColors)
                    val dotStrength = (0.62f * breath * boost).coerceIn(0f, 1f)

                    var y = dotSpacingPx / 2f
                    while (y < h) {
                        var x = dotSpacingPx / 2f
                        while (x < w) {
                            var weight = 0f; var rSum = 0f; var gSum = 0f; var bSum = 0f
                            for (blob in blobs) {
                                val dx = x - blob.center.x
                                val dy = y - blob.center.y
                                val dist = sqrt(dx * dx + dy * dy)
                                val falloff = (1f - dist / blob.radius).coerceAtLeast(0f)
                                if (falloff > 0f) {
                                    val wgt = falloff * falloff
                                    weight += wgt
                                    rSum += blob.color.red * wgt
                                    gSum += blob.color.green * wgt
                                    bSum += blob.color.blue * wgt
                                }
                            }
                            if (weight > 0.02f) {
                                val intensity = weight.coerceAtMost(1f)
                                val dotColor = Color(
                                    (rSum / weight).coerceIn(0f, 1f),
                                    (gSum / weight).coerceIn(0f, 1f),
                                    (bSum / weight).coerceIn(0f, 1f),
                                )
                                val driftX = dotDriftPx * sin(t + y * 0.006f + x * 0.003f)
                                val driftY = dotDriftPx * cos(t * 0.8f + x * 0.005f + y * 0.002f)
                                val shimmer = 0.9f + 0.1f * sin(t * 1.3f + x * 0.01f + y * 0.008f)
                                drawCircle(
                                    color = dotColor.copy(
                                        alpha = (dotStrength * intensity * shimmer).coerceIn(0f, 1f)
                                    ),
                                    radius = dotRadiusPx,
                                    center = Offset(x + driftX, y + driftY),
                                )
                            }
                            x += dotSpacingPx
                        }
                        y += dotSpacingPx
                    }
                },
        )

        // Bright travelling wave of light — the visible "wash" on open and on each look.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Which progress is currently sweeping: an active look-pulse wins, else the
                    // one-time entrance.
                    val p = when {
                        wave.value < 1f -> wave.value
                        entrance.value < 1f -> entrance.value
                        else -> return@drawBehind
                    }
                    // Bell curve so the wash brightens then fades as it expands.
                    val bell = run { val d = (p - 0.5f) / 0.5f; (1f - d * d).coerceIn(0f, 1f) }
                    // Expands outward FROM the chosen origin (e.g. the side button).
                    val ox = size.width * originX
                    val oy = size.height * originY
                    val maxR = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 1.2f
                    val radius = (p * maxR).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                waveColor.copy(alpha = 0.42f * bell),
                                waveColor.copy(alpha = 0.14f * bell),
                                Color.Transparent,
                            ),
                            center = Offset(ox, oy),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(ox, oy),
                    )
                },
        )
    }
}

private fun Color.vivid(satMul: Float = 1.5f, minSat: Float = 0.6f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] = maxOf(hsv[1] * satMul, minSat).coerceIn(0f, 1f)
    hsv[2] = hsv[2].coerceIn(0.5f, 0.92f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private class GlowBlob(val center: Offset, val radius: Float, val color: Color)

private fun glowBlobs(w: Float, h: Float, phase: Float, colors: List<Color>): List<GlowBlob> {
    val twoPi = (2.0 * PI).toFloat()
    val r = 0.30f * h
    return listOf(
        GlowBlob(Offset(-0.12f * w, h * (0.24f + 0.12f * sin(twoPi * phase))), r, colors[0]),
        GlowBlob(Offset(-0.12f * w, h * (0.74f + 0.10f * sin(twoPi * phase + 2.1f))), r, colors[1]),
        GlowBlob(Offset(w * (0.5f + 0.28f * sin(twoPi * phase + 1.2f)), -0.12f * h), r * 0.9f, colors[2]),
        GlowBlob(Offset(1.12f * w, h * (0.30f + 0.12f * sin(twoPi * phase + 3.5f))), r, colors[3]),
        GlowBlob(Offset(1.12f * w, h * (0.78f + 0.10f * sin(twoPi * phase + 4.6f))), r, colors[4]),
        GlowBlob(Offset(w * (0.46f + 0.28f * sin(twoPi * phase + 5.4f)), 1.12f * h), r * 1.05f, colors[5]),
    )
}

/** Subtle light hairline outline used across the assistant surfaces. */
@Composable
private fun LightHairline(): Color =
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
