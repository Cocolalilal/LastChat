package me.rerere.rikkahub.ui.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
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
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import org.json.JSONObject
import java.net.URI

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

private fun extractShortHost(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        URI(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

@Composable
fun rememberBrowserLiveSnapshot(
    step: BrowserStepRecord,
    tabPool: BrowserTabPool,
    intervalMs: Long,
    paused: Boolean = false,
): Bitmap? {
    var liveBitmap by remember(step.toolCallId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(step.toolCallId, step.isRunning, step.tabId, intervalMs, paused) {
        if (!step.isRunning || intervalMs <= 0L || paused) return@LaunchedEffect
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
    conversationId: String = "",
    hasUserMessageAfterSteps: Boolean = false,
    isChatScrolling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    val agentDismissVersion by tabPool.barDismissedByAgentVersion.collectAsState()
    val dismissedMap by tabPool.dismissedBarStepCounts.collectAsState()
    val latestLiveFrame by tabPool.latestFrame.collectAsState()

    val persistedDismissCount = if (conversationId.isNotBlank()) {
        dismissedMap[conversationId] ?: -1
    } else {
        -1
    }

    // Persist agent-triggered close_browser dismissal across navigation too
    var lastSeenAgentDismissVersion by remember(conversationId) { mutableIntStateOf(agentDismissVersion) }
    LaunchedEffect(agentDismissVersion, steps.size, conversationId) {
        if (agentDismissVersion != lastSeenAgentDismissVersion) {
            lastSeenAgentDismissVersion = agentDismissVersion
            if (conversationId.isNotBlank() && browserSettings.barDismissMode == BrowserBarDismissMode.AUTO_AGENT) {
                tabPool.dismissPreviewBar(conversationId, steps.size)
            }
        }
    }

    val isManuallyDismissed = persistedDismissCount >= 0 && steps.size <= persistedDismissCount
    val isAgentDismissed = browserSettings.barDismissMode == BrowserBarDismissMode.AUTO_AGENT &&
        steps.lastOrNull()?.let { it.actionName == "close_browser" && !it.isRunning } == true
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
        enter = fadeIn(tween(180)) + expandVertically(tween(200)),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(160)),
        modifier = modifier,
    ) {
        if (steps.isEmpty()) return@AnimatedVisibility
        var currentIndex by remember(conversationId) { mutableIntStateOf(steps.lastIndex.coerceAtLeast(0)) }
        var userPinnedBack by remember(conversationId) { mutableStateOf(false) }
        val lastIndex = steps.lastIndex
        LaunchedEffect(steps.size) {
            if (!userPinnedBack || currentIndex > lastIndex) {
                currentIndex = lastIndex.coerceAtLeast(0)
            }
        }
        val safeIdx = currentIndex.coerceIn(0, lastIndex)
        val step = steps[safeIdx]
        val isOnLatestStep = safeIdx == lastIndex

        val fallbackImagePath = remember(step.toolCallId, steps.size) {
            if (step.imageFilePath != null) null
            else (safeIdx - 1 downTo 0).firstNotNullOfOrNull { i -> steps[i].imageFilePath }
        }

        // Pause periodic polling while user is actively scrolling the chat list
        val polledBitmap = rememberBrowserLiveSnapshot(
            step = step,
            tabPool = tabPool,
            intervalMs = browserSettings.liveSnapshotIntervalMs,
            paused = isChatScrolling,
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

        // Reactive live frame pushed immediately by BrowserUseManager on page load / action finish
        val pushedBitmap = latestLiveFrame
            ?.takeIf { !it.bitmap.isRecycled && isOnLatestStep && (step.tabId == null || it.tabId == step.tabId) }
            ?.bitmap

        val candidateBitmap = if (step.isRunning) {
            pushedBitmap ?: polledBitmap ?: BrowserFrames.get(step.toolCallId) ?: savedBitmap
        } else {
            (if (step.imageFilePath != null) savedBitmap else null)
                ?: pushedBitmap
                ?: BrowserFrames.get(step.toolCallId)
                ?: savedBitmap
                ?: polledBitmap
        }

        // Keep last valid bitmap during async decode so thumbnail never flickers to blank/globe
        var lastValidBitmap by remember(conversationId) { mutableStateOf<Bitmap?>(null) }
        if (candidateBitmap != null && !candidateBitmap.isRecycled) {
            lastValidBitmap = candidateBitmap
        }
        val displayBitmap = candidateBitmap?.takeIf { !it.isRecycled } ?: lastValidBitmap?.takeIf { !it.isRecycled }

        val (thumbnailWidth, thumbnailHeight) = when (browserSettings.miniPreviewSize) {
            BrowserPreviewSize.COMPACT -> 100.dp to 65.dp
            BrowserPreviewSize.MEDIUM -> 136.dp to 88.dp
            BrowserPreviewSize.LARGE -> 176.dp to 114.dp
        }
        val barHeight = 40.dp
        val overhang = (thumbnailHeight - barHeight).coerceAtLeast(0.dp)
        val thumbnailInset = 10.dp
        val barStartPadding = thumbnailWidth + thumbnailInset + 8.dp
        // Crisp elevated surface WITHOUT live Haze blur pass so chat scrolling stays 60fps on 4GB tablets
        val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) 0.985f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
            label = "browser_mini_preview_scale",
        )

        val shortHost = remember(step.pageUrl, latestLiveFrame?.url, isOnLatestStep) {
            extractShortHost(if (isOnLatestStep && step.isRunning) (latestLiveFrame?.url ?: step.pageUrl) else step.pageUrl)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = overhang)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        ) {
            // Layer 1: 40dp status bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = surfaceColor,
                border = lastChatSoftEdgeBorder(surfaceColor),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .align(Alignment.BottomCenter),
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
                                if (conversationId.isNotBlank()) {
                                    tabPool.dismissPreviewBar(conversationId, steps.size)
                                }
                            },
                    )
                }
            }

            // Layer 2: Overhanging top-framed thumbnail on the left with smooth Crossfade & live badge
            val thumbShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = thumbnailInset)
                    .size(width = thumbnailWidth, height = thumbnailHeight)
                    .shadow(6.dp, thumbShape)
                    .clip(thumbShape)
                    .background(Color(0xFF1A1A1E))
                    .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape)
                    .clickable {
                        haptics.perform(HapticPattern.Pop)
                        onInspectStep(safeIdx)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = displayBitmap,
                    animationSpec = tween(160),
                    label = "browser_thumb_crossfade",
                    modifier = Modifier.fillMaxSize(),
                ) { bmp ->
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Browser step preview",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Subtle host caption at bottom of Medium / Large thumbnails
                if (browserSettings.miniPreviewSize != BrowserPreviewSize.COMPACT && !shortHost.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = shortHost,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Live pulsing dot badge while agent is actively running
                if (step.isRunning) {
                    val pulseTransition = rememberInfiniteTransition(label = "live_thumb_pulse")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                        label = "live_thumb_pulse_alpha",
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(8.dp)
                            .background(Color(0xFF34C759).copy(alpha = pulseAlpha), CircleShape)
                            .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }
        }
    }
}
