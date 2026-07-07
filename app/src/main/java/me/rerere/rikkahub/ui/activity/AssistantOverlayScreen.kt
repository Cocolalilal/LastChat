package me.rerere.rikkahub.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberCustomSttState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.sin

/**
 * The digital-assistant overlay, matching the issue-#177 sketches:
 * the underlying screen stays visible, an organic Material You glow hugs the screen
 * edges (revealed bottom→top on entrance, reacting to voice), and a floating "+" button
 * plus a dark pill input rise from the bottom. Replies appear in a dark translucent
 * panel with the assistant avatar, an activity pill, an "Open in app" chip, a top text
 * fade, and drag-to-expand.
 */
@Composable
fun AssistantOverlayScreen(
    viewModel: AssistantOverlayVM,
    onDismiss: () -> Unit,
    onOpenInApp: () -> Unit,
) {
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val config = settings.assistantOverlayConfig
    val assistant = remember(settings) { settings.resolveAssistantOverlayAssistant() }
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()

    val stt = rememberCustomSttState()
    val tts = rememberCustomTtsState()
    val sttState by stt.state.collectAsStateWithLifecycle()
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showPlusSheet by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<QuickAskAttachment>() }
    val overlayState = viewModel.state

    // Screenshot captured at summon time, attached to the message when enabled.
    val screenshotDataUrl = remember {
        if (config.attachScreenshot) AssistScreenHolder.dataUrlOrNull() else null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) stt.start { inputText = it }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachments += QuickAskAttachment(
                uri = uri.toString(),
                fileName = uri.lastPathSegment ?: "file",
                mimeType = context.contentResolver.getType(uri),
            )
        }
    }

    fun startListening() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) stt.start { inputText = it }
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun doSend() {
        if (sttState.isRecording) stt.stop()
        val text = inputText.trim()
        if (text.isBlank() && screenshotDataUrl == null && attachments.isEmpty()) return
        haptics.perform(HapticPattern.Send)
        viewModel.send(text, screenshotDataUrl, attachments.toList())
    }

    LaunchedEffect(Unit) {
        if (config.autoStartStt) startListening()
    }

    // Auto-send when transcription settles.
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(sttState.status) {
        if (sttState.status == ASRStatus.Listening || sttState.status == ASRStatus.Stopping) {
            wasRecording = true
        } else if (sttState.status == ASRStatus.Idle && wasRecording) {
            wasRecording = false
            if (config.autoSendOnSttFinish &&
                inputText.isNotBlank() &&
                overlayState is AssistantOverlayVM.OverlayState.Idle
            ) {
                doSend()
            }
        }
    }

    // Read the reply aloud once streaming completes.
    LaunchedEffect(overlayState) {
        val s = overlayState
        if (s is AssistantOverlayVM.OverlayState.Result && !s.isStreaming &&
            config.autoReadReply && s.responseText.isNotBlank()
        ) {
            tts.speak(s.responseText)
        }
    }

    val voiceActive = isSpeaking || sttState.isRecording

    Box(modifier = Modifier.fillMaxSize()) {
        // The screen behind stays visible — only a light scrim + tap-to-dismiss catcher.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        // Organic edge glow, revealed from the bottom of the screen to the top.
        EdgeGlow(
            active = voiceActive,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            AnimatedVisibility(
                visible = overlayState !is AssistantOverlayVM.OverlayState.Idle,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                    initialOffsetY = { it / 2 },
                ) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
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

            Spacer(Modifier.height(10.dp))

            if (attachments.isNotEmpty()) {
                AttachmentChips(
                    attachments = attachments,
                    onRemove = { attachments.remove(it) },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Input row rises from the bottom with spring physics.
            val inputVisible = remember {
                MutableTransitionState(false).apply { targetState = true }
            }
            AnimatedVisibility(
                visibleState = inputVisible,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                    initialOffsetY = { it * 2 },
                ) + fadeIn(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Separate round "+" button, left of the pill (as sketched).
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showPlusSheet = true
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Dark pill input with the assistant avatar as the voice button.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 20.dp, end = 8.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = stringResource(
                                            R.string.assistant_overlay_hint, assistant.name
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                    ),
                                    cursorBrush = Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary,
                                        )
                                    ),
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (inputText.isNotBlank()) {
                                Surface(
                                    onClick = { doSend() },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            } else {
                                // Assistant avatar = voice button (pulsing ring while listening).
                                val listenPulse by rememberInfiniteTransition(label = "listen")
                                    .animateFloat(
                                        initialValue = 0.35f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(900, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse,
                                        ),
                                        label = "listenPulse",
                                    )
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .then(
                                            if (sttState.isRecording) Modifier.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                                    .copy(alpha = listenPulse),
                                                shape = CircleShape,
                                            ) else Modifier
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            haptics.perform(HapticPattern.Pop)
                                            if (sttState.isRecording) stt.stop() else startListening()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    UIAvatar(
                                        name = assistant.name,
                                        value = assistant.avatar,
                                        modifier = Modifier.size(38.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }

    if (showPlusSheet) {
        ModalBottomSheet(onDismissRequest = { showPlusSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Model picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "Model",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    ModelSelector(
                        modelId = config.modelId,
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        allowClear = true,
                        onClear = {
                            scope.launch {
                                settingsStore.update {
                                    it.copy(
                                        assistantOverlayConfig = it.assistantOverlayConfig
                                            .copy(modelId = null)
                                    )
                                }
                            }
                        },
                        onSelect = { model ->
                            scope.launch {
                                settingsStore.update {
                                    it.copy(
                                        assistantOverlayConfig = it.assistantOverlayConfig
                                            .copy(modelId = model.id)
                                    )
                                }
                            }
                        },
                    )
                }
                // File picker
                Surface(
                    onClick = {
                        showPlusSheet = false
                        filePickerLauncher.launch("*/*")
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            Icons.Rounded.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Attach file",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                // Open in app
                Surface(
                    onClick = {
                        showPlusSheet = false
                        onOpenInApp()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.assistant_overlay_open_in_app),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChips(
    attachments: List<QuickAskAttachment>,
    onRemove: (QuickAskAttachment) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onRemove(attachment) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
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
                                        (panelHeight.value - deltaDp).coerceIn(minHeight, maxHeight)
                                    )
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    val target = if (
                                        panelHeight.value > (minHeight + maxHeight) / 2
                                    ) maxHeight else minHeight
                                    panelHeight.animateTo(
                                        target,
                                        spring(dampingRatio = 0.7f, stiffness = 300f),
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

            // Header: avatar + activity pill (right of avatar) ... "Open in app" chip.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                assistantAvatar(Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                ActivityPill(state = state)
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onOpenInApp,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_overlay_open_in_app),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
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
                            )
                        }
                    }

                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Thinking…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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

@Composable
private fun ActivityPill(state: AssistantOverlayVM.OverlayState) {
    val label = when (state) {
        is AssistantOverlayVM.OverlayState.Generating -> "Thinking"
        is AssistantOverlayVM.OverlayState.Result -> if (state.isStreaming) "Writing" else null
        is AssistantOverlayVM.OverlayState.Error -> "Error"
        else -> null
    } ?: return
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Organic Material You glow hugging the screen edges. Colored blobs drift slowly along
 * each edge; the whole glow is revealed from the bottom of the screen to the top on
 * entrance, and swells while the assistant is listening or speaking.
 */
@Composable
private fun EdgeGlow(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "edgeGlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glowPhase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowBreath",
    )
    val boost by animateFloatAsState(
        targetValue = if (active) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "glowBoost",
    )
    // Entrance: the glow rises from the bottom of the screen to the top.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .blur(40.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                val w = size.width
                val h = size.height
                val alpha = (0.55f * breath * boost).coerceIn(0f, 1f)
                val twoPi = (2.0 * PI).toFloat()

                fun blob(center: Offset, radius: Float, color: Color) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }

                // Blobs drifting along each edge (positions slide organically with phase).
                blob(
                    center = Offset(-0.04f * w, h * (0.35f + 0.22f * sin(twoPi * phase))),
                    radius = 0.42f * h,
                    color = primary,
                )
                blob(
                    center = Offset(w * (0.55f + 0.25f * sin(twoPi * phase + 1.7f)), -0.04f * h),
                    radius = 0.38f * h,
                    color = tertiary,
                )
                blob(
                    center = Offset(1.04f * w, h * (0.55f + 0.22f * sin(twoPi * phase + 3.4f))),
                    radius = 0.42f * h,
                    color = secondary,
                )
                blob(
                    center = Offset(w * (0.45f + 0.28f * sin(twoPi * phase + 5.1f)), 1.04f * h),
                    radius = 0.46f * h,
                    color = primary,
                )

                drawContent()

                // Bottom→top reveal mask for the entrance animation.
                val p = entrance.value
                if (p < 1f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = p * p),
                            (1f - p).coerceIn(0f, 1f) to Color.Black,
                            1f to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    )
}
