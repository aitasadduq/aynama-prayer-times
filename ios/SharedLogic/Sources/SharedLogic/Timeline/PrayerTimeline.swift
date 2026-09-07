import Foundation

/// The single source of truth for "what prayer is it, and how long until the next one".
///
/// Every surface that shows a countdown — the home screen, the widgets, the Live Activity, and
/// the watch app once it exists — resolves it here rather than re-deriving slightly different
/// rules from a ``PrayerTimesResult``. The rules are stated once, in ``countdownAt(_:now:window:)``,
/// and the formatting once, in ``PrayerCountdown/formatted()``.
///
/// This is a port of `shared-logic/.../shared/timeline/PrayerTimeline.kt`, which is the product
/// specification (DESIGN.md §19). The rules and the boundaries are identical by construction —
/// `PrayerTimelineTests` ports the Kotlin suite case for case.
///
/// Everything is instant-based rather than ``ClockTime``-based. Wall-clock comparison breaks at
/// every date boundary the app actually hits: Isha after midnight sorts before Fajr, "the next
/// prayer" after Isha lives on tomorrow's date, and a profile pinned to a far-away timezone can
/// produce a day whose times are not in canonical clock order at all.
public enum TimelineEvent: String, CaseIterable, Sendable {
    case fajr

    /// Not a prayer, but a real boundary: it closes the Fajr window, so it is a legitimate
    /// countdown target. It never counts *up* — nothing begins at sunrise.
    case sunrise
    case dhuhr
    case asr
    case maghrib
    case isha

    public var isPrayer: Bool { self != .sunrise }

    /// The prayer this event is, for the surfaces that store prayers rather than events.
    /// Nil for sunrise, which is not one.
    public var prayer: Prayer? {
        switch self {
        case .fajr: .fajr
        case .sunrise: nil
        case .dhuhr: .dhuhr
        case .asr: .asr
        case .maghrib: .maghrib
        case .isha: .isha
        }
    }
}

/// One event of one prayer day, resolved to an absolute instant in the profile's zone.
///
/// `date` is the calendar date the event actually *occurs* on, which is not always the date it
/// was calculated for — see ``buildTimeline(days:asrMadhab:timeZone:)`` on late Isha.
public struct TimelineEntry: Hashable, Sendable {
    public let event: TimelineEvent
    public let date: CalendarDate
    public let time: ClockTime
    public let instant: Date

    public init(event: TimelineEvent, date: CalendarDate, time: ClockTime, instant: Date) {
        self.event = event
        self.date = date
        self.time = time
        self.instant = instant
    }

    /// The name to show for this entry — "Jumuah" for a Friday Dhuhr. See ``prayerDisplayName(_:on:)``.
    ///
    /// On the entry rather than on ``TimelineEvent`` because a prayer's name depends on the day
    /// it falls on. Every surface reads names from here so there is one place to change.
    public var displayName: String { prayerDisplayName(event, on: date) }
}

/// The count-up window: how long a prayer stays "current" after its time before the countdown
/// flips to the next one. Specified in IMPLEMENTATION_PLAN.md Phase 1 and DESIGN.md §19.
public let countUpWindow: Duration = .seconds(30 * 60)

/// Where the clock is relative to the prayer timeline.
///
/// `.elapsed` counts upward from a prayer that has started; `.remaining` counts down towards one
/// that has not. Both carry the same payload so callers can render them uniformly and branch
/// only on the direction.
public enum PrayerCountdown: Hashable, Sendable {
    case elapsed(entry: TimelineEntry, duration: Duration)
    case remaining(entry: TimelineEntry, duration: Duration)

    /// The event being counted from (`.elapsed`) or towards (`.remaining`).
    public var entry: TimelineEntry {
        switch self {
        case let .elapsed(entry, _), let .remaining(entry, _): entry
        }
    }

    /// Always non-negative. Direction is carried by the case, not the sign.
    public var duration: Duration {
        switch self {
        case let .elapsed(_, duration), let .remaining(_, duration): duration
        }
    }

    public var isElapsed: Bool {
        if case .elapsed = self { return true }
        return false
    }

    /// `-HH:MM:SS` while counting down, `HH:MM:SS` while counting up.
    ///
    /// Hours are not wrapped at 24 — a gap longer than a day (possible at high latitudes) reads
    /// as `-31:04:12` rather than silently restarting.
    public func formatted() -> String {
        (isElapsed ? "" : "-") + formatDuration(duration)
    }
}

/// Build the ordered event timeline for `days`, resolved in `timeZone`.
///
/// Pass at least yesterday, today and tomorrow: ``countdownAt(_:now:window:)`` needs an event on
/// each side of `now` at every moment of the day, including the stretch after Isha and the
/// stretch before Fajr. Entries are sorted by instant, so callers never depend on clock ordering.
public func buildTimeline(
    days: [CalendarDate: PrayerTimesResult],
    asrMadhab: AsrMadhab,
    timeZone: TimeZone
) -> [TimelineEntry] {
    days
        .flatMap { entriesFor(date: $0.key, times: $0.value, asrMadhab: asrMadhab, timeZone: timeZone) }
        .sorted { lhs, rhs in
            // Ties keep the day's canonical order, so a collapsed high-latitude day sorts
            // Fajr before Isha rather than however the dictionary happened to enumerate.
            lhs.instant == rhs.instant
                ? canonicalOrder(lhs) < canonicalOrder(rhs)
                : lhs.instant < rhs.instant
        }
}

