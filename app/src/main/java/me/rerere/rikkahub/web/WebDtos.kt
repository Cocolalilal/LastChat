package me.rerere.rikkahub.web

import android.content.Context
import androidx.core.net.toUri
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.RpStyleRule
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantUISettings
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.withoutSkillSelectionOverride
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Skill
import me.rerere.common.http.urlEncode
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

// Requests

@Serializable
data class SendMessageRequest(
    val parts: List<WebMessagePartDto>
)

@Serializable
data class RegenerateRequest(
    val messageId: String
)

@Serializable
data class ToolApprovalRequest(
    val toolCallId: String,
    val approved: Boolean,
    val reason: String = "",
    val answer: String? = null,
)

@Serializable
data class EditMessageRequest(
    val parts: List<WebMessagePartDto>
)

@Serializable
data class UpdateConversationSkillsRequest(
    val skillIds: List<String>
)

@Serializable
data class ForkConversationRequest(
    val messageId: String
)

@Serializable
data class SelectMessageNodeRequest(
    val selectIndex: Int
)

@Serializable
data class MoveConversationRequest(
    val assistantId: String
)

@Serializable
data class UpdateConversationTitleRequest(
    val title: String
)

@Serializable
data class UpdateAssistantRequest(
    val assistantId: String
)

@Serializable
data class UpdateAssistantModelRequest(
    val assistantId: String,
    val modelId: String,
)

@Serializable
data class UpdateAssistantThinkingBudgetRequest(
    val assistantId: String,
    val thinkingBudget: Int?,
)

@Serializable
data class UpdateAssistantMcpServersRequest(
    val assistantId: String,
    val mcpServerIds: List<String>,
)

@Serializable
data class UpdateAssistantInjectionsRequest(
    val assistantId: String,
    val modeInjectionIds: List<String>,
    val lorebookIds: List<String>,
)

@Serializable
data class UpdateSearchEnabledRequest(
    val enabled: Boolean,
)

@Serializable
data class UpdateSearchServiceRequest(
    val index: Int,
)

@Serializable
data class UpdateBuiltInToolRequest(
    val modelId: String,
    val tool: String,
    val enabled: Boolean,
)

@Serializable
data class UpdateFavoriteModelsRequest(
    val modelIds: List<String>,
)

@Serializable
data class WebAuthTokenRequest(
    val password: String,
)

@Serializable
data class CreateConversationRequest(
    val assistantId: String? = null,
)

// Responses

@Serializable
data class ConversationListDto(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val isGenerating: Boolean = false,
    val isFork: Boolean = false,
    val isConsolidated: Boolean = false,
    val contextSummary: String? = null,
    val contextSummaryUpToIndex: Int = -1,
    val lastPruneTime: Long = 0L,
    val lastPruneMessageCount: Int = 0,
    val lastRefreshTime: Long = 0L,
)

@Serializable
data class PagedResult<T>(
    val items: List<T>,
    val nextOffset: Int? = null,
    val hasMore: Boolean = nextOffset != null,
)

@Serializable
data class UploadedFileDto(
    val id: Long,
    val url: String,
    val fileName: String,
    val mime: String,
    val size: Long,
)

@Serializable
data class UploadFilesResponseDto(
    val files: List<UploadedFileDto>
)

@Serializable
data class ConversationDto(
    val id: String,
    val assistantId: String,
    val title: String,
    val messages: List<MessageNodeDto>,
    val enabledSkillIds: List<String>,
    val truncateIndex: Int,
    val chatSuggestions: List<String>,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val isGenerating: Boolean = false,
    val isFork: Boolean = false,
    val isConsolidated: Boolean = false,
    val contextSummary: String? = null,
    val contextSummaryUpToIndex: Int = -1,
    val lastPruneTime: Long = 0L,
    val lastPruneMessageCount: Int = 0,
    val lastRefreshTime: Long = 0L,
)

@Serializable
data class MessageNodeDto(
    val id: String,
    val messages: List<MessageDto>,
    val selectIndex: Int,
)

@Serializable
data class MessageDto(
    val id: String,
    val role: String,
    val parts: List<WebMessagePartDto>,
    val annotations: List<UIMessageAnnotation> = emptyList(),
    val createdAt: String,
    val finishedAt: String? = null,
    val modelId: String? = null,
    val usage: TokenUsage? = null,
    val translation: String? = null,
)

@Serializable
data class ForkConversationResponse(
    val conversationId: String
)

@Serializable
data class MessageSearchResultDto(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Long,
    val snippet: String,
)

@Serializable
data class WebAuthTokenResponse(
    val token: String,
    val expiresAt: Long,
)

@Serializable
data class CreateConversationResponse(
    val id: String,
    val assistantId: String,
)

