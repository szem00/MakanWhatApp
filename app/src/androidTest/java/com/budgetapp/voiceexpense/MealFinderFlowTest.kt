package com.budgetapp.voiceexpense

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.domain.LatLng
import com.budgetapp.voiceexpense.domain.NearbyPlace
import com.budgetapp.voiceexpense.domain.PriceLevel
import com.budgetapp.voiceexpense.places.PlacesException
import com.budgetapp.voiceexpense.places.PlacesSource
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on Wednesday 4 Nov 2026 at 12:30 at Raffles Place, with location and Google faked. With a
 * $150 weekly budget and $20 spent, $130 is left for 14 meals: $9.28 each.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MealFinderFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val location: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val zone = ZoneId.of("Asia/Singapore")
    private val now = LocalDateTime.of(2026, 11, 4, 12, 30)
    private val here = LatLng(1.2840, 103.8514)
    private val app get() = ApplicationProvider.getApplicationContext<VoiceExpenseApp>()

    private val places = FakePlacesSource(
        listOf(
            nearby("Maxwell Food Centre", latitude = 1.2803),
            nearby("Cheap Eats", latitude = 1.2860, from = 500, to = 1_000),
            nearby("Fancy Steak", latitude = 1.2850, level = PriceLevel.EXPENSIVE),
            nearby("Mid Bistro", latitude = 1.2870, from = 2_000, to = 3_000),
        ),
    )

    private lateinit var database: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        useGoogle(places)
    }

    @After
    fun tearDown() {
        scenario?.close()
        app.containerOverride = null
        database.close()
    }

    private fun useGoogle(source: PlacesSource?) {
        app.containerOverride = AppContainer(
            database = database,
            themeSettings = testThemeSettings(app),
            clock = Clock.fixed(now.atZone(zone).toInstant(), zone),
            createVoiceRecognizer = { FakeVoiceRecognizer() },
            locationSource = FakeLocationSource(here),
            placesSource = source,
        )
    }

    private fun nearby(
        name: String,
        latitude: Double,
        level: PriceLevel? = null,
        from: Long? = null,
        to: Long? = null,
    ) = NearbyPlace(
        id = name,
        name = name,
        address = null,
        location = LatLng(latitude, here.longitude),
        priceLevel = level,
        priceFromCents = from,
        priceToCents = to,
        currency = if (from != null) "SGD" else null,
    )

    private fun spent(cents: Long, at: LocalDateTime, category: Category, note: String, latitude: Double) =
        runBlocking {
            database.expenseDao().insert(
                Expense(
                    timestamp = at.atZone(zone).toInstant(),
                    amountCents = cents,
                    category = category,
                    note = note,
                    latitude = latitude,
                    longitude = here.longitude,
                ),
            )
        }

    private fun seedBudget() = runBlocking {
        database.budgetDao().setOverall(BudgetPeriod.WEEK, 15_000, LocalDate.of(2026, 10, 1))
    }

    private fun seedMeals() {
        spent(800, LocalDateTime.of(2026, 11, 2, 19, 0), Category.DINNER, "Fishball noodles", 1.2867)
        spent(1_200, LocalDateTime.of(2026, 11, 3, 12, 30), Category.LUNCH, "Poke bowl", 1.2885)
        spent(600, LocalDateTime.of(2026, 10, 28, 12, 0), Category.LUNCH, "Fishball noodles", 1.2868)
        spent(550, LocalDateTime.of(2026, 10, 20, 12, 0), Category.LUNCH, "Chicken rice", 1.3290)
    }

    private fun waitFor(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), 5_000)
    }

    private fun openMealFinder() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("This week")
        compose.onNodeWithText("Find a meal nearby").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Nearby on Google Maps"), 5_000)
    }

    @Test
    fun showsTheMealBudgetAndYourPlacesThatFitIt() {
        seedBudget()
        seedMeals()
        openMealFinder()

        waitFor("$9.28 a meal")
        waitFor("$130.00 left for 14 meals this week")
        waitFor("Fishball noodles")
        waitFor("$8.00 last time · 2 visits · 300 m")
        // Over $9.28, and 5 km away.
        compose.onNodeWithText("Poke bowl").assertDoesNotExist()
        compose.onNodeWithText("Chicken rice").assertDoesNotExist()
    }

    @Test
    fun googlePlacesOverTheMealBudgetAreLeftOut() {
        seedBudget()
        seedMeals()
        openMealFinder()

        waitFor("Cheap Eats")
        waitFor("$5–$10 · 220 m")
        waitFor("Maxwell Food Centre")
        waitFor("No price info · 410 m")
        compose.onNodeWithText("Fancy Steak").assertDoesNotExist()
        compose.onNodeWithText("Mid Bistro").assertDoesNotExist()
        waitFor("2 more places are over $9.28 a meal.")
        waitFor("Results from Google Maps")
        assertEquals(1, places.calls)
    }

    @Test
    fun withoutABudgetEveryPlaceShows() {
        openMealFinder()

        waitFor("Set a weekly budget to see what each meal can cost.")
        waitFor("Fancy Steak")
        waitFor("Expensive · 110 m")
        waitFor("Mid Bistro")
    }

    @Test
    fun withoutAnApiKeyItSaysHowToAddOne() {
        useGoogle(null)
        seedBudget()
        seedMeals()
        openMealFinder()

        waitFor("Add PLACES_API_KEY to local.properties to search Google Maps.")
        waitFor("Fishball noodles")
    }

    @Test
    fun aFailedSearchSaysWhyAndCanBeTriedAgain() {
        places.failure = PlacesException("API key not valid. Please pass a valid API key.")
        seedBudget()
        openMealFinder()

        waitFor("Couldn't search Google Maps. API key not valid. Please pass a valid API key.")
        places.failure = null
        compose.onNodeWithText("Search again").performClick()
        waitFor("Cheap Eats")
        assertEquals(2, places.calls)
    }

    @Test
    fun aMealLoggedHereRemembersWhereAndShowsUp() {
        seedBudget()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("Nothing logged today.")
        compose.onNodeWithContentDescription("Add expense").performClick()
        waitFor("Amount")
        compose.onNodeWithText("Amount").performTextInput("4.50")
        compose.onNodeWithText("Note (optional)").performTextInput("Kopi")
        compose.onNodeWithText("Save").performClick()
        waitFor("This week")
        compose.waitUntil(5_000) {
            runBlocking { database.expenseDao().withLocation() }.any { it.note == "Kopi" && it.latitude == here.latitude }
        }

        compose.onNodeWithText("Find a meal nearby").performClick()
        // $145.50 left; lunch is logged, so 13 meals remain.
        waitFor("$11.19 a meal")
        waitFor("Kopi")
        waitFor("$4.50 last time · 1 visit · 0 m")
    }
}
