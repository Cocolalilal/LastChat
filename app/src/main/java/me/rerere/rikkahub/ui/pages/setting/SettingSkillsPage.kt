package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.MaterialIconPickerDialog
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.SkillExportImport
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SettingSkillsPage(
    scrollToSkillId: String? = null,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val listState = rememberLazyListState()
    val useWideLayout = LocalSettingsWideLayout.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }

    LaunchedEffect(scrollToSkillId, settings.skills) {
        if (scrollToSkillId.isNullOrBlank()) return@LaunchedEffect
        val index = settings.skills.indexOfFirst { it.id.toString() == scrollToSkillId }
        if (index >= 0) {
            listState.animateScrollToItem(index + 1)
            editingSkill = settings.skills[index]
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = SkillExportImport.importFromUri(context, uri)) {
            is SkillExportImport.ImportResult.Success -> {
                vm.updateSettings(settings.copy(skills = settings.skills + result.skill))
                haptics.perform(HapticPattern.Success)
                toaster.show(context.getString(R.string.skill_import_success, result.skill.name))
            }

            is SkillExportImport.ImportResult.Error -> {
                haptics.perform(HapticPattern.Error)
                toaster.show(result.message)
            }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.prompt_injections_page_skills),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
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
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        navController.navigate(Screen.SettingLorebooks) {
                                            popUpTo(Screen.SettingSkills()) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            importLauncher.launch(arrayOf("application/json", "text/markdown", "*/*"))
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
                            haptics.perform(HapticPattern.Pop)
                            showAddDialog = true
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
        SkillsPageContent(
            settings = settings,
            vm = vm,
            haptics = haptics,
            listState = listState,
            contentPadding = contentPadding,
            onEditSkill = { editingSkill = it }
        )
    }

    if (showAddDialog || editingSkill != null) {
        SkillEditorSheet(
            skill = editingSkill,
            assistants = settings.assistants,
            onDismiss = {
                showAddDialog = false
                editingSkill = null
            },
            onSave = { savedSkill ->
                if (editingSkill == null) {
                    vm.updateSettings(settings.copy(skills = settings.skills + savedSkill))
                } else {
                    vm.updateSettings(
                        settings.copy(
                            skills = settings.skills.map {
                                if (it.id == savedSkill.id) savedSkill else it
                            }
                        )
                    )
                }
                showAddDialog = false
                editingSkill = null
            },
            onAutoSave = { savedSkill ->
                vm.updateSettings(
                    settings.copy(
                        skills = settings.skills.map {
                            if (it.id == savedSkill.id) savedSkill else it
                        }
                    )
                )
            }
        )
    }
}

