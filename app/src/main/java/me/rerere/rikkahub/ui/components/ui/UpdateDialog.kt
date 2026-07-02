package me.rerere.rikkahub.ui.components.ui

import android.app.DownloadManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.flowOf
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.DownloadProgress
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UpdateInfo

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    updateChecker: UpdateChecker,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    var downloadId by remember(info.version) { mutableStateOf<Long?>(null) }
    val progress by remember(downloadId) {
        val id = downloadId
        if (id != null && id != -1L) {
            updateChecker.observeDownload(context, id)
        } else {
            flowOf(DownloadProgress())
        }
    }.collectAsState(initial = DownloadProgress())

    val primaryDownload = info.downloads.firstOrNull()
    val isDownloading = progress.status == DownloadManager.STATUS_RUNNING ||
        progress.status == DownloadManager.STATUS_PENDING ||
        progress.status == DownloadManager.STATUS_PAUSED
    val isDownloaded = progress.status == DownloadManager.STATUS_SUCCESSFUL
    val downloadFailed = progress.status == DownloadManager.STATUS_FAILED
    val needsInstallPermission = isDownloaded && !updateChecker.canInstallDownloadedUpdate(context)
    val buttonShape = AppShapes.ButtonPill

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AppShapes.CardLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = when {
                        needsInstallPermission -> "Allow installs to continue"
                        isDownloaded -> "Ready to install"
                        else -> "Update available"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                AnimatedContent(
                    targetState = Triple(progress.status, needsInstallPermission, downloadFailed),
                    label = "update_dialog_status"
                ) { (status, permissionNeeded, failed) ->
                    Text(
                        text = when {
                            permissionNeeded -> "Enable app installs for LastChat, then come back and continue."
                            failed -> "The download failed. You can try again."
                            status == DownloadManager.STATUS_RUNNING -> "Downloading update..."
                            status == DownloadManager.STATUS_PENDING -> "Waiting to download..."
                            status == DownloadManager.STATUS_PAUSED -> "Download paused."
                            status == DownloadManager.STATUS_SUCCESSFUL -> "The update is downloaded and ready."
                            else -> "A new version is ready to download."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = AppShapes.CardMedium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = info.version,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (primaryDownload != null) {
                            Text(
                                text = "${primaryDownload.size} - ${primaryDownload.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (downloadId != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Text(
                            text = if (isDownloaded) {
                                "Download complete"
                            } else {
                                "${(progress.progress * 100).toInt().coerceIn(0, 100)}%"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Release notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(AppShapes.CardMedium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    MarkdownBlock(
                        content = info.changelog.ifBlank { "No release notes provided." }
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isDownloading) {
                        Button(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                when {
                                    isDownloaded && progress.localUri != null -> {
                                        updateChecker.installDownloadedUpdate(context, progress.localUri!!)
                                    }
                                    primaryDownload != null -> {
                                        downloadId = updateChecker.downloadUpdate(context, primaryDownload)
                                    }
                                }
                            },
                            enabled = (isDownloaded && progress.localUri != null) || primaryDownload != null,
                            shape = buttonShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = when {
                                    isDownloaded && needsInstallPermission -> "Continue"
                                    isDownloaded -> "Install"
                                    downloadFailed -> "Try again"
                                    else -> "Update"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!isDownloading && !isDownloaded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    onIgnore()
                                },
                                shape = buttonShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Ignore")
                            }
                            OutlinedButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    onLater()
                                },
                                shape = buttonShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Later")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                onLater()
                            },
                            shape = buttonShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Later")
                        }
                    }
                }
            }
        }
    }
}
