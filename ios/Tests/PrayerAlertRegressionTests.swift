import SharedLogic
import SwiftData
@preconcurrency import UserNotifications
import XCTest
@testable import Aynama

final class PrayerAlertRegressionTests: XCTestCase {
    private var london: Profile {
        Profile(id: 1, name: "London", latitude: 51.5074, longitude: -0.1278,
                calculationMethod: .mwl, asrMadhab: .shafii, timezone: "Europe/London", useLocationTimezone: true)
    }

    func testFixedIshaOccursExactlyOncePerCalendarDayAcrossBothMidnightTransitions() throws {
        for start in [CalendarDate(year: 2026, month: 5, day: 21), CalendarDate(year: 2026, month: 7, day: 22)] {
            let zone = london.effectiveTimeZone
            let before = try XCTUnwrap(PrayerSchedule.instant(.isha, profile: london, date: start))
            let after = try XCTUnwrap(PrayerSchedule.instant(.isha, profile: london, date: start.plusDays(1)))
            let beforeCrossesMidnight = CalendarDate.from(before, in: zone) != start
            let afterCrossesMidnight = CalendarDate.from(after, in: zone) != start.plusDays(1)
            XCTAssertNotEqual(beforeCrossesMidnight, afterCrossesMidnight, "Fixture must cross midnight")
            var configurations = Dictionary(uniqueKeysWithValues: Prayer.allCases.map {
                ($0, PrayerAlertConfiguration(enabled: false))
            })
            configurations[.isha] = PrayerAlertConfiguration(fixedMinutes: 22 * 60)
            let plan = PrayerAlertPlan.build(profile: london, configurations: configurations, imsak: false,
                                            now: start.atTime(ClockTime(hour: 0, minute: 0), in: zone))
            let expected = (0..<7).map { start.plusDays($0).atTime(ClockTime(hour: 22, minute: 0), in: zone) }
            XCTAssertEqual(plan.map(\.instant).sorted(), expected)
            XCTAssertEqual(Set(plan.map(\.id)).count, expected.count)
        }
    }

    @MainActor
    func testForegroundAlertsPresentBannersAndRespectSilentContent() {
        let content = UNMutableNotificationContent()
        content.sound = .default
        XCTAssertEqual(AynamaAppDelegate.presentationOptions(for: content), [.banner, .list, .sound])
        content.sound = nil
        XCTAssertEqual(AynamaAppDelegate.presentationOptions(for: content), [.banner, .list])
    }

    @MainActor
    func testMasterOffAndProfileSwitchSupersedeAnInFlightBackgroundAdd() async {
        for latestIdentifiers in [[], ["aynama.new-profile.fajr", "aynama.new-profile.isha"]] {
            let store = SuspendedNotificationStore()
            let scheduler = PrayerAlertScheduler(store: store)
            let old = scheduler.replace(with: [request("aynama.old-profile.fajr"), request("aynama.old-profile.isha")])
            await store.waitForSuspendedAdd()
            // The old add completes AFTER the user's new choice has reached the scheduler.
            let latest = scheduler.replace(with: latestIdentifiers.map(request))
            store.releaseAdd()
            let oldSucceeded = await old.value
            let latestSucceeded = await latest.value
            XCTAssertFalse(oldSucceeded)
            XCTAssertTrue(latestSucceeded)
            XCTAssertEqual(store.identifiers, Set(latestIdentifiers + ["unrelated.notification"]))
            XCTAssertFalse(store.added.contains("aynama.old-profile.isha"))
        }
    }

    @MainActor
    func testExpiredBackgroundRefillPreservesPreviouslyScheduledMatchingAlerts() async {
        let identifiers = ["aynama.profile.fajr", "aynama.profile.dhuhr", "aynama.profile.isha"]
        let store = SuspendedNotificationStore()
        store.identifiers.formUnion(identifiers)
        let scheduler = PrayerAlertScheduler(store: store)
        let work = scheduler.replace(with: identifiers.map(request))
        await store.waitForSuspendedAdd()
        work.cancel()
        store.releaseAdd()
        let succeeded = await work.value
        XCTAssertFalse(succeeded)
        XCTAssertEqual(store.identifiers, Set(identifiers + ["unrelated.notification"]))
    }

    @MainActor
    func testDeletingAProfileClearsItsAlertPreferencesAndItsIDIsNotReused() throws {
        let suite = "PrayerAlertRegressionTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let container = AynamaStore.makeContainer(inMemory: true)
        let repository = ProfileRepository(context: ModelContext(container), defaults: defaults)
        let first = try repository.insert(london)
        let deleted = try repository.insert(london)
        let settings = PrayerAlertSettings(defaults: defaults)
        let custom = PrayerAlertConfiguration(enabled: false, fixedMinutes: 420)
        settings.set(custom, profileID: first, prayer: .fajr)
        settings.set(custom, profileID: deleted, prayer: .fajr)
        settings.profileID = deleted
        try repository.delete(id: deleted)
        settings.removeProfile(id: deleted)
        let recreated = try repository.insert(london)
        XCTAssertNotEqual(recreated, deleted, "A deleted profile's ID is never handed out again")
        let reloaded = PrayerAlertSettings(defaults: defaults)
        XCTAssertNil(reloaded.profileID)
        XCTAssertEqual(reloaded.configuration(profileID: deleted, prayer: .fajr), .init())
        XCTAssertEqual(reloaded.configuration(profileID: recreated, prayer: .fajr), .init())
        XCTAssertEqual(reloaded.configuration(profileID: first, prayer: .fajr), custom)
    }

    private func request(_ id: String) -> UNNotificationRequest {
        UNNotificationRequest(identifier: id, content: UNMutableNotificationContent(), trigger: nil)
    }
}

@MainActor
private final class SuspendedNotificationStore: PrayerNotificationStore {
    var identifiers: Set<String> = ["unrelated.notification"]
    var added: [String] = []
    private var suspendedAdd: CheckedContinuation<Void, Never>?
    private var observer: CheckedContinuation<Void, Never>?
    private var shouldSuspend = true

    func pendingIdentifiers() async -> [String] { Array(identifiers) }
    func removePending(withIdentifiers identifiers: [String]) { self.identifiers.subtract(identifiers) }
    func add(_ request: UNNotificationRequest) async throws {
        if shouldSuspend {
            shouldSuspend = false
            await withCheckedContinuation { continuation in
                suspendedAdd = continuation
                observer?.resume()
                observer = nil
            }
        }
        added.append(request.identifier)
        identifiers.insert(request.identifier)
    }
    func waitForSuspendedAdd() async {
        if suspendedAdd != nil { return }
        await withCheckedContinuation { observer = $0 }
    }
    func releaseAdd() {
        suspendedAdd?.resume()
        suspendedAdd = nil
    }
}
