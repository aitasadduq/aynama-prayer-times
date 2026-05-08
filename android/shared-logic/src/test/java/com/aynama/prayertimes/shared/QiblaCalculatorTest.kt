package com.aynama.prayertimes.shared

import org.junit.Test
import kotlin.math.abs

class QiblaCalculatorTest {

    private fun assertBearing(userLat: Double, userLng: Double, expectedDeg: Double) {
        val bearing = QiblaCalculator.bearingTo(userLat, userLng)
        val diff = abs(bearing - expectedDeg).let { if (it > 180) 360 - it else it }
        assert(diff <= 1.0) {
            "Bearing from ($userLat, $userLng): expected ~${expectedDeg}° but got ${bearing}° (diff ${diff}°)"
        }
    }

    @Test fun bearing_london() = assertBearing(51.5074, -0.1278, 119.0)
    @Test fun bearing_newYork() = assertBearing(40.7128, -74.0060, 58.0)
    @Test fun bearing_jakarta() = assertBearing(-6.2088, 106.8456, 295.0)
    @Test fun bearing_sydney() = assertBearing(-33.8688, 151.2093, 277.0)

    @Test
    fun bearing_result_is_in_range() {
        val bearing = QiblaCalculator.bearingTo(51.5074, -0.1278)
        assert(bearing >= 0.0 && bearing < 360.0) { "Bearing $bearing not in [0, 360)" }
    }

    @Test
    fun bearing_atKaaba_isInRange() {
        val bearing = QiblaCalculator.bearingTo(21.4225, 39.8262)
        assert(bearing >= 0.0 && bearing < 360.0) { "Bearing at Kaaba $bearing not in [0, 360)" }
    }

    @Test
    fun bearing_northPole_isInRange() {
        val bearing = QiblaCalculator.bearingTo(90.0, 0.0)
        assert(bearing >= 0.0 && bearing < 360.0) { "Bearing from North Pole $bearing not in [0, 360)" }
    }

    @Test
    fun bearing_southPole_isInRange() {
        val bearing = QiblaCalculator.bearingTo(-90.0, 0.0)
        assert(bearing >= 0.0 && bearing < 360.0) { "Bearing from South Pole $bearing not in [0, 360)" }
    }

    @Test
    fun bearing_antimeridian_isInRange() {
        val bearing = QiblaCalculator.bearingTo(0.0, 180.0)
        assert(bearing >= 0.0 && bearing < 360.0) { "Bearing from antimeridian $bearing not in [0, 360)" }
    }

    @Test
    fun distance_atKaaba_isNearZero() {
        val dist = QiblaCalculator.distanceKm(21.4225, 39.8262)
        assert(dist < 1.0) { "Distance at Kaaba should be ~0 km but got $dist km" }
    }

    @Test
    fun distance_london_isApprox4800km() {
        val dist = QiblaCalculator.distanceKm(51.5074, -0.1278)
        assert(dist in 4500.0..5100.0) { "London distance: expected ~4800 km but got $dist km" }
    }

    @Test
    fun distance_newYork_isApprox10300km() {
        val dist = QiblaCalculator.distanceKm(40.7128, -74.0060)
        assert(dist in 10000.0..10600.0) { "New York distance: expected ~10300 km but got $dist km" }
    }

    @Test
    fun distance_jakarta_isApprox7900km() {
        val dist = QiblaCalculator.distanceKm(-6.2088, 106.8456)
        assert(dist in 7500.0..8400.0) { "Jakarta distance: expected ~7900 km but got $dist km" }
    }
}