@Serializable
data class ContextRefreshResponse(
    val success: Boolean,
    val summary: String? = null,
    val messagesSummarized: Int = 0,
    val tokensSaved: Int = 0,
    val error: String? = null,
)

@Serializable
data class WebBootstrapDto(
    val assistantId: String,
    val assistants: List<WebAssistantDto>,
    val conversations: List<ConversationListDto>,
)

@Serializable
data class WebClientBootConfig(
    val authRequired: Boolean,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val code: Int,
)

@Serializable
data class ConversationSnapshotEvent(
    val type: String = "snapshot",
    val seq: Long,
    val conversation: ConversationDto,
    val serverTime: Long = System.currentTimeMillis(),
)

@Serializable
data class ConversationNodeUpdateEvent(
    val type: String = "node_update",
    val seq: Long,
    val conversationId: String,
    val nodeId: String,
    val nodeIndex: Int,
    val node: MessageNodeDto,
    val updateAt: Long,
    val isGenerating: Boolean,
    val serverTime: Long = System.currentTimeMillis(),
)

@Serializable
data class ConversationListInvalidateEvent(
    val type: String = "invalidate",
    val assistantId: String,
    val timestamp: Long,
)

@Serializable
data class ErrorEvent(
    val type: String = "error",
    val message: String,
)

@Serializable
data class WebAvatarDto(
    val type: String? = null,
    val content: String? = null,
    val url: String? = null,
)

@Serializable
data class WebDisplaySettingDto(
    val userNickname: String,
    val userAvatar: WebAvatarDto? = null,
    val showUserAvatar: Boolean,
    val showModelIcon: Boolean,
    val showModelName: Boolean,
    val showTokenUsage: Boolean,
    val showThinkingContent: Boolean,
    val autoCloseThinking: Boolean,
    val codeBlockAutoWrap: Boolean,
    val codeBlockAutoCollapse: Boolean,
    val showLineNumbers: Boolean,
    val sendOnEnter: Boolean,
    val enableAutoScroll: Boolean,
    val fontSizeRatio: Float,
    val pasteLongTextAsFile: Boolean,
    val pasteLongTextThreshold: Int,
    val rpStyleRules: List<WebRpStyleRuleDto> = emptyList(),
)

@Serializable
data class WebRpStyleRuleDto(
    val id: String,
    val pattern: String,
    val colorHex: String,
    val enabled: Boolean,
)

@Serializable
data class WebAssistantUiSettingsDto(
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
    val newChatHeaderStyle: String? = null,
    val newChatContentStyle: String? = null,
    val newChatShowAvatar: Boolean? = null,
)

@Serializable
data class WebAssistantTagDto(
    val id: String,
    val name: String,
)

@Serializable
data class WebQuickMessageDto(
    val title: String,
    val content: String,
)

@Serializable
data class WebModeInjectionDto(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val alwaysEnabled: Boolean = false,
    val icon: String? = null,
)

