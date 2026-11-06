package me.rerere.rikkahub.ui.pages.library

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.dao.MemorySummaryDao
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.db.entity.MemorySummaryEntity
import me.rerere.rikkahub.data.model.OtherUploadFile
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.ArtifactStorageManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.SavedArtifact
import me.rerere.rikkahub.data.repository.StoryProject
import me.rerere.rikkahub.data.repository.StoryStudioRepository
import me.rerere.rikkahub.data.repository.VoiceMemoRecord
import me.rerere.rikkahub.data.repository.VoiceMemoRepository
import me.rerere.rikkahub.service.voice.VoicePlaybackManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.richtext.ArtifactZoomDialog
import me.rerere.rikkahub.ui.components.toast.LocalToaster
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.openAttachmentUri
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject
import java.io.File

enum class LibraryCategory(
    val label: String,
    val icon: ImageVector,
) {
    ALL("All", Icons.Rounded.GridView),
    PHOTOS("Photos & Art", Icons.Rounded.Collections),
    ARTIFACTS("Artifacts", Icons.Rounded.AutoAwesome),
    STORIES("Stories", Icons.AutoMirrored.Rounded.MenuBook),
    RECORDINGS("Recordings", Icons.Rounded.Mic),
    SUMMARIES("Summaries", Icons.Rounded.Psychology),
    UPLOADS("Uploads & Files", Icons.Rounded.CloudUpload),
}

