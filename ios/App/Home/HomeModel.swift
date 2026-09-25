import Foundation
import Observation
import SharedLogic

/// One page of the home pager, ready to render.
struct ProfileUiState: Equatable {
    let profile: Profile
    let ribbonRows: [RibbonRow]
    /// Already signed and padded per DESIGN.md §19 — render it verbatim.
    let countdownText: String
    /// True while counting up from a prayer that has started; false while counting down to one.
    let countdownIsElapsed: Bool
    /// The prayer the countdown refers to: the one just started, or the one coming next.
    let countdownPrayerName: String
    let countdownPrayerTime: String
    let phase: PrayerPhase
    let isRamadan: Bool
    let hijriDateText: String
}

/// A page of the pager.
///
/// Failure is per profile, not per screen: a location whose times cannot be calculated becomes an
/// `.unavailable` page and every other profile still renders. Folding it into a screen-wide error
/// would blank the whole pager, including profiles that are perfectly fine.
enum ProfilePage: Equatable, Identifiable {
    case ready(ProfileUiState)
    case unavailable(profile: Profile, reason: String)

    var profile: Profile {
        switch self {
        case let .ready(state): state.profile
        case let .unavailable(profile, _): profile
        }
    }

    var id: Int64 { profile.id }
}

/// Drives the Prayers screen. The iOS counterpart of `HomeViewModel.kt`.
///
/// Ticks once a second because the countdown shows seconds and DESIGN.md §8 forbids a per-minute
/// tick that would visibly stall. The tick is a `Task`, not a `Timer`, so it stops with the view
/// rather than outliving it.
@MainActor
@Observable
final class HomeModel {

    private(set) var pages: [ProfilePage] = []
    private(set) var now: Date = .now

    private let adhan = AdhanWrapper()
    private var tickTask: Task<Void, Never>?

    /// Cache key for a day's times. Everything the calculation depends on is in it, so a profile
    /// edit invalidates only the entries it should.
    private struct CacheKey: Hashable {
        let profileID: Int64
        let date: CalendarDate
        let latitude: Double
        let longitude: Double
        let method: CalculationMethodKey
        let timeZone: String
    }

    /// A nil value means "adhan has no times for that day" (polar day or night). Cached like any
    /// other answer so a profile inside the polar circle does not re-run the calculation on every
    /// tick.
    private var timesCache: [CacheKey: PrayerTimesResult?] = [:]
    private var hijriCache: [CacheKey2: String] = [:]
    private var ramadanCache: [CacheKey2: Bool] = [:]

    private struct CacheKey2: Hashable {
        let date: CalendarDate
        let offset: Int
        let timeZone: String
    }

