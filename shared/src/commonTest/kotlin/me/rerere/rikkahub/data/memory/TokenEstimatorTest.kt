package me.rerere.rikkahub.data.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenEstimatorTest {

    @Test
    fun estimate_handlesEmptyAndZero() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun estimate_measuresLatinAndCjkAccurately() {
        val latinText = "This is a simple English sentence for token estimation."
        val latinTokens = TokenEstimator.estimate(latinText)
        assertTrue(latinTokens in 10..20, "Expected ~15 tokens, got $latinTokens")

        val cjkText = "这是用于测试Token计算的一段中文文本"
        val cjkTokens = TokenEstimator.estimate(cjkText)
        assertTrue(cjkTokens in 10..20, "Expected ~13 tokens, got $cjkTokens")
    }

    @Test
    fun truncateToTokenBudget_preservesShortText() {
        val text = "Brief summary text."
        assertEquals(text, TokenEstimator.truncateToTokenBudget(text, 500))
    }

    @Test
    fun truncateToTokenBudget_truncatesAndAppendsMessage() {
        val lines = (1..100).joinToString("\n") { "Line $it with various details and hardware preferences [#$it]" }
        val truncated = TokenEstimator.truncateToTokenBudget(lines, 40)
        assertTrue(truncated.contains("[Summary truncated to fit context budget."))
        assertTrue(truncated.lines().size < 20)
    }
}
