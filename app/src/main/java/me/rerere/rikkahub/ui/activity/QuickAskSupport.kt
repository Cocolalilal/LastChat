package me.rerere.rikkahub.ui.activity

import android.content.Intent
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant

internal const val EXTRA_QUICK_ASK_CONTINUATION =
    "me.rerere.rikkahub.extra.QUICK_ASK_CONTINUATION"

@Serializable
internal data class QuickAskAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String? = null,
)

@Serializable
internal data class QuickAskInputData(
    val text: String = "",
    val attachments: List<QuickAskAttachment> = emptyList(),
) {
    fun hasContent(): Boolean {
        return text.isNotBlank() || attachments.isNotEmpty()
    }
}

@Serializable
internal data class QuickAskContinuationData(
    val text: String = "",
    val attachments: List<QuickAskAttachment> = emptyList(),
    val aiResponse: String? = null,
    val userPrompt: String? = null,
    val assistantId: String? = null,
)

internal fun buildQuickAskTextContent(
    text: String,
    customPrompt: String? = null,
): String {
    val trimmedText = text.trim()
    val trimmedPrompt = customPrompt?.trim().takeIf { !it.isNullOrBlank() }
    return when {
        trimmedText.isNotBlank() && trimmedPrompt != null -> {
            "$trimmedText\n\nQuestion: $trimmedPrompt"
        }

        trimmedText.isNotBlank() -> trimmedText
        trimmedPrompt != null -> "Question: $trimmedPrompt"
        else -> ""
    }
}

internal fun buildQuickAskMessageParts(
    text: String,
    attachments: List<QuickAskAttachment>,
    customPrompt: String? = null,
): List<UIMessagePart> {
    val parts = mutableListOf<UIMessagePart>()
    val mergedText = buildQuickAskTextContent(text, customPrompt)
    if (mergedText.isNotBlank()) {
        parts += UIMessagePart.Text(mergedText)
    }
    attachments.forEach { attachment ->
        val mimeType = attachment.mimeType.orEmpty()
        parts += when {
            mimeType.startsWith("image/") -> UIMessagePart.Image(url = attachment.uri)
            mimeType.startsWith("video/") -> UIMessagePart.Video(url = attachment.uri)
            mimeType.startsWith("audio/") -> UIMessagePart.Audio(url = attachment.uri)
            else -> UIMessagePart.Document(
                url = attachment.uri,
                fileName = attachment.fileName,
                mime = attachment.mimeType ?: "application/octet-stream"
            )
        }
    }
    return parts
}

internal fun Intent.putQuickAskContinuationData(data: QuickAskContinuationData) {
    putExtra(EXTRA_QUICK_ASK_CONTINUATION, JsonInstant.encodeToString(data))
}

internal fun Intent.readQuickAskContinuationData(): QuickAskContinuationData? {
    val encoded = getStringExtra(EXTRA_QUICK_ASK_CONTINUATION) ?: return null
    return runCatching {
        JsonInstant.decodeFromString<QuickAskContinuationData>(encoded)
    }.getOrNull()
}
