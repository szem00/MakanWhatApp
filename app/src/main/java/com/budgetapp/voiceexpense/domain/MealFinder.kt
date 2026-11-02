package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val latitude: Double, val longitude: Double)

/** How far the meal finder looks, for Google's places and your own. */
const val MEAL_FINDER_RADIUS_METERS = 2_000

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Straight-line distance, which is close enough to walking distance for sorting a list. */
fun distanceMeters(a: LatLng, b: LatLng): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
}

/** The three meals counted, each with when its window ends (see inferMealCategory). */
private val MAIN_MEALS = listOf(
    Category.BREAKFAST to LocalTime.of(11, 0),
    Category.LUNCH to LocalTime.of(15, 0),
    Category.DINNER to LocalTime.of(21, 0),
)

/**
 * Breakfasts, lunches and dinners still to come this week: today's that haven't ended or been
 * logged, plus three for each day after today. Supper isn't counted, since most days have none.
 */
fun mealsLeftThisWeek(now: LocalDateTime, week: DateRange, loggedToday: Set<Category>): Int {
    val laterToday = MAIN_MEALS.count { (meal, ends) -> now.toLocalTime() < ends && meal !in loggedToday }
    val laterDays = (ChronoUnit.DAYS.between(now.toLocalDate(), week.endExclusive) - 1).coerceAtLeast(0)
    return laterToday + laterDays.toInt() * MAIN_MEALS.size
}

/**
 * What each meal left can cost, rounded down so eating at this price can't go over. Null without a
 * weekly budget. With no meals left this week, everything left could go on one.
 */
fun perMealBudgetCents(remainingCents: Long?, mealsLeft: Int): Long? {
    if (remainingCents == null) return null
    val left = remainingCents.coerceAtLeast(0)
    return if (mealsLeft <= 0) left else left / mealsLeft
}

enum class PriceLevel { FREE, INEXPENSIVE, MODERATE, EXPENSIVE, VERY_EXPENSIVE }

data class NearbyPlace(
    val id: String,
    val name: String,
    val address: String?,
    val location: LatLng,
    val priceLevel: PriceLevel?,
    /** Google's typical spend per person, in cents of [currency]. Either end can be missing. */
    val priceFromCents: Long?,
    val priceToCents: Long?,
    val currency: String?,
)

enum class PriceFit { FITS, OVER, UNKNOWN }

/** Google's price levels as rough Singapore dollars a head, at the cheap end of each. */
private val LEVEL_FLOOR_CENTS = mapOf(
    PriceLevel.FREE to 0L,
    PriceLevel.INEXPENSIVE to 0L,
    PriceLevel.MODERATE to 1_500L,
    PriceLevel.EXPENSIVE to 3_000L,
    PriceLevel.VERY_EXPENSIVE to 6_000L,
)

/**
 * Whether a meal there can fit [budgetCents]. A price range beats a price level, since it's in
 * dollars. Hawker stalls often have neither, which is UNKNOWN rather than a guess.
 */
fun priceFit(place: NearbyPlace, budgetCents: Long, currency: String = "SGD"): PriceFit {
    val from = place.priceFromCents
    if (from != null && place.currency == currency) {
        return if (from <= budgetCents) PriceFit.FITS else PriceFit.OVER
    }
    val floor = place.priceLevel?.let(LEVEL_FLOOR_CENTS::getValue) ?: return PriceFit.UNKNOWN
    return if (floor <= budgetCents) PriceFit.FITS else PriceFit.OVER
}

/** Somewhere you've eaten, from the meals you logged there. */
data class OwnPlace(
    /** The note you gave, which is usually the dish or the stall. Blank when you gave none. */
    val name: String,
    val category: Category,
    val location: LatLng,
    val lastCents: Long,
    val lastEaten: Instant,
    val visits: Int,
    val distanceMeters: Double,
)

private val MEALS = setOf(Category.BREAKFAST, Category.LUNCH, Category.DINNER, Category.SUPPER)

/** Two meals with the same note this close together count as the same place. */
private const val SAME_PLACE_METERS = 150.0

/**
 * Meals you've logged within [radiusMeters] of [here], nearest first. Meals with the same note
 * close together are one place, showing the latest price you paid there.
 */
fun ownPlaces(expenses: List<Expense>, here: LatLng, radiusMeters: Int): List<OwnPlace> {
    val places = mutableListOf<OwnPlace>()
    for (expense in expenses.sortedByDescending { it.timestamp }) {
        if (expense.category !in MEALS) continue
        val location = LatLng(expense.latitude ?: continue, expense.longitude ?: continue)
        val name = expense.note.trim()
        val same = places.indexOfFirst {
            it.name.equals(name, ignoreCase = true) &&
                (name.isNotEmpty() || it.category == expense.category) &&
                distanceMeters(it.location, location) <= SAME_PLACE_METERS
        }
        if (same >= 0) {
            places[same] = places[same].copy(visits = places[same].visits + 1)
        } else {
            places += OwnPlace(
                name = name,
                category = expense.category,
                location = location,
                lastCents = expense.amountCents,
                lastEaten = expense.timestamp,
                visits = 1,
                distanceMeters = distanceMeters(here, location),
            )
        }
    }
    return places.filter { it.distanceMeters <= radiusMeters }.sortedBy { it.distanceMeters }
}
