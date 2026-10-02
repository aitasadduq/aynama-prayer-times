import SharedLogic
import SwiftData
import XCTest

@testable import Aynama

final class ProfileRepositoryTests: XCTestCase {
    private func profile(_ name: String, isGps: Bool = false) -> Profile {
        Profile(name: name, latitude: 21.4225, longitude: 39.8262,
                calculationMethod: .mwl, asrMadhab: .shafii, isGps: isGps)
    }

    @MainActor
    func testInsertAssignsDistinctIDsAndAppendsInPagerOrder() throws {
        let container = AynamaStore.makeContainer(inMemory: true)
        let repository = ProfileRepository(context: ModelContext(container))
        let first = try repository.insert(profile("Home"))
        let second = try repository.insert(profile("Office"))

        XCTAssertNotEqual(first, second)
        XCTAssertEqual(repository.all().map(\.id), [first, second])
        XCTAssertEqual(repository.all().map(\.sortOrder), [0, 1])
        XCTAssertEqual(repository.profile(id: second)?.name, "Office")
    }

    @MainActor
    func testDeletingTheNewestProfileNeverHandsItsIDToTheNextOne() throws {
        let suite = "ProfileRepositoryTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let container = AynamaStore.makeContainer(inMemory: true)
        let repository = ProfileRepository(context: ModelContext(container), defaults: defaults)
        let home = try repository.insert(profile("Home"))
        let newest = try repository.insert(profile("Office"))

        try repository.delete(id: newest)
        let next = try repository.insert(profile("Travel"))

        // A notification or deep link that still names the deleted id must not open Travel.
        XCTAssertNotEqual(next, newest)
        XCTAssertEqual(next, newest + 1)
        XCTAssertEqual(repository.all().map(\.id), [home, next])

        // The mark survives a fresh repository, as it would an app relaunch.
        try repository.delete(id: next)
        let afterRelaunch = try ProfileRepository(context: ModelContext(container), defaults: defaults)
            .insert(profile("Hajj"))
        XCTAssertEqual(afterRelaunch, next + 1)
    }

    @MainActor
    func testUpdatePreservesIdentityAndPersistsFormFields() throws {
        let container = AynamaStore.makeContainer(inMemory: true)
        let repository = ProfileRepository(context: ModelContext(container))
        let id = try repository.insert(profile("Home"))
        var updated = try XCTUnwrap(repository.profile(id: id))
        updated.name = "Travel"
        updated.latitude = 25.2048
        updated.longitude = 55.2708
        updated.calculationMethod = .dubai
        updated.asrMadhab = .hanafi
        updated.useLocationTimezone = true
        updated.timezone = "Asia/Dubai"
        try repository.update(updated)

        // A fresh context reads the saved record, rather than the original context's cache.
        let reloaded = ProfileRepository(context: ModelContext(container))
        let saved = try XCTUnwrap(reloaded.profile(id: id))
        XCTAssertEqual(saved.id, id)
        XCTAssertEqual(saved.name, "Travel")
        XCTAssertEqual(saved.latitude, 25.2048)
        XCTAssertEqual(saved.longitude, 55.2708)
        XCTAssertEqual(saved.calculationMethod, .dubai)
        XCTAssertEqual(saved.asrMadhab, .hanafi)
        XCTAssertTrue(saved.useLocationTimezone)
        XCTAssertEqual(saved.timezone, "Asia/Dubai")
    }

    @MainActor
    func testDeleteCascadesTrackedPrayersAndResequencesRemainingProfiles() throws {
        let container = AynamaStore.makeContainer(inMemory: true)
        let context = ModelContext(container)
        let repository = ProfileRepository(context: context)
        let first = try repository.insert(profile("Home"))
        let second = try repository.insert(profile("Office"))
        let date = CalendarDate(epochDay: 20_000)
        context.insert(QazaRecord(profileID: first, prayer: .fajr, date: date, status: .prayedOnTime))
        context.insert(QazaRecord(profileID: second, prayer: .fajr, date: date, status: .prayedOnTime))
        try context.save()

        try repository.delete(id: first)

        XCTAssertNil(repository.profile(id: first))
        XCTAssertEqual(repository.all().map(\.sortOrder), [0])
        XCTAssertEqual(try context.fetch(FetchDescriptor<QazaRecord>()).map(\.profileID), [second])
    }

    @MainActor
    func testDefaultProfilePrefersGPSOtherwiseFirstInPagerOrder() throws {
        let container = AynamaStore.makeContainer(inMemory: true)
        let repository = ProfileRepository(context: ModelContext(container))
        XCTAssertNil(repository.defaultProfile())
        let first = try repository.insert(profile("Home"))
        XCTAssertEqual(repository.defaultProfile()?.id, first)
        let gps = try repository.insert(profile("Current location", isGps: true))
        XCTAssertEqual(repository.defaultProfile()?.id, gps)
        try repository.delete(id: gps)
        XCTAssertEqual(repository.defaultProfile()?.id, first)
    }

    @MainActor
    func testSelectionSurvivesReloadAndResolvesDeletedProfiles() throws {
        let suite = "ProfileRepositoryTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        var home = profile("Home")
        home.id = 1
        var office = profile("Office")
        office.id = 2
        let selection = SelectedProfile(defaults: defaults)
        selection.id = office.id

        let reloaded = SelectedProfile(defaults: defaults)
        XCTAssertEqual(reloaded.resolve(against: [home, office])?.id, office.id)
        XCTAssertEqual(reloaded.resolve(against: [home])?.id, home.id)
        XCTAssertNil(reloaded.resolve(against: []))
        reloaded.id = nil
        XCTAssertNil(SelectedProfile(defaults: defaults).id)
    }
}
