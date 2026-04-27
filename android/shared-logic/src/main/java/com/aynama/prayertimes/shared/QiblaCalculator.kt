package com.aynama.prayertimes.shared

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val KAABA_LAT = 21.4225
private const val KAABA_LNG = 39.8262
private const val EARTH_RADIUS_KM = 6371.0

object QiblaCalculator {

    fun bearingTo(userLat: Double, userLng: Double): Double {
        val lat1 = Math.toRadians(userLat)
        val lat2 = Math.toRadians(KAABA_LAT)
        val dLng = Math.toRadians(KAABA_LNG - userLng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun distanceKm(userLat: Double, userLng: Double): Double {
        val lat1 = Math.toRadians(userLat)
        val lat2 = Math.toRadians(KAABA_LAT)
        val dLat = Math.toRadians(KAABA_LAT - userLat)
        val dLng = Math.toRadians(KAABA_LNG - userLng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}
