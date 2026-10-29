package me.rerere.rikkahub.ui.pages.studio

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.repository.RecordingState
import me.rerere.rikkahub.data.repository.VoiceMemoItem
import me.rerere.rikkahub.data.repository.VoiceMemoRepository
import me.rerere.rikkahub.data.repository.VoicePlaybackManager
import me.rerere.rikkahub.data.repository.VoiceRecordingManager
import me.rerere.rikkahub.data.repository.VoiceTranscriptSegment
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.plus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMemosPage() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val recordingManager = koinInject<VoiceRecordingManager>()
    val playbackManager = koinInject<VoicePlaybackManager>()
    val memoRepository = koinInject<VoiceMemoRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val providerManager = koinInject<ProviderManager>()
    val secretKeyManager = koinInject<SecretKeyManager>()
    val httpClient = koinInject<OkHttpClient>()

    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val recordings by memoRepository.recordingsFlow.collectAsStateWithLifecycle()

    val recState by recordingManager.recordingState.collectAsStateWithLifecycle()
    val elapsedTimeMs by recordingManager.elapsedTimeMs.collectAsStateWithLifecycle()
    val currentAmp by recordingManager.currentAmplitude.collectAsStateWithLifecycle()

    val isPlaying by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val playPositionMs by playbackManager.currentPositionMs.collectAsStateWithLifecycle()
    val playDurationMs by playbackManager.durationMs.collectAsStateWithLifecycle()
    val playSpeed by playbackManager.speed.collectAsStateWithLifecycle()
    val currentPlayingFile by playbackManager.currentFile.collectAsStateWithLifecycle()

    var selectedMemo by remember { mutableStateOf<VoiceMemoItem?>(null) }
    var memoToDelete by remember { mutableStateOf<VoiceMemoItem?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    var isProcessingAI by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            playbackManager.stop()
        }
    }

    // Auto-select latest recorded memo
    LaunchedEffect(recordings) {
        if (selectedMemo == null && recordings.isNotEmpty()) {
            selectedMemo = recordings.first()
        } else if (selectedMemo != null) {
            selectedMemo = recordings.firstOrNull { it.id == selectedMemo?.id }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.studio_voice_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            memoRepository.refresh()
                            toaster.show("Refreshed recordings from device")
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = lazyListState,
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================================================================
            // 1. TOP HERO CARD: READY TO RECORD (DEVICE VOICE RECORDER)
            // ================================================================
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = AppShapes.ButtonPill,
                            color = when (recState) {
                                RecordingState.Recording -> MaterialTheme.colorScheme.errorContainer
                                RecordingState.Paused -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (recState) {
                                                RecordingState.Recording -> MaterialTheme.colorScheme.error
                                                RecordingState.Paused -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.outline
                                            }
                                        )
                                )
                                Text(
                                    text = when (recState) {
                                        RecordingState.Recording -> "Recording Active"
                                        RecordingState.Paused -> "Recording Paused"
                                        RecordingState.Stopping -> "Finalizing Audio..."
                                        RecordingState.Idle -> "Ready to Record"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (recState) {
                                        RecordingState.Recording -> MaterialTheme.colorScheme.onErrorContainer
                                        RecordingState.Paused -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        // Formatted Duration Timer
                        val seconds = (elapsedTimeMs / 1000) % 60
                        val minutes = (elapsedTimeMs / 1000) / 60
                        val hours = minutes / 60
                        val timeString = if (hours > 0) {
                            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes % 60, seconds)
                        } else {
                            String.format(Locale.US, "%02d:%02d", minutes, seconds)
                        }

                        Text(
                            text = if (recState != RecordingState.Idle) timeString else "00:00",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Live Dynamic Amplitude Waveform
                        Row(
                            modifier = Modifier
                                .height(44.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val count = 16
                            for (i in 0 until count) {
                                val distance = Math.abs(i - count / 2f) / (count / 2f)
                                val scale = (1f - distance * 0.45f).coerceIn(0.2f, 1f)
                                val ampHeight = if (recState == RecordingState.Recording) {
                                    (8f + currentAmp * 36f * scale).coerceIn(6f, 44f)
                                } else if (recState == RecordingState.Paused) {
                                    8f
                                } else {
                                    6f
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.5.dp)
                                        .width(4.dp)
                                        .height(ampHeight.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (recState == RecordingState.Recording) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            }
                                        )
                                )
                            }
                        }

                        // Recorder Controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Discard button
                            IconButton(
                                onClick = {
                                    if (recState != RecordingState.Idle) {
                                        haptics.perform(HapticPattern.Tick)
                                        recordingManager.discardRecording()
                                        toaster.show("Recording discarded")
                                    }
                                },
                                enabled = recState != RecordingState.Idle
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Discard",
                                    tint = if (recState != RecordingState.Idle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                                )
                            }

                            // Big Record / Stop Button
                            Surface(
                                shape = CircleShape,
                                color = if (recState == RecordingState.Recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clickable {
                                        haptics.perform(HapticPattern.Pop)
                                        if (recState == RecordingState.Idle) {
                                            recordingManager.startRecording().onSuccess {
                                                toaster.show("Recording saved to /sdcard/Recordings/LastChat/")
                                            }.onFailure {
                                                toaster.show("Failed to start recording: ${it.message}")
                                            }
                                        } else {
                                            recordingManager.stopRecording().onSuccess { file ->
                                                toaster.show("Audio saved: ${file.name}")
                                                memoRepository.refresh()
                                            }.onFailure {
                                                toaster.show("Error saving: ${it.message}")
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (recState != RecordingState.Idle) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                        contentDescription = null,
                                        tint = if (recState == RecordingState.Recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Pause / Resume button
                            IconButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    if (recState == RecordingState.Recording) {
                                        recordingManager.pauseRecording()
                                    } else if (recState == RecordingState.Paused) {
                                        recordingManager.resumeRecording()
                                    }
                                },
                                enabled = recState == RecordingState.Recording || recState == RecordingState.Paused
                            ) {
                                Icon(
                                    imageVector = if (recState == RecordingState.Paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = "Pause/Resume",
                                    tint = if (recState != RecordingState.Idle) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 2. SELECTED AUDIO CONTROLLER & AI ACTIONS
            // ================================================================
            val activeMemo = selectedMemo
            if (activeMemo != null) {
                item {
                    Text(
                        text = "Now Playing & AI Tools",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = AppShapes.CardLarge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = activeMemo.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${activeMemo.audioFile.name} • ${Formatter.formatFileSize(context, activeMemo.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { memoToDelete = activeMemo }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Scrubber Slider
                            val effectiveDur = if (playDurationMs > 0L) playDurationMs else activeMemo.durationMs
                            val currentFraction = if (effectiveDur > 0L && currentPlayingFile == activeMemo.audioFile) {
                                (playPositionMs.toFloat() / effectiveDur).coerceIn(0f, 1f)
                            } else 0f

                            Slider(
                                value = currentFraction,
                                onValueChange = { fraction ->
                                    if (currentPlayingFile == activeMemo.audioFile) {
                                        playbackManager.seekToFraction(fraction)
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Time Labels
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentSec = if (currentPlayingFile == activeMemo.audioFile) playPositionMs / 1000 else 0L
                                val totalSec = effectiveDur / 1000
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d", currentSec / 60, currentSec % 60),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Player Transport Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { playbackManager.skipBy(-15000L) }) {
                                    Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind 15s")
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable {
                                            haptics.perform(HapticPattern.Tick)
                                            if (currentPlayingFile == activeMemo.audioFile && isPlaying) {
                                                playbackManager.pause()
                                            } else {
                                                playbackManager.play(activeMemo.audioFile)
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (currentPlayingFile == activeMemo.audioFile && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }

                                IconButton(onClick = { playbackManager.skipBy(15000L) }) {
                                    Icon(Icons.Rounded.FastForward, contentDescription = "Forward 15s")
                                }

                                // Speed Multiplier
                                TextButton(
                                    onClick = {
                                        val nextSpeed = when (playSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        playbackManager.setSpeed(nextSpeed)
                                    }
                                ) {
                                    Text(
                                        text = "${playSpeed}x",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // AI Action Buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Transcribe Button
                                AssistChip(
                                    onClick = {
                                        if (!isTranscribing) {
                                            isTranscribing = true
                                            haptics.perform(HapticPattern.Pop)
                                            scope.launch {
                                                runCatching {
                                                    val transcript = transcribeAudio(
                                                        file = activeMemo.audioFile,
                                                        settings = settings,
                                                        httpClient = httpClient,
                                                        secretKeyManager = secretKeyManager
                                                    )
                                                    memoRepository.saveTranscript(activeMemo.id, transcript)
                                                    toaster.show("Transcription complete & synced to workspace!")
                                                }.onFailure {
                                                    toaster.show("Transcription failed: ${it.message}")
                                                }
                                                isTranscribing = false
                                            }
                                        }
                                    },
                                    label = {
                                        if (isTranscribing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(if (activeMemo.isTranscribed) "Re-Transcribe" else "Transcribe")
                                        }
                                    },
                                    leadingIcon = {
                                        if (!isTranscribing) {
                                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )

                                // Summarize Button
                                AssistChip(
                                    onClick = {
                                        if (!isProcessingAI) {
                                            val text = activeMemo.transcript
                                            if (text.isNullOrBlank()) {
                                                toaster.show("Please transcribe the audio first!")
                                                return@AssistChip
                                            }
                                            isProcessingAI = true
                                            haptics.perform(HapticPattern.Pop)
                                            scope.launch {
                                                runCatching {
                                                    val (smartTitle, summary, bullets, actions) = analyzeTranscript(
                                                        transcript = text,
                                                        settings = settings,
                                                        providerManager = providerManager,
                                                        secretKeyManager = secretKeyManager
                                                    )
                                                    memoRepository.saveAnalysis(
                                                        id = activeMemo.id,
                                                        title = smartTitle,
                                                        summary = summary,
                                                        bulletPoints = bullets,
                                                        actionItems = actions
                                                    )
                                                    toaster.show("AI Summary saved & synced to /workspace/transcripts/!")
                                                }.onFailure {
                                                    toaster.show("AI Analysis failed: ${it.message}")
                                                }
                                                isProcessingAI = false
                                            }
                                        }
                                    },
                                    label = {
                                        if (isProcessingAI) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text("Summarize & Analyze")
                                        }
                                    },
                                    leadingIcon = {
                                        if (!isProcessingAI) {
                                            Icon(Icons.Rounded.FormatListBulleted, null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )

                                // Send to Story Studio
                                AssistChip(
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        toaster.show("Exporting transcript into Story Studio Outline...")
                                        navController.navigate(Screen.StoryStudio)
                                    },
                                    label = { Text("Story Studio") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }

                            // Summary & Bullet Points Display
                            if (!activeMemo.summary.isNullOrBlank() || activeMemo.bulletPoints.isNotEmpty()) {
                                Surface(
                                    shape = AppShapes.CardSmall,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!activeMemo.summary.isNullOrBlank()) {
                                            Text(
                                                text = "Executive Summary",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = activeMemo.summary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (activeMemo.bulletPoints.isNotEmpty()) {
                                            Text(
                                                text = "Key Takeaways",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            activeMemo.bulletPoints.forEach { pt ->
                                                Text(
                                                    text = "• $pt",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Transcript View
                            if (!activeMemo.transcript.isNullOrBlank()) {
                                Surface(
                                    shape = AppShapes.CardSmall,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Transcript",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = activeMemo.transcript,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 20.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 3. RECORDINGS LIBRARY LIST (FROM /sdcard/Recordings/LastChat/)
            // ================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Recordings (${recordings.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(
                        text = "/sdcard/Recordings/LastChat/",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (recordings.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = AppShapes.CardMedium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No voice memos recorded yet. Tap the red record button above to start your first note.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(recordings, key = { it.id }) { memo ->
                    val isSelected = selectedMemo?.id == memo.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = AppShapes.CardMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.perform(HapticPattern.Tick)
                                selectedMemo = memo
                                playbackManager.play(memo.audioFile)
                            }
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    text = memo.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                val totalSec = memo.durationMs / 1000
                                val timeStr = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
                                Text(
                                    text = "$timeStr • ${Formatter.formatFileSize(context, memo.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isSelected && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (memo.isTranscribed) {
                                        Surface(
                                            shape = AppShapes.ButtonPill,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "Transcribed",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (memo.isSummarized) {
                                        Surface(
                                            shape = AppShapes.ButtonPill,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "AI Ready",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    memoToDelete?.let { memo ->
        LastChatDestructiveConfirmDialog(
            title = "Delete Recording",
            consequence = "Are you sure you want to delete \"${memo.title}\"? The audio file will be permanently removed from your device storage.",
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
            onDismiss = { memoToDelete = null },
            onConfirm = {
                scope.launch {
                    memoRepository.deleteRecording(memo)
                    if (selectedMemo?.id == memo.id) {
                        playbackManager.stop()
                        selectedMemo = null
                    }
                    toaster.show("Recording deleted")
                    memoToDelete = null
                }
            }
        )
    }
}

/**
 * Transcribes audio file using the configured STT model via OpenAI-compatible endpoint.
 */
private suspend fun transcribeAudio(
    file: File,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    httpClient: OkHttpClient,
    secretKeyManager: SecretKeyManager
): String = withContext(Dispatchers.IO) {
    val model = settings.findModelById(settings.sttModelId)
        ?: return@withContext "Error: No STT model selected in Default Models settings."
    val rawProvider = model.findProvider(settings.providers)
        ?: return@withContext "Error: Provider not found for STT model ${model.displayName}."
    val provider = secretKeyManager.populateProviderSecrets(rawProvider)

    val (baseUrl, apiKey) = when (val p = provider) {
        is me.rerere.ai.provider.ProviderSetting.OpenAI -> p.baseUrl to p.apiKey
        is me.rerere.ai.provider.ProviderSetting.Claude -> "https://api.anthropic.com" to p.apiKey
        is me.rerere.ai.provider.ProviderSetting.Google -> "https://generativelanguage.googleapis.com" to p.apiKey
        else -> "" to ""
    }

    if (baseUrl.isBlank() || apiKey.isBlank()) {
        return@withContext "Error: Provider baseUrl or apiKey is missing."
    }

    val url = if (baseUrl.endsWith("/audio/transcriptions")) baseUrl else "${baseUrl.trimEnd('/')}/audio/transcriptions"

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file",
            file.name,
            file.asRequestBody("audio/m4a".toMediaType())
        )
        .addFormDataPart("model", model.modelId)
        .build()

    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .post(requestBody)
        .build()

    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) {
        throw IllegalStateException("STT API returned HTTP ${response.code}: ${response.body?.string()}")
    }

    val bodyStr = response.body?.string() ?: ""
    val json = JSONObject(bodyStr)
    json.optString("text").ifBlank {
        json.optString("transcript", bodyStr)
    }
}

/**
 * Prompts the LLM to extract a smart title, executive summary, bullet points, and action items.
 */
private suspend fun analyzeTranscript(
    transcript: String,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    providerManager: ProviderManager,
    secretKeyManager: SecretKeyManager
): Quadruple<String?, String?, List<String>, List<String>> = withContext(Dispatchers.IO) {
    val model = settings.findModelById(settings.summarizerModelId)
        ?: settings.findModelById(settings.chatModelId)
        ?: return@withContext Quadruple(null, null, emptyList(), emptyList())
    val rawProvider = model.findProvider(settings.providers)
        ?: return@withContext Quadruple(null, null, emptyList(), emptyList())
    val provider = secretKeyManager.populateProviderSecrets(rawProvider)
    val handler = providerManager.getProviderByType(provider)

    val prompt = """
        You are an intelligent audio notes assistant. Analyze the following speech transcript.
        Respond ONLY with a valid JSON object matching this exact schema:
        {
          "title": "Smart 3-5 word concise title",
          "summary": "2-sentence executive summary of what was spoken",
          "bullet_points": ["Key point 1", "Key point 2", "Key point 3"],
          "action_items": ["Action item 1", "Action item 2"]
        }
        
        Transcript:
        $transcript
    """.trimIndent()

    val resp = handler.generateText(
        providerSetting = provider,
        messages = listOf(UIMessage.user(prompt)),
        params = TextGenerationParams(
            model = model,
            temperature = 0.2f,
            maxTokens = 800
        )
    )

    val text = resp.choices.firstOrNull()?.message?.toContentText().orEmpty()
    val cleanJson = text.substringAfter("{").substringBeforeLast("}")
    val json = runCatching { JSONObject("{$cleanJson}") }.getOrNull()

    val title = json?.optString("title")
    val summary = json?.optString("summary")
    val bullets = json?.optJSONArray("bullet_points")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList()
    val actions = json?.optJSONArray("action_items")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList()

    Quadruple(title, summary, bullets, actions)
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
