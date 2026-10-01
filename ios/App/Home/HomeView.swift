import SharedLogic
import SwiftData
import SwiftUI

struct HomeView: View {
    @Binding var requestedProfileID: Int64?
    var onSurfaceChange: (TimeOfDaySurface) -> Void = { _ in }
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
        .onChange(of: surface, initial: true) { _, new in onSurfaceChange(new) }
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
    @ScaledMetric(relativeTo: .footnote) private var headerSize: CGFloat = 13
    @ScaledMetric(relativeTo: .largeTitle) private var heroSize: CGFloat = 72
    @ScaledMetric(relativeTo: .title) private var subtitleSize: CGFloat = 32
    @ScaledMetric(relativeTo: .title3) private var rowSize: CGFloat = 20
    @ScaledMetric(relativeTo: .footnote) private var qazaSize: CGFloat = 13
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
            let outstanding = marks.filter { $0.profileID == state.profile.id && $0.status == .missed }.count
            let metrics = HomePageMetrics(size: geometry.size, rows: state.ribbonRows.count,
                                          hasQaza: outstanding > 0, headerSize: headerSize,
                                          heroSize: heroSize, subtitleSize: subtitleSize,
                                          rowSize: rowSize, qazaSize: qazaSize)
            VStack(alignment: .leading, spacing: 0) {
                header(state, metrics: metrics, surface: surface)
                Spacer().frame(height: metrics.headerGap)
                CountdownHero(text: state.countdownText, isElapsed: state.countdownIsElapsed,
                              prayerName: state.countdownPrayerName, prayerTime: state.countdownPrayerTime,
                              surface: surface, metrics: metrics)
                Spacer().frame(height: metrics.heroGap)
                PrayerRibbon(rows: state.ribbonRows, surface: surface,
                             rowHeight: metrics.rowHeight, fontSize: metrics.rowFont,
                             markFontSize: metrics.markFont, scale: metrics.scale) { prayer in
                    if let day = PrayerSchedule.latestPrayerDay(prayer, profile: state.profile, now: now) {
                        onMark(PrayerMarkTarget(profile: state.profile, prayer: prayer, date: day))
                    }
                }
                if outstanding > 0 {
                    Text("\(outstanding) outstanding Qaḍā")
                        .font(AynamaFont.homeMeta(size: metrics.qazaFont))
                        .foregroundStyle(surface.foregroundMuted)
                        .padding(.top, metrics.qazaGap)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, metrics.horizontalPadding)
            .padding(.top, metrics.topPadding).padding(.bottom, metrics.bottomPadding)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .timeOfDaySurface(surface)
        }
    }

    @ViewBuilder
    private func header(_ state: ProfileUiState, metrics: HomePageMetrics,
                        surface: TimeOfDaySurface) -> some View {
        let profile = Text("\(state.profile.name) · \(state.profile.calculationMethod.shortName)")
        let hijri = Text(state.hijriDateText)
        Group {
            if metrics.headerStacked {
                VStack(alignment: .leading, spacing: 2 * metrics.scale) {
                    profile
                    hijri
                }
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    profile
                    Spacer(minLength: 0)
                    hijri
                }
            }
        }
        .font(AynamaFont.homeMeta(size: metrics.headerFont))
        .foregroundStyle(surface.foregroundMuted)
        .lineLimit(1).minimumScaleFactor(0.7)
        .accessibilityIdentifier("profile-header")
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
