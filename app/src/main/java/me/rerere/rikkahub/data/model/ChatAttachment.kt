package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

@Serializable
data class ChatStorageSettings(
    val imageMaxLongEdgePx: Int? = null,
    val autoDeleteChatImagesAfterDays: Int? = DEFAULT_CHAT_IMAGE_AUTO_DELETE_DAYS,
)

const val DEFAULT_CHAT_IMAGE_AUTO_DELETE_DAYS = 90

enum class ChatAttachmentKind {
    IMAGE,
    DOCUMENT,
    VIDEO,
    AUDIO,
}

enum class ChatAttachmentState {
    ACTIVE,
    ARCHIVED,
    DELETED,
}

enum class ChatAttachmentOcrStatus {
    NONE,
    PENDING,
    SUCCESS,
    FAILED,
    UNAVAILABLE,
}

object ChatAttachmentMetadataKeys {
    const val FILE_ID = "fileId"
    const val STATE = "chatAttachmentState"
    const val OCR_TEXT = "chatAttachmentOcrText"
    const val OCR_STATUS = "chatAttachmentOcrStatus"
    const val DISPLAY_NAME = "chatAttachmentDisplayName"
    const val MIME = "chatAttachmentMime"
}

fun UIMessagePart.chatAttachmentKind(): ChatAttachmentKind? {
    return when (this) {
        is UIMessagePart.Image -> ChatAttachmentKind.IMAGE
        is UIMessagePart.Document -> ChatAttachmentKind.DOCUMENT
        is UIMessagePart.Video -> ChatAttachmentKind.VIDEO
        is UIMessagePart.Audio -> ChatAttachmentKind.AUDIO
        else -> null
    }
}

fun UIMessagePart.chatAttachmentUrl(): String? {
    return when (this) {
        is UIMessagePart.Image -> url
        is UIMessagePart.Document -> url
        is UIMessagePart.Video -> url
        is UIMessagePart.Audio -> url
        else -> null
    }?.takeIf { it.isNotBlank() }
}

fun UIMessagePart.chatAttachmentId(): String? {
    return metadata?.get(ChatAttachmentMetadataKeys.FILE_ID)?.jsonPrimitiveOrNull?.contentOrNull
}

fun UIMessagePart.chatAttachmentState(): ChatAttachmentState {
    val rawState = metadata
        ?.get(ChatAttachmentMetadataKeys.STATE)
        ?.jsonPrimitiveOrNull
        ?.contentOrNull
    return runCatching { rawState?.let { ChatAttachmentState.valueOf(it) } }.getOrNull()
        ?: ChatAttachmentState.ACTIVE
}

fun UIMessagePart.chatAttachmentOcrStatus(): ChatAttachmentOcrStatus {
    val rawStatus = metadata
        ?.get(ChatAttachmentMetadataKeys.OCR_STATUS)
        ?.jsonPrimitiveOrNull
        ?.contentOrNull
    return runCatching { rawStatus?.let { ChatAttachmentOcrStatus.valueOf(it) } }.getOrNull()
        ?: ChatAttachmentOcrStatus.NONE
}

fun UIMessagePart.chatAttachmentOcrText(): String? {
    return metadata?.get(ChatAttachmentMetadataKeys.OCR_TEXT)?.jsonPrimitiveOrNull?.contentOrNull
}

fun UIMessagePart.chatAttachmentDisplayName(): String? {
    return metadata?.get(ChatAttachmentMetadataKeys.DISPLAY_NAME)?.jsonPrimitiveOrNull?.contentOrNull
}

fun UIMessagePart.chatAttachmentMimeHint(): String? {
    return metadata?.get(ChatAttachmentMetadataKeys.MIME)?.jsonPrimitiveOrNull?.contentOrNull
}

fun UIMessagePart.withChatAttachmentMetadata(
    fileId: String,
    state: ChatAttachmentState = chatAttachmentState(),
    ocrText: String? = chatAttachmentOcrText(),
    ocrStatus: ChatAttachmentOcrStatus = chatAttachmentOcrStatus(),
    displayName: String? = chatAttachmentDisplayName(),
    mime: String? = chatAttachmentMimeHint(),
): UIMessagePart {
    val mergedMetadata = buildChatAttachmentMetadata(
        existing = metadata,
        fileId = fileId,
        state = state,
        ocrText = ocrText,
        ocrStatus = ocrStatus,
        displayName = displayName,
        mime = mime,
    )
    return when (this) {
        is UIMessagePart.Image -> copy(metadata = mergedMetadata)
        is UIMessagePart.Document -> copy(metadata = mergedMetadata)
        is UIMessagePart.Video -> copy(metadata = mergedMetadata)
        is UIMessagePart.Audio -> copy(metadata = mergedMetadata)
        else -> this
    }
}

fun buildChatAttachmentMetadata(
    existing: JsonObject?,
    fileId: String,
    state: ChatAttachmentState,
    ocrText: String?,
    ocrStatus: ChatAttachmentOcrStatus,
    displayName: String?,
    mime: String?,
): JsonObject {
    return buildJsonObject {
        existing?.forEach { (key, value) ->
            if (
                key != ChatAttachmentMetadataKeys.FILE_ID &&
                key != ChatAttachmentMetadataKeys.STATE &&
                key != ChatAttachmentMetadataKeys.OCR_TEXT &&
                key != ChatAttachmentMetadataKeys.OCR_STATUS &&
                key != ChatAttachmentMetadataKeys.DISPLAY_NAME &&
                key != ChatAttachmentMetadataKeys.MIME
            ) {
                put(key, value)
            }
        }
        put(ChatAttachmentMetadataKeys.FILE_ID, fileId)
        put(ChatAttachmentMetadataKeys.STATE, state.name)
        put(ChatAttachmentMetadataKeys.OCR_STATUS, ocrStatus.name)
        displayName?.takeIf { it.isNotBlank() }?.let {
            put(ChatAttachmentMetadataKeys.DISPLAY_NAME, it)
        }
        mime?.takeIf { it.isNotBlank() }?.let {
            put(ChatAttachmentMetadataKeys.MIME, it)
        }
        ocrText?.takeIf { it.isNotBlank() }?.let {
            put(ChatAttachmentMetadataKeys.OCR_TEXT, it)
        }
    }
}

fun compactChatAttachmentDisplayName(
    fileName: String,
    maxLength: Int = 22,
): String {
    if (fileName.length <= maxLength) {
        return fileName
    }

    val extension = fileName.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it != fileName }
        ?.let { ".$it" }

    if (extension == null || extension.length >= maxLength - 6) {
        val leading = (maxLength - 3) / 2
        val trailing = (maxLength - 3 - leading).coerceAtLeast(2)
        return fileName.take(leading) + "..." + fileName.takeLast(trailing)
    }

    val baseName = fileName.removeSuffix(extension)
    val availableBase = (maxLength - extension.length - 3).coerceAtLeast(4)
    val leading = (availableBase * 0.65f).toInt().coerceAtLeast(2)
    val trailing = (availableBase - leading).coerceAtLeast(2)
    return baseName.take(leading) + "..." + baseName.takeLast(trailing) + extension
}
