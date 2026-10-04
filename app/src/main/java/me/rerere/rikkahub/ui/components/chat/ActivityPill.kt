package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as graphicsLerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as dpLerp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.updatePreviewAutoFollowPaused
import me.rerere.rikkahub.ui.modifier.fadeEdges
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalOpticalFrame
import me.rerere.rikkahub.ui.theme.OpticalFrame
sealed interface ActivityState {
    /** Waiting for first token - shows typing dots */
    data object Waiting : ActivityState
    
    /** OCR is preprocessing attachments before generation starts */
    data object Ocr : ActivityState

    /** Model is reasoning/thinking - shows timer */
    data class Reasoning(
        val startTimeMs: Long = System.currentTimeMillis(),
        val title: String? = null,
        val reasoningText: String = ""
    ) : ActivityState

    /** Model is using a tool */
    data class ToolUse(
        val toolName: String,
        val displayName: String,
        val startTimeMs: Long = System.currentTimeMillis()
    ) : ActivityState
    
    /** Local model is being loaded into memory */
    data class LoadingModel(
        val modelName: String? = null
    ) : ActivityState
    
    /** Model is generating text reply */
    data object Replying : ActivityState
    
    /** No activities happened - hide the pill */
    data object Hidden : ActivityState
    
    /** Single activity completed - show expanded pill */
    data class CompletedSingle(
        val type: ActivityType,
        val durationMs: Long? = null,
        val toolName: String? = null,
        val displayName: String? = null,
        val count: Int = 1  // Number of times this activity occurred
    ) : ActivityState
    
    /** Multiple activities completed - show compact pills */
    data class CompletedMultiple(
        val reasoningDurationMs: Long? = null,
        val activityTypes: List<ActivityType> = emptyList()
    ) : ActivityState
}

/**
 * Convert ActivityState to a key for AnimatedContent.
 * Same key = no transition animation.
 * 
 * For ToolUse, we group by tool category (e.g., all web searches share the same key)
 * so consecutive searches don't trigger transitions.
 */
private fun stateToKey(state: ActivityState): Any = when (state) {
    is ActivityState.Waiting -> "waiting"
    is ActivityState.Ocr -> "ocr"
    is ActivityState.Reasoning -> "reasoning"
    is ActivityState.ToolUse -> "tool_${categorizeToolName(state.toolName)}"
    is ActivityState.LoadingModel -> "loading_model"
    is ActivityState.Replying -> "replying"
    is ActivityState.Hidden -> "hidden"
    is ActivityState.CompletedSingle -> "completed_single_${state.type}"
    is ActivityState.CompletedMultiple -> "completed_multi"
}

/**
 * Represents a single activity that happened during the turn.
 */
data class ActivityItem(
    val type: ActivityType,
    val durationMs: Long? = null,  // For reasoning
    val count: Int = 1,            // How many times this happened
    val displayName: String? = null // For tools
)

enum class ActivityType {
    REASONING,
    OCR,
    SEARCH,
    MEMORY_RECALL,
    PYTHON,
    WORKSPACE,
    SKILL,
    MCP,
    LOADING_MODEL,
    TOOL_OTHER
}

private fun ActivityType.toTestTag(): String = when (this) {
    ActivityType.REASONING -> "activity_pill_reasoning"
    ActivityType.OCR -> "activity_pill_ocr"
    ActivityType.SEARCH -> "activity_pill_search"
    ActivityType.MEMORY_RECALL -> "activity_pill_memory_recall"
    ActivityType.PYTHON -> "activity_pill_python"
    ActivityType.WORKSPACE -> "activity_pill_workspace"
    ActivityType.SKILL -> "activity_pill_skill"
    ActivityType.MCP -> "activity_pill_mcp"
    ActivityType.LOADING_MODEL -> "activity_pill_loading_model"
    ActivityType.TOOL_OTHER -> "activity_pill_tool_other"
}

/**
 * Get the icon for an activity type.
 */
private fun ActivityType.getIcon(): ImageVector = when (this) {
    ActivityType.REASONING -> Icons.Rounded.Lightbulb
    ActivityType.OCR -> Icons.Rounded.Image
    ActivityType.SEARCH -> Icons.Rounded.Public
    ActivityType.MEMORY_RECALL -> Icons.Rounded.Memory
    ActivityType.PYTHON -> Icons.Rounded.Terminal
    ActivityType.WORKSPACE -> Icons.Rounded.Computer
    ActivityType.SKILL -> Icons.Rounded.Category
    ActivityType.MCP -> Icons.Rounded.Memory
    ActivityType.LOADING_MODEL -> Icons.Rounded.Memory
    ActivityType.TOOL_OTHER -> Icons.Rounded.Build
}

/**
 * Get display text for an activity type (for expanded single pill).
 */
private fun ActivityType.getDisplayText(): String = when (this) {
    ActivityType.REASONING -> "Reasoned"
    ActivityType.OCR -> "OCR"
    ActivityType.SEARCH -> "Searched"
    ActivityType.MEMORY_RECALL -> "Recalled"
    ActivityType.PYTHON -> "Ran Python"
    ActivityType.WORKSPACE -> "Used workspace"
    ActivityType.SKILL -> "Skills"
    ActivityType.MCP -> "MCP"
    ActivityType.LOADING_MODEL -> "Loaded model"
    ActivityType.TOOL_OTHER -> "Used tools"
}

/**
 * Categorize a tool name into activity type.
 */
internal fun categorizeToolName(toolName: String): ActivityType = when (toolName) {
    "search_web", "scrape_web" -> ActivityType.SEARCH
    "search_memory" -> ActivityType.MEMORY_RECALL
    "eval_python", "pip_install", "write_sandbox_file", 
    "read_sandbox_file", "list_sandbox_files", "delete_sandbox_file" -> ActivityType.PYTHON
    "workspace_read_file", "workspace_write_file", "workspace_edit_file", "workspace_shell", "workspace_view_image" -> ActivityType.WORKSPACE
    "manage_skills" -> ActivityType.SKILL
    else -> if (toolName.startsWith("mcp_")) ActivityType.MCP else ActivityType.TOOL_OTHER
}

private val pythonToolNames = setOf(
    "eval_python",
    "pip_install",
    "write_sandbox_file",
    "read_sandbox_file",
    "list_sandbox_files",
    "delete_sandbox_file"
)

private val workspaceToolNames = setOf(
    "workspace_read_file",
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_shell",
    "workspace_view_image",
)

internal fun resolveActivityToolName(toolName: String, arguments: String): String {
    val normalized = toolName.trim()
    if (normalized in pythonToolNames || normalized in workspaceToolNames) {
        return normalized
    }
    if (normalized.length >= 10 && workspaceToolNames.any { it.startsWith(normalized) }) {
        return workspaceToolNames.first { it.startsWith(normalized) }
    }
    if (normalized.length >= 3 && pythonToolNames.any { it.startsWith(normalized) }) {
        return "eval_python"
    }
    if (normalized.isBlank() && arguments.looksLikePythonToolArguments()) {
        return "eval_python"
    }
    return normalized
}

private fun String.looksLikePythonToolArguments(): Boolean {
    if (isBlank()) return false
    return contains("\"code\"") ||
        contains("'code'") ||
        contains("\\\"code\\\"")
}


/**
 * Build activity items from a CompletedMultiple state.
 */
fun buildActivityItemsFromMultiple(state: ActivityState.CompletedMultiple): List<ActivityItem> {
    val items = mutableListOf<ActivityItem>()
    
    // Add reasoning if present
    if (state.reasoningDurationMs != null) {
        items.add(ActivityItem(
            type = ActivityType.REASONING,
            durationMs = state.reasoningDurationMs
        ))
    }
    
    if (state.activityTypes.isNotEmpty()) {
        state.activityTypes.distinct().forEach { type ->
            items.add(ActivityItem(type = type))
        }
    }
    
    return items
}

