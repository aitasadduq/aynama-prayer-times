package com.aynama.prayertimes.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AdhanService : Service() {

    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildAdhanServiceNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NotificationHelper.ADHAN_SERVICE_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.ADHAN_SERVICE_NOTIF_ID, notification)
        }
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, soundUri)
        ringtone?.play()
        handler.postDelayed(stopRunnable, AUTO_STOP_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        ringtone?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val AUTO_STOP_MS = 30_000L
    }
}
