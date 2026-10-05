@preconcurrency import CoreLocation
import SharedLogic
import SwiftUI

struct ProfileFormSheet: View {
    let editing: Profile?
    var onDelete: ((Profile) -> Void)?
    let onSave: (Profile) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @StateObject private var location = LocationService()
    @StateObject private var selectedLocation: ProfileLocation
    @State private var name: String
    @State private var latitudeText: String
    @State private var longitudeText: String
    @State private var method: CalculationMethodKey
    @State private var madhab: AsrMadhab
    @State private var useLocationTimezone: Bool
    @State private var timezone: String
    @State private var hijriOffset: Int
    @State private var search = ""
    @State private var results: [CLPlacemark] = []
    @State private var searching = false
    @State private var searchError: String?
    @State private var manualLocation = false

    init(editing: Profile? = nil, onDelete: ((Profile) -> Void)? = nil, onSave: @escaping (Profile) -> Void) {
        self.editing = editing
        self.onDelete = onDelete
        self.onSave = onSave
        _selectedLocation = StateObject(wrappedValue: ProfileLocation(place: editing.map {
            ProfilePlace(coordinates: ProfileCoordinates(latitude: $0.latitude, longitude: $0.longitude),
                         name: $0.locationName, timezone: $0.timezone)
        }))
        _name = State(initialValue: editing?.name ?? "")
        _latitudeText = State(initialValue: editing.map { String($0.latitude) } ?? "")
        _longitudeText = State(initialValue: editing.map { String($0.longitude) } ?? "")
        _method = State(initialValue: editing?.calculationMethod ?? .mwl)
        _madhab = State(initialValue: editing?.asrMadhab ?? .shafii)
        _useLocationTimezone = State(initialValue: editing?.useLocationTimezone ?? true)
        _timezone = State(initialValue: editing?.timezone ?? "")
        _hijriOffset = State(initialValue: editing.map {
            HijriCalendar.effectiveOffset($0.hijriOffset, monthKey: $0.hijriOffsetMonthKey,
                                         on: CalendarDate.from(AppClock.now, in: $0.effectiveTimeZone), in: $0.effectiveTimeZone)
        } ?? 0)
    }

