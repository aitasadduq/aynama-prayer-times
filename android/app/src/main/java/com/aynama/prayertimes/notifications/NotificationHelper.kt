package com.aynama.prayertimes.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aynama.prayertimes.R

object NotificationHelper {

    const val CHANNEL_PRAYER_TIMES = "prayer_times"
    const val CHANNEL_ADHAN_SERVICE = "adhan_service"
    const val ADHAN_SERVICE_NOTIF_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PRAYER_TIMES,
                "Prayer Times",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Prayer time alerts"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ADHAN_SERVICE,
                "Adhan",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Adhan playback service"
                setSound(null, null)
            }
        )
    }

    fun showPrayerNotification(context: Context, prayerName: String, notificationId: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, CHANNEL_PRAYER_TIMES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(prayerName)
            .setContentText("It is time for $prayerName prayer")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId, notification)
    }

    fun buildAdhanServiceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ADHAN_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Adhan")
            .setContentText("Playing…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
