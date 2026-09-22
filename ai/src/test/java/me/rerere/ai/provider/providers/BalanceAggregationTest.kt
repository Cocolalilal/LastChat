package me.rerere.ai.provider.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceAggregationTest {

    @Test
    fun testParseBalanceAmount() {
        val p1 = parseBalanceAmount("12.34")
        assertNotNull(p1)
        assertEquals("", p1!!.prefix)
        assertEquals(12.34, p1.amount, 0.001)
        assertEquals("", p1.suffix)

        val p2 = parseBalanceAmount("$12.34")
        assertNotNull(p2)
        assertEquals("$", p2!!.prefix)
        assertEquals(12.34, p2.amount, 0.001)
        assertEquals("", p2.suffix)

        val p3 = parseBalanceAmount("¥ 50.00")
        assertNotNull(p3)
        assertEquals("¥ ", p3!!.prefix)
        assertEquals(50.0, p3.amount, 0.001)
        assertEquals("", p3.suffix)

        val p4 = parseBalanceAmount("100 credits")
        assertNotNull(p4)
        assertEquals("", p4!!.prefix)
        assertEquals(100.0, p4.amount, 0.001)
        assertEquals(" credits", p4.suffix)

        val p5 = parseBalanceAmount("1,234.56 USD")
        assertNotNull(p5)
        assertEquals("", p5!!.prefix)
        assertEquals(1234.56, p5.amount, 0.001)
        assertEquals(" USD", p5.suffix)

        val p6 = parseBalanceAmount("-5.20")
        assertNotNull(p6)
        assertEquals("", p6!!.prefix)
        assertEquals(-5.2, p6.amount, 0.001)
        assertEquals("", p6.suffix)

        val p7 = parseBalanceAmount("Unlimited")
        assertNull(p7)
    }

    @Test
    fun testAggregateBalancesSingle() {
        assertEquals("12.34", aggregateBalances(listOf("12.34")))
        assertEquals("$12.34", aggregateBalances(listOf("$12.34")))
        assertEquals("Unlimited", aggregateBalances(listOf("Unlimited")))
    }

    @Test
    fun testAggregateBalancesMultiple() {
        assertEquals("35.50", aggregateBalances(listOf("10.00", "25.50")))
        assertEquals("$35.50", aggregateBalances(listOf("$10.00", "$25.50")))
        assertEquals("350.00 credits", aggregateBalances(listOf("100 credits", "250 credits")))
        assertEquals("1500.50 USD", aggregateBalances(listOf("1,000.50 USD", "500 USD")))
        assertEquals("Unlimited", aggregateBalances(listOf("Unlimited", "Unlimited")))
    }
}
