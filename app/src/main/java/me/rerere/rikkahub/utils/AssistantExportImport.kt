package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.CharacterCardV2
import me.rerere.rikkahub.data.model.CharacterCardV2Data
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.TavernCharacterBook
import me.rerere.rikkahub.data.model.TavernCharacterBookEntry
import me.rerere.rikkahub.data.model.toTavernCharacterBook
import me.rerere.rikkahub.data.model.toLorebook
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import okio.Buffer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.zip.Inflater
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.model.AssistantMemory

@Serializable
data class AssistantExportV1(
    val version: Int = 1,
    val format: String = "lastchat_assistant",
    val assistant: Assistant,
    // Bundled assets
    val avatarContent: String? = null, // Base64 encoded avatar image
    val avatarMimeType: String? = null,
    val backgroundContent: String? = null, // Base64 encoded chat background image
    val backgroundMimeType: String? = null,
    // Bundled Lorebooks
    val lorebooks: List<LorebookExportV2> = emptyList(),
    // Bundled Memories
    val memories: List<AssistantMemory> = emptyList(),
)

object AssistantExportImport : KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val chatEpisodeDAO: ChatEpisodeDAO by inject()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Export an Assistant to LastChat Bundle JSON format.
     * Includes all settings, avatar image, and enabled lorebooks.
     */
    suspend fun exportToLastChatBundle(
        assistant: Assistant, 
        context: Context,
        includeMemories: Boolean,
        includeLorebooks: Boolean
    ): String {
        // 1. Process Avatar
        var avatarContent: String? = null
        var avatarMime: String? = null
        if (assistant.avatar is Avatar.Image) {
            val url = (assistant.avatar as Avatar.Image).url
            try {
                val bytes = readUriBytes(context, url)
                if (bytes != null) {
                    avatarContent = base64Encode(bytes)
                    avatarMime = "image/*" // Simplified, can detect if needed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Process chat background. The Assistant field itself only contains a
        // local URI, so bundle the bytes as well to keep the background portable.
        var backgroundContent: String? = null
        var backgroundMime: String? = null
        assistant.background?.let { url ->
            try {
                val bytes = readUriBytes(context, url)
                if (bytes != null) {
                    backgroundContent = base64Encode(bytes)
                    backgroundMime = context.getFileMimeType(Uri.parse(url)) ?: "image/*"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Process Lorebooks
        val bundledLorebooks = if (includeLorebooks) {
            val allLorebooks = settingsStore.settingsFlow.value.lorebooks
            assistant.enabledLorebookIds.mapNotNull { id ->
                allLorebooks.find { it.id == id }
            }.map { lorebook ->
                val entryAttachments = lorebook.entries.associate { entry ->
                    entry.id.toString() to entry.attachments.mapNotNull { attachment ->
                        embedAttachment(context, attachment.url, attachment.type.name, attachment.fileName, attachment.mime)
                    }
                }.filterValues { it.isNotEmpty() }

                LorebookExportV2(
                    version = 2,
                    format = "lastchat",
                    lorebook = lorebook,
                    entryAttachments = entryAttachments
                )
            }
        } else {
            emptyList()
        }

        // 4. Process Memories
        val bundledMemories: List<AssistantMemory> = if (includeMemories) {
            // Fetch Core Memories (already returns AssistantMemory list)
            val coreConfigured = memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
            
            // Fetch Episodic Memories
            val episodes = memoryRepository.getEpisodeEntitiesOfAssistant(assistant.id.toString())
            val episodicConfigured = episodes.map {
                AssistantMemory(
                    id = -it.id, // Negative to distinguish
                    content = it.content,
                    type = 1, // EPISODIC
                    hasEmbedding = !it.embedding.isNullOrBlank() || it.embeddingBlob != null,
                    embeddingModelId = it.embeddingModelId,
                    timestamp = it.startTime,
                    significance = it.significance
                )
            }
            
            coreConfigured + episodicConfigured
        } else {
            emptyList()
        }

        val export = AssistantExportV1(
            assistant = assistant,
            avatarContent = avatarContent,
            avatarMimeType = avatarMime,
            backgroundContent = backgroundContent,
            backgroundMimeType = backgroundMime,
            lorebooks = bundledLorebooks,
            memories = bundledMemories,
        )

        return json.encodeToString(AssistantExportV1.serializer(), export)
    }

    /**
     * Import an Assistant from LastChat Bundle JSON format.
     */
    suspend fun importFromLastChatBundle(jsonContent: String, context: Context): Assistant {
        val export = json.decodeFromString<AssistantExportV1>(jsonContent)
        return finalizeLastChatImport(export, context, true, true)
    }

    /**
     * Finalize the import from a parsed ExportV1 object.
     * Restores files, memories, and lorebooks based on flags.
     */
    suspend fun finalizeLastChatImport(
        export: AssistantExportV1, 
        context: Context,
        importMemories: Boolean,
        importLorebooks: Boolean
    ): Assistant {
        var assistant = export.assistant.copy(id = Uuid.random()) // New Import = New ID

        // 1. Restore Avatar
        if (export.avatarContent != null && assistant.avatar is Avatar.Image) {
            val fileName = "avatar_${assistant.id}_${System.currentTimeMillis()}.png" // assume png or use mime
            val file = File(context.filesDir, "avatars/$fileName") 
            file.parentFile?.mkdirs()
            try {
                val bytes = base64Decode(export.avatarContent)
                file.writeBytes(bytes)
                assistant = assistant.copy(avatar = Avatar.Image(url = Uri.fromFile(file).toString()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        // 2. Restore chat background to an app-owned file. Older bundles do not
        // contain backgroundContent and continue to import without modification.
        if (export.backgroundContent != null) {
            val extension = export.backgroundMimeType
                ?.substringBefore(';')
                ?.trim()
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?.takeIf { it.isNotBlank() }
                ?: "img"
            val fileName = "background_${assistant.id}_${System.currentTimeMillis()}.$extension"
            val file = context.getOwnedDirectory(OwnedFileDirectory.ASSISTANT_BACKGROUND)
                .resolve(fileName)
            try {
                file.writeBytes(base64Decode(export.backgroundContent))
                assistant = assistant.copy(background = Uri.fromFile(file).toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Restore Lorebooks
        val newLorebookIds = mutableSetOf<Uuid>()
        val importedLorebooks = mutableListOf<Lorebook>()
        
        if (importLorebooks) {
            export.lorebooks.forEach { lbExport ->
                var lorebook = lbExport.lorebook.copy(id = Uuid.random()) // New ID
                val entryAttachments = lbExport.entryAttachments
                
                // Restore attachments for entries
                val newEntries = lorebook.entries.map { entry ->
                    val attachments = entryAttachments[entry.id.toString()] ?: emptyList()
                    val restoredAttachments = attachments.map { att ->
                        try {
                            val fileName = "lb_${lorebook.id}_${System.currentTimeMillis()}_${att.fileName}"
                            val file = File(context.filesDir, "lorebook_attachments/$fileName")
                            file.parentFile?.mkdirs()
                            file.writeBytes(base64Decode(att.content))
                            ModeAttachment(
                                url = Uri.fromFile(file).toString(),
                                type = att.type,
                                fileName = att.fileName,
                                mime = att.mime
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.filterNotNull()
                    entry.copy(attachments = restoredAttachments)
                }
                lorebook = lorebook.copy(entries = newEntries)
                
                importedLorebooks.add(lorebook)
                newLorebookIds.add(lorebook.id)
            }
            
            // Update Settings with new lorebooks
            if (importedLorebooks.isNotEmpty()) {
                 settingsStore.update { current ->
                     current.copy(lorebooks = current.lorebooks + importedLorebooks)
                 }
            }
            
            if (newLorebookIds.isNotEmpty()) {
                 assistant = assistant.copy(enabledLorebookIds = newLorebookIds)
            }
        } else {
             assistant = assistant.copy(enabledLorebookIds = emptySet())
        }

        // 4. Restore Memories
        if (importMemories) {
            export.memories.forEach { memory ->
                if (memory.type == 0) { // Core
                     memoryRepository.addMemory(
                         assistantId = assistant.id.toString(),
                         content = memory.content
                     )
                } else if (memory.type == 1) { // Episodic
                     val entity = me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity(
                         id = 0, // Auto-generate
                         assistantId = assistant.id.toString(),
                         startTime = memory.timestamp,
                         endTime = memory.timestamp, // approximate
                         content = memory.content,
                         lastAccessedAt = System.currentTimeMillis(),
                         significance = memory.significance ?: 5,
                         embedding = null, // Needs regeneration
                         embeddingModelId = null
                     )
                     chatEpisodeDAO.insertEpisode(entity)
                }
            }
        }

        return assistant
    }

    /**
     * Export an Assistant to Character Card V2 JSON format.
     */
    suspend fun exportToCharacterCardV2(assistant: Assistant, context: Context): String {
        // Collect Lorebooks into a single CharacterBook
        val allLorebooks = settingsStore.settingsFlow.value.lorebooks
        val enabledLorebooks = assistant.enabledLorebookIds.mapNotNull { id ->
            allLorebooks.find { it.id == id }
        }
        
        val mergedTavernEntries = mutableListOf<TavernCharacterBookEntry>()
        enabledLorebooks.forEach { lb ->
            mergedTavernEntries.addAll(lb.toTavernCharacterBook().entries)
        }
        
        val characterBook = if (mergedTavernEntries.isNotEmpty()) {
            TavernCharacterBook(
                name = "Bundled Lore",
                description = "Merged lorebooks from LastChat",
                entries = mergedTavernEntries
            )
        } else {
            null
        }

        val card = CharacterCardV2(
            data = CharacterCardV2Data(
                name = assistant.name,
                description = "", 
                personality = assistant.systemPrompt, 
                firstMes = assistant.presetMessages.firstOrNull()?.toContentText() ?: "",
                mesExample = "", 
                systemPrompt = assistant.systemPrompt, 
                characterBook = characterBook,
                tags = assistant.tags.map { it.toString() },
                alternateGreetings = assistant.alternateGreetings
            )
        )

        return json.encodeToString(CharacterCardV2.serializer(), card)
    }
    
    // -- Private Helpers (Duplicated from LorebookExportImport roughly, should refactor later) --

    private fun readUriBytes(context: Context, url: String): ByteArray? {
        val uri = Uri.parse(url)
        return when {
            url.startsWith("file://") -> {
                val file = File(uri.path ?: return null)
                if (file.exists()) file.readBytes() else null
            }
            url.startsWith("content://") -> {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            url.startsWith("/") -> {
                // Plain file path without scheme
                val file = File(url)
                if (file.exists()) file.readBytes() else null
            }
            url.startsWith("http://") || url.startsWith("https://") -> {
                // Network URL - skip for now (would need async loading)
                null
            }
            else -> null
        }
    }
    
    private fun embedAttachment(context: Context, url: String, typeName: String, fileName: String, mime: String): EmbeddedAttachment? {
         val bytes = readUriBytes(context, url) ?: return null
         return EmbeddedAttachment(
             type = me.rerere.rikkahub.data.model.ModeAttachmentType.valueOf(typeName),
             fileName = fileName,
             mime = mime,
             content = base64Encode(bytes)
         )
    }
    
    fun getSuggestedFileName(assistant: Assistant, format: String): String {
        val baseName = assistant.name.ifEmpty { "character" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(50)
        return when (format) {
            "card_v2" -> "${baseName}_card_v2.json"
            "card_v2_png" -> "${baseName}_card.png"
            else -> "${baseName}_bundle.json" // LastChat format
        }
    }
    
    /**
     * Export an Assistant to Character Card V2 PNG format.
     * The character data is embedded in a tEXt chunk with keyword "chara".
     * This format is compatible with SillyTavern, Agnai, and other chat frontends.
     */
    suspend fun exportToCharacterCardPng(assistant: Assistant, context: Context): ByteArray? {
        // Get the avatar image based on avatar type
        val avatarBytes: ByteArray = when (val avatar = assistant.avatar) {
            is Avatar.Image -> {
                val url = avatar.url
                readUriBytes(context, url) ?: createPlaceholderPng(assistant.name)
            }
            is Avatar.Resource -> {
                // Render Android resource to PNG
                renderResourceToPng(context, avatar.id, assistant.name)
            }
            is Avatar.Emoji -> {
                // Create placeholder with emoji text
                createEmojiPng(avatar.content)
            }
            Avatar.Dummy -> {
                createPlaceholderPng(assistant.name)
            }
        }
        
        // Generate the Character Card V2 JSON
        val cardJson = exportToCharacterCardV2(assistant, context)
        
        // Base64 encode the JSON (as per spec)
        val base64Data = base64Encode(cardJson.toByteArray(Charsets.UTF_8))
        
        // Embed the data into the PNG
        return embedTextChunkInPng(avatarBytes, "chara", base64Data)
    }
    
    /**
     * Create a simple placeholder PNG image with the character's initial.
     */
    private fun createPlaceholderPng(name: String): ByteArray {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw background with a gradient-like effect
        val bgPaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.rgb(100, 100, 150) // Soft purple-gray
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
        
        // Draw initial letter
        val initial = name.firstOrNull()?.uppercaseChar() ?: 'C'
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = size * 0.5f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(initial.toString(), 0, 1, textBounds)
        val yPos = (size / 2f) + (textBounds.height() / 2f)
        canvas.drawText(initial.toString(), size / 2f, yPos, textPaint)
        
        // Compress to PNG
        val bytes = bitmap.toPngBytes()
        bitmap.recycle()
        
        return bytes
    }
    
    /**
     * Render an Android resource drawable to PNG.
     */
    private fun renderResourceToPng(context: Context, resourceId: Int, fallbackName: String): ByteArray {
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, resourceId)
            if (drawable != null) {
                val size = 512
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                
                val bytes = bitmap.toPngBytes()
                bitmap.recycle()
                
                return bytes
            }
        } catch (e: Exception) {
            // Resource not found or other error
            e.printStackTrace()
        }
        // Fallback to placeholder if resource can't be rendered
        return createPlaceholderPng(fallbackName)
    }
    
    /**
     * Create a PNG with an emoji as the content.
     */
    private fun createEmojiPng(emoji: String): ByteArray {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw background
        val bgPaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.rgb(60, 60, 80) // Dark background
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
        
        // Draw emoji
        val textPaint = android.graphics.Paint().apply {
            textSize = size * 0.6f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, textBounds)
        val yPos = (size / 2f) + (textBounds.height() / 2f)
        canvas.drawText(emoji, size / 2f, yPos, textPaint)
        
        val bytes = bitmap.toPngBytes()
        bitmap.recycle()
        
        return bytes
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val buffer = Buffer()
        buffer.outputStream().use { output ->
            compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return buffer.readByteArray()
    }
    
    /**
     * Embed a tEXt chunk with the given keyword and text into a PNG image.
     * The chunk is inserted before the IEND chunk.
     */
    private fun embedTextChunkInPng(pngBytes: ByteArray, keyword: String, text: String): ByteArray? {
        // Validate PNG header
        if (pngBytes.size < 8 || !pngBytes.take(8).toByteArray().contentEquals(PNG_HEADER)) {
            return null
        }
        
        // Find IEND chunk position
        var offset = 8
        var iendPosition = -1
        
        while (offset < pngBytes.size) {
            if (offset + 8 > pngBytes.size) break
            
            val length = ((pngBytes[offset].toInt() and 0xFF) shl 24) or
                         ((pngBytes[offset + 1].toInt() and 0xFF) shl 16) or
                         ((pngBytes[offset + 2].toInt() and 0xFF) shl 8) or
                         (pngBytes[offset + 3].toInt() and 0xFF)
            
            val type = String(pngBytes, offset + 4, 4)
            
            if (type == "IEND") {
                iendPosition = offset
                break
            }
            
            offset += 4 + 4 + length + 4 // length + type + data + crc
        }
        
        if (iendPosition == -1) return null
        
        // Build tEXt chunk
        val keywordBytes = keyword.toByteArray(Charsets.ISO_8859_1)
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)
        val chunkData = keywordBytes + byteArrayOf(0) + textBytes
        val chunkLength = chunkData.size
        
        // Calculate CRC32 (of type + data)
        val typeBytes = "tEXt".toByteArray(Charsets.ISO_8859_1)
        val crc = java.util.zip.CRC32()
        crc.update(typeBytes)
        crc.update(chunkData)
        val crcValue = crc.value.toInt()
        
        // Build the complete chunk
        val chunk = ByteArray(4 + 4 + chunkData.size + 4)
        // Length (big-endian)
        chunk[0] = ((chunkLength shr 24) and 0xFF).toByte()
        chunk[1] = ((chunkLength shr 16) and 0xFF).toByte()
        chunk[2] = ((chunkLength shr 8) and 0xFF).toByte()
        chunk[3] = (chunkLength and 0xFF).toByte()
        // Type
        System.arraycopy(typeBytes, 0, chunk, 4, 4)
        // Data
        System.arraycopy(chunkData, 0, chunk, 8, chunkData.size)
        // CRC (big-endian)
        chunk[chunk.size - 4] = ((crcValue shr 24) and 0xFF).toByte()
        chunk[chunk.size - 3] = ((crcValue shr 16) and 0xFF).toByte()
        chunk[chunk.size - 2] = ((crcValue shr 8) and 0xFF).toByte()
        chunk[chunk.size - 1] = (crcValue and 0xFF).toByte()
        
        // Assemble final PNG: [before IEND] + [tEXt chunk] + [IEND chunk]
        val beforeIend = pngBytes.copyOfRange(0, iendPosition)
        val iendChunk = pngBytes.copyOfRange(iendPosition, pngBytes.size)
        
        return beforeIend + chunk + iendChunk
    }

    // -- Import Logic with Config --

    @Serializable
    sealed class ImportResult {
        data class Success(val assistant: Assistant) : ImportResult()
        
        data class Configurable(
            val assistant: Assistant,
            val exportV1: AssistantExportV1?, // Null if not LastChat Bundle
            val hasMemories: Boolean,
            val hasLorebooks: Boolean,
            val missingModels: List<String> // List of missing model IDs (names for display)
        ) : ImportResult()
        
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Smart Import: Parser for JSON (Bundle/Card) or PNG (Tavern).
     * Returns a Configurable result if options are available, or Success/Error.
     */
    suspend fun parseImport(uri: Uri, context: Context): ImportResult {
        return try {
            val contentBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return ImportResult.Error("Failed to read file")
            
            var jsonContent: String? = null
            var avatarBytes: ByteArray? = null

            // Determine type by magic bytes
            val isPng = contentBytes.size >= 8 && contentBytes.take(8).toByteArray().contentEquals(PNG_HEADER)
            val isWebp = contentBytes.size >= 12 && String(contentBytes, 0, 4) == "RIFF" && String(contentBytes, 8, 4) == "WEBP"
            val isZip = contentBytes.size >= 4 && contentBytes[0] == 0x50.toByte() && contentBytes[1] == 0x4B.toByte() && contentBytes[2] == 0x03.toByte() && contentBytes[3] == 0x04.toByte()

            if (isPng) {
                val chunks = extractPngChunks(contentBytes)
                // Case insensitive matching for keys
                val characterData = chunks.entries.firstOrNull { it.key.lowercase() in listOf("chara", "ccv3", "character", "card", "ccv2") }?.value
                if (characterData != null) {
                     jsonContent = try { String(base64Decode(characterData)) } catch(e: Exception) { characterData }
                     avatarBytes = contentBytes
                } else {
                    return ImportResult.Error("No character data found in PNG")
                }
            } else if (isWebp) {
                val characterData = extractWebpData(contentBytes)
                if (characterData != null) {
                    jsonContent = characterData
                    avatarBytes = contentBytes
                } else {
                    return ImportResult.Error("No character data found in WebP")
                }
            } else if (isZip) {
                val extracted = extractZipData(contentBytes)
                if (extracted.first != null) {
                    jsonContent = extracted.first
                    avatarBytes = extracted.second
                } else {
                    return ImportResult.Error("No character data found in ZIP/CharX")
                }
            } else {
                jsonContent = String(contentBytes)
            }

            if (jsonContent == null) return ImportResult.Error("Unknown file format")

            try {
                if (jsonContent.contains("\"format\": \"lastchat_assistant\"") || jsonContent.contains("\"format\":\"lastchat_assistant\"")) {
                    val export = json.decodeFromString<AssistantExportV1>(jsonContent)
                    return ImportResult.Configurable(
                        assistant = export.assistant,
                        exportV1 = export,
                        hasMemories = export.memories.isNotEmpty(),
                        hasLorebooks = export.lorebooks.isNotEmpty(),
                        missingModels = checkMissingModels(export.assistant)
                    )
                } else {
                    val parseRes = parseCharacterCard(jsonContent, avatarBytes, context)
                    if (parseRes != null) {
                        return ImportResult.Configurable(
                            assistant = parseRes.first,
                            exportV1 = parseRes.second,
                            hasMemories = false,
                            hasLorebooks = parseRes.second?.lorebooks?.isNotEmpty() == true,
                            missingModels = checkMissingModels(parseRes.first)
                        )
                    } else {
                        return ImportResult.Error("Unsupported JSON format")
                    }
                }
            } catch (e: Exception) {
                return ImportResult.Error("JSON Parse Error: ")
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult.Error(e.message ?: "Unknown Parsing Error")
        }
    }

    private fun extractWebpData(bytes: ByteArray): String? {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val type = String(bytes, offset, 4)
            val size = (bytes[offset + 4].toInt() and 0xFF) or
                       ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                       ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                       ((bytes[offset + 7].toInt() and 0xFF) shl 24)
            offset += 8
            if (offset + size > bytes.size) break

            val data = bytes.copyOfRange(offset, offset + size)
            
            if (type == "EXIF" || type == "XMP ") {
                val contentStr = String(data, Charsets.UTF_8)
                val ccv3Match = Regex("(?i)(chara|ccv3|character|card|ccv2)\\0(.*)").find(contentStr)
                if (ccv3Match != null) {
                    try { return String(base64Decode(ccv3Match.groupValues[2].trim())) } catch(e: Exception) {}
                }
                val base64Match = Regex("(ey[a-zA-Z0-9\\+/\\r\\n\\s]+={0,2})").find(contentStr)
                if (base64Match != null) {
                    try { 
                        val b64clean = base64Match.groupValues[1].replace("\\s+".toRegex(), "")
                        val decoded = String(base64Decode(b64clean))
                        if (decoded.contains("name") || decoded.contains("data")) return decoded
                    } catch(e: Exception) {}
                }
            }
            // Add custom chunks (chara, ccv3)
            if (type.lowercase() in listOf("chara", "ccv3", "character", "card", "ccv2")) {
                try { return String(base64Decode(String(data))) } catch(e: Exception) { return String(data) }
            }
            
            offset += size + (size % 2) // padding
        }
        
        // Brute force base64 search as last resort
        val contentStr = String(bytes, Charsets.UTF_8)
        val base64Match = Regex("(ey[a-zA-Z0-9\\+/\\r\\n\\s]{20,}={0,2})").findAll(contentStr)
        for (match in base64Match) {
            try { 
                val b64clean = match.groupValues[1].replace("\\s+".toRegex(), "")
                val decoded = String(base64Decode(b64clean))
                if ((decoded.contains("\"name\"") || decoded.contains("\"char_name\"")) && 
                    (decoded.contains("\"description\"") || decoded.contains("\"personality\"") || decoded.contains("\"data\""))) {
                    return decoded
                }
            } catch(e: Exception) {}
        }
        return null
    }

    private fun extractZipData(bytes: ByteArray): Pair<String?, ByteArray?> {
        try {
            val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var jsonStr: String? = null
            var imageBytes: ByteArray? = null
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.lowercase()
                    if (name.endsWith(".json")) {
                        jsonStr = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                        if (imageBytes == null || name.contains("card") || name.contains("avatar") || name.contains("chara")) {
                            imageBytes = zis.readBytes()
                        }
                    }
                }
                entry = zis.nextEntry
            }
            zis.close()
            return Pair(jsonStr, imageBytes)
        } catch (e: Exception) {
            return Pair(null, null)
        }
    }

    private fun checkMissingModels(assistant: Assistant): List<String> {
        val settings = settingsStore.settingsFlow.value
        val missing = mutableListOf<String>()
        
        fun check(id: Uuid?, name: String) {
            if (id != null) {
                 val exists = settings.findModelById(id) != null
                 if (!exists) {
                     missing.add(name)
                 }
            }
        }
        
        check(assistant.chatModelId, "Chat Model")
        check(assistant.backgroundModelId, "Background Model")
        check(assistant.embeddingModelId, "Embedding Model")
        return missing
    }
    
    // Function to clear missing models from assistant
    fun clearMissingModels(assistant: Assistant): Assistant {
        val settings = settingsStore.settingsFlow.value
        
        fun checkAndClear(id: Uuid?): Uuid? {
             if (id != null) {
                 val exists = settings.findModelById(id) != null
                 return if (exists) id else null
             }
             return null
        }
        
        return assistant.copy(
            chatModelId = checkAndClear(assistant.chatModelId),
            backgroundModelId = checkAndClear(assistant.backgroundModelId),
            embeddingModelId = checkAndClear(assistant.embeddingModelId),
            summarizerModelId = null
        )
    }

    // Helper for PNG Chunks
    private val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())

    /**
     * Extract text data from PNG chunks (tEXt, zTXt, iTXt).
     * Supports all common PNG text chunk types for maximum compatibility.
     */
    private fun extractPngChunks(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var offset = 8 // Skip PNG header
        
        while (offset < bytes.size) {
            if (offset + 8 > bytes.size) break
            
            // Read Length (4 bytes, big endian)
            val length = ((bytes[offset].toInt() and 0xFF) shl 24) or
                         ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                         ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                         (bytes[offset + 3].toInt() and 0xFF)
            offset += 4
            
            // Read Type (4 bytes)
            val type = String(bytes, offset, 4)
            offset += 4
            
            // Read Data
            if (offset + length > bytes.size) break
            val data = bytes.copyOfRange(offset, offset + length)
            offset += length
            
            // Skip CRC (4 bytes)
            offset += 4
            
            when (type) {
                "tEXt" -> {
                    // tEXt: Keyword + Null + Text (uncompressed)
                    val separator = data.indexOf(0.toByte())
                    if (separator > 0) {
                        val keyword = String(data, 0, separator, Charsets.ISO_8859_1)
                        val text = String(data, separator + 1, data.size - separator - 1, Charsets.ISO_8859_1)
                        result[keyword] = text
                    }
                }
                "zTXt" -> {
                    // zTXt: Keyword + Null + CompressionMethod (1 byte) + CompressedText
                    val separator = data.indexOf(0.toByte())
                    if (separator > 0 && separator + 1 < data.size) {
                        val keyword = String(data, 0, separator, Charsets.ISO_8859_1)
                        val compressionMethod = data[separator + 1].toInt() and 0xFF
                        if (compressionMethod == 0) { // Only deflate is standard
                            try {
                                val compressedData = data.copyOfRange(separator + 2, data.size)
                                val inflater = Inflater()
                                inflater.setInput(compressedData)
                                val outputBuffer = Buffer()
                                val buffer = ByteArray(1024)
                                while (!inflater.finished()) {
                                    val count = inflater.inflate(buffer)
                                    if (count == 0 && inflater.needsInput()) break
                                    outputBuffer.write(buffer, 0, count)
                                }
                                inflater.end()
                                result[keyword] = outputBuffer.readString(Charsets.ISO_8859_1)
                            } catch (e: Exception) {
                                // Skip malformed zTXt chunks
                            }
                        }
                    }
                }
                "iTXt" -> {
                    // iTXt: Keyword + Null + CompressionFlag + CompressionMethod + LanguageTag + Null + TranslatedKeyword + Null + Text
                    val separator = data.indexOf(0.toByte())
                    if (separator > 0 && separator + 3 < data.size) {
                        val keyword = String(data, 0, separator, Charsets.UTF_8)
                        val compressionFlag = data[separator + 1].toInt() and 0xFF
                        val compressionMethod = data[separator + 2].toInt() and 0xFF
                        
                        // Find text start (skip language tag and translated keyword)
                        var textStart = separator + 3
                        // Skip language tag
                        while (textStart < data.size && data[textStart] != 0.toByte()) textStart++
                        textStart++ // Skip null
                        // Skip translated keyword
                        while (textStart < data.size && data[textStart] != 0.toByte()) textStart++
                        textStart++ // Skip null
                        
                        if (textStart < data.size) {
                            val textData = data.copyOfRange(textStart, data.size)
                            val text = if (compressionFlag == 1 && compressionMethod == 0) {
                                try {
                                    val inflater = Inflater()
                                    inflater.setInput(textData)
                                    val outputBuffer = Buffer()
                                    val buffer = ByteArray(1024)
                                    while (!inflater.finished()) {
                                        val count = inflater.inflate(buffer)
                                        if (count == 0 && inflater.needsInput()) break
                                        outputBuffer.write(buffer, 0, count)
                                    }
                                    inflater.end()
                                    outputBuffer.readString(Charsets.UTF_8)
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                String(textData, Charsets.UTF_8)
                            }
                            if (text != null) {
                                result[keyword] = text
                            }
                        }
                    }
                }
            }
        }
        return result
    }
    
    /**
     * Parse character card JSON (V1, V2, or V3 format).
     * Returns null if parsing fails.
     */
    private fun parseCharacterCard(jsonContent: String, avatarBytes: ByteArray?, context: Context? = null): Pair<Assistant, AssistantExportV1?>? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonContent)
            var jsonObj = if (jsonElement is kotlinx.serialization.json.JsonArray) {
                jsonElement.firstOrNull()?.jsonObject ?: return null
            } else {
                jsonElement.jsonObject
            }
            
            // Unwrap Chub wrappers
            val characterElement = jsonObj["character"]
            val definitionElement = jsonObj["definition"]
            val cardElement = jsonObj["card"]
            
            if (characterElement is kotlinx.serialization.json.JsonObject && characterElement["definition"] is kotlinx.serialization.json.JsonObject) {
                jsonObj = characterElement["definition"] as kotlinx.serialization.json.JsonObject
            } else if (definitionElement is kotlinx.serialization.json.JsonObject) {
                jsonObj = definitionElement
            } else if (cardElement is kotlinx.serialization.json.JsonObject) {
                jsonObj = cardElement
            }
            
            // Un-stringify data payload
            val dataElement = jsonObj["data"]
            if (dataElement != null && dataElement is kotlinx.serialization.json.JsonPrimitive && dataElement.isString) {
                try {
                    val unstr = json.parseToJsonElement(dataElement.content)
                    val newObj = jsonObj.toMutableMap()
                    newObj["data"] = unstr
                    jsonObj = kotlinx.serialization.json.JsonObject(newObj)
                } catch(e: Exception) {}
            }

            val spec = jsonObj["spec"]?.jsonPrimitive?.contentOrNull
            
            val assistantPair = when {
                spec == "chara_card_v2" || spec == "chara_card_v3" || jsonObj.containsKey("data") -> {
                    val dataObj = jsonObj["data"]?.jsonObject ?: jsonObj
                    parseV2CardRobustly(dataObj)
                }
                jsonObj.containsKey("name") || jsonObj.containsKey("char_name") || jsonObj.containsKey("char_persona") -> {
                    parseV1Card(jsonObj)
                }
                else -> null
            } ?: return null
            
            var assistant = assistantPair.first
            var exportV1 = assistantPair.second
            
            if (avatarBytes != null && context != null) {
                val fileName = "avatar_${assistant.id}_${System.currentTimeMillis()}.png"
                val file = File(context.filesDir, "avatars/$fileName")
                file.parentFile?.mkdirs()
                file.writeBytes(avatarBytes)
                assistant = assistant.copy(avatar = Avatar.Image(url = Uri.fromFile(file).toString()))
            }
            
            Pair(assistant, exportV1)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? {
        return try {
            if (this is kotlinx.serialization.json.JsonPrimitive) this.contentOrNull else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseV1Card(jsonObj: JsonObject): Pair<Assistant, AssistantExportV1?> {
        val name = jsonObj["name"].asStringOrNull()
            ?: jsonObj["char_name"].asStringOrNull()
            ?: "Imported Character"
        val description = jsonObj["description"].asStringOrNull()
            ?: jsonObj["char_persona"].asStringOrNull()
            ?: ""
        val personality = jsonObj["personality"].asStringOrNull() ?: ""
        val scenario = jsonObj["scenario"].asStringOrNull()
            ?: jsonObj["world_scenario"].asStringOrNull()
            ?: ""
        val firstMes = jsonObj["first_message"].asStringOrNull()
            ?: jsonObj["first_mes"].asStringOrNull()
            ?: jsonObj["char_greeting"].asStringOrNull()
            ?: jsonObj["greeting"].asStringOrNull()
            ?: ""
        val mesExample = jsonObj["example_dialogs"].asStringOrNull()
            ?: jsonObj["mes_example"].asStringOrNull()
            ?: jsonObj["example_dialogue"].asStringOrNull()
            ?: ""
        val postHistory = jsonObj["post_history_instructions"].asStringOrNull() ?: ""
        
        val systemPromptBuilder = StringBuilder()
        if (description.isNotBlank()) {
            systemPromptBuilder.append("Description:\n$description\n\n")
        }
        if (personality.isNotBlank()) {
            systemPromptBuilder.append("Personality:\n$personality\n\n")
        }
        if (scenario.isNotBlank()) {
            systemPromptBuilder.append("Scenario:\n$scenario\n\n")
        }
        if (mesExample.isNotBlank()) {
            systemPromptBuilder.append("Examples:\n$mesExample\n\n")
        }
        if (postHistory.isNotBlank()) {
            systemPromptBuilder.append("Instructions:\n$postHistory\n\n")
        }
        
        val presetMessages = if (firstMes.isNotBlank()) {
            listOf(me.rerere.ai.ui.UIMessage(
                role = me.rerere.ai.core.MessageRole.ASSISTANT,
                parts = listOf(me.rerere.ai.ui.UIMessagePart.Text(text = firstMes))
            ))
        } else {
            emptyList()
        }
        
        val alternateGreetings = jsonObj["alternate_greetings"]?.let {
            if (it is kotlinx.serialization.json.JsonArray) {
                it.mapNotNull { elem -> elem.asStringOrNull() }
            } else {
                emptyList()
            }
        } ?: emptyList()
        
        val assistant = Assistant(
            name = name,
            systemPrompt = systemPromptBuilder.toString().trim(),
            presetMessages = presetMessages,
            alternateGreetings = alternateGreetings
        )
        
        val lorebooks = jsonObj["character_book"]?.jsonObject?.let {
            try {
                val book = json.decodeFromJsonElement(me.rerere.rikkahub.data.model.TavernCharacterBook.serializer(), it)
                listOf(LorebookExportV2(version = 2, format = "lastchat", lorebook = book.toLorebook(), entryAttachments = emptyMap()))
            } catch (e: Exception) { null }
        } ?: emptyList()
        
        return Pair(
            assistant.copy(enabledLorebookIds = lorebooks.map { it.lorebook.id }.toSet()),
            if (lorebooks.isNotEmpty()) AssistantExportV1(assistant = assistant, lorebooks = lorebooks) else null
        )
    }

    private fun parseV2CardRobustly(dataObj: JsonObject): Pair<Assistant, AssistantExportV1?> {
        val name = dataObj["name"].asStringOrNull() ?: "Imported Character"
        val description = dataObj["description"].asStringOrNull() ?: ""
        val personality = dataObj["personality"].asStringOrNull() ?: ""
        val scenario = dataObj["scenario"].asStringOrNull() ?: ""
        val systemPrompt = dataObj["system_prompt"].asStringOrNull() ?: ""
        val mesExample = dataObj["example_dialogs"].asStringOrNull()
            ?: dataObj["mes_example"].asStringOrNull() 
            ?: dataObj["example_dialogue"].asStringOrNull() 
            ?: ""
        val firstMes = dataObj["first_message"].asStringOrNull()
            ?: dataObj["first_mes"].asStringOrNull() 
            ?: dataObj["char_greeting"].asStringOrNull()
            ?: dataObj["greeting"].asStringOrNull()
            ?: ""
        val postHistory = dataObj["post_history_instructions"].asStringOrNull() ?: ""
        
        val alternateGreetings = dataObj["alternate_greetings"]?.let {
            if (it is kotlinx.serialization.json.JsonArray) {
                it.mapNotNull { elem -> elem.asStringOrNull() }
            } else {
                emptyList()
            }
        } ?: emptyList()

        val systemPromptBuilder = StringBuilder()
        if (description.isNotBlank()) {
            systemPromptBuilder.append("Description:\n$description\n\n")
        }
        if (personality.isNotBlank()) {
            systemPromptBuilder.append("Personality:\n$personality\n\n")
        }
        if (scenario.isNotBlank()) {
            systemPromptBuilder.append("Scenario:\n$scenario\n\n")
        }
        if (systemPrompt.isNotBlank()) {
             systemPromptBuilder.append("System:\n$systemPrompt\n\n")
        }
        if (mesExample.isNotBlank()) {
            systemPromptBuilder.append("Examples:\n$mesExample\n\n")
        }
        if (postHistory.isNotBlank()) {
            systemPromptBuilder.append("Instructions:\n$postHistory\n\n")
        }
        
        val presetMessages = if (firstMes.isNotBlank()) {
            listOf(me.rerere.ai.ui.UIMessage(
                role = me.rerere.ai.core.MessageRole.ASSISTANT,
                parts = listOf(me.rerere.ai.ui.UIMessagePart.Text(text = firstMes))
            ))
        } else {
            emptyList()
        }
        
        val assistant = Assistant(
            name = name.ifBlank { "Imported Character" },
            systemPrompt = systemPromptBuilder.toString().trim(),
            presetMessages = presetMessages,
            alternateGreetings = alternateGreetings
        )
        
        val lorebooks = dataObj["character_book"]?.jsonObject?.let {
            try {
                val book = json.decodeFromJsonElement(me.rerere.rikkahub.data.model.TavernCharacterBook.serializer(), it)
                listOf(LorebookExportV2(version = 2, format = "lastchat", lorebook = book.toLorebook(), entryAttachments = emptyMap()))
            } catch (e: Exception) { null }
        } ?: emptyList()
        
        return Pair(
            assistant.copy(enabledLorebookIds = lorebooks.map { it.lorebook.id }.toSet()),
            if (lorebooks.isNotEmpty()) AssistantExportV1(assistant = assistant, lorebooks = lorebooks) else null
        )
    }



}

@OptIn(ExperimentalEncodingApi::class)
private fun base64Encode(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun base64Decode(value: String): ByteArray = Base64.decode(value)

