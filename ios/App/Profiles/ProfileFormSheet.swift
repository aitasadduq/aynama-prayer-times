import SharedLogic
import SwiftUI

/// The one profile form (DESIGN.md §21, "One form, three doors").
///
/// The Prayers FAB, the empty state's CTA and Settings → Profiles all present *this* view. A
/// field added here cannot appear at one entry point and not another — which is the whole reason
/// it is one view and not three sheets that happen to look alike.
///
/// Dismissing writes nothing. `onSave` is the only path that persists, and it is the only path
/// that reports an id back, so a cancelled sheet cannot leave a half-made profile behind or move
/// the pager.
struct ProfileFormSheet: View {

    /// The profile being edited, or nil to create one.
    let editing: Profile?
    let onSave: (Profile) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var latitudeText: String
    @State private var longitudeText: String
    @State private var method: CalculationMethodKey
    @State private var madhab: AsrMadhab
    @State private var useLocationTimezone: Bool
    @State private var timezone: String

    init(editing: Profile? = nil, onSave: @escaping (Profile) -> Void) {
        self.editing = editing
        self.onSave = onSave
        _name = State(initialValue: editing?.name ?? "")
        _latitudeText = State(initialValue: editing.map { String($0.latitude) } ?? "")
        _longitudeText = State(initialValue: editing.map { String($0.longitude) } ?? "")
        _method = State(initialValue: editing?.calculationMethod ?? .mwl)
        _madhab = State(initialValue: editing?.asrMadhab ?? .shafii)
        _useLocationTimezone = State(initialValue: editing?.useLocationTimezone ?? false)
        _timezone = State(initialValue: editing?.timezone ?? TimeZone.current.identifier)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Location") {
                    TextField("Name", text: $name)
                        .textInputAutocapitalization(.words)
                    LabeledContent("Latitude") {
                        TextField("21.4225", text: $latitudeText)
                            .keyboardType(.numbersAndPunctuation)
                            .multilineTextAlignment(.trailing)
                    }
                    LabeledContent("Longitude") {
                        TextField("39.8262", text: $longitudeText)
                            .keyboardType(.numbersAndPunctuation)
                            .multilineTextAlignment(.trailing)
                    }
                }

                Section("Calculation") {
                    Picker("Method", selection: $method) {
                        ForEach(CalculationMethodKey.allCases, id: \.self) { key in
                            Text(key.displayName).tag(key)
                        }
                    }
                    Picker("Asr", selection: $madhab) {
                        Text("Shafi'i").tag(AsrMadhab.shafii)
                        Text("Hanafi").tag(AsrMadhab.hanafi)
                    }
                }

                // DESIGN.md §17. Off means "show these times on my phone's clock", which is right
                // at home and wrong for a saved location on the other side of the world.
                Section {
                    Toggle("Use this location's timezone", isOn: $useLocationTimezone)
                    if useLocationTimezone {
                        Picker("Timezone", selection: $timezone) {
                            ForEach(TimeZone.knownTimeZoneIdentifiers, id: \.self) { identifier in
                                Text(identifier).tag(identifier)
                            }
                        }
                    }
                } footer: {
                    Text(
                        useLocationTimezone
                            ? "Prayer times show on this location's own clock."
                            : "Prayer times show on your phone's clock."
                    )
                }
            }
            .navigationTitle(editing == nil ? "New profile" : "Edit profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    // §21 step 4: dismiss writes nothing. No profile is created and no existing
                    // profile changes.
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(!isValid)
                        .fontWeight(.semibold)
                }
            }
            .tint(AynamaColor.saffron)
        }
    }

    private var latitude: Double? {
        Double(latitudeText.trimmingCharacters(in: .whitespaces)).flatMap {
            (-90.0...90.0).contains($0) ? $0 : nil
        }
    }

    private var longitude: Double? {
        Double(longitudeText.trimmingCharacters(in: .whitespaces)).flatMap {
            (-180.0...180.0).contains($0) ? $0 : nil
        }
    }

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && latitude != nil && longitude != nil
    }

    private func save() {
        guard let latitude, let longitude else { return }
        var profile = editing ?? Profile(
            name: "",
            latitude: 0,
            longitude: 0,
            calculationMethod: .mwl,
            asrMadhab: .shafii
        )
        profile.name = name.trimmingCharacters(in: .whitespaces)
        profile.latitude = latitude
        profile.longitude = longitude
        profile.calculationMethod = method
        profile.asrMadhab = madhab
        profile.useLocationTimezone = useLocationTimezone
        profile.timezone = useLocationTimezone ? timezone : ""
        onSave(profile)
        dismiss()
    }
}

extension CalculationMethodKey {
    /// The names users recognise. Not derived from the raw value — "Umm al-Qura" is not
    /// `ummAlQura` with spaces, and "MWL" is an initialism the enum case cannot carry.
    var displayName: String {
        switch self {
        case .mwl: "Muslim World League"
        case .isna: "ISNA (North America)"
        case .ummAlQura: "Umm al-Qura"
        case .egyptian: "Egyptian General Authority"
        case .karachi: "University of Karachi"
        case .dubai: "Dubai"
        case .moonSightingCommittee: "Moonsighting Committee"
        case .kuwait: "Kuwait"
        case .qatar: "Qatar"
        case .singapore: "Singapore"
        }
    }
}
