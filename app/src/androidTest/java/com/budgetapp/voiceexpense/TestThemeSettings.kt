package com.budgetapp.voiceexpense

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.budgetapp.voiceexpense.data.ThemeSettings
import java.io.File
import java.util.UUID

/** A theme store in a file of its own, so tests never touch the app's real choice or each other's. */
fun testThemeSettings(context: Context): ThemeSettings = ThemeSettings(
    PreferenceDataStoreFactory.create(
        produceFile = { File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb") },
    ),
)
