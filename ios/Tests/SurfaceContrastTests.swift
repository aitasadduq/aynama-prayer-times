import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Aynama

final class SurfaceContrastTests: XCTestCase {
    func testTabAppearanceAndTintFollowThePhaseInEitherSystemAppearance() {
        for system in [ColorScheme.light, .dark] {
            for surface in [TimeOfDaySurface.fajr, .sunriseTransition, .dhuhr, .asr, .maghrib, .isha] {
                let chrome = NativeTabChrome(surface: surface, fallback: system)
                XCTAssertEqual(chrome.scheme, surface.prefersLightForeground ? .dark : .light)
                XCTAssertGreaterThanOrEqual(contrast(chrome.tint, NeutralPalette(scheme: chrome.scheme).background), 4.5)
            }
            XCTAssertEqual(NativeTabChrome(surface: nil, fallback: system).scheme, system)
        }
    }

    func testNativeLabelsAndAccentsMeetAAInBothAppearances() {
        for scheme in [ColorScheme.light, .dark] {
            let palette = NeutralPalette(scheme: scheme)
            for foreground in [palette.foreground, palette.muted, palette.accent] {
                XCTAssertGreaterThanOrEqual(contrast(foreground, palette.background), 4.5, "\(scheme) native labels")
            }
        }
    }

    func testBodyMetadataAndActiveTextMeetAAAtBothEndsOfEveryPhaseGradient() {
        let surfaces: [TimeOfDaySurface] = [.fajr, .sunriseTransition, .dhuhr, .asr, .maghrib, .isha]
        for surface in surfaces {
            for background in [surface.stops.top, surface.stops.bottom] {
                XCTAssertGreaterThanOrEqual(contrast(surface.foreground, background), 4.5, "\(surface) body text")
                XCTAssertGreaterThanOrEqual(contrast(surface.foregroundMuted, background), 4.5, "\(surface) metadata")
                XCTAssertGreaterThanOrEqual(contrast(surface.activeForeground, background), 4.5, "\(surface) active text")
            }
        }
    }

    private func contrast(_ foreground: Color, _ background: Color) -> Double {
        let values = [luminance(foreground), luminance(background)].sorted()
        return (values[1] + 0.05) / (values[0] + 0.05)
    }
    private func luminance(_ color: Color) -> Double {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, alpha: CGFloat = 0
        XCTAssertTrue(UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &alpha))
        func linear(_ value: CGFloat) -> Double {
            let v = Double(value)
            return v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
}