// Compact pills stay capsule-like (36dp tall). The tuck matches the bubble joint.
private val LARGE_RADIUS = 20.dp
private val SMALL_RADIUS = AppShapes.MessageBubbleJoint
private val PILL_HEIGHT = 36.dp
// Compact row padding. The open header uses the timeline's 12dp inset, so the
// single-entry morph can slide the same icon and label between those two tops.
private val ACTIVITY_HEADER_HORIZONTAL = 14.dp
private val COMPACT_HEADER_VERTICAL = 8.dp
private val HEADER_CONTENT_GAP = 8.dp
// One critically damped clock. Size, corners, color, and the crossfade all read it,
// so a tap mid-flight reverses from the live value instead of restarting a second spring.
// 320 is a small step up from 240: still no bounce, just a little quicker.
private const val PILL_MORPH_STIFFNESS = 320f
private val PILL_MORPH_SPEC = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = PILL_MORPH_STIFFNESS,
)
private val PILL_PROGRESS_SPEC = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = PILL_MORPH_STIFFNESS,
)

/** Multi-step only: compact icon/label fade out over the early spring range. */
internal const val MULTI_STEP_COMPACT_FADE_END = 0.35f
/** Fraction of each entry window that overlaps the next (ripple, no gap). */
internal const val MULTI_STEP_ENTRY_OVERLAP = 0.45f
/** Soft settle while an entry fades in; reverses with the same progress. */
internal val MULTI_STEP_ENTRY_SETTLE = 6.dp

/** Hermite smoothstep with zero slope at both ends. */
internal fun activitySmoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 == edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Compact icon/label alpha for multi-step open/close. 1 at rest, 0 by fade-end. */
internal fun multiStepCompactAlpha(progress: Float): Float =
    1f - activitySmoothstep(0f, MULTI_STEP_COMPACT_FADE_END, progress.coerceIn(0f, 1f))

/**
 * Per-entry (or footer) reveal alpha for multi-step timelines.
 * Driven only by [progress] so a mid-flight reverse walks the same curve backward.
 * Entries use the range after [MULTI_STEP_COMPACT_FADE_END], staggered top→bottom with overlap.
 */
internal fun multiStepEntryAlpha(progress: Float, index: Int, count: Int): Float {
    if (count <= 0 || index < 0 || index >= count) return 0f
    val t = ((progress.coerceIn(0f, 1f) - MULTI_STEP_COMPACT_FADE_END) /
        (1f - MULTI_STEP_COMPACT_FADE_END)).coerceIn(0f, 1f)
    if (count == 1) return activitySmoothstep(0f, 1f, t)
    val window = 1f / (1f + (count - 1) * (1f - MULTI_STEP_ENTRY_OVERLAP))
    val step = window * (1f - MULTI_STEP_ENTRY_OVERLAP)
    val start = index * step
    val end = (start + window).coerceAtMost(1f)
    return activitySmoothstep(start, end, t)
}

/**
 * How far sibling [index] (0 = the pill immediately right of the anchor) has flown
 * out from behind the leftmost pill. 0 is tucked on top of the anchor, 1 is settled.
 * The same curve runs backward when a tap interrupts the fly-out.
 */
internal fun multiStepSiblingFly(reveal: Float, index: Int, siblingCount: Int): Float {
    if (siblingCount <= 0 || index < 0) return 1f
    if (index >= siblingCount) return 0f
    val t = reveal.coerceIn(0f, 1f)
    val window = if (siblingCount == 1) 1f else 0.62f
    val span = 1f - window
    val start = if (siblingCount == 1) 0f else index * span / (siblingCount - 1)
    val end = (start + window).coerceAtMost(1f)
    return activitySmoothstep(start, end, t)
}

/**
 * Position of a pill in a row of pills.
 */
enum class PillPosition {
    SINGLE,     // Only one pill - fully rounded
    FIRST,      // First in row - rounded left, flat right
    MIDDLE,     // Middle pills - flat both sides
    LAST        // Last in row - flat left, rounded right
}

private sealed interface SinglePillContentState {
    class Compact(val state: ActivityState) : SinglePillContentState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Compact) return false
            val s1 = this.state
            val s2 = other.state
            if (s1 === s2) return true
            if (s1 is ActivityState.Reasoning && s2 is ActivityState.Reasoning) {
                return s1.startTimeMs == s2.startTimeMs && s1.title == s2.title
            }
            return s1 == s2
        }

        override fun hashCode(): Int {
            return if (state is ActivityState.Reasoning) {
                31 * state.startTimeMs.hashCode() + (state.title?.hashCode() ?: 0)
            } else {
                state.hashCode()
            }
        }
    }
    data class ExpandedReasoning(
        val state: ActivityState.Reasoning,
        val durationMs: Long? = null,
        val isLive: Boolean = true
    ) : SinglePillContentState
    data class ExpandedTimeline(
        val entries: List<TimelineEntry>,
        val initialRequest: TimelineOpenRequest?,
        val assistantId: String?,
        val scrollHandoffMode: TimelineScrollHandoffMode,
        val isLive: Boolean,
    ) : SinglePillContentState
}

/**
 * A row of activity pills with Apple-like smooth animations.
 * 
 * During loading: Shows a single morphing pill (Waiting → Reasoning → Tool → etc.)
 * After completion: If multiple activities, reveals them with staggered fly-out animation
 */
@Composable
internal fun ActivityPillRow(
    state: ActivityState,
    onClick: (ActivityType?) -> Unit,
    modifier: Modifier = Modifier,
    connectsToBubbleBelow: Boolean = true,
    reasoningPreviewEnabled: Boolean = false,
    maxBubbleWidth: Dp = Dp.Infinity,
    timelineOpen: Boolean = false,
    timelineEntries: List<TimelineEntry> = emptyList(),
    initialTimelineOpenRequest: TimelineOpenRequest? = null,
    assistantId: String? = null,
    timelineScrollHandoffMode: TimelineScrollHandoffMode = TimelineScrollHandoffMode.EdgeGatedToParent,
    timelineLive: Boolean = false,
    onTimelineDismiss: () -> Unit = { onClick(null) },
    key: Any? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "pill_scale"
    )
    
    // Build activity items for multi-pill state
    val activityItems = remember(state) {
        if (state is ActivityState.CompletedMultiple) buildActivityItemsFromMultiple(state) else emptyList()
    }

    val wasCompletedInitially = remember(key) { state is ActivityState.CompletedMultiple || state is ActivityState.CompletedSingle }
    
    // Material's 48dp tap target was centering the 36dp pill under the avatar.
    // The drawn pill is already 36dp, same as the avatar, so don't add that inset.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    // Animated visibility for the entire pill row
    if (wasCompletedInitially) {
        if (state !is ActivityState.Hidden) {
            Row(
                modifier = modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedSinglePill(
                    state = state,
                    onClick = onClick,
                    connectsToBubbleBelow = connectsToBubbleBelow,
                    reasoningPreviewEnabled = reasoningPreviewEnabled,
                    maxBubbleWidth = maxBubbleWidth,
                    timelineOpen = timelineOpen,
                    timelineEntries = timelineEntries,
                    initialTimelineOpenRequest = initialTimelineOpenRequest,
                    assistantId = assistantId,
                    timelineScrollHandoffMode = timelineScrollHandoffMode,
                    timelineLive = timelineLive,
                    onTimelineDismiss = onTimelineDismiss,
                    wasCompletedInitially = wasCompletedInitially,
                    key = key
                )
            }
        }
    } else {
        AnimatedVisibility(
            visible = state !is ActivityState.Hidden,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.9f)
        ) {
            Row(
                modifier = modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedSinglePill(
                    state = state,
                    onClick = onClick,
                    connectsToBubbleBelow = connectsToBubbleBelow,
                    reasoningPreviewEnabled = reasoningPreviewEnabled,
                    maxBubbleWidth = maxBubbleWidth,
                    timelineOpen = timelineOpen,
                    timelineEntries = timelineEntries,
                    initialTimelineOpenRequest = initialTimelineOpenRequest,
                    assistantId = assistantId,
                    timelineScrollHandoffMode = timelineScrollHandoffMode,
                    timelineLive = timelineLive,
                    onTimelineDismiss = onTimelineDismiss,
                    wasCompletedInitially = wasCompletedInitially,
                    key = key
                )
            }
        }
    }
    }
}

