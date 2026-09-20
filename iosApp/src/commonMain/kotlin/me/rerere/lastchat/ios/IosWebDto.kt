package me.rerere.lastchat.ios

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.mcp.PortableMcpServer
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.web.toWebMediaUrl

internal object IosWebDto {
    fun conversationList(conversations: List<IosConversation>, generatingId: String?): String =
        buildJsonArray {
            conversations.forEach { conversation ->
                add(conversationListItem(conversation, generatingId == conversation.id))
            }
        }.toString()

    fun conversation(conversation: IosConversation, generating: Boolean): String =
        conversationObject(conversation, generating).toString()

    fun bootstrap(
        assistantId: String,
        assistants: List<IosAssistantPreferences>,
        conversations: List<IosConversation>,
        generatingId: String?,
    ): String = buildJsonObject {
        put("assistantId", assistantId)
        put("assistants", buildJsonArray { assistants.forEach { add(assistant(it)) } })
        put(
            "conversations",
            buildJsonArray { conversations.forEach { add(conversationListItem(it, generatingId == it.id)) } },
        )
    }.toString()

    fun settings(
        appearance: IosAppearancePreferences,
        assistantId: String,
        chatModelId: String,
        assistants: List<IosAssistantPreferences>,
        providers: List<ProviderSetting>,
        skills: List<PortableSkill>,
        lorebooks: List<PortableLorebook>,
        mcpServers: List<PortableMcpServer>,
        searchType: String,
        enableWebSearch: Boolean,
    ): String = buildJsonObject {
        put("dynamicColor", false)
        put("themeId", appearance.themeId)
        put("developerMode", false)
        put(
            "displaySetting",
            buildJsonObject {
                put("userNickname", "")
                put("showUserAvatar", true)
                put("showModelIcon", true)
                put("showModelName", true)
                put("showAssistantBubbles", appearance.showAssistantBubbles)
                put("showTokenUsage", true)
                put("showContextTokenSummary", false)
                put("showThinkingContent", true)
                put("autoCloseThinking", true)
                put("codeBlockAutoWrap", true)
                put("codeBlockAutoCollapse", false)
                put("showLineNumbers", false)
                put("sendOnEnter", false)
                put("enableAutoScroll", true)
                put("fontSizeRatio", appearance.fontSizeRatio.toDouble())
                put("pasteLongTextAsFile", false)
                put("pasteLongTextThreshold", 4096)
                put(
                    "rpStyleRules",
                    buildJsonArray {
                        appearance.rpStyleRules.forEach { rule ->
                            add(
                                buildJsonObject {
                                    put("id", rule.id)
                                    put("pattern", rule.pattern)
                                    put("colorHex", rule.colorHex)
                                    put("enabled", rule.enabled)
                                },
                            )
                        }
                    },
                )
            },
        )
        put("enableWebSearch", enableWebSearch)
        put("favoriteModels", buildJsonArray { })
        put("chatModelId", chatModelId)
        put("assistantId", assistantId)
        put("providers", buildJsonArray { providers.forEach { add(provider(it)) } })
        put("assistants", buildJsonArray { assistants.forEach { add(assistant(it)) } })
        put("assistantTags", buildJsonArray { })
        put(
            "modeInjections",
            buildJsonArray {
                skills.forEach { skill ->
                    add(
                        buildJsonObject {
                            put("id", skill.id)
                            put("name", skill.name)
                            put("description", skill.description)
                            put("enabled", skill.enabled)
                        },
                    )
                }
            },
        )
        put(
            "lorebooks",
            buildJsonArray {
                lorebooks.forEach { lorebook ->
                    add(
                        buildJsonObject {
                            put("id", lorebook.id)
                            put("name", lorebook.name)
                            put("description", lorebook.description)
                            put("enabled", lorebook.enabled)
                        },
                    )
                }
            },
        )
        put(
            "mcpServers",
            buildJsonArray {
                mcpServers.forEach { server ->
                    add(
                        buildJsonObject {
                            put("id", server.id)
                            put("type", "http")
                            put(
                                "commonOptions",
                                buildJsonObject {
                                    put("enable", server.enable)
                                    put("name", server.name)
                                    put("tools", buildJsonArray { })
                                },
                            )
                        },
                    )
                }
            },
        )
        put("searchServices", buildJsonArray { add(buildJsonObject { put("id", searchType); put("type", searchType) }) })
        put("searchServiceSelected", 0)
        put("webServerJwtEnabled", false)
    }.toString()

