package com.aynama.prayertimes.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.nextTransition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val ACTION_PRAYER_WIDGET_UPDATE = "com.aynama.prayertimes.widgets.PRAYER_WIDGET_UPDATE"

internal const val WIDGET_UPDATE_REQUEST_CODE_BASE = 70_000

// A slot is one *state transition* of the unified countdown (DESIGN.md §19), not just a
// prayer boundary: the widget also has to be refreshed 30 minutes after each prayer, when
// it stops counting up and starts counting down to the next one. A day has six events plus
// five count-up flips (Sunrise has none), so twelve slots covers a full day of transitions
// with one still pending — including the stretch between Isha and midnight.
internal const val WIDGET_UPDATE_SLOT_COUNT = 12

// Each profile a placed widget resolves to gets its own block of slots. Widgets carry
// per-instance profiles, so one chain built from the notification profile would leave
// every widget bound to a different profile with no alarm at its own boundaries.
internal const val WIDGET_UPDATE_MAX_PROFILES = 16

// The countdown is a Chronometer ticking in the launcher process: nothing stops it at
// zero, so a rollover that lands even a millisecond early recomputes the same prayer and
// the countdown runs negative until the next update. Firing a couple of seconds late
// costs nothing and makes the recomputation unambiguous.
internal const val WIDGET_UPDATE_GUARD_MS = 2_000L

private const val TAG = "PrayerWidgetSched"

data class ScheduledWidgetUpdate(
    val requestCode: Int,
    val triggerEpochMs: Long,
)

object PrayerWidgetScheduler {

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
            .forEachIndexed { slot, profile ->
                // Slots are independent chains. A profile whose times cannot be computed loses
                // its own rollovers; every other widget must keep updating, and this must never
                // throw out of here — the callers run on scopes that would take the process down.
                runCatching { arm(context, slot, profile, nowEpochMs) }
                    .onFailure { Log.w(TAG, "no widget rollovers for profile ${profile.id} (${profile.name})", it) }
            }
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
            days = profileDays(profile, date),
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

}

/**
 * The next [WIDGET_UPDATE_SLOT_COUNT] instants at which the widget's countdown changes state.
 *
 * Walks [nextTransition] forward rather than listing prayer boundaries, so the +30 minute flip
 * from counting up to counting down gets an alarm too. Each trigger is nudged past the exact
 * instant by [WIDGET_UPDATE_GUARD_MS]: the countdown is a Chronometer ticking in the launcher
 * process, and a rollover that lands even a millisecond early recomputes the *same* state, so
 * the widget would sit on a stale value until something else refreshed it.
 */
fun buildWidgetUpdateSchedule(
    profile: Profile,
    days: Map<LocalDate, PrayerTimesResult>,
    zone: ZoneId,
    nowEpochMs: Long,
    profileSlot: Int = 0,
): List<ScheduledWidgetUpdate> {
    val timeline = buildTimeline(days, profile.asrMadhab, zone)
    val base = WIDGET_UPDATE_REQUEST_CODE_BASE + profileSlot * WIDGET_UPDATE_SLOT_COUNT
    var cursor = Instant.ofEpochMilli(nowEpochMs)
    return buildList {
        while (size < WIDGET_UPDATE_SLOT_COUNT) {
            val transition = nextTransition(timeline, cursor) ?: break
            add(ScheduledWidgetUpdate(base + size, transition.toEpochMilli() + WIDGET_UPDATE_GUARD_MS))
            cursor = transition
        }
    }
}
