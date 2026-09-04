package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Message branch version switcher (< 1 / 3 >).
 * Shared across Android and iOS in Compose Multiplatform.
 */
@Composable
fun LastChatBranchSelector(
    currentIndex: Int,
    totalVersions: Int,
    onSelectVersion: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalVersions <= 1) return

    val canGoPrev = currentIndex > 0
    val canGoNext = currentIndex < totalVersions - 1

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Previous Version Arrow
        Icon(
            imageVector = Icons.Rounded.ChevronLeft,
            contentDescription = "Previous version",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .alpha(if (canGoPrev) 1f else 0.4f)
                .clickable(
                    enabled = canGoPrev,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelectVersion(currentIndex - 1) },
                )
                .padding(4.dp)
                .size(16.dp),
        )

        // Version counter (e.g. 1/3)
        Text(
            text = "${currentIndex + 1}/$totalVersions",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Next Version Arrow
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Next version",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .alpha(if (canGoNext) 1f else 0.4f)
                .clickable(
                    enabled = canGoNext,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelectVersion(currentIndex + 1) },
                )
                .padding(4.dp)
                .size(16.dp),
        )
    }
}
