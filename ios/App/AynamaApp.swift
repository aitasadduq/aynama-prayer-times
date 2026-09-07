import SharedLogic
import SwiftData
import SwiftUI

@main
struct AynamaApp: App {

    /// One container for the process, shared with the widget extension through the App Group.
    @State private var container = AynamaStore.makeContainer()
    @StateObject private var selection = SelectedProfile()

    /// The profile a widget or notification tap asked for.
    ///
    /// Held here rather than read from the URL where it is used, so that honouring it once and
    /// clearing it is a single assignment. A deep link that stayed set would pull the user back
    /// to the widget's profile on every redraw.
    @State private var requestedProfileID: Int64?

    var body: some Scene {
        WindowGroup {
            HomeView(requestedProfileID: $requestedProfileID)
                .environmentObject(selection)
                // §3: the only accent is saffron. Without this, every unstyled control, text
                // cursor and selection handle in the app renders in the system's blue.
                .tint(AynamaColor.accent)
                .onOpenURL { url in
                    requestedProfileID = AynamaURL.profileID(from: url)
                }
        }
        .modelContainer(container)
    }
}
