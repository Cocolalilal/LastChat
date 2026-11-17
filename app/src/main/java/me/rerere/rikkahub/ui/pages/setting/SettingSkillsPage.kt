package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.utils.plus
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import me.rerere.rikkahub.ui.hooks.rememberSwipeNeighborState
import me.rerere.rikkahub.ui.hooks.rememberReorderableItems
import me.rerere.rikkahub.ui.hooks.reorderedByKeys

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import me.rerere.rikkahub.ui.pages.setting.components.AddonEmptyState
import me.rerere.rikkahub.ui.pages.setting.components.AddonTextEditor
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.ui.pages.setting.components.AddonPromptField
import me.rerere.rikkahub.ui.pages.setting.components.CharacterAccessSection
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Category
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.motion.ExpandableContent
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
import me.rerere.rikkahub.ui.components.ui.Select
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
import org.koin.androidx.compose.koinViewModel
import me.rerere.rikkahub.ui.hooks.AppReorderableItem
import me.rerere.rikkahub.ui.hooks.rememberAppReorderableLazyListState

@Composable
fun SettingSkillsPage(scrollToSkillId: String? = null, vm: SettingVM = koinViewModel()) {
    SettingPromptInjectionsPage(initialPage = 0, scrollToSkillId = scrollToSkillId, vm = vm)
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
    if (settings.skills.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            AddonEmptyState(Icons.Rounded.Category, stringResource(R.string.skills_page_empty_title))
        }
        return
    }
    val context = LocalContext.current
    val toaster = LocalToaster.current

    val swipeNeighbors = rememberSwipeNeighborState()
    val itemOrder = rememberReorderableItems(settings.skills, key = { it.id }) { order ->
        vm.updateSettings { latest -> latest.copy(skills = latest.skills.reorderedByKeys(order) { it.id }) }
    }
    val orderedSkills = itemOrder.items
    val canDelete = true


    val reorderableState = rememberAppReorderableLazyListState(
        listState, itemOrder,
        scrollThresholdPadding = contentPadding + PaddingValues(bottom = 45.dp),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = listState,
            contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 45.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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

                val neighborOffset = swipeNeighbors.offsetFor(index, orderedSkills) { it.id }

                AppReorderableItem(
                    state = reorderableState,
                    key = skill.id
                ) { isDragging ->
                    PhysicsSwipeToDelete(
                        position = position,
                        deleteEnabled = canDelete,
                        neighborOffset = neighborOffset,
                        onDragProgress = { offset, unlocked -> swipeNeighbors.update(skill.id, offset, unlocked) },
                        onDragEnd = { swipeNeighbors.end(skill.id) },
                        onDelete = {
                            val deletedSkill = skill
                            vm.updateSettings { settings -> settings.copy(skills = settings.skills.filter { it.id != skill.id }) }
                            toaster.show(
                                message = context.getString(
                                    R.string.skills_page_deleted,
                                    skill.name.ifEmpty { context.getString(R.string.skills_page_unnamed) }
                                ),
                                action = ToastAction(
                                    label = context.getString(R.string.undo),
                                    onClick = {
                                        vm.updateSettings { settings -> settings.copy(
                                                skills = settings.skills.toMutableList().apply {
                                                    add(index.coerceAtMost(size), deletedSkill)
                                                }
                                            ) }
                                    }
                                )
                            )
                        },
                        modifier = Modifier
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
                                            haptics.perform(HapticPattern.Pop)
                                        },
                                        onDragStopped = {
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
fun SkillEditorPage(
    skill: Skill?,
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    onSave: (Skill) -> Unit,
    onPackageMetadataChanged: ((Skill) -> Unit)? = null,
    onAutoSave: ((Skill) -> Unit)? = null,
    isSaving: Boolean = false,
) {
    val isEditing = skill != null
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics()

    var name by remember { mutableStateOf(skill?.name ?: "") }
    var description by remember { mutableStateOf(skill?.description ?: "") }
    var icon by remember { mutableStateOf(skill?.icon) }
    var instructions by remember { mutableStateOf(skill?.instructions ?: "") }
    val alwaysEnabled = false
    var disableModelInvocation by remember { mutableStateOf(skill?.disableModelInvocation ?: false) }
    var userInvocable by remember { mutableStateOf(skill?.userInvocable ?: true) }
    var injectionPosition by remember { mutableStateOf(skill?.injectionPosition ?: InjectionPosition.AFTER_SYSTEM) }
    var depth by remember { mutableStateOf(skill?.depth?.toString() ?: "0") }
    var availableForAllAssistants by remember { mutableStateOf(skill?.availableForAllAssistants ?: true) }
    var availableAssistantIds by remember { mutableStateOf(skill?.availableAssistantIds ?: emptySet()) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var namePending by remember { mutableStateOf(false) }
    var descriptionPending by remember { mutableStateOf(false) }
    var instructionsPending by remember { mutableStateOf(false) }
    var packageSkillMetadata by remember(skill?.id) { mutableStateOf(skill) }
    var packageEntries by remember(skill?.id) { mutableStateOf(emptyList<SkillExportImport.PackageEntry>()) }
    var editingPackagePath by remember { mutableStateOf<String?>(null) }
    var editingPackageContent by remember { mutableStateOf("") }
    var packageFileSaving by remember { mutableStateOf(false) }
    var showAddPackageFile by remember { mutableStateOf(false) }
    var deletePackagePath by remember { mutableStateOf<String?>(null) }
    val isAutoSaving = isEditing && (namePending || descriptionPending || instructionsPending)

    fun refreshPackageFiles(currentSkill: Skill? = packageSkillMetadata ?: skill) {
        val resolvedSkill = currentSkill ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SkillExportImport.listPackageEntries(context, resolvedSkill) }
            }
            result.onSuccess { packageEntries = it }
                .onFailure { toaster.show(it.message ?: "Could not read skill package") }
        }
    }

    LaunchedEffect(skill?.id) {
        val currentSkill = skill ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching {
                withSkillPackageEdit {
                    SkillExportImport.ensureManagedSkillPackage(context, currentSkill)
                    SkillExportImport.listPackageEntries(context, currentSkill)
                }
            }
        }
        result.onSuccess { packageEntries = it }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            toaster.show(it.message ?: "Could not read skill package")
        }
    }

    val initialSkill = remember(skill?.id) { skill ?: Skill() }
    val formEnabled = !isSaving || isEditing
    fun currentDraft(): Skill = (packageSkillMetadata ?: skill ?: initialSkill).copy(
        name = name.trim(), description = description.trim(), icon = icon, instructions = instructions,
        alwaysEnabled = false, disableModelInvocation = disableModelInvocation, userInvocable = userInvocable,
        injectionPosition = injectionPosition, depth = depth.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        availableForAllAssistants = availableForAllAssistants, availableAssistantIds = availableAssistantIds,
        updatedAt = System.currentTimeMillis(),
    )

    fun persistDraft() {
        if (isEditing) onAutoSave?.invoke(currentDraft())
    }

    androidx.activity.compose.BackHandler(enabled = isSaving && !isEditing) { }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) name.ifBlank { stringResource(R.string.skills_page_unnamed) }
                    else stringResource(R.string.skills_page_add_skill), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (isSaving && !isEditing) Box(Modifier.size(48.dp)) else BackButton()
                },
                actions = {
                    AutoSaveIndicator(visible = isAutoSaving || isSaving)
                    if (isEditing) {
                        Box {
                            IconButton(onClick = { haptics.perform(HapticPattern.Pop); showExportDialog = true },
                                enabled = !isAutoSaving && !isSaving) {
                                Icon(Icons.Rounded.IosShare, contentDescription = stringResource(R.string.export_label))
                            }
                            SkillExportMenu(skill = currentDraft(), expanded = showExportDialog,
                                onDismiss = { showExportDialog = false })
                        }
                    } else {
                        TextButton(
                            enabled = name.isNotBlank() && instructions.isNotBlank() &&
                                !namePending && !descriptionPending && !instructionsPending && !isSaving,
                            onClick = { haptics.perform(HapticPattern.Success); onSave(currentDraft()) },
                        ) { Text(stringResource(R.string.create)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            SettingsGroup(title = "") {
                SettingGroupItem(title = stringResource(R.string.skills_page_name), trailing = {
                    DebouncedTextField(value = name, onValueChange = { value ->
                        name = value.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
                        persistDraft()
                    }, stateKey = "skill_name_${skill?.id ?: "new"}", singleLine = true,
                        inputTransformation = InputTransformation {
                            val normalized = asCharSequence().toString().lowercase()
                                .filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
                            if (normalized != asCharSequence().toString()) replace(0, length, normalized)
                        },
                        modifier = Modifier.fillMaxWidth(0.5f), onPendingChange = { namePending = it }, enabled = formEnabled)
                })
                SettingGroupItem(title = "Icon", icon = { Icon(ModeIcons.getIcon(icon ?: "category"), null) },
                    onClick = if (formEnabled) ({ showIconPicker = true }) else null)
            }
            FormItem(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), label = {
                Text(stringResource(R.string.skills_page_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }) {
                DebouncedTextField(value = description, onValueChange = { description = it.take(1024); persistDraft() },
                    stateKey = "skill_desc_${skill?.id ?: "new"}", minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall,
                    onPendingChange = { descriptionPending = it }, enabled = formEnabled)
            }
            AddonPromptField(title = stringResource(R.string.skills_page_instructions), value = instructions,
                onValueChange = { instructions = it; persistDraft() }, stateKey = "skill_instructions_${skill?.id ?: "new"}",
                enabled = formEnabled, onPendingChange = { instructionsPending = it })
            SettingsGroup(title = "Activation") {
                SettingGroupItem(
                    title = "Assistant can use",
                    trailing = { HapticSwitch(!disableModelInvocation, {
                        disableModelInvocation = !it; persistDraft()
                    }, enabled = formEnabled) },
                    onClick = if (formEnabled) ({ disableModelInvocation = !disableModelInvocation; persistDraft() }) else null,
                )
                SettingGroupItem(
                    title = "Slash command",
                    trailing = { HapticSwitch(userInvocable, { userInvocable = it; persistDraft() }, enabled = formEnabled) },
                    onClick = if (formEnabled) ({ userInvocable = !userInvocable; persistDraft() }) else null,
                )
            }
            CharacterAccessSection(
                assistants = assistants, selectedIds = availableAssistantIds, enabled = formEnabled, entryName = name,
                allSelected = availableForAllAssistants,
                onAllSelectedChange = { availableForAllAssistants = it; persistDraft() },
                onSelectionChange = { id, selected ->
                    availableAssistantIds = if (selected) availableAssistantIds + id else availableAssistantIds - id
                    persistDraft()
                },
            )
            if (skill != null) SkillPackageFilesCard(
                skill = packageSkillMetadata ?: skill, entries = packageEntries, enabled = !isAutoSaving && !isSaving,
                onAddFile = { haptics.perform(HapticPattern.Pop); showAddPackageFile = true },
                onEditFile = { entry ->
                    haptics.perform(HapticPattern.Pop)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            withSkillPackageEdit { runCatching { SkillExportImport.readPackageTextFile(context, packageSkillMetadata ?: skill, entry.relativePath) } }
                        }
                        result.onSuccess { editingPackageContent = it; editingPackagePath = entry.relativePath }
                            .onFailure { toaster.show(it.message ?: "This file cannot be edited") }
                    }
                },
                onDeleteFile = { entry -> haptics.perform(HapticPattern.Pop); deletePackagePath = entry.relativePath },
            )
        }
    }
    if (showIconPicker) MaterialIconPickerDialog(
        onDismiss = { showIconPicker = false },
        onIconSelected = { icon = it; showIconPicker = false; persistDraft() },
    )

    editingPackagePath?.let { relativePath ->
        SkillPackageTextEditorDialog(
            relativePath = relativePath,
            initialContent = editingPackageContent, saving = packageFileSaving,
            onDismiss = { if (!packageFileSaving) editingPackagePath = null },
            onSave = { content ->
                val currentSkill = packageSkillMetadata ?: skill ?: return@SkillPackageTextEditorDialog
                val parsedSkill = if (relativePath == "SKILL.md") {
                    when (val parsed = SkillExportImport.importFromString(content)) {
                        is SkillExportImport.ImportResult.Success -> {
                            if (parsed.format != "skill_md") {
                                toaster.show("SKILL.md must use YAML frontmatter and Markdown")
                                return@SkillPackageTextEditorDialog
                            }
                            parsed.skill
                        }
                        is SkillExportImport.ImportResult.Error -> {
                            toaster.show(parsed.message)
                            return@SkillPackageTextEditorDialog
                        }
                    }
                } else null

                packageFileSaving = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            withSkillPackageEdit {
                                SkillExportImport.savePackageTextFile(context, currentSkill, relativePath, content)
                            }
                        }
                    }
                    result.onSuccess {
                        packageFileSaving = false
                        if (parsedSkill != null) {
                            val updatedSkill = currentSkill.copy(
                                name = parsedSkill.name,
                                description = parsedSkill.description,
                                instructions = parsedSkill.instructions,
                                disableModelInvocation = parsedSkill.disableModelInvocation,
                                userInvocable = parsedSkill.userInvocable,
                                argumentHint = parsedSkill.argumentHint,
                                license = parsedSkill.license,
                                compatibility = parsedSkill.compatibility,
                                metadata = parsedSkill.metadata,
                                allowedTools = parsedSkill.allowedTools,
                                updatedAt = System.currentTimeMillis(),
                            )
                            packageSkillMetadata = updatedSkill
                            name = updatedSkill.name
                            description = updatedSkill.description
                            instructions = updatedSkill.instructions
                            disableModelInvocation = updatedSkill.disableModelInvocation
                            userInvocable = updatedSkill.userInvocable
                            onPackageMetadataChanged?.invoke(updatedSkill)
                        }
                        editingPackagePath = null
                        haptics.perform(HapticPattern.Success)
                        refreshPackageFiles(packageSkillMetadata ?: currentSkill)
                    }.onFailure {
                        packageFileSaving = false
                        haptics.perform(HapticPattern.Error)
                        toaster.show(it.message ?: "Could not save skill file")
                    }
                }
            },
        )
    }

    if (showAddPackageFile && skill != null) {
        AddSkillPackageFileDialog(
            onDismiss = { showAddPackageFile = false },
            onCreate = { relativePath, content ->
                val currentSkill = packageSkillMetadata ?: skill
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            require(relativePath != "SKILL.md") { "SKILL.md already exists" }
                            require(packageEntries.none { it.relativePath == relativePath }) { "A package file already exists at this path" }
                            withSkillPackageEdit {
                                SkillExportImport.savePackageTextFile(context, currentSkill, relativePath, content)
                            }
                        }
                    }
                    result.onSuccess {
                        showAddPackageFile = false
                        haptics.perform(HapticPattern.Success)
                        refreshPackageFiles(currentSkill)
                    }.onFailure {
                        haptics.perform(HapticPattern.Error)
                        toaster.show(it.message ?: "Could not create skill file")
                    }
                }
            },
        )
    }

    deletePackagePath?.let { relativePath ->
        LastChatDestructiveConfirmDialog(
            title = "Delete skill file?", consequence = relativePath,
            onDismiss = { deletePackagePath = null },
            onConfirm = {
                val currentSkill = packageSkillMetadata ?: skill
                deletePackagePath = null
                if (currentSkill != null) scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { withSkillPackageEdit { SkillExportImport.deletePackageFile(context, currentSkill, relativePath) } }
                    }
                    result.onSuccess { deleted ->
                        if (deleted) { haptics.perform(HapticPattern.Success); refreshPackageFiles(currentSkill) }
                        else toaster.show("Could not delete skill file")
                    }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        toaster.show(it.message ?: "Could not delete skill file")
                    }
                }
            },
        )
    }

}

