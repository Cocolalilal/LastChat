package me.rerere.rikkahub.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import me.rerere.rikkahub.ui.components.ai.MinimalChatInput
import me.rerere.rikkahub.ui.components.chat.ActivityPillRow
import me.rerere.rikkahub.ui.components.chat.ActivityState
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSTTState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberChatInputState
import me.rerere.rikkahub.ui.hooks.rememberCustomSttState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.LastChatBlur
import me.rerere.rikkahub.ui.modifier.LocalLastChatBlur
import me.rerere.rikkahub.ui.modifier.blurredContainerColor
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatBlurSource
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The digital-assistant overlay (issue #177 sketches):
 * - the underlying screen stays crisp (drawn from the summon-time capture so the app's
 *   haze blur can sample it),
 * - a vivid Material You glow hugs the screen edges under a drifting "silk" dot grid,
 *   revealed bottom→top and swelling with the assistant's voice,
 * - the app's real [MinimalChatInput] floats at the bottom (blur, pickers, STT and all),
 * - replies appear in a blurred panel with avatar, activity pill, "Open in app",
 *   a top text fade and drag-to-expand.
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
    val config = settings.assistantOverlayConfig
    val assistant = remember(settings) { settings.resolveAssistantOverlayAssistant() }
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()

    // Dismissal animation: smooth slide-down + fade-out before calling finish().
    val dismissProgress = remember { Animatable(0f) }
    val isDismissing = remember { mutableStateOf(false) }
    fun dismissWithAnimation() {
        if (isDismissing.value) return
        isDismissing.value = true
        scope.launch {
            dismissProgress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 200f))
            onDismiss()
        }
    }

    // Predictive back: smoothly scale-fade the overlay as user swipes back.
    val predictiveBackProgress = remember { Animatable(1f) }
    PredictiveBackHandler(enabled = !isDismissing.value) { backFlow ->
        backFlow.collect { event ->
            predictiveBackProgress.snapTo(1f - event.progress)
        }
    }
    BackHandler(enabled = !isDismissing.value) { dismissWithAnimation() }

    val stt = rememberCustomSttState()
    val tts = rememberCustomTtsState()
    val sttState by stt.state.collectAsStateWithLifecycle()
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    val inputState = rememberChatInputState()
    val overlayState = viewModel.state

    // Same blur system as the rest of the app; the backdrop below is the haze source.
    val hazeState = rememberHazeState()
    val blur = remember(settings.displaySetting.enableBlurEffect, hazeState) {
        LastChatBlur(
            enabled = settings.displaySetting.enableBlurEffect,
            hazeState = hazeState,
        )
    }

    // Summon-time capture: crisp backdrop (so blur has something to sample) + model attachment.
    val backdrop = remember { AssistScreenHolder.bitmapOrNull()?.asImageBitmap() }
    val screenshotDataUrl = remember {
        if (config.attachScreenshot) AssistScreenHolder.dataUrlOrNull() else null
    }

    val conversation = remember(assistant.id) {
        Conversation(assistantId = assistant.id, messageNodes = emptyList())
    }

    fun doSend() {
        if (sttState.isRecording) stt.stop()
        if (inputState.isEmpty() && screenshotDataUrl == null) return
        haptics.perform(HapticPattern.Send)
        viewModel.send(inputState.getContents(), screenshotDataUrl)
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

    // Auto-send when transcription settles.
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(sttState.status) {
        if (sttState.status == ASRStatus.Listening || sttState.status == ASRStatus.Stopping) {
            wasRecording = true
        } else if (sttState.status == ASRStatus.Idle && wasRecording) {
            wasRecording = false
            if (config.autoSendOnSttFinish &&
                !inputState.isEmpty() &&
                overlayState is AssistantOverlayVM.OverlayState.Idle
            ) {
                doSend()
            }
        }
    }

    // Read the reply aloud once streaming completes; keep the input's loading state in sync.
    LaunchedEffect(overlayState) {
        val s = overlayState
        inputState.loading = s is AssistantOverlayVM.OverlayState.Generating ||
            (s is AssistantOverlayVM.OverlayState.Result && s.isStreaming)
        if (s is AssistantOverlayVM.OverlayState.Result && !s.isStreaming &&
            config.autoReadReply && s.responseText.isNotBlank()
        ) {
            tts.speak(s.responseText)
        }
    }

    val voiceActive = isSpeaking || sttState.isRecording

    CompositionLocalProvider(
        LocalSTTState provides stt,
        LocalLastChatBlur provides blur,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val dismissVal = dismissProgress.value
                    val backVal = predictiveBackProgress.value
                    val combined = dismissVal + (1f - backVal).coerceIn(0f, 1f)
                    alpha = (1f - combined).coerceIn(0f, 1f)
                    translationY = dismissVal * 200f
                    scaleX = backVal
                    scaleY = backVal
                }
        ) {
            // Backdrop = haze source: crisp capture of the summoning screen + scrim + glow.
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
                val scrimAlpha by animateFloatAsState(
                    targetValue = 0.28f,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "scrim",
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { dismissWithAnimation() },
                )
                SilkGlowLayer(
                    active = voiceActive,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                AnimatedVisibility(
                    visible = overlayState !is AssistantOverlayVM.OverlayState.Idle,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                        initialOffsetY = { it / 2 },
                    ) + fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)),
                    exit = slideOutVertically(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                        targetOffsetY = { it / 2 },
                    ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    ReplyPanel(
                        assistantName = assistant.name,
                        assistantAvatar = { modifier ->
                            UIAvatar(name = assistant.name, value = assistant.avatar, modifier = modifier)
                        },
                        state = overlayState,
                        onOpenInApp = onOpenInApp,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // The app's real input bar, rising from the bottom with physics.
                val inputVisible = remember {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = inputVisible,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 220f,
                        ),
                        initialOffsetY = { it / 3 },
                    ) + fadeIn(animationSpec = tween(450, easing = FastOutSlowInEasing)),
                ) {
                    MinimalChatInput(
                        modifier = Modifier.fillMaxWidth(),
                        state = inputState,
                        conversation = conversation,
                        settings = settings,
                        mcpManager = mcpManager,
                        enableSearch = false,
                        onToggleSearch = {},
                        onUpdateChatModel = { model ->
                            scope.launch {
                                settingsStore.update { s ->
                                    s.copy(assistants = s.assistants.map { a ->
                                        if (a.id == assistant.id) a.copy(chatModelId = model.id) else a
                                    })
                                }
                            }
                        },
                        onUpdateAssistant = { updated ->
                            scope.launch {
                                settingsStore.update { s ->
                                    s.copy(assistants = s.assistants.map { a ->
                                        if (a.id == updated.id) updated else a
                                    })
                                }
                            }
                        },
                        onUpdateConversation = {},
                        onToolApproval = { _, _, _, _ -> },
                        onUpdateSearchService = {},
                        onClearContext = {},
                        onCancelClick = { viewModel.cancel() },
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
private fun ReplyPanel(
    assistantName: String,
    assistantAvatar: @Composable (Modifier) -> Unit,
    state: AssistantOverlayVM.OverlayState,
    onOpenInApp: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val minHeight = 170f
    val maxHeight = configuration.screenHeightDp * 0.72f
    val panelHeight = remember { Animatable(minHeight) }
    val scroll = rememberScrollState()

    val blur = LocalLastChatBlur.current
    val panelShape = RoundedCornerShape(28.dp)
    val containerColor = if (blur.enabled && blur.hazeState != null) {
        blurredContainerColor(MaterialTheme.colorScheme.surfaceContainerLow)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f)
    }

    Surface(
        color = containerColor,
        shape = panelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .lastChatBlurEffect(containerColor = containerColor, shape = panelShape),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            // Drag handle — drag up/down to expand/collapse the panel.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                val deltaDp = dragAmount / density.density
                                scope.launch {
                                    panelHeight.snapTo(
                                        (panelHeight.value - deltaDp * 0.85f).coerceIn(minHeight, maxHeight)
                                    )
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    // Easy expand: 30% from min triggers expansion.
                                    val expandThreshold = minHeight + (maxHeight - minHeight) * 0.3f
                                    val target = if (panelHeight.value > expandThreshold) maxHeight
                                    else minHeight
                                    panelHeight.animateTo(
                                        target,
                                        spring(dampingRatio = 0.9f, stiffness = 180f),
                                    )
                                }
                            },
                        )
                    }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            CircleShape,
                        ),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Header: avatar + activity pill (right of avatar) ... "Open in app" icon.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                assistantAvatar(Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ActivityPillRow(
                        state = state.toActivityState(),
                        onClick = { },
                        connectsToBubbleBelow = false,
                        timelineOpen = false,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onOpenInApp,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.assistant_overlay_open_in_app),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Reply content: scrollable, text fades out at the top (as sketched).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight.value.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.08f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                when (state) {
                    is AssistantOverlayVM.OverlayState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is AssistantOverlayVM.OverlayState.Result -> {
                        SelectionContainer(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll),
                        ) {
                            MarkdownBlock(
                                content = state.responseText.ifBlank { "…" },
                                style = MaterialTheme.typography.bodyLarge,
                                streamingTextReveal = state.isStreaming,
                            )
                        }
                    }

                    else -> {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }
    }

    // Keep the reply pinned to the latest text while streaming.
    LaunchedEffect(state) {
        if (state is AssistantOverlayVM.OverlayState.Result && state.isStreaming) {
            scroll.scrollTo(scroll.maxValue)
        }
    }
}