/**
 * Animated single pill that smoothly morphs between states.
 * Uses AnimatedContent for crossfade and smooth size transitions.
 */
@Composable
private fun AnimatedSinglePill(
    state: ActivityState,
    onClick: (ActivityType?) -> Unit,
    connectsToBubbleBelow: Boolean,
    reasoningPreviewEnabled: Boolean,
    maxBubbleWidth: Dp,
    timelineOpen: Boolean,
    timelineEntries: List<TimelineEntry>,
    initialTimelineOpenRequest: TimelineOpenRequest?,
    assistantId: String?,
    timelineScrollHandoffMode: TimelineScrollHandoffMode,
    timelineLive: Boolean,
    onTimelineDismiss: () -> Unit,
    wasCompletedInitially: Boolean,
    key: Any? = null
) {
    val isExpandedReasoning = reasoningPreviewEnabled && state is ActivityState.Reasoning && !timelineOpen
    val requestedContentState = if (timelineOpen && timelineEntries.isNotEmpty()) {
        if (timelineEntries.size == 1 && timelineEntries.first() is TimelineEntry.Reasoning) {
            val reasoningEntry = timelineEntries.first() as TimelineEntry.Reasoning
            val isLive = timelineLive && reasoningEntry.isInProgress
            SinglePillContentState.ExpandedReasoning(
                state = ActivityState.Reasoning(
                    startTimeMs = (state as? ActivityState.Reasoning)?.startTimeMs
                        ?: (System.currentTimeMillis() - reasoningEntry.durationMs),
                    title = reasoningEntry.title,
                    reasoningText = reasoningEntry.content
                ),
                durationMs = reasoningEntry.durationMs,
                isLive = isLive
            )
        } else {
            SinglePillContentState.ExpandedTimeline(
                entries = timelineEntries,
                initialRequest = initialTimelineOpenRequest,
                assistantId = assistantId,
                scrollHandoffMode = timelineScrollHandoffMode,
                isLive = timelineLive,
            )
        }
    } else if (isExpandedReasoning && state is ActivityState.Reasoning) {
        SinglePillContentState.ExpandedReasoning(
            state = state,
            durationMs = null,
            isLive = timelineLive
        )
    } else {
        SinglePillContentState.Compact(state)
    }
    val surfaceExpanded = requestedContentState !is SinglePillContentState.Compact
    // Keep the open panel composed through a collapse so the spring can measure it
    // on the way back. Drop it only after progress has settled on compact.
    var retainedExpanded by remember { mutableStateOf<SinglePillContentState?>(null) }
    SideEffect {
        if (requestedContentState !is SinglePillContentState.Compact &&
            retainedExpanded != requestedContentState
        ) {
            retainedExpanded = requestedContentState
        }
    }
    val visibleExpanded = if (requestedContentState !is SinglePillContentState.Compact) {
        requestedContentState
    } else {
        retainedExpanded
    }

    val chatAnimationsEnabled = me.rerere.rikkahub.ui.context.LocalChatAnimationsEnabled.current
    val progress = remember { Animatable(if (surfaceExpanded) 1f else 0f) }
    val multiPillCount = if (state is ActivityState.CompletedMultiple) {
        buildActivityItemsFromMultiple(state).size
    } else {
        0
    }
    val isMultiPill = multiPillCount > 1
    // History opens already settled. A live turn starts tucked so the siblings can
    // fly out once, the same way a minimize ends.
    val siblingReveal = remember(key) { Animatable(if (wasCompletedInitially) 1f else 0f) }
    // Minimize of a multi-step card keeps the timeline in one piece while it shrinks
    // to the leftmost pill. Expand from rest leaves this false so the ripple stays.
    var tuckMinimize by remember(key) { mutableStateOf(false) }
    LaunchedEffect(surfaceExpanded, chatAnimationsEnabled, isMultiPill, wasCompletedInitially) {
        val open = surfaceExpanded
        if (!chatAnimationsEnabled) {
            progress.snapTo(if (open) 1f else 0f)
            if (isMultiPill) siblingReveal.snapTo(1f)
            tuckMinimize = false
            if (!open && progress.value == 0f) retainedExpanded = null
            return@LaunchedEffect
        }
        if (!isMultiPill) {
            // Always retarget. A cancelled flight leaves targetValue stale, and skipping
            // animateTo there would freeze the pill between sizes.
            progress.animateTo(if (open) 1f else 0f, PILL_PROGRESS_SPEC)
            if (!open && progress.value == 0f) retainedExpanded = null
            return@LaunchedEffect
        }
        if (open) {
            // Interrupting a fly-out: siblings go back behind the anchor, then the
            // card grows from that one pill. Opening from rest (reveal already 1)
            // skips this and keeps the ripple expand.
            if (progress.value <= 0.02f && siblingReveal.value < 0.999f) {
                siblingReveal.animateTo(0f, PILL_PROGRESS_SPEC)
            }
            progress.animateTo(1f, PILL_PROGRESS_SPEC)
            tuckMinimize = false
            return@LaunchedEffect
        }
        if (!wasCompletedInitially && progress.value <= 0.02f && siblingReveal.value < 0.999f) {
            siblingReveal.animateTo(1f, PILL_PROGRESS_SPEC)
            return@LaunchedEffect
        }
        if (progress.value >= 0.98f) {
            // Fully open: shrink the whole card onto the leftmost pill (a complete
            // pill), then let the other segments fly out from behind it.
            tuckMinimize = true
            siblingReveal.snapTo(0f)
            progress.animateTo(0f, PILL_PROGRESS_SPEC)
            if (progress.value <= 0.02f) {
                tuckMinimize = false
                retainedExpanded = null
                siblingReveal.animateTo(1f, PILL_PROGRESS_SPEC)
            }
        } else {
            // Early reverse of an expand. Keep the symmetric ripple; don't retarget
            // the width onto the first pill or the rows would hard-cut.
            progress.animateTo(0f, PILL_PROGRESS_SPEC)
        }
        if (!open && progress.value == 0f) retainedExpanded = null
    }
    val morph = progress.value.coerceIn(0f, 1f)
    val morphRunning = progress.isRunning
    val fullyCollapsed = !surfaceExpanded && !morphRunning && morph == 0f

    // Every expanded activity surface matches the multi-step ActivityTimelinePanel:
    // InputField (24dp). Reasoning-only used to stay at the compact 20dp pill radius,
    // which left a different corner when that card was expanded.
    val expandedRadius = AppShapes.MessageBubbleRadius
    val collapsedBottom = if (connectsToBubbleBelow) SMALL_RADIUS else LARGE_RADIUS
    val topStartRadius = dpLerp(LARGE_RADIUS, expandedRadius, morph)
    val topEndRadius = topStartRadius
    val bottomStartRadius = dpLerp(collapsedBottom, expandedRadius, morph)
    val bottomEndRadius = bottomStartRadius

    val expandedIsTimeline = visibleExpanded is SinglePillContentState.ExpandedTimeline
    val collapsedColor = if (state is ActivityState.CompletedMultiple) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    // Multi-step timeline: the inner ActivityTimelinePanel paints surfaceContainerLow
    // itself, so the outer pill stays transparent and cannot peek past those corners.
    // Reasoning-only expanded states have no inner panel, so they use that same
    // surfaceContainerLow here instead of the compact pill's surfaceContainerHigh.
    val expandedColor = if (expandedIsTimeline) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val pillColor = graphicsLerp(collapsedColor, expandedColor, morph)
    val pillShape = RoundedCornerShape(
        topStart = topStartRadius,
        topEnd = topEndRadius,
        bottomStart = bottomStartRadius,
        bottomEnd = bottomEndRadius
    )
    val testTag = when (state) {
        is ActivityState.Ocr -> ActivityType.OCR
        is ActivityState.Reasoning -> ActivityType.REASONING
        is ActivityState.ToolUse -> categorizeToolName(state.toolName)
        is ActivityState.CompletedSingle -> state.type
        else -> null
    }?.toTestTag()

    Surface(
        modifier = Modifier
            .clip(pillShape)
            .then(
                // Streaming growth only after the open/close spring has finished,
                // so it cannot fight the morph or leave a stale width behind.
                if (chatAnimationsEnabled && surfaceExpanded && !morphRunning) {
                    Modifier.animateContentSize(
                        animationSpec = PILL_MORPH_SPEC,
                        alignment = Alignment.TopStart
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (fullyCollapsed) Modifier.defaultMinSize(minHeight = PILL_HEIGHT) else Modifier
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        shape = pillShape,
        color = pillColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = {
            val clickType = when (state) {
                is ActivityState.Ocr -> ActivityType.OCR
                is ActivityState.Reasoning -> ActivityType.REASONING
                is ActivityState.ToolUse -> categorizeToolName(state.toolName)
                is ActivityState.CompletedSingle -> state.type
                else -> null
            }
            onClick(clickType)
        }
    ) {
        val multiStepReveal =
            (visibleExpanded as? SinglePillContentState.ExpandedTimeline)?.entries?.size?.let { it > 1 } == true
        val expandedReasoning = visibleExpanded as? SinglePillContentState.ExpandedReasoning
        val activeReasoningState = expandedReasoning?.state?.let { targetState ->
            val parentState = state as? ActivityState.Reasoning
            if (parentState != null &&
                parentState.startTimeMs == targetState.startTimeMs &&
                parentState.reasoningText.length > targetState.reasoningText.length
            ) {
                parentState
            } else {
                targetState
            }
        }
        // Multi-step keeps the ripple. Single-entry pairs the closed and open headers
        // so one spring can move them instead of crossfading two stacked labels.
        val headerPair = if (multiStepReveal) {
            null
        } else {
            pillHeaderPair(
                state = state,
                expanded = visibleExpanded,
                reasoningState = activeReasoningState,
            )
        }
        val anchorHeader = headerPair != null
        val expandedHeaderAlpha = if (anchorHeader && morph < 1f) 0f else 1f
        Box {
        PillMorphLayout(
            progress = morph,
            maxExpandedWidth = maxBubbleWidth,
            multiStepReveal = multiStepReveal,
            collapseAsOneCard = tuckMinimize,
            anchorHeader = anchorHeader,
        ) {
            if (visibleExpanded != null && !fullyCollapsed) {
            PillMorphLayer(id = "expanded") {
                when (val targetContentState = visibleExpanded) {
                    is SinglePillContentState.ExpandedTimeline -> {
                        ActivityTimelinePanel(
                            entries = targetContentState.entries,
                            initialOpenRequest = targetContentState.initialRequest,
                            assistantId = targetContentState.assistantId,
                            scrollHandoffMode = targetContentState.scrollHandoffMode,
                            isLive = targetContentState.isLive,
                            onTimelineClick = onTimelineDismiss,
                            animateSize = false,
                            pillAnchored = true,
                            revealProgress = if (multiStepReveal && !tuckMinimize) morph else null,
                            singleEntryHeaderAlpha = expandedHeaderAlpha,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is SinglePillContentState.ExpandedReasoning -> {
                        ReasoningPreviewCard(
                            state = activeReasoningState ?: targetContentState.state,
                            active = surfaceExpanded,
                            isLive = targetContentState.isLive,
                            durationMs = targetContentState.durationMs,
                            headerAlpha = expandedHeaderAlpha,
                            onHeaderClick = if (timelineOpen) onTimelineDismiss else null
                        )
                    }
                    else -> Unit
                }
            }
            }
            PillMorphLayer(
                id = "compact",
                modifier = Modifier.defaultMinSize(minHeight = PILL_HEIGHT),
            ) {
                    val compactState = if (!surfaceExpanded && !isExpandedReasoning) state else (requestedContentState as? SinglePillContentState.Compact)?.state ?: state
                    
                    AnimatedContent(
                        targetState = compactState,
                        transitionSpec = {
                            if (wasCompletedInitially) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                (fadeIn(animationSpec = tween(200)) +
                                    scaleIn(initialScale = 0.92f, animationSpec = tween(200)))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(150)) +
                                            scaleOut(targetScale = 0.92f, animationSpec = tween(150))
                                    )
                            }
                        },
                        label = "pill_content",
                        contentKey = { stateToKey(it) }
                    ) { targetState ->
                        if (targetState is ActivityState.CompletedMultiple) {
                            val items = buildActivityItemsFromMultiple(targetState)
                            val siblingCount = (items.size - 1).coerceAtLeast(0)
                            val reveal = if (siblingCount > 0) siblingReveal.value else 1f
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items.forEachIndexed { index, item ->
                                    val position = when {
                                        items.size == 1 -> PillPosition.SINGLE
                                        index == 0 -> PillPosition.FIRST
                                        index == items.lastIndex -> PillPosition.LAST
                                        else -> PillPosition.MIDDLE
                                    }
                                    val fly = if (index == 0 || siblingCount == 0) {
                                        1f
                                    } else {
                                        multiStepSiblingFly(reveal, index - 1, siblingCount)
                                    }
                                    // 0 = a complete pill. The anchor flattens as the first
                                    // neighbor comes out; each sibling settles into its own slot.
                                    val segment = if (siblingCount == 0) {
                                        1f
                                    } else if (index == 0) {
                                        multiStepSiblingFly(reveal, 0, siblingCount)
                                    } else {
                                        fly
                                    }
                                    if (items.size == 1) {
                                        ExpandedActivityPill(
                                            item = item,
                                            onClick = { onClick(item.type) },
                                            position = position,
                                            connectsToBubbleBelow = connectsToBubbleBelow
                                        )
                                    } else {
                                        CompactActivityPill(
                                            item = item,
                                            onClick = { onClick(item.type) },
                                            position = position,
                                            connectsToBubbleBelow = connectsToBubbleBelow,
                                            cornerSegment = segment,
                                            modifier = if (index == 0) {
                                                Modifier
                                            } else {
                                                Modifier
                                                    .padding(start = 2.dp * fly)
                                                    .siblingFlySlot(fly)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = ACTIVITY_HEADER_HORIZONTAL,
                                    vertical = COMPACT_HEADER_VERTICAL,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (targetState) {
                                    is ActivityState.Waiting -> {
                                        TypingIndicator(
                                            dotSize = 7.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    is ActivityState.Ocr -> {
                                        OcrContent(isLive = true)
                                    }
                                    is ActivityState.Reasoning -> {
                                        val liveState = (state as? ActivityState.Reasoning) ?: targetState
                                        ReasoningContent(
                                            startTimeMs = liveState.startTimeMs,
                                            title = liveState.title,
                                            isLive = true
                                        )
                                    }
                                    is ActivityState.ToolUse -> {
                                        ToolUseContent(
                                            toolName = targetState.toolName,
                                            displayName = targetState.displayName,
                                            isLive = true
                                        )
                                    }
                                    is ActivityState.Replying -> {
                                        Text(
                                            text = stringResource(R.string.activity_pill_replying),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.shimmer(isLoading = true)
                                        )
                                    }
                                    is ActivityState.LoadingModel -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Memory,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = targetState.modelName?.let { "Loading $it..." } ?: "Loading model...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.shimmer(isLoading = true)
                                        )
                                    }
                                    is ActivityState.CompletedSingle -> {
                                        val item = ActivityItem(
                                            type = targetState.type,
                                            durationMs = targetState.durationMs,
                                            count = targetState.count,
                                            displayName = targetState.displayName
                                        )
                                        ExpandedActivityContent(item = item)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                    }
            }
        val ends = headerPair
        if (ends != null && morph > 0f && morph < 1f) {
            SingleEntryMorphHeader(
                progress = morph,
                closed = ends.first,
                open = ends.second,
                modifier = Modifier.matchParentSize(),
            )
        }
        }
    }
}



private data class PillHeaderModel(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val trailing: String?,
    val shimmer: Boolean,
)

/**
 * Vertical swap inside one line. Outgoing leaves upward, incoming arrives from below.
 * Their ranges meet at an edge ([outgoingShift] + 1 == [incomingShift]) so the glyphs
 * never occupy the same pixels. [progress] is the pill spring, so a reverse replays this.
 */
internal data class HeaderTextCrossfade(
    val outgoingAlpha: Float,
    val outgoingShift: Float,
    val incomingAlpha: Float,
    val incomingShift: Float,
)

internal fun headerTextCrossfade(progress: Float): HeaderTextCrossfade {
    val t = progress.coerceIn(0f, 1f)
    return HeaderTextCrossfade(
        outgoingAlpha = 1f - t,
        outgoingShift = -t,
        incomingAlpha = t,
        incomingShift = 1f - t,
    )
}

@Composable
private fun pillHeaderPair(
    state: ActivityState,
    expanded: SinglePillContentState?,
    reasoningState: ActivityState.Reasoning?,
): Pair<PillHeaderModel, PillHeaderModel>? {
    if (expanded == null || expanded is SinglePillContentState.Compact) return null
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val nowMs = System.currentTimeMillis()
    val open = when (expanded) {
        is SinglePillContentState.ExpandedReasoning -> {
            val reasoning = reasoningState ?: expanded.state
            val title = reasoning.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.activity_timeline_reasoning)
            val elapsed = if (expanded.isLive) {
                nowMs - reasoning.startTimeMs
            } else {
                expanded.durationMs ?: 0L
            }
            PillHeaderModel(
                icon = Icons.Rounded.Lightbulb,
                iconTint = tint,
                title = title,
                trailing = formatDuration(elapsed),
                shimmer = expanded.isLive,
            )
        }
        is SinglePillContentState.ExpandedTimeline -> {
            val entry = expanded.entries.singleOrNull() ?: return null
            val durationLabel = if (entry is TimelineEntry.Reasoning) {
                formatTimelineDuration(entry.durationMs)?.let { " · $it" }.orEmpty()
            } else {
                ""
            }
            PillHeaderModel(
                icon = getTimelineIcon(entry),
                iconTint = getTimelineIconTint(entry, getTimelineAccentColor(entry)),
                title = getTimelineLabel(entry) + durationLabel,
                trailing = null,
                shimmer = false,
            )
        }
    }
    val closed = compactPillHeader(state, nowMs, tint) ?: return null
    return closed to open
}

@Composable
private fun compactPillHeader(
    state: ActivityState,
    nowMs: Long,
    tint: Color,
): PillHeaderModel? {
    return when (state) {
        is ActivityState.Reasoning -> PillHeaderModel(
            icon = Icons.Rounded.Lightbulb,
            iconTint = tint,
            title = state.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.activity_timeline_reasoning),
            trailing = formatDuration(nowMs - state.startTimeMs),
            shimmer = true,
        )
        is ActivityState.ToolUse -> PillHeaderModel(
            icon = categorizeToolName(state.toolName).getIcon(),
            iconTint = tint,
            title = state.displayName,
            trailing = null,
            shimmer = true,
        )
        is ActivityState.Ocr -> PillHeaderModel(
            icon = Icons.Rounded.Image,
            iconTint = tint,
            title = stringResource(R.string.activity_pill_ocr_live),
            trailing = null,
            shimmer = true,
        )
        is ActivityState.CompletedSingle -> {
            val item = ActivityItem(
                type = state.type,
                durationMs = state.durationMs,
                count = state.count,
                displayName = state.displayName,
            )
            PillHeaderModel(
                icon = state.type.getIcon(),
                iconTint = tint,
                title = expandedActivityLabel(item),
                trailing = null,
                shimmer = false,
            )
        }
        else -> null
    }
}

@Composable
private fun expandedActivityLabel(item: ActivityItem): String {
    return when (item.type) {
        ActivityType.REASONING -> {
            if (item.durationMs != null) {
                "Reasoned for ${formatDuration(item.durationMs)}"
            } else {
                "Reasoned"
            }
        }
        ActivityType.OCR -> {
            if (item.count > 1) {
                stringResource(R.string.activity_pill_ocr_done_count, item.count)
            } else {
                stringResource(R.string.activity_pill_ocr_done)
            }
        }
        ActivityType.SEARCH -> "Searched the Web"
        ActivityType.MEMORY_RECALL -> stringResource(R.string.activity_pill_memory_recalled)
        ActivityType.PYTHON -> "Ran Python"
        ActivityType.WORKSPACE -> "Used workspace"
        ActivityType.SKILL -> "Managed skills"
        ActivityType.MCP -> "MCP"
        ActivityType.TOOL_OTHER -> "Used tool"
        ActivityType.LOADING_MODEL -> "Loading model"
    }
}

@Composable
private fun SingleEntryMorphHeader(
    progress: Float,
    closed: PillHeaderModel,
    open: PillHeaderModel,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    val top = dpLerp(COMPACT_HEADER_VERTICAL, TimelineLiveInsetVertical, t)
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val shimmer = closed.shimmer || open.shimmer
    Box(modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .offset(y = top)
                .padding(horizontal = ACTIVITY_HEADER_HORIZONTAL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphHeaderIcon(closed = closed, open = open, progress = t)
            Spacer(Modifier.width(HEADER_CONTENT_GAP))
            VerticalCrossfadeText(
                closed = closed.title,
                open = open.title,
                progress = t,
                style = labelStyle,
                color = labelColor,
                shimmer = shimmer,
                modifier = Modifier.weight(1f),
            )
            MorphHeaderTrailing(closed = closed, open = open, progress = t, shimmer = shimmer)
        }
    }
}

@Composable
private fun MorphHeaderIcon(
    closed: PillHeaderModel,
    open: PillHeaderModel,
    progress: Float,
) {
    if (closed.icon == open.icon) {
        Icon(
            imageVector = closed.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = graphicsLerp(closed.iconTint, open.iconTint, progress),
        )
    } else {
        val fade = headerTextCrossfade(progress)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clipToBounds()
        ) {
            Icon(
                imageVector = closed.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        alpha = fade.outgoingAlpha
                        translationY = fade.outgoingShift * size.height
                    },
                tint = closed.iconTint,
            )
            Icon(
                imageVector = open.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        alpha = fade.incomingAlpha
                        translationY = fade.incomingShift * size.height
                    },
                tint = open.iconTint,
            )
        }
    }
}

@Composable
private fun MorphHeaderTrailing(
    closed: PillHeaderModel,
    open: PillHeaderModel,
    progress: Float,
    shimmer: Boolean,
) {
    val closedText = closed.trailing
    val openText = open.trailing
    if (closedText == null && openText == null) return
    val presence = when {
        closedText == null -> progress
        openText == null -> 1f - progress
        else -> 1f
    }
    if (presence <= 0f) return
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val gap = HEADER_CONTENT_GAP.roundToPx()
                val full = placeable.width + gap
                val width = (full * presence).roundToInt().coerceAtLeast(0)
                layout(width, placeable.height) {
                    placeable.place(width - placeable.width, 0)
                }
            }
            .graphicsLayer {
                alpha = if (closedText != null && openText != null) 1f else presence
            }
    ) {
        if (closedText != null && openText != null) {
            VerticalCrossfadeText(
                closed = closedText,
                open = openText,
                progress = progress,
                style = style,
                color = color,
                shimmer = shimmer,
            )
        } else {
            Text(
                text = openText ?: closedText.orEmpty(),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.shimmer(shimmer),
            )
        }
    }
}

@Composable
private fun VerticalCrossfadeText(
    closed: String,
    open: String,
    progress: Float,
    style: TextStyle,
    color: Color,
    shimmer: Boolean,
    modifier: Modifier = Modifier,
) {
    if (closed == open) {
        Text(
            text = closed,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.shimmer(shimmer),
        )
        return
    }
    val fade = headerTextCrossfade(progress)
    val lineDp = with(LocalDensity.current) {
        val line = style.lineHeight
        if (line.type == androidx.compose.ui.unit.TextUnitType.Sp) line.toDp() else 18.dp
    }
    Box(
        modifier
            .height(lineDp)
            .clipToBounds()
            .shimmer(shimmer)
    ) {
        Text(
            text = closed,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                alpha = fade.outgoingAlpha
                translationY = fade.outgoingShift * size.height
            },
        )
        Text(
            text = open,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                alpha = fade.incomingAlpha
                translationY = fade.incomingShift * size.height
            },
        )
    }
}

@Composable
private fun PillMorphLayer(
    id: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.layoutId(id)) { content() }
}

