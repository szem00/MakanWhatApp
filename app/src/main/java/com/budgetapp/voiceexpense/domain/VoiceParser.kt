package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import java.time.LocalTime

data class ParsedExpense(
    val amountCents: Long?,
    val category: Category,
    /** False when the category was only inferred from the time of day. */
    val categorySaid: Boolean,
    val note: String,
)

/** Rule-based reading of a phrase like "lunch chicken rice 5.50". The confirm card fixes any misses. */
fun parseVoiceExpense(transcript: String, time: LocalTime): ParsedExpense {
    val rawWords = spokenWords(transcript)
    val words = rawWords.map { it.lowercase() }

    val categoryIndex = words.indexOfFirst { it in CATEGORY_WORDS }
    val said = categoryIndex.takeIf { it >= 0 }?.let { CATEGORY_WORDS.getValue(words[it]) }
        ?: categoryFromHints(words)
    val category = said ?: inferMealCategory(time)

    val amount = readAmounts(words, typedNumbersMayBeCents = category !in LARGE_PURCHASE_CATEGORIES)
        .maxWithOrNull(MOST_LIKELY_PRICE)

    val before = rawWords.indices.filter { it < (amount?.start ?: rawWords.size) && it != categoryIndex }
    val after = rawWords.indices.filter { amount != null && it >= amount.end && it != categoryIndex }
    val noteWords = before.map { rawWords[it] }.dropLastWhile { it.lowercase() in NOTE_TRAILING_FILLERS } +
        after.map { rawWords[it] }.dropWhile { it.lowercase() in NOTE_RESUMING_FILLERS }
    val note = noteWords
        .dropWhile { it.lowercase() in NOTE_LEADING_FILLERS }
        .dropLastWhile { it.lowercase() in NOTE_TRAILING_FILLERS }
        .joinToString(" ")
        .replaceFirstChar { it.uppercase() }

    return ParsedExpense(amount?.cents, category, categorySaid = said != null, note = note)
}

/** Whether the phrase already holds a price. In "2 kopi" the 2 is probably a quantity, so it doesn't. */
fun mentionsPrice(transcript: String): Boolean {
    val words = spokenWords(transcript).map { it.lowercase() }
    val amount = readAmounts(words, typedNumbersMayBeCents = true).maxWithOrNull(MOST_LIKELY_PRICE)
        ?: return false
    return amount.rank >= RANK_PAIR || amount.end == words.size
}

private fun spokenWords(transcript: String): List<String> =
    transcript.trim().split(WHITESPACE)
        .map { it.trimStart(*LEADING_PUNCTUATION).trimEnd(*TRAILING_PUNCTUATION) }
        .filter { it.isNotEmpty() }

private val WHITESPACE = Regex("""\s+""")
private val LEADING_PUNCTUATION = charArrayOf('"', '\'', '(') // Not '.', which ".50" needs.
private val TRAILING_PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', '"', '\'', ')')

/** These name the category and nothing else, so they don't belong in the note. */
private val CATEGORY_WORDS = mapOf(
    "breakfast" to Category.BREAKFAST,
    "lunch" to Category.LUNCH,
    "dinner" to Category.DINNER,
    "supper" to Category.SUPPER,
    "transport" to Category.TRANSPORT,
    "groceries" to Category.GROCERIES,
    "grocery" to Category.GROCERIES,
    "shopping" to Category.SHOPPING,
    "other" to Category.OTHER,
)

/** These suggest a category but are also worth keeping in the note. */
private val TRANSPORT_HINTS =
    listOf("bus", "mrt", "train", "taxi", "cab", "grab", "gojek", "tada", "ezlink", "ez-link", "fare", "petrol", "parking", "erp")
private val GROCERY_HINTS = listOf("ntuc", "fairprice", "cold storage", "sheng siong", "giant", "supermarket")
private val SHOPPING_HINTS = listOf("shopee", "lazada", "amazon", "taobao", "uniqlo", "clothes", "shoes")
private val FOOD_DELIVERY = listOf("grab food", "grabfood")

private fun categoryFromHints(words: List<String>): Category? {
    val text = " ${words.joinToString(" ")} "
    fun mentions(phrase: String) = " $phrase " in text
    return when {
        FOOD_DELIVERY.any(::mentions) -> null
        TRANSPORT_HINTS.any(::mentions) -> Category.TRANSPORT
        GROCERY_HINTS.any(::mentions) -> Category.GROCERIES
        SHOPPING_HINTS.any(::mentions) -> Category.SHOPPING
        else -> null
    }
}

