package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Collapsible thinking/reasoning container for reasoning models (DeepSeek-R1, o1/o3, Claude Thinking).
 * Shared across Android and iOS in Compose Multiplatform.
 */
@Composable
fun LastChatThinkBlock(
    thinkingText: String,
    modifier: Modifier = Modifier,
    durationSeconds: Int? = null,
    isOngoing: Boolean = false,
    initiallyExpanded: Boolean = false,
    content: @Composable (() -> Unit)? = null,
) {
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded || isOngoing) }

    val headerText = when {
        isOngoing -> "Thinking..."
        durationSeconds != null && durationSeconds > 0 -> "Thought for ${durationSeconds}s"
        else -> "Thinking Process"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                ) {
                    if (content != null) {
                        content()
                    } else {
                        Text(
                            text = thinkingText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.alpha(0.9f),
                        )
                    }
                }
            }
        }
    }
}
