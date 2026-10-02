package com.aynama.prayertimes.wear

import android.content.SharedPreferences
import com.aynama.prayertimes.notifications.NotificationPreferences
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.sync.WearSyncContract
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the phone sends the watch, and when.
 *
 * The watch's active profile is the phone's alerts profile, which lives in a preference. A change
 * made only in Notification settings used to reach the watch only after an unrelated profile edit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchPublicationsTest {

    private val home = profile(id = 1, name = "Home", sortOrder = 0)
    private val work = profile(id = 2, name = "Work", sortOrder = 1)

    @Test
    fun aPreferenceOnlyChangeRepublishes() = runTest {
        var stored = -1L
        val listener = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getLong(any(), any()) } answers { stored }
            every { registerOnSharedPreferenceChangeListener(capture(listener)) } just runs
        }
        val emitted = mutableListOf<WatchPublication>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            watchPublications(
                profiles = MutableStateFlow(listOf(home, work)),
                notificationProfileId = NotificationPreferences(prefs).notificationProfileIdFlow(),
            ).toList(emitted)
        }
        advanceUntilIdle()
        assertEquals(listOf(home.id), emitted.map { it.activeProfileId })

        // The profiles are untouched; only the alerts profile moves.
        stored = work.id
        listener.captured.onSharedPreferenceChanged(prefs, "notif_profile_id")
        advanceUntilIdle()

        assertEquals(listOf(home.id, work.id), emitted.map { it.activeProfileId })
        assertEquals(listOf(home, work), emitted.last().profiles)
    }

    @Test
    fun unrelatedPreferencesDoNotRepublish() = runTest {
        val listener = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getLong(any(), any()) } returns home.id
            every { registerOnSharedPreferenceChangeListener(capture(listener)) } just runs
        }
        val emitted = mutableListOf<WatchPublication>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            watchPublications(
                profiles = MutableStateFlow(listOf(home, work)),
                notificationProfileId = NotificationPreferences(prefs).notificationProfileIdFlow(),
            ).toList(emitted)
        }
        advanceUntilIdle()

        listener.captured.onSharedPreferenceChanged(prefs, "notif_master_enabled")
        advanceUntilIdle()

        assertEquals(1, emitted.size)
    }

    @Test
    fun aDeletedAlertsProfileFallsBackToTheFirstProfile() = runTest {
        val publication = mutableListOf<WatchPublication>()
        watchPublications(flowOf(listOf(home, work)), flowOf(99L)).toList(publication)
        assertEquals(home.id, publication.single().activeProfileId)
    }

    @Test
    fun noProfilesMeansNoActiveProfile() = runTest {
        val publication = mutableListOf<WatchPublication>()
        watchPublications(flowOf(emptyList()), flowOf(-1L)).toList(publication)
        assertEquals(WearSyncContract.NO_ACTIVE_PROFILE, publication.single().activeProfileId)
    }

    private fun profile(id: Long, name: String, sortOrder: Int) = Profile(
        id = id,
        name = name,
        latitude = 51.5074,
        longitude = -0.1278,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = sortOrder,
    )
}
