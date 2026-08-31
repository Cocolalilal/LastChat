package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantRegexCacheTest {
    @Test
    fun testValidRegexCompilationAndCaching() {
        val regex1 = getOrCompileRegex("\\d+")
        val regex2 = getOrCompileRegex("\\d+")
        assertNotNull(regex1)
        assertEquals(regex1, regex2)
    }

    @Test
    fun testInvalidRegexDoesNotThrowAndReturnsNull() {
        // Invalid syntax: unclosed bracket
        val invalidPattern = "[a-z"
        val result1 = getOrCompileRegex(invalidPattern)
        assertNull(result1)

        // Subsequent call must return null without re-evaluating or throwing
        val result2 = getOrCompileRegex(invalidPattern)
        assertNull(result2)
    }

    @Test
    fun testBlankPatternReturnsNull() {
        assertNull(getOrCompileRegex(""))
        assertNull(getOrCompileRegex("   "))
    }
}
