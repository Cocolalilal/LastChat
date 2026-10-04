package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.transformers.DefaultPlaceholderProvider
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.ai.ui.toMessageNode
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Tag
import androidx.compose.ui.text.font.FontFamily
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.insertAtCursor
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.components.settings.CollapseSpacingColumn
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.utils.onError
import me.rerere.rikkahub.utils.onSuccess
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun AssistantPromptSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templateTransformer = koinInject<TemplateTransformer>()
    var isFullScreen by remember { mutableStateOf(false) }
    val fullScreenOpen = rememberUpdatedState(isFullScreen)
    val persistedAssistant = rememberUpdatedState(assistant)
    val currentOnUpdate = rememberUpdatedState(onUpdate)
    val holder = remember(assistant.id) { PromptDraftHolder(assistant) }
    var displayed by remember(assistant.id) { mutableStateOf(assistant) }
    val systemPromptValue = androidx.compose.runtime.key(assistant.id) {
        rememberTextFieldState(initialText = assistant.systemPrompt)
    }

    LaunchedEffect(assistant) {
        if (!holder.edited || assistant.samePromptDraft(holder.assistant)) {
            // Nothing local to keep, or our flush just landed. Take the stored copy so a
            // later flush cannot write an older snapshot of the other fields back.
            holder.replace(assistant)
            displayed = assistant
        } else {
            val prompt = holder.assistant.systemPrompt
            val intros = holder.assistant.introTexts()
            val cycle = holder.assistant.cycleIntrosOnNewChat
            holder.replace(assistant)
            holder.applySystemPrompt(prompt)
            holder.applyIntros(intros)
            holder.setCycleIntros(cycle)
            displayed = holder.assistant
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(assistant.id, lifecycleOwner) {
        fun flush() {
            holder.flush(
                systemPrompt = if (fullScreenOpen.value) null else systemPromptValue.text.toString(),
                persisted = persistedAssistant.value,
                onUpdate = currentOnUpdate.value,
            )
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flush()
        }
    }

    fun flushDraft() {
        holder.flush(
            systemPrompt = if (fullScreenOpen.value) null else systemPromptValue.text.toString(),
            persisted = persistedAssistant.value,
            onUpdate = currentOnUpdate.value,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // INTROS
        // ═══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
            val intros = remember(displayed.presetMessages, displayed.alternateGreetings) {
                displayed.introTexts()
            }
            val updateIntros = { newIntros: List<String> ->
                if (!fullScreenOpen.value) {
                    holder.applySystemPrompt(systemPromptValue.text.toString())
                }
                holder.applyIntros(newIntros)
                displayed = holder.assistant
                flushDraft()
            }

            var introsExpanded by remember { mutableStateOf(intros.isNotEmpty()) }
            var introPendingDelete by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(intros.isNotEmpty()) {
                if (intros.isNotEmpty()) introsExpanded = true
            }

            introPendingDelete?.let { pendingIndex ->
                AlertDialog(
                    onDismissRequest = { introPendingDelete = null },
                    title = { Text("Delete intro?") },
                    text = { Text("This intro will be removed from the character.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                haptics.perform(HapticPattern.Error)
                                val newList = intros.toMutableList()
                                if (pendingIndex in newList.indices) {
                                    newList.removeAt(pendingIndex)
                                    updateIntros(newList)
                                }
                                introPendingDelete = null
                            }
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { introPendingDelete = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (intros.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        updateIntros(listOf(""))
                        introsExpanded = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add intro to this character")
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current)
                        MaterialTheme.colorScheme.surfaceContainerLow
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
                ) {
                    CollapseSpacingColumn(
                        modifier = Modifier.padding(16.dp),
                        spacing = 12.dp,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.perform(HapticPattern.Tick)
                                    introsExpanded = !introsExpanded
                                }
                        ) {
                            Text(
                                text = "Intros",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (introsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        ExpandableContent(visible = introsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    intros.forEachIndexed { index, intro ->
                                        var isEditing by remember { mutableStateOf(false) }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardSmall,
                                            shadowElevation = 1.dp,
                                            modifier = Modifier
                                                .width(280.dp)
                                                .height(140.dp)
                                                .clickable {
                                                    haptics.perform(HapticPattern.Pop)
                                                    isEditing = true
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "Intro #${index + 1}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = {
                                                            haptics.perform(HapticPattern.Tick)
                                                            introPendingDelete = index
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = intro.ifEmpty { "Tap to write intro..." },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 4,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (intro.isEmpty())
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                    else
                                                        MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        if (isEditing) {
                                            FullScreenSystemPromptEditor(
                                                systemPrompt = intro,
                                                onDraft = { newText ->
                                                    holder.replaceIntro(index, newText)
                                                },
                                                onUpdate = { newText ->
                                                    holder.replaceIntro(index, newText)
                                                    displayed = holder.assistant
                                                    flushDraft()
                                                },
                                                onDone = { isEditing = false }
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardSmall,
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(140.dp)
                                            .clickable {
                                                haptics.perform(HapticPattern.Pop)
                                                updateIntros(intros + "")
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.Add,
                                                null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                "Add Intro",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                if (intros.size > 1) {
                                    FormItem(
                                        label = { Text("Cycle through intros on new chats") },
                                        tail = {
                                            HapticSwitch(
                                                checked = displayed.cycleIntrosOnNewChat,
                                                onCheckedChange = {
                                                    holder.applySystemPrompt(systemPromptValue.text.toString())
                                                    holder.setCycleIntros(it)
                                                    displayed = holder.assistant
                                                    flushDraft()
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SYSTEM PROMPT
        // ═══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Title with token count. The count composable is the only thing that
                    // reads the field each keystroke, so the rest of this page does not recompose.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_page_system_prompt),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        PromptTokenCount(systemPromptValue, vm::estimateTokens)
                        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                isFullScreen = !isFullScreen
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Rounded.Fullscreen, null)
                        }
                    }

                    SyncPromptDraft(
                        state = systemPromptValue,
                        enabled = !isFullScreen,
                    ) { text ->
                        holder.applySystemPrompt(text)
                    }
                    OutlinedTextField(
                        state = systemPromptValue,
                        modifier = Modifier
                            .fillMaxWidth(),
                        trailingIcon = null,
                        lineLimits = TextFieldLineLimits.MultiLine(
                            minHeightInLines = 5,
                            maxHeightInLines = 10,
                        ),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                    )

                    if (isFullScreen) {
                        FullScreenSystemPromptEditor(
                            systemPrompt = systemPromptValue.text.toString(),
                            onDraft = { newText ->
                                holder.applySystemPrompt(newText)
                            },
                            onUpdate = { newSystemPrompt ->
                                holder.applySystemPrompt(newSystemPrompt)
                                if (systemPromptValue.text.toString() != newSystemPrompt) {
                                    systemPromptValue.edit {
                                        replace(0, length, newSystemPrompt)
                                    }
                                }
                                flushDraft()
                            }
                        ) {
                            isFullScreen = false
                        }
                    }

                    var variablesExpanded by remember { mutableStateOf(false) }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { variablesExpanded = !variablesExpanded }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.assistant_page_available_variables),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Icon(
                                if (variablesExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        ExpandableContent(visible = variablesExpanded) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                var pendingInsertion by remember { mutableStateOf<String?>(null) }
                                val permissionLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.RequestMultiplePermissions()
                                ) {
                                    pendingInsertion?.let { text ->
                                        systemPromptValue.insertAtCursor(text)
                                    }
                                    pendingInsertion = null
                                }

                                DefaultPlaceholderProvider.placeholders.forEach { (k, info) ->
                                    Tag(
                                        onClick = {
                                            val textToInsert = "{{$k}}"
                                            val permissions = mutableListOf<String>()
                                            if (k == "location") {
                                                permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                                permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                            } else if (k == "calendar") {
                                                permissions.add(android.Manifest.permission.READ_CALENDAR)
                                            }

                                            if (permissions.isNotEmpty()) {
                                                pendingInsertion = textToInsert
                                                permissionLauncher.launch(permissions.toTypedArray())
                                            } else {
                                                systemPromptValue.insertAtCursor(textToInsert)
                                            }
                                        }
                                    ) {
                                        info.displayName()
                                        Text(": {{$k}}")
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
private fun FullScreenSystemPromptEditor(
    systemPrompt: String,
    onDraft: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onDone: () -> Unit
) {
    // Use TextFieldState for stable, reliable text editing
    // Initialize once with the current value - do NOT re-sync from parent
    val textFieldState = rememberTextFieldState(initialText = systemPrompt)
    val currentText = textFieldState.text.toString()
    val draft = rememberUpdatedState(onDraft)
    val commit = rememberUpdatedState(onUpdate)

    // In-memory only. Writing the datastore here made typing hitch, and the
    // debounce was cancelled when the dialog or the screen went away.
    SideEffect {
        draft.value(currentText)
    }
    DisposableEffect(Unit) {
        onDispose {
            draft.value(textFieldState.text.toString())
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            val latest = textFieldState.text.toString()
            draft.value(latest)
            commit.value(latest)
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = me.rerere.rikkahub.ui.theme.AppShapes.BottomSheet,
                color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                val latest = textFieldState.text.toString()
                                draft.value(latest)
                                commit.value(latest)
                                onDone()
                            }
                        ) {
                            Text(stringResource(R.string.assistant_page_save))
                        }
                    }
                    OutlinedTextField(
                        state = textFieldState,
                        modifier = Modifier
                            .imePadding()
                            .fillMaxSize(),
                        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                        placeholder = {
                            Text(stringResource(R.string.assistant_page_system_prompt))
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptTokenCount(
    state: TextFieldState,
    estimate: (String) -> Int,
) {
    Text(
        text = "${estimate(state.text.toString())} tokens",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun SyncPromptDraft(
    state: TextFieldState,
    enabled: Boolean,
    onText: (String) -> Unit,
) {
    val text = state.text.toString()
    val current = rememberUpdatedState(onText)
    SideEffect {
        if (enabled) current.value(text)
    }
}

internal class PromptDraftHolder(initial: Assistant) {
    var assistant: Assistant = initial
        private set
    var edited: Boolean = false
        private set
    private var lastFlushed: Assistant? = initial

    fun replace(next: Assistant) {
        assistant = next
        edited = false
        lastFlushed = next
    }

    fun applySystemPrompt(text: String) {
        if (assistant.systemPrompt == text) return
        assistant = assistant.copy(systemPrompt = text)
        edited = true
    }

    fun setCycleIntros(enabled: Boolean) {
        if (assistant.cycleIntrosOnNewChat == enabled) return
        assistant = assistant.copy(cycleIntrosOnNewChat = enabled)
        edited = true
    }

    fun replaceIntro(index: Int, text: String) {
        val current = assistant.introTexts()
        if (index !in current.indices || current[index] == text) return
        val next = current.toMutableList()
        next[index] = text
        applyIntros(next)
    }

    fun applyIntros(intros: List<String>) {
        if (assistant.introTexts() == intros) return
        assistant = assistant.withIntros(intros)
        edited = true
    }

    fun flush(systemPrompt: String?, persisted: Assistant, onUpdate: (Assistant) -> Unit) {
        if (systemPrompt != null) applySystemPrompt(systemPrompt)
        if (!edited) return
        val next = assistant
        if (lastFlushed?.samePromptDraft(next) == true) return
        lastFlushed = next
        if (next.samePromptDraft(persisted)) return
        onUpdate(next)
    }
}

internal fun Assistant.introTexts(): List<String> {
    val list = mutableListOf<String>()
    presetMessages.filter { it.role == MessageRole.ASSISTANT }.forEach { list.add(it.toText()) }
    list.addAll(alternateGreetings)
    return list
}

internal fun Assistant.withIntros(intros: List<String>): Assistant {
    if (intros.isEmpty()) {
        return copy(presetMessages = emptyList(), alternateGreetings = emptyList())
    }
    return copy(
        presetMessages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text(intros.first()))
            )
        ),
        alternateGreetings = intros.drop(1)
    )
}

internal fun Assistant.samePromptDraft(other: Assistant): Boolean {
    return systemPrompt == other.systemPrompt &&
        introTexts() == other.introTexts() &&
        cycleIntrosOnNewChat == other.cycleIntrosOnNewChat
}
