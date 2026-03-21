package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Activation type for lorebook entries.
 * Determines how an entry is triggered during chat.
 */
@Serializable
enum class LorebookActivationType {
    @SerialName("always")
    ALWAYS,      // Always inject this entry
    
    @SerialName("keywords")
    KEYWORDS,    // Inject when keywords are found in messages
    
    @SerialName("rag")
    RAG          // Inject based on semantic similarity (requires embedding)
}

/**
 * A single entry in a lorebook.
 * Each entry has its own activation rules and injection settings.
 */
@Serializable
data class LorebookEntry(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
    val injectionPosition: InjectionPosition = InjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,  // Only used when injectionPosition is AT_DEPTH
    
    // Activation settings
    val activationType: LorebookActivationType = LorebookActivationType.KEYWORDS,
    val keywords: List<String> = emptyList(),  // Keywords that trigger this entry
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val scanDepth: Int = 10,  // How many recent messages to scan for keywords/RAG
    
    // Multimedia attachments (images, files, etc.)
    val attachments: List<ModeAttachment> = emptyList(),
    
    // Legacy: For multimodal models - can inject images (deprecated, use attachments instead)
    val imageContent: String? = null,  // Base64 encoded image or file path
    
    // RAG settings (when activationType is RAG)
    val embedding: List<Float>? = null,  // The embedding vector
    val hasEmbedding: Boolean = false,
    val embeddingModelId: String? = null
)

/**
 * A lorebook is a collection of entries that can be injected based on context.
 * Lorebooks can have covers and be shared with other users.
 */
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val cover: Avatar? = null,  // Cover image for the book visualization
    val entries: List<LorebookEntry> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun Lorebook.collectMediaFileRefs(): List<String> {
    return buildList {
        (cover as? Avatar.Image)?.url?.let(::add)
        entries.forEach { entry ->
            addAll(entry.collectAttachmentFileRefs())
        }
    }
}

fun LorebookEntry.collectAttachmentFileRefs(): List<String> {
    return attachments.map { attachment -> attachment.url }
}

/**
 * Export format for lorebooks to share between LastChat users.
 */
@Serializable
data class LorebookExport(
    val version: Int = 1,
    val format: String = "lastchat",
    val lorebook: Lorebook
)

/**
 * Chub.ai / Tavern V2 character format which may contain an embedded lorebook.
 * This is used when downloading a character card with its lorebook from Chub.ai.
 */
@Serializable
data class ChubCharacterV2(
    val spec: String = "",
    val spec_version: String = "",
    val data: ChubCharacterData? = null,
    // Direct fields (for older V1 format)
    val name: String = "",
    val description: String = "",
    val character_book: TavernCharacterBook? = null
)

@Serializable
data class ChubCharacterData(
    val name: String = "",
    val description: String = "",
    val character_book: TavernCharacterBook? = null
)

/**
 * Tavern-compatible lorebook format for import/export.
 * Note: Image content is lost when exporting to Tavern format.
 */
@Serializable
data class TavernCharacterBook(
    val name: String = "",
    val description: String = "",
    val entries: List<TavernCharacterBookEntry> = emptyList()
)

@Serializable
data class TavernCharacterBookEntry(
    val keys: List<String> = emptyList(),
    val content: String = "",
    val enabled: Boolean = true,
    val insertion_order: Int = 0,
    val case_sensitive: Boolean = false,
    val priority: Int = 10,
    val position: String = "after_char",  // "before_char" or "after_char"
    val extensions: Map<String, String> = emptyMap()
) {
    /**
     * Convert Tavern entry to LastChat LorebookEntry
     */
    fun toLorebookEntry(): LorebookEntry {
        return LorebookEntry(
            name = keys.firstOrNull() ?: "",
            prompt = content,
            enabled = enabled,
            injectionPosition = if (position == "before_char") InjectionPosition.BEFORE_SYSTEM else InjectionPosition.AFTER_SYSTEM,
            activationType = LorebookActivationType.KEYWORDS,
            keywords = keys,
            caseSensitive = case_sensitive
        )
    }
}

/**
 * Convert a Lorebook to Tavern CharacterBook format.
 * Warning: Image content will be lost in conversion.
 */
fun Lorebook.toTavernCharacterBook(): TavernCharacterBook {
    return TavernCharacterBook(
        name = name,
        description = description,
        entries = entries.mapIndexed { index, entry ->
            TavernCharacterBookEntry(
                keys = entry.keywords.ifEmpty { listOf(entry.name) },
                content = entry.prompt,
                enabled = entry.enabled,
                insertion_order = index,
                case_sensitive = entry.caseSensitive,
                priority = 10,
                position = when (entry.injectionPosition) {
                    InjectionPosition.BEFORE_SYSTEM -> "before_char"
                    else -> "after_char"
                }
            )
        }
    )
}

