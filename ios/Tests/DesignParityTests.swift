import SharedLogic
import SwiftData
import XCTest
@testable import Aynama

final class DesignParityTests: XCTestCase {
    private var london: Profile {
        Profile(id: 1, name: "London", latitude: 51.5074, longitude: -0.1278,
                calculationMethod: .mwl, asrMadhab: .shafii, timezone: "Europe/London", useLocationTimezone: true)
    }

    @MainActor
    func testHistoryUpdatesOneEntryAndKeepsTheOriginalPrayerDay() throws {
        let container = AynamaStore.makeContainer(inMemory: true)
        let context = ModelContext(container)
        let day = CalendarDate(year: 2026, month: 9, day: 28)
        let target = PrayerMarkTarget(profile: london, prayer: .fajr, date: day)
        let repository = PrayerHistoryRepository(context: context)
        try repository.mark(target, status: .missed)
        try repository.mark(target, status: .madeUp)
        let records = try ModelContext(container).fetch(FetchDescriptor<QazaRecord>())
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records.first?.date, day)
        XCTAssertEqual(records.first?.status, .madeUp)
    }

    func testOnTimeWindowEndsAtSunriseAndUsesTheProfileZone() throws {
        let day = CalendarDate(year: 2026, month: 9, day: 30)
        let entries = PrayerSchedule.entries(profile: london, date: day)
        let fajr = try XCTUnwrap(entries.first { $0.event == .fajr })
        let sunrise = try XCTUnwrap(entries.first { $0.event == .sunrise })
        XCTAssertFalse(PrayerSchedule.isInWindow(.fajr, profile: london, date: day, now: fajr.instant.addingTimeInterval(-1)))
        XCTAssertTrue(PrayerSchedule.isInWindow(.fajr, profile: london, date: day, now: fajr.instant))
        XCTAssertFalse(PrayerSchedule.isInWindow(.fajr, profile: london, date: day, now: sunrise.instant))
    }

    func testAllPrayerAndReminderOptionsFitTheWholeDayBudget() throws {
        let day = CalendarDate(year: 2026, month: 2, day: 20)
        let now = day.atTime(ClockTime(hour: 0, minute: 0), in: london.effectiveTimeZone)
        var configurations: [Prayer: PrayerAlertConfiguration] = [:]
        for prayer in Prayer.allCases {
            var config = PrayerAlertConfiguration()
            config.earlyMinutes = 15
            configurations[prayer] = config
        }
        let plan = PrayerAlertPlan.build(profile: london, configurations: configurations, imsak: true, now: now)
        XCTAssertLessThanOrEqual(plan.count, 60)
        XCTAssertEqual(Set(plan.filter { !$0.isReminder && !$0.isImsak }.map { CalendarDate.from($0.instant, in: london.effectiveTimeZone) }).count, 5)
        XCTAssertEqual(plan.filter(\.isReminder).count, 25)
        XCTAssertEqual(plan.filter(\.isImsak).count, 5)
        XCTAssertEqual(Set(plan.map(\.id)).count, plan.count)
    }

    func testAlertOffsetAndFixedTimeDoNotChangeCalculatedPrayerTime() throws {
        let day = CalendarDate(year: 2026, month: 9, day: 30)
        let scheduled = try XCTUnwrap(PrayerSchedule.instant(.fajr, profile: london, date: day))
        var config = PrayerAlertConfiguration()
        config.offsetMinutes = -10
        XCTAssertEqual(PrayerAlertPlan.alertTime(prayer: .fajr, profile: london, date: day, configuration: config), scheduled.addingTimeInterval(-600))
        config.fixedMinutes = 360
        XCTAssertEqual(PrayerAlertPlan.alertTime(prayer: .fajr, profile: london, date: day, configuration: config), day.atTime(ClockTime(hour: 6, minute: 0), in: london.effectiveTimeZone))
        XCTAssertEqual(PrayerSchedule.instant(.fajr, profile: london, date: day), scheduled)
    }

    @MainActor
    func testAlertPreferencesAreScopedToEachProfileAndSurviveRelaunch() throws {
        let suite = "DesignParityTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = PrayerAlertSettings(defaults: defaults)
        var config = PrayerAlertConfiguration()
        config.enabled = false
        config.fixedMinutes = 420
        settings.set(config, profileID: 1, prayer: .fajr)
        settings.enabled = true
        settings.profileID = 2
        let reloaded = PrayerAlertSettings(defaults: defaults)
        XCTAssertEqual(reloaded.configuration(profileID: 1, prayer: .fajr), config)
        XCTAssertTrue(reloaded.configuration(profileID: 2, prayer: .fajr).enabled)
        XCTAssertEqual(reloaded.profileID, 2)
        XCTAssertTrue(reloaded.enabled)
    }
}
