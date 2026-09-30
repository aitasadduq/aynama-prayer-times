import SwiftUI

/// The countdown hero (DESIGN.md §5, §19).
///
/// The number is rendered verbatim from ``PrayerCountdown/formatted()`` — the app never
/// re-derives the sign or the padding, because §19 says one rule governs every surface and the
/// app is the surface that gets to show it in full: `-00:12:35` counting down, `00:15:42`
/// counting up.
///
/// The prayer it refers to is always named beside it. A bare signed number does not say whether
/// Dhuhr is coming or has just started, and the sign alone is a subtle thing to hang that on.
struct CountdownHero: View {

    let text: String
    let isElapsed: Bool
    let prayerName: String
    let prayerTime: String
    let surface: TimeOfDaySurface

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(text)
                .font(AynamaFont.displayXL)
                .monospacedDigit()
                .foregroundStyle(isElapsed ? AynamaColor.saffron : surface.foreground)
                // §5: never centred. The hero is left-aligned with the ribbon beneath it.
                .frame(maxWidth: .infinity, alignment: .leading)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                .accessibilityLabel(spokenCountdown)

            if !prayerName.isEmpty {
                Text(prayerTime.isEmpty ? prayerName : "\(prayerName) · \(prayerTime)")
                    .font(AynamaFont.displayMD)
                    .foregroundStyle(surface.foreground)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
            }
        }
        // The number changes every second; without this the whole hero animates on each tick and
        // §8 forbids rolling numerals.
        .animation(nil, value: text)
    }

    /// VoiceOver should not read "dash zero zero colon one two colon three five".
    private var spokenCountdown: String {
        let digits = text.hasPrefix("-") ? String(text.dropFirst()) : text
        let parts = digits.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 3 else { return text }
        var spoken: [String] = []
        if parts[0] > 0 { spoken.append("\(parts[0]) hour\(parts[0] == 1 ? "" : "s")") }
        if parts[1] > 0 { spoken.append("\(parts[1]) minute\(parts[1] == 1 ? "" : "s")") }
        spoken.append("\(parts[2]) second\(parts[2] == 1 ? "" : "s")")
        let elapsed = spoken.joined(separator: " ")
        return isElapsed ? "\(elapsed) since \(prayerName)" : "\(elapsed) until \(prayerName)"
    }
}
