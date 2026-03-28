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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R

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
        title = { Text(stringResource(R.string.assistant_importer_import_character)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (missingModels.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.assistant_importer_missing_models_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.assistant_importer_missing_models_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    missingModels.forEach { name ->
                        Text(
                            text = "- $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.assistant_importer_missing_models_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                }

                if (hasMemories || hasLorebooks) {
                    Text(
                        text = stringResource(R.string.assistant_importer_include_components),
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
                                Text(stringResource(R.string.assistant_importer_lorebooks))
                                Text(
                                    stringResource(R.string.assistant_importer_lorebooks_desc),
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
                                Text(stringResource(R.string.assistant_importer_memories))
                                Text(
                                    stringResource(R.string.assistant_importer_memories_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (missingModels.isEmpty()) {
                     Text(stringResource(R.string.assistant_importer_ready))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(importMemories, importLorebooks)
                }
            ) {
                Text(stringResource(R.string.import_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
