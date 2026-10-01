import SharedLogic
import SwiftUI

struct PrayerRibbon: View {
    let rows: [RibbonRow]
    let surface: TimeOfDaySurface
    let rowHeight: CGFloat
    let fontSize: CGFloat
    let markFontSize: CGFloat
    let scale: CGFloat
    let onMark: (Prayer) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(rows) { row in
                if case let .prayer(prayer, _, _, state) = row, state != .upcoming {
                    Button { onMark(prayer) } label: { rowView(row) }.buttonStyle(.plain)
                        .accessibilityLabel("\(title(row)) at \(row.displayTime), \(state == .current ? "current" : "passed")")
                        .accessibilityHint("Record this prayer")
                        .accessibilityIdentifier("home-row-\(title(row))")
                } else {
                    rowView(row).accessibilityElement(children: .combine)
                        .accessibilityIdentifier("home-row-\(title(row))")
                }
            }
        }
    }

    private func rowView(_ row: RibbonRow) -> some View {
        HStack(spacing: 12 * scale) {
            ZStack {
                if case let .prayer(_, _, _, state) = row {
                    if state == .passed {
                        Text("✓").font(AynamaFont.homeMark(size: markFontSize))
                            .foregroundStyle(surface.foregroundMuted)
                    } else if state == .current {
                        Circle().fill(surface.activeForeground).frame(width: 8 * scale, height: 8 * scale)
                    }
                }
            }.frame(width: 20 * scale).accessibilityHidden(true)
            Text(title(row)).font(AynamaFont.homeRowName(size: fontSize))
                .lineLimit(1).minimumScaleFactor(0.8)
            Spacer(minLength: 4 * scale)
            Text(row.displayTime).font(AynamaFont.homeRowTime(size: fontSize))
                .monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
        }.foregroundStyle(color(row)).frame(maxWidth: .infinity).frame(height: rowHeight)
            .contentShape(Rectangle())
    }
    private func title(_ row: RibbonRow) -> String {
        switch row {
        case let .prayer(_, displayName, _, _): displayName
        case .sunrise: "Sunrise"
        case .imsak: "Imsak"
        }
    }
    private func color(_ row: RibbonRow) -> Color {
        switch row {
        case .prayer(_, _, _, .current): surface.activeForeground
        case .prayer(_, _, _, .passed), .sunrise, .imsak(_, true): surface.foregroundMuted
        default: surface.foreground
        }
    }
}
