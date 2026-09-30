import SharedLogic
import SwiftData
import SwiftUI

struct AppRootView: View {
    @Binding var requestedProfileID: Int64?
    @State private var tab = 0
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var records: [ProfileRecord]
    @StateObject private var alerts = PrayerAlertSettings()

    var body: some View {
        TabView(selection: $tab) {
            HomeView(requestedProfileID: $requestedProfileID)
                .tabItem { Label("Prayers", systemImage: tab == 0 ? "house.fill" : "house") }.tag(0)
            QiblaView()
                .tabItem { Label("Qibla", systemImage: tab == 1 ? "location.fill" : "location") }.tag(1)
            NavigationStack { TrackerView() }
                .tabItem { Label("Tracker", systemImage: "calendar") }.tag(2)
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: tab == 3 ? "gearshape.fill" : "gearshape") }.tag(3)
        }
        .onAppear { ScreenshotFixtures.seed(context) }
        .font(AynamaFont.body)
        .environmentObject(alerts)
        .onChange(of: requestedProfileID, initial: true) { _, id in
            if id != nil { tab = 0 }
        }
        .task(id: AlertRefreshKey(profiles: records.map(\.profile), revision: alerts.revision)) { await alerts.reschedule(profiles: records.map(\.profile)) }
        .onReceive(NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange)) { _ in
            Task { await alerts.reschedule(profiles: records.map(\.profile)) }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                AynamaAppDelegate.scheduleRefresh()
                Task { await alerts.reschedule(profiles: records.map(\.profile)) }
            }
        }
    }
}

private struct AlertRefreshKey: Equatable {
    let profiles: [Profile]
    let revision: Int
}
