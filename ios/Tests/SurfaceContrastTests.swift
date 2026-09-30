import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Aynama

final class SurfaceContrastTests: XCTestCase {
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
