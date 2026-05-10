package com.aynama.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aynama.prayertimes.AynamaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimezoneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as AynamaApplication
                val profiles = app.profileRepository.observeAll().first()
                AlarmScheduler.scheduleAll(context, profiles)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
