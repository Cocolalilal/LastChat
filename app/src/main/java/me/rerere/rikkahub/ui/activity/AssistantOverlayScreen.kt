package me.rerere.rikkahub.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.PredictiveBackHandler
import me.rerere.rikkahub.service.hasPendingToolApprovals
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
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.snap
import me.rerere.rikkahub.phoneuse.PHONE_MOTION_STIFFNESS
import me.rerere.rikkahub.phoneuse.PHONE_MOTION_DAMPING
import me.rerere.rikkahub.phoneuse.PhoneOverlayDragCallbacks
import me.rerere.rikkahub.phoneuse.phoneOverlayDrag
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SmartToy
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import me.rerere.rikkahub.ui.pages.chat.isCharacterIntroMessage
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
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.toLocalInferenceUserMessage
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
 * - the underlying app stays visible through a fixed transparent window,
 * - a vivid Material You glow "washes" over the screen on open and again whenever the model
 *   looks at the screen,
 * - the app's real [MinimalChatInput] floats at the bottom (search toggle, pickers, STT…),
 * - the exchange appears in a blurred, growing transcript panel with a compact activity pill
 *   and "Open in app" (which opens this exact chat).
 */
data class AssistantSurfaceMotion(
    val alpha: Float,
    val scale: Float,
    val pivotX: Float,
    val pivotY: Float,
    val horizontalDrift: Float,
    val liftDp: Float,
)

