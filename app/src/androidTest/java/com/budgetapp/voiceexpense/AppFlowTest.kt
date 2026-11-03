package com.budgetapp.voiceexpense

import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ThemeMode
import com.budgetapp.voiceexpense.data.ThemeSettings
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against an in-memory database, on a Wednesday at 12:30 with 5 days left in the week. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val zone = ZoneId.of("Asia/Singapore")
    private val now = LocalDateTime.of(2026, 11, 4, 12, 30)
    private val app get() = ApplicationProvider.getApplicationContext<VoiceExpenseApp>()

    private lateinit var database: AppDatabase
    private lateinit var themeSettings: ThemeSettings
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        themeSettings = testThemeSettings(app)
        app.containerOverride = AppContainer(
            database = database,
            themeSettings = themeSettings,
            clock = Clock.fixed(now.atZone(zone).toInstant(), zone),
            createVoiceRecognizer = { FakeVoiceRecognizer() },
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        app.containerOverride = null
        database.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), 5_000)
    }

    private fun waitUntilGone(text: String) {
        compose.waitUntilDoesNotExist(hasText(text), 5_000)
    }

    private fun addExpense(amount: String, note: String = "") {
        compose.onNodeWithContentDescription("Add expense").performClick()
        waitFor("Amount")
        compose.onNodeWithText("Amount").performTextInput(amount)
        if (note.isNotEmpty()) compose.onNodeWithText("Note (optional)").performTextInput(note)
        compose.onNodeWithText("Save").performClick()
        waitFor("This week")
    }

    /** Reads the top corner of the screen, which is the app bar's surface colour in both themes. */
    private fun isDrawnDark(): Boolean {
        val screen = compose.onRoot().captureToImage().asAndroidBitmap()
        return Color.luminance(screen.getPixel(4, 4)) < 0.5f
    }

    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
    }

    /** Runs [block] with the phone's own dark mode set, then puts it back as it was. */
    private fun withPhoneDarkMode(dark: Boolean, block: () -> Unit) {
        val before = shell("cmd uimode night").substringAfter("Night mode: ").trim()
        shell("cmd uimode night ${if (dark) "yes" else "no"}")
        try {
            block()
        } finally {
            shell("cmd uimode night $before")
        }
    }

    private fun setBudgets(weekly: String, monthly: String) {
        compose.onNodeWithContentDescription("Budgets and settings").performClick()
        waitFor("Weekly budget")
        compose.onNodeWithText("Weekly budget").performTextInput(weekly)
        compose.onNodeWithText("Monthly budget").performTextInput(monthly)
        compose.onNodeWithText("Save").performClick()
        waitFor("This week")
    }

    @Test
    fun startsEmptyWithNoBudgets() {
        launch()
        waitFor("Nothing logged today.")
        compose.onNodeWithText("Mon 2 Nov – Sun 8 Nov").assertExists()
        compose.onNodeWithText("November").assertExists()
        waitFor("Set a budget")
    }

    @Test
    fun addEditAndDeleteAnExpense() {
        launch()
        waitFor("Nothing logged today.")

        addExpense("5.50", note = "Chicken rice")
        waitFor("Chicken rice", substring = true)
        waitFor("$5.50")

        compose.onNodeWithText("Lunch").performClick()
        waitFor("Edit expense")
        compose.onNodeWithText("5.50").assertExists()
        compose.onNodeWithText("Amount").performTextClearance()
        compose.onNodeWithText("Amount").performTextInput("6")
        compose.onNodeWithText("Save").performClick()
        waitFor("$6.00")

        compose.onNodeWithText("Lunch").performClick()
        waitFor("Edit expense")
        compose.onNodeWithContentDescription("Delete").performClick()
        waitFor("Delete this expense?")
        compose.onNodeWithText("Delete").performClick()
        waitFor("Nothing logged today.")
    }

    @Test
    fun newExpenseDefaultsToTheMealForTheTimeAndCanBeChanged() {
        launch()
        compose.onNodeWithContentDescription("Add expense").performClick()
        waitFor("Amount")
        compose.onNode(hasText("Lunch") and isSelected()).assertExists()

        compose.onNodeWithText("Transport").performClick()
        compose.onNodeWithText("Transport").assertIsSelected()
        compose.onNodeWithText("Amount").performTextInput("2")
        compose.onNodeWithText("Save").performClick()
        waitFor("This week")
        compose.onNodeWithText("Transport").assertExists()
    }

    @Test
    fun invalidAmountsAreRefusedAndNothingIsSaved() {
        launch()
        compose.onNodeWithContentDescription("Add expense").performClick()
        waitFor("Amount")

        compose.onNodeWithText("Save").performClick()
        waitFor("Enter an amount above zero, like 5.50")

        compose.onNodeWithText("Amount").performTextInput("abc")
        waitUntilGone("Enter an amount above zero, like 5.50")
        compose.onNodeWithText("Save").performClick()
        waitFor("Enter an amount above zero, like 5.50")

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Nothing logged today.")
    }

    @Test
    fun budgetsDriveRemainingAndSafeToSpend() {
        launch()
        waitFor("Nothing logged today.")
        addExpense("6")
        setBudgets(weekly = "100", monthly = "400")

        waitFor("$94.00 left")
        waitFor("$18.80/day safe")
        waitFor("$394.00 left")
        waitFor("$6.00 of $100.00")
        waitFor("$6.00 of $400.00")
    }

    @Test
    fun goingOverBudgetSaysSoAndDropsSafeToSpend() {
        launch()
        waitFor("Nothing logged today.")
        setBudgets(weekly = "100", monthly = "400")
        waitFor("/day safe", substring = true)

        addExpense("106")
        waitFor("$6.00 over budget")
        compose.onNodeWithText("/day safe", substring = true).assertDoesNotExist()
    }

    @Test
    fun weekStartSettingMovesTheWeek() {
        launch()
        waitFor("Mon 2 Nov – Sun 8 Nov")

        compose.onNodeWithContentDescription("Budgets and settings").performClick()
        waitFor("Week starts on")
        compose.onNodeWithText("Sunday").performClick()
        compose.onNodeWithText("Save").performClick()

        waitFor("Sun 1 Nov – Sat 7 Nov")
    }

    @Test
    fun darkThemeAppliesStraightAwayAndIsKeptWithoutSaving() = withPhoneDarkMode(dark = false) {
        launch()
        compose.onNodeWithContentDescription("Budgets and settings").performClick()
        waitFor("Theme")
        compose.onNodeWithText("System").assertIsSelected()
        assertFalse(isDrawnDark())

        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitUntil(5_000) { isDrawnDark() }
        assertEquals(ThemeMode.DARK, runBlocking { themeSettings.mode.first() })

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("This week")
        assertTrue(isDrawnDark())
    }

    @Test
    fun lightThemeStaysLightWhenThePhoneIsDark() = withPhoneDarkMode(dark = true) {
        runBlocking { themeSettings.setMode(ThemeMode.LIGHT) }
        launch()
        waitFor("This week")
        compose.waitUntil(5_000) { !isDrawnDark() }

        compose.onNodeWithContentDescription("Budgets and settings").performClick()
        waitFor("Theme")
        compose.onNodeWithText("Light").assertIsSelected()
        compose.onNodeWithText("System").performScrollTo().performClick()
        compose.waitUntil(5_000) { isDrawnDark() }
    }

    @Test
    fun allExpensesShowsOlderDaysAndOpensThemForEditing() {
        runBlocking {
            database.expenseDao().insert(
                Expense(
                    timestamp = now.minusDays(1).atZone(zone).toInstant(),
                    amountCents = 1_200,
                    category = Category.TRANSPORT,
                    note = "Bus",
                ),
            )
        }
        launch()
        waitFor("Nothing logged today.")

        compose.onNodeWithText("All expenses").performClick()
        waitFor("Yesterday")
        compose.onNodeWithText("Transport").performClick()
        waitFor("Edit expense")
        compose.onNodeWithText("Tue 3 Nov").assertExists()
        compose.onNodeWithText("Bus").assertExists()
    }
}
