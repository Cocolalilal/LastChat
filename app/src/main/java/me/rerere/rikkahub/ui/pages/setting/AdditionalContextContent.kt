package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.theme.AppShapes

@Composable
internal fun AdditionalContextContent(
    settings: Settings,
    onSave: (AdditionalContext) -> Unit,
    onDelete: (AdditionalContext) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var editing by remember { mutableStateOf<AdditionalContext?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Prompt snippets are always included for their assigned characters. Skills load only when invoked.")
            TextButton(onClick = { editing = AdditionalContext() }) { Text("Add context") }
        }
        items(settings.additionalContexts, key = { it.id }) { snippet ->
            Card(
                onClick = { editing = snippet },
                shape = AppShapes.CardLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(snippet.name.ifBlank { "Untitled context" }, style = MaterialTheme.typography.titleMedium)
                        Text(snippet.prompt, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (snippet.allAssistants) "All characters" else "${snippet.assistantIds.size} characters",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    HapticSwitch(checked = snippet.enabled, onCheckedChange = { onSave(snippet.copy(enabled = it)) })
                }
            }
        }
    }
    editing?.let { original ->
        var draft by remember(original.id) { mutableStateOf(original) }
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Additional context", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.prompt,
                    onValueChange = { draft = draft.copy(prompt = it) },
                    label = { Text("Prompt") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("All characters", modifier = Modifier.weight(1f))
                    HapticSwitch(checked = draft.allAssistants, onCheckedChange = { draft = draft.copy(allAssistants = it) })
                }
                if (!draft.allAssistants) {
                    settings.assistants.forEach { assistant ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(assistant.name.ifBlank { "Unnamed character" }, modifier = Modifier.weight(1f))
                            HapticSwitch(
                                checked = assistant.id in draft.assistantIds,
                                onCheckedChange = { checked ->
                                    draft = draft.copy(assistantIds = if (checked) draft.assistantIds + assistant.id else draft.assistantIds - assistant.id)
                                },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (settings.additionalContexts.any { it.id == original.id }) {
                        TextButton(onClick = { onDelete(original); editing = null }) { Text("Delete") }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                    TextButton(
                        enabled = draft.name.isNotBlank() && draft.prompt.isNotBlank(),
                        onClick = { onSave(draft.copy(name = draft.name.trim())); editing = null },
                    ) { Text("Save") }
                }
            }
        }
    }
}
