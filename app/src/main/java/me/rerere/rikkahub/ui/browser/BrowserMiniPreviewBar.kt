package me.rerere.rikkahub.ui.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.browser.BrowserFrames
import me.rerere.rikkahub.data.browser.BrowserTabPool
import me.rerere.rikkahub.data.datastore.BrowserBarDismissMode
import me.rerere.rikkahub.data.datastore.BrowserPreviewSize
import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatFloatingSurfaceColor
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import org.json.JSONObject

data class BrowserStepRecord(
    val toolCallId: String,
    val toolTitle: String,
    val actionName: String,
    val argumentsJson: String,
    val resultText: String,
    val imageFilePath: String?,
    val pageUrl: String?,
    val tabId: Int?,
    val isRunning: Boolean,
    val isSuccess: Boolean,
    val messageIndex: Int = 0,
)

private val parsedStepCache = LruCache<String, BrowserStepRecord>(256)

/**
 * Extracts ONLY `browser_use` steps from the conversation's messages.
 * Memoizes completed steps so streaming tokens do not re-parse JSON on every frame.
 */
fun collectBrowserSteps(messages: List<UIMessage>, isGenerating: Boolean): List<BrowserStepRecord> {
    val resultsByCallId = LinkedHashMap<String, UIMessagePart.ToolResult>()
    messages.forEach { msg ->
        msg.parts.forEach { part ->
            if (part is UIMessagePart.ToolResult && part.toolName == "browser_use") {
                resultsByCallId[part.toolCallId] = part
            }
        }
    }

    val steps = mutableListOf<BrowserStepRecord>()
    messages.forEachIndexed { msgIdx, msg ->
        msg.parts.forEach { part ->
            if (part is UIMessagePart.ToolCall && part.toolName == "browser_use") {
                val result = resultsByCallId[part.toolCallId]
                val running = result == null && isGenerating
                val cacheKey = "${part.toolCallId}|${part.arguments.hashCode()}|${result != null}|$running|$msgIdx"
                val cached = parsedStepCache.get(cacheKey)
                if (cached != null) {
                    steps += cached
                    return@forEach
                }
                val argsObj = runCatching { JSONObject(part.arguments) }.getOrNull()
                val actionName = argsObj?.optString("action").orEmpty().ifBlank { "browser_use" }
                val title = argsObj?.optString("tool_title").orEmpty().ifBlank {
                    actionName.replace('_', ' ').replaceFirstChar { it.uppercase() }
                }
                val contentObj = result?.content as? JsonObject
                val text = contentObj?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: contentObj?.get("error")?.jsonPrimitive?.contentOrNull
                    ?: result?.content?.toString().orEmpty()
                val imagePath = contentObj?.get("image_file_path")?.jsonPrimitive?.contentOrNull
                    ?: result?.inspectedImages?.firstOrNull()?.sourceUrl
                val pageUrl = contentObj?.get("page_url")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.optString("url")?.takeIf { it.isNotBlank() }
                val tabId = contentObj?.get("tab_id")?.jsonPrimitive?.intOrNull
                val success = contentObj?.get("success")?.jsonPrimitive?.booleanOrNull ?: (result != null)
                val record = BrowserStepRecord(
                    toolCallId = part.toolCallId,
                    toolTitle = title,
                    actionName = actionName,
                    argumentsJson = part.arguments,
                    resultText = text,
                    imageFilePath = imagePath,
                    pageUrl = pageUrl,
                    tabId = tabId,
                    isRunning = running,
                    isSuccess = success,
                    messageIndex = msgIdx,
                )
                if (!running) {
                    parsedStepCache.put(cacheKey, record)
                }
                steps += record
            }
        }
    }
    return steps
}

fun hasUserMessageAfterBrowserSteps(messages: List<UIMessage>, steps: List<BrowserStepRecord>): Boolean {
    val lastStepMsgIdx = steps.lastOrNull()?.messageIndex ?: return false
    for (i in (lastStepMsgIdx + 1) until messages.size) {
        if (messages[i].role == MessageRole.USER) return true
    }
    return false
}

