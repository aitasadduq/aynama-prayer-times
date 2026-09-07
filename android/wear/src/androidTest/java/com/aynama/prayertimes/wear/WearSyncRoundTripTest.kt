package com.aynama.prayertimes.wear

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import com.aynama.prayertimes.shared.data.repository.QazaRepository
import com.aynama.prayertimes.shared.sync.ProfileCodec
import com.aynama.prayertimes.shared.sync.WearSyncContract
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The phone→watch profile sync, over the real Data Layer.
 *
 * Publishes exactly what the phone publishes — the same [WearSyncContract] path and keys, the
 * same [ProfileCodec] payload — and then reads it back through the watch's own
 * [WearProfileSync.pullFromPhone]. Everything on both sides is exercised except the Play
 * Services hop between two physical nodes.
 *
 * That hop is the one thing this environment cannot reach: pairing two emulators needs the Wear
 * OS companion app on the phone, and installing it needs a Play Store sign-in. What is covered
 * here is what actually carries product risk — the encoding, the reconciliation, and the
 * refusal to act on a payload that cannot be read.
 */
@RunWith(AndroidJUnit4::class)
class WearSyncRoundTripTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val app = context as WearApplication
    private val qazaRepo = QazaRepository(app.db.qazaEntryDao())

    private lateinit var saved: List<Profile>
    private var savedActive = -1L

    private val london = Profile(
        id = 101,
        name = "London",
        latitude = 51.5074,
        longitude = -0.1278,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
        timezone = "Europe/London",
        useLocationTimezone = true,
    )
    private val makkah = london.copy(
        id = 102,
        name = "Makkah",
        latitude = 21.4225,
        longitude = 39.8262,
        calculationMethod = CalculationMethodKey.UMM_AL_QURA,
        sortOrder = 1,
        timezone = "Asia/Riyadh",
    )

    @Before
    fun setUp() = runBlocking {
        saved = app.profileRepository.observeAll().first()
        savedActive = app.syncState.activeProfileId
    }

    @After
    fun tearDown() = runBlocking {
        app.profileRepository.mirror(saved)
        app.syncState.activeProfileId = savedActive
    }

    /** Publish the way the phone does, then let the watch pull it. */
    private suspend fun phonePublishes(profiles: List<Profile>, activeId: Long) {
        val request = PutDataMapRequest.create(WearSyncContract.PATH_PROFILES).apply {
            dataMap.putString(WearSyncContract.KEY_PROFILES, ProfileCodec.encode(profiles))
            dataMap.putLong(WearSyncContract.KEY_ACTIVE_PROFILE_ID, activeId)
            dataMap.putLong(WearSyncContract.KEY_PUBLISHED_AT, System.currentTimeMillis())
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
        WearProfileSync.pullFromPhone(context)
    }

    private suspend fun mirrored() = app.profileRepository.observeAll().first()

    @Test
    fun profilesArriveOnTheWatch() = runBlocking {
        phonePublishes(listOf(london, makkah), activeId = makkah.id)

        assertEquals(listOf("London", "Makkah"), mirrored().map { it.name })
        // Ids survive the crossing — a complication that remembers a profile must still mean
        // the same place.
        assertEquals(listOf(101L, 102L), mirrored().map { it.id })
        assertEquals(makkah.id, app.syncState.activeProfileId)
        assertTrue("last sync should be recorded", app.syncState.lastSyncedAt > 0L)
    }

    @Test
    fun everyFieldSurvivesTheCrossing() = runBlocking {
        // Not just the names: the watch computes prayer times itself, so a madhab or a
        // calculation method lost in transit is a wrong time, not a cosmetic bug.
        val fussy = makkah.copy(
            asrMadhab = AsrMadhab.HANAFI,
            hijriOffset = 1,
            hijriOffsetMonthKey = 17_384,
            useLocationTimezone = false,
        )
        phonePublishes(listOf(fussy), activeId = fussy.id)

        assertEquals(listOf(fussy), mirrored())
    }

    @Test
    fun aNewProfileOnThePhoneAppearsOnTheWatch() = runBlocking {
        phonePublishes(listOf(london), activeId = london.id)
        assertEquals(listOf("London"), mirrored().map { it.name })

        phonePublishes(listOf(london, makkah), activeId = london.id)

        assertEquals(listOf("London", "Makkah"), mirrored().map { it.name })
    }

    @Test
    fun aDeletedProfileDisappearsFromTheWatch() = runBlocking {
        phonePublishes(listOf(london, makkah), activeId = london.id)

        phonePublishes(listOf(london), activeId = london.id)

        assertEquals(listOf("London"), mirrored().map { it.name })
    }

    @Test
    fun aRenameOnThePhoneFollowsTheSameRow() = runBlocking {
        phonePublishes(listOf(london), activeId = london.id)

        phonePublishes(listOf(london.copy(name = "Home")), activeId = london.id)

        assertEquals(listOf("Home"), mirrored().map { it.name })
        assertEquals(listOf(101L), mirrored().map { it.id })
    }

    @Test
    fun changingTheActiveProfileOnThePhoneMovesTheWatch() = runBlocking {
        phonePublishes(listOf(london, makkah), activeId = london.id)
        assertEquals(london.id, app.syncState.activeProfileId)

        phonePublishes(listOf(london, makkah), activeId = makkah.id)

        assertEquals(makkah.id, app.syncState.activeProfileId)
    }

    @Test
    fun deletingEveryProfileOnThePhoneClearsTheWatch() = runBlocking {
        phonePublishes(listOf(london, makkah), activeId = london.id)

        phonePublishes(emptyList(), activeId = WearSyncContract.NO_ACTIVE_PROFILE)

        // An empty set is a real state and distinct from an unreadable payload: the watch
        // should say the phone has no profiles, not keep showing two that are gone.
        assertEquals(emptyList<Profile>(), mirrored())
    }

    @Test
    fun aSyncKeepsTrackerHistoryForProfilesThatSurvive() = runBlocking {
        phonePublishes(listOf(london, makkah), activeId = london.id)
        qazaRepo.markPrayer(london.id, Prayer.FAJR, LocalDate.of(2026, 9, 4), QazaStatus.PRAYED_ON_TIME)

        phonePublishes(listOf(london.copy(name = "Home"), makkah), activeId = london.id)

        assertEquals(
            1,
            qazaRepo.observeByDateRange(
                london.id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
            ).first().size,
        )
    }

    @Test
    fun anUnreadablePayloadLeavesTheWatchAsItWas() = runBlocking {
        phonePublishes(listOf(london, makkah), activeId = london.id)
        val before = mirrored()

        val corrupt = PutDataMapRequest.create(WearSyncContract.PATH_PROFILES).apply {
            dataMap.putString(WearSyncContract.KEY_PROFILES, "aynama-profiles-v99whatever")
            dataMap.putLong(WearSyncContract.KEY_ACTIVE_PROFILE_ID, 7L)
            dataMap.putLong(WearSyncContract.KEY_PUBLISHED_AT, System.currentTimeMillis())
        }
        Wearable.getDataClient(context).putDataItem(corrupt.asPutDataRequest().setUrgent()).await()
        WearProfileSync.pullFromPhone(context)

        // One damaged or newer-versioned message must not wipe a watch that is working fine
        // from the last good one.
        assertEquals(before, mirrored())
        assertEquals(london.id, app.syncState.activeProfileId)
    }
}
