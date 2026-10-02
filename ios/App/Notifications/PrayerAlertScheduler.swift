import Foundation
@preconcurrency import UserNotifications

@MainActor
protocol PrayerNotificationStore: AnyObject {
    func pendingIdentifiers() async -> [String]
    func removePending(withIdentifiers identifiers: [String])
    func add(_ request: UNNotificationRequest) async throws
}

@MainActor
private final class SystemPrayerNotificationStore: PrayerNotificationStore {
    private let center = UNUserNotificationCenter.current()

    func pendingIdentifiers() async -> [String] {
        await center.pendingNotificationRequests().map(\.identifier)
    }
    func removePending(withIdentifiers identifiers: [String]) {
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
    }
    func add(_ request: UNNotificationRequest) async throws {
        try await center.add(request)
    }
}

/// One writer for the process. A replacement waits for any in-flight add to finish before
/// removing the old queue, so a superseded foreground or background refill cannot add it back.
@MainActor
final class PrayerAlertScheduler {
    static let shared = PrayerAlertScheduler(store: SystemPrayerNotificationStore())
    private let store: any PrayerNotificationStore
    private var generation = 0
    private var tail: Task<Bool, Never>?

    init(store: any PrayerNotificationStore) { self.store = store }

    func replace(with requests: [UNNotificationRequest]) -> Task<Bool, Never> {
        generation += 1
        let revision = generation
        let previous = tail
        let work = Task { @MainActor [self] in
            if let previous { _ = await previous.value }
            guard revision == generation, !Task.isCancelled else { return false }
            let pending = await store.pendingIdentifiers()
            guard revision == generation, !Task.isCancelled else { return false }
            // Upsert matching IDs so expiration during a routine refill preserves its horizon.
            let desired = Set(requests.map(\.identifier))
            store.removePending(withIdentifiers: pending.filter { $0.hasPrefix("aynama.") && !desired.contains($0) })
            var succeeded = true
            for request in requests {
                guard revision == generation, !Task.isCancelled else { return false }
                do { try await store.add(request) }
                catch { succeeded = false }
            }
            return succeeded && revision == generation && !Task.isCancelled
        }
        tail = work
        return work
    }
}
