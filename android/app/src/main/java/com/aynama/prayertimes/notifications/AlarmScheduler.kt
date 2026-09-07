package com.aynama.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aynama.prayertimes.widgets.PrayerWidgetScheduler
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.timeline.prayerDisplayName
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val ACTION_PRAYER_ALARM = "com.aynama.prayertimes.PRAYER_ALARM"
const val ACTION_MIDNIGHT_RESCHEDULE = "com.aynama.prayertimes.MIDNIGHT_RESCHEDULE"
const val EXTRA_PROFILE_ID = "profile_id"
const val EXTRA_PRAYER_INDEX = "prayer_index"

// Resolved at scheduling time, when the alarm's calendar day is known. The receiver has only
// a request code, and re-deriving the day there would be wrong for an alarm that fired late
// across midnight. Alarms armed by an older build carry no extra; the receiver falls back.
const val EXTRA_PRAYER_NAME = "prayer_name"

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
private const val TAG = "AlarmScheduler"

/**
 * Canonical, day-independent names, for the notification *settings* screen — a row there
 * governs all seven days, so naming it after today would misdescribe the toggle.
 *
 * A fired notification names the prayer as it falls on its own day: see [prayerNameOn].
 */
val PRAYER_NAMES = mapOf(
    PRAYER_INDEX_FAJR to "Fajr",
    PRAYER_INDEX_DHUHR to "Dhuhr",
    PRAYER_INDEX_ASR to "Asr",
    PRAYER_INDEX_MAGHRIB to "Maghrib",
    PRAYER_INDEX_ISHA to "Isha",
    PRAYER_INDEX_IMSAK to "Imsak",
)

/** The name an alarm for [prayerIndex] on [date] should announce — "Jumuah" for a Friday Dhuhr. */
fun prayerNameOn(prayerIndex: Int, date: LocalDate): String = when (prayerIndex) {
    PRAYER_INDEX_FAJR -> prayerDisplayName(Prayer.FAJR, date)
    PRAYER_INDEX_DHUHR -> prayerDisplayName(Prayer.DHUHR, date)
    PRAYER_INDEX_ASR -> prayerDisplayName(Prayer.ASR, date)
    PRAYER_INDEX_MAGHRIB -> prayerDisplayName(Prayer.MAGHRIB, date)
    PRAYER_INDEX_ISHA -> prayerDisplayName(Prayer.ISHA, date)
    else -> PRAYER_NAMES[prayerIndex] ?: ""
}

data class ScheduledAlarm(
    val requestCode: Int,
    val triggerEpochMs: Long,
    val prayerName: String,
    val isEarlyReminder: Boolean = false,
)

// Pure function — tested without Android runtime.
// The scheduling day must be resolved in the PROFILE's zone, not the device's.
// buildAlarmSchedule and buildWidgetUpdateSchedule both resolve this date against
// profile.effectiveZoneId(), so a device-zone date pairs a wall time with the wrong
// calendar day whenever the two zones straddle midnight — every alarm lands a day off.
internal fun schedulingDate(profile: Profile, instant: Instant = Instant.now()): LocalDate =
    instant.atZone(profile.effectiveZoneId()).toLocalDate()

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

    /**
     * Arm every alarm the current profile set needs.
     *
     * A profile whose times cannot be computed (see [PrayerTimesUnavailableException]) is logged
     * and skipped rather than allowed to propagate. This runs from `Application.onCreate`,
     * `MainActivity.onResume`, and two broadcast receivers, all on scopes with no exception
     * handler — an escaping throw there kills the process, and because onResume reschedules on
     * every launch it would relaunch straight into the same crash. One unsupported profile must
     * cost that profile its alarms, nothing more.
     */
    suspend fun scheduleAll(context: Context, profiles: List<Profile>) {
        val notifPrefs = NotificationPreferences(
            context.getSharedPreferences("aynama_prefs", android.content.Context.MODE_PRIVATE)
        )
        // Cancel all profiles first, then schedule only the notification profile
        profiles.forEach { cancelForProfile(context, it.id) }
        // Widget rollovers are scheduled separately, per profile that a placed widget is
        // actually bound to — a widget can render a profile that never gets notifications.
        runCatching { PrayerWidgetScheduler.scheduleForBoundProfiles(context, profiles) }
            .onFailure { Log.w(TAG, "widget rollover scheduling failed", it) }
        val profile = resolveNotificationProfile(notifPrefs.notificationProfileId, profiles)
        if (profile != null) {
            runCatching { scheduleForProfile(context, profile, schedulingDate(profile), notifPrefs) }
                .onFailure { Log.w(TAG, "no alarms armed for profile ${profile.id} (${profile.name})", it) }
        }
        // The live notification rides the same reschedule points as everything else: app
        // start and resume, boot, timezone change, prayer rollover, settings change.
        runCatching { LiveNotificationScheduler.refreshAndArm(context) }
            .onFailure { Log.w(TAG, "live notification refresh failed", it) }
        scheduleMidnightReschedule(context)
    }

    // Private on purpose: this arms notification alarms only. Callers that reach for it
    // instead of scheduleAll silently skip widget rollovers, which is exactly how widgets
    // bound to a non-notification profile ended up with no alarms at their own boundaries.
    private fun scheduleForProfile(
        context: Context,
        profile: Profile,
        date: LocalDate = schedulingDate(profile),
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
            val requestCode = (profileId * REQUEST_CODE_MULTIPLIER + index).toInt()
            val pi = PendingIntent.getBroadcast(
                context, requestCode, alarmIntent(context),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    /**
     * The intent identity every prayer alarm shares.
     *
     * PendingIntent lookup matches on [Intent.filterEquals], which compares the action and
     * ignores extras. Building the cancel-side intent separately let it drift: it carried no
     * action while the armed one did, so FLAG_NO_CREATE never found anything and
     * cancelForProfile silently cancelled nothing — prayer alarms kept firing after the user
     * turned notifications off. One builder, so the two sides cannot disagree again.
     */
    private fun alarmIntent(context: Context): Intent =
        Intent(context, PrayerAlarmReceiver::class.java).setAction(ACTION_PRAYER_ALARM)

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
        val intent = alarmIntent(context).apply {
            putExtra(EXTRA_PROFILE_ID, profileIdFromRequestCode(alarm.requestCode))
            putExtra(EXTRA_PRAYER_INDEX, prayerIndexFromRequestCode(alarm.requestCode))
            putExtra(EXTRA_PRAYER_NAME, alarm.prayerName)
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
                prayerName = prayerNameOn(index, date),
            ))
            val earlyMinutes = earlyReminderMinutes(index)
            if (earlyMinutes > 0) {
                val earlyTime = effectiveTime.minusMinutes(earlyMinutes.toLong())
                add(ScheduledAlarm(
                    requestCode = (profile.id * REQUEST_CODE_MULTIPLIER + index + EARLY_REMINDER_BASE_INDEX).toInt(),
                    triggerEpochMs = localTimeToEpochMs(earlyTime, date, zone),
                    prayerName = prayerNameOn(index, date),
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
