package com.aynama.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aynama.prayertimes.widgets.PrayerWidgetScheduler
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val ACTION_PRAYER_ALARM = "com.aynama.prayertimes.PRAYER_ALARM"
const val ACTION_MIDNIGHT_RESCHEDULE = "com.aynama.prayertimes.MIDNIGHT_RESCHEDULE"
const val EXTRA_PROFILE_ID = "profile_id"
const val EXTRA_PRAYER_INDEX = "prayer_index"

const val PRAYER_INDEX_FAJR = 0
const val PRAYER_INDEX_DHUHR = 1
const val PRAYER_INDEX_ASR = 2
const val PRAYER_INDEX_MAGHRIB = 3
const val PRAYER_INDEX_ISHA = 4
const val PRAYER_INDEX_IMSAK = 5

// Early reminder alarms use prayerIndex + EARLY_REMINDER_BASE_INDEX as their slot.
// Slots 0–5: regular prayers + imsak; slots 10–14: early reminders for prayers 0–4.
// Multiplier 20 avoids collisions across profiles.
const val EARLY_REMINDER_BASE_INDEX = 10
internal const val REQUEST_CODE_MULTIPLIER = 20

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
    val isEarlyReminder: Boolean = false,
)

// Pure function — tested without Android runtime.
// savedProfileId: value of NotificationPreferences.notificationProfileId (-1 = unset).
fun resolveNotificationProfile(savedProfileId: Long, profiles: List<Profile>): Profile? {
    if (profiles.isEmpty()) return null
    if (savedProfileId >= 0) {
        val match = profiles.firstOrNull { it.id == savedProfileId }
        if (match != null) return match
    }
    return profiles.minByOrNull { it.sortOrder }
}

object AlarmScheduler {

    private val adhan = AdhanWrapper()

    fun scheduleAll(context: Context, profiles: List<Profile>) {
        val notifPrefs = NotificationPreferences(
            context.getSharedPreferences("aynama_prefs", android.content.Context.MODE_PRIVATE)
        )
        // Cancel all profiles first, then schedule only the notification profile
        profiles.forEach { cancelForProfile(context, it.id) }
        PrayerWidgetScheduler.cancel(context)
        val profile = resolveNotificationProfile(notifPrefs.notificationProfileId, profiles)
        if (profile != null) {
            scheduleForProfile(context, profile, LocalDate.now(), notifPrefs)
        }
        scheduleMidnightReschedule(context)
    }

    fun scheduleForProfile(
        context: Context,
        profile: Profile,
        date: LocalDate = LocalDate.now(),
        notifPrefs: NotificationPreferences = NotificationPreferences(
            context.getSharedPreferences("aynama_prefs", android.content.Context.MODE_PRIVATE)
        ),
    ) {
        cancelForProfile(context, profile.id)
        val times = adhan.getPrayerTimes(
            latitude = profile.latitude,
            longitude = profile.longitude,
            date = date,
            timezone = profile.effectiveZoneId(),
            method = profile.calculationMethod,
        )
        PrayerWidgetScheduler.scheduleForProfile(context, profile, date, times)
        val offset = RamadanDetector.effectiveHijriOffset(
            profile.hijriOffset, profile.hijriOffsetMonthKey, date, profile.effectiveZoneId(),
        )
        val isRamadan = RamadanDetector.isRamadanWithOffset(date, offset, profile.effectiveZoneId())
        val pid = profile.id
        val alarms = buildAlarmSchedule(
            profile = profile,
            date = date,
            isRamadan = isRamadan,
            times = times,
            zone = profile.effectiveZoneId(),
            masterEnabled = notifPrefs.masterEnabled,
            prayerEnabled = { index -> notifPrefs.isPrayerEnabled(pid, index) },
            imsakEnabled = notifPrefs.imsakEnabled,
            prayerOffset = { index -> notifPrefs.getPrayerOffset(pid, index) },
            earlyReminderMinutes = { index -> notifPrefs.getPrayerEarlyReminder(pid, index) },
            alertMode = { index -> notifPrefs.getAlertMode(pid, index) },
            fixedTimeMinutes = { index -> notifPrefs.getFixedTimeMinutes(pid, index) },
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            if (alarm.triggerEpochMs <= now) continue
            submitAlarm(context, alarmManager, alarm)
        }
    }

    fun cancelForProfile(context: Context, profileId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (index in 0 until REQUEST_CODE_MULTIPLIER) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            val requestCode = (profileId * REQUEST_CODE_MULTIPLIER + index).toInt()
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

    private fun profileIdFromRequestCode(code: Int): Long = (code / REQUEST_CODE_MULTIPLIER).toLong()
    private fun prayerIndexFromRequestCode(code: Int): Int = code % REQUEST_CODE_MULTIPLIER
}

// Pure function — tested without Android runtime
fun buildAlarmSchedule(
    profile: Profile,
    date: LocalDate,
    isRamadan: Boolean,
    times: PrayerTimesResult,
    zone: ZoneId = ZoneId.systemDefault(),
    masterEnabled: Boolean = true,
    prayerEnabled: (Int) -> Boolean = { true },
    imsakEnabled: Boolean = true,
    prayerOffset: (Int) -> Int = { 0 },
    earlyReminderMinutes: (Int) -> Int = { 0 },
    alertMode: (Int) -> AlertTimeMode = { AlertTimeMode.OFFSET },
    fixedTimeMinutes: (Int) -> Int = { -1 },
): List<ScheduledAlarm> {
    if (!masterEnabled) return emptyList()
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
            if (!prayerEnabled(index)) continue
            val effectiveTime = when {
                alertMode(index) == AlertTimeMode.FIXED -> {
                    val minsOfDay = fixedTimeMinutes(index)
                    if (minsOfDay >= 0) LocalTime.of(minsOfDay / 60, minsOfDay % 60)
                    else time.plusMinutes(prayerOffset(index).toLong())
                }
                else -> time.plusMinutes(prayerOffset(index).toLong())
            }
            add(ScheduledAlarm(
                requestCode = (profile.id * REQUEST_CODE_MULTIPLIER + index).toInt(),
                triggerEpochMs = localTimeToEpochMs(effectiveTime, date, zone),
                prayerName = PRAYER_NAMES[index]!!,
            ))
            val earlyMinutes = earlyReminderMinutes(index)
            if (earlyMinutes > 0) {
                val earlyTime = effectiveTime.minusMinutes(earlyMinutes.toLong())
                add(ScheduledAlarm(
                    requestCode = (profile.id * REQUEST_CODE_MULTIPLIER + index + EARLY_REMINDER_BASE_INDEX).toInt(),
                    triggerEpochMs = localTimeToEpochMs(earlyTime, date, zone),
                    prayerName = PRAYER_NAMES[index]!!,
                    isEarlyReminder = true,
                ))
            }
        }
        if (isRamadan && imsakEnabled) {
            val imsak = times.fajr.minusMinutes(10)
            add(ScheduledAlarm(
                requestCode = (profile.id * REQUEST_CODE_MULTIPLIER + PRAYER_INDEX_IMSAK).toInt(),
                triggerEpochMs = localTimeToEpochMs(imsak, date, zone),
                prayerName = "Imsak",
            ))
        }
    }
}

internal fun localTimeToEpochMs(time: LocalTime, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atTime(time).atZone(zone).toInstant().toEpochMilli()