    private fun conversationListItem(conversation: IosConversation, generating: Boolean) = buildJsonObject {
        put("id", conversation.id)
        put("assistantId", conversation.assistantId.orEmpty())
        put("title", conversation.title.ifBlank { "New chat" })
        put("isPinned", conversation.isPinned)
        put("createAt", conversation.updatedAtEpochMs)
        put("updateAt", conversation.updatedAtEpochMs)
        put("isGenerating", generating)
        put("isFork", false)
        put("isConsolidated", false)
        put("contextSummaryUpToIndex", -1)
        put("lastPruneTime", 0)
        put("lastPruneMessageCount", 0)
        put("lastRefreshTime", 0)
    }

    private fun conversationObject(conversation: IosConversation, generating: Boolean) = buildJsonObject {
        put("id", conversation.id)
        put("assistantId", conversation.assistantId.orEmpty())
        put("title", conversation.title.ifBlank { "New chat" })
        put("messages", buildJsonArray { conversation.messageNodes.ifEmpty {
            conversation.currentMessages.map { MessageNode.of(it) }
        }.forEach { add(messageNode(it)) } })
        put(
            "enabledSkillIds",
            buildJsonArray { conversation.enabledSkillIds.orEmpty().forEach { add(JsonPrimitive(it)) } },
        )
        put("truncateIndex", -1)
        put("chatSuggestions", buildJsonArray { })
        put("isPinned", conversation.isPinned)
        put("createAt", conversation.updatedAtEpochMs)
        put("updateAt", conversation.updatedAtEpochMs)
        put("isGenerating", generating)
        put("isFork", false)
        put("isConsolidated", false)
        put("contextSummaryUpToIndex", -1)
        put("lastPruneTime", 0)
        put("lastPruneMessageCount", 0)
        put("lastRefreshTime", 0)
    }

    private fun messageNode(node: MessageNode) = buildJsonObject {
        put("id", node.id.toString())
        put("messages", buildJsonArray { node.messages.forEach { add(message(it)) } })
        put("selectIndex", node.selectIndex)
    }

    private fun message(message: UIMessage) = buildJsonObject {
        put("id", message.id.toString())
        put("role", message.role.wireName())
        put("parts", buildJsonArray { message.parts.forEach { part -> part.toDto()?.let(::add) } })
        put("createdAt", message.createdAt.toString())
        if (message.modelId != null) put("modelId", message.modelId.toString())
    }

    private fun UIMessagePart.toDto(): JsonObject? = when (this) {
        is UIMessagePart.Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        is UIMessagePart.Image -> buildJsonObject {
            put("type", "image")
            put("url", toWebMediaUrl(url))
        }
        is UIMessagePart.Video -> buildJsonObject {
            put("type", "video")
            put("url", toWebMediaUrl(url))
        }
        is UIMessagePart.Audio -> buildJsonObject {
            put("type", "audio")
            put("url", toWebMediaUrl(url))
        }
        is UIMessagePart.Document -> buildJsonObject {
            put("type", "document")
            put("url", toWebMediaUrl(url))
            put("fileName", fileName)
            put("mime", mime)
        }
        is UIMessagePart.Reasoning -> buildJsonObject {
            put("type", "reasoning")
            put("reasoning", reasoning)
        }
        is UIMessagePart.ToolCall -> buildJsonObject {
            put("type", "tool")
            put("toolCallId", toolCallId)
            put("toolName", toolName)
            put("input", arguments)
            put("output", buildJsonArray { })
            put("approvalState", "auto")
        }
        else -> null
    }

    private fun assistant(assistant: IosAssistantPreferences) = buildJsonObject {
        put("id", assistant.id)
        put("name", assistant.name)
        put("tags", buildJsonArray { })
        put("enableMemory", assistant.memoryMode != IosMemoryMode.OFF)
        put("modeInjectionIds", buildJsonArray { assistant.enabledSkillIds.forEach { add(JsonPrimitive(it)) } })
        put("lorebookIds", buildJsonArray { assistant.enabledLorebookIds.forEach { add(JsonPrimitive(it)) } })
    }

    private fun provider(provider: ProviderSetting) = buildJsonObject {
        put("id", provider.id.toString())
        put("type", provider::class.simpleName ?: "OpenAI")
        put("enabled", provider.enabled)
        put("name", provider.name)
        put("models", buildJsonArray { provider.models.forEach { add(model(it)) } })
        put("systemOwned", false)
    }

    private fun model(model: Model) = buildJsonObject {
        put("id", model.id.toString())
        put("modelId", model.modelId)
        put("displayName", model.displayName)
        put("type", model.type.name)
    }

    private fun MessageRole.wireName(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
        MessageRole.TOOL -> "tool"
    }
}
