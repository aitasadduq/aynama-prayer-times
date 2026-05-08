package com.aynama.prayertimes.shared

/**
 * Calibration accuracy of the device's fused rotation sensor.
 *
 * Mirrors `android.hardware.SensorManager.SENSOR_STATUS_*` constants but lives
 * outside the Android-SDK dependency surface so it can be unit-tested as JVM.
 */
enum class SensorAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE;

    companion object {
        // Constants below match android.hardware.SensorManager.SENSOR_STATUS_*.
        // Hardcoded here so the mapper is JVM-testable without the Android SDK.
        const val ANDROID_HIGH = 3
        const val ANDROID_MEDIUM = 2
        const val ANDROID_LOW = 1
        const val ANDROID_UNRELIABLE = 0

        /**
         * Map an Android `SENSOR_STATUS_*` int to a [SensorAccuracy]. Anything
         * outside the documented range maps to [UNRELIABLE] (covers
         * `SENSOR_STATUS_NO_CONTACT = -1` and any future additions).
         */
        fun fromAndroid(status: Int): SensorAccuracy = when (status) {
            ANDROID_HIGH -> HIGH
            ANDROID_MEDIUM -> MEDIUM
            ANDROID_LOW -> LOW
            else -> UNRELIABLE
        }
    }
}
