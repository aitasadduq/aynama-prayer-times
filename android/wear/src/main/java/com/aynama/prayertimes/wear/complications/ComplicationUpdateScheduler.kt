package com.aynama.prayertimes.wear.complications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import java.time.Instant

const val ACTION_COMPLICATION_REFRESH =
    "com.aynama.prayertimes.wear.COMPLICATION_REFRESH"

private const val REQUEST_CODE = 61_000
private const val TAG = "ComplicationSched"

// Same reason as the phone widget's: the complication's own text ticks in the watch face
// process, so a refresh landing a millisecond early recomputes the state it already shows.
private const val GUARD_MS = 2_000L

/**
 * When the prayer complication is refreshed.
 *
 * This is architecture-design.md **Reviewer Concern #5** — WearOS has no timeline API like
 * watchOS, so a data source is asked for one value at a time and must say when to ask again.
 *
 * The answer is not a periodic refresh. The content changes when the *prayer state* changes —
 * a prayer starting, or its 30-minute count-up window closing — which does not fall on any
 * regular interval. One exact alarm at [nextChangeAt][PrayerComplicationData.nextChangeAt],
 * re-armed each time the data source is asked, tracks it exactly and wakes the watch far less
 * often than a 15-minute poll would.
 *
 * Between those points nothing needs to run: the text is a
 * [androidx.wear.watchface.complications.data.TimeDifferenceComplicationText], which the
 * system ticks on its own.
 */
object ComplicationUpdateScheduler {

    fun arm(context: Context, at: Instant?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (at == null) {
            pendingIntent(context, PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            return
        }
        val pi = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val trigger = at.toEpochMilli() + GUARD_MS
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            // A watch that will not grant exact alarms still gets a complication; it just
            // updates a little late. Better than a data source that crashes the watch face.
            Log.w(TAG, "falling back to an inexact complication refresh", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    /** Ask the system to re-query the data source now. */
    fun requestUpdateNow(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, PrayerComplicationService::class.java))
            .requestUpdateAll()
    }

    private fun pendingIntent(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ComplicationRefreshReceiver::class.java)
            .setAction(ACTION_COMPLICATION_REFRESH),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )
}

class ComplicationRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLICATION_REFRESH) return
        // Asking for an update re-enters the data source, which arms the next alarm itself —
        // so the chain continues without this receiver knowing anything about prayer times.
        ComplicationUpdateScheduler.requestUpdateNow(context)
    }
}
