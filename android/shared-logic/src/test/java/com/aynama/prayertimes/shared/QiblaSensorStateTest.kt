package com.aynama.prayertimes.shared

import org.junit.Test
import kotlin.math.abs

class QiblaSensorStateTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, msg: String = "") {
        assert(abs(expected - actual) < tolerance) {
            "$msg expected $expected, got $actual (diff ${abs(expected - actual)})"
        }
    }

    // -- First-sample / no-op smoothing --

    @Test
    fun firstSample_smoothedEqualsRaw() {
        val state = QiblaSensorState()
        state.update(120f)
        assertClose(120f, state.smoothed)
        assertClose(120f, state.unwrapped)
    }

    @Test
    fun firstSample_zeroAzimuth() {
        val state = QiblaSensorState()
        state.update(0f)
        assertClose(0f, state.smoothed)
        assertClose(0f, state.unwrapped)
    }

    // -- Magnetic declination --

    @Test
    fun declinationAdded_beforeSmoothing() {
        val state = QiblaSensorState()
        state.update(rawAzimuth = 100f, magneticDeclination = 13f)
        assertClose(113f, state.smoothed, tolerance = 0.5f)
    }

    @Test
    fun declinationNegative_normalizes() {
        // raw 5° + declination -13° = -8° → wraps to 352°
        val state = QiblaSensorState()
        state.update(rawAzimuth = 5f, magneticDeclination = -13f)
        assertClose(352f, state.smoothed, tolerance = 0.5f)
    }

    @Test
    fun declinationCrossesNorth() {
        // raw 358° + declination 5° = 363° → wraps to 3°
        val state = QiblaSensorState()
        state.update(rawAzimuth = 358f, magneticDeclination = 5f)
        assertClose(3f, state.smoothed, tolerance = 0.5f)
    }

    // -- Low-pass convergence --

    @Test
    fun lpFilter_convergesTowardSteadyState() {
        val state = QiblaSensorState(lpAlpha = 0.15f)
        state.update(0f) // initialize
        repeat(50) { state.update(90f) }
        // After 50 samples at alpha=0.15, smoothed should be ≥99% of target
        assertClose(90f, state.smoothed, tolerance = 1.0f, msg = "lp should converge to 90°")
    }

    @Test
    fun lpFilter_lagsSingleStep() {
        // First sample initializes smoothed = raw. Second sample is the first that
        // actually applies LP smoothing.
        val state = QiblaSensorState(lpAlpha = 0.15f)
        state.update(0f)
        state.update(90f)
        // After one LP step: smoothedSin/Cos move 15% toward target
        // The atan2 result is non-linear in degree-space but should be < 90° and > 0°
        assert(state.smoothed in 1f..30f) {
            "expected partial convergence < 30°, got ${state.smoothed}"
        }
    }

    @Test
    fun lpFilter_nearWrapAround_doesNotJump() {
        // 359° → 1° transition: naive averaging would yield ~180°. sin/cos space avoids this.
        val state = QiblaSensorState(lpAlpha = 0.15f)
        state.update(359f)
        state.update(1f)
        // Smoothed should stay near 0° (not 180°)
        val s = state.smoothed
        assert(s > 350f || s < 10f) {
            "wraparound should keep smoothed near 0°, got $s (would be ~180° with naive avg)"
        }
    }

    // -- Unwrap delta --

    @Test
    fun unwrap_acrossWrapForward_accumulates() {
        // Rotate 359° → 1°: unwrap should produce ~+2°, not -358°
        val state = QiblaSensorState(lpAlpha = 1f) // alpha=1 means smoothed == raw, easy assertions
        state.update(359f)
        state.update(1f)
        val unwrappedDelta = state.unwrapped - 359f
        assert(unwrappedDelta in 1.5f..2.5f) {
            "unwrap forward across 0°: expected ~+2° accumulation, got delta=$unwrappedDelta"
        }
    }

    @Test
    fun unwrap_acrossWrapBackward_accumulates() {
        // Rotate 1° → 359°: unwrap should produce ~-2°, not +358°
        val state = QiblaSensorState(lpAlpha = 1f)
        state.update(1f)
        state.update(359f)
        val unwrappedDelta = state.unwrapped - 1f
        assert(unwrappedDelta in -2.5f..-1.5f) {
            "unwrap backward across 0°: expected ~-2°, got delta=$unwrappedDelta"
        }
    }

    @Test
    fun unwrap_fullRotationForward_accumulates() {
        val state = QiblaSensorState(lpAlpha = 1f)
        // Walk through 0° → 90° → 180° → 270° → 0° (back where we started)
        state.update(0f)
        state.update(90f)
        state.update(180f)
        state.update(270f)
        state.update(0f) // back to north via the long way
        // Unwrap should be ~360° (one full clockwise rotation)
        assert(state.unwrapped in 350f..370f) {
            "full rotation: expected unwrapped ≈ 360°, got ${state.unwrapped}"
        }
    }

    @Test
    fun unwrap_fullRotationBackward_accumulatesNegative() {
        val state = QiblaSensorState(lpAlpha = 1f)
        // Walk 0° → 270° → 180° → 90° → 0° (counter-clockwise via wraparound)
        state.update(0f)
        state.update(270f)
        state.update(180f)
        state.update(90f)
        state.update(0f)
        assert(state.unwrapped in -370f..-350f) {
            "full counter-rotation: expected unwrapped ≈ -360°, got ${state.unwrapped}"
        }
    }

    @Test
    fun unwrap_smallStaticJitter_doesNotAccumulate() {
        val state = QiblaSensorState(lpAlpha = 1f)
        state.update(45f)
        repeat(100) { state.update(45f) }
        assertClose(45f, state.unwrapped, tolerance = 0.5f, msg = "static input should not drift")
    }

    // -- Reset --

    @Test
    fun reset_clearsState() {
        val state = QiblaSensorState()
        state.update(120f)
        state.update(125f)
        state.reset()
        // After reset, the next sample initializes smoothed=raw again
        state.update(50f)
        assertClose(50f, state.smoothed, tolerance = 0.5f)
        assertClose(50f, state.unwrapped, tolerance = 0.5f)
    }

    // -- Output bounds --

    @Test
    fun smoothed_alwaysInRange() {
        val state = QiblaSensorState(lpAlpha = 0.3f)
        // Drive through every degree of rotation
        for (deg in 0..720 step 7) {
            val s = state.update(deg.toFloat() % 360f)
            assert(s in 0f..360f) { "smoothed out of range at deg=$deg: $s" }
        }
    }

    // -- Custom alpha --

    @Test
    fun customAlpha_alpha1_meansNoSmoothing() {
        val state = QiblaSensorState(lpAlpha = 1f)
        state.update(0f)
        state.update(120f)
        assertClose(120f, state.smoothed, tolerance = 0.5f, msg = "alpha=1 should track input exactly")
    }

    @Test
    fun customAlpha_alphaSmall_meansHeavySmoothing() {
        val state = QiblaSensorState(lpAlpha = 0.01f)
        state.update(0f)
        state.update(180f)
        // After one step at alpha=0.01, smoothed should still be very close to 0°
        assert(state.smoothed < 5f || state.smoothed > 355f) {
            "alpha=0.01 should barely move from 0° toward 180°, got ${state.smoothed}"
        }
    }
}
