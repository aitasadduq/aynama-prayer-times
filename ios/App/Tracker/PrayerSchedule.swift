import Foundation
import SharedLogic

/// Prayer-day instants, including Isha after midnight. Views never order wall-clock strings.
enum PrayerSchedule {
    static func times(profile: Profile, date: CalendarDate) -> PrayerTimesResult? {
        try? AdhanWrapper().prayerTimes(latitude: profile.latitude, longitude: profile.longitude,
                                     date: date, timeZone: profile.effectiveTimeZone,
                                     method: profile.calculationMethod)
    }

    static func entries(profile: Profile, date: CalendarDate) -> [TimelineEntry] {
        guard let times = times(profile: profile, date: date) else { return [] }
        return buildTimeline(days: [date: times], asrMadhab: profile.asrMadhab,
                             timeZone: profile.effectiveTimeZone)
    }

    static func instant(_ prayer: Prayer, profile: Profile, date: CalendarDate) -> Date? {
        entries(profile: profile, date: date).first { $0.event.prayer == prayer }?.instant
    }

    /// The latest occurrence may belong to yesterday's prayer day even after civil midnight.
    static func latestPrayerDay(_ prayer: Prayer, profile: Profile, now: Date) -> CalendarDate? {
        let today = CalendarDate.from(now, in: profile.effectiveTimeZone)
        return [today.minusDays(1), today].compactMap { day -> (CalendarDate, Date)? in
            guard let instant = instant(prayer, profile: profile, date: day), instant <= now else { return nil }
            return (day, instant)
        }.max { $0.1 < $1.1 }?.0
    }

    static func isInWindow(_ prayer: Prayer, profile: Profile, date: CalendarDate,
                           now: Date = AppClock.now) -> Bool {
        var days: [CalendarDate: PrayerTimesResult] = [:]
        for offset in 0...1 {
            let day = date.plusDays(offset)
            if let times = times(profile: profile, date: day) { days[day] = times }
        }
        let timeline = buildTimeline(days: days, asrMadhab: profile.asrMadhab,
                                     timeZone: profile.effectiveTimeZone)
        guard let index = timeline.firstIndex(where: { $0.event.prayer == prayer }),
              index + 1 < timeline.count else { return false }
        return now >= timeline[index].instant && now < timeline[index + 1].instant
    }

    static func formatted(_ instant: Date?, zone: TimeZone) -> String {
        guard let instant else { return "—" }
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.timeZone = zone
        formatter.setLocalizedDateFormatFromTemplate("jmm")
        return formatter.string(from: instant)
    }
}