@Serializable
data class WebLorebookDto(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class WebAssistantDto(
    val id: String,
    val chatModelId: String? = null,
    val thinkingBudget: Int? = null,
    val mcpServers: List<String> = emptyList(),
    val modeInjectionIds: List<String> = emptyList(),
    val lorebookIds: List<String> = emptyList(),
    val name: String,
    val avatar: WebAvatarDto? = null,
    val useAssistantAvatar: Boolean = false,
    val uiSettings: WebAssistantUiSettingsDto = WebAssistantUiSettingsDto(),
    val tags: List<String> = emptyList(),
    val quickMessages: List<WebQuickMessageDto> = emptyList(),
    val presetMessages: List<MessageDto> = emptyList(),
    val enableMemory: Boolean = false,
    val enableMemoryConsolidation: Boolean = false,
    val enableHistorySummarization: Boolean = false,
    val autoRegenerateSummary: Boolean = false,
    val maxHistoryMessages: Int? = null,
)

@Serializable
data class WebBuiltInToolDto(
    val type: String,
)

@Serializable
data class WebProviderModelDto(
    val id: String,
    val modelId: String,
    val displayName: String,
    val type: ModelType,
    val inputModalities: List<Modality> = emptyList(),
    val outputModalities: List<Modality> = emptyList(),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: List<WebBuiltInToolDto> = emptyList(),
    val iconUrl: String? = null,
    val customIconUri: String? = null,
    val providerSlug: String? = null,
)

@Serializable
data class WebProviderDto(
    val id: String,
    val type: String,
    val enabled: Boolean,
    val name: String,
    val models: List<WebProviderModelDto>,
    val systemOwned: Boolean = false,
)

@Serializable
data class WebSearchServiceDto(
    val id: String,
    val type: String,
)

@Serializable
data class WebMcpToolDto(
    val enable: Boolean,
    val name: String,
    val description: String? = null,
    val needsApproval: Boolean = false,
)

@Serializable
data class WebMcpCommonOptionsDto(
    val enable: Boolean,
    val name: String,
    val tools: List<WebMcpToolDto>,
)

@Serializable
data class WebMcpServerDto(
    val id: String,
    val type: String,
    val commonOptions: WebMcpCommonOptionsDto,
)

@Serializable
data class WebSettingsDto(
    val dynamicColor: Boolean,
    val themeId: String,
    val developerMode: Boolean,
    val displaySetting: WebDisplaySettingDto,
    val enableWebSearch: Boolean,
    val favoriteModels: List<String>,
    val chatModelId: String,
    val assistantId: String,
    val providers: List<WebProviderDto>,
    val assistants: List<WebAssistantDto>,
    val assistantTags: List<WebAssistantTagDto>,
    val modeInjections: List<WebModeInjectionDto>,
    val lorebooks: List<WebLorebookDto>,
    val mcpServers: List<WebMcpServerDto>,
    val searchServices: List<WebSearchServiceDto>,
    val searchServiceSelected: Int,
    val webServerJwtEnabled: Boolean,
)

@Serializable
sealed class ToolApprovalStateDto {
    @Serializable
    @SerialName("auto")
    data object Auto : ToolApprovalStateDto()

    @Serializable
    @SerialName("pending")
    data object Pending : ToolApprovalStateDto()

    @Serializable
    @SerialName("approved")
    data object Approved : ToolApprovalStateDto()

    @Serializable
    @SerialName("denied")
    data class Denied(
        val reason: String,
    ) : ToolApprovalStateDto()

    @Serializable
    @SerialName("answered")
    data class Answered(
        val answer: String,
    ) : ToolApprovalStateDto()
}

@Serializable
sealed class WebMessagePartDto {
    abstract val metadata: JsonObject?

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        override val metadata: JsonObject? = null,
    ) : WebMessagePartDto()

    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        override val metadata: JsonObject? = null,
    ) : WebMessagePartDto()

    @Serializable
    @SerialName("video")
    data class Video(
        val url: String,
        override val metadata: JsonObject? = null,
    ) : WebMessagePartDto()

    @Serializable
    @SerialName("audio")
    data class Audio(
        val url: String,
        override val metadata: JsonObject? = null,
    ) : WebMessagePartDto()

    @Serializable
    @SerialName("document")
    data class Document(
        val url: String,
        val fileName: String,
        val mime: String,
        override val metadata: JsonObject? = null,
    ) : WebMessagePartDto()

    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val reasoning: String,
        val createdAt: String? = null,
        val finishedAt: String? = null,
        override val metadata: JsonObject? = null,
    ) : WebMessagePartDto()

    @Serializable
    @SerialName("tool")
    data class Tool(
        val toolCallId: String,
        val toolName: String,
        val input: String,
        val output: List<WebMessagePartDto> = emptyList(),
        val approvalState: ToolApprovalStateDto = ToolApprovalStateDto.Auto,
        override val metadata: JsonObject? = null,
    ) : WebMessagePartDto()
}

fun Settings.toWebSettingsDto(context: Context): WebSettingsDto {
    val currentAssistant = assistants.find { it.id == assistantId }
    val currentModelId = currentAssistant?.chatModelId ?: chatModelId
    val currentSearchMode = currentAssistant?.searchMode
    val selectedSearchIndex = when (currentSearchMode) {
        is AssistantSearchMode.Provider -> currentSearchMode.index
        else -> searchServiceSelected
    }.coerceIn(0, (searchServices.size - 1).coerceAtLeast(0))

    return WebSettingsDto(
        dynamicColor = dynamicColor,
        themeId = themeId,
        developerMode = developerMode,
        displaySetting = displaySetting.toWebDisplaySetting(context),
        enableWebSearch = currentSearchMode is AssistantSearchMode.Provider,
        favoriteModels = favoriteModels.map(Uuid::toString),
        chatModelId = chatModelId.toString(),
        assistantId = assistantId.toString(),
        providers = providers.map { provider ->
            provider.toWebProviderDto(
                selectedModelId = currentModelId,
                builtInSearchEnabled = currentAssistant?.let {
                    it.preferBuiltInSearch || it.searchMode is AssistantSearchMode.BuiltIn
                } == true,
            )
        },
        assistants = assistants.map { it.toWebAssistantDto(context) },
        assistantTags = assistantTags.map { WebAssistantTagDto(id = it.id.toString(), name = it.name) },
        modeInjections = skills.map { it.toWebModeInjectionDto() },
        lorebooks = lorebooks.map { it.toWebLorebookDto() },
        mcpServers = mcpServers.map { it.toWebMcpServerDto() },
        searchServices = searchServices.map { it.toWebSearchServiceDto() },
        searchServiceSelected = selectedSearchIndex,
        webServerJwtEnabled = webServerJwtEnabled,
    )
}

