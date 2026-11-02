package com.budgetapp.voiceexpense

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Wednesday 4 Nov 2026 at 12:30, with a Monday-to-Sunday week. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TrendsFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val zone = ZoneId.of("Asia/Singapore")
    private val now = LocalDateTime.of(2026, 11, 4, 12, 30)
    private val app get() = ApplicationProvider.getApplicationContext<VoiceExpenseApp>()

    private lateinit var database: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        app.containerOverride = AppContainer(
            database = database,
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

    private fun seed() = runBlocking {
        suspend fun spend(cents: Long, on: LocalDateTime, category: Category) {
            database.expenseDao().insert(
                Expense(
                    timestamp = on.atZone(zone).toInstant(),
                    amountCents = cents,
                    category = category,
                ),
            )
        }
        spend(1_200, now, Category.LUNCH)
        spend(800, now.minusDays(2), Category.DINNER) // Monday 2 Nov.
        spend(2_000, now.minusDays(8), Category.LUNCH) // Tuesday 27 Oct, last week.
        spend(10_000, now.minusDays(34), Category.SHOPPING) // 1 Oct, an earlier month.
        database.budgetDao().setOverall(BudgetPeriod.WEEK, 15_000, LocalDate.of(2026, 10, 1))
    }

    private fun openTrends() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasText("This week"), 5_000)
        compose.onNodeWithContentDescription("Trends").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Last 12 weeks"), 5_000)
    }

    private fun waitFor(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), 5_000)
    }

    private fun tapPeriod(label: String) {
        compose.onAllNodesWithText(label)[compose.onAllNodesWithText(label).fetchSemanticsNodes().size - 1]
            .performScrollTo()
            .performClick()
    }

    @Test
    fun opensOnTodayAndThisWeek() {
        seed()
        openTrends()
        waitFor("Wed 4 Nov · $12.00")
        waitFor("Week of 2 Nov · $20.00")
    }

    @Test
    fun tappingAColumnShowsItsValue() {
        seed()
        openTrends()
        waitFor("Wed 4 Nov · $12.00")

        compose.onNodeWithTag("daily_chart").performTouchInput {
            // Just inside the plot, past the axis labels: Monday's column.
            click(Offset(50.dp.toPx(), center.y))
        }
        waitFor("Mon 2 Nov · $8.00")

        compose.onNodeWithTag("weekly_chart").performScrollTo().performTouchInput {
            // Second from the right: last week.
            val plotLeft = 44.dp.toPx()
            val slot = (width - plotLeft) / 12
            click(Offset(plotLeft + slot * 10.5f, center.y))
        }
        waitFor("Week of 26 Oct · $20.00")
    }

    @Test
    fun everyValueIsReachableAsNumbersWithoutTapping() {
        seed()
        openTrends()

        compose.onAllNodesWithText("Numbers")[0].performClick()
        waitFor("Mon 2 Nov")
        compose.onNodeWithText("Sun 8 Nov").assertExists()
        waitFor("$8.00")

        compose.onNodeWithText("Chart").performClick()
        waitFor("Wed 4 Nov · $12.00")
    }

    @Test
    fun weeklyChartDescribesItsBudgetAndWeeksForScreenReaders() {
        seed()
        openTrends()

        val description = compose.onNodeWithTag("weekly_chart")
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString() ?: ""
        assertTrue(description, description.contains("Budget $150.00"))
        assertTrue(description, description.contains("Week of 26 Oct $20.00"))
        assertTrue(description, description.contains("Week of 2 Nov $20.00"))
    }

    @Test
    fun periodSelectorScopesCategoriesAndMeals() {
        seed()
        openTrends()

        // This month is the default: only November's two expenses.
        waitFor("60%")
        waitFor("40%")
        compose.onNodeWithText("Shopping").assertDoesNotExist()

        tapPeriod("12 weeks")
        waitFor("Shopping")
        waitFor("$100.00")
        waitFor("2 logged") // Two lunches.
        waitFor("$16.00") // Their average: (12 + 20) / 2.

        tapPeriod("This week")
        compose.waitUntilDoesNotExist(hasText("Shopping"), 5_000)
    }

    @Test
    fun anEmptyPeriodSaysSoWithoutBreaking() {
        openTrends()
        waitFor("Nothing logged in this period.")
        compose.onNodeWithText("Breakfast").assertExists()
        waitFor("0 logged")
        waitFor("Wed 4 Nov · $0.00")
    }

    @Test
    fun backReturnsHome() {
        openTrends()
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Nothing logged today.")
    }
}
