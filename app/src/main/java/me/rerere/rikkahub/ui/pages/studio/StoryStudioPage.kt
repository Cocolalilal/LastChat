package me.rerere.rikkahub.ui.pages.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import me.rerere.rikkahub.data.repository.StoryProject
import me.rerere.rikkahub.data.repository.StoryStatus
import me.rerere.rikkahub.data.repository.StoryStudioRepository
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val reduceMotion = LocalMotionPolicy.current.reduceMotion

    val storyRepo = koinInject<StoryStudioRepository>()
    val settingsStore = koinInject<SettingsStore>()

    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val projects by storyRepo.projectsFlow.collectAsStateWithLifecycle()

    val activeAssistant = remember(settings.assistantId, settings.assistants) {
        settings.getAssistantById(settings.assistantId) ?: settings.assistants.firstOrNull()
    }

    var selectedProjectId by remember(initialStoryId) { mutableStateOf(initialStoryId) }
    val currentProject = projects.firstOrNull { it.id == selectedProjectId }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var isGeneratingBible by remember { mutableStateOf(false) }
    var activeProseChunk by remember { mutableStateOf("") }
    var steerText by remember { mutableStateOf("") }
    var isSteerDrawerOpen by remember { mutableStateOf(false) }

    // Story Bible collapsible section flags
    var charactersExpanded by remember { mutableStateOf(true) }
    var rulesExpanded by remember { mutableStateOf(true) }
    var beatsExpanded by remember { mutableStateOf(true) }

    // Director chat state
    var directorInputText by remember { mutableStateOf("") }
    var isDirectorStreaming by remember { mutableStateOf(false) }
    var activeDirectorChunk by remember { mutableStateOf("") }
    var bibleUpdatedBannerVisible by remember { mutableStateOf(false) }

    // New story bottom sheet state
    var showNewStorySheet by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newPremise by remember { mutableStateOf("") }
    var newGenre by remember { mutableStateOf("") }
    var newTone by remember { mutableStateOf("") }
    var newArchitectModelId by remember { mutableStateOf<Uuid?>(null) }
    var newProseModelId by remember { mutableStateOf<Uuid?>(null) }

    var projectToDelete by remember { mutableStateOf<StoryProject?>(null) }

    fun triggerBeatWrite(project: StoryProject, beatIndex: Int) {
        if (project.bible == null) {
            toaster.show("Story Bible not generated yet!")
            return
        }
        if (beatIndex !in project.bible.beats.indices) {
            toaster.show("All beats already completed!")
            return
        }
        if (isGenerating || isGeneratingBible) return
        isGenerating = true
        activeProseChunk = ""
        scope.launch {
            storyRepo.generateBeatProse(
                projectId = project.id,
                beatIndex = beatIndex,
                steerNote = steerText.takeIf { it.isNotBlank() },
                onChunk = { delta -> activeProseChunk += delta },
            ).onSuccess {
                toaster.show("Beat ${beatIndex + 1} finished & saved!", type = ToastType.Success)
                activeProseChunk = ""
                steerText = ""
                isSteerDrawerOpen = false
            }.onFailure {
                toaster.show("Generation error: ${it.message}", type = ToastType.Error)
            }
            isGenerating = false
        }
    }

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
                                showNewStorySheet = true
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
                    isGeneratingBible -> "Generating Story Bible…"
                    currentProject.bible == null -> "Story Bible Not Built"
                    isAllDone -> "Novel Completed"
                    currentBeat != null -> "Beat ${currentBeat.index + 1}: ${currentBeat.title}"
                    else -> "Outlining"
                }

                val statusSubtitle = when {
                    isGeneratingBible -> "Architect drafting characters & outline…"
                    isGenerating -> "Writing prose • ${currentProject.totalWords} words total"
                    !currentProject.lastError.isNullOrBlank() -> "Error: ${currentProject.lastError}"
                    isAllDone -> "All beats written • ${currentProject.totalWords} words total"
                    else -> "Ready • ${currentProject.totalWords} words total"
                }

                val dockColor = MaterialTheme.colorScheme.surfaceContainerLow
                Surface(
                    color = dockColor,
                    border = lastChatSoftEdgeBorder(dockColor),
                    tonalElevation = 6.dp,
                    shape = AppShapes.BottomSheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .lastChatBlurEffect(dockColor, AppShapes.BottomSheet),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = statusTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (!currentProject.lastError.isNullOrBlank() && !isGenerating && !isGeneratingBible) {
                                            MaterialTheme.colorScheme.error
                                        } else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = statusSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!currentProject.lastError.isNullOrBlank() && !isGenerating && !isGeneratingBible) {
                                            MaterialTheme.colorScheme.error
                                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Selection)
                                        isSteerDrawerOpen = !isSteerDrawerOpen
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "Steer note",
                                        tint = if (isSteerDrawerOpen || steerText.isNotBlank()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }

                                if (isGenerating || isGeneratingBible) {
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
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            haptics.perform(HapticPattern.Tick)
                                            triggerBeatWrite(currentProject, currentProject.currentBeatIndex)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Start Beat",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            haptics.perform(HapticPattern.Pop)
                                            val maxIdx = currentProject.bible?.beats?.size ?: 0
                                            val nextIdx = (currentProject.currentBeatIndex + 1).coerceAtMost(maxIdx)
                                            scope.launch {
                                                storyRepo.saveProject(currentProject.copy(currentBeatIndex = nextIdx))
                                                toaster.show("Advanced to Beat ${nextIdx + 1}")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FastForward,
                                            contentDescription = "Advance Beat",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .height(5.dp)
                                .clip(AppShapes.ButtonPill),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )

                        ExpandableContent(visible = isSteerDrawerOpen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = steerText,
                                    onValueChange = { steerText = it },
                                    placeholder = {
                                        Text(
                                            text = "Steer next beat: e.g. 'Focus on dialogue'…",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    shape = AppShapes.ButtonPill,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )

                                Button(
                                    onClick = {
                                        if (steerText.isNotBlank()) {
                                            haptics.perform(HapticPattern.Tick)
                                            toaster.show("Director steer note queued!")
                                            isSteerDrawerOpen = false
                                        }
                                    },
                                    shape = AppShapes.ButtonPill,
                                ) {
                                    Text("Queue", style = MaterialTheme.typography.labelLarge)
                                }
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
                // EDITORIAL STORY LIBRARY VIEW
                // ========================================================
                val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.primaryContainer),
                            shape = AppShapes.CardLarge,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "Start a New Story",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Draft multi-beat novels with an AI Story Bible, distraction-free manuscript reader, and assistant director chat.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        newTitle = ""
                                        newPremise = ""
                                        newGenre = ""
                                        newTone = ""
                                        showNewStorySheet = true
                                    },
                                    shape = AppShapes.ButtonPill,
                                ) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create Story", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "YOUR SHELF (${projects.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    if (projects.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = AppShapes.CardLarge,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No stories created yet. Tap 'Create Story' above to start.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(projects, key = { it.id }) { project ->
                            val beatsCount = project.bible?.beats?.size ?: 0
                            val completed = project.bible?.beats?.count { it.status == BeatStatus.Done } ?: 0
                            val progress = if (beatsCount > 0) (completed.toFloat() / beatsCount).coerceIn(0f, 1f) else 0f

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = AppShapes.CardLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        selectedProjectId = project.id
                                    },
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = project.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )

                                        Surface(
                                            shape = AppShapes.ButtonPill,
                                            color = when (project.status) {
                                                StoryStatus.Completed -> MaterialTheme.colorScheme.tertiaryContainer
                                                StoryStatus.Writing -> MaterialTheme.colorScheme.primaryContainer
                                                StoryStatus.Outlining -> MaterialTheme.colorScheme.secondaryContainer
                                                StoryStatus.Paused -> MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                        ) {
                                            Text(
                                                text = project.status.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (project.genre.isNotBlank()) {
                                            EditorialPill(project.genre)
                                        }
                                        if (project.tone.isNotBlank()) {
                                            EditorialPill(project.tone)
                                        }
                                    }

                                    Text(
                                        text = project.premise,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    if (beatsCount > 0) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(AppShapes.ButtonPill),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = buildString {
                                                if (beatsCount > 0) append("Beat $completed/$beatsCount • ")
                                                append("${project.totalWords} words • ")
                                                append(dateFmt.format(Date(project.updatedAt)))
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )

                                        IconButton(
                                            onClick = {
                                                haptics.perform(HapticPattern.Pop)
                                                projectToDelete = project
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete Story",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp),
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
                // SLEEK SEGMENTED PILL SWITCHER
                // ========================================================
                val tabs = listOf(
                    Triple(0, "Manuscript", Icons.AutoMirrored.Rounded.MenuBook),
                    Triple(1, "Story Bible", Icons.Rounded.AutoAwesome),
                    Triple(2, "Director", Icons.Rounded.Chat),
                )
                Surface(
                    shape = AppShapes.ButtonPill,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tabs.forEach { (index, label, icon) ->
                            val selected = selectedTabIndex == index
                            Surface(
                                shape = AppShapes.ButtonPill,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                onClick = {
                                    haptics.perform(HapticPattern.Selection)
                                    selectedTabIndex = index
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = selectedTabIndex,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(80)) togetherWith fadeOut(tween(60))
                        } else {
                            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                fadeOut(tween(120))
                        }
                    },
                    label = "story_workspace_tab",
                    modifier = Modifier.weight(1f),
                ) { tab ->
                    when (tab) {
                        0 -> {
                            // ========================================================
                            // TAB 0: DISTRACTION-FREE MANUSCRIPT READER & WRITER
                            // ========================================================
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = lazyListState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        ),
                                        border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerLow),
                                        shape = AppShapes.CardLarge,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        SelectionContainer {
                                            Column(
                                                modifier = Modifier.padding(22.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = currentProject.title,
                                                            style = MaterialTheme.typography.headlineSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Serif,
                                                        )
                                                        Text(
                                                            text = listOfNotNull(
                                                                currentProject.genre.takeIf { it.isNotBlank() },
                                                                currentProject.tone.takeIf { it.isNotBlank() },
                                                                "${currentProject.totalWords} words",
                                                            ).joinToString(" • "),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }

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
                                                                tint = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                    }
                                                }

                                                val beats = currentProject.bible?.beats ?: emptyList()
                                                if (beats.isEmpty()) {
                                                    Column(
                                                        modifier = Modifier.padding(vertical = 8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                                    ) {
                                                        Text(
                                                            text = "Premise: ${currentProject.premise}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )

                                                        if (!currentProject.lastError.isNullOrBlank()) {
                                                            StoryErrorBanner(currentProject.lastError.orEmpty())
                                                        }

                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Button(
                                                                onClick = {
                                                                    haptics.perform(HapticPattern.Pop)
                                                                    scope.launch {
                                                                        isGeneratingBible = true
                                                                        toaster.show("Architect model building Story Bible & Beats…")
                                                                        storyRepo.generateStoryBible(currentProject.id).onSuccess {
                                                                            toaster.show("Story Bible generated successfully!", type = ToastType.Success)
                                                                        }.onFailure {
                                                                            toaster.show("Error: ${it.message}", type = ToastType.Error)
                                                                        }
                                                                        isGeneratingBible = false
                                                                    }
                                                                },
                                                                enabled = !isGeneratingBible,
                                                                shape = AppShapes.ButtonPill,
                                                            ) {
                                                                if (isGeneratingBible) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(16.dp),
                                                                        strokeWidth = 2.dp,
                                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                                    )
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Text("Generating…")
                                                                } else {
                                                                    Text(
                                                                        if (currentProject.lastError != null) "Retry Story Bible"
                                                                        else "Generate Story Bible"
                                                                    )
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
                                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                                                    ),
                                                                    shape = AppShapes.ButtonPill,
                                                                ) {
                                                                    Text("Cancel")
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                                        beats.forEach { beat ->
                                                            if (beat.content.isNotBlank()) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                ) {
                                                                    HorizontalDivider(
                                                                        modifier = Modifier.weight(1f),
                                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                                    )
                                                                    Surface(
                                                                        shape = AppShapes.ButtonPill,
                                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                                                                        ) {
                                                                            Text(
                                                                                text = "Beat ${beat.index + 1}: ${beat.title} • ${beat.wordsWritten}w",
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = MaterialTheme.colorScheme.primary,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis,
                                                                            )
                                                                            IconButton(
                                                                                onClick = {
                                                                                    haptics.perform(HapticPattern.Tick)
                                                                                    context.writeClipboardText(beat.content)
                                                                                    toaster.show("Beat ${beat.index + 1} copied")
                                                                                },
                                                                                modifier = Modifier.size(24.dp),
                                                                            ) {
                                                                                Icon(
                                                                                    Icons.Rounded.ContentCopy,
                                                                                    contentDescription = "Copy beat",
                                                                                    modifier = Modifier.size(13.dp),
                                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                                )
                                                                            }
                                                                            IconButton(
                                                                                onClick = {
                                                                                    haptics.perform(HapticPattern.Pop)
                                                                                    triggerBeatWrite(currentProject, beat.index)
                                                                                },
                                                                                enabled = !isGenerating && !isGeneratingBible,
                                                                                modifier = Modifier.size(24.dp),
                                                                            ) {
                                                                                Icon(
                                                                                    Icons.Rounded.Refresh,
                                                                                    contentDescription = "Regenerate beat",
                                                                                    modifier = Modifier.size(14.dp),
                                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                    HorizontalDivider(
                                                                        modifier = Modifier.weight(1f),
                                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                                    )
                                                                }

                                                                Text(
                                                                    text = beat.content,
                                                                    style = MaterialTheme.typography.bodyLarge,
                                                                    lineHeight = 26.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                                                )
                                                            }
                                                        }

                                                        if (isGenerating && activeProseChunk.isNotBlank()) {
                                                            Text(
                                                                text = activeProseChunk,
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                lineHeight = 26.sp,
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(72.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ========================================================
                            // TAB 1: STRUCTURED STORY BIBLE VIEW
                            // ========================================================
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = lazyListState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                val bible = currentProject.bible
                                if (bible == null) {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                            shape = AppShapes.CardLarge,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                            ) {
                                                Text(
                                                    text = if (isGeneratingBible) "Generating Story Bible…" else "Story Bible Not Built Yet",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = "Premise: ${currentProject.premise}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (!currentProject.lastError.isNullOrBlank()) {
                                                    StoryErrorBanner(currentProject.lastError.orEmpty())
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            haptics.perform(HapticPattern.Pop)
                                                            scope.launch {
                                                                isGeneratingBible = true
                                                                toaster.show("Architect model building Story Bible & Beats…")
                                                                storyRepo.generateStoryBible(currentProject.id).onSuccess {
                                                                    toaster.show("Story Bible generated successfully!", type = ToastType.Success)
                                                                }.onFailure {
                                                                    toaster.show("Error: ${it.message}", type = ToastType.Error)
                                                                }
                                                                isGeneratingBible = false
                                                            }
                                                        },
                                                        enabled = !isGeneratingBible,
                                                        shape = AppShapes.ButtonPill,
                                                    ) {
                                                        if (isGeneratingBible) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(16.dp),
                                                                strokeWidth = 2.dp,
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Architect Generating…")
                                                        } else {
                                                            Text(
                                                                if (currentProject.lastError != null) "Retry Generating Story Bible"
                                                                else "Generate Story Bible & Beats"
                                                            )
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
                                                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                                            ),
                                                            shape = AppShapes.ButtonPill,
                                                        ) {
                                                            Text("Cancel")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Premise & Tone Hero Card + Copy Action
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            ),
                                            border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.primaryContainer),
                                            shape = AppShapes.CardLarge,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            SelectionContainer {
                                                Column(
                                                    modifier = Modifier.padding(18.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = bible.title,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontFamily = FontFamily.Serif,
                                                                fontWeight = FontWeight.Bold,
                                                            )
                                                            FlowRow(
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.padding(top = 4.dp),
                                                            ) {
                                                                if (bible.genre.isNotBlank()) EditorialPill(bible.genre)
                                                                if (bible.tone.isNotBlank()) EditorialPill(bible.tone)
                                                            }
                                                        }

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
                                                                tint = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = bible.premise,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Collapsible Characters Roster
                                    item {
                                        CollapsibleBibleCard(
                                            title = "Character Roster (${bible.characters.size})",
                                            expanded = charactersExpanded,
                                            onToggle = {
                                                haptics.perform(HapticPattern.Selection)
                                                charactersExpanded = !charactersExpanded
                                            },
                                        ) {
                                            SelectionContainer {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    bible.characters.forEach { char ->
                                                        Surface(
                                                            shape = AppShapes.CardSmall,
                                                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                                            modifier = Modifier.fillMaxWidth(),
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(12.dp),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                            ) {
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                ) {
                                                                    Text(
                                                                        text = char.name,
                                                                        style = MaterialTheme.typography.titleSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                    )
                                                                    if (char.role.isNotBlank()) {
                                                                        EditorialPill(char.role)
                                                                    }
                                                                }
                                                                if (char.description.isNotBlank()) {
                                                                    Text(
                                                                        text = char.description,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurface,
                                                                    )
                                                                }
                                                                if (char.motive.isNotBlank()) {
                                                                    Text(
                                                                        text = "Motive: ${char.motive}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Collapsible World Rules & Style Guidelines
                                    item {
                                        CollapsibleBibleCard(
                                            title = "World Rules & Voice",
                                            expanded = rulesExpanded,
                                            onToggle = {
                                                haptics.perform(HapticPattern.Selection)
                                                rulesExpanded = !rulesExpanded
                                            },
                                        ) {
                                            SelectionContainer {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(
                                                        text = bible.styleGuidelines,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    if (bible.worldRules.isNotEmpty()) {
                                                        FlowRow(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            bible.worldRules.forEach { rule ->
                                                                Surface(
                                                                    shape = AppShapes.CardSmall,
                                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                                ) {
                                                                    Text(
                                                                        text = rule,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Collapsible Interactive Beat Timeline
                                    item {
                                        CollapsibleBibleCard(
                                            title = "Beat Timeline (${bible.beats.size})",
                                            expanded = beatsExpanded,
                                            onToggle = {
                                                haptics.perform(HapticPattern.Selection)
                                                beatsExpanded = !beatsExpanded
                                            },
                                        ) {
                                            SelectionContainer {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    bible.beats.forEach { beat ->
                                                        BeatTimelineRow(
                                                            beat = beat,
                                                            isCurrent = beat.index == currentProject.currentBeatIndex,
                                                            isGenerating = isGenerating || isGeneratingBible,
                                                            onWriteOrJump = {
                                                                haptics.perform(HapticPattern.Pop)
                                                                scope.launch {
                                                                    storyRepo.saveProject(currentProject.copy(currentBeatIndex = beat.index))
                                                                }
                                                                selectedTabIndex = 0
                                                                triggerBeatWrite(currentProject, beat.index)
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // ========================================================
                            // TAB 2: POLISHED DIRECTOR CHAT
                            // ========================================================
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .navigationBarsPadding()
                                    .imePadding(),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = (activeAssistant?.name?.firstOrNull() ?: 'A').uppercase(),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = activeAssistant?.name ?: "Assistant",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = "Director & Co-writer • Live Memory Active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }

                                        if (isDirectorStreaming) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = bibleUpdatedBannerVisible) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = "Story Bible synced from Director Chat!",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.weight(1f),
                                            )
                                            TextButton(
                                                onClick = {
                                                    bibleUpdatedBannerVisible = false
                                                    selectedTabIndex = 1
                                                }
                                            ) {
                                                Text("View Bible")
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    state = directorChatListState,
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (currentProject.directorChat.isEmpty()) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                                ),
                                                shape = AppShapes.CardLarge,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(
                                                        text = "Talk to ${activeAssistant?.name ?: "your assistant"}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    Text(
                                                        text = "Brainstorm characters, change world rules, suggest plot twists, or steer future beats. Requested Story Bible changes apply automatically!",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(currentProject.directorChat, key = { it.id }) { msg ->
                                            val isUser = msg.role == "user"
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                                            ) {
                                                Surface(
                                                    shape = if (isUser) {
                                                        AppShapes.CardMedium.copy(bottomEnd = CornerSize(4.dp))
                                                    } else {
                                                        AppShapes.CardMedium.copy(bottomStart = CornerSize(4.dp))
                                                    },
                                                    color = if (isUser) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                                    },
                                                    modifier = Modifier.fillMaxWidth(0.86f),
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                text = if (isUser) "You" else activeAssistant?.name ?: "Director",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                                            )
                                                            IconButton(
                                                                onClick = {
                                                                    haptics.perform(HapticPattern.Tick)
                                                                    context.writeClipboardText(msg.content)
                                                                    toaster.show("Message copied to clipboard")
                                                                },
                                                                modifier = Modifier.size(24.dp),
                                                            ) {
                                                                Icon(
                                                                    Icons.Rounded.ContentCopy,
                                                                    contentDescription = "Copy message",
                                                                    modifier = Modifier.size(14.dp),
                                                                    tint = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                        SelectionContainer {
                                                            Text(
                                                                text = msg.content,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (isDirectorStreaming && activeDirectorChunk.isNotBlank()) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Start,
                                            ) {
                                                Surface(
                                                    shape = AppShapes.CardMedium.copy(bottomStart = CornerSize(4.dp)),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier.fillMaxWidth(0.86f),
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = activeAssistant?.name ?: "Director",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = activeDirectorChunk,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = directorInputText,
                                            onValueChange = { directorInputText = it },
                                            placeholder = {
                                                Text(
                                                    text = "Talk to ${activeAssistant?.name ?: "assistant"} about the story…",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            },
                                            shape = AppShapes.ButtonPill,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            ),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 3,
                                        )

                                        IconButton(
                                            onClick = {
                                                val text = directorInputText.trim()
                                                if (text.isNotBlank() && !isDirectorStreaming) {
                                                    haptics.perform(HapticPattern.Pop)
                                                    directorInputText = ""
                                                    isDirectorStreaming = true
                                                    activeDirectorChunk = ""
                                                    bibleUpdatedBannerVisible = false
                                                    scope.launch {
                                                        storyRepo.sendDirectorMessage(
                                                            projectId = currentProject.id,
                                                            userMessage = text,
                                                            onChunk = { delta -> activeDirectorChunk += delta },
                                                        ).onSuccess { msg ->
                                                            activeDirectorChunk = ""
                                                            if (msg.content.contains("updated_bible", ignoreCase = true) ||
                                                                msg.content.contains("\"beats\"")
                                                            ) {
                                                                bibleUpdatedBannerVisible = true
                                                                toaster.show("Story Bible updated from Director Chat!", type = ToastType.Success)
                                                            }
                                                        }.onFailure { err ->
                                                            toaster.show("Director Chat error: ${err.message}", type = ToastType.Error)
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
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                                contentDescription = "Send",
                                                tint = if (directorInputText.isNotBlank() && !isDirectorStreaming) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
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

    // New Story Bottom Sheet
    if (showNewStorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewStorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Create New Story",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                )

                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Story Title") },
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = newPremise,
                    onValueChange = { newPremise = it },
                    label = { Text("Premise / Core Idea") },
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = newGenre,
                        onValueChange = { newGenre = it },
                        label = { Text("Genre") },
                        placeholder = { Text("Fantasy, Sci-Fi…") },
                        shape = AppShapes.InputField,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = newTone,
                        onValueChange = { newTone = it },
                        label = { Text("Tone") },
                        placeholder = { Text("Gritty, Playful…") },
                        shape = AppShapes.InputField,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Architect Model", style = MaterialTheme.typography.labelMedium)
                    ModelSelector(
                        modelId = newArchitectModelId ?: settings.chatModelId,
                        type = ModelType.CHAT,
                        onSelect = { newArchitectModelId = it.id },
                        providers = settings.providers,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Prose Model", style = MaterialTheme.typography.labelMedium)
                    ModelSelector(
                        modelId = newProseModelId ?: settings.chatModelId,
                        type = ModelType.CHAT,
                        onSelect = { newProseModelId = it.id },
                        providers = settings.providers,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { showNewStorySheet = false },
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (newPremise.isNotBlank()) {
                                haptics.perform(HapticPattern.Pop)
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
                                    showNewStorySheet = false
                                    toaster.show("Story created! Building Story Bible…")
                                    isGeneratingBible = true
                                    storyRepo.generateStoryBible(p.id).onSuccess {
                                        toaster.show("Story Bible generated successfully!", type = ToastType.Success)
                                    }.onFailure { err ->
                                        toaster.show("Story Bible generation failed: ${err.message}", type = ToastType.Error)
                                    }
                                    isGeneratingBible = false
                                }
                            }
                        },
                        enabled = newPremise.isNotBlank(),
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.weight(1.4f),
                    ) {
                        Text("Build Story Bible")
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
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
            },
        )
    }
}

@Composable
private fun EditorialPill(text: String) {
    Surface(
        shape = AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun StoryErrorBanner(error: String) {
    Surface(
        shape = AppShapes.CardSmall,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Generation Error",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun CollapsibleBibleCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.surfaceContainerLow),
        shape = AppShapes.CardLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpandableContent(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun BeatTimelineRow(
    beat: StoryBeat,
    isCurrent: Boolean,
    isGenerating: Boolean,
    onWriteOrJump: () -> Unit,
) {
    Surface(
        shape = AppShapes.CardSmall,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when (beat.status) {
                    BeatStatus.Done -> MaterialTheme.colorScheme.primary
                    BeatStatus.Writing -> MaterialTheme.colorScheme.secondary
                    BeatStatus.Queued -> MaterialTheme.colorScheme.surfaceContainerHigh
                    BeatStatus.Failed -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${beat.index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (beat.status) {
                            BeatStatus.Done, BeatStatus.Writing, BeatStatus.Failed -> MaterialTheme.colorScheme.onPrimary
                            BeatStatus.Queued -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = beat.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = AppShapes.ButtonPill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = beat.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = beat.objective,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onWriteOrJump,
                enabled = !isGenerating,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (beat.status == BeatStatus.Done) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                    contentDescription = if (beat.status == BeatStatus.Done) "Rewrite beat" else "Write beat",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
