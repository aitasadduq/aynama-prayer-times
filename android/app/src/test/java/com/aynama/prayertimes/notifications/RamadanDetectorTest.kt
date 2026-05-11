package com.aynama.prayertimes.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RamadanDetectorTest {

    @Test
    fun `isRamadan returns true for Ramadan month (8)`() {
        assertTrue(RamadanDetector.isRamadan(8))
    }

    @Test
    fun `isRamadan returns false for Shaaban (7)`() {
        assertFalse(RamadanDetector.isRamadan(7))
    }

    @Test
    fun `isRamadan returns false for Shawwal (9)`() {
        assertFalse(RamadanDetector.isRamadan(9))
    }

    @Test
    fun `isRamadan returns false for month 0`() {
        assertFalse(RamadanDetector.isRamadan(0))
    }
}
