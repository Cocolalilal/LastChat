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
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.VoiceSelector
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.toFixed
import me.rerere.tts.provider.TTSProviderSetting

/**
 * Model tab - All model and generation-related settings.
 * Designed with cohesive SettingsGroup pattern.
 */
@Composable
fun AssistantModelSubPage(
    assistant: Assistant,
    providers: List<ProviderSetting>,
    ttsProviders: List<TTSProviderSetting>,
    onUpdate: (Assistant) -> Unit
) {
    var maxTokensPending by remember { mutableStateOf(false) }

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
        SettingsGroup(title = stringResource(R.string.assistant_model_group_models)) {
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

            // Voice
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
                        text = "Voice",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Character voice for TTS. Leave empty to use the global default.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VoiceSelector(
                        voiceId = assistant.ttsVoiceId,
                        providers = ttsProviders,
                        allowClear = true,
                        onClear = { onUpdate(assistant.copy(ttsVoiceId = null)) },
                        onSelect = { onUpdate(assistant.copy(ttsVoiceId = it.id)) },
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
                        text = stringResource(R.string.assistant_model_background_model),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.assistant_model_background_model_desc),
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
            
        }

        // ═══════════════════════════════════════════════════════════════════
        // GENERATION GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.assistant_model_group_generation)) {
            // Temperature
            val tempLabel = if (assistant.temperature != null) {
                val temp = assistant.temperature
                when (temp) {
                    in 0.0f..0.3f -> stringResource(R.string.assistant_model_temp_strict_value, temp)
                    in 0.3f..1.0f -> stringResource(R.string.assistant_model_temp_balanced_value, temp)
                    in 1.0f..1.5f -> stringResource(R.string.assistant_model_temp_creative_value, temp)
                    else -> stringResource(R.string.assistant_model_temp_chaotic_value, temp)
                }
            } else stringResource(R.string.common_default)
            
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
                subtitle = if (assistant.topP != null) {
                    stringResource(R.string.common_enabled_value, assistant.topP.toString())
                } else {
                    stringResource(R.string.common_default)
                },
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
        SettingsGroup(title = stringResource(R.string.assistant_model_group_output)) {
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
                subtitle = when (ReasoningLevel.fromBudgetTokens(assistant.thinkingBudget)) {
                    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
                    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
                    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
                    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
                    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
                },
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AutoSaveIndicator(visible = maxTokensPending)
                        DebouncedTextField(
                            value = assistant.maxTokens?.toString() ?: "",
                            onValueChange = { text ->
                                val tokens = if (text.isBlank()) null else text.filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }
                                onUpdate(assistant.copy(maxTokens = tokens))
                            },
                            stateKey = "assistant_max_tokens_${assistant.id}",
                            modifier = Modifier.width(100.dp),
                            placeholder = stringResource(R.string.assistant_model_auto),
                            singleLine = true,
                            onPendingChange = { maxTokensPending = it },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            )
        }
    }
}
