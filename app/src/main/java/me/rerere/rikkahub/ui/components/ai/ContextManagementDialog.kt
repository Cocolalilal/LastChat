package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.context.ContextUsageBreakdown
import me.rerere.ai.context.smartInputBudget
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextManagementMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.effectiveContextManagementMode
import me.rerere.rikkahub.data.model.restorePreviousContextCheckpoint
import me.rerere.rikkahub.service.ContextCompressionPreview
import me.rerere.rikkahub.service.ContextCompressionStatus
import me.rerere.rikkahub.service.ContextManagementActivity
import me.rerere.rikkahub.service.activeContextMessages
import me.rerere.rikkahub.service.previewContextCompression
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatDialogContainerColor
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.pages.assistant.detail.title
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.AppSurface

/** Matches the app's floating dialogs; background work belongs to the service, never this popup. */
@Composable
fun ContextManagementDialog(
    conversation: Conversation,
    assistant: Assistant,
    model: Model?,
    usage: ContextUsageBreakdown? = null,
    status: ContextCompressionStatus?,
    onOptimize: () -> Unit,
    onCancel: () -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit,
    haptics: PremiumHaptics = rememberPremiumHaptics(),
    details: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val preview by produceState<ContextCompressionPreview?>(null, conversation, assistant, model) {
        value = withContext(Dispatchers.Default) {
            model?.let { previewContextCompression(conversation, assistant, it) }
        }
    }
    val plannedPreview = preview
    val running = status?.isRunning == true
    var showDetails by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val summary = conversation.contextSummary.orEmpty()
    val readableSummary = remember(summary) {
        summary.replace(Regex(""" <!-- lastchat:coverage=\d+\.\.\d+ -->"""), "")
    }
    var draft by remember(conversation.id, readableSummary) { mutableStateOf(readableSummary) }
    val estimatedUsed = remember(conversation, model, assistant.systemPrompt) {
        ContextTokenEstimator.messagesTokens(activeContextMessages(conversation), model) +
            ContextTokenEstimator.textTokens(summary + assistant.systemPrompt, model)
    }
    val used = usage?.usedTokens ?: estimatedUsed
    val budget = usage?.usableInputTokens ?: model?.let { smartInputBudget(it, assistant.maxTokens) }
    val progress = if (budget != null && budget > 0) (used.toFloat() / budget).coerceIn(0f, 1f) else 0f
    val mode = if (assistant.effectiveContextManagementMode == ContextManagementMode.AUTOMATIC) "Automatic" else "Manual"
    val containerColor = lastChatDialogContainerColor()
    val maxBodyHeight = LocalConfiguration.current.screenHeightDp.dp * 0.55f

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.lastChatBlurEffect(containerColor, AppShapes.Dialog),
        shape = AppShapes.Dialog,
        containerColor = containerColor,
        tonalElevation = AppSurface.TonalElevation,
        title = { Text("Chat context", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxBodyHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                Text(
                    "$mode · ${assistant.contextCompressionStyle.title()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Context used", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (budget == null) "Adaptive" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                if (running) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    ) {
                        if (LocalMotionPolicy.current.reduceMotion) {
                            LoadingIndicator(progress = { 0f }, modifier = Modifier.size(24.dp))
                        } else LoadingIndicator(Modifier.size(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                            Text(
                                when (status?.activity) {
                                    ContextManagementActivity.QUEUED -> "Waiting to optimize"
                                    ContextManagementActivity.WAITING -> "Ready after the current reply"
                                    else -> "Optimizing context"
                                }, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if ((status?.totalBatches ?: 0) > 1) {
                                    "${status?.completedBatches} of ${status?.totalBatches} sections finished"
                                } else "Running in the background",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text("Close this popup and keep chatting. Optimization will continue.")
                } else {
                    if (status?.activity == ContextManagementActivity.FAILED) {
                        Text("Couldn't finish this time. Your context is unchanged; you can try again.")
                    } else if (status?.activity == ContextManagementActivity.COMPLETE && status.tokensSaved > 0) {
                        Text("Context optimized · about ${contextTokenLabel(status.tokensSaved)} tokens freed.")
                    }
                    Text(when {
                        model == null -> "Choose a chat model to optimize context."
                        plannedPreview == null -> "Your recent conversation is protected. There's nothing to optimize yet."
                        else -> "Summarize ${plannedPreview.messagesToCompress} older messages. " +
                            "Keep ${plannedPreview.recentExchanges} recent exchanges in full."
                    })
                    Text("Your original messages are always kept.", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    if (summary.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ContextDisclosure("Saved summary", showSummary) {
                            haptics.perform(HapticPattern.Selection)
                            showSummary = !showSummary
                        }
                        ExpandableContent(visible = showSummary) {
                            Column(
                                Modifier.padding(bottom = AppSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            ) {
                                if (editing) {
                                    OutlinedTextField(
                                        value = draft, onValueChange = { draft = it }, minLines = 4, maxLines = 8,
                                        shape = AppShapes.InputField, modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Summary") }, enabled = !running,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                                        TextButton(onClick = {
                                            onUpdateConversation(conversation.copy(
                                                contextSummary = draft.trim().takeIf { it.isNotBlank() },
                                                contextSummaryUpToIndex = if (draft.isBlank()) -1
                                                    else conversation.contextSummaryUpToIndex,
                                                lastRefreshTime = maxOf(
                                                    System.currentTimeMillis(), conversation.lastRefreshTime + 1,
                                                ),
                                            ))
                                            editing = false
                                        }, enabled = !running) { Text("Save") }
                                        TextButton(onClick = { draft = readableSummary; editing = false }) {
                                            Text("Discard")
                                        }
                                    }
                                } else {
                                    SelectionContainer {
                                        Text(
                                            readableSummary.takeLast(16_000), maxLines = 12,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(onClick = { editing = true }, enabled = !running) {
                                        Text("Edit summary")
                                    }
                                }
                                TextButton(onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    onUpdateConversation(restorePreviousContextCheckpoint(conversation))
                                }, enabled = !running) {
                                    Text(if (summary.contains("<!-- lastchat:coverage=")) "Undo last optimization"
                                        else "Restore original context")
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ContextDisclosure("Context details", showDetails) {
                        haptics.perform(HapticPattern.Selection)
                        showDetails = !showDetails
                    }
                    ExpandableContent(visible = showDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            if (details != null) details() else {
                                Text("Estimated context: ${contextTokenLabel(used)} tokens")
                                Text(
                                    "Response space is reserved separately. Attachments and tools also use context.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (status?.activity == ContextManagementActivity.FAILED) {
                                status.errorResId?.let {
                                    Text(context.getString(it, *status.errorArgs.toTypedArray()),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (running) onDismiss() else {
                        haptics.perform(HapticPattern.Pop)
                        onOptimize()
                    }
                },
                enabled = running || (plannedPreview != null && !editing),
                shape = AppShapes.ButtonPill,
            ) {
                Text(when {
                    running -> "Keep chatting"
                    status?.activity == ContextManagementActivity.FAILED -> "Try again"
                    else -> "Optimize"
                })
            }
        },
        dismissButton = {
            TextButton(onClick = if (running) onCancel else onDismiss) {
                Text(if (running) "Stop" else "Close")
            }
        },
    )
}

@Composable
private fun ContextDisclosure(title: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = AppShapes.ListItem) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                if (expanded) "Collapse" else "Expand", modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun contextTokenLabel(tokens: Int): String = when {
    tokens >= 10_000 -> "${tokens / 1_000}k"
    else -> tokens.toString()
}
