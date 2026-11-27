package me.rerere.rikkahub.ui.pages.studio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.repository.BeatStatus
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.StoryProject
import me.rerere.rikkahub.data.repository.StoryStatus
import me.rerere.rikkahub.data.repository.StoryStudioRepository
import me.rerere.rikkahub.data.repository.StudioChecklistEntry
import me.rerere.rikkahub.data.repository.StudioFeedEntry
import me.rerere.rikkahub.data.repository.StudioHubItem
import me.rerere.rikkahub.data.repository.StudioHubRepository
import me.rerere.rikkahub.data.repository.StudioItemType
import me.rerere.rikkahub.data.repository.VoiceMemoItem
import me.rerere.rikkahub.data.repository.VoiceMemoRepository
import me.rerere.rikkahub.data.repository.VoicePlaybackManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.motion.rememberControlPressValue
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class StudioFilter(val label: String) {
    ALL("All"),
    NOTES("Notes"),
    TASKS("Tasks"),
    REMINDERS("Reminders"),
    VOICE_MEMOS("Voice Memos"),
    STORIES("Stories"),
    IMAGES("Images"),
    ARCHIVED("Archived"),
}

private data class StudioColorSpec(
    val token: String?,
    val label: String,
    val lightContainer: Color,
    val darkContainer: Color,
    val amoledContainer: Color,
)

private val STUDIO_COLORS = listOf(
    StudioColorSpec(
        token = null,
        label = "Default",
        lightContainer = Color.Unspecified,
        darkContainer = Color.Unspecified,
        amoledContainer = Color.Unspecified,
    ),
    StudioColorSpec(
        token = "amber",
        label = "Amber",
        lightContainer = Color(0xFFFFF3CD),
        darkContainer = Color(0xFF3A2E14),
        amoledContainer = Color(0xFF1F1909),
    ),
    StudioColorSpec(
        token = "sage",
        label = "Sage",
        lightContainer = Color(0xFFE2F2E5),
        darkContainer = Color(0xFF193221),
        amoledContainer = Color(0xFF0C1C11),
    ),
    StudioColorSpec(
        token = "sky",
        label = "Sky",
        lightContainer = Color(0xFFDFEEFD),
        darkContainer = Color(0xFF172C42),
        amoledContainer = Color(0xFF0A1725),
    ),
    StudioColorSpec(
        token = "rose",
        label = "Rose",
        lightContainer = Color(0xFFFDE3E7),
        darkContainer = Color(0xFF3E1C24),
        amoledContainer = Color(0xFF210D12),
    ),
    StudioColorSpec(
        token = "lavender",
        label = "Lavender",
        lightContainer = Color(0xFFECE4FA),
        darkContainer = Color(0xFF2B2042),
        amoledContainer = Color(0xFF171026),
    ),
)

