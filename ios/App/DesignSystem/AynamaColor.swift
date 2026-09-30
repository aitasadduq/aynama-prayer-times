import SharedLogic
import SwiftUI

extension PrayerPhase {
    /// The gradient this phase carries on a contemplative screen.
    ///
    /// The mapping lives here rather than on ``PrayerPhase`` because the phase is domain — it
    /// says which prayer window the clock is in, and the widget and watch app read it too —
    /// while the gradient is this app's palette. `SharedLogic` stays free of SwiftUI so the
    /// widget extension and the watch app can link it without dragging a view layer along.
    var surface: TimeOfDaySurface {
        switch self {
        case .fajr: .fajr
        case .sunriseTransition: .sunriseTransition
        case .dhuhr: .dhuhr
        case .asr: .asr
        case .maghrib: .maghrib
        case .isha: .isha
        }
    }
}

/// DESIGN.md §3. Six tokens, one accent, no exceptions.
///
/// The iOS counterpart of `AynamaTheme.kt`'s "every Material role must be assigned" rule is
/// narrower but has the same shape: iOS has no palette to leave half-filled, but it does have a
/// **system-default tint** — a warm blue that shows up on every unstyled `Button`, `Toggle`,
/// `Link`, text cursor and selection handle in the app. Leaving it is the same mistake as
/// leaving `secondaryContainer` unset on Android, and it fails §3 for the same reason: the
/// palette has one accent and it is saffron. `AccentColor` in the asset catalog and
/// ``AynamaColor/accent`` are the same value; the catalog covers the controls SwiftUI tints
/// before any modifier of ours runs.
enum AynamaColor {

    /// Warm brown-black. Primary text on light, primary surface on dark. Not `#000`, not slate.
    static let ink = Color(red: 0x1C / 255, green: 0x1A / 255, blue: 0x17 / 255)

    /// Secondary text, passed-prayer states.
    static let inkMuted = Color(red: 0x6B / 255, green: 0x65 / 255, blue: 0x60 / 255)

    /// Unbleached linen. Primary surface on light, primary text on dark. Not white, not cream.
    static let parchment = Color(red: 0xF2 / 255, green: 0xEA / 255, blue: 0xD8 / 255)

    /// Dividers, subtle backgrounds.
    static let parchmentMuted = Color(red: 0xD4 / 255, green: 0xC9 / 255, blue: 0xB1 / 255)

    /// The only accent. Active states, current prayer, key CTAs.
    static let saffron = Color(red: 0xB8 / 255, green: 0x7A / 255, blue: 0x2E / 255)

    /// Saffron on parchment, pressed.
    static let saffronInk = Color(red: 0x8A / 255, green: 0x5A / 255, blue: 0x22 / 255)

    /// - SeeAlso: the type-level note on the system tint.
    static let accent = saffron
}

/// The six phases of DESIGN.md §3's time-of-day cycle, and the two-stop gradient each carries.
///
/// Contemplative screens only — Home, Countdown, Qibla. Utilitarian screens stay on a stable
/// ground (§2), which is why this is not a global environment value.
enum TimeOfDaySurface {
    case fajr
    case sunriseTransition
    case dhuhr
    case asr
    case maghrib
    case isha

    var stops: (top: Color, bottom: Color) {
        switch self {
        // Warm ink into predawn warmth. Not indigo, not cool blue — §3 says so twice.
        case .fajr: (hex(0x1C1A17), hex(0x3A3530))
        // Morning light: linen into honey. Runs sunrise → Dhuhr, so it stays legible under ink.
        case .sunriseTransition: (hex(0xEDE1C5), hex(0xE8C89A))
        case .dhuhr: (hex(0xF2EAD8), hex(0xEDE1C5))
        case .asr: (hex(0xE8C89A), hex(0xB87A2E))
        case .maghrib: (hex(0x6B2E2A), hex(0x1C1A17))
        case .isha: (hex(0x0F1419), hex(0x1C1A17))
        }
    }

    /// Whether text on this surface should be parchment rather than ink.
    ///
    /// Read from the phase rather than from the colour scheme: the cycle is the same at midnight
    /// whichever appearance the device is in, and a Maghrib oxblood needs light text in both.
    var prefersLightForeground: Bool {
        switch self {
        case .fajr, .maghrib, .isha: true
        case .sunriseTransition, .dhuhr, .asr: false
        }
    }

    var foreground: Color { prefersLightForeground ? AynamaColor.parchment : AynamaColor.ink }

    var foregroundMuted: Color {
        prefersLightForeground ? AynamaColor.parchmentMuted : AynamaColor.inkMuted
    }

    private func hex(_ value: Int) -> Color {
        Color(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

extension View {
    /// The time-of-day gradient behind a contemplative screen.
    ///
    /// A cross-fade, not a cut: §8 asks for 400ms ease-out on prayer transitions, and the phase
    /// changes underneath a view that is already on screen.
    func timeOfDaySurface(_ phase: TimeOfDaySurface) -> some View {
        background {
            LinearGradient(
                colors: [phase.stops.top, phase.stops.bottom],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            .animation(.easeOut(duration: 0.4), value: phase)
        }
    }
}
