package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.ExpandedPromptEditor
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PromptInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.uuid.Uuid

/** Settings binding for the same prompt field and expanded editor used by characters. */
@Composable
fun AddonPromptField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    stateKey: Any?,
    enabled: Boolean = true,
    onPendingChange: (Boolean) -> Unit = {},
) {
    val state = key(stateKey) { rememberTextFieldState(initialText = value) }
    var published by remember(stateKey) { mutableStateOf(value) }
    var expanded by remember(stateKey) { mutableStateOf(false) }
    var expandedDraft by remember(stateKey) { mutableStateOf(value) }
    val update by rememberUpdatedState(onValueChange)
    val pending by rememberUpdatedState(onPendingChange)
    fun publish(text: String) {
        if (text != published) {
            published = text
            update(text)
        }
        pending(false)
    }
    LaunchedEffect(value) {
        // Incoming changes may update an idle field, never an uncommitted local draft.
        if (!expanded && state.text.toString() == published) {
            if (state.text.toString() != value) state.edit { replace(0, length, value) }
            published = value
        }
    }
    LaunchedEffect(stateKey, expanded) {
        if (!expanded) snapshotFlow { state.text.toString() }.collectLatest { text ->
            if (text != published) {
                pending(true)
                delay(300)
                publish(text)
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(stateKey, lifecycle) {
        fun flush() {
            val text = if (expanded) expandedDraft else state.text.toString()
            // The inline field is saveable; keep it current before lifecycle state is saved.
            // Otherwise recreation can restore its older text over the expanded draft.
            if (expanded && state.text.toString() != text) state.edit { replace(0, length, text) }
            publish(text)
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) flush() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); flush() }
    }
    PromptInput(
        title = title, state = state, enabled = enabled && !expanded,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        onFocusLost = { if (!expanded) publish(state.text.toString()) },
        onExpand = {
            expandedDraft = state.text.toString()
            publish(expandedDraft)
            expanded = true
        },
    )
    if (expanded) ExpandedPromptEditor(
        systemPrompt = expandedDraft, title = title,
        onDraft = { expandedDraft = it },
        onUpdate = { text ->
            if (state.text.toString() != text) state.edit { replace(0, length, text) }
            publish(text)
        },
        onDone = { expanded = false },
    )
}

fun addonItemPosition(index: Int, count: Int): ItemPosition = when {
    count == 1 -> ItemPosition.ONLY
    index == 0 -> ItemPosition.FIRST
    index == count - 1 -> ItemPosition.LAST
    else -> ItemPosition.MIDDLE
}

/** Connected row geometry used by the existing skill and lorebook assignment lists. */
fun addonItemShape(position: ItemPosition): Shape = when (position) {
    ItemPosition.ONLY -> AppShapes.CardLarge
    ItemPosition.FIRST -> RoundedCornerShape(28.dp, 28.dp, 8.dp, 8.dp)
    ItemPosition.MIDDLE -> RoundedCornerShape(8.dp)
    ItemPosition.LAST -> RoundedCornerShape(8.dp, 8.dp, 28.dp, 28.dp)
}

/** Uses the original lorebook character picker interaction and switch rows. */
@Composable
fun CharacterAccessSection(
    assistants: List<Assistant>,
    selectedIds: Set<Uuid>,
    onSelectionChange: (Uuid, Boolean) -> Unit,
    allSelected: Boolean? = null,
    onAllSelectedChange: ((Boolean) -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
    title: String = "Characters",
    entryName: String = title,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val summary = if (allSelected == true) "All characters" else "${assistants.count { it.id in selectedIds }} selected"
    SettingsGroup(title = "", horizontalPadding = horizontalPadding) {
        SettingGroupItem(title = title, subtitle = summary,
            trailing = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
            onClick = if (enabled) ({ open = true }) else null)
    }
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = { open = false }, sheetState = sheetState, sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = { scope.launch { sheetState.hide(); open = false } }) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close characters")
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entryName.ifBlank { title }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            if (allSelected != null && onAllSelectedChange != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), shape = AppShapes.CardLarge) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("All characters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        HapticSwitch(allSelected, onAllSelectedChange, enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = "All characters" })
                    }
                }
            }
            if (allSelected != true) assistants.forEachIndexed { index, assistant ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    shape = addonItemShape(addonItemPosition(index, assistants.size))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        UIAvatar(name = assistant.name, value = assistant.avatar,
                            modifier = Modifier.size(40.dp).clearAndSetSemantics {})
                        Text(assistant.name.ifBlank { "Character" }, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HapticSwitch(assistant.id in selectedIds, { onSelectionChange(assistant.id, it) }, enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = assistant.name })
                    }
                }
            }
        }
    }
}

@Composable
fun AddonEmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Package files keep explicit Save, using the existing expanded prompt editor presentation. */
@Composable
fun AddonTextEditor(title: String, initialContent: String, onDismiss: () -> Unit, onSave: (String) -> Unit,
    monospace: Boolean = true, saving: Boolean = false) {
    ExpandedPromptEditor(systemPrompt = initialContent, title = title, onDraft = {}, onUpdate = onSave,
        commitOnDismiss = false, closeOnSave = false, saving = saving, onDone = onDismiss)
}
