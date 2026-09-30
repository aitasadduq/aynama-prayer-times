import SharedLogic
import SwiftData
import SwiftUI

struct HomeView: View {
    @Binding var requestedProfileID: Int64?
    @Environment(\.modelContext) private var modelContext
    @EnvironmentObject private var selection: SelectedProfile
    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var records: [ProfileRecord]
    @State private var model = HomeModel()
    @State private var isPresentingNewProfile = false
    @State private var pagerSelection: Int64?
    @State private var target: PrayerMarkTarget?
    @State private var saveError = false
    private var profiles: [Profile] { records.map(\.profile) }
    private var surface: TimeOfDaySurface {
        if let page = model.pages.first(where: { $0.id == pagerSelection }), case let .ready(state) = page {
            return state.phase.surface
        }
        return .isha
    }

    var body: some View {
        Group {
            if profiles.isEmpty {
                EmptyProfilesView { isPresentingNewProfile = true }
            } else {
                TabView(selection: $pagerSelection) {
                    ForEach(model.pages) { page in
                        ProfilePageView(page: page, now: model.now) { target = $0 }
                            .tag(Optional(page.id))
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .safeAreaInset(edge: .bottom) {
                    HStack {
                        Color.clear.frame(width: 56, height: 1)
                        Spacer()
                        HStack(spacing: 6) {
                            ForEach(profiles) { profile in
                                Circle().fill(profile.id == pagerSelection ? surface.activeForeground : surface.foreground.opacity(0.3))
                                    .frame(width: profile.id == pagerSelection ? 8 : 5, height: profile.id == pagerSelection ? 8 : 5)
                            }
                        }.accessibilityLabel("Profile \(profiles.firstIndex(where: { $0.id == pagerSelection }).map { $0 + 1 } ?? 1) of \(profiles.count)")
                        Spacer()
                        AddProfileButton { isPresentingNewProfile = true }
                    }.padding(.horizontal, 24).padding(.vertical, 8)
                }
                .timeOfDaySurface(surface)
            }
        }
        .sheet(isPresented: $isPresentingNewProfile) {
            ProfileFormSheet { create($0) }
        }
        .sheet(item: $target) { PrayerMarkSheet(target: $0) }
        .alert("Couldn't save the profile", isPresented: $saveError) { Button("OK", role: .cancel) {} }
        .onAppear { model.start(); showRequestedProfile() }
        .onDisappear { model.stop() }
        .onChange(of: model.now, initial: true) { model.refresh(profiles: profiles) }
        .onChange(of: profiles, initial: true) { syncSelection() }
        .onChange(of: requestedProfileID) { showRequestedProfile() }
        .onChange(of: pagerSelection) { _, new in selection.id = new }
    }

    private func create(_ profile: Profile) {
        let repository = ProfileRepository(context: modelContext)
        do {
            let id = try repository.insert(profile)
            selection.id = id
            pagerSelection = id
            model.refresh(profiles: repository.all())
        } catch { saveError = true }
    }
    private func syncSelection() {
        let resolved = selection.resolve(against: profiles)
        selection.id = resolved?.id
        pagerSelection = resolved?.id
        model.refresh(profiles: profiles)
    }
    private func showRequestedProfile() {
        guard let requested = requestedProfileID else { return }
        if profiles.contains(where: { $0.id == requested }) {
            selection.id = requested
            pagerSelection = requested
        }
        requestedProfileID = nil
    }
}

private struct ProfilePageView: View {
    let page: ProfilePage
    let now: Date
    let onMark: (PrayerMarkTarget) -> Void
    @Query private var marks: [QazaRecord]

    var body: some View {
        switch page {
        case let .ready(state): ready(state)
        case let .unavailable(profile, reason):
            VStack(alignment: .leading, spacing: 16) {
                Text(profile.name).font(AynamaFont.bodySM)
                Text("No prayer times today").font(AynamaFont.displayMD)
                Text(reason).font(AynamaFont.bodyLG).foregroundStyle(TimeOfDaySurface.isha.foregroundMuted)
            }.padding(24).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .foregroundStyle(TimeOfDaySurface.isha.foreground).timeOfDaySurface(.isha)
        }
    }

    private func ready(_ state: ProfileUiState) -> some View {
        let surface = state.phase.surface
        return GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .firstTextBaseline) {
                            Text("\(state.profile.name) · \(state.profile.calculationMethod.shortName)")
                            Spacer(minLength: 8)
                            Text(state.hijriDateText)
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(state.profile.name) · \(state.profile.calculationMethod.shortName)")
                            Text(state.hijriDateText)
                        }
                    }
                    .font(AynamaFont.bodySM).foregroundStyle(surface.foregroundMuted)
                    .accessibilityIdentifier("profile-header")
                    CountdownHero(text: state.countdownText, isElapsed: state.countdownIsElapsed,
                                  prayerName: state.countdownPrayerName, prayerTime: state.countdownPrayerTime, surface: surface)
                    PrayerRibbon(rows: state.ribbonRows, surface: surface,
                                 rowHeight: max(56, (geometry.size.height - 270) / CGFloat(state.ribbonRows.count)),
                                 progress: ribbonPosition(state)) { prayer in
                        onMark(PrayerMarkTarget(profile: state.profile, prayer: prayer,
                                               date: CalendarDate.from(now, in: state.profile.effectiveTimeZone)))
                    }
                    let outstanding = marks.filter { $0.profileID == state.profile.id && $0.status == .missed }.count
                    if outstanding > 0 {
                        Text("\(outstanding) outstanding Qaḍā").font(AynamaFont.bodySM)
                            .foregroundStyle(surface.foregroundMuted)
                    }
                }
                .padding(.horizontal, 24).padding(.top, 16).padding(.bottom, 16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }.scrollBounceBehavior(.basedOnSize).timeOfDaySurface(surface)
        }
    }

    private func ribbonPosition(_ state: ProfileUiState) -> Double? {
        let date = CalendarDate.from(now, in: state.profile.effectiveTimeZone)
        let entries = PrayerSchedule.entries(profile: state.profile, date: date)
        guard let index = entries.lastIndex(where: { $0.instant <= now }), index + 1 < entries.count else { return nil }
        let start = entries[index].instant
        let interval = entries[index + 1].instant.timeIntervalSince(start)
        let fraction = interval > 0 ? now.timeIntervalSince(start) / interval : 0
        return Double(index + (state.isRamadan ? 1 : 0)) + min(1, max(0, fraction))
    }
}

private struct EmptyProfilesView: View {
    let onCreate: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // A quiet architectural mark, without calligraphy or stock ornament.
            Rectangle().stroke(AynamaColor.parchment.opacity(0.6), lineWidth: 1.5)
                .frame(width: 40, height: 40).accessibilityHidden(true)
            Text("Set up your first prayer profile").font(AynamaFont.displayMD)
            Text("Add a location to see accurate prayer times.").font(AynamaFont.bodyLG)
                .foregroundStyle(AynamaColor.parchmentMuted)
            Button("Create profile", action: onCreate)
                .font(AynamaFont.bodyLG).foregroundStyle(AynamaColor.ink)
                .padding(.horizontal, 20).padding(.vertical, 12)
                .background(AynamaColor.saffron, in: Capsule()).padding(.top, 8)
        }.padding(24).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .foregroundStyle(AynamaColor.parchment).background(AynamaColor.ink.ignoresSafeArea())
    }
}
