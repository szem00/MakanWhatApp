package com.budgetapp.voiceexpense

import com.budgetapp.voiceexpense.domain.LatLng
import com.budgetapp.voiceexpense.domain.NearbyPlace
import com.budgetapp.voiceexpense.location.LocationSource
import com.budgetapp.voiceexpense.places.PlacesSource

/** Stands in for the phone's location, which tests set rather than wait for. */
class FakeLocationSource(var location: LatLng?) : LocationSource {
    override suspend fun current(): LatLng? = location
}

/** Stands in for Google, so tests never make a billed call. */
class FakePlacesSource(private val places: List<NearbyPlace>) : PlacesSource {
    var failure: Exception? = null

    @Volatile
    var calls = 0
        private set

    override suspend fun nearbyFood(center: LatLng, radiusMeters: Int): List<NearbyPlace> {
        calls++
        failure?.let { throw it }
        return places
    }
}