    private let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.setLocalizedDateFormatFromTemplate("jmm")
        return formatter
    }()

    func start() {
        guard tickTask == nil else { return }
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                // Ends the loop when the model is gone, rather than spinning on a nil optional
                // once a second forever. There is no `deinit` to cancel it from: `tickTask` is
                // main-actor isolated and `deinit` is not, so the loop has to end itself.
                guard let self else { return }
                self.now = .now
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    func stop() {
        tickTask?.cancel()
        tickTask = nil
    }

    /// Rebuild every page against `profiles` at the current tick.
    func refresh(profiles: [Profile]) {
        let hijriYear = HijriCalendar.currentHijriYear()
        pages = profiles.map { profile in
            let zone = profile.effectiveTimeZone
            let today = CalendarDate.from(now, in: zone)
            guard let times = cachedTimes(profile, on: today) else {
                return .unavailable(profile: profile, reason: Self.polarReason)
            }
            return .ready(state(for: profile, times: times, today: today, hijriYear: hijriYear))
        }
    }

    // MARK: - Derivation

    private func state(
        for profile: Profile,
        times: PrayerTimesResult,
        today: CalendarDate,
        hijriYear: Int
    ) -> ProfileUiState {
        let zone = profile.effectiveTimeZone
        let localNow = ClockTime.from(now, in: zone)
        let offset = HijriCalendar.effectiveOffset(
            profile.hijriOffset, monthKey: profile.hijriOffsetMonthKey, on: today, in: zone
        )
        let ramadan = cachedIsRamadan(today, offset: offset, zone: zone)
        let countdown = countdownAt(timeline(for: profile, around: today), now: now)

        return ProfileUiState(
            profile: profile,
            ribbonRows: deriveRibbonRows(
                times,
                asrMadhab: profile.asrMadhab,
                now: localNow,
                date: today,
                isRamadan: ramadan,
                formatter: { [self] in format($0, in: zone, on: today) }
            ),
            countdownText: countdown?.formatted() ?? Self.noCountdown,
            countdownIsElapsed: countdown?.isElapsed ?? false,
            countdownPrayerName: countdown?.entry.displayName ?? "",
            countdownPrayerTime: countdown.map { format($0.entry.time, in: zone, on: $0.entry.date) } ?? "",
            phase: derivePhase(times, asrMadhab: profile.asrMadhab, now: localNow),
            isRamadan: ramadan,
            hijriDateText: cachedHijri(today, offset: offset, zone: zone)
        )
    }

    /// Yesterday, today and tomorrow. Both neighbours are required: before Fajr the current event
    /// is last night's Isha, and after Isha the next one is tomorrow's Fajr.
    private func timeline(for profile: Profile, around today: CalendarDate) -> [TimelineEntry] {
        var days: [CalendarDate: PrayerTimesResult] = [:]
        for offset in -1...1 {
            let date = today.plusDays(offset)
            if let times = cachedTimes(profile, on: date) { days[date] = times }
        }
        return buildTimeline(days: days, asrMadhab: profile.asrMadhab, timeZone: profile.effectiveTimeZone)
    }

    private func cachedTimes(_ profile: Profile, on date: CalendarDate) -> PrayerTimesResult? {
        let zone = profile.effectiveTimeZone
        let key = CacheKey(
            profileID: profile.id,
            date: date,
            latitude: profile.latitude,
            longitude: profile.longitude,
            method: profile.calculationMethod,
            timeZone: zone.identifier
        )
        if let cached = timesCache[key] { return cached }

        let computed = try? adhan.prayerTimes(
            latitude: profile.latitude,
            longitude: profile.longitude,
            date: date,
            timeZone: zone,
            method: profile.calculationMethod
        )
        timesCache[key] = computed

        // The countdown needs yesterday and tomorrow as well as today, so the cache grows three
        // entries a day rather than one. This process can live for days; keep only the days a
        // timeline can still reach.
        let reachable = (date.epochDay - 2)...(date.epochDay + 2)
        timesCache = timesCache.filter { reachable.contains($0.key.date.epochDay) }
        return computed
    }

    private func cachedHijri(_ date: CalendarDate, offset: Int, zone: TimeZone) -> String {
        let key = CacheKey2(date: date, offset: offset, timeZone: zone.identifier)
        if let cached = hijriCache[key] { return cached }
        let value = HijriCalendar.displayString(date, offsetDays: offset, in: zone)
        hijriCache[key] = value
        return value
    }

    private func cachedIsRamadan(_ date: CalendarDate, offset: Int, zone: TimeZone) -> Bool {
        let key = CacheKey2(date: date, offset: offset, timeZone: zone.identifier)
        if let cached = ramadanCache[key] { return cached }
        let value = HijriCalendar.isRamadan(date, offsetDays: offset, in: zone)
        ramadanCache[key] = value
        return value
    }

    /// A clock time in the profile's zone, in the user's locale ("1:00 PM" / "13:00").
    ///
    /// The formatter is given the zone explicitly rather than being left on the device's, so a
    /// profile pinned to Karachi reads Karachi's clock on a phone in London.
    private func format(_ time: ClockTime, in zone: TimeZone, on date: CalendarDate) -> String {
        timeFormatter.timeZone = zone
        return timeFormatter.string(from: date.atTime(time, in: zone))
    }

    // Only reachable when today has times but neither neighbouring day does, so the timeline has
    // no event on one side of now. Em dashes rather than "00:00:00", which would read as a prayer
    // that has just started.
    static let noCountdown = "--:--:--"

    static let polarReason = """
        The sun doesn't fully rise or set at this location today, so there are no times to \
        calculate from. This happens inside the polar circles around midsummer and midwinter. \
        Other profiles are unaffected.
        """
}
