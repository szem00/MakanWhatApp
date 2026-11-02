package com.budgetapp.voiceexpense

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.preferencesDataStore
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.ThemeSettings
import com.budgetapp.voiceexpense.location.AndroidLocationSource
import com.budgetapp.voiceexpense.location.ExpenseLocations
import com.budgetapp.voiceexpense.location.LocationSource
import com.budgetapp.voiceexpense.location.NoLocation
import com.budgetapp.voiceexpense.places.PlacesApiClient
import com.budgetapp.voiceexpense.places.PlacesSource
import com.budgetapp.voiceexpense.ui.theme.applyNightMode
import com.budgetapp.voiceexpense.voice.AndroidVoiceRecognizer
import com.budgetapp.voiceexpense.voice.VoiceRecognizer
import com.budgetapp.voiceexpense.widget.refreshBudgetWidgets
import java.time.Clock
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class AppContainer(
    val database: AppDatabase,
    val themeSettings: ThemeSettings,
    val clock: Clock = Clock.systemDefaultZone(),
    val createVoiceRecognizer: () -> VoiceRecognizer,
    /** Called after any change to what the widget shows. */
    val onDataChanged: () -> Unit = {},
    /** How long quick capture's "saved" card stays up before closing itself. */
    val savedCardMillis: Long = 3_000,
    /** Where the phone is, for the meal finder and for remembering where expenses were logged. */
    val locationSource: LocationSource = NoLocation,
    /** Google's nearby places. Null without an API key. */
    val placesSource: PlacesSource? = null,
) {
    val expenseLocations = ExpenseLocations(locationSource, database.expenseDao())
}

private val Context.preferences by preferencesDataStore(name = "preferences")

class VoiceExpenseApp : Application() {
    private val themeSettings by lazy { ThemeSettings(preferences) }

    private val defaultContainer by lazy {
        AppContainer(
            database = AppDatabase.create(this),
            themeSettings = themeSettings,
            createVoiceRecognizer = { AndroidVoiceRecognizer(this) },
            onDataChanged = { refreshBudgetWidgets(this) },
            locationSource = AndroidLocationSource(this),
            placesSource = PlacesApiClient.create(this, BuildConfig.PLACES_API_KEY),
        )
    }

    /** Lets tests use an in-memory database, so they never open the real one. */
    @VisibleForTesting
    var containerOverride: AppContainer? = null

    val container: AppContainer get() = containerOverride ?: defaultContainer

    override fun onCreate() {
        super.onCreate()
        // This watches the real setting even when tests swap the container, so tests never change it.
        // It also runs at launch, which keeps the system's copy of the choice in step with the saved one.
        MainScope().launch {
            themeSettings.mode.distinctUntilChanged().collect { mode ->
                applyNightMode(this@VoiceExpenseApp, mode)
                refreshBudgetWidgets(this@VoiceExpenseApp)
            }
        }
    }
}
