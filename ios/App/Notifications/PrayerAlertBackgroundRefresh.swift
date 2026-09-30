@preconcurrency import BackgroundTasks
import SwiftData
import UIKit
@preconcurrency import UserNotifications

@MainActor
final class AynamaAppDelegate: NSObject, UIApplicationDelegate, @preconcurrency UNUserNotificationCenterDelegate {
    static let refreshIdentifier = "com.aynama.prayertimes.refresh"

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.refreshIdentifier, using: .main) { task in
            // BackgroundTasks invokes this on the main queue configured above. Keep the store
            // and preferences on the same actor as foreground rescheduling.
            MainActor.assumeIsolated {
                guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
                Self.scheduleRefresh()
                let work = Task { @MainActor in
                    let container = AynamaStore.makeContainer()
                    let settings = PrayerAlertSettings()
                    await settings.reschedule(profiles: ProfileRepository(context: ModelContext(container)).all())
                    refresh.setTaskCompleted(success: !Task.isCancelled && settings.schedulingError == nil)
                }
                refresh.expirationHandler = { work.cancel() }
            }
        }
        Self.scheduleRefresh()
        return true
    }

    static func scheduleRefresh() {
        #if DEBUG
        if ProcessInfo.processInfo.environment["AYNAMA_UI_TEST_STORE"] != nil { return }
        #endif
        let request = BGAppRefreshTaskRequest(identifier: refreshIdentifier)
        request.earliestBeginDate = Date().addingTimeInterval(12 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if let text = response.notification.request.content.userInfo["profileURL"] as? String,
           let url = URL(string: text) { UIApplication.shared.open(url) }
        completionHandler()
    }
}
