import Foundation
import SharedLogic
import SwiftData

/// Reads and writes profiles. The iOS counterpart of `ProfileRepository.kt`.
///
/// Main-actor bound because SwiftData's `ModelContext` is: everything that touches profiles in
/// this app is a view or a view model, so there is no work to move off it, and pretending
/// otherwise would only add `@Sendable` ceremony around a handful of rows.
@MainActor
struct ProfileRepository {

    let context: ModelContext

    init(context: ModelContext) {
        self.context = context
    }

    func all() -> [Profile] {
        records().map(\.profile)
    }

    /// The single profile screens default to when they do not let the user pick one — Qibla and
    /// the tracker. Prefers the GPS profile, otherwise the lowest `sortOrder`. Shared so those
    /// screens never disagree, exactly as on Android.
    func defaultProfile() -> Profile? {
        let profiles = all()
        return profiles.first(where: \.isGps) ?? profiles.min { $0.sortOrder < $1.sortOrder }
    }

    func profile(id: Int64) -> Profile? {
        record(id: id)?.profile
    }

    /// Persist a new profile, appended last, and return the id it was given.
    ///
    /// The caller needs the id to land the pager on the new profile, which is the whole point of
    /// the FAB flow (DESIGN.md §21): the user lands on what they just made.
    @discardableResult
    func insert(_ profile: Profile) throws -> Int64 {
        let existing = records()
        var toInsert = profile
        toInsert.id = (existing.map(\.profileID).max() ?? 0) + 1
        toInsert.sortOrder = existing.count
        context.insert(ProfileRecord(from: toInsert))
        try context.save()
        return toInsert.id
    }

    func update(_ profile: Profile) throws {
        guard let record = record(id: profile.id) else { return }
        record.apply(profile)
        try context.save()
    }

    func delete(id: Int64) throws {
        guard let record = record(id: id) else { return }
        context.delete(record)
        // Qaza entries are the user's own record of their prayers. SwiftData has no cascade here
        // (they are not a relationship — the widget extension reads both by id, and a
        // relationship would drag the whole object graph into the extension), so the cascade
        // Android gets from Room's ForeignKey is done explicitly.
        for entry in qazaRecords(profileID: id) {
            context.delete(entry)
        }
        try context.save()
        try resequence()
    }

    /// Keep `sortOrder` dense after a delete so the pager's page indices stay the profile order.
    private func resequence() throws {
        for (index, record) in records().enumerated() where record.sortOrder != index {
            record.sortOrder = index
        }
        try context.save()
    }

    private func records() -> [ProfileRecord] {
        let descriptor = FetchDescriptor<ProfileRecord>(
            sortBy: [SortDescriptor(\.sortOrder), SortDescriptor(\.profileID)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    private func record(id: Int64) -> ProfileRecord? {
        var descriptor = FetchDescriptor<ProfileRecord>(predicate: #Predicate { $0.profileID == id })
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }

    private func qazaRecords(profileID: Int64) -> [QazaRecord] {
        let descriptor = FetchDescriptor<QazaRecord>(
            predicate: #Predicate { $0.profileID == profileID }
        )
        return (try? context.fetch(descriptor)) ?? []
    }
}

/// Which profile the app is showing, and which one a tap asked for.
///
/// In the App Group's `UserDefaults` rather than SwiftData because the widget extension writes
/// nothing and only needs to read one number, and because it must survive the store being
/// rebuilt — a lost selection should not look like a lost profile.
@MainActor
final class SelectedProfile: ObservableObject {

    static let key = "selected_profile_id"

    private let defaults: UserDefaults

    @Published var id: Int64? {
        didSet {
            if let id { defaults.set(Int(id), forKey: Self.key) } else { defaults.removeObject(forKey: Self.key) }
        }
    }

    init(defaults: UserDefaults? = nil) {
        let store = defaults ?? UserDefaults(suiteName: AynamaStore.appGroupIdentifier) ?? .standard
        self.defaults = store
        let stored = store.object(forKey: Self.key) as? Int
        id = stored.map(Int64.init)
    }

    /// Resolve the selection against the profiles that actually exist.
    ///
    /// A stale id — the selected profile was deleted, or a widget names one that is gone — falls
    /// back to the first profile rather than showing an empty pager. Returns nil only when there
    /// are genuinely no profiles.
    func resolve(against profiles: [Profile]) -> Profile? {
        if let id, let match = profiles.first(where: { $0.id == id }) { return match }
        return profiles.first
    }
}
