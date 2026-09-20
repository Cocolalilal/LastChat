package me.rerere.rikkahub.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class TtsTextFiltersTest {
    @Test
    fun skipRemovesPairedPatternAndThenStripsMarkdown() {
        val rules = listOf(
            TtsTextFilterRule(id = "skip-pipe", pattern = "||", mode = TtsFilterMode.SKIP),
        )
        val spoken = prepareTtsPlaybackText("Hello ||aside|| **world**", rules)
        assertEquals("Hello  world", spoken)
    }

    @Test
    fun onlyReadExtractsCapturesBeforeSkip() {
        val rules = listOf(
            TtsTextFilterRule(id = "only", pattern = "||", mode = TtsFilterMode.ONLY_READ),
            TtsTextFilterRule(id = "skip", pattern = "*", mode = TtsFilterMode.SKIP),
        )
        val spoken = prepareTtsPlaybackText("ignore ||keep this|| and *drop* ||also this||", rules)
        assertEquals("keep this also this", spoken)
    }

    @Test
    fun disabledRulesAreIgnoredAndBlankOnlyReadSpeaksNothing() {
        val disabledSkip = listOf(
            TtsTextFilterRule(id = "off", pattern = "*", mode = TtsFilterMode.SKIP, enabled = false),
        )
        assertEquals("plain text", prepareTtsPlaybackText("plain *text*", disabledSkip))
        val onlyRead = listOf(
            TtsTextFilterRule(id = "only", pattern = "||", mode = TtsFilterMode.ONLY_READ),
        )
        assertEquals("", prepareTtsPlaybackText("no delimiters here", onlyRead))
    }

    @Test
    fun stripMarkdownMatchesAndroidHighlightAndListRules() {
        assertEquals(
            "Here is highlighted text in a sentence.",
            "Here is ==highlighted text== in a sentence.".stripMarkdown(),
        )
        assertEquals(
            "Here is highlighted text in a sentence.",
            "Here is <mark>highlighted text</mark> in a sentence.".stripMarkdown(),
        )
        assertEquals(
            "Here is underlined text in a sentence.",
            "Here is ++underlined text++ in a sentence.".stripMarkdown(),
        )
        assertEquals(
            "item one\nitem two",
            "- item one\n1. item two".stripMarkdown(),
        )
    }
}
