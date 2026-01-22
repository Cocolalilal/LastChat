package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.toFixed

/**
 * Model tab - All model and generation-related settings.
 * Designed with cohesive SettingsGroup pattern.
 */
@Composable
fun AssistantModelSubPage(
    assistant: Assistant,
    providers: List<ProviderSetting>,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // MODELS GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Models") {
            // Chat Model (Primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.assistant_page_chat_model),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.assistant_page_chat_model_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModelSelector(
                        modelId = assistant.chatModelId,
                        providers = providers,
                        type = ModelType.CHAT,
                        onSelect = { onUpdate(assistant.copy(chatModelId = it.id)) },
                    )
                }
            }
            
            // Background Model
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Background Model",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "For notifications and background tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModelSelector(
                        modelId = assistant.backgroundModelId,
                        providers = providers,
                        type = ModelType.CHAT,
                        onSelect = { onUpdate(assistant.copy(backgroundModelId = it.id)) },
                    )
                }
            }
            
            // Summarizer Model
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Summarizer Model",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "For memory summarization and context refresh",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModelSelector(
                        modelId = assistant.summarizerModelId,
                        providers = providers,
                        type = ModelType.CHAT,
                        onSelect = { onUpdate(assistant.copy(summarizerModelId = it.id)) },
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // GENERATION GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Generation") {
            // Temperature
            val tempLabel = if (assistant.temperature != null) {
                val temp = assistant.temperature
                when (temp) {
                    in 0.0f..0.3f -> "Strict ($temp)"
                    in 0.3f..1.0f -> "Balanced ($temp)"
                    in 1.0f..1.5f -> "Creative ($temp)"
                    else -> "Chaotic ($temp)"
                }
            } else "Default"
            
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_temperature),
                subtitle = tempLabel,
                trailing = {
                    HapticSwitch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(temperature = if (enabled) 1.0f else null))
                        }
                    )
                }
            )
            
            // Temperature Slider
            AnimatedVisibility(
                visible = assistant.temperature != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = if (LocalDarkMode.current) 
                        MaterialTheme.colorScheme.surfaceContainerLow 
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Slider(
                            value = assistant.temperature ?: 1.0f,
                            onValueChange = { onUpdate(assistant.copy(temperature = it.toFixed(2).toFloatOrNull() ?: 0.6f)) },
                            valueRange = 0f..2f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentTemp = assistant.temperature ?: 1.0f
                            val tagType = when (currentTemp) {
                                in 0.0f..0.3f -> TagType.INFO
                                in 0.3f..1.0f -> TagType.SUCCESS
                                in 1.0f..1.5f -> TagType.WARNING
                                else -> TagType.ERROR
                            }
                            Tag(type = tagType) {
                                Text(when (currentTemp) {
                                    in 0.0f..0.3f -> stringResource(R.string.assistant_page_strict)
                                    in 0.3f..1.0f -> stringResource(R.string.assistant_page_balanced)
                                    in 1.0f..1.5f -> stringResource(R.string.assistant_page_creative)
                                    else -> stringResource(R.string.assistant_page_chaotic)
                                })
                            }
                        }
                    }
                }
            }

            // Top-P
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_top_p),
                subtitle = if (assistant.topP != null) "Enabled (${assistant.topP})" else "Default",
                trailing = {
                    HapticSwitch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(topP = if (enabled) 0.9f else null))
                        }
                    )
                }
            )

            // Top-P Slider
            AnimatedVisibility(
                visible = assistant.topP != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = if (LocalDarkMode.current) 
                        MaterialTheme.colorScheme.surfaceContainerLow 
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Slider(
                            value = assistant.topP ?: 0.9f,
                            onValueChange = { onUpdate(assistant.copy(topP = it.toFixed(2).toFloatOrNull() ?: 0.9f)) },
                            valueRange = 0f..1f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // OUTPUT GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Output") {
            // Stream Output
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_stream_output),
                subtitle = stringResource(R.string.assistant_page_stream_output_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.streamOutput,
                        onCheckedChange = { onUpdate(assistant.copy(streamOutput = it)) }
                    )
                }
            )

            // Thinking Budget
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_thinking_budget),
                subtitle = if (assistant.thinkingBudget != null && assistant.thinkingBudget > 0) 
                    "${assistant.thinkingBudget} tokens" 
                else 
                    "Disabled",
                trailing = {
                    ReasoningButton(
                        reasoningTokens = assistant.thinkingBudget ?: 0,
                        onUpdateReasoningTokens = { tokens ->
                            onUpdate(assistant.copy(thinkingBudget = tokens))
                        }
                    )
                }
            )

            // Max Tokens
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_max_tokens),
                subtitle = if (assistant.maxTokens != null) 
                    stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens) 
                else 
                    stringResource(R.string.assistant_page_max_tokens_no_token_limit),
                trailing = {
                    OutlinedTextField(
                        value = assistant.maxTokens?.toString() ?: "",
                        onValueChange = { text ->
                            val tokens = if (text.isBlank()) null else text.toIntOrNull()?.takeIf { it > 0 }
                            onUpdate(assistant.copy(maxTokens = tokens))
                        },
                        modifier = Modifier.width(100.dp),
                        placeholder = { Text("Auto") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
    }
}
