package com.budgetapp.voiceexpense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesAmountsPeopleType() {
        assertEquals(550L, parseAmountToCents("5.50"))
        assertEquals(500L, parseAmountToCents("5"))
        assertEquals(500L, parseAmountToCents("5."))
        assertEquals(50L, parseAmountToCents(".5"))
        assertEquals(550L, parseAmountToCents("5.5"))
        assertEquals(1230L, parseAmountToCents(" 12.30 "))
        assertEquals(550L, parseAmountToCents("5,50"))
        assertEquals(1L, parseAmountToCents("0.01"))
    }

    @Test
    fun rejectsAnythingElse() {
        listOf("", " ", ".", "0", "0.00", "-1", "abc", "1.234", "1,000.50", "5.5.5", "1e3", "٥")
            .forEach { assertNull("should reject '$it'", parseAmountToCents(it)) }
    }

    @Test
    fun rejectsAmountsTooLargeToBeReal() {
        assertEquals(999_999_999L, parseAmountToCents("9999999.99"))
        assertNull(parseAmountToCents("10000000"))
        assertNull(parseAmountToCents("99999999999999999999999"))
    }

    @Test
    fun formatsMoney() {
        assertEquals("$0.00", formatMoney(0))
        assertEquals("$0.05", formatMoney(5))
        assertEquals("$5.50", formatMoney(550))
        assertEquals("$1,234.56", formatMoney(123_456))
        assertEquals("-$12.00", formatMoney(-1_200))
    }

    @Test
    fun amountFieldTextParsesBackToTheSameCents() {
        listOf(1L, 50L, 550L, 123_456L).forEach { cents ->
            assertEquals(cents, parseAmountToCents(centsToInput(cents)))
        }
    }
}