@Composable
private fun SkillPackageFilesCard(
    skill: Skill,
    entries: List<SkillExportImport.PackageEntry>,
    onAddFile: () -> Unit,
    onEditFile: (SkillExportImport.PackageEntry) -> Unit,
    onDeleteFile: (SkillExportImport.PackageEntry) -> Unit,
    enabled: Boolean = true,
) {
    SettingsGroup(title = "Package files") {
        SettingGroupItem(title = "New file", icon = { Icon(Icons.Rounded.Add, null) }, onClick = onAddFile.takeIf { enabled })
        entries.forEach { entry ->
            SettingGroupItem(
                title = entry.relativePath,
                subtitle = if (entry.isDirectory) null else entry.sizeBytes.fileSizeToString(),
                icon = { Icon(if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description, null) },
                onClick = if (enabled && entry.isTextEditable && !entry.isDirectory) ({ onEditFile(entry) }) else null,
                trailing = if (!entry.isDirectory && entry.relativePath != "SKILL.md") ({
                    IconButton(enabled = enabled, onClick = { onDeleteFile(entry) }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.delete))
                    }
                }) else null,
            )
        }
    }
}

@Composable
private fun SkillPackageTextEditorDialog(
    relativePath: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    saving: Boolean = false,
) {
    AddonTextEditor(title = relativePath, initialContent = initialContent,
        onDismiss = onDismiss, onSave = onSave, monospace = true, saving = saving)

}

