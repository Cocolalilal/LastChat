package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.versionSelectionIndices
import me.rerere.rikkahub.data.model.versionSelectionPosition

@Composable
fun ChatMessageBranchSelector(
    node: MessageNode,
    modifier: Modifier = Modifier,
    onUpdate: (MessageNode) -> Unit,
) {
    val versionIndices = remember(node.messages) {
        node.versionSelectionIndices()
    }
    val currentVersionPosition = remember(node.messages, node.selectIndex) {
        node.versionSelectionPosition()
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (versionIndices.size > 1 && currentVersionPosition >= 0) {
            val canGoPrev = currentVersionPosition > 0
            val canGoNext = currentVersionPosition < versionIndices.lastIndex

            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier
                    .clip(CircleShape)
                    .alpha(if (canGoPrev) 1f else 0.5f)
                    .clickable(
                        enabled = canGoPrev,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            val targetIndex = versionIndices.getOrNull(currentVersionPosition - 1)
                                ?: return@clickable
                            onUpdate(node.copy(selectIndex = targetIndex))
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp)
            )

            Text(
                text = "${currentVersionPosition + 1}/${versionIndices.size}",
                style = MaterialTheme.typography.bodySmall
            )

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier
                    .clip(CircleShape)
                    .alpha(if (canGoNext) 1f else 0.5f)
                    .clickable(
                        enabled = canGoNext,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            val targetIndex = versionIndices.getOrNull(currentVersionPosition + 1)
                                ?: return@clickable
                            onUpdate(node.copy(selectIndex = targetIndex))
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
            )
        }
    }
}
