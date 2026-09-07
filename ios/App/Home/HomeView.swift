import SharedLogic
import SwiftData
import SwiftUI

/// The Prayers screen: a horizontal pager of profiles, weather-app style (DESIGN.md §5, §21).
struct HomeView: View {

    /// A profile a widget or notification tap asked for, held until the pager has shown it.
    ///
    /// Bound rather than read from the URL at render time: the deep link would otherwise drag the
    /// user back to the widget's profile on every redraw, and after every return from Settings.
    /// Android holds it the same way, for the same reason.
    @Binding var requestedProfileID: Int64?

    @Environment(\.modelContext) private var modelContext
    @EnvironmentObject private var selection: SelectedProfile

    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var records: [ProfileRecord]

    @State private var model = HomeModel()
    @State private var isPresentingNewProfile = false
    @State private var pagerSelection: Int64?

    private var profiles: [Profile] { records.map(\.profile) }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            if profiles.isEmpty {
                EmptyProfilesView { isPresentingNewProfile = true }
            } else {
                pager
                addProfileButton
            }
        }
        .sheet(isPresented: $isPresentingNewProfile) {
            ProfileFormSheet { profile in create(profile) }
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .onChange(of: model.now, initial: true) { model.refresh(profiles: profiles) }
        .onChange(of: records.count, initial: true) { syncSelection() }
        .onChange(of: requestedProfileID) { showRequestedProfile() }
        .onChange(of: pagerSelection) { _, new in selection.id = new }
    }

    private var pager: some View {
        TabView(selection: $pagerSelection) {
            ForEach(model.pages) { page in
                ProfilePageView(page: page).tag(Optional(page.id))
            }
        }
        .tabViewStyle(.page(indexDisplayMode: profiles.count > 1 ? .automatic : .never))
        // The dots sit on the surface, which can be near-black at Isha or honey at Asr, so they
        // cannot inherit the system's dark-on-light default.
        .indexViewStyle(.page(backgroundDisplayMode: .interactive))
    }

    /// DESIGN.md §21: a saffron FAB at the bottom-right of the Prayers screen, icon only, Ink on
    /// Saffron — clear of the dot indicator.
    private var addProfileButton: some View {
        Button {
            isPresentingNewProfile = true
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(AynamaColor.ink)
                .frame(width: 56, height: 56)
                .background(AynamaColor.saffron, in: Circle())
                .shadow(color: AynamaColor.ink.opacity(0.25), radius: 8, y: 3)
        }
        .padding(.trailing, 24)
        // Clear of the page dots, which sit at the bottom edge.
        .padding(.bottom, 56)
        .accessibilityLabel("New profile")
    }

    // MARK: - Actions

    /// §21 step 3: persist, select, and land the pager on what the user just made.
    private func create(_ profile: Profile) {
        let repository = ProfileRepository(context: modelContext)
        guard let id = try? repository.insert(profile) else { return }
        selection.id = id
        pagerSelection = id
        model.refresh(profiles: repository.all())
    }

    /// Keep the pager on a profile that still exists.
    ///
    /// Runs when the profile count changes, which covers a delete from Settings as well as an
    /// insert here. `resolve` falls back to the first profile, so deleting the selected one lands
    /// somewhere real instead of on an empty page.
    private func syncSelection() {
        let resolved = selection.resolve(against: profiles)
        selection.id = resolved?.id
        pagerSelection = resolved?.id
        model.refresh(profiles: profiles)
    }

    /// A widget or notification tap. Honoured once, then cleared, so it cannot re-fire.
    private func showRequestedProfile() {
        guard let requested = requestedProfileID else { return }
        if profiles.contains(where: { $0.id == requested }) {
            selection.id = requested
            pagerSelection = requested
        }
        requestedProfileID = nil
    }
}

/// One page: the hero, the ribbon, and the profile's own name.
private struct ProfilePageView: View {

    let page: ProfilePage

    var body: some View {
        switch page {
        case let .ready(state):
            ready(state)
        case let .unavailable(profile, reason):
            unavailable(profile, reason)
        }
    }

    private func ready(_ state: ProfileUiState) -> some View {
        let surface = state.phase.surface
        return ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                CountdownHero(
                    text: state.countdownText,
                    isElapsed: state.countdownIsElapsed,
                    prayerName: state.countdownPrayerName,
                    prayerTime: state.countdownPrayerTime,
                    surface: surface
                )
                PrayerRibbon(rows: state.ribbonRows, surface: surface)
                footer(state, surface: surface)
            }
            // §5: 24pt side margins, 32pt top, on utilitarian and contemplative alike.
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .padding(.bottom, 96)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollBounceBehavior(.basedOnSize)
        .timeOfDaySurface(surface)
    }

    private func footer(_ state: ProfileUiState, surface: TimeOfDaySurface) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(state.profile.name)
                .font(AynamaFont.title)
                .foregroundStyle(surface.foreground)
            if !state.hijriDateText.isEmpty {
                Text(state.hijriDateText)
                    .font(AynamaFont.bodySM)
                    .foregroundStyle(surface.foregroundMuted)
            }
        }
    }

    private func unavailable(_ profile: Profile, _ reason: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(profile.name)
                .font(AynamaFont.displayMD)
                .foregroundStyle(AynamaColor.ink)
            Text(reason)
                .font(AynamaFont.bodyLG)
                .foregroundStyle(AynamaColor.inkMuted)
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        // Utilitarian ground: there is no time of day to render here (§2).
        .background(AynamaColor.parchment)
    }
}

/// DESIGN.md §21: the empty state's CTA opens the same sheet the FAB does.
private struct EmptyProfilesView: View {

    let onCreate: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("No profiles yet")
                .font(AynamaFont.displayMD)
                .foregroundStyle(AynamaColor.ink)
            Text("Add a location and aynama will show its prayer times.")
                .font(AynamaFont.bodyLG)
                .foregroundStyle(AynamaColor.inkMuted)
            Button("Create profile", action: onCreate)
                .font(AynamaFont.bodyLG)
                .foregroundStyle(AynamaColor.ink)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(AynamaColor.saffron, in: Capsule())
                .padding(.top, 8)
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .background(AynamaColor.parchment)
    }
}
