package com.aynama.prayertimes.notifications

import android.content.SharedPreferences

enum class AdhanVoice(val displayName: String, val caption: String = "") {
    MAKKAH("Makkah", "Al-Masjid Al-Haram"),
    MADINAH("Madinah", "Al-Masjid An-Nabawi"),
    EGYPTIAN("Egyptian", "Abdul Basit Abdus Samad"),
    TURKISH("Turkish", "Diyanet İşleri"),
    AL_AQSA("Al-Aqsa"),
    NONE("None", "Silent — no audio"),
}

enum class AlertTimeMode { OFFSET, FIXED }

enum class VibrationMode(val displayName: String) {
    ALWAYS("Always"),
    WITH_SOUND("With sound"),
    NEVER("Never"),
}

class NotificationPreferences(private val prefs: SharedPreferences) {

    // -1L means "use first profile by sortOrder"
    var notificationProfileId: Long
        get() = prefs.getLong(KEY_NOTIFICATION_PROFILE, -1L)
        set(value) = prefs.edit().putLong(KEY_NOTIFICATION_PROFILE, value).apply()

    // Global (not profile-scoped)
    var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER, true)
        set(value) = prefs.edit().putBoolean(KEY_MASTER, value).apply()

    var imsakEnabled: Boolean
        get() = prefs.getBoolean(KEY_IMSAK, true)
        set(value) = prefs.edit().putBoolean(KEY_IMSAK, value).apply()

    var adhanVoice: AdhanVoice
        get() = AdhanVoice.entries.find { it.name == prefs.getString(KEY_ADHAN_VOICE, null) }
            ?: AdhanVoice.MAKKAH
        set(value) = prefs.edit().putString(KEY_ADHAN_VOICE, value.name).apply()

    var vibration: VibrationMode
        get() = VibrationMode.entries.find { it.name == prefs.getString(KEY_VIBRATION, null) }
            ?: VibrationMode.WITH_SOUND
        set(value) = prefs.edit().putString(KEY_VIBRATION, value.name).apply()

    // Per-prayer, profile-scoped
    fun isPrayerEnabled(profileId: Long, prayerIndex: Int): Boolean =
        prefs.getBoolean(prayerKey(profileId, prayerIndex), true)

    fun setPrayerEnabled(profileId: Long, prayerIndex: Int, enabled: Boolean) =
        prefs.edit().putBoolean(prayerKey(profileId, prayerIndex), enabled).apply()

    fun getPrayerOffset(profileId: Long, prayerIndex: Int): Int =
        prefs.getInt(offsetKey(profileId, prayerIndex), 0)

    fun setPrayerOffset(profileId: Long, prayerIndex: Int, minutes: Int) =
        prefs.edit().putInt(offsetKey(profileId, prayerIndex), minutes).apply()

    fun getPrayerEarlyReminder(profileId: Long, prayerIndex: Int): Int =
        prefs.getInt(earlyReminderKey(profileId, prayerIndex), 0)

    fun setPrayerEarlyReminder(profileId: Long, prayerIndex: Int, minutes: Int) =
        prefs.edit().putInt(earlyReminderKey(profileId, prayerIndex), minutes).apply()

    fun getAlertMode(profileId: Long, prayerIndex: Int): AlertTimeMode {
        val name = prefs.getString(alertModeKey(profileId, prayerIndex), null)
        return AlertTimeMode.entries.find { it.name == name } ?: AlertTimeMode.OFFSET
    }

    fun setAlertMode(profileId: Long, prayerIndex: Int, mode: AlertTimeMode) =
        prefs.edit().putString(alertModeKey(profileId, prayerIndex), mode.name).apply()

    fun getFixedTimeMinutes(profileId: Long, prayerIndex: Int): Int =
        prefs.getInt(fixedTimeKey(profileId, prayerIndex), -1)

    fun setFixedTimeMinutes(profileId: Long, prayerIndex: Int, minutesOfDay: Int) =
        prefs.edit().putInt(fixedTimeKey(profileId, prayerIndex), minutesOfDay).apply()

    private fun prayerKey(profileId: Long, index: Int): String = "notif_prayer_${profileId}_$index"
    private fun offsetKey(profileId: Long, index: Int): String = "notif_offset_${profileId}_$index"
    private fun earlyReminderKey(profileId: Long, index: Int): String = "notif_early_${profileId}_$index"
    private fun alertModeKey(profileId: Long, index: Int): String = "notif_alert_mode_${profileId}_$index"
    private fun fixedTimeKey(profileId: Long, index: Int): String = "notif_fixed_time_${profileId}_$index"

    companion object {
        private const val KEY_NOTIFICATION_PROFILE = "notif_profile_id"
        private const val KEY_MASTER = "notif_master_enabled"
        private const val KEY_IMSAK = "notif_imsak_enabled"
        private const val KEY_ADHAN_VOICE = "notif_adhan_voice"
        private const val KEY_VIBRATION = "notif_vibration"
    }
}
