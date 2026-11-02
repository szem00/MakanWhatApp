package com.budgetapp.voiceexpense.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fork's database moves to version 2, so expenses from the main app's version 1 must survive.
 * Room's MigrationTestHelper clashes with the serialization library the app already ships, so this
 * builds version 1 by hand and lets Room migrate it. Room checks every table against version 2 as
 * it opens, so a wrong migration fails here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE)
    }

    /** Version 1 exactly as the main app creates it, from app/schemas/…/1.json. */
    private fun createVersion1WithALunch() {
        val file = context.getDatabasePath(DATABASE).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_1_SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO expenses (timestamp, amount_cents, currency, category, note, raw_transcript, source) " +
                    "VALUES (1762230600000, 550, 'SGD', 'LUNCH', 'Chicken rice', 'lunch chicken rice 5.50', 'VOICE')",
            )
            db.execSQL("INSERT INTO budgets (period, amount_cents, category, effective_from) VALUES ('WEEK', 15000, NULL, 20393)")
            db.version = 1
        }
    }

    private fun <T> withMigrated(block: suspend (AppDatabase) -> T): T {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE)
            .addMigrations(MIGRATION_1_2)
            .build()
        return try {
            runBlocking { block(database) }
        } finally {
            database.close()
        }
    }

    @Test
    fun version1ExpensesKeepEverythingAndHaveNoLocation() {
        createVersion1WithALunch()
        withMigrated { database ->
            val expense = database.expenseDao().observeAll().first().single()
            assertEquals(550L, expense.amountCents)
            assertEquals("Chicken rice", expense.note)
            assertEquals("lunch chicken rice 5.50", expense.rawTranscript)
            assertEquals(ExpenseSource.VOICE, expense.source)
            assertNull(expense.latitude)
            assertNull(expense.longitude)

            val budget = database.budgetDao().observeOverall(BudgetPeriod.WEEK, LocalDate.of(2026, 11, 4)).first()
            assertEquals(15_000L, budget?.amountCents)
        }
    }

    @Test
    fun aMigratedExpenseCanTakeALocation() {
        createVersion1WithALunch()
        withMigrated { database ->
            val dao = database.expenseDao()
            dao.setLocation(dao.observeAll().first().single().id, 1.284, 103.8514)
            val located = dao.withLocation().single()
            assertEquals(1.284, located.latitude!!, 0.0)
            assertEquals(103.8514, located.longitude!!, 0.0)
        }
    }

    private companion object {
        const val DATABASE = "migration-test"

        val VERSION_1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `amount_cents` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `note` TEXT NOT NULL, `raw_transcript` TEXT, `source` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`period` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `category` TEXT, " +
                "`effective_from` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_budgets_period_effective_from` ON `budgets` (`period`, `effective_from`)",
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `week_start_day` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}
