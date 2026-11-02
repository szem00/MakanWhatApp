package com.budgetapp.voiceexpense.places

import com.budgetapp.voiceexpense.domain.LatLng
import com.budgetapp.voiceexpense.domain.PriceLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesResponseTest {
    @Test
    fun requestAsksForFoodPlacesWithinTheRadiusNearestFirst() {
        val body = JSONObject(nearbyRequestBody(LatLng(1.284, 103.8514), radiusMeters = 2_000))
        assertEquals(listOf("restaurant", "food_court"), body.getJSONArray("includedTypes").toList())
        assertEquals("DISTANCE", body.getString("rankPreference"))
        assertEquals(20, body.getInt("maxResultCount"))
        val circle = body.getJSONObject("locationRestriction").getJSONObject("circle")
        assertEquals(2_000.0, circle.getDouble("radius"), 0.0)
        assertEquals(1.284, circle.getJSONObject("center").getDouble("latitude"), 0.0)
        assertEquals(103.8514, circle.getJSONObject("center").getDouble("longitude"), 0.0)
    }

    @Test
    fun readsNamesLocationsAndPrices() {
        val places = parseNearbyResponse(
            """
            {"places": [
              {"id": "a1", "displayName": {"text": "Maxwell Food Centre", "languageCode": "en"},
               "formattedAddress": "1 Kadayanallur St, Singapore 069184",
               "location": {"latitude": 1.2803, "longitude": 103.8448}},
              {"id": "b2", "displayName": {"text": "Cheap Eats"},
               "location": {"latitude": 1.286, "longitude": 103.8514},
               "priceLevel": "PRICE_LEVEL_INEXPENSIVE",
               "priceRange": {"startPrice": {"currencyCode": "SGD", "units": "5"},
                              "endPrice": {"currencyCode": "SGD", "units": "10", "nanos": 500000000}}}
            ]}
            """,
        )
        assertEquals(2, places.size)
        val hawker = places[0]
        assertEquals("Maxwell Food Centre", hawker.name)
        assertEquals("1 Kadayanallur St, Singapore 069184", hawker.address)
        assertEquals(LatLng(1.2803, 103.8448), hawker.location)
        assertNull(hawker.priceLevel)
        assertNull(hawker.priceFromCents)
        assertNull(hawker.currency)

        val cheap = places[1]
        assertNull(cheap.address)
        assertEquals(PriceLevel.INEXPENSIVE, cheap.priceLevel)
        assertEquals(500L, cheap.priceFromCents)
        assertEquals(1_050L, cheap.priceToCents)
        assertEquals("SGD", cheap.currency)
    }

    @Test
    fun aRangeWithOnlyAnEndStillReadsItsCurrency() {
        val place = parseNearbyResponse(
            """{"places": [{"id": "c", "displayName": {"text": "Bistro"},
                "location": {"latitude": 1.0, "longitude": 103.0},
                "priceRange": {"endPrice": {"currencyCode": "SGD", "units": "30"}}}]}""",
        ).single()
        assertNull(place.priceFromCents)
        assertEquals(3_000L, place.priceToCents)
        assertEquals("SGD", place.currency)
    }

    @Test
    fun anEmptyResultIsAnEmptyList() {
        assertTrue(parseNearbyResponse("{}").isEmpty())
    }

    @Test
    fun placesWithoutANameOrLocationAreSkipped() {
        val places = parseNearbyResponse(
            """{"places": [
                {"id": "x", "location": {"latitude": 1.0, "longitude": 103.0}},
                {"id": "y", "displayName": {"text": "No location"}},
                {"id": "z", "displayName": {"text": "Kept"}, "location": {"latitude": 1.0, "longitude": 103.0}}
            ]}""",
        )
        assertEquals(listOf("Kept"), places.map { it.name })
    }

    private fun org.json.JSONArray.toList(): List<Any> = (0 until length()).map { get(it) }
}
