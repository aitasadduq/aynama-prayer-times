package com.aynama.prayertimes.location

import android.location.LocationManager.GPS_PROVIDER
import android.location.LocationManager.NETWORK_PROVIDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationProviderTest {

    // Qibla's dialog offers Approximate. With network location off, GPS is the only source left;
    // leaving it out sent the compass back to the saved profile without a word.
    @Test
    fun `approximate on Android 12 and later can use GPS`() {
        assertEquals(listOf(NETWORK_PROVIDER, GPS_PROVIDER), locationProviders(hasFine = false, sdkInt = 31))
    }

    @Test
    fun `approximate before Android 12 stays on the network provider`() {
        assertEquals(listOf(NETWORK_PROVIDER), locationProviders(hasFine = false, sdkInt = 30))
    }

    @Test
    fun `precise uses network then GPS`() {
        assertEquals(listOf(NETWORK_PROVIDER, GPS_PROVIDER), locationProviders(hasFine = true, sdkInt = 30))
    }

    // Coarse users rarely get a fresh GPS fix, so the last-known one is the usual answer. Without
    // an age limit, a fix from a previous trip would be shown as where the user is now.
    @Test
    fun `a last-known fix older than an hour is not used`() {
        val now = 100 * HOUR_NANOS
        assertTrue(isRecentFix(fixElapsedNanos = now - 59 * MINUTE_NANOS, nowElapsedNanos = now))
        assertFalse(isRecentFix(fixElapsedNanos = now - 61 * MINUTE_NANOS, nowElapsedNanos = now))
    }

    private companion object {
        const val MINUTE_NANOS = 60L * 1_000_000_000
        const val HOUR_NANOS = 60 * MINUTE_NANOS
    }
}
