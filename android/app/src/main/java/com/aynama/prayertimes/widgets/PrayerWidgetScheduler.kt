package com.aynama.prayertimes.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import java.time.LocalDate
import java.time.ZoneId

const val ACTION_PRAYER_WIDGET_UPDATE = "com.aynama.prayertimes.widgets.PRAYER_WIDGET_UPDATE"

private const val WIDGET_UPDATE_REQUEST_CODE_BASE = 70_000

data class ScheduledWidgetUpdate(
    val requestCode: Int,
    val triggerEpochMs: Long,
)

object PrayerWidgetScheduler {
    fun scheduleForProfile(
        context: Context,
        profile: Profile,
        date: LocalDate,
        times: PrayerTimesResult,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        cancel(context)
        val updates = buildWidgetUpdateSchedule(
            profile = profile,
            date = date,
            times = times,
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
        repeat(5) { index ->
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
}

fun buildWidgetUpdateSchedule(
    profile: Profile,
    date: LocalDate,
    times: PrayerTimesResult,
    zone: ZoneId,
    nowEpochMs: Long,
): List<ScheduledWidgetUpdate> {
    val asr = if (profile.asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val prayerTimes = listOf(
        times.fajr,
        times.dhuhr,
        asr,
        times.maghrib,
        times.isha,
    )
    return prayerTimes.mapIndexedNotNull { index, time ->
        val prayerDate = if (index == 4 && time < times.fajr) date.plusDays(1) else date
        val trigger = prayerDate.atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (trigger <= nowEpochMs) null
        else ScheduledWidgetUpdate(WIDGET_UPDATE_REQUEST_CODE_BASE + index, trigger)
    }
}
