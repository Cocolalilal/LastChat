package me.rerere.rikkahub.data.ai.transformers

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant

data class PlaceholderRuntimeValues(
    val currentDate: String,
    val currentTime: String,
    val currentDateTime: String,
    val timezone: String,
    val systemVersion: String,
    val deviceInfo: String,
    val batteryLevel: String,
    val location: String,
)

data class PlaceholderCtx(
    val settingsStore: SettingsStore,
    val model: Model,
    val assistant: Assistant,
    val runtimeValues: PlaceholderRuntimeValues,
)

interface PlaceholderProvider {
    val placeholders: Map<String, PlaceholderInfo>
}

data class PlaceholderInfo(
    val displayName: @Composable () -> Unit,
    val resolver: (PlaceholderCtx) -> String,
)

class PlaceholderBuilder {
    private val placeholders = mutableMapOf<String, PlaceholderInfo>()

    fun placeholder(
        key: String,
        displayName: @Composable () -> Unit,
        resolver: (PlaceholderCtx) -> String,
    ) {
        placeholders[key] = PlaceholderInfo(displayName, resolver)
    }

    fun build(): Map<String, PlaceholderInfo> = placeholders.toMap()
}

fun buildPlaceholders(block: PlaceholderBuilder.() -> Unit): Map<String, PlaceholderInfo> {
    return PlaceholderBuilder().apply(block).build()
}

object DefaultPlaceholderProvider : PlaceholderProvider {
    override val placeholders: Map<String, PlaceholderInfo> = buildPlaceholders {
        placeholder("cur_date", { Text(stringResource(R.string.placeholder_current_date)) }) {
            it.runtimeValues.currentDate
        }

        placeholder("cur_time", { Text(stringResource(R.string.placeholder_current_time)) }) {
            it.runtimeValues.currentTime
        }

        placeholder("cur_datetime", { Text(stringResource(R.string.placeholder_current_datetime)) }) {
            it.runtimeValues.currentDateTime
        }

        placeholder("model_name", { Text(stringResource(R.string.placeholder_model_name)) }) {
            it.model.displayName
        }

        placeholder("timezone", { Text(stringResource(R.string.placeholder_timezone)) }) {
            it.runtimeValues.timezone
        }

        placeholder("system_version", { Text(stringResource(R.string.placeholder_system_version)) }) {
            it.runtimeValues.systemVersion
        }

        placeholder("device_info", { Text(stringResource(R.string.placeholder_device_info)) }) {
            it.runtimeValues.deviceInfo
        }

        placeholder("battery_level", { Text(stringResource(R.string.placeholder_battery_level)) }) {
            it.runtimeValues.batteryLevel
        }

        placeholder("char", { Text(stringResource(R.string.placeholder_char)) }) {
            it.assistant.name.ifBlank { "assistant" }
        }

        placeholder("user", { Text(stringResource(R.string.placeholder_user)) }) {
            it.settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
        }

        placeholder("location", { Text(stringResource(R.string.placeholder_location)) }) {
            it.runtimeValues.location
        }
    }
}
