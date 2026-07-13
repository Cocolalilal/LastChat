package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.LocalToolOptionListSerializer
import kotlin.uuid.Uuid

import me.rerere.rikkahub.data.datastore.NewChatHeaderStyle
import me.rerere.rikkahub.data.datastore.NewChatContentStyle

/**
 * Per-assistant UI settings. All nullable - null means "use global setting".
 */
@Serializable
data class AssistantUISettings(
    val showUserAvatar: Boolean? = null,
    val showAssistantAvatar: Boolean? = null,
    val showAssistantBubbles: Boolean? = null,
    val showTokenUsage: Boolean? = null,
    val autoCloseThinking: Boolean? = null,
    val showMessageJumper: Boolean? = null,
    val messageJumperOnLeft: Boolean? = null,
    val fontSizeRatio: Float? = null,
    val codeBlockAutoWrap: Boolean? = null,
    val codeBlockAutoCollapse: Boolean? = null,
    val showContextStacks: Boolean? = null,
    // New chat customization
    val newChatHeaderStyle: NewChatHeaderStyle? = null,
    val newChatContentStyle: NewChatContentStyle? = null,
    val newChatShowAvatar: Boolean? = null, // null = use global, true/false = override
)

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val workspaceId: Uuid? = null,
    val chatModelId: Uuid? = null, // 如果为null, 使用全局默认模型
    val ttsVoiceId: Uuid? = null,
    val ttsAutoplayMode: me.rerere.rikkahub.data.datastore.TtsAutoplayMode? = null,
    val backgroundModelId: Uuid? = null, // 用于后台检查的模型
    val searchMode: AssistantSearchMode = AssistantSearchMode.Off, // Search mode for this assistant
    val preferBuiltInSearch: Boolean = false, // If true, use built-in search when model supports it, otherwise fall back to searchMode
    val embeddingModelId: Uuid? = null, // 用于生成嵌入的模型
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // 使用助手头像替代模型头像
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokenUsage: Int = 81920, // 80k default
    val contextPriority: ContextPriority = ContextPriority.BALANCED,
    val summarizerModelId: Uuid? = null, // Legacy import field; global summarizer lives in Settings
    val streamOutput: Boolean = true,
    val enableMemory: Boolean = false,
    val enableMemorySearchTool: Boolean = false, // Allow the assistant to deliberately search memories and past chats
    val useRagMemoryRetrieval: Boolean = true, // If true, use vector-based RAG. If false, inject all memories
    val ragSimilarityThreshold: Float = 0.45f, // Similarity threshold for RAG (0.0 = include all, 1.0 = only perfect matches)
    val ragLimit: Int = 10, // Maximum number of memories to retrieve via RAG
    val enableRecentChatsReference: Boolean = false, // Use chat episodes in memory
    val ragIncludeEpisodes: Boolean = true, // Include episodic memories in RAG
    val ragIncludeCore: Boolean = true, // Include core memories in RAG
    val enableRagLogging: Boolean = false, // Enable detailed RAG logging
    val enableMemoryConsolidation: Boolean = false, // Enable episodic memory creation from chats (requires RAG)
    val notificationStartHour: Int = 7, // Hour when spontaneous messages can start (0-23)
    val notificationEndHour: Int = 22, // Hour when spontaneous messages must stop (0-23)
    val notificationFrequencyHours: Int = 4, // Minimum hours between spontaneous messages
    val lastNotificationTime: Long = 0L, // Timestamp of last spontaneous notification
    val lastNotificationContent: String = "", // Content of last spontaneous notification to reduce repetition
    val enableSpontaneous: Boolean = false,
    val spontaneousMessageMode: SpontaneousMessageMode = SpontaneousMessageMode.BOTH,
    val spontaneousPrompt: String = "",

    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val regexes: List<AssistantRegex> = emptyList(),
    val thinkingBudget: Int? = -1,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    @Serializable(with = LocalToolOptionListSerializer::class)
    val localTools: List<LocalToolOption> = emptyList(),
    val background: String? = null,
    val backgroundDim: Float = 0.6f,
    val useAssistantMaterialYouColors: Boolean = false,
    val materialYouColorIndex: Int = 0, // 0 = auto (default pick), 1-3 = alternative palette colors
    val learningMode: Boolean = false,
    val enabledLorebookIds: Set<Uuid> = emptySet(), // Lorebooks enabled for this assistant
    val enabledSkillIds: Set<Uuid> = emptySet(), // Skills enabled for this assistant

    // Context Management Settings
    val maxHistoryMessages: Int? = null, // null = unlimited (use token budgeting only)
    val enableHistorySummarization: Boolean = false, // Generate summaries of pruned messages
    val maxSearchResultsRetained: Int? = null, // null = keep all, e.g. 2 = keep last 2 search results
    val archiveImagesAfterMessageAge: Int? = null, // null = disabled
    val enableTimeAwareness: Boolean = false, // Inject current time and notable timeline cues into context
    val enableContextRefresh: Boolean = false, // Legacy compatibility field; manual summarization is always available
    val autoRegenerateSummary: Boolean = false, // Automatically summarize when maxHistoryMessages reached

    // Memory System Configuration & Stats
    val consolidationDelayMinutes: Int = 30, // Wait time before consolidating a chat
    val lastConsolidationTime: Long = 0L,
    val lastConsolidationResult: String = "",


    // Per-assistant UI customization (null = use global setting)
    val uiSettings: AssistantUISettings = AssistantUISettings(),
)