    var body: some View {
        let palette = NeutralPalette(scheme: scheme)
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name, prompt: Text("Name").foregroundStyle(palette.muted)).font(AynamaFont.bodyLG)
                        .textInputAutocapitalization(.words).accessibilityIdentifier("profile-name")
                } header: { sectionTitle(editing == nil ? "New profile" : "Edit profile", display: true) }
                .listRowBackground(Color.clear)
                Section {
                    TextField("City or location", text: $search, prompt: Text("City or location").foregroundStyle(palette.muted)).accessibilityIdentifier("profile-city")
                    if searching { Text("Finding locations…").font(AynamaFont.bodySM).foregroundStyle(palette.muted) }
                    ForEach(Array(results.enumerated()), id: \.offset) { _, place in
                        Button(placeLabel(place)) { choose(place) }.frame(minHeight: 44)
                    }
                    if let searchError { Text(searchError).font(AynamaFont.bodySM).foregroundStyle(palette.muted) }
                    Button { location.locate() } label: {
                        Label(location.isLocating ? "Finding your location…" : "Use current location", systemImage: "location")
                            .frame(minHeight: 44)
                    }.disabled(location.isLocating)
                    if let error = location.error { Text(error).font(AynamaFont.bodySM).foregroundStyle(palette.muted) }
                    if coordinates != nil {
                        Text(selectedPlace?.name ?? (selectedLocation.isResolving ? "Finding city…" : "Location selected"))
                            .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                            .accessibilityIdentifier("profile-location-name")
                    }
                    DisclosureGroup("Enter coordinates", isExpanded: $manualLocation) {
                        coordinateField("Latitude", text: $latitudeText, example: "21.4225", id: "profile-latitude")
                        coordinateField("Longitude", text: $longitudeText, example: "39.8262", id: "profile-longitude")
                        Picker("Time zone", selection: $timezone) {
                            Text("Device time zone").tag("")
                            ForEach(TimeZone.knownTimeZoneIdentifiers, id: \.self) { Text($0).tag($0) }
                        }
                    }
                    if !timezone.isEmpty {
                        Toggle(isOn: $useLocationTimezone) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Use location time zone")
                                Text(timezone)
                                    .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                            }
                        }.accessibilityIdentifier("profile-location-time-zone")
                    }
                } header: { sectionTitle("Location") }
                .listRowBackground(Color.clear)
                Section {
                    Picker("Calculation method", selection: $method) {
                        ForEach(CalculationMethodKey.allCases, id: \.self) { Text($0.displayName).tag($0) }
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Asr school")
                        Picker("Asr school", selection: $madhab) {
                            Text("Shāfiʻī").tag(AsrMadhab.shafii)
                            Text("Ḥanafī").tag(AsrMadhab.hanafi)
                        }.pickerStyle(.segmented)
                    }.padding(.vertical, 8)
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Hijri date adjustment")
                        Text("Shifts the Hijri date and Ramadan for local moon sighting. 0 keeps the calculated date; + starts the month earlier, − later.")
                            .font(AynamaFont.bodySM).foregroundStyle(palette.muted)
                        Picker("Hijri date adjustment", selection: $hijriOffset) {
                            ForEach(-2...2, id: \.self) { value in
                                Text(value < 0 ? "−\(abs(value))" : value > 0 ? "+\(value)" : "0").tag(value)
                            }
                        }.pickerStyle(.segmented).accessibilityIdentifier("profile-hijri-adjustment")
                    }.padding(.vertical, 8)
                } header: { sectionTitle("Calculation") }
                .listRowBackground(Color.clear)
                if let editing, let onDelete {
                    Section {
                        Button("Delete profile", role: .destructive) { onDelete(editing); dismiss() }
                            .frame(minHeight: 44)
                    }.listRowBackground(Color.clear)
                }
            }
            .neutralSurface()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.font(AynamaFont.body).foregroundStyle(palette.foreground) }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() }.font(AynamaFont.body).foregroundStyle(palette.foreground).disabled(!isValid) }
            }
            .task(id: search) { await findPlaces() }
            .task(id: coordinates) {
                guard let coordinates else { return }
                if manualLocation {
                    try? await Task.sleep(for: .milliseconds(400))
                    guard !Task.isCancelled else { return }
                }
                await selectedLocation.resolve(coordinates)
                guard !Task.isCancelled, self.coordinates == coordinates,
                      !manualLocation, let zone = selectedPlace?.timezone, !zone.isEmpty else { return }
                timezone = zone
            }
            .onChange(of: location.location) { _, fix in
                guard let fix else { return }
                latitudeText = String(fix.coordinate.latitude)
                longitudeText = String(fix.coordinate.longitude)
                timezone = TimeZone.current.identifier
                useLocationTimezone = true
                search = ""
                results = []
            }
            .onDisappear { location.stop() }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationBackground(palette.background)
    }

    private func sectionTitle(_ title: String, display: Bool = false) -> some View {
        Text(title).font(display ? AynamaFont.displayMD : AynamaFont.title).textCase(nil)
            .foregroundStyle(NeutralPalette(scheme: scheme).foreground)
    }
    private func coordinateField(_ title: String, text: Binding<String>, example: String, id: String) -> some View {
        LabeledContent(title) {
            TextField(example, text: text, prompt: Text(example).foregroundStyle(NeutralPalette(scheme: scheme).muted)).accessibilityIdentifier(id)
                .keyboardType(.numbersAndPunctuation).multilineTextAlignment(.trailing)
        }
    }
    private var latitude: Double? {
        Double(latitudeText.trimmingCharacters(in: .whitespaces)).flatMap { (-90.0...90.0).contains($0) ? $0 : nil }
    }
    private var longitude: Double? {
        Double(longitudeText.trimmingCharacters(in: .whitespaces)).flatMap { (-180.0...180.0).contains($0) ? $0 : nil }
    }
    private var isValid: Bool { !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && latitude != nil && longitude != nil }
    private var coordinates: ProfileCoordinates? {
        guard let latitude, let longitude else { return nil }
        return ProfileCoordinates(latitude: latitude, longitude: longitude)
    }
    private var selectedPlace: ProfilePlace? {
        selectedLocation.place?.coordinates == coordinates ? selectedLocation.place : nil
    }

    private func placeLabel(_ place: CLPlacemark) -> String {
        ProfilePlace(place).name ?? "Location selected"
    }
    private func choose(_ place: CLPlacemark) {
        guard let fix = place.location else { return }
        selectedLocation.select(ProfilePlace(place))
        latitudeText = String(fix.coordinate.latitude)
        longitudeText = String(fix.coordinate.longitude)
        timezone = place.timeZone?.identifier ?? ""
        useLocationTimezone = !timezone.isEmpty
        search = ""
        results = []
    }
    private func findPlaces() async {
        results = []
        searchError = nil
        guard search.trimmingCharacters(in: .whitespaces).count >= 3 else { searching = false; return }
        let query = search
        do {
            try await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            searching = true
            let places = try await CLGeocoder().geocodeAddressString(query)
            guard !Task.isCancelled, query == search else { return }
            results = places.filter { $0.location != nil }
            searching = false
            if results.isEmpty { searchError = "No locations found. Try another city or enter coordinates." }
        } catch {
            guard !Task.isCancelled, query == search else { return }
            searching = false
            searchError = "Couldn't find that location. Try again or enter coordinates."
        }
    }
    private func save() {
        guard let latitude, let longitude else { return }
        var profile = editing ?? Profile(name: "", latitude: 0, longitude: 0, calculationMethod: .mwl, asrMadhab: .shafii)
        profile.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.latitude = latitude
        profile.longitude = longitude
        profile.locationName = selectedPlace?.name
        profile.calculationMethod = method
        profile.asrMadhab = madhab
        // Keep the detected zone when the toggle is off, so enabling it later remains possible.
        profile.timezone = timezone
        profile.useLocationTimezone = useLocationTimezone && !timezone.isEmpty
        profile.setHijriAdjustment(hijriOffset, now: AppClock.now)
        onSave(profile)
        dismiss()
    }
}

extension CalculationMethodKey {
    var displayName: String {
        switch self {
        case .mwl: "Muslim World League"
        case .isna: "ISNA (North America)"
        case .ummAlQura: "Umm al-Qurā"
        case .egyptian: "Egyptian General Authority"
        case .karachi: "University of Karachi"
        case .dubai: "Dubai"
        case .moonSightingCommittee: "Moon Sighting Committee"
        case .kuwait: "Kuwait"
        case .qatar: "Qatar"
        case .singapore: "Singapore"
        }
    }
    var shortName: String {
        switch self {
        case .mwl: "MWL"
        case .isna: "ISNA"
        case .ummAlQura: "Umm al-Qurā"
        case .egyptian: "Egyptian"
        case .karachi: "Karachi"
        case .dubai: "Dubai"
        case .moonSightingCommittee: "MSC"
        case .kuwait: "Kuwait"
        case .qatar: "Qatar"
        case .singapore: "Singapore"
        }
    }
}