@Composable
fun AssistantOverlayScreen(
    viewModel: AssistantOverlayVM,
    onDismiss: () -> Unit,
    onOpenInApp: () -> Unit,
    onPhoneUseYield: () -> Unit = {},
    autoStartListening: Boolean = true,
    nativeBlurActive: Boolean = false,
    floatingContent: Boolean = false,
    onSurfaceOpacity: (Float) -> Unit = {},
    inputState: me.rerere.rikkahub.ui.hooks.ChatInputState = rememberChatInputState(),
    persistentPhoneMode: Boolean? = null,
    onSurfaceMotion: (AssistantSurfaceMotion) -> Unit = {},
    onGlowActive: (Boolean) -> Unit = {},
) {
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val phoneManager = koinInject<me.rerere.rikkahub.phoneuse.PhoneUseManager>()
    val phoneTask by phoneManager.session.collectAsStateWithLifecycle()
    val lastChatBehind by phoneManager.otherAppUiVisible.collectAsStateWithLifecycle()
    val mcpManager = koinInject<McpManager>()
    val toaster = LocalToaster.current
    val config = settings.assistantOverlayConfig
    val assistant = settings.assistants.firstOrNull { it.id == viewModel.overlayAssistantId }
        ?: settings.resolveAssistantOverlayAssistant()
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()

    val reduceMotion = me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy().reduceMotion
    val opening = remember { Animatable(if (reduceMotion || phoneManager.assistantDocked.value != null ||
        phoneManager.assistantReturning.value) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        opening.animateTo(1f, if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS))
    }
    // Content remains visible during the short spring lift; the native window never resizes.
    // Animate the assistant surface only; another app's pixels are never copied into this UI.
    val dismiss = remember { Animatable(0f) }
    // Custom predictive-back: 0 = open, 1 = fully swiped. Follows the finger (shrink + drift
    // toward the swipe edge + fade), commits to a dismiss on release, springs back on cancel.
    val backProgress = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(0) }
    val opacityCallback by androidx.compose.runtime.rememberUpdatedState(onSurfaceOpacity)
    val motionCallback by androidx.compose.runtime.rememberUpdatedState(onSurfaceMotion)
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow {
            val back = backProgress.value
            AssistantSurfaceMotion(
                alpha = (opening.value * (1f - dismiss.value) * (1f - back * 0.2f)).coerceIn(0f, 1f),
                scale = (0.97f + 0.03f * opening.value) * (1f - back * 0.15f),
                pivotX = if (back == 0f) 0.5f else if (backSwipeEdge == 1) 1f else 0f,
                pivotY = if (back == 0f) 1f else 0.5f,
                horizontalDrift = (if (backSwipeEdge == 1) -1f else 1f) * back * 0.05f,
                liftDp = (1f - opening.value) * 8f + dismiss.value * 24f,
            )
        }.collect { motion ->
            opacityCallback(motion.alpha)
            motionCallback(motion)
        }
    }
    val isDismissing = remember { mutableStateOf(false) }
    fun dismissWithAnimation() {
        if (isDismissing.value) return
        isDismissing.value = true
        scope.launch {
            dismiss.animateTo(1f, if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS))
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
            backProgress.animateTo(0f, spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS))
        }
    }

    // ---- Conversation (real ChatService chat, one per summon) ----------------------------
    val conversationId by viewModel.conversationId.collectAsStateWithLifecycle()
    val currentPhoneTask = phoneTask?.takeIf {
        it.conversationId == conversationId || (conversationId == null && it.systemAssistant && it.assistantId == assistant.id)
    }
    var returnPose by remember {
        val docked = phoneManager.assistantDocked.value
        mutableStateOf(docked != null && (conversationId == null || docked == conversationId))
    }
    LaunchedEffect(returnPose, conversationId) {
        if (returnPose && conversationId != null) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            returnPose = false
            phoneManager.assistantDocked.value = null
        }
    }
    val compactPhone = if (persistentPhoneMode != null) {
        persistentPhoneMode || currentPhoneTask?.let {
            it.running && !it.paused && !it.awaitingReply && it.action != "reply"
        } == true
    } else returnPose || currentPhoneTask?.let {
        it.running && !it.paused && !it.awaitingReply && it.action != "reply"
    } == true
    var panelCompactSettled by remember { mutableStateOf(false) }
    var inputHidden by remember { mutableStateOf(false) }
    LaunchedEffect(currentPhoneTask?.conversationId, currentPhoneTask?.running, persistentPhoneMode) {
        if (currentPhoneTask?.running == false && persistentPhoneMode != true) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            phoneManager.markAssistantPresented(currentPhoneTask.conversationId)
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val softwareKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(compactPhone) {
        if (compactPhone) { focusManager.clearFocus(); softwareKeyboard?.hide() }
    }
    val stt = rememberCustomSttState()
    val tts = rememberCustomTtsState()
    val sttState by stt.state.collectAsStateWithLifecycle()
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    LaunchedEffect(conversationId) {
        conversationId?.let { id ->
            val draft = phoneManager.takeReplyDraft(id)
            if (draft.isNotBlank()) inputState.setMessageText(draft)
        }
    }

    val blur = remember(nativeBlurActive) {
        LastChatBlur(enabled = nativeBlurActive, windowBlur = nativeBlurActive)
    }

    val screenReadSignal by AssistScreenHolder.screenReadSignal.collectAsStateWithLifecycle()

    // Surface ChatService errors as toasts.
    LaunchedEffect(Unit) {
        viewModel.errorFlow.collect { error ->
            toaster.show(error.toLocalInferenceUserMessage(context) ?: "Error", type = ToastType.Error)
        }
    }

    var acceptSttTranscript by remember { mutableStateOf(true) }
    var utteranceSent by remember { mutableStateOf(false) }
    var sttSessionEpoch by remember { mutableIntStateOf(inputState.sttCommitEpoch) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, stt) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && stt.state.value.isRecording) {
                // Retaining the scene must not retain a hidden microphone or send its final
                // transcript when the user returns from another app or a picker.
                utteranceSent = true
                stt.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun applySttTranscript(text: String) {
        if (acceptSttTranscript && inputState.sttCommitEpoch == sttSessionEpoch) {
            inputState.setMessageText(text)
        }
    }

    fun doSend() {
        acceptSttTranscript = false
        utteranceSent = true
        if (sttState.isRecording) stt.stop()
        if (inputState.isEmpty()) return
        haptics.perform(HapticPattern.Send)
        viewModel.send(inputState.getContents())
        inputState.clearInput()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) stt.start(::applySttTranscript)
    }
    LaunchedEffect(Unit) {
        if (autoStartListening && config.autoStartStt) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) stt.start(::applySttTranscript)
            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(compactPhone, panelCompactSettled, inputHidden, conversationId) {
        if (compactPhone && !returnPose && panelCompactSettled && inputHidden) {
            if (sttState.isRecording) stt.stop()
            tts.stop()
            phoneManager.assistantDocked.value = conversationId
            onPhoneUseYield()
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
                .then(if (floatingContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .graphicsLayer {
                    // Fade and transform the floating surface during predictive back.
                    val bp = backProgress.value
                    val scale = androidx.compose.ui.util.lerp(1f, 0.85f, bp)
                    scaleX = if (floatingContent) 1f else scale
                    scaleY = if (floatingContent) 1f else scale
                    // Pivot + drift toward the swipe edge (EDGE_RIGHT == 1).
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        if (backSwipeEdge == 1) 1f else 0f, 0.5f,
                    )
                    translationX = if (floatingContent) 0f else (if (backSwipeEdge == 1) -1f else 1f) * bp * size.width * 0.05f
                    alpha = if (floatingContent) 1f else ((1f - dismiss.value) * (1f - bp * 0.2f)).coerceIn(0f, 1f)
                }
        ) {
            Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { dismissWithAnimation() })
            if (!floatingContent) SilkGlowLayer(
                active = voiceActive, waveSignal = screenReadSignal,
                originX = config.waveOriginX, originY = config.waveOriginY,
                modifier = Modifier.matchParentSize(),
            )

            // Foreground: transcript panel + input. Bottom-anchored and wrapping its content, so
            // when the keyboard opens with the panel fully expanded the input still rises to the
            // keyboard and the panel simply overflows off the top of the screen.
            // Keep finite screen constraints: MinimalChatInput scrolls internally. The same
            // panel/avatar slot shrinks into the dock before the activity yields to the target.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .then(if (floatingContent) Modifier else Modifier.imePadding().navigationBarsPadding().padding(bottom = 24.dp))
                    .graphicsLayer {
                        translationY = if (floatingContent) 0f else (1f - opening.value) * 8.dp.toPx() + dismiss.value * 24.dp.toPx()
                    },
                verticalArrangement = Arrangement.Bottom,
            ) {
                val convId = conversationId
                if (convId != null) {
                    val conversation by viewModel.conversationFlow(convId)
                        .collectAsStateWithLifecycle()
                    val generationJob by viewModel.generationJobFlow(convId)
                        .collectAsStateWithLifecycle(initialValue = null)
                    val isGenerating = generationJob != null
                    androidx.compose.runtime.SideEffect { onGlowActive(voiceActive || isGenerating || compactPhone) }

                    // Read the reply aloud once generation settles.
                    var wasGenerating by remember { mutableStateOf(false) }
                    val spokenReply = conversation.currentMessages.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
                    LaunchedEffect(isGenerating, spokenReply?.id, currentPhoneTask?.running) {
                        val returningReply = currentPhoneTask?.let { !it.running && it.error == null } == true
                        if (!isGenerating && config.autoReadReply &&
                            (wasGenerating || returningReply) &&
                            !conversation.hasPendingToolApprovals() &&
                            spokenReply != null && spokenReply.toContentText().isNotBlank() &&
                            phoneManager.claimSpokenReply(spokenReply.id)) {
                            tts.speak(spokenReply.toContentText(), overrideSetting = settings.getEffectiveTTSProvider(assistant))
                        }
                        wasGenerating = isGenerating
                    }

                    // Auto-send when transcription settles.
                    var wasRecording by remember { mutableStateOf(false) }
                    LaunchedEffect(sttState.status) {
                        when (sttState.status) {
                            ASRStatus.Connecting, ASRStatus.Listening -> {
                                acceptSttTranscript = true
                                utteranceSent = false
                                sttSessionEpoch = inputState.sttCommitEpoch
                                wasRecording = true
                            }
                            ASRStatus.Stopping -> {
                                wasRecording = true
                            }
                            ASRStatus.Idle -> {
                                if (wasRecording) {
                                    wasRecording = false
                                    if (
                                        config.autoSendOnSttFinish &&
                                        !utteranceSent &&
                                        !isGenerating
                                    ) {
                                        val spoken = sttState.transcript.trim()
                                        if (inputState.isEmpty() && spoken.isNotBlank()) {
                                            inputState.setMessageText(spoken)
                                        }
                                        if (!inputState.isEmpty()) {
                                            doSend()
                                        }
                                    }
                                }
                            }
                            ASRStatus.Error -> {
                                wasRecording = false
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

                    TranscriptPanel(
                        visible = transcript.isNotEmpty() || compactPhone,
                        modifier = Modifier.weight(1f, fill = false)
                            .padding(horizontal = if (floatingContent) 0.dp else 16.dp),
                        assistantAvatar = { modifier ->
                            AssistantOverlayAvatar(
                                assistant, modifier, active = isGenerating, personalized = !lastChatBehind,
                            )
                        },
                        compact = compactPhone,
                        onCompactSettled = { panelCompactSettled = it },
                        phoneTask = currentPhoneTask,
                        onPausePhone = { phoneManager.togglePause() },
                        onStopPhone = { phoneManager.cancelAndReturn(); viewModel.stopGeneration() },
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

                    AssistantInputReveal(visible = !compactPhone, onHidden = { inputHidden = it }) {
                        Column {
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
                                onCancelClick = {
                                    viewModel.stopGeneration()
                                    generationJob?.cancel()
                                },
                                onSendClick = { doSend() },
                                onLongSendClick = { doSend() },
                                bottomPadding = 0.dp,
                                applyBottomInsets = false,
                                horizontalPadding = if (floatingContent) 0.dp else 16.dp,
                            )
                        }
                    }

                } else if (returnPose && currentPhoneTask != null) {
                    TranscriptPanel(
                        assistantAvatar = {
                            AssistantOverlayAvatar(assistant, it, currentPhoneTask.running, !lastChatBehind)
                        },
                        messages = emptyList(), activityState = ActivityState.Waiting,
                        isGenerating = currentPhoneTask.running, isSpeaking = false,
                        onOpenInApp = onOpenInApp, onCopy = {}, onRegenerate = {}, onToggleTts = {},
                        compact = true, phoneTask = currentPhoneTask,
                        modifier = Modifier.padding(horizontal = if (floatingContent) 0.dp else 16.dp),
                    )
                } else {
                    AssistantStartingBar(assistant, sttState.isRecording,
                        personalizedAvatar = !lastChatBehind,
                        horizontalPadding = if (floatingContent) 0.dp else 16.dp) {
                        if (sttState.isRecording) stt.stop()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED) stt.start(::applySttTranscript)
                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }
}

/** Reserve the same slot without repeating a portrait already visible in LastChat underneath. */
@Composable
private fun AssistantOverlayAvatar(
    assistant: me.rerere.rikkahub.data.model.Assistant,
    modifier: Modifier,
    active: Boolean,
    personalized: Boolean,
) {
    if (personalized) {
        me.rerere.rikkahub.phoneuse.PhoneUseAvatar(assistant, modifier, active)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SmartToy, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize().padding(6.dp))
        }
    }
}

/** Visible from the first frame, even if creating/loading the conversation is still pending. */
@Composable
internal fun AssistantStartingBar(
    assistant: me.rerere.rikkahub.data.model.Assistant,
    recording: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    personalizedAvatar: Boolean = true,
    onToggleMicrophone: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AssistantOverlayAvatar(assistant, Modifier.size(32.dp), recording, personalizedAvatar)
            Text(assistant.name, Modifier.weight(1f).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            androidx.compose.material3.IconButton(onClick = onToggleMicrophone) {
                Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    if (recording) "Stop listening" else "Start listening")
            }
        }
    }
}

