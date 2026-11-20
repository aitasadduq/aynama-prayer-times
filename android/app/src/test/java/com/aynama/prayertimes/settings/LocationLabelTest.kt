package com.aynama.prayertimes.settings

import android.location.Address
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationLabelTest {
    private fun address(city: String?, district: String? = null, region: String? = null, country: String? = null) =
        mockk<Address> {
            every { locality } returns city
            every { subAdminArea } returns district
            every { adminArea } returns region
            every { countryName } returns country
            // No street address or coordinates should ever be requested for a display label.
        }

    @Test
    fun selectedCitiesUseCityAndCountry() {
        assertEquals("Makkah, Saudi Arabia", buildCityLabel(address(" Makkah ", country = "Saudi Arabia")))
    }

    @Test
    fun locationsOutsideACityUseANamedDistrictOrRegion() {
        assertEquals("Makkah Province, Saudi Arabia", buildCityLabel(address("", region = "Makkah Province", country = "Saudi Arabia")))
        assertEquals("Westminster, United Kingdom", buildCityLabel(address(null, district = "Westminster", country = "United Kingdom")))
    }

    @Test
    fun unresolvablePlacesHaveNoNumericFallback() {
        assertNull(buildCityLabel(address(" ")))
        assertEquals("Saudi Arabia", buildCityLabel(address(null, country = "Saudi Arabia")))
    }
}
