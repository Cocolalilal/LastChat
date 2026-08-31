package me.rerere.ai.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormattingTest {
    @Test
    fun formatsBalanceWithExactlyTwoDecimalPlaces() {
        assertEquals("2.50", formatFixed2(2.5f))
        assertEquals("0.00", formatFixed2(0f))
        assertEquals("-1.24", formatFixed2(-1.236f))
    }
}
