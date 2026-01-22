package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.roundToInt

@Composable
fun AssistantContextManagementSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    onNavigateToLorebooks: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // LOREBOOKS
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.context_lorebooks_title)) {
            SettingGroupItem(
                title = stringResource(R.string.assistant_lorebooks_title),
                subtitle = stringResource(R.string.context_lorebooks_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = onNavigateToLorebooks
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // MESSAGE HISTORY
        // ═══════════════════════════════════════════════════════════════════
        
        SettingsGroup(title = stringResource(R.string.context_message_history_title)) {
            // Warning banner when message summarization is enabled but no summarizer model is set
            val needsSummarizerWarning = assistant.enableContextRefresh && assistant.summarizerModelId == null
            AnimatedVisibility(
                visible = needsSummarizerWarning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SummarizerWarningBanner(onClick = onNavigateToModels)
            }
            
            // 1. Message summarization toggle (first - enables manual summarization button)
            SettingGroupItem(
                title = "Message summarization",
                subtitle = "Summarize older messages in conversation",
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableContextRefresh,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(
                                enableContextRefresh = enabled,
                                // If disabling, also disable auto-summarize
                                autoRegenerateSummary = if (!enabled) false else assistant.autoRegenerateSummary
                            ))
                        }
                    )
                },
                onClick = {
                    val newEnabled = !assistant.enableContextRefresh
                    onUpdate(assistant.copy(
                        enableContextRefresh = newEnabled,
                        autoRegenerateSummary = if (!newEnabled) false else assistant.autoRegenerateSummary
                    ))
                }
            )
            
            // 2. Auto-summarize toggle (only visible when Message summarization is ON)
            AnimatedVisibility(
                visible = assistant.enableContextRefresh,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SettingGroupItem(
                    title = "Auto-summarize messages",
                    subtitle = "Automatically summarize when history limit is reached",
                    trailing = {
                        HapticSwitch(
                            checked = assistant.autoRegenerateSummary,
                            onCheckedChange = { enabled ->
                                onUpdate(assistant.copy(autoRegenerateSummary = enabled))
                            }
                        )
                    },
                    onClick = {
                        onUpdate(assistant.copy(autoRegenerateSummary = !assistant.autoRegenerateSummary))
                    }
                )
            }
            
            // 3. History limit slider (only visible when Auto-summarize is ON)
            AnimatedVisibility(
                visible = assistant.enableContextRefresh && assistant.autoRegenerateSummary,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val historyLimit = assistant.maxHistoryMessages ?: 10
                var sliderValue by remember(historyLimit) { mutableFloatStateOf(historyLimit.toFloat()) }
                
                SliderSettingCard(
                    title = "History limit",
                    value = sliderValue,
                    valueText = "${sliderValue.roundToInt()} messages",
                    description = "Number of messages before auto-summarization triggers",
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val newValue = sliderValue.roundToInt()
                        onUpdate(assistant.copy(
                            maxHistoryMessages = if (newValue <= 1) 10 else newValue
                        ))
                    },
                    valueRange = 5f..50f,
                    steps = 44
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SEARCH RESULTS
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.context_search_results_title)) {
            val maxSearchResults = assistant.maxSearchResultsRetained ?: 0
            var searchSliderValue by remember(maxSearchResults) { mutableFloatStateOf(maxSearchResults.toFloat()) }
            
            SliderSettingCard(
                title = if (searchSliderValue.roundToInt() == 0) {
                    stringResource(R.string.context_max_search_results_unlimited)
                } else {
                    stringResource(R.string.context_max_search_results, searchSliderValue.roundToInt())
                },
                value = searchSliderValue,
                valueText = "", // Title already shows the value
                description = stringResource(R.string.context_max_search_results_desc),
                onValueChange = { searchSliderValue = it },
                onValueChangeFinished = {
                    val newValue = searchSliderValue.roundToInt()
                    onUpdate(assistant.copy(
                        maxSearchResultsRetained = if (newValue == 0) null else newValue
                    ))
                },
                valueRange = 0f..50f,
                steps = 49
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SummarizerWarningBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Select a summarizer model in the Models tab",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SliderSettingCard(
    title: String,
    value: Float,
    valueText: String,
    description: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Surface(
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (valueText.isNotEmpty()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
