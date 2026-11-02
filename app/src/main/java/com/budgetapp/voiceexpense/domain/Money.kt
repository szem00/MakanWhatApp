package com.budgetapp.voiceexpense.domain

import java.math.BigDecimal
import java.util.Locale
import kotlin.math.abs

internal const val MAX_CENTS = 999_999_999L
private val AMOUNT_PATTERN = Regex("""\d+(\.\d{0,2})?|\.\d{1,2}""")

/** Returns null unless [input] is a positive dollar amount with at most two decimals. */
fun parseAmountToCents(input: String): Long? {
    val text = input.trim().replace(',', '.')
    if (!AMOUNT_PATTERN.matches(text)) return null
    val cents = BigDecimal(text).movePointRight(2)
    if (cents.signum() <= 0 || cents > BigDecimal.valueOf(MAX_CENTS)) return null
    return cents.toLong()
}

fun formatMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val value = abs(cents)
    return String.format(Locale.US, "%s$%,d.%02d", sign, value / 100, value % 100)
}

/** The plain form shown in an amount field, without the dollar sign or thousands separators. */
fun centsToInput(cents: Long): String =
    String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)
