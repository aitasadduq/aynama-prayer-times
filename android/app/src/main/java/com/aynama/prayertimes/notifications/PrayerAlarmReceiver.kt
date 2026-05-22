package com.aynama.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aynama.prayertimes.AynamaApplication

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRAYER_ALARM) return
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        val rawIndex = intent.getIntExtra(EXTRA_PRAYER_INDEX, -1)
        if (profileId < 0 || rawIndex < 0) return

        val isEarlyReminder = rawIndex >= EARLY_REMINDER_BASE_INDEX
        val prayerIndex = if (isEarlyReminder) rawIndex - EARLY_REMINDER_BASE_INDEX else rawIndex
        val prayerName = PRAYER_NAMES[prayerIndex] ?: return
        val notificationId = (profileId * REQUEST_CODE_MULTIPLIER + rawIndex).toInt()

        val prefs = (context.applicationContext as AynamaApplication).notificationPreferences

        if (isEarlyReminder) {
            val minutesBefore = prefs.getPrayerEarlyReminder(profileId, prayerIndex)
            NotificationHelper.showEarlyReminderNotification(context, prayerName, minutesBefore, notificationId)
            NotificationHelper.vibrateForPrayer(context)
            return
        }

        NotificationHelper.showPrayerNotification(context, prayerName, notificationId)
        if (NotificationHelper.shouldVibrate(prefs.vibration, prefs.adhanVoice)) {
            NotificationHelper.vibrateForPrayer(context)
        }

        val serviceIntent = Intent(context, AdhanService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
