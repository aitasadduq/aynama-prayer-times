package com.aynama.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val ACTION_PRAYER_ALARM = "com.aynama.prayertimes.PRAYER_ALARM"
const val ACTION_MIDNIGHT_RESCHEDULE = "com.aynama.prayertimes.MIDNIGHT_RESCHEDULE"
const val EXTRA_PROFILE_ID = "profile_id"
const val EXTRA_PRAYER_INDEX = "prayer_index"

private const val PRAYER_INDEX_FAJR = 0
private const val PRAYER_INDEX_DHUHR = 1
private const val PRAYER_INDEX_ASR = 2
private const val PRAYER_INDEX_MAGHRIB = 3
private const val PRAYER_INDEX_ISHA = 4
private const val PRAYER_INDEX_IMSAK = 5

private const val MIDNIGHT_REQUEST_CODE = 9999

val PRAYER_NAMES = mapOf(
    PRAYER_INDEX_FAJR to "Fajr",
    PRAYER_INDEX_DHUHR to "Dhuhr",
    PRAYER_INDEX_ASR to "Asr",
    PRAYER_INDEX_MAGHRIB to "Maghrib",
    PRAYER_INDEX_ISHA to "Isha",
    PRAYER_INDEX_IMSAK to "Imsak",
)

data class ScheduledAlarm(
    val requestCode: Int,
    val triggerEpochMs: Long,
    val prayerName: String,
)

object AlarmScheduler {

    private val adhan = AdhanWrapper()

    fun scheduleAll(context: Context, profiles: List<Profile>) {
        val date = LocalDate.now()
        profiles.forEach { scheduleForProfile(context, it, date) }
        scheduleMidnightReschedule(context)
    }

    fun scheduleForProfile(context: Context, profile: Profile, date: LocalDate = LocalDate.now()) {
        cancelForProfile(context, profile.id)
        val times = adhan.getPrayerTimes(
            latitude = profile.latitude,
            longitude = profile.longitude,
            date = date,
            timezone = ZoneId.systemDefault(),
            method = profile.calculationMethod,
        )
        val isRamadan = RamadanDetector.isRamadan(date)
        val alarms = buildAlarmSchedule(profile, date, isRamadan, times)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            if (alarm.triggerEpochMs <= now) continue
            submitAlarm(context, alarmManager, alarm)
        }
    }

    fun cancelForProfile(context: Context, profileId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (index in 0..9) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            val requestCode = (profileId * 10 + index).toInt()
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    fun scheduleMidnightReschedule(context: Context) {
        val midnight = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val intent = Intent(context, BootReceiver::class.java).setAction(ACTION_MIDNIGHT_RESCHEDULE)
        val pi = PendingIntent.getBroadcast(
            context, MIDNIGHT_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, pi)
        }
    }

    private fun submitAlarm(context: Context, alarmManager: AlarmManager, alarm: ScheduledAlarm) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PROFILE_ID, profileIdFromRequestCode(alarm.requestCode))
            putExtra(EXTRA_PRAYER_INDEX, prayerIndexFromRequestCode(alarm.requestCode))
        }
        val pi = PendingIntent.getBroadcast(
            context, alarm.requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerEpochMs, pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerEpochMs, pi)
        }
    }

    private fun profileIdFromRequestCode(code: Int): Long = (code / 10).toLong()
    private fun prayerIndexFromRequestCode(code: Int): Int = code % 10
}

// Pure function — tested without Android runtime
fun buildAlarmSchedule(
    profile: Profile,
    date: LocalDate,
    isRamadan: Boolean,
    times: PrayerTimesResult,
): List<ScheduledAlarm> {
    val asr = if (profile.asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val prayers = listOf(
        PRAYER_INDEX_FAJR to times.fajr,
        PRAYER_INDEX_DHUHR to times.dhuhr,
        PRAYER_INDEX_ASR to asr,
        PRAYER_INDEX_MAGHRIB to times.maghrib,
        PRAYER_INDEX_ISHA to times.isha,
    )
    return buildList {
        for ((index, time) in prayers) {
            add(ScheduledAlarm(
                requestCode = (profile.id * 10 + index).toInt(),
                triggerEpochMs = localTimeToEpochMs(time, date),
                prayerName = PRAYER_NAMES[index]!!,
            ))
        }
        if (isRamadan) {
            val imsak = times.fajr.minusMinutes(10)
            add(ScheduledAlarm(
                requestCode = (profile.id * 10 + PRAYER_INDEX_IMSAK).toInt(),
                triggerEpochMs = localTimeToEpochMs(imsak, date),
                prayerName = "Imsak",
            ))
        }
    }
}

internal fun localTimeToEpochMs(time: LocalTime, date: LocalDate): Long =
    date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
