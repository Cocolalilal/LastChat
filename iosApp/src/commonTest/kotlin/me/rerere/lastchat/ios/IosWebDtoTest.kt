package me.rerere.lastchat.ios

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMessageNode
import kotlin.test.Test
import kotlin.test.assertTrue

class IosWebDtoTest {
    @Test
    fun conversationDtoUsesSpaMessageNodesAndRewritesFileUrls() {
        val conversation = IosConversation(
            id = "c1",
            assistantId = "a1",
            title = "Voice note",
            messageNodes = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(
                        UIMessagePart.Text("listen"),
                        UIMessagePart.Audio("file:///tmp/note.wav"),
                    ),
                ).toMessageNode(),
            ),
        )
        val json = IosWebDto.conversation(conversation, generating = true)
        assertTrue(json.contains("\"id\":\"c1\""))
        assertTrue(json.contains("\"selectIndex\":0"))
        assertTrue(json.contains("\"type\":\"audio\""))
        assertTrue(json.contains("/api/files/content?uri="))
        assertTrue(json.contains("\"isGenerating\":true"))
    }

    @Test
    fun settingsDtoKeepsThemeAndAssistantIds() {
        val json = IosWebDto.settings(
            appearance = IosAppearancePreferences(themeId = "sakura"),
            assistantId = "a1",
            chatModelId = "model-1",
            assistants = listOf(IosAssistantPreferences(id = "a1", name = "Ada")),
            providers = emptyList(),
            skills = emptyList(),
            lorebooks = emptyList(),
            mcpServers = emptyList(),
            searchType = "keyless",
            enableWebSearch = true,
        )
        assertTrue(json.contains("\"themeId\":\"sakura\""))
        assertTrue(json.contains("\"assistantId\":\"a1\""))
        assertTrue(json.contains("\"enableWebSearch\":true"))
        assertTrue(json.contains("Ada"))
        assertTrue(json.contains("\"showMessageJumper\":false"))
        assertTrue(json.contains("\"newChatHeaderStyle\":\"GREETING\""))
        assertTrue(json.contains("\"newChatContentStyle\":\"ACTIONS\""))
        assertTrue(json.contains("\"enableMessageGenerationHapticEffect\":false"))
        assertTrue(json.contains("\"sttReplaceModelIcon\":false"))
        assertTrue(json.contains("\"codeBlockAutoWrap\":false"))
        assertTrue(json.contains("\"showContextStacks\":false"))
    }
}
