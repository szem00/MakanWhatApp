package com.budgetapp.voiceexpense.ui.meals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.domain.LatLng
import com.budgetapp.voiceexpense.domain.MEAL_FINDER_RADIUS_METERS
import com.budgetapp.voiceexpense.domain.NearbyPlace
import com.budgetapp.voiceexpense.domain.OwnPlace
import com.budgetapp.voiceexpense.domain.PriceFit
import com.budgetapp.voiceexpense.domain.dayOf
import com.budgetapp.voiceexpense.domain.distanceMeters
import com.budgetapp.voiceexpense.domain.mealsLeftThisWeek
import com.budgetapp.voiceexpense.domain.ownPlaces
import com.budgetapp.voiceexpense.domain.perMealBudgetCents
import com.budgetapp.voiceexpense.domain.priceFit
import com.budgetapp.voiceexpense.domain.weekOf
import com.budgetapp.voiceexpense.location.LocationSource
import com.budgetapp.voiceexpense.places.PlacesSource
import com.budgetapp.voiceexpense.widget.loadWeekStatus
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MealBudget(
    /** Null without a weekly budget. */
    val perMealCents: Long?,
    val remainingCents: Long?,
    val mealsLeft: Int,
)

data class NearbyItem(val place: NearbyPlace, val distanceMeters: Double)

sealed interface NearbyResult {
    data object NoKey : NearbyResult

    data object Searching : NearbyResult

    data class Failed(val reason: String?) : NearbyResult

    /** [hiddenOver] places were left out for costing more than a meal's budget. */
    data class Found(val places: List<NearbyItem>, val hiddenOver: Int) : NearbyResult
}

data class MealFinderState(
    val budget: MealBudget? = null,
    val needsPermission: Boolean = false,
    val locating: Boolean = true,
    val noLocation: Boolean = false,
    val ownPlaces: List<OwnPlace> = emptyList(),
    val nearby: NearbyResult = NearbyResult.Searching,
)

class MealFinderViewModel(
    private val database: AppDatabase,
    private val clock: Clock,
    private val locationSource: LocationSource,
    private val placesSource: PlacesSource?,
    private val hasPermission: () -> Boolean,
) : ViewModel() {
    var state by mutableStateOf(MealFinderState())
        private set

    private var here: LatLng? = null
    private var loading: Job? = null

    init {
        refresh()
    }

    /** Google is asked on opening and on Search again, not on every return to the screen, since each call is billed. */
    fun refresh() {
        loading?.cancel()
        loading = viewModelScope.launch {
            val budget = loadBudget()
            if (!hasPermission()) {
                state = MealFinderState(budget = budget, needsPermission = true, locating = false)
                return@launch
            }
            state = MealFinderState(budget = budget)
            val location = locationSource.current()
            here = location
            if (location == null) {
                state = state.copy(locating = false, noLocation = true)
                return@launch
            }
            val perMeal = budget.perMealCents
            val own = ownPlaces(database.expenseDao().withLocation(), location, MEAL_FINDER_RADIUS_METERS)
                .filter { perMeal == null || it.lastCents <= perMeal }
            state = state.copy(
                locating = false,
                ownPlaces = own,
                nearby = if (placesSource == null) NearbyResult.NoKey else NearbyResult.Searching,
            )
            if (placesSource != null) state = state.copy(nearby = search(placesSource, location, perMeal))
        }
    }

    fun searchAgain() {
        val location = here
        if (location == null || placesSource == null) return refresh()
        loading?.cancel()
        loading = viewModelScope.launch {
            state = state.copy(nearby = NearbyResult.Searching)
            state = state.copy(nearby = search(placesSource, location, state.budget?.perMealCents))
        }
    }

    private suspend fun search(source: PlacesSource, location: LatLng, perMealCents: Long?): NearbyResult = try {
        val places = source.nearbyFood(location, MEAL_FINDER_RADIUS_METERS)
        // Places with no price data stay in, since hawker stalls rarely have any.
        val shown = places.filter { perMealCents == null || priceFit(it, perMealCents) != PriceFit.OVER }
        NearbyResult.Found(
            places = shown.map { NearbyItem(it, distanceMeters(location, it.location)) }.sortedBy { it.distanceMeters },
            hiddenOver = places.size - shown.size,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NearbyResult.Failed(e.message)
    }

    private suspend fun loadBudget(): MealBudget {
        val now = LocalDateTime.now(clock)
        val remaining = loadWeekStatus(database, clock).summary.remainingCents
        val weekStart = database.settingsDao().observe().first()?.weekStartDay ?: DayOfWeek.MONDAY
        val today = dayOf(now.toLocalDate())
        val loggedToday = database.expenseDao()
            .observeBetween(today.startInstant(clock.zone), today.endInstant(clock.zone))
            .first()
            .map { it.category }
            .toSet()
        val mealsLeft = mealsLeftThisWeek(now, weekOf(now.toLocalDate(), weekStart), loggedToday)
        return MealBudget(perMealBudgetCents(remaining, mealsLeft), remaining, mealsLeft)
    }
}
