package com.aynama.prayertimes.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    // Re-push all widgets. Chronometer self-ticks between calls, so this is only needed
    // when the next prayer / profile / date changes (prayer-alarm fire, boot, app resume).
    suspend fun updateAll(context: Context) {
        AynamaWidgetSmall().updateAll(context)
        AynamaWidgetMedium().updateAll(context)
        AynamaWidgetLarge().updateAll(context)
    }
}