@Composable
private fun PillMorphLayout(
    progress: Float,
    maxExpandedWidth: Dp,
    multiStepReveal: Boolean = false,
    collapseAsOneCard: Boolean = false,
    anchorHeader: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val compactPlaceable = measurables.first { it.layoutId == "compact" }.measure(loose)
        val expandedMeasurable = measurables.firstOrNull { it.layoutId == "expanded" }
        val expandedCap = if (maxExpandedWidth.value.isFinite()) {
            maxExpandedWidth.roundToPx().coerceAtMost(loose.maxWidth)
        } else {
            loose.maxWidth
        }
        // Always measure the open panel at its full size; the layout clips to the
        // lerped height so per-row fades cannot change the morphing bounds.
        val expandedPlaceable = expandedMeasurable?.measure(
            loose.copy(maxWidth = expandedCap.coerceAtLeast(0))
        )
        val t = progress.coerceIn(0f, 1f)
        val width = if (expandedPlaceable == null) {
            compactPlaceable.width
        } else {
            (compactPlaceable.width + (expandedPlaceable.width - compactPlaceable.width) * t).roundToInt()
        }
        val height = if (expandedPlaceable == null) {
            compactPlaceable.height
        } else {
            (compactPlaceable.height + (expandedPlaceable.height - compactPlaceable.height) * t).roundToInt()
        }
        val layoutWidth = width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutWidth, layoutHeight) {
            if (multiStepReveal && collapseAsOneCard) {
                // Shrink the open card as one piece onto the leftmost pill. Rows do not
                // un-ripple; the whole panel crossfades into that pill at the end.
                val compactAlpha = multiStepCompactAlpha(t)
                val panelAlpha = (1f - compactAlpha).coerceIn(0f, 1f)
                if (expandedPlaceable != null && panelAlpha > 0.001f) {
                    expandedPlaceable.placeWithLayer(0, 0) { alpha = panelAlpha }
                }
                if (compactAlpha > 0.001f) {
                    compactPlaceable.placeWithLayer(0, 0) { alpha = compactAlpha }
                }
            } else if (multiStepReveal) {
                // Panel stays fully opaque; entries ripple in from revealProgress.
                // Compact icon/label ease out over the early range, then back on close.
                val compactAlpha = multiStepCompactAlpha(t)
                if (expandedPlaceable != null && t > 0f) {
                    expandedPlaceable.placeWithLayer(0, 0) { alpha = 1f }
                }
                if (compactAlpha > 0.001f) {
                    compactPlaceable.placeWithLayer(0, 0) { alpha = compactAlpha }
                }
            } else if (anchorHeader) {
                // One moving header is drawn above this layout. Painting the compact
                // label as well would stack the two strings. The open panel fades in
                // under that header; its own header stays invisible until the spring lands.
                if (expandedPlaceable != null && t > 0f) {
                    expandedPlaceable.placeWithLayer(0, 0) { alpha = t }
                }
                if (expandedPlaceable == null || t == 0f) {
                    compactPlaceable.place(0, 0)
                }
            } else {
                // Fallback when the two headers cannot be paired: the old crossfade.
                val expandedOnTop = t >= 0.5f
                if (expandedPlaceable != null && !expandedOnTop && t > 0f) {
                    expandedPlaceable.placeWithLayer(0, 0) { alpha = t }
                }
                if (t < 1f) {
                    compactPlaceable.placeWithLayer(0, 0) { alpha = 1f - t }
                }
                if (expandedPlaceable != null && expandedOnTop) {
                    expandedPlaceable.placeWithLayer(0, 0) { alpha = t }
                }
            }
        }
    }
}

