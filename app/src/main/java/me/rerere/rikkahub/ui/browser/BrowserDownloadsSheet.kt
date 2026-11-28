package me.rerere.rikkahub.ui.browser

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import me.rerere.rikkahub.data.browser.BrowserTabPool
import me.rerere.rikkahub.ui.modifier.lastChatSheetContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserDownloadsSheet(
    tabPool: BrowserTabPool,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloads by tabPool.downloads.collectAsState()

    LaunchedEffect(Unit) { tabPool.markDownloadsSeen() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lastChatSheetContainerColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (downloads.any { it.state != BrowserTabPool.DownloadState.DOWNLOADING }) {
                    TextButton(onClick = { tabPool.clearFinishedDownloads() }) {
                        Text("Clear")
                    }
                }
            }
            if (downloads.isEmpty()) {
                Text(
                    "No downloads yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn {
                    items(downloads, key = { it.id }) { entry ->
                        DownloadRow(
                            entry = entry,
                            onCancel = { tabPool.cancelDownload(entry.id) },
                            onOpen = { openDownloadedFile(context, entry) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: BrowserTabPool.DownloadEntry,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        val (icon, tint) = when (entry.state) {
            BrowserTabPool.DownloadState.DOWNLOADING ->
                Icons.Rounded.Download to MaterialTheme.colorScheme.primary
            BrowserTabPool.DownloadState.COMPLETED ->
                Icons.Rounded.CheckCircle to Color(0xFF34C759)
            BrowserTabPool.DownloadState.FAILED ->
                Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                BrowserTabPool.middleTruncated(entry.filename, head = 24, tail = 12),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            when (entry.state) {
                BrowserTabPool.DownloadState.DOWNLOADING -> {
                    val doneText = Formatter.formatShortFileSize(context, entry.bytesDone)
                    val statusText = if (entry.totalBytes > 0) {
                        val totalText = Formatter.formatShortFileSize(context, entry.totalBytes)
                        val pct = (entry.bytesDone * 100 / entry.totalBytes).toInt()
                        "$pct% · $doneText / $totalText"
                    } else {
                        doneText
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (entry.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (entry.bytesDone.toFloat() / entry.totalBytes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                BrowserTabPool.DownloadState.COMPLETED -> {
                    Text(
                        Formatter.formatShortFileSize(context, entry.bytesDone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BrowserTabPool.DownloadState.FAILED -> {
                    Text(
                        entry.failureReason ?: "Download failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        when (entry.state) {
            BrowserTabPool.DownloadState.DOWNLOADING -> {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Rounded.Cancel,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BrowserTabPool.DownloadState.COMPLETED -> {
                IconButton(onClick = onOpen) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            BrowserTabPool.DownloadState.FAILED -> Unit
        }
    }
}

private fun openDownloadedFile(context: Context, entry: BrowserTabPool.DownloadEntry) {
    val file = entry.destination ?: return
    if (!file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: Exception) {
        Log.w("BrowserDownloads", "open failed: ${e.message}")
    }
}
