package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Terminal

/**
 * An entry in the activity timeline.
 */
sealed interface TimelineEntry {
    val id: String
    
    /** Reasoning/thinking phase */
    data class Reasoning(
        override val id: String,
        val content: String,
        val durationMs: Long,
        val title: String? = null  // Optional title like "Analyzing the approach"
    ) : TimelineEntry
    
    /** Tool call */
    data class ToolCall(
        override val id: String,
        val toolName: String,
        val displayName: String,
        val arguments: String,
        val result: String?,
        val isLoading: Boolean = false
    ) : TimelineEntry
    
    /** Reply segment (when model alternates between thinking and replying) */
    data class Reply(
        override val id: String,
        val preview: String  // First ~100 chars of the reply
    ) : TimelineEntry
}

/**
 * Get icon for a timeline entry type.
 */
private fun getTimelineIcon(entry: TimelineEntry): ImageVector {
    return when (entry) {
        is TimelineEntry.Reasoning -> Icons.Rounded.Lightbulb
        is TimelineEntry.ToolCall -> when (entry.toolName) {
            "search_web", "scrape_web" -> Icons.Rounded.Public
            "eval_python", "pip_install", "write_sandbox_file",
            "read_sandbox_file", "list_sandbox_files", "delete_sandbox_file" -> Icons.Rounded.Terminal
            else -> Icons.Rounded.Build
        }
        is TimelineEntry.Reply -> Icons.Rounded.QuestionAnswer
    }
}

/**
 * Get display label for a timeline entry.
 */
private fun getTimelineLabel(entry: TimelineEntry): String {
    return when (entry) {
        is TimelineEntry.Reasoning -> entry.title ?: "Reasoning"
        is TimelineEntry.ToolCall -> entry.displayName
        is TimelineEntry.Reply -> "Reply"
    }
}

/**
 * Format duration in a human-readable way.
 */
private fun formatDuration(ms: Long): String? {
    if (ms <= 0) return null
    val seconds = ms / 1000.0
    return if (seconds < 10) {
        String.format("%.1fs", seconds)
    } else {
        String.format("%.0fs", seconds)
    }
}

/**
 * Activity timeline bottom sheet.
 * 
 * Shows a chronological list of all activities during the generation.
 * Each item can be expanded to show full content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTimelineSheet(
    entries: List<TimelineEntry>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Activity Timeline",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (entries.isEmpty()) {
                Text(
                    text = "No activity recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    entries.forEachIndexed { index, entry ->
                        TimelineEntryItem(
                            entry = entry,
                            isLast = index == entries.lastIndex
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single entry in the timeline.
 */
@Composable
private fun TimelineEntryItem(
    entry: TimelineEntry,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val hasExpandableContent = when (entry) {
        is TimelineEntry.Reasoning -> entry.content.isNotBlank()
        is TimelineEntry.ToolCall -> entry.arguments.isNotBlank() || entry.result != null
        is TimelineEntry.Reply -> false  // Reply preview is always shown, no expansion
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = hasExpandableContent) { expanded = !expanded }
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getTimelineIcon(entry),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Label
            Text(
                text = getTimelineLabel(entry),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            
            // Duration (for reasoning)
            if (entry is TimelineEntry.Reasoning) {
                formatDuration(entry.durationMs)?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            
            // Expand icon
            if (hasExpandableContent) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Expanded content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp, top = 8.dp)
            ) {
                when (entry) {
                    is TimelineEntry.Reasoning -> {
                        Text(
                            text = entry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    is TimelineEntry.ToolCall -> {
                        if (entry.arguments.isNotBlank()) {
                            Text(
                                text = "Arguments:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = entry.arguments,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        entry.result?.let { result ->
                            Text(
                                text = "Result:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = result.take(500) + if (result.length > 500) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    is TimelineEntry.Reply -> {
                        // No expanded content for reply
                    }
                }
            }
        }
        
        // Reply preview (always shown for Reply entries)
        if (entry is TimelineEntry.Reply) {
            Text(
                text = entry.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 44.dp, top = 4.dp)
            )
        }
    }
}
