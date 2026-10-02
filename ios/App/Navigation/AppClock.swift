import Foundation

/// Only debug simulator tests can freeze time; release builds always use the device clock.
enum AppClock {
    static var now: Date {
        #if DEBUG
        if let text = ProcessInfo.processInfo.environment["AYNAMA_TEST_NOW"],
           let date = ISO8601DateFormatter().date(from: text) { return date }
        #endif
        return .now
    }
}

extension AynamaStore {
    static var preferences: UserDefaults {
        #if DEBUG
        if let value = ProcessInfo.processInfo.environment["AYNAMA_UI_TEST_STORE"],
           let id = UUID(uuidString: value),
           let defaults = UserDefaults(suiteName: "ui-test-\(id.uuidString)") { return defaults }
        #endif
        return UserDefaults(suiteName: appGroupIdentifier) ?? .standard
    }
}
