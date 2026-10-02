import Foundation
import Testing

@testable import SharedLogic

/// A port of `QiblaCalculatorTest.kt`. Same cities, same tolerances — the two platforms must
/// point the same way from the same place.
@Suite("Qibla")
struct QiblaCalculatorTests {

    @Test(
        "the bearing matches the known value for each city",
        arguments: [
            (name: "London", lat: 51.5074, lng: -0.1278, expected: 119.0),
            (name: "New York", lat: 40.7128, lng: -74.0060, expected: 58.0),
            (name: "Jakarta", lat: -6.2088, lng: 106.8456, expected: 295.0),
            (name: "Sydney", lat: -33.8688, lng: 151.2093, expected: 277.0),
        ]
    )
    func bearings(city: (name: String, lat: Double, lng: Double, expected: Double)) {
        let bearing = QiblaCalculator.bearing(latitude: city.lat, longitude: city.lng)
        let raw = abs(bearing - city.expected)
        let diff = raw > 180 ? 360 - raw : raw
        #expect(diff <= 1.0, "\(city.name): expected ~\(city.expected)° but got \(bearing)°")
    }

    @Test(
        "the bearing stays in [0, 360) at every degenerate point",
        arguments: [
            (name: "London", lat: 51.5074, lng: -0.1278),
            (name: "the Kaaba itself", lat: 21.4225, lng: 39.8262),
            (name: "the North Pole", lat: 90.0, lng: 0.0),
            (name: "the South Pole", lat: -90.0, lng: 0.0),
            (name: "the antimeridian", lat: 0.0, lng: 180.0),
        ]
    )
    func bearingRange(place: (name: String, lat: Double, lng: Double)) {
        let bearing = QiblaCalculator.bearing(latitude: place.lat, longitude: place.lng)
        #expect(bearing >= 0.0 && bearing < 360.0, "\(place.name): \(bearing) not in [0, 360)")
    }

    @Test("the distance at the Kaaba is near zero")
    func distanceAtKaaba() {
        let distance = QiblaCalculator.distanceKm(latitude: 21.4225, longitude: 39.8262)
        #expect(distance < 1.0)
    }

    @Test(
        "the distance matches the known value for each city",
        arguments: [
            (name: "London", lat: 51.5074, lng: -0.1278, range: 4500.0...5100.0),
            (name: "New York", lat: 40.7128, lng: -74.0060, range: 10000.0...10600.0),
            (name: "Jakarta", lat: -6.2088, lng: 106.8456, range: 7500.0...8400.0),
        ]
    )
    func distances(city: (name: String, lat: Double, lng: Double, range: ClosedRange<Double>)) {
        let distance = QiblaCalculator.distanceKm(latitude: city.lat, longitude: city.lng)
        #expect(city.range.contains(distance), "\(city.name): got \(distance) km")
    }
}

/// The two places the great-circle formulas can produce a non-number.
@Suite("Qibla edge cases")
struct QiblaEdgeCaseTests {

    @Test("the antipode of the Kaaba has a finite distance")
    func antipodeIsFinite() {
        // Haversine's `a` is exactly 1 here, and floating point can carry it just past, which
        // would make sqrt(1 - a) NaN and take the distance with it.
        let distance = QiblaCalculator.distanceKm(
            latitude: -QiblaCalculator.kaabaLatitude,
            longitude: QiblaCalculator.kaabaLongitude - 180
        )
        #expect(distance.isFinite)
        #expect(abs(distance - 20_015) < 5)
    }

    @Test("the Kaaba itself is zero away", arguments: [0.0, 1e-12])
    func zeroDistanceAtTheKaaba(offset: Double) {
        let distance = QiblaCalculator.distanceKm(
            latitude: QiblaCalculator.kaabaLatitude + offset,
            longitude: QiblaCalculator.kaabaLongitude
        )
        #expect(distance.isFinite)
        #expect(distance < 1)
    }
}
