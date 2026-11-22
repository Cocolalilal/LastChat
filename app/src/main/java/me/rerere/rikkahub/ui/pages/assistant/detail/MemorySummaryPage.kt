package me.rerere.rikkahub.ui.pages.assistant.detail

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.memory.MemorySummaryMarkdownCodec
import me.rerere.rikkahub.data.memory.MemorySummarySection
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MemorySummaryPage(
    assistantId: String,
    navBackStack: NavHostController,
    vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(assistantId) }),
) {
    val uiState by vm.summaryUiState.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbarMessage.collectAsStateWithLifecycle()

    MemorySummaryPage(
        assistantId = assistantId,
        uiState = uiState,
        snackbarMessage = snackbarMsg,
        onClearSnackbarMessage = { vm.clearSnackbarMessage() },
        onBack = { navBackStack.popBackStack() },
        onRegenerate = { vm.regenerateMemorySummary() },
        onAskOrUpdate = { prompt -> vm.askOrUpdateMemorySummary(prompt) },
        onClearAgentState = { vm.clearAgentState() },
        onSaveManualEdit = { title, subtitle, markdown ->
            vm.saveManualSummaryEdit(assistantId, title, subtitle, markdown)
        },
        onInspectReceipt = { memoryId -> vm.inspectMemoryReceipt(assistantId, memoryId) },
        onDismissReceipt = { vm.dismissReceiptSheet() },
        onSaveRawMemory = { id, content -> vm.updateRawMemoryFromReceipt(assistantId, id, content) },
        onDeleteRawMemory = { id, prune -> vm.deleteRawMemoryFromReceipt(assistantId, id, prune) },
        onRemoveStaleCitation = { id -> vm.removeStaleCitation(assistantId, id) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySummaryPage(
    assistantId: String,
    uiState: MemorySummaryUiState,
    snackbarMessage: String? = null,
    onClearSnackbarMessage: () -> Unit = {},
    onBack: () -> Unit,
    onRegenerate: () -> Unit = {},
    onAskOrUpdate: (String) -> Unit,
    onClearAgentState: () -> Unit = {},
    onSaveManualEdit: (title: String, subtitle: String, sectionsMarkdown: String) -> Unit,
    onInspectReceipt: (Int) -> Unit,
    onDismissReceipt: () -> Unit,
    onSaveRawMemory: (Int, String) -> Unit,
    onDeleteRawMemory: (Int, Boolean) -> Unit,
    onRemoveStaleCitation: (Int) -> Unit,
) {
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAgentInspector by remember { mutableStateOf(false) }

    // Manual editor local draft state (preserved across configuration changes and tablet rotation)
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editSubtitle by rememberSaveable { mutableStateOf("") }
    var editMarkdown by rememberSaveable { mutableStateOf("") }
    var initialMarkdown by rememberSaveable { mutableStateOf("") }

    // Bottom ask bar state preserved across configuration changes and tablet rotation
    var askInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val askFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(snackbarMessage) {
        if (!snackbarMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbarMessage()
        }
    }

    val hasUnsavedEdits = isEditMode && (
        editMarkdown != initialMarkdown ||
            editTitle != uiState.summaryData.title ||
            editSubtitle != uiState.summaryData.subtitle
        )

    fun enterEditMode() {
        val encoded = MemorySummaryMarkdownCodec.encodeSectionsToMarkdown(uiState.summaryData)
        editTitle = uiState.summaryData.title
        editSubtitle = uiState.summaryData.subtitle
        editMarkdown = encoded
        initialMarkdown = encoded
        isEditMode = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    navigationIcon = {
                        if (isEditMode) {
                            IconButton(
                                onClick = {
                                    if (hasUnsavedEdits) showDiscardDialog = true else isEditMode = false
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel editing")
                            }
                        } else {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    title = {
                        Text(
                            text = if (isEditMode) "Edit Summary" else uiState.summaryData.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (isEditMode) {
                            TextButton(
                                enabled = hasUnsavedEdits && editMarkdown.isNotBlank(),
                                onClick = {
                                    onSaveManualEdit(editTitle, editSubtitle, editMarkdown)
                                    isEditMode = false
                                }
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                        } else {
                            IconButton(
                                onClick = { showAgentInspector = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = "Agent reasoning & live activity",
                                    tint = if (uiState.agentState.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                enabled = !uiState.isGenerating,
                                onClick = ::enterEditMode,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit summary manually",
                                )
                            }
                            Box {
                                IconButton(onClick = { showOptionsMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "More options",
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Regenerate Summary") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                                        },
                                        enabled = !uiState.isGenerating,
                                        onClick = {
                                            showOptionsMenu = false
                                            onRegenerate()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Live Agent Stream") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Psychology, contentDescription = null)
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showAgentInspector = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                if (uiState.isGenerating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            if (!isEditMode) {
                MemorySummaryAskBar(
                    inputValue = askInput,
                    onValueChange = { askInput = it },
                    isGenerating = uiState.isGenerating,
                    enabled = true,
                    focusRequester = askFocusRequester,
                    onSubmit = {
                        val prompt = askInput.text.trim()
                        if (prompt.isNotEmpty() && !uiState.isGenerating) {
                            askInput = TextFieldValue("")
                            onAskOrUpdate(prompt)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = isEditMode,
            label = "summary_mode_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { editing ->
            if (editing) {
                MemorySummaryManualEditor(
                    title = editTitle,
                    onTitleChange = { editTitle = it },
                    subtitle = editSubtitle,
                    onSubtitleChange = { editSubtitle = it },
                    markdown = editMarkdown,
                    onMarkdownChange = { editMarkdown = it },
                    onInsertSectionTemplate = {
                        val suffix = if (editMarkdown.endsWith("\n") || editMarkdown.isEmpty()) "" else "\n\n"
                        editMarkdown += "${suffix}## New Section\n- "
                    },
                )
            } else {
                if (uiState.summaryData.sections.isEmpty() && !uiState.isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (uiState.agentState.error != null || uiState.agentState.stage.isNotBlank()) {
                                AgentActivityBannerCard(
                                    agentState = uiState.agentState,
                                    onDismiss = onClearAgentState,
                                    onRetry = onRegenerate,
                                    onOpenInspector = { showAgentInspector = true },
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "No summary generated yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Synthesize a baseline understanding of your profile and memories, or write one manually.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onRegenerate) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Synthesize")
                                }
                                TextButton(onClick = ::enterEditMode) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit Manually")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 1. Live Agent Activity Banner (visible whenever agent is working, errored, or has live answer)
                        if (uiState.agentState.isWorking || uiState.agentState.error != null || uiState.agentState.answer != null) {
                            item(key = "_summary_agent_activity") {
                                AgentActivityBannerCard(
                                    agentState = uiState.agentState,
                                    onDismiss = onClearAgentState,
                                    onRetry = onRegenerate,
                                    onOpenInspector = { showAgentInspector = true },
                                )
                            }
                        }

                        // 2. Metadata & Last Upgraded Header
                        item(key = "_summary_metadata_header") {
                            val lastUpdatedMs = maxOf(uiState.lastSyncedAt, uiState.lastManualEditAt)
                            val relativeTime = if (lastUpdatedMs > 0L) {
                                DateUtils.getRelativeTimeSpanString(lastUpdatedMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                            } else "Not yet synchronized"

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Updated $relativeTime",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {
                                            Text(
                                                text = "Rev ${uiState.revision}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                        if (uiState.tokenCount > 0) {
                                            Text(
                                                text = "~${uiState.tokenCount} tokens",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Subtitle if present
                        if (uiState.summaryData.subtitle.isNotBlank()) {
                            item(key = "_summary_subtitle") {
                                Text(
                                    text = uiState.summaryData.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }

                        // 4. Section cards with beautiful icons and citations
                        items(
                            items = uiState.summaryData.sections,
                            key = { it.normalizedKey() },
                        ) { section ->
                            MemorySummarySectionCard(
                                section = section,
                                validMemoryIds = uiState.validMemoryIds,
                                onPointerClick = onInspectReceipt,
                                onQuoteText = { cleanQuote ->
                                    val prefix = if (askInput.text.isNotEmpty() && !askInput.text.endsWith("\n")) "\n" else ""
                                    val compactQuote = cleanQuote.replace(Regex("\\s+"), " ").trim()
                                    val next = "${askInput.text}$prefix> \"$compactQuote\"\n"
                                    askInput = TextFieldValue(next, TextRange(next.length))
                                    askFocusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                                onNoReceiptFound = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("No memory pointer found for the selected text.")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAgentInspector) {
        MemoryAgentActivitySheet(
            agentState = uiState.agentState,
            onDismiss = { showAgentInspector = false },
            onRetry = onRegenerate,
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard unsaved edits?") },
            text = { Text("Your manual changes to the memory summary have not been saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        isEditMode = false
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            },
        )
    }

    uiState.inspectedMemoryId?.let { inspectedId ->
        MemoryReceiptBottomSheet(
            memoryId = inspectedId,
            memory = uiState.inspectedMemory,
            isLoading = uiState.isLoadingReceipt,
            onDismiss = onDismissReceipt,
            onSaveEdit = onSaveRawMemory,
            onDeleteMemory = onDeleteRawMemory,
            onRemoveStaleCitation = onRemoveStaleCitation,
        )
    }
}

@Composable
private fun SectionIconBox(
    iconName: String,
    modifier: Modifier = Modifier,
) {
    val (vector, tint, bg) = when (iconName.lowercase().trim()) {
        "heart" -> Triple(Icons.Rounded.Favorite, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        "star" -> Triple(Icons.Rounded.Star, Color(0xFFE6A23C), Color(0xFFE6A23C).copy(alpha = 0.18f))
        "code" -> Triple(Icons.Rounded.Code, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
        "target" -> Triple(Icons.Rounded.TrackChanges, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        "devices" -> Triple(Icons.Rounded.Devices, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
        "sparkles" -> Triple(Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        "profile" -> Triple(Icons.Rounded.Person, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
        "book" -> Triple(Icons.Rounded.MenuBook, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
        "music" -> Triple(Icons.Rounded.MusicNote, Color(0xFF9C27B0), Color(0xFF9C27B0).copy(alpha = 0.18f))
        else -> Triple(Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MemorySummarySectionCard(
    section: MemorySummarySection,
    validMemoryIds: Set<Int>,
    onPointerClick: (Int) -> Unit,
    onQuoteText: (String) -> Unit,
    onNoReceiptFound: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionIconBox(iconName = section.icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            ProvideMemorySelectionToolbar(
                sectionContent = section.content,
                sectionHasCitations = section.citedMemoryIds.isNotEmpty(),
                onQuoteText = onQuoteText,
                onInspectReceipt = onPointerClick,
                onNoReceiptFound = onNoReceiptFound,
            ) {
                SelectionContainer {
                    MemoryPointerText(
                        content = section.content,
                        validMemoryIds = validMemoryIds,
                        onPointerClick = onPointerClick,
                    )
                }
            }
            // Display citation source chips at the bottom of the section card
            if (section.citedMemoryIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "Sources:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    section.citedMemoryIds.forEach { memId ->
                        val isValid = memId in validMemoryIds
                        Surface(
                            onClick = { onPointerClick(memId) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isValid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "#$memId",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isValid) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentActivityBannerCard(
    agentState: MemorySummaryAgentState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenInspector: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (agentState.error != null) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            }
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (agentState.isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (agentState.error != null) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = if (agentState.error != null) "Operation Failed" else agentState.stage.ifBlank { "Agent Active" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (agentState.error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenInspector) {
                    Text("Details", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (agentState.error != null) {
                Text(
                    text = agentState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            } else if (agentState.answer != null) {
                Text(
                    text = agentState.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else if (agentState.reasoning.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Reasoning: " + agentState.reasoning.takeLast(200),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryAgentActivitySheet(
    agentState: MemorySummaryAgentState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Memory Agent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (agentState.isWorking) "Active Generation & Reasoning" else if (agentState.error != null) "Encountered Error" else "Idle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (agentState.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            if (agentState.stage.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Stage: ${agentState.stage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if (agentState.error != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Error Details",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = agentState.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onRetry()
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Retry Operation")
                        }
                    }
                }
            }

            if (agentState.reasoning.isNotBlank()) {
                Text(
                    text = "Live Reasoning Stream",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = agentState.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (agentState.liveDelta.isNotBlank()) {
                Text(
                    text = "Synthesized Stream Content",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = agentState.liveDelta,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (agentState.answer != null) {
                Text(
                    text = "Agent Response",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = agentState.answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MemorySummaryManualEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    subtitle: String,
    onSubtitleChange: (String) -> Unit,
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    onInsertSectionTemplate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Summary Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = subtitle,
            onValueChange = onSubtitleChange,
            label = { Text("Subtitle") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Sections (Markdown with ## Headings and [#ID] tags)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onInsertSectionTemplate) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Section")
            }
        }
        OutlinedTextField(
            value = markdown,
            onValueChange = onMarkdownChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            placeholder = { Text("## Hardware & Workflow\n- Uses Lenovo Tab M9 [#79]") },
        )
    }
}

@Composable
private fun MemorySummaryAskBar(
    inputValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isGenerating: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = enabled && !isGenerating && inputValue.text.isNotBlank()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = inputValue,
                onValueChange = onValueChange,
                enabled = enabled && !isGenerating,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = "Ask or instruct your assistant about memories...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = false,
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
            )

            FilledIconButton(
                onClick = onSubmit,
                enabled = canSend,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(44.dp),
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Send update request",
                    )
                }
            }
        }
    }
}
