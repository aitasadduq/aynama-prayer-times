package com.aynama.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.widgets.updateAllPrayerWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Also handles ACTION_TIME_CHANGED: the widget countdown is a Chronometer anchored
// to elapsedRealtime, so it keeps ticking at its old rate when the wall clock jumps
// (NTP correction, DST, manual change) and drifts by exactly the jump until the next
// prayer boundary. Recomputing on the spot re-anchors it.
class TimezoneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_TIMEZONE_CHANGED && action != Intent.ACTION_TIME_CHANGED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as AynamaApplication
                val profiles = app.profileRepository.observeAll().first()
                AlarmScheduler.scheduleAll(context, profiles)
                updateAllPrayerWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
