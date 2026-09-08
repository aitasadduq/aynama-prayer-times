import Foundation

/// A date on the proleptic Gregorian calendar, with no time and no zone.
///
/// The Swift counterpart of `java.time.LocalDate`, which the Android implementation this port
/// follows is built on. Foundation offers `DateComponents`, but it is optional in every field
/// and carries an optional calendar and timezone of its own, so "the day this prayer falls on"
/// would be expressible in a dozen subtly different ways. A prayer day is one thing.
///
/// Arithmetic and weekday go through the epoch-day number rather than `Calendar`, so they are
/// exact and cannot be perturbed by the current locale, the user's first-weekday preference, or
/// a timezone the date does not have. Only ``atTime(_:in:)`` needs a zone, and it takes one.
public struct CalendarDate: Hashable, Comparable, Codable, Sendable {

    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Days since 1970-01-01. The canonical form: equality, ordering and arithmetic use it.
    public var epochDay: Int {
        // Howard Hinnant's days_from_civil. Shifts the year to start in March so the leap day
        // lands at the end of the year and the month-length pattern becomes a closed form.
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400                                     // [0, 399]
        let doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1  // [0, 365]
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy             // [0, 146096]
        return era * 146_097 + doe - 719_468
    }

    public init(epochDay: Int) {
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        let y = yoe + era * 400
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        self.day = doy - (153 * mp + 2) / 5 + 1
        self.month = mp + (mp < 10 ? 3 : -9)
        self.year = month <= 2 ? y + 1 : y
    }

    public func plusDays(_ days: Int) -> CalendarDate {
        CalendarDate(epochDay: epochDay + days)
    }

    public func minusDays(_ days: Int) -> CalendarDate {
        plusDays(-days)
    }

    /// 1 = Monday … 7 = Sunday, matching `java.time.DayOfWeek`.
    public var dayOfWeek: Int {
        // 1970-01-01 was a Thursday, so epoch day 0 must land on 4.
        (((epochDay + 3) % 7) + 7) % 7 + 1
    }

    public var isFriday: Bool { dayOfWeek == 5 }

    /// The instant this date's `time` occurs at in `zone`.
    ///
    /// A zone is required and never defaulted: a profile can be pinned to a timezone the device
    /// is not in, and resolving its prayer day in the device's zone would be wrong by hours.
    public func atTime(_ time: ClockTime, in zone: TimeZone) -> Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = time.hour
        components.minute = time.minute
        components.second = time.second
        components.timeZone = zone
        // gregorianUTC-style calendar with the zone attached: `Calendar.current` would pull in
        // the user's locale, which can be a non-Gregorian calendar entirely.
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        // Only nil for a date the Gregorian calendar cannot represent, which these components
        // cannot be — a local time inside a DST gap resolves forward, as java.time does.
        return calendar.date(from: components)!
    }

    /// The calendar date `instant` falls on in `zone`.
    public static func from(_ instant: Date, in zone: TimeZone) -> CalendarDate {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let c = calendar.dateComponents([.year, .month, .day], from: instant)
        return CalendarDate(year: c.year!, month: c.month!, day: c.day!)
    }

    public static func < (lhs: CalendarDate, rhs: CalendarDate) -> Bool {
        lhs.epochDay < rhs.epochDay
    }

    /// Equality is the epoch day too, as ``epochDay`` says and as ``<`` already used.
    ///
    /// The synthesized version compared the raw triple, so an out-of-range component — and the
    /// vector schema's date pattern permits `2026-02-30` — was unequal to the `2026-03-02` it
    /// resolves to while neither sorted before the other. That breaks `Comparable`, and this
    /// type is the key of ``buildTimeline(days:asrMadhab:timeZone:)``'s `days`, where it would
    /// have meant two dictionary entries for one real day.
    public static func == (lhs: CalendarDate, rhs: CalendarDate) -> Bool {
        lhs.epochDay == rhs.epochDay
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(epochDay)
    }
}

extension CalendarDate: CustomStringConvertible {
    public var description: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }
}

/// A time on a 24-hour clock, with no date and no zone — `java.time.LocalTime` to the second.
///
/// Prayer times come out of Adhan as absolute instants; this is what they read as on the wall
/// clock of the profile's zone, which is what the ribbon and the settings rows show. Ordering is
/// clock ordering, which is *not* timeline ordering — an Isha at 00:25 sorts before Fajr here and
/// after Maghrib in reality. ``PrayerTimeline`` is careful about that; see `entriesFor`.
public struct ClockTime: Hashable, Comparable, Codable, Sendable {

    public let hour: Int
    public let minute: Int
    public let second: Int

    public init(hour: Int, minute: Int, second: Int = 0) {
        self.hour = hour
        self.minute = minute
        self.second = second
    }

    public var secondOfDay: Int { hour * 3600 + minute * 60 + second }

    /// The wall-clock time `instant` reads as in `zone`, truncated to the second.
    public static func from(_ instant: Date, in zone: TimeZone) -> ClockTime {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let c = calendar.dateComponents([.hour, .minute, .second], from: instant)
        return ClockTime(hour: c.hour!, minute: c.minute!, second: c.second!)
    }

    /// Wraps at midnight, like `java.time.LocalTime.minusMinutes`. Used for the Imsak row.
    public func minusMinutes(_ minutes: Int) -> ClockTime {
        let total = ((secondOfDay - minutes * 60) % 86_400 + 86_400) % 86_400
        return ClockTime(hour: total / 3600, minute: (total % 3600) / 60, second: total % 60)
    }

    public static func < (lhs: ClockTime, rhs: ClockTime) -> Bool {
        lhs.secondOfDay < rhs.secondOfDay
    }
}

extension ClockTime: CustomStringConvertible {
    public var description: String {
        String(format: "%02d:%02d:%02d", hour, minute, second)
    }
}
