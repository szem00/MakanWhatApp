package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MealInferenceTest {
    private fun assertMeal(expected: Category, hour: Int, minute: Int) {
        assertEquals(
            "at $hour:$minute",
            expected,
            inferMealCategory(LocalTime.of(hour, minute)),
        )
    }

    @Test
    fun followsThePlansMealWindows() {
        assertMeal(Category.SUPPER, 0, 0)
        assertMeal(Category.SUPPER, 2, 59)
        assertMeal(Category.BREAKFAST, 3, 0)
        assertMeal(Category.BREAKFAST, 10, 59)
        assertMeal(Category.LUNCH, 11, 0)
        assertMeal(Category.LUNCH, 14, 59)
        assertMeal(Category.DINNER, 17, 0)
        assertMeal(Category.DINNER, 20, 59)
        assertMeal(Category.SUPPER, 21, 0)
        assertMeal(Category.SUPPER, 23, 59)
    }

    @Test
    fun afternoonGapIsNotAMeal() {
        assertMeal(Category.OTHER, 15, 0)
        assertMeal(Category.OTHER, 16, 59)
    }
}
