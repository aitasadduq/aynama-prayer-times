package com.aynama.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRAYER_ALARM) return
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        val prayerIndex = intent.getIntExtra(EXTRA_PRAYER_INDEX, -1)
        if (profileId < 0 || prayerIndex < 0) return

        val prayerName = PRAYER_NAMES[prayerIndex] ?: return
        val notificationId = (profileId * 10 + prayerIndex).toInt()
        NotificationHelper.showPrayerNotification(context, prayerName, notificationId)

        val serviceIntent = Intent(context, AdhanService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
