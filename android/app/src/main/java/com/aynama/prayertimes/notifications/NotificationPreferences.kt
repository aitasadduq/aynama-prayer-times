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

enum class VibrationMode(val displayName: String) {
    ALWAYS("Always"),
    WITH_SOUND("With sound"),
    NEVER("Never"),
}

class NotificationPreferences(private val prefs: SharedPreferences) {

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

    fun isPrayerEnabled(prayerIndex: Int): Boolean =
        prefs.getBoolean(prayerKey(prayerIndex), true)

    fun setPrayerEnabled(prayerIndex: Int, enabled: Boolean) =
        prefs.edit().putBoolean(prayerKey(prayerIndex), enabled).apply()

    private fun prayerKey(index: Int): String = when (index) {
        PRAYER_INDEX_FAJR -> KEY_FAJR
        PRAYER_INDEX_DHUHR -> KEY_DHUHR
        PRAYER_INDEX_ASR -> KEY_ASR
        PRAYER_INDEX_MAGHRIB -> KEY_MAGHRIB
        PRAYER_INDEX_ISHA -> KEY_ISHA
        else -> "notif_prayer_$index"
    }

    companion object {
        private const val KEY_MASTER = "notif_master_enabled"
        private const val KEY_FAJR = "notif_fajr_enabled"
        private const val KEY_DHUHR = "notif_dhuhr_enabled"
        private const val KEY_ASR = "notif_asr_enabled"
        private const val KEY_MAGHRIB = "notif_maghrib_enabled"
        private const val KEY_ISHA = "notif_isha_enabled"
        private const val KEY_IMSAK = "notif_imsak_enabled"
        private const val KEY_ADHAN_VOICE = "notif_adhan_voice"
        private const val KEY_VIBRATION = "notif_vibration"
    }
}
