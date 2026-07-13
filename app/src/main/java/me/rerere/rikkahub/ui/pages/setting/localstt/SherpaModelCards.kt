package me.rerere.rikkahub.ui.pages.setting.localstt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.asr.local.InstalledSherpaModel
import me.rerere.asr.local.SherpaDownload
import me.rerere.asr.local.SherpaModelConfig
import me.rerere.asr.local.SherpaModelFamily
import me.rerere.asr.local.SherpaModelMetadata
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.roundToInt

@Composable
fun SherpaInstalledModelCard(
    model: InstalledSherpaModel,
    shape: Shape,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkMode.current
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        formatBytes(model.sizeInBytes),
                        if (model.streaming) "Live transcription" else "Speech to text",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dragHandle()
        }
    }
}

@Composable
fun SherpaModelDragHandle(modifier: Modifier = Modifier) {
    IconButton(onClick = {}, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = "Reorder model",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SherpaDownloadableModelCard(
    model: SherpaModelMetadata,
    download: SherpaDownload?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkMode.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = listOf(
                            formatBytes(model.archiveSizeBytes),
                            if (model.streaming) "Live transcription" else "Speech to text",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (download == null || download is SherpaDownload.Failed) {
                    Button(onClick = onDownload) {
                        Text(if (download is SherpaDownload.Failed) "Retry" else "Download")
                    }
                }
            }
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SherpaDownloadStatus(
                state = download,
                onCancel = onCancel,
                onDismissFailure = onDismissFailure,
            )
        }
    }
}

@Composable
private fun SherpaDownloadStatus(
    state: SherpaDownload?,
    onCancel: () -> Unit,
    onDismissFailure: () -> Unit,
) {
    when (state) {
        null -> Unit
        is SherpaDownload.Running -> {
            LinearProgressIndicator(
                progress = { state.progress.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Downloading ${formatBytes(state.progress.bytesDownloaded)} of ${formatBytes(state.progress.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }

        is SherpaDownload.Failed -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onDismissFailure) { Text("Dismiss") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SherpaModelSettingsSheet(
    model: InstalledSherpaModel,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onConfigChange: (SherpaModelConfig) -> Unit,
    onDelete: () -> Unit,
) {
    var displayName by remember(model.id) { mutableStateOf(model.displayName) }
    var config by remember(model.id) { mutableStateOf(model.config) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun saveAndDismiss() {
        val trimmedName = displayName.trim()
        if (trimmedName.isNotEmpty() && trimmedName != model.displayName) {
            onRename(trimmedName)
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::saveAndDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Model settings", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = if (model.streaming) "This model can publish live partial transcripts while you speak." else "This model transcribes completed speech segments.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = config.language,
                onValueChange = {
                    config = config.copy(language = it)
                    onConfigChange(config)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Language") },
                supportingText = { Text("Use auto for language detection when the model supports it.") },
                singleLine = true,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CPU threads: ${config.numThreads}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = config.numThreads.toFloat(),
                    onValueChange = {
                        config = config.copy(numThreads = it.roundToInt().coerceIn(1, 8))
                        onConfigChange(config)
                    },
                    valueRange = 1f..8f,
                    steps = 6,
                )
            }
            if (model.family == SherpaModelFamily.SENSE_VOICE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Format punctuation and numbers", style = MaterialTheme.typography.bodyLarge)
                    HapticSwitch(
                        checked = config.useInverseTextNormalization,
                        onCheckedChange = {
                            config = config.copy(useInverseTextNormalization = it)
                            onConfigChange(config)
                        },
                    )
                }
            }
            HorizontalDivider()
            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
            ) {
                Text("Delete downloaded model", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete ${model.displayName}?") },
            text = { Text("The downloaded model files will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> "%.1f GB".format(mb / 1024.0)
        else -> "%.0f MB".format(mb)
    }
}