/// Resolve one calculated day into occurrence-dated entries.
///
/// ``PrayerTimesResult`` carries wall-clock times with the date stripped, so a late Isha — 00:25
/// for a summer northern city — comes back looking like it happened before that morning's Fajr.
/// Any event whose clock time sorts before Fajr's belongs to the following calendar day; that is
/// what "the night of the 12th" means. Without this roll-forward the evening of a long summer day
/// would count *backwards* to an Isha 23 hours in the past.
private func entriesFor(
    date: CalendarDate,
    times: PrayerTimesResult,
    asrMadhab: AsrMadhab,
    timeZone: TimeZone
) -> [TimelineEntry] {
    let asr = asrMadhab == .hanafi ? times.asrHanafi : times.asrShafii
    let pairs: [(TimelineEvent, ClockTime)] = [
        (.fajr, times.fajr),
        (.sunrise, times.sunrise),
        (.dhuhr, times.dhuhr),
        (.asr, asr),
        (.maghrib, times.maghrib),
        (.isha, times.isha),
    ]
    return pairs.map { event, time in
        let occursOn = time < times.fajr ? date.plusDays(1) : date
        return TimelineEntry(
            event: event,
            date: occursOn,
            time: time,
            instant: occursOn.atTime(time, in: timeZone)
        )
    }
}

/// Resolve the countdown state at `now`.
///
/// The rules, in full:
///
/// - Before a prayer, count down towards it. Rendered with a minus sign: `-00:12:35`.
/// - At the prayer instant the sign drops and the clock counts up from `00:00:00`.
/// - It keeps counting up for `window` (30 minutes), or until the next event arrives if that
///   comes sooner — so a short Fajr-to-sunrise gap cannot leave two events "current".
/// - After that it counts down towards the next event again.
///
/// Only prayers count up. Sunrise is a boundary, not an act, so the moment it passes the
/// countdown moves straight on to Dhuhr.
///
/// Returns nil when `timeline` has no event on the needed side of `now` — the caller's timeline
/// is too short, not a state the UI should invent a value for.
public func countdownAt(
    _ timeline: [TimelineEntry],
    now: Date,
    window: Duration = countUpWindow
) -> PrayerCountdown? {
    if let current = currentEntry(timeline, now: now), current.event.isPrayer {
        let elapsed = duration(from: current.instant, to: now)
        if elapsed < window {
            return .elapsed(entry: current, duration: elapsed)
        }
    }
    guard let next = timeline.first(where: { $0.instant > now }) else { return nil }
    return .remaining(entry: next, duration: duration(from: now, to: next.instant))
}

/// The event that is currently under way at `now`: the most recently started one.
///
/// Two events can share an instant. Above roughly 48° latitude Adhan's high-latitude fallback
/// collapses Fajr and Isha onto the same moment, and then "the most recent event" is genuinely
/// ambiguous. Ties resolve to the earliest event in the day's canonical order — at 04:38 on a
/// June morning in London the answer users expect is Fajr, not the previous night's Isha.
public func currentEntry(_ timeline: [TimelineEntry], now: Date) -> TimelineEntry? {
    guard let latest = timeline.last(where: { $0.instant <= now }) else { return nil }
    return timeline.first { $0.instant == latest.instant }
}

/// The instant at which ``countdownAt(_:now:window:)`` would return a different state than it
/// does at `now`.
///
/// Surfaces that cannot tick continuously — widgets, the Live Activity's stale-content deadline —
/// arm an update here instead of guessing at prayer boundaries, which would miss the +30 minute
/// flip from counting up to counting down.
public func nextTransition(
    _ timeline: [TimelineEntry],
    now: Date,
    window: Duration = countUpWindow
) -> Date? {
    let next = timeline.first { $0.instant > now }
    switch countdownAt(timeline, now: now, window: window) {
    case let .elapsed(entry, _):
        let windowEnd = entry.instant.addingTimeInterval(window.seconds)
        if let next, next.instant < windowEnd { return next.instant }
        return windowEnd
    case let .remaining(entry, _):
        return entry.instant
    case nil:
        return nil
    }
}

/// Every transition in `timeline` from `now` onwards, for surfaces that must precompute a
/// schedule rather than being woken at each one.
///
/// WidgetKit takes a whole timeline up front and will not call the provider again until it is
/// spent, so a widget cannot use ``nextTransition(_:now:window:)`` one step at a time the way an
/// Android alarm chain does. This walks the same rule forward and returns every instant at which
/// the rendered state changes.
public func transitions(
    _ timeline: [TimelineEntry],
    from now: Date,
    window: Duration = countUpWindow,
    limit: Int = 64
) -> [Date] {
    var result: [Date] = []
    var cursor = now
    while result.count < limit, let next = nextTransition(timeline, now: cursor, window: window) {
        result.append(next)
        // Step a whole second past the transition. Stepping by the smallest representable
        // amount would let Date's Double resolution round back onto the same instant and spin.
        cursor = next.addingTimeInterval(1)
    }
    return result
}

// MARK: - Duration helpers

/// Exact for whole-second inputs, which every instant in this file is: prayer times are
/// truncated to the second and `atTime` builds from integral components.
func duration(from start: Date, to end: Date) -> Duration {
    .seconds(end.timeIntervalSince(start))
}

extension Duration {
    /// Whole seconds, discarding the attosecond remainder — the countdown's resolution.
    public var seconds: Double { Double(components.seconds) }
}

func formatDuration(_ duration: Duration) -> String {
    let total = Int(max(duration.components.seconds, 0))
    return String(format: "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

/// The day's canonical order, used only to break instant ties deterministically.
private func canonicalOrder(_ entry: TimelineEntry) -> Int {
    TimelineEvent.allCases.firstIndex(of: entry.event) ?? 0
}