private val NOTE_LEADING_FILLERS = setOf(
    "i", "we", "had", "have", "having", "got", "get", "bought", "buy", "paid", "pay", "spent", "spend",
    "just", "for", "on", "at", "some", "a", "an", "the", "my", "was", "it",
)
private val NOTE_TRAILING_FILLERS = setOf(
    "for", "at", "on", "about", "around", "like", "roughly", "only", "just", "was", "is", "it", "cost",
    "costs", "total", "spent", "paid", "and", "of", "the", "a", "to", "that", "so", "um", "uh",
)
private val NOTE_RESUMING_FILLERS = setOf("for", "only", "just", "on", "and")

/** Shopping and groceries are the categories where a typed "120" really can mean $120. */
private val LARGE_PURCHASE_CATEGORIES = setOf(Category.GROCERIES, Category.SHOPPING)

private val ONES = mapOf(
    "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L,
    "five" to 5L, "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L,
)
private val TEENS = mapOf(
    "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L,
    "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L,
)
private val TENS = mapOf(
    "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L,
    "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L,
)
private val SCALES = setOf("hundred", "thousand")
private val DOLLAR_WORDS = setOf("dollar", "dollars", "buck", "bucks", "sgd")
private val CENT_WORDS = setOf("cent", "cents")
private val CURRENCY_PREFIXES = setOf("$", "s$", "sgd")
private val NUMERIC_TOKEN = Regex("""(s?\$|sgd)?(\d{1,3}(?:,\d{3})+|\d+)?(?:[.,](\d{1,2}))?""")
private val SMALL_DIGITS = Regex("""\d{1,2}""")

// When a phrase holds several numbers, the most explicit one is the price, then the last said.
private const val RANK_PLAIN = 0 // "12", "twelve"
private const val RANK_PAIR = 1 // "six fifty", "5 50"
private const val RANK_DECIMAL = 2 // "5.50", "five point five"
private const val RANK_CURRENCY = 3 // "$5", "five dollars", "fifty cents"

private class Amount(val cents: Long, val start: Int, val end: Int, val rank: Int)

private val MOST_LIKELY_PRICE = compareBy<Amount> { it.rank }.thenBy { it.start }

/** [whole] is set when the number has no cents part of its own. */
private class NumberRead(
    val whole: Long?,
    val cents: Long,
    val rank: Int,
    val end: Int,
    val hasCurrencySymbol: Boolean = false,
    val typedDigits: Boolean = false,
)

private fun readAmounts(words: List<String>, typedNumbersMayBeCents: Boolean): List<Amount> = buildList {
    var index = 0
    while (index < words.size) {
        val amount = readAmountAt(words, index, typedNumbersMayBeCents)
        if (amount == null) {
            index++
        } else {
            if (amount.cents in 1..MAX_CENTS) add(amount)
            index = amount.end
        }
    }
}

private fun readAmountAt(words: List<String>, start: Int, typedNumbersMayBeCents: Boolean): Amount? {
    val prefixed = words[start] in CURRENCY_PREFIXES
    val number = readNumber(words, if (prefixed) start + 1 else start) ?: return null
    var cents = number.cents
    var end = number.end
    var marked = prefixed || number.hasCurrencySymbol

    when (words.getOrNull(end)) {
        in CENT_WORDS -> {
            cents = number.whole ?: number.cents
            end++
            marked = true
        }
        in DOLLAR_WORDS -> {
            end++
            marked = true
            val extraStart = if (words.getOrNull(end) == "and") end + 1 else end
            val extra = readNumber(words, extraStart)?.takeIf { it.whole != null && it.whole in 0..99 }
            if (extra != null) {
                val saysCents = words.getOrNull(extra.end) in CENT_WORDS
                // A small number after "dollars" with no "cents" is more likely a quantity: "5 dollars 2 kopi".
                if (saysCents || extra.whole!! >= 10) {
                    cents += extra.whole!!
                    end = if (saysCents) extra.end + 1 else extra.end
                }
            }
        }
    }

    // Speech recognition writes "five fifty" as 550 and "twelve fifty" as 1250.
    val whole = number.whole
    if (!marked && number.typedDigits && typedNumbersMayBeCents && whole != null &&
        whole in 100..9999 && whole % 100 != 0L
    ) {
        cents = whole
    }
    return Amount(cents, start, end, if (marked) RANK_CURRENCY else number.rank)
}

private fun readNumber(words: List<String>, from: Int): NumberRead? {
    val word = words.getOrNull(from) ?: return null
    if (word == "a" && words.getOrNull(from + 1) in DOLLAR_WORDS) {
        return NumberRead(whole = 1, cents = 100, rank = RANK_PLAIN, end = from + 1)
    }
    val token = NUMERIC_TOKEN.matchEntire(word)
    if (token != null) {
        val (symbol, digits, fraction) = token.destructured
        if (digits.isNotEmpty() || fraction.isNotEmpty()) {
            // One or two plain digits can pair up with the next word, so they're read as speech.
            if (symbol.isNotEmpty() || fraction.isNotEmpty() || digits.length > 2) {
                return readNumericToken(symbol, digits, fraction, from)
            }
        }
    }
    return readSpokenNumber(words, from)
}

