import SharedLogic
import SwiftData
import SwiftUI

struct SettingsView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.colorScheme) private var scheme
    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var records: [ProfileRecord]
    @State private var showingForm = false
    @State private var editing: Profile?
    @State private var error: String?

    var body: some View {
        let palette = NeutralPalette(scheme: scheme)
        List {
            NavigationLink { NotificationSettingsView() } label: {
                Label("Notifications", systemImage: "bell").font(AynamaFont.body)
                    .frame(minHeight: 44)
            }
            .listRowBackground(Color.clear)
            Section {
                ForEach(records) { record in
                    Button {
                        editing = record.profile
                        showingForm = true
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(record.name).font(AynamaFont.title)
                            Text(String(format: "%.4f, %.4f · %@", record.latitude, record.longitude, record.profile.calculationMethod.displayName))
                                .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                        }.frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(Color.clear)
                    .swipeActions { Button("Delete", role: .destructive) { remove(record.profile) } }
                }
            } header: {
                Text("Profiles").font(AynamaFont.displayMD).textCase(nil).foregroundStyle(palette.foreground)
                    .padding(.vertical, 12)
            }
        }
        .listStyle(.plain)
        .safeAreaInset(edge: .bottom) {
            HStack { Spacer(); AddProfileButton(usesGlass: true) { editing = nil; showingForm = true } }
                .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .neutralSurface()
        .sheet(isPresented: $showingForm) {
            ProfileFormSheet(editing: editing, onDelete: { remove($0) }) { profile in
                do {
                    let repository = ProfileRepository(context: context)
                    if editing != nil { try repository.update(profile) } else { try repository.insert(profile) }
                } catch { self.error = "Couldn't save this profile. Please try again." }
            }
        }
        .alert("Profile wasn't saved", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }

    private func remove(_ profile: Profile) {
        do { try ProfileRepository(context: context).delete(id: profile.id) }
        catch { self.error = "Couldn't delete this profile. Please try again." }
    }
}
