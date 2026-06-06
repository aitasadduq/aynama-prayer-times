package com.aynama.prayertimes.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.notifications.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrayerWidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRAYER_WIDGET_UPDATE) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PrayerGlanceWidget().updateAll(context)
                val app = context.applicationContext as AynamaApplication
                val profiles = app.profileRepository.observeAll().first()
                AlarmScheduler.scheduleAll(context, profiles)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
