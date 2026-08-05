package com.aynama.prayertimes.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val ACTION_PRAYER_WIDGET_UPDATE = "com.aynama.prayertimes.widgets.PRAYER_WIDGET_UPDATE"

internal const val WIDGET_UPDATE_REQUEST_CODE_BASE = 70_000

// Slots 0-5 are one day's events, Sunrise included: buildPrayerWidgetState picks the
// soonest upcoming event, so after Fajr the widget counts down to Sunrise and needs an
// alarm there too. Slot 6 is tomorrow's Fajr, so a rollover is always pending —
// including the stretch between Isha and midnight.
internal const val WIDGET_UPDATE_SLOT_COUNT = 7

// Each profile a placed widget resolves to gets its own block of slots. Widgets carry
// per-instance profiles, so one chain built from the notification profile would leave
// every widget bound to a different profile with no alarm at its own boundaries.
internal const val WIDGET_UPDATE_MAX_PROFILES = 16

// The countdown is a Chronometer ticking in the launcher process: nothing stops it at
// zero, so a rollover that lands even a millisecond early recomputes the same prayer and
// the countdown runs negative until the next update. Firing a couple of seconds late
// costs nothing and makes the recomputation unambiguous.
internal const val WIDGET_UPDATE_GUARD_MS = 2_000L

data class ScheduledWidgetUpdate(
    val requestCode: Int,
    val triggerEpochMs: Long,
)

object PrayerWidgetScheduler {

    private val adhan = AdhanWrapper()

    /**
     * Arm a rollover chain for every profile a placed widget resolves to.
     *
     * Each widget stores its own profile, so scheduling only the notification profile
     * leaves every other widget without an alarm at its own prayer boundaries — its
     * Chronometer ticks past zero and grows negative until something else happens to
     * refresh it. Each profile's chain is computed in that profile's own zone.
     */
    suspend fun scheduleForBoundProfiles(
        context: Context,
        profiles: List<Profile>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        cancelAll(context)
        boundWidgetProfiles(context, profiles)
            .take(WIDGET_UPDATE_MAX_PROFILES)
            .forEachIndexed { slot, profile -> arm(context, slot, profile, nowEpochMs) }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        repeat(WIDGET_UPDATE_MAX_PROFILES * WIDGET_UPDATE_SLOT_COUNT) { offset ->
            val pi = PendingIntent.getBroadcast(
                context,
                WIDGET_UPDATE_REQUEST_CODE_BASE + offset,
                updateIntent(context),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun arm(context: Context, profileSlot: Int, profile: Profile, nowEpochMs: Long) {
        val zone = profile.effectiveZoneId()
        val date = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val updates = buildWidgetUpdateSchedule(
            profile = profile,
            date = date,
            times = adhan.timesFor(profile, date),
            tomorrowTimes = adhan.timesFor(profile, date.plusDays(1)),
            zone = zone,
            nowEpochMs = nowEpochMs,
            profileSlot = profileSlot,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        updates.forEach { update ->
            val pi = PendingIntent.getBroadcast(
                context,
                update.requestCode,
                updateIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, update.triggerEpochMs, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, update.triggerEpochMs, pi)
            }
        }
    }

    private fun updateIntent(context: Context): Intent =
        Intent(context, PrayerWidgetUpdateReceiver::class.java).setAction(ACTION_PRAYER_WIDGET_UPDATE)

    private fun AdhanWrapper.timesFor(profile: Profile, date: LocalDate): PrayerTimesResult =
        getPrayerTimes(
            latitude = profile.latitude,
            longitude = profile.longitude,
            date = date,
            timezone = profile.effectiveZoneId(),
            method = profile.calculationMethod,
        )
}

fun buildWidgetUpdateSchedule(
    profile: Profile,
    date: LocalDate,
    times: PrayerTimesResult,
    tomorrowTimes: PrayerTimesResult,
    zone: ZoneId,
    nowEpochMs: Long,
    profileSlot: Int = 0,
): List<ScheduledWidgetUpdate> {
    val asr = if (profile.asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val moments = listOf(
        date to times.fajr,
        date to times.sunrise,
        date to times.dhuhr,
        date to asr,
        date to times.maghrib,
        (if (times.isha < times.fajr) date.plusDays(1) else date) to times.isha,
        date.plusDays(1) to tomorrowTimes.fajr,
    )
    val base = WIDGET_UPDATE_REQUEST_CODE_BASE + profileSlot * WIDGET_UPDATE_SLOT_COUNT
    return moments.mapIndexedNotNull { index, (prayerDate, time) ->
        val trigger = prayerDate.atTime(time).atZone(zone).toInstant().toEpochMilli() + WIDGET_UPDATE_GUARD_MS
        if (trigger <= nowEpochMs) null
        else ScheduledWidgetUpdate(base + index, trigger)
    }
}
