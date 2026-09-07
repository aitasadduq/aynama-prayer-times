package com.aynama.prayertimes.shared.sync

import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {

    private val makkah = Profile(
        id = 1,
        name = "Makkah",
        latitude = 21.4225,
        longitude = 39.8262,
        calculationMethod = CalculationMethodKey.UMM_AL_QURA,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
        timezone = "Asia/Riyadh",
        useLocationTimezone = true,
        hijriOffset = 1,
        hijriOffsetMonthKey = 17_384,
    )

    private val london = makkah.copy(
        id = 2,
        name = "London (Ḥanafī)",
        latitude = 51.5074,
        longitude = -0.1278,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.HANAFI,
        isGps = true,
        sortOrder = 1,
        timezone = "Europe/London",
        useLocationTimezone = false,
        hijriOffset = 0,
        hijriOffsetMonthKey = 0,
    )

    @Test
    fun roundTripsEveryField() {
        assertEquals(listOf(makkah, london), ProfileCodec.decode(ProfileCodec.encode(listOf(makkah, london))))
    }

    @Test
    fun roundTripsAnEmptySet() {
        // A phone with no profiles is a real state, and distinct from a payload we could not read.
        assertEquals(emptyList<Profile>(), ProfileCodec.decode(ProfileCodec.encode(emptyList())))
    }

    @Test
    fun namesContainingSeparatorsSurvive() {
        // Profile names are user text; nothing stops one containing the separators themselves.
        val awkward = makkah.copy(name = "Home \u001F Work \u001E Travel \\ Other")
        assertEquals(listOf(awkward), ProfileCodec.decode(ProfileCodec.encode(listOf(awkward))))
    }

    @Test
    fun negativeCoordinatesAndOffsetsSurvive() {
        val southWest = makkah.copy(latitude = -33.9249, longitude = -18.4241, hijriOffset = -2)
        assertEquals(listOf(southWest), ProfileCodec.decode(ProfileCodec.encode(listOf(southWest))))
    }

    // --- Refusing to guess ------------------------------------------------------

    @Test
    fun aPayloadFromAnotherVersionIsNotDecoded() {
        assertNull(ProfileCodec.decode("aynama-profiles-v2\u001EMakkah"))
    }

    @Test
    fun rubbishIsNotDecoded() {
        assertNull(ProfileCodec.decode("hello"))
        assertNull(ProfileCodec.decode(""))
        assertNull(ProfileCodec.decode(null))
    }

    @Test
    fun aTruncatedRecordIsNotDecoded() {
        val encoded = ProfileCodec.encode(listOf(makkah))
        val truncated = encoded.substringBeforeLast('\u001F')

        // Not an empty list: "I could not read this" must not be mistaken for "the phone has
        // no profiles", which would wipe a working watch because one message arrived damaged.
        assertNull(ProfileCodec.decode(truncated))
    }

    @Test
    fun anUnknownCalculationMethodIsNotDecoded() {
        val encoded = ProfileCodec.encode(listOf(makkah)).replace("UMM_AL_QURA", "ATLANTIS")

        assertNull(ProfileCodec.decode(encoded))
    }

    @Test
    fun anUnparseableNumberIsNotDecoded() {
        val encoded = ProfileCodec.encode(listOf(makkah)).replace("21.4225", "north-ish")

        assertNull(ProfileCodec.decode(encoded))
    }

    @Test
    fun theVersionTagIsPresentSoAFutureReaderCanRefuseUs() {
        assertTrue(ProfileCodec.encode(listOf(makkah)).startsWith(ProfileCodec.VERSION))
    }
}
