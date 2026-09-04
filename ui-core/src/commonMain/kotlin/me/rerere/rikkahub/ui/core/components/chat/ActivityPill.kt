package me.rerere.rikkahub.ui.core.components.chat

import me.rerere.rikkahub.ui.components.chat.TypingIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.rikkahub.ui.theme.AppShapes

enum class ActivityType {
    REASONING,
    SEARCH,
    MEMORY_RECALL,
    SKILL,
    MCP,
    TOOL_OTHER;

    fun displayName(): String = when (this) {
        REASONING -> "Reasoning"
        SEARCH -> "Web Search"
        MEMORY_RECALL -> "Memory"
        SKILL -> "Skill"
        MCP -> "MCP Tool"
        TOOL_OTHER -> "Tool"
    }

    fun icon(): ImageVector = when (this) {
        REASONING -> Icons.Rounded.Lightbulb
        SEARCH -> Icons.Rounded.Public
        MEMORY_RECALL -> Icons.Rounded.Memory
        SKILL -> Icons.Rounded.AutoAwesome
        MCP -> Icons.Rounded.Extension
        TOOL_OTHER -> Icons.Rounded.Build
    }
}

sealed interface ActivityState {
    data object Hidden : ActivityState
    data object Waiting : ActivityState
    data class Reasoning(
        val startTimeMs: Long,
        val reasoningText: String = "",
    ) : ActivityState
    data class ToolUse(
        val toolName: String,
        val displayName: String,
        val startTimeMs: Long,
        val type: ActivityType = ActivityType.TOOL_OTHER,
    ) : ActivityState
    data class CompletedSingle(
        val type: ActivityType,
        val durationMs: Long? = null,
        val displayName: String? = null,
    ) : ActivityState
    data class CompletedMultiple(
        val reasoningDurationMs: Long? = null,
        val activityTypes: List<ActivityType> = emptyList(),
    ) : ActivityState
}

data class TimelineItem(
    val title: String,
    val description: String? = null,
    val type: ActivityType,
    val durationMs: Long? = null,
    val isCompleted: Boolean = true,
)

/**
 * Animated floating Activity Pill row.
 * Displays dynamic live generation status (Typing, Stopwatch Timer for Reasoning, Shimmering Tool Ticker).
 * Tap opens the Activity Timeline Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityPillRow(
    state: ActivityState,
    modifier: Modifier = Modifier,
    timelineItems: List<TimelineItem> = emptyList(),
    onPillClick: (() -> Unit)? = null,
) {
    if (state is ActivityState.Hidden) return

    var showTimelineSheet by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = modifier
            .padding(vertical = 4.dp)
            .graphicsLayer {
                scaleX = if (isPressed) 0.96f else 1.0f
                scaleY = if (isPressed) 0.96f else 1.0f
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                if (timelineItems.isNotEmpty()) {
                    showTimelineSheet = true
                }
                onPillClick?.invoke()
            },
        shape = AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is ActivityState.Waiting -> {
                    TypingIndicator(
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Thinking...",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ActivityState.Reasoning -> {
                    var elapsedSec by remember(state.startTimeMs) { mutableLongStateOf(0L) }
                    LaunchedEffect(state.startTimeMs) {
                        while (isActive) {
                            val elapsed = (kotlin.time.Clock.System.now().toEpochMilliseconds() - state.startTimeMs) / 1000
                            elapsedSec = elapsed.coerceAtLeast(0L)
                            delay(200)
                        }
                    }

                    val infiniteTransition = rememberInfiniteTransition()
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    )

                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { alpha = pulseAlpha },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Thinking (${elapsedSec}s)...",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is ActivityState.ToolUse -> {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1400, easing = LinearEasing),
                        ),
                    )

                    Icon(
                        state.type.icon(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = rotation },
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        state.displayName.ifBlank { state.toolName },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is ActivityState.CompletedSingle -> {
                    Icon(
                        state.type.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    val label = when {
                        state.type == ActivityType.REASONING && state.durationMs != null -> {
                            val sec = (state.durationMs / 1000.0)
                            "Thought for ${((sec * 10).toInt() / 10.0)}s"
                        }
                        !state.displayName.isNullOrBlank() -> state.displayName
                        else -> state.type.displayName()
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ActivityState.CompletedMultiple -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.activityTypes.distinct().forEach { type ->
                            Icon(
                                type.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (state.reasoningDurationMs != null) {
                            val sec = (state.reasoningDurationMs / 1000.0)
                            Text(
                                "Thought for ${((sec * 10).toInt() / 10.0)}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {}
            }
        }
    }

    if (showTimelineSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTimelineSheet = false },
            sheetState = sheetState,
            shape = AppShapes.BottomSheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Activity Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { showTimelineSheet = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(timelineItems) { item ->
                        Surface(
                            shape = AppShapes.CardSmall,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (item.isCompleted) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            item.type.icon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (item.isCompleted) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            },
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (!item.description.isNullOrBlank()) {
                                        Text(
                                            item.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                if (item.durationMs != null) {
                                    Text(
                                        "${((item.durationMs / 100.0).toInt() / 10.0)}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
