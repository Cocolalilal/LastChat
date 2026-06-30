package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.ChubCharacterV2
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.LorebookExport
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.data.model.SillyTavernWorldInfo
import me.rerere.rikkahub.data.model.TavernCharacterBook
import me.rerere.rikkahub.data.model.toLorebook
import me.rerere.rikkahub.data.model.toSillyTavernWorldInfo
import me.rerere.rikkahub.data.model.toTavernCharacterBook
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Embedded attachment for export - contains base64 encoded file content.
 */
@Serializable
data class EmbeddedAttachment(
    val type: ModeAttachmentType,
    val fileName: String,
    val mime: String,
    val content: String  // Base64 encoded file content
)

/**
 * Lorebook entry with embedded attachments for export.
 */
@Serializable  
data class LorebookEntryExport(
    val entry: LorebookEntry,
    val embeddedAttachments: List<EmbeddedAttachment> = emptyList()
)

/**
 * LastChat export format with embedded attachments.
 */
@Serializable
data class LorebookExportV2(
    val version: Int = 2,
    val format: String = "lastchat",
    val lorebook: Lorebook,
    val entryAttachments: Map<String, List<EmbeddedAttachment>> = emptyMap()  // entry id -> attachments
)

/**
 * Utility for importing and exporting lorebooks.
 * Supports LastChat format, Tavern CharacterBook format, and SillyTavern World Info format.
 */
object LorebookExportImport {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
    /**
     * Export a lorebook to LastChat JSON format with embedded attachments.
     */
    fun exportToLastChatFormat(lorebook: Lorebook, context: Context? = null): String {
        // If context is provided, embed attachments as base64
        val entryAttachments = if (context != null) {
            lorebook.entries.associate { entry ->
                entry.id.toString() to entry.attachments.mapNotNull { attachment ->
                    embedAttachment(context, attachment)
                }
            }.filterValues { it.isNotEmpty() }
        } else {
            emptyMap()
        }
        
        if (entryAttachments.isNotEmpty()) {
            // Use V2 format with embedded attachments
            val export = LorebookExportV2(
                version = 2,
                format = "lastchat",
                lorebook = lorebook,
                entryAttachments = entryAttachments
            )
            return json.encodeToString(LorebookExportV2.serializer(), export)
        }
        
        // Fallback to V1 format (no attachments or no context)
        val export = LorebookExport(
            version = 1,
            format = "lastchat",
            lorebook = lorebook
        )
        return json.encodeToString(LorebookExport.serializer(), export)
    }
    
