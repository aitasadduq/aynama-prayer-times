import SharedLogic
import SwiftData
import SwiftUI

struct TrackerView: View {
    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var profiles: [ProfileRecord]
    @Query private var marks: [QazaRecord]
    @Environment(\.colorScheme) private var scheme
    @State private var expandedDay: CalendarDate?
    @State private var target: PrayerMarkTarget?

    private var profile: Profile? { (profiles.first(where: \.isGps) ?? profiles.first)?.profile }
    private var palette: NeutralPalette { NeutralPalette(scheme: scheme) }

    var body: some View {
        Group {
            if let profile {
                TimelineView(.periodic(from: .now, by: 60)) { _ in history(profile) }
            } else { ProfileRequiredView(title: "Create a profile to track prayers") }
        }
        .navigationTitle("Tracker")
        .navigationBarTitleDisplayMode(.inline)
        .neutralSurface()
        .sheet(item: $target) { PrayerMarkSheet(target: $0) }
    }

    private func history(_ profile: Profile) -> some View {
        let today = CalendarDate.from(AppClock.now, in: profile.effectiveTimeZone)
        let monday = today.minusDays(today.dayOfWeek - 1)
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Today").font(AynamaFont.title).padding(.bottom, 8)
                ForEach(Prayer.allCases, id: \.self) { prayer in
                    prayerRow(prayer, profile: profile, date: today)
                }
                let outstanding = marks.filter { $0.profileID == profile.id && $0.status == .missed }.count
                if outstanding > 0 {
                    Text("\(outstanding) prayers outstanding").font(AynamaFont.bodySM)
                        .foregroundStyle(palette.muted).padding(.top, 8)
                }
                HStack(spacing: 4) {
                    Spacer()
                    ForEach(Prayer.allCases, id: \.self) { prayer in
                        Text(String(prayer.rawValue.prefix(1)).uppercased())
                            .font(AynamaFont.bodySM).frame(width: 15)
                    }
                    Color.clear.frame(width: 30, height: 1)
                }.foregroundStyle(palette.muted).padding(.top, 32).padding(.bottom, 8)
                ForEach(0..<4, id: \.self) { week in
                    let start = monday.minusDays(week * 7)
                    let end = min(start.plusDays(6), today.minusDays(1))
                    VStack(alignment: .leading, spacing: 8) {
                        Text(weekTitle(week, start: start, end: start.plusDays(6), profile: profile))
                            .font(AynamaFont.title)
                        if week == 0 {
                            Text(summary(profile, start: monday, today: today))
                                .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                        }
                    }.padding(.vertical, 12)
                    if end >= start {
                        ForEach((start.epochDay...end.epochDay).reversed().map { CalendarDate(epochDay: $0) }, id: \.self) { date in
                            dayRow(date, profile: profile)
                        }
                    }
                }
            }.padding(.horizontal, 24).padding(.top, 16).padding(.bottom, 24)
        }.scrollBounceBehavior(.basedOnSize)
    }

    private func prayerRow(_ prayer: Prayer, profile: Profile, date: CalendarDate) -> some View {
        let instant = PrayerSchedule.instant(prayer, profile: profile, date: date)
        let due = instant.map { $0 <= AppClock.now } ?? false
        return Button {
            target = PrayerMarkTarget(profile: profile, prayer: prayer, date: date)
        } label: {
            HStack(spacing: 16) {
                PrayerStatusSquare(status: status(prayer, profile: profile, date: date), size: 12)
                Text(prayerDisplayName(prayer, on: date)).font(AynamaFont.bodyLG)
                Spacer(minLength: 8)
                Text(PrayerSchedule.formatted(instant, zone: profile.effectiveTimeZone))
                    .font(AynamaFont.monoNum).foregroundStyle(palette.muted)
            }.frame(minHeight: 56).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!due)
        .opacity(due ? 1 : 0.45)
        .accessibilityLabel("\(prayerDisplayName(prayer, on: date)), \(due ? statusLabel(status(prayer, profile: profile, date: date)) : "not due yet")")
    }

    private func dayRow(_ date: CalendarDate, profile: Profile) -> some View {
        VStack(spacing: 0) {
            Button {
                expandedDay = expandedDay == date ? nil : date
            } label: {
                HStack(spacing: 4) {
                    Text(dateLabel(date, profile: profile)).font(AynamaFont.body)
                    Spacer(minLength: 8)
                    ForEach(Prayer.allCases, id: \.self) { prayer in
                        PrayerStatusSquare(status: status(prayer, profile: profile, date: date))
                    }
                    Text("\(completed(profile, date: date))/5").font(AynamaFont.bodySM)
                        .foregroundStyle(palette.muted).frame(width: 30, alignment: .trailing)
                }.frame(minHeight: 56).contentShape(Rectangle())
            }.buttonStyle(.plain)
                .accessibilityLabel("\(dateLabel(date, profile: profile)), \(completed(profile, date: date)) of 5 prayers")
            if expandedDay == date {
                ForEach(Prayer.allCases, id: \.self) { prayer in
                    prayerRow(prayer, profile: profile, date: date).padding(.leading, 16)
                }
            }
        }
    }

    private func status(_ prayer: Prayer, profile: Profile, date: CalendarDate) -> QazaStatus? {
        marks.first { $0.profileID == profile.id && $0.epochDay == date.epochDay && $0.prayer == prayer }?.status
    }
    private func completed(_ profile: Profile, date: CalendarDate) -> Int {
        Prayer.allCases.filter {
            let value = status($0, profile: profile, date: date)
            return value == .prayedOnTime || value == .madeUp
        }.count
    }
    private func statusLabel(_ status: QazaStatus?) -> String {
        switch status {
        case .prayedOnTime: "prayed on time"
        case .madeUp: "prayed later as Qada"
        case .missed: "missed"
        default: "unmarked"
        }
    }
    private func dateLabel(_ date: CalendarDate, profile: Profile) -> String {
        let formatter = DateFormatter()
        formatter.timeZone = profile.effectiveTimeZone
        formatter.setLocalizedDateFormatFromTemplate("EEE MMM d")
        return formatter.string(from: date.atTime(ClockTime(hour: 12, minute: 0), in: profile.effectiveTimeZone))
    }
    private func weekTitle(_ week: Int, start: CalendarDate, end: CalendarDate, profile: Profile) -> String {
        if week == 0 { return "This week" }
        if week == 1 { return "Last week" }
        return "\(dateLabel(start, profile: profile)) – \(dateLabel(end, profile: profile))"
    }
    private func summary(_ profile: Profile, start: CalendarDate, today: CalendarDate) -> String {
        let days = (start.epochDay...today.epochDay).map { CalendarDate(epochDay: $0) }
        let due = days.reduce(0) { count, date in
            count + PrayerSchedule.entries(profile: profile, date: date)
                .filter { $0.event.isPrayer && $0.instant <= AppClock.now }.count
        }
        let onTime = marks.filter {
            $0.profileID == profile.id && $0.epochDay >= start.epochDay && $0.epochDay <= today.epochDay && $0.status == .prayedOnTime
        }.count
        return "\(onTime) of \(due) prayers on time this week"
    }
}
