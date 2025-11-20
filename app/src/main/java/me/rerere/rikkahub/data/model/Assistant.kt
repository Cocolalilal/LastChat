package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import kotlin.uuid.Uuid

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null, // 如果为null, 使用全局默认模型
    val backgroundModelId: Uuid? = null, // 用于后台检查的模型
    val embeddingModelId: Uuid? = null, // 用于生成嵌入的模型
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // 使用助手头像替代模型头像
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageSize: Int = 64,
    val streamOutput: Boolean = true,
    val enableMemory: Boolean = false,
    val useRagMemoryRetrieval: Boolean = true, // If true, use vector-based RAG. If false, inject all memories
    val enableRecentChatsReference: Boolean = false, // Use chat episodes in memory

    // Spontaneous Notification Settings
    val notificationStartHour: Int = 7, // Hour when notifications can start (0-23)
    val notificationEndHour: Int = 22, // Hour when notifications must stop (0-23)
    val notificationFrequencyHours: Int = 4, // Minimum hours between notifications
    val lastNotificationTime: Long = 0L, // Timestamp of last notification
    val lastNotificationContent: String = "", // Content of last notification to avoid repetition
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val regexes: List<AssistantRegex> = emptyList(),
    val thinkingBudget: Int? = 1024,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = emptyList(),
    val background: String? = null,
    val learningMode: Boolean = false,
    val enableSpontaneous: Boolean = false, // 是否启用自发消息
    val spontaneousPrompt: String = "", // 自发消息的Prompt
)

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
    val hasEmbedding: Boolean = false
)

@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
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
