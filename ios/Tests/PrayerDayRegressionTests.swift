import SharedLogic
import SwiftData
import XCTest
@testable import Aynama

final class PrayerDayRegressionTests: XCTestCase {
    private var zurich: Profile {
        Profile(id: 1, name: "Zurich", latitude: 47.3769, longitude: 8.5417,
                calculationMethod: .mwl, asrMadhab: .shafii, timezone: "Europe/Zurich", useLocationTimezone: true)
    }

    @MainActor
    func testMarkingIshaAfterMidnightUsesItsOriginalPrayerDayAndOnTimeWindow() throws {
        let today = CalendarDate(year: 2026, month: 6, day: 22)
        let now = today.atTime(ClockTime(hour: 0, minute: 15), in: zurich.effectiveTimeZone)
        let day = try XCTUnwrap(PrayerSchedule.latestPrayerDay(.isha, profile: zurich, now: now))
        XCTAssertEqual(day, today.minusDays(1))
        let instant = try XCTUnwrap(PrayerSchedule.instant(.isha, profile: zurich, date: day))
        XCTAssertEqual(CalendarDate.from(instant, in: zurich.effectiveTimeZone), today)
        XCTAssertTrue(PrayerSchedule.isInWindow(.isha, profile: zurich, date: day, now: now))
        let container = AynamaStore.makeContainer(inMemory: true)
        let context = ModelContext(container)
        let target = PrayerMarkTarget(profile: zurich, prayer: .isha, date: day)
        try PrayerHistoryRepository(context: context).mark(target, status: .prayedOnTime)
        let records = try ModelContext(container).fetch(FetchDescriptor<QazaRecord>())
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records.first?.date, today.minusDays(1))
        XCTAssertEqual(records.first?.status, .prayedOnTime)
    }

    func testOrdinaryPrayerMarkKeepsTodaysPrayerDay() throws {
        let today = CalendarDate(year: 2026, month: 9, day: 30)
        let now = try XCTUnwrap(PrayerSchedule.instant(.dhuhr, profile: zurich, date: today))
        XCTAssertEqual(PrayerSchedule.latestPrayerDay(.dhuhr, profile: zurich, now: now), today)
    }

    func testSavingAnExpiredHijriAdjustmentRenewsTheSameValueAndZeroClearsIt() {
        var profile = zurich
        let previous = CalendarDate(year: 2026, month: 4, day: 15)
        let today = CalendarDate(year: 2026, month: 6, day: 15)
        let zone = profile.effectiveTimeZone
        profile.setHijriAdjustment(1, now: previous.atTime(ClockTime(hour: 12, minute: 0), in: zone))
        XCTAssertEqual(HijriCalendar.effectiveOffset(profile.hijriOffset, monthKey: profile.hijriOffsetMonthKey,
                                                   on: today, in: zone), 0)
        let now = today.atTime(ClockTime(hour: 12, minute: 0), in: zone)
        profile.setHijriAdjustment(1, now: now)
        XCTAssertEqual(HijriCalendar.effectiveOffset(profile.hijriOffset, monthKey: profile.hijriOffsetMonthKey,
                                                   on: today, in: zone), 1)
        profile.setHijriAdjustment(0, now: now)
        XCTAssertEqual(profile.hijriOffset, 0)
        XCTAssertEqual(profile.hijriOffsetMonthKey, 0)
    }
}
