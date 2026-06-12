package me.rerere.rikkahub.data.ai.prompts

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionPromptTest {
    @Test
    fun contentMarksUserAsTargetSpeakerAndIncludesStyleExamples() {
        val content = buildSuggestionPromptContent(
            messages = listOf(
                UIMessage.user("lol wait what"),
                UIMessage.assistant("I can explain it another way."),
                UIMessage.user("yeah go on"),
                UIMessage.assistant("Want the short version?")
            ),
            truncateIndex = -1,
        )

        assertTrue(content.contains("USER = the human app user"))
        assertTrue(content.contains("ASSISTANT = the character/AI"))
        assertTrue(content.contains("USER_STYLE_EXAMPLE: lol wait what"))
        assertTrue(content.contains("USER_STYLE_EXAMPLE: yeah go on"))
        assertTrue(content.contains("ASSISTANT: Want the short version?"))
        assertTrue(content.contains("Next speaker to suggest: USER"))
    }

    @Test
    fun parseSuggestionLinesStripsUserLabelsAndDropsAssistantLines() {
        val suggestions = parseSuggestionLines(
            """
            1. User: yeah go on
            - Assistant: Sure, I can do that.
            [USER]: wait why
            "lol okay"
            """.trimIndent()
        )

        assertEquals(listOf("yeah go on", "wait why", "lol okay"), suggestions)
        assertFalse(suggestions.any { it.contains("Assistant", ignoreCase = true) })
    }

    @Test
    fun parseSuggestionLinesPreservesLeadingRoleplayAsterisk() {
        val suggestions = parseSuggestionLines(
            """
            *walking closer* Are you okay?
            * pauses and thinks
            - *smiles* Yeah, I'm fine.
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "*walking closer* Are you okay?",
                "pauses and thinks",
                "*smiles* Yeah, I'm fine.",
            ),
            suggestions,
        )
    }
}
