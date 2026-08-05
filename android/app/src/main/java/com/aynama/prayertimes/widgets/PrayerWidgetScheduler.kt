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
import java.time.LocalDate
import java.time.ZoneId

const val ACTION_PRAYER_WIDGET_UPDATE = "com.aynama.prayertimes.widgets.PRAYER_WIDGET_UPDATE"

private const val WIDGET_UPDATE_REQUEST_CODE_BASE = 70_000

// Slots 0-5 are today's events, Sunrise included: buildPrayerWidgetState picks the
// soonest upcoming event, so after Fajr the widget counts down to Sunrise and needs
// an alarm there too. Slot 6 is tomorrow's Fajr, so a rollover is always pending —
// including the stretch between Isha and midnight.
internal const val WIDGET_UPDATE_SLOT_COUNT = 7

// The widget countdown is a Chronometer ticking in the launcher process: nothing
// stops it at zero, so a rollover that lands even a millisecond early recomputes
// the same prayer and the countdown runs negative until the next update. Firing
// a couple of seconds late costs nothing and makes the recomputation unambiguous.
internal const val WIDGET_UPDATE_GUARD_MS = 2_000L

data class ScheduledWidgetUpdate(
    val requestCode: Int,
    val triggerEpochMs: Long,
)

object PrayerWidgetScheduler {

    private val adhan = AdhanWrapper()

    fun scheduleForProfile(
        context: Context,
        profile: Profile,
        date: LocalDate,
        times: PrayerTimesResult = adhan.timesFor(profile, date),
        tomorrowTimes: PrayerTimesResult = adhan.timesFor(profile, date.plusDays(1)),
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        cancel(context)
        val updates = buildWidgetUpdateSchedule(
            profile = profile,
            date = date,
            times = times,
            tomorrowTimes = tomorrowTimes,
            zone = profile.effectiveZoneId(),
            nowEpochMs = nowEpochMs,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        updates.forEach { update ->
            val pi = PendingIntent.getBroadcast(
                context,
                update.requestCode,
                Intent(context, PrayerWidgetUpdateReceiver::class.java).setAction(ACTION_PRAYER_WIDGET_UPDATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, update.triggerEpochMs, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, update.triggerEpochMs, pi)
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        repeat(WIDGET_UPDATE_SLOT_COUNT) { index ->
            val pi = PendingIntent.getBroadcast(
                context,
                WIDGET_UPDATE_REQUEST_CODE_BASE + index,
                Intent(context, PrayerWidgetUpdateReceiver::class.java).setAction(ACTION_PRAYER_WIDGET_UPDATE),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

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
    return moments.mapIndexedNotNull { index, (prayerDate, time) ->
        val trigger = prayerDate.atTime(time).atZone(zone).toInstant().toEpochMilli() + WIDGET_UPDATE_GUARD_MS
        if (trigger <= nowEpochMs) null
        else ScheduledWidgetUpdate(WIDGET_UPDATE_REQUEST_CODE_BASE + index, trigger)
    }
}
