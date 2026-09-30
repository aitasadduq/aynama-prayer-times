import SharedLogic
import SwiftUI

struct PrayerRibbon: View {
    let rows: [RibbonRow]
    let surface: TimeOfDaySurface
    var rowHeight: CGFloat = 56
    var progress: Double?
    let onMark: (Prayer) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(rows) { row in
                if case let .prayer(prayer, _, _, state) = row, state != .upcoming {
                    Button { onMark(prayer) } label: { rowView(row) }.buttonStyle(.plain)
                        .accessibilityLabel("\(title(row)) at \(row.displayTime), \(state == .current ? "current" : "passed")")
                        .accessibilityHint("Record this prayer")
                } else { rowView(row).accessibilityElement(children: .combine) }
            }
        }
        .background(alignment: .topLeading) {
            Rectangle().fill(surface.foregroundMuted.opacity(0.25))
                .frame(width: 1.5, height: CGFloat(max(0, rows.count - 1)) * rowHeight)
                .offset(x: 9, y: rowHeight / 2)
        }
        .overlay(alignment: .topLeading) {
            if let progress {
                Rectangle().fill(surface.activeForeground).frame(width: 18, height: 2)
                    .offset(x: 1, y: rowHeight / 2 + CGFloat(progress) * rowHeight)
                    .accessibilityHidden(true)
            }
        }
    }

    private func rowView(_ row: RibbonRow) -> some View {
        HStack(spacing: 12) {
            ZStack {
                if case let .prayer(_, _, _, state) = row {
                    if state == .passed {
                        Text("✓").font(AynamaFont.monoNum).foregroundStyle(surface.foregroundMuted)
                    } else if state == .current {
                        Circle().fill(surface.activeForeground).frame(width: 8, height: 8)
                    }
                }
            }.frame(width: 20).accessibilityHidden(true)
            Text(title(row)).font(AynamaFont.title)
            Spacer(minLength: 8)
            Text(row.displayTime).font(AynamaFont.timelineTime).monospacedDigit().lineLimit(1)
        }.foregroundStyle(color(row)).frame(minHeight: rowHeight).contentShape(Rectangle())
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
