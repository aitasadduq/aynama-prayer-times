import Foundation
import OSLog
import SharedLogic
import SwiftData

/// Representative data for simulator screenshot tests. Never reachable in a release build.
enum ScreenshotFixtures {
    @MainActor
    static func seed(_ context: ModelContext) {
        #if DEBUG
        guard ProcessInfo.processInfo.environment["AYNAMA_SCREENSHOT_FIXTURES"] == "1" else { return }
        let repository = ProfileRepository(context: context)
        guard repository.all().isEmpty else { return }
        do {
            let london = try repository.insert(Profile(name: "London", latitude: 51.5074, longitude: -0.1278,
                calculationMethod: .mwl, asrMadhab: .shafii, timezone: "Europe/London", useLocationTimezone: true))
            try repository.insert(Profile(name: "Dubai", latitude: 25.2048, longitude: 55.2708,
                calculationMethod: .dubai, asrMadhab: .hanafi, timezone: "Asia/Dubai", useLocationTimezone: true))
            let today = CalendarDate.from(AppClock.now, in: TimeZone(identifier: "Europe/London")!)
            for offset in 1...8 {
                for prayer in Prayer.allCases {
                    context.insert(QazaRecord(profileID: london, prayer: prayer, date: today.minusDays(offset),
                                              status: offset == 2 && prayer == .fajr ? .missed : offset == 3 && prayer == .asr ? .madeUp : .prayedOnTime))
                }
            }
            context.insert(QazaRecord(profileID: london, prayer: .fajr, date: today, status: .prayedOnTime))
            try context.save()
            AynamaStore.preferences.set(true, forKey: "prayer_alerts_enabled")
        } catch { AynamaStore.logger.error("Couldn't seed simulator fixtures: \(error)") }
        #endif
    }
}
