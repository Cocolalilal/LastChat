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
data class ReasoningRequestBehavior(
    val off: List<CustomBody> = emptyList(),
    val auto: List<CustomBody> = emptyList(),
    val low: List<CustomBody> = emptyList(),
    val medium: List<CustomBody> = emptyList(),
    val high: List<CustomBody> = emptyList(),
) {
    fun bodiesFor(level: ReasoningLevel): List<CustomBody> {
        return when (level) {
            ReasoningLevel.OFF -> off
            ReasoningLevel.AUTO -> auto
            ReasoningLevel.LOW -> low
            ReasoningLevel.MEDIUM -> medium
            ReasoningLevel.HIGH -> high
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

