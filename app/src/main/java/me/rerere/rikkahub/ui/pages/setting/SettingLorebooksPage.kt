package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.collectMediaFileRefs
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.LorebookExportImport
import me.rerere.rikkahub.utils.OwnedFileDirectory
import me.rerere.rikkahub.utils.importOwnedFile
import me.rerere.rikkahub.utils.plus
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SettingLorebooksPage(vm: SettingVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val useWideLayout = LocalSettingsWideLayout.current
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            when (val result = LorebookExportImport.importFromUri(context, uri)) {
                is LorebookExportImport.ImportResult.Success -> {
                    vm.updateSettings(settings.copy(lorebooks = settings.lorebooks + result.lorebook))
                    haptics.perform(HapticPattern.Success)
                    toaster.show(
                        message = context.getString(R.string.lorebook_import_success, result.lorebook.name),
                    )
                }
                is LorebookExportImport.ImportResult.Error -> {
                    haptics.perform(HapticPattern.Error)
                    toaster.show(
                        message = result.message,
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.prompt_injections_page_lorebooks),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        bottomBar = {
            // Both FAB and tab bar at same height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Centered floating tab bar
                if (!useWideLayout) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = -ScreenOffset),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Skills tab
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        navController.navigate(Screen.SettingSkills()) {
                                            popUpTo(Screen.SettingLorebooks) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Lorebooks tab (selected)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(if (useWideLayout) Modifier else Modifier.offset(y = -ScreenOffset)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        shape = AppShapes.CardLarge,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Input,
                            contentDescription = stringResource(R.string.import_label)
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            showAddDialog = true
                            haptics.perform(HapticPattern.Pop)
                        },
                        shape = AppShapes.CardLarge
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add))
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LorebooksPageContent(
            settings = settings,
            vm = vm,
            haptics = haptics,
            listState = lazyListState,
            contentPadding = contentPadding
        )
    }

    // Add Lorebook Dialog
    if (showAddDialog) {
        LorebookCreatorSheet(
            onDismiss = { showAddDialog = false },
            onSave = { lorebook ->
                vm.updateSettings(settings.copy(lorebooks = settings.lorebooks + lorebook))
                showAddDialog = false
                // Navigate to detail page to add entries
                navController.navigate(Screen.SettingLorebookDetail(lorebook.id.toString()))
            }
        )
    }
}

