package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExportOptionsDialog(
    title: String,
    onDismissRequest: () -> Unit,
    showMemoriesOption: Boolean,
    showLorebooksOption: Boolean,
    onConfirm: (includeMemories: Boolean, includeLorebooks: Boolean) -> Unit
) {
    var includeMemories by remember { mutableStateOf(showMemoriesOption) }
    var includeLorebooks by remember { mutableStateOf(showLorebooksOption) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                if (showMemoriesOption) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeMemories = !includeMemories }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeMemories,
                            onCheckedChange = { includeMemories = it }
                        )
                        Text("Include Memories", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                
                if (showLorebooksOption) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeLorebooks = !includeLorebooks }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeLorebooks,
                            onCheckedChange = { includeLorebooks = it }
                        )
                        Text("Include Linked Lorebooks", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                
                Text(
                    "Character settings and profile are always included.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(includeMemories, includeLorebooks) }
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
