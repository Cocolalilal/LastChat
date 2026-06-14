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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.TtsAutoplayMode
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.SpontaneousMessageMode
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.currentAppLocale
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Advanced tab - Message formatting and custom request settings.
 */
@Composable
fun AssistantAdvancedSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val haptics = rememberPremiumHaptics()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onUpdate(assistant.copy(enableSpontaneous = true))
        }
    }
    var scheduleSelection by remember(
        assistant.id,
        assistant.notificationStartHour,
        assistant.notificationEndHour,
    ) {
        mutableStateOf(
            spontaneousScheduleSelectionFromHours(
                startHour = assistant.notificationStartHour,
                endHour = assistant.notificationEndHour,
            )
        )
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
        SettingsGroup(title = "TTS") {
            val autoplayOptions: List<TtsAutoplayMode?> = listOf(
                null,
                TtsAutoplayMode.WHILE_GENERATING,
                TtsAutoplayMode.OFF,
            )
            SettingGroupItem(
                title = "TTS Autoplay",
                subtitle = "Override automatic reading for this character",
                trailing = {
                    Select(
                        options = autoplayOptions,
                        selectedOption = assistant.ttsAutoplayMode,
                        onOptionSelected = { mode -> onUpdate(assistant.copy(ttsAutoplayMode = mode)) },
                        optionToString = { mode ->
                            when (mode) {
                                null -> if (settings.ttsAutoplayMode == TtsAutoplayMode.OFF) {
                                    "Use global: Off"
                                } else {
                                    "Use global: On"
                                }
                                TtsAutoplayMode.OFF -> "Off"
                                TtsAutoplayMode.AFTER_GENERATION,
                                TtsAutoplayMode.WHILE_GENERATING -> "On"
                            }
                        },
                    )
                }
            )
        }

        SettingsGroup(title = stringResource(R.string.assistant_advanced_spontaneous_messaging)) {
            SettingGroupItem(
                title = stringResource(R.string.assistant_advanced_enable_spontaneous),
                subtitle = stringResource(R.string.assistant_advanced_enable_spontaneous_desc),
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
                val scheduleStartHour = scheduleSelection.startHour
                SettingGroupInputItem(
                    title = stringResource(R.string.assistant_advanced_delivery_schedule),
                    subtitle = stringResource(R.string.assistant_advanced_delivery_schedule_desc)
                ) {
                    Text(
                        text = buildSpontaneousWindowSummary(context, scheduleSelection),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Tag {
                            Text(
                                stringResource(
                                    R.string.assistant_advanced_schedule_starts,
                                    formatHourLabel(scheduleStartHour)
                                )
                            )
                        }
                        Tag(type = TagType.SUCCESS) {
                            Text(buildSpontaneousScheduleStatusLabel(context, scheduleSelection))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Tag(type = TagType.INFO) {
                            Text(
                                stringResource(
                                    R.string.assistant_advanced_schedule_ends,
                                    formatScheduleEndLabel(context, scheduleSelection)
                                )
                            )
                        }
                    }
                    RangeSlider(
                        value = scheduleSelection.toSliderValues(),
                        onValueChange = { newRange ->
                            scheduleSelection = normalizeSpontaneousScheduleSelection(
                                rawStart = newRange.start.roundToInt(),
                                rawEnd = newRange.endInclusive.roundToInt(),
                            )
                        },
                        onValueChangeFinished = {
                            val (newStartHour, newEndHour) =
                                spontaneousScheduleSelectionToHours(scheduleSelection)
                            if (
                                newStartHour != assistant.notificationStartHour ||
                                newEndHour != assistant.notificationEndHour
                            ) {
                                haptics.perform(HapticPattern.Selection)
                                onUpdate(
                                    assistant.copy(
                                        notificationStartHour = newStartHour,
                                        notificationEndHour = newEndHour,
                                    )
                                )
                            }
                        },
                        valueRange = SPONTANEOUS_SCHEDULE_SLIDER_MIN.toFloat()..
                            SPONTANEOUS_SCHEDULE_SLIDER_MAX.toFloat(),
                        steps = SPONTANEOUS_SCHEDULE_SLIDER_STEPS,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatHourLabel(0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.assistant_advanced_next_day,
                                formatHourLabel(23)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.assistant_advanced_schedule_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }


            AnimatedVisibility(
                visible = assistant.enableSpontaneous,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SettingGroupItem(
                    title = stringResource(R.string.assistant_advanced_message_type),
                    subtitle = stringResource(R.string.assistant_advanced_message_type_desc),
                    trailing = {
                        Select(
                            modifier = Modifier.width(160.dp),
                            options = SpontaneousMessageMode.entries.toList(),
                            selectedOption = assistant.spontaneousMessageMode,
                            onOptionSelected = { mode ->
                                onUpdate(assistant.copy(spontaneousMessageMode = mode))
                            },
                            optionToString = { mode ->
                                when (mode) {
                                    SpontaneousMessageMode.BOTH -> stringResource(R.string.assistant_advanced_message_type_both)
                                    SpontaneousMessageMode.CONTINUE_ONLY -> stringResource(R.string.assistant_advanced_message_type_continue)
                                    SpontaneousMessageMode.NEW_ONLY -> stringResource(R.string.assistant_advanced_message_type_new)
                                }
                            },
                        )
                    }
                )
            }
            AnimatedVisibility(
                visible = assistant.enableSpontaneous,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val frequencyHours = frequencySlider.roundToInt().coerceIn(1, 24)
                SettingGroupInputItem(
                    title = stringResource(R.string.assistant_advanced_minimum_gap),
                    subtitle = stringResource(R.string.assistant_advanced_minimum_gap_desc),
                ) {
                    SpontaneousSliderSection(
                        title = stringResource(R.string.assistant_advanced_minimum_gap),
                        valueLabel = formatFrequencyLabel(context, frequencyHours),
                        description = stringResource(R.string.assistant_advanced_minimum_gap_desc),
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

        SettingsGroup(title = stringResource(R.string.assistant_advanced_message_formatting)) {
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
        SettingsGroup(title = stringResource(R.string.assistant_advanced_custom_request)) {
            androidx.compose.material3.Surface(
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

            androidx.compose.material3.Surface(
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
private fun SpontaneousSliderSection(
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
    Column(
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

internal data class SpontaneousScheduleSelection(
    val absoluteStart: Int,
    val absoluteEnd: Int,
) {
    val startHour: Int
        get() = absoluteStart.mod(24)

    val endHour: Int
        get() = absoluteEnd.mod(24)

    val spanHours: Int
        get() = (absoluteEnd - absoluteStart).coerceIn(1, 24)

    val isAllDay: Boolean
        get() = spanHours == 24

    val endsNextDay: Boolean
        get() = !isAllDay && absoluteEnd >= 24

    fun toSliderValues(): ClosedFloatingPointRange<Float> {
        return absoluteStart.toFloat()..absoluteEnd.toFloat()
    }
}

internal fun spontaneousScheduleSelectionFromHours(
    startHour: Int,
    endHour: Int,
): SpontaneousScheduleSelection {
    val normalizedStart = startHour.mod(24)
    val normalizedEnd = endHour.mod(24)
    val absoluteEnd = when {
        normalizedStart == normalizedEnd -> normalizedStart + 24
        normalizedStart < normalizedEnd -> normalizedEnd
        else -> normalizedEnd + 24
    }
    return normalizeSpontaneousScheduleSelection(
        rawStart = normalizedStart,
        rawEnd = absoluteEnd,
    )
}

internal fun normalizeSpontaneousScheduleSelection(
    rawStart: Int,
    rawEnd: Int,
): SpontaneousScheduleSelection {
    val clampedStart = rawStart.coerceIn(0, SPONTANEOUS_SCHEDULE_START_MAX)
    val minEnd = (clampedStart + 1).coerceAtMost(SPONTANEOUS_SCHEDULE_SLIDER_MAX)
    val maxEnd = (clampedStart + 24).coerceAtMost(SPONTANEOUS_SCHEDULE_SLIDER_MAX)
    val clampedEnd = rawEnd
        .coerceAtLeast(minEnd)
        .coerceAtMost(maxEnd)
    return SpontaneousScheduleSelection(
        absoluteStart = clampedStart,
        absoluteEnd = clampedEnd,
    )
}

internal fun spontaneousScheduleSelectionToHours(
    selection: SpontaneousScheduleSelection,
): Pair<Int, Int> {
    val normalizedSelection = normalizeSpontaneousScheduleSelection(
        rawStart = selection.absoluteStart,
        rawEnd = selection.absoluteEnd,
    )
    val startHour = normalizedSelection.startHour
    val endHour = if (normalizedSelection.isAllDay) {
        startHour
    } else {
        (startHour + normalizedSelection.spanHours).mod(24)
    }
    return startHour to endHour
}

private const val SPONTANEOUS_SCHEDULE_SLIDER_MIN = 0
private const val SPONTANEOUS_SCHEDULE_START_MAX = 23
private const val SPONTANEOUS_SCHEDULE_SLIDER_MAX = 47
private const val SPONTANEOUS_SCHEDULE_SLIDER_STEPS = 46

private fun formatHourLabel(hour: Int): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a", currentAppLocale())
    return LocalTime.of(hour.mod(24), 0).format(formatter)
}

private fun formatFrequencyLabel(context: android.content.Context, hours: Int): String {
    return if (hours == 1) {
        context.getString(R.string.assistant_advanced_frequency_hour)
    } else {
        context.getString(R.string.assistant_advanced_frequency_hours, hours)
    }
}

private fun formatScheduleEndLabel(
    context: android.content.Context,
    selection: SpontaneousScheduleSelection,
): String {
    val endLabel = formatHourLabel(selection.endHour)
    return if (selection.endsNextDay) {
        context.getString(R.string.assistant_advanced_next_day, endLabel)
    } else {
        endLabel
    }
}

private fun buildSpontaneousScheduleStatusLabel(
    context: android.content.Context,
    selection: SpontaneousScheduleSelection,
): String {
    return if (selection.isAllDay) {
        context.getString(R.string.assistant_advanced_all_day)
    } else {
        context.getString(
            R.string.assistant_advanced_duration,
            formatFrequencyLabel(context, selection.spanHours)
        )
    }
}

private fun buildSpontaneousWindowSummary(
    context: android.content.Context,
    selection: SpontaneousScheduleSelection,
): String {
    val startLabel = formatHourLabel(selection.startHour)
    val endLabel = formatScheduleEndLabel(context, selection)
    return if (selection.isAllDay) {
        context.getString(R.string.assistant_advanced_reach_out_any_time)
    } else {
        context.getString(R.string.assistant_advanced_reach_out_between, startLabel, endLabel)
    }
}
