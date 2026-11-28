package me.rerere.rikkahub.data.browser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.ToolResultImage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.computePreRequestToolAccounting
import me.rerere.rikkahub.data.ai.contextUsageSourceKey
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ScreenshotBudgetAndAccountingTest {

    @Test
    fun screenshotBudgetKeepsNewestAndElidesOlderWithMetadata() {
        val messages = (1..6).map { idx ->
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        toolCallId = "call_$idx",
                        toolName = "browser_use",
                        arguments = JsonPrimitive("{}"),
                        content = buildJsonObject {
                            put("image_file_path", JsonPrimitive("/tmp/shot_$idx.jpg"))
                            put("page_url", JsonPrimitive("https://example.com/$idx"))
                        },
                        inspectedImages = listOf(
                            ToolResultImage(
                                url = "data:image/jpeg;base64,AAA$idx",
                                mimeType = "image/jpeg",
                                title = "https://example.com/$idx",
                                sourceUrl = "/tmp/shot_$idx.jpg",
                                originTool = "browser_use",
                            )
                        ),
                    )
                ),
            )
        }

        val budgeted = ScreenshotBudget.apply(messages, maxScreenshots = 4)
        val activeCounts = budgeted.map { msg ->
            (msg.parts.first() as UIMessagePart.ToolResult).inspectedImages.count { it.url.isNotBlank() }
        }
        assertEquals(listOf(0, 0, 1, 1, 1, 1), activeCounts)

        val elidedPart = budgeted[0].parts.first() as UIMessagePart.ToolResult
        val elidedNote = (elidedPart.content as JsonObject)["screenshot_elided"].toString()
        assertTrue(elidedNote.contains("/tmp/shot_1.jpg"))
        assertTrue(elidedNote.contains("https://example.com/1"))
    }

    @Test
    fun contextUsageSourceKeyInvalidatesOnBrowserSettingsAndSecretsRevision() {
        val assistant = Assistant(id = Uuid.random(), name = "Tester", localTools = listOf(LocalToolOption.Browser))
        val conversation = Conversation.ofAssistant(assistant)
        val model = Model(modelId = "gpt-test", abilities = listOf(ModelAbility.TOOL), inputModalities = listOf(Modality.TEXT, Modality.IMAGE))
        val baseSettings = Settings()

        val key0 = contextUsageSourceKey(conversation, assistant, model, baseSettings, browserSecretsRevision = 0)
        val key1 = contextUsageSourceKey(conversation, assistant, model, baseSettings, browserSecretsRevision = 1)
        val key2 = contextUsageSourceKey(
            conversation,
            assistant,
            model,
            baseSettings.copy(browserSettings = BrowserSettings(useCompactToolSchema = false)),
            browserSecretsRevision = 0,
        )

        assertNotEquals(key0, key1)
        assertNotEquals(key0, key2)
    }

    @Test
    fun preRequestToolAccountingDropsBrowserTokensToZeroWhenDisabled() {
        val model = Model(
            modelId = "gpt-test",
            contextWindowTokens = 32_000,
            abilities = listOf(ModelAbility.TOOL),
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
        )
        val settings = Settings(enableStudioHubBridge = false)
        val withoutBrowser = Assistant(id = Uuid.random(), name = "Off", localTools = emptyList())
        val withBrowserCompact = Assistant(id = Uuid.random(), name = "On", localTools = listOf(LocalToolOption.Browser))

        val accOff = computePreRequestToolAccounting(
            model = model,
            assistant = withoutBrowser,
            settings = settings,
            includeMemoryTools = false,
            availableSkills = emptyList(),
            assistantDefaultSkillIds = emptySet(),
            conversationSkillIds = emptySet(),
            workspaceReady = false,
        )
        assertEquals(0, accOff.localAndSearchTokens)
        assertFalse(accOff.toolNames.contains("browser_use"))

        val accOnCompact = computePreRequestToolAccounting(
            model = model,
            assistant = withBrowserCompact,
            settings = settings.copy(browserSettings = BrowserSettings(useCompactToolSchema = true)),
            includeMemoryTools = false,
            availableSkills = emptyList(),
            assistantDefaultSkillIds = emptySet(),
            conversationSkillIds = emptySet(),
            workspaceReady = false,
        )
        assertTrue(accOnCompact.toolNames.contains("browser_use"))
        assertTrue(accOnCompact.localAndSearchTokens > 0)
        assertTrue(accOnCompact.embeddedToolText.contains("Agent Browser"))

        val accOnVerbose = computePreRequestToolAccounting(
            model = model,
            assistant = withBrowserCompact,
            settings = settings.copy(browserSettings = BrowserSettings(useCompactToolSchema = false)),
            includeMemoryTools = false,
            availableSkills = emptyList(),
            assistantDefaultSkillIds = emptySet(),
            conversationSkillIds = emptySet(),
            workspaceReady = false,
        )
        assertTrue(accOnVerbose.localAndSearchTokens > accOnCompact.localAndSearchTokens)
    }
}
