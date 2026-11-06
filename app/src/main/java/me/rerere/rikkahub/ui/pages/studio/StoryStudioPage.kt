package me.rerere.rikkahub.ui.pages.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import me.rerere.rikkahub.utils.writeClipboardText
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.repository.BeatStatus
import me.rerere.rikkahub.data.repository.StoryBeat
import me.rerere.rikkahub.data.repository.StoryDirectorMessage
import me.rerere.rikkahub.data.repository.StoryProject
import me.rerere.rikkahub.data.repository.StoryStatus
import me.rerere.rikkahub.data.repository.StoryStudioRepository
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryStudioPage(
    initialStoryId: String? = null,
) {
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val directorChatListState = rememberLazyListState()

    val storyRepo = koinInject<StoryStudioRepository>()
    val settingsStore = koinInject<SettingsStore>()

    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val projects by storyRepo.projectsFlow.collectAsStateWithLifecycle()

    val activeAssistant = remember(settings.assistantId, settings.assistants) {
        settings.getAssistantById(settings.assistantId) ?: settings.assistants.firstOrNull()
    }

    var selectedProjectId by remember(initialStoryId) { mutableStateOf<String?>(initialStoryId) }
    val currentProject = projects.firstOrNull { it.id == selectedProjectId }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var isGeneratingBible by remember { mutableStateOf(false) }
    var activeProseChunk by remember { mutableStateOf("") }
    var steerText by remember { mutableStateOf("") }

    // Director chat state
    var directorInputText by remember { mutableStateOf("") }
    var isDirectorStreaming by remember { mutableStateOf(false) }
    var activeDirectorChunk by remember { mutableStateOf("") }

    // Dialog state
    var showNewStoryDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newPremise by remember { mutableStateOf("") }
    var newGenre by remember { mutableStateOf("") }
    var newTone by remember { mutableStateOf("") }
    var newArchitectModelId by remember { mutableStateOf<Uuid?>(null) }
    var newProseModelId by remember { mutableStateOf<Uuid?>(null) }

    var projectToDelete by remember { mutableStateOf<StoryProject?>(null) }

    // Auto-scroll director chat when new messages or chunks arrive
    LaunchedEffect(currentProject?.directorChat?.size, activeDirectorChunk) {
        if (selectedTabIndex == 2 && currentProject != null && currentProject.directorChat.isNotEmpty()) {
            directorChatListState.animateScrollToItem(currentProject.directorChat.size)
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = currentProject?.title ?: stringResource(R.string.studio_story_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (currentProject != null) {
                        IconButton(
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                selectedProjectId = null
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Stories")
                        }
                    } else {
                        BackButton()
                    }
                },
                actions = {
                    if (currentProject != null) {
                        IconButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                projectToDelete = currentProject
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Story", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                newTitle = ""
                                newPremise = ""
                                newGenre = ""
                                newTone = ""
                                showNewStoryDialog = true
                            }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "New Story")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentProject != null && selectedTabIndex == 0) {
                val currentBeat = currentProject.bible?.beats?.getOrNull(currentProject.currentBeatIndex)
                val isAllDone = currentProject.bible?.let { b ->
                    b.beats.isNotEmpty() && currentProject.currentBeatIndex >= b.beats.size
                } ?: false

                val statusTitle = when {
                    isGeneratingBible -> "Generating Story Bible..."
                    currentProject.bible == null -> "Story Bible Not Built"
                    isAllDone -> "Novel Completed"
                    currentBeat != null -> "Beat ${currentBeat.index + 1}: ${currentBeat.title}"
                    else -> "Outlining"
                }

                val statusSubtitle = when {
                    isGeneratingBible -> "Architect drafting characters & outline..."
                    isGenerating -> "Writing prose chunk • ${currentProject.totalWords} words total"
                    !currentProject.lastError.isNullOrBlank() -> "Error: ${currentProject.lastError}"
                    isAllDone -> "All beats written • ${currentProject.totalWords} words total"
                    else -> "Ready • ${currentProject.totalWords} words total"
                }

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
                                        .background(
                                            when {
                                                isGenerating || isGeneratingBible -> MaterialTheme.colorScheme.primary
                                                !currentProject.lastError.isNullOrBlank() -> MaterialTheme.colorScheme.error
                                                isAllDone -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.outline
                                            }
                                        )
                                )
                                Column {
                                    Text(
                                        text = statusTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!currentProject.lastError.isNullOrBlank() && !isGenerating && !isGeneratingBible) {
                                            MaterialTheme.colorScheme.error
                                        } else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = statusSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!currentProject.lastError.isNullOrBlank() && !isGenerating && !isGeneratingBible) {
                                            MaterialTheme.colorScheme.error
                                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isGenerating || isGeneratingBible) {
                                    // Cancel Button
                                    IconButton(
                                        onClick = {
                                            haptics.perform(HapticPattern.Pop)
                                            storyRepo.cancelGeneration(currentProject.id)
                                            isGenerating = false
                                            isGeneratingBible = false
                                            toaster.show("Generation cancelled")
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Stop,
                                            contentDescription = "Cancel Generation",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            haptics.perform(HapticPattern.Tick)
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
                                                    toaster.show("Beat ${beatIdx + 1} finished & saved!")
                                                    activeProseChunk = ""
                                                    steerText = ""
                                                }.onFailure {
                                                    toaster.show("Generation error: ${it.message}")
                                                }
                                                isGenerating = false
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Start Beat",
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
                                        text = "Steer next beat: e.g. 'Focus on dialogue and sensory details'...",
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
                                        toaster.show("Director steer note applied!")
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
                // ========================================================
                // FRESH STORY LIBRARY & CREATIONS BROWSER
                // ========================================================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero New Story Action Card
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Start a New Story",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Craft multi-beat novels with an AI Story Bible and real-time assistant director steering.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        newTitle = ""
                                        newPremise = ""
                                        newGenre = ""
                                        newTone = ""
                                        showNewStoryDialog = true
                                    },
                                    shape = AppShapes.ButtonPill
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                                        Text("Create Story", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Your Stories (${projects.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    if (projects.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = AppShapes.CardLarge,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No stories created yet. Tap 'Create Story' above to start.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(projects, key = { it.id }) { project ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = AppShapes.CardLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        selectedProjectId = project.id
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = project.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Surface(
                                            shape = AppShapes.ButtonPill,
                                            color = when (project.status) {
                                                StoryStatus.Completed -> MaterialTheme.colorScheme.tertiaryContainer
                                                StoryStatus.Writing -> MaterialTheme.colorScheme.primaryContainer
                                                StoryStatus.Outlining -> MaterialTheme.colorScheme.secondaryContainer
                                                StoryStatus.Paused -> MaterialTheme.colorScheme.surfaceContainerHighest
                                            }
                                        ) {
                                            Text(
                                                text = project.status.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    val genreTone = listOfNotNull(
                                        project.genre.takeIf { it.isNotBlank() },
                                        project.tone.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")

                                    if (genreTone.isNotBlank()) {
                                        Text(
                                            text = genreTone,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Text(
                                        text = project.premise,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val beatsCount = project.bible?.beats?.size ?: 0
                                        val completed = project.bible?.beats?.count { it.status == BeatStatus.Done } ?: 0
                                        Text(
                                            text = if (beatsCount > 0) "Beat $completed/$beatsCount • ${project.totalWords} words" else "${project.totalWords} words",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        IconButton(
                                            onClick = {
                                                haptics.perform(HapticPattern.Pop)
                                                projectToDelete = project
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete Story",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ========================================================
                // FULL STORY WORKSPACE TABS
                // ========================================================
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
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
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
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            selectedTabIndex = 2
                        },
                        text = { Text("Director Chat", style = MaterialTheme.typography.titleSmall) },
                        icon = {
                            Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                }

                if (selectedTabIndex == 0) {
                    // ========================================================
                    // TAB 0: MANUSCRIPT READER VIEW
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
                                SelectionContainer {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                        Text(
                                            text = currentProject.title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Serif,
                                            modifier = Modifier.weight(1f)
                                        )

                                        val beats = currentProject.bible?.beats ?: emptyList()
                                        if (beats.any { it.content.isNotBlank() }) {
                                            IconButton(
                                                onClick = {
                                                    haptics.perform(HapticPattern.Tick)
                                                    val manuscriptText = buildString {
                                                        appendLine("# ${currentProject.title}")
                                                        appendLine("Genre: ${currentProject.genre} • Tone: ${currentProject.tone}")
                                                        appendLine()
                                                        beats.forEach { beat ->
                                                            if (beat.content.isNotBlank()) {
                                                                appendLine("## Beat ${beat.index + 1}: ${beat.title}")
                                                                appendLine()
                                                                appendLine(beat.content)
                                                                appendLine()
                                                            }
                                                        }
                                                    }.trim()
                                                    context.writeClipboardText(manuscriptText)
                                                    toaster.show("Manuscript copied to clipboard")
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ContentCopy,
                                                    contentDescription = "Copy manuscript",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    val beats = currentProject.bible?.beats ?: emptyList()
                                    if (beats.isEmpty()) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Premise: ${currentProject.premise}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            if (!currentProject.lastError.isNullOrBlank()) {
                                                Surface(
                                                    shape = AppShapes.CardSmall,
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = "Generation Error",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                        Text(
                                                            text = currentProject.lastError ?: "",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        haptics.perform(HapticPattern.Pop)
                                                        scope.launch {
                                                            isGeneratingBible = true
                                                            toaster.show("Architect model building Story Bible & Beats...")
                                                            storyRepo.generateStoryBible(currentProject.id).onSuccess {
                                                                toaster.show("Story Bible generated successfully!")
                                                            }.onFailure {
                                                                toaster.show("Error: ${it.message}")
                                                            }
                                                            isGeneratingBible = false
                                                        }
                                                    },
                                                    enabled = !isGeneratingBible,
                                                    shape = AppShapes.ButtonPill
                                                ) {
                                                    if (isGeneratingBible) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Generating...")
                                                    } else {
                                                        Text(if (currentProject.lastError != null) "Retry Generating Story Bible" else "Generate Story Bible")
                                                    }
                                                }

                                                if (isGeneratingBible) {
                                                    Button(
                                                        onClick = {
                                                            haptics.perform(HapticPattern.Pop)
                                                            storyRepo.cancelGeneration(currentProject.id)
                                                            isGeneratingBible = false
                                                            toaster.show("Generation cancelled")
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                        ),
                                                        shape = AppShapes.ButtonPill
                                                    ) {
                                                        Text("Cancel")
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        SelectionContainer {
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Beat ${beat.index + 1}: ${beat.title} • ${beat.wordsWritten} Words",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                    )
                                                                    IconButton(
                                                                        onClick = {
                                                                            haptics.perform(HapticPattern.Tick)
                                                                            context.writeClipboardText(beat.content)
                                                                            toaster.show("Beat ${beat.index + 1} copied to clipboard")
                                                                        },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Rounded.ContentCopy,
                                                                            contentDescription = "Copy beat",
                                                                            modifier = Modifier.size(14.dp),
                                                                            tint = MaterialTheme.colorScheme.primary
                                                                        )
                                                                    }
                                                                }
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
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(80.dp))
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTabIndex == 1) {
                    // ========================================================
                    // TAB 1: STORY BIBLE & BEATS TAB
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
                                            text = if (isGeneratingBible) "Generating Story Bible..." else "Story Bible Not Built Yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Premise: ${currentProject.premise}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (!currentProject.lastError.isNullOrBlank()) {
                                            Surface(
                                                shape = AppShapes.CardSmall,
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        text = "Generation Error",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                    Text(
                                                        text = currentProject.lastError ?: "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    haptics.perform(HapticPattern.Pop)
                                                    scope.launch {
                                                        isGeneratingBible = true
                                                        toaster.show("Architect model building Story Bible & Beats...")
                                                        storyRepo.generateStoryBible(currentProject.id).onSuccess {
                                                            toaster.show("Story Bible generated successfully!")
                                                        }.onFailure {
                                                            toaster.show("Error: ${it.message}")
                                                        }
                                                        isGeneratingBible = false
                                                    }
                                                },
                                                enabled = !isGeneratingBible,
                                                shape = AppShapes.ButtonPill
                                            ) {
                                                if (isGeneratingBible) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Architect Generating...")
                                                } else {
                                                    Text(if (currentProject.lastError != null) "Retry Generating Story Bible" else "Generate Story Bible & Beats")
                                                }
                                            }

                                            if (isGeneratingBible) {
                                                Button(
                                                    onClick = {
                                                        haptics.perform(HapticPattern.Pop)
                                                        storyRepo.cancelGeneration(currentProject.id)
                                                        isGeneratingBible = false
                                                        toaster.show("Generation cancelled")
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    ),
                                                    shape = AppShapes.ButtonPill
                                                ) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Story Bible Header & Copy Action
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Story Bible & Beats Outline",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            haptics.perform(HapticPattern.Tick)
                                            val bibleText = buildString {
                                                appendLine("# ${currentProject.title} — Story Bible")
                                                appendLine("Premise: ${currentProject.premise}")
                                                appendLine("Genre: ${currentProject.genre} • Tone: ${currentProject.tone}")
                                                appendLine("Style Guidelines: ${bible.styleGuidelines}")
                                                appendLine()
                                                appendLine("## Characters")
                                                bible.characters.forEach {
                                                    appendLine("- ${it.name} (${it.role}): ${it.description} Motive: ${it.motive}")
                                                }
                                                if (bible.worldRules.isNotEmpty()) {
                                                    appendLine()
                                                    appendLine("## Setting & Narrative Rules")
                                                    bible.worldRules.forEach { appendLine("- $it") }
                                                }
                                                if (bible.beats.isNotEmpty()) {
                                                    appendLine()
                                                    appendLine("## Beats Outline")
                                                    bible.beats.forEach { beat ->
                                                        appendLine("${beat.index + 1}. ${beat.title} [${beat.status}] (~${beat.targetWords} words)")
                                                        appendLine("   Objective: ${beat.objective}")
                                                    }
                                                }
                                            }.trim()
                                            context.writeClipboardText(bibleText)
                                            toaster.show("Story Bible copied to clipboard")
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy Story Bible",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Characters Card
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    shape = AppShapes.CardLarge,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Characters (${bible.characters.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            bible.characters.forEach { char ->
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = char.name,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Surface(
                                                            shape = AppShapes.ButtonPill,
                                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                        ) {
                                                            Text(
                                                                text = char.role,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = char.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (char.motive.isNotBlank()) {
                                                        Text(
                                                            text = "Motive: ${char.motive}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                            }
                                        }
                                    }
                                }
                            }

                            // Setting & Lore Rules Card
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    shape = AppShapes.CardLarge,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Setting & Narrative Rules",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                text = bible.styleGuidelines,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            if (bible.worldRules.isNotEmpty()) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                                bible.worldRules.forEach { rule ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Text("•", color = MaterialTheme.colorScheme.primary)
                                                        Text(
                                                            text = rule,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Milestone Beats Outline Card
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    shape = AppShapes.CardLarge,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Milestone Beat Outline (${bible.beats.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            bible.beats.forEach { beat ->
                                                Surface(
                                                    shape = AppShapes.CardSmall,
                                                    color = if (beat.index == currentProject.currentBeatIndex) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                    } else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = when (beat.status) {
                                                                BeatStatus.Done -> MaterialTheme.colorScheme.primary
                                                                BeatStatus.Writing -> MaterialTheme.colorScheme.secondary
                                                                BeatStatus.Queued -> MaterialTheme.colorScheme.surfaceContainerHigh
                                                                BeatStatus.Failed -> MaterialTheme.colorScheme.error
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = "${beat.index + 1}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = when (beat.status) {
                                                                        BeatStatus.Done, BeatStatus.Writing, BeatStatus.Failed -> MaterialTheme.colorScheme.onPrimary
                                                                        BeatStatus.Queued -> MaterialTheme.colorScheme.onSurfaceVariant
                                                                    }
                                                                )
                                                            }
                                                        }

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = beat.title,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.SemiBold
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
                } else {
                    // ========================================================
                    // TAB 2: DIRECTOR CHAT WITH ASSISTANT
                    // ========================================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Director Chat Top Context Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Chat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${activeAssistant?.name ?: "Assistant"} • Director & Co-writer",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isDirectorStreaming) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Message List
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = directorChatListState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (currentProject.directorChat.isEmpty()) {
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
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Talk to ${activeAssistant?.name ?: "your assistant"}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Brainstorm characters, change world rules, suggest plot twists, or steer future beats. Any requested changes to the Story Bible are updated automatically!",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(currentProject.directorChat, key = { it.id }) { msg ->
                                    val isUser = msg.role == "user"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                    ) {
                                        Surface(
                                            shape = if (isUser) {
                                                AppShapes.CardMedium.copy(bottomEnd = androidx.compose.foundation.shape.CornerSize(4.dp))
                                            } else {
                                                AppShapes.CardMedium.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(4.dp))
                                            },
                                            color = if (isUser) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (isUser) "You" else activeAssistant?.name ?: "Director",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            haptics.perform(HapticPattern.Tick)
                                                            context.writeClipboardText(msg.content)
                                                            toaster.show("Message copied to clipboard")
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.ContentCopy,
                                                            contentDescription = "Copy message",
                                                            modifier = Modifier.size(14.dp),
                                                            tint = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                SelectionContainer {
                                                    Text(
                                                        text = msg.content,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Active streaming bubble
                            if (isDirectorStreaming && activeDirectorChunk.isNotBlank()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Surface(
                                            shape = AppShapes.CardMedium.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(4.dp)),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = activeAssistant?.name ?: "Director",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = activeDirectorChunk,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Input Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = directorInputText,
                                    onValueChange = { directorInputText = it },
                                    placeholder = {
                                        Text(
                                            text = "Talk to ${activeAssistant?.name ?: "assistant"} about the story...",
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
                                    modifier = Modifier.weight(1f),
                                    maxLines = 3
                                )

                                IconButton(
                                    onClick = {
                                        val text = directorInputText.trim()
                                        if (text.isNotBlank() && !isDirectorStreaming) {
                                            haptics.perform(HapticPattern.Pop)
                                            directorInputText = ""
                                            isDirectorStreaming = true
                                            activeDirectorChunk = ""
                                            scope.launch {
                                                storyRepo.sendDirectorMessage(
                                                    projectId = currentProject.id,
                                                    userMessage = text,
                                                    onChunk = { delta ->
                                                        activeDirectorChunk += delta
                                                    }
                                                ).onSuccess {
                                                    activeDirectorChunk = ""
                                                }.onFailure { err ->
                                                    toaster.show("Director Chat error: ${err.message}")
                                                }
                                                isDirectorStreaming = false
                                            }
                                        }
                                    },
                                    enabled = directorInputText.isNotBlank() && !isDirectorStreaming,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (directorInputText.isNotBlank() && !isDirectorStreaming) {
                                                MaterialTheme.colorScheme.primary
                                            } else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send",
                                        tint = if (directorInputText.isNotBlank() && !isDirectorStreaming) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
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

                    OutlinedTextField(
                        value = newGenre,
                        onValueChange = { newGenre = it },
                        label = { Text("Genre") },
                        placeholder = { Text("e.g. Fantasy, Sci-Fi, Romance") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newTone,
                        onValueChange = { newTone = it },
                        label = { Text("Tone") },
                        placeholder = { Text("e.g. Dark, Humorous, Epic, Playful") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
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
                                    genre = newGenre.ifBlank { "Fiction" },
                                    tone = newTone.ifBlank { "Engaging" },
                                    architectModelId = newArchitectModelId ?: settings.chatModelId,
                                    proseModelId = newProseModelId ?: settings.chatModelId,
                                )
                                selectedProjectId = p.id
                                showNewStoryDialog = false
                                toaster.show("Story created! Generating Story Bible...")
                                isGeneratingBible = true
                                storyRepo.generateStoryBible(p.id).onSuccess {
                                    toaster.show("Story Bible generated successfully!")
                                }.onFailure { err ->
                                    toaster.show("Story Bible generation failed: ${err.message}")
                                }
                                isGeneratingBible = false
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

    // Delete Story Confirmation Dialog
    projectToDelete?.let { proj ->
        LastChatDestructiveConfirmDialog(
            title = "Delete Story",
            consequence = "Are you sure you want to permanently delete \"${proj.title}\"? The entire manuscript, Story Bible, and director chat will be removed.",
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
            onDismiss = { projectToDelete = null },
            onConfirm = {
                scope.launch {
                    storyRepo.deleteProject(proj.id)
                    if (selectedProjectId == proj.id) {
                        selectedProjectId = null
                    }
                    toaster.show("Story deleted")
                    projectToDelete = null
                }
            }
        )
    }
}
