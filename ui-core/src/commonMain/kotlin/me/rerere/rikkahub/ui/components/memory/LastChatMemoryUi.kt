package me.rerere.rikkahub.ui.components.memory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.AppShapes

enum class LastChatMemoryGroupPosition { Single, First, Middle, Last }

@Composable
fun LastChatMemoryModeCard(
    enabled: Boolean,
    title: String,
    description: String,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Surface(
        shape = AppShapes.CardMedium,
        color = background,
        modifier = modifier.animateContentSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "memoryModeName",
                ) { value ->
                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                AnimatedContent(
                    targetState = description,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "memoryModeDescription",
                ) { value ->
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun LastChatMemorySettingsItem(
    title: String,
    darkTheme: Boolean,
    position: LastChatMemoryGroupPosition,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onHaptic: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "memorySettingsScale",
    )
    val topCorner by animateDpAsState(
        if (position == LastChatMemoryGroupPosition.Single || position == LastChatMemoryGroupPosition.First) 24.dp else 10.dp,
        spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "memorySettingsTopCorner",
    )
    val bottomCorner by animateDpAsState(
        if (position == LastChatMemoryGroupPosition.Single || position == LastChatMemoryGroupPosition.Last) 24.dp else 10.dp,
        spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "memorySettingsBottomCorner",
    )
    Surface(
        onClick = {
            onClick?.let {
                onHaptic()
                it()
            }
        },
        enabled = onClick != null,
        color = if (darkTheme) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner,
        ),
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun LastChatMemoryRow(
    content: String,
    darkTheme: Boolean,
    position: LastChatMemoryGroupPosition,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteTitle: String,
    deleteLabel: String,
    cancelLabel: String,
    deleteConfirmation: String,
    modifier: Modifier = Modifier,
    typeLabel: String? = null,
    typeIsCore: Boolean = true,
    embeddingWarning: String? = null,
    embeddingWarningIsError: Boolean = false,
    onHaptic: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "memoryRowScale",
    )
    val topCorner by animateDpAsState(
        if (position == LastChatMemoryGroupPosition.Single || position == LastChatMemoryGroupPosition.First) 24.dp else 10.dp,
        spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "memoryRowTopCorner",
    )
    val bottomCorner by animateDpAsState(
        if (position == LastChatMemoryGroupPosition.Single || position == LastChatMemoryGroupPosition.Last) 24.dp else 10.dp,
        spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "memoryRowBottomCorner",
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(deleteTitle) },
            text = { Text("$deleteConfirmation\n\n\"${content.take(100)}${if (content.length > 100) "..." else ""}\"") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(deleteLabel) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(cancelLabel) }
            },
        )
    }
    Surface(
        onClick = onEdit,
        color = if (darkTheme) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner,
        ),
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedVisibility(
                    visible = typeLabel != null || embeddingWarning != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        typeLabel?.let {
                            Surface(
                                color = if (typeIsCore) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = if (typeIsCore) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        embeddingWarning?.let {
                            Surface(
                                color = if (embeddingWarningIsError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.tertiary,
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = if (embeddingWarningIsError) Color.White
                                    else MaterialTheme.colorScheme.onTertiary,
                                )
                            }
                        }
                    }
                }
                Text(content, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            if (onDelete != null) {
                IconButton(onClick = { onHaptic(); confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, deleteLabel)
                }
            }
        }
    }
}
