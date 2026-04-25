package com.aynama.prayertimes.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.shared.data.db.AynamaDatabase
import com.aynama.prayertimes.shared.data.db.Converters
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.data.repository.QazaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class QazaTrackerTest {

    private lateinit var db: AynamaDatabase
    private lateinit var profileRepo: ProfileRepository
    private lateinit var repo: QazaRepository
    private var profileId: Long = 0L

    @Before
    fun setup() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = AynamaDatabase.buildInMemory(ctx)
        profileRepo = ProfileRepository(db.profileDao())
        repo = QazaRepository(db.qazaEntryDao())
        profileId = profileRepo.insert(
            Profile(
                name = "Home",
                latitude = 21.4225,
                longitude = 39.8262,
                calculationMethod = CalculationMethodKey.MWL,
                asrMadhab = AsrMadhab.SHAFII,
                isGps = false,
                sortOrder = 0,
            )
        )
    }

    @After
    fun teardown() = db.close()

    @Test
    fun type_converter_qaza_status_round_trips() {
        val c = Converters()
        for (status in QazaStatus.entries) {
            assertEquals(status, c.stringToQazaStatus(c.qazaStatusToString(status)))
        }
    }

    @Test
    fun type_converter_prayer_round_trips() {
        val c = Converters()
        for (prayer in Prayer.entries) {
            assertEquals(prayer, c.stringToPrayer(c.prayerToString(prayer)))
        }
    }

    @Test
    fun mark_prayer_writes_entry() = runBlocking {
        val date = LocalDate.of(2026, 4, 25)
        repo.markPrayer(profileId, Prayer.FAJR, date, QazaStatus.MADE_UP)

        val entries = repo.observeByDateRange(profileId, date, date).first()
        assertEquals(1, entries.size)
        assertEquals(Prayer.FAJR, entries[0].prayer)
        assertEquals(QazaStatus.MADE_UP, entries[0].status)
    }

    @Test
    fun mark_prayer_overwrites_existing_entry() = runBlocking {
        val date = LocalDate.of(2026, 4, 25)
        repo.markPrayer(profileId, Prayer.FAJR, date, QazaStatus.MISSED)
        repo.markPrayer(profileId, Prayer.FAJR, date, QazaStatus.MADE_UP)

        val entries = repo.observeByDateRange(profileId, date, date).first()
        assertEquals(1, entries.size)
        assertEquals(QazaStatus.MADE_UP, entries[0].status)
    }

    @Test
    fun auto_mark_missed_inserts_when_no_entry() = runBlocking {
        val date = LocalDate.of(2026, 4, 25)
        repo.autoMarkMissed(profileId, Prayer.DHUHR, date)

        val entries = repo.observeByDateRange(profileId, date, date).first()
        assertEquals(1, entries.size)
        assertEquals(QazaStatus.MISSED, entries[0].status)
    }

    @Test
    fun auto_mark_missed_does_not_overwrite_existing_entry() = runBlocking {
        val date = LocalDate.of(2026, 4, 25)
        repo.markPrayer(profileId, Prayer.DHUHR, date, QazaStatus.MADE_UP)
        repo.autoMarkMissed(profileId, Prayer.DHUHR, date)

        val entries = repo.observeByDateRange(profileId, date, date).first()
        assertEquals(1, entries.size)
        assertEquals(QazaStatus.MADE_UP, entries[0].status)
    }

    @Test
    fun outstanding_count_counts_missed_and_intention() = runBlocking {
        val date = LocalDate.of(2026, 4, 25)
        repo.markPrayer(profileId, Prayer.FAJR, date, QazaStatus.MISSED)
        repo.markPrayer(profileId, Prayer.DHUHR, date, QazaStatus.INTENTION_TO_MAKEUP)
        repo.markPrayer(profileId, Prayer.ASR, date, QazaStatus.MADE_UP)

        val count = repo.observeOutstandingCount(profileId).first()
        assertEquals(2, count)
    }
}
