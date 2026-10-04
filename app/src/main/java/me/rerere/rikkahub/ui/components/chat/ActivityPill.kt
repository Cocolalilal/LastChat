package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as graphicsLerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
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
// One critically damped clock. Size, corners, color, and the crossfade all read it,
// so a tap mid-flight reverses from the live value instead of restarting a second spring.
private const val PILL_MORPH_STIFFNESS = 240f
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
    LaunchedEffect(surfaceExpanded, chatAnimationsEnabled) {
        val target = if (surfaceExpanded) 1f else 0f
        if (!chatAnimationsEnabled) {
            progress.snapTo(target)
        } else {
            // Always retarget. A cancelled flight leaves targetValue stale, and skipping
            // animateTo there would freeze the pill between sizes.
            progress.animateTo(target, PILL_PROGRESS_SPEC)
        }
        if (!surfaceExpanded && progress.value == 0f) {
            retainedExpanded = null
        }
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
        PillMorphLayout(
            progress = morph,
            maxExpandedWidth = maxBubbleWidth,
            multiStepReveal = multiStepReveal,
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
                            revealProgress = if (multiStepReveal) morph else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is SinglePillContentState.ExpandedReasoning -> {
                        val activeReasoningState = targetContentState.state.let { targetState ->
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
                        ReasoningPreviewCard(
                            state = activeReasoningState,
                            active = surfaceExpanded,
                            isLive = targetContentState.isLive,
                            durationMs = targetContentState.durationMs,
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
                            var othersCanAppear by remember(key) { mutableStateOf(wasCompletedInitially) }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items.forEachIndexed { index, item ->
                                    val position = when {
                                        items.size == 1 -> PillPosition.SINGLE
                                        index == 0 -> PillPosition.FIRST
                                        index == items.lastIndex -> PillPosition.LAST
                                        else -> PillPosition.MIDDLE
                                    }
                                    
                                    if (index == 0) {
                                        LaunchedEffect(Unit) {
                                            if (!wasCompletedInitially) {
                                                delay(100L)
                                            }
                                            othersCanAppear = true
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
                                                connectsToBubbleBelow = connectsToBubbleBelow
                                            )
                                        }
                                    } else {
                                        if (wasCompletedInitially) {
                                            CompactActivityPill(
                                                item = item,
                                                onClick = { onClick(item.type) },
                                                position = position,
                                                connectsToBubbleBelow = connectsToBubbleBelow
                                            )
                                        } else {
                                            var visible by remember(key) { mutableStateOf(false) }
                                            LaunchedEffect(othersCanAppear) {
                                                if (othersCanAppear && !visible) {
                                                    delay(index * 50L)
                                                    visible = true
                                                }
                                            }
                                            
                                            AnimatedVisibility(
                                                visible = visible,
                                                enter = fadeIn(tween(150)) + slideInHorizontally(
                                                    initialOffsetX = { -it / 2 },
                                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                                ),
                                                exit = fadeOut(tween(100)) + slideOutHorizontally(targetOffsetX = { -it / 2 })
                                            ) {
                                                CompactActivityPill(
                                                    item = item,
                                                    onClick = { onClick(item.type) },
                                                    position = position,
                                                    connectsToBubbleBelow = connectsToBubbleBelow
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
            if (multiStepReveal) {
                // Panel stays fully opaque; entries ripple in from revealProgress.
                // Compact icon/label ease out over the early range, then back on close.
                val compactAlpha = multiStepCompactAlpha(t)
                if (expandedPlaceable != null && t > 0f) {
                    expandedPlaceable.placeWithLayer(0, 0) { alpha = 1f }
                }
                if (compactAlpha > 0.001f) {
                    compactPlaceable.placeWithLayer(0, 0) { alpha = compactAlpha }
                }
            } else {
                // Single-entry / reasoning: one crossfade on the same spring.
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
    onHeaderClick: (() -> Unit)? = null
) {
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

    val horizontal = 14.dp
    val vertical = 12.dp
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
        ActivityType.SEARCH -> "Searched the Web"
        ActivityType.MEMORY_RECALL -> stringResource(R.string.activity_pill_memory_recalled)
        ActivityType.PYTHON -> "Ran Python"
        ActivityType.WORKSPACE -> "Used workspace"
        ActivityType.SKILL -> "Managed skills"
        ActivityType.MCP -> "MCP"
        ActivityType.TOOL_OTHER -> "Used tool"
        ActivityType.LOADING_MODEL -> "Loading model"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Get corner radii based on pill position and whether it connects to bubble below.
 */
private fun getCornerRadii(
    position: PillPosition,
    connectsToBubbleBelow: Boolean
): RoundedCornerShape {
    val bottomLeft = if (connectsToBubbleBelow) SMALL_RADIUS else LARGE_RADIUS
    val bottomRight = if (connectsToBubbleBelow) SMALL_RADIUS else LARGE_RADIUS
    
    return when (position) {
        PillPosition.SINGLE -> RoundedCornerShape(
            topStart = LARGE_RADIUS,
            topEnd = LARGE_RADIUS,
            bottomStart = bottomLeft,
            bottomEnd = bottomRight
        )
        PillPosition.FIRST -> RoundedCornerShape(
            topStart = LARGE_RADIUS,
            topEnd = SMALL_RADIUS,
            bottomStart = bottomLeft,  // Flat to connect to bubble
            bottomEnd = SMALL_RADIUS
        )
        PillPosition.MIDDLE -> RoundedCornerShape(
            topStart = SMALL_RADIUS,
            topEnd = SMALL_RADIUS,
            bottomStart = SMALL_RADIUS,
            bottomEnd = SMALL_RADIUS
        )
        PillPosition.LAST -> RoundedCornerShape(
            topStart = SMALL_RADIUS,
            topEnd = LARGE_RADIUS,
            bottomStart = SMALL_RADIUS,
            bottomEnd = bottomRight  // Flat to connect to bubble
        )
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
    content: @Composable () -> Unit
) {
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val pillShape = getCornerRadii(position, connectsToBubbleBelow)

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
    modifier: Modifier = Modifier
) {
    SinglePill(
        onClick = onClick,
        position = position,
        connectsToBubbleBelow = connectsToBubbleBelow,
        modifier = modifier,
        testTag = item.type.toTestTag()
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
