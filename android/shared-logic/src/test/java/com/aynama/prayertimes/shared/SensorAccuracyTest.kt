package com.aynama.prayertimes.shared

import org.junit.Test

class SensorAccuracyTest {

    @Test fun mapsHigh() {
        assert(SensorAccuracy.fromAndroid(SensorAccuracy.ANDROID_HIGH) == SensorAccuracy.HIGH)
    }

    @Test fun mapsMedium() {
        assert(SensorAccuracy.fromAndroid(SensorAccuracy.ANDROID_MEDIUM) == SensorAccuracy.MEDIUM)
    }

    @Test fun mapsLow() {
        assert(SensorAccuracy.fromAndroid(SensorAccuracy.ANDROID_LOW) == SensorAccuracy.LOW)
    }

    @Test fun mapsUnreliable() {
        assert(SensorAccuracy.fromAndroid(SensorAccuracy.ANDROID_UNRELIABLE) == SensorAccuracy.UNRELIABLE)
    }

    @Test fun mapsNoContactToUnreliable() {
        // SENSOR_STATUS_NO_CONTACT = -1 — not one of the documented HIGH/MED/LOW values;
        // we treat it as UNRELIABLE so the calibration banner shows.
        assert(SensorAccuracy.fromAndroid(-1) == SensorAccuracy.UNRELIABLE)
    }

    @Test fun mapsUnknownPositiveToUnreliable() {
        // Future or unrecognized values fall through to UNRELIABLE.
        assert(SensorAccuracy.fromAndroid(99) == SensorAccuracy.UNRELIABLE)
    }

    @Test fun androidConstantsMatchSdk() {
        // Document-by-test: these must match android.hardware.SensorManager.SENSOR_STATUS_*
        // constants exactly. If Android ever changes them, the production mapping in
        // QiblaViewModel.onAccuracyChanged would silently break — this test pins the values.
        assert(SensorAccuracy.ANDROID_HIGH == 3)
        assert(SensorAccuracy.ANDROID_MEDIUM == 2)
        assert(SensorAccuracy.ANDROID_LOW == 1)
        assert(SensorAccuracy.ANDROID_UNRELIABLE == 0)
    }
}
