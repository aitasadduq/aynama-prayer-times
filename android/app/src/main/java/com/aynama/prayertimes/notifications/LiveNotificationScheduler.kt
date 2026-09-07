package com.aynama.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

const val ACTION_LIVE_NOTIFICATION_REFRESH =
    "com.aynama.prayertimes.notifications.LIVE_NOTIFICATION_REFRESH"

private const val LIVE_REQUEST_CODE = 95_000
private const val TAG = "LiveNotifScheduler"

// The alarm is nudged past the transition instant for the same reason the widget's is: the
// notification's chronometer keeps ticking in the system process, so a refresh that lands even
// a millisecond early recomputes the state it already shows and the number runs on past zero.
private const val LIVE_REFRESH_GUARD_MS = 2_000L

/**
 * Keeps the live notification honest without anything of ours running continuously.
 *
 * One exact alarm at a time, armed at the next transition of the shared countdown rule
 * (DESIGN.md §19) — the moment a prayer starts, or the moment its 30-minute count-up window
 * closes. The receiver re-arms on every fire, so the chain survives the app being killed; boot
 * and timezone changes re-enter through [AlarmScheduler.scheduleAll], which calls this.
 *
 * Deliberately not a foreground service. A service would hold a process alive all day to render
 * a number the system can tick on its own, and would need a `FOREGROUND_SERVICE_SPECIAL_USE`
 * justification it does not deserve.
 */
object LiveNotificationScheduler {

    /** Refresh the notification now and arm the next transition. Never throws. */
    suspend fun refreshAndArm(context: Context, now: Instant = Instant.now()) {
        runCatching { LivePrayerNotification.refresh(context, now) }
            .onFailure { Log.w(TAG, "live notification refresh failed", it) }
        runCatching { arm(context, LivePrayerNotification.nextRefreshAt(context, now)) }
            .onFailure { Log.w(TAG, "live notification scheduling failed", it) }
    }

    private fun arm(context: Context, at: Instant?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (at == null) {
            // Feature off, no profile, or a location with no computable times: leave nothing armed.
            pendingIntent(context, PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            return
        }
        val pi = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val trigger = at.toEpochMilli() + LIVE_REFRESH_GUARD_MS
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun pendingIntent(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        LIVE_REQUEST_CODE,
        Intent(context, LiveNotificationReceiver::class.java).setAction(ACTION_LIVE_NOTIFICATION_REFRESH),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )
}

class LiveNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LIVE_NOTIFICATION_REFRESH) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                LiveNotificationScheduler.refreshAndArm(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
