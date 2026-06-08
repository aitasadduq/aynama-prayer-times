package com.aynama.prayertimes.widgets

import android.content.SharedPreferences

object WidgetProfilePreferences {
    private fun key(appWidgetId: Int) = "widget_profile_id_$appWidgetId"

    /** Returns the stored profileId, or -1 if none set for this widget instance. */
    fun getProfileId(prefs: SharedPreferences, appWidgetId: Int): Long =
        prefs.getLong(key(appWidgetId), -1L)

    fun setProfileId(prefs: SharedPreferences, appWidgetId: Int, profileId: Long) {
        prefs.edit().putLong(key(appWidgetId), profileId).apply()
    }

    fun clear(prefs: SharedPreferences, appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
    }
}