@Composable
private fun ReasoningPreviewCard(
    state: ActivityState.Reasoning,
    active: Boolean,
    isLive: Boolean = true,
    durationMs: Long? = null,
    headerAlpha: Float = 1f,
    onHeaderClick: (() -> Unit)? = null
) {
    val previewScrollLock = remember { timelineLeftoverScrollConnection() }
    var elapsedMs by remember { mutableLongStateOf(durationMs ?: 0L) }
    val scrollState = rememberScrollState()
    var previewAutoFollowPaused by remember(state.startTimeMs) { mutableStateOf(false) }
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    var previousScrollValue by remember(state.startTimeMs) { mutableIntStateOf(0) }

    if (isLive) {
        LaunchedEffect(state.startTimeMs) {
            while (isActive) {
                elapsedMs = System.currentTimeMillis() - state.startTimeMs
                delay(50)
            }
        }
    } else {
        LaunchedEffect(durationMs) {
            if (durationMs != null) {
                elapsedMs = durationMs
            }
        }
    }

    LaunchedEffect(scrollState, active && isLive) {
        if (!active || !isLive) return@LaunchedEffect
        snapshotFlow {
            Triple(
                scrollState.value,
                scrollState.maxValue,
                scrollState.isScrollInProgress
            )
        }.collect { (scrollValue, maxValue, isScrollInProgress) ->
            val isAtBottom = scrollValue >= maxValue
            previewAutoFollowPaused = updatePreviewAutoFollowPaused(
                currentlyPaused = previewAutoFollowPaused,
                isAtBottom = isAtBottom,
                scrollDelta = scrollValue - previousScrollValue,
                userScrollInProgress = isScrollInProgress,
                programmaticScrollInProgress = programmaticScrollInProgress
            )
            previousScrollValue = scrollValue
        }
    }

    LaunchedEffect(scrollState, previewAutoFollowPaused, active && isLive) {
        if (!active || !isLive) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }.collect { maxValue ->
            if (!previewAutoFollowPaused) {
                programmaticScrollInProgress = true
                try {
                    scrollState.scrollTo(maxValue)
                } finally {
                    programmaticScrollInProgress = false
                }
            }
        }
    }

    var streamingLayoutTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(streamingLayoutTick, previewAutoFollowPaused, active && isLive) {
        if (!active || !isLive || previewAutoFollowPaused) return@LaunchedEffect
        delay(16)
        programmaticScrollInProgress = true
        try {
            scrollState.scrollTo(scrollState.maxValue)
        } finally {
            programmaticScrollInProgress = false
        }
    }

    val displayTitle = state.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.activity_timeline_reasoning)
    val previewText = state.reasoningText.takeIf { it.isNotBlank() } ?: displayTitle

    val horizontal = TimelineLiveInsetHorizontal
    val vertical = TimelineLiveInsetVertical
    val mediaCompensation = AppShapes.MessageBubblePaddingHorizontal - AppShapes.MessageBubblePaddingVertical
    CompositionLocalProvider(
        LocalOpticalFrame provides OpticalFrame(
            outer = AppShapes.MessageBubbleRadius,
            inset = maxOf(horizontal, vertical + mediaCompensation),
        )
    ) {
    Column(
        modifier = Modifier.padding(horizontal = horizontal, vertical = vertical),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = headerAlpha }
                .then(
                    if (onHeaderClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onHeaderClick
                        )
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedContent(
                targetState = displayTitle,
                transitionSpec = {
                    (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                contentAlignment = Alignment.CenterStart,
                label = "reasoning_preview_title",
                modifier = Modifier.weight(1f)
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isLive) Modifier.shimmer(true) else Modifier
                )
            }
            val formattedDuration = formatDuration(if (isLive) elapsedMs else (durationMs ?: elapsedMs))
            if (formattedDuration.isNotBlank()) {
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (isLive) Modifier.shimmer(true) else Modifier
                )
            }
        }

        val canScrollBackward by remember { derivedStateOf { scrollState.canScrollBackward } }
        val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }
        val topFadeProgress by animateFloatAsState(
            targetValue = if (canScrollBackward) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
            label = "reasoning_top_fade"
        )
        val bottomFadeProgress by animateFloatAsState(
            targetValue = if (canScrollForward) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
            label = "reasoning_bottom_fade"
        )

        MarkdownBlock(
            content = previewText,
            modifier = Modifier
                .fillMaxWidth()
                .fadeEdges(
                    topProgress = topFadeProgress,
                    bottomProgress = bottomFadeProgress,
                    fadeHeight = 64f
                )
                .nestedScroll(previewScrollLock)
                .heightIn(max = if (active && isLive && onHeaderClick == null) 120.dp else 280.dp)
                .verticalScroll(scrollState),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            paragraphSpacing = 8.dp,
            streamingTextReveal = isLive,
            onExpandedStreamingCodeBlockChanged = {
                if (isLive) streamingLayoutTick++
            }
        )
    }
    }
}

