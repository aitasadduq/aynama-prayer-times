import SharedLogic
import SwiftData
import SwiftUI

struct NotificationSettingsView: View {
    @EnvironmentObject private var alerts: PrayerAlertSettings
    @Environment(\.colorScheme) private var scheme
    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var records: [ProfileRecord]
    @State private var detail: PrayerAlertTarget?
    @State private var choosingProfile = false
    private var profile: Profile? { (records.first(where: { $0.profileID == alerts.profileID }) ?? records.first)?.profile }
    private var palette: NeutralPalette { NeutralPalette(scheme: scheme) }

    var body: some View {
        Form {
            Section {
                if alerts.authorization == .denied {
                    Button("Enable in Settings →") { openSettings() }.frame(minHeight: 44)
                } else {
                    Toggle("Prayer alerts", isOn: Binding(get: { alerts.enabled }, set: { value in
                        if value { Task { await alerts.requestPermission() } } else { alerts.enabled = false }
                    })).frame(minHeight: 44)
                }
            }.listRowBackground(Color.clear)
            if alerts.enabled {
                if let profile {
                    Section {
                        Button { choosingProfile = true } label: {
                            disclosure("Alerts for", value: profile.name)
                        }
                    }.listRowBackground(Color.clear)
                    Section {
                        ForEach(Prayer.allCases, id: \.self) { prayer in prayerRow(prayer, profile: profile) }
                    } header: { header("PRAYERS") }
                    .listRowBackground(Color.clear)
                    Section {
                        NavigationLink { AdhanVoiceView() } label: {
                            HStack { Text("Adhan voice"); Spacer(); Text(alerts.voice.name).font(AynamaFont.bodySM).foregroundStyle(palette.muted) }
                                .frame(minHeight: 44)
                        }
                    } header: { header("ADHAN") }
                    .listRowBackground(Color.clear)
                    Section {
                        Toggle(isOn: Binding(get: { alerts.imsak }, set: { alerts.imsak = $0 })) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Ramadan Imsak")
                                Text("10 minutes before Fajr, during Ramadan").font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                            }.frame(minHeight: 52)
                        }
                        Button { openSettings() } label: { disclosure("Sound and vibration", value: "iOS Settings") }
                    } header: { header("OTHER") } footer: {
                        Text("Alerts follow this profile's time zone. iOS controls notification sound and vibration.")
                            .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                    }.listRowBackground(Color.clear)
                } else { Text("Create a profile to set prayer alerts.").listRowBackground(Color.clear) }
            }
            if let error = alerts.schedulingError { Text(error).font(AynamaFont.bodySM).listRowBackground(Color.clear) }
        }
        .navigationTitle("Notifications").navigationBarTitleDisplayMode(.inline)
        .neutralSurface()
        .sheet(item: $detail) { PrayerAlertDetailView(target: $0) }
        .sheet(isPresented: $choosingProfile) {
            NavigationStack {
                List(records) { record in
                    Button {
                        alerts.profileID = record.profileID
                        choosingProfile = false
                    } label: {
                        HStack {
                            Text(record.name)
                            Spacer()
                            if record.profileID == profile?.id { Image(systemName: "checkmark").foregroundStyle(palette.accent) }
                        }.frame(minHeight: 44)
                    }.listRowBackground(Color.clear)
                }.neutralSurface().navigationTitle("Profile").navigationBarTitleDisplayMode(.inline)
            }.presentationDetents([.medium, .large]).presentationDragIndicator(.visible).presentationBackground(palette.background)
        }
    }
    private func prayerRow(_ prayer: Prayer, profile: Profile) -> some View {
        HStack(spacing: 12) {
            Button { detail = .init(profile: profile, prayer: prayer) } label: {
                HStack {
                    Text(prayer.rawValue.capitalized)
                    Spacer(minLength: 8)
                    Text(PrayerSchedule.formatted(alertTime(prayer, profile: profile), zone: profile.effectiveTimeZone))
                        .font(AynamaFont.monoNum).foregroundStyle(palette.muted)
                }.frame(minHeight: 44)
            }.buttonStyle(.plain)
            Toggle("\(prayer.rawValue.capitalized) prayer alert", isOn: Binding(get: {
                alerts.configuration(profileID: profile.id, prayer: prayer).enabled
            }, set: { value in
                var config = alerts.configuration(profileID: profile.id, prayer: prayer)
                config.enabled = value
                alerts.set(config, profileID: profile.id, prayer: prayer)
            })).labelsHidden()
            Button { detail = .init(profile: profile, prayer: prayer) } label: {
                Image(systemName: "chevron.right").frame(width: 44, height: 44)
            }.accessibilityLabel("\(prayer.rawValue.capitalized) notification settings")
        }
    }
    private func alertTime(_ prayer: Prayer, profile: Profile) -> Date? {
        PrayerAlertPlan.alertTime(prayer: prayer, profile: profile,
                                 date: CalendarDate.from(AppClock.now, in: profile.effectiveTimeZone),
                                 configuration: alerts.configuration(profileID: profile.id, prayer: prayer))
    }
    private func header(_ title: String) -> some View { Text(title).font(AynamaFont.title).foregroundStyle(palette.foreground).textCase(nil) }
    private func disclosure(_ label: String, value: String) -> some View {
        HStack { Text(label); Spacer(); Text(value).font(AynamaFont.bodySM).foregroundStyle(palette.muted); Image(systemName: "chevron.right") }
            .frame(minHeight: 44)
    }
    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }
}

