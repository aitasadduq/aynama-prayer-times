package com.aynama.prayertimes.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.shared.data.db.AynamaDatabase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ProfileRepositoryTest {

    private lateinit var db: AynamaDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var qazaRepo: QazaRepository

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = AynamaDatabase.buildInMemory(ctx)
        repo = ProfileRepository(db.profileDao())
        qazaRepo = QazaRepository(db.qazaEntryDao())
    }

    @After
    fun teardown() = db.close()

    private fun profile(name: String, isGps: Boolean = false, sortOrder: Int = 0) = Profile(
        name = name,
        latitude = 21.4225,
        longitude = 39.8262,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = isGps,
        sortOrder = sortOrder,
    )

    @Test
    fun insert_and_read() = runBlocking {
        val id = repo.insert(profile("Home"))
        val all = repo.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Home", all[0].name)
        assertEquals(id, all[0].id)
    }

    @Test
    fun update_profile() = runBlocking {
        val id = repo.insert(profile("Home"))
        val updated = profile("London").copy(id = id)
        repo.update(updated)
        val all = repo.observeAll().first()
        assertEquals("London", all[0].name)
    }

    @Test
    fun delete_profile() = runBlocking {
        val id = repo.insert(profile("Home"))
        val p = profile("Home").copy(id = id)
        repo.delete(p)
        val all = repo.observeAll().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun gps_constraint_only_one_gps_profile() = runBlocking {
        val idA = repo.setGpsProfile("Home", 21.4225, 39.8262, CalculationMethodKey.MWL, AsrMadhab.SHAFII)
        val idB = repo.setGpsProfile("Work", 51.5074, -0.1278, CalculationMethodKey.ISNA, AsrMadhab.HANAFI)

        val all = repo.observeAll().first()
        val gpsList = all.filter { it.isGps }
        assertEquals(1, gpsList.size)
        assertEquals(idB, gpsList[0].id)

        val profileA = all.first { it.id == idA }
        assertEquals(false, profileA.isGps)
    }

    @Test
    fun qaza_cascade_on_profile_delete() = runBlocking {
        val id = repo.insert(profile("Home"))
        qazaRepo.markPrayer(id, Prayer.FAJR, LocalDate.of(2026, 4, 25), QazaStatus.MISSED)
        qazaRepo.markPrayer(id, Prayer.DHUHR, LocalDate.of(2026, 4, 25), QazaStatus.MISSED)

        val before = qazaRepo.observeByDateRange(id, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)).first()
        assertEquals(2, before.size)

        repo.delete(profile("Home").copy(id = id))

        val after = qazaRepo.observeByDateRange(id, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)).first()
        assertTrue(after.isEmpty())
    }
}