@Composable
internal fun TranscriptPanel(
    assistantAvatar: @Composable (Modifier) -> Unit,
    messages: List<UIMessage>,
    activityState: ActivityState,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    onOpenInApp: () -> Unit,
    onCopy: (UIMessage) -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onToggleTts: (UIMessage) -> Unit,
    compact: Boolean = false,
    visible: Boolean = true,
    phoneTask: me.rerere.rikkahub.phoneuse.PhoneUseTask? = null,
    onPausePhone: () -> Unit = {},
    onStopPhone: () -> Unit = {},
    windowDrag: PhoneOverlayDragCallbacks? = null,
    openDescription: String = stringResource(R.string.assistant_overlay_open_in_app),
    modifier: Modifier = Modifier,
    onCompactSettled: (Boolean) -> Unit = {},
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val reduceMotion = me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy().reduceMotion
        val configuration = LocalConfiguration.current
        val screenMaximum = (configuration.screenHeightDp * 0.66f).coerceAtLeast(80f)
        val availableMaximum = if (constraints.hasBoundedHeight) {
            constraints.maxHeight / density.density
        } else screenMaximum
        val maxHeight = minOf(screenMaximum, availableMaximum).coerceAtLeast(0f)
        val minHeight = minOf(76f, maxHeight)
        var preferredHeight by remember { mutableStateOf(minOf(220f, maxHeight)) }
        var userSized by remember { mutableStateOf(false) }
        val messageHeights = remember { androidx.compose.runtime.mutableStateMapOf<kotlin.uuid.Uuid, Int>() }
        val latestMessages = messages.takeLast(2)
        val latestHeight = latestMessages.sumOf { messageHeights[it.id] ?: 0 } / density.density
        // Keep the latest exchange readable when there is room. Older history remains
        // scrollable, rather than making every conversation grow to the screen maximum.
        val naturalHeight = maxOf(220f, 76f + 24f + latestHeight +
            (latestMessages.size - 1).coerceAtLeast(0) * 10f).coerceAtMost(maxHeight)
        val height = remember {
            Animatable(if (!visible) 0f else if (compact) 56f else preferredHeight, visibilityThreshold = 0.05f)
        }
        val measuredHeight = remember { floatArrayOf(height.value) }
        var dragHeight by remember { mutableStateOf<Float?>(null) }
        var dragging by remember { mutableStateOf(false) }
        var resizeVersion by remember { mutableIntStateOf(0) }
        var releaseVelocity by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()
        val resizeDrag = remember(minHeight, maxHeight, density.density) {
            PhoneOverlayDragCallbacks(
                onStart = {
                    userSized = true
                    dragging = true
                    dragHeight = measuredHeight[0]
                    scope.launch { height.stop() }
                },
                onMove = { _, dy ->
                    dragHeight = ((dragHeight ?: measuredHeight[0]) - dy / density.density)
                        .coerceIn(minHeight, maxHeight)
                },
                onRelease = { _, vy ->
                    dragging = false
                    val current = dragHeight ?: measuredHeight[0]
                    val velocity = -vy / density.density
                    val projected = current + velocity * 0.15f
                    preferredHeight = listOf(minHeight, minOf(220f, maxHeight), maxHeight)
                        .minBy { kotlin.math.abs(it - projected) }
                    // Input can deliver several moves between display frames. Release
                    // from the last presented size, never an unseen final pointer sample.
                    val presented = measuredHeight[0].coerceIn(minHeight, maxHeight)
                    dragHeight = presented
                    val frequency = kotlin.math.sqrt(PHONE_MOTION_STIFFNESS)
                    releaseVelocity = velocity.coerceIn(
                        -minOf(3000f, (presented - minHeight) * frequency),
                        minOf(3000f, (maxHeight - presented) * frequency),
                    )
                    resizeVersion++
                },
            )
        }
        val compactCallback by androidx.compose.runtime.rememberUpdatedState(onCompactSettled)
        val targetHeight = when {
            !visible -> 0f
            compact -> 56f
            userSized -> preferredHeight.coerceIn(minHeight, maxHeight)
            else -> naturalHeight.coerceIn(minHeight, maxHeight)
        }
        // IME/input geometry changes the available height every frame. Restart only
        // when the actual target changes, otherwise the spring never gets its first frame.
        LaunchedEffect(compact, visible, targetHeight, resizeVersion, reduceMotion) {
            compactCallback(false)
            val velocity = if (dragHeight != null) releaseVelocity else height.velocity
            dragHeight?.let { height.snapTo(it) }
            releaseVelocity = 0f
            height.animateTo(targetHeight,
                if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS),
                initialVelocity = velocity) {
                // Keep the last finger position until the spring publishes its first frame.
                // Clearing it before animateTo starts briefly renders the previous spring value.
                if (!dragging) dragHeight = null
            }
            compactCallback(compact && visible)
        }
        val scroll = rememberScrollState()
        val userDragging by scroll.interactionSource.collectIsDraggedAsState()
        var followLatest by remember { mutableStateOf(true) }
        LaunchedEffect(userDragging, scroll.value) {
            if (userDragging) followLatest = scroll.maxValue - scroll.value < with(density) { 32.dp.toPx() }
        }

        val blur = LocalLastChatBlur.current
        val compactTransition = updateTransition(compact, label = "assistantPhoneMorph")
        val handleProgress by compactTransition.animateFloat(
            transitionSpec = { if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS) },
            label = "resizeHandle",
        ) { if (it) 0f else 1f }
        val handleFraction = handleProgress.coerceIn(0f, 1f)
        val panelAlpha by animateFloatAsState(if (visible) 1f else 0f,
            if (reduceMotion) snap() else spring(stiffness = PHONE_MOTION_STIFFNESS), label = "assistantPanelOpacity")
        val panelShape = RoundedCornerShape(28.dp)
        val containerColor = if (blur.enabled && (blur.hazeState != null || blur.windowBlur)) {
            blurredContainerColor(MaterialTheme.colorScheme.surfaceContainerLow)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        val contentColor = MaterialTheme.colorScheme.onSurface

        var displayedMessages by remember { mutableStateOf(messages) }
        if (!compact && displayedMessages !== messages) displayedMessages = messages
        val lastAssistantId = remember(displayedMessages) {
            displayedMessages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
        }
        var expandedIds by remember { mutableStateOf(emptySet<kotlin.uuid.Uuid>()) }

        val transcriptColumn: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                displayedMessages.forEach { message ->
                    val isAssistant = message.role == MessageRole.ASSISTANT
                    val actionsVisible = isAssistant && !isGenerating &&
                        (message.id == lastAssistantId || message.id in expandedIds)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.onSizeChanged { size ->
                            if (messageHeights[message.id] != size.height) messageHeights[message.id] = size.height
                        }.then(if (isAssistant) {
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
                        } else Modifier),
                    ) {
                        MessageRowContent(message = message, contentColor = contentColor,
                            streaming = isGenerating && message.id == lastAssistantId)
                        if (isAssistant) {
                            AnimatedVisibility(visible = actionsVisible) {
                                AssistantActionsRow(
                                    isSpeaking = isSpeaking,
                                    showRegenerate = message.id == lastAssistantId &&
                                        !isCharacterIntroMessage(displayedMessages, message),
                                    onCopy = { onCopy(message) },
                                    onRegenerate = { onRegenerate(message) },
                                    onToggleTts = { onToggleTts(message) },
                                )
                            }
                        }
                    }
                }
                phoneTask?.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = panelShape,
            border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .height((dragHeight ?: height.value).coerceIn(0f, maxHeight).dp)
                .onSizeChanged { measuredHeight[0] = it.height / density.density }
                .graphicsLayer { alpha = panelAlpha }
                .lastChatBlurEffect(containerColor = containerColor, shape = panelShape),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Floating header (drag handle + avatar + compact activity pill + open-in-app).
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((20f * handleFraction).dp)
                            .semantics {
                                contentDescription = "Resize assistant"
                                customActions = listOf(
                                    androidx.compose.ui.semantics.CustomAccessibilityAction("Expand") {
                                        userSized = true; preferredHeight = maxHeight; resizeVersion++; true
                                    },
                                    androidx.compose.ui.semantics.CustomAccessibilityAction("Minimize") {
                                        userSized = true; preferredHeight = minHeight; resizeVersion++; true
                                    },
                                )
                            }
                            .graphicsLayer { alpha = handleFraction; clip = true }
                            .phoneOverlayDrag(if (compact) null else windowDrag ?: resizeDrag)
                            .padding(top = (10f * handleFraction).dp, bottom = (6f * handleFraction).dp),
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
                            .fillMaxWidth().height(56.dp)
                            .phoneOverlayDrag(if (compact) windowDrag else null)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        assistantAvatar(Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        // Weighted box fills the leftover width so the button sits at the far right.
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            androidx.compose.animation.Crossfade(
                                targetState = phoneTask?.let { compact || it.running || it.awaitingReply } == true,
                                animationSpec = if (reduceMotion) snap() else tween(180),
                                label = "phoneActivityLabel",
                            ) { phoneActivity ->
                                val current = phoneTask
                                if (phoneActivity && current != null) {
                                    me.rerere.rikkahub.phoneuse.PhoneUseActivityPill(current)
                                } else CompactActivityPill(state = activityState)
                            }
                        }
                        val operating = phoneTask != null && (compact || phoneTask.running)
                        AnimatedVisibility(
                            visible = operating && (compact || phoneTask?.action != "reply"),
                            enter = fadeIn(if (reduceMotion) snap() else spring(stiffness = PHONE_MOTION_STIFFNESS)) +
                                androidx.compose.animation.expandHorizontally(
                                    if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS)),
                            exit = fadeOut(if (reduceMotion) snap() else spring(stiffness = PHONE_MOTION_STIFFNESS)) +
                                androidx.compose.animation.shrinkHorizontally(
                                    if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS)),
                        ) {
                            androidx.compose.material3.IconButton(onClick = onPausePhone,
                                enabled = phoneTask?.running == true, modifier = Modifier.size(40.dp)) {
                                Icon(if (phoneTask?.paused == true) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    if (phoneTask?.paused == true) "Resume phone use" else "Pause phone use")
                            }
                        }
                        AnimatedVisibility(
                            visible = operating,
                            enter = fadeIn(if (reduceMotion) snap() else tween(180)) +
                                androidx.compose.animation.expandHorizontally(
                                    if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS)),
                            exit = fadeOut(if (reduceMotion) snap() else tween(180)) +
                                androidx.compose.animation.shrinkHorizontally(
                                    if (reduceMotion) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS)),
                        ) {
                            androidx.compose.material3.IconButton(onClick = onStopPhone,
                                modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.Stop, "Stop phone use")
                            }
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
                                contentDescription = openDescription,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // The scrolling reply is below the header from the first frame; no measured spacer.
                val bodyAlpha by compactTransition.animateFloat(
                    transitionSpec = { if (reduceMotion) snap() else spring(stiffness = PHONE_MOTION_STIFFNESS) },
                    label = "transcriptOpacity",
                ) { if (it) 0f else 1f }
                val topFade by animateFloatAsState(
                    targetValue = (scroll.value / with(density) { 16.dp.toPx() }).coerceIn(0f, 1f),
                    animationSpec = if (reduceMotion) snap() else tween(120),
                    label = "transcriptOverflowFade",
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)
                    .graphicsLayer { alpha = bodyAlpha.coerceIn(0f, 1f) }
                    .fadeEdges(topProgress = topFade, fadeHeight = with(density) { 16.dp.toPx() })
                    .verticalScroll(scroll)) {
                    if (!compact || compactTransition.isRunning) transcriptColumn()
                }

            }
        }

        // Keep the transcript pinned to the latest text while generating.
        LaunchedEffect(if (compact) emptyList<UIMessage>() else messages, isGenerating, userDragging, compact) {
            if (!compact && followLatest && !userDragging) {
                androidx.compose.runtime.withFrameNanos { }
                scroll.scrollTo(scroll.maxValue)
            }
        }
    }
}

