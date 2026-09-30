import SharedLogic
import SwiftData
import SwiftUI

struct PrayerMarkTarget: Identifiable {
    let profile: Profile
    let prayer: Prayer
    let date: CalendarDate
    var id: String { "\(profile.id)-\(date)-\(prayer.rawValue)" }
}

struct PrayerMarkSheet: View {
    let target: PrayerMarkTarget
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @State private var error: String?

    var body: some View {
        let palette = NeutralPalette(scheme: scheme)
        VStack(spacing: 8) {
            Text(prayerDisplayName(target.prayer, on: target.date)).font(AynamaFont.displayMD)
            Text("\(dayLabel) · \(PrayerSchedule.formatted(PrayerSchedule.instant(target.prayer, profile: target.profile, date: target.date), zone: target.profile.effectiveTimeZone))")
                .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
            VStack(spacing: 0) {
                option(.prayedOnTime, "I prayed this", enabled: PrayerSchedule.isInWindow(
                    target.prayer, profile: target.profile, date: target.date))
                option(.madeUp, "I prayed this later (Qada)")
                option(.missed, "I didn't pray this")
            }.padding(.top, 16)
            if let error { Text(error).font(AynamaFont.bodySM) }
            Spacer(minLength: 0)
        }
        .padding(24)
        .neutralSurface()
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(palette.background)
    }

    private var dayLabel: String {
        let today = CalendarDate.from(AppClock.now, in: target.profile.effectiveTimeZone)
        if target.date == today { return "Today" }
        if target.date == today.minusDays(1) { return "Yesterday" }
        let formatter = DateFormatter()
        formatter.timeZone = target.profile.effectiveTimeZone
        formatter.setLocalizedDateFormatFromTemplate("EEE MMM d")
        return formatter.string(from: target.date.atTime(ClockTime(hour: 12, minute: 0), in: target.profile.effectiveTimeZone))
    }

    private func option(_ status: QazaStatus, _ label: String, enabled: Bool = true) -> some View {
        Button {
            do {
                try PrayerHistoryRepository(context: context).mark(target, status: status)
                dismiss()
            } catch { self.error = "Couldn't save this prayer. Please try again." }
        } label: {
            HStack(spacing: 16) {
                PrayerStatusSquare(status: status, size: 12)
                Text(label).font(AynamaFont.bodyLG)
                Spacer()
            }.frame(minHeight: 56).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityIdentifier(label)
        .accessibilityAddTraits(.isButton)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
    }
}

struct PrayerStatusSquare: View {
    let status: QazaStatus?
    var size: CGFloat = 15
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        Rectangle()
            .fill(fill)
            .overlay { Rectangle().stroke(NeutralPalette(scheme: scheme).muted.opacity(0.65), lineWidth: status == nil || status == .missed ? 1 : 0) }
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
    private var fill: Color {
        switch status {
        case .prayedOnTime: NeutralPalette(scheme: scheme).accent
        case .madeUp: NeutralPalette(scheme: scheme).muted
        default: .clear
        }
    }
}

@MainActor
struct PrayerHistoryRepository {
    let context: ModelContext

    func mark(_ target: PrayerMarkTarget, status: QazaStatus) throws {
        let id = target.profile.id
        let day = target.date.epochDay
        let prayer = target.prayer.rawValue
        let descriptor = FetchDescriptor<QazaRecord>(predicate: #Predicate {
            $0.profileID == id && $0.epochDay == day && $0.prayerRaw == prayer
        })
        let existing = try context.fetch(descriptor)
        if let record = existing.first {
            record.statusRaw = status.rawValue
            record.updatedAt = AppClock.now
            for duplicate in existing.dropFirst() { context.delete(duplicate) }
        } else {
            context.insert(QazaRecord(profileID: id, prayer: target.prayer, date: target.date, status: status))
        }
        try context.save()
    }
}
