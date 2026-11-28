package me.rerere.rikkahub.ui.pages.studio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.repository.StudioChecklistEntry
import me.rerere.rikkahub.data.repository.StudioHubItem
import me.rerere.rikkahub.data.repository.StudioItemType
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.writeClipboardText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StudioItemEditorScreen(
    initialItem: StudioHubItem,
    settings: Settings,
    activeAssistantName: String,
    enhanceModelName: String,
    onCloseAndSave: (StudioHubItem, Boolean) -> Unit,
    onDelete: (StudioHubItem) -> Unit,
    onDuplicate: (StudioHubItem) -> Unit,
    onAskAiToEnhance: suspend (StudioHubItem) -> String,
    onAskAiAboutItem: (StudioHubItem) -> Unit,
    onImportAttachments: suspend (String, List<Uri>, Int) -> List<String>,
    onZoomImages: (List<String>, String?) -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalMotionPolicy.current.reduceMotion

    var type by remember(initialItem.id) { mutableStateOf(initialItem.type) }
    var title by remember(initialItem.id) { mutableStateOf(initialItem.title) }
    var body by remember(initialItem.id) { mutableStateOf(initialItem.body) }
    var checklist by remember(initialItem.id) { mutableStateOf(initialItem.checklist) }
    var newChecklistInput by remember(initialItem.id) { mutableStateOf("") }
    var attachments by remember(initialItem.id) { mutableStateOf(initialItem.attachmentPaths) }
    var tags by remember(initialItem.id) { mutableStateOf(initialItem.tags) }
    var colorToken by remember(initialItem.id) { mutableStateOf(initialItem.colorToken) }
    var themeId by remember(initialItem.id) { mutableStateOf(initialItem.themeId) }
    var isPinned by remember(initialItem.id) { mutableStateOf(initialItem.isPinned) }
    var isArchived by remember(initialItem.id) { mutableStateOf(initialItem.isArchived) }
    var isCompleted by remember(initialItem.id) { mutableStateOf(initialItem.isCompleted) }
    var reminderAtMillis by remember(initialItem.id) { mutableStateOf(initialItem.reminderAtMillis) }

    var showChecklistSection by remember(initialItem.id) {
        mutableStateOf(initialItem.type == StudioItemType.TASK_LIST || initialItem.checklist.isNotEmpty())
    }
    var checkedExpanded by remember(initialItem.id) { mutableStateOf(false) }
    var isReaderMode by remember(initialItem.id) { mutableStateOf(false) }

    var showColorThemeSheet by remember { mutableStateOf(false) }
    var showInsertSheet by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var pendingTagInput by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingPickedDateMillis by remember { mutableStateOf<Long?>(null) }
    var isEnhancing by remember { mutableStateOf(false) }

    val palette = rememberStudioPalette(colorToken)
    val animatedCanvasBg by animateColorAsState(
        targetValue = palette.container,
        animationSpec = tween(if (reduceMotion) 0 else 240),
        label = "editor_canvas_bg",
    )

    fun buildDraft(): StudioHubItem {
        val cleanChecklist = checklist.filter { it.text.isNotBlank() }
        val resolvedType = when {
            attachments.isNotEmpty() && type == StudioItemType.NOTE -> StudioItemType.IMAGE_NOTE
            cleanChecklist.isNotEmpty() && type == StudioItemType.NOTE -> StudioItemType.TASK_LIST
            reminderAtMillis != null && type == StudioItemType.NOTE && body.isBlank() && cleanChecklist.isEmpty() -> StudioItemType.REMINDER
            else -> type
        }
        return initialItem.copy(
            type = resolvedType,
            title = title.trim(),
            body = body.trimEnd(),
            checklist = cleanChecklist,
            attachmentPaths = attachments,
            tags = tags,
            colorToken = colorToken,
            themeId = themeId,
            isPinned = isPinned,
            isArchived = isArchived,
            isCompleted = isCompleted,
            reminderAtMillis = reminderAtMillis,
        )
    }

    fun hasContent(draft: StudioHubItem): Boolean {
        return draft.title.isNotBlank() ||
            draft.body.isNotBlank() ||
            draft.checklist.isNotEmpty() ||
            draft.attachmentPaths.isNotEmpty() ||
            draft.reminderAtMillis != null
    }

    fun closeAndPersist() {
        val draft = buildDraft()
        val shouldPersist = hasContent(draft)
        onCloseAndSave(draft, shouldPersist)
    }

    BackHandler(enabled = true) {
        haptics.perform(HapticPattern.Tick)
        closeAndPersist()
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val imported = onImportAttachments(initialItem.id, uris, attachments.size)
                if (imported.isNotEmpty()) {
                    attachments = attachments + imported
                    if (type == StudioItemType.NOTE) {
                        type = StudioItemType.IMAGE_NOTE
                    }
                    haptics.perform(HapticPattern.Success)
                }
            }
        }
    }

    val creatorName = remember(initialItem, settings.assistants, settings.assistantId) {
        resolveStudioCreatorName(initialItem, settings)
    }
    val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedCanvasBg),
    ) {
        // Subtle canvas texture backdrop
        StudioCanvasThemeBackdrop(
            themeId = themeId,
            inkColor = palette.onContainer,
            accentColor = palette.accent,
            isCardPreview = false,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Keep-style top action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Tick)
                        closeAndPersist()
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Save and go back",
                        tint = palette.onContainer,
                    )
                }

                Spacer(Modifier.weight(1f))

                // Pin button (rounded square, Keep style)
                KeepTopActionSquare(
                    icon = Icons.Rounded.PushPin,
                    contentDescription = if (isPinned) "Unpin note" else "Pin note",
                    active = isPinned,
                    palette = palette,
                    onClick = {
                        haptics.perform(HapticPattern.Selection)
                        isPinned = !isPinned
                    },
                )

                // Reminder button
                KeepTopActionSquare(
                    icon = if (reminderAtMillis != null) Icons.Rounded.NotificationsActive else Icons.Rounded.AlarmAdd,
                    contentDescription = "Set reminder",
                    active = reminderAtMillis != null,
                    palette = palette,
                    onClick = {
                        haptics.perform(HapticPattern.Selection)
                        showDatePicker = true
                    },
                )

                // Archive button
                KeepTopActionSquare(
                    icon = if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    contentDescription = if (isArchived) "Unarchive" else "Archive",
                    active = isArchived,
                    palette = palette,
                    onClick = {
                        haptics.perform(HapticPattern.Selection)
                        isArchived = !isArchived
                        toaster.show(if (isArchived) "Marked archived" else "Restored from archive")
                    },
                )
            }

            // Full-height distraction-free scrollable canvas
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                // Edge-to-edge or clean photo attachment gallery
                if (attachments.isNotEmpty()) {
                    if (attachments.size == 1) {
                        val path = attachments.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(AppShapes.CardSmall),
                        ) {
                            AsyncImage(
                                model = path,
                                contentDescription = title.ifBlank { "Note photo" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 320.dp)
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        onZoomImages(attachments, title.ifBlank { null })
                                    },
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.56f),
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    attachments = emptyList()
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(attachments) { idx, path ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 210.dp, height = 168.dp)
                                        .clip(AppShapes.CardSmall),
                                ) {
                                    AsyncImage(
                                        model = path,
                                        contentDescription = "Photo ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                haptics.perform(HapticPattern.Tick)
                                                onZoomImages(attachments, title.ifBlank { null })
                                            },
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.56f),
                                        onClick = {
                                            haptics.perform(HapticPattern.Tick)
                                            attachments = attachments.filterIndexed { i, _ -> i != idx }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(26.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Tasteful Assistant attribution
                    if (creatorName != null) {
                        StudioAttributionBadge(
                            creatorName = creatorName,
                            appearance = settings.studioAppearance,
                            palette = palette,
                        )
                    }

                    // Title + Body (Edit mode vs Reader mode)
                    AnimatedContent(
                        targetState = isReaderMode,
                        transitionSpec = {
                            fadeIn(tween(if (reduceMotion) 0 else 160)) togetherWith
                                fadeOut(tween(if (reduceMotion) 0 else 120))
                        },
                        label = "editor_reader_toggle",
                    ) { reading ->
                        if (reading) {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (title.isNotBlank()) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 32.sp,
                                            ),
                                            color = palette.onContainer,
                                        )
                                    }
                                    if (body.isNotBlank()) {
                                        Text(
                                            text = buildCleanMarkdownAnnotatedString(
                                                raw = body,
                                                codeBackground = palette.chipContainer,
                                            ),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                lineHeight = 26.sp,
                                            ),
                                            color = palette.onContainer.copy(alpha = 0.94f),
                                        )
                                    } else if (title.isBlank()) {
                                        Text(
                                            text = "Nothing written yet.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = palette.onContainerMuted,
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Borderless Keep-style Title field
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (title.isEmpty()) {
                                        Text(
                                            text = "Title",
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.Normal,
                                                lineHeight = 32.sp,
                                            ),
                                            color = palette.onContainerMuted.copy(alpha = 0.65f),
                                        )
                                    }
                                    BasicTextField(
                                        value = title,
                                        onValueChange = { title = it },
                                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 32.sp,
                                            color = palette.onContainer,
                                        ),
                                        cursorBrush = SolidColor(palette.accent),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // Borderless Keep-style Body field (unbounded height, scrolls naturally with page)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = if (showChecklistSection) 48.dp else 180.dp),
                                ) {
                                    if (body.isEmpty()) {
                                        Text(
                                            text = if (showChecklistSection) "Add a note..." else "Note",
                                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                            color = palette.onContainerMuted.copy(alpha = 0.65f),
                                        )
                                    }
                                    BasicTextField(
                                        value = body,
                                        onValueChange = { body = it },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 26.sp,
                                            color = palette.onContainer.copy(alpha = 0.94f),
                                        ),
                                        cursorBrush = SolidColor(palette.accent),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    // Native Keep-style Checklist (active items + collapsible checked items)
                    AnimatedVisibility(
                        visible = showChecklistSection,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        val unchecked = checklist.filterNot { it.isChecked }
                        val checked = checklist.filter { it.isChecked }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            unchecked.forEach { entry ->
                                KeepChecklistRow(
                                    entry = entry,
                                    palette = palette,
                                    onToggle = {
                                        haptics.perform(HapticPattern.Tick)
                                        checklist = checklist.map {
                                            if (it.id == entry.id) it.copy(isChecked = !it.isChecked) else it
                                        }
                                    },
                                    onTextChange = { updatedText ->
                                        checklist = checklist.map {
                                            if (it.id == entry.id) it.copy(text = updatedText) else it
                                        }
                                    },
                                    onRemove = {
                                        haptics.perform(HapticPattern.Tick)
                                        checklist = checklist.filterNot { it.id == entry.id }
                                    },
                                )
                            }

                            // Inline "+ List item" input row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = palette.onContainerMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (newChecklistInput.isEmpty()) {
                                        Text(
                                            text = "List item",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = palette.onContainerMuted.copy(alpha = 0.65f),
                                        )
                                    }
                                    BasicTextField(
                                        value = newChecklistInput,
                                        onValueChange = { newChecklistInput = it },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                val txt = newChecklistInput.trim()
                                                if (txt.isNotBlank()) {
                                                    haptics.perform(HapticPattern.Tick)
                                                    checklist = checklist + StudioChecklistEntry(text = txt)
                                                    newChecklistInput = ""
                                                }
                                            }
                                        ),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = palette.onContainer,
                                        ),
                                        cursorBrush = SolidColor(palette.accent),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (newChecklistInput.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val txt = newChecklistInput.trim()
                                            if (txt.isNotBlank()) {
                                                haptics.perform(HapticPattern.Tick)
                                                checklist = checklist + StudioChecklistEntry(text = txt)
                                                newChecklistInput = ""
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Add list item",
                                            tint = palette.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            // Collapsible checked items drawer
                            if (checked.isNotEmpty()) {
                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (checkedExpanded) 180f else 0f,
                                    animationSpec = if (reduceMotion) tween(0) else spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                    label = "checked_chevron",
                                )
                                HorizontalDivider(
                                    color = palette.border,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptics.perform(HapticPattern.Tick)
                                            checkedExpanded = !checkedExpanded
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = palette.onContainerMuted,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .graphicsLayer { rotationZ = chevronRotation },
                                    )
                                    Text(
                                        text = "${checked.size} checked ${if (checked.size == 1) "item" else "items"}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = palette.onContainerMuted,
                                    )
                                }
                                AnimatedVisibility(visible = checkedExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        checked.forEach { entry ->
                                            KeepChecklistRow(
                                                entry = entry,
                                                palette = palette,
                                                onToggle = {
                                                    haptics.perform(HapticPattern.Tick)
                                                    checklist = checklist.map {
                                                        if (it.id == entry.id) it.copy(isChecked = !it.isChecked) else it
                                                    }
                                                },
                                                onTextChange = { updatedText ->
                                                    checklist = checklist.map {
                                                        if (it.id == entry.id) it.copy(text = updatedText) else it
                                                    }
                                                },
                                                onRemove = {
                                                    haptics.perform(HapticPattern.Tick)
                                                    checklist = checklist.filterNot { it.id == entry.id }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Reminder chip + Tag chips (just like Keep's "Human" chip under the body)
                    if (reminderAtMillis != null || tags.isNotEmpty() || !initialItem.sourceUrl.isNullOrBlank()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            reminderAtMillis?.let { millis ->
                                val isOverdue = !isCompleted && millis < System.currentTimeMillis()
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isOverdue) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        palette.chipContainer
                                    },
                                    onClick = {
                                        haptics.perform(HapticPattern.Selection)
                                        showDatePicker = true
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NotificationsActive,
                                            contentDescription = null,
                                            tint = if (isOverdue) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                palette.chipContent
                                            },
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = dateFmt.format(Date(millis)),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isOverdue) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                palette.chipContent
                                            },
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear reminder",
                                            tint = if (isOverdue) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                palette.chipContent
                                            },
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    haptics.perform(HapticPattern.Tick)
                                                    reminderAtMillis = null
                                                },
                                        )
                                    }
                                }
                            }

                            tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.chipContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.chipContent,
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove tag $tag",
                                            tint = palette.chipContent.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    haptics.perform(HapticPattern.Tick)
                                                    tags = tags.filterNot { it == tag }
                                                },
                                        )
                                    }
                                }
                            }

                            initialItem.sourceUrl?.takeIf { it.isNotBlank() }?.let { sourceUrl ->
                                val host = remember(sourceUrl) {
                                    runCatching { Uri.parse(sourceUrl).host }.getOrNull()
                                        ?: sourceUrl
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.chipContainer,
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        context.writeClipboardText(sourceUrl)
                                        toaster.show("Copied source link")
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = null,
                                            tint = palette.chipContent,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = host,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.chipContent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quiet edited timestamp footer
                    val wordCount = remember(body) {
                        body.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.size ?: 0
                    }
                    val editedText = remember(initialItem.updatedAt, wordCount) {
                        val stamp = "Edited ${dateFmt.format(Date(initialItem.updatedAt))}"
                        if (wordCount >= 10) "$stamp · $wordCount words" else stamp
                    }
                    Text(
                        text = editedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onContainerMuted.copy(alpha = 0.68f),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            // Keep-style Bottom Toolbar
            Surface(
                color = animatedCanvasBg,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Column {
                    HorizontalDivider(color = palette.border.copy(alpha = 0.55f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        KeepBottomActionCircle(
                            icon = Icons.Rounded.AddBox,
                            contentDescription = "Add attachment, checklist, or tag",
                            palette = palette,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                showInsertSheet = true
                            },
                        )

                        KeepBottomActionCircle(
                            icon = Icons.Rounded.Palette,
                            contentDescription = "Color and background theme",
                            palette = palette,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                showColorThemeSheet = true
                            },
                        )

                        KeepBottomActionCircle(
                            icon = if (isReaderMode) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = if (isReaderMode) "Switch to edit mode" else "Switch to reader mode",
                            active = isReaderMode,
                            palette = palette,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                isReaderMode = !isReaderMode
                            },
                        )

                        // Enhance with configured AI model
                        Surface(
                            shape = CircleShape,
                            color = palette.chipContainer,
                            onClick = {
                                if (!isEnhancing) {
                                    haptics.perform(HapticPattern.Pop)
                                    val prevTitle = title
                                    val prevBody = body
                                    isEnhancing = true
                                    scope.launch {
                                        runCatching { onAskAiToEnhance(buildDraft()) }
                                            .onSuccess { polished ->
                                                if (polished.isNotBlank()) {
                                                    val lines = polished.lines()
                                                    val titleLine = lines.firstOrNull {
                                                        it.startsWith("TITLE:", ignoreCase = true)
                                                    }
                                                    if (titleLine != null && title.isBlank()) {
                                                        title = titleLine.substringAfter(":").trim()
                                                        body = lines.filterNot { it == titleLine }
                                                            .joinToString("\n").trim()
                                                    } else {
                                                        body = polished
                                                    }
                                                    haptics.perform(HapticPattern.Success)
                                                    toaster.show(
                                                        message = "Enhanced with $enhanceModelName",
                                                        type = ToastType.Success,
                                                        action = ToastAction(
                                                            label = "Undo",
                                                            onClick = {
                                                                title = prevTitle
                                                                body = prevBody
                                                            },
                                                        ),
                                                    )
                                                }
                                            }
                                            .onFailure { err ->
                                                haptics.perform(HapticPattern.Error)
                                                toaster.show(
                                                    message = err.message ?: "Enhance failed",
                                                    type = ToastType.Error,
                                                )
                                            }
                                        isEnhancing = false
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isEnhancing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = palette.accent,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Enhance note with $enhanceModelName",
                                        tint = palette.accent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Ask Assistant pill (passes full note context via Base64)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.accent.copy(alpha = 0.16f),
                            border = BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.35f)),
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                val draft = buildDraft()
                                onAskAiAboutItem(draft)
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SmartToy,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Ask $activeAssistantName",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.onContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Overflow menu
                        Box {
                            KeepBottomActionCircle(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                palette = palette,
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    showOverflowMenu = true
                                },
                            )
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy text") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        val draft = buildDraft()
                                        val copied = buildString {
                                            if (draft.title.isNotBlank()) appendLine(draft.title)
                                            if (draft.body.isNotBlank()) appendLine(draft.body)
                                            draft.checklist.forEach {
                                                appendLine("- [${if (it.isChecked) "x" else " "}] ${it.text}")
                                            }
                                        }.trim()
                                        context.writeClipboardText(copied)
                                        toaster.show("Copied to clipboard")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Make a copy") },
                                    leadingIcon = { Icon(Icons.Rounded.Add, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onDuplicate(buildDraft())
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onDelete(initialItem)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Color & Canvas Theme Picker Sheet
    if (showColorThemeSheet) {
        StudioColorAndThemePickerSheet(
            selectedColorToken = colorToken,
            selectedThemeId = themeId,
            onSelectColor = { colorToken = it },
            onSelectTheme = { themeId = it },
            onDismiss = { showColorThemeSheet = false },
        )
    }

    // Keep-style Insert (+) Bottom Sheet
    if (showInsertSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInsertSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            ) {
                Text(
                    text = "Add to note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                ListItem(
                    headlineContent = { Text("Add image") },
                    leadingContent = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                    modifier = Modifier.clickable {
                        showInsertSheet = false
                        pickImagesLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(if (showChecklistSection) "Hide checkboxes" else "Checkboxes")
                    },
                    leadingContent = { Icon(Icons.Rounded.Checklist, null) },
                    modifier = Modifier.clickable {
                        showInsertSheet = false
                        haptics.perform(HapticPattern.Selection)
                        showChecklistSection = !showChecklistSection
                        if (showChecklistSection && type == StudioItemType.NOTE) {
                            type = StudioItemType.TASK_LIST
                        }
                    },
                )
                ListItem(
                    headlineContent = { Text("Add tag") },
                    leadingContent = { Icon(Icons.Rounded.Tag, null) },
                    modifier = Modifier.clickable {
                        showInsertSheet = false
                        pendingTagInput = ""
                        showAddTagDialog = true
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(if (reminderAtMillis != null) "Change reminder" else "Set reminder")
                    },
                    leadingContent = { Icon(Icons.Rounded.AlarmAdd, null) },
                    modifier = Modifier.clickable {
                        showInsertSheet = false
                        showDatePicker = true
                    },
                )
            }
        }
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add tag") },
            text = {
                OutlinedTextField(
                    value = pendingTagInput,
                    onValueChange = { pendingTagInput = it },
                    placeholder = { Text("e.g. ideas, work, movies") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = pendingTagInput.trim().removePrefix("#").trim()
                        if (cleaned.isNotBlank() && cleaned !in tags) {
                            tags = tags + cleaned
                            haptics.perform(HapticPattern.Tick)
                        }
                        showAddTagDialog = false
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Reminder Date Picker -> Time Picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = reminderAtMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPickedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = remember(reminderAtMillis) {
            Calendar.getInstance().apply {
                timeInMillis = reminderAtMillis ?: (System.currentTimeMillis() + 3_600_000L)
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick reminder time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedDate = pendingPickedDateMillis ?: System.currentTimeMillis()
                        val merged = Calendar.getInstance().apply {
                            timeInMillis = pickedDate
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        reminderAtMillis = merged.timeInMillis
                        if (type == StudioItemType.NOTE && body.isBlank() && checklist.isEmpty()) {
                            type = StudioItemType.REMINDER
                        }
                        showTimePicker = false
                        haptics.perform(HapticPattern.Success)
                    },
                ) { Text("Set Reminder") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun KeepTopActionSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    palette: StudioResolvedPalette,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) palette.accent.copy(alpha = 0.22f) else palette.chipContainer,
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) palette.accent else palette.onContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun KeepBottomActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    palette: StudioResolvedPalette,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (active) palette.accent.copy(alpha = 0.22f) else palette.chipContainer,
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) palette.accent else palette.onContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun KeepChecklistRow(
    entry: StudioChecklistEntry,
    palette: StudioResolvedPalette,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = if (entry.isChecked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (entry.isChecked) "Uncheck item" else "Check item",
                tint = if (entry.isChecked) palette.accent else palette.onContainerMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        BasicTextField(
            value = entry.text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (entry.isChecked) palette.onContainerMuted.copy(alpha = 0.65f) else palette.onContainer,
                textDecoration = if (entry.isChecked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove item",
                tint = palette.onContainerMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
