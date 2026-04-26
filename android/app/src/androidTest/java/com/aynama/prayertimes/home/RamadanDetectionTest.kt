package com.aynama.prayertimes.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RamadanDetectionTest {

    @Test
    fun midRamadan1445_returnsTrue() {
        // Ramadan 1445 ran approximately March 11 – April 9, 2024
        assertTrue(isRamadan(LocalDate.of(2024, 3, 20)))
    }

    @Test
    fun firstDayRamadan1445_returnsTrue() {
        assertTrue(isRamadan(LocalDate.of(2024, 3, 11)))
    }

    @Test
    fun lastDayRamadan1445_returnsTrue() {
        assertTrue(isRamadan(LocalDate.of(2024, 4, 9)))
    }

    @Test
    fun dayAfterRamadan1445_returnsFalse() {
        assertFalse(isRamadan(LocalDate.of(2024, 4, 10)))
    }

    @Test
    fun shawwal1445_returnsFalse() {
        assertFalse(isRamadan(LocalDate.of(2024, 4, 15)))
    }

    @Test
    fun nonRamadanDate_returnsFalse() {
        assertFalse(isRamadan(LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun ramadan1446_returnsTrue() {
        // Ramadan 1446 started approximately March 1, 2025
        assertTrue(isRamadan(LocalDate.of(2025, 3, 10)))
    }
}
