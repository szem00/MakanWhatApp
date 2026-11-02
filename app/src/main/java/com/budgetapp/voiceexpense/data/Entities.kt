package com.budgetapp.voiceexpense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

// Room stores these enums by name, so renaming a value needs a database migration.
enum class Category { BREAKFAST, LUNCH, DINNER, SUPPER, TRANSPORT, GROCERIES, SHOPPING, OTHER }

enum class ExpenseSource { MANUAL, VOICE }

enum class BudgetPeriod { WEEK, MONTH }

@Entity(tableName = "expenses", indices = [Index("timestamp")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val currency: String = "SGD",
    val category: Category,
    val note: String = "",
    @ColumnInfo(name = "raw_transcript") val rawTranscript: String? = null,
    val source: ExpenseSource = ExpenseSource.MANUAL,
)

/** A null [category] is the overall budget for the period. */
@Entity(tableName = "budgets", indices = [Index("period", "effective_from")])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: BudgetPeriod,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val category: Category? = null,
    @ColumnInfo(name = "effective_from") val effectiveFrom: LocalDate,
)

/** Always a single row, so a missing row means the defaults. */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "week_start_day") val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
)