    /**
     * Embed an attachment as base64.
     */
    private fun embedAttachment(context: Context, attachment: ModeAttachment): EmbeddedAttachment? {
        return try {
            val uri = Uri.parse(attachment.url)
            val bytes = when {
                attachment.url.startsWith("file://") -> {
                    val file = File(uri.path ?: return null)
                    if (file.exists()) {
                        file.source().buffer().use { source ->
                            source.readByteArray()
                        }
                    } else {
                        return null
                    }
                }
                attachment.url.startsWith("content://") -> {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.source().buffer().use { source ->
                            source.readByteArray()
                        }
                    } ?: return null
                }
                else -> return null
            }
            
            EmbeddedAttachment(
                type = attachment.type,
                fileName = attachment.fileName,
                mime = attachment.mime,
                content = base64Encode(bytes)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Export a lorebook to Tavern CharacterBook format.
     * Note: Attachments are not supported in Tavern format.
     */
    fun exportToTavernFormat(lorebook: Lorebook): String {
        val tavernBook = lorebook.toTavernCharacterBook()
        return json.encodeToString(TavernCharacterBook.serializer(), tavernBook)
    }
    
    /**
     * Export a lorebook to SillyTavern World Info format.
     * Note: Attachments are not supported in SillyTavern format.
     */
    fun exportToSillyTavernFormat(lorebook: Lorebook): String {
        val worldInfo = lorebook.toSillyTavernWorldInfo()
        return json.encodeToString(SillyTavernWorldInfo.serializer(), worldInfo)
    }
    
    /**
     * Result of importing a lorebook.
     */
    sealed class ImportResult {
        data class Success(val lorebook: Lorebook, val format: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
    
    /**
     * Import a lorebook from a JSON string.
     * Auto-detects format (LastChat V1/V2, Tavern, or SillyTavern).
     */
    fun importFromJson(jsonString: String, context: Context? = null): ImportResult {
        return try {
            // Try LastChat V2 format first (with embedded attachments)
            try {
                val export = json.decodeFromString(LorebookExportV2.serializer(), jsonString)
                if (export.format == "lastchat" && export.version >= 2) {
                    val lorebook = if (context != null && export.entryAttachments.isNotEmpty()) {
                        restoreAttachments(context, export.lorebook, export.entryAttachments)
                    } else {
                        export.lorebook
                    }
                    return ImportResult.Success(lorebook, "lastchat")
                }
            } catch (e: Exception) {
                // Not V2 format
            }
            
            // Try LastChat V1 format
            try {
                val export = json.decodeFromString(LorebookExport.serializer(), jsonString)
                return ImportResult.Success(export.lorebook, "lastchat")
            } catch (e: Exception) {
                // Not LastChat format, try others
            }
            
            // Try SillyTavern World Info format (entries is an object/map)
            try {
                val worldInfo = json.decodeFromString(SillyTavernWorldInfo.serializer(), jsonString)
                // Check if it has entries (map format indicates SillyTavern)
                if (worldInfo.entries.isNotEmpty()) {
                    return ImportResult.Success(worldInfo.toLorebook(), "sillytavern")
                }
            } catch (e: Exception) {
                // Not SillyTavern format
            }
            
            // Try Tavern CharacterBook format (entries is an array)
            try {
                val tavernBook = json.decodeFromString(TavernCharacterBook.serializer(), jsonString)
                // Ensure it has entries or a name to be considered valid
                if (tavernBook.entries.isNotEmpty() || tavernBook.name.isNotEmpty()) {
                    return ImportResult.Success(tavernBook.toLorebook(), "tavern")
                }
            } catch (e: Exception) {
                // Not Tavern format either
            }
            
            // Try Chub.ai / Tavern V2 character format (may have nested character_book)
            try {
                val chubChar = json.decodeFromString(ChubCharacterV2.serializer(), jsonString)
                // Check for character_book in data (V2) or directly (V1)
                val characterBook = chubChar.data?.character_book ?: chubChar.character_book
                if (characterBook != null && (characterBook.entries.isNotEmpty() || characterBook.name.isNotEmpty())) {
                    val lorebook = characterBook.toLorebook().copy(
                        name = characterBook.name.ifEmpty { chubChar.data?.name ?: chubChar.name },
                        description = characterBook.description.ifEmpty { chubChar.data?.description ?: chubChar.description }
                    )
                    return ImportResult.Success(lorebook, "chub")
                }
            } catch (e: Exception) {
                // Not Chub format
            }
            
            ImportResult.Error("Unsupported lorebook format")
        } catch (e: Exception) {
            ImportResult.Error("Failed to parse lorebook: ${e.message}")
        }
    }
    
    /**
     * Restore embedded attachments to local files.
     */
    private fun restoreAttachments(
        context: Context,
        lorebook: Lorebook,
        entryAttachments: Map<String, List<EmbeddedAttachment>>
    ): Lorebook {
        val updatedEntries = lorebook.entries.map { entry ->
            val embeddedList = entryAttachments[entry.id.toString()] ?: emptyList()
            if (embeddedList.isEmpty()) {
                entry
            } else {
                val restoredAttachments = embeddedList.mapNotNull { embedded ->
                    restoreEmbeddedAttachment(context, embedded)
                }
                entry.copy(attachments = entry.attachments + restoredAttachments)
            }
        }
        return lorebook.copy(entries = updatedEntries)
    }
    
    /**
     * Restore a single embedded attachment to a local file.
     */
    private fun restoreEmbeddedAttachment(context: Context, embedded: EmbeddedAttachment): ModeAttachment? {
        return try {
            val bytes = base64Decode(embedded.content)
            val extension = embedded.fileName.substringAfterLast('.', "bin")
            val file = File(context.filesDir, "chat_files/${System.currentTimeMillis()}_${embedded.fileName}")
            file.parentFile?.mkdirs()
            file.sink().buffer().use { output ->
                output.write(bytes)
            }
            
            ModeAttachment(
                url = "file://${file.absolutePath}",
                type = embedded.type,
                fileName = embedded.fileName,
                mime = embedded.mime
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Import a lorebook from a URI.
     */
    fun importFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult.Error("Could not open file")

            val content = inputStream.use { input ->
                input.source().buffer().use { source ->
                    source.readUtf8()
                }
            }
            
            importFromJson(content, context)
        } catch (e: Exception) {
            ImportResult.Error("Failed to read file: ${e.message}")
        }
    }
    
    /**
     * Get file name suggestion based on lorebook name.
     */
    fun getSuggestedFileName(lorebook: Lorebook, format: String = "lastchat"): String {
        val baseName = lorebook.name.ifEmpty { "lorebook" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(50)
        return when (format) {
            "tavern" -> "${baseName}_tavern.json"
            "sillytavern" -> "${baseName}_sillytavern.json"
            else -> "${baseName}.json"
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun base64Encode(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun base64Decode(value: String): ByteArray = Base64.decode(value)
