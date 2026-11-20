import CoreLocation
import XCTest

@testable import Aynama

final class ProfileLocationTests: XCTestCase {
    private let makkah = ProfileCoordinates(latitude: 21.3871, longitude: 39.8688)

    func testCityAndCountryAvoidsAddressesAndHandlesMissingCities() {
        XCTAssertEqual(ProfilePlace.cityAndCountry(city: " Makkah ", region: nil, country: "Saudi Arabia"), "Makkah, Saudi Arabia")
        XCTAssertEqual(ProfilePlace.cityAndCountry(city: "", region: "Makkah Province", country: "Saudi Arabia"), "Makkah Province, Saudi Arabia")
        XCTAssertEqual(ProfilePlace.cityAndCountry(city: nil, region: nil, country: "Saudi Arabia"), "Saudi Arabia")
        XCTAssertNil(ProfilePlace.cityAndCountry(city: " ", region: nil, country: nil))
    }

    @MainActor
    func testCurrentLocationResolvesCityAndCountryFromTheOriginalFix() async {
        let location = ProfileLocation { coordinates in
            XCTAssertEqual(coordinates, self.makkah)
            // A placemark can be centred on the city rather than the actual device fix.
            return ProfilePlace(coordinates: ProfileCoordinates(latitude: 21.4225, longitude: 39.8262),
                                name: "Makkah, Saudi Arabia", timezone: "Asia/Riyadh")
        }
        await location.resolve(makkah)
        XCTAssertEqual(location.place?.name, "Makkah, Saudi Arabia")
        XCTAssertEqual(location.place?.coordinates, makkah)
        XCTAssertEqual(location.place?.timezone, "Asia/Riyadh")
        XCTAssertFalse(location.isResolving)
    }

    @MainActor
    func testSearchAndSavedSelectionsKeepTheirNamesWithoutReverseGeocoding() async {
        let place = ProfilePlace(coordinates: makkah, name: "Makkah, Saudi Arabia")
        let location = ProfileLocation(place: place) { _ in
            XCTFail("A saved or searched place already has its label, including while offline")
            return nil
        }
        await location.resolve(makkah)
        XCTAssertEqual(location.place?.name, place.name)
        location.select(place)
        await location.resolve(makkah)
        XCTAssertEqual(location.place?.name, place.name)
    }

    @MainActor
    func testFailedLookupClearsTheOldCityWithoutDisplayingCoordinates() async {
        let location = ProfileLocation(place: ProfilePlace(coordinates: makkah, name: "Makkah, Saudi Arabia")) { _ in
            throw NSError(domain: "offline", code: 1)
        }
        let london = ProfileCoordinates(latitude: 51.5074, longitude: -0.1278)
        await location.resolve(london)
        XCTAssertEqual(location.place?.coordinates, london)
        XCTAssertNil(location.place?.name)
        XCTAssertFalse(location.isResolving)
    }

    @MainActor
    func testLateCurrentLocationLookupCannotReplaceASearchedCity() async {
        var pending: CheckedContinuation<ProfilePlace?, Error>?
        let location = ProfileLocation { _ in
            try await withCheckedThrowingContinuation { pending = $0 }
        }
        let coordinates = makkah
        let task = Task { await location.resolve(coordinates) }
        while pending == nil { await Task.yield() }
        let london = ProfilePlace(coordinates: ProfileCoordinates(latitude: 51.5074, longitude: -0.1278),
                                  name: "London, United Kingdom")
        location.select(london)
        pending?.resume(returning: ProfilePlace(coordinates: makkah, name: "Makkah, Saudi Arabia"))
        await task.value
        XCTAssertEqual(location.place?.name, london.name)
        XCTAssertEqual(location.place?.coordinates, london.coordinates)
    }
}
