package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseSource
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val header = "id,date,time,amount,currency,category,note,source,raw_transcript"

    private fun expense(
        id: Long = 1,
        at: LocalDateTime = LocalDateTime.of(2026, 11, 4, 12, 30, 5),
        cents: Long = 550,
        category: Category = Category.LUNCH,
        note: String = "",
        source: ExpenseSource = ExpenseSource.MANUAL,
        transcript: String? = null,
    ) = Expense(
        id = id,
        timestamp = at.atZone(zone).toInstant(),
        amountCents = cents,
        category = category,
        note = note,
        source = source,
        rawTranscript = transcript,
    )

    private fun lines(vararg expenses: Expense): List<String> =
        expensesToCsv(expenses.toList(), zone).removePrefix("﻿").split("\r\n")

    @Test
    fun startsWithAByteOrderMarkSoNonEnglishNotesOpenCorrectly() {
        assertTrue(expensesToCsv(emptyList(), zone).startsWith("﻿"))
    }

    @Test
    fun noExpensesIsJustTheHeader() {
        assertEquals(listOf(header, ""), lines())
    }

    @Test
    fun writesEveryColumn() {
        val row = lines(
            expense(
                id = 7,
                note = "Chicken rice",
                source = ExpenseSource.VOICE,
                transcript = "lunch chicken rice 5.50",
            ),
        )[1]
        assertEquals(
            "7,2026-11-04,12:30:05,5.50,SGD,Lunch,Chicken rice,voice,lunch chicken rice 5.50",
            row,
        )
    }

    @Test
    fun blankFieldsStayInPlace() {
        assertEquals("1,2026-11-04,12:30:05,5.50,SGD,Lunch,,manual,", lines(expense())[1])
    }

    @Test
    fun amountsAreAlwaysTwoDecimalsWithNoSymbolOrSeparator() {
        assertEquals("0.05", lines(expense(cents = 5))[1].split(",")[3])
        assertEquals("1234.50", lines(expense(cents = 123_450))[1].split(",")[3])
    }

    @Test
    fun usesTheLocalDateNotUtc() {
        // 00:30 on the 4th in Singapore is still the 3rd in UTC.
        val row = lines(expense(at = LocalDateTime.of(2026, 11, 4, 0, 30)))[1]
        assertEquals("2026-11-04", row.split(",")[1])
        assertEquals("00:30:00", row.split(",")[2])
    }

    @Test
    fun quotesFieldsThatWouldBreakTheFile() {
        assertEquals(
            "1,2026-11-04,12:30:05,5.50,SGD,Lunch,\"rice, kopi\",manual,",
            lines(expense(note = "rice, kopi"))[1],
        )
        assertEquals(
            "1,2026-11-04,12:30:05,5.50,SGD,Lunch,\"the \"\"good\"\" one\",manual,",
            lines(expense(note = "the \"good\" one"))[1],
        )
    }

    @Test
    fun keepsLineBreaksInsideQuotes() {
        val csv = expensesToCsv(listOf(expense(note = "first\nsecond")), zone)
        assertTrue(csv.contains("\"first\nsecond\""))
    }

    @Test
    fun keepsNonEnglishText() {
        assertTrue(lines(expense(note = "海南鸡饭 咖啡"))[1].contains("海南鸡饭 咖啡"))
    }

    @Test
    fun rowsAreOldestFirst() {
        val rows = lines(
            expense(id = 2, at = LocalDateTime.of(2026, 11, 4, 12, 0)),
            expense(id = 3, at = LocalDateTime.of(2026, 11, 5, 12, 0)),
            expense(id = 1, at = LocalDateTime.of(2026, 11, 3, 12, 0)),
        )
        assertEquals(listOf("1", "2", "3"), rows.drop(1).dropLast(1).map { it.substringBefore(",") })
    }

    @Test
    fun endsEveryRowWithALineBreak() {
        assertTrue(expensesToCsv(listOf(expense()), zone).endsWith("\r\n"))
    }
}
