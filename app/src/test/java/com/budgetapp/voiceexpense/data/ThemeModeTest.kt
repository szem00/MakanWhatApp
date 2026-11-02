package com.budgetapp.voiceexpense.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    private val notLoaded: ThemeMode? = null

    @Test
    fun systemFollowsThePhone() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun lightAndDarkIgnoreThePhone() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun thePhoneDecidesUntilTheChoiceHasLoaded() {
        assertTrue(notLoaded.isDark(systemDark = true))
        assertFalse(notLoaded.isDark(systemDark = false))
    }
}