/**
 * Content for reasoning pill (live timer).
 */
@Composable
private fun ReasoningContent(startTimeMs: Long, title: String? = null, isLive: Boolean) {
    var elapsedMs by remember { mutableLongStateOf(0L) }

    if (isLive) {
        LaunchedEffect(startTimeMs) {
            while (isActive) {
                elapsedMs = System.currentTimeMillis() - startTimeMs
                delay(50)
            }
        }
    }

    Icon(
        imageVector = Icons.Rounded.Lightbulb,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Crossfade in place so the label baseline never slides.
    val displayTitle = title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.activity_timeline_reasoning)
    AnimatedContent(
        targetState = displayTitle,
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                .using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        contentAlignment = Alignment.CenterStart,
        label = "reasoning_title"
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (isLive) Modifier.shimmer(true) else Modifier
        )
    }
    Text(
        text = formatDuration(elapsedMs),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (isLive) Modifier.shimmer(true) else Modifier
    )
}

@Composable
private fun OcrContent(isLive: Boolean) {
    Icon(
        imageVector = Icons.Rounded.Image,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(R.string.activity_pill_ocr_live),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (isLive) Modifier.shimmer(true) else Modifier
    )
}

/**
 * Content for tool use pill.
 */
@Composable
private fun ToolUseContent(toolName: String, displayName: String, isLive: Boolean) {
    val type = categorizeToolName(toolName)

    Icon(
        imageVector = type.getIcon(),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = displayName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (isLive) Modifier.shimmer(true) else Modifier
    )
}

