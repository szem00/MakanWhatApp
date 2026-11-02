package com.budgetapp.voiceexpense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun dateToEpochDay(value: LocalDate): Long = value.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)
}

@Database(entities = [Expense::class, Budget::class, Settings::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "voice_expense.db").build()
    }
}
