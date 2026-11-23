package me.rerere.rikkahub.utils

import me.rerere.rikkahub.data.datastore.TtsFilterMode
import me.rerere.rikkahub.data.datastore.TtsTextFilterRule
import org.junit.Assert.*
import org.junit.Test

class TtsTextFiltersTest {
    private val skip = listOf(TtsTextFilterRule(pattern = "*"))

    @Test fun skipsRoleplayButRetainsBoldAndCompleteDialogueParagraphs() {
        assertEquals(listOf("Hello.", "Important dialogue survives.", "Goodbye."), prepareTtsText(
            "*waves* Hello.\n\n**Important dialogue survives.**\n\n*smiles* Goodbye.", skip).split("\n\n").map(String::trim))
    }

    @Test fun unmatchedStarsCannotConsumeAnotherParagraphOrListItems() {
        val text = "Unmatched *action\n\nA complete paragraph.\n\n* First item\n* Second item"
        val result = prepareTtsText(text, skip)
        assertTrue(result, result.contains("A complete paragraph."))
        assertTrue(result, result.contains("First item"))
        assertTrue(result, result.contains("Second item"))
    }

    @Test fun escapedStarsAndLongerDelimiterRunsDoNotBecomeActionPairs() {
        assertEquals("Literal \\*stars\\* and **bold**.", applyTtsTextFilters("Literal \\*stars\\* and **bold**.", skip))
        assertEquals("**bold one** plain **bold two**", applyTtsTextFilters("**bold one** plain **bold two**", skip))
        assertEquals("before   after", applyTtsTextFilters("before *first\nsecond* after", skip))
    }

    @Test fun onlyReadRulesPreserveMessageOrderAndSkipWithinEachSelection() {
        val rules = listOf(TtsTextFilterRule(pattern = "'", mode = TtsFilterMode.ONLY_READ),
            TtsTextFilterRule(pattern = "\"", mode = TtsFilterMode.ONLY_READ), TtsTextFilterRule(pattern = "*"))
        assertEquals("First Second", prepareTtsText("\"First\" outside 'Second *gesture*'", rules))
        assertEquals("A * B", applyTtsTextFilters("\"A *\" outside \"B\"", listOf(rules[1], rules[2])))
    }

    @Test fun disabledAndEmptyRulesDoNotDeleteSpeech() {
        assertEquals("*action* **bold**", applyTtsTextFilters("*action* **bold**",
            listOf(TtsTextFilterRule(pattern = "", enabled = true), TtsTextFilterRule(enabled = false))))
    }
}
