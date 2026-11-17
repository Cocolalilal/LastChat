package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.uuid.Uuid

@Composable
fun AddonEditorField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    stateKey: Any?,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    onPendingChange: (Boolean) -> Unit = {},
    onExpand: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var pending by remember(stateKey) { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()
    SettingGroupInputItem(title = title, trailing = onExpand?.let { expand -> {
        IconButton(enabled = enabled && !pending, onClick = { haptics.perform(HapticPattern.Pop); expand() }) {
            Icon(Icons.Rounded.Fullscreen, "Expand $title")
        }
    } }) {
        DebouncedTextField(
            value = value, onValueChange = onValueChange, stateKey = stateKey,
            singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
            onPendingChange = { pending = it; onPendingChange(it) }, outlined = false, enabled = enabled,
        )
    }
}

fun addonItemPosition(index: Int, count: Int): ItemPosition = when {
    count == 1 -> ItemPosition.ONLY
    index == 0 -> ItemPosition.FIRST
    index == count - 1 -> ItemPosition.LAST
    else -> ItemPosition.MIDDLE
}

fun addonItemShape(position: ItemPosition): Shape = when (position) {
    ItemPosition.ONLY -> AppShapes.CardMedium
    ItemPosition.FIRST -> AppShapes.ListItemFirst
    ItemPosition.MIDDLE -> AppShapes.ListItemMiddle
    ItemPosition.LAST -> AppShapes.ListItemLast
}

@Composable
fun AddonListItem(
    title: String,
    subtitle: String? = null,
    position: ItemPosition,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
    selected: Boolean? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberPremiumHaptics()
    Card(
        onClick = { haptics.perform(HapticPattern.Pop); onClick() }, enabled = enabled,
        shape = addonItemShape(position),
        colors = CardDefaults.cardColors(containerColor = if (LocalDarkMode.current) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            if (selected != null) {
                role = Role.Checkbox
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

@Composable
fun CharacterAccessSection(
    assistants: List<Assistant>,
    selectedIds: Set<Uuid>,
    onSelectionChange: (Uuid, Boolean) -> Unit,
    allSelected: Boolean? = null,
    onAllSelectedChange: ((Boolean) -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
    title: String = "Characters",
    enabled: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val visible = assistants.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.padding(horizontal = horizontalPadding).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (allSelected != null && onAllSelectedChange != null) {
            AddonListItem(title = "All characters", position = ItemPosition.ONLY, leading = {}, selected = allSelected, enabled = enabled,
                trailing = { Checkbox(allSelected, onCheckedChange = null, enabled = enabled) },
                onClick = { onAllSelectedChange(!allSelected) })
        }
        if (allSelected != true) {
            if (assistants.size > 8) OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, enabled = enabled,
                placeholder = { Text("Search characters") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = AppShapes.SearchField, modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(visible, key = { _, it -> it.id }) { index, assistant ->
                    val selected = assistant.id in selectedIds
                    AddonListItem(
                        title = assistant.name.ifBlank { "Unnamed character" }, selected = selected, enabled = enabled,
                        position = addonItemPosition(index, visible.size),
                        leading = { UIAvatar(name = assistant.name, value = assistant.avatar, modifier = Modifier.size(40.dp).clearAndSetSemantics {}) },
                        trailing = { Checkbox(selected, onCheckedChange = null, enabled = enabled) },
                        onClick = { onSelectionChange(assistant.id, !selected) },
                    )
                }
                if (visible.isEmpty() && query.isNotBlank()) item {
                    Text("No matching characters", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
fun AddonEmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-height editor for long prompt or package text; only Save commits this draft. */
@Composable
fun AddonTextEditor(
    title: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    monospace: Boolean = false,
    saving: Boolean = false,
) {
    var content by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(initialContent) }
    val haptics = rememberPremiumHaptics()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = !saving, dismissOnClickOutside = false),
    ) {
        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (saving) Box(Modifier.size(48.dp)) else {
                            me.rerere.rikkahub.ui.components.nav.LastChatBackButton(
                                onClick = onDismiss, contentDescription = "Back",
                                onHaptic = { haptics.perform(HapticPattern.Pop) },
                            )
                        }
                    },
                    actions = {
                        me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator(saving)
                        androidx.compose.material3.TextButton(enabled = !saving, onClick = { haptics.perform(HapticPattern.Pop); onSave(content) }) {
                            Text("Save")
                        }
                    },
                )
            },
        ) { padding ->
            androidx.compose.material3.TextField(
                value = content, onValueChange = { content = it }, enabled = !saving,
                modifier = Modifier.fillMaxSize().padding(padding),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace
                    else MaterialTheme.typography.bodyLarge.fontFamily,
                ),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
    }
}
