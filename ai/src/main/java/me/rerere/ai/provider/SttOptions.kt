package me.rerere.ai.provider

import kotlinx.serialization.Serializable

@Serializable
data class SttOptions(
    val sampleRate: Int = 16000,
    val segmentDurationSec: Int = 30,
    val vadThreshold: Float = 0.5f,
    val enableItn: Boolean = false,
    val hotwords: List<String> = emptyList(),
    val language: String = "",
    val prompt: String = "",
    val responseFormat: String = "text",
    val temperature: Float = 0f,
    val preferOffline: Boolean = false,
    val partialResults: Boolean = false
)
