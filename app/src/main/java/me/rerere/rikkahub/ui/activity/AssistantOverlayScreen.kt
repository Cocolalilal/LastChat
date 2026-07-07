package me.rerere.rikkahub.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberCustomSttState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics

/**
 * The digital-assistant overlay: a blurred capture of the summoning screen, an organic
 * Material You glow that reacts to voice, a reply panel and a compact input bar that
 * rise from the bottom with physics.
 */
@Composable
fun AssistantOverlayScreen(
    viewModel: AssistantOverlayVM,
    onDismiss: () -> Unit,
    onOpenInApp: () -> Unit,
) {
    val settings = LocalSettings.current
    val config = settings.assistantOverlayConfig
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()

    val stt = rememberCustomSttState()
    val tts = rememberCustomTtsState()
    val sttState by stt.state.collectAsStateWithLifecycle()
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val overlayState = viewModel.state

    // Trigger-time screenshot: backdrop + optional model attachment.
    val backdrop = remember { AssistScreenHolder.bitmapOrNull()?.asImageBitmap() }
    val screenshotDataUrl = remember {
        if (config.attachScreenshot) AssistScreenHolder.dataUrlOrNull() else null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) stt.start { inputText = it }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startListening() {
        if (hasMicPermission()) stt.start { inputText = it }
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun doSend() {
        if (sttState.isRecording) stt.stop()
        val text = inputText.trim()
        if (text.isBlank() && screenshotDataUrl == null) return
        haptics.perform(HapticPattern.Send)
        viewModel.send(text, screenshotDataUrl)
    }

    // Auto-start STT on open.
    LaunchedEffect(Unit) {
        if (config.autoStartStt) startListening()
    }

    // Auto-send when transcription settles (status returns to Idle with content).
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
        // Backdrop: blurred capture of the screen we were summoned over.
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp),
            )
        }
        // Scrim (also the dismiss target).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        // Organic edge glow (Material You colors, reacts to voice).
        AssistantGlow(
            active = voiceActive,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                    assistantName = viewModel.assistantName,
                    state = overlayState,
                    onOpenInApp = onOpenInApp,
                )
            }

            Spacer(Modifier.height(8.dp))

            OverlayInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                hint = context.getString(R.string.assistant_overlay_hint, viewModel.assistantName),
                listening = sttState.isRecording,
                canSend = inputText.isNotBlank(),
                onPlusClick = onOpenInApp,
                onMicClick = {
                    if (sttState.isRecording) stt.stop() else startListening()
                },
                onSendClick = { doSend() },
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReplyPanel(
    assistantName: String,
    state: AssistantOverlayVM.OverlayState,
    onOpenInApp: () -> Unit,
) {
    val scroll = rememberScrollState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 460.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        CircleShape,
                    ),
            )
            Spacer(Modifier.height(14.dp))

            // Header: avatar + activity pill + open-in-app
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = assistantName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                ActivityPill(state = state)
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onOpenInApp,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.assistant_overlay_open_in_app),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

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
                            .fillMaxWidth()
                            .verticalScroll(scroll),
                    ) {
                        Text(
                            text = state.responseText.ifBlank { "…" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun ActivityPill(state: AssistantOverlayVM.OverlayState) {
    val label = when (state) {
        is AssistantOverlayVM.OverlayState.Generating -> "Thinking"
        is AssistantOverlayVM.OverlayState.Result -> if (state.isStreaming) "Writing" else "Done"
        is AssistantOverlayVM.OverlayState.Error -> "Error"
        else -> ""
    }
    if (label.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun OverlayInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    hint: String,
    listening: Boolean,
    canSend: Boolean,
    onPlusClick: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            RoundIconButton(
                icon = Icons.Rounded.Add,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                onClick = onPlusClick,
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (canSend) {
                RoundIconButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = onSendClick,
                )
            } else {
                RoundIconButton(
                    icon = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentColor = if (listening) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    containerColor = if (listening) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = onMicClick,
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Slow, organic Material You edge glow. Breathes on its own and intensifies while the
 * assistant is listening or speaking. A synthetic envelope stands in for true TTS
 * amplitude for now — see the wrap-up notes for wiring the Visualizer API.
 */
@Composable
private fun AssistantGlow(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "glow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPhase",
    )
    val intensity by animateFloatAsState(
        targetValue = if (active) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "glowIntensity",
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier.drawBehind {
            val h = size.height
            val w = size.width
            val radius = h * (0.55f + 0.12f * phase)
            // Bottom glow (where the UI rises from)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.42f * intensity),
                        Color.Transparent,
                    ),
                    center = Offset(w * (0.35f + 0.3f * phase), h * 1.02f),
                    radius = radius,
                ),
            )
            // Top accent glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = 0.30f * intensity),
                        Color.Transparent,
                    ),
                    center = Offset(w * (0.7f - 0.3f * phase), h * -0.02f),
                    radius = radius * 0.9f,
                ),
            )
        },
    )
}
