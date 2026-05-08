package com.aynama.prayertimes.shared

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-JVM sensor smoothing + angle-unwrap state for the Qibla compass.
 *
 * Smooths raw azimuth (degrees, [0, 360)) with a sin/cos-space low-pass filter to
 * avoid wrap-around artifacts at the 0/360 boundary, then accumulates an unwrapped
 * azimuth so the rose can rotate continuously past ±360° without the rotation
 * animation snapping back.
 *
 * Single-threaded by design. The QiblaViewModel owns one instance and only calls
 * [update] from the sensor thread (and [reset] from the main thread when the
 * ViewModel is reset for testing). No internal locking.
 */
class QiblaSensorState(
    private val lpAlpha: Float = DEFAULT_LP_ALPHA,
) {
    private var smoothedSin = 0f
    private var smoothedCos = 1f
    private var hasFirstSample = false

    var unwrapped: Float = 0f
        private set

    var smoothed: Float = 0f
        private set

    /**
     * Apply [magneticDeclination] (degrees, signed) to the raw magnetic-north
     * azimuth, smooth it, accumulate the unwrap, and return the new smoothed
     * azimuth in [0, 360).
     */
    fun update(rawAzimuth: Float, magneticDeclination: Float = 0f): Float {
        val trueAzimuth = ((rawAzimuth + magneticDeclination) % 360f + 360f) % 360f
        val rad = Math.toRadians(trueAzimuth.toDouble())
        val s = sin(rad).toFloat()
        val c = cos(rad).toFloat()

        if (!hasFirstSample) {
            smoothedSin = s
            smoothedCos = c
        } else {
            smoothedSin = lpAlpha * s + (1f - lpAlpha) * smoothedSin
            smoothedCos = lpAlpha * c + (1f - lpAlpha) * smoothedCos
        }

        val newSmoothed = ((Math.toDegrees(
            atan2(smoothedSin.toDouble(), smoothedCos.toDouble())
        ).toFloat() + 360f) % 360f)

        if (!hasFirstSample) {
            unwrapped = newSmoothed
        } else {
            // Shortest-arc delta in (-180, 180]
            val delta = ((newSmoothed - smoothed + 540f) % 360f) - 180f
            unwrapped += delta
        }

        smoothed = newSmoothed
        hasFirstSample = true
        return newSmoothed
    }

    fun reset() {
        smoothedSin = 0f
        smoothedCos = 1f
        hasFirstSample = false
        unwrapped = 0f
        smoothed = 0f
    }

    companion object {
        // ~6-sample (~300 ms at SENSOR_DELAY_UI) settling time.
        // Lower = smoother but laggier.
        const val DEFAULT_LP_ALPHA = 0.15f
    }
}
