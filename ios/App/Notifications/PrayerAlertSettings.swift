import SharedLogic
import SwiftUI
@preconcurrency import UserNotifications

struct PrayerAlertConfiguration: Codable, Equatable, Sendable {
    var enabled = true
    var offsetMinutes = 0
    var fixedMinutes: Int?
    var earlyMinutes = 0
}

enum AdhanVoice: String, CaseIterable, Codable {
    case makkah, madinah, egyptian, turkish, alAqsa, none
    var name: String {
        switch self {
        case .makkah: "Makkah"
        case .madinah: "Madinah"
        case .egyptian: "Egyptian"
        case .turkish: "Turkish"
        case .alAqsa: "Al-Aqsa"
        case .none: "None"
        }
    }
    var caption: String {
        switch self {
        case .makkah: "Al-Masjid Al-Haram"
        case .madinah: "Al-Masjid An-Nabawi"
        case .egyptian: "Abdul Basit Abdus Samad"
        case .turkish: "Diyanet İşleri"
        case .alAqsa: ""
        case .none: "Silent — no audio"
        }
    }
}

struct PlannedPrayerAlert: Equatable {
    let id: String
    let prayer: Prayer
    let instant: Date
    let isReminder: Bool
    let isImsak: Bool
    let title: String
}

/// Whole-day horizon and 60-slot budget from DESIGN.md §24. Sunrise never gets an alert.
enum PrayerAlertPlan {
    static func alertTime(prayer: Prayer, profile: Profile, date: CalendarDate,
                          configuration: PrayerAlertConfiguration) -> Date? {
        guard let scheduled = PrayerSchedule.instant(prayer, profile: profile, date: date) else { return nil }
        if let fixed = configuration.fixedMinutes {
            let occurrenceDate = CalendarDate.from(scheduled, in: profile.effectiveTimeZone)
            return occurrenceDate.atTime(ClockTime(hour: fixed / 60, minute: fixed % 60), in: profile.effectiveTimeZone)
        }
        return scheduled.addingTimeInterval(Double(configuration.offsetMinutes * 60))
    }

    static func build(profile: Profile, configurations: [Prayer: PrayerAlertConfiguration],
                      imsak: Bool, now: Date) -> [PlannedPrayerAlert] {
        let today = CalendarDate.from(now, in: profile.effectiveTimeZone)
        let enabled = Prayer.allCases.filter { configurations[$0, default: .init()].enabled }
        let base = enabled.count + enabled.filter { configurations[$0, default: .init()].earlyMinutes > 0 }.count
        let ramadan = (0..<7).contains { offset in
            let day = today.plusDays(offset)
            let adjustment = HijriCalendar.effectiveOffset(profile.hijriOffset, monthKey: profile.hijriOffsetMonthKey,
                                                           on: day, in: profile.effectiveTimeZone)
            return HijriCalendar.isRamadan(day, offsetDays: adjustment, in: profile.effectiveTimeZone)
        }
        let perDay = base + (imsak && ramadan ? 1 : 0)
        guard perDay > 0 else { return [] }
        let horizon = min(7, max(3, 60 / perDay))
        var alerts: [PlannedPrayerAlert] = []
        let horizonEnd = today.plusDays(horizon).atTime(ClockTime(hour: 0, minute: 0), in: profile.effectiveTimeZone)
        // Yesterday's Isha can occur after today's midnight. Keep it when refilling, then
        // bound the queue by occurrence day rather than the day the times were calculated for.
        for offset in -1..<horizon {
            let day = today.plusDays(offset)
            var reminders: [PlannedPrayerAlert] = []
            for prayer in enabled {
                let config = configurations[prayer, default: .init()]
                guard let instant = alertTime(prayer: prayer, profile: profile, date: day, configuration: config) else { continue }
                let id = "aynama.\(profile.id).\(day).\(prayer.rawValue)"
                if instant > now {
                    alerts.append(.init(id: id, prayer: prayer, instant: instant, isReminder: false, isImsak: false,
                                        title: prayerDisplayName(prayer, on: day)))
                }
                let reminderTime = instant.addingTimeInterval(Double(-config.earlyMinutes * 60))
                if config.earlyMinutes > 0, reminderTime > now {
                    reminders.append(.init(id: id + ".early", prayer: prayer, instant: reminderTime,
                                           isReminder: true, isImsak: false,
                                           title: "\(prayerDisplayName(prayer, on: day)) in \(config.earlyMinutes) minutes"))
                }
            }
            let adjustment = HijriCalendar.effectiveOffset(profile.hijriOffset, monthKey: profile.hijriOffsetMonthKey,
                                                           on: day, in: profile.effectiveTimeZone)
            if imsak, HijriCalendar.isRamadan(day, offsetDays: adjustment, in: profile.effectiveTimeZone),
               let fajr = PrayerSchedule.instant(.fajr, profile: profile, date: day) {
                let instant = fajr.addingTimeInterval(-600)
                if instant > now {
                    alerts.append(.init(id: "aynama.\(profile.id).\(day).imsak", prayer: .fajr, instant: instant,
                                        isReminder: false, isImsak: true, title: "Ramadan Imsak"))
                }
            }
            alerts.append(contentsOf: reminders)
        }
        return Array(alerts.filter { $0.instant < horizonEnd }.prefix(60))
    }
}