@Composable
fun LorebooksPageContent(
    settings: Settings,
    vm: SettingVM,
    haptics: PremiumHaptics,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val density = LocalDensity.current

    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    var orderedLorebooks by remember { mutableStateOf(settings.lorebooks) }
    var isReordering by remember { mutableStateOf(false) }
    var awaitingPersistedOrder by remember { mutableStateOf(false) }
    val canDelete = true

    LaunchedEffect(settings.lorebooks, isReordering) {
        if (!isReordering) {
            if (awaitingPersistedOrder && settings.lorebooks.map { it.id } != orderedLorebooks.map { it.id }) {
                return@LaunchedEffect
            }
            awaitingPersistedOrder = false
            orderedLorebooks = settings.lorebooks
        }
    }

    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex !in orderedLorebooks.indices || toIndex !in 0..orderedLorebooks.size) {
            return@rememberReorderableLazyListState
        }
        orderedLorebooks = orderedLorebooks.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = listState,
            contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (orderedLorebooks.isEmpty()) {
                item(key = "empty") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (LocalDarkMode.current) {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                        shape = AppShapes.CardLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Book,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = stringResource(R.string.lorebooks_page_empty_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.lorebooks_page_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = orderedLorebooks,
                    key = { _, lorebook -> lorebook.id }
                ) { index, lorebook ->
                    val position = when {
                        orderedLorebooks.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == orderedLorebooks.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }

                    val thresholdPx = with(density) { 35.dp.toPx() }
                    if (draggingIndex >= 0 && !neighborsUnlocked && kotlin.math.abs(dragOffset) >= thresholdPx) {
                        neighborsUnlocked = true
                    }

                    val shouldNeighborFollow = draggingIndex >= 0 &&
                        draggingIndex != index &&
                        !isUnlocked &&
                        !neighborsUnlocked

                    val neighborOffset = if (shouldNeighborFollow) {
                        when (kotlin.math.abs(index - draggingIndex)) {
                            1 -> dragOffset * 0.35f
                            2 -> dragOffset * 0.12f
                            else -> 0f
                        }
                    } else {
                        0f
                    }

                    ReorderableItem(
                        state = reorderableState,
                        key = lorebook.id
                    ) { isDragging ->
                        androidx.compose.runtime.key(canDelete) {
                            PhysicsSwipeToDelete(
                                position = position,
                                deleteEnabled = canDelete,
                                neighborOffset = neighborOffset,
                                onDragProgress = { offset, unlocked ->
                                    draggingIndex = index
                                    dragOffset = offset
                                    isUnlocked = unlocked
                                },
                                onDragEnd = {
                                    if (draggingIndex == index) {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    }
                                },
                                onDelete = {
                                    val deletedLorebook = lorebook
                                    vm.updateSettings(
                                        settings.copy(lorebooks = settings.lorebooks.filter { it.id != lorebook.id })
                                    )
                                    vm.cleanupFilesIfUnreferenced(
                                        fileRefs = deletedLorebook.collectMediaFileRefs(),
                                        delayMs = 4500L,
                                    )
                                    toaster.show(
                                        message = context.getString(
                                            R.string.lorebooks_page_deleted,
                                            lorebook.name.ifEmpty { context.getString(R.string.lorebooks_page_unnamed) }
                                        ),
                                        action = ToastAction(
                                            label = context.getString(R.string.undo),
                                            onClick = {
                                                vm.updateSettings(
                                                    settings.copy(lorebooks = settings.lorebooks.toMutableList().apply {
                                                        add(index.coerceAtMost(size), deletedLorebook)
                                                    })
                                                )
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .scale(if (isDragging) 0.95f else 1f)
                                    .fillMaxWidth()
                            ) { _ ->
                                LorebookCard(
                                    lorebook = lorebook,
                                    position = position,
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        navController.navigate(Screen.SettingLorebookDetail(lorebook.id.toString()))
                                    },
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    isReordering = true
                                                    haptics.perform(HapticPattern.Pop)
                                                },
                                                onDragStopped = {
                                                    isReordering = false
                                                    if (orderedLorebooks.map { it.id } != settings.lorebooks.map { it.id }) {
                                                        awaitingPersistedOrder = true
                                                        vm.updateSettings(settings.copy(lorebooks = orderedLorebooks))
                                                    }
                                                    haptics.perform(HapticPattern.Thud)
                                                }
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = stringResource(R.string.drag_to_reorder)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
    }
}

@Composable
private fun LorebookCard(
    lorebook: Lorebook,
    position: ItemPosition,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = AppShapes.CardLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book cover or placeholder
            val bookShape = when (position) {
                ItemPosition.ONLY -> RoundedCornerShape(
                    topStart = 16.dp, topEnd = 6.dp,
                    bottomStart = 16.dp, bottomEnd = 6.dp
                )
                ItemPosition.FIRST -> RoundedCornerShape(
                    topStart = 16.dp, topEnd = 6.dp,
                    bottomStart = 6.dp, bottomEnd = 6.dp
                )
                ItemPosition.MIDDLE -> RoundedCornerShape(6.dp)
                ItemPosition.LAST -> RoundedCornerShape(
                    topStart = 6.dp, topEnd = 6.dp,
                    bottomStart = 16.dp, bottomEnd = 6.dp
                )
            }
            Surface(
                shape = bookShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(width = 50.dp, height = 70.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (val cover = lorebook.cover) {
                        is Avatar.Image -> {
                            coil3.compose.AsyncImage(
                                model = cover.url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        is Avatar.Emoji -> {
                            Text(
                                text = cover.content,
                                fontSize = 24.sp
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Rounded.Book,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Book info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = lorebook.name.ifEmpty { stringResource(R.string.lorebooks_page_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lorebook.description.ifEmpty { 
                        stringResource(R.string.lorebooks_page_no_description) 
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.lorebooks_page_entries_count, lorebook.entries.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            dragHandle()
        }
    }
}

@Composable
internal fun LorebookCreatorSheet(
    onDismiss: () -> Unit,
    onSave: (Lorebook) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<Avatar?>(null) }
    var showCoverPicker by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            scope.launch {
                context.importOwnedFile(
                    sourceUri = it,
                    directory = OwnedFileDirectory.LOREBOOK_COVER,
                )?.let { localUri ->
                    cover = Avatar.Image(localUri.toString())
                }
            }
        }
    }
    
    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.lorebooks_page_create_lorebook),
                style = MaterialTheme.typography.titleLarge
            )

            // Cover picker + Name input inline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover picker - book-like aspect ratio
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(width = 56.dp, height = 78.dp),
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (val c = cover) {
                            is Avatar.Image -> {
                                coil3.compose.AsyncImage(
                                    model = c.url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            is Avatar.Emoji -> {
                                Text(text = c.content, fontSize = 28.sp)
                            }
                            else -> {
                                Icon(
                                    Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                // Name input
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lorebooks_page_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.lorebooks_page_name_placeholder)) },
                        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                    )
                }
            }

            FormItem(
                label = { Text(stringResource(R.string.lorebooks_page_description)) }
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = { Text(stringResource(R.string.lorebooks_page_description_placeholder)) },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val lorebook = Lorebook(
                            name = name,
                            description = description,
                            cover = cover
                        )
                        onSave(lorebook)
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text(stringResource(R.string.create))
                }
            }
        }
    }
}
