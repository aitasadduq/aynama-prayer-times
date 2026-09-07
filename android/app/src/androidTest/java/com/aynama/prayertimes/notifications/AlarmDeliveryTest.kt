package com.aynama.prayertimes.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That prayer alarms are really armed, really fire, and really come back.
 *
 * `AlarmSchedulerTest` covers *what* gets scheduled — times, offsets, Imsak, Jumu'ah naming —
 * as pure functions. None of that proves AlarmManager accepted the alarm, that the receiver
 * runs, or that a notification reaches the shade. Only a device shows that.
 *
 * Required by architecture-design.md's E2E table (`AlarmFiresWhileClosedTest`,
 * `AlarmRestoredAfterRebootTest`), which TODOS.md had ticked off while the files did not exist.
 */
@RunWith(AndroidJUnit4::class)
class AlarmDeliveryTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val app = context as AynamaApplication
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private lateinit var profile: Profile
    private var savedNotificationProfile = -1L

    @Before
    fun setUp() = runBlocking {
        // Self-sufficient on a fresh device: without POST_NOTIFICATIONS the shade assertion
        // fails for a reason that has nothing to do with alarms.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, android.Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        NotificationHelper.createChannels(context)
        val prefs = NotificationPreferences(app.prefs)
        savedNotificationProfile = prefs.notificationProfileId

        val id = app.profileRepository.insert(
            Profile(
                name = "Alarm test Makkah",
                latitude = 21.4225,
                longitude = 39.8262,
                calculationMethod = CalculationMethodKey.UMM_AL_QURA,
                asrMadhab = AsrMadhab.SHAFII,
                isGps = false,
                sortOrder = 950,
                timezone = "Asia/Riyadh",
                useLocationTimezone = true,
            ),
        )
        profile = app.profileRepository.observeAll().first().first { it.id == id }
        prefs.notificationProfileId = id
        prefs.masterEnabled = true
    }

    @After
    fun tearDown() = runBlocking {
        AlarmScheduler.cancelForProfile(context, profile.id)
        notificationManager.cancelAll()
        NotificationPreferences(app.prefs).notificationProfileId = savedNotificationProfile
        app.profileRepository.delete(profile)
        // Leave the device as we found it: the profile set changed, so re-arm from what remains.
        AlarmScheduler.scheduleAll(context, app.profileRepository.observeAll().first())
    }

    @Test
    fun scheduleAll_armsAlarmsForTheNotificationProfile() = runBlocking {
        AlarmScheduler.cancelForProfile(context, profile.id)
        assertNull("precondition: no alarm armed", existingAlarm(PRAYER_INDEX_FAJR))

        AlarmScheduler.scheduleAll(context, app.profileRepository.observeAll().first())

        // Only prayers still ahead in the profile's day are armed, so assert on the set rather
        // than on one index — which one is next depends on when the suite happens to run.
        val armed = (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).count { existingAlarm(it) != null }
        assertTrue("expected at least one prayer alarm armed, found $armed", armed > 0)
    }

    @Test
    fun alarmsAreRestoredAfterAReschedule() = runBlocking {
        AlarmScheduler.scheduleAll(context, app.profileRepository.observeAll().first())
        val before = (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).count { existingAlarm(it) != null }
        assertTrue("precondition: alarms armed", before > 0)

        AlarmScheduler.cancelForProfile(context, profile.id)
        assertTrue(
            "precondition: alarms cleared",
            (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).none { existingAlarm(it) != null },
        )

        // The same handler BOOT_COMPLETED reaches. BOOT_COMPLETED itself is a protected
        // broadcast the instrumentation cannot forge, so the daily reschedule — which
        // BootReceiver treats identically — stands in for it here. The reboot path proper is
        // exercised by rebooting the device in the validation gate.
        context.sendBroadcast(
            Intent(context, BootReceiver::class.java).setAction(ACTION_MIDNIGHT_RESCHEDULE),
        )
        awaitTrue("alarms were not re-armed after the reschedule broadcast") {
            (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).count { existingAlarm(it) != null } == before
        }
    }

    @Test
    fun turningNotificationsOffDisarmsEveryPrayerAlarm() = runBlocking {
        val prefs = NotificationPreferences(app.prefs)
        AlarmScheduler.scheduleAll(context, app.profileRepository.observeAll().first())
        assertTrue(
            "precondition: alarms armed",
            (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).any { existingAlarm(it) != null },
        )

        prefs.masterEnabled = false
        try {
            AlarmScheduler.scheduleAll(context, app.profileRepository.observeAll().first())

            // The user turned prayer notifications off. Every armed alarm must go with them —
            // buildAlarmSchedule returning an empty list is not enough on its own, because
            // nothing re-submits over the alarms already sitting in AlarmManager.
            assertTrue(
                "prayer alarms survived the master toggle being turned off",
                (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).none { existingAlarm(it) != null },
            )
        } finally {
            prefs.masterEnabled = true
        }
    }

    @Test
    fun deletingAProfileDisarmsItsAlarms() = runBlocking {
        AlarmScheduler.scheduleAll(context, app.profileRepository.observeAll().first())
        assertTrue(
            "precondition: alarms armed",
            (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).any { existingAlarm(it) != null },
        )

        AlarmScheduler.cancelForProfile(context, profile.id)

        assertTrue(
            "a deleted profile's alarms would keep firing",
            (PRAYER_INDEX_FAJR..PRAYER_INDEX_ISHA).none { existingAlarm(it) != null },
        )
    }

    @Test
    fun anArmedAlarmFiresAndReachesTheShade() {
        notificationManager.cancelAll()
        val notificationId = (profile.id * REQUEST_CODE_MULTIPLIER + PRAYER_INDEX_DHUHR).toInt()
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PROFILE_ID, profile.id)
            putExtra(EXTRA_PRAYER_INDEX, PRAYER_INDEX_DHUHR)
            putExtra(EXTRA_PRAYER_NAME, "Dhuhr")
        }
        val pi = PendingIntent.getBroadcast(
            context, TEST_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3_000L, pi,
        )

        awaitTrue("no prayer notification reached the shade", timeoutMs = 30_000L) {
            notificationManager.activeNotifications.any { it.id == notificationId }
        }

        val posted = notificationManager.activeNotifications.first { it.id == notificationId }
        assertNotNull(posted.notification)
    }

    // --- helpers ----------------------------------------------------------------

    /**
     * The PendingIntent an armed alarm would have created, or null.
     *
     * `FLAG_NO_CREATE` returns non-null only if one already exists, which is exactly what
     * `AlarmScheduler.cancelForProfile` relies on to find alarms to cancel.
     */
    private fun existingAlarm(prayerIndex: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        (profile.id * REQUEST_CODE_MULTIPLIER + prayerIndex).toInt(),
        // Must carry the action the armed alarm carries: PendingIntent lookup matches on
        // Intent.filterEquals, which compares actions. A bare intent here would match nothing
        // and every assertion below would pass vacuously — which is exactly how the cancel
        // path shipped broken.
        Intent(context, PrayerAlarmReceiver::class.java).setAction(ACTION_PRAYER_ALARM),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun awaitTrue(message: String, timeoutMs: Long = 10_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(250)
        }
        throw AssertionError(message)
    }

    private companion object {
        const val TEST_REQUEST_CODE = 123_456
    }
}