struct PrayerAlertTarget: Identifiable {
    let profile: Profile
    let prayer: Prayer
    var id: String { "\(profile.id).\(prayer.rawValue)" }
}

struct PrayerAlertDetailView: View {
    let target: PrayerAlertTarget
    @EnvironmentObject private var alerts: PrayerAlertSettings
    @Environment(\.colorScheme) private var scheme
    @State private var choosingOffset = false
    @State private var choosingFixed = false
    @State private var fixedDate = Date()
    private var configuration: PrayerAlertConfiguration { alerts.configuration(profileID: target.profile.id, prayer: target.prayer) }

    var body: some View {
        let palette = NeutralPalette(scheme: scheme)
        NavigationStack {
            Form {
                Section {
                    VStack(spacing: 6) {
                        Text(target.prayer.rawValue.capitalized).font(AynamaFont.displayMD)
                        Text("Today · \(PrayerSchedule.formatted(alertTime, zone: target.profile.effectiveTimeZone))")
                            .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                    }.frame(maxWidth: .infinity).padding(.vertical, 8)
                    Toggle("Send alert", isOn: binding(\.enabled))
                }.listRowBackground(Color.clear)
                Section {
                    Button { choosingOffset = true } label: {
                        modeRow("Offset", value: configuration.offsetMinutes == 0 ? "On time" : "\(configuration.offsetMinutes > 0 ? "+" : "−")\(abs(configuration.offsetMinutes)) min", selected: configuration.fixedMinutes == nil)
                    }.accessibilityIdentifier("alert-offset")
                    Button {
                        let minutes = configuration.fixedMinutes ?? 360
                        fixedDate = CalendarDate.from(AppClock.now, in: target.profile.effectiveTimeZone)
                            .atTime(ClockTime(hour: minutes / 60, minute: minutes % 60), in: target.profile.effectiveTimeZone)
                        choosingFixed = true
                    } label: {
                        modeRow("Fixed time", value: configuration.fixedMinutes == nil ? "Not set" : PrayerSchedule.formatted(alertTime, zone: target.profile.effectiveTimeZone), selected: configuration.fixedMinutes != nil)
                    }.accessibilityIdentifier("alert-fixed-time")
                } header: { Text("ALERT TIME").font(AynamaFont.bodySM).foregroundStyle(palette.muted) }
                .listRowBackground(Color.clear)
                Section {
                    Picker("Early reminder", selection: binding(\.earlyMinutes)) {
                        Text("Off").tag(0)
                        ForEach([5, 10, 15], id: \.self) { Text("\($0) min before").tag($0) }
                    }
                }.listRowBackground(Color.clear)
            }.neutralSurface()
        }
        .presentationDetents([.medium, .large]).presentationDragIndicator(.visible).presentationBackground(palette.background)
        .confirmationDialog("Time offset", isPresented: $choosingOffset, titleVisibility: .visible) {
            ForEach([-15, -10, -5, 0, 5, 10, 15], id: \.self) { minutes in
                Button(minutes == 0 ? "On time" : "\(minutes > 0 ? "+" : "−")\(abs(minutes)) min") {
                    var config = configuration
                    config.offsetMinutes = minutes
                    config.fixedMinutes = nil
                    alerts.set(config, profileID: target.profile.id, prayer: target.prayer)
                }
            }
        }
        .sheet(isPresented: $choosingFixed) {
            NavigationStack {
                DatePicker("Fixed time", selection: $fixedDate, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel).environment(\.timeZone, target.profile.effectiveTimeZone)
                    .padding(24).neutralSurface().navigationTitle("Fixed time").navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("Cancel") { choosingFixed = false } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Save") {
                                let time = ClockTime.from(fixedDate, in: target.profile.effectiveTimeZone)
                                var config = configuration
                                config.fixedMinutes = time.hour * 60 + time.minute
                                alerts.set(config, profileID: target.profile.id, prayer: target.prayer)
                                choosingFixed = false
                            }
                        }
                    }
            }.presentationDetents([.medium]).presentationBackground(palette.background)
        }
    }
    private var alertTime: Date? {
        PrayerAlertPlan.alertTime(prayer: target.prayer, profile: target.profile,
                                 date: CalendarDate.from(AppClock.now, in: target.profile.effectiveTimeZone), configuration: configuration)
    }
    private func binding<T>(_ keyPath: WritableKeyPath<PrayerAlertConfiguration, T>) -> Binding<T> {
        Binding(get: { configuration[keyPath: keyPath] }, set: { value in
            var config = configuration
            config[keyPath: keyPath] = value
            alerts.set(config, profileID: target.profile.id, prayer: target.prayer)
        })
    }
    private func modeRow(_ label: String, value: String, selected: Bool) -> some View {
        HStack {
            Image(systemName: selected ? "record.circle" : "circle").foregroundStyle(selected ? NeutralPalette(scheme: scheme).accent : NeutralPalette(scheme: scheme).muted)
            Text(label)
            Spacer()
            Text(value).font(AynamaFont.bodySM).foregroundStyle(NeutralPalette(scheme: scheme).muted)
            Image(systemName: "chevron.right")
        }.frame(minHeight: 44)
    }
}

struct AdhanVoiceView: View {
    @EnvironmentObject private var alerts: PrayerAlertSettings
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let palette = NeutralPalette(scheme: scheme)
        List {
            ForEach(AdhanVoice.allCases, id: \.self) { voice in
                Button { alerts.voice = voice } label: {
                    HStack(spacing: 16) {
                        Image(systemName: alerts.voice == voice ? "record.circle" : "circle").foregroundStyle(palette.accent)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(voice.name).font(AynamaFont.body)
                            if !voice.caption.isEmpty { Text(voice.caption).font(AynamaFont.bodySM).foregroundStyle(palette.muted) }
                        }
                        Spacer()
                    }.frame(minHeight: 56)
                }.buttonStyle(.plain).listRowBackground(Color.clear)
                    .accessibilityAddTraits(alerts.voice == voice ? [.isSelected] : [])
            }
            Text("Adhan recordings are not bundled yet. Alerts use the iOS notification sound; None sends silent alerts.")
                .font(AynamaFont.bodySM).foregroundStyle(palette.muted).listRowBackground(Color.clear)
        }.listStyle(.plain).neutralSurface().navigationTitle("Adhan voice").navigationBarTitleDisplayMode(.inline)
    }
}