fun Conversation.toListDto(isGenerating: Boolean = false) = ConversationListDto(
    id = id.toString(),
    assistantId = assistantId.toString(),
    title = title.ifBlank { "New chat" },
    isPinned = isPinned,
    createAt = createAt.toEpochMilli(),
    updateAt = updateAt.toEpochMilli(),
    isGenerating = isGenerating,
    isFork = isFork,
    isConsolidated = isConsolidated,
    contextSummary = contextSummary,
    contextSummaryUpToIndex = contextSummaryUpToIndex,
    lastPruneTime = lastPruneTime,
    lastPruneMessageCount = lastPruneMessageCount,
    lastRefreshTime = lastRefreshTime,
)

fun Conversation.toDto(
    settings: Settings,
    context: Context,
    isGenerating: Boolean = false,
) = ConversationDto(
    id = id.toString(),
    assistantId = assistantId.toString(),
    title = title.ifBlank { "New chat" },
    messages = messageNodes.map { it.toDto(settings, context) },
    enabledSkillIds = enabledModeIds.withoutSkillSelectionOverride().map(Uuid::toString),
    truncateIndex = truncateIndex,
    chatSuggestions = chatSuggestions,
    isPinned = isPinned,
    createAt = createAt.toEpochMilli(),
    updateAt = updateAt.toEpochMilli(),
    isGenerating = isGenerating,
    isFork = isFork,
    isConsolidated = isConsolidated,
    contextSummary = contextSummary,
    contextSummaryUpToIndex = contextSummaryUpToIndex,
    lastPruneTime = lastPruneTime,
    lastPruneMessageCount = lastPruneMessageCount,
    lastRefreshTime = lastRefreshTime,
)

fun MessageNode.toDto(
    settings: Settings,
    context: Context,
) = MessageNodeDto(
    id = id.toString(),
    messages = messages.map { it.toDto(context) },
    selectIndex = selectIndex,
)

fun UIMessage.toDto(
    context: Context,
): MessageDto {
    return MessageDto(
        id = id.toString(),
        role = role.name,
        parts = parts.toWebMessageParts(context),
        annotations = annotations,
        createdAt = createdAt.toString(),
        finishedAt = finishedAtString(),
        modelId = modelId?.toString(),
        usage = usage,
        translation = translation,
    )
}

fun List<WebMessagePartDto>.toUiMessageParts(): List<UIMessagePart> {
    return flatMap { part ->
        when (part) {
            is WebMessagePartDto.Text -> {
                listOf(
                    UIMessagePart.Text(
                        text = part.text,
                        metadata = part.metadata.stripFileId(),
                    )
                )
            }

            is WebMessagePartDto.Image -> {
                listOf(
                    UIMessagePart.Image(
                        url = part.url,
                        metadata = part.metadata.stripFileId(),
                    )
                )
            }

            is WebMessagePartDto.Video -> {
                listOf(
                    UIMessagePart.Video(
                        url = part.url,
                        metadata = part.metadata.stripFileId(),
                    )
                )
            }

            is WebMessagePartDto.Audio -> {
                listOf(
                    UIMessagePart.Audio(
                        url = part.url,
                        metadata = part.metadata.stripFileId(),
                    )
                )
            }

            is WebMessagePartDto.Document -> {
                listOf(
                    UIMessagePart.Document(
                        url = part.url,
                        fileName = part.fileName,
                        mime = part.mime,
                        metadata = part.metadata.stripFileId(),
                    )
                )
            }

            is WebMessagePartDto.Reasoning -> {
                listOf(
                    UIMessagePart.Reasoning(
                        reasoning = part.reasoning,
                        metadata = part.metadata.stripFileId(),
                    )
                )
            }

            is WebMessagePartDto.Tool -> buildList {
                add(
                    UIMessagePart.ToolCall(
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        arguments = part.input,
                        approvalState = part.approvalState.toUiToolApprovalState(),
                        metadata = part.metadata.stripFileId(),
                    )
                )

                if (part.output.isNotEmpty()) {
                    add(
                        UIMessagePart.ToolResult(
                            toolCallId = part.toolCallId,
                            toolName = part.toolName,
                            content = part.output.toToolResultContent(),
                            arguments = parseJsonElementOrPrimitive(part.input),
                            metadata = part.metadata.stripFileId(),
                        )
                    )
                }
            }
        }
    }
}

