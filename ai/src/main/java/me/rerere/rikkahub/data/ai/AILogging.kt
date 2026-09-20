package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.StateFlow
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.common.log.PortableLogRing

sealed class AILogging {
    data class Generation(
        val params: TextGenerationParams,
        val messages: List<UIMessage>,
        val providerSetting: ProviderSetting,
        val stream: Boolean,
    ) : AILogging() {
        fun developerLine(): String {
            val modelId = params.model.modelId.ifBlank { params.model.displayName }
            val provider = providerSetting.name.ifBlank { providerSetting::class.simpleName ?: "provider" }
            val preview = messages.lastOrNull()?.parts
                ?.mapNotNull { part -> (part as? me.rerere.ai.ui.UIMessagePart.Text)?.text }
                ?.lastOrNull()
                ?.replace(Regex("\\s+"), " ")
                ?.take(160)
                .orEmpty()
            return buildString {
                append(provider)
                append(" · ")
                append(modelId)
                append(" · ")
                append(messages.size)
                append(" messages")
                if (stream) append(" · stream")
                if (params.tools.isNotEmpty()) {
                    append(" · ")
                    append(params.tools.size)
                    append(" tools")
                }
                if (preview.isNotBlank()) {
                    append('\n')
                    append(preview)
                }
            }
        }
    }
}

private const val MAX_LOGS = 10

class AILoggingManager {
    private val ring = PortableLogRing<AILogging>(MAX_LOGS)

    fun getLogs(): StateFlow<List<AILogging>> = ring.observe()

    fun addLog(log: AILogging) {
        ring.add(log)
    }

    fun clearLogs() {
        ring.clear()
    }
}
