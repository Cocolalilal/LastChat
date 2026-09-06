package me.rerere.lastchat.ios.backup

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessagePart
import me.rerere.lastchat.ios.IosConversation
import me.rerere.lastchat.ios.IosMemoryRecord
import kotlin.time.Clock

internal object IosBackupDataImporter {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    const val CONVERSATIONS_TABLE = "ConversationEntity"
    const val MEMORIES_TABLE = "MemoryEntity"

    /** Managed file dirs from Android BackupArchiveFormat; workspaces are intentionally excluded. */
    val FILE_DIRS = setOf(
        "upload", "avatars", "assistant_backgrounds", "custom_icons", "custom_fonts",
        "images", "chat_files", "lorebook_covers", "lorebook_attachments", "skills",
        "tool_outputs", "model_catalog",
    )

    fun mapConversationRow(
        row: Map<String, String?>,
        warnings: MutableList<String>,
    ): IosConversation? {
        val id = row["id"]
        val nodesJson = row["nodes"]
        if (id.isNullOrBlank() || nodesJson == null) return null
        val nodes = runCatching {
            json.decodeFromString<List<MessageNode>>(nodesJson)
        }.getOrElse {
            warnings += "Conversation ${id.take(8)}: message nodes could not be parsed and were skipped"
            return null
        }
        return IosConversation(
            id = id,
            assistantId = row["assistant_id"],
            title = row["title"]?.takeIf(String::isNotBlank) ?: "Imported chat",
            messageNodes = nodes,
            isPinned = row["is_pinned"] == "1" || row["is_pinned"] == "true",
            updatedAtEpochMs = row["update_at"]?.toLongOrNull()
                ?: Clock.System.now().toEpochMilliseconds(),
        )
    }

    fun mapMemoryRow(row: Map<String, String?>): IosMemoryRecord? {
        val id = row["id"]?.toIntOrNull() ?: return null
        val content = row["content"].orEmpty()
        if (content.isBlank()) return null
        return IosMemoryRecord(
            id = id,
            assistantId = row["assistant_id"].orEmpty(),
            content = content,
            type = row["type"]?.toIntOrNull() ?: 0,
            // Android stores embeddings in its own float format; iOS re-embeds on demand.
            embeddings = null,
            embeddingModelId = null,
            timestampEpochMs = row["created_at"]?.toLongOrNull()
                ?: Clock.System.now().toEpochMilliseconds(),
        )
    }

    fun withRemappedAttachmentUrls(
        conversation: IosConversation,
        remap: (String) -> String?,
    ): IosConversation {
        if (conversation.messageNodes.isEmpty()) return conversation
        return conversation.copy(
            messageNodes = conversation.messageNodes.map { node ->
                node.copy(messages = node.messages.map { message ->
                    message.copy(parts = message.parts.map { part ->
                        when (part) {
                            is UIMessagePart.Image -> part.copy(url = remap(part.url) ?: part.url)
                            is UIMessagePart.Video -> part.copy(url = remap(part.url) ?: part.url)
                            is UIMessagePart.Audio -> part.copy(url = remap(part.url) ?: part.url)
                            is UIMessagePart.Document -> part.copy(url = remap(part.url) ?: part.url)
                            else -> part
                        }
                    })
                })
            },
        )
    }

    /** Remaps Android file-part URLs (file:///data/.../files/<dir>/<name>) to the staged
     *  iOS file URL for <dir>/<name>, or null when the part is not a managed backup file. */
    fun androidAttachmentRemap(
        url: String,
        resolve: (storagePath: String) -> String?,
    ): String? {
        if (!url.startsWith("file://")) return null
        val path = decodeUriPath(url.removePrefix("file://"))
        val afterFiles = path.substringAfter("/files/", "").takeIf(String::isNotEmpty) ?: return null
        val dir = afterFiles.substringBefore('/')
        if (dir !in FILE_DIRS) return null
        val name = afterFiles.substringAfterLast('/')
        if (name.isBlank()) return null
        return resolve("$dir/$name")
    }

    internal fun decodeUriPath(value: String): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null && byte in 0..255) {
                    bytes.add(byte.toByte())
                    index += 3
                    continue
                }
            }
            char.toString().encodeToByteArray().forEach(bytes::add)
            index++
        }
        return bytes.toByteArray().decodeToString()
    }
}