private fun DisplaySetting.toWebDisplaySetting(context: Context): WebDisplaySettingDto {
    return WebDisplaySettingDto(
        userNickname = userNickname,
        userAvatar = userAvatar.toWebAvatarDto(context),
        showUserAvatar = showUserAvatar,
        showModelIcon = showModelIcon,
        showModelName = showModelName,
        showTokenUsage = showTokenUsage,
        showThinkingContent = true,
        autoCloseThinking = autoCloseThinking,
        codeBlockAutoWrap = codeBlockAutoWrap,
        codeBlockAutoCollapse = codeBlockAutoCollapse,
        showLineNumbers = false,
        sendOnEnter = true,
        enableAutoScroll = true,
        fontSizeRatio = fontSizeRatio,
        pasteLongTextAsFile = false,
        pasteLongTextThreshold = 1000,
        rpStyleRules = rpStyleRules.map { it.toWebRpStyleRuleDto() },
    )
}

private fun RpStyleRule.toWebRpStyleRuleDto(): WebRpStyleRuleDto {
    return WebRpStyleRuleDto(
        id = id,
        pattern = pattern,
        colorHex = colorHex,
        enabled = enabled,
    )
}

internal fun Assistant.toWebAssistantDto(context: Context): WebAssistantDto {
    return WebAssistantDto(
        id = id.toString(),
        chatModelId = chatModelId?.toString(),
        thinkingBudget = thinkingBudget,
        mcpServers = mcpServers.map(Uuid::toString),
        modeInjectionIds = enabledSkillIds.map(Uuid::toString),
        lorebookIds = enabledLorebookIds.map(Uuid::toString),
        name = name.ifBlank { "Assistant" },
        avatar = avatar.toWebAvatarDto(context),
        useAssistantAvatar = useAssistantAvatar,
        uiSettings = uiSettings.toWebAssistantUiSettingsDto(),
        tags = tags.map(Uuid::toString),
        quickMessages = quickMessages.map(QuickMessage::toWebQuickMessageDto),
        presetMessages = presetMessages.map { it.toDto(context) },
        enableMemory = enableMemory,
        enableMemoryConsolidation = enableMemoryConsolidation,
        enableHistorySummarization = enableHistorySummarization,
        autoRegenerateSummary = autoRegenerateSummary,
        maxHistoryMessages = maxHistoryMessages,
    )
}

private fun AssistantUISettings.toWebAssistantUiSettingsDto(): WebAssistantUiSettingsDto {
    return WebAssistantUiSettingsDto(
        showUserAvatar = showUserAvatar,
        showAssistantAvatar = showAssistantAvatar,
        showAssistantBubbles = showAssistantBubbles,
        showTokenUsage = showTokenUsage,
        autoCloseThinking = autoCloseThinking,
        showMessageJumper = showMessageJumper,
        messageJumperOnLeft = messageJumperOnLeft,
        fontSizeRatio = fontSizeRatio,
        codeBlockAutoWrap = codeBlockAutoWrap,
        codeBlockAutoCollapse = codeBlockAutoCollapse,
        showContextStacks = showContextStacks,
        newChatHeaderStyle = newChatHeaderStyle?.name,
        newChatContentStyle = newChatContentStyle?.name,
        newChatShowAvatar = newChatShowAvatar,
    )
}

private fun QuickMessage.toWebQuickMessageDto(): WebQuickMessageDto {
    return WebQuickMessageDto(
        title = title,
        content = content,
    )
}

private fun Skill.toWebModeInjectionDto(): WebModeInjectionDto {
    return WebModeInjectionDto(
        id = id.toString(),
        name = name.ifBlank { description.ifBlank { "Skill" } },
        description = description,
        enabled = enabled,
        alwaysEnabled = alwaysEnabled,
        icon = icon,
    )
}

private fun Lorebook.toWebLorebookDto(): WebLorebookDto {
    return WebLorebookDto(
        id = id.toString(),
        name = name.ifBlank { "Lorebook" },
        description = description,
        enabled = enabled,
    )
}

private fun me.rerere.rikkahub.data.ai.mcp.McpServerConfig.toWebMcpServerDto(): WebMcpServerDto {
    val type = when (this) {
        is me.rerere.rikkahub.data.ai.mcp.McpServerConfig.SseTransportServer -> "sse"
        is me.rerere.rikkahub.data.ai.mcp.McpServerConfig.StreamableHTTPServer -> "streamable_http"
    }
    return WebMcpServerDto(
        id = id.toString(),
        type = type,
        commonOptions = WebMcpCommonOptionsDto(
            enable = commonOptions.enable,
            name = commonOptions.name,
            tools = commonOptions.tools.map { tool ->
                WebMcpToolDto(
                    enable = tool.enable,
                    name = tool.name,
                    description = tool.description,
                )
            },
        ),
    )
}

