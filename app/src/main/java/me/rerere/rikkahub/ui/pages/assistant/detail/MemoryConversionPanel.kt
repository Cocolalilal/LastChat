package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.model.MemoryConversionDirection
import me.rerere.rikkahub.ui.theme.AppShapes

@Composable
fun MemoryConversionPanel(vm: AssistantDetailVM) {
    val available by vm.conversionAvailable.collectAsStateWithLifecycle()
    val busy by vm.conversionBusy.collectAsStateWithLifecycle()
    val error by vm.conversionError.collectAsStateWithLifecycle()
    val preview by vm.conversionPreview.collectAsStateWithLifecycle()

    if (available) {
        Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Sync, null)
                Column(Modifier.weight(1f)) {
                    Text("Bring newer memories into this system", style = MaterialTheme.typography.titleSmall)
                    Text("Review an incremental, non-destructive conversion before applying it.", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                Button(onClick = vm::generateConversionPreview, enabled = !busy) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Preview")
                }
            }
        }
    }

    preview?.let { current ->
        AlertDialog(
            onDismissRequest = { if (!busy) vm.cancelConversionPreview() },
            title = { Text("Conversion preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current.summary)
                    if (current.direction == MemoryConversionDirection.ENTRY_TO_DOCUMENT) {
                        current.userProfileReplacement?.let {
                            Text("User Profile replacement", style = MaterialTheme.typography.labelLarge)
                            Text(it, maxLines = 8, style = MaterialTheme.typography.bodySmall)
                        }
                        current.characterMemoryReplacement?.let {
                            Text("Character Memory replacement", style = MaterialTheme.typography.labelLarge)
                            Text(it, maxLines = 8, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("${current.entryProposals.size} proposed entry changes", style = MaterialTheme.typography.labelLarge)
                        current.entryProposals.take(20).forEach { Text("• ${it.content}", style = MaterialTheme.typography.bodySmall) }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = vm::applyConversionPreview, enabled = !busy) { Text("Apply preview") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelConversionPreview, enabled = !busy) { Text("Cancel") }
            },
        )
    }
}
