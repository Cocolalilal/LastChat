package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import kotlin.math.roundToInt

/**
 * Advanced tab - Message formatting and custom request settings.
 */
@Composable
fun AssistantAdvancedSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val haptics = rememberPremiumHaptics()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onUpdate(assistant.copy(enableSpontaneous = true))
        }
    }
    var startHourSlider by remember(assistant.id, assistant.notificationStartHour) {
        mutableFloatStateOf(assistant.notificationStartHour.toFloat())
    }
    var endHourSlider by remember(assistant.id, assistant.notificationEndHour) {
        mutableFloatStateOf(assistant.notificationEndHour.toFloat())
    }
    var frequencySlider by remember(assistant.id, assistant.notificationFrequencyHours) {
        mutableFloatStateOf(assistant.notificationFrequencyHours.coerceIn(1, 24).toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        SettingsGroup(title = "Spontaneous Messaging") {
            SettingGroupItem(
                title = "Enable Spontaneous Messages",
                subtitle = "Let this character message you on their own",
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableSpontaneous,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onUpdate(assistant.copy(enableSpontaneous = true))
                                }
                            } else {
                                onUpdate(assistant.copy(enableSpontaneous = false))
                            }
                        }
                    )
                }
            )

            AnimatedVisibility(
                visible = assistant.enableSpontaneous,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val startHour = startHourSlider.roundToInt().coerceIn(0, 23)
                val endHour = endHourSlider.roundToInt().coerceIn(0, 23)
                val frequencyHours = frequencySlider.roundToInt().coerceIn(1, 24)

                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (LocalDarkMode.current) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = AppShapes.CardMedium,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Timing",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = buildSpontaneousWindowSummary(startHour, endHour),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Tag {
                                    Text("Starts ${formatHourLabel(startHour)}")
                                }
                                Tag(type = TagType.INFO) {
                                    Text("Ends ${formatHourLabel(endHour)}")
                                }
                            }
                            Tag(type = TagType.SUCCESS) {
                                Text("Minimum gap ${formatFrequencyLabel(frequencyHours)}")
                            }
                        }
                    }

                    SpontaneousSliderCard(
                        title = "Start time",
                        valueLabel = formatHourLabel(startHour),
                        description = "Earliest local time this character is allowed to message you.",
                        sliderValue = startHourSlider,
                        onSliderValueChange = { startHourSlider = it.roundToInt().toFloat() },
                        onSliderValueFinished = {
                            val newHour = startHourSlider.roundToInt().coerceIn(0, 23)
                            if (newHour != assistant.notificationStartHour) {
                                haptics.perform(HapticPattern.Selection)
                                onUpdate(assistant.copy(notificationStartHour = newHour))
                            }
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        startLabel = "12 AM",
                        endLabel = "11 PM",
                    )

                    SpontaneousSliderCard(
                        title = "End time",
                        valueLabel = formatHourLabel(endHour),
                        description = "Latest local time this character can still send a spontaneous message.",
                        sliderValue = endHourSlider,
                        onSliderValueChange = { endHourSlider = it.roundToInt().toFloat() },
                        onSliderValueFinished = {
                            val newHour = endHourSlider.roundToInt().coerceIn(0, 23)
                            if (newHour != assistant.notificationEndHour) {
                                haptics.perform(HapticPattern.Selection)
                                onUpdate(assistant.copy(notificationEndHour = newHour))
                            }
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        startLabel = "12 AM",
                        endLabel = "11 PM",
                    )

                    SpontaneousSliderCard(
                        title = "Minimum gap",
                        valueLabel = formatFrequencyLabel(frequencyHours),
                        description = "Minimum cooldown between spontaneous messages from this character.",
                        sliderValue = frequencySlider,
                        onSliderValueChange = { frequencySlider = it.roundToInt().toFloat() },
                        onSliderValueFinished = {
                            val newFrequency = frequencySlider.roundToInt().coerceIn(1, 24)
                            if (newFrequency != assistant.notificationFrequencyHours) {
                                haptics.perform(HapticPattern.Selection)
                                onUpdate(assistant.copy(notificationFrequencyHours = newFrequency))
                            }
                        },
                        valueRange = 1f..24f,
                        steps = 22,
                        startLabel = "1h",
                        endLabel = "24h",
                    )
                }
            }
        }

        SettingsGroup(title = "Message Formatting") {
            MessageTemplateSettingsCard(
                assistant = assistant,
                onUpdate = onUpdate
            )

            MessageRegexSettingsCard(
                assistant = assistant,
                onUpdate = onUpdate
            )
        }

        // Custom request settings
        SettingsGroup(title = "Custom Request") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = AppShapes.CardMedium
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomHeaders(
                        headers = assistant.customHeaders,
                        onUpdate = { onUpdate(assistant.copy(customHeaders = it)) }
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = AppShapes.CardMedium
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomBodies(
                        customBodies = assistant.customBodies,
                        onUpdate = { onUpdate(assistant.copy(customBodies = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpontaneousSliderCard(
    title: String,
    valueLabel: String,
    description: String,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    startLabel: String,
    endLabel: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (LocalDarkMode.current) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = AppShapes.CardMedium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Tag(type = TagType.INFO) {
                    Text(valueLabel)
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = onSliderValueChange,
                onValueChangeFinished = onSliderValueFinished,
                valueRange = valueRange,
                steps = steps,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = startLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = endLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatHourLabel(hour: Int): String {
    val normalizedHour = hour.mod(24)
    val hourOfDay = when (val value = normalizedHour % 12) {
        0 -> 12
        else -> value
    }
    val meridiem = if (normalizedHour < 12) "AM" else "PM"
    return "$hourOfDay:00 $meridiem"
}

private fun formatFrequencyLabel(hours: Int): String {
    return if (hours == 1) {
        "1 hour"
    } else {
        "$hours hours"
    }
}

private fun buildSpontaneousWindowSummary(startHour: Int, endHour: Int): String {
    val startLabel = formatHourLabel(startHour)
    val endLabel = formatHourLabel(endHour)
    return when {
        startHour == endHour -> "This character can reach out any time of day."
        startHour < endHour -> "This character can reach out between $startLabel and $endLabel."
        else -> "This character can reach out between $startLabel and $endLabel, including overnight."
    }
}
