package com.budgetapp.voiceexpense.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.budgetapp.voiceexpense.data.ExpenseDao
import com.budgetapp.voiceexpense.domain.LatLng
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface LocationSource {
    /** Null without permission, with location off, or when nothing arrives in time. */
    suspend fun current(): LatLng?
}

object NoLocation : LocationSource {
    override suspend fun current(): LatLng? = null
}

fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** Uses Android's own location, so the app needs no Google Play services. */
class AndroidLocationSource(private val context: Context) : LocationSource {
    @SuppressLint("MissingPermission") // Checked first.
    override suspend fun current(): LatLng? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        recentFix(manager)?.let { return it.toLatLng() }
        val provider = providers().firstOrNull { manager.isProviderEnabled(it) } ?: return null
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location?.toLatLng())
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun recentFix(manager: LocationManager): Location? =
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { ageMillis(it) <= RECENT_MILLIS }
            .maxByOrNull { it.elapsedRealtimeNanos }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    /** The fused provider is quickest where it exists. GPS comes last, since it can be slow indoors. */
    private fun providers(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }

    private fun Location.toLatLng() = LatLng(latitude, longitude)

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val RECENT_MILLIS = 2 * 60_000L
    }
}

/**
 * Remembers where each new expense was logged, for the meal finder's list of your own places.
 * Without location permission it quietly does nothing.
 */
class ExpenseLocations(
    private val source: LocationSource,
    private val expenseDao: ExpenseDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var prepared: Deferred<LatLng?>? = null

    /**
     * Starts finding the phone while a logging screen is open. Android stops giving an app its
     * location soon after it leaves the screen, and quick capture's card closes within seconds.
     */
    fun prepare() {
        prepared = scope.async { source.current() }
    }

    /** Saves the prepared location against [expenseId], or finds one now if none was prepared. */
    fun tag(expenseId: Long) {
        val fix = prepared ?: scope.async { source.current() }
        prepared = null
        scope.launch {
            fix.await()?.let { expenseDao.setLocation(expenseId, it.latitude, it.longitude) }
        }
    }
}