/**
 * Content for expanded single activity (after completion).
 */
@Composable
private fun ExpandedActivityContent(item: ActivityItem) {
    Icon(
        imageVector = item.type.getIcon(),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val text = expandedActivityLabel(item)

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Get corner radii based on pill position and whether it connects to bubble below.
 */
private data class PillRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
) {
    fun toShape(): RoundedCornerShape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
    )

    fun lerpTo(other: PillRadii, fraction: Float): PillRadii {
        val t = fraction.coerceIn(0f, 1f)
        return PillRadii(
            topStart = dpLerp(topStart, other.topStart, t),
            topEnd = dpLerp(topEnd, other.topEnd, t),
            bottomStart = dpLerp(bottomStart, other.bottomStart, t),
            bottomEnd = dpLerp(bottomEnd, other.bottomEnd, t),
        )
    }
}

/**
 * [segment] 0 draws a complete pill. 1 is the settled [position] in the row.
 * Corners move with the fly-out instead of swapping when a neighbor appears.
 */
private fun pillRadii(
    position: PillPosition,
    connectsToBubbleBelow: Boolean,
    segment: Float = 1f,
): PillRadii {
    val bottomLeft = if (connectsToBubbleBelow) SMALL_RADIUS else LARGE_RADIUS
    val bottomRight = if (connectsToBubbleBelow) SMALL_RADIUS else LARGE_RADIUS
    val complete = PillRadii(
        topStart = LARGE_RADIUS,
        topEnd = LARGE_RADIUS,
        bottomStart = bottomLeft,
        bottomEnd = bottomRight,
    )
    val settled = when (position) {
        PillPosition.SINGLE -> complete
        PillPosition.FIRST -> PillRadii(
            topStart = LARGE_RADIUS,
            topEnd = SMALL_RADIUS,
            bottomStart = bottomLeft,
            bottomEnd = SMALL_RADIUS,
        )
        PillPosition.MIDDLE -> PillRadii(
            topStart = SMALL_RADIUS,
            topEnd = SMALL_RADIUS,
            bottomStart = SMALL_RADIUS,
            bottomEnd = SMALL_RADIUS,
        )
        PillPosition.LAST -> PillRadii(
            topStart = SMALL_RADIUS,
            topEnd = LARGE_RADIUS,
            bottomStart = SMALL_RADIUS,
            bottomEnd = bottomRight,
        )
    }
    return complete.lerpTo(settled, segment)
}

private fun getCornerRadii(
    position: PillPosition,
    connectsToBubbleBelow: Boolean,
    segment: Float = 1f,
): RoundedCornerShape = pillRadii(position, connectsToBubbleBelow, segment).toShape()

