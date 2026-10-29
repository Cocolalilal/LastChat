package me.rerere.rikkahub.ui.pages.studio

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.BeatStatus
import me.rerere.rikkahub.data.repository.StoryBeat
import me.rerere.rikkahub.data.repository.StoryProject
import me.rerere.rikkahub.data.repository.StoryStatus
import me.rerere.rikkahub.data.repository.StoryStudioRepository
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryStudioPage() {
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()

    val storyRepo = koinInject<StoryStudioRepository>()
    val settingsStore = koinInject<SettingsStore>()

    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val projects by storyRepo.projectsFlow.collectAsStateWithLifecycle()

    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    val currentProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var activeProseChunk by remember { mutableStateOf("") }
    var steerText by remember { mutableStateOf("") }

    var showNewStoryDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newPremise by remember { mutableStateOf("") }
    var newGenre by remember { mutableStateOf("Cyberpunk") }
    var newTone by remember { mutableStateOf("Gritty") }
    var newArchitectModelId by remember { mutableStateOf<Uuid?>(null) }
    var newProseModelId by remember { mutableStateOf<Uuid?>(null) }

    LaunchedEffect(projects) {
        if (selectedProjectId == null && projects.isNotEmpty()) {
            selectedProjectId = projects.first().id
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = currentProject?.title ?: stringResource(R.string.studio_story_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            showNewStoryDialog = true
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "New Story")
                    }
                }
            )
        },
        bottomBar = {
            if (currentProject != null && selectedTabIndex == 0) {
                val currentBeat = currentProject.bible?.beats?.getOrNull(currentProject.currentBeatIndex)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 6.dp,
                    shape = AppShapes.BottomSheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                )
                                Column {
                                    Text(
                                        text = currentBeat?.let { "Beat ${it.index + 1}: ${it.title}" } ?: "Novel Completed",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isGenerating) {
                                            "Writing prose chunk • ${currentProject.totalWords} words total"
                                        } else {
                                            "Ready • ${currentProject.totalWords} words total"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        if (isGenerating) {
                                            isGenerating = false
                                            toaster.show("Generation paused")
                                        } else {
                                            val beatIdx = currentProject.currentBeatIndex
                                            if (currentProject.bible == null) {
                                                toaster.show("Story Bible not generated yet!")
                                                return@IconButton
                                            }
                                            if (beatIdx >= (currentProject.bible.beats.size)) {
                                                toaster.show("All beats already completed!")
                                                return@IconButton
                                            }
                                            isGenerating = true
                                            scope.launch {
                                                storyRepo.generateBeatProse(
                                                    projectId = currentProject.id,
                                                    beatIndex = beatIdx,
                                                    steerNote = steerText.takeIf { it.isNotBlank() },
                                                    onChunk = { delta ->
                                                        activeProseChunk += delta
                                                    }
                                                ).onSuccess {
                                                    toaster.show("Beat ${beatIdx + 1} finished & saved to workspace!")
                                                    activeProseChunk = ""
                                                    steerText = ""
                                                }.onFailure {
                                                    toaster.show("Generation error: ${it.message}")
                                                }
                                                isGenerating = false
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isGenerating) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        val nextIdx = currentProject.currentBeatIndex + 1
                                        scope.launch {
                                            storyRepo.saveProject(currentProject.copy(currentBeatIndex = nextIdx))
                                            toaster.show("Advanced to Beat ${nextIdx + 1}")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FastForward,
                                        contentDescription = "Advance Beat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        val targetWords = currentBeat?.targetWords ?: 1000
                        val progressFraction = if (targetWords > 0) {
                            ((currentBeat?.wordsWritten ?: 0).toFloat() / targetWords).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(AppShapes.ButtonPill),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = steerText,
                                onValueChange = { steerText = it },
                                placeholder = {
                                    Text(
                                        text = "Steer next beat: e.g. 'Maya hesitates before shooting'...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                shape = AppShapes.ButtonPill,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (steerText.isNotBlank()) {
                                        haptics.perform(HapticPattern.Tick)
                                        toaster.show("Director steer instruction queued!")
                                    }
                                },
                                shape = AppShapes.ButtonPill,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Queue", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding)
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            if (currentProject == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "No Story Manuscript Found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap below to create a new persistent novel. The Architect model will build a Story Bible and sequential beats.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showNewStoryDialog = true },
                            shape = AppShapes.ButtonPill
                        ) {
                            Text("Create Story")
                        }
                    }
                }
            } else {
                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            selectedTabIndex = 0
                        },
                        text = { Text("Manuscript", style = MaterialTheme.typography.titleSmall) },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            selectedTabIndex = 1
                        },
                        text = { Text("Story Bible & Beats", style = MaterialTheme.typography.titleSmall) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }

                if (selectedTabIndex == 0) {
                    // ========================================================
                    // MANUSCRIPT READER VIEW
                    // ========================================================
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
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
                                        .padding(22.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = currentProject.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    val beats = currentProject.bible?.beats ?: emptyList()
                                    if (beats.isEmpty()) {
                                        Text(
                                            text = "Story Bible not generated yet. Go to the Story Bible tab to generate character sheets and beats outline.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        beats.forEach { beat ->
                                            if (beat.content.isNotBlank()) {
                                                // Beat Divider Pill
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.weight(1f),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    )
                                                    Surface(
                                                        shape = AppShapes.ButtonPill,
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                    ) {
                                                        Text(
                                                            text = "Beat ${beat.index + 1}: ${beat.title} • ${beat.wordsWritten} Words",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        modifier = Modifier.weight(1f),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    )
                                                }

                                                Text(
                                                    text = beat.content,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    lineHeight = 26.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                                )
                                            }
                                        }

                                        // Streaming active prose chunk
                                        if (isGenerating && activeProseChunk.isNotBlank()) {
                                            Text(
                                                text = activeProseChunk,
                                                style = MaterialTheme.typography.bodyLarge,
                                                lineHeight = 26.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    }
                } else {
                    // ========================================================
                    // STORY BIBLE & BEATS TAB
                    // ========================================================
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val bible = currentProject.bible
                        if (bible == null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    shape = AppShapes.CardLarge,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = "Story Bible Not Built Yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Premise: ${currentProject.premise}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = {
                                                haptics.perform(HapticPattern.Pop)
                                                scope.launch {
                                                    toaster.show("Architect model building Story Bible & Beats...")
                                                    storyRepo.generateStoryBible(currentProject.id).onSuccess {
                                                        toaster.show("Story Bible generated!")
                                                    }.onFailure {
                                                        toaster.show("Error: ${it.message}")
                                                    }
                                                }
                                            },
                                            shape = AppShapes.ButtonPill
                                        ) {
                                            Text("Generate Story Bible & Beats")
                                        }
                                    }
                                }
                            }
                        } else {
                            // Characters Card
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    shape = AppShapes.CardLarge,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Story Bible • Characters & Lore",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        bible.characters.forEach { char ->
                                            Column {
                                                Text(
                                                    text = "${char.name} (${char.role})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = char.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Milestone Beats Outline
                            item {
                                Text(
                                    text = "Milestone Beats (Autonomous Chain)",
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
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        bible.beats.forEach { beat ->
                                            val isDone = beat.status == BeatStatus.Done || beat.content.isNotBlank()
                                            val isActive = currentProject.currentBeatIndex == beat.index && !isDone

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(AppShapes.CardSmall)
                                                    .background(
                                                        if (isActive) {
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                                                        }
                                                    )
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isDone) MaterialTheme.colorScheme.primary else if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (isDone) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        } else {
                                                            Text(
                                                                text = "${beat.index + 1}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Beat ${beat.index + 1}: ${beat.title}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = beat.objective,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Text(
                                                    text = "${beat.targetWords}w",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New Story Creation Dialog
    if (showNewStoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewStoryDialog = false },
            title = { Text("Create New Story") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Story Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newPremise,
                        onValueChange = { newPremise = it },
                        label = { Text("Premise / Core Idea") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Architect Model:", style = MaterialTheme.typography.labelMedium)
                        ModelSelector(
                            modelId = newArchitectModelId ?: settings.chatModelId,
                            type = ModelType.CHAT,
                            onSelect = { newArchitectModelId = it.id },
                            providers = settings.providers
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Prose Model:", style = MaterialTheme.typography.labelMedium)
                        ModelSelector(
                            modelId = newProseModelId ?: settings.chatModelId,
                            type = ModelType.CHAT,
                            onSelect = { newProseModelId = it.id },
                            providers = settings.providers
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPremise.isNotBlank()) {
                            scope.launch {
                                val p = storyRepo.createProject(
                                    title = newTitle.ifBlank { "Untitled Story" },
                                    premise = newPremise,
                                    genre = newGenre,
                                    tone = newTone,
                                    architectModelId = newArchitectModelId ?: settings.chatModelId,
                                    proseModelId = newProseModelId ?: settings.chatModelId,
                                )
                                selectedProjectId = p.id
                                showNewStoryDialog = false
                                toaster.show("Story created! Generating Story Bible...")
                                storyRepo.generateStoryBible(p.id)
                            }
                        }
                    }
                ) {
                    Text("Build Story Bible")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewStoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