@Composable
fun rememberBrowserLiveSnapshot(
    step: BrowserStepRecord,
    tabPool: BrowserTabPool,
    intervalMs: Long,
): Bitmap? {
    var liveBitmap by remember(step.toolCallId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(step.toolCallId, step.isRunning, step.tabId, intervalMs) {
        if (!step.isRunning || intervalMs <= 0L) return@LaunchedEffect
        val pollMs = intervalMs.coerceAtLeast(1_500L)
        while (isActive) {
            val manager = tabPool.managerForTab(step.tabId)
            val snap = runCatching { manager?.captureLiveSnapshot(maxWidthPx = 480) }.getOrNull()
            if (snap != null && !snap.isRecycled) {
                BrowserFrames.remember(step.toolCallId, snap)
                liveBitmap = BrowserFrames.get(step.toolCallId) ?: snap
            }
            delay(pollMs)
        }
    }
    return liveBitmap
}

@Composable
fun BrowserMiniPreviewBar(
    steps: List<BrowserStepRecord>,
    browserSettings: BrowserSettings,
    tabPool: BrowserTabPool,
    onInspectStep: (index: Int) -> Unit,
    hasUserMessageAfterSteps: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    val agentDismissVersion by tabPool.barDismissedByAgentVersion.collectAsState()

    // Track manual dismissal (resets automatically whenever a new browser step arrives)
    var dismissedAtStepCount by remember { mutableIntStateOf(-1) }
    var lastSeenAgentDismissVersion by remember { mutableIntStateOf(agentDismissVersion) }
    var dismissedByAgentAtStepCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(agentDismissVersion, steps.size) {
        if (agentDismissVersion != lastSeenAgentDismissVersion) {
            lastSeenAgentDismissVersion = agentDismissVersion
            dismissedByAgentAtStepCount = steps.size
        }
    }

    val isManuallyDismissed = dismissedAtStepCount >= 0 && steps.size <= dismissedAtStepCount
    val isAgentDismissed = browserSettings.barDismissMode == BrowserBarDismissMode.AUTO_AGENT &&
        (dismissedByAgentAtStepCount >= 0 && steps.size <= dismissedByAgentAtStepCount ||
            steps.lastOrNull()?.let { it.actionName == "close_browser" && !it.isRunning } == true)
    val isHiddenByNextUserMsg = browserSettings.barDismissMode == BrowserBarDismissMode.HIDE_ON_NEXT_MESSAGE &&
        hasUserMessageAfterSteps &&
        steps.lastOrNull()?.isRunning != true

    val visible = browserSettings.showFloatingMiniPreviewBar &&
        steps.isNotEmpty() &&
        !isManuallyDismissed &&
        !isAgentDismissed &&
        !isHiddenByNextUserMsg

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        if (steps.isEmpty()) return@AnimatedVisibility
        var currentIndex by remember { mutableIntStateOf(steps.lastIndex.coerceAtLeast(0)) }
        var userPinnedBack by remember { mutableStateOf(false) }
        val lastIndex = steps.lastIndex
        LaunchedEffect(steps.size) {
            if (!userPinnedBack || currentIndex > lastIndex) {
                currentIndex = lastIndex.coerceAtLeast(0)
            }
        }
        val safeIdx = currentIndex.coerceIn(0, lastIndex)
        val step = steps[safeIdx]
        val fallbackImagePath = remember(step.toolCallId, steps.size) {
            if (step.imageFilePath != null) null
            else (safeIdx - 1 downTo 0).firstNotNullOfOrNull { i -> steps[i].imageFilePath }
        }

        val liveBitmap = rememberBrowserLiveSnapshot(
            step = step,
            tabPool = tabPool,
            intervalMs = browserSettings.liveSnapshotIntervalMs,
        )
        val savedBitmap by produceState<Bitmap?>(
            initialValue = null,
            step.imageFilePath,
            fallbackImagePath,
        ) {
            val path = step.imageFilePath ?: fallbackImagePath
            value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(path, opts)
                }.getOrNull()
            }
        }
        val thumbnailBitmap = if (step.isRunning) {
            liveBitmap ?: BrowserFrames.get(step.toolCallId) ?: savedBitmap
        } else {
            savedBitmap ?: BrowserFrames.get(step.toolCallId) ?: liveBitmap
        }

        val (thumbnailWidth, thumbnailHeight) = when (browserSettings.miniPreviewSize) {
            BrowserPreviewSize.COMPACT -> 100.dp to 65.dp
            BrowserPreviewSize.MEDIUM -> 136.dp to 88.dp
            BrowserPreviewSize.LARGE -> 176.dp to 114.dp
        }
        val barHeight = 38.dp
        val overhang = (thumbnailHeight - barHeight).coerceAtLeast(0.dp)
        val thumbnailInset = 10.dp
        val barStartPadding = thumbnailWidth + thumbnailInset + 8.dp
        val surfaceColor = lastChatFloatingSurfaceColor(MaterialTheme.colorScheme.surfaceContainerHigh)

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) 0.985f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
            label = "browser_mini_preview_scale",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = overhang)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        ) {
            // Layer 1: 38dp status bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = surfaceColor,
                border = lastChatSoftEdgeBorder(surfaceColor),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .align(Alignment.BottomCenter)
                    .lastChatBlurEffect(surfaceColor, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = barStartPadding, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 1.6.dp,
                        )
                    } else if (step.isSuccess) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(15.dp),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (!step.isRunning && step.isSuccess) "${step.toolTitle} · Done" else step.toolTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) {
                                haptics.perform(HapticPattern.Pop)
                                onInspectStep(safeIdx)
                            },
                    )
                    if (steps.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ChevronLeft,
                                contentDescription = "Previous step",
                                tint = if (safeIdx > 0) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(enabled = safeIdx > 0) {
                                        haptics.perform(HapticPattern.Selection)
                                        if (safeIdx > 0) {
                                            currentIndex = safeIdx - 1
                                            userPinnedBack = true
                                        }
                                    },
                            )
                            Text(
                                text = "${safeIdx + 1}/${steps.size}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = "Next step",
                                tint = if (safeIdx < lastIndex) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(enabled = safeIdx < lastIndex) {
                                        haptics.perform(HapticPattern.Selection)
                                        if (safeIdx < lastIndex) {
                                            currentIndex = safeIdx + 1
                                            userPinnedBack = currentIndex < lastIndex
                                        }
                                    },
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close browser preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                haptics.perform(HapticPattern.Pop)
                                dismissedAtStepCount = steps.size
                            },
                    )
                }
            }

            // Layer 2: Overhanging thumbnail on the left (Compact / Medium / Large)
            val thumbShape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = thumbnailInset)
                    .size(width = thumbnailWidth, height = thumbnailHeight)
                    .shadow(8.dp, thumbShape)
                    .clip(thumbShape)
                    .background(Color(0xFF1A1A1E))
                    .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape)
                    .clickable {
                        haptics.perform(HapticPattern.Pop)
                        onInspectStep(safeIdx)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Browser step preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