@Composable
private fun studioCardContainerColor(colorToken: String?): Color {
    val isDark = LocalDarkMode.current
    val amoled by rememberAmoledDarkMode()
    val spec = STUDIO_COLORS.firstOrNull { it.token == colorToken }
    if (spec == null || spec.token == null) {
        return if (amoled && isDark) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    }
    return when {
        amoled && isDark -> spec.amoledContainer
        isDark -> spec.darkContainer
        else -> spec.lightContainer
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StudioPage(
    initialItemId: String? = null,
) {
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val reduceMotion = LocalMotionPolicy.current.reduceMotion

    val studioRepo = koinInject<StudioHubRepository>()
    val storyRepo = koinInject<StoryStudioRepository>()
    val voiceRepo = koinInject<VoiceMemoRepository>()
    val genMediaRepo = koinInject<GenMediaRepository>()
    val playbackManager = koinInject<VoicePlaybackManager>()
    val settingsStore = koinInject<SettingsStore>()
    val providerManager = koinInject<ProviderManager>()
    val secretKeyManager = koinInject<SecretKeyManager>()

    val hubItems by studioRepo.itemsFlow.collectAsStateWithLifecycle()
    val stories by storyRepo.projectsFlow.collectAsStateWithLifecycle()
    val voiceMemos by voiceRepo.recordingsFlow.collectAsStateWithLifecycle()
    val isPlayingVoice by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val currentVoiceFile by playbackManager.currentFile.collectAsStateWithLifecycle()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    var generatedImages by remember { mutableStateOf<List<GenMediaEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        generatedImages = withContext(Dispatchers.IO) {
            runCatching { genMediaRepo.getAllMediaList() }.getOrDefault(emptyList())
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(StudioFilter.ALL) }
    var isGridLayout by rememberSaveable { mutableStateOf(true) }
    var isFabMenuOpen by remember { mutableStateOf(false) }

    var editingItem by remember { mutableStateOf<StudioHubItem?>(null) }
    var quickActionItem by remember { mutableStateOf<StudioHubItem?>(null) }
    var itemToDelete by remember { mutableStateOf<StudioHubItem?>(null) }
    var zoomImages by remember { mutableStateOf<Pair<List<String>, String?>?>(null) }
    var handledInitialItem by remember(initialItemId) { mutableStateOf(false) }

    LaunchedEffect(initialItemId, hubItems) {
        if (!initialItemId.isNullOrBlank() && !handledInitialItem) {
            val found = hubItems.firstOrNull { it.id == initialItemId } ?: studioRepo.getItem(initialItemId)
            if (found != null) {
                editingItem = found
                handledInitialItem = true
            }
        }
    }

    val pickImageForNewNoteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val created = studioRepo.createItem(
                    type = StudioItemType.IMAGE_NOTE,
                    title = "",
                    body = "",
                    attachmentSources = listOf(uri.toString()),
                    createdBy = "user",
                )
                editingItem = created
            }
        }
    }

    val allEntries = remember(hubItems, stories, voiceMemos, generatedImages) {
        buildList<StudioFeedEntry> {
            hubItems.forEach { add(StudioFeedEntry.HubItem(it)) }
            voiceMemos.forEach { add(StudioFeedEntry.VoiceMemo(it)) }
            stories.forEach { add(StudioFeedEntry.Story(it)) }
            generatedImages.forEach { add(StudioFeedEntry.GeneratedImage(it)) }
        }
    }

    val filterCounts = remember(allEntries) {
        mapOf(
            StudioFilter.ALL to allEntries.count { it !is StudioFeedEntry.HubItem || !it.item.isArchived },
            StudioFilter.NOTES to allEntries.count {
                it is StudioFeedEntry.HubItem && !it.item.isArchived &&
                    (it.item.type == StudioItemType.NOTE || it.item.type == StudioItemType.IMAGE_NOTE)
            },
            StudioFilter.TASKS to allEntries.count {
                it is StudioFeedEntry.HubItem && !it.item.isArchived && it.item.type == StudioItemType.TASK_LIST
            },
            StudioFilter.REMINDERS to allEntries.count {
                it is StudioFeedEntry.HubItem && !it.item.isArchived &&
                    (it.item.type == StudioItemType.REMINDER || it.item.reminderAtMillis != null)
            },
            StudioFilter.VOICE_MEMOS to allEntries.count { it is StudioFeedEntry.VoiceMemo },
            StudioFilter.STORIES to allEntries.count { it is StudioFeedEntry.Story },
            StudioFilter.IMAGES to allEntries.count {
                it is StudioFeedEntry.GeneratedImage ||
                    (it is StudioFeedEntry.HubItem && !it.item.isArchived && it.item.attachmentPaths.isNotEmpty())
            },
            StudioFilter.ARCHIVED to allEntries.count { it is StudioFeedEntry.HubItem && it.item.isArchived },
        )
    }

    val filteredEntries = remember(allEntries, selectedFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        allEntries.filter { entry ->
            val matchesFilter = when (selectedFilter) {
                StudioFilter.ALL -> entry !is StudioFeedEntry.HubItem || !entry.item.isArchived
                StudioFilter.NOTES -> entry is StudioFeedEntry.HubItem && !entry.item.isArchived &&
                    (entry.item.type == StudioItemType.NOTE || entry.item.type == StudioItemType.IMAGE_NOTE)
                StudioFilter.TASKS -> entry is StudioFeedEntry.HubItem && !entry.item.isArchived &&
                    entry.item.type == StudioItemType.TASK_LIST
                StudioFilter.REMINDERS -> entry is StudioFeedEntry.HubItem && !entry.item.isArchived &&
                    (entry.item.type == StudioItemType.REMINDER || entry.item.reminderAtMillis != null)
                StudioFilter.VOICE_MEMOS -> entry is StudioFeedEntry.VoiceMemo
                StudioFilter.STORIES -> entry is StudioFeedEntry.Story
                StudioFilter.IMAGES -> entry is StudioFeedEntry.GeneratedImage ||
                    (entry is StudioFeedEntry.HubItem && !entry.item.isArchived && entry.item.attachmentPaths.isNotEmpty())
                StudioFilter.ARCHIVED -> entry is StudioFeedEntry.HubItem && entry.item.isArchived
            }
            if (!matchesFilter) return@filter false
            if (q.isBlank()) return@filter true

            when (entry) {
                is StudioFeedEntry.HubItem -> {
                    val i = entry.item
                    i.title.lowercase().contains(q) ||
                        i.body.lowercase().contains(q) ||
                        i.briefSummary.lowercase().contains(q) ||
                        i.tags.any { it.lowercase().contains(q) } ||
                        i.checklist.any { it.text.lowercase().contains(q) }
                }
                is StudioFeedEntry.VoiceMemo -> {
                    val m = entry.memo
                    m.title.lowercase().contains(q) ||
                        m.summary?.lowercase()?.contains(q) == true ||
                        m.transcript?.lowercase()?.contains(q) == true
                }
                is StudioFeedEntry.Story -> {
                    val p = entry.project
                    p.title.lowercase().contains(q) ||
                        p.premise.lowercase().contains(q) ||
                        p.genre.lowercase().contains(q) ||
                        p.tone.lowercase().contains(q)
                }
                is StudioFeedEntry.GeneratedImage -> {
                    entry.media.prompt.lowercase().contains(q)
                }
            }
        }.sortedWith(
            compareByDescending<StudioFeedEntry> { it.isPinned }
                .thenByDescending { it.updatedAt }
        )
    }

    val pinnedEntries = remember(filteredEntries) { filteredEntries.filter { it.isPinned } }
    val otherEntries = remember(filteredEntries) { filteredEntries.filterNot { it.isPinned } }

    val generatingStory = stories.firstOrNull { storyRepo.isProjectGenerating(it.id) }
    val activePlayingMemo = if (isPlayingVoice && currentVoiceFile != null) {
        voiceMemos.firstOrNull { it.audioFile == currentVoiceFile }
    } else null

    BackHandler(enabled = isFabMenuOpen) {
        isFabMenuOpen = false
    }

    val fabRotation by if (reduceMotion) {
        rememberUpdatedState(if (isFabMenuOpen) 45f else 0f)
    } else {
        animateFloatAsState(
            targetValue = if (isFabMenuOpen) 45f else 0f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "fab_rotation",
        )
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.chat_drawer_studio),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            navController.navigate(Screen.Library)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Collections,
                            contentDescription = "Creations Library",
                        )
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding)
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Keep-inspired floating pill search bar
                val searchSurface = MaterialTheme.colorScheme.surfaceContainerHigh
                Surface(
                    shape = AppShapes.ButtonPill,
                    color = searchSurface,
                    border = lastChatSoftEdgeBorder(searchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .lastChatBlurEffect(searchSurface, AppShapes.ButtonPill),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search notes, tasks, memos, stories…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                isGridLayout = !isGridLayout
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            AnimatedContent(
                                targetState = isGridLayout,
                                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(120)) },
                                label = "layout_toggle_icon",
                            ) { grid ->
                                Icon(
                                    imageVector = if (grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                                    contentDescription = if (grid) "Switch to list view" else "Switch to grid view",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // Horizontal Filter Chips with live counts
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StudioFilter.entries, key = { it.name }) { filter ->
                        val count = filterCounts[filter] ?: 0
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                selectedFilter = filter
                            },
                            label = {
                                Text(
                                    text = if (count > 0) "${filter.label} · $count" else filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            shape = AppShapes.ButtonPill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                // Conditional Live Active Banner
                AnimatedVisibility(
                    visible = generatingStory != null || activePlayingMemo != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        generatingStory?.let { story ->
                            LiveActiveStudioBanner(
                                title = "Writing: ${story.title}",
                                subtitle = "Beat ${story.currentBeatIndex + 1} • ${story.totalWords} words",
                                actionLabel = "View",
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    navController.navigate(Screen.StoryStudio(storyId = story.id))
                                },
                            )
                        }
                        activePlayingMemo?.let { memo ->
                            LiveActiveStudioBanner(
                                title = "Playing: ${memo.title}",
                                subtitle = formatDuration(memo.durationMs),
                                actionLabel = "Pause",
                                icon = Icons.Rounded.Pause,
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    playbackManager.pause()
                                },
                            )
                        }
                    }
                }

                // Feed body
                if (filteredEntries.isEmpty()) {
                    StudioEmptyState(
                        filter = selectedFilter,
                        hasQuery = searchQuery.isNotBlank(),
                        onCreateNote = {
                            scope.launch {
                                editingItem = studioRepo.createItem(StudioItemType.NOTE)
                            }
                        },
                    )
                } else {
                    AnimatedContent(
                        targetState = isGridLayout,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "feed_layout_mode",
                        modifier = Modifier.weight(1f),
                    ) { grid ->
                        if (grid) {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
                                verticalItemSpacing = 10.dp,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (pinnedEntries.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine, key = "header_pinned") {
                                        FeedSectionHeader("Pinned")
                                    }
                                    staggeredItems(pinnedEntries, key = { it.stableKey }) { entry ->
                                        StudioFeedCard(
                                            entry = entry,
                                            isPlayingVoice = isPlayingVoice,
                                            currentVoiceFile = currentVoiceFile,
                                            onOpenHubItem = { editingItem = it },
                                            onLongPressHubItem = { quickActionItem = it },
                                            onToggleChecklist = { itemId, entryId ->
                                                haptics.perform(HapticPattern.Tick)
                                                scope.launch { studioRepo.toggleChecklistEntry(itemId, entryId) }
                                            },
                                            onToggleComplete = { itemId ->
                                                haptics.perform(HapticPattern.Tick)
                                                scope.launch { studioRepo.toggleCompleted(itemId) }
                                            },
                                            onPlayPauseVoice = { memo ->
                                                haptics.perform(HapticPattern.Tick)
                                                if (isPlayingVoice && currentVoiceFile == memo.audioFile) {
                                                    playbackManager.pause()
                                                } else {
                                                    playbackManager.play(memo.audioFile)
                                                }
                                            },
                                            onOpenVoiceMemos = { navController.navigate(Screen.VoiceMemos) },
                                            onOpenStory = { storyId -> navController.navigate(Screen.StoryStudio(storyId = storyId)) },
                                            onZoomImages = { urls, caption -> zoomImages = urls to caption },
                                        )
                                    }
                                    if (otherEntries.isNotEmpty()) {
                                        item(span = StaggeredGridItemSpan.FullLine, key = "header_others") {
                                            FeedSectionHeader("Others")
                                        }
                                    }
                                }
                                staggeredItems(otherEntries, key = { it.stableKey }) { entry ->
                                    StudioFeedCard(
                                        entry = entry,
                                        isPlayingVoice = isPlayingVoice,
                                        currentVoiceFile = currentVoiceFile,
                                        onOpenHubItem = { editingItem = it },
                                        onLongPressHubItem = { quickActionItem = it },
                                        onToggleChecklist = { itemId, entryId ->
                                            haptics.perform(HapticPattern.Tick)
                                            scope.launch { studioRepo.toggleChecklistEntry(itemId, entryId) }
                                        },
                                        onToggleComplete = { itemId ->
                                            haptics.perform(HapticPattern.Tick)
                                            scope.launch { studioRepo.toggleCompleted(itemId) }
                                        },
                                        onPlayPauseVoice = { memo ->
                                            haptics.perform(HapticPattern.Tick)
                                            if (isPlayingVoice && currentVoiceFile == memo.audioFile) {
                                                playbackManager.pause()
                                            } else {
                                                playbackManager.play(memo.audioFile)
                                            }
                                        },
                                        onOpenVoiceMemos = { navController.navigate(Screen.VoiceMemos) },
                                        onOpenStory = { storyId -> navController.navigate(Screen.StoryStudio(storyId = storyId)) },
                                        onZoomImages = { urls, caption -> zoomImages = urls to caption },
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (pinnedEntries.isNotEmpty()) {
                                    item(key = "list_header_pinned") { FeedSectionHeader("Pinned") }
                                    items(pinnedEntries, key = { it.stableKey }) { entry ->
                                        StudioFeedCard(
                                            entry = entry,
                                            isPlayingVoice = isPlayingVoice,
                                            currentVoiceFile = currentVoiceFile,
                                            onOpenHubItem = { editingItem = it },
                                            onLongPressHubItem = { quickActionItem = it },
                                            onToggleChecklist = { itemId, entryId ->
                                                haptics.perform(HapticPattern.Tick)
                                                scope.launch { studioRepo.toggleChecklistEntry(itemId, entryId) }
                                            },
                                            onToggleComplete = { itemId ->
                                                haptics.perform(HapticPattern.Tick)
                                                scope.launch { studioRepo.toggleCompleted(itemId) }
                                            },
                                            onPlayPauseVoice = { memo ->
                                                haptics.perform(HapticPattern.Tick)
                                                if (isPlayingVoice && currentVoiceFile == memo.audioFile) {
                                                    playbackManager.pause()
                                                } else {
                                                    playbackManager.play(memo.audioFile)
                                                }
                                            },
                                            onOpenVoiceMemos = { navController.navigate(Screen.VoiceMemos) },
                                            onOpenStory = { storyId -> navController.navigate(Screen.StoryStudio(storyId = storyId)) },
                                            onZoomImages = { urls, caption -> zoomImages = urls to caption },
                                        )
                                    }
                                    if (otherEntries.isNotEmpty()) {
                                        item(key = "list_header_others") { FeedSectionHeader("Others") }
                                    }
                                }
                                items(otherEntries, key = { it.stableKey }) { entry ->
                                    StudioFeedCard(
                                        entry = entry,
                                        isPlayingVoice = isPlayingVoice,
                                        currentVoiceFile = currentVoiceFile,
                                        onOpenHubItem = { editingItem = it },
                                        onLongPressHubItem = { quickActionItem = it },
                                        onToggleChecklist = { itemId, entryId ->
                                            haptics.perform(HapticPattern.Tick)
                                            scope.launch { studioRepo.toggleChecklistEntry(itemId, entryId) }
                                        },
                                        onToggleComplete = { itemId ->
                                            haptics.perform(HapticPattern.Tick)
                                            scope.launch { studioRepo.toggleCompleted(itemId) }
                                        },
                                        onPlayPauseVoice = { memo ->
                                            haptics.perform(HapticPattern.Tick)
                                            if (isPlayingVoice && currentVoiceFile == memo.audioFile) {
                                                playbackManager.pause()
                                            } else {
                                                playbackManager.play(memo.audioFile)
                                            }
                                        },
                                        onOpenVoiceMemos = { navController.navigate(Screen.VoiceMemos) },
                                        onOpenStory = { storyId -> navController.navigate(Screen.StoryStudio(storyId = storyId)) },
                                        onZoomImages = { urls, caption -> zoomImages = urls to caption },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Translucent scrim when Morphing FAB Menu is open
            AnimatedVisibility(
                visible = isFabMenuOpen,
                enter = fadeIn(tween(if (reduceMotion) 60 else 180)),
                exit = fadeOut(tween(if (reduceMotion) 60 else 140)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isFabMenuOpen = false },
                        )
                )
            }

            // Floating Morphing FAB + Creation Menu Card
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = isFabMenuOpen,
                    enter = if (reduceMotion) {
                        fadeIn(tween(80))
                    } else {
                        scaleIn(
                            initialScale = 0.72f,
                            transformOrigin = TransformOrigin(1f, 1f),
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                        ) + fadeIn(tween(160))
                    },
                    exit = if (reduceMotion) {
                        fadeOut(tween(70))
                    } else {
                        scaleOut(
                            targetScale = 0.78f,
                            transformOrigin = TransformOrigin(1f, 1f),
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
                        ) + fadeOut(tween(120))
                    },
                ) {
                    val menuBg = MaterialTheme.colorScheme.surfaceContainerHigh
                    Surface(
                        shape = AppShapes.CardLarge,
                        color = menuBg,
                        border = lastChatSoftEdgeBorder(menuBg),
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .widthIn(min = 236.dp, max = 276.dp)
                            .lastChatBlurEffect(menuBg, AppShapes.CardLarge),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            FabMenuActionRow(
                                icon = Icons.Rounded.EditNote,
                                label = "Note",
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    isFabMenuOpen = false
                                    editingItem = StudioHubItem(type = StudioItemType.NOTE)
                                },
                            )
                            FabMenuActionRow(
                                icon = Icons.Rounded.Checklist,
                                label = "Checklist / Task",
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    isFabMenuOpen = false
                                    editingItem = StudioHubItem(
                                        type = StudioItemType.TASK_LIST,
                                        checklist = listOf(StudioChecklistEntry(text = "")),
                                    )
                                },
                            )
                            FabMenuActionRow(
                                icon = Icons.Rounded.AlarmAdd,
                                label = "Reminder",
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    isFabMenuOpen = false
                                    editingItem = StudioHubItem(
                                        type = StudioItemType.REMINDER,
                                        reminderAtMillis = System.currentTimeMillis() + 3_600_000L,
                                    )
                                },
                            )
                            FabMenuActionRow(
                                icon = Icons.Rounded.AddPhotoAlternate,
                                label = "Image Note",
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    isFabMenuOpen = false
                                    pickImageForNewNoteLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )
                            FabMenuActionRow(
                                icon = Icons.Rounded.Mic,
                                label = "Voice Memo",
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    isFabMenuOpen = false
                                    navController.navigate(Screen.VoiceMemos)
                                },
                            )
                            FabMenuActionRow(
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                label = "New Story",
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    isFabMenuOpen = false
                                    navController.navigate(Screen.StoryStudio())
                                },
                            )
                            FabMenuActionRow(
                                icon = Icons.Rounded.AutoAwesome,
                                label = "Generate Image",
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    isFabMenuOpen = false
                                    navController.navigate(Screen.ImageGen)
                                },
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        isFabMenuOpen = !isFabMenuOpen
                    },
                    shape = if (isFabMenuOpen) CircleShape else AppShapes.CardMedium,
                    containerColor = if (isFabMenuOpen) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (isFabMenuOpen) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = if (isFabMenuOpen) "Close creation menu" else "Create Studio item",
                        modifier = Modifier.graphicsLayer { rotationZ = fabRotation },
                    )
                }
            }
        }
    }

    // Note / Task / Reminder / Image Note Editor Modal Sheet
    editingItem?.let { targetItem ->
        StudioItemEditorSheet(
            initialItem = targetItem,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                scope.launch {
                    val saved = studioRepo.upsertItem(updated)
                    editingItem = null
                    toaster.show(
                        message = if (saved.title.isNotBlank()) "Saved \"${saved.title}\"" else "Saved to Studio",
                        type = ToastType.Success,
                    )
                }
            },
            onDelete = { item ->
                editingItem = null
                itemToDelete = item
            },
            onAskAiToPolish = { draft ->
                withContext(Dispatchers.IO) {
                    val assistant = settings.getAssistantById(settings.assistantId)
                        ?: settings.assistants.firstOrNull()
                    val modelId = assistant?.chatModelId ?: settings.chatModelId
                    val model = settings.findModelById(modelId)
                        ?: error("No chat model configured")
                    val rawProv = model.findProvider(settings.providers)
                        ?: error("Provider not found for ${model.displayName}")
                    val provider = secretKeyManager.populateProviderSecrets(rawProv)
                    val handler = providerManager.getProviderByType(provider)
                    val prompt = buildString {
                        appendLine("Polish and clarify this Studio note cleanly, keeping the user's meaning and tone.")
                        appendLine("If the title is empty, suggest a crisp short title on the first line as: TITLE: <title>")
                        appendLine("Then output the cleaned body text.")
                        appendLine()
                        if (draft.title.isNotBlank()) appendLine("Current title: ${draft.title}")
                        if (draft.body.isNotBlank()) appendLine("Body:\n${draft.body}")
                        if (draft.checklist.isNotEmpty()) {
                            appendLine("Checklist:")
                            draft.checklist.forEach { appendLine("- ${it.text}") }
                        }
                    }
                    val resp = handler.generateText(
                        providerSetting = provider,
                        messages = listOf(UIMessage.user(prompt)),
                        params = TextGenerationParams(model = model, temperature = 0.4f, maxTokens = 1000),
                    )
                    resp.choices.firstOrNull()?.message?.toContentText().orEmpty().trim()
                }
            },
            onAskAiAboutItem = { item ->
                editingItem = null
                val promptText = buildString {
                    appendLine("Let's talk about my Studio ${item.type.name.lowercase().replace('_', ' ')}:")
                    if (item.title.isNotBlank()) appendLine("Title: ${item.title}")
                    if (item.body.isNotBlank()) appendLine(item.body)
                    if (item.checklist.isNotEmpty()) {
                        appendLine("Checklist:")
                        item.checklist.forEach {
                            appendLine("- [${if (it.isChecked) "x" else " "}] ${it.text}")
                        }
                    }
                }.trim()
                val fileUris = item.attachmentPaths.mapNotNull { path ->
                    runCatching { Uri.fromFile(File(path)) }.getOrNull()
                }
                navigateToChatPage(
                    navController = navController,
                    initText = promptText,
                    initFiles = fileUris,
                )
            },
            onImportAttachments = { itemId, uris, startIdx ->
                studioRepo.importAttachments(
                    id = itemId,
                    sources = uris.map { it.toString() },
                    startIndex = startIdx,
                )
            },
            onZoomImages = { urls, caption -> zoomImages = urls to caption },
        )
    }

    // Long-press Quick Actions Sheet
    quickActionItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { quickActionItem = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = item.title.ifBlank { item.type.name.lowercase().replace('_', ' ') },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                // Color tint row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(STUDIO_COLORS, key = { it.token ?: "default" }) { spec ->
                        val isSelected = item.colorToken == spec.token
                        val swatchColor = if (spec.token == null) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else if (LocalDarkMode.current) {
                            spec.darkContainer
                        } else {
                            spec.lightContainer
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    haptics.perform(HapticPattern.Selection)
                                    scope.launch {
                                        studioRepo.updateColor(item.id, spec.token)
                                        quickActionItem = null
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = spec.label,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                ListItem(
                    headlineContent = { Text(if (item.isPinned) "Unpin" else "Pin to top") },
                    leadingContent = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptics.perform(HapticPattern.Tick)
                        scope.launch { studioRepo.togglePin(item.id) }
                        quickActionItem = null
                    },
                )
                ListItem(
                    headlineContent = { Text(if (item.isArchived) "Unarchive" else "Archive") },
                    leadingContent = {
                        Icon(
                            if (item.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        haptics.perform(HapticPattern.Tick)
                        scope.launch {
                            studioRepo.toggleArchive(item.id)
                            toaster.show(
                                message = if (item.isArchived) "Restored from archive" else "Archived",
                                action = ToastAction(
                                    label = "Undo",
                                    onClick = { scope.launch { studioRepo.toggleArchive(item.id) } },
                                ),
                            )
                        }
                        quickActionItem = null
                    },
                )
                ListItem(
                    headlineContent = { Text("Copy text") },
                    leadingContent = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptics.perform(HapticPattern.Tick)
                        val copied = buildString {
                            if (item.title.isNotBlank()) appendLine(item.title)
                            if (item.body.isNotBlank()) appendLine(item.body)
                            item.checklist.forEach { appendLine("- [${if (it.isChecked) "x" else " "}] ${it.text}") }
                        }.trim()
                        context.writeClipboardText(copied)
                        toaster.show("Copied to clipboard")
                        quickActionItem = null
                    },
                )
                ListItem(
                    headlineContent = { Text("Ask AI about this item") },
                    leadingContent = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptics.perform(HapticPattern.Pop)
                        quickActionItem = null
                        val promptText = buildString {
                            appendLine("Let's work on my Studio ${item.type.name.lowercase().replace('_', ' ')}:")
                            if (item.title.isNotBlank()) appendLine("Title: ${item.title}")
                            if (item.body.isNotBlank()) appendLine(item.body)
                            if (item.checklist.isNotEmpty()) {
                                appendLine("Checklist:")
                                item.checklist.forEach {
                                    appendLine("- [${if (it.isChecked) "x" else " "}] ${it.text}")
                                }
                            }
                        }.trim()
                        val fileUris = item.attachmentPaths.mapNotNull { path ->
                            runCatching { Uri.fromFile(File(path)) }.getOrNull()
                        }
                        navigateToChatPage(navController, initText = promptText, initFiles = fileUris)
                    },
                )
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        haptics.perform(HapticPattern.Pop)
                        quickActionItem = null
                        itemToDelete = item
                    },
                )
            }
        }
    }

    itemToDelete?.let { target ->
        LastChatDestructiveConfirmDialog(
            title = "Delete Studio Item",
            consequence = "This will permanently remove \"${target.title.ifBlank { "this item" }}\" and its attachments.",
            onConfirm = {
                scope.launch {
                    studioRepo.deleteItem(target.id)
                    itemToDelete = null
                    toaster.show("Deleted from Studio")
                }
            },
            onDismiss = { itemToDelete = null },
        )
    }

    zoomImages?.let { (urls, promptCaption) ->
        ImagePreviewDialog(
            images = urls,
            prompt = promptCaption,
            onDismissRequest = { zoomImages = null },
        )
    }
}