private fun me.rerere.search.SearchServiceOptions.toWebSearchServiceDto(): WebSearchServiceDto {
    val type = when (this) {
        is me.rerere.search.SearchServiceOptions.BingLocalOptions -> "bing_local"
        is me.rerere.search.SearchServiceOptions.ZhipuOptions -> "zhipu"
        is me.rerere.search.SearchServiceOptions.TavilyOptions -> "tavily"
        is me.rerere.search.SearchServiceOptions.ExaOptions -> "exa"
        is me.rerere.search.SearchServiceOptions.SearXNGOptions -> "searxng"
        is me.rerere.search.SearchServiceOptions.LinkUpOptions -> "linkup"
        is me.rerere.search.SearchServiceOptions.BraveOptions -> "brave"
        is me.rerere.search.SearchServiceOptions.MetasoOptions -> "metaso"
        is me.rerere.search.SearchServiceOptions.OllamaOptions -> "ollama"
        is me.rerere.search.SearchServiceOptions.PerplexityOptions -> "perplexity"
        is me.rerere.search.SearchServiceOptions.FirecrawlOptions -> "firecrawl"
        is me.rerere.search.SearchServiceOptions.JinaOptions -> "jina"
        is me.rerere.search.SearchServiceOptions.BochaOptions -> "bocha"
        is me.rerere.search.SearchServiceOptions.NanoGPTOptions -> "nanogpt"
        is me.rerere.search.SearchServiceOptions.GrokOptions -> "grok"
    }
    return WebSearchServiceDto(
        id = id.toString(),
        type = type,
    )
}

private fun ProviderSetting.toWebProviderDto(
    selectedModelId: Uuid,
    builtInSearchEnabled: Boolean,
): WebProviderDto {
    return WebProviderDto(
        id = id.toString(),
        type = when (this) {
            is ProviderSetting.OpenAI -> "openai"
            is ProviderSetting.Google -> "google"
            is ProviderSetting.Claude -> "claude"
            is ProviderSetting.ComfyUI -> "comfyui"
        },
        enabled = enabled,
        name = name,
        models = models.map { model ->
            model.toWebProviderModelDto(
                isSelected = model.id == selectedModelId,
                builtInSearchEnabled = builtInSearchEnabled,
            )
        },
        systemOwned = builtIn,
    )
}

private fun Model.toWebProviderModelDto(
    isSelected: Boolean,
    builtInSearchEnabled: Boolean,
): WebProviderModelDto {
    val tools = buildList {
        this@toWebProviderModelDto.tools.forEach { tool ->
            when (tool) {
                BuiltInTools.Search -> {
                    if (!isSelected || builtInSearchEnabled) {
                        add(WebBuiltInToolDto(type = "search"))
                    }
                }

                BuiltInTools.UrlContext -> add(WebBuiltInToolDto(type = "url_context"))
            }
        }

        if (isSelected && builtInSearchEnabled && this@toWebProviderModelDto.supportsBuiltInSearch() && none { it.type == "search" }) {
            add(WebBuiltInToolDto(type = "search"))
        }
    }

    return WebProviderModelDto(
        id = id.toString(),
        modelId = modelId,
        displayName = displayName,
        type = type,
        inputModalities = inputModalities,
        outputModalities = outputModalities,
        abilities = abilities,
        tools = tools,
        iconUrl = iconUrl,
        customIconUri = customIconUri,
        providerSlug = providerSlug,
    )
}

private fun Model.supportsBuiltInSearch(): Boolean {
    return tools.any { it is BuiltInTools.Search } || modelId.contains("gemini", ignoreCase = true)
}

private fun Avatar.toWebAvatarDto(context: Context): WebAvatarDto? {
    return when (this) {
        Avatar.Dummy -> null
        is Avatar.Emoji -> WebAvatarDto(type = "emoji", content = content)
        is Avatar.Image -> WebAvatarDto(type = "image", url = url.toWebAssetUrl(context))
        is Avatar.Resource -> WebAvatarDto(
            type = "image",
            url = "android.resource://${context.packageName}/${id}".toWebAssetUrl(context),
        )
    }
}

