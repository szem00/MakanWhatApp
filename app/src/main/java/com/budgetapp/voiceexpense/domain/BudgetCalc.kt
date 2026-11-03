package com.budgetapp.voiceexpense.domain

data class PeriodSummary(val spentCents: Long, val budgetCents: Long?) {
    val remainingCents: Long? get() = budgetCents?.minus(spentCents)

    val isOverBudget: Boolean get() = (remainingCents ?: 0) < 0

    val fractionUsed: Float?
        get() = budgetCents?.let { (spentCents.toFloat() / it).coerceIn(0f, 1f) }

    /** Most of the budget is gone but not all of it, which turns the bar amber before red. */
    val isNearlyUsed: Boolean
        get() = !isOverBudget && (fractionUsed ?: 0f) >= NEARLY_USED_FRACTION
}

private const val NEARLY_USED_FRACTION = 0.85f

/** Rounds down, so spending this much every day can't go over budget. */
fun safeToSpendPerDayCents(remainingCents: Long, daysLeft: Int): Long =
    if (daysLeft <= 0) 0 else remainingCents.coerceAtLeast(0) / daysLeft
