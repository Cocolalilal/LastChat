
package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.UsedLorebookEntry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.tts.provider.TTSProviderSetting

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    showRegenerate: Boolean = true,
    onOpenActionSheet: () -> Unit,
    onEditLorebookEntry: ((UsedLorebookEntry) -> Unit)? = null,
    onModeClick: ((me.rerere.ai.ui.UsedMode) -> Unit)? = null,
    onMemoryClick: ((me.rerere.ai.ui.UsedMemory) -> Unit)? = null,
    ttsProviderOverride: TTSProviderSetting? = null,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting()
    var isPendingDelete by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf(false) }
    
    val usedEntries = message.usedLorebookEntries ?: emptyList()
    val usedModes = message.usedModes ?: emptyList()
    val usedMemories = message.usedMemories ?: emptyList()
    val hasContextSources = usedEntries.isNotEmpty() || usedModes.isNotEmpty() || usedMemories.isNotEmpty()
    val showContextStacks = effectiveDisplay.showContextStacks && hasContextSources

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000) // 3秒后自动取消
            isPendingDelete = false
        }
    }
    
    // Context sources sheet
    if (showContextSheet && hasContextSources) {
        ContextSourcesSheet(
            modes = usedModes,
            memories = usedMemories,
            entries = usedEntries,
            onModeClick = { mode ->
                showContextSheet = false
                onModeClick?.invoke(mode)
            },
            onMemoryClick = { memory ->
                showContextSheet = false
                onMemoryClick?.invoke(memory)
            },
            onEntryClick = { entry ->
                showContextSheet = false
                onEditLorebookEntry?.invoke(entry)
            },
            onDismissRequest = { showContextSheet = false }
        )
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Context stack indicator at the start
        if (showContextStacks) {
            ContextStackIndicator(
                modes = usedModes,
                memories = usedMemories,
                entries = usedEntries,
                onClick = { showContextSheet = true },
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        
        Icon(
            Icons.Rounded.ContentCopy, stringResource(R.string.copy), modifier = Modifier
                .clip(CircleShape)
                .clickable { context.copyMessageToClipboard(message) }
                .padding(8.dp)
                .size(16.dp)
        )

        if (showRegenerate) {
            Icon(
                Icons.Rounded.Refresh, stringResource(R.string.regenerate), modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRegenerate() }
                    .padding(8.dp)
                    .size(16.dp)
            )
        }

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            Icon(
                imageVector = if (isSpeaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.tts),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = isAvailable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (!isSpeaking) {
                                tts.speak(
                                    text = message.toContentText(),
                                    overrideSetting = ttsProviderOverride,
                                )
                            } else {
                                tts.stop()
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = if (isAvailable) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
            )
        }

        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.more_options),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = {
                        onOpenActionSheet()
                    }
                )
                .padding(8.dp)
                .size(16.dp)
        )

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )
    }
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val haptics = rememberPremiumHaptics()
    val isDarkMode = LocalDarkMode.current
    val groupContainerColor = if (isDarkMode) {
        androidx.compose.ui.graphics.Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
        .any { it.text.isNotBlank() }

    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MessageActionGroupItem(
                    label = stringResource(R.string.select_and_copy),
                    icon = { Icon(Icons.Rounded.SelectAll, null, modifier = Modifier.padding(4.dp)) },
                    position = MessageActionItemPosition.FIRST,
                    containerColor = groupContainerColor,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onDismissRequest()
                        onSelectAndCopy()
                    }
                )
                if (hasTextContent) {
                    MessageActionGroupItem(
                        label = stringResource(R.string.render_with_webview),
                        icon = { Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.padding(4.dp)) },
                        position = MessageActionItemPosition.MIDDLE,
                        containerColor = groupContainerColor,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            onDismissRequest()
                            onWebViewPreview()
                        }
                    )
                }
                MessageActionGroupItem(
                    label = stringResource(R.string.edit),
                    icon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.padding(4.dp)) },
                    position = MessageActionItemPosition.MIDDLE,
                    containerColor = groupContainerColor,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onDismissRequest()
                        onEdit()
                    }
                )
                MessageActionGroupItem(
                    label = stringResource(R.string.create_fork),
                    icon = { Icon(Icons.AutoMirrored.Rounded.CallSplit, null, modifier = Modifier.padding(4.dp)) },
                    position = MessageActionItemPosition.LAST,
                    containerColor = groupContainerColor,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onDismissRequest()
                        onFork()
                    }
                )
            }

            Spacer(modifier = Modifier.size(4.dp))

            // Delete
            Card(
                onClick = {
                    haptics.perform(HapticPattern.Thud)
                    onDismissRequest()
                    onDelete()
                },

                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.delete),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Message Info
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                if (model != null) {
                    Text(model.displayName)
                }
            }
        }
    }
}

private enum class MessageActionItemPosition {
    FIRST,
    MIDDLE,
    LAST,
    SINGLE
}

private fun groupedMessageActionShape(position: MessageActionItemPosition): RoundedCornerShape {
    return when (position) {
        MessageActionItemPosition.FIRST -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 10.dp,
            bottomEnd = 10.dp
        )
        MessageActionItemPosition.MIDDLE -> RoundedCornerShape(10.dp)
        MessageActionItemPosition.LAST -> RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        MessageActionItemPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
}

@Composable
private fun MessageActionGroupItem(
    label: String,
    icon: @Composable () -> Unit,
    position: MessageActionItemPosition,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(groupedMessageActionShape(position))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
