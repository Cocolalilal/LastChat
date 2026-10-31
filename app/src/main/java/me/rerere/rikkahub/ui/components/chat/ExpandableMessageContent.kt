package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.longMessagePreviewEnd

internal data class ExpandableMessagePresentation(
    val visibleText: String,
    val expandable: Boolean,
    val hasMore: Boolean,
    val onToggle: () -> Unit,
)

/** Also shared with bubble counting, which must never parse the hidden remainder. */
@Composable
internal fun rememberExpandableMessagePresentation(
    content: String,
    streaming: Boolean = false,
): ExpandableMessagePresentation {
    val initialEnd = remember(content) {
        longMessagePreviewEnd(content, maxChars = 1_200, maxLines = 12)
    }
    if (streaming || initialEnd == content.length) {
        return ExpandableMessagePresentation(content, expandable = false, hasMore = false, onToggle = {})
    }
    var visibleEnd by rememberSaveable(content) { mutableIntStateOf(initialEnd) }
    val safeVisibleEnd = visibleEnd.coerceIn(initialEnd, content.length)
    val hasMore = safeVisibleEnd < content.length
    val visibleText = remember(content, safeVisibleEnd) { content.substring(0, safeVisibleEnd) }
    return ExpandableMessagePresentation(visibleText, expandable = true, hasMore = hasMore) {
        visibleEnd = if (hasMore) longMessagePreviewEnd(content, safeVisibleEnd) else initialEnd
    }
}

/** Truncate BEFORE Markdown parsing/measurement; clipping a full block still lays out all of it. */
@Composable
internal fun ExpandableMessageContent(
    content: String,
    modifier: Modifier = Modifier,
    fadeColor: Color,
    streaming: Boolean = false,
    presentation: ExpandableMessagePresentation? = null,
    render: @Composable (String) -> Unit,
) {
    val displayed = presentation ?: rememberExpandableMessagePresentation(content, streaming)
    if (!displayed.expandable) {
        render(displayed.visibleText)
        return
    }
    val haptics = rememberPremiumHaptics()
    Column(modifier) {
        Box(
            Modifier.drawWithContent {
                drawContent()
                if (displayed.hasMore) {
                    val fadeHeight = minOf(32.dp.toPx(), size.height)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, fadeColor),
                            startY = size.height - fadeHeight,
                            endY = size.height,
                        ),
                    )
                }
            }
        ) { render(displayed.visibleText) }
        TextButton(
            onClick = {
                haptics.perform(HapticPattern.Pop)
                displayed.onToggle()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (displayed.hasMore) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(if (displayed.hasMore) "Show more" else "Show less")
        }
    }
}
