package me.rerere.rikkahub.ui.components.ui

import android.content.Intent
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.ResolvedLocalFile
import me.rerere.rikkahub.utils.canPreviewInApp
import me.rerere.rikkahub.utils.formatFileSize
import me.rerere.rikkahub.utils.isImage
import me.rerere.rikkahub.utils.isTextPreviewable
import me.rerere.rikkahub.utils.openAttachmentUri
import me.rerere.rikkahub.utils.openOwnedUriInputStream
import me.rerere.rikkahub.utils.saveToDownloads

private const val TAG = "FileActionSheet"
private const val MAX_TEXT_PREVIEW_BYTES = 512 * 1024

/** Request opening the shared file action sheet for a resolved local file. */
val LocalRequestFileAction = compositionLocalOf<(ResolvedLocalFile) -> Unit> { {} }

@Composable
fun FileActionBottomSheet(
    file: ResolvedLocalFile,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val sheetContentColor = MaterialTheme.colorScheme.onSurface
    val sheetSupportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val optionContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest

    var showImagePreview by remember { mutableStateOf(false) }
    var showTextPreview by remember { mutableStateOf(false) }
    var textPreviewContent by remember { mutableStateOf<String?>(null) }
    var textPreviewLoading by remember { mutableStateOf(false) }
    var textPreviewError by remember { mutableStateOf<String?>(null) }

    val canPreview = file.canPreviewInApp()
    val sizeLabel = file.sizeBytes?.let { formatFileSize(it) }
    val mimeLabel = file.mimeType.takeIf { it.isNotBlank() && it != "application/octet-stream" }

    ModalBottomSheet(
        containerColor = sheetContainerColor,
        contentColor = sheetContentColor,
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.file_action_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = sheetContentColor,
                )
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = sheetContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(sizeLabel, mimeLabel).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sheetSupportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canPreview) {
                    FileActionCard(
                        icon = Icons.Rounded.Visibility,
                        title = stringResource(R.string.file_action_preview),
                        description = stringResource(R.string.file_action_preview_desc),
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            if (file.isImage()) {
                                showImagePreview = true
                            } else if (file.isTextPreviewable()) {
                                textPreviewLoading = true
                                textPreviewError = null
                                textPreviewContent = null
                                showTextPreview = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { readTextPreview(context, file) }
                                    }
                                    textPreviewLoading = false
                                    result.onSuccess { textPreviewContent = it }
                                        .onFailure {
                                            Log.e(TAG, "Failed to read text preview", it)
                                            textPreviewError = it.message
                                                ?: context.getString(R.string.file_action_preview_failed)
                                        }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = optionContainerColor,
                        contentColor = sheetContentColor,
                        supportingColor = sheetSupportingColor,
                    )
                }

                FileActionCard(
                    icon = Icons.Rounded.OpenInNew,
                    title = stringResource(R.string.file_action_open),
                    description = stringResource(R.string.file_action_open_desc),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        context.openAttachmentUri(file.uri, file.mimeType)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    containerColor = optionContainerColor,
                    contentColor = sheetContentColor,
                    supportingColor = sheetSupportingColor,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FileActionCard(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.common_share),
                    description = stringResource(R.string.file_action_share_desc),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        runCatching {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = file.mimeType.ifBlank { "application/octet-stream" }
                                putExtra(Intent.EXTRA_STREAM, file.uri)
                                putExtra(Intent.EXTRA_SUBJECT, file.fileName)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, file.fileName))
                        }.onFailure {
                            Log.e(TAG, "Share failed", it)
                        }
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    containerColor = optionContainerColor,
                    contentColor = sheetContentColor,
                    supportingColor = sheetSupportingColor,
                )

                FileActionCard(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.file_action_save_downloads),
                    description = stringResource(R.string.file_action_save_downloads_desc),
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        scope.launch {
                            context.saveToDownloads(file.uri, file.fileName)
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    containerColor = optionContainerColor,
                    contentColor = sheetContentColor,
                    supportingColor = sheetSupportingColor,
                )
            }
        }
    }

    if (showImagePreview) {
        val imageModel = file.localFile?.let { "file://${it.absolutePath}" }
            ?: file.uri.toString()
        ImagePreviewDialog(
            images = listOf(imageModel),
            onDismissRequest = {
                showImagePreview = false
                onDismissRequest()
            },
        )
    }

    if (showTextPreview) {
        TextFilePreviewDialog(
            fileName = file.fileName,
            loading = textPreviewLoading,
            content = textPreviewContent,
            error = textPreviewError,
            onDismissRequest = {
                showTextPreview = false
                onDismissRequest()
            },
            onDownload = {
                scope.launch {
                    context.saveToDownloads(file.uri, file.fileName)
                }
            },
            onShare = {
                runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = file.mimeType.ifBlank { "text/plain" }
                        putExtra(Intent.EXTRA_STREAM, file.uri)
                        putExtra(Intent.EXTRA_SUBJECT, file.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, file.fileName))
                }
            },
        )
    }
}

@Composable
private fun FileActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "file_action_card_scale",
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = supportingColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TextFilePreviewDialog(
    fileName: String,
    loading: Boolean,
    content: String?,
    error: String?,
    onDismissRequest: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = AppShapes.CardLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = fileName,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.common_share),
                        )
                    }
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.download),
                        )
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.a11y_close))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(16.dp),
                ) {
                    when {
                        loading -> {
                            Text(
                                text = stringResource(R.string.file_action_preview_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        error != null -> {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        content != null -> {
                            SelectionContainer {
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readTextPreview(
    context: android.content.Context,
    file: ResolvedLocalFile,
): String {
    val local = file.localFile
    if (local != null && local.isFile) {
        val bytes = local.length().coerceAtMost(MAX_TEXT_PREVIEW_BYTES.toLong()).toInt()
        val raw = local.inputStream().use { it.readNBytes(bytes) }
        val text = String(raw, Charsets.UTF_8)
        return if (local.length() > MAX_TEXT_PREVIEW_BYTES) {
            text + "\n\n… (truncated)"
        } else {
            text
        }
    }
    val stream = context.openOwnedUriInputStream(file.uri)
        ?: error("Unable to open file for preview")
    return stream.use { input ->
        val raw = input.readNBytes(MAX_TEXT_PREVIEW_BYTES)
        val text = String(raw, Charsets.UTF_8)
        if (raw.size >= MAX_TEXT_PREVIEW_BYTES) text + "\n\n… (truncated)" else text
    }
}

/**
 * Hosts [FileActionBottomSheet] state and provides [LocalRequestFileAction] to [content].
 */
@Composable
fun FileActionHost(content: @Composable () -> Unit) {
    var pending by remember { mutableStateOf<ResolvedLocalFile?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalRequestFileAction provides { pending = it },
    ) {
        content()
    }
    pending?.let { file ->
        FileActionBottomSheet(
            file = file,
            onDismissRequest = { pending = null },
        )
    }
}
