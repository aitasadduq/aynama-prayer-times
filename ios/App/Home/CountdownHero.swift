import SwiftUI

/// The countdown hero (DESIGN.md §5, §19).
///
/// The number is rendered verbatim from ``PrayerCountdown/compactFormatted()`` in shared logic.
///
/// The prayer it refers to is always named beside it. A bare signed number does not say whether
/// Dhuhr is coming or has just started, and the sign alone is a subtle thing to hang that on.
struct CountdownHero: View {

    let text: String
    let isElapsed: Bool
    let prayerName: String
    let prayerTime: String
    let surface: TimeOfDaySurface
    let metrics: HomePageMetrics

    var body: some View {
        VStack(alignment: .leading, spacing: metrics.heroLineGap) {
            Text(text)
                .font(AynamaFont.homeCountdown(size: metrics.heroFont))
                .foregroundStyle(isElapsed ? surface.activeForeground : surface.foreground)
                // §5: never centred. The hero is left-aligned with the ribbon beneath it.
                .frame(maxWidth: .infinity, alignment: .leading)
                .minimumScaleFactor(0.35)
                .lineLimit(1)
                .accessibilityLabel(spokenCountdown)

            if !prayerName.isEmpty {
                Text(prayerTime.isEmpty ? prayerName : "\(prayerName) · \(prayerTime)")
                    .font(AynamaFont.homeSubtitle(size: metrics.subtitleFont))
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

    /// VoiceOver should say the compact units in words.
    private var spokenCountdown: String {
        guard !prayerName.isEmpty else { return "No countdown available" }
        let units: [Character: String] = ["h": "hour", "m": "minute", "s": "second"]
        let elapsed = text.trimmingCharacters(in: CharacterSet(charactersIn: "-"))
            .split(separator: " ")
            .compactMap { part -> String? in
                guard let suffix = part.last, let unit = units[suffix],
                      let value = Int(part.dropLast()) else { return nil }
                return "\(value) \(unit)\(value == 1 ? "" : "s")"
            }
            .joined(separator: " ")
        return isElapsed ? "\(elapsed) since \(prayerName)" : "\(elapsed) until \(prayerName)"
    }
}
