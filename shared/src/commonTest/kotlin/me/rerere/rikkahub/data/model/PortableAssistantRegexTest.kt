package me.rerere.rikkahub.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class PortableAssistantRegexTest {
    @Test
    fun generationRegexRewritesMatchingText() {
        val rules = listOf(
            PortableAssistantRegex(
                id = Uuid.random().toString(),
                findRegex = "cat",
                replaceString = "dog",
                affectingScope = setOf(PortableAffectScope.ASSISTANT),
            ),
            PortableAssistantRegex(
                id = Uuid.random().toString(),
                findRegex = "secret",
                replaceString = "redacted",
                affectingScope = setOf(PortableAffectScope.ASSISTANT),
                visualOnly = true,
            ),
        )
        assertEquals("the dog sat", "the cat sat".replacePortableRegexes(rules, PortableAffectScope.ASSISTANT))
        assertEquals("the cat sat", "the cat sat".replacePortableRegexes(rules, PortableAffectScope.USER))
        assertEquals("keep secret", "keep secret".replacePortableRegexes(rules, PortableAffectScope.ASSISTANT))
        assertEquals(
            "keep redacted",
            "keep secret".replacePortableRegexes(rules, PortableAffectScope.ASSISTANT, visual = true),
        )
    }

    @Test
    fun invalidPatternIsSkipped() {
        val rules = listOf(
            PortableAssistantRegex(
                id = "r1",
                findRegex = "[a-z",
                replaceString = "x",
                affectingScope = setOf(PortableAffectScope.USER),
            ),
        )
        assertEquals("hello", "hello".replacePortableRegexes(rules, PortableAffectScope.USER))
        assertNull(runCatching { Regex("[a-z") }.getOrNull())
    }
}
