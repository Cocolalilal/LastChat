package me.rerere.rikkahub.ui.components.chat

import me.rerere.rikkahub.ui.core.components.chat.ActivityPillRow
import me.rerere.rikkahub.ui.core.components.chat.ActivityState
import me.rerere.rikkahub.ui.core.components.chat.TimelineItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.components.message.LastChatActionRow
import me.rerere.rikkahub.ui.components.message.LastChatToolCallCard
import me.rerere.rikkahub.ui.components.richtext.LastChatMarkdown

/**
 * Complete, cross-platform representation of a Conversation Message Turn.
 * Handles role-based alignments, markdown, code highlighting, tool call cards,
 * attachments, branching, and action sheets.
 */
@Composable
fun LastChatMessageTurn(
    role: MessageRole,
    text: String,
    modifier: Modifier = Modifier,
    senderName: String? = null,
    avatarUrl: String? = null,
    modelName: String? = null,
    parts: List<UIMessagePart> = emptyList(),
    toolCalls: List<ToolCallPresentation> = emptyList(),
    attachments: List<AttachmentPresentation> = emptyList(),
    branchCurrentIndex: Int = 0,
    branchTotalCount: Int = 1,
    onSelectBranch: ((Int) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isSpeakingTts: Boolean = false,
    onToggleTts: (() -> Unit)? = null,
    onAttachmentClick: ((String) -> Unit)? = null,
    fontSizeRatio: Float = 1.0f,
    showAssistantBubble: Boolean = true,
    activityState: ActivityState = ActivityState.Hidden,
    timelineItems: List<TimelineItem> = emptyList(),
) {
    val isOutgoing = role == MessageRole.USER
    var showActionToolbar by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Sender Header (Assistant name / Model badge)
        if (!isOutgoing && (!senderName.isNullOrBlank() || !modelName.isNullOrBlank())) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = senderName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }

                // Name
                if (!senderName.isNullOrBlank()) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Model Tag Badge
                if (!modelName.isNullOrBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        // Live Activity Status Pill (Waiting, Reasoning, Tools, Timeline)
        if (!isOutgoing && activityState !is ActivityState.Hidden) {
            ActivityPillRow(
                state = activityState,
                timelineItems = timelineItems,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }

        // Tool Calls Section (if any tools executed in this turn)
        if (toolCalls.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isOutgoing) 0.85f else 0.92f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                toolCalls.forEach { tool ->
                    LastChatToolCallCard(
                        toolName = tool.name,
                        arguments = tool.arguments,
                        result = tool.result,
                        isLoading = tool.isLoading,
                        errorMessage = tool.errorMessage,
                        onApprove = tool.onApprove,
                        onDeny = tool.onDeny,
                    )
                }
            }
        }

        // Attachments Row (Images, Media, Files)
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(vertical = 2.dp),
                horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
            ) {
                attachments.forEach { attachment ->
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onAttachmentClick?.invoke(attachment.uri) },
                    ) {
                        if (attachment.isImage) {
                            AsyncImage(
                                model = attachment.uri,
                                contentDescription = attachment.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = attachment.name.takeLast(12),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Message Bubble with GroupedMessageBubble and Rich Markdown
        if (text.isNotBlank()) {
            val baseTextStyle = MaterialTheme.typography.bodyLarge
            val adjustedStyle = if (fontSizeRatio != 1.0f) {
                baseTextStyle.copy(
                    fontSize = baseTextStyle.fontSize * fontSizeRatio,
                    lineHeight = baseTextStyle.lineHeight * fontSizeRatio,
                )
            } else baseTextStyle

            if (!isOutgoing && !showAssistantBubble) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showActionToolbar = !showActionToolbar },
                ) {
                    LastChatMarkdown(
                        content = text,
                        style = adjustedStyle,
                    )
                }
            } else {
                GroupedMessageBubble(
                    position = BubblePosition.SINGLE,
                    role = if (isOutgoing) BubbleRole.USER else BubbleRole.ASSISTANT,
                    modifier = Modifier
                        .fillMaxWidth(if (isOutgoing) 0.85f else 0.90f)
                        .widthIn(max = 600.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showActionToolbar = !showActionToolbar },
                ) {
                    LastChatMarkdown(
                        content = text,
                        style = adjustedStyle,
                    )
                }
            }
        }

        // Bottom Action Toolbar (Copy, Regenerate, Branch, Delete)
        AnimatedVisibility(
            visible = showActionToolbar,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LastChatActionRow(
                messageText = text,
                isAssistant = !isOutgoing,
                branchCurrentIndex = branchCurrentIndex,
                branchTotalCount = branchTotalCount,
                onSelectBranch = onSelectBranch,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onFork = onFork,
                onDelete = onDelete,
                isSpeakingTts = isSpeakingTts,
                onToggleTts = onToggleTts,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

data class ToolCallPresentation(
    val name: String,
    val arguments: String,
    val result: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val onApprove: (() -> Unit)? = null,
    val onDeny: (() -> Unit)? = null,
)

data class AttachmentPresentation(
    val uri: String,
    val name: String,
    val isImage: Boolean = true,
)
