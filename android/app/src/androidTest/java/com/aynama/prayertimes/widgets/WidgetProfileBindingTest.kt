package com.aynama.prayertimes.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two real widgets, bound to two different profiles.
 *
 * Everything below the Glance state — which prayer, which countdown — is covered by unit
 * tests. What only a device can show is the round trip: that a widget's profile choice is
 * actually persisted against its own `appWidgetId`, that a second widget does not overwrite
 * the first, and that the rollover chain is armed for *both* profiles rather than only the
 * one that happens to receive notifications.
 *
 * Widgets are placed through a real [AppWidgetHost], the same API a launcher uses. Binding
 * needs `BIND_APPWIDGET`, which the instrumentation borrows from the shell identity.
 */
@RunWith(AndroidJUnit4::class)
class WidgetProfileBindingTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val app = context as AynamaApplication
    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)

    private var widgetA = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetB = AppWidgetManager.INVALID_APPWIDGET_ID
    private val createdProfileIds = mutableListOf<Long>()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.adoptShellPermissionIdentity()
    }

    @After
    fun tearDown() {
        listOf(widgetA, widgetB)
            .filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }
            .forEach { host.deleteAppWidgetId(it) }
        PrayerWidgetScheduler.cancelAll(context)
        runBlocking {
            val all = app.profileRepository.observeAll().first()
            all.filter { it.id in createdProfileIds }.forEach { app.profileRepository.delete(it) }
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun eachWidgetKeepsItsOwnProfile() = runBlocking {
        val (makkah, istanbul) = twoProfiles()
        widgetA = place()
        widgetB = place()

        setWidgetProfile(context, widgetA, makkah.id)
        setWidgetProfile(context, widgetB, istanbul.id)

        assertEquals(makkah.id, widgetProfileIdFor(context, widgetA))
        assertEquals(istanbul.id, widgetProfileIdFor(context, widgetB))
    }

    @Test
    fun configuringOneWidgetDoesNotDisturbTheOther() = runBlocking {
        val (makkah, istanbul) = twoProfiles()
        widgetA = place()
        widgetB = place()
        setWidgetProfile(context, widgetA, makkah.id)
        setWidgetProfile(context, widgetB, istanbul.id)

        // Reconfigure the second one. The first must not follow it.
        setWidgetProfile(context, widgetB, makkah.id)

        assertEquals(makkah.id, widgetProfileIdFor(context, widgetA))
        assertEquals(makkah.id, widgetProfileIdFor(context, widgetB))

        setWidgetProfile(context, widgetB, istanbul.id)
        assertEquals(makkah.id, widgetProfileIdFor(context, widgetA))
        assertEquals(istanbul.id, widgetProfileIdFor(context, widgetB))
    }

    @Test
    fun bothBoundProfilesGetRolloverChains() = runBlocking {
        val (makkah, istanbul) = twoProfiles()
        widgetA = place()
        widgetB = place()
        setWidgetProfile(context, widgetA, makkah.id)
        setWidgetProfile(context, widgetB, istanbul.id)

        val bound = boundWidgetProfiles(context, app.profileRepository.observeAll().first())

        // A widget can render a profile that never receives notifications; scheduling only the
        // notification profile would leave it with no alarm at its own prayer boundaries.
        assertTrue("Makkah is bound to a widget", bound.any { it.id == makkah.id })
        assertTrue("Istanbul is bound to a widget", bound.any { it.id == istanbul.id })
    }

    @Test
    fun rolloverAlarmsAreActuallyArmedForBothProfiles() = runBlocking {
        val (makkah, istanbul) = twoProfiles()
        widgetA = place()
        widgetB = place()
        setWidgetProfile(context, widgetA, makkah.id)
        setWidgetProfile(context, widgetB, istanbul.id)

        PrayerWidgetScheduler.scheduleForBoundProfiles(
            context, app.profileRepository.observeAll().first(),
        )

        // boundWidgetProfiles sorts by id, so the two profiles occupy slots 0 and 1. Each slot
        // owns its own block of request codes; both blocks must contain armed alarms, or a
        // widget sits on a countdown that never rolls over.
        for (slot in 0..1) {
            val base = WIDGET_UPDATE_REQUEST_CODE_BASE + slot * WIDGET_UPDATE_SLOT_COUNT
            val armed = (0 until WIDGET_UPDATE_SLOT_COUNT).count { existingRollover(base + it) != null }
            assertTrue("slot $slot has no rollover alarm armed", armed > 0)
        }
    }

    @Test
    fun anUnconfiguredWidgetAsksForNoParticularProfile() = runBlocking {
        widgetA = place()

        assertEquals(NO_WIDGET_PROFILE, widgetProfileIdFor(context, widgetA))
    }

    // --- helpers ----------------------------------------------------------------

    /** The PendingIntent an armed rollover would have created, or null. */
    private fun existingRollover(requestCode: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, PrayerWidgetUpdateReceiver::class.java)
            .setAction(ACTION_PRAYER_WIDGET_UPDATE),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun place(): Int {
        val id = host.allocateAppWidgetId()
        val provider = ComponentName(context, NextPrayerWidgetReceiver::class.java)
        val bound = manager.bindAppWidgetIdIfAllowed(id, provider)
        assertTrue(
            "could not bind an app widget — instrumentation needs BIND_APPWIDGET from the " +
                "shell identity, which this build may not grant",
            bound,
        )
        return id
    }

    private suspend fun twoProfiles(): Pair<Profile, Profile> {
        val makkahId = app.profileRepository.insert(
            profile("Widget test Makkah", 21.4225, 39.8262, "Asia/Riyadh", 900),
        )
        val istanbulId = app.profileRepository.insert(
            profile("Widget test Istanbul", 41.0082, 28.9784, "Europe/Istanbul", 901),
        )
        createdProfileIds += listOf(makkahId, istanbulId)
        val all = app.profileRepository.observeAll().first()
        return all.first { it.id == makkahId } to all.first { it.id == istanbulId }
    }

    private fun profile(name: String, lat: Double, lng: Double, zone: String, sortOrder: Int) = Profile(
        name = name,
        latitude = lat,
        longitude = lng,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = sortOrder,
        timezone = zone,
        useLocationTimezone = true,
    )

    private companion object {
        const val HOST_ID = 0x41594E41 // "AYNA"
    }
}
