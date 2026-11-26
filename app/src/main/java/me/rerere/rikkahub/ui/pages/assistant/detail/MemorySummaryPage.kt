package me.rerere.rikkahub.ui.pages.assistant.detail

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.memory.DiveDeeperAction
import me.rerere.rikkahub.data.memory.MemoryPointerParser
import me.rerere.rikkahub.data.memory.MemorySummaryCadence
import me.rerere.rikkahub.data.memory.MemorySummaryMarkdownCodec
import me.rerere.rikkahub.data.memory.MemorySummarySection
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.base64Encode
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

private data class SummaryBulletLine(
    val index: Int,
    val rawLine: String,
    val cleanText: String,
    val pointerIds: List<Int>,
    val isBullet: Boolean,
)

private fun parseSummaryLines(content: String): List<SummaryBulletLine> {
    if (content.isBlank()) return emptyList()
    return content.lines().mapIndexedNotNull { idx, rawLine ->
        val trimmedStart = rawLine.trimStart()
        if (trimmedStart.isBlank()) return@mapIndexedNotNull null
        val isBullet = trimmedStart.startsWith("- ") ||
            trimmedStart.startsWith("* ") ||
            trimmedStart.startsWith("• ")
        val pointerIds = MemoryPointerParser.extractIds(rawLine)
        val cleanText = MemoryPointerParser.stripPointers(rawLine)
            .removePrefix("- ")
            .removePrefix("* ")
            .removePrefix("• ")
            .trim()
        if (cleanText.isBlank() && pointerIds.isEmpty()) {
            null
        } else {
            SummaryBulletLine(
                index = idx,
                rawLine = rawLine,
                cleanText = cleanText.ifBlank { rawLine.trim() },
                pointerIds = pointerIds,
                isBullet = isBullet,
            )
        }
    }
}

