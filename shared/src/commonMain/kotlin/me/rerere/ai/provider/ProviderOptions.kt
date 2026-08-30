package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.ReasoningLevel

@Serializable
data class CustomHeader(
    val name: String,
    val value: String
)

@Serializable
data class CustomBody(
    val key: String,
    val value: JsonElement
)

@Serializable
enum class ReasoningModeType {
    @SerialName("binary")
    BINARY,

    @SerialName("effort")
    EFFORT,

    @SerialName("budget")
    BUDGET,
}

@Serializable
data class ReasoningConfig(
    val type: ReasoningModeType = ReasoningModeType.EFFORT,
    @SerialName("supported_levels")
    val supportedLevels: List<String> = emptyList(),
    @SerialName("min_tokens")
    val minTokens: Int = 1024,
    @SerialName("max_tokens")
    val maxTokens: Int = 64_000,
    @SerialName("step_tokens")
    val stepTokens: Int = 1024,
    @SerialName("preset_tokens")
    val presetTokens: List<Int> = listOf(1024, 4096, 16_000, 32_000, 64_000),
)

@Serializable
data class ReasoningRequestBehavior(
    val off: List<CustomBody> = emptyList(),
    val auto: List<CustomBody> = emptyList(),
    val low: List<CustomBody> = emptyList(),
    val medium: List<CustomBody> = emptyList(),
    val high: List<CustomBody> = emptyList(),
    val max: List<CustomBody> = emptyList(),
) {
    fun bodiesFor(level: ReasoningLevel): List<CustomBody> {
        return when (level) {
            ReasoningLevel.OFF -> off
            ReasoningLevel.AUTO -> auto
            ReasoningLevel.LOW -> low
            ReasoningLevel.MEDIUM -> medium
            ReasoningLevel.HIGH -> high
            ReasoningLevel.MAX -> max.ifEmpty { high }
        }
    }
}

@Serializable
enum class OpenAICompatibilityMode {
    @SerialName("auto")
    AUTO,

    @SerialName("enabled")
    ENABLED,

    @SerialName("disabled")
    DISABLED,
}

