package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.CharacterCardV2
import me.rerere.rikkahub.data.model.CharacterCardV2Data
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.TavernCharacterBook
import me.rerere.rikkahub.data.model.TavernCharacterBookEntry
import me.rerere.rikkahub.data.model.toTavernCharacterBook
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
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
    // Bundled Lorebooks
    val lorebooks: List<LorebookExportV2> = emptyList(),
    // Bundled Memories
    val memories: List<AssistantMemory> = emptyList()
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
                    avatarContent = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    avatarMime = "image/*" // Simplified, can detect if needed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Process Lorebooks
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

        // 3. Process Memories
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
                    hasEmbedding = it.embedding != null,
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
            lorebooks = bundledLorebooks,
            memories = bundledMemories
        )

        return json.encodeToString(AssistantExportV1.serializer(), export)
    }

    /**
     * Import an Assistant from LastChat Bundle JSON format.
     */
    suspend fun importFromLastChatBundle(jsonContent: String, context: Context): Assistant {
        val export = json.decodeFromString<AssistantExportV1>(jsonContent)
        var assistant = export.assistant.copy(id = Uuid.random()) // New Import = New ID

        // 1. Restore Avatar
        if (export.avatarContent != null && assistant.avatar is Avatar.Image) {
            val fileName = "avatar_${assistant.id}_${System.currentTimeMillis()}.png" // assume png or use mime
            val file = File(context.filesDir, "avatars/$fileName") 
            file.parentFile?.mkdirs()
            try {
                val bytes = Base64.decode(export.avatarContent, Base64.NO_WRAP)
                file.writeBytes(bytes)
                assistant = assistant.copy(avatar = Avatar.Image(url = Uri.fromFile(file).toString()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Restore Lorebooks
        val newLorebookIds = mutableSetOf<Uuid>()
        val importedLorebooks = mutableListOf<Lorebook>()
        
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
                        file.writeBytes(Base64.decode(att.content, Base64.NO_WRAP))
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

        // 3. Restore Memories
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
                tags = assistant.tags.map { it.toString() } 
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
            else -> null
        }
    }
    
    private fun embedAttachment(context: Context, url: String, typeName: String, fileName: String, mime: String): EmbeddedAttachment? {
         val bytes = readUriBytes(context, url) ?: return null
         return EmbeddedAttachment(
             type = me.rerere.rikkahub.data.model.ModeAttachmentType.valueOf(typeName),
             fileName = fileName,
             mime = mime,
             content = Base64.encodeToString(bytes, Base64.NO_WRAP)
         )
    }
    
    fun getSuggestedFileName(assistant: Assistant, format: String): String {
        val baseName = assistant.name.ifEmpty { "assistant" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(50)
        return when (format) {
            "card_v2" -> "${baseName}_card_v2.json"
            else -> "${baseName}_bundle.json" // LastChat format
        }
    }
}