internal const val DEFAULT_AUTO_SUMMARY_HISTORY_LIMIT = 10

internal fun Assistant.canManuallySummarizeConversation(messageCount: Int): Boolean {
    return messageCount > 2
}

internal fun Assistant.shouldAutoSummarizeMessages(): Boolean {
    return autoRegenerateSummary && maxHistoryMessages != null
}

internal fun Assistant.withAutoSummaryEnabled(enabled: Boolean): Assistant {
    return if (enabled) {
        copy(
            autoRegenerateSummary = true,
            maxHistoryMessages = maxHistoryMessages ?: DEFAULT_AUTO_SUMMARY_HISTORY_LIMIT
        )
    } else {
        copy(autoRegenerateSummary = false)
    }
}

@Serializable
data class QuickMessage(
    val title: String = "",
    val content: String = "",
)

@Serializable
data class AssistantMemory(
    val id: Int,
    val content: String = "",
    val type: Int = 0, // 0: CORE, 1: EPISODIC
    val hasEmbedding: Boolean = false,
    val embeddingModelId: String? = null, // UUID of the embedding model used (for model mismatch detection)
    val timestamp: Long = 0L, // Timestamp of the memory (e.g. creation time or episode start time)
    val significance: Int? = null // Significance score (1-10) for episodic memories, null for core memories
)

@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
}

@Serializable
enum class ContextPriority {
    CHAT_HISTORY,
    BALANCED,
    MEMORIES
}

@Serializable
enum class SpontaneousMessageMode {
    BOTH,
    CONTINUE_ONLY,
    NEW_ONLY,
}

@Serializable
sealed class AssistantSearchMode {
    @Serializable
    @SerialName("off")
    data object Off : AssistantSearchMode()
    
    @Serializable
    @SerialName("builtin")
    data object BuiltIn : AssistantSearchMode()
    
    @Serializable
    @SerialName("provider")
    data class Provider(val index: Int) : AssistantSearchMode()
}

@Serializable
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "", // 正则表达式
    val replaceString: String = "", // 替换字符串
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false, // 是否仅在视觉上影响
)

fun String.replaceRegexes(
    assistant: Assistant?,
    scope: AssistantAffectScope,
    visual: Boolean = false
): String {
    if (assistant == null) return this
    if (assistant.regexes.isEmpty()) return this
    return assistant.regexes.fold(this) { acc, regex ->
        if (regex.enabled && regex.visualOnly == visual && regex.affectingScope.contains(scope)) {
            try {
                val result = acc.replace(
                    regex = Regex(regex.findRegex),
                    replacement = regex.replaceString,
                )
                // println("Regex: ${regex.findRegex} -> ${result}")
                result
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果正则表达式格式错误，返回原字符串
                acc
            }
        } else {
            acc
        }
    }
}

fun String.replacePersonaPlaceholders(
    assistant: Assistant?,
    userNickname: String,
): String {
    val charName = assistant?.name?.ifBlank { null } ?: "assistant"
    val userName = userNickname.ifBlank { "user" }
    return this
        .replace(oldValue = "{{char}}", newValue = charName, ignoreCase = true)
        .replace(oldValue = "{char}", newValue = charName, ignoreCase = true)
        .replace(oldValue = "{{user}}", newValue = userName, ignoreCase = true)
        .replace(oldValue = "{user}", newValue = userName, ignoreCase = true)
}

@Serializable
sealed class PromptInjection {
    @Serializable
    @SerialName("mode")
    data class ModeInjection(
        val name: String,
        val priority: Int,
        val prompt: String,
    ) : PromptInjection()

    @Serializable
    @SerialName("regex")
    data class RegexInjection(
        val name: String,
        val regex: String,
    ) : PromptInjection()
}
