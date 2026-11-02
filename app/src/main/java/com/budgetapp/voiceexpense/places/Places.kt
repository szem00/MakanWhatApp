package com.budgetapp.voiceexpense.places

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.budgetapp.voiceexpense.domain.LatLng
import com.budgetapp.voiceexpense.domain.NearbyPlace
import com.budgetapp.voiceexpense.domain.PriceLevel
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface PlacesSource {
    /** Restaurants and food courts within [radiusMeters] of [center], in no particular order. */
    suspend fun nearbyFood(center: LatLng, radiusMeters: Int): List<NearbyPlace>
}

class PlacesException(message: String) : Exception(message)

/**
 * Nearby Search from the Places API (New). Asking for price levels and ranges makes each call an
 * Enterprise one, which a solo user keeps within the free monthly allowance. Each search of the
 * meal finder makes two calls (see [SEARCHES]).
 */
class PlacesApiClient(
    private val apiKey: String,
    private val packageName: String,
    private val certSha1: String?,
) : PlacesSource {
    override suspend fun nearbyFood(center: LatLng, radiusMeters: Int): List<NearbyPlace> = coroutineScope {
        SEARCHES.map { types -> async { search(center, radiusMeters, types) } }.awaitAll().let(::mergeNearby)
    }

    private suspend fun search(center: LatLng, radiusMeters: Int, types: List<String>): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            val connection = (URL(NEARBY_SEARCH_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Goog-Api-Key", apiKey)
                setRequestProperty("X-Goog-FieldMask", FIELD_MASK)
                // A key restricted to this app only accepts calls that say which app is asking.
                setRequestProperty("X-Android-Package", packageName)
                certSha1?.let { setRequestProperty("X-Android-Cert", it) }
            }
            try {
                connection.outputStream.use {
                    it.write(nearbyRequestBody(center, radiusMeters, types).toByteArray(Charsets.UTF_8))
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw PlacesException(errorMessage(body) ?: "HTTP $code")
                parseNearbyResponse(body)
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private const val NEARBY_SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby"
        private const val FIELD_MASK = "places.id,places.displayName,places.formattedAddress," +
            "places.location,places.priceLevel,places.priceRange"

        /** Null when there's no key, since the meal finder then only shows your own places. */
        fun create(context: Context, apiKey: String): PlacesApiClient? =
            apiKey.takeIf { it.isNotBlank() }?.let {
                PlacesApiClient(it, context.packageName, signingCertSha1(context))
            }
    }
}

/**
 * Each search returns at most 20 places, nearest first. In a city centre the first search's 20 are
 * all within a block, so the second looks for food courts alone, which Google also uses for hawker
 * stalls. That reaches the hawker centres a few minutes' walk away, where the cheap meals are.
 */
internal val SEARCHES = listOf(
    listOf("restaurant", "food_court"),
    listOf("food_court"),
)

/** One list from both searches, without the places they both found. */
internal fun mergeNearby(searches: List<List<NearbyPlace>>): List<NearbyPlace> =
    searches.flatten().distinctBy { it.id.ifEmpty { "${it.name} ${it.location}" } }

internal fun nearbyRequestBody(center: LatLng, radiusMeters: Int, types: List<String>): String = JSONObject()
    .put("includedTypes", JSONArray(types))
    .put("maxResultCount", 20)
    .put("rankPreference", "DISTANCE")
    .put(
        "locationRestriction",
        JSONObject().put(
            "circle",
            JSONObject()
                .put("center", JSONObject().put("latitude", center.latitude).put("longitude", center.longitude))
                .put("radius", radiusMeters.toDouble()),
        ),
    )
    .toString()

private val PRICE_LEVELS = mapOf(
    "PRICE_LEVEL_FREE" to PriceLevel.FREE,
    "PRICE_LEVEL_INEXPENSIVE" to PriceLevel.INEXPENSIVE,
    "PRICE_LEVEL_MODERATE" to PriceLevel.MODERATE,
    "PRICE_LEVEL_EXPENSIVE" to PriceLevel.EXPENSIVE,
    "PRICE_LEVEL_VERY_EXPENSIVE" to PriceLevel.VERY_EXPENSIVE,
)

/** Skips any place without a name or a location, which the list can't show. */
internal fun parseNearbyResponse(json: String): List<NearbyPlace> {
    val places = JSONObject(json).optJSONArray("places") ?: return emptyList()
    return (0 until places.length()).mapNotNull { index ->
        val place = places.getJSONObject(index)
        val name = place.optJSONObject("displayName")?.optString("text").orEmpty()
        val location = place.optJSONObject("location")
        if (name.isBlank() || location == null) return@mapNotNull null
        val range = place.optJSONObject("priceRange")
        val start = range?.optJSONObject("startPrice")
        val end = range?.optJSONObject("endPrice")
        NearbyPlace(
            id = place.optString("id"),
            name = name,
            address = place.optString("formattedAddress").ifBlank { null },
            location = LatLng(location.getDouble("latitude"), location.getDouble("longitude")),
            priceLevel = PRICE_LEVELS[place.optString("priceLevel")],
            priceFromCents = start?.let(::moneyToCents),
            priceToCents = end?.let(::moneyToCents),
            currency = (start ?: end)?.optString("currencyCode")?.ifBlank { null },
        )
    }
}

/** Google's Money: whole units as a string, plus billionths. */
private fun moneyToCents(money: JSONObject): Long {
    val units = money.optString("units").toLongOrNull() ?: 0L
    return units * 100 + money.optInt("nanos") / 10_000_000
}

private fun errorMessage(body: String): String? = runCatching {
    JSONObject(body).getJSONObject("error").getString("message")
}.getOrNull()

/** The SHA-1 the API key is restricted to, as Google expects it: upper-case hex, no colons. */
@Suppress("DEPRECATION")
private fun signingCertSha1(context: Context): String? = try {
    val manager = context.packageManager
    val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners?.firstOrNull()
    } else {
        manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
    }
    signature?.let { sig ->
        MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
    }
} catch (e: PackageManager.NameNotFoundException) {
    null
}