// Only messages with actual text are shown: tool-call-only assistant turns (which would
// render as "…") and the injected screenshot USER image message are hidden — tool activity is
// conveyed by the header pill instead.
private fun UIMessage.hasDisplayableContent(): Boolean = toContentText().isNotBlank()

@Composable
private fun MessageRowContent(message: UIMessage, contentColor: Color, streaming: Boolean = false) {
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
                streamingTextReveal = streaming,
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
internal fun SilkGlowLayer(
    active: Boolean,
    waveSignal: Long,
    originX: Float,
    originY: Float,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy().reduceMotion
    val transition = rememberInfiniteTransition(label = "silkGlow")
    val phase by if (reduceMotion) androidx.compose.runtime.rememberUpdatedState(0f) else transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowPhase",
    )
    val breath by if (reduceMotion) androidx.compose.runtime.rememberUpdatedState(1f) else transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowBreath",
    )
    val boost by animateFloatAsState(
        targetValue = if (active) 1f else 0.78f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "glowBoost",
    )

    // Entrance reveal (runs once) + morphs into the glow.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
    }

    // A bright travelling "wave" band: replays on every screen-read signal.
    val wave = remember { Animatable(1f) }
    LaunchedEffect(waveSignal, reduceMotion) {
        if (reduceMotion) wave.snapTo(1f)
        else if (waveSignal > 0L) {
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

