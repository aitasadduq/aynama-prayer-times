import SharedLogic
import SwiftData
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var alerts: PrayerAlertSettings
    @Environment(\.modelContext) private var context
    @Environment(\.colorScheme) private var scheme
    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var records: [ProfileRecord]
    @State private var form: ProfileFormDestination?
    @State private var error: String?
    @Environment(\.isWideLayout) private var isWide

    var body: some View {
        let palette = NeutralPalette(scheme: scheme)
        List {
            NavigationLink { NotificationSettingsView() } label: {
                Label {
                    Text("Notifications").font(AynamaFont.bodyLG)
                } icon: {
                    Image(systemName: isWide ? "bell.fill" : "bell").frame(width: 24)
                }
                .foregroundStyle(isWide ? palette.accent : palette.foreground)
                .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            }
            .accessibilityAddTraits(isWide ? .isSelected : [])
            .settingsRow()
            .listRowSeparator(.hidden, edges: .top)
            Section {
                ForEach(records) { record in
                    Button {
                        form = ProfileFormDestination(profile: record.profile)
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(record.name).font(AynamaFont.title)
                                Text("\(record.locationName ?? "Location selected") · \(record.profile.calculationMethod.displayName)")
                                    .font(AynamaFont.body).foregroundStyle(palette.muted)
                                    .accessibilityIdentifier("profile-location-\(record.profileID)")
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.right").font(.footnote.weight(.semibold))
                                .foregroundStyle(palette.muted).accessibilityHidden(true)
                        }
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity, minHeight: 72, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(record.name)
                    .accessibilityValue("\(record.locationName ?? "Location selected"), \(record.profile.calculationMethod.displayName)")
                    .accessibilityHint("Edit profile")
                    .settingsRow()
                    .swipeActions { Button("Delete", role: .destructive) { remove(record.profile) } }
                    .task(id: ProfileCoordinates(latitude: record.latitude, longitude: record.longitude)) {
                        await resolveLocationName(for: record.profile)
                    }
                }
                if records.isEmpty {
                    Text("No profiles yet. Tap + to add a location.")
                        .font(AynamaFont.body).foregroundStyle(palette.muted)
                        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                        .settingsRow()
                }
            } header: {
                Text("Profiles").font(AynamaFont.displayMD).textCase(nil).foregroundStyle(palette.foreground)
                    .padding(.top, 24).padding(.bottom, 8)
                    .listRowInsets(EdgeInsets(top: 0, leading: SettingsLayout.margin, bottom: 0, trailing: SettingsLayout.margin))
            }
        }
        .listStyle(.plain)
        .safeAreaInset(edge: .bottom) {
            HStack { Spacer(); AddProfileButton(usesGlass: true) { form = ProfileFormDestination(profile: nil) } }
                .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .neutralSurface()
        .sheet(item: $form) { destination in
            ProfileFormSheet(editing: destination.profile, onDelete: { remove($0) }) { profile in
                do {
                    let repository = ProfileRepository(context: context)
                    if destination.profile != nil { try repository.update(profile) } else { try repository.insert(profile) }
                } catch { self.error = "Couldn't save this profile. Please try again." }
            }
        }
        .alert("Profile wasn't saved", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }

    private func remove(_ profile: Profile) {
        do {
            try ProfileRepository(context: context).delete(id: profile.id)
            alerts.removeProfile(id: profile.id)
        }
        catch { self.error = "Couldn't delete this profile. Please try again." }
    }

    private func resolveLocationName(for profile: Profile) async {
        guard profile.locationName == nil else { return }
        let coordinates = ProfileCoordinates(latitude: profile.latitude, longitude: profile.longitude)
        guard let place = try? await ProfilePlace.reverseGeocode(coordinates),
              let name = place.name, !Task.isCancelled else { return }
        let repository = ProfileRepository(context: context)
        // The user may have edited or deleted the profile while the lookup was running.
        guard var current = repository.profile(id: profile.id), current.locationName == nil,
              current.latitude == profile.latitude, current.longitude == profile.longitude else { return }
        current.locationName = name
        try? repository.update(current)
    }
}

enum SettingsLayout {
    static let margin: CGFloat = 24
}

private extension View {
    func settingsRow() -> some View {
        listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 0, leading: SettingsLayout.margin, bottom: 0, trailing: SettingsLayout.margin))
            .alignmentGuide(.listRowSeparatorLeading) { $0[.leading] }
            .alignmentGuide(.listRowSeparatorTrailing) { $0[.trailing] }
    }
}

private struct ProfileFormDestination: Identifiable {
    let profile: Profile?
    var id: String { profile.map { "edit-\($0.id)" } ?? "new" }
}
