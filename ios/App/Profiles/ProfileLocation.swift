@preconcurrency import CoreLocation
import SwiftUI

struct ProfileCoordinates: Hashable {
    let latitude: Double
    let longitude: Double
}

struct ProfilePlace {
    let coordinates: ProfileCoordinates
    let name: String?
    let timezone: String?

    init(_ place: CLPlacemark) {
        coordinates = ProfileCoordinates(latitude: place.location?.coordinate.latitude ?? 0,
                                         longitude: place.location?.coordinate.longitude ?? 0)
        name = Self.cityAndCountry(city: place.locality,
                                   region: place.subAdministrativeArea ?? place.administrativeArea,
                                   country: place.country)
        timezone = place.timeZone?.identifier
    }

    init(coordinates: ProfileCoordinates, name: String?, timezone: String? = nil) {
        self.coordinates = coordinates
        self.name = name
        self.timezone = timezone
    }

    static func cityAndCountry(city: String?, region: String?, country: String?) -> String? {
        func nonBlank(_ value: String?) -> String? {
            guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
            return trimmed
        }
        let parts = [nonBlank(city) ?? nonBlank(region), nonBlank(country)].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }

    @MainActor
    static func reverseGeocode(_ coordinates: ProfileCoordinates) async throws -> ProfilePlace? {
        let places = try await CLGeocoder().reverseGeocodeLocation(
            CLLocation(latitude: coordinates.latitude, longitude: coordinates.longitude)
        )
        return places.first.map { ProfilePlace($0) }
    }
}

/// Shares the selected-place cache across search, GPS and manual-entry flows. A late lookup
/// cannot replace a newer selection, and a failed lookup never falls back to numeric coordinates.
@MainActor
final class ProfileLocation: ObservableObject {
    typealias Lookup = @MainActor (ProfileCoordinates) async throws -> ProfilePlace?
    @Published private(set) var place: ProfilePlace?
    @Published private(set) var isResolving = false
    private var requestID = UUID()
    private let lookup: Lookup

    init(place: ProfilePlace? = nil, lookup: @escaping Lookup = ProfilePlace.reverseGeocode) {
        self.place = place
        self.lookup = lookup
    }

    func select(_ place: ProfilePlace) {
        requestID = UUID()
        isResolving = false
        self.place = place
    }

    func resolve(_ coordinates: ProfileCoordinates) async {
        if place?.coordinates == coordinates, place?.name != nil { return }
        let request = UUID()
        requestID = request
        place = nil
        isResolving = true
        let resolved = try? await lookup(coordinates)
        guard requestID == request else { return }
        isResolving = false
        guard !Task.isCancelled else { return }
        // Geocoders can snap their placemark to a nearby town centre. Cache the original fix.
        place = ProfilePlace(coordinates: coordinates, name: resolved?.name, timezone: resolved?.timezone)
    }
}
