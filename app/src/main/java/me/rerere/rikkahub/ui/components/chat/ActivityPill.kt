package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Memory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.rikkahub.ui.modifier.shimmer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * State representing what the assistant is currently doing.
 */
sealed interface ActivityState {
    /** Waiting for first token - shows typing dots */
    data object Waiting : ActivityState
    
    /** Model is reasoning/thinking - shows timer */
    data class Reasoning(val startTimeMs: Long = System.currentTimeMillis()) : ActivityState
    
    /** Model is using a tool */
    data class ToolUse(
        val toolName: String,
        val displayName: String,
        val startTimeMs: Long = System.currentTimeMillis()
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
        val toolsUsed: List<String> = emptyList()
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
    is ActivityState.Reasoning -> "reasoning"
    is ActivityState.ToolUse -> "tool_${categorizeToolName(state.toolName)}"
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
    SEARCH,
    PYTHON,
    MCP,
    TOOL_OTHER
}

/**
 * Get the icon for an activity type.
 */
private fun ActivityType.getIcon(): ImageVector = when (this) {
    ActivityType.REASONING -> Icons.Rounded.Lightbulb
    ActivityType.SEARCH -> Icons.Rounded.Public
    ActivityType.PYTHON -> Icons.Rounded.Terminal
    ActivityType.MCP -> Icons.Rounded.Memory
    ActivityType.TOOL_OTHER -> Icons.Rounded.Build
}

/**
 * Get display text for an activity type (for expanded single pill).
 */
private fun ActivityType.getDisplayText(): String = when (this) {
    ActivityType.REASONING -> "Reasoned"
    ActivityType.SEARCH -> "Searched"
    ActivityType.PYTHON -> "Ran Python"
    ActivityType.MCP -> "MCP"
    ActivityType.TOOL_OTHER -> "Used tools"
}

/**
 * Categorize a tool name into activity type.
 */
internal fun categorizeToolName(toolName: String): ActivityType = when (toolName) {
    "search_web", "scrape_web" -> ActivityType.SEARCH
    "eval_python", "pip_install", "write_sandbox_file", 
    "read_sandbox_file", "list_sandbox_files", "delete_sandbox_file" -> ActivityType.PYTHON
    else -> if (toolName.startsWith("mcp_")) ActivityType.MCP else ActivityType.TOOL_OTHER
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
    
    // Group tools by type and count
    if (state.toolsUsed.isNotEmpty()) {
        state.toolsUsed
            .map { categorizeToolName(it) }
            .groupingBy { it }
            .eachCount()
            .forEach { (type, count) ->
                items.add(ActivityItem(type = type, count = count))
            }
    }
    
    return items
}

// Corner radius constants
private val LARGE_RADIUS = 20.dp
private val SMALL_RADIUS = 6.dp
private val PILL_HEIGHT = 36.dp

/**
 * Position of a pill in a row of pills.
 */
enum class PillPosition {
    SINGLE,     // Only one pill - fully rounded
    FIRST,      // First in row - rounded left, flat right
    MIDDLE,     // Middle pills - flat both sides
    LAST        // Last in row - flat left, rounded right
}

/**
 * A row of activity pills with Apple-like smooth animations.
 * 
 * During loading: Shows a single morphing pill (Waiting → Reasoning → Tool → etc.)
 * After completion: If multiple activities, reveals them with staggered fly-out animation
 */
@Composable
fun ActivityPillRow(
    state: ActivityState,
    onClick: (ActivityType?) -> Unit,
    modifier: Modifier = Modifier,
    connectsToBubbleBelow: Boolean = true,
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
    
    // Animated visibility for the entire pill row
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
            when (state) {
                is ActivityState.CompletedMultiple -> {
                    // Multiple activities - first pill morphs, others fly in
                    var othersCanAppear by remember { mutableStateOf(false) }
                    
                    activityItems.forEachIndexed { index, item ->
                        val position = when {
                            activityItems.size == 1 -> PillPosition.SINGLE
                            index == 0 -> PillPosition.FIRST
                            index == activityItems.lastIndex -> PillPosition.LAST
                            else -> PillPosition.MIDDLE
                        }
                        
                        if (index == 0) {
                            // First pill - always visible, triggers others
                            LaunchedEffect(Unit) {
                                delay(100L)
                                othersCanAppear = true
                            }
                            
                            // Use expanded pill when there's only one activity, compact otherwise
                            if (activityItems.size == 1) {
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
                            // Other pills - staggered fly-out
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(othersCanAppear) {
                                if (othersCanAppear) {
                                    delay(index * 50L)
                                    visible = true
                                }
                            }
                            
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(tween(150)) + slideInHorizontally(
                                    initialOffsetX = { -it / 2 },
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)
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
                
                else -> {
                    // Single pill for all other states (Waiting, Reasoning, ToolUse, Replying, CompletedSingle)
                    val clickType = when (state) {
                        is ActivityState.Reasoning -> ActivityType.REASONING
                        is ActivityState.ToolUse -> categorizeToolName(state.toolName)
                        is ActivityState.CompletedSingle -> state.type
                        else -> null
                    }
                    AnimatedSinglePill(
                        state = state,
                        onClick = { onClick(clickType) },
                        connectsToBubbleBelow = connectsToBubbleBelow
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
    onClick: () -> Unit,
    connectsToBubbleBelow: Boolean
) {
    // Animate corner radii for smooth transitions
    val topStartRadius by animateDpAsState(
        targetValue = LARGE_RADIUS,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "corner_top_start"
    )
    val topEndRadius by animateDpAsState(
        targetValue = LARGE_RADIUS,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "corner_top_end"
    )
    val bottomStartRadius by animateDpAsState(
        targetValue = if (connectsToBubbleBelow) SMALL_RADIUS else LARGE_RADIUS,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "corner_bottom_start"
    )
    val bottomEndRadius by animateDpAsState(
        targetValue = if (connectsToBubbleBelow) SMALL_RADIUS else LARGE_RADIUS,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "corner_bottom_end"
    )
    
    Surface(
        modifier = Modifier.height(PILL_HEIGHT),
        shape = RoundedCornerShape(
            topStart = topStartRadius,
            topEnd = topEndRadius,
            bottomStart = bottomStartRadius,
            bottomEnd = bottomEndRadius
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    ) {
        // AnimatedContent for smooth crossfade between DIFFERENT activity types
        // Use contentKey based on activity TYPE, not instance, so same-type activities don't transition
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                // Crossfade with slight scale for Apple-like feel
                (fadeIn(animationSpec = tween(200)) + 
                 scaleIn(initialScale = 0.92f, animationSpec = tween(200)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(150)) + 
                        scaleOut(targetScale = 0.92f, animationSpec = tween(150))
                    )
            },
            label = "pill_content",
            contentKey = { stateToKey(it) },  // Same type = same key = no transition
            modifier = Modifier.animateContentSize(
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)
            )
        ) { targetState ->
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
                    
                    is ActivityState.Reasoning -> {
                        ReasoningContent(startTimeMs = targetState.startTimeMs, isLive = true)
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
                            text = "Replying",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.shimmer(isLoading = true)
                        )
                    }
                    
                    is ActivityState.CompletedSingle -> {
                        // Show expanded content for the single activity
                        val item = ActivityItem(
                            type = targetState.type,
                            durationMs = targetState.durationMs,
                            count = targetState.count,
                            displayName = targetState.displayName
                        )
                        ExpandedActivityContent(item = item)
                    }
                    
                    // Hidden and CompletedMultiple are handled by parent - shouldn't reach here
                    else -> {}
                }
            }
        }
    }
}

/**
 * Content for reasoning pill (live timer).
 */
@Composable
private fun ReasoningContent(startTimeMs: Long, isLive: Boolean) {
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
        tint = MaterialTheme.colorScheme.secondary
    )
    Text(
        text = "Reasoning",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = if (isLive) Modifier.shimmer(true) else Modifier
    )
    Text(
        text = formatDuration(elapsedMs),
        style = MaterialTheme.typography.labelSmall,
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
        tint = MaterialTheme.colorScheme.secondary
    )
    Text(
        text = displayName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
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
        ActivityType.SEARCH -> "Searched the Web"
        ActivityType.PYTHON -> "Ran Python"
        ActivityType.MCP -> "MCP"
        ActivityType.TOOL_OTHER -> "Used tool"
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
    isLoading: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .height(PILL_HEIGHT)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 300f)),
        shape = getCornerRadii(position, connectsToBubbleBelow),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        isLoading = isLive
    ) {
        Icon(
            imageVector = Icons.Rounded.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = "Reasoning",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
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
        isLoading = isLive
    ) {
        Icon(
            imageVector = type.getIcon(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
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
        connectsToBubbleBelow = connectsToBubbleBelow
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
            ActivityType.SEARCH -> {
                if (item.count > 1) "Searched ×${item.count}" else "Searched the Web"
            }
            ActivityType.PYTHON -> {
                if (item.count > 1) "Ran Python ×${item.count}" else "Ran Python"
            }
            ActivityType.MCP -> {
                if (item.count > 1) "MCP calls ×${item.count}" else "MCP"
            }
            ActivityType.TOOL_OTHER -> {
                if (item.count > 1) "Used tools ×${item.count}" else "Used tool"
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
        modifier = modifier
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
