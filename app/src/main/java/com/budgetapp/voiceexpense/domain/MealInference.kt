package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import java.time.LocalTime

/** The plan's meal windows. They leave 15:00 to 17:00 without a meal, so that gap is [Category.OTHER]. */
fun inferMealCategory(time: LocalTime): Category = when (time.hour) {
    in 3..10 -> Category.BREAKFAST
    in 11..14 -> Category.LUNCH
    in 15..16 -> Category.OTHER
    in 17..20 -> Category.DINNER
    else -> Category.SUPPER
}
