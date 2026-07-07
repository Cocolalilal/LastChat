package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Configuration for the system "digital assistant" overlay.
 *
 * When LastChat is set as the device default assistant, triggering the assist
 * gesture launches a floating overlay backed by these preferences.
 */
@Serializable
data class AssistantOverlayConfig(
    /** Assistant used by the overlay. null = use the app's default assistant. */
    val assistantId: Uuid? = null,
    /** Model override picked from the overlay's model picker. null = assistant's chat model. */
    val modelId: Uuid? = null,
    /** Start speech-to-text immediately when the overlay opens. */
    val autoStartStt: Boolean = true,
    /** Automatically send the message once STT finishes transcribing. */
    val autoSendOnSttFinish: Boolean = false,
    /** Read the assistant's reply aloud with the selected TTS voice. */
    val autoReadReply: Boolean = true,
    /** Attach a screenshot of the screen present when the assistant was summoned. */
    val attachScreenshot: Boolean = true,
)