@Composable
fun MemorySummaryPage(
    assistantId: String,
    navBackStack: NavHostController,
    vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(assistantId) }),
) {
    val uiState by vm.summaryUiState.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbarMessage.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()

    MemorySummaryPage(
        assistantId = assistantId,
        uiState = uiState,
        currentCadence = assistant.memorySummaryCadence,
        isAutoSyncEnabled = assistant.enableMemory && assistant.enableMemorySummary,
        snackbarMessage = snackbarMsg,
        onClearSnackbarMessage = { vm.clearSnackbarMessage() },
        onBack = { navBackStack.popBackStack() },
        onRegenerate = { vm.regenerateMemorySummary() },
        onAskOrUpdate = { prompt -> vm.askOrUpdateMemorySummary(prompt) },
        onClearAgentState = { vm.clearAgentState() },
        onUpdateCadence = { cadence -> vm.updateSummaryCadence(cadence) },
        onDiveDeeperClick = { action ->
            val prompt = action.prompt.ifBlank { action.displayTitle() }.trim()
            if (prompt.isNotEmpty()) {
                vm.switchToAssistant(assistantId)
                navBackStack.navigate(
                    Screen.Chat(
                        id = UUID.randomUUID().toString(),
                        text = prompt.base64Encode(),
                    )
                )
            }
        },
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
    currentCadence: MemorySummaryCadence = MemorySummaryCadence.IMMEDIATE,
    isAutoSyncEnabled: Boolean = true,
    snackbarMessage: String? = null,
    onClearSnackbarMessage: () -> Unit = {},
    onBack: () -> Unit,
    onRegenerate: () -> Unit = {},
    onAskOrUpdate: (String) -> Unit,
    onClearAgentState: () -> Unit = {},
    onUpdateCadence: (MemorySummaryCadence) -> Unit = {},
    onDiveDeeperClick: (DiveDeeperAction) -> Unit = {},
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
    var showCadenceSheet by remember { mutableStateOf(false) }
    var expandedLineKey by rememberSaveable { mutableStateOf<String?>(null) }

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
    val haptics = rememberPremiumHaptics()

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

    fun quoteIntoAskBar(cleanQuote: String) {
        val prefix = if (askInput.text.isNotEmpty() && !askInput.text.endsWith("\n")) "\n" else ""
        val compactQuote = cleanQuote.replace(Regex("\\s+"), " ").trim()
        val next = "${askInput.text}$prefix> \"$compactQuote\"\n"
        askInput = TextFieldValue(next, TextRange(next.length))
        askFocusRequester.requestFocus()
        keyboardController?.show()
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
                            text = if (isEditMode) {
                                "Edit Summary"
                            } else {
                                uiState.summaryData.title.ifBlank { "Your Summary" }
                            },
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
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = Icons.Rounded.Psychology,
                                        contentDescription = "Agent reasoning and live activity",
                                        tint = if (uiState.agentState.isWorking) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    if (uiState.agentState.isWorking || uiState.agentState.error != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (uiState.agentState.error != null) {
                                                        MaterialTheme.colorScheme.error
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    }
                                                ),
                                        )
                                    }
                                }
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
                                        text = { Text("Sync Frequency...") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Sync, contentDescription = null)
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showCadenceSheet = true
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
                            haptics.perform(HapticPattern.Send)
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
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.size(64.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 1. Live Agent Activity Banner
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

                        // 2. Editorial Hero & Metadata Card
                        item(key = "_summary_hero_card") {
                            SummaryHeroMetadataCard(
                                title = uiState.summaryData.title.ifBlank { "Your Summary" },
                                subtitle = uiState.summaryData.subtitle,
                                lastUpdatedMs = maxOf(uiState.lastSyncedAt, uiState.lastManualEditAt),
                                revision = uiState.revision,
                                tokenCount = uiState.tokenCount,
                                currentCadence = currentCadence,
                                isAutoSyncEnabled = isAutoSyncEnabled,
                                onCadenceClick = { showCadenceSheet = true },
                            )
                        }

                        // 3. Section Cards (Clean Lines + Tap-to-Reveal Reference Tray)
                        itemsIndexed(
                            items = uiState.summaryData.sections,
                            key = { idx, section -> "${section.normalizedKey()}_$idx" },
                        ) { _, section ->
                            MemorySummarySectionCard(
                                section = section,
                                validMemoryIds = uiState.validMemoryIds,
                                expandedLineKey = expandedLineKey,
                                onToggleLine = { key ->
                                    haptics.perform(HapticPattern.Tick)
                                    expandedLineKey = if (expandedLineKey == key) null else key
                                },
                                onPointerClick = onInspectReceipt,
                                onQuoteText = ::quoteIntoAskBar,
                                onNoReceiptFound = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("No memory pointer found for the selected text.")
                                    }
                                },
                            )
                        }

                        // 4. Restored Dive Deeper Quick-Prompt Cards
                        if (uiState.summaryData.diveDeeper.isNotEmpty()) {
                            item(key = "_dive_deeper_header") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "Dive Deeper",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Jump into chat to explore or update these areas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            itemsIndexed(
                                items = uiState.summaryData.diveDeeper,
                                key = { idx, action -> "dive_${idx}_${action.displayTitle().hashCode()}" },
                            ) { _, action ->
                                DiveDeeperActionCard(
                                    action = action,
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        onDiveDeeperClick(action)
                                    },
                                )
                            }
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

    if (showCadenceSheet) {
        SyncCadencePickerSheet(
            currentCadence = currentCadence,
            isAutoSyncEnabled = isAutoSyncEnabled,
            onSelectCadence = { cadence ->
                haptics.perform(HapticPattern.Pop)
                onUpdateCadence(cadence)
                showCadenceSheet = false
            },
            onDismiss = { showCadenceSheet = false },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryHeroMetadataCard(
    title: String,
    subtitle: String,
    lastUpdatedMs: Long,
    revision: Long,
    tokenCount: Int,
    currentCadence: MemorySummaryCadence,
    isAutoSyncEnabled: Boolean,
    onCadenceClick: () -> Unit,
) {
    val relativeTime = remember(lastUpdatedMs) {
        if (lastUpdatedMs > 0L) {
            DateUtils.getRelativeTimeSpanString(
                lastUpdatedMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
        } else {
            null
        }
    }

    val cadencePillLabel = if (!isAutoSyncEnabled) {
        "Auto: Off"
    } else {
        when (currentCadence) {
            MemorySummaryCadence.IMMEDIATE -> "Auto: Immediate"
            MemorySummaryCadence.DEBOUNCE_10M -> "Auto: 10m"
            MemorySummaryCadence.HOURLY -> "Auto: Hourly"
            MemorySummaryCadence.DAILY -> "Auto: Daily"
            MemorySummaryCadence.MANUAL -> "Manual"
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetadataPill(
                    icon = Icons.Rounded.Schedule,
                    label = if (relativeTime != null) "Updated $relativeTime" else "Not synced yet",
                )
                if (revision > 0L) {
                    MetadataPill(
                        label = if (tokenCount > 0) "Rev $revision • ~$tokenCount tokens" else "Rev $revision",
                    )
                }
                Surface(
                    onClick = onCadenceClick,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = cadencePillLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataPill(
    label: String,
    icon: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun resolveSectionIconStyle(iconName: String): Triple<ImageVector, Color, Color> {
    return when (iconName.lowercase().trim()) {
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
}

@Composable
private fun SectionIconBox(
    iconName: String,
    modifier: Modifier = Modifier,
    boxSize: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 20.dp,
) {
    val (vector, tint, bg) = resolveSectionIconStyle(iconName)

    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemorySummarySectionCard(
    section: MemorySummarySection,
    validMemoryIds: Set<Int>,
    expandedLineKey: String?,
    onToggleLine: (String) -> Unit,
    onPointerClick: (Int) -> Unit,
    onQuoteText: (String) -> Unit,
    onNoReceiptFound: () -> Unit,
) {
    val lines = remember(section.content) { parseSummaryLines(section.content) }
    val (_, accentTint, _) = resolveSectionIconStyle(section.icon)
    val sectionKey = section.normalizedKey()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionIconBox(
                    iconName = section.icon,
                    boxSize = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 20.dp,
                )
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (lines.isNotEmpty()) {
                    val countLabel = if (lines.size == 1) "1 fact" else "${lines.size} facts"
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = countLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .memorySelectionContextMenuComponents(
                                hasSectionCitations = section.citedMemoryIds.isNotEmpty(),
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        lines.forEach { line ->
                            val lineKey = "$sectionKey:${line.index}"
                            val hasPointers = line.pointerIds.isNotEmpty()
                            val isExpanded = hasPointers && expandedLineKey == lineKey

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isExpanded) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (hasPointers) {
                                            Modifier.pointerInput(lineKey) {
                                                detectTapGestures(
                                                    onTap = { onToggleLine(lineKey) },
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    if (line.isBullet) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(accentTint.copy(alpha = 0.7f)),
                                        )
                                    }
                                    Text(
                                        text = line.cleanText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 22.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                DisableSelection {
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp, bottom = 2.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Link,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(
                                                        text = "References",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Surface(
                                                        onClick = { onQuoteText(line.cleanText) },
                                                        shape = RoundedCornerShape(50),
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.AutoAwesome,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(12.dp),
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            )
                                                            Text(
                                                                text = "Ask",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            )
                                                        }
                                                    }
                                                }

                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    line.pointerIds.forEach { memId ->
                                                        val isValid = validMemoryIds.isEmpty() || memId in validMemoryIds
                                                        Surface(
                                                            onClick = { onPointerClick(memId) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (isValid) {
                                                                MaterialTheme.colorScheme.secondaryContainer
                                                            } else {
                                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                                            },
                                                            border = BorderStroke(
                                                                0.5.dp,
                                                                if (isValid) {
                                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                                                },
                                                            ),
                                                        ) {
                                                            Text(
                                                                text = "#$memId",
                                                                style = if (isValid) {
                                                                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                                                                } else {
                                                                    MaterialTheme.typography.labelSmall.copy(
                                                                        fontWeight = FontWeight.Medium,
                                                                        textDecoration = TextDecoration.LineThrough,
                                                                    )
                                                                },
                                                                color = if (isValid) {
                                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                                } else {
                                                                    MaterialTheme.colorScheme.onErrorContainer
                                                                },
                                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
                }
            }
        }
    }
}

@Composable
private fun DiveDeeperActionCard(
    action: DiveDeeperAction,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "dive_deeper_scale",
    )

    val title = action.displayTitle()
    val preview = action.displaySubtitle().ifBlank {
        if (action.prompt != title) action.prompt else ""
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionIconBox(
                iconName = action.icon,
                boxSize = 36.dp,
                cornerRadius = 10.dp,
                iconSize = 18.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncCadencePickerSheet(
    currentCadence: MemorySummaryCadence,
    isAutoSyncEnabled: Boolean,
    onSelectCadence: (MemorySummaryCadence) -> Unit,
    onDismiss: () -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "Summary Sync Frequency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isAutoSyncEnabled) {
                            "Choose how quickly memory changes update your summary."
                        } else {
                            "Enable Memory Summary in Assistant Memory settings for background updates."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            MemorySummaryCadence.entries.forEach { cadence ->
                val selected = cadence == currentCadence
                val (label, description) = when (cadence) {
                    MemorySummaryCadence.IMMEDIATE -> "Immediate" to "Coalesces changes and updates within seconds"
                    MemorySummaryCadence.DEBOUNCE_10M -> "10 Minutes" to "Waits 10 minutes after your last memory edit"
                    MemorySummaryCadence.HOURLY -> "Hourly" to "Periodic background refresh every hour"
                    MemorySummaryCadence.DAILY -> "Daily" to "Periodic background refresh once a day"
                    MemorySummaryCadence.MANUAL -> "Manual only" to "Only updates when you regenerate or ask"
                }

                Surface(
                    onClick = { onSelectCadence(cadence) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (selected) 1.dp else 0.5.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
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
        shape = RoundedCornerShape(18.dp),
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            shadowElevation = 4.dp,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
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
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
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
                        .padding(bottom = 6.dp)
                        .size(42.dp),
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
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
}
