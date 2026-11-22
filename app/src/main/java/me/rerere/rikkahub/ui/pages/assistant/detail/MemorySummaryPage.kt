package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
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
                                text = "Generate a baseline summary from your memories or write one manually.",
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
private fun MemorySummarySectionCard(
    section: MemorySummarySection,
    validMemoryIds: Set<Int>,
    onPointerClick: (Int) -> Unit,
    onQuoteText: (String) -> Unit,
    onNoReceiptFound: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
                        text = "Ask about or update your summary...",
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
