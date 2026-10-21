package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.ai.context.ContextCompressionStyle
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextManagementMode
import me.rerere.rikkahub.data.model.effectiveContextManagementMode
import me.rerere.rikkahub.data.model.withContextManagementMode
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AppSpacing

@Composable
fun AssistantContextManagementSubPage(
    assistant: Assistant,
    hasSummarizerModelConfigured: Boolean,
    onUpdate: (Assistant) -> Unit,
    onNavigateToLorebooks: () -> Unit,
    onNavigateToSummarizerSettings: () -> Unit,
) {
    var showAdvanced by remember(assistant.id) { mutableStateOf(false) }
    val mode = assistant.effectiveContextManagementMode
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Keep the conversation flowing. Older exchanges become a checkpoint when you need more room; " +
                "your recent conversation stays intact and the original messages remain in the chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        )
        SettingsGroup(title = "Context management") {
            ContextChoice(
                "Automatic", "Makes room when needed, quietly in the background.",
                mode == ContextManagementMode.AUTOMATIC,
            ) { onUpdate(assistant.withContextManagementMode(ContextManagementMode.AUTOMATIC)) }
            ContextChoice(
                "Only when I ask", "Use Optimize context whenever you want. Context limits still apply.",
                mode == ContextManagementMode.MANUAL,
            ) { onUpdate(assistant.withContextManagementMode(ContextManagementMode.MANUAL)) }
        }
        SettingsGroup(title = "What matters most") {
            ContextCompressionStyle.entries.forEach { style ->
                ContextChoice(
                    style.title(), style.description(), assistant.contextCompressionStyle == style,
                ) { onUpdate(assistant.copy(contextCompressionStyle = style)) }
            }
        }
        SettingsGroup(title = "Fine-tune this character") {
            SettingGroupItem(
                title = "More control",
                subtitle = "Recent exchanges and details to preserve",
                trailing = {
                    Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                },
                onClick = { showAdvanced = !showAdvanced },
            )
            ExpandableContent(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    SettingGroupInputItem(
                        title = "Keep recent exchanges",
                        subtitle = "Keep these exchanges in their original form. Adaptive follows the model's capacity. " +
                            "Very tight context limits may require keeping fewer.",
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            listOf<Int?>(null, 6, 12, 20).forEach { count ->
                                FilterChip(
                                    selected = assistant.contextRecentTurns == count,
                                    onClick = { onUpdate(assistant.copy(contextRecentTurns = count)) },
                                    label = { Text(count?.toString() ?: "Adaptive") },
                                    shape = AppShapes.ButtonPill,
                                )
                            }
                        }
                    }
                    SettingGroupInputItem(
                        title = "Details to preserve",
                        subtitle = "Tell the optimizer what to carry forward when it appears in the conversation.",
                    ) {
                        OutlinedTextField(
                            value = assistant.contextSummaryInstructions,
                            onValueChange = { onUpdate(assistant.copy(contextSummaryInstructions = it.take(4_000))) },
                            placeholder = { Text("For example: keep character relationships, decisions and unfinished plans") },
                            minLines = 3, maxLines = 6,
                            shape = AppShapes.InputField,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SettingGroupItem(
                        title = "Optimization model",
                        subtitle = if (hasSummarizerModelConfigured) "Uses your dedicated summarizer model"
                            else "Uses the chat model · a dedicated model is optional",
                        onClick = onNavigateToSummarizerSettings,
                    )
                }
            }
        }
        SettingsGroup(title = "Additional context") {
            SettingGroupItem(
                title = "Time awareness",
                subtitle = "Include the current time and relevant gaps in the conversation.",
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableTimeAwareness,
                        onCheckedChange = { onUpdate(assistant.copy(enableTimeAwareness = it)) },
                    )
                },
                onClick = { onUpdate(assistant.copy(enableTimeAwareness = !assistant.enableTimeAwareness)) },
            )
            SettingGroupItem(
                title = "Lorebooks",
                subtitle = "Bring in relevant character and world details when needed.",
                icon = { Icon(Icons.Rounded.Book, null) },
                onClick = onNavigateToLorebooks,
            )
        }
        Spacer(Modifier.height(AppSpacing.xxl))
    }
}

@Composable
private fun ContextChoice(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    SettingGroupItem(
        title = title,
        subtitle = description,
        trailing = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
    )
}

internal fun ContextCompressionStyle.title(): String = when (this) {
    ContextCompressionStyle.MORE_DETAIL -> "More detail"
    ContextCompressionStyle.BALANCED -> "Balanced"
    ContextCompressionStyle.MORE_ROOM -> "More room"
}

private fun ContextCompressionStyle.description(): String = when (this) {
    ContextCompressionStyle.MORE_DETAIL -> "Keep richer checkpoints and optimize later. Best when small details matter."
    ContextCompressionStyle.BALANCED -> "Keep a detailed handoff while making room for new replies."
    ContextCompressionStyle.MORE_ROOM -> "Use shorter checkpoints and leave more room for long-running conversations."
}
