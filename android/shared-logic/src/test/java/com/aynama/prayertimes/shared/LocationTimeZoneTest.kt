package com.aynama.prayertimes.shared

import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LocationTimeZoneTest {

    // The cities DS32 found an hour off under the longitude guess, and two single-zone controls.
    @Test fun madrid() = assertZone("ES", 40.4168, -3.7038, "Europe/Madrid")
    @Test fun barcelona() = assertZone("ES", 41.3874, 2.1686, "Europe/Madrid")
    @Test fun lisbon() = assertZone("PT", 38.7223, -9.1393, "Europe/Lisbon")
    @Test fun detroit() = assertZone("US", 42.3314, -83.0458, "America/Detroit")
    @Test fun atlanta() = assertZone("US", 33.7490, -84.3880, "America/New_York")
    @Test fun columbus() = assertZone("US", 39.9612, -82.9988, "America/New_York")
    @Test fun calgary() = assertZone("CA", 51.0447, -114.0719, "America/Edmonton")
    @Test fun edmonton() = assertZone("CA", 53.5461, -113.4938, "America/Edmonton")
    @Test fun surabaya() = assertZone("ID", -7.2575, 112.7521, "Asia/Jakarta")
    @Test fun london() = assertZone("GB", 51.5074, -0.1278, "Europe/London")
    @Test fun makkah() = assertZone("SA", 21.3891, 39.8579, "Asia/Riyadh")

    // The other side of each boundary, so a lookup that always answered the capital would fail.
    @Test fun lasPalmas() = assertZone("ES", 28.1235, -15.4363, "Atlantic/Canary")
    @Test fun pontaDelgada() = assertZone("PT", 37.7412, -25.6756, "Atlantic/Azores")
    @Test fun pensacola() = assertZone("US", 30.4213, -87.2169, "America/Chicago")
    @Test fun elPaso() = assertZone("US", 31.7619, -106.4850, "America/Denver")
    @Test fun vancouver() = assertZone("CA", 49.2827, -123.1207, "America/Vancouver")
    @Test fun denpasar() = assertZone("ID", -8.6705, 115.2126, "Asia/Makassar")

    @Test fun lowercaseCountryCode() = assertZone("es", 40.4168, -3.7038, "Europe/Madrid")

    @Test
    fun unknownCountryIsBlank() {
        assertEquals("", LocationTimeZone.detect(null, 40.4168, -3.7038))
        assertEquals("", LocationTimeZone.detect("", 40.4168, -3.7038))
        assertEquals("", LocationTimeZone.detect("XX", 40.4168, -3.7038))
    }

    @Test
    fun everyZoneTabLocationResolvesToItsOwnZone() {
        val cases = javaClass.getResourceAsStream("/timezone-lookup-cases.tsv")!!
            .bufferedReader().readLines()
            .filterNot { it.startsWith("#") }
            .map { it.split("\t") }
        val available = ZoneId.getAvailableZoneIds()
        // This JVM's tzdata may predate the lookup's; a zone it lacks must come back blank.
        val checked = cases.count { (country, lat, lng, zone) ->
            val expected = if (zone in available) zone else ""
            assertEquals("$country $lat,$lng", expected, LocationTimeZone.detect(country, lat.toDouble(), lng.toDouble()))
            zone in available
        }
        assertTrue("only $checked of ${cases.size} zones known to this JVM", checked > 400)
    }

    @Test
    fun redetectFixesZoneTheLongitudeGuessPicked() {
        val madrid = cityProfile(40.4168, -3.7038, timezone = "Atlantic/Canary")
        assertEquals("Europe/Madrid", madrid.withRedetectedTimezone { "ES" }.timezone)
    }

    @Test
    fun redetectKeepsTheToggleAsTheUserLeftIt() {
        val madrid = cityProfile(40.4168, -3.7038, timezone = "Atlantic/Canary", useLocationTimezone = false)
        assertEquals(false, madrid.withRedetectedTimezone { "ES" }.useLocationTimezone)
    }

    @Test
    fun redetectLeavesCorrectGpsBlankAndUnplaceableProfilesAlone() {
        val correct = cityProfile(42.3314, -83.0458, timezone = "America/Detroit")
        val gps = cityProfile(40.4168, -3.7038, timezone = "Atlantic/Canary").copy(isGps = true)
        val blank = cityProfile(40.4168, -3.7038, timezone = "")
        val unknownRegion = cityProfile(40.4168, -3.7038, timezone = "Atlantic/Canary")
        assertSame(correct, correct.withRedetectedTimezone { "US" })
        assertSame(gps, gps.withRedetectedTimezone { "ES" })
        assertSame(blank, blank.withRedetectedTimezone { "ES" })
        assertSame(unknownRegion, unknownRegion.withRedetectedTimezone { null })
    }

    private fun assertZone(country: String, lat: Double, lng: Double, expected: String) {
        assertEquals(expected, LocationTimeZone.detect(country, lat, lng))
    }

    private fun cityProfile(
        lat: Double,
        lng: Double,
        timezone: String,
        useLocationTimezone: Boolean = true,
    ) = Profile(
        name = "City",
        latitude = lat,
        longitude = lng,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
        timezone = timezone,
        useLocationTimezone = useLocationTimezone,
    )
}