@MainActor
final class PrayerAlertSettings: ObservableObject {
    @Published private(set) var revision = 0
    @Published private(set) var authorization: UNAuthorizationStatus = .notDetermined
    @Published private(set) var schedulingError: String?
    private let defaults: UserDefaults
    private var generation = 0
    private var configurations: [String: PrayerAlertConfiguration]

    init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? AynamaStore.preferences
        configurations = self.defaults.data(forKey: "prayer_alert_configurations")
            .flatMap { try? JSONDecoder().decode([String: PrayerAlertConfiguration].self, from: $0) } ?? [:]
    }
    var enabled: Bool {
        get { defaults.bool(forKey: "prayer_alerts_enabled") }
        set { defaults.set(newValue, forKey: "prayer_alerts_enabled"); revision += 1 }
    }
    var profileID: Int64? {
        get { (defaults.object(forKey: "prayer_alert_profile") as? Int).map(Int64.init) }
        set { defaults.set(newValue.map { Int($0) }, forKey: "prayer_alert_profile"); revision += 1 }
    }
    var voice: AdhanVoice {
        get { AdhanVoice(rawValue: defaults.string(forKey: "adhan_voice") ?? "") ?? .makkah }
        set { defaults.set(newValue.rawValue, forKey: "adhan_voice"); revision += 1 }
    }
    var imsak: Bool {
        get { defaults.object(forKey: "imsak_alert") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "imsak_alert"); revision += 1 }
    }
    func configuration(profileID: Int64, prayer: Prayer) -> PrayerAlertConfiguration {
        configurations["\(profileID).\(prayer.rawValue)"] ?? .init()
    }
    func set(_ config: PrayerAlertConfiguration, profileID: Int64, prayer: Prayer) {
        configurations["\(profileID).\(prayer.rawValue)"] = config
        defaults.set(try? JSONEncoder().encode(configurations), forKey: "prayer_alert_configurations")
        revision += 1
    }
    func requestPermission() async {
        do {
            enabled = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
        } catch { schedulingError = "Couldn't enable notifications. Please try again." }
        authorization = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }
    func reschedule(profiles: [Profile]) async {
        generation += 1
        let currentGeneration = generation
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard generation == currentGeneration, !Task.isCancelled else { return }
        authorization = status
        schedulingError = nil
        let pending = await center.pendingNotificationRequests()
        guard generation == currentGeneration, !Task.isCancelled else { return }
        center.removePendingNotificationRequests(withIdentifiers: pending.filter { $0.identifier.hasPrefix("aynama.") }.map(\.identifier))
        guard enabled, status == .authorized || status == .provisional,
              let profile = profiles.first(where: { $0.id == profileID }) ?? profiles.first else { return }
        let settings = Dictionary(uniqueKeysWithValues: Prayer.allCases.map { ($0, configuration(profileID: profile.id, prayer: $0)) })
        let plan = PrayerAlertPlan.build(profile: profile, configurations: settings, imsak: imsak, now: AppClock.now)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        for alert in plan {
            guard generation == currentGeneration, !Task.isCancelled else { return }
            let content = UNMutableNotificationContent()
            content.title = alert.title
            content.body = alert.isImsak ? "10 minutes before Fajr · \(profile.name)" : profile.name
            content.userInfo = ["profileURL": "aynama://profile/\(profile.id)"]
            content.sound = alert.isReminder || voice == .none ? nil : .default
            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: alert.instant)
            var triggerComponents = components
            triggerComponents.timeZone = calendar.timeZone
            let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
            do { try await center.add(UNNotificationRequest(identifier: alert.id, content: content, trigger: trigger)) }
            catch { schedulingError = "Some alerts couldn't be scheduled. Open Aynama to try again." }
        }
    }
}