@Composable
fun SkillsPageContent(
    settings: Settings,
    vm: SettingVM,
    haptics: PremiumHaptics,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
    onEditSkill: (Skill) -> Unit = {},
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val density = LocalDensity.current

    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var neighborsUnlocked by remember { mutableStateOf(false) }
    var orderedSkills by remember { mutableStateOf(settings.skills) }
    var isReordering by remember { mutableStateOf(false) }
    var awaitingPersistedOrder by remember { mutableStateOf(false) }
    val canDelete = true

    LaunchedEffect(settings.skills, isReordering) {
        if (!isReordering) {
            if (awaitingPersistedOrder && settings.skills.map { it.id } != orderedSkills.map { it.id }) {
                return@LaunchedEffect
            }
            awaitingPersistedOrder = false
            orderedSkills = settings.skills
        }
    }

    if (dragOffset == 0f && neighborsUnlocked) {
        neighborsUnlocked = false
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex !in orderedSkills.indices || toIndex !in 0..orderedSkills.size) {
            return@rememberReorderableLazyListState
        }
        orderedSkills = orderedSkills.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = listState,
            contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 45.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (orderedSkills.isEmpty()) {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = stringResource(R.string.skills_page_empty_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.skills_page_empty_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = orderedSkills,
                    key = { _, skill -> skill.id }
                ) { index, skill ->
                    val position = when {
                        orderedSkills.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == orderedSkills.lastIndex -> ItemPosition.LAST
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
                        key = skill.id
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
                                    val deletedSkill = skill
                                    vm.updateSettings(
                                        settings.copy(skills = settings.skills.filter { it.id != skill.id })
                                    )
                                    toaster.show(
                                        message = context.getString(
                                            R.string.skills_page_deleted,
                                            skill.name.ifEmpty { context.getString(R.string.skills_page_unnamed) }
                                        ),
                                        action = ToastAction(
                                            label = context.getString(R.string.undo),
                                            onClick = {
                                                vm.updateSettings(
                                                    settings.copy(
                                                        skills = settings.skills.toMutableList().apply {
                                                            add(index.coerceAtMost(size), deletedSkill)
                                                        }
                                                    )
                                                )
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .scale(if (isDragging) 0.95f else 1f)
                                    .fillMaxWidth()
                            ) {
                                SkillCard(
                                    skill = skill,
                                    position = position,
                                    onEdit = { onEditSkill(skill) },
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
                                                    if (orderedSkills.map { it.id } != settings.skills.map { it.id }) {
                                                        awaitingPersistedOrder = true
                                                        vm.updateSettings(settings.copy(skills = orderedSkills))
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
fun DismissibleBannerCard(
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
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
        Box {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 40.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.banner_dismiss),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    position: ItemPosition,
    onEdit: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val cornerRadius = 28.dp
    val smallCorner = 8.dp
    val shape = when (position) {
        ItemPosition.ONLY -> RoundedCornerShape(cornerRadius)
        ItemPosition.FIRST -> RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = smallCorner,
            bottomEnd = smallCorner
        )

        ItemPosition.MIDDLE -> RoundedCornerShape(smallCorner)
        ItemPosition.LAST -> RoundedCornerShape(
            topStart = smallCorner,
            topEnd = smallCorner,
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        )
    }

    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ModeIcons.getIcon(skill.icon ?: "category"),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = skill.name.ifEmpty { stringResource(R.string.skills_page_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (skill.name.isNotEmpty()) FontFamily.Monospace else FontFamily.Default
                )
                if (skill.description.isNotBlank()) {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            dragHandle()
        }
    }
}

@Composable
private fun SkillAssistantToggleRow(
    assistant: me.rerere.rikkahub.data.model.Assistant,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIAvatar(
            value = assistant.avatar,
            name = assistant.name.ifBlank { stringResource(R.string.text_selection_assistant) },
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = assistant.name.ifBlank { stringResource(R.string.text_selection_assistant) },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        HapticSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SkillEditorSheet(
    skill: Skill?,
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    onDismiss: () -> Unit,
    onSave: (Skill) -> Unit,
    onAutoSave: ((Skill) -> Unit)? = null,
) {
    val isEditing = skill != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(skill?.name ?: "") }
    var description by remember { mutableStateOf(skill?.description ?: "") }
    var icon by remember { mutableStateOf(skill?.icon) }
    var instructions by remember { mutableStateOf(skill?.instructions ?: "") }
    var alwaysEnabled by remember { mutableStateOf(skill?.alwaysEnabled ?: false) }
    var availableForAllAssistants by remember { mutableStateOf(skill?.availableForAllAssistants ?: true) }
    var availableAssistantIds by remember { mutableStateOf(skill?.availableAssistantIds ?: emptySet()) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var namePending by remember { mutableStateOf(false) }
    var descriptionPending by remember { mutableStateOf(false) }
    var instructionsPending by remember { mutableStateOf(false) }
    val isAutoSaving = isEditing && (namePending || descriptionPending || instructionsPending)

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (isEditing) R.string.skills_page_edit_skill else R.string.skills_page_add_skill
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                AutoSaveIndicator(visible = isAutoSaving)
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormItem(label = { Text(stringResource(R.string.skills_page_name)) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showIconPicker = true },
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ModeIcons.getIcon(icon ?: "category"),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        DebouncedTextField(
                            value = name,
                            onValueChange = { rawNewName ->
                                val processed = rawNewName.lowercase()
                                    .filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' }
                                    .take(64)
                                name = processed
                                if (skill != null) {
                                    onAutoSave?.invoke(skill.copy(
                                        name = processed,
                                        description = description.trim(),
                                        icon = icon,
                                        instructions = instructions,
                                        alwaysEnabled = alwaysEnabled,
                                        availableForAllAssistants = availableForAllAssistants,
                                        availableAssistantIds = if (availableForAllAssistants) emptySet() else availableAssistantIds,
                                        updatedAt = System.currentTimeMillis()
                                    ))
                                }
                            },
                            stateKey = "skill_name_${skill?.id ?: "new"}",
                            modifier = Modifier.weight(1f),
                            placeholder = stringResource(R.string.skills_page_name_placeholder),
                            singleLine = true,
                            onPendingChange = { namePending = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }

                if (showIconPicker) {
                    MaterialIconPickerDialog(
                        onDismiss = { showIconPicker = false },
                        onIconSelected = { selectedIcon ->
                            icon = selectedIcon
                            showIconPicker = false
                        }
                    )
                }

                FormItem(label = { Text(stringResource(R.string.skills_page_description)) }) {
                    DebouncedTextField(
                        value = description,
                        onValueChange = { rawVal ->
                            val processed = rawVal.take(1024)
                            description = processed
                            if (skill != null) {
                                onAutoSave?.invoke(skill.copy(
                                    name = name.trim(),
                                    description = processed.trim(),
                                    icon = icon,
                                    instructions = instructions,
                                    alwaysEnabled = alwaysEnabled,
                                    availableForAllAssistants = availableForAllAssistants,
                                    availableAssistantIds = if (availableForAllAssistants) emptySet() else availableAssistantIds,
                                    updatedAt = System.currentTimeMillis()
                                ))
                            }
                        },
                        stateKey = "skill_desc_${skill?.id ?: "new"}",
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.skills_page_description_placeholder),
                        minLines = 2,
                        maxLines = 4,
                        onPendingChange = { descriptionPending = it },
                    )
                }

                FormItem(label = { Text(stringResource(R.string.skills_page_instructions)) }) {
                    DebouncedTextField(
                        value = instructions,
                        onValueChange = { newVal ->
                            instructions = newVal
                            if (skill != null) {
                                onAutoSave?.invoke(skill.copy(
                                    name = name.trim(),
                                    description = description.trim(),
                                    icon = icon,
                                    instructions = newVal,
                                    alwaysEnabled = alwaysEnabled,
                                    availableForAllAssistants = availableForAllAssistants,
                                    availableAssistantIds = if (availableForAllAssistants) emptySet() else availableAssistantIds,
                                    updatedAt = System.currentTimeMillis()
                                ))
                            }
                        },
                        stateKey = "skill_instructions_${skill?.id ?: "new"}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = stringResource(R.string.skills_page_instructions_placeholder),
                        onPendingChange = { instructionsPending = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    )
                }
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
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(R.string.skills_page_available_for_all_characters)) },
                            supportingContent = { Text(stringResource(R.string.skills_page_available_for_all_characters_desc)) },
                            trailingContent = {
                                HapticSwitch(
                                    checked = availableForAllAssistants,
                                    onCheckedChange = { checked ->
                                        availableForAllAssistants = checked
                                        if (checked) {
                                            availableAssistantIds = emptySet()
                                        }
                                    }
                                )
                            }
                        )
                        AnimatedVisibility(visible = !availableForAllAssistants) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                assistants.forEach { assistant ->
                                    SkillAssistantToggleRow(
                                        assistant = assistant,
                                        checked = availableAssistantIds.contains(assistant.id),
                                        onCheckedChange = { checked ->
                                            availableAssistantIds = if (checked) {
                                                availableAssistantIds + assistant.id
                                            } else {
                                                availableAssistantIds - assistant.id
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(R.string.skills_page_always_enabled)) },
                            supportingContent = { Text(stringResource(R.string.skills_page_always_enabled_desc)) },
                            trailingContent = {
                                HapticSwitch(
                                    checked = alwaysEnabled,
                                    onCheckedChange = { checked -> alwaysEnabled = checked }
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEditing) {
                        TextButton(onClick = { showExportDialog = true }) {
                            Text(stringResource(R.string.export_label))
                        }
                    }
                    TextButton(
                        onClick = {
                            val base = skill ?: Skill()
                            val availableIds = if (availableForAllAssistants) emptySet() else availableAssistantIds
                            val savedSkill = base.copy(
                                name = name.trim(),
                                description = description.trim(),
                                icon = icon,
                                instructions = instructions,
                                attachments = emptyList(),
                                enabled = true,
                                alwaysEnabled = alwaysEnabled,
                                availableForAllAssistants = availableForAllAssistants,
                                availableAssistantIds = availableIds,
                                autonomousForAllAssistants = true,
                                autonomousAssistantIds = emptySet(),
                                injectionPosition = InjectionPosition.AFTER_SYSTEM,
                                depth = 0,
                                disableModelInvocation = false,
                                userInvocable = true,
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(savedSkill)
                        }
                    ) {
                        Text(
                            text = stringResource(if (isEditing) R.string.save else R.string.create),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog && skill != null) {
        SkillExportDialog(
            skill = skill.copy(
                name = name.trim(),
                description = description.trim(),
                icon = icon,
                instructions = instructions,
                attachments = emptyList(),
                enabled = true,
                alwaysEnabled = alwaysEnabled,
                availableForAllAssistants = availableForAllAssistants,
                availableAssistantIds = if (availableForAllAssistants) emptySet() else availableAssistantIds,
                autonomousForAllAssistants = true,
                autonomousAssistantIds = emptySet(),
                injectionPosition = InjectionPosition.AFTER_SYSTEM,
                depth = 0,
                disableModelInvocation = false,
                userInvocable = true,
            ),
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun SkillExportDialog(
    skill: Skill,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current

    val skillMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val content = SkillExportImport.exportToSkillMd(skill)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray())
                }
            }.onSuccess {
                toaster.show(context.getString(R.string.skill_export_success))
            }.onFailure {
                toaster.show(
                    context.getString(
                        R.string.export_failed_message,
                        it.message ?: context.getString(R.string.backup_page_unknown_error)
                    )
                )
            }
        }
        onDismiss()
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.skill_export_format),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                onClick = {
                    skillMdLauncher.launch(SkillExportImport.getSuggestedFileName(skill, "skill_md"))
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (LocalDarkMode.current) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ),
                shape = AppShapes.CardLarge
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.export_format_skill_md)) },
                    supportingContent = { Text(stringResource(R.string.skills_claude_standard)) },
                    leadingContent = {
                        Icon(Icons.Rounded.Code, contentDescription = null)
                    }
                )
            }
        }
    }
}
