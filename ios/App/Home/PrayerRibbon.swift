import SharedLogic
import SwiftUI

/// DESIGN.md §5's prayer timeline ribbon — the first of the Two Deliberate Departures (§9).
///
/// A vertical line with a saffron tick that moves down it as the day passes, like a sundial
/// shadow. **Not** a circular countdown ring; §10 forbids one anywhere in the app, and §9 says to
/// reject any proposal that reintroduces it at review.
struct PrayerRibbon: View {

    let rows: [RibbonRow]
    let surface: TimeOfDaySurface

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                RibbonRowView(row: row, surface: surface, isLast: index == rows.count - 1)
            }
        }
    }
}

private struct RibbonRowView: View {

    let row: RibbonRow
    let surface: TimeOfDaySurface
    let isLast: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            marker
            label
            Spacer(minLength: 12)
            Text(row.displayTime)
                .font(AynamaFont.monoNum)
                .monospacedDigit()
                .foregroundStyle(timeColor)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
    }

    /// The ribbon line itself: a dot on the rule for prayers, a gap for sunrise.
    ///
    /// §5 gives sunrise no dot — it is a time reference, not an act, the same distinction the
    /// countdown makes when it refuses to count up from it.
    private var marker: some View {
        VStack(spacing: 0) {
            Group {
                if case .sunrise = row {
                    Circle().fill(.clear).frame(width: 9, height: 9)
                } else {
                    Circle()
                        .fill(dotColor)
                        .frame(width: isCurrent ? 11 : 7, height: isCurrent ? 11 : 7)
                }
            }
            .frame(height: 12)

            // Hairlines below 1pt disappear on OLED (§7); 1.5pt is the icon stroke weight too.
            Rectangle()
                .fill(surface.foregroundMuted.opacity(isLast ? 0 : 0.45))
                .frame(width: 1.5)
                .frame(maxHeight: .infinity)
        }
        .frame(width: 11)
        .frame(minHeight: 34)
    }

    private var label: some View {
        HStack(spacing: 8) {
            Text(title)
                .font(AynamaFont.bodyLG)
                .foregroundStyle(labelColor)
            if isPassedPrayer {
                // The passed marker. A glyph, not a filled badge — §6 keeps filled reserved for
                // active tab states.
                Image(systemName: "checkmark")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(surface.foregroundMuted)
                    .accessibilityHidden(true)
            }
            if isCurrent {
                // The sundial tick. Points at the row the moment is in.
                Image(systemName: "arrowtriangle.left.fill")
                    .font(.system(size: 9))
                    .foregroundStyle(AynamaColor.saffron)
                    .accessibilityHidden(true)
            }
        }
    }

    private var title: String {
        switch row {
        case let .prayer(_, displayName, _, _): displayName
        case .sunrise: "Sunrise"
        case .imsak: "Imsak"
        }
    }

    private var isCurrent: Bool {
        if case let .prayer(_, _, _, state) = row { return state == .current }
        return false
    }

    private var isPassedPrayer: Bool {
        if case let .prayer(_, _, _, state) = row { return state == .passed }
        if case let .imsak(_, isPast) = row { return isPast }
        return false
    }

    private var dotColor: Color {
        if isCurrent { return AynamaColor.saffron }
        if isPassedPrayer { return surface.foregroundMuted.opacity(0.6) }
        return surface.foreground.opacity(0.75)
    }

    private var labelColor: Color {
        if isCurrent { return AynamaColor.saffron }
        if isPassedPrayer { return surface.foregroundMuted }
        if case .sunrise = row { return surface.foregroundMuted }
        return surface.foreground
    }

    private var timeColor: Color { labelColor }

    /// VoiceOver reads the state, which the dot and the tick only show.
    private var accessibilityLabel: String {
        let state: String =
            if isCurrent { "current" } else if isPassedPrayer { "passed" } else { "upcoming" }
        if case .sunrise = row { return "Sunrise at \(row.displayTime)" }
        if case .imsak = row { return "Imsak at \(row.displayTime), \(isPassedPrayer ? "passed" : "upcoming")" }
        return "\(title) at \(row.displayTime), \(state)"
    }
}
