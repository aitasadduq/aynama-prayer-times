import Foundation

/// The six phases of the time-of-day surface cycle (DESIGN.md §3).
///
/// A port of `PrayerPhase` in `HomeViewModel.kt`. Separate from ``TimelineEvent`` because a phase
/// is a *stretch* of the day named after the prayer that opened it, and the cycle has a sunrise
/// band that no prayer opens.
public enum PrayerPhase: String, Equatable, CaseIterable, Sendable {
    case fajr
    case sunriseTransition
    case dhuhr
    case asr
    case maghrib
    case isha
}

/// The label for a time-of-day phase — day-aware, so a Friday afternoon reads "Jumuah".
///
/// A phase is named after the prayer that opened it, so it follows the same naming rule as every
/// other display of that day's prayer (DESIGN.md §20).
public func phaseDisplayName(_ phase: PrayerPhase, on date: CalendarDate) -> String {
    switch phase {
    case .fajr: prayerDisplayName(Prayer.fajr, on: date)
    case .sunriseTransition: "Sunrise"
    case .dhuhr: prayerDisplayName(Prayer.dhuhr, on: date)
    case .asr: prayerDisplayName(Prayer.asr, on: date)
    case .maghrib: prayerDisplayName(Prayer.maghrib, on: date)
    case .isha: prayerDisplayName(Prayer.isha, on: date)
    }
}

public enum RibbonState: Equatable, Sendable {
    case passed
    case current
    case upcoming
}

/// One line of the prayer timeline ribbon (DESIGN.md §5).
public enum RibbonRow: Equatable, Identifiable, Sendable {
    /// A prayer. `displayName` is day-aware — "Jumuah" on a Friday — and resolved here so the
    /// view never re-derives it; `prayer` is the stored value and does not change.
    case prayer(prayer: Prayer, displayName: String, displayTime: String, state: RibbonState)
    /// A time reference, not an act: no dot on the ribbon, never "current".
    case sunrise(displayTime: String)
    /// Ramadan only. Ten minutes before Fajr.
    case imsak(displayTime: String, isPast: Bool)

    public var id: String {
        switch self {
        case let .prayer(prayer, _, _, _): "prayer-\(prayer.rawValue)"
        case .sunrise: "sunrise"
        case .imsak: "imsak"
        }
    }

    public var displayTime: String {
        switch self {
        case let .prayer(_, _, time, _), let .sunrise(time), let .imsak(time, _): time
        }
    }
}

/// Which phase of the day `now` falls in. A port of `derivePhase` in `HomeViewModel.kt`.
///
/// Wall-clock, not instants — unlike the countdown. A phase is a property of the *rendered day*:
/// the surface behind a profile pinned to Karachi should be Karachi's afternoon, and comparing
/// its clock times to its own clock is the direct way to say that. The two post-midnight-Isha
/// guards at the top are what make it agree with the instant-based countdown on the days where
/// clock order and timeline order come apart.
public func derivePhase(_ times: PrayerTimesResult, asrMadhab: AsrMadhab, now: ClockTime) -> PrayerPhase {
    if times.isha < times.fajr, now < times.isha { return .maghrib }
    if times.isha < times.fajr, now >= times.maghrib { return .maghrib }
    let asr = asrMadhab == .hanafi ? times.asrHanafi : times.asrShafii
    return switch now {
    case ..<times.fajr: .isha
    case ..<times.sunrise: .fajr
    case ..<times.dhuhr: .sunriseTransition
    case ..<asr: .dhuhr
    case ..<times.maghrib: .asr
    case ..<times.isha: .maghrib
    default: .isha
    }
}

/// The ribbon's rows for one day. A port of `deriveRibbonRows` in `HomeViewModel.kt`.
public func deriveRibbonRows(
    _ times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    now: ClockTime,
    date: CalendarDate,
    isRamadan: Bool,
    formatter: (ClockTime) -> String
) -> [RibbonRow] {
    let asr = asrMadhab == .hanafi ? times.asrHanafi : times.asrShafii
    let prayerTimeline = [times.fajr, times.dhuhr, asr, times.maghrib, times.isha]

    // Prayers that cross midnight (e.g. Isha at 00:25) have a clock time earlier than Fajr.
    // Including them in a naive `<= now` check would incorrectly mark them as passed during the
    // evening. Only count a prayer as passed if it is within the same prayer-day (>= fajr).
    let currentIndex: Int =
        if times.isha < times.fajr, now < times.fajr {
            now >= times.isha ? 4 : 3
        } else {
            prayerTimeline.lastIndex { $0 <= now && $0 >= times.fajr } ?? -1
        }

    func state(at index: Int) -> RibbonState {
        if index < currentIndex { return .passed }
        if index == currentIndex { return .current }
        return .upcoming
    }

    func prayerRow(_ prayer: Prayer, _ time: ClockTime, _ index: Int) -> RibbonRow {
        .prayer(
            prayer: prayer,
            displayName: prayerDisplayName(prayer, on: date),
            displayTime: formatter(time),
            state: state(at: index)
        )
    }

    var rows: [RibbonRow] = []
    if isRamadan {
        let imsak = times.fajr.minusMinutes(10)
        rows.append(.imsak(displayTime: formatter(imsak), isPast: imsak <= now))
    }
    rows.append(prayerRow(.fajr, times.fajr, 0))
    rows.append(.sunrise(displayTime: formatter(times.sunrise)))
    rows.append(prayerRow(.dhuhr, times.dhuhr, 1))
    rows.append(prayerRow(.asr, asr, 2))
    rows.append(prayerRow(.maghrib, times.maghrib, 3))
    rows.append(prayerRow(.isha, times.isha, 4))
    return rows
}

private extension Array where Element == ClockTime {
    func lastIndex(where predicate: (ClockTime) -> Bool) -> Int? {
        for index in indices.reversed() where predicate(self[index]) { return index }
        return nil
    }
}