private fun List<UIMessagePart>.toWebMessageParts(context: Context): List<WebMessagePartDto> {
    val toolResults = filterIsInstance<UIMessagePart.ToolResult>().groupBy { it.toolCallId }
    val handledToolIds = mutableSetOf<String>()

    return mapNotNull { part ->
        when (part) {
            is UIMessagePart.Text -> WebMessagePartDto.Text(
                text = part.text,
                metadata = part.metadata.stripFileId(),
            )

            is UIMessagePart.Image -> WebMessagePartDto.Image(
                url = part.url.toWebAssetUrl(context, mimeOverride = "image/*"),
                metadata = part.metadata.stripFileId(),
            )

            is UIMessagePart.Video -> WebMessagePartDto.Video(
                url = part.url.toWebAssetUrl(context, mimeOverride = "video/*"),
                metadata = part.metadata.stripFileId(),
            )

            is UIMessagePart.Audio -> WebMessagePartDto.Audio(
                url = part.url.toWebAssetUrl(context, mimeOverride = "audio/*"),
                metadata = part.metadata.stripFileId(),
            )

            is UIMessagePart.Document -> WebMessagePartDto.Document(
                url = part.url.toWebAssetUrl(
                    context = context,
                    mimeOverride = part.mime,
                    fileName = part.fileName,
                ),
                fileName = part.fileName,
                mime = part.mime,
                metadata = part.metadata.stripFileId(),
            )

            is UIMessagePart.Reasoning -> WebMessagePartDto.Reasoning(
                reasoning = part.reasoning,
                createdAt = part.createdAt.toString(),
                finishedAt = part.finishedAt?.toString(),
                metadata = part.metadata.stripFileId(),
            )

            is UIMessagePart.Thinking -> WebMessagePartDto.Reasoning(
                reasoning = part.thinking,
                createdAt = part.createdAt.toString(),
                finishedAt = part.finishedAt?.toString(),
                metadata = part.metadata.stripFileId(),
            )

            is UIMessagePart.ToolCall -> {
                val results = toolResults[part.toolCallId].orEmpty()
                handledToolIds += part.toolCallId
                WebMessagePartDto.Tool(
                    toolCallId = part.toolCallId,
                    toolName = part.toolName,
                    input = part.arguments,
                    output = results.flatMap { it.content.toWebToolOutputParts(context) },
                    approvalState = if (part.approvalState == ToolApprovalState.Auto && results.isNotEmpty()) {
                        ToolApprovalStateDto.Approved
                    } else {
                        part.approvalState.toDto()
                    },
                    metadata = (part.metadata ?: results.firstOrNull()?.metadata).stripFileId(),
                )
            }

            is UIMessagePart.ToolResult -> {
                if (handledToolIds.contains(part.toolCallId)) {
                    null
                } else {
                    handledToolIds += part.toolCallId
                    WebMessagePartDto.Tool(
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        input = part.arguments.toJsonText(),
                        output = part.content.toWebToolOutputParts(context),
                        approvalState = ToolApprovalStateDto.Approved,
                        metadata = part.metadata.stripFileId(),
                    )
                }
            }

            UIMessagePart.Search -> null
        }
    }
}

private fun UIMessage.finishedAtString(): String? {
    val durationMs = generationDurationMs ?: return null
    val zone = TimeZone.currentSystemDefault()
    val finishedAt = createdAt.toInstant(zone) + durationMs.milliseconds
    return finishedAt.toLocalDateTime(zone).toString()
}

private fun JsonElement.toWebToolOutputParts(context: Context): List<WebMessagePartDto> {
    if (this is JsonArray && isNotEmpty()) {
        val decoded = mapNotNull { element ->
            runCatching {
                JsonInstant.decodeFromJsonElement(WebMessagePartDto.serializer(), element)
            }.getOrNull()
        }
        if (decoded.size == size) {
            return decoded
        }
    }

    extractStructuredToolOutputParts(context)?.let { return it }

    val text = when (this) {
        is JsonPrimitive -> contentOrNull ?: toString()
        else -> JsonInstantPretty.encodeToString(JsonElement.serializer(), this)
    }

    return if (text.isBlank()) {
        emptyList()
    } else {
        listOf(WebMessagePartDto.Text(text = text))
    }
}

private fun ToolApprovalState.toDto(): ToolApprovalStateDto {
    return when (this) {
        ToolApprovalState.Auto -> ToolApprovalStateDto.Auto
        ToolApprovalState.Pending -> ToolApprovalStateDto.Pending
        ToolApprovalState.Approved -> ToolApprovalStateDto.Approved
        is ToolApprovalState.Denied -> ToolApprovalStateDto.Denied(reason = reason)
        is ToolApprovalState.Answered -> ToolApprovalStateDto.Answered(answer = answer)
    }
}

private fun ToolApprovalStateDto.toUiToolApprovalState(): ToolApprovalState {
    return when (this) {
        ToolApprovalStateDto.Auto -> ToolApprovalState.Auto
        ToolApprovalStateDto.Pending -> ToolApprovalState.Pending
        ToolApprovalStateDto.Approved -> ToolApprovalState.Approved
        is ToolApprovalStateDto.Denied -> ToolApprovalState.Denied(reason = reason)
        is ToolApprovalStateDto.Answered -> ToolApprovalState.Answered(answer = answer)
    }
}