/**
 * Convert a Tavern CharacterBook to LastChat Lorebook.
 */
fun TavernCharacterBook.toLorebook(): Lorebook {
    return Lorebook(
        name = name,
        description = description,
        entries = entries.sortedBy { it.insertion_order }.map { it.toLorebookEntry() }
    )
}

// ============================================
// SillyTavern World Info Format
// ============================================

/**
 * SillyTavern World Info entry format.
 * Entries in SillyTavern are stored as an object with numeric string keys.
 */
@Serializable
data class SillyTavernWorldInfoEntry(
    val uid: Int = 0,
    val key: List<String> = emptyList(),
    val keysecondary: List<String> = emptyList(),
    val comment: String = "",
    val content: String = "",
    val constant: Boolean = false,
    val selective: Boolean = true,
    val order: Int = 100,
    val position: Int = 0,  // 0 = after_char, 1 = before_char, etc.
    val disable: Boolean = false,
    val excludeRecursion: Boolean = false,
    val preventRecursion: Boolean = false,
    val delayUntilRecursion: Boolean = false,
    val probability: Int = 100,
    val useProbability: Boolean = true,
    val depth: Int = 4,
    val group: String = "",
    val groupOverride: Boolean = false,
    val groupWeight: Int = 100,
    val scanDepth: Int? = null,
    val caseSensitive: Boolean? = null,
    val matchWholeWords: Boolean? = null,
    val automationId: String = "",
    val role: Int? = null,
    val vectorized: Boolean = false,
    val displayIndex: Int = 0
) {
    fun toLorebookEntry(): LorebookEntry {
        return LorebookEntry(
            name = comment.ifEmpty { key.firstOrNull() ?: "" },
            prompt = content,
            enabled = !disable,
            injectionPosition = when (position) {
                1 -> InjectionPosition.BEFORE_SYSTEM
                4 -> InjectionPosition.AT_DEPTH
                else -> InjectionPosition.AFTER_SYSTEM
            },
            activationType = if (constant) LorebookActivationType.ALWAYS else LorebookActivationType.KEYWORDS,
            keywords = key,
            caseSensitive = caseSensitive ?: false,
            scanDepth = scanDepth ?: depth,
            depth = if (position == 4) depth else 0
        )
    }
}

/**
 * SillyTavern World Info format.
 * The entries field is a map with string keys (e.g., "0", "1", "2").
 */
@Serializable
data class SillyTavernWorldInfo(
    val name: String = "",
    val description: String = "",
    val entries: Map<String, SillyTavernWorldInfoEntry> = emptyMap(),
    @kotlinx.serialization.SerialName("originalData")
    val originalData: SillyTavernOriginalData? = null
)

@Serializable
data class SillyTavernOriginalData(
    val name: String = "",
    val description: String = ""
)

/**
 * Convert a Lorebook to SillyTavern World Info format.
 */
fun Lorebook.toSillyTavernWorldInfo(): SillyTavernWorldInfo {
    return SillyTavernWorldInfo(
        name = name,
        description = description,
        entries = entries.mapIndexed { index, entry ->
            index.toString() to SillyTavernWorldInfoEntry(
                uid = index,
                key = entry.keywords.ifEmpty { listOf(entry.name) },
                comment = entry.name,
                content = entry.prompt,
                constant = entry.activationType == LorebookActivationType.ALWAYS,
                selective = entry.activationType == LorebookActivationType.KEYWORDS,
                order = index * 10 + 100,
                position = when (entry.injectionPosition) {
                    InjectionPosition.BEFORE_SYSTEM -> 1
                    InjectionPosition.AT_DEPTH -> 4
                    else -> 0
                },
                disable = !entry.enabled,
                depth = entry.depth,
                scanDepth = entry.scanDepth,
                caseSensitive = entry.caseSensitive,
                displayIndex = index
            )
        }.toMap()
    )
}

/**
 * Convert a SillyTavern World Info to LastChat Lorebook.
 */
fun SillyTavernWorldInfo.toLorebook(): Lorebook {
    return Lorebook(
        name = name.ifEmpty { originalData?.name ?: "" },
        description = description.ifEmpty { originalData?.description ?: "" },
        entries = entries.values
            .sortedBy { it.displayIndex }
            .map { it.toLorebookEntry() }
    )
}

