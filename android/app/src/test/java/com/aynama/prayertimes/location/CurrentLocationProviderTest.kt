package com.aynama.prayertimes.location

import android.location.LocationManager.GPS_PROVIDER
import android.location.LocationManager.NETWORK_PROVIDER
import org.junit.Assert.assertEquals
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
}
