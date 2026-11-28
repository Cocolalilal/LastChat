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
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.browser.BrowserFrames
import me.rerere.rikkahub.data.browser.BrowserHistoryStore
import me.rerere.rikkahub.data.browser.BrowserTabPool
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatSheetContainerColor
import me.rerere.rikkahub.ui.theme.AppShapes
import java.io.File

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberPremiumHaptics()
    var index by remember(steps.size, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, steps.lastIndex))
    }
    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    val fallbackPath = remember(step.toolCallId, steps.size) {
        if (step.imageFilePath != null) null
        else (safeIndex - 1 downTo 0).firstNotNullOfOrNull { i -> steps[i].imageFilePath }
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
    val cachedFrame = BrowserFrames.get(step.toolCallId)
    // Prefer sharp on-disk full-res savedBitmap for completed steps; only prefer liveBitmap while running.
    val bitmap = if (step.isRunning) {
        liveBitmap ?: savedBitmap ?: cachedFrame
    } else {
        savedBitmap ?: cachedFrame ?: liveBitmap
    }
    val isDisplayingExactFile = !step.isRunning && savedBitmap != null && (step.imageFilePath != null || cachedFrame == null)

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
            // Top bar: status + title + history + open live browser globe
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when {
                    step.isRunning -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    step.isSuccess -> Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
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
                        showHistory = true
                    },
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = "Browsing history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 270.dp)
                    .clip(previewShape)
                    .background(Color(0xFF151519))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), previewShape)
                    .clickable(enabled = bitmap != null) {
                        val currentBmp = bitmap ?: return@clickable
                        val directPath = step.imageFilePath ?: (if (isDisplayingExactFile) fallbackPath else null)
                        if (directPath != null) {
                            zoomImagePath = directPath
                        } else {
                            scope.launch {
                                val tempPath = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val outFile = File(context.cacheDir, "browser_zoom_${step.toolCallId.hashCode()}.jpg")
                                        outFile.outputStream().use { out ->
                                            currentBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        }
                                        outFile.absolutePath
                                    }.getOrNull()
                                }
                                if (tempPath != null) zoomImagePath = tempPath
                            }
                        }
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
                        enabled = safeIndex > 0,
                    ) {
                        Icon(Icons.Rounded.FirstPage, contentDescription = "First")
                    }
                    IconButton(
                        onClick = { haptics.perform(HapticPattern.Selection); if (safeIndex > 0) index = safeIndex - 1 },
                        enabled = safeIndex > 0,
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${safeIndex + 1} / ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { haptics.perform(HapticPattern.Selection); if (safeIndex < steps.lastIndex) index = safeIndex + 1 },
                        enabled = safeIndex < steps.lastIndex,
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next")
                    }
                    IconButton(
                        onClick = { haptics.perform(HapticPattern.Selection); index = steps.lastIndex },
                        enabled = safeIndex < steps.lastIndex,
                    ) {
                        Icon(Icons.Rounded.LastPage, contentDescription = "Last")
                    }
                }
            }
        }
    }

    if (showHistory) {
        BrowserHistorySheet(
            historyStore = BrowserHistoryStore.getInstance(context),
            onNavigate = { url ->
                showHistory = false
                onOpenLiveBrowser(url, step.tabId)
            },
            onDismiss = { showHistory = false },
        )
    }

    zoomImagePath?.let { path ->
        ImagePreviewDialog(
            images = listOf("file://$path"),
            onDismissRequest = { zoomImagePath = null },
        )
    }
}
