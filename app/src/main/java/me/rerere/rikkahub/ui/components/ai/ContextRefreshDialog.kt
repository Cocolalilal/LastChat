package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics

enum class RefreshDialogState {
    CONFIRM,
    LOADING,
    SUCCESS,
    ERROR
}

@Composable
fun ContextRefreshDialog(
    conversation: Conversation,
    onRefresh: suspend () -> ChatService.ContextRefreshResult,
    onEditSummary: () -> Unit = {},
    onRevertSummary: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    var state by remember { mutableStateOf(RefreshDialogState.CONFIRM) }
    var errorResId by remember { mutableStateOf<Int?>(null) }
    var errorArgs by remember { mutableStateOf<List<Any>>(emptyList()) }
    var summarizedCount by remember { mutableIntStateOf(0) }
    var tokensSaved by remember { mutableIntStateOf(0) }
    
    val messageCount = conversation.currentMessages.size
    val estimatedTokens = conversation.currentMessages.sumOf { msg ->
        msg.parts.sumOf { part ->
            when (part) {
                is me.rerere.ai.ui.UIMessagePart.Text -> part.text.length / 4
                else -> 50
            }
        }
    }
    
    // Calculate messages since last summary
    val lastSummaryIndex = conversation.contextSummaryUpToIndex
    val messagesSinceSummary = if (lastSummaryIndex >= 0) {
        (messageCount - lastSummaryIndex - 1).coerceAtLeast(0)
    } else {
        messageCount // No previous summary
    }
    val hasPreviousSummary = !conversation.contextSummary.isNullOrBlank()

    // Smooth spring animation spec
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val displayedErrorMessage = errorResId?.let { resId ->
        context.getString(resId, *errorArgs.toTypedArray())
    } ?: stringResource(R.string.context_refresh_no_summarizer)

    AlertDialog(
        onDismissRequest = { if (state != RefreshDialogState.LOADING) onDismiss() },
        modifier = Modifier.padding(horizontal = 24.dp),  // Padding from screen edges
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        title = {
            AnimatedContent(
                targetState = state,
                label = "title_anim",
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                        scaleIn(initialScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                            scaleOut(targetScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                }
            ) { currentState ->
                when (currentState) {
                    RefreshDialogState.CONFIRM -> {
                        Text(
                            text = stringResource(R.string.context_refresh_confirm_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    RefreshDialogState.LOADING -> {
                        Text(
                            text = stringResource(R.string.context_refresh_loading),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    RefreshDialogState.SUCCESS -> {
                        Text(
                            text = stringResource(R.string.context_refresh_complete),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    RefreshDialogState.ERROR -> {
                        Text(
                            text = stringResource(R.string.context_refresh_error_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            AnimatedContent(
                targetState = state,
                label = "content_anim",
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                        scaleIn(initialScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                            scaleOut(targetScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                }
            ) { currentState ->
                when (currentState) {
                    RefreshDialogState.CONFIRM -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.context_refresh_confirm_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Keep last 2 messages (user + assistant exchange)
                            val messagesToKeep = 2
                            val lastIndexToSummarize = (messageCount - messagesToKeep - 1).coerceAtLeast(0)
                            
                            // Calculate actual messages to summarize (excluding kept messages)
                            val startIndex = if (hasPreviousSummary && lastSummaryIndex >= 0 && lastSummaryIndex < messageCount) {
                                (lastSummaryIndex + 1).coerceAtMost(messageCount)
                            } else {
                                0
                            }
                            
                            val messagesToSummarize = if (startIndex <= lastIndexToSummarize) {
                                lastIndexToSummarize - startIndex + 1
                            } else {
                                0
                            }
                            
                            // Calculate tokens for messages that will be summarized
                            val messagesToProcess = if (startIndex <= lastIndexToSummarize && lastIndexToSummarize < conversation.currentMessages.size) {
                                conversation.currentMessages.subList(startIndex, (lastIndexToSummarize + 1).coerceAtMost(conversation.currentMessages.size))
                            } else {
                                emptyList()
                            }
                            val tokensToCleanUp = messagesToProcess.sumOf { msg ->
                                msg.parts.sumOf { part ->
                                    when (part) {
                                        is me.rerere.ai.ui.UIMessagePart.Text -> part.text.length / 4
                                        else -> 50
                                    }
                                }
                            }
                            
                            Text(
                                text = stringResource(
                                    R.string.context_refresh_summary_impact,
                                    messagesToSummarize,
                                    tokensToCleanUp
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Show previous summary if exists
                            if (hasPreviousSummary) {
                                Text(
                                    text = stringResource(R.string.context_refresh_previous_summary),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = conversation.contextSummary?.take(200) + if ((conversation.contextSummary?.length ?: 0) > 200) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4
                                )
                            }
                        }
                    }
                    RefreshDialogState.LOADING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LoadingIndicator()
                            Text(
                                text = stringResource(R.string.context_refresh_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    RefreshDialogState.SUCCESS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.context_refresh_cleaned, summarizedCount),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            if (tokensSaved > 0) {
                                Text(
                                    text = stringResource(R.string.context_refresh_tokens_saved, tokensSaved),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    RefreshDialogState.ERROR -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = displayedErrorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AnimatedContent(
                targetState = state,
                label = "button_anim",
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                }
            ) { currentState ->
                when (currentState) {
                    RefreshDialogState.CONFIRM -> {
                        Button(
                            onClick = {
                                state = RefreshDialogState.LOADING
                                scope.launch {
                                    val result = onRefresh()
                                    if (result.success) {
                                        summarizedCount = result.messagesSummarized
                                        tokensSaved = result.tokensSaved
                                        state = RefreshDialogState.SUCCESS
                                    } else {
                                        errorResId = result.errorResId
                                        errorArgs = result.errorArgs
                                        state = RefreshDialogState.ERROR
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.context_refresh_button))
                        }
                    }
                    RefreshDialogState.SUCCESS, RefreshDialogState.ERROR -> {
                        Button(onClick = onDismiss) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                    RefreshDialogState.LOADING -> {
                        // No button while loading - use empty box for animation
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
        },
        dismissButton = {
            AnimatedContent(
                targetState = state == RefreshDialogState.CONFIRM,
                label = "dismiss_anim",
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                }
            ) { showDismiss ->
                if (showDismiss) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasPreviousSummary) {
                            OutlinedButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    onEditSummary()
                                },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.context_refresh_edit_summary),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Thud)
                                    onRevertSummary()
                                },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = stringResource(R.string.context_refresh_revert_summary),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    )
}
