package com.aynama.prayertimes.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.aynama.prayertimes.R

object NotificationHelper {

    // v2 bump escapes the v1 channel's immutable enableVibration(true) + default sound.
    // Vibration and sound are now controlled at post-time per user preference.
    const val CHANNEL_PRAYER_TIMES = "prayer_times_v2"
    const val CHANNEL_ADHAN_SERVICE = "adhan_service"
    const val ADHAN_SERVICE_NOTIF_ID = 1001
    private const val LEGACY_CHANNEL_PRAYER_TIMES = "prayer_times"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(LEGACY_CHANNEL_PRAYER_TIMES)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PRAYER_TIMES,
                "Prayer Times",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Prayer time alerts"
                setSound(null, null)
                enableVibration(false)
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

    fun shouldVibrate(mode: VibrationMode, voice: AdhanVoice): Boolean = when (mode) {
        VibrationMode.ALWAYS -> true
        VibrationMode.WITH_SOUND -> voice != AdhanVoice.NONE
        VibrationMode.NEVER -> false
    }

    fun vibrateForPrayer(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
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
