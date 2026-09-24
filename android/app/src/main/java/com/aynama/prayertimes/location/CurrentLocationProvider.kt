package com.aynama.prayertimes.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Returns the device's current latitude/longitude, or null when location is unavailable
 * (permission not granted, no provider enabled, no fix). Intentionally uses the platform
 * [LocationManager] only — no Google Play Services — to keep the app F-Droid-compatible.
 * Coarse accuracy is sufficient: the Qibla bearing is effectively constant within a city.
 */
fun interface CurrentLocationProvider {
    suspend fun current(): Pair<Double, Double>?
}

// Before Android 12, GPS_PROVIDER needs FINE. From 12 on, COARSE (the user's "Approximate") may use
// it too and gets a blurred fix, which is the only fix there is when network location is turned off.
internal fun locationProviders(hasFine: Boolean, sdkInt: Int): List<String> =
    if (hasFine || sdkInt >= Build.VERSION_CODES.S) {
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    } else {
        listOf(LocationManager.NETWORK_PROVIDER)
    }

class AndroidCurrentLocationProvider(context: Context) : CurrentLocationProvider {

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun current(): Pair<Double, Double>? {
        if (!hasCoarsePermission()) return null
        val providers = locationProviders(hasFinePermission(), Build.VERSION.SDK_INT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in providers) {
                if (!isEnabled(provider)) continue
                fetchFresh(provider)?.let { return it.latitude to it.longitude }
            }
        }
        // Pre-30, or when a fresh fix isn't produced in time, fall back to last-known.
        for (provider in providers) {
            if (!isEnabled(provider)) continue
            lastKnown(provider)?.let { return it.latitude to it.longitude }
        }
        return null
    }

    private fun hasCoarsePermission(): Boolean {
        val granted = PackageManager.PERMISSION_GRANTED
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == granted ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == granted
    }

    private fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isEnabled(provider: String): Boolean =
        try { lm.isProviderEnabled(provider) } catch (_: Exception) { false }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun fetchFresh(provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        try {
            lm.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
                if (cont.isActive) cont.resume(location)
            }
        } catch (_: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun lastKnown(provider: String): Location? =
        try {
            @Suppress("MissingPermission")
            lm.getLastKnownLocation(provider)
        } catch (_: Exception) {
            null
        }
}
