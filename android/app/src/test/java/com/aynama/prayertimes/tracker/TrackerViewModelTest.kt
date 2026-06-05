package com.aynama.prayertimes.tracker

import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.QazaEntry
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.data.repository.QazaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModelTest {

    private val monday = LocalDate.of(2026, 5, 4)  // known Monday

    @Test
    fun `weekLabel — this week returns 'This week'`() {
        assertEquals("This week", weekLabel(monday, monday))
    }

    @Test
    fun `weekLabel — last week returns 'Last week'`() {
        val lastMonday = monday.minusWeeks(1)
        assertEquals("Last week", weekLabel(lastMonday, monday))
    }

    @Test
    fun `weekLabel — two weeks ago returns date range`() {
        val twoWeeksAgo = monday.minusWeeks(2)
        val result = weekLabel(twoWeeksAgo, monday)
        assertEquals("Apr 20–Apr 26", result)
    }

    @Test
    fun `weekLabel — three weeks ago returns date range`() {
        val threeWeeksAgo = monday.minusWeeks(3)
        val result = weekLabel(threeWeeksAgo, monday)
        assertEquals("Apr 13–Apr 19", result)
    }

    @Test
    fun `prayedCount — null status does not count`() {
        val prayers: Map<Prayer, QazaStatus?> = Prayer.entries.associateWith { null }
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(0, count)
    }

    @Test
    fun `prayedCount — PRAYED_ON_TIME counts`() {
        val prayers: Map<Prayer, QazaStatus?> = Prayer.entries.associateWith { QazaStatus.PRAYED_ON_TIME }
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(5, count)
    }

    @Test
    fun `prayedCount — MISSED does not count`() {
        val prayers: Map<Prayer, QazaStatus?> = mapOf(
            Prayer.FAJR to QazaStatus.MISSED,
            Prayer.DHUHR to QazaStatus.PRAYED_ON_TIME,
            Prayer.ASR to QazaStatus.PRAYED_ON_TIME,
            Prayer.MAGHRIB to QazaStatus.PRAYED_ON_TIME,
            Prayer.ISHA to QazaStatus.PRAYED_ON_TIME,
        )
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(4, count)
    }

    @Test
    fun `prayedCount — INTENTION_TO_MAKEUP does not count`() {
        val prayers: Map<Prayer, QazaStatus?> = mapOf(
            Prayer.FAJR to QazaStatus.INTENTION_TO_MAKEUP,
            Prayer.DHUHR to QazaStatus.INTENTION_TO_MAKEUP,
            Prayer.ASR to null,
            Prayer.MAGHRIB to null,
            Prayer.ISHA to QazaStatus.MADE_UP,
        )
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(1, count)
    }

    @Test
    fun `prayedCount — MADE_UP counts`() {
        val prayers: Map<Prayer, QazaStatus?> = Prayer.entries.associateWith { QazaStatus.MADE_UP }
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(5, count)
    }

    @Test
    fun `today is anchored to a Monday`() {
        val date = LocalDate.of(2026, 5, 9)  // Saturday
        val thisWeekMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 5, 4), thisWeekMonday)
    }

    // ---------- M6 + A2: today counts toward the week; future prayers aren't tappable ----------

    // Friday 2026-05-08 at 13:00 local: Fajr (5:12) and Dhuhr (12:30) are due; Asr (15:50),
    // Maghrib (18:30) and Isha (19:45) are not yet due.
    private val fridayAtOnePm = Clock.fixed(Instant.parse("2026-05-08T13:00:00Z"), ZoneId.of("UTC"))

    private val fakeTimes = PrayerTimesResult(
        fajr = LocalTime.of(5, 12),
        sunrise = LocalTime.of(6, 30),
        dhuhr = LocalTime.of(12, 30),
        asrShafii = LocalTime.of(15, 50),
        asrHanafi = LocalTime.of(16, 50),
        maghrib = LocalTime.of(18, 30),
        isha = LocalTime.of(19, 45),
    )

    private val londonProfile = Profile(
        id = 1,
        name = "Home",
        latitude = 51.5074,
        longitude = -0.1278,
        calculationMethod = CalculationMethodKey.ISNA,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkConstructor(AdhanWrapper::class)
        every { anyConstructed<AdhanWrapper>().getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun loadedState(todayEntries: List<QazaEntry>): TrackerUiState.Loaded {
        val profileRepo = mockk<ProfileRepository>()
        val qazaRepo = mockk<QazaRepository>()
        every { profileRepo.observeDefaultProfile() } returns flowOf(londonProfile)
        every { qazaRepo.observeByDateRange(1L, any(), any()) } returns flowOf(todayEntries)
        every { qazaRepo.observeOutstandingCount(1L) } returns flowOf(0)
        val vm = TrackerViewModel(profileRepo, qazaRepo, fridayAtOnePm)
        return vm.uiState.value as TrackerUiState.Loaded
    }

    @Test
    fun `today's prayed-on-time prayer counts toward the week aggregate`() = runTest {
        val fajrPrayed = QazaEntry(
            prayer = Prayer.FAJR,
            date = LocalDate.of(2026, 5, 8),
            status = QazaStatus.PRAYED_ON_TIME,
            profileId = 1,
            updatedAt = 0,
        )
        val loaded = loadedState(listOf(fajrPrayed))
        val thisWeek = loaded.weeks.first { it.label == "This week" }
        // 4 history days this week (Mon–Thu, no entries) = 0/20, plus today's 2 due prayers
        // with Fajr on time = 1/2 → "1 of 22". (Asr/Maghrib/Isha not yet due, excluded.)
        assertEquals("1 of 22 prayers on time this week", thisWeek.aggregate)
    }

    @Test
    fun `today rows are tappable only once their time has passed`() = runTest {
        val loaded = loadedState(emptyList())
        val byPrayer = loaded.todayRows.associateBy { it.prayer }
        assertTrue("Fajr is due by 1pm", byPrayer.getValue(Prayer.FAJR).tappable)
        assertTrue("Dhuhr is due by 1pm", byPrayer.getValue(Prayer.DHUHR).tappable)
        assertFalse("Asr is not due at 1pm", byPrayer.getValue(Prayer.ASR).tappable)
        assertFalse("Maghrib is not due at 1pm", byPrayer.getValue(Prayer.MAGHRIB).tappable)
        assertFalse("Isha is not due at 1pm", byPrayer.getValue(Prayer.ISHA).tappable)
    }
}
