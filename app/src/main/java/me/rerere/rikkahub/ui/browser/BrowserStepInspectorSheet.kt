package me.rerere.rikkahub.ui.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.browser.BrowserFrames
import me.rerere.rikkahub.data.browser.BrowserTabPool
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatSheetContainerColor
import me.rerere.rikkahub.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserStepInspectorSheet(
    steps: List<BrowserStepRecord>,
    initialIndex: Int,
    tabPool: BrowserTabPool,
    liveSnapshotIntervalMs: Long,
    onOpenLiveBrowser: (url: String?, tabId: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (steps.isEmpty()) {
        onDismiss()
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberPremiumHaptics()
    var index by remember(steps.size, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, steps.lastIndex))
    }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    var zoomImagePath by remember { mutableStateOf<String?>(null) }

    val fallbackPath = remember(step.toolCallId, steps) {
        if (step.imageFilePath != null) null
        else (index - 1 downTo 0).firstNotNullOfOrNull { i -> steps[i].imageFilePath }
    }
    val liveBitmap = rememberBrowserLiveSnapshot(step, tabPool, liveSnapshotIntervalMs)
    val savedBitmap by produceState<Bitmap?>(
        initialValue = null,
        step.imageFilePath,
        fallbackPath,
    ) {
        val path = step.imageFilePath ?: fallbackPath
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    val bitmap = liveBitmap ?: BrowserFrames.get(step.toolCallId) ?: savedBitmap

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lastChatSheetContainerColor(),
        shape = AppShapes.BottomSheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar: status + title + open live browser globe
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    step.isRunning -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    step.isSuccess -> Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.toolTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(step.actionName)
                            step.tabId?.let { append(" · tab $it") }
                            step.pageUrl?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onOpenLiveBrowser(step.pageUrl, step.tabId)
                    },
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = "Open live browser",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Screenshot preview
            val previewShape = RoundedCornerShape(14.dp)
            val zoomableFilePath = step.imageFilePath ?: fallbackPath
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 270.dp)
                    .clip(previewShape)
                    .background(Color(0xFF151519))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), previewShape)
                    .clickable(enabled = zoomableFilePath != null) {
                        zoomImagePath = zoomableFilePath
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Browser screenshot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "No screenshot for this step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Structured Result box
            Surface(
                shape = AppShapes.CardMedium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Result",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = step.resultText.ifBlank { if (step.isRunning) "Running…" else "(no output)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (step.argumentsJson.isNotBlank()) {
                            Text(
                                text = "Arguments",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = step.argumentsJson,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // |< X / Y >| scrubber
            if (steps.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { haptics.perform(HapticPattern.Selection); index = 0 },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Rounded.FirstPage, contentDescription = "First")
                    }
                    IconButton(
                        onClick = { haptics.perform(HapticPattern.Selection); if (index > 0) index-- },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${index + 1} / ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { haptics.perform(HapticPattern.Selection); if (index < steps.lastIndex) index++ },
                        enabled = index < steps.lastIndex,
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next")
                    }
                    IconButton(
                        onClick = { haptics.perform(HapticPattern.Selection); index = steps.lastIndex },
                        enabled = index < steps.lastIndex,
                    ) {
                        Icon(Icons.Rounded.LastPage, contentDescription = "Last")
                    }
                }
            }
        }
    }

    zoomImagePath?.let { path ->
        ImagePreviewDialog(
            images = listOf("file://$path"),
            onDismissRequest = { zoomImagePath = null },
        )
    }
}