@Composable
private fun FeedSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun LiveActiveStudioBanner(
    title: String,
    subtitle: String,
    actionLabel: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    val pulseAlpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "live_banner_pulse")
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dot_alpha",
        ).value
    }

    Surface(
        shape = AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = lastChatSoftEdgeBorder(MaterialTheme.colorScheme.primaryContainer),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Surface(
                shape = AppShapes.ButtonPill,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FabMenuActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioFeedCard(
    entry: StudioFeedEntry,
    isPlayingVoice: Boolean,
    currentVoiceFile: File?,
    onOpenHubItem: (StudioHubItem) -> Unit,
    onLongPressHubItem: (StudioHubItem) -> Unit,
    onToggleChecklist: (itemId: String, entryId: String) -> Unit,
    onToggleComplete: (itemId: String) -> Unit,
    onPlayPauseVoice: (VoiceMemoItem) -> Unit,
    onOpenVoiceMemos: () -> Unit,
    onOpenStory: (String) -> Unit,
    onZoomImages: (List<String>, String?) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by rememberControlPressValue(isPressed, 0.97f, "studio_card_scale")

    when (entry) {
        is StudioFeedEntry.HubItem -> {
            val item = entry.item
            val bg = studioCardContainerColor(item.colorToken)
            Surface(
                shape = AppShapes.CardMedium,
                color = bg,
                border = lastChatSoftEdgeBorder(bg),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardMedium)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onOpenHubItem(item) },
                        onLongClick = { onLongPressHubItem(item) },
                    ),
            ) {
                HubItemCardContent(
                    item = item,
                    onToggleChecklist = onToggleChecklist,
                    onToggleComplete = onToggleComplete,
                    onZoomImages = onZoomImages,
                )
            }
        }

        is StudioFeedEntry.VoiceMemo -> {
            val memo = entry.memo
            val bg = MaterialTheme.colorScheme.surfaceContainerLow
            val playingThis = isPlayingVoice && currentVoiceFile == memo.audioFile
            Surface(
                shape = AppShapes.CardMedium,
                color = bg,
                border = lastChatSoftEdgeBorder(bg),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardMedium)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onOpenVoiceMemos,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = AppShapes.ButtonPill,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    text = formatDuration(memo.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (playingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            onClick = { onPlayPauseVoice(memo) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playingThis) "Pause" else "Play",
                                    tint = if (playingThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = memo.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val preview = memo.summary?.takeIf { it.isNotBlank() }
                        ?: memo.transcript?.takeIf { it.isNotBlank() }
                        ?: "Tap to transcribe & summarize"
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        is StudioFeedEntry.Story -> {
            val project = entry.project
            val bg = MaterialTheme.colorScheme.surfaceContainerLow
            val beatsCount = project.bible?.beats?.size ?: 0
            val completedBeats = project.bible?.beats?.count { it.status == BeatStatus.Done } ?: 0
            val fraction = if (beatsCount > 0) (completedBeats.toFloat() / beatsCount).coerceIn(0f, 1f) else 0f

            Surface(
                shape = AppShapes.CardMedium,
                color = bg,
                border = lastChatSoftEdgeBorder(bg),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardMedium)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onOpenStory(project.id) },
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = AppShapes.ButtonPill,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = project.genre.ifBlank { "Story" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Text(
                            text = "${project.totalWords}w",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = project.premise,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (beatsCount > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(AppShapes.ButtonPill),
                            )
                            Text(
                                text = "Beat $completedBeats/$beatsCount • ${project.status.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        is StudioFeedEntry.GeneratedImage -> {
            val media = entry.media
            val bg = MaterialTheme.colorScheme.surfaceContainerLow
            Surface(
                shape = AppShapes.CardMedium,
                color = bg,
                border = lastChatSoftEdgeBorder(bg),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardMedium)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onZoomImages(listOf(media.path), media.prompt) },
                    ),
            ) {
                Column {
                    AsyncImage(
                        model = media.path,
                        contentDescription = media.prompt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    if (media.prompt.isNotBlank()) {
                        Text(
                            text = media.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HubItemCardContent(
    item: StudioHubItem,
    onToggleChecklist: (itemId: String, entryId: String) -> Unit,
    onToggleComplete: (itemId: String) -> Unit,
    onZoomImages: (List<String>, String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Optional edge-to-edge top image thumbnail
        if (item.attachmentPaths.isNotEmpty()) {
            val firstPath = item.attachmentPaths.first()
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = firstPath,
                    contentDescription = item.title.ifBlank { "Image attachment" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp, max = 165.dp)
                        .clickable {
                            onZoomImages(item.attachmentPaths, item.title.ifBlank { null })
                        },
                )
                if (item.attachmentPaths.size > 1) {
                    Surface(
                        shape = AppShapes.ButtonPill,
                        color = Color.Black.copy(alpha = 0.62f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = "+${item.attachmentPaths.size - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (item.isPinned || item.createdBy != "user") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.createdBy != "user") {
                        Surface(
                            shape = AppShapes.ButtonPill,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = if (item.createdBy == "quick_ask") "Quick Ask" else "Added by AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            if (item.body.isNotBlank()) {
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.checklist.isNotEmpty()) {
                val visibleEntries = item.checklist.take(5)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    visibleEntries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onToggleChecklist(item.id, entry.id) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (entry.isChecked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (entry.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = entry.text.ifBlank { "Untitled task" },
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = if (entry.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (entry.isChecked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (item.checklist.size > 5) {
                        Text(
                            text = "+${item.checklist.size - 5} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (item.reminderAtMillis != null) {
                val isOverdue = !item.isCompleted && item.reminderAtMillis < System.currentTimeMillis()
                val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = AppShapes.ButtonPill,
                        color = when {
                            item.isCompleted -> MaterialTheme.colorScheme.surfaceContainerHighest
                            isOverdue -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        onClick = { onToggleComplete(item.id) },
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (item.isCompleted) Icons.Rounded.Check else Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = when {
                                    item.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = fmt.format(Date(item.reminderAtMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    item.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (item.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioEmptyState(
    filter: StudioFilter,
    hasQuery: Boolean,
    onCreateNote: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = if (hasQuery) "No matching items" else "Nothing in ${filter.label} yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (hasQuery) {
                    "Try a different keyword or switch filter chips."
                } else {
                    "Tap + to capture a note, checklist, reminder, voice memo, or story."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasQuery && (filter == StudioFilter.ALL || filter == StudioFilter.NOTES)) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onCreateNote, shape = AppShapes.ButtonPill) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Note")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StudioItemEditorSheet(
    initialItem: StudioHubItem,
    onDismiss: () -> Unit,
    onSave: (StudioHubItem) -> Unit,
    onDelete: (StudioHubItem) -> Unit,
    onAskAiToPolish: suspend (StudioHubItem) -> String,
    onAskAiAboutItem: (StudioHubItem) -> Unit,
    onImportAttachments: suspend (String, List<Uri>, Int) -> List<String>,
    onZoomImages: (List<String>, String?) -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    var type by remember(initialItem.id) { mutableStateOf(initialItem.type) }
    var title by remember(initialItem.id) { mutableStateOf(initialItem.title) }
    var body by remember(initialItem.id) { mutableStateOf(initialItem.body) }
    var checklist by remember(initialItem.id) { mutableStateOf(initialItem.checklist) }
    var newChecklistInput by remember(initialItem.id) { mutableStateOf("") }
    var attachments by remember(initialItem.id) { mutableStateOf(initialItem.attachmentPaths) }
    var tags by remember(initialItem.id) { mutableStateOf(initialItem.tags) }
    var tagInput by remember(initialItem.id) { mutableStateOf("") }
    var colorToken by remember(initialItem.id) { mutableStateOf(initialItem.colorToken) }
    var isPinned by remember(initialItem.id) { mutableStateOf(initialItem.isPinned) }
    var isCompleted by remember(initialItem.id) { mutableStateOf(initialItem.isCompleted) }
    var reminderAtMillis by remember(initialItem.id) { mutableStateOf(initialItem.reminderAtMillis) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingPickedDateMillis by remember { mutableStateOf<Long?>(null) }
    var isPolishing by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val imported = onImportAttachments(initialItem.id, uris, attachments.size)
                attachments = (attachments + imported).distinct()
                if (type == StudioItemType.NOTE && attachments.isNotEmpty()) {
                    type = StudioItemType.IMAGE_NOTE
                }
            }
        }
    }

    fun currentDraft(): StudioHubItem = initialItem.copy(
        type = if (checklist.isNotEmpty() && type == StudioItemType.NOTE) StudioItemType.TASK_LIST else type,
        title = title.trim(),
        body = body.trim(),
        checklist = checklist.filter { it.text.isNotBlank() },
        attachmentPaths = attachments,
        tags = tags,
        colorToken = colorToken,
        isPinned = isPinned,
        isCompleted = isCompleted,
        reminderAtMillis = reminderAtMillis,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar inside editor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            isPinned = !isPinned
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Add Image")
                    }
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            showDatePicker = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AlarmAdd,
                            contentDescription = "Set Reminder",
                            tint = if (reminderAtMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onDelete(initialItem) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onSave(currentDraft())
                    },
                    shape = AppShapes.ButtonPill,
                ) {
                    Text("Save")
                }
            }

            // Type switcher chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(
                        StudioItemType.NOTE to "Note",
                        StudioItemType.TASK_LIST to "Task List",
                        StudioItemType.REMINDER to "Reminder",
                        StudioItemType.IMAGE_NOTE to "Image Note",
                    )
                ) { (t, label) ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            type = t
                            if (t == StudioItemType.REMINDER && reminderAtMillis == null) {
                                reminderAtMillis = System.currentTimeMillis() + 3_600_000L
                            }
                        },
                        label = { Text(label) },
                        shape = AppShapes.ButtonPill,
                    )
                }
            }

            // Attachment preview strip
            if (attachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachments, key = { it }) { path ->
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(AppShapes.CardSmall),
                        ) {
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onZoomImages(attachments, title.ifBlank { null }) },
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                onClick = { attachments = attachments.filterNot { it == path } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove image",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                shape = AppShapes.InputField,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Note") },
                minLines = 3,
                maxLines = 10,
                shape = AppShapes.InputField,
                modifier = Modifier.fillMaxWidth(),
            )

            // Checklist Builder
            Surface(
                shape = AppShapes.CardSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Checklist",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    checklist.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Checkbox(
                                checked = entry.isChecked,
                                onCheckedChange = { checked ->
                                    haptics.perform(HapticPattern.Tick)
                                    checklist = checklist.map {
                                        if (it.id == entry.id) it.copy(isChecked = checked) else it
                                    }
                                },
                            )
                            OutlinedTextField(
                                value = entry.text,
                                onValueChange = { txt ->
                                    checklist = checklist.map {
                                        if (it.id == entry.id) it.copy(text = txt) else it
                                    }
                                },
                                placeholder = { Text("Task item") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = AppShapes.InputField,
                            )
                            IconButton(
                                onClick = { checklist = checklist.filterNot { it.id == entry.id } },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove task", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = newChecklistInput,
                            onValueChange = { newChecklistInput = it },
                            placeholder = { Text("Add checklist item…") },
                            singleLine = true,
                            shape = AppShapes.InputField,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                val text = newChecklistInput.trim()
                                if (text.isNotBlank()) {
                                    haptics.perform(HapticPattern.Tick)
                                    checklist = checklist + StudioChecklistEntry(text = text)
                                    newChecklistInput = ""
                                }
                            },
                            shape = AppShapes.ButtonPill,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Reminder chip
            if (reminderAtMillis != null) {
                val fmt = remember { SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()) }
                Surface(
                    shape = AppShapes.CardSmall,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = fmt.format(Date(reminderAtMillis!!)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                        )
                        TextButton(onClick = { isCompleted = !isCompleted }) {
                            Text(if (isCompleted) "Reopen" else "Done")
                        }
                        IconButton(
                            onClick = { reminderAtMillis = null },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear reminder", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Tags builder
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = { tags = tags.filterNot { it == tag } },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Add tag (e.g. ideas, work)…") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        val cleaned = tagInput.trim().removePrefix("#")
                        if (cleaned.isNotBlank() && cleaned !in tags) {
                            tags = tags + cleaned
                            tagInput = ""
                        }
                    },
                    shape = AppShapes.ButtonPill,
                ) {
                    Text("Tag")
                }
            }

            // Color Tint Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                STUDIO_COLORS.forEach { spec ->
                    val selected = colorToken == spec.token
                    val swatch = if (spec.token == null) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else if (LocalDarkMode.current) {
                        spec.darkContainer
                    } else {
                        spec.lightContainer
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable {
                                haptics.perform(HapticPattern.Selection)
                                colorToken = spec.token
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = spec.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // AI Helpers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (isPolishing) return@OutlinedButton
                        haptics.perform(HapticPattern.Pop)
                        isPolishing = true
                        scope.launch {
                            runCatching { onAskAiToPolish(currentDraft()) }
                                .onSuccess { result ->
                                    if (result.isNotBlank()) {
                                        val lines = result.lines()
                                        val first = lines.firstOrNull()?.trim().orEmpty()
                                        if (first.startsWith("TITLE:", ignoreCase = true)) {
                                            if (title.isBlank()) {
                                                title = first.substringAfter(":").trim()
                                            }
                                            body = lines.drop(1).joinToString("\n").trim()
                                        } else {
                                            body = result
                                        }
                                        toaster.show("Polished with AI", type = ToastType.Success)
                                    }
                                }
                                .onFailure { err ->
                                    toaster.show("AI polish failed: ${err.message}", type = ToastType.Error)
                                }
                            isPolishing = false
                        }
                    },
                    enabled = !isPolishing && (body.isNotBlank() || title.isNotBlank() || checklist.isNotEmpty()),
                    shape = AppShapes.ButtonPill,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isPolishing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Polish with AI", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onAskAiAboutItem(currentDraft())
                    },
                    shape = AppShapes.ButtonPill,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ask AI", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = reminderAtMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPickedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val baseCal = Calendar.getInstance().apply {
            timeInMillis = reminderAtMillis ?: (System.currentTimeMillis() + 3_600_000L)
        }
        val timeState = rememberTimePickerState(
            initialHour = baseCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = baseCal.get(Calendar.MINUTE),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Reminder Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = pendingPickedDateMillis ?: System.currentTimeMillis()
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, timeState.hour)
                            set(Calendar.MINUTE, timeState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        reminderAtMillis = localCal.timeInMillis
                        if (type == StudioItemType.NOTE) {
                            type = StudioItemType.REMINDER
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("Set Reminder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
