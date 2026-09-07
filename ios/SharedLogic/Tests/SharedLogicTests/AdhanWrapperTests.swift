import Foundation
import Testing

@testable import SharedLogic

/// A port of `AdhanWrapperTest.kt`, minus the two cases that pin Adhan-Kotlin implementation
/// details Adhan-Swift does not share:
///
/// - *repeated calls are identical* — adhan-java leaks the wall-clock millisecond of the call into
///   every `Date` it returns, so Android has to truncate. Adhan-Swift builds its dates from
///   `DateComponents` with no sub-second field, so there is no noise to truncate. The property
///   still matters here (an alarm armed at one call's instant must recompute to the same prayer),
///   so it is asserted below rather than dropped.
/// - *no sub-second component* — same reason; asserted below on the instants themselves.
@Suite("Adhan wrapper")
struct AdhanWrapperTests {

    let wrapper = AdhanWrapper()
    let riyadh = TimeZone(identifier: "Asia/Riyadh")!
    let oslo = TimeZone(identifier: "Europe/Oslo")!

    /// Makkah: 21.4225°N, 39.8262°E — 2026-03-21 — MWL — Asia/Riyadh.
    ///
    /// Golden values verified against the Adhan 1.2.1 JAR directly (see `AdhanWrapperTest.kt`);
    /// `architecture-design.md` carries stale values from a different source. These are the same
    /// numbers Android asserts, which is the point: the vector contract says both ports agree.
    func makkah() throws -> PrayerTimesResult {
        try wrapper.prayerTimes(
            latitude: 21.4225,
            longitude: 39.8262,
            date: CalendarDate(year: 2026, month: 3, day: 21),
            timeZone: riyadh,
            method: .mwl
        )
    }

    @Test("the Makkah golden values match Adhan-Kotlin within a minute")
    func makkahGoldenValues() throws {
        let times = try makkah()
        expectWithin(ClockTime(hour: 5, minute: 10), times.fajr)
        expectWithin(ClockTime(hour: 6, minute: 24), times.sunrise)
        expectWithin(ClockTime(hour: 12, minute: 29), times.dhuhr)
        expectWithin(ClockTime(hour: 15, minute: 53), times.asrShafii)
        expectWithin(ClockTime(hour: 16, minute: 50), times.asrHanafi)
        expectWithin(ClockTime(hour: 18, minute: 32), times.maghrib)
        expectWithin(ClockTime(hour: 19, minute: 42), times.isha)
    }

    // MARK: - Polar day / polar night

    func timesAt(latitude: Double, date: CalendarDate) throws -> PrayerTimesResult {
        try wrapper.prayerTimes(
            latitude: latitude,
            longitude: 18.9553,
            date: date,
            timeZone: oslo,
            method: .mwl
        )
    }

    @Test("the midnight sun reports times unavailable")
    func midnightSun() {
        #expect(throws: PrayerTimesError.self) {
            try timesAt(latitude: 69.65, date: CalendarDate(year: 2026, month: 6, day: 21))
        }
    }

    @Test("polar night reports times unavailable")
    func polarNight() {
        #expect(throws: PrayerTimesError.self) {
            try timesAt(latitude: 69.65, date: CalendarDate(year: 2026, month: 12, day: 21))
        }
    }

    @Test("the unavailable failure carries the location and date")
    func unavailableCarriesContext() {
        let date = CalendarDate(year: 2026, month: 6, day: 21)
        #expect(throws: PrayerTimesError.unavailable(latitude: 69.65, date: date)) {
            try timesAt(latitude: 69.65, date: date)
        }
    }

    @Test("just south of the midnight-sun band it still computes")
    func justSouthOfTheBand() throws {
        // 65°N on the June solstice is degenerate (Fajr and Isha collapse together) but valid.
        // Pins the boundary so a future high-latitude convention can be checked against it, and
        // feeds the collapsed-day case in PrayerTimelineTests with a real number.
        let times = try timesAt(latitude: 65.0, date: CalendarDate(year: 2026, month: 6, day: 21))
        #expect(times.fajr == times.isha)
    }

    // MARK: - Determinism

    @Test("repeated calls for the same day are identical")
    func repeatedCallsAreIdentical() throws {
        // Anything that arms a notification at one call's prayer instant and then recomputes when
        // it fires depends on this. Android needs a truncation to hold it; Swift gets it free, and
        // this is the test that would notice if that stopped being true.
        let calls = try (1...25).map { _ in try makkah() }
        #expect(Set(calls).count == 1)
    }

    @Test("prayer times carry no sub-second component")
    func noSubSecondComponent() throws {
        let times = try makkah()
        let all = [
            times.fajr, times.sunrise, times.dhuhr,
            times.asrShafii, times.asrHanafi, times.maghrib, times.isha,
        ]
        // ClockTime has no sub-second field by construction; what this pins is that the instant
        // the timeline resolves from it lands on a whole second.
        let date = CalendarDate(year: 2026, month: 3, day: 21)
        for time in all {
            let instant = date.atTime(time, in: riyadh).timeIntervalSince1970
            #expect(instant == instant.rounded())
        }
    }

    // MARK: - Validation

    @Test("invalid coordinates are rejected", arguments: [(91.0, 0.0), (0.0, 181.0), (-90.5, 0.0)])
    func rejectsInvalidCoordinates(coordinate: (Double, Double)) {
        #expect(
            throws: PrayerTimesError.invalidCoordinates(
                latitude: coordinate.0, longitude: coordinate.1
            )
        ) {
            try wrapper.prayerTimes(
                latitude: coordinate.0,
                longitude: coordinate.1,
                date: CalendarDate(year: 2026, month: 3, day: 21),
                timeZone: .gmt,
                method: .mwl
            )
        }
    }

    // MARK: - The timeline window

    @Test("timelineDays drops the undefined days and keeps the rest")
    func timelineDaysDropsUndefinedDays() {
        // Tromsø on the June solstice: the day itself has no times, and neither do its
        // neighbours, so the window is empty rather than partially wrong.
        let polar = Profile(
            name: "Tromsø",
            latitude: 69.65,
            longitude: 18.9553,
            calculationMethod: .mwl,
            asrMadhab: .shafii,
            timezone: "Europe/Oslo",
            useLocationTimezone: true
        )
        let solstice = CalendarDate(year: 2026, month: 6, day: 21)
        #expect(wrapper.timelineDays(for: polar, around: solstice).isEmpty)

        let makkahProfile = Profile(
            name: "Makkah",
            latitude: 21.4225,
            longitude: 39.8262,
            calculationMethod: .mwl,
            asrMadhab: .shafii,
            timezone: "Asia/Riyadh",
            useLocationTimezone: true
        )
        let days = wrapper.timelineDays(
            for: makkahProfile, around: CalendarDate(year: 2026, month: 3, day: 21)
        )
        #expect(days.count == 3)
    }

    func expectWithin(
        _ expected: ClockTime,
        _ actual: ClockTime,
        toleranceMinutes: Int = 1,
        sourceLocation: SourceLocation = #_sourceLocation
    ) {
        let raw = abs(expected.secondOfDay - actual.secondOfDay) / 60
        let wrapped = min(raw, max(24 * 60 - raw, 0))
        #expect(
            wrapped <= toleranceMinutes,
            "Expected \(expected) ±\(toleranceMinutes)m but got \(actual)",
            sourceLocation: sourceLocation
        )
    }
}
