package com.budgetapp.voiceexpense

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the screens quick capture's Edit and Type buttons open in the full app. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ShortcutEntryTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<VoiceExpenseApp>()

    private lateinit var database: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        app.containerOverride = AppContainer(
            database = database,
            themeSettings = testThemeSettings(app),
            createVoiceRecognizer = { FakeVoiceRecognizer() },
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        app.containerOverride = null
        database.close()
    }

    private fun open(intent: Intent) {
        scenario = ActivityScenario.launch(intent)
    }

    private fun waitFor(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), 5_000)
    }

    @Test
    fun editOpensTheExpenseAndBackGoesHome() {
        val id = runBlocking {
            database.expenseDao().insert(
                Expense(
                    timestamp = Instant.now(),
                    amountCents = 550,
                    category = Category.LUNCH,
                    note = "Chicken rice",
                ),
            )
        }
        open(MainActivity.editIntent(app, id))

        waitFor("Edit expense")
        compose.onNodeWithText("5.50").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("This week")
    }

    @Test
    fun typingAPhraseWithNoPriceOpensTheConfirmCard() {
        open(MainActivity.entryIntent(app, "lunch chicken rice"))

        waitFor("Confirm expense")
        waitFor("Heard: “lunch chicken rice”")
        waitFor("No amount heard. Type it here.")
    }

    @Test
    fun typingWithNothingHeardOpensABlankForm() {
        open(MainActivity.entryIntent(app, null))

        waitFor("Add expense")
        compose.onNodeWithText("Amount").assertExists()
    }

    @Test
    fun openingNormallyStaysOnHome() {
        open(Intent(app, MainActivity::class.java))

        waitFor("This week")
        compose.onNodeWithText("Amount").assertDoesNotExist()
    }
}