sealed class LibraryItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val timestamp: Long
    abstract val category: LibraryCategory

    data class Photo(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val timestamp: Long,
        val filePath: String,
        val uri: String,
        val prompt: String,
        val entityId: Int? = null,
    ) : LibraryItem() {
        override val category: LibraryCategory = LibraryCategory.PHOTOS
    }

    data class Artifact(
        val artifact: SavedArtifact,
    ) : LibraryItem() {
        override val id: String = artifact.id
        override val title: String = artifact.title
        override val subtitle: String = "HTML In-World Visual • ${artifact.sizeBytes.fileSizeToString()}"
        override val timestamp: Long = artifact.timestamp
        override val category: LibraryCategory = LibraryCategory.ARTIFACTS
    }

    data class Story(
        val project: StoryProject,
    ) : LibraryItem() {
        override val id: String = project.id
        override val title: String = project.title
        override val subtitle: String = "${project.genre} • ${project.totalWords} words • ${project.status.name}"
        override val timestamp: Long = project.updatedAt
        override val category: LibraryCategory = LibraryCategory.STORIES
    }

    data class Recording(
        val memo: VoiceMemoRecord,
    ) : LibraryItem() {
        override val id: String = memo.id
        override val title: String = memo.title
        override val subtitle: String = "${memo.durationMs / 1000}s • ${if (memo.isTranscribed) "Transcribed" else "Audio only"}"
        override val timestamp: Long = memo.createdAt
        override val category: LibraryCategory = LibraryCategory.RECORDINGS
    }

    data class Summary(
        val entity: MemorySummaryEntity,
        val assistantName: String,
    ) : LibraryItem() {
        override val id: String = entity.assistantId
        override val title: String = "$assistantName's Memory Summary"
        override val subtitle: String = "${entity.tokenCount} tokens • ${DateUtils.getRelativeTimeSpanString(entity.updatedAt)}"
        override val timestamp: Long = entity.updatedAt
        override val category: LibraryCategory = LibraryCategory.SUMMARIES
    }

    data class Upload(
        val file: OtherUploadFile,
    ) : LibraryItem() {
        override val id: String = file.path
        override val title: String = file.displayName
        override val subtitle: String = "${file.mime ?: "File"} • ${file.sizeBytes.fileSizeToString()}"
        override val timestamp: Long = file.modifiedAt
        override val category: LibraryCategory = LibraryCategory.UPLOADS
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val genMediaDao = koinInject<GenMediaDAO>()
    val artifactManager = ArtifactStorageManager
    val storyRepo = koinInject<StoryStudioRepository>()
    val memoRepo = koinInject<VoiceMemoRepository>()
    val summaryDao = koinInject<MemorySummaryDao>()
    val appStorageRepo = koinInject<AppStorageRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val playbackManager = koinInject<VoicePlaybackManager>()

    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val isPlaying by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val currentPlayingFile by playbackManager.currentFile.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LibraryCategory.ALL) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var allItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }

    // Dialog & Inspection States
    var previewImage by remember { mutableStateOf<LibraryItem.Photo?>(null) }
    var previewArtifact by remember { mutableStateOf<SavedArtifact?>(null) }
    var previewStory by remember { mutableStateOf<StoryProject?>(null) }
    var previewMemo by remember { mutableStateOf<VoiceMemoRecord?>(null) }
    var previewSummary by remember { mutableStateOf<Pair<MemorySummaryEntity, String>?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<LibraryItem?>(null) }

    // Load and index all library assets
    LaunchedEffect(refreshTrigger) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val items = mutableListOf<LibraryItem>()

            // 1. Photos & Generated Art
            runCatching {
                val mediaEntities = genMediaDao.getAllMedia()
                mediaEntities.forEach { entity ->
                    val file = File(context.filesDir, entity.path)
                    if (file.exists()) {
                        items.add(
                            LibraryItem.Photo(
                                id = "photo_${entity.id}",
                                title = entity.prompt.take(60).ifBlank { file.name },
                                subtitle = "${entity.modelId} • ${file.length().fileSizeToString()}",
                                timestamp = entity.createAt,
                                filePath = file.absolutePath,
                                uri = Uri.fromFile(file).toString(),
                                prompt = entity.prompt,
                                entityId = entity.id,
                            )
                        )
                    }
                }
            }

            // 2. In-World Artifacts & HTML
            runCatching {
                val artifacts = artifactManager.getSavedArtifacts(context)
                artifacts.forEach { artifact ->
                    items.add(LibraryItem.Artifact(artifact))
                }
            }

            // 3. Story Studio Projects
            runCatching {
                val stories = storyRepo.projectsFlow.value
                stories.forEach { story ->
                    items.add(LibraryItem.Story(story))
                }
            }

            // 4. Voice Memos & Recordings
            runCatching {
                val memos = memoRepo.recordingsFlow.value
                memos.forEach { memo ->
                    items.add(LibraryItem.Recording(memo))
                }
            }

            // 5. Memory Summaries
            runCatching {
                val summaries = summaryDao.getAllSummaries()
                summaries.forEach { summary ->
                    val name = settings.getAssistantById(summary.assistantId)?.name ?: "Assistant"
                    items.add(LibraryItem.Summary(summary, name))
                }
            }

            // 6. Uploads & Files
            runCatching {
                val uploads = appStorageRepo.listOtherUploadFiles()
                uploads.forEach { upload ->
                    if (upload.isImage) {
                        items.add(
                            LibraryItem.Photo(
                                id = "upload_img_${upload.path}",
                                title = upload.displayName,
                                subtitle = "Uploaded Image • ${upload.sizeBytes.fileSizeToString()}",
                                timestamp = upload.modifiedAt,
                                filePath = upload.path,
                                uri = upload.uri,
                                prompt = "Uploaded image: ${upload.displayName}",
                                entityId = null,
                            )
                        )
                    } else {
                        items.add(LibraryItem.Upload(upload))
                    }
                }
            }

            allItems = items.sortedByDescending { it.timestamp }
        }
        isLoading = false
    }

    // Filter items based on active category and search
    val filteredItems = remember(allItems, selectedCategory, searchQuery) {
        allItems.filter { item ->
            val matchesCategory = when (selectedCategory) {
                LibraryCategory.ALL -> true
                else -> item.category == selectedCategory
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                item.title.lowercase().contains(q) ||
                    item.subtitle.lowercase().contains(q) ||
                    (item is LibraryItem.Photo && item.prompt.lowercase().contains(q)) ||
                    (item is LibraryItem.Story && item.project.premise.lowercase().contains(q)) ||
                    (item is LibraryItem.Recording && item.memo.transcript?.lowercase()?.contains(q) == true)
            }
            matchesCategory && matchesSearch
        }
    }

    val categoryCounts = remember(allItems) {
        LibraryCategory.entries.associateWith { cat ->
            if (cat == LibraryCategory.ALL) allItems.size
            else allItems.count { it.category == cat }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "Library",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            refreshTrigger++
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Search Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search photos, artifacts, stories, audio & files...") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = AppShapes.ButtonPill,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    // Horizontal Category Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(LibraryCategory.entries) { cat ->
                            val isSelected = selectedCategory == cat
                            val count = categoryCounts[cat] ?: 0
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    selectedCategory = cat
                                },
                                label = { Text("${cat.label} ($count)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = AppShapes.ButtonPill,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Main Content Area
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            text = "Cataloging library assets...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = selectedCategory.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = if (searchQuery.isNotBlank()) "No items matching \"$searchQuery\"" else "No ${selectedCategory.label} yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Created stories, generated photos, artifacts, summaries and recordings will automatically appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        LibraryItemCard(
                            item = item,
                            isPlaying = isPlaying && item is LibraryItem.Recording && currentPlayingFile == item.memo.audioFile,
                            onInspect = {
                                haptics.perform(HapticPattern.Tick)
                                when (item) {
                                    is LibraryItem.Photo -> previewImage = item
                                    is LibraryItem.Artifact -> previewArtifact = item.artifact
                                    is LibraryItem.Story -> previewStory = item.project
                                    is LibraryItem.Recording -> previewMemo = item.memo
                                    is LibraryItem.Summary -> previewSummary = item.entity to item.assistantName
                                    is LibraryItem.Upload -> context.openAttachmentUri(item.file.uri.toUri(), item.file.mime)
                                }
                            },
                            onCopy = {
                                haptics.perform(HapticPattern.Tick)
                                when (item) {
                                    is LibraryItem.Photo -> {
                                        context.writeClipboardText(item.prompt)
                                        toaster.show("Image prompt copied to clipboard")
                                    }
                                    is LibraryItem.Artifact -> {
                                        val code = runCatching { item.artifact.htmlFile.readText() }.getOrDefault("")
                                        context.writeClipboardText(code)
                                        toaster.show("Artifact HTML copied to clipboard")
                                    }
                                    is LibraryItem.Story -> {
                                        val manuscript = buildString {
                                            appendLine("# ${item.project.title}")
                                            appendLine("Genre: ${item.project.genre} • Tone: ${item.project.tone}")
                                            appendLine()
                                            item.project.bible?.beats?.forEach { b ->
                                                if (b.content.isNotBlank()) {
                                                    appendLine("## Beat ${b.index + 1}: ${b.title}")
                                                    appendLine()
                                                    appendLine(b.content)
                                                    appendLine()
                                                }
                                            }
                                        }.trim()
                                        context.writeClipboardText(manuscript)
                                        toaster.show("Full manuscript copied to clipboard")
                                    }
                                    is LibraryItem.Recording -> {
                                        val text = item.memo.transcript ?: item.memo.summary ?: item.memo.title
                                        context.writeClipboardText(text)
                                        toaster.show("Recording transcript copied to clipboard")
                                    }
                                    is LibraryItem.Summary -> {
                                        context.writeClipboardText(item.entity.rawMarkdown)
                                        toaster.show("Memory summary copied to clipboard")
                                    }
                                    is LibraryItem.Upload -> {
                                        context.writeClipboardText(item.file.path)
                                        toaster.show("File path copied to clipboard")
                                    }
                                }
                            },
                            onPlayPause = {
                                if (item is LibraryItem.Recording) {
                                    haptics.perform(HapticPattern.Tick)
                                    if (currentPlayingFile == item.memo.audioFile && isPlaying) {
                                        playbackManager.pause()
                                    } else {
                                        playbackManager.play(item.memo.audioFile)
                                    }
                                }
                            },
                            onOpenDestination = {
                                haptics.perform(HapticPattern.Tick)
                                when (item) {
                                    is LibraryItem.Photo -> navController.navigate(Screen.ImageGen)
                                    is LibraryItem.Artifact -> navController.navigate(Screen.SettingArtifacts)
                                    is LibraryItem.Story -> navController.navigate(Screen.StoryStudio(item.project.id))
                                    is LibraryItem.Recording -> navController.navigate(Screen.VoiceMemos)
                                    is LibraryItem.Summary -> navController.navigate(Screen.MemorySummary(item.entity.assistantId))
                                    is LibraryItem.Upload -> context.openAttachmentUri(item.file.uri.toUri(), item.file.mime)
                                }
                            },
                            onDelete = {
                                haptics.perform(HapticPattern.Thud)
                                pendingDeleteItem = item
                            }
                        )
                    }
                }
            }
        }
    }

    // ========================================================================
    // DIALOGS & PREVIEW INSPECTORS
    // ========================================================================

    // 1. Image Preview Lightbox
    previewImage?.let { photo ->
        ImagePreviewDialog(
            images = listOf(photo.uri),
            prompt = photo.prompt,
            onDismissRequest = { previewImage = null }
        )
    }

    // 2. In-World Artifact Zoom Inspector
    previewArtifact?.let { artifact ->
        val code = remember(artifact) {
            runCatching { artifact.htmlFile.readText() }.getOrDefault("")
        }
        ArtifactZoomDialog(
            code = code,
            onDismissRequest = { previewArtifact = null }
        )
    }

    // 3. Story Reader Modal Dialog
    previewStory?.let { story ->
        Dialog(
            onDismissRequest = { previewStory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = AppShapes.CardLarge,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = story.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = "${story.genre} • ${story.tone} • ${story.totalWords} words",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { previewStory = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider()

                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            val beats = story.bible?.beats ?: emptyList()
                            if (beats.isEmpty() || beats.all { it.content.isBlank() }) {
                                Text(
                                    text = "No beats written yet for this story.\n\nPremise: ${story.premise}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                beats.forEach { beat ->
                                    if (beat.content.isNotBlank()) {
                                        Text(
                                            text = "Beat ${beat.index + 1}: ${beat.title}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = beat.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 24.sp
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                previewStory = null
                                navController.navigate(Screen.StoryStudio(story.id))
                            },
                            shape = AppShapes.ButtonPill
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open in Story Studio")
                        }
                    }
                }
            }
        }
    }

    // 4. Voice Memo Detail & Transcript Dialog
    previewMemo?.let { memo ->
        Dialog(
            onDismissRequest = { previewMemo = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = AppShapes.CardLarge,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = memo.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${memo.durationMs / 1000}s recording • ${DateUtils.getRelativeTimeSpanString(memo.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { previewMemo = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider()

                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!memo.summary.isNullOrBlank()) {
                                Text(
                                    text = "Executive Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = memo.summary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (memo.bulletPoints.isNotEmpty()) {
                                Text(
                                    text = "Key Takeaways",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                memo.bulletPoints.forEach { pt ->
                                    Text(
                                        text = "• $pt",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (!memo.transcript.isNullOrBlank()) {
                                Text(
                                    text = "Full Transcript",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = memo.transcript,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                previewMemo = null
                                navController.navigate(Screen.VoiceMemos)
                            },
                            shape = AppShapes.ButtonPill
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open in Voice Memos")
                        }
                    }
                }
            }
        }
    }

    // 5. Memory Summary Reader Dialog
    previewSummary?.let { (entity, assistantName) ->
        Dialog(
            onDismissRequest = { previewSummary = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = AppShapes.CardLarge,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$assistantName's Memory Summary",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${entity.tokenCount} tokens • ${DateUtils.getRelativeTimeSpanString(entity.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { previewSummary = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider()

                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = entity.rawMarkdown,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                previewSummary = null
                                navController.navigate(Screen.MemorySummary(entity.assistantId))
                            },
                            shape = AppShapes.ButtonPill
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open in Memory Summary")
                        }
                    }
                }
            }
        }
    }

    // 6. Delete Confirmation Dialog
    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text("Delete from Library") },
            text = { Text("Are you sure you want to permanently delete \"${item.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                when (item) {
                                    is LibraryItem.Photo -> {
                                        if (item.entityId != null) {
                                            genMediaDao.delete(item.entityId)
                                        }
                                        val file = File(item.filePath)
                                        if (file.exists()) file.delete()
                                    }
                                    is LibraryItem.Artifact -> {
                                        artifactManager.deleteArtifact(context, item.artifact.id)
                                    }
                                    is LibraryItem.Story -> {
                                        storyRepo.deleteProject(item.project.id)
                                    }
                                    is LibraryItem.Recording -> {
                                        memoRepo.deleteMemo(item.memo.id)
                                    }
                                    is LibraryItem.Summary -> {
                                        summaryDao.delete(item.entity.assistantId)
                                    }
                                    is LibraryItem.Upload -> {
                                        appStorageRepo.deleteOtherUploadFile(item.file.path)
                                    }
                                }
                            }
                            pendingDeleteItem = null
                            refreshTrigger++
                            toaster.show("Item deleted")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LibraryItemCard(
    item: LibraryItem,
    isPlaying: Boolean,
    onInspect: () -> Unit,
    onCopy: () -> Unit,
    onPlayPause: () -> Unit,
    onOpenDestination: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = AppShapes.CardLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Category Pill & Timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = AppShapes.ButtonPill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = item.category.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = item.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = DateUtils.getRelativeTimeSpanString(item.timestamp).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // Body content
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Visual Thumbnail / Icon Box
                when (item) {
                    is LibraryItem.Photo -> {
                        Surface(
                            shape = AppShapes.CardMedium,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(AppShapes.CardMedium)
                                .clickable { onInspect() }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    is LibraryItem.Artifact -> {
                        Surface(
                            shape = AppShapes.CardMedium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { onInspect() }
                        ) {
                            if (item.artifact.imageFile?.exists() == true) {
                                AsyncImage(
                                    model = item.artifact.imageFile,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                    is LibraryItem.Story -> {
                        Surface(
                            shape = AppShapes.CardMedium,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { onInspect() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    is LibraryItem.Recording -> {
                        Surface(
                            shape = AppShapes.CardMedium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { onPlayPause() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    is LibraryItem.Summary -> {
                        Surface(
                            shape = AppShapes.CardMedium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { onInspect() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    is LibraryItem.Upload -> {
                        Surface(
                            shape = AppShapes.CardMedium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { onInspect() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Text details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onInspect() },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            Spacer(Modifier.height(4.dp))

            // Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onInspect, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Inspect",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onOpenDestination, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open in app",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
