package com.budgetapp.voiceexpense.domain

data class PeriodSummary(val spentCents: Long, val budgetCents: Long?) {
    val remainingCents: Long? get() = budgetCents?.minus(spentCents)

    val isOverBudget: Boolean get() = (remainingCents ?: 0) < 0

    val fractionUsed: Float?
        get() = budgetCents?.let { (spentCents.toFloat() / it).coerceIn(0f, 1f) }
}

/** Rounds down, so spending this much every day can't go over budget. */
fun safeToSpendPerDayCents(remainingCents: Long, daysLeft: Int): Long =
    if (daysLeft <= 0) 0 else remainingCents.coerceAtLeast(0) / daysLeft
