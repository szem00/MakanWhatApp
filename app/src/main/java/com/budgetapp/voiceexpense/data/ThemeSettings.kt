package com.budgetapp.voiceexpense.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Stored by name, so renaming a value would quietly reset the choice to System.
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Whether to draw dark. Until the saved choice has loaded (null), the system's setting stands in. */
fun ThemeMode?.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM, null -> systemDark
}

/** The Light, Dark or System choice. It lives in DataStore rather than Room, so it needs no migration. */
class ThemeSettings(private val dataStore: DataStore<Preferences>) {
    val mode: Flow<ThemeMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            ThemeMode.entries.find { it.name == preferences[THEME_MODE] } ?: ThemeMode.SYSTEM
        }

    suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
