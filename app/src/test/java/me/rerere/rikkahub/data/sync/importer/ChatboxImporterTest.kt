package me.rerere.rikkahub.data.sync.importer

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatboxImporterTest {
    @Test
    fun importsContentPartsConversations() {
        val result = ChatboxImporter.import(
            """
            {
              "__exported_items": ["setting", "conversations", "copilot", "key"],
              "configVersion": 14,
              "settings": {
                "providers": {
                  "openrouter": {
                    "apiKey": "sk-test",
                    "apiHost": "https://openrouter.ai/api",
                    "models": [
                      { "modelId": "openrouter/free", "nickname": "Free Models Router", "capabilities": ["reasoning"] }
                    ]
                  }
                }
              },
              "myCopilots": [
                {
                  "id": "copilot-1",
                  "name": "Coder",
                  "config": { "systemPrompt": "Be precise." }
                }
              ],
              "session:19475c1c-c361-4d8e-b094-3fccf47a02a5": {
                "id": "19475c1c-c361-4d8e-b094-3fccf47a02a5",
                "name": "Example Conversation",
                "starred": true,
                "copilotId": "copilot-1",
                "settings": {
                  "provider": "openrouter",
                  "modelId": "openrouter/free"
                },
                "messages": [
                  {
                    "id": "a700be6c-cbdd-43a3-b572-49e7a921c059",
                    "role": "system",
                    "contentParts": [
                      { "type": "text", "text": "You are helpful." }
                    ],
                    "timestamp": 1774203436843
                  },
                  {
                    "id": "844ab486-cd18-47b3-a560-27389178df80",
                    "role": "user",
                    "contentParts": [
                      { "type": "text", "text": "Write code" }
                    ],
                    "timestamp": 1774203450000
                  },
                  {
                    "id": "3c5b4b84-d258-4143-af28-bab29d280d8b",
                    "role": "assistant",
                    "model": "openrouter/free",
                    "aiProvider": "openrouter",
                    "contentParts": [
                      { "type": "reasoning", "text": "Plan briefly.", "startTime": 1774203470000, "duration": 1200 },
                      { "type": "text", "text": "Here is the code." },
                      { "type": "image", "url": "[https://example.com/diagram.png](https://example.com/diagram.png)" },
                      {
                        "type": "tool-call",
                        "state": "result",
                        "toolCallId": "call_abc123",
                        "toolName": "web_search",
                        "args": { "query": "kotlin" },
                        "result": { "answer": "ok" }
                      }
                    ],
                    "timestamp": 1774203480000,
                    "usage": {
                      "inputTokens": 788,
                      "outputTokens": 16,
                      "totalTokens": 804,
                      "cachedInputTokens": 7
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(1, result.providers.size)
        assertTrue(result.providers.single() is ProviderSetting.OpenAI)
        assertEquals(1, result.assistants.size)
        assertEquals("Coder", result.assistants.single().name)

        val conversation = result.conversations.single()
        assertEquals("Example Conversation", conversation.title)
        assertTrue(conversation.isPinned)
        assertEquals(result.assistants.single().id, conversation.assistantId)
        assertEquals(3, conversation.messageNodes.size)

        val system = conversation.messageNodes[0].currentMessage
        assertEquals(MessageRole.SYSTEM, system.role)
        assertEquals("You are helpful.", (system.parts.single() as UIMessagePart.Text).text)

        val assistant = conversation.messageNodes[2].currentMessage
        assertEquals(MessageRole.ASSISTANT, assistant.role)
        assertNotNull(assistant.modelId)
        assertEquals(788, assistant.usage?.promptTokens)
        assertEquals(16, assistant.usage?.completionTokens)
        assertEquals(7, assistant.usage?.cachedTokens)
        assertTrue(assistant.parts.any { it is UIMessagePart.Reasoning })
        assertTrue(assistant.parts.any { it is UIMessagePart.Text && it.text == "Here is the code." })
        assertTrue(assistant.parts.any { it is UIMessagePart.Image && it.url == "https://example.com/diagram.png" })
        assertTrue(assistant.parts.any { it is UIMessagePart.ToolResult && it.toolCallId == "call_abc123" })
    }

    @Test
    fun keepsLegacyContentFallback() {
        val result = ChatboxImporter.import(
            """
            {
              "session:legacy": {
                "name": "Legacy",
                "messages": [
                  { "role": "user", "content": "Plain content", "timestamp": 1774203450000 }
                ]
              }
            }
            """.trimIndent()
        )

        val message = result.conversations.single().messageNodes.single().currentMessage
        assertEquals("Plain content", (message.parts.single() as UIMessagePart.Text).text)
    }
}
