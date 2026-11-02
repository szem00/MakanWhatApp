package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The week runs Monday 2 to Sunday 8 Nov 2026, in Singapore. */
class MealFinderTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val week = DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9))
    private val wednesdayLunchtime = LocalDateTime.of(2026, 11, 4, 12, 30)
    private val here = LatLng(1.2840, 103.8514)

    @Test
    fun midweekLunchtimeCountsLunchAndDinnerThenThreeADay() {
        assertEquals(2 + 4 * 3, mealsLeftThisWeek(wednesdayLunchtime, week, loggedToday = emptySet()))
    }

    @Test
    fun aMealLoggedTodayIsNoLongerLeft() {
        assertEquals(1 + 4 * 3, mealsLeftThisWeek(wednesdayLunchtime, week, setOf(Category.LUNCH)))
    }

    @Test
    fun aMealIsLeftUntilItsWindowEnds() {
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals(3, mealsLeftThisWeek(sunday.atTime(10, 59), week, emptySet()))
        assertEquals(2, mealsLeftThisWeek(sunday.atTime(11, 0), week, emptySet()))
        assertEquals(0, mealsLeftThisWeek(sunday.atTime(21, 0), week, emptySet()))
    }

    @Test
    fun perMealBudgetRoundsDown() {
        assertEquals(928L, perMealBudgetCents(remainingCents = 13_000, mealsLeft = 14))
    }

    @Test
    fun perMealBudgetWithoutABudgetOrMoneyOrMeals() {
        assertNull(perMealBudgetCents(remainingCents = null, mealsLeft = 14))
        assertEquals(0L, perMealBudgetCents(remainingCents = -500, mealsLeft = 14))
        assertEquals(1_200L, perMealBudgetCents(remainingCents = 1_200, mealsLeft = 0))
    }

    @Test
    fun distanceOfAHundredthOfADegreeNorth() {
        assertEquals(1_112.0, distanceMeters(LatLng(1.0, 103.0), LatLng(1.01, 103.0)), 1.0)
        assertEquals(0.0, distanceMeters(here, here), 0.0)
    }

    private fun place(
        priceLevel: PriceLevel? = null,
        from: Long? = null,
        to: Long? = null,
        currency: String? = if (from != null || to != null) "SGD" else null,
    ) = NearbyPlace("id", "Somewhere", null, here, priceLevel, from, to, currency)

    @Test
    fun aPriceRangeFitsWhenItsCheapEndDoes() {
        assertEquals(PriceFit.FITS, priceFit(place(from = 500, to = 1_000), budgetCents = 928))
        assertEquals(PriceFit.FITS, priceFit(place(from = 928, to = 1_500), budgetCents = 928))
        assertEquals(PriceFit.OVER, priceFit(place(from = 2_000, to = 3_000), budgetCents = 928))
    }

    @Test
    fun aPriceRangeOutranksThePriceLevel() {
        val cheapRangeDearLevel = place(priceLevel = PriceLevel.EXPENSIVE, from = 500, to = 800)
        assertEquals(PriceFit.FITS, priceFit(cheapRangeDearLevel, budgetCents = 928))
    }

    @Test
    fun aRangeInAnotherCurrencyFallsBackToTheLevel() {
        val dollars = place(priceLevel = PriceLevel.MODERATE, from = 500, to = 800, currency = "USD")
        assertEquals(PriceFit.OVER, priceFit(dollars, budgetCents = 928))
        assertEquals(PriceFit.FITS, priceFit(dollars, budgetCents = 2_000))
    }

    @Test
    fun priceLevelsAreRoughFloors() {
        assertEquals(PriceFit.FITS, priceFit(place(priceLevel = PriceLevel.INEXPENSIVE), budgetCents = 300))
        assertEquals(PriceFit.OVER, priceFit(place(priceLevel = PriceLevel.MODERATE), budgetCents = 928))
        assertEquals(PriceFit.OVER, priceFit(place(priceLevel = PriceLevel.VERY_EXPENSIVE), budgetCents = 5_000))
    }

    @Test
    fun noPriceDataIsUnknownNotAGuess() {
        assertEquals(PriceFit.UNKNOWN, priceFit(place(), budgetCents = 928))
    }

    private fun meal(
        note: String,
        cents: Long,
        day: Int,
        latitude: Double?,
        category: Category = Category.LUNCH,
    ) = Expense(
        timestamp = LocalDateTime.of(2026, 10, day, 12, 0).atZone(zone).toInstant(),
        amountCents = cents,
        category = category,
        note = note,
        latitude = latitude,
        longitude = latitude?.let { 103.8514 },
    )

    @Test
    fun theSameNoteCloseTogetherIsOnePlaceWithItsLatestPrice() {
        val places = ownPlaces(
            listOf(
                meal("Fishball noodles", 600, day = 28, latitude = 1.2867),
                meal("fishball noodles", 800, day = 30, latitude = 1.2868),
            ),
            here,
            MEAL_FINDER_RADIUS_METERS,
        )
        val place = places.single()
        assertEquals("fishball noodles", place.name)
        assertEquals(800L, place.lastCents)
        assertEquals(2, place.visits)
        assertEquals(311.0, place.distanceMeters, 1.0)
    }

    @Test
    fun theSameNoteFarApartIsTwoPlaces() {
        val places = ownPlaces(
            listOf(
                meal("Chicken rice", 550, day = 20, latitude = 1.2860),
                meal("Chicken rice", 600, day = 21, latitude = 1.2900),
            ),
            here,
            MEAL_FINDER_RADIUS_METERS,
        )
        assertEquals(listOf(222.0, 667.0), places.map { Math.round(it.distanceMeters).toDouble() })
    }

    @Test
    fun farAwayUnlocatedAndNonMealExpensesAreLeftOut() {
        val places = ownPlaces(
            listOf(
                meal("Chicken rice", 550, day = 20, latitude = 1.3290),
                meal("Laksa", 700, day = 27, latitude = null),
                meal("Grab home", 1_200, day = 29, latitude = 1.2850, category = Category.TRANSPORT),
                meal("Kopi", 180, day = 29, latitude = 1.2841, category = Category.BREAKFAST),
            ),
            here,
            MEAL_FINDER_RADIUS_METERS,
        )
        assertEquals(listOf("Kopi"), places.map { it.name })
    }
}
