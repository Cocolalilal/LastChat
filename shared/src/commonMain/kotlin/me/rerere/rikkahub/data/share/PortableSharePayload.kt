package me.rerere.rikkahub.data.share

import kotlinx.serialization.Serializable

/**
 * Platform-neutral share-in payload. Android share trampolines and the iOS
 * overlay/share sheet both map into this shape before chat ingestion.
 */
@Serializable
data class PortableSharePayload(
    val text: String = "",
    val subject: String? = null,
    val mimeType: String? = null,
    val attachments: List<PortableShareAttachment> = emptyList(),
) {
    fun hasContent(): Boolean =
        text.isNotBlank() || !subject.isNullOrBlank() || attachments.isNotEmpty()

    fun promptText(): String = buildString {
        subject?.takeIf { it.isNotBlank() }?.let {
            appendLine(it)
            appendLine()
        }
        if (text.isNotBlank()) append(text.trim())
    }.trim()

    companion object {
        const val PENDING_SHARE_TEXT_KEY = "pending_share_text"
    }
}

@Serializable
data class PortableShareAttachment(
    val path: String,
    val fileName: String,
    val mimeType: String? = null,
)