private fun readNumericToken(symbol: String, digits: String, fraction: String, at: Int): NumberRead? {
    val dollars = if (digits.isEmpty()) 0L else digits.replace(",", "").toLongOrNull() ?: return null
    if (dollars > MAX_CENTS) return null
    val rank = when {
        symbol.isNotEmpty() -> RANK_CURRENCY
        fraction.isNotEmpty() -> RANK_DECIMAL
        else -> RANK_PLAIN
    }
    return NumberRead(
        whole = dollars.takeIf { fraction.isEmpty() },
        cents = dollars * 100 + fraction.padEnd(2, '0').ifEmpty { "0" }.toLong(),
        rank = rank,
        end = at + 1,
        hasCurrencySymbol = symbol.isNotEmpty(),
        typedDigits = symbol.isEmpty() && fraction.isEmpty(),
    )
}

private fun isNumberWord(word: String?): Boolean =
    word != null && (word in ONES || word in TEENS || word in TENS || SMALL_DIGITS.matches(word))

private fun isSpokenPart(words: List<String>, index: Int, from: Int): Boolean {
    val word = words[index]
    val next = words.getOrNull(index + 1)
    val hasPrevious = index > from
    return when (word) {
        "hundred", "thousand" -> true
        "point" -> hasPrevious && isNumberWord(next)
        "oh", "o" -> hasPrevious && (next in ONES || (next != null && SMALL_DIGITS.matches(next)))
        "a" -> next in SCALES
        "and" -> hasPrevious && words[index - 1] in SCALES && isNumberWord(next)
        else -> isNumberWord(word)
    }
}

private fun readSpokenNumber(words: List<String>, from: Int): NumberRead? {
    var end = from
    while (end < words.size && isSpokenPart(words, end, from)) end++
    if (end == from) return null
    val parts = words.subList(from, end)

    val point = parts.indexOf("point")
    if (point > 0) {
        val dollars = wholeNumber(parts.take(point)) ?: return null
        val centDigits = parts.drop(point + 1).joinToString("") { spokenDigits(it) }.take(2).padEnd(2, '0')
        return NumberRead(whole = null, cents = dollars * 100 + centDigits.toLong(), rank = RANK_DECIMAL, end = end)
    }
    if (parts.any { it in SCALES }) {
        val whole = wholeNumber(parts) ?: return null
        return NumberRead(whole = whole, cents = whole * 100, rank = RANK_PLAIN, end = end)
    }

    val chunks = spokenChunks(parts)
    val last = chunks.lastOrNull() ?: return null
    val prior = chunks.getOrNull(chunks.size - 2)
    return if (prior != null && last.canBeCents) {
        NumberRead(whole = null, cents = prior.value * 100 + last.value, rank = RANK_PAIR, end = end)
    } else {
        NumberRead(whole = last.value, cents = last.value * 100, rank = RANK_PLAIN, end = end)
    }
}

private fun spokenDigits(word: String): String =
    (ONES[word] ?: TEENS[word] ?: TENS[word])?.toString() ?: word

private fun wholeNumber(parts: List<String>): Long? {
    var total = 0L
    var current = 0L
    for (part in parts) {
        when {
            part == "and" -> Unit
            part == "a" -> current = 1
            part == "hundred" -> current = current.coerceAtLeast(1) * 100
            part == "thousand" -> {
                total += current.coerceAtLeast(1) * 1000
                current = 0
            }
            else -> current += ONES[part] ?: TEENS[part] ?: TENS[part] ?: part.toLongOrNull() ?: return null
        }
    }
    return total + current
}

private class Chunk(val value: Long, val canBeCents: Boolean)

/** Splits "six fifty" into 6 and 50, while "twenty five" stays as 25. */
private fun spokenChunks(parts: List<String>): List<Chunk> = buildList {
    var index = 0
    while (index < parts.size) {
        val part = parts[index]
        val tens = TENS[part]
        val teen = TEENS[part]
        val one = ONES[part]
        when {
            tens != null -> {
                val unit = ONES[parts.getOrNull(index + 1)]?.takeIf { it > 0 }
                add(Chunk(tens + (unit ?: 0), canBeCents = true))
                index += if (unit != null) 2 else 1
            }
            teen != null -> {
                add(Chunk(teen, canBeCents = true))
                index++
            }
            one != null -> {
                add(Chunk(one, canBeCents = false))
                index++
            }
            part == "oh" || part == "o" -> {
                val next = parts.getOrNull(index + 1)
                val value = ONES[next] ?: next?.toLongOrNull() ?: 0
                add(Chunk(value, canBeCents = true))
                index += 2
            }
            else -> {
                add(Chunk(part.toLong(), canBeCents = part.length == 2))
                index++
            }
        }
    }
}
