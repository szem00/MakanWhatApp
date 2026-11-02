package com.budgetapp.voiceexpense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query(
        "SELECT * FROM expenses WHERE timestamp >= :start AND timestamp < :end " +
            "ORDER BY timestamp DESC, id DESC",
    )
    fun observeBetween(start: Instant, end: Instant): Flow<List<Expense>>

    @Query(
        "SELECT COALESCE(SUM(amount_cents), 0) FROM expenses " +
            "WHERE timestamp >= :start AND timestamp < :end",
    )
    fun observeTotalCents(start: Instant, end: Instant): Flow<Long>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): Expense?

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("UPDATE expenses SET latitude = :latitude, longitude = :longitude WHERE id = :id")
    suspend fun setLocation(id: Long, latitude: Double, longitude: Double)

    @Query("SELECT * FROM expenses WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestamp DESC")
    suspend fun withLocation(): List<Expense>
}

@Dao
interface BudgetDao {
    @Query(
        "SELECT * FROM budgets WHERE period = :period AND category IS NULL " +
            "AND effective_from <= :onDate ORDER BY effective_from DESC, id DESC LIMIT 1",
    )
    fun observeOverall(period: BudgetPeriod, onDate: LocalDate): Flow<Budget?>

    @Insert
    suspend fun insert(budget: Budget): Long

    @Query(
        "DELETE FROM budgets WHERE period = :period AND category IS NULL " +
            "AND effective_from = :onDate",
    )
    suspend fun deleteOverallOn(period: BudgetPeriod, onDate: LocalDate)

    @Query("DELETE FROM budgets WHERE period = :period AND category IS NULL")
    suspend fun deleteOverall(period: BudgetPeriod)

    /** Keeps earlier amounts as history, but a second change on the same day replaces the first. */
    @Transaction
    suspend fun setOverall(period: BudgetPeriod, amountCents: Long, onDate: LocalDate) {
        deleteOverallOn(period, onDate)
        insert(Budget(period = period, amountCents = amountCents, effectiveFrom = onDate))
    }
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<Settings?>

    @Upsert
    suspend fun upsert(settings: Settings)
}
