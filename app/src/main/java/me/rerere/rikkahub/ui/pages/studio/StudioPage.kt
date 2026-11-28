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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.repository.BeatStatus
import me.rerere.rikkahub.data.repository.GenMediaRepository
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
import me.rerere.rikkahub.utils.base64Encode
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

private fun studioGreetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late night studio"
}

private fun buildStudioAskAiPrompt(item: StudioHubItem): String = buildString {
    val kind = item.type.name.lowercase().replace('_', ' ')
    appendLine("Let's talk about my Studio $kind:")
    if (item.title.isNotBlank()) {
        appendLine("Title: ${item.title}")
    }
    if (item.body.isNotBlank()) {
        appendLine()
        appendLine(item.body.trim())
    }
    if (item.checklist.isNotEmpty()) {
        appendLine()
        appendLine("Checklist:")
        item.checklist.forEach { entry ->
            appendLine("- [${if (entry.isChecked) "x" else " "}] ${entry.text}")
        }
    }
    if (item.tags.isNotEmpty()) {
        appendLine()
        appendLine("Tags: ${item.tags.joinToString(", ") { "#$it" }}")
    }
    if (item.reminderAtMillis != null) {
        val fmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        appendLine("Reminder: ${fmt.format(Date(item.reminderAtMillis))}")
    }
    if (!item.sourceUrl.isNullOrBlank()) {
        appendLine("Source: ${item.sourceUrl}")
    }
}.trim()

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

    val activeAssistant = remember(settings.assistants, settings.assistantId) {
        settings.getAssistantById(settings.assistantId) ?: settings.assistants.firstOrNull()
    }
    val activeAssistantName = activeAssistant?.name?.trim()?.takeIf { it.isNotBlank() } ?: "Assistant"

    val resolvedEnhanceModel = remember(settings.studioEnhanceModelId, activeAssistant?.chatModelId, settings.chatModelId, settings.providers) {
        val targetId = settings.studioEnhanceModelId ?: activeAssistant?.chatModelId ?: settings.chatModelId
        settings.findModelById(targetId)
    }
    val enhanceModelName = remember(resolvedEnhanceModel, settings.studioEnhanceModelId) {
        val base = resolvedEnhanceModel?.displayName?.ifBlank { resolvedEnhanceModel.modelId }
            ?: "Default Chat Model"
        if (settings.studioEnhanceModelId == null) "$base (Chat default)" else base
    }

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
    var colorThemeItem by remember { mutableStateOf<StudioHubItem?>(null) }
    var itemToDelete by remember { mutableStateOf<StudioHubItem?>(null) }
    var showAppearanceSettings by remember { mutableStateOf(false) }
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
                    colorToken = settings.studioAppearance.defaultNoteColorToken,
                    themeId = settings.studioAppearance.defaultNoteThemeId,
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
                        i.checklist.any { it.text.lowercase().contains(q) } ||
                        i.createdByAssistantName?.lowercase()?.contains(q) == true
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

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) { studioGreetingForHour(currentHour) }
    val totalActiveCount = filterCounts[StudioFilter.ALL] ?: 0
    val pinnedTotalCount = remember(hubItems) { hubItems.count { !it.isArchived && it.isPinned } }

    fun launchAskAiForItem(item: StudioHubItem) {
        val promptText = buildStudioAskAiPrompt(item)
        val encodedPrompt = promptText.base64Encode()
        val fileUris = item.attachmentPaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) runCatching { Uri.fromFile(file) }.getOrNull() else null
        }
        navigateToChatPage(
            navController = navController,
            initText = encodedPrompt,
            initFiles = fileUris,
        )
    }

    BackHandler(enabled = isFabMenuOpen && editingItem == null) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact, warm editorial top bar (no giant empty void)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BackButton()

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = if (settings.studioAppearance.showGreetingHeader) greeting else "Studio",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.3).sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = buildString {
                                append(if (totalActiveCount == 1) "1 item" else "$totalActiveCount items")
                                if (pinnedTotalCount > 0) append(" · $pinnedTotalCount pinned")
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                showAppearanceSettings = true
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Studio customization",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                navController.navigate(Screen.Library)
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Collections,
                                    contentDescription = "Creations Library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // Keep-inspired search bar + grid/list toggle
                    val searchSurface = MaterialTheme.colorScheme.surfaceContainerHigh
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = searchSurface,
                        border = lastChatSoftEdgeBorder(searchSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search notes, tasks, memos, stories...",
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
                                    transitionSpec = {
                                        fadeIn(tween(if (reduceMotion) 0 else 140)) togetherWith
                                            fadeOut(tween(if (reduceMotion) 0 else 120))
                                    },
                                    label = "layout_toggle_icon",
                                ) { grid ->
                                    Icon(
                                        imageVector = if (grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                                        contentDescription = if (grid) "Switch to list view" else "Switch to grid view",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Horizontal Filter Rail
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
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
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
                                    subtitle = "Beat ${story.currentBeatIndex + 1} · ${story.totalWords} words",
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
                                haptics.perform(HapticPattern.Pop)
                                editingItem = StudioHubItem(
                                    type = StudioItemType.NOTE,
                                    colorToken = settings.studioAppearance.defaultNoteColorToken,
                                    themeId = settings.studioAppearance.defaultNoteThemeId,
                                )
                            },
                            onCreateChecklist = {
                                haptics.perform(HapticPattern.Pop)
                                editingItem = StudioHubItem(
                                    type = StudioItemType.TASK_LIST,
                                    checklist = listOf(StudioChecklistEntry(text = "")),
                                    colorToken = settings.studioAppearance.defaultNoteColorToken,
                                    themeId = settings.studioAppearance.defaultNoteThemeId,
                                )
                            },
                        )
                    } else {
                        AnimatedContent(
                            targetState = isGridLayout,
                            transitionSpec = {
                                fadeIn(tween(if (reduceMotion) 0 else 180)) togetherWith
                                    fadeOut(tween(if (reduceMotion) 0 else 140))
                            },
                            label = "feed_layout_mode",
                            modifier = Modifier.weight(1f),
                        ) { grid ->
                            if (grid) {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(2),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                                    verticalItemSpacing = 12.dp,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    if (pinnedEntries.isNotEmpty()) {
                                        item(span = StaggeredGridItemSpan.FullLine, key = "header_pinned") {
                                            FeedSectionHeader("Pinned")
                                        }
                                        staggeredItems(pinnedEntries, key = { it.stableKey }) { entry ->
                                            StudioFeedCard(
                                                entry = entry,
                                                settings = settings,
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
                                                modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
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
                                            settings = settings,
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
                                            modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    if (pinnedEntries.isNotEmpty()) {
                                        item(key = "list_header_pinned") { FeedSectionHeader("Pinned") }
                                        items(pinnedEntries, key = { it.stableKey }) { entry ->
                                            StudioFeedCard(
                                                entry = entry,
                                                settings = settings,
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
                                                modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                                            )
                                        }
                                        if (otherEntries.isNotEmpty()) {
                                            item(key = "list_header_others") { FeedSectionHeader("Others") }
                                        }
                                    }
                                    items(otherEntries, key = { it.stableKey }) { entry ->
                                        StudioFeedCard(
                                            entry = entry,
                                            settings = settings,
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
                                            modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
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
                                initialScale = 0.88f,
                                transformOrigin = TransformOrigin(1f, 1f),
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                            ) + fadeIn(tween(160))
                        },
                        exit = if (reduceMotion) {
                            fadeOut(tween(70))
                        } else {
                            scaleOut(
                                targetScale = 0.90f,
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
                                .widthIn(min = 232.dp, max = 272.dp)
                                .lastChatBlurEffect(menuBg, AppShapes.CardLarge),
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                FabMenuActionRow(
                                    icon = Icons.Rounded.EditNote,
                                    label = "Note",
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        isFabMenuOpen = false
                                        editingItem = StudioHubItem(
                                            type = StudioItemType.NOTE,
                                            colorToken = settings.studioAppearance.defaultNoteColorToken,
                                            themeId = settings.studioAppearance.defaultNoteThemeId,
                                        )
                                    },
                                )
                                FabMenuActionRow(
                                    icon = Icons.Rounded.Checklist,
                                    label = "Checklist",
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        isFabMenuOpen = false
                                        editingItem = StudioHubItem(
                                            type = StudioItemType.TASK_LIST,
                                            checklist = listOf(StudioChecklistEntry(text = "")),
                                            colorToken = settings.studioAppearance.defaultNoteColorToken,
                                            themeId = settings.studioAppearance.defaultNoteThemeId,
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
                                            colorToken = settings.studioAppearance.defaultNoteColorToken,
                                            themeId = settings.studioAppearance.defaultNoteThemeId,
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

        // Full-Screen Google Keep-Style Note / Task / Photo / Reminder Editor
        AnimatedVisibility(
            visible = editingItem != null,
            enter = if (reduceMotion) {
                fadeIn(tween(80))
            } else {
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(220),
                ) + fadeIn(tween(180))
            },
            exit = if (reduceMotion) {
                fadeOut(tween(80))
            } else {
                scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(160),
                ) + fadeOut(tween(150))
            },
        ) {
            editingItem?.let { targetItem ->
                StudioItemEditorScreen(
                    initialItem = targetItem,
                    settings = settings,
                    activeAssistantName = activeAssistantName,
                    enhanceModelName = enhanceModelName,
                    onCloseAndSave = { draft, shouldPersist ->
                        editingItem = null
                        if (shouldPersist) {
                            scope.launch {
                                studioRepo.upsertItem(draft)
                            }
                        }
                    },
                    onDelete = { item ->
                        editingItem = null
                        itemToDelete = item
                    },
                    onDuplicate = { draft ->
                        scope.launch {
                            val copy = studioRepo.createItem(
                                type = draft.type,
                                title = if (draft.title.isNotBlank()) "${draft.title} (Copy)" else "",
                                body = draft.body,
                                checklist = draft.checklist.map { StudioChecklistEntry(text = it.text, isChecked = it.isChecked) },
                                attachmentSources = draft.attachmentPaths,
                                tags = draft.tags,
                                colorToken = draft.colorToken,
                                themeId = draft.themeId,
                                isPinned = false,
                                reminderAtMillis = draft.reminderAtMillis,
                                sourceUrl = draft.sourceUrl,
                                createdBy = "user",
                            )
                            editingItem = copy
                            toaster.show("Duplicated note", type = ToastType.Success)
                        }
                    },
                    onAskAiToEnhance = { draft ->
                        withContext(Dispatchers.IO) {
                            val modelId = settings.studioEnhanceModelId
                                ?: activeAssistant?.chatModelId
                                ?: settings.chatModelId
                            val model = settings.findModelById(modelId)
                                ?: error("No enhance or chat model configured")
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
                                params = TextGenerationParams(model = model, temperature = 0.4f, maxTokens = 1200),
                            )
                            resp.choices.firstOrNull()?.message?.toContentText().orEmpty().trim()
                        }
                    },
                    onAskAiAboutItem = { draft ->
                        scope.launch {
                            val saved = if (
                                draft.title.isNotBlank() ||
                                draft.body.isNotBlank() ||
                                draft.checklist.isNotEmpty() ||
                                draft.attachmentPaths.isNotEmpty()
                            ) {
                                studioRepo.upsertItem(draft)
                            } else {
                                draft
                            }
                            editingItem = null
                            launchAskAiForItem(saved)
                        }
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
        }
    }

    // Long-press Quick Actions Sheet
    quickActionItem?.let { item ->
        val isDark = LocalDarkMode.current
        val amoled by rememberAmoledDarkMode()
        val normalizedSelected = normalizeColorToken(item.colorToken)
        ModalBottomSheet(
            onDismissRequest = { quickActionItem = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                // Quick color swatch rail
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(STUDIO_NOTE_COLORS, key = { it.token ?: "default" }) { spec ->
                        val isSelected = normalizedSelected == spec.token
                        val swatchColor = if (spec.token == null) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else when {
                            amoled && isDark -> spec.amoledBg
                            isDark -> spec.darkBg
                            else -> spec.lightBg
                        }
                        val checkTint = if (spec.token == null) {
                            MaterialTheme.colorScheme.primary
                        } else if (isDark) spec.darkInk else spec.lightInk
                        Box(
                            modifier = Modifier
                                .size(36.dp)
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
                                    tint = checkTint,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                ListItem(
                    headlineContent = { Text("Color & background theme") },
                    leadingContent = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptics.perform(HapticPattern.Selection)
                        quickActionItem = null
                        colorThemeItem = item
                    },
                )
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
                    headlineContent = { Text("Ask $activeAssistantName about this item") },
                    leadingContent = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptics.perform(HapticPattern.Pop)
                        quickActionItem = null
                        launchAskAiForItem(item)
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

    // Color & Background Theme Picker Sheet (from long-press)
    colorThemeItem?.let { item ->
        StudioColorAndThemePickerSheet(
            selectedColorToken = item.colorToken,
            selectedThemeId = item.themeId,
            onSelectColor = { newColor ->
                val updated = item.copy(colorToken = newColor)
                colorThemeItem = updated
                scope.launch { studioRepo.updateAppearance(item.id, newColor, updated.themeId) }
            },
            onSelectTheme = { newTheme ->
                val updated = item.copy(themeId = newTheme)
                colorThemeItem = updated
                scope.launch { studioRepo.updateAppearance(item.id, updated.colorToken, newTheme) }
            },
            onDismiss = { colorThemeItem = null },
        )
    }

    // Studio Customization Sheet (attribution style, wording, default models link)
    if (showAppearanceSettings) {
        StudioAppearanceSettingsSheet(
            settings = settings,
            activeAssistantName = activeAssistantName,
            enhanceModelName = enhanceModelName,
            onUpdateAppearance = { updated ->
                scope.launch {
                    settingsStore.update { current ->
                        current.copy(studioAppearance = updated)
                    }
                }
            },
            onOpenDefaultModelsSettings = {
                navController.navigate(Screen.SettingModels)
            },
            onDismiss = { showAppearanceSettings = false },
        )
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
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                        fontWeight = FontWeight.SemiBold,
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
                .clip(RoundedCornerShape(10.dp))
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
    settings: Settings,
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
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by rememberControlPressValue(isPressed, 0.975f, "studio_card_scale")

    when (entry) {
        is StudioFeedEntry.HubItem -> {
            val item = entry.item
            val palette = rememberStudioPalette(item.colorToken)
            Surface(
                shape = AppShapes.CardSmall,
                color = palette.container,
                border = BorderStroke(1.dp, palette.border),
                modifier = modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardSmall)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onOpenHubItem(item) },
                        onLongClick = { onLongPressHubItem(item) },
                    ),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    StudioCanvasThemeBackdrop(
                        themeId = item.themeId,
                        inkColor = palette.onContainer,
                        accentColor = palette.accent,
                        isCardPreview = true,
                        modifier = Modifier.matchParentSize(),
                    )
                    HubItemCardContent(
                        item = item,
                        settings = settings,
                        palette = palette,
                        onToggleChecklist = onToggleChecklist,
                        onToggleComplete = onToggleComplete,
                        onZoomImages = onZoomImages,
                    )
                }
            }
        }

        is StudioFeedEntry.VoiceMemo -> {
            val memo = entry.memo
            val bg = MaterialTheme.colorScheme.surfaceContainerLow
            val playingThis = isPlayingVoice && currentVoiceFile == memo.audioFile
            Surface(
                shape = AppShapes.CardSmall,
                color = bg,
                border = lastChatSoftEdgeBorder(bg),
                modifier = modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardSmall)
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
                            shape = RoundedCornerShape(8.dp),
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
                        fontWeight = FontWeight.SemiBold,
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
                shape = AppShapes.CardSmall,
                color = bg,
                border = lastChatSoftEdgeBorder(bg),
                modifier = modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardSmall)
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
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
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
                        fontWeight = FontWeight.SemiBold,
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
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                            Text(
                                text = "Beat $completedBeats/$beatsCount · ${project.status.name}",
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
                shape = AppShapes.CardSmall,
                color = bg,
                border = lastChatSoftEdgeBorder(bg),
                modifier = modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(AppShapes.CardSmall)
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
                            modifier = Modifier.padding(12.dp),
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
    settings: Settings,
    palette: StudioResolvedPalette,
    onToggleChecklist: (itemId: String, entryId: String) -> Unit,
    onToggleComplete: (itemId: String) -> Unit,
    onZoomImages: (List<String>, String?) -> Unit,
) {
    val creatorName = remember(item, settings.assistants, settings.assistantId) {
        resolveStudioCreatorName(item, settings)
    }

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
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.64f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = "+${item.attachmentPaths.size - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (item.title.isNotBlank() || item.isPinned) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (item.title.isNotBlank()) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (item.isCompleted) {
                                palette.onContainer.copy(alpha = 0.55f)
                            } else {
                                palette.onContainer
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = palette.accent,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(14.dp),
                        )
                    }
                }
            }

            if (item.body.isNotBlank()) {
                val formattedPreview = remember(item.body, palette.chipContainer) {
                    buildCleanMarkdownAnnotatedString(
                        raw = item.body,
                        codeBackground = palette.chipContainer,
                    )
                }
                Text(
                    text = formattedPreview,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = palette.onContainerMuted,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.checklist.isNotEmpty()) {
                val unchecked = item.checklist.filterNot { it.isChecked }
                val checkedCount = item.checklist.size - unchecked.size
                val visibleEntries = (unchecked.take(5) + if (unchecked.size < 5) item.checklist.filter { it.isChecked }.take(5 - unchecked.size) else emptyList())

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
                                tint = if (entry.isChecked) palette.accent else palette.onContainerMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = entry.text.ifBlank { "Untitled task" },
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = if (entry.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (entry.isChecked) {
                                    palette.onContainerMuted.copy(alpha = 0.6f)
                                } else {
                                    palette.onContainer
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val hiddenCount = item.checklist.size - visibleEntries.size
                    if (hiddenCount > 0) {
                        Text(
                            text = if (checkedCount > 0) "+$hiddenCount more ($checkedCount checked)" else "+$hiddenCount more",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                        )
                    }
                }
            }

            if (item.reminderAtMillis != null) {
                val isOverdue = !item.isCompleted && item.reminderAtMillis < System.currentTimeMillis()
                val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        item.isCompleted -> palette.chipContainer
                        isOverdue -> MaterialTheme.colorScheme.errorContainer
                        else -> palette.accent.copy(alpha = 0.16f)
                    },
                    onClick = { onToggleComplete(item.id) },
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
                                item.isCompleted -> palette.onContainerMuted
                                isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                else -> palette.accent
                            },
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = fmt.format(Date(item.reminderAtMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                item.isCompleted -> palette.onContainerMuted
                                isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                else -> palette.onContainer
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (item.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    item.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = palette.chipContainer,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.chipContent,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                    if (item.tags.size > 3) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = palette.chipContainer,
                        ) {
                            Text(
                                text = "+${item.tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.chipContent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // Tasteful, customizable Assistant attribution footer (never "Added by AI")
            if (creatorName != null) {
                StudioAttributionBadge(
                    creatorName = creatorName,
                    appearance = settings.studioAppearance,
                    palette = palette,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StudioEmptyState(
    filter: StudioFilter,
    hasQuery: Boolean,
    onCreateNote: () -> Unit,
    onCreateChecklist: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                text = if (hasQuery) "No matching items" else "Your ${filter.label.lowercase()} space is ready",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (hasQuery) {
                    "Try a different keyword or switch filters above."
                } else {
                    "Capture quick notes, checklists, reminders, voice memos, or stories in one calm place."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!hasQuery && (filter == StudioFilter.ALL || filter == StudioFilter.NOTES || filter == StudioFilter.TASKS)) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCreateNote,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Note")
                    }
                    OutlinedButton(
                        onClick = onCreateChecklist,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Checklist")
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
