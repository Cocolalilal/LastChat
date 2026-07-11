package me.rerere.rikkahub.ui.pages.setting.localstt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.asr.local.InstalledSherpaModel
import me.rerere.asr.local.SherpaDownload
import me.rerere.asr.local.SherpaModelConfig
import me.rerere.asr.local.SherpaModelFamily
import me.rerere.asr.local.SherpaModelMetadata
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.theme.AppShapes

@Composable
internal fun SherpaInstalledModelCard(
    model: InstalledSherpaModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append(if (model.streaming) "Streaming" else "On-device")
                            append(" • ")
                            append(formatBytes(model.sizeInBytes))
                            if (model.languages.isNotEmpty()) append(" • ${model.languages.joinToString()}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Model settings") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete model") }
            }
            if (selected) {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Text("Selected", modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) { Text("Use for speech input") }
            }
        }
    }
}

@Composable
internal fun SherpaDownloadableModelCard(
    model: SherpaModelMetadata,
    download: SherpaDownload?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
) {
    Card(shape = AppShapes.CardMedium, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(model.name, style = MaterialTheme.typography.titleMedium)
            Text(
                model.description,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${if (model.streaming) "Streaming" else "Batch"} • ${formatBytes(model.archiveSizeBytes)} download • ${model.languages.joinToString()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (download) {
                is SherpaDownload.Running -> {
                    LinearProgressIndicator(
                        progress = { download.progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${download.progress.percent}%", modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                            Text("Cancel")
                        }
                    }
                }

                is SherpaDownload.Failed -> {
                    Text(download.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismissError, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                        Button(onClick = onDownload, modifier = Modifier.weight(1f)) { Text("Retry") }
                    }
                }

                null -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                    Text("Download", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
internal fun SherpaModelConfigDialog(
    model: InstalledSherpaModel,
    onDismiss: () -> Unit,
    onSave: (SherpaModelConfig) -> Unit,
) {
    var config by remember(model.id) { mutableStateOf(model.config) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speech model settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.language,
                    onValueChange = { config = config.copy(language = it.trim()) },
                    label = { Text("Language code (blank = auto)") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("CPU threads: ${config.numThreads}")
                Slider(
                    value = config.numThreads.toFloat(),
                    onValueChange = { config = config.copy(numThreads = it.toInt()) },
                    valueRange = 1f..8f,
                    steps = 6,
                )
                if (model.family == SherpaModelFamily.SENSE_VOICE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Inverse text normalization", modifier = Modifier.weight(1f))
                        HapticSwitch(
                            checked = config.useInverseTextNormalization,
                            onCheckedChange = { config = config.copy(useInverseTextNormalization = it) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(config) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "${bytes / 1_000} KB"
}