/** Reserves a shrinking slot so a sibling can slide out from behind the anchor. */
private fun Modifier.siblingFlySlot(fly: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val t = fly.coerceIn(0f, 1f)
    val shown = (placeable.width * t).roundToInt()
    layout(shown, placeable.height) {
        placeable.placeWithLayer(
            x = (-((1f - t) * placeable.width)).roundToInt(),
            y = 0,
        ) {
            alpha = t
        }
    }
}

/**
 * Base single pill component.
 */
@Composable
private fun SinglePill(
    onClick: () -> Unit,
    position: PillPosition,
    connectsToBubbleBelow: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    isLoading: Boolean = false,
    cornerSegment: Float = 1f,
    content: @Composable () -> Unit
) {
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val pillShape = getCornerRadii(position, connectsToBubbleBelow, cornerSegment)

    val chatAnimationsEnabled = me.rerere.rikkahub.ui.context.LocalChatAnimationsEnabled.current

    Surface(
        modifier = modifier
            .height(PILL_HEIGHT)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(
                if (chatAnimationsEnabled) {
                    Modifier.animateContentSize(animationSpec = PILL_MORPH_SPEC)
                } else Modifier
            ),
        shape = pillShape,
        color = pillColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/**
 * Reasoning pill - expanded with timer.
 */
@Composable
private fun ReasoningPill(
    startTimeMs: Long,
    onClick: () -> Unit,
    position: PillPosition,
    connectsToBubbleBelow: Boolean,
    isLive: Boolean = false
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    
    if (isLive) {
        LaunchedEffect(startTimeMs) {
            while (isActive) {
                elapsedMs = System.currentTimeMillis() - startTimeMs
                delay(50)
            }
        }
    }
    
    SinglePill(
        onClick = onClick,
        position = position,
        connectsToBubbleBelow = connectsToBubbleBelow,
        testTag = ActivityType.REASONING.toTestTag(),
        isLoading = isLive
    ) {
        Icon(
            imageVector = Icons.Rounded.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.activity_timeline_reasoning),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (isLive) Modifier.shimmer(true) else Modifier
        )
        Text(
            text = formatDuration(elapsedMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (isLive) Modifier.shimmer(true) else Modifier
        )
    }
}

/**
 * Tool use pill - expanded with tool name.
 */
@Composable
private fun ToolUsePill(
    toolName: String,
    displayName: String,
    onClick: () -> Unit,
    position: PillPosition,
    connectsToBubbleBelow: Boolean,
    isLive: Boolean = false
) {
    val type = categorizeToolName(toolName)
    
    SinglePill(
        onClick = onClick,
        position = position,
        connectsToBubbleBelow = connectsToBubbleBelow,
        testTag = type.toTestTag(),
        isLoading = isLive
    ) {
        Icon(
            imageVector = type.getIcon(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (isLive) Modifier.shimmer(true) else Modifier
        )
    }
}

/**
 * Expanded activity pill for completed single activity.
 * Shows text like "Reasoned for 3.2s" or "Searched the Web".
 */
@Composable
private fun ExpandedActivityPill(
    item: ActivityItem,
    onClick: () -> Unit,
    position: PillPosition,
    connectsToBubbleBelow: Boolean
) {
    SinglePill(
        onClick = onClick,
        position = position,
        connectsToBubbleBelow = connectsToBubbleBelow,
        testTag = item.type.toTestTag()
    ) {
        Icon(
            imageVector = item.type.getIcon(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        val text = when (item.type) {
            ActivityType.REASONING -> {
                if (item.durationMs != null) {
                    "Reasoned for ${formatDuration(item.durationMs)}"
                } else {
                    "Reasoned"
                }
            }
            ActivityType.OCR -> {
                if (item.count > 1) {
                    stringResource(R.string.activity_pill_ocr_done_count, item.count)
                } else {
                    stringResource(R.string.activity_pill_ocr_done)
                }
            }
            ActivityType.SEARCH -> {
                if (item.count > 1) "Searched Ã—${item.count}" else "Searched the Web"
            }
            ActivityType.MEMORY_RECALL -> {
                if (item.count > 1) stringResource(R.string.activity_pill_memory_recalled_count, item.count)
                else stringResource(R.string.activity_pill_memory_recalled)
            }
            ActivityType.PYTHON -> {
                if (item.count > 1) "Ran Python Ã—${item.count}" else "Ran Python"
            }
            ActivityType.WORKSPACE -> {
                if (item.count > 1) "Used workspace x${item.count}" else "Used workspace"
            }
            ActivityType.SKILL -> {
                if (item.count > 1) "Managed skills x${item.count}" else "Managed skills"
            }
            ActivityType.MCP -> {
                if (item.count > 1) "MCP calls Ã—${item.count}" else "MCP"
            }
            ActivityType.LOADING_MODEL -> {
                "Loading model..."
            }
            ActivityType.TOOL_OTHER -> {
                if (item.count > 1) "Used tools Ã—${item.count}" else "Used tool"
            }
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compact activity pill for multiple activities.
 * Shows just icon + optional count.
 */
@Composable
private fun CompactActivityPill(
    item: ActivityItem,
    onClick: () -> Unit,
    position: PillPosition,
    connectsToBubbleBelow: Boolean,
    modifier: Modifier = Modifier,
    cornerSegment: Float = 1f,
) {
    SinglePill(
        onClick = onClick,
        position = position,
        connectsToBubbleBelow = connectsToBubbleBelow,
        modifier = modifier,
        testTag = item.type.toTestTag(),
        cornerSegment = cornerSegment,
    ) {
        Icon(
            imageVector = item.type.getIcon(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Show duration for reasoning only (no counts for tools)
        val text = when (item.type) {
            ActivityType.REASONING -> {
                item.durationMs?.let { formatDuration(it) }
            }
            ActivityType.OCR -> null
            ActivityType.MEMORY_RECALL -> null
            else -> null
        }
        
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Format milliseconds to a readable duration string like "2.3s"
 */
private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds < 10) {
        String.format("%.1fs", seconds)
    } else {
        String.format("%.0fs", seconds)
    }
}

// Keep old ActivityPill for compatibility, but redirect to new implementation
@Composable
fun ActivityPill(
    state: ActivityState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadii: GroupedCornerRadii = GroupedCornerRadii.Default,
) {
    ActivityPillRow(
        state = state,
        onClick = { onClick() },
        modifier = modifier,
        connectsToBubbleBelow = true
    )
}

/**
 * Corner radii for grouped bubbles/pills.
 * Allows different radii on each corner for the "grouped message" look.
 */
data class GroupedCornerRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
) {
    companion object {
        val Default = GroupedCornerRadii(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )
        
        /** For first item in a group (small bottom-left corner) */
        fun first(largeRadius: Dp = 20.dp, smallRadius: Dp = 6.dp) = GroupedCornerRadii(
            topStart = largeRadius,
            topEnd = largeRadius,
            bottomStart = smallRadius,
            bottomEnd = largeRadius
        )
        
        /** For middle item in a group (small top-left and bottom-left corners) */
        fun middle(largeRadius: Dp = 20.dp, smallRadius: Dp = 6.dp) = GroupedCornerRadii(
            topStart = smallRadius,
            topEnd = largeRadius,
            bottomStart = smallRadius,
            bottomEnd = largeRadius
        )
        
        /** For last item in a group (small top-left corner) */
        fun last(largeRadius: Dp = 20.dp, smallRadius: Dp = 6.dp) = GroupedCornerRadii(
            topStart = smallRadius,
            topEnd = largeRadius,
            bottomStart = largeRadius,
            bottomEnd = largeRadius
        )
        
        /** For single item (not grouped) */
        fun single(radius: Dp = 20.dp) = GroupedCornerRadii(
            topStart = radius,
            topEnd = radius,
            bottomStart = radius,
            bottomEnd = radius
        )
    }
}
