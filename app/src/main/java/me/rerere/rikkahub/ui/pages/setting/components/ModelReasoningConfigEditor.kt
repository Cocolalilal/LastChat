package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ReasoningConfig
import me.rerere.ai.provider.ReasoningModeType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.resolveReasoningConfig
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput

private data class ReasoningPreset(
    val labelRes: Int,
    val config: ReasoningConfig,
)

private val reasoningPresets = listOf(
    ReasoningPreset(
        labelRes = R.string.setting_provider_page_reasoning_preset_openai,
        config = ReasoningConfig(
            type = ReasoningModeType.EFFORT,
            supportedLevels = listOf("off", "auto", "low", "medium", "high", "max"),
        ),
    ),
    ReasoningPreset(
        labelRes = R.string.setting_provider_page_reasoning_preset_gemini3,
        config = ReasoningConfig(
            type = ReasoningModeType.EFFORT,
            supportedLevels = listOf("off", "auto", "low", "medium", "high"),
        ),
    ),
    ReasoningPreset(
        labelRes = R.string.setting_provider_page_reasoning_preset_claude,
        config = ReasoningConfig(
            type = ReasoningModeType.BUDGET,
            minTokens = 1024,
            maxTokens = 64_000,
            stepTokens = 1024,
            presetTokens = listOf(1024, 4096, 8192, 16_000, 32_000, 64_000),
        ),
    ),
    ReasoningPreset(
        labelRes = R.string.setting_provider_page_reasoning_preset_qwen,
        config = ReasoningConfig(
            type = ReasoningModeType.BUDGET,
            minTokens = 1024,
            maxTokens = 32_768,
            stepTokens = 1024,
            presetTokens = listOf(1024, 4096, 8192, 16_000, 32_000),
        ),
    ),
    ReasoningPreset(
        labelRes = R.string.setting_provider_page_reasoning_preset_binary,
        config = ReasoningConfig(
            type = ReasoningModeType.BINARY,
            supportedLevels = listOf("off", "auto"),
        ),
    ),
)

private val effortLevels = listOf("off", "auto", "low", "medium", "high", "max")

@Composable
fun ModelReasoningConfigEditor(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    if (ModelAbility.REASONING !in model.abilities) return
    val config = model.reasoningConfig ?: resolveReasoningConfig(model)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_provider_page_reasoning_format),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.setting_provider_page_reasoning_format_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val types = ReasoningModeType.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = config.type == type,
                    onClick = {
                        onModelChange(
                            model.copy(
                                reasoningConfig = when (type) {
                                    ReasoningModeType.EFFORT -> ReasoningConfig(
                                        type = type,
                                        supportedLevels = config.supportedLevels.ifEmpty {
                                            listOf("off", "auto", "low", "medium", "high")
                                        },
                                    )
                                    ReasoningModeType.BINARY -> ReasoningConfig(
                                        type = type,
                                        supportedLevels = listOf("off", "auto"),
                                    )
                                    ReasoningModeType.BUDGET -> ReasoningConfig(
                                        type = type,
                                        minTokens = config.minTokens,
                                        maxTokens = config.maxTokens,
                                        stepTokens = config.stepTokens,
                                        presetTokens = config.presetTokens.ifEmpty {
                                            listOf(1024, 4096, 16_000, 32_000, 64_000)
                                        },
                                    )
                                }
                            )
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    label = {
                        Text(
                            text = stringResource(
                                when (type) {
                                    ReasoningModeType.EFFORT -> R.string.setting_provider_page_reasoning_format_effort
                                    ReasoningModeType.BUDGET -> R.string.setting_provider_page_reasoning_format_budget
                                    ReasoningModeType.BINARY -> R.string.setting_provider_page_reasoning_format_binary
                                }
                            )
                        )
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.setting_provider_page_reasoning_preset),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reasoningPresets.forEach { preset ->
                FilterChip(
                    selected = config == preset.config,
                    onClick = { onModelChange(model.copy(reasoningConfig = preset.config)) },
                    label = { Text(stringResource(preset.labelRes)) },
                )
            }
        }

        if (config.type != ReasoningModeType.BUDGET) {
            Text(
                text = stringResource(R.string.setting_provider_page_reasoning_levels),
                style = MaterialTheme.typography.titleSmall,
            )
            val available = if (config.type == ReasoningModeType.BINARY) {
                listOf("off", "auto")
            } else {
                effortLevels
            }
            MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                available.forEachIndexed { index, level ->
                    val selected = level in config.supportedLevels
                    SegmentedButton(
                        checked = selected,
                        onCheckedChange = { checked ->
                            val next = if (checked) {
                                (config.supportedLevels + level).distinct()
                            } else {
                                config.supportedLevels - level
                            }.ifEmpty { listOf(level) }
                            onModelChange(model.copy(reasoningConfig = config.copy(supportedLevels = next)))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, available.size),
                        label = { Text(level) },
                    )
                }
            }
        } else {
            FormItem(label = { Text(stringResource(R.string.setting_provider_page_reasoning_min_tokens)) }) {
                OutlinedNumberInput(
                    value = config.minTokens,
                    onValueChange = {
                        onModelChange(model.copy(reasoningConfig = config.copy(minTokens = it.coerceAtLeast(0))))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FormItem(label = { Text(stringResource(R.string.setting_provider_page_reasoning_max_tokens)) }) {
                OutlinedNumberInput(
                    value = config.maxTokens,
                    onValueChange = {
                        onModelChange(model.copy(reasoningConfig = config.copy(maxTokens = it.coerceAtLeast(config.minTokens))))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