/**
 * Maps [AssistantOverlayVM.OverlayState] to [ActivityState] for the real ActivityPillRow.
 * - Generating → Waiting (or Reasoning if isReasoning)
 * - Result(isStreaming=true) → Replying
 * - Result(isStreaming=false) → Hidden (completed)
 * - Error / Idle → Hidden
 */
private fun AssistantOverlayVM.OverlayState.toActivityState(): ActivityState = when (this) {
    is AssistantOverlayVM.OverlayState.Generating -> ActivityState.Waiting
    is AssistantOverlayVM.OverlayState.Result -> {
        if (isStreaming) {
            if (isReasoning) ActivityState.Reasoning() else ActivityState.Replying
        } else {
            ActivityState.Hidden
        }
    }
    is AssistantOverlayVM.OverlayState.Error -> ActivityState.Hidden
    is AssistantOverlayVM.OverlayState.Idle -> ActivityState.Hidden
}

/**
 * The "wavey AI light": a vivid Material You vignette hugging the screen edges (never
 * washing out the middle), overlaid with a dot grid that undulates slowly like a silk
 * cover. Per the reference design, the dots are MASKED BY THE GLOW ITSELF: each dot's
 * alpha/color comes from evaluating the same blob field at its position, drawn crisp
 * and slightly stronger than the blurred glow beneath — so the dots sparkle at the
 * edges and vanish toward the middle. Both layers are revealed bottom→top on entrance,
 * and the glow swells while the assistant is listening or speaking.
 */
