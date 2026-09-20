package me.rerere.lastchat.ios

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionEngine
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPromptMessageApplyTest {
    @Test
    fun topOfChatAndBeforeLatestWrapAsSystemUserMessages() {
        val history = listOf(
            UIMessage.user("older"),
            UIMessage.assistant("reply"),
            UIMessage.user("latest"),
        )
        val result = applyInContextPromptInjections(
            messages = history,
            skills = listOf(
                PortableSkill(
                    id = "top",
                    name = "Top",
                    instructions = "top-body",
                    injectionPosition = PromptInjectionPosition.TOP_OF_CHAT,
                ),
                PortableSkill(
                    id = "latest",
                    name = "Latest",
                    instructions = "latest-body",
                    injectionPosition = PromptInjectionPosition.BEFORE_LATEST,
                ),
            ),
            lorebookEntries = emptyList(),
        )
        assertEquals(MessageRole.USER, result.first().role)
        assertTrue(result.first().toText().contains("[Skill: Top]"))
        assertTrue(result[result.lastIndex - 1].toText().contains("[Skill: Latest]"))
        assertEquals("latest", result.last().toText())
    }

    @Test
    fun documentPromptUsesAndroidShapedFileWrapper() {
        val prompt = documentPromptText("notes.docx", "Hello")
        assertTrue(prompt.contains("## user sent a file: notes.docx"))
        assertTrue(prompt.contains("<content>\nHello\n</content>"))
    }
}