private fun JsonElement.extractStructuredToolOutputParts(
    context: Context,
): List<WebMessagePartDto>? {
    val jsonObject = this as? JsonObject ?: return null
    val fileParts = buildList {
        addAll(jsonObject["generated_files"].jsonArrayToWebToolParts(context))
        addAll(jsonObject["files"].jsonArrayToWebToolParts(context))
        jsonObject.toSingleWebToolPart(context)?.let(::add)
    }

    if (fileParts.isEmpty()) {
        return null
    }

    val textPayload = buildJsonObject {
        jsonObject.forEach { (key, value) ->
            if (key != "generated_files" && key != "files" && key != "uri" && key != "markdown_link") {
                put(key, value)
            }
        }
    }.takeIf { it.isNotEmpty() }

    return buildList {
        if (textPayload != null) {
            add(
                WebMessagePartDto.Text(
                    text = JsonInstantPretty.encodeToString(JsonObject.serializer(), textPayload)
                )
            )
        }
        addAll(fileParts)
    }
}

private fun JsonElement?.jsonArrayToWebToolParts(context: Context): List<WebMessagePartDto> {
    val jsonArray = this as? JsonArray ?: return emptyList()
    return jsonArray.mapNotNull { element ->
        (element as? JsonObject)?.toSingleWebToolPart(context)
    }
}

private fun JsonObject.toSingleWebToolPart(context: Context): WebMessagePartDto? {
    val uri = get("uri")?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val resolvedUrl = uri.toWebAssetUrl(
        context = context,
        mimeOverride = get("mime")?.jsonPrimitiveOrNull?.contentOrNull,
        fileName = webToolFileName(),
    )
    val mime = get("mime")?.jsonPrimitiveOrNull?.contentOrNull
        ?: guessMimeFromName(webToolFileName())
        ?: "application/octet-stream"
    val fileName = webToolFileName()
    val isImage = get("is_image")?.jsonPrimitiveOrNull?.contentOrNull == "true" ||
        mime.startsWith("image/", ignoreCase = true)

    return when {
        isImage -> WebMessagePartDto.Image(url = resolvedUrl)
        mime.startsWith("video/", ignoreCase = true) -> WebMessagePartDto.Video(url = resolvedUrl)
        mime.startsWith("audio/", ignoreCase = true) -> WebMessagePartDto.Audio(url = resolvedUrl)
        fileName != null -> WebMessagePartDto.Document(
            url = resolvedUrl,
            fileName = fileName,
            mime = mime,
        )
        else -> null
    }
}

private fun JsonObject.webToolFileName(): String? {
    return get("name")?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: get("path")?.jsonPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun guessMimeFromName(fileName: String?): String? {
    val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "pdf" -> "application/pdf"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "txt", "log", "md" -> "text/plain"
        else -> null
    }
}

private fun List<WebMessagePartDto>.toToolResultContent(): JsonElement {
    if (size == 1) {
        val single = first()
        if (single is WebMessagePartDto.Text) {
            return parseJsonElementOrPrimitive(single.text)
        }
    }

    return JsonArray(
        map { part ->
            JsonInstant.encodeToJsonElement(WebMessagePartDto.serializer(), part)
        }
    )
}

private fun parseJsonElementOrPrimitive(value: String): JsonElement {
    return runCatching { JsonInstant.parseToJsonElement(value) }
        .getOrElse { JsonPrimitive(value) }
}

private fun JsonElement.toJsonText(): String {
    return when (this) {
        is JsonPrimitive -> contentOrNull ?: toString()
        else -> JsonInstantPretty.encodeToString(JsonElement.serializer(), this)
    }
}

private fun JsonObject?.stripFileId(): JsonObject? {
    val metadata = this ?: return null
    if (!metadata.containsKey("fileId")) {
        return metadata
    }

    val filtered = buildJsonObject {
        metadata.forEach { (key, value) ->
            if (key != "fileId") {
                put(key, value)
            }
        }
    }
    return filtered.takeIf { it.isNotEmpty() }
}

private fun String.toWebAssetUrl(
    context: Context,
    mimeOverride: String? = null,
    fileName: String? = null,
): String {
    if (startsWith("data:")) return this
    if (startsWith("/api/")) return this
    if (startsWith("http://") || startsWith("https://")) return this

    val uri = runCatching { toUri() }.getOrNull()
    if (uri != null && uri.isAllowedWebMediaUri(context)) {
        return buildString {
            append("/api/files/content?uri=")
            append(this@toWebAssetUrl.urlEncode(spaceAsPlus = true))
            if (!mimeOverride.isNullOrBlank()) {
                append("&mime=")
                append(mimeOverride.urlEncode(spaceAsPlus = true))
            }
            if (!fileName.isNullOrBlank()) {
                append("&name=")
                append(fileName.urlEncode(spaceAsPlus = true))
            }
        }
    }

    return this
}
