package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.context.smartInputBudget
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextManagementMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.effectiveContextManagementMode
import me.rerere.rikkahub.data.model.restorePreviousContextCheckpoint
import me.rerere.rikkahub.service.ContextCompressionStatus
import me.rerere.rikkahub.service.ContextManagementActivity
import me.rerere.rikkahub.service.previewContextCompression
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.ui.pages.assistant.detail.title
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AppSpacing

/** A dismissible control surface; the application/worker owns optimization, never this sheet. */
@Composable
fun ContextManagementSheet(
    conversation: Conversation,
    assistant: Assistant,
    model: Model?,
    usage: me.rerere.ai.context.ContextUsageBreakdown? = null,
    status: ContextCompressionStatus?,
    onOptimize: () -> Unit,
    onCancel: () -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit,
    haptics: PremiumHaptics = rememberPremiumHaptics(),
    details: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val preview by produceState<me.rerere.rikkahub.service.ContextCompressionPreview?>(
        null, conversation, assistant, model,
    ) {
        value = withContext(Dispatchers.Default) {
            model?.let { previewContextCompression(conversation, assistant, it) }
        }
    }
    val plannedPreview = preview
    val running = status?.isRunning == true
    var showDetails by remember { mutableStateOf(false) }
    var showCheckpoint by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember(conversation.id, conversation.contextSummary) {
        mutableStateOf(conversation.contextSummary.orEmpty().replace(
            Regex(""" <!-- lastchat:coverage=\d+\.\.\d+ -->"""), "",
        ))
    }
    val summary = conversation.contextSummary.orEmpty()
    val activeMessages = me.rerere.rikkahub.service.activeContextMessages(conversation)
    val estimatedUsed = remember(conversation, model, assistant.systemPrompt) {
        ContextTokenEstimator.messagesTokens(activeMessages, model) +
            ContextTokenEstimator.textTokens(summary + assistant.systemPrompt, model)
    }
    val used = usage?.usedTokens ?: estimatedUsed
    val budget = usage?.usableInputTokens ?: model?.let { smartInputBudget(it, assistant.maxTokens) }
    val progress = if (budget != null && budget > 0) (used.toFloat() / budget).coerceIn(0f, 1f) else 0f
    val mode = if (assistant.effectiveContextManagementMode == ContextManagementMode.AUTOMATIC) "Automatic" else "Manual"
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.BottomSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Row(
                Modifier.fillMaxWidth().padding(start = AppSpacing.lg, end = AppSpacing.md, top = AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = AppShapes.IconButton, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Chat context", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$mode · ${assistant.contextCompressionStyle.title()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close context") }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                ContextPanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text("Context used", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (budget == null) "Adaptive" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                    Text(
                        "Older exchanges become a checkpoint. Recent exchanges stay in full. " +
                            "Your original chat is always kept.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedContent(targetState = status?.activity, label = "compression_sheet_status") { activity ->
                    if (running || activity == ContextManagementActivity.FAILED ||
                        (activity == ContextManagementActivity.COMPLETE && (status?.tokensSaved ?: 0) > 0)
                    ) {
                        ContextPanel {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                if (running) {
                                    if (me.rerere.rikkahub.ui.motion.LocalMotionPolicy.current.reduceMotion) {
                                        LoadingIndicator(progress = { 0f }, modifier = Modifier.size(28.dp))
                                    } else LoadingIndicator(Modifier.size(28.dp))
                                }
                                Text(
                                    when (activity) {
                                        ContextManagementActivity.QUEUED -> "Queued in the background"
                                        ContextManagementActivity.WAITING -> "Ready after the current reply"
                                        ContextManagementActivity.FAILED -> "Context was kept unchanged"
                                        ContextManagementActivity.COMPLETE -> "Context optimized"
                                        else -> "Optimizing context"
                                    }, style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Text(
                                when {
                                    activity == ContextManagementActivity.FAILED -> status?.errorResId?.let {
                                        context.getString(it, *status.errorArgs.toTypedArray())
                                    } ?: "Couldn't finish this time. You can try again."
                                    running && (status?.totalBatches ?: 0) > 1 ->
                                        "${status?.completedBatches} of ${status?.totalBatches} sections finished. You can keep chatting."
                                    running -> "You can close this sheet and keep chatting."
                                    else -> "About ${status?.tokensSaved?.let(::contextTokenLabel)} tokens freed."
                                }, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!running) {
                    Text(
                        when {
                            model == null -> "Choose a chat model to optimize context."
                            plannedPreview == null -> "Your recent conversation is already protected. There's nothing to optimize yet."
                            else -> "Ready to optimize ${plannedPreview.messagesToCompress} older messages while keeping " +
                                "${plannedPreview.recentExchanges} recent exchanges in full."
                        }, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.isNotBlank()) {
                    ContextPanel {
                        ContextDisclosure("Conversation checkpoint", showCheckpoint) { showCheckpoint = !showCheckpoint }
                        ExpandableContent(visible = showCheckpoint) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                if (editing) {
                                    OutlinedTextField(
                                        value = draft, onValueChange = { draft = it }, minLines = 4, maxLines = 10,
                                        shape = AppShapes.InputField, modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Checkpoint") },
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                        FilledTonalButton(onClick = {
                                            onUpdateConversation(conversation.copy(
                                                contextSummary = draft.trim().takeIf { it.isNotBlank() },
                                                contextSummaryUpToIndex = if (draft.isBlank()) -1 else conversation.contextSummaryUpToIndex,
                                                lastRefreshTime = maxOf(System.currentTimeMillis(), conversation.lastRefreshTime + 1),
                                            ))
                                            editing = false
                                        }, enabled = !running) { Text("Save checkpoint") }
                                        TextButton(onClick = {
                                            draft = summary.replace(Regex(""" <!-- lastchat:coverage=\d+\.\.\d+ -->"""), "")
                                            editing = false
                                        }) { Text("Discard") }
                                    }
                                } else {
                                    SelectionContainer {
                                        Text(
                                            summary.replace(Regex("<!-- lastchat:coverage=\\d+\\.\\.\\d+ -->"), "")
                                                .takeLast(16_000),
                                            style = MaterialTheme.typography.bodyMedium, maxLines = 12,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(onClick = { editing = true }, enabled = !running) { Text("Edit checkpoint") }
                                }
                                TextButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        onUpdateConversation(restorePreviousContextCheckpoint(conversation))
                                    }, enabled = !running,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Undo, null, modifier = Modifier.size(18.dp))
                                    Text(if (summary.contains("<!-- lastchat:coverage=")) " Undo last optimization" else " Restore original context")
                                }
                            }
                        }
                    }
                }
                ContextPanel {
                    ContextDisclosure("Context details", showDetails) { showDetails = !showDetails }
                    ExpandableContent(visible = showDetails) {
                        if (details != null) details() else {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text("Estimated context: ${contextTokenLabel(used)} tokens", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Response space is reserved separately. Attachments and tools also use context.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                Modifier.fillMaxWidth().padding(AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) {
                    TextButton(onClick = onCancel) { Text("Stop optimization") }
                    FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Keep chatting") }
                } else {
                    Button(
                        enabled = preview != null && !editing,
                        onClick = { haptics.perform(HapticPattern.Pop); onOptimize() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (status?.activity == ContextManagementActivity.FAILED) "Try again" else "Optimize context") }
                }
            }
        }
    }
}

@Composable
private fun ContextPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = AppShapes.CardMedium) {
        Column(
            Modifier.fillMaxWidth().padding(AppSpacing.md).animateContentSize(
                animationSpec = if (me.rerere.rikkahub.ui.motion.LocalMotionPolicy.current.reduceMotion)
                    androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(
                        dampingRatio = 0.85f, stiffness = 260f,
                    ),
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm), content = content,
        )
    }
}

@Composable
private fun ContextDisclosure(title: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = AppShapes.ListItem) {
        Row(Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Collapse" else "Expand")
        }
    }
}

private fun contextTokenLabel(tokens: Int): String = when {
    tokens >= 10_000 -> "${tokens / 1_000}k"
    else -> tokens.toString()
}
