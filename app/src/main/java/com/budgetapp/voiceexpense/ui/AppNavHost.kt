package com.budgetapp.voiceexpense.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgetapp.voiceexpense.AppContainer
import com.budgetapp.voiceexpense.VoiceExpenseApp
import com.budgetapp.voiceexpense.ui.entry.EntryScreen
import com.budgetapp.voiceexpense.ui.entry.EntryViewModel
import com.budgetapp.voiceexpense.ui.entry.NEW_EXPENSE_ID
import com.budgetapp.voiceexpense.ui.history.HistoryScreen
import com.budgetapp.voiceexpense.ui.history.HistoryViewModel
import com.budgetapp.voiceexpense.ui.home.HomeScreen
import com.budgetapp.voiceexpense.location.hasLocationPermission
import com.budgetapp.voiceexpense.ui.home.HomeViewModel
import com.budgetapp.voiceexpense.ui.meals.MealFinderScreen
import com.budgetapp.voiceexpense.ui.meals.MealFinderViewModel
import com.budgetapp.voiceexpense.ui.quick.QuickCaptureViewModel
import com.budgetapp.voiceexpense.ui.settings.SettingsScreen
import com.budgetapp.voiceexpense.ui.settings.SettingsViewModel
import com.budgetapp.voiceexpense.ui.trends.TrendsScreen
import com.budgetapp.voiceexpense.ui.trends.TrendsViewModel
import com.budgetapp.voiceexpense.ui.voice.VoiceScreen
import com.budgetapp.voiceexpense.ui.voice.VoiceViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_ENTRY = "entry"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_VOICE = "voice"
private const val ROUTE_TRENDS = "trends"
private const val ROUTE_MEAL_FINDER = "meals"
private const val ARG_EXPENSE_ID = "expenseId"
private const val ARG_TRANSCRIPT = "transcript"

object AppViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val database = container().database
            HomeViewModel(
                database.expenseDao(),
                database.budgetDao(),
                database.settingsDao(),
                container().clock,
            )
        }
        initializer {
            val saved = createSavedStateHandle()
            EntryViewModel(
                container().database.expenseDao(),
                container().clock,
                saved.get<Long>(ARG_EXPENSE_ID) ?: NEW_EXPENSE_ID,
                saved.get<String>(ARG_TRANSCRIPT),
                container().onDataChanged,
                container().expenseLocations,
            )
        }
        initializer {
            VoiceViewModel(container().createVoiceRecognizer())
        }
        initializer {
            val database = container().database
            TrendsViewModel(
                database.expenseDao(),
                database.budgetDao(),
                database.settingsDao(),
                container().clock,
            )
        }
        initializer {
            QuickCaptureViewModel(
                container().database.expenseDao(),
                container().clock,
                container().onDataChanged,
                container().savedCardMillis,
                container().expenseLocations,
            )
        }
        initializer {
            HistoryViewModel(container().database.expenseDao(), container().clock)
        }
        initializer {
            val app = this[APPLICATION_KEY] as VoiceExpenseApp
            MealFinderViewModel(
                container().database,
                container().clock,
                container().locationSource,
                container().placesSource,
                hasPermission = { hasLocationPermission(app) },
            )
        }
        initializer {
            val database = container().database
            SettingsViewModel(
                database.budgetDao(),
                database.settingsDao(),
                container().themeSettings,
                container().clock,
                container().onDataChanged,
            )
        }
    }

    private fun CreationExtras.container(): AppContainer =
        (this[APPLICATION_KEY] as VoiceExpenseApp).container
}

/** Ignores a second tap made while a screen change is already under way. */
private val NavController.isSettled: Boolean
    get() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

private fun NavController.open(route: String) {
    if (isSettled) navigate(route)
}

private fun NavController.back() {
    if (isSettled) popBackStack()
}

/** Going back from the confirm card returns to Home, not to a finished listening screen. */
private fun NavController.replaceWith(route: String) {
    navigate(route) { popUpTo(ROUTE_HOME) }
}

internal fun entryRoute(expenseId: Long? = null, transcript: String? = null): String = when {
    expenseId != null -> "$ROUTE_ENTRY?$ARG_EXPENSE_ID=$expenseId"
    transcript != null -> "$ROUTE_ENTRY?$ARG_TRANSCRIPT=${Uri.encode(transcript)}"
    else -> ROUTE_ENTRY
}

/** [openRoute] is opened on top of Home, for coming in from a shortcut. */
@Composable
fun AppNavHost(openRoute: String? = null) {
    val nav = rememberNavController()
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (openRoute != null && !opened) {
            opened = true
            nav.navigate(openRoute)
        }
    }
    NavHost(navController = nav, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onSpeak = { nav.open(ROUTE_VOICE) },
                onAddExpense = { nav.open(entryRoute()) },
                onOpenExpense = { id -> nav.open(entryRoute(expenseId = id)) },
                onOpenHistory = { nav.open(ROUTE_HISTORY) },
                onOpenTrends = { nav.open(ROUTE_TRENDS) },
                onOpenSettings = { nav.open(ROUTE_SETTINGS) },
                onOpenMealFinder = { nav.open(ROUTE_MEAL_FINDER) },
            )
        }
        composable(ROUTE_MEAL_FINDER) {
            MealFinderScreen(onBack = { nav.back() })
        }
        composable(ROUTE_TRENDS) {
            TrendsScreen(onBack = { nav.back() })
        }
        composable(ROUTE_VOICE) {
            VoiceScreen(
                onHeard = { transcript -> nav.replaceWith(entryRoute(transcript = transcript)) },
                onBack = { nav.back() },
                onTypeInstead = { nav.replaceWith(entryRoute()) },
            )
        }
        composable(
            route = "$ROUTE_ENTRY?$ARG_EXPENSE_ID={$ARG_EXPENSE_ID}&$ARG_TRANSCRIPT={$ARG_TRANSCRIPT}",
            arguments = listOf(
                navArgument(ARG_EXPENSE_ID) {
                    type = NavType.LongType
                    defaultValue = NEW_EXPENSE_ID
                },
                navArgument(ARG_TRANSCRIPT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            EntryScreen(
                onBack = { nav.back() },
                onFinished = { nav.popBackStack() },
                onSayAgain = { nav.replaceWith(ROUTE_VOICE) },
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(
                onBack = { nav.back() },
                onOpenExpense = { id -> nav.open(entryRoute(expenseId = id)) },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { nav.back() }, onFinished = { nav.popBackStack() })
        }
    }
}
