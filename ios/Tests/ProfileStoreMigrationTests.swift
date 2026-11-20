import SharedLogic
import SwiftData
import XCTest

@testable import Aynama

final class ProfileStoreMigrationTests: XCTestCase {
    @MainActor
    func testAddingLocationNamesPreservesAnExistingStoreAndPrayerHistory() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("profiles.store")
        try seedLegacyStore(at: url)

        let schema = Schema([ProfileRecord.self, QazaRecord.self])
        let container = try ModelContainer(for: schema, configurations: ModelConfiguration(schema: schema, url: url))
        let context = ModelContext(container)
        let repository = ProfileRepository(context: context)
        var profile = try XCTUnwrap(repository.profile(id: 42))
        XCTAssertEqual(profile.name, "Home")
        XCTAssertEqual(profile.latitude, 21.3871)
        XCTAssertEqual(profile.timezone, "Asia/Riyadh")
        XCTAssertNil(profile.locationName)
        XCTAssertEqual(try context.fetch(FetchDescriptor<QazaRecord>()).first?.status, .missed)
        profile.locationName = "Makkah, Saudi Arabia"
        try repository.update(profile)
        XCTAssertEqual(ProfileRepository(context: ModelContext(container)).profile(id: 42)?.locationName,
                       "Makkah, Saudi Arabia")
    }

    @MainActor
    private func seedLegacyStore(at url: URL) throws {
        try autoreleasepool {
            let schema = Schema([LegacyProfileStore.ProfileRecord.self, QazaRecord.self])
            let container = try ModelContainer(for: schema, configurations: ModelConfiguration(schema: schema, url: url))
            let context = ModelContext(container)
            context.insert(LegacyProfileStore.ProfileRecord())
            context.insert(QazaRecord(profileID: 42, prayer: .fajr, date: CalendarDate(epochDay: 20_000), status: .missed))
            try context.save()
        }
    }
}

/// The shipped schema before locationName was added. The entity name and stored properties
/// match the old app, so opening this disk store exercises the real lightweight migration.
private enum LegacyProfileStore {
    @Model
    final class ProfileRecord {
        @Attribute(.unique) var profileID: Int64
        var name: String
        var latitude: Double
        var longitude: Double
        var calculationMethodRaw: String
        var asrMadhabRaw: String
        var isGps: Bool
        var sortOrder: Int
        var timezone: String
        var useLocationTimezone: Bool
        var hijriOffset: Int
        var hijriOffsetMonthKey: Int

        init() {
            profileID = 42
            name = "Home"
            latitude = 21.3871
            longitude = 39.8688
            calculationMethodRaw = "ummAlQura"
            asrMadhabRaw = "shafii"
            isGps = false
            sortOrder = 0
            timezone = "Asia/Riyadh"
            useLocationTimezone = true
            hijriOffset = 0
            hijriOffsetMonthKey = 0
        }
    }
}