@Composable
private fun SilkGlowLayer(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "silkGlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPhase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowBreath",
    )
    val boost by animateFloatAsState(
        targetValue = if (active) 1f else 0.62f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "glowBoost",
    )
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }

    val scheme = MaterialTheme.colorScheme
    // Wider Material You range than just primary/tertiary — vivid but not neon.
    val edgeColors = remember(scheme) {
        listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary,
            scheme.inversePrimary,
            scheme.primary,
            scheme.tertiary,
        )
    }
    val density = LocalDensity.current
    val dotSpacingPx = with(density) { 20.dp.toPx() }
    val dotRadiusPx = with(density) { 1.4.dp.toPx() }
    val dotDriftPx = with(density) { 2.dp.toPx() }

    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Wave entrance: bottom→top with a soft leading edge and trailing fade.
                val p = entrance.value
                if (p < 1f) {
                    val waveFront = 1f - p
                    val waveWidth = 0.3f
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = p * p),
                            (waveFront - waveWidth).coerceIn(0f, 1f) to Color.Black.copy(alpha = p * p),
                            waveFront.coerceIn(0f, 1f) to Color.Black,
                            (waveFront + waveWidth * 0.5f).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.3f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    // Brightness pulse traveling with the wave front.
                    val waveY = waveFront * size.height
                    drawRect(
                        brush = Brush.verticalGradient(
                            (waveY - 40f) to Color.Transparent,
                            waveY to Color.White.copy(alpha = 0.08f * (1f - p)),
                            (waveY + 40f) to Color.Transparent,
                        ),
                    )
                }
            },
    ) {
        // Edge glow: soft blobs pinned to the perimeter, blurred into a vignette.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(26.dp)
                .drawBehind {
                    val alpha = (0.75f * breath * boost).coerceIn(0f, 1f)
                    glowBlobs(size.width, size.height, phase, edgeColors).forEach { blob ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(blob.color.copy(alpha = alpha), Color.Transparent),
                                center = blob.center,
                                radius = blob.radius,
                            ),
                            radius = blob.radius,
                            center = blob.center,
                        )
                    }
                },
        )

        // Silk dot grid, masked by the glow field itself (per the reference design):
        // each dot samples the same blob field — brighter/more saturated than the
        // blurred glow beneath, fading to nothing toward the middle of the screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val t = phase * (2.0 * PI).toFloat()
                    val blobs = glowBlobs(w, h, phase, edgeColors)
                    // Dots reach slightly further inward than the glow, so they read
                    // as the stronger layer on top of it.
                    val maskReach = 1.15f
                    val dotStrength = (0.95f * breath * boost).coerceIn(0f, 1f)

                    var y = dotSpacingPx / 2f
                    while (y < h) {
                        var x = dotSpacingPx / 2f
                        while (x < w) {
                            var weight = 0f
                            var rSum = 0f
                            var gSum = 0f
                            var bSum = 0f
                            for (blob in blobs) {
                                val dx = x - blob.center.x
                                val dy = y - blob.center.y
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                val falloff =
                                    (1f - dist / (blob.radius * maskReach)).coerceAtLeast(0f)
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
                                    red = (rSum / weight).coerceIn(0f, 1f),
                                    green = (gSum / weight).coerceIn(0f, 1f),
                                    blue = (bSum / weight).coerceIn(0f, 1f),
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
    }
}

private class GlowBlob(val center: Offset, val radius: Float, val color: Color)

/**
 * The shared blob field driving BOTH the blurred edge glow and the dot mask — keeping
 * the two layers perfectly in sync as the blobs drift (the Figma construction: the dot
 * layer is the same fade, masked and intensified).
 */
private fun glowBlobs(w: Float, h: Float, phase: Float, colors: List<Color>): List<GlowBlob> {
    val twoPi = (2.0 * PI).toFloat()
    // Larger radius + further offset for liquid, stretching glow at edges.
    val r = 0.34f * h
    return listOf(
        // Left edge — pushed further out for stretch
        GlowBlob(Offset(-0.06f * w, h * (0.22f + 0.14f * sin(twoPi * phase))), r, colors[0]),
        GlowBlob(Offset(-0.06f * w, h * (0.72f + 0.12f * sin(twoPi * phase + 2.1f))), r, colors[1]),
        // Top edge
        GlowBlob(Offset(w * (0.5f + 0.3f * sin(twoPi * phase + 1.2f)), -0.06f * h), r * 0.95f, colors[2]),
        // Right edge
        GlowBlob(Offset(1.06f * w, h * (0.3f + 0.14f * sin(twoPi * phase + 3.5f))), r, colors[3]),
        GlowBlob(Offset(1.06f * w, h * (0.78f + 0.12f * sin(twoPi * phase + 4.6f))), r, colors[4]),
        // Bottom edge
        GlowBlob(Offset(w * (0.45f + 0.3f * sin(twoPi * phase + 5.4f)), 1.06f * h), r * 1.15f, colors[5]),
        // Corner connectors — larger for seamless liquid bridges
        GlowBlob(Offset(-0.03f * w, -0.03f * h), r * 0.75f, colors[0]),
        GlowBlob(Offset(1.03f * w, -0.03f * h), r * 0.75f, colors[3]),
        GlowBlob(Offset(-0.03f * w, 1.03f * h), r * 0.75f, colors[1]),
        GlowBlob(Offset(1.03f * w, 1.03f * h), r * 0.75f, colors[4]),
    )
}
