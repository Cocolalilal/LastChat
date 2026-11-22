package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.data.memory.MemoryPointerParser
import me.rerere.rikkahub.data.memory.PointerSegment

@Composable
fun MemoryPointerText(
    content: String,
    validMemoryIds: Set<Int>,
    onPointerClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val segments = remember(content) { MemoryPointerParser.parseSegments(content) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chipTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    val annotated = remember(segments) {
        buildAnnotatedString {
            for (seg in segments) {
                when (seg) {
                    is PointerSegment.Text -> append(seg.value)
                    is PointerSegment.Pointer -> appendInlineContent(
                        id = "ptr_${seg.memoryId}",
                        alternateText = seg.rawTag,
                    )
                }
            }
        }
    }

    val distinctIds = remember(segments) {
        segments.filterIsInstance<PointerSegment.Pointer>().map { it.memoryId }.distinct()
    }

    val inlineContent = remember(distinctIds, validMemoryIds, chipTextStyle, density) {
        distinctIds.associate { memoryId ->
            val label = "#$memoryId"
            val measured = textMeasurer.measure(text = label, style = chipTextStyle)
            val widthSp = with(density) { (measured.size.width + 12.dp.toPx()).toSp() }
            val heightSp = with(density) { (measured.size.height + 4.dp.toPx()).coerceAtLeast(18.dp.toPx()).toSp() }
            val isAvailable = validMemoryIds.isEmpty() || memoryId in validMemoryIds

            "ptr_$memoryId" to InlineTextContent(
                placeholder = Placeholder(
                    width = widthSp,
                    height = heightSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                )
            ) {
                PointerCitationChip(
                    memoryId = memoryId,
                    isAvailable = isAvailable,
                    style = chipTextStyle,
                    onClick = { onPointerClick(memoryId) },
                )
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun PointerCitationChip(
    memoryId: Int,
    isAvailable: Boolean,
    style: TextStyle,
    onClick: () -> Unit,
) {
    val containerColor = if (isAvailable) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    }
    val contentColor = if (isAvailable) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val borderColor = if (isAvailable) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(0.5.dp, borderColor),
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = if (isAvailable) {
                    "Inspect source memory $memoryId"
                } else {
                    "Missing source memory $memoryId"
                }
            }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = "#$memoryId",
                style = if (isAvailable) style else style.copy(textDecoration = TextDecoration.LineThrough),
                maxLines = 1,
            )
        }
    }
}
