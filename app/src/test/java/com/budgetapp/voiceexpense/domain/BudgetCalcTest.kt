package com.budgetapp.voiceexpense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalcTest {
    @Test
    fun summaryWithoutBudgetHasNoRemaining() {
        val summary = PeriodSummary(spentCents = 500, budgetCents = null)
        assertNull(summary.remainingCents)
        assertNull(summary.fractionUsed)
        assertFalse(summary.isOverBudget)
    }

    @Test
    fun summaryUnderBudget() {
        val summary = PeriodSummary(spentCents = 2_500, budgetCents = 10_000)
        assertEquals(7_500L, summary.remainingCents)
        assertEquals(0.25f, summary.fractionUsed!!, 0.0001f)
        assertFalse(summary.isOverBudget)
    }

    @Test
    fun spendingExactlyTheBudgetIsNotOver() {
        val summary = PeriodSummary(spentCents = 10_000, budgetCents = 10_000)
        assertEquals(0L, summary.remainingCents)
        assertFalse(summary.isOverBudget)
    }

    @Test
    fun summaryOverBudgetCapsTheBar() {
        val summary = PeriodSummary(spentCents = 12_000, budgetCents = 10_000)
        assertEquals(-2_000L, summary.remainingCents)
        assertEquals(1f, summary.fractionUsed!!, 0.0001f)
        assertTrue(summary.isOverBudget)
    }

    @Test
    fun budgetIsNearlyUsedFrom85PercentUntilItGoesOver() {
        assertFalse(PeriodSummary(spentCents = 8_499, budgetCents = 10_000).isNearlyUsed)
        assertTrue(PeriodSummary(spentCents = 8_500, budgetCents = 10_000).isNearlyUsed)
        assertTrue(PeriodSummary(spentCents = 10_000, budgetCents = 10_000).isNearlyUsed)
        assertFalse(PeriodSummary(spentCents = 10_001, budgetCents = 10_000).isNearlyUsed)
        assertFalse(PeriodSummary(spentCents = 50_000, budgetCents = null).isNearlyUsed)
    }

    @Test
    fun safeToSpendSplitsRemainingAndRoundsDown() {
        assertEquals(1_880L, safeToSpendPerDayCents(9_400, 5))
        assertEquals(333L, safeToSpendPerDayCents(1_000, 3))
        assertEquals(1_000L, safeToSpendPerDayCents(1_000, 1))
    }

    @Test
    fun safeToSpendIsNeverNegative() {
        assertEquals(0L, safeToSpendPerDayCents(-500, 3))
        assertEquals(0L, safeToSpendPerDayCents(0, 3))
        assertEquals(0L, safeToSpendPerDayCents(500, 0))
    }
}
