package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private val lunchtime = LocalTime.of(12, 30)
    private val afternoon = LocalTime.of(15, 30)

    private fun parse(text: String, time: LocalTime = lunchtime) = parseVoiceExpense(text, time)

    private fun assertAmount(expectedCents: Long?, text: String) {
        assertEquals("amount of '$text'", expectedCents, parse(text).amountCents)
    }

    @Test
    fun readsThePlansExamplePhrases() {
        val simple = parse("lunch chicken rice 5.50")
        assertEquals(550L, simple.amountCents)
        assertEquals(Category.LUNCH, simple.category)
        assertTrue(simple.categorySaid)
        assertEquals("Chicken rice", simple.note)

        val messy = parse("had chicken rice and kopi for like six fifty")
        assertEquals(650L, messy.amountCents)
        assertEquals(Category.LUNCH, messy.category)
        assertFalse(messy.categorySaid)
        assertEquals("Chicken rice and kopi", messy.note)
    }

    @Test
    fun readsTypedAmounts() {
        assertAmount(550, "kopi 5.50")
        assertAmount(550, "kopi 5.5")
        assertAmount(50, "kopi .50")
        assertAmount(550, "kopi $5.50")
        assertAmount(500, "kopi $5")
        assertAmount(700, "taxi S$7")
        assertAmount(700, "taxi SGD 7")
        assertAmount(700, "taxi sgd7")
        assertAmount(120000, "rent $1,200")
        assertAmount(120050, "rent $1,200.50")
        assertAmount(550, "kopi 5,50")
        assertAmount(1200, "lunch 12")
    }

    @Test
    fun readsCurrencyWords() {
        assertAmount(500, "kopi 5 dollars")
        assertAmount(500, "kopi five dollars")
        assertAmount(500, "kopi 5 bucks")
        assertAmount(550, "kopi 5.50 dollars")
        assertAmount(550, "kopi 5 dollars 50")
        assertAmount(550, "kopi 5 dollars 50 cents")
        assertAmount(550, "kopi five dollars fifty")
        assertAmount(550, "kopi five dollars and fifty cents")
        assertAmount(505, "kopi five dollars five cents")
        assertAmount(50, "kopi fifty cents")
        assertAmount(150, "kopi 150 cents")
        assertAmount(100, "kopi a dollar")
        assertAmount(150, "kopi a dollar fifty")
        assertAmount(650, "kopi six fifty dollars")
    }

    @Test
    fun readsSpokenNumbers() {
        assertAmount(650, "lunch six fifty")
        assertAmount(150, "lunch one fifty")
        assertAmount(120, "lunch one twenty")
        assertAmount(125, "lunch one twenty five")
        assertAmount(2500, "lunch twenty five")
        assertAmount(2550, "lunch twenty five fifty")
        assertAmount(1230, "lunch twelve thirty")
        assertAmount(1200, "lunch twelve")
        assertAmount(505, "lunch five oh five")
        assertAmount(550, "lunch five point five")
        assertAmount(550, "lunch five point five zero")
        assertAmount(525, "lunch five point two five")
        assertAmount(550, "lunch five point fifty")
        assertAmount(550, "lunch 5 50")
        assertAmount(550, "lunch 5 fifty")
        assertAmount(550, "lunch five 50")
    }

    @Test
    fun readsLargeSpokenNumbers() {
        assertAmount(10000, "shopping a hundred")
        assertAmount(10000, "shopping one hundred")
        assertAmount(12000, "shopping one hundred and twenty")
        assertAmount(12000, "shopping one hundred twenty")
        assertAmount(55000, "lunch five hundred fifty")
        assertAmount(200000, "shopping two thousand")
        assertAmount(250000, "shopping two thousand five hundred")
    }

    @Test
    fun treatsRunTogetherDigitsAsDollarsAndCents() {
        assertAmount(550, "lunch 550")
        assertAmount(1250, "lunch 1250")
        assertAmount(10000, "lunch 100")
        assertAmount(120000, "lunch 1200")
    }

    @Test
    fun takesRunTogetherDigitsLiterallyWhenTheyMeanDollars() {
        assertAmount(55000, "lunch $550")
        assertAmount(55000, "lunch 550 dollars")
        assertAmount(12000, "shopping shoes 120")
        assertAmount(12000, "ntuc 120")
    }

    @Test
    fun prefersTheMostExplicitNumberThenTheLast() {
        val quantity = parse("2 kopi 3.60")
        assertEquals(360L, quantity.amountCents)
        assertEquals("2 kopi", quantity.note)

        val spoken = parse("two kopi three sixty")
        assertEquals(360L, spoken.amountCents)
        assertEquals("Two kopi", spoken.note)

        assertAmount(400, "2 kopi 4")
        assertAmount(550, "5.50 at 7 eleven")
        assertAmount(180, "kopi 7 eleven 1.80")
        assertAmount(700, "$7 for 3 kopi 2")
    }

    @Test
    fun keepsWordsThatOnlyLookLikeNumbers() {
        val hyphenated = parse("7-eleven snacks 4.20")
        assertEquals(420L, hyphenated.amountCents)
        assertEquals("7-eleven snacks", hyphenated.note)

        assertEquals("Coffee for you", parse("coffee for you 3").note)
    }

    @Test
    fun handlesPhrasesWithNoAmount() {
        val result = parse("lunch chicken rice")
        assertNull(result.amountCents)
        assertEquals(Category.LUNCH, result.category)
        assertEquals("Chicken rice", result.note)
    }

    @Test
    fun handlesEmptyAndAmountOnlyPhrases() {
        val empty = parse("   ")
        assertNull(empty.amountCents)
        assertEquals("", empty.note)
        assertFalse(empty.categorySaid)

        val onlyAmount = parse("5.50")
        assertEquals(550L, onlyAmount.amountCents)
        assertEquals("", onlyAmount.note)
    }

    @Test
    fun ignoresAmountsTooLargeOrZero() {
        assertNull(parse("lunch 0").amountCents)
        assertNull(parse("lunch $0.00").amountCents)
        assertNull(parse("lunch 99999999999999999999").amountCents)
        assertNull(parse("lunch 10000000 dollars").amountCents)
    }

    @Test
    fun saidMealWinsOverTime() {
        val result = parse("breakfast toast 3", lunchtime)
        assertEquals(Category.BREAKFAST, result.category)
        assertTrue(result.categorySaid)
    }

    @Test
    fun infersMealFromTimeWhenNoneIsSaid() {
        assertEquals(Category.BREAKFAST, parse("toast 3", LocalTime.of(8, 0)).category)
        assertEquals(Category.LUNCH, parse("toast 3", lunchtime).category)
        assertEquals(Category.DINNER, parse("toast 3", LocalTime.of(19, 0)).category)
        assertEquals(Category.SUPPER, parse("toast 3", LocalTime.of(23, 0)).category)

        val gap = parse("toast 3", afternoon)
        assertEquals(Category.OTHER, gap.category)
        assertFalse(gap.categorySaid)
    }

    @Test
    fun recognisesOtherCategories() {
        assertEquals(Category.TRANSPORT, parse("transport 5").category)
        assertEquals(Category.GROCERIES, parse("groceries 40").category)
        assertEquals(Category.SHOPPING, parse("shopping 40").category)
        assertEquals(Category.OTHER, parse("other haircut 20").category)
    }

    @Test
    fun hintsPickACategoryAndStayInTheNote() {
        val mrt = parse("MRT 2.10")
        assertEquals(Category.TRANSPORT, mrt.category)
        assertTrue(mrt.categorySaid)
        assertEquals("MRT", mrt.note)

        assertEquals(Category.TRANSPORT, parse("grab to work 12").category)
        assertEquals("Grab to work", parse("grab to work 12").note)
        assertEquals(Category.GROCERIES, parse("ntuc 40.50").category)
        assertEquals(Category.GROCERIES, parse("cold storage 40").category)
        assertEquals(Category.SHOPPING, parse("shopee 30").category)
    }

    @Test
    fun mealWordBeatsHints() {
        val result = parse("dinner at the mrt station 8")
        assertEquals(Category.DINNER, result.category)
        assertEquals("Mrt station", result.note)
    }

    @Test
    fun grabFoodIsNotTransport() {
        val result = parse("grab food 15")
        assertEquals(Category.LUNCH, result.category)
        assertFalse(result.categorySaid)
        assertEquals("Grab food", result.note)
    }

    @Test
    fun knowsWhenAPhraseHoldsAPrice() {
        listOf(
            "lunch chicken rice 5.50",
            "lunch 12",
            "lunch twelve",
            "six fifty",
            "lunch six fifty",
            "5.50 chicken rice",
            "six fifty chicken rice",
            "five dollars chicken rice",
            "$5 kopi",
            "fifty cents",
            "2 kopi 4",
        ).forEach { assertTrue("should hold a price: '$it'", mentionsPrice(it)) }
    }

    @Test
    fun knowsWhenAPhraseStillNeedsAPrice() {
        listOf(
            "",
            "lunch",
            "lunch chicken rice",
            "2 kopi",
            "two kopi",
            "one kopi and toast",
        ).forEach { assertFalse("should still need a price: '$it'", mentionsPrice(it)) }
    }

    @Test
    fun cleansUpTheNote() {
        assertEquals("Chicken rice", parse("Lunch, chicken rice, $5.50.").note)
        assertEquals("Chicken rice", parse("spent 5.50 on chicken rice").note)
        assertEquals("Chicken rice", parse("I paid 5.50 for chicken rice").note)
        assertEquals("Chicken rice at Maxwell", parse("had chicken rice for 5.50 at Maxwell").note)
        assertEquals("Maxwell", parse("lunch at Maxwell 5.50").note)
        assertEquals("", parse("lunch 5.50").note)
        assertEquals("Chicken rice", parse("lunch chicken rice for about five dollars").note)
    }
}