@Composable
private fun AddSkillPackageFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var relativePath by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val pathSegments = relativePath.split('/')
    val pathInvalid = relativePath.isNotBlank() && (
        relativePath.startsWith('/') ||
            relativePath.contains('\\') ||
            pathSegments.any { it.isBlank() || it == "." || it == ".." } ||
            relativePath == "SKILL.md"
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New package file") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = relativePath,
                    onValueChange = { relativePath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Path") },
                    shape = AppShapes.InputField,
                    placeholder = { Text("references/guide.md", fontFamily = FontFamily.Monospace) },
                    supportingText = if (pathInvalid) {
                        { Text("Use a relative path without empty, . or .. segments") }
                    } else null,
                    isError = pathInvalid,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                    shape = AppShapes.InputField,
                    label = { Text("Content") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(relativePath.trim(), content) },
                enabled = relativePath.isNotBlank() && !pathInvalid,
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SkillExportMenu(
    skill: Skill,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current

    val scope = rememberCoroutineScope()
    fun exportFile(write: suspend () -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { write() } }
            result.onSuccess { toaster.show(context.getString(R.string.skill_export_success)) }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                toaster.show(it.message ?: context.getString(R.string.export_failed_message, ""))
            }
        }
    }
    val skillMdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) exportFile {
            context.contentResolver.openOutputStream(uri)?.use { it.write(SkillExportImport.exportToSkillMd(skill).toByteArray()) }
                ?: error("Could not open export destination")
        }
    }
    val skillPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) exportFile {
            withSkillPackageEdit {
                context.contentResolver.openOutputStream(uri)?.use { it.write(SkillExportImport.exportPackage(context, skill)) }
                    ?: error("Could not open export destination")
            }
        }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("SKILL.md") },
            onClick = { onDismiss(); skillMdLauncher.launch(SkillExportImport.getSuggestedFileName(skill, "skill_md")) })
        DropdownMenuItem(text = { Text("Package (.zip)") },
            onClick = { onDismiss(); skillPackageLauncher.launch("${skill.name.ifBlank { "skill" }}.zip") })
    }
}
