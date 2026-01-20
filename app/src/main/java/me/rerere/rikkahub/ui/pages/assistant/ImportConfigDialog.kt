package me.rerere.rikkahub.ui.pages.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
fun ImportConfigDialog(
    onDismissRequest: () -> Unit,
    hasMemories: Boolean,
    hasLorebooks: Boolean,
    missingModels: List<String>,
    onConfirm: (importMemories: Boolean, importLorebooks: Boolean) -> Unit
) {
    var importMemories by remember { mutableStateOf(hasMemories) }
    var importLorebooks by remember { mutableStateOf(hasLorebooks) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Import Character") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (missingModels.isNotEmpty()) {
                    Text(
                        text = "Missing Models",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "This character uses the following models which are not configured in your settings. They will be unselected:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    missingModels.forEach { name ->
                        Text(
                            text = "• $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Text(
                        text = "You can re-configure them after import.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                }

                if (hasMemories || hasLorebooks) {
                    Text(
                        text = "Include Components",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (hasLorebooks) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Checkbox(
                                checked = importLorebooks,
                                onCheckedChange = { importLorebooks = it }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("Lorebooks")
                                Text(
                                    "Import and link associated lorebooks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (hasMemories) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = importMemories,
                                onCheckedChange = { importMemories = it }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("Memories")
                                Text(
                                    "Import core and episodic memories",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (missingModels.isEmpty()) {
                     Text("Ready to import this character.")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(importMemories, importLorebooks)
                }
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
