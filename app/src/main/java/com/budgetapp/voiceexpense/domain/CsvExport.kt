package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseSource
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val BYTE_ORDER_MARK = "﻿"
private const val LINE_END = "\r\n"
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val HEADER = listOf(
    "id", "date", "time", "amount", "currency", "category", "note", "source", "raw_transcript",
)

/**
 * One row per expense, oldest first, in the phone's time zone. Power BI and Excel both use the byte
 * order mark to read the file as UTF-8, and the plain dates and amounts to detect column types.
 */
fun expensesToCsv(expenses: List<Expense>, zone: ZoneId): String = buildString {
    append(BYTE_ORDER_MARK)
    append(HEADER.joinToString(",")).append(LINE_END)
    expenses.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { expense ->
        val moment = expense.timestamp.atZone(zone)
        val row = listOf(
            expense.id.toString(),
            moment.toLocalDate().toString(),
            moment.toLocalTime().format(TIME_FORMAT),
            centsToInput(expense.amountCents),
            expense.currency,
            expense.category.name.lowercase().replaceFirstChar { it.uppercase() },
            expense.note,
            if (expense.source == ExpenseSource.VOICE) "voice" else "manual",
            expense.rawTranscript.orEmpty(),
        )
        append(row.joinToString(",", transform = ::csvField)).append(LINE_END)
    }
}

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
